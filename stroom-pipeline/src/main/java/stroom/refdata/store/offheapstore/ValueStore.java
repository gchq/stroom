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

package stroom.refdata.store.offheapstore;

import org.lmdbjava.Env;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.store.RefDataValue;
import stroom.refdata.store.offheapstore.databases.ValueStoreDb;
import stroom.refdata.store.offheapstore.databases.ValueStoreMetaDb;
import stroom.refdata.util.PooledByteBuffer;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Class to manage all interactions with the {@link ValueStoreDb} and {@link ValueStoreMetaDb}
 * databases. This is to ensure that the two are kept in sync as every entry in {@link ValueStoreDb}
 * should have a corresponding entry in {@link ValueStoreMetaDb}.
 */
public class ValueStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValueStore.class);

    private final Env<ByteBuffer> lmdbEnv;
    private final ValueStoreDb valueStoreDb;
    private final ValueStoreMetaDb valueStoreMetaDb;


    ValueStore(final Env<ByteBuffer> lmdbEnv,
               final ValueStoreDb valueStoreDb,
               final ValueStoreMetaDb valueStoreMetaDb) {
        this.lmdbEnv = lmdbEnv;
        this.valueStoreDb = valueStoreDb;
        this.valueStoreMetaDb = valueStoreMetaDb;
    }

    ByteBuffer getOrCreateKey(final Txn<ByteBuffer> writeTxn,
                              final PooledByteBuffer valueStorePooledKeyBuffer,
                              final RefDataValue refDataValue,
                              final boolean overwriteExisting) {
        //get the ValueStoreKey for the RefDataValue (creating the entry if it doesn't exist)
        return valueStoreDb.getOrCreateKey(
                writeTxn,
                refDataValue,
                valueStorePooledKeyBuffer,
                overwriteExisting,
                (txn, keyBuffer, valueBuffer) ->
                        valueStoreMetaDb.incrementReferenceCount(txn, keyBuffer),
                (txn, keyBuffer, valueBuffer) ->
                        valueStoreMetaDb.createMetaEntryForValue(txn, keyBuffer, refDataValue));
    }

    public Optional<RefDataValue> get(final Txn<ByteBuffer> txn,
                                      final ValueStoreKey valueStoreKey) {
        try(PooledByteBuffer pooledKeyBuffer = valueStoreDb.getPooledKeyBuffer()) {
            ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
            valueStoreDb.serializeKey(keyBuffer, valueStoreKey);
            return get(txn, keyBuffer);
        }
    }

    public Optional<RefDataValue> get(final Txn<ByteBuffer> txn,
                                      final ByteBuffer valueStoreKeyBuffer) {

        // Lookup the passed key in the ValueStoreMetaDb to determine the value type
        // If found use the same key to look up the actual value and return the
        // deserialised value

        final OptionalInt optTypeId = valueStoreMetaDb.getTypeId(txn, valueStoreKeyBuffer);

        if (optTypeId.isPresent()) {
            Optional<RefDataValue> optRefDataValue = valueStoreDb.get(txn, valueStoreKeyBuffer, optTypeId.getAsInt());

            if (!optRefDataValue.isPresent()) {
                throw new RuntimeException("Value should have associated meta record, data likely corrupt");
            }
            return optRefDataValue;

        } else {
            return Optional.empty();
        }

    }

    public OptionalInt getReferenceCount(final Txn<ByteBuffer> txn,
                                         final ByteBuffer keyBuffer) {
        return valueStoreMetaDb.getReferenceCount(txn, keyBuffer);
    }

    public OptionalInt getTypeId(final Txn<ByteBuffer> txn,
                                 final ByteBuffer keyBuffer) {
        return valueStoreMetaDb.getTypeId(txn, keyBuffer);
    }

    boolean areValuesEqual(final Txn<ByteBuffer> txn,
                           final ByteBuffer valueStoreKeyBuffer,
                           final RefDataValue newRefDataValue) {
        return valueStoreDb.areValuesEqual(txn, valueStoreKeyBuffer, newRefDataValue);
    }

    boolean deReferenceOrDeleteValue(final Txn<ByteBuffer> writeTxn,
                                     final ByteBuffer valueStoreKeyBuffer) {

        // de-reference or delete the meta entry and if a delete happens, also
        // delete the value entry
        return valueStoreMetaDb.deReferenceOrDeleteValue(
                writeTxn,
                valueStoreKeyBuffer,
                ((txn, keyBuffer, valueBuffer) ->
                        valueStoreDb.delete(txn, keyBuffer)));
    }

    Optional<TypedByteBuffer> getTypedValueBuffer(final Txn<ByteBuffer> txn,
                                                  final ByteBuffer valueStoreKeyBuffer) {
        // Lookup the passed key in the ValueStoreMetaDb to determine the value type
        // If found use the same key to look up the actual value and return it
        // with the type information.
        OptionalInt optTypeId = valueStoreMetaDb.getTypeId(txn, valueStoreKeyBuffer);
        if (optTypeId.isPresent()) {
            ByteBuffer valueBuffer = valueStoreDb.getAsBytes(txn, valueStoreKeyBuffer)
                    .orElseThrow(() -> new RuntimeException(
                            "If we have a meta entry we should also have a value entry, data may be corrupted"));

            return Optional.of(new TypedByteBuffer(optTypeId.getAsInt(), valueBuffer));
        } else {
            return Optional.empty();
        }
    }

    public PooledByteBuffer getPooledKeyBuffer() {
        return valueStoreDb.getPooledKeyBuffer();
    }
}
