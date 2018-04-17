package stroom.refdata.lmdb;

import com.google.common.base.Preconditions;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@NotThreadSafe
public class LmdbKeyValueStore implements KeyValueStore {

    public static final int VALUE_BUFFER_SIZE = 700;

    private final Path dir;
    private final String dbName;
    private final Env<ByteBuffer> env;
    private final Dbi<ByteBuffer> db;
    private Txn<ByteBuffer> readTxn = null;

    public LmdbKeyValueStore(final String dbName,
                             final Path dir) {
        this.dbName = dbName;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Error creating directory %s", dir.toAbsolutePath()), e);
        }
        this.dir = dir;

        env = Env.<ByteBuffer>create()
                .setMapSize(1024 * 1024 * 1024)
                .setMaxDbs(1)
                .open(dir.toFile());

        db = env.openDbi(dbName, DbiFlags.MDB_CREATE);
    }

    @Override
    public void put(final String key, final String value) {
        Preconditions.checkNotNull(key);
        Preconditions.checkNotNull(value);

        final ByteBuffer keyBuf = stringToBuffer(key, env.getMaxKeySize());
        final ByteBuffer valueBuf = stringToBuffer(value, VALUE_BUFFER_SIZE);

        db.put(keyBuf, valueBuf);
    }

    @Override
    public void putBatch(final List<Map.Entry<String, String>> entries) {
        Preconditions.checkNotNull(entries);
        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            entries.forEach(entry -> {
                String key = entry.getKey();
                String value = entry.getValue();

                Preconditions.checkNotNull(key);
                Preconditions.checkNotNull(value);

                final ByteBuffer keyBuf = stringToBuffer(key, env.getMaxKeySize());
                final ByteBuffer valueBuf = stringToBuffer(value, VALUE_BUFFER_SIZE);

                db.put(txn, keyBuf, valueBuf);
            });
        }
    }

    @Override
    public Optional<String> get(final String key) {
        Preconditions.checkNotNull(key);
        ByteBuffer keyBuf = stringToBuffer(key, env.getMaxKeySize());

        try (Txn<ByteBuffer> txn = env.txnRead()) {
            final ByteBuffer valBuffer = db.get(txn, keyBuf);

            return Optional
                    .ofNullable(valBuffer)
                    .map(LmdbKeyValueStore::byteBufferToString);
        }
    }

    @Override
    public Optional<String> getWithTxn(String key) {
        Preconditions.checkNotNull(key);
        ByteBuffer keyBuf = stringToBuffer(key, env.getMaxKeySize());

            final ByteBuffer valBuffer = db.get(getReadTxn(), keyBuf);

            return Optional
                    .ofNullable(valBuffer)
                    .map(LmdbKeyValueStore::byteBufferToString);
    }

    private Txn<ByteBuffer> getReadTxn() {
        if (readTxn == null) {
            readTxn =env.txnRead();
        }
        return readTxn;
    }

    @Override
    public void clear() {
        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            db.drop(txn);
        }
    }

    @Override
    public void close() throws IOException {
        if (readTxn != null) {
            readTxn.close();
        }

        if (env != null) {
            try {
                env.close();
            } catch (Exception e) {
                throw new RuntimeException(String.format("Error closing LMDB env"), e);
            }
        }
    }

    public static ByteBuffer stringToBuffer(final String str, final int size) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(size);
        buffer.put(str.getBytes(StandardCharsets.UTF_8)).flip();
        return buffer;
    }

    public static String byteBufferToString(final ByteBuffer byteBuffer) {
        Preconditions.checkNotNull(byteBuffer);
        return StandardCharsets.UTF_8.decode(byteBuffer).toString();
    }
}
