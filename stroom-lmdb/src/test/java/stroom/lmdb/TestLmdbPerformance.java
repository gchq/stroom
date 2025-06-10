package stroom.lmdb;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.lmdb.serde.IntegerSerde;
import stroom.lmdb.serde.Serde;
import stroom.lmdb.serde.StringSerde;
import stroom.test.common.TestUtil;
import stroom.test.common.TestUtil.TimedCase;
import stroom.util.io.ByteSize;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lmdbjava.DbiFlags;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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

    private BasicLmdbDb<String, String> createStringStringDb(final LmdbEnv lmdbEnv,
                                                             final String name) {
        return new BasicLmdbDb<>(
                lmdbEnv,
                byteBufferPool,
                new StringSerde(),
                new StringSerde(),
                name,
                DbiFlags.MDB_CREATE);
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

    @Disabled // manual perf test only
    @Test
    void testGetVsHeapMap() {
        final int iterations = 10_000_000;
        final int rounds = 4;
        final BasicLmdbDb<String, String> db3 = createStringStringDb(lmdbEnv1, "db3");
        final Map<String, String> hashMap = new HashMap<>();
        final Map<String, String> concurrentHashMap = new ConcurrentHashMap<>();
        final Cache<String, String> cache = Caffeine.newBuilder()
                .build();

        lmdbEnv1.doWithWriteTxn(writeTxn -> {
            db3.put(writeTxn, "foo", "bar", true);
        });
        hashMap.put("foo", "bar");
        concurrentHashMap.put("foo", "bar");
        cache.put("foo", "bar");

        lmdbEnv1.doWithReadTxn(txn -> {
            try (PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(10)) {
                final ByteBuffer keyBuffer = pooledByteBuffer.getByteBuffer();
                db3.serializeKey(keyBuffer, "foo");

                final TimedCase lmdbGetCaseWithSer = TimedCase.of("lmdb-get-with-ser", (round, iterations2) -> {
                    for (long i = 0; i < iterations2; i++) {
                        final Optional<String> foo = db3.get(txn, "foo");
                        if (foo.isEmpty()) {
                            throw new RuntimeException("Empty");
                        }
                    }
                });

                final TimedCase lmdbGetCaseWithoutSer = TimedCase.of(
                        "lmdb-get-without-ser",
                        (round, iterations2) -> {
                            for (long i = 0; i < iterations2; i++) {
                                final Optional<ByteBuffer> foo = db3.getAsBytes(txn, keyBuffer);
                                if (foo.isEmpty()) {
                                    throw new RuntimeException("Empty");
                                }
                            }
                        });

                final TimedCase hashMapGet = TimedCase.of("hashMap", (round, iterations2) -> {
                    for (long i = 0; i < iterations2; i++) {
                        final String val = hashMap.get("foo");
                        if (val == null) {
                            throw new RuntimeException("Empty");
                        }
                    }
                });

                final TimedCase concurrentMapGet = TimedCase.of("concurrentMap", (round, iterations2) -> {
                    for (long i = 0; i < iterations2; i++) {
                        final String val = concurrentHashMap.get("foo");
                        if (val == null) {
                            throw new RuntimeException("Empty");
                        }
                    }
                });

                final TimedCase cacheGet = TimedCase.of("cache", (round, iterations2) -> {
                    for (long i = 0; i < iterations2; i++) {
                        final String val = cache.getIfPresent("foo");
                        if (val == null) {
                            throw new RuntimeException("Empty");
                        }
                    }
                });

                TestUtil.comparePerformance(
                        rounds,
                        iterations,
                        LOGGER::info,
                        lmdbGetCaseWithSer,
                        lmdbGetCaseWithoutSer,
                        hashMapGet,
                        concurrentMapGet,
                        cacheGet);
            }
        });
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
            double bytesPerEntry = lmdbEnv.getSizeOnDisk() / (double) db.getEntryCount();
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
