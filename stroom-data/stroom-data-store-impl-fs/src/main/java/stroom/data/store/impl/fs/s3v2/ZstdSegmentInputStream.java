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


import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.impl.fs.s3v2.ZstdSeekTable.FilterMode;
import stroom.util.io.SeekableInputStream;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.github.luben.zstd.ZstdInputStream;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntBidirectionalIterator;
import it.unimi.dsi.fastutil.ints.IntSortedSet;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

/**
 * Currently supports one level of segmentation, either boundary or index, not both.
 */
public class ZstdSegmentInputStream extends SegmentInputStream {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ZstdSegmentInputStream.class);

    private final byte[] singleByte = new byte[1];
    //    private final SeekableInputStream seekableInputStream;
    private final ZstdSeekTable zstdSeekTable;
    private final ZstdFrameSupplier zstdFrameSupplier;
    private final ZstdDictionary zstdDictionary;
    private final HeapBufferPool heapBufferPool;

    private IntSortedSet includedSegments;
    private boolean includeAllSegments = true;
    private AtomicBoolean startedReading = new AtomicBoolean(false);
    private FrameLocation currentFrameLocation;
    private long positionInCurrentFrame;
    private long remainingInCurrentFrame;

    private IntBidirectionalIterator includedSegmentsIterator;
    //    private InputStream currentCompressedFrameStream;
    private InputStream currentZstdInputStream;
    //    private ZstdDecompressCtx zstdDecompressCtx = null;
    private boolean complete = false;
    private boolean currentFrameAllRead = false;
    private int currentFrameReadCount = 0;

    public ZstdSegmentInputStream(final ZstdSeekTable zstdSeekTable,
                                  final ZstdFrameSupplier zstdFrameSupplier,
                                  final ZstdDictionary zstdDictionary,
                                  final HeapBufferPool heapBufferPool) {
//        this.seekableInputStream = compressedInputStream;
        this.zstdSeekTable = Objects.requireNonNull(zstdSeekTable);
        this.zstdFrameSupplier = zstdFrameSupplier;
        this.zstdDictionary = zstdDictionary;
        this.heapBufferPool = heapBufferPool;
    }

    @Override
    public long count() {
        return zstdSeekTable.getFrameCount();
    }

    @Override
    public void include(final long segment) {
        checkState(Math.toIntExact(segment));
        includedSegments = Objects.requireNonNullElseGet(includedSegments, IntAVLTreeSet::new);
        includedSegments.add(Math.toIntExact(segment));
    }

    @Override
    public void includeAll() {
        checkState(null);
        includeAllSegments = true;
    }

    @Override
    public void exclude(final long segment) {
        throw new UnsupportedOperationException("Not called in non-test code, so no point in implementing");

    }

    @Override
    public void excludeAll() {
        throw new UnsupportedOperationException("Not called in non-test code, so no point in implementing");
    }

    @Override
    public long size() {
        if (includeAllSegments) {
            return zstdSeekTable.getTotalUncompressedSize();
        } else {
            return zstdSeekTable.getTotalUncompressedSize(includedSegments, FilterMode.INCLUDE);
        }
    }

    @Override
    public int read() throws IOException {
        final int len = read(singleByte);
        if (len == -1) {
            return -1; // end of stream
        }
        // result of read must be 0-255 (unsigned) so we need to convert our
        // signed byte to unsigned.
        return singleByte[0] & 0xff;
    }

    @Override
    public int read(final byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(final byte[] bytes, final int off, final int len) throws IOException {
        LOGGER.debug(() -> LogUtil.message("read() - bytes.length: {}, off: {}, len: {}", bytes.length, off, len));
        if (startedReading.compareAndSet(false, true)) {
            final boolean success = initialiseForReading();
            if (!success) {
                LOGGER.debug("read() - No frames to read from");
                return -1;
            }
        }

        if (complete) {
            return -1;
        }

        int remainingUncompressedToRead = len;
        int totalUncompressedBytesRead = 0;
        int currOffset = off;

        while (remainingUncompressedToRead > 0) {
            if (currentFrameAllRead) {
                // This will set complete field
                final boolean success = advanceFrame();
                if (success) {
                    currentFrameAllRead = false;
                    currentFrameReadCount = 0;
                } else {
                    LOGGER.debug("read() - No more frames to read from");
                    if (totalUncompressedBytesRead > 0) {
                        // On next read call, we will return -1
                        return totalUncompressedBytesRead;
                    } else {
                        return -1;
                    }
                }
            }
            LOGGER.debug("read() - totalUncompressedBytesRead: {}, remainingUncompressedToRead: {}",
                    totalUncompressedBytesRead, remainingUncompressedToRead);
            if (totalUncompressedBytesRead > len) {
                throw new IllegalStateException(LogUtil.message("totalUncompressedBytesRead {} is > len {}",
                        totalUncompressedBytesRead, len));
            }
            final long remainingUncompressedBytesInFrame = currentFrameLocation.originalSize() - currentFrameReadCount;
            final int amountToReadInFrame = Math.toIntExact(
                    Math.min(remainingUncompressedToRead, remainingUncompressedBytesInFrame));
            final int uncompressedBytesRead = currentZstdInputStream.read(bytes, currOffset, amountToReadInFrame);
            if (uncompressedBytesRead == -1) {
                LOGGER.debug("read() - Completed frame {}", currentFrameLocation);
                currentFrameAllRead = true;
            } else {
                remainingUncompressedToRead -= uncompressedBytesRead;
                totalUncompressedBytesRead += uncompressedBytesRead;
                currOffset += uncompressedBytesRead;
            }
        }
        LOGGER.debug("read() - Returning {}", totalUncompressedBytesRead);
        return totalUncompressedBytesRead;
    }

    @Override
    public void close() throws IOException {
        super.close();
        if (currentZstdInputStream != null) {
            currentZstdInputStream.close();
        }
    }

    private boolean initialiseForReading() throws IOException {
        LOGGER.debug("initialiseForReading()");
        // Easier to have one thing to work with (the include set iterator) so add
        // all the frames given we know exactly how many there are.
        if (includeAllSegments) {
            includedSegments = Objects.requireNonNullElseGet(includedSegments, IntAVLTreeSet::new);
            IntStream.range(0, zstdSeekTable.getFrameCount())
                    .forEach(includedSegments::add);
        }
        includedSegmentsIterator = includedSegments.iterator();

        if (currentFrameLocation != null) {
            throw new IllegalStateException("Expecting currentFrameLocation to be null at this point");
        }

//        zstdDecompressCtx = new ZstdDecompressCtx();
//        NullSafe.consume(zstdDictionary, dict -> {
//            LOGGER.debug("initialiseForReading() - Loading dictionary {}", dict);
//            zstdDecompressCtx.loadDict(dict.getDictionaryBytes());
//        });


        // Move to the first frame in our iterator
        return advanceFrame();
    }

    private boolean advanceFrame() throws IOException {
        final boolean didAdvance;
        if (includedSegmentsIterator.hasNext()) {
            final int nextFrameIdx = includedSegmentsIterator.nextInt();
            currentFrameLocation = zstdSeekTable.getFrameLocation(nextFrameIdx);

            // Iterator should ensure we have a valid frameIdx.
            Objects.requireNonNull(currentFrameLocation, () ->
                    "null current frame location for nexFrameIdx: " + nextFrameIdx);


            // Will get closed on close()
            final InputStream compressedFrameInputStream = zstdFrameSupplier.getFrameInputStream(currentFrameLocation);
            Objects.requireNonNull(compressedFrameInputStream, () -> LogUtil.message(
                    "null compressedFrameInputStream for FrameLocation: {}", currentFrameLocation));
//            this.currentCompressedFrameStream = zstdFrameSupplier.getFrameInputStream(currentFrameLocation);

            // Initialise the ZstdInputStream to decompress the frame
            final ZstdInputStream zstdInputStream = new ZstdInputStream(compressedFrameInputStream, heapBufferPool);
            if (zstdDictionary != null) {
                LOGGER.debug("advanceFrame() - Setting dictionary {}", zstdDictionary);
                zstdInputStream.setDict(zstdDictionary.getDictionaryBytes());
            }
            this.currentZstdInputStream = zstdInputStream;
            LOGGER.debug("advanceFrame() - nextFrameIdx: {}, currentFrameLocation: {}",
                    nextFrameIdx, currentFrameLocation);

            remainingInCurrentFrame = currentFrameLocation.compressedSize();
            LOGGER.debug("advanceFrame() - Advanced to: {}, remainingInCurrentFrame: {}",
                    currentFrameLocation, remainingInCurrentFrame);
            didAdvance = true;
        } else {
            LOGGER.debug("advanceFrame() - No more frames, currentFrameLocation: {}", currentFrameLocation);
            currentFrameLocation = null;
            remainingInCurrentFrame = 0;
            didAdvance = false;
            complete = true;
        }
        positionInCurrentFrame = 0;
        return didAdvance;
    }


    /**
     * @return false ... we don't support this
     */
    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public void mark(final int readlimit) {
        throw new IllegalStateException("Segmented Stream does not support mark and reset");
    }

    @Override
    public void reset() {
        throw new IllegalStateException("Segmented Stream does not support mark and reset");
    }

    private void checkState(final Integer segment) {
        if (segment != null && !zstdSeekTable.isValidFrame(segment)) {
            throw new IllegalArgumentException("Invalid segment " + segment);
        }
        if (startedReading.get()) {
            throw new RuntimeException("Cannot include a new segment as reading is in progress");
        }
    }


    // --------------------------------------------------------------------------------


    @FunctionalInterface
    public interface ZstdFrameSupplier {

        // TODO considering also passing in a list of FrameLocations so that the ZstdFrameSupplier can
        //  asynchronously pre-fetch the frames in the list so that they are immediately available on
        //  the next call to getFrameInputStream with one of those FrameLocations. This is to combat
        //  the potential latency in S3 GETs.
        InputStream getFrameInputStream(final FrameLocation frameLocation) throws IOException;

    }


    // --------------------------------------------------------------------------------


    private static class InternalSeekableInputStream extends InputStream implements SeekableInputStream {

        private final SeekableInputStream compressedInputStream;

        private InternalSeekableInputStream(final SeekableInputStream compressedInputStream,
                                            final List<FrameLocation> frameLocations) {
            this.compressedInputStream = compressedInputStream;
        }

        @Override
        public int read() throws IOException {
            return 0;
        }

        @Override
        public long getSize() throws IOException {
            return compressedInputStream.getSize();
        }

        @Override
        public long getPosition() throws IOException {
            return 0;
        }

        @Override
        public void seek(final long pos) throws IOException {

        }
    }
}
