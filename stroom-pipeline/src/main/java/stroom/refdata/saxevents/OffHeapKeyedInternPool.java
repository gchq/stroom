package stroom.refdata.saxevents;

import com.google.common.base.Preconditions;
import org.lmdbjava.CursorIterator;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * key (hash|uid) | value
 * ----------------------
 * (123|0)        | 363838
 * (123|1)        | 857489
 * (456|0)        | 263673
 * (789|0)        | 689390
 *
 * @param <V>
 */
class OffHeapKeyedInternPool<V extends KeyedInternPool.AbstractKeyedInternPoolValue>
        implements KeyedInternPool<V> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OffHeapKeyedInternPool.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(OffHeapKeyedInternPool.class);

    private static final int MIN_UNIQUE_ID = 0;
    private static final int MAX_UNIQUE_ID = Integer.MAX_VALUE;

    private final Path dbDir;
    private final String dbName;
    private final Env<ByteBuffer> env;
    private final Dbi<ByteBuffer> db;

    OffHeapKeyedInternPool(final Path dbDir, final String dbName, final long maxSize) {
        this.dbName = dbName;
        try {
            Files.createDirectories(dbDir);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Error creating directory %s",
                    dbDir.toAbsolutePath()), e);
        }
        this.dbDir = dbDir;

        env = Env.<ByteBuffer>create()
                .setMapSize(maxSize)
                .setMaxDbs(1)
                .open(dbDir.toFile());

        db = env.openDbi(dbName, DbiFlags.MDB_CREATE);
    }

    @Override
    synchronized public Key put(final V value) {
        Preconditions.checkNotNull(value);

        final byte[] bValue = value.toBytes();
        final ByteBuffer valueBuffer = ByteBuffer.allocateDirect(bValue.length)
                .put(bValue);

        Key key;
        try (final Txn<ByteBuffer> txn = env.txnWrite()) {
            final KeyRange<ByteBuffer> keyRange = KeyRange.open(
                    buildStartKeyBuffer(value),
                    buildEndKeyBuffer(value));

            AtomicBoolean isValueInMap = new AtomicBoolean(false);
            AtomicInteger valuesCount = new AtomicInteger(0);
            CursorIterator.KeyVal<ByteBuffer> lastKeyValue = null;
            try (CursorIterator<ByteBuffer> cursorIterator = db.iterate(txn, keyRange)) {
                for (final CursorIterator.KeyVal<ByteBuffer> keyVal : cursorIterator.iterable()) {
                    valuesCount.incrementAndGet();
                    if (keyVal.val().equals(valueBuffer)) {
                        isValueInMap.set(true);
                        break;
                    } else {
                        lastKeyValue = keyVal;
                    }
                }
            }

            LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("isValueInMap: {}, valuesCount {}",
                    isValueInMap.get(),
                    valuesCount.get()));

            if (isValueInMap.get()) {
                // value is already in the map, so use its key
                LOGGER.trace("Found value");
                key = Key.fromBytes(lastKeyValue.key());
            } else {
                // value is not in the map so we need to add it
                final int uniqueId;
                if (lastKeyValue != null) {
                    // no existing entry for this hashCode so use first uniqueId
                    uniqueId = MIN_UNIQUE_ID;
                } else {
                    // 1 or more entries share our hashCode (but with different values)
                    // so use the next uniqueId
                    int lastUniqueId = Key.fromBytes(lastKeyValue.key()).getUniqueId();
                    uniqueId = lastUniqueId++;
                }
                LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("Putting entry with uniqueId: {}",
                        uniqueId));
                key = new Key(value.hashCode(), uniqueId);
                ByteBuffer keyBuffer = key.toDirectByteBuffer();
                db.put(txn, keyBuffer, valueBuffer);
            }
        }
        return key;
    }

    @Override
    public void clear() {
        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            db.drop(txn);
        }
    }

    @Override
    public Optional<V> get(final Key key, final Function<ByteBuffer, V> valueMapper) {
        try (final Txn<ByteBuffer> txn = env.txnRead()) {
            ByteBuffer keyBuffer = key.toDirectByteBuffer();
            ByteBuffer valueBuffer = db.get(txn, keyBuffer);
            return Optional.ofNullable(valueBuffer)
                    .map(valueMapper);
        }
    }

    @Override
    public void close() {
        if (env != null) {
            try {
                env.close();
            } catch (RuntimeException e) {
                throw new RuntimeException(
                        LambdaLogger.buildMessage("Error closing LMDB env for db {} {}",
                                dbName, dbDir.toAbsolutePath().toString()), e);
            }
        }
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        return super.equals(obj);
    }

    private ByteBuffer buildStartKeyBuffer(final V value) {
        return buildKeyBuffer(value.hashCode(), MIN_UNIQUE_ID);
    }

    private ByteBuffer buildEndKeyBuffer(final V value) {
        return buildKeyBuffer(value.hashCode(), MAX_UNIQUE_ID);
    }

    private ByteBuffer buildKeyBuffer(final int hashCode, int uniqueId) {
        final ByteBuffer keyBuffer = ByteBuffer.allocateDirect(Integer.BYTES);
        keyBuffer.putInt(hashCode);
        keyBuffer.putInt(uniqueId);
        keyBuffer.flip();
        return keyBuffer;
    }
}
