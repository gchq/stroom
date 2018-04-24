package stroom.refdata.saxevents;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import org.apache.hadoop.hbase.util.ByteBufferUtils;
import org.lmdbjava.CursorIterator;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Stat;
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

    @Override
    synchronized public Key put(final V value) {
        LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage("put called for value {}", value.toString()));
        Preconditions.checkNotNull(value);

        final byte[] bValue = value.toBytes();
        final int valueHashCode = value.hashCode();
        final ByteBuffer valueBuffer = ByteBuffer.allocateDirect(bValue.length);
        valueBuffer
                .put(bValue)
                .flip();

        Key key;
        try (final Txn<ByteBuffer> txn = env.txnWrite()) {

            ByteBuffer startKey = buildStartKeyBuffer(value);
            ByteBuffer endKey = buildEndKeyBuffer(value);
            final KeyRange<ByteBuffer> keyRange = KeyRange.open(startKey, endKey);

            dumpContentsInRange(txn, keyRange);

            AtomicBoolean isValueInMap = new AtomicBoolean(false);
            AtomicInteger valuesCount = new AtomicInteger(0);
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
                dumpBuffer(valueBuffer);
                txn.commit();
            }
        }
        return key;
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

    @Override
    public Optional<V> get(final Key key) {
        try (final Txn<ByteBuffer> txn = env.txnRead()) {
            ByteBuffer keyBuffer = key.toDirectByteBuffer();
            ByteBuffer valueBuffer = db.get(txn, keyBuffer);
            return Optional.ofNullable(valueBuffer)
                    .map(valueMapper);
        }
    }

    @Override
    public void clear() {
        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            db.drop(txn);
        }
    }

    @Override
    public long size() {
        return env.stat().entries;
    }

    @Override
    public Map<String, String> getInfo() {
        Stat stat = env.stat();
        return ImmutableMap.<String, String>builder()
                .put("pageSize", Integer.toString(stat.pageSize))
                .put("branchPages", Long.toString(stat.branchPages))
                .put("depth", Integer.toString(stat.depth))
                .put("entries", Long.toString(stat.entries))
                .put("leafPages", Long.toString(stat.leafPages))
                .put("overFlowPages", Long.toString(stat.overflowPages))
                .build();
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

    @Override
    public String toString() {
        return "OffHeapKeyedInternPool{" +
                "dbDir=" + dbDir +
                ", dbName='" + dbName + '\'' +
                ", maxSize=" + maxSize +
                '}';
    }


    void dumpContents() {
        try (final Txn<ByteBuffer> txn = env.txnRead()) {

            try (CursorIterator<ByteBuffer> cursorIterator = db.iterate(txn)) {
                for (final CursorIterator.KeyVal<ByteBuffer> keyVal : cursorIterator.iterable()) {
                    Key key = Key.fromBytes(keyVal.key());
                    V value = valueMapper.apply(keyVal.val());
                    LOGGER.info("key: {}, value: {}", key, value);
                }
            }
        }
    }

    void dumpContentsInRange(Txn<ByteBuffer> txn, final KeyRange<ByteBuffer> keyRange) {


        StringBuilder stringBuilder = new StringBuilder();
        try (CursorIterator<ByteBuffer> cursorIterator = db.iterate(txn, keyRange)) {
            for (final CursorIterator.KeyVal<ByteBuffer> keyVal : cursorIterator.iterable()) {
                Key key = Key.fromBytes(keyVal.key());
                V value = valueMapper.apply(keyVal.val());
                stringBuilder.append(LambdaLogger.buildMessage("\n  key: {}, value: {}", key, value));
            }
        }
        String contents = stringBuilder.toString();
        LAMBDA_LOGGER.info(() -> LambdaLogger.buildMessage("Dumping contents in range: startKey {}, endKey {}{}",
                byteBufferToHex(keyRange.getStart()),
                byteBufferToHex(keyRange.getStop()),
                contents));
    }

    static void dumpBuffer(final ByteBuffer byteBuffer) {
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = byteBuffer.position(); i < byteBuffer.limit(); i++) {
            byte b = byteBuffer.get(i);
            stringBuilder.append(Key.byteToHex(b));
            stringBuilder.append(" ");
        }
        LOGGER.info("byteBuffer: {}", stringBuilder.toString());
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
}
