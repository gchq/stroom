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
import stroom.util.io.IgnoreCloseOutputStream;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import com.github.luben.zstd.ZstdDictCompress;
import com.github.luben.zstd.ZstdOutputStream;
import com.google.common.io.CountingOutputStream;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
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
 * {@link ZstdSegmentOutputStream} only supports on level of segmenting, e.g. segmenting
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
 * The example is for a stream containing 3 compressed frames (segments/frame index 0-2)
 * and 1 skippable frame (frame index 3).
 * {@code CCCCCCCCUUUUUUUU} block.
 * </p>
 * <pre>
 * ============ HEADER ============
 * m == skippable frame magic number header (4 byte)
 * s == size of skippable frame (4 byte int)
 * ============ ENTRIES ============
 * C == cumulativeCompressedSize (8 byte long)
 * U == uncompressedSize (8 byte long)
 * ============ FOOTER ============
 * I == Dictionary UUID (most significant bits LE)
 * i == Dictionary UUID (least significant bits LE)
 * F == frameCount (4 byte int)
 * B == bitfield (1 byte)
 * M == seek table magic number (4 byte int)
 *
 * FrameIdx:                    0               1               2
 * < compressed frames >mmmmssssCCCCCCCCUUUUUUUUCCCCCCCCUUUUUUUUCCCCCCCCUUUUUUUUIIIIIIIIiiiiiiiiFFFFBMMMM
 * 012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901
 * 0         1         2         3         4         5         6         7         8         9
 * </pre>
 * <p>
 * The seek table format is derived from the
 * <a href="https://github.com/facebook/zstd/blob/dev/contrib/seekable_format/zstd_seekable_compression_format.md">
 * Zstd Seekable Format. Stroom's implementation of it differs in the following ways:
 * <ul>
 * <li>Use 8 bytes instead of 4 for cumulativeCompressedSize and uncompressedSize to allow for very large
 * streams</li>
 * <li>We don't use the checksums</li>
 * <li>We have added the dictionary UUID to the footer</li>
 * </ul>
 * </a>
 * </p>
 */
public class ZstdSegmentOutputStream extends SegmentOutputStream {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ZstdSegmentOutputStream.class);

    //    public static final ByteBuffer SEEKABLE_MAGIC_NUMBER_BUFFER = ByteBuffer.wrap(SEEKABLE_MAGIC_NUMBER);
    public static final int DEFAULT_COMPRESSION_LEVEL = 5;

    private final OutputStream dataOutputStream;
    private final ZstdDictionary zstdDictionary;
    private final ZstdDictCompress zstdDictCompress;
    private final int compressionLevel;
    private final CountingOutputStream compressedBytesCountingOutputStream;
    private final List<FrameInfo> frameInfoList = new ArrayList<>();
    private final HeapBufferPool heapBufferPool;

    private ZstdOutputStream zstdOutputStream = null;

    private int currentSegmentIndex = 0;
    // Tracks the un-compressed bytes written to the stream. This is the index that will
    // be written to next.
    private long position = 0;
    // Tracks the position of the start index of the current segment, in un-compressed terms.
    private long lastBoundary = 0;
    private boolean hasWrites = false;

    public ZstdSegmentOutputStream(final OutputStream dataOutputStream) {
        this(dataOutputStream, null, null, DEFAULT_COMPRESSION_LEVEL);
    }

    public ZstdSegmentOutputStream(final OutputStream dataOutputStream,
                                   final ZstdDictionary zstdDictionary) {
        this(dataOutputStream, zstdDictionary, null, DEFAULT_COMPRESSION_LEVEL);
    }

    public ZstdSegmentOutputStream(final OutputStream dataOutputStream,
                                   final ZstdDictionary zstdDictionary,
                                   final HeapBufferPool heapBufferPool,
                                   final int compressionLevel) {
        Objects.requireNonNull(dataOutputStream);
        validateCompressionLevel(compressionLevel);

        this.heapBufferPool = heapBufferPool;
        this.dataOutputStream = dataOutputStream;
        // Wrap the delegate dataOutputStream in a IgnoreCloseOutputStream so that we can
        // close the ZstdOutputStream without closing the underlying output streams.
        this.compressedBytesCountingOutputStream = new CountingOutputStream(
                new IgnoreCloseOutputStream(dataOutputStream));
        this.compressionLevel = compressionLevel;
        this.zstdDictionary = zstdDictionary;
        this.zstdDictCompress = NullSafe.get(
                zstdDictionary,
                dict -> new ZstdDictCompress(dict.getDictionaryBytes(), compressionLevel));
    }

    private void validateCompressionLevel(final int compressionLevel) {
        if (compressionLevel < 1 || compressionLevel > 22) {
            throw new IllegalArgumentException("Compression level must be between 1 and 22");
        }
    }

    private ZstdOutputStream createZstdOutputStream() {
        try {
            // Tracks the count of compressed bytes written
            final ZstdOutputStream zstdOutputStream = heapBufferPool != null
                    ? new ZstdOutputStream(compressedBytesCountingOutputStream, heapBufferPool)
                    : new ZstdOutputStream(compressedBytesCountingOutputStream);

            // We may not have a dict if this is the first stream and thus have not had
            // a chance to create a dict from training data yet.
            if (zstdDictCompress != null) {
                zstdOutputStream.setDict(zstdDictCompress);
            }
            // We are using one stream per frame/segment, so we have full control of the writing
//            zstdOutputStream.setCloseFrameOnFlush(false);
            zstdOutputStream.setLevel(compressionLevel);
            return zstdOutputStream;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void addSegment() throws IOException {
        hasWrites = true;
        closeSegment();
        zstdOutputStream = createZstdOutputStream();
    }

    @Override
    public void addSegment(final long position) throws IOException {
        // Method doesn't appear to be used anyway, so just throw
        throw new UnsupportedOperationException("addSegment(position) is not supported");
    }


    private void closeSegment() throws IOException {
        // Ensure all uncompressed bytes are compressed and the frame closed
        if (zstdOutputStream != null) {
            if (position == lastBoundary) {
                // No data has been written, so don't flush the zstdOutputStream else we will get an
                // empty Zstd frame with just a header (9 bytes).
                // This does mean a mismatch between the frames that Zstd knows about and the frames described
                // in the seek table, but if we have a lot of empty segments it means we don't waste space.
                // It does however mean that we must use our seek table as the source of truth.
                LOGGER.debug("No data written, won't write Zstd frame. position: {}, currentSegmentIndex: {}",
                        position, currentSegmentIndex);
            } else {
                zstdOutputStream.flush();
                zstdOutputStream.close(); // This won't close its delegate, compressedBytesCountingOutputStream
                compressedBytesCountingOutputStream.flush();
            }
            zstdOutputStream = null;
        }

        final long segmentUncompressedSize = position - lastBoundary;
        // Even if no data has been written to the stream, we still record the frame
        // as Zstd will write a header for the frame (about 9 bytes) so that it knows a frame
        // is there.
        final FrameInfo frameInfo = new FrameInfo(
                currentSegmentIndex,
                compressedBytesCountingOutputStream.getCount(),
                segmentUncompressedSize);
        frameInfoList.add(frameInfo);
        LOGGER.trace("closeSegment() - adding frameInfo {}, segmentUncompressedSize: {}, " +
                     "currentSegmentIndex: {}, lastBoundary: {}, position: {}",
                frameInfo, segmentUncompressedSize, currentSegmentIndex, lastBoundary, position);
        currentSegmentIndex++;
        // Reset lastBoundary to the next segment
        lastBoundary = position;
    }

    @Override
    public long getPosition() {
        return position;
    }

    @Override
    public void write(final byte @NonNull [] b, final int off, final int len) throws IOException {
        hasWrites = true;
        zstdOutputStream = Objects.requireNonNullElseGet(zstdOutputStream, this::createZstdOutputStream);
        zstdOutputStream.write(b, off, len);
        position += len;
    }

    @Override
    public void write(final byte @NonNull [] b) throws IOException {
        hasWrites = true;
        zstdOutputStream = Objects.requireNonNullElseGet(zstdOutputStream, this::createZstdOutputStream);
        zstdOutputStream.write(b);
        position += b.length;
    }

    @Override
    public void write(final int b) throws IOException {
        hasWrites = true;
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
        // Even if we only have one frame we still need to write the seek table so that
        // we have the dict uuid in there to decompress it.
        if (hasWrites && !frameInfoList.isEmpty()) {
            ZstdSeekTable.writeSeekTable(
                    compressedBytesCountingOutputStream,
                    frameInfoList,
                    zstdDictionary,
                    heapBufferPool);
        }
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
