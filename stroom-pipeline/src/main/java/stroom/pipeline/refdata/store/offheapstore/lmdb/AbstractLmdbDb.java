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

package stroom.pipeline.refdata.store.offheapstore.lmdb;

import stroom.pipeline.refdata.store.offheapstore.lmdb.serde.Serde;
import stroom.pipeline.refdata.util.ByteBufferPool;
import stroom.pipeline.refdata.util.ByteBufferUtils;
import stroom.pipeline.refdata.util.PooledByteBuffer;
import stroom.pipeline.refdata.util.PooledByteBufferPair;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.common.base.Preconditions;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.lmdbjava.Cursor;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.GetOp;
import org.lmdbjava.KeyRange;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * An abstract class representing a generic LMDB table with understanding of how to (de)serialise
 * keys/values into/out of the database. Provides various helper methods for interacting with the
 * database at a higher abstraction that the raw bytes.
 * <p>
 * See https://github.com/lmdbjava/lmdbjava/issues/81 for more information on the use/re-use
 * of the ByteBuffers passed to or returned from LMDBJava.
 * See https://github.com/lmdbjava/lmdbjava/issues/119 for tips on improving performance.
 * <p>
 * Dos/Don'ts
 * ~~~~~~~~~~
 * DO NOT use/mutate a key/value buffer from a cursor outside of the cursor's scope.
 * DO NOT mutate a key/value buffer inside a txn unless the DB is in MDB_WRITEMAP mode.
 * DO NOT use/mutate a value buffer outside of a txn as its content is indeterminate outside the txn
 * and belongs to LMDB.
 * DO ensure any {@link PooledByteBuffer}s are released/closed after use.
 * DO be aware that a get() call is using a cursor underneath, so each call to get() will move the txn's
 * cursor to the position of the new key. Therefore:
 * v1 = get(k1), v1 == X, v2 = get(k2), v2 == y, v1 == y
 * Thus if you are making multiple get() calls you may need to copy/deserialise/use the returned value before
 * doing the next get().
 *
 * @param <K> The class of the database keys
 * @param <V> The class of the database values
 */
public abstract class AbstractLmdbDb<K, V> implements LmdbDb {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLmdbDb.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(AbstractLmdbDb.class);

    private final Serde<K> keySerde;
    private final Serde<V> valueSerde;
    private final String dbName;
    private final Dbi<ByteBuffer> lmdbDbi;
    private final Env<ByteBuffer> lmdbEnvironment;
    private final ByteBufferPool byteBufferPool;

    private final int keyBufferCapacity;
    private final int valueBufferCapacity;

    /**
     * @param lmdbEnvironment The LMDB {@link Env} to add this DB to.
     * @param byteBufferPool  A self loading pool of reusable ByteBuffers.
     * @param keySerde        The {@link Serde} to use for the keys.
     * @param valueSerde      The {@link Serde} to use for the values.
     * @param dbName          The name of the database.
     * @param dbiFlags        The dbi flags to use when initialising the DB. If not provided, only MDB_CREATE will
     *                        be used.
     */
    public AbstractLmdbDb(final Env<ByteBuffer> lmdbEnvironment,
                          final ByteBufferPool byteBufferPool,
                          final Serde<K> keySerde,
                          final Serde<V> valueSerde,
                          final String dbName,
                          final DbiFlags... dbiFlags) {
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
        this.dbName = dbName;
        this.lmdbEnvironment = lmdbEnvironment;
        this.lmdbDbi = openDbi(lmdbEnvironment, dbName, dbiFlags);
        this.byteBufferPool = byteBufferPool;

        int keySerdeCapacity = keySerde.getBufferCapacity();
        int envMaxKeySize = lmdbEnvironment.getMaxKeySize();
        if (keySerdeCapacity > envMaxKeySize) {
            LAMBDA_LOGGER.debug(() -> LogUtil.message("Key serde {} capacity {} is greater than the maximum " +
                            "key size for the environment {}. " +
                            "The max environment key size {} will be used instead.",
                    keySerde.getClass().getName(), keySerdeCapacity, envMaxKeySize, envMaxKeySize));
        }
        this.keyBufferCapacity = Math.min(envMaxKeySize, keySerdeCapacity);
        this.valueBufferCapacity = Math.min(Serde.DEFAULT_CAPACITY, valueSerde.getBufferCapacity());
    }

    private static Dbi<ByteBuffer> openDbi(final Env<ByteBuffer> env,
                                           final String name,
                                           final DbiFlags... dbiFlags) {
        LOGGER.debug("Opening LMDB database with name: {}", name);
        final DbiFlags[] flags = dbiFlags.length > 0
                ? dbiFlags
                : (new DbiFlags[] {DbiFlags.MDB_CREATE});
        try {
            return env.openDbi(name, flags);
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message("Error opening LMDB database {}", name), e);
        }
    }

    @Override
    public String getDbName() {
        return dbName;
    }

    protected Dbi<ByteBuffer> getLmdbDbi() {
        return lmdbDbi;
    }

    protected ByteBufferPool getByteBufferPool() {
        return byteBufferPool;
    }

    /**
     * @return A {@link PooledByteBuffer} containing a direct {@link ByteBuffer} from the
     * pool with sufficient capacity for this database's key type.
     */
    public PooledByteBuffer getPooledKeyBuffer() {
        return byteBufferPool.getPooledByteBuffer(keyBufferCapacity);
    }

    /**
     * @return A {@link PooledByteBuffer} containing a direct {@link ByteBuffer} from the
     * pool with sufficient capacity for this database's value type.
     */
    public PooledByteBuffer getPooledValueBuffer() {
        return byteBufferPool.getPooledByteBuffer(valueBufferCapacity);
    }

    /**
     * @return A {@link PooledByteBuffer} containing a direct {@link ByteBuffer} from
     * the pool with at least the specified capacity
     */
    public PooledByteBuffer getPooledBuffer(int minBufferCapacity) {
        return byteBufferPool.getPooledByteBuffer(minBufferCapacity);
    }

    /**
     * @return A {@link PooledByteBufferPair} containing direct {@link ByteBuffer}s from the
     * pool, each with sufficient capacity for their respective key/value type.
     */
    public PooledByteBufferPair getPooledBufferPair() {
        return byteBufferPool.getPooledBufferPair(keyBufferCapacity, valueBufferCapacity);
    }

    /**
     * @return A {@link PooledByteBufferPair} containing direct {@link ByteBuffer}s from the
     * pool. The key buffer's capacity is sufficient for this database's key type while the
     * value buffer capacity is at least as specified.
     */
    public PooledByteBufferPair getPooledBufferPair(int minValueBufferCapacity) {
        return byteBufferPool.getPooledBufferPair(keyBufferCapacity, minValueBufferCapacity);
    }

    protected Env<ByteBuffer> getLmdbEnvironment() {
        return lmdbEnvironment;
    }

    /**
     * @return The {@link Serde} for (de)serialising this database's keys
     */
    public Serde<K> getKeySerde() {
        return keySerde;
    }

    /**
     * @return The {@link Serde} for (de)serialising this database's value
     */
    public Serde<V> getValueSerde() {
        return valueSerde;
    }

    /**
     * Gets the de-serialised value (if found) for the passed key
     */
    public Optional<V> get(Txn<ByteBuffer> txn, final K key) {
        try (PooledByteBuffer pooledKeyBuffer = getPooledKeyBuffer()) {
            final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
            serializeKey(keyBuffer, key);
            final ByteBuffer valueBuffer = lmdbDbi.get(txn, keyBuffer);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Get returned value [{}] for key [{}]",
                        ByteBufferUtils.byteBufferInfo(valueBuffer),
                        ByteBufferUtils.byteBufferInfo(pooledKeyBuffer.getByteBuffer()));
            }

            return Optional.ofNullable(valueBuffer)
                    .map(this::deserializeValue);
        } catch (RuntimeException e) {
            throw new RuntimeException(LogUtil.message("Error getting key {}", key), e);
        }
    }

    /**
     * Gets the de-serialised value (if found) for the passed key using  a read txn
     */
    public Optional<V> get(final K key) {
        return LmdbUtils.getWithReadTxn(lmdbEnvironment, txn ->
                get(txn, key));
    }

    public Optional<ByteBuffer> getAsBytes(Txn<ByteBuffer> txn, final K key) {
        try (final PooledByteBuffer pooledKeyBuffer = getPooledKeyBuffer()) {
            final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
            serializeKey(keyBuffer, key);
            return getAsBytes(txn, keyBuffer);
        }
    }

    /**
     * Get the bytes of the value for the given key buffer. The returned {@link ByteBuffer} should ONLY
     * by used while still inside the passed {@link Txn}, so if you need its contents outside of the
     * txn you MUST copy it.
     */
    public Optional<ByteBuffer> getAsBytes(Txn<ByteBuffer> txn, final ByteBuffer keyBuffer) {
        try {
            final ByteBuffer valueBuffer = lmdbDbi.get(txn, keyBuffer);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Get returned value [{}] for key [{}]",
                        ByteBufferUtils.byteBufferInfo(valueBuffer),
                        ByteBufferUtils.byteBufferInfo(keyBuffer));
            }

            return Optional.ofNullable(valueBuffer);
        } catch (RuntimeException e) {
            throw new RuntimeException(LogUtil.message("Error getting value for key [{}]",
                    ByteBufferUtils.byteBufferInfo(keyBuffer)), e);
        }
    }

    public KeyRange<ByteBuffer> serialiseKeyRange(final PooledByteBuffer pooledStartKeyBuffer,
                                                  final PooledByteBuffer pooledStopKeyBuffer,
                                                  final KeyRange<K> keyRange) {

        final ByteBuffer startKeyBuffer;
        final ByteBuffer stopKeyBuffer;

        if (keyRange.getStart() != null) {
            serializeKey(pooledStartKeyBuffer.getByteBuffer(), keyRange.getStart());
            startKeyBuffer = pooledStartKeyBuffer.getByteBuffer();
        } else {
            startKeyBuffer = null;
        }

        if (keyRange.getStop() != null) {
            serializeKey(pooledStopKeyBuffer.getByteBuffer(), keyRange.getStop());
            stopKeyBuffer = pooledStopKeyBuffer.getByteBuffer();
        } else {
            stopKeyBuffer = null;
        }

        return new KeyRange<>(keyRange.getType(), startKeyBuffer, stopKeyBuffer);
    }

    /**
     * Stream all entries found in keyRange in the order they are found in the DB.
     * @param streamFunction A function to map a {@link Stream} to a return value T
     * @return The result of the stream mapping function.
     */
    public <T> T streamEntries(final Txn<ByteBuffer> txn,
                               final KeyRange<K> keyRange,
                               final Function<Stream<Tuple2<K, V>>, T> streamFunction) {

        try (final PooledByteBuffer startKeyPooledBuffer = getPooledKeyBuffer();
             final PooledByteBuffer stopKeyPooledBuffer = getPooledKeyBuffer()) {

            final KeyRange<ByteBuffer> serialisedKeyRange = serialiseKeyRange(startKeyPooledBuffer,
                    stopKeyPooledBuffer,
                    keyRange);

            return streamEntriesAsBytes(txn, serialisedKeyRange, entryStream -> {
                final Stream<Tuple2<K, V>> deSerialisedStream = entryStream.map(keyVal -> {
                    K key = deserializeKey(keyVal.key());
                    V value = deserializeValue(keyVal.val());
                    return Tuple.of(key, value);
                });

                return streamFunction.apply(deSerialisedStream);
            });
        }
    }

    public <T> T streamEntriesAsBytes(final Txn<ByteBuffer> txn,
                                      final KeyRange<ByteBuffer> keyRange,
                                      final Function<Stream<CursorIterable.KeyVal<ByteBuffer>>, T> streamFunction) {

        try (CursorIterable<ByteBuffer> cursorIterable = getLmdbDbi().iterate(txn, keyRange)) {
            final Stream<CursorIterable.KeyVal<ByteBuffer>> stream =
                    StreamSupport.stream(cursorIterable.spliterator(), false);

            return streamFunction.apply(stream);
        }
    }

    /**
     * Apply the passes entryConsumer for each entry found in the keyRange.
     */
    public void forEachEntry(final Txn<ByteBuffer> txn,
                             final KeyRange<K> keyRange,
                             final Consumer<Tuple2<K, V>> keyValueTupleConsumer) {

        try (final PooledByteBuffer startKeyPooledBuffer = getPooledKeyBuffer();
             final PooledByteBuffer stopKeyPooledBuffer = getPooledKeyBuffer()) {

            final KeyRange<ByteBuffer> serialisedKeyRange = serialiseKeyRange(startKeyPooledBuffer,
                    stopKeyPooledBuffer,
                    keyRange);
            forEachEntryAsBytes(txn, serialisedKeyRange, keyVal -> {
                final Tuple2<K, V> deSerialisedKeyValue = deserializeKeyVal(keyVal);
                keyValueTupleConsumer.accept(deSerialisedKeyValue);
            });
        }

    }

    /**
     * Apply the passes entryConsumer for each entry found in the keyRange. The consumer works on
     * the raw bytes of the entry.
     */
    public void forEachEntryAsBytes(final Txn<ByteBuffer> txn,
                                    final KeyRange<ByteBuffer> keyRange,
                                    final Consumer<CursorIterable.KeyVal<ByteBuffer>> entryConsumer) {

        try (CursorIterable<ByteBuffer> cursorIterable = getLmdbDbi().iterate(txn, keyRange)) {
            for (CursorIterable.KeyVal<ByteBuffer> keyVal : cursorIterable) {
                entryConsumer.accept(keyVal);
            }
        }
    }

    /**
     * @return True if any entry exists with a key matching the passed key
     */
    public boolean exists(final K key) {
        return LmdbUtils.getWithReadTxn(lmdbEnvironment, txn ->
                exists(txn, key));
    }

    /**
     * @return True if any entry exists with a key matching the passed key
     */
    public boolean exists(final Txn<ByteBuffer> txn, final K key) {
        try (final PooledByteBuffer pooledKeyBuffer = getPooledKeyBuffer()) {
            final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
            serializeKey(keyBuffer, key);
            return exists(txn, keyBuffer);
        }
    }

    /**
     * @return True if any entry exists with a key matching the passed keyBuffer
     */
    public boolean exists(final Txn<ByteBuffer> txn, final ByteBuffer keyBuffer) {
        // It is debatable whether it is cheaper to use a cursor to see if
        // the key exists or a get like this.
        return lmdbDbi.get(txn, keyBuffer) != null;
    }

    public <T> T mapValue(final K key, final Function<V, T> valueMapper) {
        try (final Txn<ByteBuffer> txn = lmdbEnvironment.txnRead();
             final PooledByteBuffer pooledKeyBuffer = getPooledKeyBuffer()) {

            final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
            serializeKey(keyBuffer, key);
            final ByteBuffer valueBuffer = lmdbDbi.get(txn, pooledKeyBuffer.getByteBuffer());
            V value = deserializeValue(valueBuffer);
            return valueMapper.apply(value);
        } catch (RuntimeException e) {
            throw new RuntimeException(LogUtil.message("Error getting key {}", key), e);
        }
    }

    public void consumeValue(final K key, final Consumer<V> valueConsumer) {
        try (final Txn<ByteBuffer> txn = lmdbEnvironment.txnRead();
             final PooledByteBuffer pooledKeyBuffer = getPooledKeyBuffer()) {

            final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
            serializeKey(keyBuffer, key);
            final ByteBuffer valueBuffer = lmdbDbi.get(txn, keyBuffer);
            V value = deserializeValue(valueBuffer);
            valueConsumer.accept(value);
        } catch (RuntimeException e) {
            throw new RuntimeException(LogUtil.message("Error getting key {}", key), e);
        }
    }

    public boolean put(final Txn<ByteBuffer> writeTxn,
                       final K key,
                       final V value,
                       final boolean overwriteExisting) {
        try (final PooledByteBuffer pooledKeyBuffer = getPooledKeyBuffer();
             final PooledByteBuffer pooledValueBuffer = getPooledValueBuffer()) {

            final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
            final ByteBuffer valueBuffer = pooledValueBuffer.getByteBuffer();

            serializeKey(keyBuffer, key);
            serializeValue(valueBuffer, value);

            return put(
                    writeTxn,
                    keyBuffer,
                    valueBuffer,
                    overwriteExisting);
        } catch (RuntimeException e) {
            throw new RuntimeException(LogUtil.message("Error putting key {}, value {}", key, value), e);
        }
    }

    public boolean put(final K key, final V value, final boolean overwriteExisting) {
        try (final Txn<ByteBuffer> writeTxn = lmdbEnvironment.txnWrite()) {
            boolean didPutSucceed = put(writeTxn, key, value, overwriteExisting);
            writeTxn.commit();
            return didPutSucceed;
        } catch (RuntimeException e) {
            throw new RuntimeException(LogUtil.message("Error putting key {}, value {}", key, value), e);
        }
    }

    public boolean put(final Txn<ByteBuffer> writeTxn,
                       final ByteBuffer keyBuffer,
                       final ByteBuffer valueBuffer,
                       final boolean overwriteExisting) {
        try {
            boolean didPutSucceed;
            if (overwriteExisting) {
                didPutSucceed = lmdbDbi.put(writeTxn, keyBuffer, valueBuffer);
            } else {
                didPutSucceed = lmdbDbi.put(writeTxn, keyBuffer, valueBuffer, PutFlags.MDB_NOOVERWRITE);
            }
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Put returned {} for key [{}], value [{}]",
                        didPutSucceed,
                        ByteBufferUtils.byteBufferInfo(keyBuffer),
                        ByteBufferUtils.byteBufferInfo(valueBuffer));
            }

            return didPutSucceed;
        } catch (RuntimeException e) {
            throw new RuntimeException(LogUtil.message("Error putting key {}, value {}",
                    ByteBufferUtils.byteBufferInfo(keyBuffer), ByteBufferUtils.byteBufferInfo(valueBuffer)), e);
        }
    }

    public void putAll(final Map<K, V> entries) {
        try (final Txn<ByteBuffer> txn = lmdbEnvironment.txnWrite()) {
            entries.forEach((key, value) -> {
                try (final PooledByteBuffer pooledKeyBuffer = getPooledKeyBuffer();
                     final PooledByteBuffer pooledValueBuffer = getPooledValueBuffer()) {

                    final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
                    final ByteBuffer valueBuffer = pooledValueBuffer.getByteBuffer();
                    serializeKey(keyBuffer, key);
                    serializeValue(valueBuffer, value);

                    lmdbDbi.put(txn, keyBuffer, valueBuffer);
                } catch (Exception e) {
                    throw new RuntimeException(LogUtil.message("Error putting key {}, value {}", key, value), e);
                }
            });
            txn.commit();
        } catch (RuntimeException e) {
            throw new RuntimeException(LogUtil.message("Error putting {} entries", entries.size()), e);
        }
    }

    /**
     * Updates the value associated with the passed key using the valueBufferConsumer. A new {@link ByteBuffer}
     * will be created from the current value and passed to valueBufferConsumer to mutate. This mutated buffer
     * should be left in a state ready to be read, i.e. flipped.
     * This method is intended for cases where you want to modify the value based on its current value, or
     * you only want to modify part of it without (de)serialising the whole.
     */
    public void updateValue(final Txn<ByteBuffer> writeTxn,
                            final K key,
                            final Consumer<ByteBuffer> valueBufferConsumer) {

        try (final PooledByteBuffer pooledKeyBuffer = getPooledKeyBuffer()) {
            final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
            serializeKey(keyBuffer, key);
            updateValue(writeTxn, keyBuffer, valueBufferConsumer);
        }
    }

    /**
     * Updates the value associated with the passed key using the valueBufferConsumer. A new {@link ByteBuffer}
     * will be populated from the current value and passed to valueBufferConsumer to mutate. This mutated buffer
     * should be left in a state ready to be read, i.e. flipped.
     * This method is intended for cases where you want to modify the value based on its current value, or
     * you only want to modify part of it without (de)serialising the whole.
     */
    public void updateValue(final Txn<ByteBuffer> writeTxn,
                            final ByteBuffer keyBuffer,
                            final Consumer<ByteBuffer> valueBufferConsumer) {
        Preconditions.checkArgument(!writeTxn.isReadOnly());

        try (Cursor<ByteBuffer> cursor = lmdbDbi.openCursor(writeTxn)) {

            boolean isFound = cursor.get(keyBuffer, GetOp.MDB_SET_KEY);
            if (!isFound) {
                throw new RuntimeException(LogUtil.message(
                        "Expecting to find entry for {}", ByteBufferUtils.byteBufferInfo(keyBuffer)));
            }
            final ByteBuffer valueBuf = cursor.val();

            // We run LMDB in its default mode of read only mmaps so we cannot mutate the key/value
            // bytebuffers.  Instead we must copy the content and put the replacement entry.
            // We could run LMDB in MDB_WRITEMAP mode which allows mutation of the buffers (and
            // thus avoids the buffer copy cost) but adds more risk of DB corruption. As we are not
            // doing a high volume of value mutations read-only mode is a safer bet.
            try (final PooledByteBuffer pooledValueBuffer = getPooledValueBuffer()) {
                final ByteBuffer newValueBuf = pooledValueBuffer.getByteBuffer();
                // First copy the current value as is
                ByteBufferUtils.copy(valueBuf, newValueBuf);

                // Now use the consumer to update the contents of the buffer
                valueBufferConsumer.accept(newValueBuf);

                // Only put if the buffer is different
                if (ByteBufferUtils.compare(valueBuf, newValueBuf) != 0) {
                    cursor.put(cursor.key(), newValueBuf, PutFlags.MDB_CURRENT);
                } else {
                    LOGGER.trace("put call skipped as buffers are the same");
                }
            }
        }
    }

    /**
     * @see AbstractLmdbDb#updateValue(Txn, Object, Consumer)
     */
    public void updateValue(final K key, final Consumer<ByteBuffer> valueBufferConsumer) {
        LmdbUtils.doWithWriteTxn(lmdbEnvironment, writeTxn ->
                updateValue(writeTxn, key, valueBufferConsumer));
    }

    public boolean delete(final K key) {
        try (final Txn<ByteBuffer> writeTxn = lmdbEnvironment.txnWrite()) {
            boolean result = delete(writeTxn, key);
            writeTxn.commit();
            return result;
        } catch (RuntimeException e) {
            throw new RuntimeException(LogUtil.message("Error deleting key {}", key), e);
        }
    }

    public boolean delete(final Txn<ByteBuffer> writeTxn, final K key) {
        try (final PooledByteBuffer pooledKeyBuffer = getPooledKeyBuffer()) {
            final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
            serializeKey(keyBuffer, key);
            boolean result = lmdbDbi.delete(writeTxn, keyBuffer);
            LOGGER.trace("delete({}) returned {}", key, result);
            return result;
        } catch (RuntimeException e) {
            throw new RuntimeException(LogUtil.message("Error deleting key {}", key), e);
        }
    }

    public boolean delete(final ByteBuffer keyBuffer) {
        try (final Txn<ByteBuffer> writeTxn = lmdbEnvironment.txnWrite()) {
            boolean result = lmdbDbi.delete(writeTxn, keyBuffer);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("delete({}) returned {}", ByteBufferUtils.byteBufferInfo(keyBuffer), result);
            }
            writeTxn.commit();
            return result;
        } catch (RuntimeException e) {
            throw new RuntimeException(LogUtil.message("Error deleting key {}",
                    ByteBufferUtils.byteBufferInfo(keyBuffer)), e);
        }
    }

    /**
     * Delete the entry with the passed keyBuffer.
     *
     * @return True if the entry was found and deleted.
     */
    public boolean delete(final Txn<ByteBuffer> writeTxn, final ByteBuffer keyBuffer) {
        try {
            boolean result = lmdbDbi.delete(writeTxn, keyBuffer);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("delete(txn, {}) returned {}", ByteBufferUtils.byteBufferInfo(keyBuffer), result);
            }
            return result;
        } catch (RuntimeException e) {
            throw new RuntimeException(LogUtil.message("Error deleting key {}",
                    ByteBufferUtils.byteBufferInfo(keyBuffer)), e);
        }
    }

    public void deleteAll(final Collection<K> keys) {
        try (final Txn<ByteBuffer> txn = lmdbEnvironment.txnWrite();
             final PooledByteBuffer pooledKeyBuffer = getPooledKeyBuffer()) {
            keys.forEach(key -> {
                try {
                    final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
                    serializeKey(keyBuffer, key);
                    lmdbDbi.delete(txn, keyBuffer);
                } catch (Exception e) {
                    throw new RuntimeException(LogUtil.message("Error deleting key {}", key), e);
                }
            });
            txn.commit();
        } catch (RuntimeException e) {
            throw new RuntimeException(LogUtil.message("Error deleting {} keys ", keys.size()), e);
        }
    }

    @Override
    public Map<String, String> getDbInfo() {
        return LmdbUtils.getDbInfo(lmdbEnvironment, lmdbDbi);
    }

    @Override
    public long getEntryCount(final Txn<ByteBuffer> txn) {
        return lmdbDbi.stat(txn).entries;
    }

    @Override
    public long getEntryCount() {
        return LmdbUtils.getWithReadTxn(lmdbEnvironment, this::getEntryCount);
    }

    /**
     * Dumps the contents of this database to the logger using the key/value
     * serdes to de-serialise the data. Only for use at SMALL scale in tests.
     */
    @Override
    public void logDatabaseContents(final Txn<ByteBuffer> txn, Consumer<String> logEntryConsumer) {
        LmdbUtils.logDatabaseContents(
                lmdbEnvironment,
                lmdbDbi,
                txn,
                keyBuffer -> deserializeKey(keyBuffer).toString(),
                valueBuffer -> deserializeValue(valueBuffer).toString(),
                logEntryConsumer);
    }

    public void logDatabaseContents(final Txn<ByteBuffer> txn) {
        logRawDatabaseContents(txn, LOGGER::debug);
    }

    /**
     * Dumps all entries in the database to a single logger entry with one line per database entry.
     * This could potentially return thousands of rows so is only intended for small scale use in
     * testing. Entries are returned in the order they are held in the DB, e.g. a-z (unless the DB
     * is configured with reverse keys). The keys/values are de-serialised and a toString() is applied
     * to the resulting objects.
     */
    @Override
    public void logDatabaseContents(Consumer<String> logEntryConsumer) {
        LmdbUtils.logDatabaseContents(
                lmdbEnvironment,
                lmdbDbi,
                byteBuffer -> keySerde.deserialize(byteBuffer).toString(),
                byteBuffer -> valueSerde.deserialize(byteBuffer).toString(),
                logEntryConsumer);
    }

    @Override
    public void logDatabaseContents() {
        logDatabaseContents(LOGGER::debug);
    }

    @Override
    public void logRawDatabaseContents(final Txn<ByteBuffer> txn, Consumer<String> logEntryConsumer) {
        LmdbUtils.logRawDatabaseContents(
                lmdbEnvironment,
                lmdbDbi,
                txn,
                logEntryConsumer);
    }

    public void logRawDatabaseContents(final Txn<ByteBuffer> txn) {
        logRawDatabaseContents(txn, LOGGER::debug);
    }

    /**
     * Dumps all entries in the database to a single logger entry with one line per database entry.
     * This could potentially return thousands of rows so is only intended for small scale use in
     * testing. Entries are returned in the order they are held in the DB, e.g. a-z (unless the DB
     * is configured with reverse keys). The keys/values are output as hex representations of the
     * byte values.
     */
    @Override
    public void logRawDatabaseContents(Consumer<String> logEntryConsumer) {
        LmdbUtils.logRawDatabaseContents(
                lmdbEnvironment,
                lmdbDbi,
                logEntryConsumer);
    }

    public void logRawDatabaseContents() {
        logRawDatabaseContents(LOGGER::debug);
    }

    public K deserializeKey(final ByteBuffer keyBuffer) {
        try {
            return keySerde.deserialize(keyBuffer);
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message("Error de-serialising key buffer [{}]: {}",
                    ByteBufferUtils.byteBufferToHex(keyBuffer), e.getMessage()));
        }
    }

    public V deserializeValue(final ByteBuffer valueBuffer) {
        try {
            return valueSerde.deserialize(valueBuffer);
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message("Error de-serialising value buffer [{}]: {}",
                    ByteBufferUtils.byteBufferToHex(valueBuffer), e.getMessage()));
        }
    }

    public Tuple2<K, V> deserializeKeyVal(final CursorIterable.KeyVal<ByteBuffer> keyVal) {
        return Tuple.of(deserializeKey(keyVal.key()), deserializeValue(keyVal.val()));
    }

    public void serializeKey(final ByteBuffer keyBuffer, K key) {
        try {
            keySerde.serialize(keyBuffer, key);
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message("Error serialising key [{}]: {}",
                    key, e.getMessage()));
        }
    }

    public void serializeValue(final ByteBuffer valueBuffer, V value) {
        try {
            valueSerde.serialize(valueBuffer, value);
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message("Error serialising value [{}]: {}",
                    value, e.getMessage()));
        }
    }
}
