package stroom.aws.s3.impl;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdDecompressCtx;
import com.github.luben.zstd.ZstdDictDecompress;
import com.github.luben.zstd.ZstdDictTrainer;
import com.github.luben.zstd.ZstdOutputStream;
import com.google.common.io.CountingOutputStream;
import net.datafaker.Faker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

public class TestZstd {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestZstd.class);

    public static final long RANDOM_SEED = 57294857573L;
    private static final int COMPRESSION_LEVEL = 5;

    @Test
    void test(@TempDir final Path dir) throws IOException {

        final Random random = new Random(RANDOM_SEED);
        final Faker faker = new Faker(random);
        final int iterations = 200;
        final ZstdDictTrainer zstdDictTrainer = new ZstdDictTrainer(
                10000,
                1000,
                COMPRESSION_LEVEL);

        final List<String> data = new ArrayList<>(iterations);
        final List<byte[]> dataBytes = new ArrayList<>(iterations);

        for (int i = 0; i < iterations; i++) {
            String str;
            final int remainder = i % 3;
            if (remainder == 0) {
                str = faker.backToTheFuture().quote();
            } else if (remainder == 1) {
                str = faker.simpsons().quote();
            } else {
                str = faker.southPark().quotes();
            }
            str = "<quote>" + str + "</quote>";
            final byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
//            LOGGER.info("str: {}", str);
            data.add(str);
            dataBytes.add(bytes);
            zstdDictTrainer.addSample(bytes);
        }

        final byte[] dict = zstdDictTrainer.trainSamples();
        final long dictId = Zstd.getDictIdFromDict(dict);
        LOGGER.info("dictId: {}", dictId);
        final long[] eventOffsets = new long[iterations];
        final int[] cumulativeCompressedSizes = new int[iterations];
        long startIdx = 0;
        long byteSum = 0;

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final CountingOutputStream countingOutputStream = new CountingOutputStream(byteArrayOutputStream);
        try (final ZstdOutputStream zstdOutputStream = new ZstdOutputStream(countingOutputStream)) {

            zstdOutputStream.setDict(dict);
            zstdOutputStream.setCloseFrameOnFlush(true);
            zstdOutputStream.setLevel(COMPRESSION_LEVEL);

            long lastCount = countingOutputStream.getCount();
            for (int i = 0; i < dataBytes.size(); i++) {
                final byte[] bytes = dataBytes.get(i);
                byteSum += bytes.length;
                zstdOutputStream.write(bytes);
                zstdOutputStream.flush();

                final long compressedByteCount = countingOutputStream.getCount();
                cumulativeCompressedSizes[i] = (int) countingOutputStream.getCount();

                final long len = compressedByteCount - lastCount;
                eventOffsets[i] = startIdx;
                LOGGER.debug("i: {}, bytes len: {}, startIdx: {}, lastCount: {}, count: {}, len: {}",
                        i, bytes.length, startIdx, lastCount, compressedByteCount, len);
                startIdx = startIdx + len;
                lastCount = compressedByteCount;
            }

            writeOffsetsData(eventOffsets, countingOutputStream);
        }
        final byte[] compressedBytes = byteArrayOutputStream.toByteArray();

        final Path dictFile = dir.resolve("dict");
        Files.write(dictFile, dict, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        LOGGER.debug("Written dict to {}", dictFile.toAbsolutePath());

        final Path file = dir.resolve("file.zst");
        Files.write(file, compressedBytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        LOGGER.debug("Written file to {}", file.toAbsolutePath());

        LOGGER.info("Compressed bytes: {}, uncompressed bytes: {}",
                ModelStringUtil.formatCsv(compressedBytes.length),
                ModelStringUtil.formatCsv(byteSum));
        try (final ZstdDictDecompress zstdDictDecompress = new ZstdDictDecompress(dict);
                final ZstdDecompressCtx zstdDecompressCtx = new ZstdDecompressCtx()) {

            zstdDecompressCtx.loadDict(zstdDictDecompress);
            final ByteBuffer decompressedBuffer = ByteBuffer.allocateDirect(500);

            final ByteBuffer compressedBuffer = ByteBuffer.allocateDirect(compressedBytes.length);
            ByteBufferUtils.copy(ByteBuffer.wrap(compressedBytes), compressedBuffer);

            // Try and retrieve each event individually and check it matches what we expect
            for (int i = 0; i < data.size(); i++) {
                LOGGER.debug("i: {}, len: {}", i, dataBytes.get(i).length);
                final String expected = data.get(i);
                final String actual = getQuote(
                        compressedBuffer,
                        cumulativeCompressedSizes,
                        i,
                        zstdDecompressCtx,
                        decompressedBuffer);
                assertThat(actual)
                        .isEqualTo(expected);
            }
        }
    }

    private void writeOffsetsData(final long[] eventOffsets,
                                  final OutputStream outputStream) throws IOException {
        // See https://github.com/facebook/zstd/blob/dev/contrib/seekable_format/zstd_seekable_compression_format.md
        // and https://github.com/facebook/zstd/blob/release/doc/zstd_compression_format.md#skippable-frames
        final byte[] skippableHeader = new byte[]{0x18, 0x4D, 0x2A, 0x5E};
        outputStream.write(skippableHeader);

        final byte[] payloadBytes = "hello world".getBytes(StandardCharsets.UTF_8);

        final byte[] payloadSizeBytes = new byte[Integer.BYTES];
        final ByteBuffer payloadSizeBuffer = ByteBuffer.wrap(payloadSizeBytes);
        // payload size must be little endian
        payloadSizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
        payloadSizeBuffer.putInt(payloadBytes.length);
        outputStream.write(payloadSizeBytes);

        outputStream.write(payloadBytes);
    }

    private String getQuote(final ByteBuffer compressedBuffer,
                            final int[] cumulativeCompressedSizes,
                            final int eventIdx,
                            final ZstdDecompressCtx zstdDecompressCtx,
                            final ByteBuffer decompressedBuffer) {

        final long startByteOffset;
        final long len;
        if (eventIdx == 0) {
            startByteOffset = 0;
            len = cumulativeCompressedSizes[eventIdx];
        } else {
            final int prevSize = cumulativeCompressedSizes[eventIdx - 1];
            startByteOffset = prevSize;
            len = cumulativeCompressedSizes[eventIdx] - prevSize;
        }

        final ByteBuffer compressedBufferSlice = compressedBuffer.slice((int) startByteOffset, (int) len);

        final long originalSize = Zstd.getFrameContentSize(compressedBufferSlice);
        LOGGER.debug("eventIdx: {}, startByteOffset: {}, len: {}, originalSize: {}",
                eventIdx, startByteOffset, len, originalSize);

        LOGGER.debug("compressedBufferSlice: {}",
                ByteBufferUtils.byteBufferInfo(compressedBufferSlice, false));

        decompressedBuffer.clear();
        final int uncompressedCount = zstdDecompressCtx.decompress(decompressedBuffer, compressedBufferSlice);
        decompressedBuffer.flip();
        LOGGER.debug("decompressedBuffer: {}",
                ByteBufferUtils.byteBufferInfo(decompressedBuffer, false));

        final String str = StandardCharsets.UTF_8.decode(decompressedBuffer).toString();
        LOGGER.debug("str: '{}', count: {}", str, uncompressedCount);
        return str;
    }
}
