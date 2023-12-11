package stroom.proxy.repo.dao.lmdb;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.proxy.repo.dao.lmdb.serde.LongSerde;
import stroom.proxy.repo.dao.lmdb.serde.PooledByteBuffer;
import stroom.proxy.repo.dao.lmdb.serde.Serde;
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
    private final LongSerde hashSerde = new LongSerde();
    private final ReentrantLock writeLock = new ReentrantLock();
    private final Cache<V, K> keyCache;
    private final Cache<K, Optional<V>> valueCache;
    private RowKey<K> rowKey;

    public AbstractDaoLmdb(final LmdbEnv env,
                           final String dbName,
                           final String indexName) {
        try {
            this.env = env;
            this.dbi = env.openDbi(dbName);
            this.indexDbi = env.openDbi(indexName);
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
            final PooledByteBuffer keyByteBuffer = getKeySerde().serialise(k);
            return env.readResult(txn -> {
                final ByteBuffer valueByteBuffer = dbi.get(txn, keyByteBuffer.get());
                keyByteBuffer.release();
                if (valueByteBuffer != null) {
                    final V value = getValueSerde().deserialise(valueByteBuffer);
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
                    final K key = getKeySerde().deserialise(kv.key());
                    final V value = getValueSerde().deserialise(kv.val());
                    consumer.accept(key, value);
                }
            }
        });
    }

    public K getOrCreateKey(final V value) {
        return keyCache.get(value, k -> {
            final PooledByteBuffer valueByteBuffer = getValueSerde().serialise(k);
            final long hash = ByteBufferUtils.xxHash(valueByteBuffer.get());
            final PooledByteBuffer hashByteBuffer = hashSerde.serialise(hash);
            Optional<K> id = fetchKey(dbi, indexDbi, hashByteBuffer.get(), valueByteBuffer.get());
            if (id.isPresent()) {
                return id.get();
            }

            writeLock.lock();
            try {
                // Try fetch under lock.
                id = fetchKey(dbi, indexDbi, hashByteBuffer.get(), valueByteBuffer.get());
                if (id.isPresent()) {
                    return id.get();
                }

                // Couldn't fetch, try put.
                final K newId = rowKey.next();
                final PooledByteBuffer idByteBuffer = getKeySerde().serialise(newId);
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
            final ByteBuffer existingIndexValue = indexDbi.get(txn, hashByteBuffer.get());
            if (existingIndexValue != null) {
                final ByteBuffer appended;
                final int bufferSize = existingIndexValue.remaining() + getKeyLength();
                try (final MyByteBufferOutput output = new MyByteBufferOutput(bufferSize, bufferSize)) {
                    output.writeByteBuffer(existingIndexValue);
                    output.writeByteBuffer(idBuffer.get());
                    output.flush();
                    appended = output.getByteBuffer().flip();
                }
                indexDbi.put(txn, hashByteBuffer.get(), appended);
            } else {
                indexDbi.put(txn, hashByteBuffer.get(), idBuffer.get());
            }

            dbi.put(txn, idBuffer.get(), value.get());

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
                    final K id = getKeySerde().deserialise(value);
                    final PooledByteBuffer index = getKeySerde().serialise(id);
                    final ByteBuffer storedValue = dbi.get(txn, index.get());
                    index.release();
                    if (storedValue.equals(valueByteBuffer)) {
                        return Optional.of(id);
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
