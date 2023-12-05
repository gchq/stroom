package stroom.proxy.repo.dao.lmdb;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.proxy.repo.dao.lmdb.serde.LongSerde;
import stroom.proxy.repo.dao.lmdb.serde.Serde;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.BiConsumer;

public abstract class AbstractDaoLmdb<K, V> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractDaoLmdb.class);

    private final Env<ByteBuffer> env;
    private final Dbi<ByteBuffer> dbi;
    private final Dbi<ByteBuffer> indexDbi;
    private final LmdbWriteQueue writeQueue;
    private final LongSerde hashSerde = new LongSerde();
    private RowKey<K> rowKey;

    public AbstractDaoLmdb(final ProxyLmdbConfig proxyLmdbConfig,
                           final PathCreator pathCreator,
                           final LmdbEnvFactory lmdbEnvFactory,
                           final String dbName,
                           final String indexName) {
        try {
            this.env = lmdbEnvFactory.build(pathCreator, proxyLmdbConfig, dbName);
            this.dbi = env.openDbi(dbName, DbiFlags.MDB_CREATE);
            this.indexDbi = env.openDbi(indexName, DbiFlags.MDB_CREATE);
            writeQueue = new LmdbWriteQueue(env);

            rowKey = createRowKey(env, dbi);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    abstract RowKey<K> createRowKey(Env<ByteBuffer> env, Dbi<ByteBuffer> dbi);

    abstract Serde<V> getValueSerde();

    abstract Serde<K> getKeySerde();

    abstract int getKeyLength();

    public Optional<V> get(final K key) {
        final ByteBuffer keyByteBuffer = getKeySerde().serialise(key);
        try (final Txn<ByteBuffer> txn = env.txnRead()) {
            final ByteBuffer valueByteBuffer = dbi.get(txn, keyByteBuffer);
            if (valueByteBuffer != null) {
                final V value = getValueSerde().deserialise(valueByteBuffer);
                return Optional.of(value);
            }
            return Optional.empty();
        }
    }

    public void getAll(final BiConsumer<K, V> consumer) {
        try (final Txn<ByteBuffer> txn = env.txnRead()) {
            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(txn)) {
                for (final KeyVal<ByteBuffer> kv : cursor) {
                    final K key = getKeySerde().deserialise(kv.key());
                    final V value = getValueSerde().deserialise(kv.val());
                    consumer.accept(key, value);
                }
            }
        }
    }

    public Optional<K> getId(final V value) {
        final ByteBuffer valueByteBuffer = getValueSerde().serialise(value);
        final long hash = ByteBufferUtils.xxHash(valueByteBuffer);
        final ByteBuffer hashByteBuffer = hashSerde.serialise(hash);
        return fetchId(dbi, indexDbi, hashByteBuffer, valueByteBuffer);
    }

    public K getOrCreateId(final V value) {
        final ByteBuffer valueByteBuffer = getValueSerde().serialise(value);
        final long hash = ByteBufferUtils.xxHash(valueByteBuffer);
        final ByteBuffer hashByteBuffer = hashSerde.serialise(hash);
        Optional<K> id = fetchId(dbi, indexDbi, hashByteBuffer, valueByteBuffer);
        if (id.isPresent()) {
            return id.get();
        }

        synchronized (this) {
            // Try fetch under lock.
            id = fetchId(dbi, indexDbi, hashByteBuffer, valueByteBuffer);
            if (id.isPresent()) {
                return id.get();
            }

            // Couldn't fetch, try put.
            final K newId = rowKey.next();
            final ByteBuffer idByteBuffer = getKeySerde().serialise(newId);
            put(idByteBuffer, hashByteBuffer, valueByteBuffer);
            writeQueue.sync();

            return newId;
        }
    }

    private void put(final ByteBuffer idBuffer,
                     final ByteBuffer hashByteBuffer,
                     final ByteBuffer value) {
        writeQueue.write(txn -> {
            final ByteBuffer existingIndexValue = indexDbi.get(txn, hashByteBuffer);
            if (existingIndexValue != null) {
                final ByteBuffer appended;
                final int bufferSize = existingIndexValue.remaining() + getKeyLength();
                try (final MyByteBufferOutput output = new MyByteBufferOutput(bufferSize, bufferSize)) {
                    output.writeByteBuffer(existingIndexValue);
                    output.writeByteBuffer(idBuffer);
                    output.flush();
                    appended = output.getByteBuffer().flip();
                }
                indexDbi.put(txn, hashByteBuffer, appended);
            } else {
                indexDbi.put(txn, hashByteBuffer, idBuffer);
            }

            dbi.put(txn, idBuffer, value);
        });
    }

    private Optional<K> fetchId(final Dbi<ByteBuffer> dbi,
                                final Dbi<ByteBuffer> indexDbi,
                                final ByteBuffer hashByteBuffer,
                                final ByteBuffer valueByteBuffer) {
        try (final Txn<ByteBuffer> txn = env.txnRead()) {
            final ByteBuffer value = indexDbi.get(txn, hashByteBuffer);
            if (value != null) {
                while (value.hasRemaining()) {
                    final K id = getKeySerde().deserialise(value);
                    final ByteBuffer index = getKeySerde().serialise(id);
                    final ByteBuffer storedValue = dbi.get(txn, index);
                    if (storedValue.equals(valueByteBuffer)) {
                        return Optional.of(id);
                    }
                }
            }
            return Optional.empty();
        }
    }

    public long count() {
        try (final Txn<ByteBuffer> txn = env.txnRead()) {
            return LmdbUtil.count(txn, dbi);
        }
    }

    public void clear() {
        writeQueue.write(txn -> LmdbUtil.deleteAll(txn, dbi));
        writeQueue.write(txn -> LmdbUtil.deleteAll(txn, indexDbi));
        writeQueue.commit();
        writeQueue.sync();

        rowKey = createRowKey(env, dbi);
    }
}
