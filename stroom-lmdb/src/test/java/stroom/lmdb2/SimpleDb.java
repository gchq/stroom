package stroom.lmdb2;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.util.logging.LogUtil;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.KeyRange;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A simple String, String named DB.
 */
public class SimpleDb {

    private final LmdbEnv2 lmdbEnv2;
    private final Dbi<ByteBuffer> dbi;
    private final String dbName;
    private final ByteBufferFactory byteBufferFactory;

    public SimpleDb(final LmdbEnv2 lmdbEnv2,
                    final String dbName,
                    final ByteBufferFactory byteBufferFactory) {
        this.lmdbEnv2 = lmdbEnv2;
        this.dbName = dbName;
        this.byteBufferFactory = byteBufferFactory;
        this.dbi = lmdbEnv2.openDbi(dbName);
    }

    public boolean put(final Txn<ByteBuffer> txn,
                       final String key,
                       final String val,
                       final PutFlags... putFlags) {
        final byte[] keyBytes = Objects.requireNonNullElseGet(
                key.getBytes(StandardCharsets.UTF_8),
                () -> new byte[0]);

        final byte[] valBytes = Objects.requireNonNullElseGet(
                val.getBytes(StandardCharsets.UTF_8),
                () -> new byte[0]);

        final ByteBuffer keyBuf = byteBufferFactory.acquire(keyBytes.length);
        final ByteBuffer valBuf = byteBufferFactory.acquire(valBytes.length);
        try {
            keyBuf.clear();
            keyBuf.put(key.getBytes(StandardCharsets.UTF_8));
            keyBuf.flip();
            valBuf.clear();
            writeBuffer(val, valBuf);
            valBuf.flip();
            return dbi.put(txn, keyBuf, valBuf, putFlags);
        } finally {
            byteBufferFactory.release(keyBuf);
            byteBufferFactory.release(valBuf);
        }
    }

    private void writeBuffer(final String value, final ByteBuffer byteBuffer) {
        if (value != null) {
            byteBuffer.put(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    public Optional<String> get(final Txn<ByteBuffer> txn,
                                final String key) {
        final byte[] keyBytes = Objects.requireNonNullElseGet(
                key.getBytes(StandardCharsets.UTF_8),
                () -> new byte[0]);
        final ByteBuffer keyBuf = byteBufferFactory.acquire(keyBytes.length);
        try {
            keyBuf.clear();
            keyBuf.put(key.getBytes(StandardCharsets.UTF_8));
            keyBuf.flip();

            return Optional.ofNullable(dbi.get(txn, keyBuf))
                    .map(this::readBuffer);
        } finally {
            byteBufferFactory.release(keyBuf);
        }
    }

    private String readBuffer(final ByteBuffer byteBuffer) {
        if (byteBuffer == null) {
            return null;
        } else {
            return StandardCharsets.UTF_8.decode(byteBuffer).toString();
        }
    }

    public LmdbEnv2 getLmdbEnv2() {
        return lmdbEnv2;
    }

    public Dbi<ByteBuffer> getDbi() {
        return dbi;
    }

    public String getDbName() {
        return dbName;
    }

    public ByteBufferFactory getByteBufferFactory() {
        return byteBufferFactory;
    }

    /**
     * Dumps all entries in the database to a single logger entry with one line per database entry.
     * This could potentially return thousands of rows so is only intended for small scale use in
     * testing. Entries are returned in the order they are held in the DB, e.g. a-z (unless the DB
     * is configured with reverse keys). The keys/values are de-serialised and a toString() is applied
     * to the resulting objects.
     */
    public void logDatabaseContents(Consumer<String> logEntryConsumer) {

        lmdbEnv2.read(txn -> {
            final StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(LogUtil.message("Dumping {} entries for database [{}]",
                    getEntryCount(txn), new String(dbi.getName())));

            // loop over all DB entries
            try (CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(txn, KeyRange.all())) {
                for (final KeyVal<ByteBuffer> keyVal : cursorIterable) {
                    stringBuilder.append(LogUtil.message("\n  key: [{}] - value [{}]",
                            readBuffer(keyVal.key()),
                            readBuffer(keyVal.val())));
                }
            }
            logEntryConsumer.accept(stringBuilder.toString());
        });
    }

    public long getEntryCount(final Txn<ByteBuffer> txn) {

        return dbi.stat(txn).entries;
    }

    @Override
    public String toString() {
        return "TestDb{" +
                "lmdbEnv2=" + lmdbEnv2 +
                ", dbName='" + dbName + '\'' +
                '}';
    }
}
