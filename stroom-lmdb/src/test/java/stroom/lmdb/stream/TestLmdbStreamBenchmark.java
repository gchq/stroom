package stroom.lmdb.stream;

import stroom.test.common.TestUtil;
import stroom.test.common.TestUtil.TimedCase;
import stroom.util.io.ByteSize;
import stroom.util.io.FileUtil;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class TestLmdbStreamBenchmark {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestLmdbStreamBenchmark.class);

    private Path dbDir = null;
    private Env<ByteBuffer> env;
    private Dbi<ByteBuffer> dbi;

    // Used to perform profiling.
    @Disabled
    @Test
    void testForwardRange() throws IOException {
        dbDir = Files.createTempDirectory("stroom");
        env = Env.create()
                .setMapSize(ByteSize.ofGibibytes(100).getBytes())
                .open(dbDir.toFile());

        dbi = env.openDbi("test".getBytes(StandardCharsets.UTF_8), DbiFlags.MDB_CREATE);
        final ByteBuffer key = ByteBuffer.allocateDirect(Integer.BYTES + Integer.BYTES);
        final ByteBuffer value = ByteBuffer.allocateDirect(0);
        try (final Txn<ByteBuffer> txn = env.txnWrite()) {
            for (int i = 0; i < 1000000; i++) {
                for (int j = 0; j < 100; j++) {
                    key.putInt(i);
                    key.putInt(j);
                    key.flip();
                    dbi.put(txn, key, value);
                }
            }
            txn.commit();
        }

        final TimedCase testForwardRange = TimedCase.of(
                "Test forward range",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(42, 42);
                    final ByteBuffer stop = createTestBuffer(900000, 42);
                    testStream(LmdbKeyRange.builder()
                                    .start(start)
                                    .stop(stop)
                                    .build(),
                            89995801L,
                            createTestBuffer(42, 42),
                            createTestBuffer(900000, 42));
                });

        TestUtil.comparePerformance(
                1000,
                10000000,
                LOGGER::info,
                testForwardRange);
    }

    @Test
    void testIterationMethods() throws IOException {
//        System.setProperty(Env.DISABLE_CHECKS_PROP, "true");
        dbDir = Files.createTempDirectory("stroom");
        env = Env.create()
                .setMapSize(ByteSize.ofGibibytes(10).getBytes())
                .open(dbDir.toFile());

        dbi = env.openDbi("test".getBytes(StandardCharsets.UTF_8), DbiFlags.MDB_CREATE);
        writeData();

        final long totalRows = 10000000L;
        final ByteBuffer minPoint = createTestBuffer(0, 0);
        final ByteBuffer maxPoint = createTestBuffer(99999, 99);
        final ByteBuffer lowPoint = createTestBuffer(42, 42);
        final ByteBuffer highPoint = createTestBuffer(90000, 42);

        final List<TimedCase> cases = new ArrayList<>();
        cases.add(TimedCase.of("Test utility iterator", (round, iterations) -> {
            final AtomicInteger count = new AtomicInteger();
            try (final Txn<ByteBuffer> txn = env.txnRead()) {
                LmdbIterable.iterate(txn, dbi, (key, val) -> {
                    count.incrementAndGet();
                });
            }
            assertThat(count.get()).isEqualTo(iterations);
        }));

        cases.add(TimedCase.of(
                "Test library iterator",
                (round, iterations) -> {
                    long count = 0;
                    try (final Txn<ByteBuffer> txn = env.txnRead()) {
                        for (final KeyVal<ByteBuffer> kv : dbi.iterate(txn)) {
                            count++;
                        }
                    }
                    assertThat(count).isEqualTo(iterations);
                }));

        // ALL FORWARD
        addTestSet(cases,
                "all",
                KeyRange.all(),
                LmdbKeyRange.all(),
                totalRows,
                minPoint,
                maxPoint);

        // ALL BACKWARD
        addTestSet(cases,
                "allBackward",
                KeyRange.allBackward(),
                LmdbKeyRange.allReverse(),
                totalRows,
                maxPoint,
                minPoint);

        // AT LEAST
        addTestSet(cases,
                "atLeast",
                KeyRange.atLeast(lowPoint),
                LmdbKeyRange.builder().start(lowPoint).build(),
                9995758L,
                lowPoint,
                maxPoint);

        // AT LEAST BACKWARD
        addTestSet(cases,
                "atLeastBackward",
                KeyRange.atLeastBackward(highPoint),
                LmdbKeyRange.builder().start(highPoint).reverse().build(),
                9000043L,
                highPoint,
                minPoint);

        // AT MOST
        addTestSet(cases,
                "atMost",
                KeyRange.atMost(highPoint),
                LmdbKeyRange.builder().stop(highPoint).build(),
                9000043L,
                minPoint,
                highPoint);

        // AT MOST BACKWARD
        addTestSet(cases,
                "atMostBackward",
                KeyRange.atMostBackward(lowPoint),
                LmdbKeyRange.builder().stop(lowPoint).reverse().build(),
                9995758L,
                maxPoint,
                lowPoint);

        // FORWARD_CLOSED
        addTestSet(cases,
                "closed",
                KeyRange.closed(lowPoint, highPoint),
                LmdbKeyRange.builder().start(lowPoint).stop(highPoint).build(),
                8995801L,
                lowPoint,
                highPoint);

        // BACKWARD_CLOSED
        addTestSet(cases,
                "closedBackward",
                KeyRange.closedBackward(highPoint, lowPoint),
                LmdbKeyRange.builder().start(highPoint).stop(lowPoint).reverse().build(),
                8995801L,
                highPoint,
                lowPoint);

        // FORWARD_CLOSED_OPEN
        addTestSet(cases,
                "closedOpen",
                KeyRange.closedOpen(lowPoint, highPoint),
                LmdbKeyRange.builder().start(lowPoint).stop(highPoint, false).build(),
                8995800L,
                lowPoint,
                createTestBuffer(90000, 41));

        // BACKWARD_CLOSED_OPEN
        addTestSet(cases,
                "closedOpenBackward",
                KeyRange.closedOpenBackward(highPoint, lowPoint),
                LmdbKeyRange.builder().start(highPoint).stop(lowPoint, false).reverse().build(),
                8995800L,
                highPoint,
                createTestBuffer(42, 43));

        // FORWARD_GREATER_THAN
        addTestSet(cases,
                "greaterThan",
                KeyRange.greaterThan(lowPoint),
                LmdbKeyRange.builder().start(lowPoint, false).build(),
                9995757L,
                createTestBuffer(42, 43),
                maxPoint);

        // BACKWARD_GREATER_THAN
        addTestSet(cases,
                "greaterThanBackward",
                KeyRange.greaterThanBackward(highPoint),
                LmdbKeyRange.builder().start(highPoint, false).reverse().build(),
                9000042L,
                createTestBuffer(90000, 41),
                minPoint);

        // FORWARD_LESS_THAN
        addTestSet(cases,
                "lessThan",
                KeyRange.lessThan(highPoint),
                LmdbKeyRange.builder().stop(highPoint, false).build(),
                9000042L,
                minPoint,
                createTestBuffer(90000, 41));

        // BACKWARD_LESS_THAN
        addTestSet(cases,
                "lessThanBackward",
                KeyRange.lessThanBackward(lowPoint),
                LmdbKeyRange.builder().stop(lowPoint, false).reverse().build(),
                9995757L,
                maxPoint,
                createTestBuffer(42, 43));

        // FORWARD_OPEN
        addTestSet(cases,
                "open",
                KeyRange.open(lowPoint, highPoint),
                LmdbKeyRange.builder().start(lowPoint, false).stop(highPoint, false).build(),
                8995799L,
                createTestBuffer(42, 43),
                createTestBuffer(90000, 41));

        // BACKWARD_OPEN
        addTestSet(cases,
                "openBackward",
                KeyRange.openBackward(highPoint, lowPoint),
                LmdbKeyRange.builder()
                        .start(highPoint, false)
                        .stop(lowPoint, false)
                        .reverse()
                        .build(),
                8995799L,
                createTestBuffer(90000, 41),
                createTestBuffer(42, 43));

        // FORWARD_OPEN_CLOSED
        addTestSet(cases,
                "openClosed",
                KeyRange.openClosed(lowPoint, highPoint),
                LmdbKeyRange.builder().start(lowPoint, false).stop(highPoint).build(),
                8995800L,
                createTestBuffer(42, 43),
                highPoint);

        // BACKWARD_OPEN_CLOSED
        addTestSet(cases,
                "openClosedBackward",
                KeyRange.openClosedBackward(highPoint, lowPoint),
                LmdbKeyRange.builder().start(highPoint, false).stop(lowPoint).reverse().build(),
                8995800L,
                createTestBuffer(90000, 41),
                lowPoint);

        // PREFIX
        cases.add(TimedCase.of(
                "Test Stream prefix",
                (round, iterations) -> {
                    final ByteBuffer prefix = createTestBuffer(90000);
                    testStream(LmdbKeyRange.builder().prefix(prefix).build(),
                            100,
                            createTestBuffer(90000, 0),
                            createTestBuffer(90000, 99));
                }));

        cases.add(TimedCase.of(
                "Test Stream prefix reversed",
                (round, iterations) -> {
                    final ByteBuffer prefix = createTestBuffer(90000);
                    testStream(LmdbKeyRange.builder().prefix(prefix).reverse().build(),
                            100,
                            createTestBuffer(90000, 99),
                            createTestBuffer(90000, 0));
                }));

        TestUtil.comparePerformance(
                3,
                10000000,
                LOGGER::info,
                cases.toArray(new TimedCase[0]));
    }

    private void addTestSet(final List<TimedCase> cases,
                            final String name,
                            final KeyRange<ByteBuffer> keyRange,
                            final LmdbKeyRange lmdbKeyRange,
                            final long expectedCount,
                            final ByteBuffer expectedFirst,
                            final ByteBuffer expectedLast) {
        cases.add(TimedCase.of(
                "Test library " + name,
                (round, iterations) -> testKeyRange(keyRange, expectedCount, expectedFirst, expectedLast)));

        cases.add(TimedCase.of(
                "Test New Stream " + name,
                (round, iterations) -> testStream(lmdbKeyRange, expectedCount, expectedFirst, expectedLast)));

        cases.add(TimedCase.of(
                "Test New Iterator " + name,
                (round, iterations) -> testIterator(lmdbKeyRange, expectedCount, expectedFirst, expectedLast)));
    }

    private void testKeyRange(final KeyRange<ByteBuffer> keyRange,
                              final long expectedCount,
                              final ByteBuffer expectedFirst,
                              final ByteBuffer expectedLast) {
        SoftAssertions.assertSoftly(softAssertions -> {
            final AtomicLong total = new AtomicLong();
            try (final Txn<ByteBuffer> txn = env.txnRead()) {
                for (final KeyVal<ByteBuffer> kv : dbi.iterate(txn, keyRange)) {
                    final long count = total.incrementAndGet();
                    if (count == 1) {
                        softAssertions.assertThat(kv.key())
                                .withFailMessage(
                                        "%s is not equal to %s",
                                        getString(kv.key()),
                                        getString(expectedFirst))
                                .isEqualTo(expectedFirst);
                    }
                    if (count == expectedCount) {
                        softAssertions.assertThat(kv.key())
                                .withFailMessage(
                                        "%s is not equal to %s",
                                        getString(kv.key()),
                                        getString(expectedLast))
                                .isEqualTo(expectedLast);
                    }
                }
            }
            assertThat(total.get()).isEqualTo(expectedCount);
        });
    }

    private void testStream(final LmdbKeyRange lmdbKeyRange,
                            final long expectedCount,
                            final ByteBuffer expectedFirst,
                            final ByteBuffer expectedLast) {
        SoftAssertions.assertSoftly(softAssertions -> {
            final AtomicLong total = new AtomicLong();
            try (final Txn<ByteBuffer> txn = env.txnRead()) {
                try (final Stream<LmdbEntry> stream = LmdbStream.stream(txn, dbi, lmdbKeyRange)) {
                    stream.forEach(entry -> {
                        final long count = total.incrementAndGet();
                        if (count == 1) {
                            softAssertions.assertThat(entry.getKey())
                                    .withFailMessage(
                                            "%s is not equal to %s",
                                            getString(entry.getKey()),
                                            getString(expectedFirst))
                                    .isEqualTo(expectedFirst);
                        }
                        if (count == expectedCount) {
                            softAssertions.assertThat(entry.getKey())
                                    .withFailMessage(
                                            "%s is not equal to %s",
                                            getString(entry.getKey()),
                                            getString(expectedLast))
                                    .isEqualTo(expectedLast);
                        }
                    });
                }
            }
            assertThat(total.get()).isEqualTo(expectedCount);
        });
    }

    private void testIterator(final LmdbKeyRange lmdbKeyRange,
                              final long expectedCount,
                              final ByteBuffer expectedFirst,
                              final ByteBuffer expectedLast) {
        SoftAssertions.assertSoftly(softAssertions -> {
            final AtomicLong total = new AtomicLong();
            try (final Txn<ByteBuffer> txn = env.txnRead()) {
                try (final LmdbIterable iterable = LmdbIterable.create(txn, dbi, lmdbKeyRange)) {
                    iterable.forEach(entry -> {
                        final long count = total.incrementAndGet();
                        if (count == 1) {
                            softAssertions.assertThat(entry.getKey())
                                    .withFailMessage(
                                            "%s is not equal to %s",
                                            getString(entry.getKey()),
                                            getString(expectedFirst))
                                    .isEqualTo(expectedFirst);
                        }
                        if (count == expectedCount) {
                            softAssertions.assertThat(entry.getKey())
                                    .withFailMessage(
                                            "%s is not equal to %s",
                                            getString(entry.getKey()),
                                            getString(expectedLast))
                                    .isEqualTo(expectedLast);
                        }
                    });
                }
            }
            assertThat(total.get()).isEqualTo(expectedCount);
        });
    }

    private String getString(final ByteBuffer byteBuffer) {
        final ByteBuffer duplicate = byteBuffer.duplicate();
        return "[" + duplicate.getInt() + "," + duplicate.getInt() + "]";
    }

    @AfterEach
    final void teardown() {
        if (env != null) {
            env.close();
        }
        env = null;
        if (Files.isDirectory(dbDir)) {
            FileUtil.deleteDir(dbDir);
        }
    }

    private void writeData() {
        final ByteBuffer key = ByteBuffer.allocateDirect(Integer.BYTES + Integer.BYTES);
        final ByteBuffer value = ByteBuffer.allocateDirect(0);
        try (final Txn<ByteBuffer> txn = env.txnWrite()) {
            for (int i = 0; i < 100000; i++) {
                for (int j = 0; j < 100; j++) {
                    key.putInt(i);
                    key.putInt(j);
                    key.flip();
                    dbi.put(txn, key, value);
                }
            }
            txn.commit();
        }
    }

    private ByteBuffer createTestBuffer(final int i1) {
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(Integer.BYTES);
        byteBuffer.putInt(i1);
        byteBuffer.flip();
        return byteBuffer;
    }

    private ByteBuffer createTestBuffer(final int i1, final int i2) {
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(Integer.BYTES + Integer.BYTES);
        byteBuffer.putInt(i1);
        byteBuffer.putInt(i2);
        byteBuffer.flip();
        return byteBuffer;
    }
}
