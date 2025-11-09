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

package stroom.bytebuffer;

import stroom.test.common.TestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

class TestByteBufferUtils {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestByteBufferUtils.class);

    @Test
    void testIntCompare() {

        final ByteBuffer buf1 = ByteBuffer.allocate(Integer.BYTES);
        final ByteBuffer buf2 = ByteBuffer.allocate(Integer.BYTES);

        doIntCompareTest(0, 0, buf1, buf2);
        doIntCompareTest(0, 1, buf1, buf2);
        doIntCompareTest(-1, 0, buf1, buf2);
        doIntCompareTest(-1, 1, buf1, buf2);
        doIntCompareTest(-1000, 1000, buf1, buf2);
        doIntCompareTest(Integer.MAX_VALUE, Integer.MAX_VALUE, buf1, buf2);
        doIntCompareTest(Integer.MIN_VALUE, Integer.MIN_VALUE, buf1, buf2);
        doIntCompareTest(Integer.MIN_VALUE, Integer.MAX_VALUE, buf1, buf2);
        doIntCompareTest(Integer.MAX_VALUE, Integer.MIN_VALUE, buf1, buf2);

        // now just run the test with a load of random values
        final Random random = new Random();
        for (int i = 0; i < 10000; i++) {
            final int val1 = random.nextInt();
            final int val2 = random.nextInt();
            doIntCompareTest(val1, val2, buf1, buf2);
        }
    }

    @Test
    void testLongCompare() {

        final ByteBuffer buf1 = ByteBuffer.allocate(Long.BYTES);
        final ByteBuffer buf2 = ByteBuffer.allocate(Long.BYTES);
        doLongCompareTest(0L, 0L, buf1, buf2);
        doLongCompareTest(0L, 1L, buf1, buf2);
        doLongCompareTest(-1L, 0L, buf1, buf2);
        doLongCompareTest(-1L, 1L, buf1, buf2);
        doLongCompareTest(-1000L, 1000L, buf1, buf2);
        doLongCompareTest(Long.MAX_VALUE, Long.MAX_VALUE, buf1, buf2);
        doLongCompareTest(Long.MIN_VALUE, Long.MIN_VALUE, buf1, buf2);
        doLongCompareTest(Long.MIN_VALUE, Long.MAX_VALUE, buf1, buf2);
        doLongCompareTest(Long.MAX_VALUE, Long.MIN_VALUE, buf1, buf2);

        // now just run the test with a load of random values
        final Random random = new Random();
        for (int i = 0; i < 10000; i++) {
            final long val1 = random.nextLong();
            final long val2 = random.nextLong();
            doLongCompareTest(val1, val2, buf1, buf2);
        }
    }

    @Test
    void testContainsPrefix_match() {

        final ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[]{0, 1, 2, 3, 4, 5});
        final ByteBuffer prefixByteBuffer = ByteBuffer.wrap(new byte[]{0, 1, 2, 3, 4});

        final boolean result = ByteBufferUtils.containsPrefix(byteBuffer, prefixByteBuffer);

        assertThat(result).isTrue();
    }

    @Test
    void testContainsPrefix_match2() {

        final ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[]{0, 1, 2, 3, 4, 5});
        final ByteBuffer prefixByteBuffer = ByteBuffer.wrap(new byte[]{0});

        final boolean result = ByteBufferUtils.containsPrefix(byteBuffer, prefixByteBuffer);

        assertThat(result).isTrue();
    }

    @Test
    void testContainsPrefix_bufferTooShort() {

        final ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[]{0, 1, 2});
        final ByteBuffer prefixByteBuffer = ByteBuffer.wrap(new byte[]{0, 1, 2, 3, 4});

        final boolean result = ByteBufferUtils.containsPrefix(byteBuffer, prefixByteBuffer);

        assertThat(result).isFalse();
    }

    @Test
    void testContainsPrefix_noMatch() {

        final ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[]{0, 1, 2, 3, 4, 5});
        final ByteBuffer prefixByteBuffer = ByteBuffer.wrap(new byte[]{1, 2, 3, 4});

        final boolean result = ByteBufferUtils.containsPrefix(byteBuffer, prefixByteBuffer);

        assertThat(result).isFalse();
    }

    @Test
    void testContainsPrefix_exactMatch() {

        final ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[]{0, 1, 2, 3});
        final ByteBuffer prefixByteBuffer = ByteBuffer.wrap(new byte[]{0, 1, 2, 3});

        final boolean result = ByteBufferUtils.containsPrefix(byteBuffer, prefixByteBuffer);

        assertThat(result).isTrue();
    }

    private void doLongCompareTest(final long val1, final long val2, final ByteBuffer buf1, final ByteBuffer buf2) {
        buf1.clear();
        buf1.putLong(val1);
        buf1.flip();
        buf2.clear();
        buf2.putLong(val2);
        buf2.flip();

        final int cmpAsLongResult = Long.compare(val1, val2);
        final int cmpAsBufsResult = ByteBufferUtils.compareAsLong(buf1, buf2);
        final int cmpAsLongAndBufsResult = ByteBufferUtils.compareAsLong(val1, buf2);
        LOGGER.trace("Comparing {} [{}] to {} [{}], {} {} {}",
                val1, ByteBufferUtils.byteBufferToHex(buf1),
                val2, ByteBufferUtils.byteBufferToHex(buf2),
                cmpAsLongResult, cmpAsBufsResult, cmpAsLongAndBufsResult);

        // ensure comparison of the long value is the same (pos, neg or zero) as our func
        if ((cmpAsLongResult == cmpAsBufsResult && cmpAsLongResult == cmpAsLongAndBufsResult) ||
            (cmpAsLongResult < 0 && cmpAsBufsResult < 0 && cmpAsLongAndBufsResult < 0) ||
            (cmpAsLongResult > 0 && cmpAsBufsResult > 0 && cmpAsLongAndBufsResult > 0)) {
            // comparison is the same
        } else {
            LOGGER.error("Comparing {} [{}] to {} [{}], {} {} {}",
                    val1, ByteBufferUtils.byteBufferToHex(buf1),
                    val2, ByteBufferUtils.byteBufferToHex(buf2),
                    cmpAsLongResult, cmpAsBufsResult, cmpAsLongAndBufsResult);

            fail("Mismatch on %s [%s] to %s [%s]",
                    val1, ByteBufferUtils.byteBufferToHex(buf1), val2, ByteBufferUtils.byteBufferToHex(buf2));
        }
    }

    private void doIntCompareTest(final int val1, final int val2, final ByteBuffer buf1, final ByteBuffer buf2) {
        buf1.clear();
        buf1.putInt(val1);
        buf1.flip();
        buf2.clear();
        buf2.putInt(val2);
        buf2.flip();

        final int cmpLong = Integer.compare(val1, val2);
        final int cmpBuf = ByteBufferUtils.compareAsInt(buf1, buf2);
        LOGGER.trace("Comparing {} [{}] to {} [{}], {} {}",
                val1, ByteBufferUtils.byteBufferToHex(buf1),
                val2, ByteBufferUtils.byteBufferToHex(buf2),
                cmpLong, cmpBuf);

        // ensure comparison of the int value is the same (pos, neg or zero) as our func
        if (cmpLong == cmpBuf ||
            cmpLong < 0 && cmpBuf < 0 ||
            cmpLong > 0 && cmpBuf > 0) {
            // comparison is the same
        } else {
            fail("Mismatch on %s [%s] to %s [%s]",
                    val1, ByteBufferUtils.byteBufferToHex(buf1), val2, ByteBufferUtils.byteBufferToHex(buf2));
        }
    }

    @Test
    void testBasicHashCode() {

        final ByteBuffer byteBuffer1 = ByteBuffer.wrap(new byte[]{0, 0, 1, 2, 3, 4, 5, 0, 0});
        byteBuffer1.position(2);
        byteBuffer1.limit(7);
        LOGGER.info(ByteBufferUtils.byteBufferInfo(byteBuffer1));

        final ByteBuffer byteBuffer2 = ByteBuffer.wrap(new byte[]{0, 1, 2, 3, 4, 5, 0, 0});
        byteBuffer2.position(1);
        byteBuffer2.limit(6);
        LOGGER.info(ByteBufferUtils.byteBufferInfo(byteBuffer2));

        final int hash1 = ByteBufferUtils.basicHashCode(byteBuffer1);
        final int hash2 = ByteBufferUtils.basicHashCode(byteBuffer2);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void testXxHash() {

        final ByteBuffer byteBuffer1 = ByteBuffer.wrap(new byte[]{0, 0, 1, 2, 3, 4, 5, 0, 0});
        byteBuffer1.position(2);
        byteBuffer1.limit(7);
        LOGGER.info(ByteBufferUtils.byteBufferInfo(byteBuffer1));

        final ByteBuffer byteBuffer2 = ByteBuffer.wrap(new byte[]{0, 1, 2, 3, 4, 5, 0, 0});
        byteBuffer2.position(1);
        byteBuffer2.limit(6);
        LOGGER.info(ByteBufferUtils.byteBufferInfo(byteBuffer2));

        final long hash1 = ByteBufferUtils.xxHash(byteBuffer1);
        final long hash2 = ByteBufferUtils.xxHash(byteBuffer2);

        assertThat(hash1).isEqualTo(hash2);
    }

    @TestFactory
    Stream<DynamicTest> testIncrementShort() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(short.class)
                .withTestFunction(testCase -> {
                    final ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[20]);
                    final int idx = 5;
                    byteBuffer.putShort(idx, testCase.getInput());
                    LOGGER.debug("byteBuffer before: {}", ByteBufferUtils.byteBufferToHex(byteBuffer));
                    ByteBufferUtils.incrementShort(byteBuffer, idx);
                    LOGGER.debug("byteBuffer after: {}", ByteBufferUtils.byteBufferToHex(byteBuffer));
                    return byteBuffer.getShort(idx);
                })
                .withSimpleEqualityAssertion()
                .addCase((short) 0, (short) 1)
                .addCase((short) 1, (short) 2)
                .addCase((short) -2, (short) -1)
                .addCase((short) 1_000, (short) 1_001)
                .addCase((short) (Short.MAX_VALUE - (short) 1), Short.MAX_VALUE)
                .addThrowsCase(Short.MAX_VALUE, ArithmeticException.class)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testIncrementInt() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(int.class)
                .withTestFunction(testCase -> {
                    final ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[20]);
                    final int idx = 5;
                    byteBuffer.putInt(idx, testCase.getInput());
                    LOGGER.debug("byteBuffer before: {}", ByteBufferUtils.byteBufferToHex(byteBuffer));
                    ByteBufferUtils.incrementInt(byteBuffer, idx);
                    LOGGER.debug("byteBuffer after: {}", ByteBufferUtils.byteBufferToHex(byteBuffer));
                    return byteBuffer.getInt(idx);
                })
                .withSimpleEqualityAssertion()
                .addCase(0, 1)
                .addCase(-2, -1)
                .addCase(1_000, 1_001)
                .addCase(Integer.MAX_VALUE - 1, Integer.MAX_VALUE)
                .addThrowsCase(Integer.MAX_VALUE, ArithmeticException.class)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testIncrementLong() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(long.class)
                .withTestFunction(testCase -> {
                    final ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[20]);
                    final int idx = 5;
                    byteBuffer.putLong(idx, testCase.getInput());
                    LOGGER.debug("byteBuffer before: {}", ByteBufferUtils.byteBufferToHex(byteBuffer));
                    ByteBufferUtils.incrementLong(byteBuffer, idx);
                    LOGGER.debug("byteBuffer after: {}", ByteBufferUtils.byteBufferToHex(byteBuffer));
                    return byteBuffer.getLong(idx);
                })
                .withSimpleEqualityAssertion()
                .addCase(0L, 1L)
                .addCase(-1L, 0L)
                .addCase(-2L, -1L)
                .addCase(1_000L, 1_001L)
                .addCase(Long.MAX_VALUE - 1, Long.MAX_VALUE)
                .addThrowsCase(Long.MAX_VALUE, ArithmeticException.class)
                .build();
    }

    @Test
    void testIncrementInteger_perf() throws NoSuchFieldException, IllegalAccessException {
        for (int j = 0; j < 3; j++) {
            LOGGER.info("Round: " + j);

            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(20);
            final int idx = 5;
            final int iterations = 500000;
            final int maxVal = 100;

            LOGGER.logDurationIfInfoEnabled(() -> {
                for (int k = 0; k < iterations; k++) {
                    byteBuffer.putInt(idx, 0);
                    byteBuffer.clear();
                    for (int i = 0; i < maxVal; i++) {
                        ByteBufferUtils.incrementInt(byteBuffer, idx);
                    }
                }
            }, "incrementInt");

            assertThat(byteBuffer.getInt(idx))
                    .isEqualTo(maxVal);

            LOGGER.logDurationIfInfoEnabled(() -> {
                for (int k = 0; k < iterations; k++) {
                    byteBuffer.putInt(idx, 0);
                    byteBuffer.clear();
                    for (int i = 0; i < maxVal; i++) {
                        byteBuffer.putInt(idx, byteBuffer.getInt(idx) + 1);
                    }
                }
            }, "get/put");

            assertThat(byteBuffer.getInt(idx))
                    .isEqualTo(maxVal);
        }
    }

    @Test
    void testIncrement_bufferTooShort() {
        assertThatThrownBy(
                () -> {
                    final ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[20]);
                    ByteBufferUtils.increment(byteBuffer, 18, Long.BYTES);
                })
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Disabled // manual perf testing only
    @Test
    void testHashPerformance() throws IOException {

        // Warm up the jvm and get all files in the page cache
        for (int i = 0; i < 5; i++) {
            doHashTest("basic", ByteBufferUtils::basicHashCode);

            doHashTest("xxHash", ByteBufferUtils::xxHash);

            LOGGER.info("==========================================");
        }
    }

    @Disabled // manual perf testing only
    @Test
    void testCopyPerformance() {

        final int rounds = 2;
        final int iterations = 10_000;
        final int bufferSize = 1_000;

        final ByteBuffer src = ByteBuffer.allocateDirect(bufferSize);
        final ByteBuffer dest = ByteBuffer.allocateDirect(bufferSize);

        final Random random = new Random();

        final byte[] randBytes = new byte[bufferSize];
        random.nextBytes(randBytes);
        src.put(randBytes);
        src.flip();

        final Map<String, BiConsumer<ByteBuffer, ByteBuffer>> funcMap = Map.of(
                "Simple", this::doSimpleCopyTest,
                "Hadoop", this::doHadoopCopyTest);

        for (int j = 0; j < rounds; j++) {
            final int round = j;
            funcMap.forEach((name, func) -> {
                final Instant startTime = Instant.now();

                for (int i = 0; i < iterations; i++) {

                    func.accept(src, dest);

                    assertThat(dest)
                            .isEqualByComparingTo(src);

                    src.clear();
                    random.nextBytes(randBytes);
                    src.put(randBytes);
                    src.flip();

                    dest.clear();
                }
                LOGGER.info("Round {}, {} duration: {}",
                        round, name, Duration.between(startTime, Instant.now()));
            });
        }
    }

    private void doHadoopCopyTest(final ByteBuffer src, final ByteBuffer dest) {
        stroom.bytebuffer.hbase.ByteBufferUtils.copyFromBufferToBuffer(src, dest);
    }

    private void doSimpleCopyTest(final ByteBuffer src, final ByteBuffer dest) {
        dest.put(src);
    }

    private void doHashTest(final String name,
                            final Function<ByteBuffer, Number> hashFunc) throws IOException {
        final int iterations = 1_000;

        final Path start = Paths.get(".")
                .resolve("src")
                .toAbsolutePath()
                .normalize();
        System.out.println(start);

        final AtomicInteger fileCount = new AtomicInteger(0);
        final List<Path> paths = new ArrayList<>();

        try (final Stream<Path> stream = Files.walk(start, Integer.MAX_VALUE)) {
            stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(paths::add);
        }

        Instant startTime = Instant.now();

        for (int i = 0; i < iterations; i++) {
            paths.forEach(path -> {
                try {
                    try (final RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {

                        //Get file channel in read-only mode
                        final FileChannel fileChannel = file.getChannel();

                        //Get direct byte buffer access using channel.map() operation
                        final MappedByteBuffer buffer = fileChannel.map(
                                FileChannel.MapMode.READ_ONLY,
                                0,
                                fileChannel.size());

                        final Number hash = hashFunc.apply(buffer);
//                                LOGGER.info("  {} {}", path.toString(), hash);
                        fileCount.incrementAndGet();
                    }
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        LOGGER.info("{} File contents test - count: {}, in {}",
                name, fileCount.get(), Duration.between(startTime, Instant.now()));


        final ByteBuffer byteBuffer = ByteBuffer.allocate(300);

        startTime = Instant.now();

        for (int i = 0; i < iterations; i++) {
            paths.forEach(path -> {
                byteBuffer.clear();
                StandardCharsets.UTF_8.newEncoder()
                        .encode(CharBuffer.wrap(path.toString()), byteBuffer, true);

                final Number hash = hashFunc.apply(byteBuffer);
            });
        }

        LOGGER.info("{} File name test - count: {}, in {}",
                name, fileCount.get(), Duration.between(startTime, Instant.now()));
    }
}
