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
import org.apache.hadoop.hbase.util.ByteBufferUtils;
import org.lmdbjava.Cursor;
import org.lmdbjava.CursorIterator;
import org.lmdbjava.Env;
import org.lmdbjava.GetOp;
import org.lmdbjava.KeyRange;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.lmdb.AbstractLmdbDb;
import stroom.refdata.lmdb.LmdbUtils;
import stroom.refdata.offheapstore.ByteArrayUtils;
import stroom.refdata.offheapstore.RefDataValue;
import stroom.refdata.offheapstore.serdes.RefDataValueSerde;
import stroom.refdata.offheapstore.ValueStoreKey;
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
                        final ValueStoreKeySerde keySerde,
                        final RefDataValueSerde valueSerde) {
        super(lmdbEnvironment, keySerde, valueSerde, DB_NAME);
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
        boolean areValuesDifferent;
        if (currentValueHashCode != newValueHashCode) {
            // valueHashCodes differ so values differ
            areValuesDifferent = true;
        } else {
            // valueHashCodes match so need to do a full equality check

            ByteBuffer valueStoreKeyBuf = keySerde.serialize(valueStoreKey);

            Optional<ByteBuffer> optCurrentValueBuf = getAsBytes(txn, valueStoreKeyBuf);

            if (optCurrentValueBuf.isPresent()) {
                ByteBuffer otherRefDataValueBuf = valueSerde.serialize(otherRefDataValue);
                areValuesDifferent = valueSerde.areValuesEqual(optCurrentValueBuf.get(), otherRefDataValueBuf);
            } else {
                areValuesDifferent = false;
            }
        }
        return areValuesDifferent;
    }

    public void deReferenceValue(final Txn<ByteBuffer> writeTxn, final ValueStoreKey valueStoreKey) {
        ByteBuffer keyBuffer = keySerde.serialize(valueStoreKey);
        deReferenceValue(writeTxn, keyBuffer);
    }
    /**
     * Decrements the reference count for this value. If the resulting reference count is zero or less
     * the value will be deleted as it is no longer required.
     */
    public void deReferenceValue(final Txn<ByteBuffer> writeTxn, final ByteBuffer keyBuffer) {
        try (Cursor<ByteBuffer> cursor = lmdbDbi.openCursor(writeTxn)) {

            boolean isFound = cursor.get(keyBuffer, GetOp.MDB_SET_KEY);
            if (!isFound) {
                throw new RuntimeException(LambdaLogger.buildMessage(
                        "Expecting to find entry for {}", ByteArrayUtils.byteBufferInfo(keyBuffer)));
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
                cursor.delete();
            } else {
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

        LOGGER.debug("getOrCreate called for refDataValue: {}, isOverwrite", refDataValue, isOverwrite);

        final ByteBuffer valueBuffer = valueSerde.serialize(refDataValue);

        LAMBDA_LOGGER.debug(() ->
                LambdaLogger.buildMessage("valueBuffer: {}", ByteArrayUtils.byteBufferInfo(valueBuffer)));

        ValueStoreKey valueStoreKey = null;

        // build a key range for all possible keys
        final KeyRange<ByteBuffer> keyRange = buildAllIdsForSingleHashValueKeyRange(refDataValue);

        // these two lines are useful for debugging with small data volumes
//        LmdbUtils.logContentsInRange(lmdbEnvironment, lmdbDbi, keyRange);
//        logRawDatabaseContents();

        // Use atomics so they can be mutated and then used in lambdas
        final AtomicBoolean isValueInMap = new AtomicBoolean(false);
        final AtomicInteger valuesCount = new AtomicInteger(0);
        ByteBuffer lastKeyBufferClone = null;
        short lastKeyId = -1;
        short firstUnusedKeyId = -1;

        try (Cursor<ByteBuffer> cursor = lmdbDbi.openCursor(writeTxn)) {
            ByteBuffer startKey = buildStartKeyBuffer(refDataValue);
            cursor.get(startKey, GetOp.MDB_SET_RANGE);

            while
            cursor.next()
        }

        try (CursorIterator<ByteBuffer> cursorIterator = lmdbDbi.iterate(writeTxn, keyRange)) {
            for (final CursorIterator.KeyVal<ByteBuffer> keyVal : cursorIterator.iterable()) {
                valuesCount.incrementAndGet();

                LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("Found entry {} with key {}",
                        valuesCount.get(),
                        LmdbUtils.byteBufferToHex(keyVal.key())));

                final ByteBuffer valueFromDbBuf = keyVal.val();
                final ByteBuffer keyFromDbBuf = keyVal.key();
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
                    lastKeyBufferClone.put(keyFromDbBuf);
                    lastKeyBufferClone.flip();
                }

                // see if the found value is identical to the value passed in
                if(valueSerde.areValuesEqual(valueBuffer, valueFromDbBuf)) {
                    isValueInMap.set(true);
                    LAMBDA_LOGGER.trace(() -> "Found our value so breaking out");


                    //TODO we want to copy the value buffer and increment the ref count
                    // as we have a new association with it


                    break;
                } else {
                    LAMBDA_LOGGER.trace(() -> "Values are not equal, keep looking");
                }
            }
        }

        LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("isValueInMap: {}, valuesCount {}",
                isValueInMap.get(),
                valuesCount.get()));

        if (isValueInMap.get()) {
            // value is already in the map, so use its key
            LOGGER.trace("Found value");
            valueStoreKey = keySerde.deserialize(lastKeyBufferClone);
        } else {
            // value is not in the map so we need to add it
            final ByteBuffer keyBuffer;
            if (lastKeyBufferClone == null) {
                LOGGER.trace("no existing entry for this valueHashCode so use first uniqueId");
                // the start key is the first key for that valueHashcode so use that
                keyBuffer = keyRange.getStart();
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
                        ByteArrayUtils.byteBufferInfo(keyBuffer),
                        ByteArrayUtils.byteBufferInfo(valueBuffer)));
            }
        }

        return valueStoreKey;
    }

    public Optional<RefDataValue> get(final Txn<ByteBuffer> txn, final ByteBuffer keyBuffer) {
        return getAsBytes(txn, keyBuffer)
                .map(valueSerde::deserialize);
    }

    private KeyRange<ByteBuffer> buildAllIdsForSingleHashValueKeyRange(final RefDataValue value) {
        ByteBuffer startKey = buildStartKeyBuffer(value);
        ByteBuffer endKey = buildEndKeyBuffer(value);
        return KeyRange.closed(startKey, endKey);
    }

    private ByteBuffer buildStartKeyBuffer(final RefDataValue value) {
        return keySerde.serialize(ValueStoreKey.lowestKey(value.valueHashCode()));
    }

    private ByteBuffer buildEndKeyBuffer(final RefDataValue value) {
        return keySerde.serialize(ValueStoreKey.highestKey(value.valueHashCode()));
    }

    public interface Factory {
        ValueStoreDb create(final Env<ByteBuffer> lmdbEnvironment);
    }
}
