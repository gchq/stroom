/*
 * Copyright 2016-2025 Crown Copyright
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
 */

package stroom.lmdb;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.bytebuffer.PooledByteBufferPair;
import stroom.lmdb.serde.Serde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.common.base.Preconditions;
import org.lmdbjava.Cursor;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * An abstract class representing a generic LMDB table with understanding of how to (de)serialise
 * keys/values into/out of the database. Provides various helper methods for interacting with the
 * database at a higher abstraction that the raw bytes.
 * <p>
 * See <a href="https://github.com/lmdbjava/lmdbjava/issues/81">here</a> for more information on the use/re-use
 * of the ByteBuffers passed to or returned from LMDBJava.
 * <p>
 * See <a href="https://github.com/lmdbjava/lmdbjava/issues/119">here</a> for tips on improving performance.
 * <p>
 * See<a href=" https://www.youtube.com/watch?v=tEa5sAh-kVk&t=10">YouTube</a> for a talk by
 * Howard Chu on the design of LMDB to better understand how it works
 * <p>
 * See <a href="http://www.lmdb.tech/media/20141120-BuildStuff-Lightning.pdf">here</a> for the slides from
 * the YouTube vid.
 * <p>
 * See <a href="https://github.com/lmdbjava/lmdbjava/wiki">LMDBJava Wiki</a> for useful usage recommendations
 * <p>
 * Dos/Don'ts
 * <p>
 * DO NOT use/mutate a key/value buffer from a cursor outside of the cursor's scope.
 * <p>
 * DO NOT mutate a key/value buffer inside a txn unless the DB is in MDB_WRITEMAP mode.
 * <p>
 * DO NOT use/mutate a value buffer outside of a txn as its content is indeterminate outside the txn
 * and belongs to LMDB.
 * <p>
 * DO NOT open a new txn while inside a txn, e.g. calling get("key") while inside a txn.
 * <p>
 * DO ensure any {@link PooledByteBuffer}s are released/closed after use.
 * <p>
 * DO be aware that a get() call is using a cursor underneath, so each call to get() will move the txn's
 * cursor to the position of the new key. Therefore:
 * v1 = get(k1), v1 == X, v2 = get(k2), v2 == y, v1 == y
 * Thus if you are making multiple get() calls you may need to copy/deserialise/use the returned value before
 * doing the next get().
 *
 * @param <K> The class of the database keys
 * @param <V> The class of the database values
 */
public abstract class AbstractLmdbDb<K, V>
        implements LmdbDb {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLmdbDb.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(AbstractLmdbDb.class);
    private static final PutFlags[] NO_OVERWRITE = new PutFlags[]{PutFlags.MDB_NOOVERWRITE};
    private static final PutFlags[] NO_OVERWRITE_AND_APPEND = new PutFlags[]{
            PutFlags.MDB_NOOVERWRITE,
            PutFlags.MDB_APPEND};

    private final Serde<K> keySerde;
    private final Serde<V> valueSerde;
    private final String dbName;
    private final Dbi<ByteBuffer> lmdbDbi;
    private final LmdbEnv lmdbEnvironment;
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
    public AbstractLmdbDb(final LmdbEnv lmdbEnvironment,
                          final ByteBufferPool byteBufferPool,
                          final Serde<K> keySerde,
                          final Serde<V> valueSerde,
                          final String dbName,
                          final DbiFlags... dbiFlags) {
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
        this.dbName = dbName;
        this.lmdbEnvironment = lmdbEnvironment;
        this.lmdbDbi = lmdbEnvironment.openDbi(dbName, dbiFlags);
        this.byteBufferPool = byteBufferPool;

        final int keySerdeCapacity = keySerde.getBufferCapacity();
        final int envMaxKeySize = lmdbEnvironment.getMaxKeySize();
        if (keySerdeCapacity > envMaxKeySize) {
            LAMBDA_LOGGER.debug(() -> LogUtil.message("Key serde {} capacity {} is greater than the maximum " +
                                                      "key size for the environment {}. " +
                                                      "The max environment key size {} will be used instead.",
                    keySerde.getClass().getName(), keySerdeCapacity, envMaxKeySize, envMaxKeySize));
        }
        this.keyBufferCapacity = Math.min(envMaxKeySize, keySerdeCapacity);
        this.valueBufferCapacity = valueSerde.getBufferCapacity();
    }

    private static Dbi<ByteBuffer> openDbi(final Env<ByteBuffer> env,
                                           final String name,
                                           final DbiFlags... dbiFlags) {
        LOGGER.debug("Opening LMDB database with name: {}", name);
        final DbiFlags[] flags = dbiFlags.length > 0
                ? dbiFlags
                : (new DbiFlags[]{DbiFlags.MDB_CREATE});
        try {
            return env.openDbi(name, flags);
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message("Error opening LMDB database {}", name), e);
        }
    }

    @Override
    public String getDbName() {
        return dbName;
    }

    public Dbi<ByteBuffer> getLmdbDbi() {
        return lmdbDbi;
    }

    public ByteBufferPool getByteBufferPool() {
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
    public PooledByteBuffer getPooledBuffer(final int minBufferCapacity) {
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
    public PooledByteBufferPair getPooledBufferPair(final int minValueBufferCapacity) {
        return byteBufferPool.getPooledBufferPair(keyBufferCapacity, minValueBufferCapacity);
    }

    public LmdbEnv getLmdbEnvironment() {
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
    public Optional<V> get(final Txn<ByteBuffer> txn, final K key) {
        try (final PooledByteBuffer pooledKeyBuffer = getPooledKeyBuffer()) {
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
        } catch (final RuntimeException e) {
            throw new RuntimeException(LogUtil.message("Error getting key {}", key), e);
        }
    }

    /**
     * Gets the de-serialised value (if found) for the passed key using a read txn.
     * This will fail if you are already inside a txn.
     */
    public Optional<V> get(final K key) {
        return lmdbEnvironment.getWithReadTxn(txn ->
                get(txn, key));
    }

    public Optional<ByteBuffer> getAsBytes(final Txn<ByteBuffer> txn, final K key) {
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
    public Optional<ByteBuffer> getAsBytes(final Txn<ByteBuffer> txn, final ByteBuffer keyBuffer) {
        try {
            final ByteBuffer valueBuffer = lmdbDbi.get(txn, keyBuffer);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Get returned value [{}] for key [{}]",
                        ByteBufferUtils.byteBufferInfo(valueBuffer),
                        ByteBufferUtils.byteBufferInfo(keyBuffer));
            }

            return Optional.ofNullable(valueBuffer);
        } catch (final RuntimeException e) {
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
     *
     * @param streamFunction A function to map a {@link Stream} to a return value T
     * @return The result of the stream mapping function.
     */
    public <T> T streamEntries(final Txn<ByteBuffer> txn,
                               final KeyRange<K> keyRange,
                               final Function<Stream<Entry<K, V>>, T> streamFunction) {

        try (final PooledByteBuffer startKeyPooledBuffer = getPooledKeyBuffer();
                final PooledByteBuffer stopKeyPooledBuffer = getPooledKeyBuffer()) {

            final KeyRange<ByteBuffer> serialisedKeyRange = serialiseKeyRange(startKeyPooledBuffer,
                    stopKeyPooledBuffer,
                    keyRange);

            return streamEntriesAsBytes(txn, serialisedKeyRange, entryStream -> {
                final Stream<Entry<K, V>> deSerialisedStream = entryStream.map(keyVal -> {
                    final K key = deserializeKey(keyVal.key());
                    final V value = deserializeValue(keyVal.val());
                    return Map.entry(key, value);
                });

                return streamFunction.apply(deSerialisedStream);
            });
        }
    }

    /**
     * Get all entries found in keyRange in the order they are found in the DB.
     *
     * @return The result of the stream mapping function.
     */
    public SequencedMap<K, V> asSequencedMap(final Txn<ByteBuffer> txn,
                                             final KeyRange<K> keyRange) {
        return streamEntries(txn, keyRange, stream -> stream
                .collect(Collectors.toMap(
                        Entry::getKey,
                        Entry::getValue,
                        (o, o2) -> o, // Merge shouldn't be an issue as the db is essentially a map
                        LinkedHashMap::new)));
    }

    /**
     * Find the first entry matching the supplied key predicate in the supplied key range.
     * Not very efficient as it will scan over all entries in the range (de-serialising
     * each key as it goes) till it finds a match. Values only de-serialised if the keyPredicate
     * is matched.
     */
    public Optional<Entry<K, V>> findFirstMatchingKey(final Txn<ByteBuffer> txn,
                                                      final KeyRange<K> keyRange,
                                                      final Predicate<K> keyPredicate) {

        try (final PooledByteBuffer startKeyPooledBuffer = getPooledKeyBuffer();
                final PooledByteBuffer stopKeyPooledBuffer = getPooledKeyBuffer()) {

            final KeyRange<ByteBuffer> serialisedKeyRange = serialiseKeyRange(startKeyPooledBuffer,
                    stopKeyPooledBuffer,
                    keyRange);

            return streamEntriesAsBytes(txn, serialisedKeyRange, entryStream ->
                    entryStream
                            .map(keyVal ->
                                    Map.entry(deserializeKey(keyVal.key()), keyVal.val()))
                            .filter(entry -> keyPredicate.test(entry.getKey()))
                            .map(entry -> Map.entry(
                                    entry.getKey(),
                                    deserializeValue(entry.getValue())))
                            .findFirst());
        }
    }

    /**
     * Find the first entry matching the supplied key buffer predicate in the supplied key range.
     * Only de-serialises matched entries.
     */
    public Optional<Entry<K, V>> findFirstMatchingKeyBytes(final Txn<ByteBuffer> txn,
                                                           final KeyRange<K> keyRange,
                                                           final Predicate<ByteBuffer> keyPredicate) {

        try (final PooledByteBuffer startKeyPooledBuffer = getPooledKeyBuffer();
                final PooledByteBuffer stopKeyPooledBuffer = getPooledKeyBuffer()) {

            final KeyRange<ByteBuffer> serialisedKeyRange = serialiseKeyRange(startKeyPooledBuffer,
                    stopKeyPooledBuffer,
                    keyRange);

            return streamEntriesAsBytes(txn, serialisedKeyRange, entryStream ->
                    entryStream
                            .filter(keyVal ->
                                    keyPredicate.test(keyVal.key()))
                            .map(this::deserializeKeyVal)
                            .findFirst());
        }
    }

    public <T> T streamEntriesAsBytes(final Txn<ByteBuffer> txn,
                                      final KeyRange<ByteBuffer> keyRange,
                                      final Function<Stream<CursorIterable.KeyVal<ByteBuffer>>, T> streamFunction) {

        try (final CursorIterable<ByteBuffer> cursorIterable = getLmdbDbi().iterate(txn, keyRange)) {
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
                             final Consumer<Entry<K, V>> keyValueTupleConsumer) {

        try (final PooledByteBuffer startKeyPooledBuffer = getPooledKeyBuffer();
                final PooledByteBuffer stopKeyPooledBuffer = getPooledKeyBuffer()) {

            final KeyRange<ByteBuffer> serialisedKeyRange = serialiseKeyRange(
                    startKeyPooledBuffer,
                    stopKeyPooledBuffer,
                    keyRange);
            forEachEntryAsBytes(txn, serialisedKeyRange, keyVal -> {
                final Entry<K, V> deSerialisedKeyValue = deserializeKeyVal(keyVal);
                keyValueTupleConsumer.accept(deSerialisedKeyValue);
            });
        }
    }

    /**
     * Apply the passes entryConsumer for all entries in key order
     */
    public void forEachEntry(final Txn<ByteBuffer> txn,
                             final Consumer<Entry<K, V>> keyValueTupleConsumer) {

        forEachEntryAsBytes(txn, keyVal -> {
            final Entry<K, V> deSerialisedKeyValue = deserializeKeyVal(keyVal);
            keyValueTupleConsumer.accept(deSerialisedKeyValue);
        });
    }

    /**
     * Apply the passed entryConsumer for each entry found in the keyRange. The consumer works on
     * the raw bytes of the entry.
     */
    public void forEachEntryAsBytes(final Txn<ByteBuffer> txn,
                                    final KeyRange<ByteBuffer> keyRange,
                                    final Consumer<CursorIterable.KeyVal<ByteBuffer>> entryConsumer) {

        try (final CursorIterable<ByteBuffer> cursorIterable = getLmdbDbi().iterate(txn, keyRange)) {
            for (final CursorIterable.KeyVal<ByteBuffer> keyVal : cursorIterable) {
                entryConsumer.accept(keyVal);
            }
        }
    }

    /**
     * Apply the passed entryConsumer for all entries. The consumer works on
     * the raw bytes of the entry.
     */
    public void forEachEntryAsBytes(final Txn<ByteBuffer> txn,
                                    final Consumer<CursorIterable.KeyVal<ByteBuffer>> entryConsumer) {
        forEachEntryAsBytes(txn, KeyRange.all(), entryConsumer);
    }

    /**
     * @return True if any entry exists with a key matching the passed key
     */
    public boolean exists(final K key) {
        return lmdbEnvironment.getWithReadTxn(txn ->
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
     * Scans over all entries till it finds a match, so not recommended for use on big tables.
     * De-serialises each key as it goes.
     * Intended for use when it is not possible to test a key without di-serialisation, e.g. when
     * variable width serialisation is used.
     *
     * @return True if any entry exists that matches keyPredicate.
     */
    public boolean exists(final Txn<ByteBuffer> txn, final Predicate<K> keyPredicate) {
        Objects.requireNonNull(keyPredicate);
        boolean wasMatchFound = false;
        try (final CursorIterable<ByteBuffer> iterable = getLmdbDbi().iterate(txn, KeyRange.allBackward())) {
            for (final KeyVal<ByteBuffer> keyValBuffer : iterable) {
                final K key = deserializeKey(keyValBuffer.key());
                if (keyPredicate.test(key)) {
                    wasMatchFound = true;
                    break;
                }
            }
        }
        return wasMatchFound;
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
        return lmdbEnvironment.getWithReadTxn(txn -> {
            try (final PooledByteBuffer pooledKeyBuffer = getPooledKeyBuffer()) {

                final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
                serializeKey(keyBuffer, key);
                final ByteBuffer valueBuffer = lmdbDbi.get(txn, pooledKeyBuffer.getByteBuffer());
                final V value = deserializeValue(valueBuffer);
                return valueMapper.apply(value);
            } catch (final RuntimeException e) {
                throw new RuntimeException(LogUtil.message("Error getting key {}", key), e);
            }
        });
    }

    public void consumeValue(final K key, final Consumer<V> valueConsumer) {
        lmdbEnvironment.doWithReadTxn(txn -> {
            try (final PooledByteBuffer pooledKeyBuffer = getPooledKeyBuffer()) {
                final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
                serializeKey(keyBuffer, key);
                final ByteBuffer valueBuffer = lmdbDbi.get(txn, keyBuffer);
                final V value = deserializeValue(valueBuffer);
                valueConsumer.accept(value);
            } catch (final RuntimeException e) {
                throw new RuntimeException(LogUtil.message("Error getting key {}", key), e);
            }
        });
    }

    /**
     * @param writeTxn
     * @param key
     * @param value
     * @param overwriteExisting If true it will overwrite an existing entry with the same key. If false will
     *                          not do anything if an existing entry exists. If the database has
     *                          {@link DbiFlags#MDB_DUPSORT} set then you need to set this to true
     *                          to put multiple values for the same key.
     * @return
     */
    public PutOutcome put(final Txn<ByteBuffer> writeTxn,
                          final K key,
                          final V value,
                          final boolean overwriteExisting) {
        return put(writeTxn, key, value, overwriteExisting, false);
    }

    /**
     * @param writeTxn
     * @param key
     * @param value
     * @param overwriteExisting If true it will overwrite an existing entry with the same key. If false will
     *                          not do anything if an existing entry exists. If the database has
     *                          {@link DbiFlags#MDB_DUPSORT} set then you need to set this to true
     *                          to put multiple values for the same key.
     * @param isAppending       Set this to true if you are sure the key is going on the end of the DB.
     *                          Speeds up the put.
     * @return
     */
    public PutOutcome put(final Txn<ByteBuffer> writeTxn,
                          final K key,
                          final V value,
                          final boolean overwriteExisting,
                          final boolean isAppending) {
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
                    overwriteExisting,
                    isAppending);
        } catch (final RuntimeException e) {
            throw new RuntimeException(LogUtil.message("Error putting key {}, value {}", key, value), e);
        }
    }

    public PutOutcome put(final K key,
                          final V value,
                          final boolean overwriteExisting) {
        return lmdbEnvironment.getWithWriteTxn(writeTxn -> {
            try {
                final PutOutcome putOutcome = put(writeTxn, key, value, overwriteExisting, false);
                return putOutcome;
            } catch (final RuntimeException e) {
                throw new RuntimeException(LogUtil.message("Error putting key {}, value {}", key, value), e);
            }
        });
    }

    /**
     * This will fail if you are already inside a txn.
     */
    public PutOutcome put(final K key,
                          final V value,
                          final boolean overwriteExisting,
                          final boolean isAppending) {
        return lmdbEnvironment.getWithWriteTxn(writeTxn -> {
            try {
                final PutOutcome putOutcome = put(writeTxn, key, value, overwriteExisting, isAppending);
                return putOutcome;
            } catch (final RuntimeException e) {
                throw new RuntimeException(LogUtil.message("Error putting key {}, value {}", key, value), e);
            }
        });
    }

    public PutOutcome put(final Txn<ByteBuffer> writeTxn,
                          final ByteBuffer keyBuffer,
                          final ByteBuffer valueBuffer,
                          final boolean overwriteExisting) {
        return put(writeTxn, keyBuffer, valueBuffer, overwriteExisting, false);
    }

    public PutOutcome put(final Txn<ByteBuffer> writeTxn,
                          final ByteBuffer keyBuffer,
                          final ByteBuffer valueBuffer,
                          final boolean overwriteExisting,
                          final boolean isAppending) {
        try {
            boolean didPutSucceed;

            // First try with nooverwrite flag so the put will fail if the key exists
            // For use cases with heavy updates to existing entries this two-step put is not ideal,
            // we would need some kind of flag to indicate if we care about what was there before or not.
            // If we know the puts are in key order then using MDB_APPEND speeds up the puts a lot.
            final PutFlags[] initialPutFlags = isAppending
                    ? NO_OVERWRITE_AND_APPEND
                    : NO_OVERWRITE;
            didPutSucceed = lmdbDbi.put(writeTxn, keyBuffer, valueBuffer, initialPutFlags);

            final PutOutcome putOutcome;
            if (didPutSucceed) {
                putOutcome = PutOutcome.newEntry();
            } else {
                // Already have an entry for this key
                if (overwriteExisting) {
                    // Now try again without the overwrite flag set
                    // Can't use MDB_APPEND here as we know there is an existing value, which would make lmdb barf
                    didPutSucceed = lmdbDbi.put(writeTxn, keyBuffer, valueBuffer);

                    putOutcome = didPutSucceed
                            ? PutOutcome.replacedEntry()
                            : PutOutcome.failed();
                } else {
                    putOutcome = PutOutcome.failed();
                }
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("PutOutcome {} for key [{}], value [{}]",
                        putOutcome,
                        ByteBufferUtils.byteBufferInfo(keyBuffer),
                        ByteBufferUtils.byteBufferInfo(valueBuffer));
            }

            return putOutcome;
        } catch (final RuntimeException e) {
            throw new RuntimeException(LogUtil.message("Error putting key {}, value {}",
                    ByteBufferUtils.byteBufferInfo(keyBuffer), ByteBufferUtils.byteBufferInfo(valueBuffer)), e);
        }
    }

    /**
     * This will fail if you are already inside a txn.
     */
    public void putAll(final Map<K, V> entries) {
        lmdbEnvironment.doWithWriteTxn(writeTxn -> {
            try {
                entries.forEach((key, value) -> {
                    try (final PooledByteBuffer pooledKeyBuffer = getPooledKeyBuffer();
                            final PooledByteBuffer pooledValueBuffer = getPooledValueBuffer()) {

                        final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
                        final ByteBuffer valueBuffer = pooledValueBuffer.getByteBuffer();
                        serializeKey(keyBuffer, key);
                        serializeValue(valueBuffer, value);

                        lmdbDbi.put(writeTxn, keyBuffer, valueBuffer);
                    } catch (final Exception e) {
                        throw new RuntimeException(LogUtil.message("Error putting key {}, value {}", key, value), e);
                    }
                });
            } catch (final RuntimeException e) {
                throw new RuntimeException(LogUtil.message("Error putting {} entries", entries.size()), e);
            }
        });
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

        try (final Cursor<ByteBuffer> cursor = lmdbDbi.openCursor(writeTxn)) {

            final boolean isFound = cursor.get(keyBuffer, GetOp.MDB_SET_KEY);
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
                if (!valueBuf.equals(newValueBuf)) {
                    cursor.put(cursor.key(), newValueBuf, PutFlags.MDB_CURRENT);
                } else {
                    LOGGER.trace("put call skipped as buffers are the same");
                }
            }
        }
    }

    /**
     * This will fail if you are already inside a txn.
     *
     * @see AbstractLmdbDb#updateValue(Txn, Object, Consumer)
     */
    public void updateValue(final K key, final Consumer<ByteBuffer> valueBufferConsumer) {
        lmdbEnvironment.doWithWriteTxn(writeTxn ->
                updateValue(writeTxn, key, valueBufferConsumer));
    }

    /**
     * This will fail if you are already inside a txn.
     */
    public boolean delete(final K key) {
        return lmdbEnvironment.getWithWriteTxn(writeTxn -> {
            try {
                final boolean result = delete(writeTxn, key);
                return result;
            } catch (final RuntimeException e) {
                throw new RuntimeException(LogUtil.message("Error deleting key {}", key), e);
            }
        });
    }

    public boolean delete(final Txn<ByteBuffer> writeTxn, final K key) {
        try (final PooledByteBuffer pooledKeyBuffer = getPooledKeyBuffer()) {
            final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
            serializeKey(keyBuffer, key);
            final boolean result = lmdbDbi.delete(writeTxn, keyBuffer);
            LOGGER.trace("delete({}) returned {}", key, result);
            return result;
        } catch (final RuntimeException e) {
            throw new RuntimeException(LogUtil.message("Error deleting key {}", key), e);
        }
    }

    /**
     * This will fail if you are already inside a txn.
     */
    public boolean delete(final ByteBuffer keyBuffer) {
        return lmdbEnvironment.getWithWriteTxn(writeTxn -> {
            try {
                final boolean result = lmdbDbi.delete(writeTxn, keyBuffer);
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("delete({}) returned {}",
                            ByteBufferUtils.byteBufferInfo(keyBuffer),
                            result);
                }
                return result;
            } catch (final RuntimeException e) {
                throw new RuntimeException(LogUtil.message("Error deleting key {}",
                        ByteBufferUtils.byteBufferInfo(keyBuffer)), e);
            }
        });
    }

    /**
     * Delete the entry with the passed keyBuffer.
     *
     * @return True if the entry was found and deleted.
     */
    public boolean delete(final Txn<ByteBuffer> writeTxn, final ByteBuffer keyBuffer) {
        try {
            final boolean result = lmdbDbi.delete(writeTxn, keyBuffer);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("delete(txn, {}) returned {}", ByteBufferUtils.byteBufferInfo(keyBuffer), result);
            }
            return result;
        } catch (final RuntimeException e) {
            throw new RuntimeException(LogUtil.message("Error deleting key {}",
                    ByteBufferUtils.byteBufferInfo(keyBuffer)), e);
        }
    }

    /**
     * Drops all data in the database.
     */
    public void drop() {
        lmdbEnvironment.doWithWriteTxn(this::drop);
    }

    /**
     * Drops all data in the database.
     *
     * @param writeTxn
     */
    public void drop(final Txn<ByteBuffer> writeTxn) {
        LOGGER.debug("Dropping all data in database {}", dbName);
        lmdbDbi.drop(writeTxn);
    }

    /**
     * This will fail if you are already inside a txn.
     */
    public void deleteAll(final Collection<K> keys) {
        lmdbEnvironment.doWithWriteTxn(writeTxn -> {
            try (final PooledByteBuffer pooledKeyBuffer = getPooledKeyBuffer()) {
                keys.forEach(key -> {
                    try {
                        final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
                        serializeKey(keyBuffer, key);
                        lmdbDbi.delete(writeTxn, keyBuffer);
                    } catch (final Exception e) {
                        throw new RuntimeException(LogUtil.message("Error deleting key {}", key), e);
                    }
                });
            } catch (final RuntimeException e) {
                throw new RuntimeException(LogUtil.message("Error deleting {} keys ", keys.size()), e);
            }
        });
    }

    @Override
    public Map<String, String> getDbInfo() {
        return lmdbEnvironment.getDbInfo(lmdbDbi);
    }

    @Override
    public long getEntryCount(final Txn<ByteBuffer> txn) {
        return lmdbDbi.stat(txn).entries;
    }

    @Override
    public long getEntryCount() {
        return lmdbEnvironment.getWithReadTxn(this::getEntryCount);
    }

    /**
     * Dumps the contents of this database to the logger using the key/value
     * serdes to de-serialise the data. Only for use at SMALL scale in tests.
     */
    @Override
    public void logDatabaseContents(final Txn<ByteBuffer> txn, final Consumer<String> logEntryConsumer) {
        LmdbUtils.logDatabaseContents(
                lmdbEnvironment,
                lmdbDbi,
                txn,
                keyBuffer -> deserializeKey(keyBuffer).toString(),
                valueBuffer -> deserializeValue(valueBuffer).toString(),
                logEntryConsumer);
    }

    @Override
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
    public void logDatabaseContents(final Consumer<String> logEntryConsumer) {
        LmdbUtils.logDatabaseContents(
                lmdbEnvironment,
                lmdbDbi,
                byteBuffer -> keySerde.deserialize(byteBuffer).toString(),
                byteBuffer -> valueSerde.deserialize(byteBuffer).toString(),
                logEntryConsumer);
    }

    @Override
    public void logDatabaseContents() {
        logDatabaseContents(LOGGER::info);
    }

    @Override
    public void logRawDatabaseContents(final Txn<ByteBuffer> txn, final Consumer<String> logEntryConsumer) {
        LmdbUtils.logRawDatabaseContents(
                lmdbEnvironment,
                lmdbDbi,
                txn,
                logEntryConsumer);
    }

    @Override
    public void logRawDatabaseContents(final Txn<ByteBuffer> txn) {
        logRawDatabaseContents(txn, LOGGER::info);
    }

    /**
     * Dumps all entries in the database to a single logger entry with one line per database entry.
     * This could potentially return thousands of rows so is only intended for small scale use in
     * testing. Entries are returned in the order they are held in the DB, e.g. a-z (unless the DB
     * is configured with reverse keys). The keys/values are output as hex representations of the
     * byte values.
     */
    @Override
    public void logRawDatabaseContents(final Consumer<String> logEntryConsumer) {
        LmdbUtils.logRawDatabaseContents(
                lmdbEnvironment,
                lmdbDbi,
                logEntryConsumer);
    }

    @Override
    public void logRawDatabaseContents() {
        logRawDatabaseContents(LOGGER::info);
    }

    public K deserializeKey(final ByteBuffer keyBuffer) {
        try {
            return keySerde.deserialize(keyBuffer);
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message("Error de-serialising key buffer [{}]: {}",
                    ByteBufferUtils.byteBufferToHex(keyBuffer), e.getMessage()), e);
        }
    }

    public V deserializeValue(final ByteBuffer valueBuffer) {
        try {
            return valueSerde.deserialize(valueBuffer);
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message("Error de-serialising value buffer [{}]: {}",
                    ByteBufferUtils.byteBufferToHex(valueBuffer), e.getMessage()), e);
        }
    }

    public Entry<K, V> deserializeKeyVal(final CursorIterable.KeyVal<ByteBuffer> keyVal) {
        return Map.entry(deserializeKey(keyVal.key()), deserializeValue(keyVal.val()));
    }

    public void serializeKey(final ByteBuffer keyBuffer, final K key) {
        try {
            keySerde.serialize(keyBuffer, key);
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message("Error serialising key [{}]: {}",
                    key, e.getMessage()), e);
        }
    }

    public void serializeValue(final ByteBuffer valueBuffer, final V value) {
        try {
            valueSerde.serialize(valueBuffer, value);
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message("Error serialising value [{}]: {}",
                    value, e.getMessage()), e);
        }
    }

    public CursorIterable<ByteBuffer> iterate(final Txn<ByteBuffer> txn) {
        return lmdbDbi.iterate(txn);
    }

    public CursorIterable<ByteBuffer> iterate(final Txn<ByteBuffer> txn, final KeyRange<ByteBuffer> range) {
        return lmdbDbi.iterate(txn, range);
    }
}
