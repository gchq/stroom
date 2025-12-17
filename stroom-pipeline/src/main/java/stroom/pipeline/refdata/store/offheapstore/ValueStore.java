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

package stroom.pipeline.refdata.store.offheapstore;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.StagingValue;
import stroom.pipeline.refdata.store.ValueStoreHashAlgorithm;
import stroom.pipeline.refdata.store.offheapstore.databases.ValueStoreDb;
import stroom.pipeline.refdata.store.offheapstore.databases.ValueStoreMetaDb;
import stroom.util.logging.LogUtil;

import com.google.inject.assistedinject.Assisted;
import jakarta.inject.Inject;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final RefDataLmdbEnv lmdbEnv;
    private final ValueStoreDb valueStoreDb;
    private final ValueStoreMetaDb valueStoreMetaDb;


    @Inject
    ValueStore(@Assisted final RefDataLmdbEnv lmdbEnv,
               final ValueStoreDb valueStoreDb,
               final ValueStoreMetaDb valueStoreMetaDb) {
        this.lmdbEnv = lmdbEnv;
        this.valueStoreDb = valueStoreDb;
        this.valueStoreMetaDb = valueStoreMetaDb;
    }

    /**
     * Either gets the {@link ValueStoreKey} corresponding to the passed refDataValue
     * from the database if we already hold it or creates the entry in the database
     * and returns the generated key. To determine if we already hold it, the value
     * will be hashed and that hash will be looked up and any matches tested for equality
     * using the serialised bytes.
     * <p>
     * onExistingValueAction Action to perform when the value is found to already exist
     *
     * @return A clone of the {@link ByteBuffer} containing the database key.
     */
    ByteBuffer getOrCreateKey(final Txn<ByteBuffer> writeTxn,
                              final PooledByteBuffer valueStorePooledKeyBuffer,
                              final StagingValue stagingValue) {
        //get the ValueStoreKey for the RefDataValue (creating the entry if it doesn't exist)
        return valueStoreDb.getOrCreateKey(
                writeTxn,
                stagingValue,
                valueStorePooledKeyBuffer,
                (txn, keyBuffer, valueBuffer) ->
                        valueStoreMetaDb.incrementReferenceCount(txn, keyBuffer),
                (txn, keyBuffer, valueBuffer) ->
                        valueStoreMetaDb.createMetaEntryForValue(txn, keyBuffer, stagingValue));
    }

    public Optional<RefDataValue> get(final Txn<ByteBuffer> txn,
                                      final ValueStoreKey valueStoreKey) {
        try (final PooledByteBuffer pooledKeyBuffer = valueStoreDb.getPooledKeyBuffer()) {
            final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
            valueStoreDb.serializeKey(keyBuffer, valueStoreKey);
            return get(txn, keyBuffer);
        }
    }

    public Optional<ByteBuffer> getAsBytes(final Txn<ByteBuffer> txn,
                                           final ByteBuffer valueStoreKeyBuffer) {
        return valueStoreDb.getAsBytes(txn, valueStoreKeyBuffer);
    }

    public Optional<RefDataValue> get(final Txn<ByteBuffer> txn,
                                      final ByteBuffer valueStoreKeyBuffer) {

        // Lookup the passed key in the ValueStoreMetaDb to determine the value type
        // If found use the same key to look up the actual value and return the
        // de-serialised value

        final Byte optTypeId = valueStoreMetaDb.getTypeId(txn, valueStoreKeyBuffer);
        if (optTypeId != null) {
            final Optional<RefDataValue> optRefDataValue = valueStoreDb.get(txn, valueStoreKeyBuffer, optTypeId);
            if (optRefDataValue.isEmpty()) {
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

    public OptionalInt getReferenceCount(final Txn<ByteBuffer> txn,
                                         final ValueStoreKey valueStoreKey) {
        try (final PooledByteBuffer pooledKeyBuffer = valueStoreDb.getPooledKeyBuffer()) {
            final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
            valueStoreDb.serializeKey(keyBuffer, valueStoreKey);
            return valueStoreMetaDb.getReferenceCount(txn, keyBuffer);
        }
    }

    public Byte getTypeId(final Txn<ByteBuffer> txn,
                                 final ByteBuffer keyBuffer) {
        return valueStoreMetaDb.getTypeId(txn, keyBuffer);
    }

    boolean areValuesEqual(final Txn<ByteBuffer> txn,
                           final ByteBuffer valueStoreKeyBuffer,
                           final StagingValue stagingValue) {
        return valueStoreDb.areValuesEqual(txn, valueStoreKeyBuffer, stagingValue);
    }

    boolean deReferenceOrDeleteValue(final Txn<ByteBuffer> writeTxn,
                                     final ByteBuffer valueStoreKeyBuffer) {

        // de-reference or delete the meta entry and if a delete happens, also
        // delete the value entry
        return valueStoreMetaDb.deReferenceOrDeleteValue(
                writeTxn,
                valueStoreKeyBuffer,
                ((writeTxn2, keyBuffer) -> {
                    try {
                        valueStoreDb.delete(writeTxn2, keyBuffer);
                    } catch (final Exception e) {
                        throw new RuntimeException(LogUtil.message(
                                "Error deleting value entry for value key: {}",
                                ByteBufferUtils.byteBufferInfo(keyBuffer), e));
                    }
                }));
    }

    Optional<TypedByteBuffer> getTypedValueBuffer(final Txn<ByteBuffer> txn,
                                                  final ByteBuffer valueStoreKeyBuffer) {
        // Lookup the passed key in the ValueStoreMetaDb to determine the value type
        // If found use the same key to look up the actual value and return it
        // with the type information.
        final Byte optTypeId = valueStoreMetaDb.getTypeId(txn, valueStoreKeyBuffer);
        if (optTypeId != null) {
            final ByteBuffer valueBuffer = valueStoreDb.getAsBytes(txn, valueStoreKeyBuffer)
                    .orElseThrow(() -> new RuntimeException(
                            "If we have a meta entry we should also have a value entry, data may be corrupted"));
            return Optional.of(new TypedByteBuffer(optTypeId, valueBuffer));
        } else {
            return Optional.empty();
        }
    }

    ValueStoreHashAlgorithm getValueStoreHashAlgorithm() {
        return valueStoreDb.getValueStoreHashAlgorithm();
    }

    public PooledByteBuffer getPooledKeyBuffer() {
        return valueStoreDb.getPooledKeyBuffer();
    }

    long getEntryCount() {
        return valueStoreMetaDb.getEntryCount();
    }

    public long getEntryCount(final Txn<ByteBuffer> txn) {
        return valueStoreMetaDb.getEntryCount(txn);
    }


    // --------------------------------------------------------------------------------


    public interface Factory {

        ValueStore create(final RefDataLmdbEnv lmdbEnvironment);
    }
}
