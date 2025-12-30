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
import stroom.util.logging.LogUtil;
import stroom.util.shared.Range;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * See {@link ZstdSegmentOutputStream} for details of the structure of a segmented Zstd file and its
 * seek table.
 */
public class ZstdSegmentUtil {

    private ZstdSegmentUtil() {
    }

    /**
     * The size of just the seek table entries, excluding the frame header and footer.
     */
    public static int calculateSeekTableSize(final int frameCount) {
        // Returning an int, the highest frameCount we can support is ~134mil.
        // Hopefully that will be enough.
        return Math.toIntExact(ZstdConstants.SEEK_TABLE_ENTRY_SIZE * (long) frameCount);
    }

    /**
     * The size of the seek table frame in bytes, including its footer but <strong>EXCLUDING</strong>
     * its header.
     */
    public static int calculateSeekTableFramePayloadSize(final int frameCount) {
        return Math.toIntExact((long) calculateSeekTableSize(frameCount) + ZstdConstants.SEEKABLE_FOOTER_SIZE);
    }

    /**
     * The size of the whole seek table skippable frame including its header and footer.
     */
    public static int calculateSeekTableFrameSize(final int frameCount) {
        return Math.toIntExact(ZstdConstants.SKIPPABLE_FRAME_HEADER_SIZE +
                               (long) calculateSeekTableFramePayloadSize(frameCount));
    }

    /**
     * Create a {@link Range} that covers the whole of a seek table frame that appears at the
     * very end of the file.
     *
     * @param frameCount The number of data frames in the file
     * @param totalSize  The total file size.
     */
    public static Range<Long> createSeekTableFrameRange(final int frameCount,
                                                        final long totalSize) {
        final long seekTableFrameSize = calculateSeekTableFrameSize(frameCount);
        return ZstdSegmentUtil.getLastNRange(totalSize, seekTableFrameSize);
    }

    /**
     * Create a {@link Range} for the last N bytes.
     *
     * @param totalSize The total size of the data.
     * @param rangeSize The required number of bytes at the very end of the data.
     */
    public static Range<Long> getLastNRange(final long totalSize,
                                            final long rangeSize) {
        if (rangeSize > totalSize) {
            throw new IllegalArgumentException(LogUtil.message("rangeSize {} is larger than totalSize {}",
                    rangeSize, totalSize));
        }
        if (rangeSize < 0) {
            throw new IllegalArgumentException(LogUtil.message("rangeSize {} is negative", rangeSize));
        }
        return Range.of(totalSize - rangeSize, totalSize);
    }

    /**
     * Return true if {@link ZstdConstants#SEEKABLE_MAGIC_NUMBER} is found at the very end of
     * compressedBuffer (with the end being its limit).
     *
     * @param compressedBuffer The buffer to test. The buffer does not have to include the whole file, just
     *                         at least the last 4 bytes.
     */
    public static boolean isSeekable(final ByteBuffer compressedBuffer) {
        Objects.requireNonNull(compressedBuffer);
        if (compressedBuffer.remaining() < ZstdConstants.SEEKABLE_MAGIC_NUMBER_SIZE) {
            return false;
        } else {
            final int seekableMagicNumberIdx = compressedBuffer.limit() - ZstdConstants.SEEKABLE_MAGIC_NUMBER_SIZE;
            return ByteBufferUtils.equals(
                    compressedBuffer,
                    seekableMagicNumberIdx,
                    ZstdConstants.SEEKABLE_MAGIC_NUMBER_BUFFER,
                    0,
                    ZstdConstants.SEEKABLE_MAGIC_NUMBER_SIZE);
        }
    }

    public static int getFrameCountRelativePosition() {
        return -ZstdConstants.SEEKABLE_MAGIC_NUMBER_SIZE - 1 -  // The bit field
               Integer.BYTES;

    }

    /**
     * Get the number of frames in a seekable Zstd file.
     *
     * @param compressedBuffer A {@link ByteBuffer} that includes the end of the file.
     */
    public static int getFrameCount(final ByteBuffer compressedBuffer) {
        // Includes the skippable frame
        final int frameCountIdx = compressedBuffer.capacity()
                                  - ZstdConstants.SEEKABLE_MAGIC_NUMBER.length
                                  - 1 // bitfield
                                  - Integer.BYTES;

        final long frameCount = getUnsignedIntLE(compressedBuffer, frameCountIdx);
        return (int) frameCount;
    }

    public static FrameLocation getFrameLocation(final ByteBuffer compressedBuffer, final int frameIdx) {
        final int frameCount = getFrameCount(compressedBuffer);
        final long entryIdx = getSeekTableEntryIndex(compressedBuffer, frameIdx, frameCount);
        final FrameInfo frameInfo = getFrameInfo(compressedBuffer, frameIdx, entryIdx);

        final FrameLocation frameLocation;
        if (frameIdx == 0) {
            frameLocation = new FrameLocation(
                    frameIdx, 0L,
                    (int) frameInfo.cumulativeCompressedSize(),
                    frameInfo.uncompressedSize());
        } else {
            final int prevFrameIdx = frameIdx - 1;
            final long prevEntryIdx = getSeekTableEntryIndex(compressedBuffer, prevFrameIdx, frameCount);
            final FrameInfo prevFrameInfo = getFrameInfo(compressedBuffer, prevFrameIdx, prevEntryIdx);
            final long compressedFrameSize = frameInfo.cumulativeCompressedSize()
                                             - prevFrameInfo.cumulativeCompressedSize();
            frameLocation = new FrameLocation(
                    frameIdx, prevFrameInfo.cumulativeCompressedSize(),
                    compressedFrameSize,
                    frameInfo.uncompressedSize());
        }
        return frameLocation;
    }

    public static FrameInfo getFrameInfo(final ByteBuffer byteBuffer,
                                         final int frameIdx,
                                         final long entryIdx) {
        return new FrameInfo(
                frameIdx,
                getLongLE(byteBuffer, (int) entryIdx),
                getLongLE(byteBuffer, (int) entryIdx + Long.BYTES));
    }

    public static long getSeekTableEntryIndex(final ByteBuffer compressedBuffer,
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
               - ZstdConstants.SEEKABLE_FOOTER_SIZE
               - ((frameCount - frameIdx) * (long) ZstdConstants.SEEK_TABLE_ENTRY_SIZE);
    }

    /**
     * Writes val as a 4-byte unsigned integer in LE order. Clears the buffer, but does not flip it.
     */
    public static void writeLEInteger(final long val,
                                      final ByteBuffer byteBuffer,
                                      final OutputStream outputStream) throws IOException {
        byteBuffer.clear();
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        putUnsignedInt(byteBuffer, val);
        outputStream.write(byteBuffer.array(), 0, Integer.BYTES);
    }

    /**
     * Writes val as an 8-byte long in LE order. Clears the buffer, but does not flip it.
     */
    public static void writeLELong(final long val,
                                   final ByteBuffer byteBuffer,
                                   final OutputStream outputStream) throws IOException {
        byteBuffer.clear();
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putLong(val);
        outputStream.write(byteBuffer.array(), 0, Long.BYTES);
    }

    public static long getUnsignedIntLE(final ByteBuffer byteBuffer, final int index) {
        final ByteBuffer slice = byteBuffer.slice(index, Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        return ((long) slice.getInt(0) & 0xFFFFFFFFL);
    }

    public static long getLongLE(final ByteBuffer byteBuffer, final int index) {
        final ByteBuffer slice = byteBuffer.slice(index, Long.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        return slice.getLong(0);
    }

    public static void putUnsignedInt(final ByteBuffer byteBuffer, final long value) {
        byteBuffer.putInt((int) (value & 0xFFFFFFFFL));
    }

}
