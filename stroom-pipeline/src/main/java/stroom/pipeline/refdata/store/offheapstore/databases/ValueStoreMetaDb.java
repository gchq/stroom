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
import stroom.lmdb.AbstractLmdbDb;
import stroom.lmdb.KeyConsumer;
import stroom.lmdb.PutOutcome;
import stroom.pipeline.refdata.store.StagingValue;
import stroom.pipeline.refdata.store.offheapstore.RefDataLmdbEnv;
import stroom.pipeline.refdata.store.offheapstore.ValueStoreKey;
import stroom.pipeline.refdata.store.offheapstore.ValueStoreMeta;
import stroom.pipeline.refdata.store.offheapstore.serdes.ValueStoreKeySerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.ValueStoreMetaSerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.inject.assistedinject.Assisted;
import jakarta.inject.Inject;
import org.lmdbjava.Cursor;
import org.lmdbjava.GetOp;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * This store holds meta data about the corresponding entries in {@link ValueStoreDb}.
 * It shares the same key as {@link ValueStoreDb} and each entry in here has a corresponding
 * entry in {@link ValueStoreDb}.
 * <p>
 * The type part of the value defines the data type of the value in {@link ValueStoreDb}.
 * The referenceCount part is used to keep track of the number of key/value or range/value
 * entries that are associated with an entry in {@link ValueStoreDb}.
 * <p>
 * key        | value
 * (hash|id)  | (type|referenceCount)
 * ---------------------------------------------
 * (1234|00)  | (0|001)
 * (1234|01)  | (0|001)
 * (4567|00)  | (0|001)
 * (7890|00)  | (1|001)
 */
public class ValueStoreMetaDb extends AbstractLmdbDb<ValueStoreKey, ValueStoreMeta> {

    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(ValueStoreMetaDb.class);

    private static final String DB_NAME = "ValueStoreMeta";

    private final ValueStoreKeySerde keySerde;
    private final ValueStoreMetaSerde valueSerde;

    @Inject
    public ValueStoreMetaDb(@Assisted final RefDataLmdbEnv lmdbEnvironment,
                            final ByteBufferPool byteBufferPool,
                            final ValueStoreKeySerde keySerde,
                            final ValueStoreMetaSerde valueSerde) {

        super(lmdbEnvironment.getEnvironment(), byteBufferPool, keySerde, valueSerde, DB_NAME);
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
        lmdbEnvironment.registerDatabases(this);
    }

    public Byte getTypeId(final Txn<ByteBuffer> txn, final ByteBuffer keyBuffer) {
        final Optional<ByteBuffer> optValueBuffer = getAsBytes(txn, keyBuffer);

        return optValueBuffer
                .map(valueSerde::extractTypeId)
                .orElse(null);
    }

    public OptionalInt getReferenceCount(final Txn<ByteBuffer> txn, final ByteBuffer keyBuffer) {
        final Optional<ByteBuffer> optValueBuffer = getAsBytes(txn, keyBuffer);

        return optValueBuffer
                .map(byteBuffer ->
                        OptionalInt.of(valueSerde.extractReferenceCount(byteBuffer)))
                .orElseGet(OptionalInt::empty);
    }


    public void createMetaEntryForValue(final Txn<ByteBuffer> txn,
                                        final ByteBuffer keyBuffer,
                                        final StagingValue refDataValue) {

        try (final PooledByteBuffer pooledValueBuffer = getPooledValueBuffer()) {
            final ByteBuffer valueBuffer = pooledValueBuffer.getByteBuffer();
            serializeValue(pooledValueBuffer.getByteBuffer(), new ValueStoreMeta(refDataValue.getTypeId()));

            final PutOutcome putOutcome = put(txn, keyBuffer, valueBuffer, false);

            if (!putOutcome.isSuccess()) {
                throw new RuntimeException(LogUtil.message(
                        "Put failed for key [{}], value [{}], may be an entry already",
                        ByteBufferUtils.byteBufferInfo(keyBuffer),
                        refDataValue));
            }
        }
    }

    /**
     * increments the reference count by one for the key represented by the valueStoreKeyBuf.
     */
    public void incrementReferenceCount(final Txn<ByteBuffer> writeTxn, final ByteBuffer keyBuffer) {

        final ByteBuffer currValueBuffer = getAsBytes(writeTxn, keyBuffer)
                .orElseThrow(() -> new RuntimeException(LogUtil.message(
                        "keyBuffer {} not found in DB",
                        ByteBufferUtils.byteBufferInfo(keyBuffer))));

        try (final PooledByteBuffer pooledValueBuffer = getPooledValueBuffer()) {
            final ByteBuffer newValueBuffer = pooledValueBuffer.getByteBuffer();
            valueSerde.cloneAndIncrementRefCount(currValueBuffer, newValueBuffer);
            final PutOutcome putOutcome = put(writeTxn, keyBuffer, newValueBuffer, true);
            if (!putOutcome.isSuccess()) {
                throw new RuntimeException(LogUtil.message("Put failed for keyBuffer {}",
                        ByteBufferUtils.byteBufferInfo(keyBuffer)));
            }
        }
    }

    /**
     * Decrements the reference count for this value. If the resulting reference count is zero or less
     * the value will be deleted as it is no longer required.
     *
     * @return True if the value was deleted as a result of the reference count reaching zero, else false.
     */
    public boolean deReferenceOrDeleteValue(final Txn<ByteBuffer> writeTxn,
                                            final ByteBuffer keyBuffer,
                                            final KeyConsumer onDeleteAction) {
        LAMBDA_LOGGER.trace(() -> LogUtil.message("deReferenceValue({}, {})",
                writeTxn, ByteBufferUtils.byteBufferInfo(keyBuffer)));

        try (final Cursor<ByteBuffer> cursor = getLmdbDbi().openCursor(writeTxn)) {
            final boolean isFound = cursor.get(keyBuffer, GetOp.MDB_SET_KEY);
            if (isFound) {
                final ByteBuffer valueBuffer = cursor.val();

                // We run LMDB in its default mode of read only mmaps so we cannot mutate the key/value
                // bytebuffers.  Instead, we must copy the content and put the replacement entry.
                // We could run LMDB in MDB_WRITEMAP mode which allows mutation of the buffers (and
                // thus avoids the buffer copy cost) but adds more risk of DB corruption. As we are not
                // doing a high volume of value mutations read-only mode is a safer bet.
                final boolean isLastReference = valueSerde.isLastReference(valueBuffer);

                if (isLastReference) {
                    // we have the last ref to this value, so we can delete it
                    LAMBDA_LOGGER.trace(() -> LogUtil.message(
                            "Ref count is zero, deleting entry for key {}",
                            ByteBufferUtils.byteBufferInfo(keyBuffer)));
                    cursor.delete();

                    // perform any post delete actions
                    onDeleteAction.accept(writeTxn, keyBuffer);
                    return true;

                } else {
                    // other people have a ref to it so just decrement the ref count
                    try (final PooledByteBuffer pooledNewValueBuffer = getPooledValueBuffer()) {
                        final ByteBuffer newValueBuf = pooledNewValueBuffer.getByteBuffer();
                        valueSerde.cloneAndDecrementRefCount(
                                valueBuffer,
                                newValueBuf);

                        if (LAMBDA_LOGGER.isTraceEnabled()) {
                            final int oldRefCount = valueSerde.extractReferenceCount(keyBuffer);
                            final int newRefCount = valueSerde.extractReferenceCount(newValueBuf);
                            LAMBDA_LOGGER.trace(() -> LogUtil.message(
                                    "Updating entry ref count from {} to {} for key {}",
                                    oldRefCount,
                                    newRefCount,
                                    ByteBufferUtils.byteBufferInfo(keyBuffer)));
                        }
                        cursor.put(cursor.key(), newValueBuf, PutFlags.MDB_CURRENT);
                    }
                    return false;
                }
            } else {
                // Entry not found. This should not really happen as a value store entry should only be deleted
                // if its reference count drops to zero which should not be the case here as our KV entry
                // holds a ref to it. It indicates we have a problem somewhere.

                LAMBDA_LOGGER.warn(() -> "Expected to find a valueStoreMetaDb entry found with key: "
                        + ByteBufferUtils.byteBufferInfo(keyBuffer));
                // It is not there, which is what we want, so we have effectively deleted it, however
                // I think we have to assume if it is not there then the corresponding valueStoreDb entry is also
                // not there so don't call the onDeleteAction.
                return true;
            }
        }
    }


    // --------------------------------------------------------------------------------


    public interface Factory {

        ValueStoreMetaDb create(final RefDataLmdbEnv lmdbEnvironment);
    }
}
