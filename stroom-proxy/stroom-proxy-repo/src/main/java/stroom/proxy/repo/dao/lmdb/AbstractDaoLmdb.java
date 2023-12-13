package stroom.proxy.repo.dao.lmdb;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.lmdb.serde.Serde;
import stroom.proxy.repo.dao.lmdb.serde.LongSerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

public abstract class AbstractDaoLmdb<K, V> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractDaoLmdb.class);

    private final Dbi<ByteBuffer> dbi;
    private final Dbi<ByteBuffer> indexDbi;
    private final LmdbEnv env;
    private final LongSerde hashSerde;
    private final ReentrantLock writeLock = new ReentrantLock();
    private final Cache<V, K> keyCache;
    private final Cache<K, Optional<V>> valueCache;
    private RowKey<K> rowKey;

    public AbstractDaoLmdb(final LmdbEnv env,
                           final String dbName,
                           final String indexName,
                           final LongSerde hashSerde) {
        try {
            this.env = env;
            this.dbi = env.openDbi(dbName);
            this.indexDbi = env.openDbi(indexName);
            this.hashSerde = hashSerde;
            rowKey = createRowKey(env, dbi);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }

        keyCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterAccess(1, TimeUnit.MINUTES)
                .build();
        valueCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterAccess(1, TimeUnit.MINUTES)
                .build();
    }

    abstract RowKey<K> createRowKey(LmdbEnv lmdbEnv, Dbi<ByteBuffer> dbi);

    abstract Serde<V> getValueSerde();

    abstract Serde<K> getKeySerde();

    abstract int getKeyLength();

    public Optional<V> get(final K key) {
        return valueCache.get(key, k -> {
            final PooledByteBuffer keyByteBuffer = getKeySerde().serialize(k);
            return env.readResult(txn -> {
                final ByteBuffer valueByteBuffer = dbi.get(txn, keyByteBuffer.getByteBuffer());
                keyByteBuffer.release();
                if (valueByteBuffer != null) {
                    final V value = getValueSerde().deserialize(valueByteBuffer);
                    return Optional.of(value);
                }
                return Optional.empty();
            });
        });
    }

    public void getAll(final BiConsumer<K, V> consumer) {
        env.read(txn -> {
            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(txn)) {
                for (final KeyVal<ByteBuffer> kv : cursor) {
                    final K key = getKeySerde().deserialize(kv.key());
                    final V value = getValueSerde().deserialize(kv.val());
                    consumer.accept(key, value);
                }
            }
        });
    }

    public K getOrCreateKey(final V value) {
        return keyCache.get(value, k -> {
            final PooledByteBuffer valueByteBuffer = getValueSerde().serialize(k);
            final long hash = ByteBufferUtils.xxHash(valueByteBuffer.getByteBuffer());
            final PooledByteBuffer hashByteBuffer = hashSerde.serialize(hash);
            Optional<K> id = fetchKey(dbi, indexDbi, hashByteBuffer.getByteBuffer(), valueByteBuffer.getByteBuffer());
            if (id.isPresent()) {
                return id.get();
            }

            writeLock.lock();
            try {
                // Try fetch under lock.
                id = fetchKey(dbi, indexDbi, hashByteBuffer.getByteBuffer(), valueByteBuffer.getByteBuffer());
                if (id.isPresent()) {
                    return id.get();
                }

                // Couldn't fetch, try put.
                final K newId = rowKey.next();
                final PooledByteBuffer idByteBuffer = getKeySerde().serialize(newId);
                put(idByteBuffer, hashByteBuffer, valueByteBuffer);

                return newId;

            } finally {
                writeLock.unlock();
            }
        });
    }

    private void put(final PooledByteBuffer idBuffer,
                     final PooledByteBuffer hashByteBuffer,
                     final PooledByteBuffer value) {
        env.write(txn -> {
            final ByteBuffer existingIndexValue = indexDbi.get(txn, hashByteBuffer.getByteBuffer());
            if (existingIndexValue != null) {
                final ByteBuffer appended;
                final int bufferSize = existingIndexValue.remaining() + getKeyLength();
                try (final MyByteBufferOutput output = new MyByteBufferOutput(bufferSize, bufferSize)) {
                    output.writeByteBuffer(existingIndexValue);
                    output.writeByteBuffer(idBuffer.getByteBuffer());
                    output.flush();
                    appended = output.getByteBuffer().flip();
                }
                indexDbi.put(txn, hashByteBuffer.getByteBuffer(), appended);
            } else {
                indexDbi.put(txn, hashByteBuffer.getByteBuffer(), idBuffer.getByteBuffer());
            }

            dbi.put(txn, idBuffer.getByteBuffer(), value.getByteBuffer());

            idBuffer.release();
            hashByteBuffer.release();
            value.release();
        });
    }

    private Optional<K> fetchKey(final Dbi<ByteBuffer> dbi,
                                 final Dbi<ByteBuffer> indexDbi,
                                 final ByteBuffer hashByteBuffer,
                                 final ByteBuffer valueByteBuffer) {
        return env.readResult(txn -> {
            final ByteBuffer value = indexDbi.get(txn, hashByteBuffer);
            if (value != null) {
                while (value.hasRemaining()) {
                    final K id = getKeySerde().deserialize(value);
                    try (final PooledByteBuffer index = getKeySerde().serialize(id)) {
                        final ByteBuffer storedValue = dbi.get(txn, index.getByteBuffer());
                        if (storedValue.equals(valueByteBuffer)) {
                            return Optional.of(id);
                        }
                    }
                }
            }
            return Optional.empty();
        });
    }

    public long count() {
        return env.count(dbi);
    }

    public void clear() {
        env.clear(dbi);
        env.clear(indexDbi);
        rowKey = createRowKey(env, dbi);
    }
}
