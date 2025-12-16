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
import stroom.util.io.NoCloseOutputStream;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.github.luben.zstd.ZstdOutputStream;
import com.google.common.io.CountingOutputStream;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A {@link SegmentOutputStream} for writing data such that each segment will be compressed
 * as a separate Zstd compressed frame so that a single segment can be decompressed and read
 * in isolation. The entire stream can be decompressed as a whole, disregarding the frames (segments),
 * so any standard Zstd binary/library will be able to decompress a file without any knowledge
 * of the frames. Not thread safe.
 * <p>
 * The data is optionally compressed using a supplied dictionary that has been created from
 * a set of training data. If no dictionary is supplied then data is compressed without a dictionary
 * and thus will result in poor compression rates given the potentially small segment sizes.
 * </p>
 * <p>
 * {@link SegmentedZstdOutputStream} only supports on level of segmenting, e.g. segmenting
 * on raw stream parts, or on cooked stream records, not both.
 * </p>
 * <pre>
 * < segment 0 > < segment 1> < segment 2> < segment 3> < seek table >
 *              ▲️            ▲️            ▲️
 *           boundary     boundary     boundary
 * </pre>
 * <p>
 * Each segment is a zstd compressed frame.
 * </p>
 *
 * <p>
 * The seek table structure is like this. Each compressed frame has a corresponding 16 byte.
 * The example is for a stream containing 4 compressed frames (segments).
 * {@code CCCCCCCCUUUUUUUU} block.
 * </p>
 * <pre>
 * m == skippable frame magic number header (4 byte)
 * s == size of skippable frame (4 byte int)
 * C == cumulativeCompressedSize (8 byte long)
 * U == uncompressedSize (8 byte long)
 * F == frameCount (4 byte int)               ∖
 * B == bitfield (1 byte)                     | - Footer
 * M == seek table magic number (4 byte int)  /
 * Frames:                      0               1               2               3
 * < compressed frames >mmmmssssCCCCCCCCUUUUUUUUCCCCCCCCUUUUUUUUCCCCCCCCUUUUUUUUCCCCCCCCUUUUUUUUFFFFBMMMM
 * 0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
 * 0         1         2         3         4         5         6         7         8         9
 * </pre>
 * <p>
 * The seek table format is derived from the
 * <a href="https://github.com/facebook/zstd/blob/dev/contrib/seekable_format/zstd_seekable_compression_format.md">
 * Zstd Seekable Format. It differs from it in the following ways:
 * <ul>
 * <li>Use 8 bytes instead of 4 for cumulativeCompressedSize and uncompressedSize to allow for very large
 * streams</li>
 * <li>We don't use the checksums</li>
 * </ul>
 * </a>
 * </p>
 */
public class SegmentedZstdOutputStream extends SegmentOutputStream {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SegmentedZstdOutputStream.class);

    //    public static final ByteBuffer SEEKABLE_MAGIC_NUMBER_BUFFER = ByteBuffer.wrap(SEEKABLE_MAGIC_NUMBER);
    public static final int DEFAULT_COMPRESSION_LEVEL = 5;

    private final OutputStream dataOutputStream;
    private final ZstdDictionary zstdDictionary;
    private final int compressionLevel;
    private final CountingOutputStream compressedBytesCountingOutputStream;
    private final List<FrameInfo> frameInfoList = new ArrayList<>();
    private final byte[] fourByteArray = new byte[Integer.BYTES];
    private final ByteBuffer fourByteBuffer = ByteBuffer.wrap(fourByteArray);
    private final byte[] eightByteArray = new byte[Long.BYTES];
    private final ByteBuffer eightByteBuffer = ByteBuffer.wrap(eightByteArray);

    private ZstdOutputStream zstdOutputStream = null;

    private int currentSegmentIndex = 0;
    // Tracks the un-compressed bytes written to the stream. This is the index that will
    // be written to next.
    private long position = 0;
    // Tracks the position of the start index of the current segment, in un-compressed terms.
    private long lastBoundary = 0;

    public SegmentedZstdOutputStream(final OutputStream dataOutputStream,
                                     final ZstdDictionary zstdDictionary) {
        this(dataOutputStream, zstdDictionary, DEFAULT_COMPRESSION_LEVEL);
    }

    public SegmentedZstdOutputStream(final OutputStream dataOutputStream) {
        this(dataOutputStream, null, DEFAULT_COMPRESSION_LEVEL);
    }

    public SegmentedZstdOutputStream(final OutputStream dataOutputStream,
                                     final ZstdDictionary zstdDictionary,
                                     final int compressionLevel) {
        Objects.requireNonNull(dataOutputStream);
        validateCompressionLevel(compressionLevel);

        this.dataOutputStream = dataOutputStream;
        // Wrap the delegate dataOutputStream in a NoCloseOutputStream so that we can close the ZstdOutputStream
        // without closing the underlying output streams.
        this.compressedBytesCountingOutputStream = new CountingOutputStream(new NoCloseOutputStream(dataOutputStream));
        this.compressionLevel = compressionLevel;
        this.zstdDictionary = zstdDictionary;
    }

    private void validateCompressionLevel(final int compressionLevel) {
        if (compressionLevel < 1 || compressionLevel > 22) {
            throw new IllegalArgumentException("Compression level must be between 1 and 22");
        }
    }

    private ZstdOutputStream createZstdOutputStream() {
        try {
            // Tracks the count of compressed bytes written
            final ZstdOutputStream zstdOutputStream = new ZstdOutputStream(compressedBytesCountingOutputStream);
            // We may not have a dict if this is the first stream and thus have not had
            // a chance to create a dict from training data yet.
            if (zstdDictionary != null) {
                zstdOutputStream.setDict(zstdDictionary.getDictionaryBytes());
            }
            zstdOutputStream.setCloseFrameOnFlush(false);
            zstdOutputStream.setLevel(compressionLevel);
            return zstdOutputStream;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void addSegment() throws IOException {
        closeSegment();
        zstdOutputStream = createZstdOutputStream();
    }

    private void closeSegment() throws IOException {
        // Ensure all uncompressed bytes are compressed and the frame closed
        zstdOutputStream.flush();
        zstdOutputStream.close(); // This won't close its delegate, compressedBytesCountingOutputStream
        compressedBytesCountingOutputStream.flush();
        zstdOutputStream = null;

        final long segmentUncompressedSize = position - lastBoundary;
        if (segmentUncompressedSize > 0) {
            final FrameInfo frameInfo = new FrameInfo(
                    currentSegmentIndex,
                    compressedBytesCountingOutputStream.getCount(),
                    segmentUncompressedSize);
            frameInfoList.add(frameInfo);
            LOGGER.debug("closeSegment() - adding frameInfo {}, segmentUncompressedSize: {}, " +
                         "currentSegmentIndex: {}, lastBoundary: {}, position: {}",
                    frameInfo, segmentUncompressedSize, currentSegmentIndex, lastBoundary, position);
            currentSegmentIndex++;
        }
        // Reset lastBoundary to the next segment
        lastBoundary = position;
    }

    private void writeSeekTable() throws IOException {
        // Format of a generic skippable frame is
        // <4 byte magic number><4 byte size of payload (LE)><payload>

        // Write the magic number that identifies this frame to Zstd as a skippable one
        compressedBytesCountingOutputStream.write(ZstdConstants.SKIPPABLE_FRAME_HEADER);

        // Determine how big the seek table is (including footer)
        final int frameCount = frameInfoList.size();
        final int framePayloadSize = SegmentedZstdUtil.calculateFramePayloadSize(frameCount);
        LOGGER.debug("writeSeekTable() - frameCount: {}, framePayloadSize: {}", frameCount, framePayloadSize);
        // Write the payload size so Zstd knows how far to skip
        writeLEInteger(framePayloadSize, compressedBytesCountingOutputStream);

        // Write one entry describing each frame
        for (final FrameInfo frameInfo : frameInfoList) {
            writeLELong(frameInfo.cumulativeCompressedSize(), compressedBytesCountingOutputStream);
            writeLELong(frameInfo.uncompressedSize(), compressedBytesCountingOutputStream);
        }

        // Now write the seek table frame footer

        // Frame count, so know how big our seek table is
        writeLEInteger(frameInfoList.size(), compressedBytesCountingOutputStream);
        // Seek table descriptor bitfield
        // We currently don't use any bits in this, but may as well keep it in case
        // we find a use for some of them.
        compressedBytesCountingOutputStream.write((byte) 0);
        // Seekable magic number, so we can look at the last 4 bytes of a zst file and determine
        // that it is seekable
        compressedBytesCountingOutputStream.write(ZstdConstants.SEEKABLE_MAGIC_NUMBER);
    }

    private void writeLEInteger(final long val, final OutputStream outputStream) throws IOException {
        SegmentedZstdUtil.writeLEInteger(val, fourByteBuffer, outputStream);
    }

    private void writeLELong(final long val, final OutputStream outputStream) throws IOException {
        SegmentedZstdUtil.writeLELong(val, eightByteBuffer, outputStream);
    }

    @Override
    public void addSegment(final long position) throws IOException {
        throw new UnsupportedOperationException("addSegment(position) is not supported");
    }

    @Override
    public long getPosition() {
        return position;
    }

    @Override
    public void write(final byte @NonNull [] b, final int off, final int len) throws IOException {
        zstdOutputStream = Objects.requireNonNullElseGet(zstdOutputStream, this::createZstdOutputStream);
        zstdOutputStream.write(b, off, len);
        position += len;
    }

    @Override
    public void write(final byte @NonNull [] b) throws IOException {
        zstdOutputStream = Objects.requireNonNullElseGet(zstdOutputStream, this::createZstdOutputStream);
        zstdOutputStream.write(b);
        position += b.length;
    }

    @Override
    public void write(final int b) throws IOException {
        zstdOutputStream = Objects.requireNonNullElseGet(zstdOutputStream, this::createZstdOutputStream);
        zstdOutputStream.write(b);
        position++;
    }

    public ZstdDictionary getZstdDictionary() {
        return zstdDictionary;
    }

    public boolean hasZstdDictionary() {
        return zstdDictionary != null;
    }

    public int getCompressionLevel() {
        return compressionLevel;
    }

    @Override
    public void close() throws IOException {
        closeSegment();
        // Write the segment info to the delegate stream
        writeSeekTable();
        compressedBytesCountingOutputStream.flush();
        // Close the underlying
        dataOutputStream.close();
    }

    @Override
    public String toString() {
        return "SegmentedZstdOutputStream{" +
               "compressionLevel=" + compressionLevel +
               ", position=" + position +
               ", lastBoundary=" + lastBoundary +
               ", zstdDictionary=" + zstdDictionary +
               '}';
    }
}
