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
import stroom.data.store.api.SegmentInputStream;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.common.base.Strings;
import com.google.common.io.CountingOutputStream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestZstdSegmentInputStream {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestZstdSegmentInputStream.class);
    private static final String PADDING = Strings.repeat("#", 20);
    private static final int BUFFER_SIZE = 1;

    private final List<String> data = new ArrayList<>();
    private final List<byte[]> dataBytes = new ArrayList<>();

    /**
     * Make sure we can handle a variety of buffer sizes when consuming the stream
     */
    public static Stream<Arguments> getArguments() {
        return Stream.of(
                Arguments.of(1),
                Arguments.of(7),
                Arguments.of(1000)
        );
    }

    @ParameterizedTest
    @MethodSource("getArguments")
    void test1(final int bufferSize) throws IOException {
        doTest(
                SegmentInputStream::includeAll,
                new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
                bufferSize);
    }

    @ParameterizedTest
    @MethodSource("getArguments")
    void test2(final int bufferSize) throws IOException {
        doTest(
                segmentInputStream -> {
                    segmentInputStream.include(0);
                    segmentInputStream.include(3);
                    segmentInputStream.includeAll();
                },
                new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
                bufferSize);
    }

    @ParameterizedTest
    @MethodSource("getArguments")
    void test3(final int bufferSize) throws IOException {
        doTest(
                segmentInputStream -> {
                    segmentInputStream.include(9);
                    segmentInputStream.include(3);
                    segmentInputStream.include(0);
                    segmentInputStream.include(3);
                    segmentInputStream.include(5);
                },
                new int[]{0, 3, 5, 9},
                bufferSize);
    }

    @ParameterizedTest
    @MethodSource("getArguments")
    void test4(final int bufferSize) throws IOException {
        doTest(
                segmentInputStream -> {
                    segmentInputStream.include(3);
                },
                new int[]{3},
                bufferSize);
    }

    @ParameterizedTest
    @MethodSource("getArguments")
    void test5(final int bufferSize) {
        Assertions.assertThatThrownBy(
                () -> {
                    doTest(
                            segmentInputStream -> {
                                segmentInputStream.include(99);
                            },
                            null,
                            bufferSize);
                }
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("getArguments")
    void test6(final int bufferSize) {
        Assertions.assertThatThrownBy(
                () -> {
                    doTest(
                            segmentInputStream -> {
                                segmentInputStream.include(-1);
                            },
                            null,
                            bufferSize);
                }
        ).isInstanceOf(IllegalArgumentException.class);
    }

    private void doTest(final Consumer<SegmentInputStream> inputStreamConsumer,
                        final int[] expectedItemIndexes,
                        final int bufferSize) throws IOException {
        final int iterations = 10;

        final byte[] compressedBytes = makeData(iterations);
        LOGGER.debug("uncompressedBytes.length: {}", dataBytes.stream().mapToInt(bytes -> bytes.length).sum());
        final ZstdSeekTable zstdSeekTable = ZstdSeekTable.parse(
                        ByteBuffer.wrap(compressedBytes), false)
                .orElseThrow();

        assertThat(zstdSeekTable.getFrameCount())
                .isEqualTo(iterations);

        final ZstdFrameSupplier zstdFrameSupplier = new ByteArrayFrameSupplier(compressedBytes);
        final HeapBufferPool heapBufferPool = new HeapBufferPool(ByteBufferPoolConfig::new);
        final ZstdSegmentInputStream zstdSegmentInputStream = new ZstdSegmentInputStream(
                zstdSeekTable,
                zstdFrameSupplier,
                null,
                heapBufferPool);

        if (inputStreamConsumer != null) {
            inputStreamConsumer.accept(zstdSegmentInputStream);
        }

        // Use a tiny buffer to consume the input stream to make sure we test repeated read calls
        // rather than just reading it all into an 8k buffer.
        final byte[] uncompressedBytes = toBytes(zstdSegmentInputStream, bufferSize);
        final String uncompressedStr = new String(uncompressedBytes, StandardCharsets.UTF_8);
        final String expectedOutput = createExpectedOutput(expectedItemIndexes);

        LOGGER.debug("str: {}", uncompressedStr);

        assertThat(uncompressedStr)
                .isEqualTo(expectedOutput);
    }

    private byte[] toBytes(final InputStream inputStream, final int bufferSize) throws IOException {
        final byte[] buffer = new byte[bufferSize];
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int cnt;
        do {
            cnt = inputStream.read(buffer);
            if (cnt != -1) {
                byteArrayOutputStream.write(buffer, 0, cnt);
            }
        } while (cnt != -1);
        return byteArrayOutputStream.toByteArray();
    }

    private byte[] makeData(final int iterations) throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(1024);
        final CountingOutputStream countingOutputStream = new CountingOutputStream(byteArrayOutputStream);
        try (final ZstdSegmentOutputStream zstdSegmentOutputStream =
                new ZstdSegmentOutputStream(countingOutputStream)) {

            LOGGER.debug("Using frame count: {}", iterations);

            long lastOffset = 0;
            for (int i = 0; i < iterations; i++) {
                if (i != 0) {
                    zstdSegmentOutputStream.addSegment();
                }
                // Pad out the string so we have something bigger than our read buffer to read
                final String str = "Item-" + i + "_" + PADDING;
                final byte[] strBytes = (str).getBytes(StandardCharsets.UTF_8);
                zstdSegmentOutputStream.write(strBytes);
                zstdSegmentOutputStream.flush();
                data.add(str);
                dataBytes.add(strBytes);
                final long offset = countingOutputStream.getCount();
                LOGGER.debug("offset: {}, len: {}, str: {}",
                        offset, strBytes.length, str);
                lastOffset = offset;
            }
        }
        final byte[] bytes = byteArrayOutputStream.toByteArray();
        LOGGER.debug("bytes.length: {}", bytes.length);
        return bytes;
    }

    private static String createExpectedOutput(final int[] itemIndexes) {
        return Arrays.stream(itemIndexes)
                .boxed()
                .map(idx -> "Item-" + idx + "_" + PADDING)
                .collect(Collectors.joining(""));
    }


    // --------------------------------------------------------------------------------


    private static class ByteArrayFrameSupplier extends AbstractZstdFrameSupplier {

        private final byte[] compressedBytes;

        private ByteArrayFrameSupplier(final byte[] compressedBytes) {
            this.compressedBytes = compressedBytes;
        }

        @Override
        public void close() throws Exception {
            // no-op
        }

        @Override
        public InputStream next() {
            final FrameLocation frameLocation = nextFrameLocation();
            return new ByteArrayInputStream(
                    compressedBytes,
                    Math.toIntExact(frameLocation.position()),
                    Math.toIntExact(frameLocation.compressedSize()));
        }
    }
}
