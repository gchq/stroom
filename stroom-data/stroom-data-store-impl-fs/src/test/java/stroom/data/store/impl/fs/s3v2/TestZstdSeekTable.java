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

import stroom.data.store.api.SegmentOutputStream;
import stroom.data.store.impl.fs.s3v2.ZstdSeekTable.FilterMode;
import stroom.data.store.impl.fs.s3v2.ZstdSeekTable.InsufficientSeekTableDataException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.github.luben.zstd.ZstdInputStream;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.datafaker.Faker;
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

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

        for (int i = 0; i < ITERATIONS; i++) {
            final FrameLocation frameLocation = zstdSeekTable.getFrameLocation(i);
            LOGGER.debug("frameLocation: {}", frameLocation);

            assertThat(frameLocation)
                    .isNotNull();
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
                COMPRESSION_LEVEL)) {

            // Don't write any data to the stream
        }
        final byte[] compressedBytes = byteArrayOutputStream.toByteArray();

        final Optional<ZstdSeekTable> zstdSeekTable = ZstdSeekTable.parse(ByteBuffer.wrap(compressedBytes));
        assertThat(zstdSeekTable)
                .isEmpty();
//
//        assertThat(zstdSeekTable.getFrameCount())
//                .isZero();
//        assertThat(zstdSeekTable.isEmpty())
//                .isTrue();
//        assertThat(zstdSeekTable)
//                .isSameAs(ZstdSeekTable.EMPTY);
//
//        Assertions.assertThatThrownBy(() -> zstdSeekTable.getFrameLocation(-1))
//                .isInstanceOf(RuntimeException.class);
//        Assertions.assertThatThrownBy(() -> zstdSeekTable.getFrameLocation(0))
//                .isInstanceOf(RuntimeException.class);
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

        assertThat(zstdSeekTable.getTotalUncompressedSize(
                IntSet.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
                FilterMode.INCLUDE))
                .isEqualTo(expectedTotalSize);
        assertThat(zstdSeekTable.getTotalUncompressedSize(
                IntSet.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
                FilterMode.EXCLUDE))
                .isEqualTo(0);

        assertThat(zstdSeekTable.getTotalUncompressedSize(
                IntSet.of(),
                FilterMode.INCLUDE))
                .isEqualTo(0);
        assertThat(zstdSeekTable.getTotalUncompressedSize(
                IntSet.of(),
                FilterMode.EXCLUDE))
                .isEqualTo(expectedTotalSize);

        assertThat(zstdSeekTable.getTotalUncompressedSize(IntSet.of(2, 5), FilterMode.INCLUDE))
                .isEqualTo(dataBytes.get(2).length + dataBytes.get(5).length);

        assertThat(zstdSeekTable.getTotalUncompressedSize(IntSet.of(0, 1, 3, 4, 6, 7, 8, 9), FilterMode.EXCLUDE))
                .isEqualTo(dataBytes.get(2).length + dataBytes.get(5).length);

        Assertions.assertThatThrownBy(
                        () -> zstdSeekTable.getTotalUncompressedSize(IntSet.of(22), FilterMode.INCLUDE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private byte @NonNull [] createCompressedData() throws IOException {
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
}
