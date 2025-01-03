package stroom.planb.impl.dao;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.lmdb.LmdbConfig;
import stroom.lmdb2.LmdbEnvDir;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.KeyRange;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.function.Predicate;

import static java.nio.charset.StandardCharsets.UTF_8;

abstract class AbstractLmdbWriter<K, V> implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractLmdbWriter.class);

    private static final byte[] NAME = "db".getBytes(UTF_8);

    private final Serde<K, V> serde;
    final ByteBufferFactory byteBufferFactory;
    final Env<ByteBuffer> env;
    final Dbi<ByteBuffer> dbi;
    private Txn<ByteBuffer> txn;
    private final boolean keepFirst;
    private int commitCount = 0;

    public AbstractLmdbWriter(final Path path,
                              final ByteBufferFactory byteBufferFactory,
                              final Serde<K, V> serde,
                              final boolean keepFirst) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(path, true);
        this.byteBufferFactory = byteBufferFactory;
        this.serde = serde;
        this.keepFirst = keepFirst;

        LOGGER.info(() -> "Creating: " + path);

        final Env.Builder<ByteBuffer> builder = Env.create()
                .setMapSize(LmdbConfig.DEFAULT_MAX_STORE_SIZE.getBytes())
                .setMaxDbs(1)
                .setMaxReaders(1);

        env = builder.open(lmdbEnvDir.getEnvDir().toFile(), EnvFlags.MDB_NOTLS);
        dbi = env.openDbi(NAME, DbiFlags.MDB_CREATE, DbiFlags.MDB_DUPSORT);
    }

    public boolean insert(final KV<K, V> kv) {
        return insert(kv.key(), kv.value());
    }

    public boolean insert(final K key, final V value) {
        return serde.createKeyByteBuffer(key, keyByteBuffer ->
                serde.createValueByteBuffer(key, value, valueByteBuffer -> {
                    final Txn<ByteBuffer> txn = getOrCreateTxn();

                    // Try to put without overwriting existing values.
                    if (!dbi.put(txn, keyByteBuffer, valueByteBuffer, PutFlags.MDB_NOOVERWRITE)) {
                        serde.createPrefixPredicate(key, predicate -> {
                            if (keepFirst) {
                                if (!exists(txn, keyByteBuffer, predicate)) {
                                    dbi.put(txn, keyByteBuffer, valueByteBuffer);
                                }
                            } else {
                                // Delete current value if there is one.
                                delete(txn, keyByteBuffer, predicate);
                                // Put new value allowing for duplicate keys as we are only using a hash key.
                                dbi.put(txn, keyByteBuffer, valueByteBuffer);
                            }
                            return true;
                        });
                    }

                    commitCount++;
                    if (commitCount > 10000) {
                        commit();
                        commitCount = 0;
                    }

                    return true;
                }));
    }

    private boolean delete(final Txn<ByteBuffer> txn,
                           final ByteBuffer keyByteBuffer,
                           final Predicate<KeyVal<ByteBuffer>> predicate) {
        final KeyRange<ByteBuffer> keyRange = KeyRange.closed(keyByteBuffer, keyByteBuffer);
        try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(txn, keyRange)) {
            final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
            while (iterator.hasNext()) {
                final KeyVal<ByteBuffer> keyVal = iterator.next();
                if (predicate.test(keyVal)) {
                    iterator.remove();
                    return true;
                }
            }
        }
        return false;
    }

    private boolean exists(final Txn<ByteBuffer> txn,
                           final ByteBuffer keyByteBuffer,
                           final Predicate<KeyVal<ByteBuffer>> predicate) {
        final KeyRange<ByteBuffer> keyRange = KeyRange.closed(keyByteBuffer, keyByteBuffer);
        try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(txn, keyRange)) {
            final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
            while (iterator.hasNext()) {
                final KeyVal<ByteBuffer> keyVal = iterator.next();
                if (predicate.test(keyVal)) {
                    return true;
                }
            }
        }
        return false;
    }


    Txn<ByteBuffer> getOrCreateTxn() {
        if (txn == null) {
            txn = env.txnWrite();
        }
        return txn;
    }

    void commit() {
        if (txn != null) {
            try {
                txn.commit();
            } finally {
                try {
                    txn.close();
                } finally {
                    txn = null;
                }
            }
        }
    }

    @Override
    public void close() {
        commit();
        env.close();
    }
}
