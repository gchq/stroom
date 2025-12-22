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
import stroom.data.store.impl.fs.s3v2.ZstdSegmentInputStream.ZstdFrameSupplier;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.common.io.CountingOutputStream;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestZstdSegmentInputStream {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestZstdSegmentInputStream.class);

    private final List<String> data = new ArrayList<>();
    private final List<byte[]> dataBytes = new ArrayList<>();

    @Test
    void test() throws IOException {
        final int iterations = 10;

        final byte[] compressedBytes = makeData(iterations);
        LOGGER.debug("uncompressedBytes.length: {}", dataBytes.stream().mapToInt(bytes -> bytes.length).sum());
        final ZstdSeekTable zstdSeekTable = ZstdSeekTable.parse(
                        ByteBuffer.wrap(compressedBytes), false)
                .orElseThrow();

        assertThat(zstdSeekTable.getFrameCount())
                .isEqualTo(iterations);

        final ZstdFrameSupplier zstdFrameSupplier = frameLocation ->
                new ByteArrayInputStream(
                        compressedBytes,
                        Math.toIntExact(frameLocation.position()),
                        Math.toIntExact(frameLocation.compressedSize()));

        final HeapBufferPool heapBufferPool = new HeapBufferPool(ByteBufferPoolConfig::new);
        final ZstdSegmentInputStream zstdSegmentInputStream = new ZstdSegmentInputStream(
                zstdSeekTable,
                zstdFrameSupplier,
                null,
                heapBufferPool);

        zstdSegmentInputStream.includeAll();

        final byte[] uncompressedBytes = IOUtils.toByteArray(zstdSegmentInputStream);
        final String uncompressedStr = new String(uncompressedBytes, StandardCharsets.UTF_8);

        LOGGER.debug("str: {}", uncompressedStr);

        for (final String str : data) {
            assertThat(uncompressedStr)
                    .contains(str);
        }
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
}
