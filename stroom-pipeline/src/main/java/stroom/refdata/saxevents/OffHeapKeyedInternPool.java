package stroom.refdata.saxevents;

import com.google.common.base.Preconditions;
import org.apache.hadoop.hbase.util.ByteBufferUtils;
import org.lmdbjava.CursorIterator;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
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
    private final long maxSize;
    private final Env<ByteBuffer> env;
    private final Dbi<ByteBuffer> db;
    private final Function<ByteBuffer, V> valueMapper;

    OffHeapKeyedInternPool(final Path dbDir,
                           final String dbName,
                           final long maxSize,
                           final Function<ByteBuffer, V> valueMapper) {
        this.dbName = dbName;
        this.maxSize = maxSize;
        try {
            Files.createDirectories(dbDir);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Error creating directory %s",
                    dbDir.toAbsolutePath()), e);
        }
        this.dbDir = dbDir;
        this.valueMapper = valueMapper;

        env = Env.<ByteBuffer>create()
                .setMapSize(maxSize)
                .setMaxDbs(1)
                .open(dbDir.toFile());

        db = env.openDbi(dbName, DbiFlags.MDB_CREATE);
    }

    /**
     * Used for testing
     *
     * @param key
     * @param value
     */
    synchronized public void forcedPut(final Key key, final V value) {
        LmdbUtils.doInWriteTxn(env, txn -> {
            final ByteBuffer keyBuffer = buildKeyBuffer(key);
            final byte[] bValue = value.toBytes();
            final int valueHashCode = value.hashCode();
            final ByteBuffer valueBuffer = ByteBuffer.allocateDirect(bValue.length);
            valueBuffer
                    .put(bValue)
                    .flip();
            boolean didPutSucceed = db.put(txn, keyBuffer, valueBuffer, PutFlags.MDB_NOOVERWRITE);
            LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("Putting entry with key {}, result: {}", key, didPutSucceed));
            dumpBuffer(valueBuffer, "valueBuffer");
        });
    }

    @Override
    public ValueSupplier<V> intern(final V value) {
        // TODO we may want to call get here with the default key to optimistically get
        // the value outside of a write txn and synchronized block, however if we get a value
        // we will have to do an equality check on the value. If that fails we would then need
        // to enter the write txn/synchronised block and iterate over the values for that hashcode.
        // The benefits will depend on the degree of reuse of the values. Low reuse means we don't bother,
        // but high re-use (e.g. if the TTL is long) means we will benefit from an additional get.
        Key key = put(value);
        return makeValueSupplier(key);
    }

    synchronized Key put(final V value) {
        LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage("put called for value {}", value.toString()));
        Preconditions.checkNotNull(value);

        final byte[] bValue = value.toBytes();
        final ByteBuffer valueBuffer = ByteBuffer.allocateDirect(bValue.length)
                .put(bValue);
        valueBuffer.flip();

        Key key;
        try (final Txn<ByteBuffer> txn = env.txnWrite()) {

            final KeyRange<ByteBuffer> keyRange = buildAllIdsForSingleHashValueKeyRange(value);

            dumpContentsInRange(txn, keyRange);

            // Use atomics so they can be mutated and then used in lambdas
            final AtomicBoolean isValueInMap = new AtomicBoolean(false);
            final AtomicInteger valuesCount = new AtomicInteger(0);
            CursorIterator.KeyVal<ByteBuffer> lastKeyValue = null;

            try (CursorIterator<ByteBuffer> cursorIterator = db.iterate(txn, keyRange)) {
                for (final CursorIterator.KeyVal<ByteBuffer> keyVal : cursorIterator.iterable()) {
                    valuesCount.incrementAndGet();
                    LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("Found entry {} with key {}",
                            valuesCount.get(),
                            byteBufferToHex(keyVal.key())));

                    ByteBuffer valueFromDbBuf = keyVal.val();

                    LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("Our value {}, db value {}",
                            byteBufferToHex(valueBuffer),
                            byteBufferToHex(valueFromDbBuf)));

                    int res = ByteBufferUtils.compareTo(
                            valueBuffer, valueBuffer.position(), valueBuffer.remaining(),
                            valueFromDbBuf, valueFromDbBuf.position(), valueFromDbBuf.remaining());
                    if (res == 0) {
//                        if (keyVal.val().equals(valueBuffer)) {
                        isValueInMap.set(true);
                        LAMBDA_LOGGER.trace(() -> "Values are equal breaking out");
                        break;
                    } else {
                        LAMBDA_LOGGER.trace(() -> "Values are not equal");
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
                if (lastKeyValue == null || lastKeyValue.key() == null) {
                    // no existing entry for this valueHashCode so use first uniqueId
                    uniqueId = MIN_UNIQUE_ID;
                } else {
                    // 1 or more entries share our valueHashCode (but with different values)
                    // so use the next uniqueId
                    int lastUniqueId = Key.fromBytes(lastKeyValue.key()).getUniqueId();
                    uniqueId = ++lastUniqueId;
                }
                key = new Key(value.hashCode(), uniqueId);
                ByteBuffer keyBuffer = buildKeyBuffer(key);
                boolean didPutSucceed = db.put(txn, keyBuffer, valueBuffer, PutFlags.MDB_NOOVERWRITE);
                LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("Putting entry with key {}, result: {}", key, didPutSucceed));
                dumpBuffer(valueBuffer, "valueBuffer");
                txn.commit();
            }
        }
        return key;
    }

    private KeyRange<ByteBuffer> buildAllIdsForSingleHashValueKeyRange(final V value) {
        ByteBuffer startKey = buildStartKeyBuffer(value);
        ByteBuffer endKey = buildEndKeyBuffer(value);
        return KeyRange.open(startKey, endKey);
    }

//    private Optional<Key> doOptimisticPut(final Txn<ByteBuffer> txn,
//                                          final Key key,
//                                          final ByteBuffer valueBuffer) {
//        Key key = new Key(valueHashCode, MIN_UNIQUE_ID);
//        try {
//            boolean result = putValue(txn, key, valueBuffer);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//    }

    private boolean putValue(final Txn<ByteBuffer> txn, final ByteBuffer keyBuffer, final ByteBuffer valueBuffer) {
        return db.put(txn, keyBuffer, valueBuffer, PutFlags.MDB_NOOVERWRITE);
    }

    private boolean putValue(final Txn<ByteBuffer> txn, final Key key, final ByteBuffer valueBuffer) {
        final ByteBuffer keyBuffer = buildKeyBuffer(key.getValueHashCode(), key.getUniqueId());
        LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("Putting entry with key {}", key));
        return putValue(txn, key, valueBuffer);
    }

    Optional<V> get(final Key key) {
        return LmdbUtils.getInReadTxn(env, txn -> {
            ByteBuffer keyBuffer = key.toDirectByteBuffer();
            ByteBuffer valueBuffer = db.get(txn, keyBuffer);
            return Optional.ofNullable(valueBuffer)
                    .map(valueMapper);
        });
    }

    @Override
    public void clear() {
        LmdbUtils.doInWriteTxn(env, db::drop);
    }

    @Override
    public long size() {
        return LmdbUtils.getInReadTxn(env, txn ->
            db.stat(txn).entries
        );
    }

    @Override
    public Map<String, String> getInfo() {
        return LmdbUtils.getDbInfo(env, db);
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
    public String toString() {
        return "OffHeapKeyedInternPool{" +
                "dbDir=" + dbDir +
                ", dbName='" + dbName + '\'' +
                ", maxSize=" + maxSize +
                '}';
    }

    void dumpContents() {
        StringBuilder stringBuilder = new StringBuilder();
        try (final Txn<ByteBuffer> txn = env.txnRead()) {
            try (CursorIterator<ByteBuffer> cursorIterator = db.iterate(txn)) {
                for (final CursorIterator.KeyVal<ByteBuffer> keyVal : cursorIterator.iterable()) {
                    Key key = Key.fromBytes(keyVal.key());
                    V value = valueMapper.apply(keyVal.val());
                    stringBuilder.append(LambdaLogger.buildMessage("\n   key: {}, value: {}", key, value));
                }
            }
        }
        LAMBDA_LOGGER.info(() -> LambdaLogger.buildMessage("Dumping contents: {}", stringBuilder.toString()));
    }

    void dumpContentsInRange(final Key startKey, final Key endKey) {
        try (final Txn<ByteBuffer> txn = env.txnRead()) {
            ByteBuffer startKeyBuf = buildKeyBuffer(startKey);
            ByteBuffer endKeyBuf = buildKeyBuffer(endKey);
            final KeyRange<ByteBuffer> keyRange = KeyRange.closed(startKeyBuf, endKeyBuf);
            dumpContentsInRange(txn, keyRange);
        }
    }

    void dumpContentsInRange(Txn<ByteBuffer> txn, final KeyRange<ByteBuffer> keyRange) {


        StringBuilder stringBuilder = new StringBuilder();
        try (CursorIterator<ByteBuffer> cursorIterator = db.iterate(txn, keyRange)) {
            for (final CursorIterator.KeyVal<ByteBuffer> keyVal : cursorIterator.iterable()) {
                Key key = Key.fromBytes(keyVal.key());
                V value = valueMapper.apply(keyVal.val());
                stringBuilder.append(LambdaLogger.buildMessage("\n   key: {}, value: {}", key, value));
            }
        }
        String contents = stringBuilder.toString();
        LAMBDA_LOGGER.info(() -> LambdaLogger.buildMessage("Dumping contents in range: startKey {}, endKey {}{}",
                byteBufferToHex(keyRange.getStart()),
                byteBufferToHex(keyRange.getStop()),
                contents));
    }

    static void dumpBuffer(final ByteBuffer byteBuffer, final String description) {
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = byteBuffer.position(); i < byteBuffer.limit(); i++) {
            byte b = byteBuffer.get(i);
            stringBuilder.append(Key.byteToHex(b));
            stringBuilder.append(" ");
        }
        LOGGER.info("{} byteBuffer: {}", description, stringBuilder.toString());
    }

    static String byteBufferToHex(final ByteBuffer byteBuffer) {
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = byteBuffer.position(); i < byteBuffer.limit(); i++) {
            byte b = byteBuffer.get(i);
            stringBuilder.append(Key.byteToHex(b));
            stringBuilder.append(" ");
        }
        return stringBuilder.toString();
    }

    private ByteBuffer buildStartKeyBuffer(final V value) {
        return buildKeyBuffer(value.hashCode(), MIN_UNIQUE_ID);
    }

    private ByteBuffer buildEndKeyBuffer(final V value) {
        return buildKeyBuffer(value.hashCode(), MAX_UNIQUE_ID);
    }

    private ByteBuffer buildKeyBuffer(final int valueHashCode, int uniqueId) {
        final ByteBuffer keyBuffer = ByteBuffer.allocateDirect(env.getMaxKeySize());
//                .order(ByteOrder.nativeOrder());
        Key.putContent(keyBuffer, valueHashCode, uniqueId);
        keyBuffer.flip();
        return keyBuffer;
    }

    private ByteBuffer buildKeyBuffer(final int valueHashCode) {
        return buildKeyBuffer(valueHashCode, MIN_UNIQUE_ID);
    }

    private ByteBuffer buildKeyBuffer(final Key key) {
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(env.getMaxKeySize());
//                .order(ByteOrder.nativeOrder());
        Key.putContent(byteBuffer, key.getValueHashCode(), key.getUniqueId());
        byteBuffer.flip();
        return byteBuffer;
    }

    private ValueSupplier<V> makeValueSupplier(final Key key) {
        return new ValueSupplier<>(this, key, () -> get(key));
    }
}
