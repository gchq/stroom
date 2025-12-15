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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SegmentedZstdUtil {

    private SegmentedZstdUtil() {
    }

    /**
     * The size of the seek table frame in bytes, including its footer but <strong>excluding</strong>
     * its header.
     */
    public static int calculateFramePayloadSize(final int frameCount) {
        return (ZstdConstants.SEEK_TABLE_ENTRY_BYTES * frameCount)
               + ZstdConstants.SEEKABLE_FOOTER_BYTES;
    }

    public static boolean isSeekable(final ByteBuffer compressedBuffer) {
        // Includes the skippable frame
        final int totalCompressedSize = compressedBuffer.capacity();
        final int len = ZstdConstants.SEEKABLE_MAGIC_NUMBER.length;
        return ByteBufferUtils.equals(
                compressedBuffer,
                totalCompressedSize - len,
                ZstdConstants.SEEKABLE_MAGIC_NUMBER_BUFFER,
                0,
                len);
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
                    0L,
                    (int) frameInfo.cumulativeCompressedSize(),
                    frameInfo.uncompressedSize());
        } else {
            final int prevFrameIdx = frameIdx - 1;
            final long prevEntryIdx = getSeekTableEntryIndex(compressedBuffer, prevFrameIdx, frameCount);
            final FrameInfo prevFrameInfo = getFrameInfo(compressedBuffer, prevFrameIdx, prevEntryIdx);
            final long compressedFrameSize = frameInfo.cumulativeCompressedSize()
                                             - prevFrameInfo.cumulativeCompressedSize();
            frameLocation = new FrameLocation(
                    prevFrameInfo.cumulativeCompressedSize(),
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
               - ZstdConstants.SEEKABLE_FOOTER_BYTES
               - ((frameCount - frameIdx) * (long) ZstdConstants.SEEK_TABLE_ENTRY_BYTES);
    }

    public static void writeLEInteger(final long val,
                                      final ByteBuffer fourByteBuffer,
                                      final OutputStream outputStream) throws IOException {
        fourByteBuffer.clear();
        fourByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        putUnsignedInt(fourByteBuffer, val);
        fourByteBuffer.flip();
        outputStream.write(fourByteBuffer.array());
    }

    public static void writeLELong(final long val,
                                   final ByteBuffer eightByteBuffer,
                                   final OutputStream outputStream) throws IOException {
        eightByteBuffer.clear();
        eightByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        eightByteBuffer.putLong(val);
        eightByteBuffer.flip();
        outputStream.write(eightByteBuffer.array());
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
