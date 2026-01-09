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
import stroom.bytebuffer.ByteBufferUtils;
import stroom.data.store.api.SegmentOutputStream;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.github.luben.zstd.ZstdDecompressCtx;
import com.github.luben.zstd.ZstdDictDecompress;
import com.github.luben.zstd.ZstdDictTrainer;
import com.github.luben.zstd.ZstdException;
import net.datafaker.Faker;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestZstdSegmentOutputStream {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestZstdSegmentOutputStream.class);

    private static final int COMPRESSION_LEVEL = 7;
    private static final long RANDOM_SEED = 57294857573L;

    @Test
    void test_noDict() throws IOException {
        final int iterations = 10;
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final Faker faker = new Faker(new Random(RANDOM_SEED));
        final List<String> data = new ArrayList<>(iterations);
        final List<byte[]> dataBytes = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            generateTestData(faker, i, data, dataBytes);
        }

        try (final SegmentOutputStream segmentOutputStream = new ZstdSegmentOutputStream(
                byteArrayOutputStream,
                null,
                new HeapBufferPool(ByteBufferPoolConfig::new),
                COMPRESSION_LEVEL)) {

            writeDataToStream(dataBytes, segmentOutputStream);
        }
        final byte[] compressedBytes = byteArrayOutputStream.toByteArray();

        try (final ZstdDecompressCtx zstdDecompressCtx = new ZstdDecompressCtx()) {
            // Make sure we can decompress the whole thing with no knowledge of the frames
            final int originalSize = data.stream()
                    .mapToInt(str -> str.getBytes(StandardCharsets.UTF_8).length)
                    .sum();
            final byte[] decompressed = zstdDecompressCtx.decompress(compressedBytes, originalSize);
            final String decompressedStr = new String(decompressed, StandardCharsets.UTF_8);

            for (final String line : data) {
                assertThat(decompressedStr)
                        .contains(line);
            }

            final ByteBuffer decompressedBuffer = ByteBuffer.allocateDirect(500);

            final ByteBuffer compressedBuffer = ByteBuffer.allocateDirect(compressedBytes.length);
            ByteBufferUtils.copy(ByteBuffer.wrap(compressedBytes), compressedBuffer);

            assertThat(ZstdSegmentUtil.isSeekable(compressedBuffer))
                    .isTrue();
            final ZstdSeekTable zstdSeekTable = ZstdSeekTable.parse(compressedBuffer)
                    .orElseThrow();
            assertThat(zstdSeekTable.isEmpty())
                    .isEqualTo(false);
            assertThat(zstdSeekTable.hasDictionary())
                    .isFalse();
            assertThat(zstdSeekTable.getDictionaryUuid())
                    .isEmpty();

            // Try and retrieve each event individually and check it matches what we expect
            for (int i = 0; i < data.size(); i++) {
                LOGGER.debug("i: {}, len: {}", i, dataBytes.get(i).length);
                final String expected = data.get(i);
                final String actual = getEvent(
                        zstdSeekTable, compressedBuffer, i, zstdDecompressCtx, decompressedBuffer, data, dataBytes);
            }
        }
    }

    @Test
    void test_oneSegment() throws IOException {
        final int iterations = 1;
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final Faker faker = new Faker(new Random(RANDOM_SEED));
        final List<String> data = new ArrayList<>(iterations);
        final List<byte[]> dataBytes = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            generateTestData(faker, i, data, dataBytes);
        }

        try (final SegmentOutputStream segmentOutputStream = new ZstdSegmentOutputStream(
                byteArrayOutputStream,
                null,
                new HeapBufferPool(ByteBufferPoolConfig::new),
                COMPRESSION_LEVEL)) {

            writeDataToStream(dataBytes, segmentOutputStream);
        }
        final byte[] compressedBytes = byteArrayOutputStream.toByteArray();

        try (final ZstdDecompressCtx zstdDecompressCtx = new ZstdDecompressCtx()) {
            // Make sure we can decompress the whole thing with no knowledge of the frames
            final int originalSize = data.stream()
                    .mapToInt(str -> str.getBytes(StandardCharsets.UTF_8).length)
                    .sum();
            final byte[] decompressed = zstdDecompressCtx.decompress(compressedBytes, originalSize);
            final String decompressedStr = new String(decompressed, StandardCharsets.UTF_8);

            for (final String line : data) {
                assertThat(decompressedStr)
                        .contains(line);
            }

            final ByteBuffer compressedBuffer = ByteBuffer.allocateDirect(compressedBytes.length);
            ByteBufferUtils.copy(ByteBuffer.wrap(compressedBytes), compressedBuffer);

            assertThat(ZstdSegmentUtil.isSeekable(compressedBuffer))
                    .isTrue();

            final Optional<ZstdSeekTable> optZstdSeekTable = ZstdSeekTable.parse(compressedBuffer);
            assertThat(optZstdSeekTable)
                    .isPresent();
            final ZstdSeekTable zstdSeekTable = optZstdSeekTable.get();
            assertThat(zstdSeekTable.getFrameCount())
                    .isEqualTo(1);
            assertThat(zstdSeekTable.hasDictionary())
                    .isFalse();
            assertThat(zstdSeekTable.getDictionaryUuid())
                    .isEmpty();
        }
    }

    @Test
    void test_someEmptySegments() throws IOException {
        final int iterations = 10;
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final List<String> data = new ArrayList<>(iterations);
        final List<byte[]> dataBytes = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            final String str = "frame-" + i;
            data.add(str);
            dataBytes.add(str.getBytes(StandardCharsets.UTF_8));
        }

        try (final SegmentOutputStream segmentOutputStream = new ZstdSegmentOutputStream(
                byteArrayOutputStream,
                null,
                new HeapBufferPool(ByteBufferPoolConfig::new),
                COMPRESSION_LEVEL)) {

            for (int i = 0; i < dataBytes.size(); i++) {
                final byte[] bytes = dataBytes.get(i);
                // Write the segments regardless
                if (i != 0) {
                    segmentOutputStream.addSegment();
                }

                if (i % 3 == 0) {
                    LOGGER.debug("Writing data {}", i);
                    segmentOutputStream.write(bytes);
                }
            }
        }
        final byte[] compressedBytes = byteArrayOutputStream.toByteArray();

        try (final ZstdDecompressCtx zstdDecompressCtx = new ZstdDecompressCtx()) {
            // Make sure we can decompress the whole thing with no knowledge of the frames
            final int originalSize = data.stream()
                    .mapToInt(str -> str.getBytes(StandardCharsets.UTF_8).length)
                    .sum();
            final byte[] decompressed = zstdDecompressCtx.decompress(compressedBytes, originalSize);
            final String decompressedStr = new String(decompressed, StandardCharsets.UTF_8);

            for (int i = 0; i < data.size(); i++) {
                if (i % 3 == 0) {
                    final String line = data.get(i);
                    assertThat(decompressedStr)
                            .contains(line);
                }
            }
            assertThat(decompressedStr)
                    .isEqualTo("frame-0frame-3frame-6frame-9");

            final ByteBuffer decompressedBuffer = ByteBuffer.allocateDirect(500);
            final ByteBuffer compressedBuffer = ByteBuffer.allocateDirect(compressedBytes.length);
            ByteBufferUtils.copy(ByteBuffer.wrap(compressedBytes), compressedBuffer);

            assertThat(ZstdSegmentUtil.isSeekable(compressedBuffer))
                    .isTrue();

            final Optional<ZstdSeekTable> optZstdSeekTable = ZstdSeekTable.parse(compressedBuffer);
            assertThat(optZstdSeekTable)
                    .isPresent();
            final ZstdSeekTable zstdSeekTable = optZstdSeekTable.get();
            assertThat(zstdSeekTable.getFrameCount())
                    .isEqualTo(iterations);
            assertThat(zstdSeekTable.hasDictionary())
                    .isFalse();
            assertThat(zstdSeekTable.getDictionaryUuid())
                    .isEmpty();

            // Try and retrieve each event individually and check it matches what we expect
            for (int i = 0; i < data.size(); i++) {
                final FrameLocation frameLocation = zstdSeekTable.getFrameLocation(i);
                if (i % 3 == 0) {
                    LOGGER.debug("i: {}, len: {}", i, dataBytes.get(i).length);
                    final String expected = data.get(i);
                    final String actual = getEvent(
                            zstdSeekTable, compressedBuffer, i, zstdDecompressCtx, decompressedBuffer, data, dataBytes);
                    assertThat(actual)
                            .isEqualTo(expected);
                    assertThat(frameLocation.isEmptyFrame())
                            .isFalse();
                } else {
                    assertThat(frameLocation.compressedSize())
                            .isEqualTo(0);  // Zstd still writes a frame header
                    assertThat(frameLocation.originalSize())
                            .isEqualTo(0);
                    assertThat(frameLocation.isEmptyFrame())
                            .isTrue();
                }
            }
        }
    }

    @Test
    void test_allEmptySegments() throws IOException {
        final int iterations = 10;
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final List<String> data = new ArrayList<>(iterations);
        final List<byte[]> dataBytes = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            final String str = "frame-" + i;
            data.add(str);
            dataBytes.add(str.getBytes(StandardCharsets.UTF_8));
        }

        try (final SegmentOutputStream segmentOutputStream = new ZstdSegmentOutputStream(
                byteArrayOutputStream,
                null,
                new HeapBufferPool(ByteBufferPoolConfig::new),
                COMPRESSION_LEVEL)) {

            for (int i = 0; i < dataBytes.size(); i++) {
                // Write the segments regardless
                if (i != 0) {
                    segmentOutputStream.addSegment();
                }
                // don't write any data
            }
        }
        // Should contain only the seek table frame
        final byte[] compressedBytes = byteArrayOutputStream.toByteArray();
        assertThat(compressedBytes.length)
                .isEqualTo(ZstdSegmentUtil.calculateSeekTableFrameSize(iterations));

        try (final ZstdDecompressCtx zstdDecompressCtx = new ZstdDecompressCtx()) {
            // Make sure we can decompress the whole thing with no knowledge of the frames
            final int originalSize = data.stream()
                    .mapToInt(str -> str.getBytes(StandardCharsets.UTF_8).length)
                    .sum();
            final byte[] decompressed = zstdDecompressCtx.decompress(compressedBytes, originalSize);

            assertThat(decompressed.length)
                    .isEqualTo(0);

            final ByteBuffer compressedBuffer = ByteBuffer.allocateDirect(compressedBytes.length);
            ByteBufferUtils.copy(ByteBuffer.wrap(compressedBytes), compressedBuffer);

            assertThat(ZstdSegmentUtil.isSeekable(compressedBuffer))
                    .isTrue();

            final Optional<ZstdSeekTable> optZstdSeekTable = ZstdSeekTable.parse(compressedBuffer);
            assertThat(optZstdSeekTable)
                    .isPresent();
            final ZstdSeekTable zstdSeekTable = optZstdSeekTable.get();
            assertThat(zstdSeekTable.getFrameCount())
                    .isEqualTo(iterations);
            assertThat(zstdSeekTable.hasDictionary())
                    .isFalse();
            assertThat(zstdSeekTable.getDictionaryUuid())
                    .isEmpty();

            // Try and retrieve each event individually and check it matches what we expect
            for (int i = 0; i < data.size(); i++) {
                final FrameLocation frameLocation = zstdSeekTable.getFrameLocation(i);
                assertThat(frameLocation.compressedSize())
                        .isEqualTo(0);  // Zstd still writes a frame header
                assertThat(frameLocation.originalSize())
                        .isEqualTo(0);
                assertThat(frameLocation.isEmptyFrame())
                        .isTrue();
            }
        }
    }

    @Test
    void test_noDataOrSegmentsWriten() throws IOException {
        final int iterations = 10;
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
//        final List<String> data = new ArrayList<>(iterations);
//        final List<byte[]> dataBytes = new ArrayList<>(iterations);
//        for (int i = 0; i < iterations; i++) {
//            final String str = "frame-" + i;
//            data.add(str);
//            dataBytes.add(str.getBytes(StandardCharsets.UTF_8));
//        }

        try (final SegmentOutputStream segmentOutputStream = new ZstdSegmentOutputStream(
                byteArrayOutputStream,
                null,
                new HeapBufferPool(ByteBufferPoolConfig::new),
                COMPRESSION_LEVEL)) {
            // don't write any data or segments
        }
        // Should contain only the seek table frame
        final byte[] compressedBytes = byteArrayOutputStream.toByteArray();
        assertThat(compressedBytes.length)
                .isEqualTo(0);

        try (final ZstdDecompressCtx zstdDecompressCtx = new ZstdDecompressCtx()) {
            // Make sure we can decompress the whole thing with no knowledge of the frames
            final int originalSize = 0;
            final byte[] decompressed = zstdDecompressCtx.decompress(compressedBytes, originalSize);

            assertThat(decompressed.length)
                    .isEqualTo(0);

            final ByteBuffer compressedBuffer = ByteBuffer.allocateDirect(compressedBytes.length);
            ByteBufferUtils.copy(ByteBuffer.wrap(compressedBytes), compressedBuffer);

            assertThat(ZstdSegmentUtil.isSeekable(compressedBuffer))
                    .isFalse();

            final Optional<ZstdSeekTable> optZstdSeekTable = ZstdSeekTable.parse(compressedBuffer);
            assertThat(optZstdSeekTable)
                    .isEmpty();
        }
    }

    @Test
    void test_withDict() throws IOException {
        final int iterations = 200;
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final Faker faker = new Faker(new Random(RANDOM_SEED));
        final List<String> data = new ArrayList<>(iterations);
        final List<byte[]> dataBytes = new ArrayList<>(iterations);

        for (int i = 0; i < iterations; i++) {
            generateTestData(faker, i, data, dataBytes);
        }

        // Make sure we can set the dict with a compression level that is different to that
        // actually used during compression. I believe setting it on the dict is just an optimisation
        // for that level.
        final int dictCompressionLevel = COMPRESSION_LEVEL - 1;

        final ZstdDictTrainer zstdDictTrainer = new ZstdDictTrainer(
                10_000, 1_000, dictCompressionLevel);
        dataBytes.forEach(zstdDictTrainer::addSample);
        final byte[] dict;
        try {
            dict = zstdDictTrainer.trainSamples();
        } catch (final ZstdException e) {
            throw new RuntimeException("Error training dictionary", e);
        }
        final UUID dictUuid = UUID.randomUUID();
        final ZstdDictionary zstdDictionary = new ZstdDictionary(dictUuid, dict);

        try (final SegmentOutputStream segmentOutputStream = new ZstdSegmentOutputStream(
                byteArrayOutputStream,
                zstdDictionary,
                new HeapBufferPool(ByteBufferPoolConfig::new),
                COMPRESSION_LEVEL)) {

            writeDataToStream(dataBytes, segmentOutputStream);
        }

        final byte[] compressedBytes = byteArrayOutputStream.toByteArray();

        try (final ZstdDictDecompress zstdDictDecompress = new ZstdDictDecompress(zstdDictionary.getDictionaryBytes());
                final ZstdDecompressCtx zstdDecompressCtx = new ZstdDecompressCtx()) {
            zstdDecompressCtx.loadDict(zstdDictDecompress);
            // Make sure we can decompress the whole thing with no knowledge of the frames
            final int originalSize = data.stream()
                    .mapToInt(str -> str.getBytes(StandardCharsets.UTF_8).length)
                    .sum();
            final byte[] decompressed = zstdDecompressCtx.decompress(compressedBytes, originalSize);
            final String decompressedStr = new String(decompressed, StandardCharsets.UTF_8);

            for (final String line : data) {
                assertThat(decompressedStr)
                        .contains(line);
            }

            final ByteBuffer decompressedBuffer = ByteBuffer.allocateDirect(500);

            final ByteBuffer compressedBuffer = ByteBuffer.allocateDirect(compressedBytes.length);
            ByteBufferUtils.copy(ByteBuffer.wrap(compressedBytes), compressedBuffer);

            assertThat(ZstdSegmentUtil.isSeekable(compressedBuffer))
                    .isTrue();

            final ZstdSeekTable zstdSeekTable = ZstdSeekTable.parse(compressedBuffer)
                    .orElseThrow();
            assertThat(zstdSeekTable.hasDictionary())
                    .isTrue();
            assertThat(zstdSeekTable.getDictionaryUuid())
                    .hasValue(dictUuid);

            // Try and retrieve each event individually and check it matches what we expect
            for (int i = 0; i < data.size(); i++) {
                LOGGER.debug("i: {}, len: {}", i, dataBytes.get(i).length);
                final String expected = data.get(i);
                final String actual = getEvent(
                        zstdSeekTable, compressedBuffer, i, zstdDecompressCtx, decompressedBuffer, data, dataBytes);
                assertThat(actual)
                        .isEqualTo(expected);
            }
        }
    }

    static void writeDataToStream(final List<byte[]> dataBytes, final SegmentOutputStream segmentOutputStream)
            throws IOException {
        for (int i = 0; i < dataBytes.size(); i++) {
            final byte[] bytes = dataBytes.get(i);
            if (i != 0) {
                segmentOutputStream.addSegment();
            }
            segmentOutputStream.write(bytes);
        }
    }

    static void generateTestData(final Faker faker,
                                 final int iteration,
                                 final List<String> data,
                                 final List<byte[]> dataBytes) {

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

    private String getEvent(final ZstdSeekTable zstdSeekTable,
                            final ByteBuffer compressedBuffer,
                            final int eventIdx,
                            final ZstdDecompressCtx zstdDecompressCtx,
                            final ByteBuffer decompressedBuffer,
                            final List<String> data,
                            final List<byte[]> dataBytes) {

        final FrameLocation frameLocation = zstdSeekTable.getFrameLocation(eventIdx);
//        final FrameLocation frameLocation = ZstdSegmentUtil.getFrameLocation(compressedBuffer, eventIdx);
        final long compressedIdx = frameLocation.position();
        final long compressedLen = frameLocation.compressedSize();
        final long originalSize = frameLocation.originalSize();

        LOGGER.debug("getQuote() - eventIdx: {}, compressedIdx: {}, compressedLen: {}, originalSize: {}",
                eventIdx, compressedIdx, compressedLen, originalSize);
        final ByteBuffer frameBuffer = compressedBuffer.slice((int) compressedIdx, (int) compressedLen);
        final ByteBuffer outputBuffer = decompressedBuffer.slice(0, (int) originalSize);

        zstdDecompressCtx.decompress(outputBuffer, frameBuffer);
        outputBuffer.flip();

        final long expectedOriginalSize = dataBytes.get(eventIdx).length;
        assertThat(originalSize)
                .isEqualTo(expectedOriginalSize);

        final String expected = data.get(eventIdx);
        final String actual = StandardCharsets.UTF_8.decode(outputBuffer).toString();
        assertThat(actual)
                .isEqualTo(expected);
        return actual;
    }
}
