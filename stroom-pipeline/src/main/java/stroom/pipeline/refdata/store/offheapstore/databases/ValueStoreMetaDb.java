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

package stroom.pipeline.refdata.store.offheapstore.databases;

import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.offheapstore.ValueStoreKey;
import stroom.pipeline.refdata.store.offheapstore.ValueStoreMeta;
import stroom.pipeline.refdata.store.offheapstore.lmdb.AbstractLmdbDb;
import stroom.pipeline.refdata.store.offheapstore.lmdb.EntryConsumer;
import stroom.pipeline.refdata.store.offheapstore.serdes.ValueStoreKeySerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.ValueStoreMetaSerde;
import stroom.pipeline.refdata.util.ByteBufferPool;
import stroom.pipeline.refdata.util.ByteBufferUtils;
import stroom.pipeline.refdata.util.PooledByteBuffer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.inject.assistedinject.Assisted;
import org.lmdbjava.Cursor;
import org.lmdbjava.Env;
import org.lmdbjava.GetOp;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
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
 * (1234|00)  | (0|0001)
 * (1234|01)  | (0|0001)
 * (4567|00)  | (0|0001)
 * (7890|00)  | (1|0001)
 */
public class ValueStoreMetaDb extends AbstractLmdbDb<ValueStoreKey, ValueStoreMeta> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValueStoreMetaDb.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(ValueStoreMetaDb.class);

    private static final String DB_NAME = "ValueStoreMeta";

    private final ValueStoreKeySerde keySerde;
    private final ValueStoreMetaSerde valueSerde;

    @Inject
    public ValueStoreMetaDb(
            @Assisted final Env<ByteBuffer> lmdbEnvironment,
            final ByteBufferPool byteBufferPool,
            final ValueStoreKeySerde keySerde,
            final ValueStoreMetaSerde valueSerde) {

        super(lmdbEnvironment, byteBufferPool, keySerde, valueSerde, DB_NAME);
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
    }

    public OptionalInt getTypeId(final Txn<ByteBuffer> txn, final ByteBuffer keyBuffer) {
        Optional<ByteBuffer> optValueBuffer = getAsBytes(txn, keyBuffer);

        return optValueBuffer
                .map(byteBuffer ->
                        OptionalInt.of(valueSerde.extractTypeId(byteBuffer)))
                .orElseGet(OptionalInt::empty);
    }

    public OptionalInt getReferenceCount(final Txn<ByteBuffer> txn, final ByteBuffer keyBuffer) {
        Optional<ByteBuffer> optValueBuffer = getAsBytes(txn, keyBuffer);

        return optValueBuffer
                .map(byteBuffer ->
                        OptionalInt.of(valueSerde.extractReferenceCount(byteBuffer)))
                .orElseGet(OptionalInt::empty);
    }


    public void createMetaEntryForValue(final Txn<ByteBuffer> txn,
                                        final ByteBuffer keyBuffer,
                                        final RefDataValue refDataValue) {

        try (PooledByteBuffer pooledValueBuffer = getPooledValueBuffer()) {
            ByteBuffer valueBuffer = pooledValueBuffer.getByteBuffer();
            serializeValue(pooledValueBuffer.getByteBuffer(), new ValueStoreMeta(refDataValue.getTypeId()));

            boolean didPutSucceed = put(txn, keyBuffer, valueBuffer, false);

            if (!didPutSucceed) {
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

        try (PooledByteBuffer pooledValueBuffer = getPooledValueBuffer()) {
            ByteBuffer newValueBuffer = pooledValueBuffer.getByteBuffer();
            valueSerde.cloneAndIncrementRefCount(currValueBuffer, newValueBuffer);
            boolean didPutSucceed = put(writeTxn, keyBuffer, newValueBuffer, true);
            if (!didPutSucceed) {
                throw new RuntimeException(LogUtil.message("Put failed for keyBuffer {}",
                        ByteBufferUtils.byteBufferInfo(keyBuffer)));
            }
        }
    }

    private void updateReferenceCount(final Txn<ByteBuffer> writeTxn,
                                      final ByteBuffer keyBuffer,
                                      final int referenceCountDelta) {

    }

    /**
     * Decrements the reference count for this value. If the resulting reference count is zero or less
     * the value will be deleted as it is no longer required.
     *
     * @return True if the value was deleted as a result of the reference count reaching zero, else false.
     */
    public boolean deReferenceOrDeleteValue(final Txn<ByteBuffer> writeTxn,
                                            final ByteBuffer keyBuffer,
                                            final EntryConsumer onDeleteAction) {
        LAMBDA_LOGGER.trace(() -> LogUtil.message("deReferenceValue({}, {})",
                writeTxn, ByteBufferUtils.byteBufferInfo(keyBuffer)));

        try (Cursor<ByteBuffer> cursor = getLmdbDbi().openCursor(writeTxn)) {

            boolean isFound = cursor.get(keyBuffer, GetOp.MDB_SET_KEY);
            if (!isFound) {
                throw new RuntimeException(LogUtil.message(
                        "Expecting to find entry for {}", ByteBufferUtils.byteBufferInfo(keyBuffer)));
            }
            final ByteBuffer valueBuffer = cursor.val();

            // We run LMDB in its default mode of read only mmaps so we cannot mutate the key/value
            // bytebuffers.  Instead we must copy the content and put the replacement entry.
            // We could run LMDB in MDB_WRITEMAP mode which allows mutation of the buffers (and
            // thus avoids the buffer copy cost) but adds more risk of DB corruption. As we are not
            // doing a high volume of value mutations read-only mode is a safer bet.
            int currRefCount = valueSerde.extractReferenceCount(valueBuffer);

            if (currRefCount <= 1) {
                // we had the last ref to this value so we can delete it
                LAMBDA_LOGGER.trace(() -> LogUtil.message(
                        "Ref count is zero, deleting entry for key {}",
                        ByteBufferUtils.byteBufferInfo(keyBuffer)));
                cursor.delete();

                // perform any post delete actions
                onDeleteAction.accept(writeTxn, keyBuffer, valueBuffer);
                return true;

            } else {
                // other people have a ref to it so just decrement the ref count
                try (PooledByteBuffer pooledNewValueBuffer = getPooledValueBuffer()) {
                    final ByteBuffer newValueBuf = pooledNewValueBuffer.getByteBuffer();
                    valueSerde.cloneAndDecrementRefCount(
                            valueBuffer,
                            newValueBuf);

                    if (LAMBDA_LOGGER.isTraceEnabled()) {
                        int oldRefCount = valueSerde.extractReferenceCount(keyBuffer);
                        int newRefCount = valueSerde.extractReferenceCount(newValueBuf);
                        LAMBDA_LOGGER.trace(() -> LogUtil.message(
                                "Updating entry ref count from {} to {} for key {}",
                                oldRefCount,
                                newRefCount,
                                ByteBufferUtils.byteBufferInfo(keyBuffer)));
                    }
                    cursor.put(cursor.key(), newValueBuf, PutFlags.MDB_CURRENT);
                }
                return true;
            }
        }
    }

    public interface Factory {
        ValueStoreMetaDb create(final Env<ByteBuffer> lmdbEnvironment);
    }
}
