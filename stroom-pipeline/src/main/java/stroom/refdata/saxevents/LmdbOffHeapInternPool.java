/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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
import stroom.pool.InternPool;
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
import java.util.function.Consumer;
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
class LmdbOffHeapInternPool<V extends AbstractPoolValue> implements OffHeapInternPool<V> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LmdbOffHeapInternPool.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(LmdbOffHeapInternPool.class);

    private static final String INTERN_POOL_DB_NAME = "InternPool";

    private final Path dbDir;
    private final String dbName;
    private final long maxSize;
    private final Env<ByteBuffer> env;
    private final Dbi<ByteBuffer> db;
    private final Function<ByteBuffer, V> valueMapper;
    private final Function<ByteBuffer, ByteBuffer> byteBufferMapper;

    // An intern pool to ensure we only have one instance of a ValueProxy for the same
    // valueHashCode, uniqueId & LmdbOffHeapInternPool instance
    private final InternPool<ValueProxy<V>> valueSupplierInternPool = new InternPool<>();

    LmdbOffHeapInternPool(final Path dbDir,
                          final long maxSize,
                          final Function<ByteBuffer, V> valueMapper) {
        this(dbDir, maxSize, valueMapper, Function.identity());
    }

    /**
     * @param dbDir The directory the LMDB environment will be created in, it must already exist
     * @param maxSize The max size in bytes of the environment
     * @param valueMapper The mapping function to use to map from the byteBuffer value in the DB
     *                    to an instance of V
     * @param byteBufferMapper A mapping function to change the view of the ByteBuffer returned by the
     *                         database, e.g. to map to a sub set of the original ByteBuffer. This
     *                         function will be called in the mapValue and consumeValue methods
     */
    LmdbOffHeapInternPool(final Path dbDir,
                          final long maxSize,
                          final Function<ByteBuffer, V> valueMapper,
                          final Function<ByteBuffer, ByteBuffer> byteBufferMapper) {
        this.dbName = INTERN_POOL_DB_NAME;
        this.maxSize = maxSize;
        try {
            Files.createDirectories(dbDir);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Error creating directory %s",
                    dbDir.toAbsolutePath()), e);
        }
        this.dbDir = dbDir;
        this.valueMapper = valueMapper;
        this.byteBufferMapper = byteBufferMapper;

        LOGGER.debug("Creating LMDB environment with maxSize: {}, dbDir {}", maxSize, dbDir.toAbsolutePath().toString());
        env = Env.<ByteBuffer>create()
                .setMapSize(maxSize)
                .setMaxDbs(1)
                .open(dbDir.toFile());

        LOGGER.debug("Opening LMDB database with name: {}", dbName);
        db = env.openDbi(dbName, DbiFlags.MDB_CREATE);
    }

    /**
     * Used for testing
     *
     * @param key
     * @param value
     */
    synchronized public void forcedPut(final Key key, final V value) {
        LOGGER.debug("forcedPut called for key {}, value: {}", key, value);
        LmdbUtils.doWithWriteTxn(env, txn -> {
            final ByteBuffer keyBuffer = buildKeyBuffer(key);
            final byte[] bValue = value.getValueBytes();
            final int valueHashCode = value.hashCode();
            final ByteBuffer valueBuffer = ByteBuffer.allocateDirect(bValue.length);
            valueBuffer
                    .put(bValue)
                    .flip();
            boolean didPutSucceed = db.put(txn, keyBuffer, valueBuffer, PutFlags.MDB_NOOVERWRITE);
            LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("Putting entry with key {}, result: {}", key, didPutSucceed));
            LmdbUtils.dumpBuffer(valueBuffer, "valueBuffer");
        });
    }

    @Override
    public ValueProxy<V> intern(final V value) {
        LOGGER.debug("intern called for value: {}", value);
        // TODO we may want to call get here with the default key to optimistically get
        // the value outside of a write txn and synchronized block, however if we get a value
        // we will have to do an equality check on the value. If that fails we would then need
        // to enter the write txn/synchronised block and iterate over the values for that hashcode.
        // The benefits will depend on the degree of reuse of the values. Low reuse means we don't bother,
        // but high re-use (e.g. if the TTL is long) means we will benefit from an additional get.
        Key key = put(value);
        ValueProxy<V> valueProxy = new ValueProxy<>(this, key, value.getClass());

        //ensure we only have one instance of this logical valueProxy
        //TODO using this intern pool incurs synchronisation, may be cheaper to accept
        // multiple instances for the same logical valueProxy
        valueProxy = valueSupplierInternPool.intern(valueProxy);

        return valueProxy;

    }

    synchronized Key put(final V value) {
        LOGGER.debug("put called for value: {}", value);
        Preconditions.checkNotNull(value);

        final byte[] bValue = value.getValueBytes();
        final ByteBuffer valueBuffer = ByteBuffer.allocateDirect(bValue.length)
                .put(bValue);
        valueBuffer.flip();

        final Key key;
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
                            LmdbUtils.byteBufferToHex(keyVal.key())));

                    ByteBuffer valueFromDbBuf = keyVal.val();

                    LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("Our value {}, db value {}",
                            LmdbUtils.byteBufferToHex(valueBuffer),
                            LmdbUtils.byteBufferToHex(valueFromDbBuf)));

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
                key = Key.fromByteBuffer(lastKeyValue.key());
            } else {
                // value is not in the map so we need to add it
                final int uniqueId;
                if (lastKeyValue == null || lastKeyValue.key() == null) {
                    // no existing entry for this valueHashCode so use first uniqueId
                    key = Key.lowestKey(value.hashCode());
                } else {
                    // 1 or more entries share our valueHashCode (but with different values)
                    // so use the next uniqueId
                    key = Key.fromByteBuffer(lastKeyValue.key())
                            .nextKey();

                }
                final ByteBuffer keyBuffer = buildKeyBuffer(key);
                boolean didPutSucceed = db.put(txn, keyBuffer, valueBuffer, PutFlags.MDB_NOOVERWRITE);
                LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("Putting entry with key {}, result: {}", key, didPutSucceed));
                LmdbUtils.dumpBuffer(valueBuffer, "valueBuffer");
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

//    private boolean putValue(final Txn<ByteBuffer> txn, final ByteBuffer keyBuffer, final ByteBuffer valueBuffer) {
//        return db.put(txn, keyBuffer, valueBuffer, PutFlags.MDB_NOOVERWRITE);
//    }
//
//    private boolean putValue(final Txn<ByteBuffer> txn, final Key key, final ByteBuffer valueBuffer) {
//        final ByteBuffer keyBuffer = buildKeyBuffer(key.getValueHashCode(), key.getUniqueId());
//        LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("Putting entry with key {}", key));
//        return putValue(txn, key, valueBuffer);
//    }

    @Override
    public Optional<V> get(final ValueProxy<V> valueProxy) {
        LOGGER.debug("get called for valueProxy: {}", valueProxy);
        return LmdbUtils.getWithReadTxn(env, txn -> {
            final ByteBuffer keyBuffer = buildKeyBuffer(valueProxy.getKey());
            final ByteBuffer valueBuffer = db.get(txn, keyBuffer).asReadOnlyBuffer();
            return Optional.ofNullable(valueBuffer)
                    .map(valueMapper);
        });
    }

    @Override
    public <T> Optional<T> mapValue(final ValueProxy<V> valueProxy,
                                    final Function<ByteBuffer, T> valueMapper) {
        LOGGER.debug("gepValue called for valueProxy: {}", valueProxy);
        return LmdbUtils.getWithReadTxn(env, txn -> {
            // make the buffers readonly to prevent the mapper from mutating them
            final ByteBuffer keyBuffer = buildKeyBuffer(valueProxy.getKey()).asReadOnlyBuffer();
            final ByteBuffer valueBuffer = db.get(txn, keyBuffer).asReadOnlyBuffer();
            return Optional.ofNullable(valueBuffer)
                    .map(byteBufferMapper)
                    .map(valueMapper);
        });
    }

    @Override
    public void consumeValue(final ValueProxy<V> valueProxy,
                             final Consumer<ByteBuffer> valueConsumer) {
        LOGGER.debug("consumeValue called for valueProxy: {}", valueProxy);
        LmdbUtils.doWithReadTxn(env, txn -> {
            final ByteBuffer keyBuffer = buildKeyBuffer(valueProxy.getKey()).asReadOnlyBuffer();
            final ByteBuffer valueBuffer = db.get(txn, keyBuffer).asReadOnlyBuffer();
            final ByteBuffer mappedValueBuffer = byteBufferMapper.apply(valueBuffer);
            valueConsumer.accept(mappedValueBuffer);
        });
    }

    @Override
    public void clear() {
        LOGGER.debug("clear called");
        LmdbUtils.doWithWriteTxn(env, db::drop);
    }

    @Override
    public long size() {
        LOGGER.debug("size called");
        return LmdbUtils.getWithReadTxn(env, txn ->
            db.stat(txn).entries
        );
    }

    @Override
    public Map<String, String> getInfo() {
        return LmdbUtils.getDbInfo(env, db);
    }

    @Override
    public void close() {
        LOGGER.debug("Closing LMDB environment with dbDir: {}", maxSize, dbDir.toAbsolutePath().toString());
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
        return "LmdbOffHeapInternPool{" +
                "dbDir=" + dbDir +
                ", dbName='" + dbName + '\'' +
                ", maxSize=" + maxSize +
                '}';
    }

    /**
     * Only intended for use in tests as the DB could be massive and thus produce a LOT of logging
     */
    void dumpContents() {
        StringBuilder stringBuilder = new StringBuilder();
        try (final Txn<ByteBuffer> txn = env.txnRead()) {
            try (CursorIterator<ByteBuffer> cursorIterator = db.iterate(txn)) {
                for (final CursorIterator.KeyVal<ByteBuffer> keyVal : cursorIterator.iterable()) {
                    Key key = Key.fromByteBuffer(keyVal.key());
                    V value = valueMapper.apply(keyVal.val());
                    stringBuilder.append(LambdaLogger.buildMessage("\n   key: {}, value: {}", key, value));
                }
            }
        }
        LAMBDA_LOGGER.info(() -> LambdaLogger.buildMessage("Dumping contents: {}", stringBuilder.toString()));
    }

    /**
     * Only intended for use in tests as the DB could be massive and thus produce a LOT of logging
     */
    void dumpContentsInRange(final Key startKey, final Key endKey) {
        try (final Txn<ByteBuffer> txn = env.txnRead()) {
            ByteBuffer startKeyBuf = buildKeyBuffer(startKey);
            ByteBuffer endKeyBuf = buildKeyBuffer(endKey);
            final KeyRange<ByteBuffer> keyRange = KeyRange.closed(startKeyBuf, endKeyBuf);
            dumpContentsInRange(txn, keyRange);
        }
    }

    /**
     * Only intended for use in tests as the DB could be massive and thus produce a LOT of logging
     */
    void dumpContentsInRange(Txn<ByteBuffer> txn, final KeyRange<ByteBuffer> keyRange) {


        StringBuilder stringBuilder = new StringBuilder();
        try (CursorIterator<ByteBuffer> cursorIterator = db.iterate(txn, keyRange)) {
            for (final CursorIterator.KeyVal<ByteBuffer> keyVal : cursorIterator.iterable()) {
                Key key = Key.fromByteBuffer(keyVal.key());
                V value = valueMapper.apply(keyVal.val());
                stringBuilder.append(LambdaLogger.buildMessage("\n   key: {}, value: {}", key, value));
            }
        }
        String contents = stringBuilder.toString();
        LAMBDA_LOGGER.info(() -> LambdaLogger.buildMessage("Dumping contents in range: startKey {}, endKey {}{}",
                LmdbUtils.byteBufferToHex(keyRange.getStart()),
                LmdbUtils.byteBufferToHex(keyRange.getStop()),
                contents));
    }

    private ByteBuffer buildStartKeyBuffer(final V value) {
        return buildKeyBuffer(Key.lowestKey(value.hashCode()));
    }

    private ByteBuffer buildEndKeyBuffer(final V value) {
        return buildKeyBuffer(Key.highestKey(value.hashCode()));
    }

    private ByteBuffer buildKeyBuffer(final Key key) {
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(env.getMaxKeySize());
        key.putContent(byteBuffer);
        byteBuffer.flip();
        return byteBuffer;
    }

}
