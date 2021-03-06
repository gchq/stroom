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

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.lmdb.AbstractLmdbDb;
import stroom.lmdb.EntryConsumer;
import stroom.pipeline.refdata.store.offheapstore.RangeStoreKey;
import stroom.pipeline.refdata.store.offheapstore.UID;
import stroom.pipeline.refdata.store.offheapstore.ValueStoreKey;
import stroom.pipeline.refdata.store.offheapstore.serdes.RangeStoreKeySerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.UIDSerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.ValueStoreKeySerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Range;

import com.google.inject.assistedinject.Assisted;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;

public class RangeStoreDb extends AbstractLmdbDb<RangeStoreKey, ValueStoreKey> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RangeStoreDb.class);

    public static final String DB_NAME = "RangeStore";

    private final RangeStoreKeySerde keySerde;
    private final ValueStoreKeySerde valueSerde;

    @Inject
    public RangeStoreDb(@Assisted final Env<ByteBuffer> lmdbEnvironment,
                        final ByteBufferPool byteBufferPool,
                        final RangeStoreKeySerde keySerde,
                        final ValueStoreKeySerde valueSerde) {

        super(lmdbEnvironment, byteBufferPool, keySerde, valueSerde, DB_NAME);
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
    }

    /**
     * Looks up the supplied mapDefinitionUid and key in the store and attempts to find a range that includes the
     * passed key. It will scan backwards from key until it finds the first matching range. If no matching range
     * is found an empty Optional is returned.
     */
    public Optional<ValueStoreKey> get(final Txn<ByteBuffer> txn, final UID mapDefinitionUid, final long key) {
        return getAsBytes(txn, mapDefinitionUid, key)
                .map(valueSerde::deserialize);
    }

    public Optional<ByteBuffer> getAsBytes(final Txn<ByteBuffer> txn, final UID mapDefinitionUid, final long key) {
        LOGGER.trace(() -> "get called for " + mapDefinitionUid + ", key " + key);

        try (final PooledByteBuffer startKeyPolledBuffer = getPooledKeyBuffer();
                final PooledByteBuffer endKeyPolledBuffer = getPooledKeyBuffer()) {

            final KeyRange<ByteBuffer> keyRange = buildKeyRange(
                    mapDefinitionUid,
                    key,
                    startKeyPolledBuffer.getByteBuffer(),
                    endKeyPolledBuffer.getByteBuffer());

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
            try (CursorIterable<ByteBuffer> cursorIterable = getLmdbDbi().iterate(txn, keyRange)) {
                // loop backwards over all rows with the same mapDefinitionUid, starting at key
                for (final CursorIterable.KeyVal<ByteBuffer> keyVal : cursorIterable) {
                    cnt.incrementAndGet();
                    final ByteBuffer keyBuffer = keyVal.key();

                    if (LOGGER.isTraceEnabled()) {
                        final RangeStoreKey rangeStoreKey = keySerde.deserialize(keyBuffer);
                        LOGGER.trace("rangeStoreKey {}, keyBuffer {}",
                                rangeStoreKey,
                                ByteBufferUtils.byteBufferInfo(keyBuffer));
                    }

                    if (RangeStoreKeySerde.isKeyInRange(keyBuffer, key)) {
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
                        return Optional.of(keyVal.val());
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

        try (PooledByteBuffer pooledKeyBuffer = getPooledKeyBuffer()) {
            final Range<Long> startRange = new Range<>(0L, 0L);
            final RangeStoreKey startRangeStoreKey = new RangeStoreKey(mapDefinitionUid, startRange);
            final ByteBuffer startKeyBuf = pooledKeyBuffer.getByteBuffer();
            keySerde.serialize(startKeyBuf, startRangeStoreKey);

            final KeyRange<ByteBuffer> keyRange = KeyRange.atLeast(startKeyBuf);

            try (CursorIterable<ByteBuffer> cursorIterable = getLmdbDbi().iterate(txn, keyRange)) {
                return cursorIterable.iterator().hasNext();
            }

        }
    }

    private KeyRange<ByteBuffer> buildKeyRange(final UID mapDefinitionUid,
                                               final long key,
                                               final ByteBuffer startKeyBuf,
                                               final ByteBuffer endKeyBuf) {
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

        // Zero is the lowest value for 'from' and 'to' so this represents the smallest key for
        // a given mapDefinitionUid.
        final Range<Long> endRange = new Range<>(0L, 0L);
        final RangeStoreKey endRangeStoreKey = new RangeStoreKey(mapDefinitionUid, endRange);
        keySerde.serialize(endKeyBuf, endRangeStoreKey);

        LOGGER.trace(() -> "Using range [" + endRangeStoreKey + "] to [" + startRangeStoreKey + "]");
        // we want to scan backward from (and including, if found) our start key
        return KeyRange.closedBackward(startKeyBuf, endKeyBuf);
    }

    public void deleteMapEntries(final Txn<ByteBuffer> writeTxn,
                                 final UID mapUid,
                                 final EntryConsumer entryConsumer) {

        try (PooledByteBuffer startKeyIncPooledBuffer = getPooledKeyBuffer();
                PooledByteBuffer endKeyExcPooledBuffer = getPooledKeyBuffer()) {

            // TODO there appears to be a bug in LMDB that causes an IndexOutOfBoundsException
            // when both the start and end key are used in the keyRange
            // see https://github.com/lmdbjava/lmdbjava/issues/98
            // As a work around will have to use an AT_LEAST cursor and manually
            // test entries to see when I have gone too far.
//            final KeyRange<ByteBuffer> singleMapUidKeyRange = buildSingleMapUidKeyRange(
//                    mapUid, startKeyIncPooledBuffer.getByteBuffer(), endKeyExcPooledBuffer.getByteBuffer());

            final Range<Long> dummyRange = Range.of(0L, 1L);
            final RangeStoreKey startKeyInc = new RangeStoreKey(mapUid, dummyRange);
            final ByteBuffer startKeyIncBuffer = startKeyIncPooledBuffer.getByteBuffer();
            keySerde.serializeWithoutRangePart(startKeyIncBuffer, startKeyInc);
            final KeyRange<ByteBuffer> atLeastKeyRange = KeyRange.atLeast(startKeyIncBuffer);

            try (CursorIterable<ByteBuffer> cursorIterable = getLmdbDbi().iterate(writeTxn, atLeastKeyRange)) {
                final AtomicInteger cnt = new AtomicInteger();
                final Iterator<KeyVal<ByteBuffer>> iterator = cursorIterable.iterator();
                while (iterator.hasNext()) {
                    final KeyVal<ByteBuffer> keyVal = iterator.next();
                    if (ByteBufferUtils.containsPrefix(keyVal.key(), startKeyIncBuffer)) {
                        // prefixed with our UID

                        LOGGER.trace(() -> LogUtil.message("Found entry {} {}",
                                ByteBufferUtils.byteBufferInfo(keyVal.key()),
                                ByteBufferUtils.byteBufferInfo(keyVal.val())));

                        // pass the found kv pair from this entry to the consumer
                        entryConsumer.accept(writeTxn, keyVal.key(), keyVal.val());
                        iterator.remove();
                        cnt.incrementAndGet();
                    } else {
                        // passed out UID so break out
                        break;
                    }
                }
                LOGGER.debug(() -> "Deleted " + DB_NAME + " " + cnt.get() + " entries");
            }
        }
    }

//    private KeyRange<ByteBuffer> buildSingleMapUidKeyRange(final UID mapUid,
//                                                           final ByteBuffer startKeyIncBuffer,
//                                                           final ByteBuffer endKeyExcBuffer) {
//        Range<Long> dummyRange = Range.of(0L, 1L);
//        final RangeStoreKey startKeyInc = new RangeStoreKey(mapUid, dummyRange);
//
//        keySerde.serializeWithoutRangePart(startKeyIncBuffer, startKeyInc);
//
//        UID nextMapUid = mapUid.nextUid();
//        final RangeStoreKey endKeyExc = new RangeStoreKey(nextMapUid, dummyRange);
//
//        LOGGER.trace(() -> LogUtil.message("Using range {} (inc) {} (exc)",
//                ByteBufferUtils.byteBufferInfo(startKeyIncBuffer),
//                ByteBufferUtils.byteBufferInfo(endKeyExcBuffer)));
//
//        keySerde.serializeWithoutRangePart(endKeyExcBuffer, endKeyExc);
//
//        return KeyRange.closedOpen(startKeyIncBuffer, endKeyExcBuffer);
//    }

    public interface Factory {

        RangeStoreDb create(final Env<ByteBuffer> lmdbEnvironment);
    }
}
