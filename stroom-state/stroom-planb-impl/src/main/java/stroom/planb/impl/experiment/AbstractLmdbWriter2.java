package stroom.planb.impl.experiment;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.lmdb.LmdbConfig;
import stroom.lmdb2.LmdbEnvDir;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;

abstract class AbstractLmdbWriter2<K, V> implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractLmdbWriter2.class);

    private static final byte[] KEY = "key".getBytes(UTF_8);
    private static final byte[] VALUE = "value".getBytes(UTF_8);
    private static final byte[] STATE = "state".getBytes(UTF_8);

    private final Serde2<K, V> serde;
    final ByteBufferFactory byteBufferFactory;
    final Env<ByteBuffer> env;
    final Dbi<ByteBuffer> keyDb;
    final Dbi<ByteBuffer> valueDb;
    final Dbi<ByteBuffer> stateDb;
    private Txn<ByteBuffer> writeTxn;
    final boolean keepFirst;
    int commitCount = 0;

    public AbstractLmdbWriter2(final Path path,
                               final ByteBufferFactory byteBufferFactory,
                               final Serde2<K, V> serde,
                               final boolean keepFirst) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(path, true);
        this.byteBufferFactory = byteBufferFactory;
        this.serde = serde;
        this.keepFirst = keepFirst;

        LOGGER.info(() -> "Creating: " + path);

        final Env.Builder<ByteBuffer> builder = Env.create()
                .setMapSize(LmdbConfig.DEFAULT_MAX_STORE_SIZE.getBytes())
                .setMaxDbs(3)
                .setMaxReaders(1);

        env = builder.open(lmdbEnvDir.getEnvDir().toFile(), EnvFlags.MDB_NOTLS);
        keyDb = env.openDbi(KEY, DbiFlags.MDB_CREATE);
        valueDb = env.openDbi(VALUE, DbiFlags.MDB_CREATE);
        stateDb = env.openDbi(STATE, DbiFlags.MDB_CREATE);
    }

//    public void merge(final Path source) {
//        final Env.Builder<ByteBuffer> builder = Env.create()
//                .setMaxDbs(1)
//                .setMaxReaders(1);
//        try (final Env<ByteBuffer> sourceEnv = builder.open(source.toFile(), EnvFlags.MDB_NOTLS)) {
//            final Dbi<ByteBuffer> sourceDbi = sourceEnv.openDbi(STATE);
//            try (final Txn<ByteBuffer> readTxn = sourceEnv.txnRead()) {
//                try (final CursorIterable<ByteBuffer> cursorIterable = sourceDbi.iterate(readTxn)) {
//                    final Iterator<KeyVal<ByteBuffer>> iterator = cursorIterable.iterator();
//                    while (iterator.hasNext()) {
//                        final KeyVal<ByteBuffer> keyVal = iterator.next();
//                        insert(keyVal.key(), keyVal.val());
//                    }
//                }
//            }
//        }
//    }
//
//    public boolean insert(final KV<K, V> kv) {
//        return insert(kv.key(), kv.value());
//    }
//
//    public boolean insert(final K key, final V value) {
//        return serde.createKeyByteBuffer(key, keyByteBuffer ->
//                serde.createValueByteBuffer(key, value, valueByteBuffer ->
//                        insert(keyByteBuffer, valueByteBuffer)));
//    }
//
//    public boolean insert(final ByteBuffer keyByteBuffer,
//                          final ByteBuffer valueByteBuffer) {
//        final Txn<ByteBuffer> writeTxn = getOrCreateWriteTxn();
//
//        // If we do not prefix values then we can simply put rows.
//
//
//
//            // If the value has no key prefix, i.e. we are not using key hashes then just try to put.
//            if (keepFirst) {
//                // If we are keeping the first then don't allow overwrite.
//                stateDb.put(writeTxn, keyByteBuffer, valueByteBuffer, PutFlags.MDB_NOOVERWRITE);
//            } else {
//                // Put and overwrite any existing key/value.
//                stateDb.put(writeTxn, keyByteBuffer, valueByteBuffer);
//            }
////        } else {
////            // Try to put without overwriting existing values.
////            if (!stateDb.put(writeTxn, keyByteBuffer, valueByteBuffer, PutFlags.MDB_NOOVERWRITE)) {
////                serde.createPrefixPredicate(keyByteBuffer, valueByteBuffer, predicate -> {
////                    if (keepFirst) {
////                        if (!exists(writeTxn, keyByteBuffer, predicate)) {
////                            stateDb.put(writeTxn, keyByteBuffer, valueByteBuffer);
////                        }
////                    } else {
////                        // Delete current value if there is one.
////                        delete(writeTxn, keyByteBuffer, predicate);
////                        // Put new value allowing for duplicate keys as we are only using a hash key.
////                        stateDb.put(writeTxn, keyByteBuffer, valueByteBuffer);
////                    }
////                    return true;
////                });
////            }
////        }
//
//        commitCount++;
//        if (commitCount > 10000) {
//            commit();
//            commitCount = 0;
//        }
//
//        return true;
//    }
//
////    private boolean delete(final Txn<ByteBuffer> txn,
////                           final ByteBuffer keyByteBuffer,
////                           final Predicate<KeyVal<ByteBuffer>> predicate) {
////        final KeyRange<ByteBuffer> keyRange = KeyRange.closed(keyByteBuffer, keyByteBuffer);
////        try (final CursorIterable<ByteBuffer> cursor = stateDb.iterate(txn, keyRange)) {
////            final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
////            while (iterator.hasNext()) {
////                final KeyVal<ByteBuffer> keyVal = iterator.next();
////                if (predicate.test(keyVal)) {
////                    iterator.remove();
////                    return true;
////                }
////            }
////        }
////        return false;
////    }
////
////    private boolean exists(final Txn<ByteBuffer> txn,
////                           final ByteBuffer keyByteBuffer,
////                           final Predicate<KeyVal<ByteBuffer>> predicate) {
////        final KeyRange<ByteBuffer> keyRange = KeyRange.closed(keyByteBuffer, keyByteBuffer);
////        try (final CursorIterable<ByteBuffer> cursor = stateDb.iterate(txn, keyRange)) {
////            final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
////            while (iterator.hasNext()) {
////                final KeyVal<ByteBuffer> keyVal = iterator.next();
////                if (predicate.test(keyVal)) {
////                    return true;
////                }
////            }
////        }
////        return false;
////    }


    Txn<ByteBuffer> getOrCreateWriteTxn() {
        if (writeTxn == null) {
            writeTxn = env.txnWrite();
        }
        return writeTxn;
    }

    void commit() {
        if (writeTxn != null) {
            try {
                writeTxn.commit();
            } finally {
                try {
                    writeTxn.close();
                } finally {
                    writeTxn = null;
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
