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

package stroom.lmdb;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.lmdb.serde.IntegerSerde;
import stroom.lmdb.serde.Serde;
import stroom.lmdb.serde.StringSerde;
import stroom.util.io.ByteSize;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lmdbjava.DbiFlags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class TestLmdbPerformance extends AbstractDualEnvLmdbTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestLmdbPerformance.class);
    public static final int ITERATIONS = 1_000_000;
    public static final int ROUNDS = 7;

    private final ByteBufferPool byteBufferPool = new ByteBufferPoolFactory().getByteBufferPool();

    private BasicLmdbDb<Integer, String> db1;
    private BasicLmdbDb<Integer, String> db2;

    private BasicLmdbDb<Integer, String> createStandardDb(final LmdbEnv lmdbEnv,
                                                          final String name) {
        return new BasicLmdbDb<>(
                lmdbEnv,
                byteBufferPool,
                new IntegerSerde(),
                new StringSerde(),
                name,
                DbiFlags.MDB_CREATE);
    }

    private BasicLmdbDb<Integer, String> createIntegerKeyDb(final LmdbEnv lmdbEnv,
                                                            final String name) {
        return new BasicLmdbDb<>(
                lmdbEnv,
                byteBufferPool,
                Serde.usingNativeOrder(new IntegerSerde()), // Must use native order for MDB_INTEGERKEY
                new StringSerde(),
                name,
                DbiFlags.MDB_CREATE,
                DbiFlags.MDB_INTEGERKEY);
    }

    /**
     * Compare using MDB_APPEND vs not, when repeatedly putting a batch of entries.
     */
    @Disabled // manual perf test only
    @Test
    void testAppendVsNonAppend() {
        final int iterations = ITERATIONS;
        final int rounds = ROUNDS;
        final boolean deleteBetweenRounds = false;

        db1 = createStandardDb(lmdbEnv1, "db1");
        db2 = createStandardDb(lmdbEnv2, "db2");

        LOGGER.info("Ordered puts");
        putData(db1, iterations, rounds, true, deleteBetweenRounds);

        LOGGER.info("Random puts");
        putData(db2, iterations, rounds, false, deleteBetweenRounds);
    }

    /**
     * Compare using MDB_APPEND vs not, when repeatedly putting a batch of entries
     * then deleting them all.
     */
    @Disabled // manual perf test only
    @Test
    void testAppendVsNonAppend_withDeletes() {
        final int iterations = ITERATIONS;
        final int rounds = ROUNDS;
        final boolean deleteBetweenRounds = true;

        db1 = createStandardDb(lmdbEnv1, "db1");
        db2 = createStandardDb(lmdbEnv2, "db2");

        LOGGER.info("Ordered puts");
        putData(db1, iterations, rounds, true, deleteBetweenRounds);

        LOGGER.info("Random puts");
        putData(db2, iterations, rounds, false, deleteBetweenRounds);
    }

    /**
     * Compare using MDB_APPEND with and without MDB_INTEGERKEY
     */
    @Disabled // manual perf test only
    @Test
    void testAppendVsAppendWithIntegerKey() {
        final int iterations = ITERATIONS;
        final int rounds = ROUNDS;
        final boolean deleteBetweenRounds = false;

        db1 = createStandardDb(lmdbEnv1, "db1");
        db2 = createIntegerKeyDb(lmdbEnv2, "db2");

        LOGGER.info("Ordered puts");
        putData(db1, iterations, rounds, true, deleteBetweenRounds);

        LOGGER.info("Ordered puts (MDB_INTEGERKEY)");
        putData(db2, iterations, rounds, true, deleteBetweenRounds);
    }

    /**
     * Compare using MDB_APPEND with and without MDB_INTEGERKEY, deleting entries
     * as we go
     */
    @Disabled // manual perf test only
    @Test
    void testAppendVsAppendWithIntegerKey_withDeletes() {
        final int iterations = ITERATIONS;
        final int rounds = ROUNDS;
        final boolean deleteBetweenRounds = true;

        db1 = createStandardDb(lmdbEnv1, "db1");
        db2 = createIntegerKeyDb(lmdbEnv2, "db2");

        LOGGER.info("Ordered puts");
        putData(db1, iterations, rounds, true, deleteBetweenRounds);

        LOGGER.info("Ordered puts (MDB_INTEGERKEY)");
        putData(db2, iterations, rounds, true, deleteBetweenRounds);
    }

    private void putData(final AbstractLmdbDb<Integer, String> db,
                         final int iterations,
                         final int rounds,
                         final boolean isOrdered,
                         final boolean deleteBetweenRounds) {

        final LmdbEnv lmdbEnv = db.getLmdbEnvironment();
        int round = 0;
        final List<Entry<Integer, String>> inputData = new ArrayList<>(iterations);

        while (round < rounds) {
            inputData.clear();
            for (int i = 0; i < iterations; i++) {
                inputData.add(Map.entry(i + ((int) iterations * round), "value-" + i));
            }

            if (!isOrdered) {
                // Shuffle the data and put the keys in random order
                Collections.shuffle(inputData);
            }

            // Put the ordered keys in MDB_APPEND mode
            DurationTimer timer = DurationTimer.start();
            putValues(db, false, isOrdered, inputData);
            timer.stop();
            final double bytesPerEntry = lmdbEnv.getSizeOnDisk() / (double) db.getEntryCount();
            LOGGER.info("{} puts,    size on disk: {}, bytes/entry: {}, time: {}, entry count: {}",
                    ModelStringUtil.formatCsv(iterations),
                    ByteSize.ofBytes(lmdbEnv.getSizeOnDisk()),
                    bytesPerEntry,
                    timer,
                    ModelStringUtil.formatCsv(db.getEntryCount()));

            if (deleteBetweenRounds) {
                timer = DurationTimer.start();
                deleteValues(db, inputData);
                timer.stop();
                LOGGER.info("{} deletes, size on disk: {}, bytes/entry: {}, time: {}, entry count: {}",
                        ModelStringUtil.formatCsv(iterations),
                        ByteSize.ofBytes(lmdbEnv.getSizeOnDisk()),
                        bytesPerEntry,
                        timer,
                        ModelStringUtil.formatCsv(db.getEntryCount()));
            }
            round++;
        }
    }
}
