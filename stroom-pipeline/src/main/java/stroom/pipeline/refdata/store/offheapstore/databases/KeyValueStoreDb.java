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
import stroom.lmdb.EntryConsumer;
import stroom.lmdb.LmdbEnv.BatchingWriteTxn;
import stroom.pipeline.refdata.store.offheapstore.KeyValueStoreKey;
import stroom.pipeline.refdata.store.offheapstore.RefDataLmdbEnv;
import stroom.pipeline.refdata.store.offheapstore.UID;
import stroom.pipeline.refdata.store.offheapstore.ValueStoreKey;
import stroom.pipeline.refdata.store.offheapstore.serdes.KeyValueStoreKeySerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.ValueStoreKeySerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.inject.assistedinject.Assisted;
import jakarta.inject.Inject;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Consumer;

public class KeyValueStoreDb
        extends AbstractLmdbDb<KeyValueStoreKey, ValueStoreKey>
        implements EntryStoreDb<KeyValueStoreKey> {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyValueStoreDb.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(KeyValueStoreDb.class);


    public static final String DB_NAME = "KeyValueStore";

    private final KeyValueStoreKeySerde keySerde;
    private final ValueStoreKeySerde valueSerde;

    @Inject
    KeyValueStoreDb(@Assisted final RefDataLmdbEnv lmdbEnvironment,
                    final ByteBufferPool byteBufferPool,
                    final KeyValueStoreKeySerde keySerde,
                    final ValueStoreKeySerde valueSerde) {

        super(lmdbEnvironment.getEnvironment(), byteBufferPool, keySerde, valueSerde, DB_NAME);
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
        lmdbEnvironment.registerDatabases(this);
    }

    public void deleteMapEntries(final BatchingWriteTxn batchingWriteTxn,
                                 final UID mapUid,
                                 final EntryConsumer entryConsumer) {
        LOGGER.debug("deleteMapEntries(..., {}, ...)", mapUid);

        try (final PooledByteBuffer startKeyIncPooledBuffer = getPooledKeyBuffer();
                final PooledByteBuffer endKeyExcPooledBuffer = getPooledKeyBuffer()) {

            final KeyRange<ByteBuffer> singleMapUidKeyRange = buildSingleMapUidKeyRange(
                    mapUid,
                    startKeyIncPooledBuffer.getByteBuffer(),
                    endKeyExcPooledBuffer.getByteBuffer());

            boolean isComplete = false;
            int totalCount = 0;

            // We need the outer loop as the inner loop may reach batch full state part way through
            // and break out. After the inner loop we commit the txn, so the iterable has to be re-created.
            while (!isComplete) {
                boolean foundMatchingEntry = false;
                try (final CursorIterable<ByteBuffer> cursorIterable = getLmdbDbi().iterate(
                        batchingWriteTxn.getTxn(), singleMapUidKeyRange)) {

                    boolean didBreakOutEarly = false;
                    int batchCount = 0;
                    final Iterator<KeyVal<ByteBuffer>> iterator = cursorIterable.iterator();

                    while (iterator.hasNext()) {
                        final KeyVal<ByteBuffer> keyVal = iterator.next();
                        LAMBDA_LOGGER.trace(() -> LogUtil.message("Entry {} {}",
                                ByteBufferUtils.byteBufferInfo(keyVal.key()),
                                ByteBufferUtils.byteBufferInfo(keyVal.val())));

                        foundMatchingEntry = true;
                        // prefixed with our UID

                        // Pass the found kv pair from this entry to the consumer.
                        // Consumer MUST not hold on to the key/value references as they can change
                        // once the cursor is closed or moves position
                        entryConsumer.accept(batchingWriteTxn.getTxn(), keyVal.key(), keyVal.val());
                        // Delete this entry
                        iterator.remove();
                        batchCount++;

                        // Can't use batchingWriteTxn.commitIfRequired() as the commit would close
                        // the txn which then causes an error in the cursorIterable auto close
                        if (batchingWriteTxn.incrementBatchCount()) {
                            // Batch is full so break out
                            didBreakOutEarly = true;
                            break;
                        }
                    }

                    if (foundMatchingEntry) {
                        isComplete = !didBreakOutEarly;
                        totalCount += batchCount;
                        LOGGER.debug("Deleted {} {} entries this iteration, total deleted: {}",
                                batchCount, DB_NAME, totalCount);
                    } else {
                        isComplete = true;
                    }
                }

                if (foundMatchingEntry) {
                    // Force the commit as we either have a full batch or we have finished
                    // We may now have a partial purge committed, but we are still under write
                    // lock so no other threads can purge or load and there is a lock on the
                    // ref stream.
                    LOGGER.debug("Committing, totalCount {}", totalCount);
                    batchingWriteTxn.commit();
                } else {
                    LOGGER.debug("No entry found since last commit, not committing, totalCount {}",
                            totalCount);
                }
            }
        }
    }

    public long getEntryCount(final UID mapUid, final Txn<ByteBuffer> readTxn) {
        long cnt = 0;
        try (final PooledByteBuffer startKeyBuffer = getPooledKeyBuffer();
                final PooledByteBuffer endKeyBuffer = getPooledKeyBuffer()) {

            final KeyRange<ByteBuffer> keyRange = buildSingleMapUidKeyRange(
                    mapUid,
                    startKeyBuffer.getByteBuffer(),
                    endKeyBuffer.getByteBuffer());

            try (final CursorIterable<ByteBuffer> cursorIterable = getLmdbDbi().iterate(
                    readTxn, keyRange)) {
                //noinspection unused
                for (final KeyVal<ByteBuffer> keyVal : cursorIterable) {
//                    LAMBDA_LOGGER.trace(() -> LogUtil.message(
//                            "Key: {}",
//                            ByteBufferUtils.byteBufferInfo(keyVal.key())));

                    cnt++;
                }
            }
        }
        return cnt;
    }

    /**
     * Apply the passes entryConsumer for each entry found matching the supplied UID
     */
    public void forEachEntryAsBytes(final Txn<ByteBuffer> txn,
                                    final UID mapUid,
                                    final Consumer<KeyVal<ByteBuffer>> keyValueConsumer) {
        try (final PooledByteBuffer startKeyBuffer = getPooledKeyBuffer();
                final PooledByteBuffer stopKeyBuffer = getPooledKeyBuffer()) {

            final KeyRange<ByteBuffer> keyRange = buildSingleMapUidKeyRange(
                    mapUid,
                    startKeyBuffer.getByteBuffer(),
                    stopKeyBuffer.getByteBuffer());

            forEachEntryAsBytes(txn, keyRange, keyValueConsumer);
        }
    }

    public Optional<UID> getMaxUid(final Txn<ByteBuffer> txn, final PooledByteBuffer pooledByteBuffer) {

        try (final CursorIterable<ByteBuffer> iterable = getLmdbDbi().iterate(txn, KeyRange.allBackward())) {
            final Iterator<KeyVal<ByteBuffer>> iterator = iterable.iterator();

            if (iterator.hasNext()) {
                final ByteBuffer keyBuffer = iterator.next().key();
                final UID uid = keySerde.extractUid(keyBuffer);
                final ByteBuffer copyByteBuffer = pooledByteBuffer.getByteBuffer();
                copyByteBuffer.clear();
                final UID uidClone = uid.cloneToBuffer(pooledByteBuffer.getByteBuffer());
                return Optional.ofNullable(uidClone);
            } else {
                return Optional.empty();
            }
        }
    }

    private KeyRange<ByteBuffer> buildSingleMapUidKeyRange(final UID mapUid,
                                                           final ByteBuffer startKeyIncBuffer,
                                                           final ByteBuffer endKeyExcBuffer) {
        final KeyValueStoreKey startKeyInc = new KeyValueStoreKey(mapUid, "");

        // serialise the startKeyInc to both start and end buffers, then
        // we will mutate the uid of the end buffer
        keySerde.serializeWithoutKeyPart(startKeyIncBuffer, startKeyInc);
        keySerde.serializeWithoutKeyPart(endKeyExcBuffer, startKeyInc);

        // Increment the UID part of the end key buffer to give us an exclusive key
        UID.incrementUid(endKeyExcBuffer);

//        final KeyValueStoreKey endKeyExc = new KeyValueStoreKey(nextMapUid, "");

        LAMBDA_LOGGER.trace(() -> LogUtil.message("Using range {} (inc) {} (exc)",
                ByteBufferUtils.byteBufferInfo(startKeyIncBuffer),
                ByteBufferUtils.byteBufferInfo(endKeyExcBuffer)));

//        keySerde.serializeWithoutKeyPart(endKeyExcBuffer, endKeyExc);

        return KeyRange.closedOpen(startKeyIncBuffer, endKeyExcBuffer);
    }


    // --------------------------------------------------------------------------------


    public interface Factory {

        KeyValueStoreDb create(final RefDataLmdbEnv lmdbEnvironment);
    }
}
