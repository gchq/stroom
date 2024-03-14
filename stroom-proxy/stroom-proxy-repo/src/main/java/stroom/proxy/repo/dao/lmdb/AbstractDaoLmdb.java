package stroom.proxy.repo.dao.lmdb;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.proxy.repo.dao.lmdb.serde.ExtendedSerde;
import stroom.proxy.repo.dao.lmdb.serde.LongSerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

public abstract class AbstractDaoLmdb<K, V> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractDaoLmdb.class);

    private final Db<K, V> db;
    private final Db<Long, K> indexDb;
    private final LmdbEnv env;
    private final ExtendedSerde<V> valueSerde;
    private final ReentrantLock writeLock = new ReentrantLock();
    private final Cache<V, K> keyCache;
    private final Cache<K, Optional<V>> valueCache;
    private RowKey<K> rowKey;

    public AbstractDaoLmdb(final LmdbEnv env,
                           final String dbName,
                           final String indexName,
                           final ExtendedSerde<K> keySerde,
                           final ExtendedSerde<V> valueSerde) {
        try {
            this.env = env;
            this.valueSerde = valueSerde;
            this.db = env.openDb(dbName, keySerde, valueSerde);
            this.indexDb = env.openDb(indexName, new LongSerde(), keySerde);
            rowKey = createRowKey(db);
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

    abstract RowKey<K> createRowKey(Db<K, V> db);

    public Optional<V> get(final K key) {
        return valueCache.get(key, k -> db.getOptional(key));
    }

    public void getAll(final BiConsumer<K, V> consumer) {
        db.getAll(consumer);
    }

    public K getOrCreateKey(final V value) {
        return keyCache.get(value, k -> {
            final long hash;
            try (final PooledByteBuffer valueByteBuffer = valueSerde.serialize(k, env.getByteBufferPool())) {
                hash = ByteBufferUtils.xxHash(valueByteBuffer.getByteBuffer());
            }
            Optional<K> key = fetchKey(hash);
            if (key.isPresent()) {
                return key.get();
            }

            writeLock.lock();
            try {
                // Try fetch under lock.
                key = fetchKey(hash);
                if (key.isPresent()) {
                    return key.get();
                }

                // Couldn't fetch, try put.
                final K newKey = rowKey.next();
                put(hash, newKey, value);

                return newKey;

            } finally {
                writeLock.unlock();
            }
        });
    }

    private void put(final long hash,
                     final K key,
                     final V value) {
        indexDb.put(hash, key);
        db.put(key, value);

//        env.writeAsync(txn -> {
//            final ByteBuffer existingIndexValue = indexDbi.get(txn, hashByteBuffer.getByteBuffer());
//            if (existingIndexValue != null) {
//                final ByteBuffer appended;
//                final int bufferSize = existingIndexValue.remaining() + getKeyLength();
//                try (final MyByteBufferOutput output = new MyByteBufferOutput(bufferSize, bufferSize)) {
//                    output.writeByteBuffer(existingIndexValue);
//                    output.writeByteBuffer(idBuffer.getByteBuffer());
//                    output.flush();
//                    appended = output.getByteBuffer().flip();
//                }
//                indexDbi.put(txn, hashByteBuffer.getByteBuffer(), appended);
//            } else {
//                indexDbi.put(txn, hashByteBuffer.getByteBuffer(), idBuffer.getByteBuffer());
//            }
//
//            dbi.put(txn, idBuffer.getByteBuffer(), value.getByteBuffer());
//
//            idBuffer.release();
//            hashByteBuffer.release();
//            value.release();
//        });
    }

    private Optional<K> fetchKey(final long valueHash) {
        return env.readResult(txn -> {
            return indexDb.getOptional(valueHash);

//            if (value != null) {
//                while (value.hasRemaining()) {
//                    final K id = getKeySerde().deserialize(value);
//                    try (final PooledByteBuffer index = getKeySerde().serialize(id)) {
//                        final ByteBuffer storedValue = dbi.get(txn, index.getByteBuffer());
//                        if (storedValue.equals(valueByteBuffer)) {
//                            return Optional.of(id);
//                        }
//                    }
//                }
//            }
        });
    }

    public long count() {
        return db.count();
    }

    public void clear() {
        db.clear();
        indexDb.clear();
        rowKey = createRowKey(db);
    }
}
