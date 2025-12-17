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
import stroom.pipeline.refdata.store.offheapstore.RangeStoreKey;
import stroom.pipeline.refdata.store.offheapstore.RefDataLmdbEnv;
import stroom.pipeline.refdata.store.offheapstore.UID;
import stroom.pipeline.refdata.store.offheapstore.ValueStoreKey;
import stroom.pipeline.refdata.store.offheapstore.serdes.RangeStoreKeySerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.RangeStoreKeySerde.CompareResult;
import stroom.pipeline.refdata.store.offheapstore.serdes.UIDSerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.ValueStoreKeySerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Range;

import com.google.inject.assistedinject.Assisted;
import jakarta.inject.Inject;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class RangeStoreDb
        extends AbstractLmdbDb<RangeStoreKey, ValueStoreKey>
        implements EntryStoreDb<RangeStoreKey> {

    protected static final Range<Long> IGNORED_RANGE = Range.of(0L, 1L);
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RangeStoreDb.class);

    public static final String DB_NAME = "RangeStore";

    private final RangeStoreKeySerde keySerde;
    private final ValueStoreKeySerde valueSerde;

    @Inject
    public RangeStoreDb(@Assisted final RefDataLmdbEnv lmdbEnvironment,
                        final ByteBufferPool byteBufferPool,
                        final RangeStoreKeySerde keySerde,
                        final ValueStoreKeySerde valueSerde) {

        super(lmdbEnvironment.getEnvironment(), byteBufferPool, keySerde, valueSerde, DB_NAME);
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
        lmdbEnvironment.registerDatabases(this);
    }

    /**
     * Looks up the supplied mapDefinitionUid and key in the store and attempts to find a range that includes the
     * passed key. It will scan backwards from key until it finds the first matching range. If no matching range
     * is found an empty Optional is returned.
     */
    public Optional<ValueStoreKey> get(final Txn<ByteBuffer> txn,
                                       final UID mapDefinitionUid,
                                       final long key) {
        return getAsBytes(txn, mapDefinitionUid, key)
                .map(valueSerde::deserialize);
    }

    public Optional<ByteBuffer> getAsBytes(final Txn<ByteBuffer> txn,
                                           final UID mapDefinitionUid,
                                           final long key) {
        LOGGER.trace(() -> "get called for " + mapDefinitionUid + ", key " + key);

        try (final PooledByteBuffer startKeyPolledBuffer = getPooledKeyBuffer()) {

            final KeyRange<ByteBuffer> keyRange = buildKeyRange(
                    mapDefinitionUid,
                    key,
                    startKeyPolledBuffer.getByteBuffer());

            // these lines are useful for debugging with SMALL data volumes
//        logDatabaseContents(txn);
//        logRawDatabaseContents(txn);
//        LmdbUtils.logRawContentsInRange(lmdbEnvironment, lmdbDbi, txn, keyRange);
//        LmdbUtils.logContentsInRange(
//                lmdbEnvironment,
//                lmdbDbi,
//                txn,
//                keyRange,
//                buf -> keySerde.deserialize(buf).toString(),
//                buf -> valueSerde.deserialize(buf).toString());

            final AtomicInteger cnt = new AtomicInteger();
            try (final CursorIterable<ByteBuffer> cursorIterable = getLmdbDbi().iterate(
                    txn, keyRange)) {
                // loop backwards over all rows with the same mapDefinitionUid, starting at key
                for (final CursorIterable.KeyVal<ByteBuffer> keyVal : cursorIterable) {
                    cnt.incrementAndGet();
                    final ByteBuffer keyBuffer = keyVal.key();

                    final CompareResult compareResult = RangeStoreKeySerde.isKeyInRange(
                            keyBuffer,
                            mapDefinitionUid,
                            key);

                    if (LOGGER.isTraceEnabled()) {
                        final RangeStoreKey rangeStoreKey = keySerde.deserialize(keyBuffer);
                        LOGGER.trace("rangeStoreKey {}, keyBuffer {}, compareResult {}",
                                rangeStoreKey,
                                ByteBufferUtils.byteBufferInfo(keyBuffer),
                                compareResult);
                    }

                    if (CompareResult.IN_RANGE.equals(compareResult)) {
                        final UID uidOfFoundKey = UIDSerde.extractUid(keyBuffer);
                        // double check to be sure we have the right mapDefinitionUid
                        if (!uidOfFoundKey.equals(mapDefinitionUid)) {
                            throw new RuntimeException(LogUtil.message(
                                    "Found a key with a different mapDefinitionUid, found: {}, expected {}",
                                    uidOfFoundKey, mapDefinitionUid));
                        }
                        LOGGER.trace(() -> "key " + key + " is in the range, " +
                                "found the required value after " + cnt.get() + " iterations");

                        // TODO we are returning the cursor buffer out of the cursor scope so we must first copy it.
                        //  Better still we could accept an arg of a Function<ByteBuffer, ByteBuffer> to map the
                        //  valueStoreKey buffer into the actual value bytebuffer from the valueStoreDb.
                        //  This would save a buffer copy.
                        //if (cnt.get() > 1) {
                        //LOGGER.info("{} iterations", cnt.get());
                        //}
                        return Optional.of(keyVal.val());
                    } else {
                        // If we are not inside the range then we have no hit.
                        // This assumes ranges are non-overlapping.
                        // e.g. if an entry exists with key [10-20] and we lookup with key 15,
                        // the cursor start key will be [15-Long.MAX_VAL]. Thus [10-20] will be
                        // the first one found.  If the first one found was [0-10] then we could safely
                        // bomb out.
                        break;
                    }
                }
            }
            LOGGER.trace(() ->
                    "Value not found for " + mapDefinitionUid + ", key " + key + ", iterations " + cnt.get());
            return Optional.empty();
        }
    }

    /**
     * Returns true if the store contains at least one row with a key containing the passed mapDefinitionUid
     */
    public boolean containsMapDefinition(final Txn<ByteBuffer> txn, final UID mapDefinitionUid) {
        LOGGER.trace(() -> "containsMapDefinition called for " + mapDefinitionUid);

        try (final PooledByteBuffer pooledStartKeyBuffer = getPooledKeyBuffer();
                final PooledByteBuffer pooledEndKeyBuffer = getPooledKeyBuffer();
                final PooledByteBuffer nextUidBuffer = getByteBufferPool().getPooledByteBuffer(UID.length())) {

            final Range<Long> startRange = new Range<>(0L, 0L);
            final RangeStoreKey startRangeStoreKey = new RangeStoreKey(mapDefinitionUid, startRange);
            final ByteBuffer startKeyBuf = pooledStartKeyBuffer.getByteBuffer();
            keySerde.serialize(startKeyBuf, startRangeStoreKey);

            // Build an exclusive end key using the next map UID
            mapDefinitionUid.writeNextUid(nextUidBuffer.getByteBuffer());
            final UID nextMapDefUid = UID.wrap(nextUidBuffer.getByteBuffer());
            final RangeStoreKey endRangeStoreKey = new RangeStoreKey(nextMapDefUid, startRange);
            final ByteBuffer endKeyBuf = pooledEndKeyBuffer.getByteBuffer();
            keySerde.serialize(endKeyBuf, endRangeStoreKey);

            // start key inclusive, end key exclusive
            final KeyRange<ByteBuffer> keyRange = KeyRange.closedOpen(startKeyBuf, endKeyBuf);

            try (final CursorIterable<ByteBuffer> cursorIterable = getLmdbDbi().iterate(txn, keyRange)) {
                // If we find anything in our range then it means this map uid is in the range store.
                return cursorIterable.iterator().hasNext();
            }
        }
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

    private KeyRange<ByteBuffer> buildKeyRange(final UID mapDefinitionUid,
                                               final long key,
                                               final ByteBuffer startKeyBuf) {
        // We want to scan backwards over all keys with the passed mapDefinitionUid,
        // starting with a range from == key. E.g. with the following data (ignoring mapDefUid)
        // in DB order.

        // 100-200
        // 200-300
        // 300-400
        // 400-500

        // key  | DB keys returned
        // -----|-----------------
        // 50   | [no rows]
        // 150  | 100-200
        // 300  | 300-400, 200-300, 100-200
        // 1000 | 400-500, 300-400, 200-300, 100-200

        // Set the 'to' bit of the start key to Long.MAX_VALUE to ensure we get all possible values
        // with that 'from' part.
        final Range<Long> startRange = new Range<>(key, Long.MAX_VALUE);
        final RangeStoreKey startRangeStoreKey = new RangeStoreKey(mapDefinitionUid, startRange);
        keySerde.serialize(startKeyBuf, startRangeStoreKey);

        LOGGER.trace(() -> "Using startRangeStoreKey [" + startRangeStoreKey + "]");
        // we want to scan backward from (and including, if found) our start key
        return KeyRange.atLeastBackward(startKeyBuf);
    }

    public void deleteMapEntries(final BatchingWriteTxn batchingWriteTxn,
                                 final UID mapUid,
                                 final EntryConsumer entryConsumer) {

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
                boolean foundMatchingEntry;
                // Scan over all entries from our start key and test each one to check we
                // haven't gone past the ones we want.
                try (final CursorIterable<ByteBuffer> cursorIterable = getLmdbDbi().iterate(
                        batchingWriteTxn.getTxn(), singleMapUidKeyRange)) {

                    boolean didBreakOutEarly = false;
                    int batchCount = 0;
                    foundMatchingEntry = false;
                    final Iterator<KeyVal<ByteBuffer>> iterator = cursorIterable.iterator();

                    while (iterator.hasNext()) {
                        final KeyVal<ByteBuffer> keyVal = iterator.next();
                        LOGGER.trace(() -> LogUtil.message("Entry {} {}",
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

                for (final KeyVal<ByteBuffer> ignored : cursorIterable) {
                    cnt++;
                }
            }
        }
        return cnt;
    }

    private KeyRange<ByteBuffer> buildSingleMapUidKeyRange(final UID mapUid,
                                                           final ByteBuffer startKeyIncBuffer,
                                                           final ByteBuffer endKeyExcBuffer) {
        // The range part is irrelevant as we are only concerned with the uid part
        final RangeStoreKey startKeyInc = new RangeStoreKey(mapUid, IGNORED_RANGE);

        // serialise the startKeyInc to both start and end buffers, then
        // we will mutate the uid of the end buffer
        keySerde.serializeWithoutRangePart(startKeyIncBuffer, startKeyInc);
        keySerde.serializeWithoutRangePart(endKeyExcBuffer, startKeyInc);

        // Increment the UID part of the end key buffer to give us an exclusive key
        UID.incrementUid(endKeyExcBuffer);

//        final KeyValueStoreKey endKeyExc = new KeyValueStoreKey(nextMapUid, "");

        LOGGER.trace(() -> LogUtil.message("Using range {} (inc) {} (exc)",
                ByteBufferUtils.byteBufferInfo(startKeyIncBuffer),
                ByteBufferUtils.byteBufferInfo(endKeyExcBuffer)));

//        keySerde.serializeWithoutKeyPart(endKeyExcBuffer, endKeyExc);

        return KeyRange.closedOpen(startKeyIncBuffer, endKeyExcBuffer);
    }


    // --------------------------------------------------------------------------------


    public interface Factory {

        RangeStoreDb create(final RefDataLmdbEnv lmdbEnvironment);
    }
}
