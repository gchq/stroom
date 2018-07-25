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

package stroom.refdata.offheapstore.databases;

import com.google.common.base.Preconditions;
import com.google.inject.assistedinject.Assisted;
import org.lmdbjava.Cursor;
import org.lmdbjava.Env;
import org.lmdbjava.GetOp;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.lmdb.AbstractLmdbDb;
import stroom.refdata.lmdb.LmdbUtils;
import stroom.refdata.offheapstore.ByteBufferPool;
import stroom.refdata.offheapstore.ByteBufferUtils;
import stroom.refdata.offheapstore.RefDataValue;
import stroom.refdata.offheapstore.TypedByteBuffer;
import stroom.refdata.offheapstore.ValueStoreKey;
import stroom.refdata.offheapstore.serdes.RefDataValueSerde;
import stroom.refdata.offheapstore.serdes.ValueStoreKeySerde;
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
 * <p>
 * The purpose of this table is to de-duplicate the storage of identical reference data values. E.g. if
 * multiple reference data keys are associated with the same reference data value then we only need
 * to store the value one in this table and each key then stores a pointer to it (the {@link ValueStoreKey}.)
 * <p>
 * key        | value
 * (hash|id)  | (type|referenceCount|valueBytes)
 * ---------------------------------------------
 * (1234|00)  | (0|0001|363838)
 * (1234|01)  | (0|0001|857489)
 * (4567|00)  | (0|0001|263673)
 * (7890|00)  | (0|0001|689390)
 */
public class ValueStoreDb extends AbstractLmdbDb<ValueStoreKey, RefDataValue> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValueStoreDb.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(ValueStoreDb.class);

    private static final String DB_NAME = "ValueStore";

    private final ValueStoreKeySerde keySerde;
    private final RefDataValueSerde valueSerde;

    @Inject
    public ValueStoreDb(@Assisted final Env<ByteBuffer> lmdbEnvironment,
                        final ByteBufferPool byteBufferPool,
                        final ValueStoreKeySerde keySerde,
                        final RefDataValueSerde valueSerde) {
        super(lmdbEnvironment, byteBufferPool, keySerde, valueSerde, DB_NAME);
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
    }

    /**
     * Tests if the passed otherRefDataValue is equal to the value associated with the
     * passed valueStoreKey (if there is one). If there is no value associated with
     * the passed valueStoreKey, false will be returned.
     */
    public boolean areValuesEqual(final Txn<ByteBuffer> txn,
                                  final ValueStoreKey valueStoreKey,
                                  final RefDataValue otherRefDataValue) {

        int currentValueHashCode = valueStoreKey.getValueHashCode();
        int newValueHashCode = otherRefDataValue.valueHashCode();
        boolean areValuesEqual;
        if (currentValueHashCode != newValueHashCode) {
            // valueHashCodes differ so values differ
            areValuesEqual = false;
        } else {
            // valueHashCodes match so need to do a full equality check

            ByteBuffer valueStoreKeyBuf = keySerde.serialize(valueStoreKey);

            Optional<ByteBuffer> optCurrentValueBuf = getAsBytes(txn, valueStoreKeyBuf);

            if (optCurrentValueBuf.isPresent()) {
                ByteBuffer otherRefDataValueBuf = valueSerde.serialize(otherRefDataValue);
                areValuesEqual = valueSerde.areValuesEqual(optCurrentValueBuf.get(), otherRefDataValueBuf);
            } else {
                areValuesEqual = false;
            }
        }
        return areValuesEqual;
    }

    public void deReferenceOrDeleteValue(final Txn<ByteBuffer> writeTxn, final ValueStoreKey valueStoreKey) {
        LOGGER.trace("deReferenceValue({}, {})", writeTxn, valueStoreKey);

        ByteBuffer keyBuffer = keySerde.serialize(valueStoreKey);
        deReferenceOrDeleteValue(writeTxn, keyBuffer);
    }

    /**
     * Decrements the reference count for this value. If the resulting reference count is zero or less
     * the value will be deleted as it is no longer required.
     */
    public void deReferenceOrDeleteValue(final Txn<ByteBuffer> writeTxn, final ByteBuffer keyBuffer) {
        LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("deReferenceValue({}, {})",
                writeTxn, ByteBufferUtils.byteBufferInfo(keyBuffer)));

        try (Cursor<ByteBuffer> cursor = lmdbDbi.openCursor(writeTxn)) {

            boolean isFound = cursor.get(keyBuffer, GetOp.MDB_SET_KEY);
            if (!isFound) {
                throw new RuntimeException(LambdaLogger.buildMessage(
                        "Expecting to find entry for {}", ByteBufferUtils.byteBufferInfo(keyBuffer)));
            }
            final ByteBuffer valueBuf = cursor.val();

            // We run LMDB in its default mode of read only mmaps so we cannot mutate the key/value
            // bytebuffers.  Instead we must copy the content and put the replacement entry.
            // We could run LMDB in MDB_WRITEMAP mode which allows mutation of the buffers (and
            // thus avoids the buffer copy cost) but adds more risk of DB corruption. As we are not
            // doing a high volume of value mutations read-only mode is a safer bet.
            final ByteBuffer newValueBuf = LmdbUtils.copyDirectBuffer(valueBuf);

            int newRefCount = valueSerde.updateReferenceCount(newValueBuf, -1);

            if (newRefCount <= 0) {
                // we had the last ref to this value so we can delete it
                LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("Ref count is zero, derleting entry for key {}",
                        ByteBufferUtils.byteBufferInfo(keyBuffer)));
                cursor.delete();
            } else {
                LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("Updating entry with new ref count {} for key {}",
                        newRefCount,
                        ByteBufferUtils.byteBufferInfo(keyBuffer)));
                cursor.put(cursor.key(), newValueBuf, PutFlags.MDB_CURRENT);
            }
        }
    }

    public ValueStoreKey getOrCreate(final Txn<ByteBuffer> writeTxn,
                                     final RefDataValue refDataValue) {
        return getOrCreate(writeTxn, refDataValue, false);

    }

    /**
     * Either gets the {@link ValueStoreKey} corresponding to the passed refDataValue
     * from the database or creates the entry in the database and returns the generated
     * key.
     *
     * @return A clone of the {@link ByteBuffer} containing the database key.
     */
    public ValueStoreKey getOrCreate(final Txn<ByteBuffer> writeTxn,
                                     final RefDataValue refDataValue,
                                     final boolean isOverwrite) {

        Preconditions.checkArgument(!writeTxn.isReadOnly(), "A write transaction is required");

        if (refDataValue.getReferenceCount() != 1) {
            throw new RuntimeException(LambdaLogger.buildMessage(
                    "Expecting refDataValue.getReferenceCount to be 1 at this point, found {}",
                    refDataValue.getReferenceCount()));
        }

        LOGGER.trace("getOrCreate called for refDataValue: {}, isOverwrite", refDataValue, isOverwrite);

        final ByteBuffer valueBuffer = valueSerde.serialize(refDataValue);

        LAMBDA_LOGGER.trace(() ->
                LambdaLogger.buildMessage("valueBuffer: {}", ByteBufferUtils.byteBufferInfo(valueBuffer)));

        ValueStoreKey valueStoreKey = null;

        // Use atomics so they can be mutated and then used in lambdas
        final AtomicBoolean isValueInDb = new AtomicBoolean(false);
        final AtomicInteger valuesCount = new AtomicInteger(0);
        short lastKeyId = -1;
        short firstUnusedKeyId = -1;
        final ByteBuffer startKey = buildStartKeyBuffer(refDataValue);
        ByteBuffer lastKeyBufferClone = null;

        try (Cursor<ByteBuffer> cursor = lmdbDbi.openCursor(writeTxn)) {
            //get this key or one greater than it
            boolean isFound = cursor.get(startKey, GetOp.MDB_SET_RANGE);

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

                // we cannot use keyVal outside of the cursor loop so have to
                // copy the content
                if (lastKeyBufferClone == null) {
                    // make a new buffer from the cursor key content
                    lastKeyBufferClone = LmdbUtils.copyDirectBuffer(keyFromDbBuf);
                } else {
                    // copy the cursor key content into our mutable buffer
                    // TODO we could just copy the 2 bytes that make up the ID as that is the only bit that changes
                    // though this only saves copying the extra 4 bytes
                    lastKeyBufferClone.put(keyFromDbBuf);
                    lastKeyBufferClone.flip();
                }

                // see if the found value is identical to the value passed in
                if (valueSerde.areValuesEqual(valueBuffer, valueFromDbBuf)) {
                    isValueInDb.set(true);
                    LAMBDA_LOGGER.trace(() -> "Found our value so incrementing its ref count and breaking out");

                    // TODO this copy could be expensive as some of the value can be many hundreds of bytes
                    // May be preferable to hold the ref count in a separate table (ValueReferenceCountDb)
                    // as the copy/put of those 4 bytes will be cheaper but at the expense of an extra cursor get op
                    final ByteBuffer valueBufClone = LmdbUtils.copyDirectBuffer(valueFromDbBuf);
                    // we have an interest in this value so increment the reference count
                    int newRefCount = valueSerde.incrementReferenceCount(valueBufClone);

                    LOGGER.trace("newRefCount is {}", newRefCount);

                    try {
                        //flip the buffer so it is ready for reading by LMDB
                        keyFromDbBuf.flip();
                        cursor.put(keyFromDbBuf, valueBufClone, PutFlags.MDB_CURRENT);
                    } catch (Exception e) {
                        throw new RuntimeException(LambdaLogger.buildMessage("Error doing cursor.put with [{}] & [{}]",
                                ByteBufferUtils.byteBufferInfo(keyFromDbBuf),
                                ByteBufferUtils.byteBufferInfo(valueBufClone)), e);
                    }

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

        if (isValueInDb.get()) {
            // value is already in the map, so use its key
            LOGGER.trace("Found value");
            valueStoreKey = keySerde.deserialize(lastKeyBufferClone);
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
                LOGGER.trace("Incrementing key, valueStoreKey {}", valueStoreKey);
            }
            valueStoreKey = keySerde.deserialize(keyBuffer);
            boolean didPutSucceed = put(writeTxn, keyBuffer, valueBuffer, false);
            if (!didPutSucceed) {
                throw new RuntimeException(LambdaLogger.buildMessage("Put failed for key: {}, value {}",
                        ByteBufferUtils.byteBufferInfo(keyBuffer),
                        ByteBufferUtils.byteBufferInfo(valueBuffer)));
            }
        }

        return valueStoreKey;
    }

    public Optional<RefDataValue> get(final Txn<ByteBuffer> txn, final ByteBuffer keyBuffer) {
        return getAsBytes(txn, keyBuffer)
                .map(valueSerde::deserialize);
    }

    public Optional<TypedByteBuffer> getValueBytes(final Txn<ByteBuffer> txn, final ByteBuffer keyBuffer) {
        return getAsBytes(txn, keyBuffer)
                .map(valueSerde::extractTypedValueBuffer);
    }

    private ByteBuffer buildStartKeyBuffer(final RefDataValue value) {
        return keySerde.serialize(ValueStoreKey.lowestKey(value.valueHashCode()));
    }

    public interface Factory {
        ValueStoreDb create(final Env<ByteBuffer> lmdbEnvironment);
    }
}
