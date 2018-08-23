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

package stroom.refdata.store.offheapstore.databases;

import com.google.common.base.Preconditions;
import com.google.inject.assistedinject.Assisted;
import org.lmdbjava.Cursor;
import org.lmdbjava.Env;
import org.lmdbjava.GetOp;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.store.offheapstore.lmdb.AbstractLmdbDb;
import stroom.refdata.store.offheapstore.lmdb.EntryConsumer;
import stroom.refdata.store.offheapstore.lmdb.LmdbUtils;
import stroom.refdata.util.ByteBufferPool;
import stroom.refdata.util.ByteBufferUtils;
import stroom.refdata.util.PooledByteBuffer;
import stroom.refdata.store.RefDataValue;
import stroom.refdata.store.offheapstore.ValueStoreKey;
import stroom.refdata.store.offheapstore.serdes.GenericRefDataValueSerde;
import stroom.refdata.store.offheapstore.serdes.ValueStoreKeySerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
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
 * <p>
 * The purpose of this table is to de-duplicate the storage of identical reference data values. E.g. if
 * multiple reference data keys are associated with the same reference data value then we only need
 * to store the value one in this table and each key then stores a pointer to it (the {@link ValueStoreKey}.)
 * <p>
 * key        | value
 * (hash|id)  | (valueBytes)
 * ---------------------------------------------
 * (1234|00)  | (363838)
 * (1234|01)  | (857489)
 * (4567|00)  | (263673)
 * (7890|00)  | (689390)
 */
public class ValueStoreDb extends AbstractLmdbDb<ValueStoreKey, RefDataValue> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValueStoreDb.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(ValueStoreDb.class);

    public static final String DB_NAME = "ValueStore";

    private final ValueStoreKeySerde keySerde;
    // the values in the DB are not typed so on retrieval we have no idea what type they are so have to extract them
    // in their raw form and the caller can do the deserialisation. On insertion if we are given a typed object
    // we can serialise them appropriately.
    private final GenericRefDataValueSerde valueSerde;

    @Inject
    public ValueStoreDb(@Assisted final Env<ByteBuffer> lmdbEnvironment,
                        final ByteBufferPool byteBufferPool,
                        final ValueStoreKeySerde keySerde,
                        final GenericRefDataValueSerde valueSerde) {

        super(lmdbEnvironment, byteBufferPool, keySerde, valueSerde, DB_NAME);
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
    }

    /**
     * Tests if the passed newRefDataValue is equal to the value associated with the
     * passed valueStoreKey (if there is one). If there is no value associated with
     * the passed valueStoreKey, false will be returned.
     */
    public boolean areValuesEqual(final Txn<ByteBuffer> txn,
                                  final ByteBuffer valueStoreKeyBuffer,
                                  final RefDataValue newRefDataValue) {

        int currentValueHashCode = keySerde.extractValueHashCode(valueStoreKeyBuffer);
        int newValueHashCode = newRefDataValue.getValueHashCode();
        boolean areValuesEqual;
        if (currentValueHashCode != newValueHashCode) {
            // valueHashCodes differ so values differ
            areValuesEqual = false;
        } else {
            // valueHashCodes match so need to do a full equality check
            try (final PooledByteBuffer newRefDataValuePooledBuf = getPooledValueBuffer()) {

                Optional<ByteBuffer> optCurrentValueBuf = getAsBytes(txn, valueStoreKeyBuffer);

                if (optCurrentValueBuf.isPresent()) {
                    ByteBuffer newValueBuffer = valueSerde.serialize(newRefDataValuePooledBuf::getByteBuffer, newRefDataValue);

                    areValuesEqual = optCurrentValueBuf.get().equals(newValueBuffer);
//                    areValuesEqual = valueSerde.areValuesEqual(
//                            optCurrentValueBuf.get(), newRefDataValuePooledBuf.getByteBuffer());
                } else {
                    areValuesEqual = false;
                }
            }
        }
        return areValuesEqual;
    }

    ByteBuffer getOrCreateKey(final Txn<ByteBuffer> writeTxn,
                              final RefDataValue refDataValue,
                              final PooledByteBuffer valueStoreKeyPooledBuffer,
                              final EntryConsumer onExistingEntryAction,
                              final EntryConsumer onNewEntryAction) {

        return getOrCreateKey(
                writeTxn,
                refDataValue,
                valueStoreKeyPooledBuffer,
                false,
                onExistingEntryAction,
                onNewEntryAction);
    }

    /**
     * Either gets the {@link ValueStoreKey} corresponding to the passed refDataValue
     * from the database or creates the entry in the database and returns the generated
     * key.
     * <p>
     * onExistingValueAction Action to perform when the value is found to already exist
     *
     * @return A clone of the {@link ByteBuffer} containing the database key.
     */
    public ByteBuffer getOrCreateKey(final Txn<ByteBuffer> writeTxn,
                                     final RefDataValue refDataValue,
                                     final PooledByteBuffer valueStoreKeyPooledBuffer,
                                     final boolean isOverwrite,
                                     final EntryConsumer onExistingEntryAction,
                                     final EntryConsumer onNewEntryAction) {

        Preconditions.checkArgument(!writeTxn.isReadOnly(), "A write transaction is required");

        LOGGER.trace("getOrCreate called for refDataValue: {}, isOverwrite", refDataValue, isOverwrite);

        try (final PooledByteBuffer pooledValueBuffer = getPooledValueBuffer()) {
//            final ByteBuffer valueBuffer = pooledValueBuffer.getByteBuffer();
//            valueSerde.serialize(valueBuffer, refDataValue);

            final ByteBuffer valueBuffer = valueSerde.serialize(pooledValueBuffer::getByteBuffer, refDataValue);

            LAMBDA_LOGGER.trace(() ->
                    LambdaLogger.buildMessage("valueBuffer: {}", ByteBufferUtils.byteBufferInfo(valueBuffer)));

            // Use atomics so they can be mutated and then used in lambdas
            final AtomicBoolean isValueInDb = new AtomicBoolean(false);
            final AtomicInteger valuesCount = new AtomicInteger(0);
            short firstUnusedKeyId = -1;

            // TODO we may instead be able to use one from the pool then if we do the put()
            // get the key buffer from the txn and return that.

            // We have to allocate a new ByteBuffer here as we may/may not return it
            final ByteBuffer startKey = buildStartKeyBuffer(refDataValue);
            ByteBuffer lastKeyBufferClone = null;

            try (Cursor<ByteBuffer> cursor = getLmdbDbi().openCursor(writeTxn)) {
                //get this key or one greater than it
                boolean isFound = cursor.get(startKey, GetOp.MDB_SET_RANGE);

                short lastKeyId = -1;
                while (isFound) {
                    if (ValueStoreKeySerde.compareValueHashCode(startKey, cursor.key()) != 0) {
                        // cursor key has a different hashcode so we can stop looping
                        break;
                    }
                    valuesCount.incrementAndGet();

                    final ByteBuffer valueFromDbBuf = cursor.val();
                    final ByteBuffer keyFromDbBuf = cursor.key();
                    short thisKeyId = ValueStoreKeySerde.extractId(keyFromDbBuf);

                    // Because we have removal of entries we can end up with sparse id sequences
                    // therefore capture the first unused ID so we can use it if we need to put a
                    // new key/value.
                    if (firstUnusedKeyId == -1 && lastKeyId != -1) {
                        if (thisKeyId <= lastKeyId) {
                            throw new RuntimeException(LambdaLogger.buildMessage(
                                    "thisKeyId [{}] should be greater than lastId [{}]", thisKeyId, lastKeyId));
                        }
                        if ((thisKeyId - lastKeyId) > 1) {
                            firstUnusedKeyId = (short) (lastKeyId + 1);
                        }
                    }
                    lastKeyId = thisKeyId;

                    LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("Our value {}, db value {}",
                            LmdbUtils.byteBufferToHex(valueBuffer),
                            LmdbUtils.byteBufferToHex(valueFromDbBuf)));

                    // we cannot use keyVal outside of the cursor loop so have to copy the content
                    if (lastKeyBufferClone == null) {
                        // make a new buffer from the cursor key content
                        lastKeyBufferClone = valueStoreKeyPooledBuffer.getByteBuffer();
                    } else {
                        lastKeyBufferClone.clear();
                    }

                    // copy the cursor key content into our mutable buffer
                    ByteBufferUtils.copy(keyFromDbBuf, lastKeyBufferClone);

                    // see if the found value is identical to the value passed in
                    if (valueBuffer.equals(valueFromDbBuf)) {
                        isValueInDb.set(true);
                        LAMBDA_LOGGER.trace(() -> "Found our value so incrementing its ref count and breaking out");

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

            LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("isValueInMap: {}, valuesCount {}",
                    isValueInDb.get(),
                    valuesCount.get()));

            ByteBuffer valueStoreKeyBuffer;

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
                        ValueStoreKeySerde.incrementId(lastKeyBufferClone);
                    }
                    keyBuffer = lastKeyBufferClone;
//                LOGGER.trace("Incrementing key, valueStoreKey {}", valueStoreKey);
                }
                valueStoreKeyBuffer = keyBuffer;
                boolean didPutSucceed = put(writeTxn, keyBuffer, valueBuffer, false);

                // perform any new entry created actions
                onNewEntryAction.accept(writeTxn, keyBuffer, valueBuffer);

                if (!didPutSucceed) {
                    throw new RuntimeException(LambdaLogger.buildMessage("Put failed for key: {}, value {}",
                            ByteBufferUtils.byteBufferInfo(keyBuffer),
                            ByteBufferUtils.byteBufferInfo(valueBuffer)));
                }
            }
            return valueStoreKeyBuffer;
        }
    }

    public Optional<RefDataValue> get(final Txn<ByteBuffer> txn, final ByteBuffer keyBuffer, final int typeId) {
        return getAsBytes(txn, keyBuffer)
                .map(valueBuffer ->
                        valueSerde.deserialize(valueBuffer, typeId));
    }

    private ByteBuffer buildStartKeyBuffer(final RefDataValue value) {
        return keySerde.serialize(ValueStoreKey.lowestKey(value.getValueHashCode()));
    }

    public interface Factory {
        ValueStoreDb create(final Env<ByteBuffer> lmdbEnvironment);
    }
}
