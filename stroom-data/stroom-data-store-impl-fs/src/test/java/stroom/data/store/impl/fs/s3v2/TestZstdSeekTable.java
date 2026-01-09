/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.data.store.impl.fs.s3v2;

import stroom.bytebuffer.ByteBufferPoolConfig;
import stroom.data.store.api.SegmentOutputStream;
import stroom.data.store.impl.fs.s3v2.ZstdSeekTable.InsufficientSeekTableDataException;
import stroom.test.common.TestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import com.github.luben.zstd.ZstdInputStream;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.ints.IntSortedSets;
import net.datafaker.Faker;
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.Assertions;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class TestZstdSeekTable {

    public static final int ITERATIONS = 10;
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestZstdSeekTable.class);

    private static final int COMPRESSION_LEVEL = 7;
    private static final long RANDOM_SEED = 57294857573L;

    private List<byte[]> dataBytes = null;
    private List<String> data = null;

    @Test
    void test() throws IOException {
        final byte[] compressedBytes = createCompressedData();
        final ZstdSeekTable zstdSeekTable = ZstdSeekTable.parse(ByteBuffer.wrap(compressedBytes))
                .orElseThrow();

        assertThat(zstdSeekTable.getFrameCount())
                .isEqualTo(ITERATIONS);
        assertThat(zstdSeekTable.isEmpty())
                .isFalse();

        Assertions.assertThatThrownBy(() -> zstdSeekTable.getFrameLocation(-1))
                .isInstanceOf(RuntimeException.class);
        Assertions.assertThatThrownBy(() -> zstdSeekTable.getFrameLocation(ITERATIONS + 1))
                .isInstanceOf(RuntimeException.class);

        long compressedTotal = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            final FrameLocation frameLocation = zstdSeekTable.getFrameLocation(i);
            LOGGER.debug("frameLocation: {}", frameLocation);

            assertThat(frameLocation)
                    .isNotNull();
            assertThat(frameLocation.isEmptyFrame())
                    .isFalse();

            compressedTotal += frameLocation.compressedSize();
//            final ByteBuffer frameBuffer = ByteBuffer.wrap(
//                    compressedBytes,
//                    (int) frameLocation.position(),
//                    (int) frameLocation.compressedSize());

            // They have to be direct buffers for decompress
//            final ByteBuffer srcBuffer = ByteBuffer.allocateDirect((int) frameLocation.compressedSize());
//            ByteBufferUtils.copy(frameBuffer, srcBuffer);
//            final ByteBuffer decompressedBuffer = ByteBuffer.allocateDirect((int) frameLocation.originalSize());

//            try (final ZstdDecompressCtx zstdDecompressCtx = new ZstdDecompressCtx()) {
//                zstdDecompressCtx.decompress(decompressedBuffer, srcBuffer);
//            }

//            decompressedBuffer.flip();
//            final String output = StandardCharsets.UTF_8.decode(decompressedBuffer).toString();
//            LOGGER.debug("output: {}", output);

            final ByteArrayInputStream compressedBytesInputStream = new ByteArrayInputStream(
                    compressedBytes,
                    (int) frameLocation.position(),
                    (int) frameLocation.compressedSize());
            try (final ZstdInputStream zstdInputStream = new ZstdInputStream(compressedBytesInputStream)) {
                final byte[] decompressedBytes = IOUtils.toByteArray(zstdInputStream);
                final String output = new String(decompressedBytes, StandardCharsets.UTF_8);
                LOGGER.debug("output: {}", output);
            }
        }

        final IntSortedSet includeSet = sortedSet(2, 4, 7);
        final double percentageOfCompressed = zstdSeekTable.getPercentageOfCompressed(includeSet);
        final long filtered = includeSet.intStream()
                .mapToLong(i -> zstdSeekTable.getFrameLocation(i).compressedSize())
                .sum();

        final double percentageOfCompressed2 = filtered / (double) compressedTotal * 100;
        assertThat(percentageOfCompressed)
                .isCloseTo(percentageOfCompressed2, Percentage.withPercentage(3));

        final Set<Integer> frameIndexes1 = StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(zstdSeekTable.iterator(), Spliterator.ORDERED),
                        false)
                .map(FrameLocation::frameIdx)
                .collect(Collectors.toSet());
        assertThat(frameIndexes1)
                .containsExactly(
                        0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

        final Set<Integer> frameIndexes2 = StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(zstdSeekTable.iterator(includeSet), Spliterator.ORDERED),
                        false)
                .map(FrameLocation::frameIdx)
                .collect(Collectors.toSet());
        assertThat(frameIndexes2)
                .containsExactly(
                        2, 4, 7);
    }

    @Test
    void testNotSeekableFile() {
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.limit(100);
        buffer.position(0);

        final Optional<ZstdSeekTable> optZstdSeekTable = ZstdSeekTable.parse(buffer);
        assertThat(optZstdSeekTable)
                .isEmpty();

//        Assertions.assertThatThrownBy(
//                () -> {
//                    ZstdSeekTable.parse(buffer);
//                }).isInstanceOf(InvalidSeekTableDataException.class);
    }

    @Test
    void testInsufficientData() throws IOException {
        final int seekTableFrameSize = ZstdSegmentUtil.calculateSeekTableFrameSize(ITERATIONS);
        final int halfFrameSize = seekTableFrameSize / 2;

        final byte[] compressedData = createCompressedData();
        final ByteBuffer buffer = ByteBuffer.wrap(
                compressedData,
                compressedData.length - halfFrameSize,
                halfFrameSize);

        assertThat(buffer.remaining())
                .isLessThan(seekTableFrameSize);

        final InsufficientSeekTableDataException e = Assertions.catchThrowableOfType(
                InsufficientSeekTableDataException.class,
                () -> ZstdSeekTable.parse(buffer));

        assertThat(e.getActualSeekTableFrameSize())
                .isEqualTo(halfFrameSize);
        assertThat(e.getRequiredSeekTableFrameSize())
                .isEqualTo(seekTableFrameSize);
    }

    @Test
    void testEmpty() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        //noinspection EmptyTryBlock
        try (final SegmentOutputStream ignored = new ZstdSegmentOutputStream(
                byteArrayOutputStream,
                null,
                new HeapBufferPool(ByteBufferPoolConfig::new),
                COMPRESSION_LEVEL)) {

            // Don't write any data to the stream
        }
        // No data written at all, so no Zstd frame headers or seek table
        final byte[] compressedBytes = byteArrayOutputStream.toByteArray();
        LOGGER.debug("compressedBytes.length: {}", compressedBytes.length);
        assertThat(compressedBytes)
                .hasSize(0);

        final Optional<ZstdSeekTable> zstdSeekTable = ZstdSeekTable.parse(ByteBuffer.wrap(compressedBytes));
        assertThat(zstdSeekTable)
                .isEmpty();
    }

    @Test
    void testAllEmptySegments() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        //noinspection EmptyTryBlock
        try (final SegmentOutputStream ignored = new ZstdSegmentOutputStream(
                byteArrayOutputStream,
                null,
                new HeapBufferPool(ByteBufferPoolConfig::new),
                COMPRESSION_LEVEL)) {

            // Don't write any data to the stream, but mark 3 empty segment boundaries,
            // so 4 segments
            ignored.addSegment();
            ignored.addSegment();
            ignored.addSegment();

        }
        // No data written at all, so no Zstd frame headers or seek table
        final byte[] compressedBytes = byteArrayOutputStream.toByteArray();
        LOGGER.debug("compressedBytes.length: {}", compressedBytes.length);
        assertThat(compressedBytes.length)
                .isGreaterThan(0);

        final ZstdSeekTable zstdSeekTable = ZstdSeekTable.parse(ByteBuffer.wrap(compressedBytes))
                .orElseThrow();
        assertThat(zstdSeekTable.getFrameCount())
                .isEqualTo(4);

        for (int i = 0; i < 4; i++) {
            final FrameLocation frameLocation = zstdSeekTable.getFrameLocation(i);
            assertThat(frameLocation.compressedSize())
                    .isEqualTo(0);
            assertThat(frameLocation.originalSize())
                    .isEqualTo(0);
        }
    }

    @Test
    void testGetTotalUncompressedSize() throws IOException {
        final byte[] compressedBytes = createCompressedData();
        final ZstdSeekTable zstdSeekTable = ZstdSeekTable.parse(ByteBuffer.wrap(compressedBytes))
                .orElseThrow();

        assertThat(zstdSeekTable.getFrameCount())
                .isEqualTo(ITERATIONS);
        assertThat(zstdSeekTable.isEmpty())
                .isFalse();

        final int expectedTotalSize = dataBytes.stream()
                .mapToInt(bytes -> bytes.length)
                .sum();

        assertThat(zstdSeekTable.getTotalUncompressedSize())
                .isEqualTo(expectedTotalSize);
        assertThat(zstdSeekTable.getTotalUncompressedSize())
                .isEqualTo(expectedTotalSize);

        assertThat(zstdSeekTable.getTotalUncompressedSize(
                IntSet.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)))
                .isEqualTo(expectedTotalSize);

        assertThat(zstdSeekTable.getTotalUncompressedSize(
                IntSet.of()))
                .isEqualTo(0);

        assertThat(zstdSeekTable.getTotalUncompressedSize(IntSet.of(2, 5)))
                .isEqualTo(dataBytes.get(2).length + dataBytes.get(5).length);

        Assertions.assertThatThrownBy(
                        () -> zstdSeekTable.getTotalUncompressedSize(IntSet.of(22)))
                .isInstanceOf(IllegalStateException.class);
    }

    @TestFactory
    Stream<DynamicTest> testFrameRanges() throws IOException {
        final byte[] compressedBytes = createCompressedData();
        final ZstdSeekTable zstdSeekTable = ZstdSeekTable.parse(ByteBuffer.wrap(compressedBytes))
                .orElseThrow();

        return TestUtil.buildDynamicTestStream()
                .withInputType(IntSortedSet.class)
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final List<FrameRange> ranges = zstdSeekTable.getContiguousRanges(testCase.getInput());
                    return NullSafe.stream(ranges)
                            .map(frameRange ->
                                    frameRange.startFrame().frameIdx() + "-" +
                                    frameRange.endFrame().frameIdx())
                            .collect(Collectors.joining(","));
                })
                .withSimpleEqualityAssertion()
                .addCase(null, "")
                .addCase(sortedSet(), "")
                .addCase(sortedSet(1), "1-1")
                .addCase(sortedSet(5), "5-5")
                .addCase(sortedSet(0, 9), "0-0,9-9")
                .addCase(sortedSet(0, 1, 2), "0-2")
                .addCase(sortedSet(0, 1, 2, 5, 7, 8), "0-2,5-5,7-8")
                .addCase(sortedSet(0, 1, 3, 4, 6, 7, 9), "0-1,3-4,6-7,9-9")
                .addCase(sortedSet(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), "0-9")
                .build();
    }

    private byte[] createCompressedData() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final Faker faker = new Faker(new Random(RANDOM_SEED));
        this.data = new ArrayList<>(ITERATIONS);
        this.dataBytes = new ArrayList<>(ITERATIONS);
        for (int i = 0; i < ITERATIONS; i++) {
            generateTestData(faker, i);
        }

        try (final SegmentOutputStream segmentOutputStream = new ZstdSegmentOutputStream(
                byteArrayOutputStream,
                null,
                new HeapBufferPool(ByteBufferPoolConfig::new),
                COMPRESSION_LEVEL)) {

            TestZstdSegmentOutputStream.writeDataToStream(dataBytes, segmentOutputStream);
        }
        return byteArrayOutputStream.toByteArray();
    }

    private void generateTestData(final Faker faker,
                                  final int iteration) {

        final int remainder = iteration % 3;
        String str;
        if (remainder == 0) {
            str = faker.backToTheFuture().quote();
        } else if (remainder == 1) {
            str = faker.simpsons().quote();
        } else {
            str = faker.southPark().quotes();
        }
        str = "<quote>" + str + "</quote>";
        // Dup[licate the str on each line so it can compress better with no dict
//        str = str + str + str + str;
        final byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
//            LOGGER.info("str: {}", str);
        data.add(str);
        dataBytes.add(bytes);
    }

    private static IntSortedSet sortedSet(final int... ints) {
        if (ints == null || ints.length == 0) {
            return IntSortedSets.emptySet();
        } else {
            return new IntAVLTreeSet(IntSet.of(ints));
        }
    }
}
