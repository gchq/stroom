package stroom.planb.impl.io;

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
    private Txn<ByteBuffer> writeTxn;
    private int commitCount = 0;
    private int hashClashes = 0;
    private final DBWriter dbWriter;

    public AbstractLmdbWriter(final Path path,
                              final ByteBufferFactory byteBufferFactory,
                              final Serde<K, V> serde,
                              final boolean overwrite) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(path, true);
        this.byteBufferFactory = byteBufferFactory;
        this.serde = serde;

        LOGGER.info(() -> "Creating: " + path);

        final Env.Builder<ByteBuffer> builder = Env.create()
                .setMapSize(LmdbConfig.DEFAULT_MAX_STORE_SIZE.getBytes())
                .setMaxDbs(1)
                .setMaxReaders(1);

        env = builder.open(lmdbEnvDir.getEnvDir().toFile(), EnvFlags.MDB_NOTLS);
        dbi = env.openDbi(NAME, getDbiFlags());

        // If we do not prefix values then we can simply put rows.
        if (!serde.hasPrefix()) {
            // If the value has no key prefix, i.e. we are not using key hashes then just try to put.
            if (overwrite) {
                // Put and overwrite any existing key/value.
                dbWriter = dbi::put;
            } else {
                // Put but do not overwrite any existing key/value.
                dbWriter = (writeTxn, keyByteBuffer, valueByteBuffer) ->
                        dbi.put(writeTxn, keyByteBuffer, valueByteBuffer, PutFlags.MDB_NOOVERWRITE);
            }
        } else {
            if (overwrite) {
                dbWriter = (writeTxn, keyByteBuffer, valueByteBuffer) -> {
                    // First try to put without overwriting existing values.
                    if (!dbi.put(writeTxn, keyByteBuffer, valueByteBuffer, PutFlags.MDB_NOOVERWRITE)) {
                        serde.createPrefixPredicate(keyByteBuffer, valueByteBuffer, predicate -> {
                            // Delete current value if there is one.
                            if (!delete(writeTxn, keyByteBuffer, predicate)) {
                                // We must have had a hash clash here because we didn't find a row for the key even
                                // though the db contains the key hash.
                                hashClashes++;
                            }

                            // Put new value allowing for duplicate keys as we are only using a hash key.
                            dbi.put(writeTxn, keyByteBuffer, valueByteBuffer);
                            return true;
                        });
                    }
                };
            } else {
                dbWriter = (writeTxn, keyByteBuffer, valueByteBuffer) -> {
                    // First try to put without overwriting existing values.
                    if (!dbi.put(writeTxn, keyByteBuffer, valueByteBuffer, PutFlags.MDB_NOOVERWRITE)) {
                        serde.createPrefixPredicate(keyByteBuffer, valueByteBuffer, predicate -> {
                            if (!exists(writeTxn, keyByteBuffer, predicate)) {
                                // We must have had a hash clash here because we didn't find a row for the key even
                                // though the db contains the key hash.
                                hashClashes++;

                                // Put the value as another row for the same key hash as we didn't find a row for the
                                // full key value.
                                dbi.put(writeTxn, keyByteBuffer, valueByteBuffer);
                            }
                            return true;
                        });
                    }
                };
            }
        }
    }

    DbiFlags[] getDbiFlags() {
        if (serde.hasPrefix()) {
            return new DbiFlags[]{DbiFlags.MDB_CREATE, DbiFlags.MDB_DUPSORT};
        }
        return new DbiFlags[]{DbiFlags.MDB_CREATE};
    }

    public void merge(final Path source) {
        final Env.Builder<ByteBuffer> builder = Env.create()
                .setMaxDbs(1)
                .setMaxReaders(1);
        try (final Env<ByteBuffer> sourceEnv = builder.open(source.toFile(),
                EnvFlags.MDB_NOTLS,
                EnvFlags.MDB_NOLOCK,
                EnvFlags.MDB_RDONLY_ENV)) {
            final Dbi<ByteBuffer> sourceDbi = sourceEnv.openDbi(NAME);
            try (final Txn<ByteBuffer> readTxn = sourceEnv.txnRead()) {
                try (final CursorIterable<ByteBuffer> cursorIterable = sourceDbi.iterate(readTxn)) {
                    for (final KeyVal<ByteBuffer> keyVal : cursorIterable) {
                        insert(keyVal.key(), keyVal.val());
                    }
                }
            }
        }
    }

    public boolean insert(final KV<K, V> kv) {
        return insert(kv.key(), kv.value());
    }

    public boolean insert(final K key, final V value) {
        return serde.createKeyByteBuffer(key, keyByteBuffer ->
                serde.createValueByteBuffer(key, value, valueByteBuffer ->
                        insert(keyByteBuffer, valueByteBuffer)));
    }

    public boolean insert(final ByteBuffer keyByteBuffer,
                          final ByteBuffer valueByteBuffer) {
        final Txn<ByteBuffer> writeTxn = getOrCreateWriteTxn();
        dbWriter.write(writeTxn, keyByteBuffer, valueByteBuffer);

        commitCount++;
        if (commitCount > 10000) {
            commit();
        }

        return true;
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
            for (final KeyVal<ByteBuffer> keyVal : cursor) {
                if (predicate.test(keyVal)) {
                    return true;
                }
            }
        }
        return false;
    }


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

        commitCount = 0;

        if (hashClashes > 0) {
            // We prob don't want to warn but will keep for now until we know how big the issue is.
            LOGGER.warn(() -> "We had " + hashClashes + " hash clashes since last commit");
            hashClashes = 0;
        }
    }

    @Override
    public void close() {
        commit();
        env.close();
    }

    private interface DBWriter {

        void write(Txn<ByteBuffer> writeTxn,
                   ByteBuffer keyByteBuffer,
                   ByteBuffer valueByteBuffer);
    }
}
