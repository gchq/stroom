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

import stroom.bytebuffer.ByteBufferPoolConfig;
import stroom.data.store.api.SegmentInputStream;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.common.io.CountingOutputStream;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class TestZstdSegmentInputStream {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestZstdSegmentInputStream.class);

    private final List<String> data = new ArrayList<>();
    private final List<byte[]> dataBytes = new ArrayList<>();

    @Test
    void test1() throws IOException {
        doTest(
                SegmentInputStream::includeAll,
                "Item-0Item-1Item-2Item-3Item-4Item-5Item-6Item-7Item-8Item-9");
    }

    @Test
    void test2() throws IOException {
        doTest(
                segmentInputStream -> {
                    segmentInputStream.include(0);
                    segmentInputStream.include(3);
                    segmentInputStream.includeAll();
                },
                "Item-0Item-1Item-2Item-3Item-4Item-5Item-6Item-7Item-8Item-9");
    }

    @Test
    void test3() throws IOException {
        doTest(
                segmentInputStream -> {
                    segmentInputStream.include(9);
                    segmentInputStream.include(3);
                    segmentInputStream.include(0);
                    segmentInputStream.include(3);
                    segmentInputStream.include(5);
                },
                "Item-0Item-3Item-5Item-9");
    }

    @Test
    void test4() throws IOException {
        doTest(
                segmentInputStream -> {
                    segmentInputStream.include(3);
                },
                "Item-3");
    }

    @Test
    void test5() {
        Assertions.assertThatThrownBy(
                () -> {
                    doTest(
                            segmentInputStream -> {
                                segmentInputStream.include(99);
                            },
                            null);
                }
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void test6() {
        Assertions.assertThatThrownBy(
                () -> {
                    doTest(
                            segmentInputStream -> {
                                segmentInputStream.include(-1);
                            },
                            null);
                }
        ).isInstanceOf(IllegalArgumentException.class);
    }

    private void doTest(final Consumer<SegmentInputStream> inputStreamConsumer,
                        final String expectedOutput) throws IOException {
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
        final byte[] uncompressedBytes = toBytes(zstdSegmentInputStream, 7);
        final String uncompressedStr = new String(uncompressedBytes, StandardCharsets.UTF_8);

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
                final String str = "Item-" + i;
                final byte[] strBytes = ("Item-" + i).getBytes(StandardCharsets.UTF_8);
                zstdSegmentOutputStream.write(strBytes);
                data.add(str);
                dataBytes.add(strBytes);
                final long offset = countingOutputStream.getCount();
                LOGGER.debug("offset: {}, len: {}, str: {}",
                        offset, offset - lastOffset, str);
                lastOffset = offset;
            }
        }
        final byte[] bytes = byteArrayOutputStream.toByteArray();
        LOGGER.debug("bytes.length: {}", bytes.length);
        return bytes;
    }


    // --------------------------------------------------------------------------------


    private static class ByteArrayFrameSupplier implements ZstdFrameSupplier {

        private final byte[] compressedBytes;

        private ByteArrayFrameSupplier(final byte[] compressedBytes) {
            this.compressedBytes = compressedBytes;
        }

        @Override
        public void close() throws Exception {
            // no-op
        }

        @Override
        public void initialise(final ZstdSeekTable zstdSeekTable,
                               final IntSortedSet includedFrameIndexes,
                               final boolean includeAll) {

        }

        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public InputStream next() {
            return null;
        }

        @Override
        public FrameLocation getCurrentFrameLocation() {
            return null;
        }
    }
}
