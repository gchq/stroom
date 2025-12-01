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

    // See https://github.com/facebook/zstd/blob/release/doc/zstd_compression_format.md#skippable-frames
    // Identifies the frame as one that zstd will skip over
    public static final byte[] SKIPPABLE_FRAME_HEADER = new byte[]{0x5E, 0x2A, 0x4D, 0x18};

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestZstd.class);

    public static final long RANDOM_SEED = 57294857573L;
    private static final int COMPRESSION_LEVEL = 5;

    private final byte[] fourByteArray = new byte[Integer.BYTES];
    private final ByteBuffer fourByteBuffer = ByteBuffer.wrap(fourByteArray);

    @Test
    void test(@TempDir final Path dir) throws IOException {

        final int iterations = 200;

        final List<String> data = new ArrayList<>(iterations);
        final List<byte[]> dataBytes = new ArrayList<>(iterations);

        final byte[] dict = buildTestDataAndDictionary(iterations, data, dataBytes);

        final CompressResult compressResult = compressData(iterations, dict, dataBytes);
        final byte[] compressedBytes = compressResult.compressedBytes;


        final Path dictFile = dir.resolve("dict");
        Files.write(dictFile, dict, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        LOGGER.debug("Written dict {} to {}", Zstd.getDictIdFromDict(dict), dictFile.toAbsolutePath());

        final Path file = dir.resolve("file.zst");
        Files.write(file, compressedBytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        LOGGER.debug("Written file to {}", file.toAbsolutePath());

        LOGGER.info("dir: {}", dir.toAbsolutePath());


        try (final ZstdDictDecompress zstdDictDecompress = new ZstdDictDecompress(dict);
                final ZstdDecompressCtx zstdDecompressCtx = new ZstdDecompressCtx()) {

            zstdDecompressCtx.loadDict(zstdDictDecompress);

            // Make sure we can decompress the whole thing as if it were just one frame
            final byte[] decompressed = zstdDecompressCtx.decompress(compressedBytes, (int) compressResult.byteSum());
            final String decompressedStr = new String(decompressed, StandardCharsets.UTF_8);

            for (final String line : data) {
                assertThat(decompressedStr)
                        .contains(line);
            }

            final ByteBuffer decompressedBuffer = ByteBuffer.allocateDirect(500);

            final ByteBuffer compressedBuffer = ByteBuffer.allocateDirect(compressedBytes.length);
            ByteBufferUtils.copy(ByteBuffer.wrap(compressedBytes), compressedBuffer);

            assertThat(isSeekable(compressedBuffer))
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

    private CompressResult compressData(final int iterations, final byte[] dict, final List<byte[]> dataBytes)
            throws IOException {
        final List<FrameInfo> frameInfoList = new ArrayList<>(iterations);
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
//                cumulativeCompressedSizes[i] = (int) countingOutputStream.getCount();
                frameInfoList.add(new FrameInfo(i, countingOutputStream.getCount(), bytes.length));

                final long len = compressedByteCount - lastCount;
//                eventOffsets[i] = startIdx;
                LOGGER.debug("i: {}, uncompressed bytes len: {}, startIdx: {}, lastCount: {}, " +
                             "count: {}, compressed len: {}",
                        i, bytes.length, startIdx, lastCount, compressedByteCount, len);
                startIdx = startIdx + len;
                lastCount = compressedByteCount;
            }

            writeOffsetsData(frameInfoList, countingOutputStream);
        }
        final byte[] compressedBytes = byteArrayOutputStream.toByteArray();

        LOGGER.info("Compressed bytes: {}, uncompressed bytes: {}",
                ModelStringUtil.formatCsv(compressedBytes.length),
                ModelStringUtil.formatCsv(byteSum));
        return new CompressResult(byteSum, compressedBytes);
    }

    private static byte[] buildTestDataAndDictionary(final int iterations,
                                                     final List<String> data,
                                                     final List<byte[]> dataBytes) {
        final Faker faker = new Faker(new Random(RANDOM_SEED));
        final ZstdDictTrainer zstdDictTrainer = new ZstdDictTrainer(
                10000,
                1000,
                COMPRESSION_LEVEL);

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
        return dict;
    }

    private void writeOffsetsData(final List<FrameInfo> frameInfoList,
                                  final OutputStream outputStream) throws IOException {
        // Format of a generic skippable frame is
        // <4 byte magic number><4 byte size of payload (LE)><payload>
        // See https://github.com/facebook/zstd/blob/release/doc/zstd_compression_format.md#skippable-frames

        final long payloadSize = FrameInfo.calculatePayloadSize(frameInfoList.size(), false);

        // Write the skippable frame header

        // Magic number so zstd will skip over this frame
        outputStream.write(SKIPPABLE_FRAME_HEADER);
        // Size of the frame (after this header)
        writeLEInteger(payloadSize, outputStream);

        // See https://github.com/facebook/zstd/blob/dev/contrib/seekable_format/zstd_seekable_compression_format.md

        // Write one entry describing each frame
        for (final FrameInfo frameInfo : frameInfoList) {
            writeLEInteger(frameInfo.cumulativeCompressedSize, outputStream);
            writeLEInteger(frameInfo.uncompressedSize, outputStream);
        }

        // write the footer

        // Frame count, so know how big our seek table is
        writeLEInteger(frameInfoList.size(), outputStream);
        // Seek table descriptor bitfield
        outputStream.write((byte) 0);
        // Seekable magic number, so we can look at the last 4 bytes of a zst file and determine
        // that it is seekable
        outputStream.write(FrameInfo.SEEKABLE_MAGIC_NUMBER);
    }

    private void writeLEInteger(final long val, final OutputStream outputStream) throws IOException {
        fourByteBuffer.clear();
        fourByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        putUnsignedInt(fourByteBuffer, val);
        fourByteBuffer.flip();
        outputStream.write(fourByteBuffer.array());
    }

//    public static long getUnsignedInt(final ByteBuffer byteBuffer) {
//        final ByteBuffer slice = byteBuffer.slice(0Integer.BYTES)
//                .order(ByteOrder.LITTLE_ENDIAN);
//        return ((long) slice.getInt() & 0xFFFFFFFFL);
//    }

    public static long getUnsignedInt(final ByteBuffer byteBuffer, final int index) {
        final ByteBuffer slice = byteBuffer.slice(index, Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        return ((long) slice.getInt(0) & 0xFFFFFFFFL);
    }

    public static void putUnsignedInt(final ByteBuffer byteBuffer, final long value) {
        byteBuffer.putInt((int) (value & 0xFFFFFFFFL));
    }

//    private String getQuote(final ByteBuffer compressedBuffer,
//                            final long[] cumulativeCompressedSizes,
//                            final int eventIdx,
//                            final ZstdDecompressCtx zstdDecompressCtx,
//                            final ByteBuffer decompressedBuffer) {
//
//        final long startByteOffset;
//        final long len;
//        if (eventIdx == 0) {
//            startByteOffset = 0;
//            len = cumulativeCompressedSizes[eventIdx];
//        } else {
//            final long prevSize = cumulativeCompressedSizes[eventIdx - 1];
//            startByteOffset = prevSize;
//            len = cumulativeCompressedSizes[eventIdx] - prevSize;
//        }
//
//        final ByteBuffer compressedBufferSlice = compressedBuffer.slice((int) startByteOffset, (int) len);
//
//        final long originalSize = Zstd.getFrameContentSize(compressedBufferSlice);
//        LOGGER.debug("eventIdx: {}, startByteOffset: {}, len: {}, originalSize: {}",
//                eventIdx, startByteOffset, len, originalSize);
//
//        LOGGER.debug("compressedBufferSlice: {}",
//                ByteBufferUtils.byteBufferInfo(compressedBufferSlice, false));
//
//        decompressedBuffer.clear();
//        final int uncompressedCount = zstdDecompressCtx.decompress(decompressedBuffer, compressedBufferSlice);
//        decompressedBuffer.flip();
//        LOGGER.debug("decompressedBuffer: {}",
//                ByteBufferUtils.byteBufferInfo(decompressedBuffer, false));
//
//        final String str = StandardCharsets.UTF_8.decode(decompressedBuffer).toString();
//        LOGGER.debug("str: '{}', count: {}", str, uncompressedCount);
//        return str;
//    }

    private String getEvent(final ByteBuffer compressedBuffer,
                            final int eventIdx,
                            final ZstdDecompressCtx zstdDecompressCtx,
                            final ByteBuffer decompressedBuffer) {

        final FrameLocation frameLocation = getFrameLocation(compressedBuffer, eventIdx);
        final long compressedIdx = frameLocation.index;
        final int compressedLen = frameLocation.len;
        final long originalSize = frameLocation.originalSize;

        LOGGER.debug("getQuote() - eventIdx: {}, compressedIdx: {}, compressedLen: {}, originalSize: {}",
                eventIdx, compressedIdx, compressedLen, originalSize);
        final ByteBuffer frameBuffer = compressedBuffer.slice((int) compressedIdx, compressedLen);
        final ByteBuffer outputBuffer = decompressedBuffer.slice(0, (int) originalSize);

        zstdDecompressCtx.decompress(outputBuffer, frameBuffer);
        outputBuffer.flip();

        return StandardCharsets.UTF_8.decode(outputBuffer).toString();
    }

    private boolean isSeekable(final ByteBuffer compressedBuffer) {
        // Includes the skippable frame
        final int totalCompressedSize = compressedBuffer.capacity();
        final int len = FrameInfo.SEEKABLE_MAGIC_NUMBER.length;
        final int result = ByteBufferUtils.compareTo(
                compressedBuffer, totalCompressedSize - len, len,
                FrameInfo.SEEKABLE_MAGIC_NUMBER_BUFFER, 0, len);
        return result == 0;
    }

    private int getFrameCount(final ByteBuffer compressedBuffer) {
        // Includes the skippable frame
        final int frameCountIdx = compressedBuffer.capacity()
                                  - FrameInfo.SEEKABLE_MAGIC_NUMBER.length
                                  - 1 // bitfield
                                  - Integer.BYTES;

        final long frameCount = getUnsignedInt(compressedBuffer, frameCountIdx);
        return (int) frameCount;
    }

    private FrameLocation getFrameLocation(final ByteBuffer compressedBuffer, final int frameIdx) {
        final int frameCount = getFrameCount(compressedBuffer);
        final long entryIdx = getSeekTableEntryIndex(compressedBuffer, frameIdx, frameCount);
        final FrameInfo frameInfo = getFrameInfo(compressedBuffer, frameIdx, entryIdx);

        if (frameIdx == 0) {
            return new FrameLocation(
                    0L,
                    (int) frameInfo.cumulativeCompressedSize,
                    frameInfo.uncompressedSize());
        } else {
            final int prevFrameIdx = frameIdx - 1;
            final long prevEntryIdx = getSeekTableEntryIndex(compressedBuffer, prevFrameIdx, frameCount);
            final FrameInfo prevFrameInfo = getFrameInfo(compressedBuffer, prevFrameIdx, prevEntryIdx);
            return new FrameLocation(
                    prevFrameInfo.cumulativeCompressedSize,
                    (int) (frameInfo.cumulativeCompressedSize - prevFrameInfo.cumulativeCompressedSize),
                    frameInfo.uncompressedSize);
        }
    }

    private FrameInfo getFrameInfo(final ByteBuffer byteBuffer,
                                   final int frameIdx,
                                   final long entryIdx) {
        return new FrameInfo(
                frameIdx,
                getUnsignedInt(byteBuffer, (int) entryIdx),
                (int) getUnsignedInt(byteBuffer, (int) entryIdx + Integer.BYTES));
    }

    private long getSeekTableEntryIndex(final ByteBuffer compressedBuffer,
                                        final int frameIdx,
                                        final int frameCount) {
        // C == cumulativeCompressedSize
        // U == uncompressedSize
        // F == frameCount    \
        // B == bitfield      | - Footer
        // M == magic number  /
        // Frames:                 0       1       2       3       4       5
        // ........................CCCCUUUUCCCCUUUUCCCCUUUUCCCCUUUUCCCCUUUUCCCCUUUUFFFFBMMMM
        // 012345678901234567890123456789012345678901234567890123456789012345678901234567890
        // 0         1         2         3         4         5         6         7         8

        return compressedBuffer.capacity()
               - FrameInfo.FOOTER_BYTES
               - ((frameCount - frameIdx) * (long) FrameInfo.getEntrySize(false));
    }


    // --------------------------------------------------------------------------------


    private record CompressResult(long byteSum, byte[] compressedBytes) {

    }


    // --------------------------------------------------------------------------------

    private record FrameLocation(long index, int len, int originalSize) {

    }


    // --------------------------------------------------------------------------------


    private record FrameInfo(int frameIdx,
                             long cumulativeCompressedSize, // long so we can serialise as unsigned int
                             int uncompressedSize) {

        // See https://github.com/facebook/zstd/blob/dev/contrib/seekable_format/zstd_seekable_compression_format.md
        public static final byte[] SEEKABLE_MAGIC_NUMBER = new byte[]{
                (byte) 0xB1, (byte) 0xEA, (byte) 0x92, (byte) 0x8F};
        public static final ByteBuffer SEEKABLE_MAGIC_NUMBER_BUFFER = ByteBuffer.wrap(SEEKABLE_MAGIC_NUMBER);
        // TODO is an int big enough. An unsigned int gives us a max compressed file of 4.2Gb
        // <4b frame count LE><1b table descriptor><4b seekable magic number LE>
        public static int FOOTER_BYTES = 9;

        public static int getEntrySize(final boolean useChecksums) {
            int entryBytes = Integer.BYTES + Integer.BYTES;
            if (useChecksums) {
                entryBytes += Integer.BYTES;
            }
            return entryBytes;
        }

        public static int calculatePayloadSize(final int frameCount,
                                               final boolean useChecksums) {
            final int entryBytes = getEntrySize(useChecksums);
            return (entryBytes * frameCount) + FOOTER_BYTES;
        }
    }

//    @Test
//    void name() {
//        ByteBuffer bb1 = ByteBuffer.wrap(new byte[4])
//                .order(ByteOrder.LITTLE_ENDIAN);
//        bb1.putInt(1067);
//        bb1.flip();
//        LOGGER.info("bb1: {}", ByteBufferUtils.byteBufferInfo(bb1, false));
//
//        ByteBuffer bb2 = ByteBuffer.wrap(new byte[4])
//                .order(ByteOrder.LITTLE_ENDIAN);
//        UnsignedBytesInstances.FOUR.put(bb2, 1067);
//        bb2.flip();
//        LOGGER.info("bb2: {}", ByteBufferUtils.byteBufferInfo(bb2, false));
//    }
}
