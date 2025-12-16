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

package stroom.data.store.impl.fs.s3v2;

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
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestSegmentedZstdOutputStream {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestSegmentedZstdOutputStream.class);

    private static final int COMPRESSION_LEVEL = 7;
    private static final long RANDOM_SEED = 57294857573L;
    private List<String> lines;

    @Test
    void test_noDict() throws IOException {
        final int iterations = 10;
        lines = new ArrayList<>(iterations);
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final Faker faker = new Faker(new Random(RANDOM_SEED));
        final List<String> data = new ArrayList<>(iterations);
        final List<byte[]> dataBytes = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            generateTestData(faker, i, data, dataBytes);
        }

        try (final SegmentOutputStream segmentOutputStream = new SegmentedZstdOutputStream(
                byteArrayOutputStream,
                null,
                COMPRESSION_LEVEL)) {

            writeDataToStream(dataBytes, segmentOutputStream);
        }
        final byte[] compressedBytes = byteArrayOutputStream.toByteArray();

        try (final ZstdDecompressCtx zstdDecompressCtx = new ZstdDecompressCtx()) {
            // Make sure we can decompress the whole thing with no knowledge of the frames
            final int originalSize = lines.stream()
                    .mapToInt(str -> str.getBytes(StandardCharsets.UTF_8).length)
                    .sum();
            final byte[] decompressed = zstdDecompressCtx.decompress(compressedBytes, originalSize);
            final String decompressedStr = new String(decompressed, StandardCharsets.UTF_8);

            for (final String line : lines) {
                assertThat(decompressedStr)
                        .contains(line);
            }

            final ByteBuffer decompressedBuffer = ByteBuffer.allocateDirect(500);

            final ByteBuffer compressedBuffer = ByteBuffer.allocateDirect(compressedBytes.length);
            ByteBufferUtils.copy(ByteBuffer.wrap(compressedBytes), compressedBuffer);

            assertThat(SegmentedZstdUtil.isSeekable(compressedBuffer))
                    .isTrue();

            // Try and retrieve each event individually and check it matches what we expect
            for (int i = 0; i < data.size(); i++) {
                LOGGER.debug("i: {}, len: {}", i, dataBytes.get(i).length);
                final String expected = data.get(i);
                final String actual = getEvent(compressedBuffer, i, zstdDecompressCtx, decompressedBuffer);
                assertThat(actual)
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void test_withDict() throws IOException {
        final int iterations = 200;
        lines = new ArrayList<>(iterations);
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final Faker faker = new Faker(new Random(RANDOM_SEED));
        final List<String> data = new ArrayList<>(iterations);
        final List<byte[]> dataBytes = new ArrayList<>(iterations);

        for (int i = 0; i < iterations; i++) {
            generateTestData(faker, i, data, dataBytes);
        }

        final ZstdDictTrainer zstdDictTrainer = new ZstdDictTrainer(10000, 1000, COMPRESSION_LEVEL);
        dataBytes.forEach(zstdDictTrainer::addSample);
        final byte[] dict;
        try {
            dict = zstdDictTrainer.trainSamples();
        } catch (final ZstdException e) {
            throw new RuntimeException("Error training dictionary", e);
        }
        final ZstdDictionary zstdDictionary = new ZstdDictionary(UUID.randomUUID().toString(), dict);

        try (final SegmentOutputStream segmentOutputStream = new SegmentedZstdOutputStream(
                byteArrayOutputStream,
                zstdDictionary,
                COMPRESSION_LEVEL)) {

            writeDataToStream(dataBytes, segmentOutputStream);
        }

        final byte[] compressedBytes = byteArrayOutputStream.toByteArray();

        try (final ZstdDictDecompress zstdDictDecompress = new ZstdDictDecompress(zstdDictionary.getDictionaryBytes());
                final ZstdDecompressCtx zstdDecompressCtx = new ZstdDecompressCtx()) {
            zstdDecompressCtx.loadDict(zstdDictDecompress);
            // Make sure we can decompress the whole thing with no knowledge of the frames
            final int originalSize = lines.stream()
                    .mapToInt(str -> str.getBytes(StandardCharsets.UTF_8).length)
                    .sum();
            final byte[] decompressed = zstdDecompressCtx.decompress(compressedBytes, originalSize);
            final String decompressedStr = new String(decompressed, StandardCharsets.UTF_8);

            for (final String line : lines) {
                assertThat(decompressedStr)
                        .contains(line);
            }

            final ByteBuffer decompressedBuffer = ByteBuffer.allocateDirect(500);

            final ByteBuffer compressedBuffer = ByteBuffer.allocateDirect(compressedBytes.length);
            ByteBufferUtils.copy(ByteBuffer.wrap(compressedBytes), compressedBuffer);

            assertThat(SegmentedZstdUtil.isSeekable(compressedBuffer))
                    .isTrue();

            // Try and retrieve each event individually and check it matches what we expect
            for (int i = 0; i < data.size(); i++) {
                LOGGER.debug("i: {}, len: {}", i, dataBytes.get(i).length);
                final String expected = data.get(i);
                final String actual = getEvent(compressedBuffer, i, zstdDecompressCtx, decompressedBuffer);
                assertThat(actual)
                        .isEqualTo(expected);
            }
        }
    }

    private static void writeDataToStream(final List<byte[]> dataBytes, final SegmentOutputStream segmentOutputStream)
            throws IOException {
        for (int i = 0; i < dataBytes.size(); i++) {
            final byte[] bytes = dataBytes.get(i);
            if (i != 0) {
                segmentOutputStream.addSegment();
            }
            segmentOutputStream.write(bytes);
        }
    }

    private void generateTestData(final Faker faker,
                                  final int iteration,
                                  final List<String> data,
                                  final List<byte[]> dataBytes) throws IOException {

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
        lines.add(str);
        final byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
//            LOGGER.info("str: {}", str);
        data.add(str);
        dataBytes.add(bytes);
    }

    private String getEvent(final ByteBuffer compressedBuffer,
                            final int eventIdx,
                            final ZstdDecompressCtx zstdDecompressCtx,
                            final ByteBuffer decompressedBuffer) {

        final FrameLocation frameLocation = SegmentedZstdUtil.getFrameLocation(compressedBuffer, eventIdx);
        final long compressedIdx = frameLocation.position();
        final long compressedLen = frameLocation.compressedSize();
        final long originalSize = frameLocation.originalSize();

        LOGGER.debug("getQuote() - eventIdx: {}, compressedIdx: {}, compressedLen: {}, originalSize: {}",
                eventIdx, compressedIdx, compressedLen, originalSize);
        final ByteBuffer frameBuffer = compressedBuffer.slice((int) compressedIdx, (int) compressedLen);
        final ByteBuffer outputBuffer = decompressedBuffer.slice(0, (int) originalSize);

        zstdDecompressCtx.decompress(outputBuffer, frameBuffer);
        outputBuffer.flip();

        return StandardCharsets.UTF_8.decode(outputBuffer).toString();
    }
}
