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
import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.bytebuffer.ByteBufferUtils;
import stroom.lmdb.LmdbEnv.BatchingWriteTxn;
import stroom.pipeline.refdata.store.offheapstore.RangeStoreKey;
import stroom.pipeline.refdata.store.offheapstore.UID;
import stroom.pipeline.refdata.store.offheapstore.ValueStoreKey;
import stroom.pipeline.refdata.store.offheapstore.serdes.RangeStoreKeySerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.RangeStoreKeySerde.CompareResult;
import stroom.pipeline.refdata.store.offheapstore.serdes.ValueStoreKeySerde;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.shared.Range;

import com.google.common.util.concurrent.Striped;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestRangeStoreDb extends AbstractStoreDbTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestRangeStoreDb.class);
    private static final UID UID_1 = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 0, 0, 0, 1);
    private static final UID UID_2 = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 0, 0, 0, 2);
    private static final UID UID_3 = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 0, 0, 0, 3);
    private RangeStoreDb rangeStoreDb;
    private ByteBufferPool byteBufferPool = new ByteBufferPoolFactory().getByteBufferPool();

    @BeforeEach
    void setup() {
        rangeStoreDb = new RangeStoreDb(
                refDataLmdbEnv,
                new ByteBufferPoolFactory().getByteBufferPool(),
                new RangeStoreKeySerde(),
                new ValueStoreKeySerde());
    }

    @Test
    void testGet() {

        final List<UID> uids = Arrays.asList(UID_1, UID_2, UID_3);

        // Load some non-contiguous ranges for different mapDefinitionUids
        for (int i = 0; i < uids.size(); i++) {
            LOGGER.debug("Iteration {}, loading for UID {}", i, uids.get(i));
            rangeStoreDb.put(key(uids.get(i), 1, 11), val((i * 10) + 1), false);
            rangeStoreDb.put(key(uids.get(i), 11, 13), val((i * 10) + 2), false);
            rangeStoreDb.put(key(uids.get(i), 13, 21), val((i * 10) + 3), false);
            // gap in ranges
            rangeStoreDb.put(key(uids.get(i), 101, 201), val((i * 10) + 4), false);
            rangeStoreDb.put(key(uids.get(i), 201, 301), val((i * 10) + 5), false);
        }

        rangeStoreDb.logRawDatabaseContents();
        rangeStoreDb.logDatabaseContents();

        // now try and get some keys
        lmdbEnv.doWithReadTxn(txn -> {
            for (int i = 0; i < uids.size(); i++) {
                LOGGER.debug("Iteration {}, testing with UID {}", i, uids.get(i));

                getAndAssert(txn, uids.get(i), 1, (i * 10) + 1); // on range start
                getAndAssert(txn, uids.get(i), 5, (i * 10) + 1); // in range middle
                getAndAssert(txn, uids.get(i), 10, (i * 10) + 1); // on range end
                getAndAssert(txn, uids.get(i), 11, (i * 10) + 2); // on range start
                getAndAssert(txn, uids.get(i), 300, (i * 10) + 5); // on range end

                getAndAssertNotFound(txn, uids.get(i), 0); // not in a range
                getAndAssertNotFound(txn, uids.get(i), 21); // not in a range
                getAndAssertNotFound(txn, uids.get(i), 50); // not in a range
                getAndAssertNotFound(txn, uids.get(i), 100); // not in a range
                getAndAssertNotFound(txn, uids.get(i), 301); // not in a range
            }
        });
    }

    @Test
    void testContainsMapDefinition() {

        final List<UID> uids = Arrays.asList(UID_2, UID_3);
        final UID uid4 = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 0, 0, 0, 4);

        // Load some non-contiguous ranges for different mapDefinitionUids
        for (int i = 0; i < uids.size(); i++) {
            LOGGER.debug("Iteration {}, loading for UID {}", i, uids.get(i));
            rangeStoreDb.put(key(uids.get(i), 1, 11), val((i * 10) + 1), false);
            rangeStoreDb.put(key(uids.get(i), 11, 13), val((i * 10) + 2), false);
            rangeStoreDb.put(key(uids.get(i), 13, 21), val((i * 10) + 3), false);
            // gap in ranges
            rangeStoreDb.put(key(uids.get(i), 101, 201), val((i * 10) + 4), false);
            rangeStoreDb.put(key(uids.get(i), 201, 301), val((i * 10) + 5), false);
        }

        rangeStoreDb.logRawDatabaseContents();
        rangeStoreDb.logDatabaseContents();

        lmdbEnv.doWithReadTxn(txn -> {
            boolean result;
            for (int i = 0; i < uids.size(); i++) {
                result = rangeStoreDb.containsMapDefinition(txn, uids.get(i));
                assertThat(result).isTrue();
            }

            // no entries exist for uid1
            result = rangeStoreDb.containsMapDefinition(txn, UID_1);
            assertThat(result).isFalse();

            // no entries exist for uid4
            result = rangeStoreDb.containsMapDefinition(txn, uid4);
            assertThat(result).isFalse();
        });
    }

    @Test
    void testIsKeyInRange() {
        final RangeStoreKey rangeStoreKey = new RangeStoreKey(UID_1, Range.of(10L, 20L));
        final ByteBuffer keyBuffer = ByteBuffer.allocate(Long.BYTES * 2 + UID.UID_ARRAY_LENGTH);
        rangeStoreDb.serializeKey(keyBuffer, rangeStoreKey);

        assertThat(RangeStoreKeySerde.isKeyInRange(keyBuffer, UID_1, 9))
                .isEqualTo(CompareResult.BELOW_RANGE);
        assertThat(RangeStoreKeySerde.isKeyInRange(keyBuffer, UID_1, 10))
                .isEqualTo(CompareResult.IN_RANGE);
        assertThat(RangeStoreKeySerde.isKeyInRange(keyBuffer, UID_1, 15L))
                .isEqualTo(CompareResult.IN_RANGE);
        assertThat(RangeStoreKeySerde.isKeyInRange(keyBuffer, UID_1, 19))
                .isEqualTo(CompareResult.IN_RANGE);
        assertThat(RangeStoreKeySerde.isKeyInRange(keyBuffer, UID_1, 20))
                .isEqualTo(CompareResult.ABOVE_RANGE);
        assertThat(RangeStoreKeySerde.isKeyInRange(keyBuffer, UID_2, 15L))
                .isEqualTo(CompareResult.MAP_UID_MISMATCH);
    }

    private void getAndAssert(final Txn<ByteBuffer> txn, final UID uid, final long key, final int expectedValue) {
        LOGGER.debug("getAndAssert {}, {}, {}", uid, key, expectedValue);
        final Optional<ValueStoreKey> optValueStoreKey = rangeStoreDb.get(txn, uid, key);
        assertThat(optValueStoreKey).isNotEmpty();
        assertThat(optValueStoreKey.get()).isEqualTo(val(expectedValue));
    }

    private void getAndAssertNotFound(final Txn<ByteBuffer> txn, final UID uid, final long key) {
        LOGGER.debug("getAndAssertNotFound {}, {}", uid, key);

        final Optional<ValueStoreKey> optValueStoreKey = rangeStoreDb.get(txn, uid, key);
        assertThat(optValueStoreKey).isEmpty();
    }

    @Disabled // ReferenceDataFilter will prevent neg values
    @Test
    void testGetWithNegativeNumbers() {

        final List<UID> uids = Arrays.asList(UID_1);

        for (int i = 0; i < uids.size(); i++) {
            LOGGER.debug("Iteration {}, loading for UID {}", i, uids.get(i));
            // non-contiguous ranges
            rangeStoreDb.put(key(uids.get(i), -20, -10), val((i * 10) + 1), false);
            rangeStoreDb.put(key(uids.get(i), -10, 0), val((i * 10) + 2), false);
            rangeStoreDb.put(key(uids.get(i), 0, 1), val((i * 10) + 3), false);
            rangeStoreDb.put(key(uids.get(i), 1, 11), val((i * 10) + 4), false);
            rangeStoreDb.put(key(uids.get(i), 12, 13), val((i * 10) + 5), false);
            rangeStoreDb.put(key(uids.get(i), 13, 21), val((i * 10) + 6), false);
            // gap in ranges
            rangeStoreDb.put(key(uids.get(i), 101, 201), val((i * 10) + 4), false);
            rangeStoreDb.put(key(uids.get(i), 201, 301), val((i * 10) + 8), false);
        }

        rangeStoreDb.logRawDatabaseContents();
        rangeStoreDb.logDatabaseContents();

        lmdbEnv.doWithReadTxn(txn -> {
            final Optional<ValueStoreKey> optValueStoreKey = rangeStoreDb.get(txn, UID_2, 5);
            assertThat(optValueStoreKey).isNotEmpty();
            assertThat(optValueStoreKey.get()).isEqualTo(val(11));
        });
    }

    @Test
    void forEachEntry() throws Exception {

        final UID uid1 = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 1, 0, 0, 1);
        final UID uid2 = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 2, 0, 0, 2);
        final UID uid3 = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 3, 0, 0, 3);

        final RangeStoreKey rangeStoreKey11 = new RangeStoreKey(uid1, Range.of(10L, 20L));
        final RangeStoreKey rangeStoreKey21 = new RangeStoreKey(uid2, Range.of(20L, 25L));
        final RangeStoreKey rangeStoreKey22 = new RangeStoreKey(uid2, Range.of(20L, 30L));
        final RangeStoreKey rangeStoreKey31 = new RangeStoreKey(uid3, Range.of(30L, 40L));

        final ValueStoreKey valueStoreKey11 = new ValueStoreKey(11, (short) 11);
        final ValueStoreKey valueStoreKey21 = new ValueStoreKey(21, (short) 21);
        final ValueStoreKey valueStoreKey22 = new ValueStoreKey(22, (short) 22);
        final ValueStoreKey valueStoreKey31 = new ValueStoreKey(31, (short) 31);

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            rangeStoreDb.put(writeTxn, rangeStoreKey11, valueStoreKey11, false);
            rangeStoreDb.put(writeTxn, rangeStoreKey21, valueStoreKey21, false);
            rangeStoreDb.put(writeTxn, rangeStoreKey22, valueStoreKey22, false);
            rangeStoreDb.put(writeTxn, rangeStoreKey31, valueStoreKey31, false);

            assertThat(rangeStoreDb.getEntryCount(writeTxn)).isEqualTo(4);
        });

        doForEachTest(uid1, 1);
        doForEachTest(uid2, 2);
        doForEachTest(uid3, 1);
    }

    /**
     * Make sure that the entries are stored in the db in the order we expect.
     */
    @Test
    void testSortOrder() {
        final ValueStoreKey valueStoreKey = new ValueStoreKey(123, (short) 0);
        final int max = 100_000;
        final int fromDelta = 30;
        final int firstFrom = 10;
        final int rangeSize = 15;

        final AtomicInteger putCount = new AtomicInteger();
        lmdbEnv.doWithWriteTxn(writeTxn -> {
            for (long from = firstFrom; from <= max; from += fromDelta) {
                final long to = from + rangeSize;
                rangeStoreDb.put(
                        writeTxn, RangeStoreKey.of(UID_1, from, to), valueStoreKey, false);
                putCount.incrementAndGet();

                // Dump the first 50 rows
                if (from == (fromDelta * 50) + firstFrom) {
                    rangeStoreDb.logDatabaseContents(writeTxn, LOGGER::info);
                }
            }
        });

        // Check that the cursor iterates over the ranges in the expected order
        final AtomicReference<Range<Long>> lastRangeRef = new AtomicReference<>();
        lmdbEnv.doWithReadTxn(readTxn -> {
            rangeStoreDb.forEachEntry(readTxn, entry -> {
                final Range<Long> range = entry.getKey().getKeyRange();
                final Range<Long> lastRange = lastRangeRef.get();
                if (lastRange != null) {
                    Assertions.assertThat(range.getFrom())
                            .isGreaterThan(lastRange.getFrom());
                    Assertions.assertThat(range.getFrom() - fromDelta)
                            .isEqualTo(lastRange.getFrom());
                }
                lastRangeRef.set(range);
            });
        });

        lmdbEnv.doWithReadTxn(readTxn -> {
            int i = 0;
            for (long from = firstFrom; from <= max; from += fromDelta) {
                i++;
                final long stopKeyFrom = from + fromDelta + 10;
                final RangeStoreKey startKeyInc = RangeStoreKey.of(UID_1, from, from);
                final RangeStoreKey stopKeyExc = RangeStoreKey.of(UID_1, stopKeyFrom, stopKeyFrom);

                final AtomicInteger count = new AtomicInteger();

                LOGGER.debug("startKeyInc: {}, stopKeyExc: {}", startKeyInc, stopKeyExc);
                rangeStoreDb.forEachEntry(readTxn, KeyRange.closedOpen(startKeyInc, stopKeyExc), entry -> {
                    count.incrementAndGet();
                    LOGGER.debug("Entry {}: {}", count.get(), entry.getKey().getKeyRange());
                });

                if (i < putCount.get() - 2) {
                    Assertions.assertThat(count)
                            .hasValue(2);
                }
            }
        });
    }

    @Test
    void testSortOrder2() {
        final ValueStoreKey valueStoreKey = new ValueStoreKey(123, (short) 0);

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            rangeStoreDb.put(
                    writeTxn, RangeStoreKey.of(UID_1, 1, 10), valueStoreKey, false);
            rangeStoreDb.put(
                    writeTxn,
                    RangeStoreKey.of(UID_1, Long.MAX_VALUE - 10, Long.MAX_VALUE),
                    valueStoreKey,
                    false);
        });

        rangeStoreDb.logDatabaseContents(LOGGER::debug);

        final AtomicReference<Range<Long>> lastRangeRef = new AtomicReference<>();
        lmdbEnv.doWithReadTxn(readTxn -> {
            rangeStoreDb.forEachEntry(readTxn, entry -> {
                final Range<Long> range = entry.getKey().getKeyRange();
                final Range<Long> lastRange = lastRangeRef.get();
                if (lastRange != null) {
                    Assertions.assertThat(range.getFrom())
                            .isGreaterThan(lastRange.getFrom());
                }
                lastRangeRef.set(range);
            });
        });
    }

    @Test
    void testGetEntryCount() {
        final UID uid1 = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 1, 0, 0, 1);
        final UID uid2 = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 2, 0, 0, 2);
        final UID uid3 = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 3, 0, 0, 3);

        final RangeStoreKey rangeStoreKey11 = new RangeStoreKey(uid1, Range.of(10L, 20L));
        final RangeStoreKey rangeStoreKey21 = new RangeStoreKey(uid2, Range.of(20L, 25L));
        final RangeStoreKey rangeStoreKey22 = new RangeStoreKey(uid2, Range.of(20L, 30L));
        final RangeStoreKey rangeStoreKey31 = new RangeStoreKey(uid3, Range.of(30L, 40L));

        final ValueStoreKey valueStoreKey11 = new ValueStoreKey(11, (short) 11);
        final ValueStoreKey valueStoreKey21 = new ValueStoreKey(21, (short) 21);
        final ValueStoreKey valueStoreKey22 = new ValueStoreKey(22, (short) 22);
        final ValueStoreKey valueStoreKey31 = new ValueStoreKey(31, (short) 31);

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            rangeStoreDb.put(writeTxn, rangeStoreKey11, valueStoreKey11, false);
            rangeStoreDb.put(writeTxn, rangeStoreKey21, valueStoreKey21, false);
            rangeStoreDb.put(writeTxn, rangeStoreKey22, valueStoreKey22, false);
            rangeStoreDb.put(writeTxn, rangeStoreKey31, valueStoreKey31, false);

            assertThat(rangeStoreDb.getEntryCount(writeTxn)).isEqualTo(4);
        });

        rangeStoreDb.logRawDatabaseContents();

        lmdbEnv.doWithReadTxn(readTxn -> {
            final long entryCount = rangeStoreDb.getEntryCount(uid2, readTxn);
            assertThat(entryCount).isEqualTo(2);
        });
    }

    private void doForEachTest(final UID uid, final int expectedEntryCount) throws Exception {
        try (final BatchingWriteTxn batchingWriteTxn = lmdbEnv.openBatchingWriteTxn(2)) {
            final AtomicInteger cnt = new AtomicInteger(0);
            rangeStoreDb.deleteMapEntries(
                    batchingWriteTxn,
                    uid,
                    (writeTxn2, keyBuf, valBuf) -> {
                        cnt.incrementAndGet();
                        LOGGER.info("{} {}",
                                ByteBufferUtils.byteBufferInfo(keyBuf),
                                ByteBufferUtils.byteBufferInfo(valBuf));
                    });

            assertThat(cnt).hasValue(expectedEntryCount);
        }
    }

//
//    @Test
//    public void testRanges() {
//        rangeStoreDb.put(key(uid1, 1, 0), val(0), false);
//        rangeStoreDb.put(key(uid1, 1, 1), val(0), false);
//        rangeStoreDb.put(key(uid1, 1, 2), val(0), false);
//        rangeStoreDb.put(key(uid1, 1, 3), val(0), false);
//        rangeStoreDb.put(key(uid1, 1, 30), val(0), false);
//        rangeStoreDb.put(key(uid1, 1, 300), val(0), false);
//        rangeStoreDb.put(key(uid1, 2, 0), val(0), false);
//        rangeStoreDb.put(key(uid1, 2, 1), val(0), false);
//        rangeStoreDb.put(key(uid1, 2, 2), val(0), false);
//        rangeStoreDb.put(key(uid1, 2, 3), val(0), false);
//        rangeStoreDb.put(key(uid1, 2, 30), val(0), false);
//        rangeStoreDb.put(key(uid1, 2, 300), val(0), false);
//
//        rangeStoreDb.logRawDatabaseContents();
//        rangeStoreDb.logDatabaseContents();
//
//        RangeStoreKeySerde keySerde = new RangeStoreKeySerde();
//        ValueStoreKeySerde valueSerde = new ValueStoreKeySerde();
//
//        final Range<Long> startRange = new Range<>(2L, 301L);
//        final RangeStoreKey startRangeStoreKey = new RangeStoreKey(uid1, startRange);
//        final ByteBuffer startKeyBuf = keySerde.serialize(startRangeStoreKey);
//
//        final Range<Long> endRange = new Range<>(0L, 0L);
//        final RangeStoreKey endRangeStoreKey = new RangeStoreKey(uid1, endRange);
//        final ByteBuffer endKeyBuf = keySerde.serialize(endRangeStoreKey);
//
//
//        LOGGER.debug("Using range [{}] to [{}]", endRangeStoreKey, startRangeStoreKey);
//        KeyRange<ByteBuffer> keyRange = KeyRange.atLeastBackward(startKeyBuf);
//
//        LmdbUtils.logContentsInRange(
//                lmdbEnv,
//                rangeStoreDb.getLmdbDbi(),
//                keyRange,
//                buf -> keySerde.deserialize(buf).toString(),
//                buf -> valueSerde.deserialize(buf).toString());
//    }

    private RangeStoreKey key(final UID uid, final long fromInc, final long toExc) {
        return new RangeStoreKey(uid, new Range<>(fromInc, toExc));
    }

    private ValueStoreKey val(final int val) {
        return new ValueStoreKey(val, (short) val);
    }

    @Disabled // a bit of manual testing of a Striped Semaphore
    @Test
    void testStripedSemaphore() throws InterruptedException {

        final List<String> keys = Arrays.asList(
                "one",
                "two",
                "three",
                "four");

        final Map<String, AtomicInteger> map = keys.stream()
                .map(k ->
                        Tuple.of(k, new AtomicInteger())
                )
                .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2));

        final Striped<Semaphore> stripedSemaphore = Striped.semaphore(10, 1);

        final ExecutorService executorService = Executors.newFixedThreadPool(6);

        IntStream.rangeClosed(1, 1000000)
                .forEach(i -> {
                    executorService.submit(() -> {
                        final String key = keys.get(new Random().nextInt(keys.size()));
                        LOGGER.debug("Using key {} on thread {}", key, Thread.currentThread().getName());
                        final Semaphore semaphore = stripedSemaphore.get(key);

                        try {
                            try {
                                semaphore.acquire();
                            } catch (final InterruptedException e) {
                                e.printStackTrace();
                            }
                            final AtomicInteger atomicInteger = map.get(key);
                            boolean success = atomicInteger.compareAndSet(0, 1);
                            if (!success) {
                                throw new RuntimeException("compare and set failed");
                            }
                            ThreadUtil.sleepIgnoringInterrupts(500);

                            success = atomicInteger.compareAndSet(1, 0);
                            if (!success) {
                                throw new RuntimeException("compare and set failed");
                            }
                        } finally {
                            semaphore.release();

                        }
                    });
                });

        executorService.awaitTermination(20, TimeUnit.SECONDS);
    }

    private static class Obj {

        private final String key;
        private final AtomicInteger atomicInteger = new AtomicInteger();

        Obj(final String key) {
            this.key = key;
        }


    }
}
