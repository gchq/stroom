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

@Disabled
public class TestLmdbStreamBenchmark {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestLmdbStreamBenchmark.class);

    private Path dbDir = null;
    private Env<ByteBuffer> env;
    private Dbi<ByteBuffer> dbi;

    private final int min1 = 0;
    private final int min2 = 0;
    private final int max1 = 99999;
    private final int max2 = 99;
    private final int low1 = 42;
    private final int low2 = 42;
    private final int high1 = 90000;
    private final int high2 = 42;
    private final int total1 = max1 - min1 + 1;
    private final int total2 = max2 - min2 + 1;
    private final long totalRows = total1 * total2;

    private final Point minPoint = new Point(min1, min2);
    private final Point maxPoint = new Point(max1, max2);
    private final Point lowPoint = new Point(low1, low2);
    private final Point highPoint = new Point(high1, high2);

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
            for (int i = min1; i <= max1; i++) {
                for (int j = min2; j <= max2; j++) {
                    key.putInt(i);
                    key.putInt(j);
                    key.flip();
                    dbi.put(txn, key, value);
                }
            }
            txn.commit();
        }

        final TimedCase testForwardRange = makeCase(
                "Test forward range",
                () -> {
                    final ByteBuffer start = createTestBuffer(low1, low2);
                    final ByteBuffer stop = createTestBuffer(high1, low2);
                    testStream(LmdbKeyRange.builder()
                                    .start(start)
                                    .stop(stop)
                                    .build(),
                            89995801L,
                            createTestBuffer(low1, low2),
                            createTestBuffer(high1, low2));
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

        final List<TimedCase> cases = new ArrayList<>();
        cases.add(makeCase("Test utility iterator", () -> {
            final AtomicInteger count = new AtomicInteger();
            try (final Txn<ByteBuffer> txn = env.txnRead()) {
                LmdbIterable.iterate(txn, dbi, (key, val) -> count.incrementAndGet());
            }
            assertThat(count.get()).isEqualTo(totalRows);
        }));

        cases.add(makeCase(
                "Test library iterator",
                () -> {
                    long count = 0;
                    try (final Txn<ByteBuffer> txn = env.txnRead()) {
                        for (final KeyVal<ByteBuffer> ignored : dbi.iterate(txn)) {
                            count++;
                        }
                    }
                    assertThat(count).isEqualTo(totalRows);
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
                KeyRange.atLeast(lowPoint.toBuffer()),
                LmdbKeyRange.builder().start(lowPoint.toBuffer()).build(),
                diff(lowPoint, maxPoint) + 1,
                lowPoint,
                maxPoint);

        // AT LEAST BACKWARD
        addTestSet(cases,
                "atLeastBackward",
                KeyRange.atLeastBackward(highPoint.toBuffer()),
                LmdbKeyRange.builder().start(highPoint.toBuffer()).reverse().build(),
                diff(minPoint, highPoint) + 1,
                highPoint,
                minPoint);

        // AT MOST
        addTestSet(cases,
                "atMost",
                KeyRange.atMost(highPoint.toBuffer()),
                LmdbKeyRange.builder().stop(highPoint.toBuffer()).build(),
                diff(minPoint, highPoint) + 1,
                minPoint,
                highPoint);

        // AT MOST BACKWARD
        addTestSet(cases,
                "atMostBackward",
                KeyRange.atMostBackward(lowPoint.toBuffer()),
                LmdbKeyRange.builder().stop(lowPoint.toBuffer()).reverse().build(),
                diff(lowPoint, maxPoint) + 1,
                maxPoint,
                lowPoint);

        // FORWARD_CLOSED
        addTestSet(cases,
                "closed",
                KeyRange.closed(lowPoint.toBuffer(), highPoint.toBuffer()),
                LmdbKeyRange.builder().start(lowPoint.toBuffer()).stop(highPoint.toBuffer()).build(),
                diff(lowPoint, highPoint) + 1,
                lowPoint,
                highPoint);

        // BACKWARD_CLOSED
        addTestSet(cases,
                "closedBackward",
                KeyRange.closedBackward(highPoint.toBuffer(), lowPoint.toBuffer()),
                LmdbKeyRange.builder().start(highPoint.toBuffer()).stop(lowPoint.toBuffer()).reverse().build(),
                diff(lowPoint, highPoint) + 1,
                highPoint,
                lowPoint);

        // FORWARD_CLOSED_OPEN
        addTestSet(cases,
                "closedOpen",
                KeyRange.closedOpen(lowPoint.toBuffer(), highPoint.toBuffer()),
                LmdbKeyRange.builder().start(lowPoint.toBuffer()).stop(highPoint.toBuffer(), false).build(),
                diff(lowPoint, highPoint),
                lowPoint,
                new Point(high1, low2 - 1));

        // BACKWARD_CLOSED_OPEN
        addTestSet(cases,
                "closedOpenBackward",
                KeyRange.closedOpenBackward(highPoint.toBuffer(), lowPoint.toBuffer()),
                LmdbKeyRange.builder()
                        .start(highPoint.toBuffer())
                        .stop(lowPoint.toBuffer(), false)
                        .reverse().build(),
                diff(lowPoint, highPoint),
                highPoint,
                new Point(low1, low2 + 1));

        // FORWARD_GREATER_THAN
        addTestSet(cases,
                "greaterThan",
                KeyRange.greaterThan(lowPoint.toBuffer()),
                LmdbKeyRange.builder().start(lowPoint.toBuffer(), false).build(),
                diff(lowPoint, maxPoint),
                new Point(low1, low2 + 1),
                maxPoint);

        // BACKWARD_GREATER_THAN
        addTestSet(cases,
                "greaterThanBackward",
                KeyRange.greaterThanBackward(highPoint.toBuffer()),
                LmdbKeyRange.builder().start(highPoint.toBuffer(), false).reverse().build(),
                diff(minPoint, highPoint),
                new Point(high1, low2 - 1),
                minPoint);

        // FORWARD_LESS_THAN
        addTestSet(cases,
                "lessThan",
                KeyRange.lessThan(highPoint.toBuffer()),
                LmdbKeyRange.builder().stop(highPoint.toBuffer(), false).build(),
                diff(minPoint, highPoint),
                minPoint,
                new Point(high1, low2 - 1));

        // BACKWARD_LESS_THAN
        addTestSet(cases,
                "lessThanBackward",
                KeyRange.lessThanBackward(lowPoint.toBuffer()),
                LmdbKeyRange.builder().stop(lowPoint.toBuffer(), false).reverse().build(),
                diff(lowPoint, maxPoint),
                maxPoint,
                new Point(low1, low2 + 1));

        // FORWARD_OPEN
        addTestSet(cases,
                "open",
                KeyRange.open(lowPoint.toBuffer(), highPoint.toBuffer()),
                LmdbKeyRange.builder()
                        .start(lowPoint.toBuffer(), false)
                        .stop(highPoint.toBuffer(), false)
                        .build(),
                diff(lowPoint, highPoint) - 1,
                new Point(low1, low2 + 1),
                new Point(high1, low2 - 1));

        // BACKWARD_OPEN
        addTestSet(cases,
                "openBackward",
                KeyRange.openBackward(highPoint.toBuffer(), lowPoint.toBuffer()),
                LmdbKeyRange.builder()
                        .start(highPoint.toBuffer(), false)
                        .stop(lowPoint.toBuffer(), false)
                        .reverse()
                        .build(),
                diff(lowPoint, highPoint) - 1,
                new Point(high1, low2 - 1),
                new Point(low1, low2 + 1));

        // FORWARD_OPEN_CLOSED
        addTestSet(cases,
                "openClosed",
                KeyRange.openClosed(lowPoint.toBuffer(), highPoint.toBuffer()),
                LmdbKeyRange.builder()
                        .start(lowPoint.toBuffer(), false)
                        .stop(highPoint.toBuffer())
                        .build(),
                diff(lowPoint, highPoint),
                new Point(low1, low2 + 1),
                highPoint);

        // BACKWARD_OPEN_CLOSED
        addTestSet(cases,
                "openClosedBackward",
                KeyRange.openClosedBackward(highPoint.toBuffer(), lowPoint.toBuffer()),
                LmdbKeyRange.builder()
                        .start(highPoint.toBuffer(), false)
                        .stop(lowPoint.toBuffer())
                        .reverse()
                        .build(),
                diff(lowPoint, highPoint),
                new Point(high1, low2 - 1),
                lowPoint);

        // PREFIX
        cases.add(makeCase(
                "Test Stream prefix",
                () -> {
                    final ByteBuffer prefix = createTestBuffer(high1);
                    testStream(LmdbKeyRange.builder().prefix(prefix).build(),
                            (max2 + 1) - min2,
                            createTestBuffer(high1, min2),
                            createTestBuffer(high1, max2));
                }));

        cases.add(makeCase(
                "Test Stream prefix reversed",
                () -> {
                    final ByteBuffer prefix = createTestBuffer(high1);
                    testStream(LmdbKeyRange.builder().prefix(prefix).reverse().build(),
                            (max2 + 1) - min2,
                            createTestBuffer(high1, max2),
                            createTestBuffer(high1, min2));
                }));

        TestUtil.comparePerformance(
                3,
                5,
                LOGGER::info,
                cases.toArray(new TimedCase[0]));
    }

    private void addTestSet(final List<TimedCase> cases,
                            final String name,
                            final KeyRange<ByteBuffer> keyRange,
                            final LmdbKeyRange lmdbKeyRange,
                            final long expectedCount,
                            final Point expectedFirst,
                            final Point expectedLast) {
        final ByteBuffer first = expectedFirst.toBuffer();
        final ByteBuffer last = expectedLast.toBuffer();

        cases.add(makeCase(
                "Test library " + name,
                () -> testKeyRange(keyRange, expectedCount, first, last)));

        cases.add(makeCase(
                "Test New Stream " + name,
                () -> testStream(lmdbKeyRange, expectedCount, first, last)));

        cases.add(makeCase(
                "Test New Iterator " + name,
                () -> testIterator(lmdbKeyRange, expectedCount, first, last)));
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
            for (int i = min1; i <= max1; i++) {
                for (int j = min2; j <= max2; j++) {
                    key.putInt(i);
                    key.putInt(j);
                    key.flip();
                    dbi.put(txn, key, value);
                }
            }
            txn.commit();
        }
    }

    private static ByteBuffer createTestBuffer(final int i1) {
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(Integer.BYTES);
        byteBuffer.putInt(i1);
        byteBuffer.flip();
        return byteBuffer;
    }

    private static ByteBuffer createTestBuffer(final int i1, final int i2) {
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(Integer.BYTES + Integer.BYTES);
        byteBuffer.putInt(i1);
        byteBuffer.putInt(i2);
        byteBuffer.flip();
        return byteBuffer;
    }

    private long diff(final Point a, final Point b) {
        final long diffX = b.x > a.x
                ? b.x - a.x
                : a.x - b.x;
        final long diffY = b.y > a.y
                ? b.y - a.y
                : a.y - b.y;
        return (diffX * total2) + diffY;
    }

    private record Point(int x, int y) {

        ByteBuffer toBuffer() {
            return createTestBuffer(x, y);
        }
    }

    private TimedCase makeCase(final String name, final Runnable runnable) {
        return TimedCase.of(name, (round, iterations) -> {
            for (int i = 0; i < iterations; i++) {
                runnable.run();
            }
        });
    }
}
