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


import stroom.bytebuffer.ByteBufferUtils;
import stroom.util.UuidUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;

import it.unimi.dsi.fastutil.ints.IntBidirectionalIterator;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 *
 * Encapsulates the seek table skippable frame found at the end of the data produced
 * by {@link ZstdSegmentOutputStream}.
 * <p>
 * The seek table provides the means to locate and decompress individual frames/segments.
 * </p>
 * <p>
 * For details of the format of the compressed file and its skippable frame,
 * see {@link ZstdSegmentOutputStream}.
 * </p>
 */
@NullMarked
public class ZstdSeekTable implements Iterable<FrameLocation> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ZstdSeekTable.class);

    private static final int VALUE_BUFFER_CAPACITY = Long.BYTES;
    private static final UUID NO_DICTIONARY_UUID = ZstdConstants.ZERO_UUID;
    private static final byte[] NO_DICTIONARY_UUID_BYTES = UuidUtil.toByteArray(NO_DICTIONARY_UUID);

    static final ZstdSeekTable EMPTY = new ZstdSeekTable(
            NO_DICTIONARY_UUID,
            0,
            ByteBuffer.wrap(new byte[0]));

    private final UUID dictionaryUuid;
    private final int frameCount;
    /**
     * Holds all the entries as a series of pairs, where each pair has this format:
     * <pre>
     * < cumulativeCompressedSize 8 byte LE > < uncompressedSize 8 byte LE >
     * </pre>
     * All operations on this buffer should be absolute so as not to modify its postion/limit
     */
    private final ByteBuffer seekTableEntriesBuffer;

//    private ZstdSeekTable(final int frameCount, final byte[] seekTableEntries) {
//        this.frameCount = frameCount;
//        if (frameCount < 0) {
//            throw new IllegalArgumentException(LogUtil.message("Expecting frameCount {} to be >=0", frameCount));
//        }
//        Objects.requireNonNull(seekTableEntries);
//        if (seekTableEntries.length % ZstdConstants.SEEK_TABLE_ENTRY_SIZE != 0) {
//            throw new IllegalArgumentException(LogUtil.message(
//                    "Expecting seekTableEntries.length {} to be a multiple of {}",
//                    seekTableEntries.length, ZstdConstants.SEEK_TABLE_ENTRY_SIZE));
//        }
//        this.seekTableEntriesBuffer = ByteBuffer.wrap(seekTableEntries);
//    }

    private ZstdSeekTable(final UUID dictionaryUuid,
                          final int frameCount,
                          final ByteBuffer seekTableEntries) {
        this.dictionaryUuid = Objects.requireNonNull(dictionaryUuid);
        this.frameCount = frameCount;
        if (frameCount < 0) {
            throw new IllegalArgumentException(LogUtil.message("Expecting frameCount {} to be >=0", frameCount));
        }
        Objects.requireNonNull(seekTableEntries);
        if (seekTableEntries.remaining() % ZstdConstants.SEEK_TABLE_ENTRY_SIZE != 0) {
            throw new IllegalArgumentException(LogUtil.message(
                    "Expecting seekTableEntries.remaining {} to be a multiple of {}",
                    seekTableEntries.remaining(), ZstdConstants.SEEK_TABLE_ENTRY_SIZE));
        }
        this.seekTableEntriesBuffer = seekTableEntries;
    }

    public static Optional<ZstdSeekTable> parse(final ByteBuffer byteBuffer) {
        return parse(byteBuffer, true);
    }

    /**
     * @param byteBuffer         The {@link ByteBuffer} to parse the seek table from.
     * @param copyBufferContents If true, the seek table part of byteBuffer will be copied.
     * @return A new {@link ZstdSeekTable} instance.
     * @throws InsufficientSeekTableDataException If byteBuffer is not large enough to include all the
     *                                            seek table skippable frame.
     * @throws InvalidSeekTableDataException      For any error parsing the {@link ZstdSeekTable} from byteBuffer.
     */
    public static Optional<ZstdSeekTable> parse(final ByteBuffer byteBuffer,
                                                final boolean copyBufferContents) {
        Objects.requireNonNull(byteBuffer);
        final Optional<ZstdSeekTable> optZstdSeekTable;
        if (!ZstdSegmentUtil.isSeekable(byteBuffer)) {
            // May just be a bog-standard zstd file with no frames and therefore no seek table frame.
            LOGGER.debug("parse() - No seekable magic number");
            optZstdSeekTable = Optional.empty();
        } else {
            // If byteBuffer doesn't cover all the seek table frame we should at least have enough to
            // read the frame count so the call can some back with the correct amount of data to parse.
            final int frameCount = readFrameCount(byteBuffer);

            if (frameCount == 0) {
                LOGGER.debug("parse() - No frames");
                optZstdSeekTable = Optional.of(EMPTY);
            } else {
                final UUID dictionaryUuid = readDictionaryUuid(byteBuffer);
                final int seekTableFrameSize = ZstdSegmentUtil.calculateSeekTableFrameSize(frameCount);
                try {
                    final int dataLength = byteBuffer.remaining();
                    if (dataLength < seekTableFrameSize) {
                        throw new InsufficientSeekTableDataException(seekTableFrameSize, dataLength);
                    }

                    checkIsSkippableFrame(byteBuffer, seekTableFrameSize);
                    final int seekTableSize = ZstdSegmentUtil.calculateSeekTableSize(frameCount);
                    // This is the table + footer
                    final int seekTablePayloadSize = ZstdSegmentUtil.calculateSeekTableFramePayloadSize(frameCount);
                    final int seekTablePosition = byteBuffer.limit()
                                                  - seekTablePayloadSize;
                    // Slice just the table part
                    final ByteBuffer seekTableBuffer = byteBuffer.slice(seekTablePosition, seekTableSize);
                    if (copyBufferContents) {
                        final byte[] seekTableEntries = new byte[seekTableSize];
                        final ByteBuffer seekTableBufferCopy = ByteBuffer.wrap(seekTableEntries);
                        ByteBufferUtils.copy(seekTableBuffer, seekTableBufferCopy);
                        optZstdSeekTable = Optional.of(new ZstdSeekTable(
                                dictionaryUuid, frameCount, seekTableBufferCopy));
                    } else {
                        optZstdSeekTable = Optional.of(new ZstdSeekTable(
                                dictionaryUuid, frameCount, seekTableBuffer));
                    }
                    LOGGER.debug(() -> LogUtil.message("parse() - frameCount: {}, seekTableFrameSize",
                            frameCount, seekTableFrameSize));
                } catch (final InsufficientSeekTableDataException | InvalidSeekTableDataException e) {
                    throw e;
                } catch (final RuntimeException e) {
                    throw new InvalidSeekTableDataException(LogUtil.message(
                            "Error parsing ZstdSeekTable with frameCount: {} - {}",
                            frameCount, LogUtil.exceptionMessage(e)), e);
                }
            }
        }
        return optZstdSeekTable;
    }

    /**
     * Writes the frameInfoList as a complete seek table to outputStream.
     *
     * @param outputStream   The {@link OutputStream} to write to.
     * @param frameInfoList  The details of the frames already written to outputStream.
     * @param zstdDictionary The dictionary that is being used to compress the associated data.
     * @param heapBufferPool A buffer pool if available, can be null.
     */
    public static void writeSeekTable(final OutputStream outputStream,
                                      final List<FrameInfo> frameInfoList,
                                      final ZstdDictionary zstdDictionary,
                                      final HeapBufferPool heapBufferPool) throws IOException {
        // Format of a generic skippable frame is
        // <4 byte magic number><4 byte size of payload (LE)><payload>

        // --- Seek table frame header ---

        // Write the magic number that identifies this frame to Zstd as a skippable one
        outputStream.write(ZstdConstants.SKIPPABLE_FRAME_MAGIC_NUMBER);

        final ByteBuffer valueBuffer = getHeapByteBuffer(heapBufferPool);

        try {
            // Determine how big the seek table is (including footer)
            final int frameCount = frameInfoList.size();
            final int framePayloadSize = ZstdSegmentUtil.calculateSeekTableFramePayloadSize(frameCount);
            LOGGER.debug("writeSeekTable() - frameCount: {}, framePayloadSize: {}", frameCount, framePayloadSize);
            // Write the payload size so Zstd knows how far to skip
            writeLEInteger(framePayloadSize, outputStream, valueBuffer);

            // --- Seek table frame entries ---

            // Write one entry describing each frame
            for (final FrameInfo frameInfo : frameInfoList) {
                writeLELong(frameInfo.cumulativeCompressedSize(), outputStream, valueBuffer);
                writeLELong(frameInfo.uncompressedSize(), outputStream, valueBuffer);
            }

            // --- Seek table frame footer ---

            // Write the UUID of the dict used by this outputStream. Writes all zeros if there is no dict
            // so that we have a fixed width footer.
            writeDictionaryUuid(outputStream, zstdDictionary);
            // Frame count, so know how big our seek table is
            writeLEInteger(frameInfoList.size(), outputStream, valueBuffer);
            // Seek table descriptor bitfield
            // We currently don't use any bits in this, but may as well keep it in case
            // we find a use for some of them.
            outputStream.write((byte) 0);
            // Seekable magic number, so we can look at the last 4 bytes of a zst file and determine
            // that it is seekable
            outputStream.write(ZstdConstants.SEEKABLE_MAGIC_NUMBER);
        } finally {
            NullSafe.consume(heapBufferPool, pool -> pool.release(valueBuffer));
        }
    }

    private static ByteBuffer getHeapByteBuffer(final HeapBufferPool heapBufferPool) {
        return NullSafe.getOrElseGet(
                heapBufferPool,
                pool -> pool.get(VALUE_BUFFER_CAPACITY),
                () -> ByteBuffer.allocate(VALUE_BUFFER_CAPACITY));
    }

    private static void writeDictionaryUuid(final OutputStream outputStream,
                                            final ZstdDictionary zstdDictionary) throws IOException {
        final byte[] dictUuidBytes = NullSafe.getOrElse(
                zstdDictionary,
                ZstdDictionary::getUuidBytes,
                NO_DICTIONARY_UUID_BYTES);
        outputStream.write(dictUuidBytes);
    }

    private static void writeLEInteger(final long val,
                                       final OutputStream outputStream,
                                       final ByteBuffer byteBuffer) throws IOException {
        ZstdSegmentUtil.writeLEInteger(val, byteBuffer, outputStream);
    }

    private static void writeLELong(final long val,
                                    final OutputStream outputStream,
                                    final ByteBuffer byteBuffer) throws IOException {
        ZstdSegmentUtil.writeLELong(val, byteBuffer, outputStream);
    }

    private static void checkIsSkippableFrame(final ByteBuffer byteBuffer, final long seekTableFrameSize) {
        final int skippableMagicNumberOffset = Math.toIntExact(byteBuffer.limit() - seekTableFrameSize);
        final boolean isSkippableFrame = ByteBufferUtils.equals(
                ZstdConstants.SKIPPABLE_FRAME_MAGIC_NUMBER_BUFFER,
                0,
                byteBuffer,
                skippableMagicNumberOffset,
                ZstdConstants.SKIPPABLE_FRAME_MAGIC_NUMBER_SIZE);
        if (!isSkippableFrame) {
            throw new InvalidSeekTableDataException("Skippable frame magic number not found at position "
                                                    + skippableMagicNumberOffset);
        }
    }

//    private static void checkIsSeekableFile(final ByteBuffer byteBuffer) {
//        final boolean isSeekableFile = isIsSeekableFile(byteBuffer);
//        if (!isSeekableFile) {
//            throw new InvalidSeekTableDataException("Seekable magic number not found at end of file");
//        }
//    }
//
//    private static boolean isIsSeekableFile(final ByteBuffer byteBuffer) {
//        return ByteBufferUtils.equals(ZstdConstants.SEEKABLE_MAGIC_NUMBER_BUFFER,
//                0,
//                byteBuffer,
//                byteBuffer.limit() - ZstdConstants.SEEKABLE_MAGIC_NUMBER_SIZE,
//                ZstdConstants.SEEKABLE_MAGIC_NUMBER_SIZE);
//    }

    private static int readFrameCount(final ByteBuffer byteBuffer) {
        final int frameCountPosition = byteBuffer.limit() + ZstdConstants.FRAME_COUNT_RELATIVE_POSITION;
        try {
            // Frame count is an unsigned int. If we have so many frames that we overflow an int then we
            // probably have bigger issues as all the ByteBuffer and byte[] api are int based for positions.
            return Math.toIntExact(ZstdSegmentUtil.getUnsignedIntLE(byteBuffer, frameCountPosition));
        } catch (final Exception e) {
            throw new InvalidSeekTableDataException(LogUtil.message(
                    "Error reading frameCount from position {} - {}",
                    frameCountPosition, LogUtil.exceptionMessage(e)), e);
        }
    }

    private static UUID readDictionaryUuid(final ByteBuffer byteBuffer) {
        final int dictUuidPosition = byteBuffer.limit() + ZstdConstants.DICTIONARY_UUID_RELATIVE_POSITION;
        try {
            // Frame count is an unsigned int. If we have so many frames that we overflow an int then we
            // probably have bigger issues as all the ByteBuffer and byte[] api are int based for positions.
            return UuidUtil.readUuid(byteBuffer, dictUuidPosition);
        } catch (final Exception e) {
            throw new InvalidSeekTableDataException(LogUtil.message(
                    "Error reading dictionaryUuid from position {} - {}",
                    dictUuidPosition, LogUtil.exceptionMessage(e)), e);
        }
    }

    /**
     * @return True if there are no compressed data frames/segments.
     */
    public boolean isEmpty() {
        return frameCount == 0;
    }

    /**
     * @return The number of compressed data frames.
     */
    public int getFrameCount() {
        return frameCount;
    }

    /**
     * @return The UUID of the dictionary that compressed the frames that this {@link ZstdSeekTable} describes.
     * The dictionary is applicable to ALL frames.
     */
    public Optional<UUID> getDictionaryUuid() {
        return hasDictionary()
                ? Optional.of(dictionaryUuid)
                : Optional.empty();
    }

    /**
     * @return True if a dictionary was used to compress the data frames.
     */
    public boolean hasDictionary() {
        return !NO_DICTIONARY_UUID.equals(dictionaryUuid);
    }

    /**
     * The location of the frame/segment corresponding to the frameIdx.
     *
     * @param frameIdx The frame/segment index (zero based).
     */
    public FrameLocation getFrameLocation(final int frameIdx) {
        if (frameIdx < 0 || frameIdx >= frameCount) {
            throw new IllegalArgumentException(LogUtil.message("Invalid frameIdx {} for frameCount: {}.",
                    frameIdx, frameCount));
        }
        final int entryIdx = getEntryIdx(frameIdx);
        final FrameInfo frameInfo = ZstdSegmentUtil.getFrameInfo(seekTableEntriesBuffer, frameIdx, entryIdx);

        final FrameLocation frameLocation;
        if (frameIdx == 0) {
            frameLocation = new FrameLocation(
                    frameIdx, 0L,
                    (int) frameInfo.cumulativeCompressedSize(),
                    frameInfo.uncompressedSize());
        } else {
            final int prevFrameIdx = frameIdx - 1;
            final long prevEntryIdx = getEntryIdx(prevFrameIdx);
            final FrameInfo prevFrameInfo = ZstdSegmentUtil.getFrameInfo(
                    seekTableEntriesBuffer, prevFrameIdx, prevEntryIdx);
            final long compressedFrameSize = frameInfo.cumulativeCompressedSize()
                                             - prevFrameInfo.cumulativeCompressedSize();
            frameLocation = new FrameLocation(
                    frameIdx,
                    prevFrameInfo.cumulativeCompressedSize(),
                    compressedFrameSize,
                    frameInfo.uncompressedSize());
        }
        LOGGER.debug("getFrameLocation() - frameIdx {}, frameLocation: {}", frameIdx, frameLocation);
        return frameLocation;
    }

    /**
     * @return The total size of the compressed data frames, excluding the seek table frame.
     */
    public long getTotalCompressedDataSize() {
        final int cumCompressedValueIdx = getEntryIdx(frameCount - 1);
        return ZstdSegmentUtil.getLongLE(seekTableEntriesBuffer, cumCompressedValueIdx);
    }

    /**
     * @return The total size of the file including all data frames and this seek table frame.
     */
    public long getCompressedFileSize() {
        final int seekTableFrameSize = ZstdSegmentUtil.calculateSeekTableFrameSize(frameCount);
        return getTotalCompressedDataSize() + seekTableFrameSize;
    }

    /**
     * @return The total size in bytes of all the compressed frames once uncompressed.
     * This does NOT include the skippable seek table frame.
     */
    public long getTotalUncompressedSize() {
        final ByteBuffer buffer = seekTableEntriesBuffer.slice();
        long total = 0L;
        for (int i = 0; i < buffer.limit(); i += 16) {
            total += ZstdSegmentUtil.getLongLE(buffer, i + Long.BYTES);
        }
        return total;
    }

    /**
     * @param frameIdxSet The set of frame indexes to include
     * @return The total size in bytes of all the compressed frames once uncompressed.
     * This does NOT include the skippable seek table frame.
     */
    public long getTotalUncompressedSize(final IntSet frameIdxSet) {
        if (NullSafe.isEmptyCollection(frameIdxSet)) {
            return 0L;
        } else {
            final long total = frameIdxSet.intStream()
                    .peek(this::checkValidFrame)
                    .mapToLong(frameIdx -> {
                        final int valueIdx = getEntryIdx(frameIdx) + Long.BYTES;
                        return ZstdSegmentUtil.getLongLE(seekTableEntriesBuffer, valueIdx);
                    })
                    .sum();
            LOGGER.debug("getTotalUncompressedSize() - frameIdxSet: {}, total: {}", frameIdxSet, total);
            return total;
        }
    }

    /**
     * For a given frameIdxSet, return the uncompressed size of the frames included
     * in the filter expressed as a percentage of the total uncompressed size.
     *
     * @param frameIdxSet The frames to include in the calculation.
     */
    public double getPercentageOfCompressed(final IntSet frameIdxSet) {
        if (NullSafe.isEmptyCollection(frameIdxSet)) {
            return 0;
        } else {
            final long filtered = frameIdxSet.intStream()
                    .mapToLong(idx -> getFrameLocation(idx).compressedSize())
                    .sum();
            final int lastIdx = getEntryIdx(frameCount - 1);
            final long total = ZstdSegmentUtil.getLongLE(seekTableEntriesBuffer, lastIdx);
            return filtered / (double) total * 100;
        }
    }

    /**
     * @return True if frameIdx is a valid index for a frame/segment.
     */
    public boolean isValidFrame(final int frameIdx) {
        return frameIdx >= 0 && frameIdx < frameCount;
    }

    private void checkValidFrame(final int frameIdx) {
        if (!isValidFrame(frameIdx)) {
            throw new IllegalStateException("Invalid frame index: " + frameIdx);
        }
    }

    /**
     * The position of the entry relative to the start of the seek table entries.
     */
    private static int getEntryIdx(final int frameIdx) {
        return frameIdx * ZstdConstants.SEEK_TABLE_ENTRY_SIZE;
    }

    @Override
    public Iterator<FrameLocation> iterator() {
        return new ZstdSeekTableIterator();
    }

    public Iterator<FrameLocation> iterator(final IntSortedSet frameIdxSet) {
        return new ZstdSeekTableFilteredIterator(frameIdxSet);
    }

    /**
     * For a given set of frame indexes, build a list of {@link FrameRange} objects, with one
     * frameRange per contiguous block of frames.
     * <p>
     * e.g. If there are 10 frames (0-9) and frameIdxSet contains [0,1,2,5,6,9] you will get
     * three ranges as follows: 0-2, 5-6 & 9-9 (inclusive-inclusive).
     * </p>
     *
     * @param frameIdxSet The frame indexes to include
     * @return The list of {@link FrameRange}s in frame index order.
     */
    public List<FrameRange> getContiguousRanges(final IntSortedSet frameIdxSet) {
        if (NullSafe.hasItems(frameIdxSet)) {
            final List<FrameRange> frameRanges = new ArrayList<>();
            final Iterator<FrameLocation> iterator = iterator(frameIdxSet);
            FrameLocation firstFrameInRange = null;
            FrameLocation lastFrameLocation = null;
            while (iterator.hasNext()) {
                final FrameLocation frameLocation = iterator.next();

                if (lastFrameLocation == null || (frameLocation.frameIdx() - lastFrameLocation.frameIdx() > 1)) {
                    // Start of new range, so record the last range if there was one
                    if (firstFrameInRange != null) {
                        final FrameRange frameRange = firstFrameInRange.asFrameRange(lastFrameLocation);
                        LOGGER.debug("getContiguousRanges() - Creating range {}", frameRange);
                        frameRanges.add(frameRange);
                    }

                    // Now set the start point for the new range
                    firstFrameInRange = frameLocation;
                }
                lastFrameLocation = frameLocation;
            }

            if (firstFrameInRange != null) {
                // Add the last range
                final FrameRange frameRange = firstFrameInRange.asFrameRange(lastFrameLocation);
                LOGGER.debug("getContiguousRanges() - Creating final range {}", frameRange);
                frameRanges.add(frameRange);
            }
            LOGGER.debug(() -> LogUtil.message("getCompressedRanges() - Returning {} ranges", frameRanges.size()));
            return Collections.unmodifiableList(frameRanges);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public String toString() {
        return "ZstdSeekTable{" +
               "frameCount=" + frameCount +
               ", hasDictionary=" + hasDictionary() +
               ", dictionaryUuid=" + dictionaryUuid +
               ", totalCompressedSize=" + ModelStringUtil.formatCsv(getTotalCompressedDataSize()) +
               ", totalUncompressedSize=" + ModelStringUtil.formatCsv(getTotalUncompressedSize()) +
               '}';
    }

    // --------------------------------------------------------------------------------


    @NullMarked
    private class ZstdSeekTableIterator implements Iterator<FrameLocation> {

        private int lastFrameIdx = -1;

        @Override
        public boolean hasNext() {
            final int nextFrameIdx = lastFrameIdx + 1;
            return isValidFrame(nextFrameIdx);
        }

        @Override
        public FrameLocation next() {
            final int nextFrameIdx = lastFrameIdx + 1;
            if (isValidFrame(nextFrameIdx)) {
                final FrameLocation frameLocation = getFrameLocation(nextFrameIdx);
                lastFrameIdx = nextFrameIdx;
                return frameLocation;
            } else {
                throw new NoSuchElementException();
            }
        }
    }


    // --------------------------------------------------------------------------------


    @NullMarked
    private class ZstdSeekTableFilteredIterator implements Iterator<FrameLocation> {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ZstdSeekTableFilteredIterator.class);

        private final IntBidirectionalIterator frameIdxIterator;

        private ZstdSeekTableFilteredIterator(final IntSortedSet includedFrameIndexes) {
            this.frameIdxIterator = includedFrameIndexes.iterator();
        }

        @Override
        public boolean hasNext() {
            final boolean hasNext = frameIdxIterator.hasNext();
            LOGGER.trace("hasNext() - Returning hasNext: {}, zstdSeekTable: {}", hasNext, ZstdSeekTable.this);
            return hasNext;
        }

        @Override
        public FrameLocation next() {
            final int nextFrameIdx = frameIdxIterator.nextInt();
            if (isValidFrame(nextFrameIdx)) {
                final FrameLocation frameLocation = getFrameLocation(nextFrameIdx);
                LOGGER.trace("next() - Returning frameLocation: {}, zstdSeekTable: {}",
                        frameLocation,
                        ZstdSeekTable.this);
                return frameLocation;
            } else {
                throw new IllegalStateException(LogUtil.message("Invalid frameIdx {} for seekTable: {}",
                        nextFrameIdx, ZstdSeekTable.this));
            }
        }
    }


    // --------------------------------------------------------------------------------


    public static class InvalidSeekTableDataException extends RuntimeException {

        public InvalidSeekTableDataException(final String message) {
            super(message);
        }

        public InvalidSeekTableDataException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }


    // --------------------------------------------------------------------------------


    @NullMarked
    public static class InsufficientSeekTableDataException extends RuntimeException {

        private final long requiredSeekTableFrameSize;
        private final long actualSeekTableFrameSize;

        public InsufficientSeekTableDataException(final long requiredSeekTableFrameSize,
                                                  final long actualSeekTableFrameSize) {
            super(LogUtil.message("Insufficient data for complete seek table frame. Expected: {}, actual: {}",
                    requiredSeekTableFrameSize, actualSeekTableFrameSize));
            this.requiredSeekTableFrameSize = requiredSeekTableFrameSize;
            this.actualSeekTableFrameSize = actualSeekTableFrameSize;
        }

        public long getRequiredSeekTableFrameSize() {
            return requiredSeekTableFrameSize;
        }

        public long getActualSeekTableFrameSize() {
            return actualSeekTableFrameSize;
        }

        @Override
        public String toString() {
            return "InsufficientSeekTableDataException{" +
                   "expectedSeekTableFrameSize=" + requiredSeekTableFrameSize +
                   ", message=" + getMessage() +
                   '}';
        }
    }
}
