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

package stroom.pipeline.refdata.store.offheapstore.databases;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.bytebuffer.PooledByteBufferOutputStream;
import stroom.lmdb.AbstractLmdbDb;
import stroom.lmdb.EntryConsumer;
import stroom.lmdb.LmdbUtils;
import stroom.lmdb.PutOutcome;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.StagingValue;
import stroom.pipeline.refdata.store.ValueStoreHashAlgorithm;
import stroom.pipeline.refdata.store.offheapstore.RefDataLmdbEnv;
import stroom.pipeline.refdata.store.offheapstore.ValueStoreKey;
import stroom.pipeline.refdata.store.offheapstore.serdes.GenericRefDataValueSerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.ValueStoreKeySerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.common.base.Preconditions;
import com.google.inject.assistedinject.Assisted;
import jakarta.inject.Inject;
import org.lmdbjava.Cursor;
import org.lmdbjava.GetOp;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * A database to hold reference data values using a generated unique surrogate key. The key
 * consists of the valueHashcode of the {@link RefDataValue} suffixed with a unique identifier. The
 * unique identifier is just a short value to distinguish different {@link RefDataValue} objects
 * that share the same valueHashcode. On creation of an entry, if an existing entry is found with the same
 * valueHashcode but a different value then the next id will be used.
 * The key structure is identical to the structure of the value in {@link KeyValueStoreDb} and {@link RangeStoreDb}
 * databases. The key structure is also identical to the key structure in the {@link ValueStoreMetaDb}
 * database. Each entry in this DB has a corresponding entry in the {@link ValueStoreMetaDb} which holds
 * the type information and reference counts.
 * For this to perform we need to use a hash with minimal clashes else we have to scan over multiple
 * values with the same hash each time.
 * <p>
 * The purpose of this table is to de-duplicate the storage of identical reference data values. E.g. if
 * multiple reference data keys are associated with the same reference data value then we only need
 * to store the value one in this table and each key then stores a pointer to it (the {@link ValueStoreKey}.)
 * <p>
 * <pre>
 * key        | value
 * (hash|id)  | (valueBytes)
 * ---------------------------------------------
 * (1234|00)  | (363838)
 * (1234|01)  | (857489)
 * (4567|00)  | (263673)
 * (7890|00)  | (689390)
 * </pre>
 * <p>
 * As values are deleted it means there can be gaps in the ids for that hash code. These gaps will
 * be reused to ensure the ids are not exhausted.
 */
public class ValueStoreDb extends AbstractLmdbDb<ValueStoreKey, RefDataValue> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValueStoreDb.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(ValueStoreDb.class);

    private static final int BUFFER_OUTPUT_STREAM_INITIAL_CAPACITY = 1_000;

    public static final String DB_NAME = "ValueStore";

    private final ValueStoreKeySerde keySerde;
    // the values in the DB are not typed so on retrieval we have no idea what type they are so have to extract them
    // in their raw form and the caller can do the deserialisation. On insertion if we are given a typed object
    // we can serialise them appropriately.
    private final GenericRefDataValueSerde valueSerde;
    private final ValueStoreHashAlgorithm valueStoreHashAlgorithm;
    private final PooledByteBufferOutputStream.Factory pooledByteBufferOutputStreamFactory;

    @Inject
    public ValueStoreDb(@Assisted final RefDataLmdbEnv lmdbEnvironment,
                        final ByteBufferPool byteBufferPool,
                        final ValueStoreKeySerde keySerde,
                        final GenericRefDataValueSerde valueSerde,
                        final ValueStoreHashAlgorithm valueStoreHashAlgorithm,
                        final PooledByteBufferOutputStream.Factory pooledByteBufferOutputStreamFactory) {

        super(lmdbEnvironment.getEnvironment(), byteBufferPool, keySerde, valueSerde, DB_NAME);
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
        this.valueStoreHashAlgorithm = valueStoreHashAlgorithm;
        this.pooledByteBufferOutputStreamFactory = pooledByteBufferOutputStreamFactory;
        lmdbEnvironment.registerDatabases(this);
    }

    public ValueStoreHashAlgorithm getValueStoreHashAlgorithm() {
        return valueStoreHashAlgorithm;
    }

    private PooledByteBufferOutputStream getPooledByteBufferOutputStream() {
        return pooledByteBufferOutputStreamFactory.create(BUFFER_OUTPUT_STREAM_INITIAL_CAPACITY);
    }

    /**
     * Tests if the passed newRefDataValue is equal to the value associated with the
     * passed valueStoreKey (if there is one). If there is no value associated with
     * the passed valueStoreKey, false will be returned.
     */
    public boolean areValuesEqual(final Txn<ByteBuffer> txn,
                                  final ByteBuffer valueStoreKeyBuffer,
                                  final StagingValue newRefDataValue) {

        final long currentValueHashCode = ValueStoreKeySerde.extractValueHashCode(valueStoreKeyBuffer);
        final long newValueHashCode = newRefDataValue.getValueHashCode();
        final boolean areValuesEqual;
        if (currentValueHashCode != newValueHashCode) {
            // valueHashCodes differ so values differ
            areValuesEqual = false;
        } else {
            // valueHashCodes match so need to do a full equality check
            final ByteBuffer currentValueBuf = getAsBytes(txn, valueStoreKeyBuffer)
                    .orElseThrow(() -> new RuntimeException("The value should exist at this point"));

            final ByteBuffer newValueBuffer = newRefDataValue.getValueBuffer();

            areValuesEqual = currentValueBuf.equals(newValueBuffer);
            if (!areValuesEqual) {
                // Hopefully this won't happen often, logging in case we want to track collisions.
                LAMBDA_LOGGER.debug("Hash collision");
            }
        }
        return areValuesEqual;
    }

//    /**
//     * For testing use
//     */
//    ByteBuffer getOrCreateKey(final Txn<ByteBuffer> writeTxn,
//                              final StagingValue refDataValue,
//                              final PooledByteBuffer valueStoreKeyPooledBuffer,
//                              final EntryConsumer onExistingEntryAction,
//                              final EntryConsumer onNewEntryAction) {
//
//        return getOrCreateKey(
//                writeTxn,
//                refDataValue,
//                valueStoreKeyPooledBuffer,
//                false,
//                onExistingEntryAction,
//                onNewEntryAction);
//    }

    /**
     * Either gets the {@link ValueStoreKey} corresponding to the passed refDataValue
     * from the database if we already hold it or creates the entry in the database
     * and returns the generated key. To determine if we already hold it, the value
     * will be hashed and that hash will be looked up and any matches tested for equality
     * using the serialised bytes.
     * <p>
     * onExistingValueAction Action to perform when the value is found to already exist
     *
     * @param valueStoreKeyPooledBuffer A pooled buffer to use for the return value
     * @return A clone of the {@link ByteBuffer} containing the database key.
     */
    public ByteBuffer getOrCreateKey(final Txn<ByteBuffer> writeTxn,
                                     final StagingValue refDataValue,
                                     final PooledByteBuffer valueStoreKeyPooledBuffer,
                                     final EntryConsumer onExistingEntryAction,
                                     final EntryConsumer onNewEntryAction) {

        Preconditions.checkArgument(!writeTxn.isReadOnly(), "A write transaction is required");

        LOGGER.trace("getOrCreate called for refDataValue: {}", refDataValue);

        final ByteBuffer valueBuffer = refDataValue.getValueBuffer();

        LAMBDA_LOGGER.trace(() ->
                LogUtil.message("valueBuffer: {}", ByteBufferUtils.byteBufferInfo(valueBuffer)));

        // Use atomics so they can be mutated and then used in lambdas
        final AtomicBoolean isValueInDb = new AtomicBoolean(false);
        final AtomicInteger valuesCount = new AtomicInteger(0);
        short firstUnusedKeyId = -1;
        short firstUsedKeyId = -1;

        // We have to allocate a new ByteBuffer here as we may/may not return it
        final ByteBuffer startKey = buildStartKeyBuffer(refDataValue, valueStoreKeyPooledBuffer);
        ByteBuffer lastKeyBufferClone = null;

        try (final Cursor<ByteBuffer> cursor = getLmdbDbi().openCursor(writeTxn)) {
            // get this key or one greater than it
            boolean isFound = cursor.get(startKey, GetOp.MDB_SET_RANGE);

            short lastKeyId = -1;
            while (isFound) {
                if (!ValueStoreKeySerde.valueHashCodeEquals(startKey, cursor.key())) {
                    // cursor key has a different hashcode to ours so we can stop looping
                    break;
                }
                valuesCount.incrementAndGet();

                final ByteBuffer valueFromDbBuf = cursor.val();
                final ByteBuffer keyFromDbBuf = cursor.key();
                final short thisKeyId = ValueStoreKeySerde.extractId(keyFromDbBuf);

                // Because we have removal of entries we can end up with sparse id sequences
                // therefore capture the first used and unused IDs so we can use it if we need to put a
                // new key/value.
                if (firstUsedKeyId == -1) {
                    // Capture the first id we find for this hash
                    firstUsedKeyId = thisKeyId;
                }
                if (firstUnusedKeyId == -1 && firstUsedKeyId > ValueStoreKey.MIN_UNIQUE_ID) {
                    // There is a gap before the first used key so use the lowest id
                    // e.g. 2,3,7, so use 0
                    firstUnusedKeyId = ValueStoreKey.MIN_UNIQUE_ID;
                } else if (firstUnusedKeyId == -1 && lastKeyId != -1) {
                    if (thisKeyId <= lastKeyId) {
                        throw new RuntimeException(LogUtil.message(
                                "thisKeyId [{}] should be greater than lastId [{}]", thisKeyId, lastKeyId));
                    }
                    if ((thisKeyId - lastKeyId) > 1) {
                        // There is a gap between this id and the last so use one after the last
                        // e.g. 0,1,2,3,7, so use 4
                        firstUnusedKeyId = (short) (lastKeyId + 1);
                    }
                }
                lastKeyId = thisKeyId;

                LAMBDA_LOGGER.trace(() -> LogUtil.message("Our value {}, db value {}",
                        LmdbUtils.byteBufferToHex(valueBuffer),
                        LmdbUtils.byteBufferToHex(valueFromDbBuf)));

                // we cannot use keyVal outside of the cursor loop so have to copy the content
                if (lastKeyBufferClone == null) {
                    // make a new buffer from the cursor key content
                    lastKeyBufferClone = valueStoreKeyPooledBuffer.getByteBuffer();
                }
                lastKeyBufferClone.clear();

                // copy the cursor key content out of the LMDB managed buffer and into our passed in one
                ByteBufferUtils.copy(keyFromDbBuf, lastKeyBufferClone);

                // see if the found value is identical to the value passed in
                if (valueBuffer.equals(valueFromDbBuf)) {
                    isValueInDb.set(true);
                    LAMBDA_LOGGER.trace(() ->
                            "Found our value so incrementing its ref count and breaking out");

                    // perform any entry found actions
                    onExistingEntryAction.accept(writeTxn, keyFromDbBuf, valueFromDbBuf);

                    break;
                } else {
                    LAMBDA_LOGGER.trace(() -> "Values are not equal, keep looking");
                }
                // advance cursor
                isFound = cursor.next();
            }
        }

        LAMBDA_LOGGER.trace(() -> LogUtil.message("isValueInMap: {}, valuesCount {}",
                isValueInDb.get(),
                valuesCount.get()));

        final ByteBuffer valueStoreKeyBuffer;

        if (isValueInDb.get()) {
            // value is already in the map, so use its key
            LOGGER.trace("Found value");
            valueStoreKeyBuffer = lastKeyBufferClone;
        } else {
            // value is not in the map so we need to add it
            final ByteBuffer keyBuffer;
            if (lastKeyBufferClone == null) {
                LOGGER.trace("no existing entry for this valueHashCode so use first uniqueId");
                // the start key is the first key for that valueHashcode so use that
                keyBuffer = startKey;
            } else {
                // One or more entries share our valueHashCode (but with different values)
                // so give it a new ID value
                if (firstUnusedKeyId != -1) {
                    ValueStoreKeySerde.updateId(lastKeyBufferClone, firstUnusedKeyId);
                } else {
                    // No gaps in the ids so just use one more than last one
                    ValueStoreKeySerde.incrementId(lastKeyBufferClone);
                }
                keyBuffer = lastKeyBufferClone;
//                LOGGER.trace("Incrementing key, valueStoreKey {}", valueStoreKey);
            }
            valueStoreKeyBuffer = keyBuffer;
            final PutOutcome putOutcome = put(writeTxn, keyBuffer, valueBuffer, false);

            // perform any new entry created actions
            onNewEntryAction.accept(writeTxn, keyBuffer, valueBuffer);

            if (!putOutcome.isSuccess()) {
                throw new RuntimeException(LogUtil.message("Put failed for key: {}, value {}",
                        ByteBufferUtils.byteBufferInfo(keyBuffer),
                        ByteBufferUtils.byteBufferInfo(valueBuffer)));
            }
        }
        return valueStoreKeyBuffer;
    }

    public Optional<RefDataValue> get(final Txn<ByteBuffer> txn,
                                      final ByteBuffer keyBuffer,
                                      final byte typeId) {
        return getAsBytes(txn, keyBuffer)
                .map(valueBuffer ->
                        valueSerde.deserialize(valueBuffer, typeId));
    }

    private ByteBuffer buildStartKeyBuffer(final StagingValue value,
                                           final PooledByteBuffer pooledKeyBuffer) {
        return keySerde.serialize(
                pooledKeyBuffer::getByteBuffer,
                ValueStoreKey.lowestKey(value.getValueHashCode()));
    }


    // --------------------------------------------------------------------------------


    public interface Factory {

        ValueStoreDb create(final RefDataLmdbEnv lmdbEnvironment);
    }
}
