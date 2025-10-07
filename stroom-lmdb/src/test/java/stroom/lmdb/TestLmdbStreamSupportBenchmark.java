package stroom.lmdb;

import stroom.lmdb.LmdbStreamSupport.AbstractStreamBuilder;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class TestLmdbStreamSupportBenchmark {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestLmdbStreamSupportBenchmark.class);

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
                    testStream((txn, dbi) -> LmdbStreamSupport.streamBuilder(txn, dbi)
                                    .start(start)
                                    .stop(stop),
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

        final TimedCase testUtilityIterator = TimedCase.of("Test utility iterator", (round, iterations) -> {
            final AtomicInteger count = new AtomicInteger();
            try (final Txn<ByteBuffer> txn = env.txnRead()) {
                LmdbIterableSupport.iterate(txn, dbi, (key, val) -> {
                    count.incrementAndGet();
                });
            }
            assertThat(count.get()).isEqualTo(iterations);
        });

        final TimedCase testLibraryIterator = TimedCase.of(
                "Test library iterator",
                (round, iterations) -> {
                    long count = 0;
                    try (final Txn<ByteBuffer> txn = env.txnRead()) {
                        for (final KeyVal<ByteBuffer> kv : dbi.iterate(txn)) {
                            count++;
                        }
                    }
                    assertThat(count).isEqualTo(iterations);
                });

        // ALL FORWARD
        final TimedCase testLibraryAll = TimedCase.of(
                "Test library all",
                (round, iterations) -> {
                    testKeyRange(KeyRange.all(),
                            iterations,
                            createTestBuffer(0, 0),
                            createTestBuffer(99999, 99));
                });

        final TimedCase testStreamAll = TimedCase.of(
                "Test Stream all",
                (round, iterations) -> {
                    testStream(LmdbStreamSupport::streamBuilder,
                            iterations,
                            createTestBuffer(0, 0),
                            createTestBuffer(99999, 99));
                });

        // ALL BACKWARD
        final TimedCase testLibraryAllBackward = TimedCase.of(
                "Test library allBackward",
                (round, iterations) -> {
                    testKeyRange(KeyRange.allBackward(),
                            iterations,
                            createTestBuffer(99999, 99),
                            createTestBuffer(0, 0));
                });

        final TimedCase testStreamAllBackward = TimedCase.of(
                "Test Stream allBackward",
                (round, iterations) -> {
                    testStream((txn, dbi) -> LmdbStreamSupport.streamBuilder(txn, dbi)
                                    .reverse(),
                            iterations,
                            createTestBuffer(99999, 99),
                            createTestBuffer(0, 0));
                });

        // AT LEAST
        final TimedCase testLibraryAtLeast = TimedCase.of(
                "Test library atLeast",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(42, 42);
                    testKeyRange(KeyRange.atLeast(start),
                            9995758L,
                            createTestBuffer(42, 42),
                            createTestBuffer(99999, 99));
                });

        final TimedCase testStreamAtLeast = TimedCase.of(
                "Test Stream atLeast",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(42, 42);
                    testStream((txn, dbi) -> LmdbStreamSupport.streamBuilder(txn, dbi)
                                    .start(start),
                            9995758L,
                            createTestBuffer(42, 42),
                            createTestBuffer(99999, 99));
                });

        // AT LEAST BACKWARD
        final TimedCase testLibraryAtLeastBackward = TimedCase.of(
                "Test library atLeastBackward",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(90000, 42);
                    testKeyRange(KeyRange.atLeastBackward(start),
                            9000043L,
                            createTestBuffer(90000, 42),
                            createTestBuffer(0, 0));
                });

        final TimedCase testStreamAtLeastBackward = TimedCase.of(
                "Test Stream atLeastBackward",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(90000, 42);
                    testStream((txn, dbi) ->
                                    LmdbStreamSupport.streamBuilder(txn, dbi)
                                            .start(start)
                                            .reverse(),
                            9000043L,
                            createTestBuffer(90000, 42),
                            createTestBuffer(0, 0));
                });

        // AT MOST
        final TimedCase testLibraryAtMost = TimedCase.of(
                "Test library atMost",
                (round, iterations) -> {
                    final ByteBuffer stop = createTestBuffer(90000, 42);
                    testKeyRange(KeyRange.atMost(stop),
                            9000043L,
                            createTestBuffer(0, 0),
                            createTestBuffer(90000, 42));
                });

        final TimedCase testStreamAtMost = TimedCase.of(
                "Test Stream atMost",
                (round, iterations) -> {
                    final ByteBuffer stop = createTestBuffer(90000, 42);
                    testStream((txn, dbi) ->
                                    LmdbStreamSupport.streamBuilder(txn, dbi)
                                            .stop(stop),
                            9000043L,
                            createTestBuffer(0, 0),
                            createTestBuffer(90000, 42));
                });

        // AT MOST BACKWARD
        final TimedCase testLibraryAtMostBackward = TimedCase.of(
                "Test library atMostBackward",
                (round, iterations) -> {
                    final ByteBuffer stop = createTestBuffer(42, 42);
                    testKeyRange(KeyRange.atMostBackward(stop),
                            9995758L,
                            createTestBuffer(99999, 99),
                            createTestBuffer(42, 42));
                });

        final TimedCase testStreamAtMostBackward = TimedCase.of(
                "Test Stream atMostBackward",
                (round, iterations) -> {
                    final ByteBuffer stop = createTestBuffer(42, 42);
                    testStream((txn, dbi) ->
                                    LmdbStreamSupport.streamBuilder(txn, dbi)
                                            .stop(stop)
                                            .reverse(),
                            9995758L,
                            createTestBuffer(99999, 99),
                            createTestBuffer(42, 42));
                });

        // FORWARD_CLOSED
        final TimedCase testLibraryClosed = TimedCase.of(
                "Test library closed",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(42, 42);
                    final ByteBuffer stop = createTestBuffer(90000, 42);
                    testKeyRange(KeyRange.closed(start, stop),
                            8995801L,
                            createTestBuffer(42, 42),
                            createTestBuffer(90000, 42));
                });

        final TimedCase testStreamClosed = TimedCase.of(
                "Test Stream closed",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(42, 42);
                    final ByteBuffer stop = createTestBuffer(90000, 42);
                    testStream((txn, dbi) ->
                                    LmdbStreamSupport.streamBuilder(txn, dbi)
                                            .start(start)
                                            .stop(stop),
                            8995801L,
                            createTestBuffer(42, 42),
                            createTestBuffer(90000, 42));
                });

        // BACKWARD_CLOSED
        final TimedCase testLibraryClosedBackward = TimedCase.of(
                "Test library closedBackward",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(90000, 42);
                    final ByteBuffer stop = createTestBuffer(42, 42);
                    testKeyRange(KeyRange.closedBackward(start, stop),
                            8995801L,
                            createTestBuffer(90000, 42),
                            createTestBuffer(42, 42));
                });

        final TimedCase testStreamClosedBackward = TimedCase.of(
                "Test Stream closedBackward",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(90000, 42);
                    final ByteBuffer stop = createTestBuffer(42, 42);
                    testStream((txn, dbi) ->
                                    LmdbStreamSupport.streamBuilder(txn, dbi)
                                            .start(start)
                                            .stop(stop)
                                            .reverse(),
                            8995801L,
                            createTestBuffer(90000, 42),
                            createTestBuffer(42, 42));
                });

        // FORWARD_CLOSED_OPEN
        final TimedCase testLibraryClosedOpen = TimedCase.of(
                "Test library closedOpen",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(42, 42);
                    final ByteBuffer stop = createTestBuffer(90000, 42);
                    testKeyRange(KeyRange.closedOpen(start, stop),
                            8995800L,
                            createTestBuffer(42, 42),
                            createTestBuffer(90000, 41));
                });

        final TimedCase testStreamClosedOpen = TimedCase.of(
                "Test Stream closedOpen",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(42, 42);
                    final ByteBuffer stop = createTestBuffer(90000, 42);
                    testStream((txn, dbi) ->
                                    LmdbStreamSupport.streamBuilder(txn, dbi)
                                            .start(start)
                                            .stop(stop, false),
                            8995800L,
                            createTestBuffer(42, 42),
                            createTestBuffer(90000, 41));
                });

        // BACKWARD_CLOSED_OPEN
        final TimedCase testLibraryClosedOpenBackward = TimedCase.of(
                "Test library closedOpenBackward",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(90000, 42);
                    final ByteBuffer stop = createTestBuffer(42, 42);
                    testKeyRange(KeyRange.closedOpenBackward(start, stop),
                            8995800L,
                            createTestBuffer(90000, 42),
                            createTestBuffer(42, 43));
                });

        final TimedCase testStreamClosedOpenBackward = TimedCase.of(
                "Test Stream closedOpenBackward",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(90000, 42);
                    final ByteBuffer stop = createTestBuffer(42, 42);
                    testStream((txn, dbi) ->
                                    LmdbStreamSupport.streamBuilder(txn, dbi)
                                            .start(start)
                                            .stop(stop, false)
                                            .reverse(),
                            8995800L,
                            createTestBuffer(90000, 42),
                            createTestBuffer(42, 43));
                });

        // FORWARD_GREATER_THAN
        final TimedCase testLibraryGreaterThan = TimedCase.of(
                "Test library greaterThan",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(42, 42);
                    testKeyRange(KeyRange.greaterThan(start),
                            9995757L,
                            createTestBuffer(42, 43),
                            createTestBuffer(99999, 99));
                });

        final TimedCase testStreamGreaterThan = TimedCase.of(
                "Test Stream greaterThan",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(42, 42);
                    testStream((txn, dbi) ->
                                    LmdbStreamSupport.streamBuilder(txn, dbi)
                                            .start(start, false),
                            9995757L,
                            createTestBuffer(42, 43),
                            createTestBuffer(99999, 99));
                });

        // BACKWARD_GREATER_THAN
        final TimedCase testLibraryGreaterThanBackward = TimedCase.of(
                "Test library greaterThanBackward",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(90000, 42);
                    testKeyRange(KeyRange.greaterThanBackward(start),
                            9000042L,
                            createTestBuffer(90000, 41),
                            createTestBuffer(0, 0));
                });

        final TimedCase testStreamGreaterThanBackward = TimedCase.of(
                "Test Stream greaterThanBackward",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(90000, 42);
                    testStream((txn, dbi) ->
                                    LmdbStreamSupport.streamBuilder(txn, dbi)
                                            .start(start, false)
                                            .reverse(),
                            9000042L,
                            createTestBuffer(90000, 41),
                            createTestBuffer(0, 0));
                });

        // FORWARD_LESS_THAN
        final TimedCase testLibraryLessThan = TimedCase.of(
                "Test library lessThan",
                (round, iterations) -> {
                    final ByteBuffer stop = createTestBuffer(90000, 42);
                    testKeyRange(KeyRange.lessThan(stop),
                            9000042L,
                            createTestBuffer(0, 0),
                            createTestBuffer(90000, 41));
                });

        final TimedCase testStreamLessThan = TimedCase.of(
                "Test Stream lessThan",
                (round, iterations) -> {
                    final ByteBuffer stop = createTestBuffer(90000, 42);
                    testStream((txn, dbi) ->
                                    LmdbStreamSupport.streamBuilder(txn, dbi)
                                            .stop(stop, false),
                            9000042L,
                            createTestBuffer(0, 0),
                            createTestBuffer(90000, 41));
                });

        // BACKWARD_LESS_THAN
        final TimedCase testLibraryLessThanBackward = TimedCase.of(
                "Test library lessThanBackward",
                (round, iterations) -> {
                    final ByteBuffer stop = createTestBuffer(42, 42);
                    testKeyRange(KeyRange.lessThanBackward(stop),
                            9995757L,
                            createTestBuffer(99999, 99),
                            createTestBuffer(42, 43));
                });

        final TimedCase testStreamLessThanBackward = TimedCase.of(
                "Test Stream lessThanBackward",
                (round, iterations) -> {
                    final ByteBuffer stop = createTestBuffer(42, 42);
                    testStream((txn, dbi) ->
                                    LmdbStreamSupport.streamBuilder(txn, dbi)
                                            .stop(stop, false)
                                            .reverse(),
                            9995757L,
                            createTestBuffer(99999, 99),
                            createTestBuffer(42, 43));
                });

        // FORWARD_OPEN
        final TimedCase testLibraryOpen = TimedCase.of(
                "Test library open",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(42, 42);
                    final ByteBuffer stop = createTestBuffer(90000, 42);
                    testKeyRange(KeyRange.open(start, stop),
                            8995799L,
                            createTestBuffer(42, 43),
                            createTestBuffer(90000, 41));
                });

        final TimedCase testStreamOpen = TimedCase.of(
                "Test Stream open",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(42, 42);
                    final ByteBuffer stop = createTestBuffer(90000, 42);
                    testStream((txn, dbi) ->
                                    LmdbStreamSupport.streamBuilder(txn, dbi)
                                            .start(start, false)
                                            .stop(stop, false),
                            8995799L,
                            createTestBuffer(42, 43),
                            createTestBuffer(90000, 41));
                });

        // BACKWARD_OPEN
        final TimedCase testLibraryOpenBackward = TimedCase.of(
                "Test library openBackward",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(90000, 42);
                    final ByteBuffer stop = createTestBuffer(42, 42);
                    testKeyRange(KeyRange.openBackward(start, stop),
                            8995799L,
                            createTestBuffer(90000, 41),
                            createTestBuffer(42, 43));
                });

        final TimedCase testStreamOpenBackward = TimedCase.of(
                "Test Stream openBackward",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(90000, 42);
                    final ByteBuffer stop = createTestBuffer(42, 42);
                    testStream((txn, dbi) ->
                                    LmdbStreamSupport.streamBuilder(txn, dbi)
                                            .start(start, false)
                                            .stop(stop, false)
                                            .reverse(),
                            8995799L,
                            createTestBuffer(90000, 41),
                            createTestBuffer(42, 43));
                });

        // FORWARD_OPEN_CLOSED
        final TimedCase testLibraryOpenClosed = TimedCase.of(
                "Test library openClosed",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(42, 42);
                    final ByteBuffer stop = createTestBuffer(90000, 42);
                    testKeyRange(KeyRange.openClosed(start, stop),
                            8995800L,
                            createTestBuffer(42, 43),
                            createTestBuffer(90000, 42));
                });

        final TimedCase testStreamOpenClosed = TimedCase.of(
                "Test Stream openClosed",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(42, 42);
                    final ByteBuffer stop = createTestBuffer(90000, 42);
                    testStream((txn, dbi) ->
                                    LmdbStreamSupport.streamBuilder(txn, dbi)
                                            .start(start, false)
                                            .stop(stop),
                            8995800L,
                            createTestBuffer(42, 43),
                            createTestBuffer(90000, 42));
                });

        // BACKWARD_OPEN_CLOSED
        final TimedCase testLibraryOpenClosedBackward = TimedCase.of(
                "Test library openClosedBackward",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(90000, 42);
                    final ByteBuffer stop = createTestBuffer(42, 42);
                    testKeyRange(KeyRange.openClosedBackward(start, stop),
                            8995800L,
                            createTestBuffer(90000, 41),
                            createTestBuffer(42, 42));
                });

        final TimedCase testStreamOpenClosedBackward = TimedCase.of(
                "Test Stream openClosedBackward",
                (round, iterations) -> {
                    final ByteBuffer start = createTestBuffer(90000, 42);
                    final ByteBuffer stop = createTestBuffer(42, 42);
                    testStream((txn, dbi) ->
                                    LmdbStreamSupport.streamBuilder(txn, dbi)
                                            .start(start, false)
                                            .stop(stop)
                                            .reverse(),
                            8995800L,
                            createTestBuffer(90000, 41),
                            createTestBuffer(42, 42));
                });

        // PREFIX
        final TimedCase testStreamPrefix = TimedCase.of(
                "Test Stream prefix",
                (round, iterations) -> {
                    final ByteBuffer prefix = createTestBuffer(90000);
                    testStream((txn, dbi) ->
                                    LmdbStreamSupport.streamBuilder(txn, dbi)
                                            .prefix(prefix),
                            100,
                            createTestBuffer(90000, 0),
                            createTestBuffer(90000, 99));
                });

        final TimedCase testStreamPrefixReversed = TimedCase.of(
                "Test Stream prefix reversed",
                (round, iterations) -> {
                    final ByteBuffer prefix = createTestBuffer(90000);
                    testStream((txn, dbi) ->
                                    LmdbStreamSupport.streamBuilder(txn, dbi)
                                            .prefix(prefix)
                                            .reverse(),
                            100,
                            createTestBuffer(90000, 99),
                            createTestBuffer(90000, 0));
                });

        TestUtil.comparePerformance(
                3,
                10000000,
                LOGGER::info,
                testUtilityIterator,
                testLibraryIterator,
                testLibraryAll,
                testStreamAll,
                testLibraryAllBackward,
                testStreamAllBackward,
                testLibraryAtLeast,
                testStreamAtLeast,
                testLibraryAtLeastBackward,
                testStreamAtLeastBackward,
                testLibraryAtMost,
                testStreamAtMost,
                testLibraryAtMostBackward,
                testStreamAtMostBackward,
                testLibraryClosed,
                testStreamClosed,
                testLibraryClosedBackward,
                testStreamClosedBackward,
                testLibraryClosedOpen,
                testStreamClosedOpen,
                testLibraryClosedOpenBackward,
                testStreamClosedOpenBackward,
                testLibraryGreaterThan,
                testStreamGreaterThan,
                testLibraryGreaterThanBackward,
                testStreamGreaterThanBackward,
                testLibraryLessThan,
                testStreamLessThan,
                testLibraryLessThanBackward,
                testStreamLessThanBackward,
                testLibraryOpen,
                testStreamOpen,
                testLibraryOpenBackward,
                testStreamOpenBackward,
                testLibraryOpenClosed,
                testStreamOpenClosed,
                testLibraryOpenClosedBackward,
                testStreamOpenClosedBackward,
                testStreamPrefix,
                testStreamPrefixReversed
        );
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

    private void testStream(final BiFunction<Txn<ByteBuffer>, Dbi<ByteBuffer>, AbstractStreamBuilder<?>> function,
                            final long expectedCount,
                            final ByteBuffer expectedFirst,
                            final ByteBuffer expectedLast) {
        SoftAssertions.assertSoftly(softAssertions -> {
            final AtomicLong total = new AtomicLong();
            try (final Txn<ByteBuffer> txn = env.txnRead()) {
                try (final Stream<LmdbEntry> stream = function.apply(txn, dbi).create()) {
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
