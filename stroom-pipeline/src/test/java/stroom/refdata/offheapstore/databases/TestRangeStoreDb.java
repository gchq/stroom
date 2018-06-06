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

import com.google.common.util.concurrent.Striped;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.apache.hadoop.util.ThreadUtil;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.Range;
import stroom.refdata.lmdb.LmdbUtils;
import stroom.refdata.offheapstore.RangeStoreKey;
import stroom.refdata.offheapstore.UID;
import stroom.refdata.offheapstore.ValueStoreKey;
import stroom.refdata.offheapstore.serdes.RangeStoreKeySerde;
import stroom.refdata.offheapstore.serdes.ValueStoreKeySerde;

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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class TestRangeStoreDb extends AbstractLmdbDbTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestRangeStoreDb.class);

    private RangeStoreDb rangeStoreDb;

    final UID uid1 = UID.of(0, 0, 0, 1);
    final UID uid2 = UID.of(0, 0, 0, 2);
    final UID uid3 = UID.of(0, 0, 0, 3);

    @Before
    @Override
    public void setup() {
        super.setup();

        rangeStoreDb = new RangeStoreDb(
                lmdbEnv,
                new RangeStoreKeySerde(),
                new ValueStoreKeySerde());
    }

    @Test
    public void testGet() {

        final List<UID> uids = Arrays.asList(uid1, uid2, uid3);

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
        LmdbUtils.doWithReadTxn(lmdbEnv, txn -> {
            for (int i = 0; i < uids.size(); i++) {
                LOGGER.debug("Iteration {}, testing with UID {}", i, uids.get(i));

                getAndAssert(txn, uids.get(i), 1, (i *  10) + 1); // on range start
                getAndAssert(txn, uids.get(i), 5, (i *  10) + 1); // in range middle
                getAndAssert(txn, uids.get(i), 10, (i *  10) + 1); // on range end
                getAndAssert(txn, uids.get(i), 11, (i *  10) + 2); // on range start
                getAndAssert(txn, uids.get(i), 300, (i *  10) + 5); // on range end

                getAndAssertNotFound(txn, uids.get(i), 0); // not in a range
                getAndAssertNotFound(txn, uids.get(i), 50); // not in a range
                getAndAssertNotFound(txn, uids.get(i), 301); // not in a range
            }
        });
    }

    @Test
    public void testContainsMapDefinition() {

        final List<UID> uids = Arrays.asList(uid1, uid2, uid3);
        final UID uid4 = UID.of(0, 0, 0, 4);

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

        LmdbUtils.doWithReadTxn(lmdbEnv, txn -> {
            boolean result;
            for (int i = 0; i < uids.size(); i++) {
                result = rangeStoreDb.containsMapDefinition(txn, uids.get(i));
                assertThat(result).isTrue();
            }

            // no entries exist for uid4
            result = rangeStoreDb.containsMapDefinition(txn, uid4);
            assertThat(result).isFalse();
        });
    }

    private void getAndAssert(Txn<ByteBuffer> txn, UID uid, long key, int expectedValue) {
        LOGGER.debug("getAndAssert {}, {}, {}", uid, key, expectedValue);
        Optional<ValueStoreKey> optValueStoreKey = rangeStoreDb.get(txn, uid, key);
        assertThat(optValueStoreKey).isNotEmpty();
        assertThat(optValueStoreKey.get()).isEqualTo(val(expectedValue));
    }
    private void getAndAssertNotFound(Txn<ByteBuffer> txn, UID uid, long key) {
        LOGGER.debug("getAndAssertNotFound {}, {}, {}", uid, key);

        Optional<ValueStoreKey> optValueStoreKey = rangeStoreDb.get(txn, uid, key);
        assertThat(optValueStoreKey).isEmpty();
    }

    @Ignore // ReferenceDataFilter will prevent neg values
    @Test
    public void testGetWithNegativeNumbers() {

        final List<UID> uids = Arrays.asList(uid1);

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

        LmdbUtils.doWithReadTxn(lmdbEnv, txn -> {
            Optional<ValueStoreKey> optValueStoreKey = rangeStoreDb.get(txn, uid2, 5);
            assertThat(optValueStoreKey).isNotEmpty();
            assertThat(optValueStoreKey.get()).isEqualTo(val(11));
        });
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

    private RangeStoreKey key(UID uid, long fromInc, long toExc) {
        return new RangeStoreKey(uid, new Range<>(fromInc, toExc));
    }

    private ValueStoreKey val(int val) {
        return new ValueStoreKey(val, (short) val);
    }

    @Ignore // a bit of manual testing of a Striped Semaphore
    @Test
    public void testStripedSemaphore() throws InterruptedException {

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

        IntStream.rangeClosed(1,1000000)
                .forEach(i -> {
                    executorService.submit(() -> {
                        String key = keys.get(new Random().nextInt(keys.size()));
                        LOGGER.debug("Using key {} on thread {}", key, Thread.currentThread().getName());
                        Semaphore semaphore = stripedSemaphore.get(key);

                        try {
                            try {
                                semaphore.acquire();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            AtomicInteger atomicInteger = map.get(key);
                            boolean success = atomicInteger.compareAndSet(0, 1);
                            if (!success) {
                                throw new RuntimeException("compare and set failed");
                            }
                            ThreadUtil.sleepAtLeastIgnoreInterrupts(500);

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
        private String key;
        private AtomicInteger atomicInteger = new AtomicInteger();

        Obj(final String key) {
            this.key = key;
        }


    }
}