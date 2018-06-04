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
import org.lmdbjava.CursorIterator;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
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
 * consists of the hashcode of the {@link RefDataValue} suffixed with a unique identifier. The
 * unique identifier is just a short value to distinguish different {@link RefDataValue} objects
 * that share the same hashcode. On creation of an entry, if an existing entry is found with the same
 * value hashcode but a different value then the next id will be used.
 * <p>
 * The purpose of this table is to de-duplicate the storage of identical reference data values. E.g. if
 * multiple reference data keys are associated with the same reference data value then we only need
 * to store the value one in this table and each key then stores a pointer to it (the {@link ValueStoreKey}.)
 * <p>
 * key (hash|id)    | value
 * -------------------------
 * (1234|00)        | 363838
 * (1234|01)        | 857489
 * (4567|00)        | 263673
 * (7890|00)        | 689390
 */
public class ValueStoreDb extends AbstractLmdbDb<ValueStoreKey, RefDataValue> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValueStoreDb.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(ValueStoreDb.class);

    private static final String DB_NAME = "ValueStore";

    @Inject
    public ValueStoreDb(@Assisted final Env<ByteBuffer> lmdbEnvironment,
                        final ValueStoreKeySerde keySerde,
                        final RefDataValueSerde valueSerde) {
        super(lmdbEnvironment, keySerde, valueSerde, DB_NAME);
    }

    /**
     * Either gets the {@link ValueStoreKey} corresponding to the passed refDataValue
     * from the database or creates the entry in the database and returns the generated
     * key.
     *
     * @return A clone of the {@link ByteBuffer} containing the database key.
     */
    public ValueStoreKey getOrCreate(final Txn<ByteBuffer> writeTxn, final RefDataValue refDataValue) {

        Preconditions.checkArgument(!writeTxn.isReadOnly(), "A write transaction is required");

        LOGGER.debug("getOrCreate called for refDataValue: {}", refDataValue);

        final ByteBuffer valueBuffer = valueSerde.serialize(refDataValue);

        LAMBDA_LOGGER.debug(() ->
                LambdaLogger.buildMessage("valueBuffer: {}", ByteArrayUtils.byteBufferInfo(valueBuffer)));

        ValueStoreKey valueStoreKey = null;

        // build a key range for all possible keys
        final KeyRange<ByteBuffer> keyRange = buildAllIdsForSingleHashValueKeyRange(refDataValue);

        LmdbUtils.logContentsInRange(lmdbEnvironment, lmdbDbi, keyRange);

        logRawDatabaseContents();

        // Use atomics so they can be mutated and then used in lambdas
        final AtomicBoolean isValueInMap = new AtomicBoolean(false);
        final AtomicInteger valuesCount = new AtomicInteger(0);
        ValueStoreKey lastValueStoreKey = null;

        try (CursorIterator<ByteBuffer> cursorIterator = lmdbDbi.iterate(writeTxn, keyRange)) {
            for (final CursorIterator.KeyVal<ByteBuffer> keyVal : cursorIterator.iterable()) {
                valuesCount.incrementAndGet();

                LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("Found entry {} with key {}",
                        valuesCount.get(),
                        LmdbUtils.byteBufferToHex(keyVal.key())));

                final ByteBuffer valueFromDbBuf = keyVal.val();

                LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("Our value {}, db value {}",
                        LmdbUtils.byteBufferToHex(valueBuffer),
                        LmdbUtils.byteBufferToHex(valueFromDbBuf)));

                // we cannot use keyVal outside of the cursor loop so have to either
                // deser it, copy the whole buffer or copy the bit of interest
                lastValueStoreKey = keySerde.deserialize(keyVal.key());
                if (ByteBufferUtils.compareTo(
                        valueBuffer, valueBuffer.position(), valueBuffer.remaining(),
                        valueFromDbBuf, valueFromDbBuf.position(), valueFromDbBuf.remaining()) == 0) {
                    isValueInMap.set(true);
                    LAMBDA_LOGGER.trace(() -> "Values are equal breaking out");
                    break;
                } else {
                    LAMBDA_LOGGER.trace(() -> "Values are not equal");
                }
            }
        }

        LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("isValueInMap: {}, valuesCount {}",
                isValueInMap.get(),
                valuesCount.get()));

        if (isValueInMap.get()) {
            // value is already in the map, so use its key
            LOGGER.trace("Found value");
            valueStoreKey = lastValueStoreKey;
        } else {
            // value is not in the map so we need to add it
            final int uniqueId;
            if (lastValueStoreKey == null) {
                LOGGER.trace("no existing entry for this valueHashCode so use first uniqueId");
                valueStoreKey = ValueStoreKey.lowestKey(refDataValue.hashCode());
            } else {
                // 1 or more entries share our valueHashCode (but with different values)
                // so use the next uniqueId
                valueStoreKey = lastValueStoreKey.nextKey();
                LOGGER.trace("Incrementing key, lastValueStoreKey {}, valueStoreKey {}",
                        lastValueStoreKey, valueStoreKey);

            }
            final ByteBuffer keyBuffer = keySerde.serialize(valueStoreKey);
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
        return Optional.empty();
    }

    private KeyRange<ByteBuffer> buildAllIdsForSingleHashValueKeyRange(final RefDataValue value) {
        ByteBuffer startKey = buildStartKeyBuffer(value);
        ByteBuffer endKey = buildEndKeyBuffer(value);
        return KeyRange.closed(startKey, endKey);
    }

    private ByteBuffer buildStartKeyBuffer(final RefDataValue value) {
        return keySerde.serialize(ValueStoreKey.lowestKey(value.hashCode()));
    }

    private ByteBuffer buildEndKeyBuffer(final RefDataValue value) {
        return keySerde.serialize(ValueStoreKey.highestKey(value.hashCode()));
    }

    public interface Factory {
        ValueStoreDb create(final Env<ByteBuffer> lmdbEnvironment);
    }
}
