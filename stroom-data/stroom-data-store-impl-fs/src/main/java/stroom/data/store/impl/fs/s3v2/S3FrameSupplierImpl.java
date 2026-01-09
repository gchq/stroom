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


import stroom.aws.s3.impl.S3Manager;
import stroom.meta.shared.Meta;
import stroom.task.api.ExecutorProvider;
import stroom.util.NullSafeExtra;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.io.WrappedInputStream;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Range;

import com.esotericsoftware.kryo.io.ByteBufferOutputStream;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gets each frame directly from S3 using a GET with byte range.
 * Intended for use when only one segment or only a small percentage of the stream is required.
 */
public class S3FrameSupplierImpl extends AbstractZstdFrameSupplier {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3FrameSupplierImpl.class);

    private final ExecutorProvider executorProvider;
    private final S3Manager s3Manager;
    private final Meta meta;
    private final String childStreamType;
    private final String keyNameTemplate;
    private final Path tempDir;
    private final S3StreamTypeExtensions s3StreamTypeExtensions;

    private InputStream currentInputStream = null;
    private FileFrameSupplierImpl fileFrameSupplier = null;
    private Path tempFile = null;
    private FileChannel sparseFileChannel = null;
    private boolean downloadAll = false;


    // Source frameLocation => translated frameLocation
//    Map<FrameLocation, FrameLocation> frameLocationTranslationMap = null;
    List<FrameRange> frameRanges = null;
    Arena arena;
    Map<FrameRange, CompletableFuture<Void>> rangeFutures;

    S3FrameSupplierImpl(final ExecutorProvider executorProvider,
                        final S3Manager s3Manager,
                        final Meta meta,
                        final String childStreamType,
                        final String keyNameTemplate,
                        final Path tempDir,
                        final S3StreamTypeExtensions s3StreamTypeExtensions) {
        this.executorProvider = executorProvider;
        this.s3Manager = s3Manager;
        this.meta = meta;
        this.childStreamType = childStreamType;
        this.keyNameTemplate = keyNameTemplate;
        this.tempDir = tempDir;
        this.s3StreamTypeExtensions = s3StreamTypeExtensions;
    }

    @Override
    public void initialise(final ZstdSeekTable zstdSeekTable,
                           final IntSortedSet includedFrameIndexes,
                           final boolean includeAll) {
        super.initialise(zstdSeekTable, includedFrameIndexes, includeAll);
        this.downloadAll = shouldDownloadAll(zstdSeekTable, includedFrameIndexes, includeAll);
        this.tempFile = createTempFile();
        if (downloadAll) {
            // We need to download the whole file as we need all or most of it
            initDownloadAllFileFrameSupplier();
        } else {
            // TODO We could pre-emptively fetch all the byte ranges in parallel with N threads.
            //  We would need to first create the tempFile with a size equal to the total of all
            //  the included compressed frames. We then need to build a map of FrameLocations from
            //  the seek table to a shifted FrameLocation in the non-sparse temp file. Each thread can then
            //  fetch an included frame and write it to the file using FileChannel.map(mode, offset, size)
            //  and the shifted frame location. The FileChannel and MappedByteBuffers/MemorySegments can
            //  be held until close() is called, the next() method can hopefully read back from memory.
            //  next() would need to block until the required frame is available as we need them in order.
            //  S3 has not great latency but is highly parallel, so hopefully pre-fetching like this would
            //  speed things up a bit.
            initialiseByteRanges(zstdSeekTable, includedFrameIndexes);
        }
    }

    private void initialiseByteRanges(final ZstdSeekTable zstdSeekTable,
                                      final IntSortedSet includedFrameIndexes) {
        // Find all the contiguous ranges that we need to fetch
        frameRanges = zstdSeekTable.getContiguousRanges(includedFrameIndexes);
        rangeFutures = new ConcurrentHashMap<>(frameRanges.size());
        arena = Arena.ofShared();
        final long requiredFileSize = zstdSeekTable.getTotalCompressedDataSize();

        try {
            // Open the file for writing
            sparseFileChannel = openForReadWrite(tempFile);

            // Map the full size of the sparse file even if we are only writing a few frames into it.
            // The actual amount of disk used will be similar to the data written, NOT requiredFileSize.
            final MemorySegment sparseFileMemSegment = sparseFileChannel.map(
                    MapMode.READ_WRITE,
                    0,
                    requiredFileSize,
                    arena);
            fileFrameSupplier = new FileFrameSupplierImpl(tempFile, sparseFileMemSegment);
            fileFrameSupplier.initialise(zstdSeekTable, includedFrameIndexes, false);

            // Now spawn threads to fetch all the ranges we need and write them into the file.
            // They will each be writing to their own dedicated slice.
            for (final FrameRange frameRange : frameRanges) {
                initialiseByteRange(frameRange, sparseFileMemSegment);
            }

        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void initialiseByteRange(final FrameRange frameRange,
                                     final MemorySegment sparseFileMemSegment) {
        final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(
                () -> {
                    final Range<Long> byteRange = frameRange.asCompressedByteRange();
                    final MemorySegment memSegmentSlice = frameRange.asSlice(sparseFileMemSegment);
                    final ByteBuffer sliceByteBuffer = memSegmentSlice.asByteBuffer();
                    try (final OutputStream outputStream = new ByteBufferOutputStream(sliceByteBuffer);
                            final ResponseInputStream<GetObjectResponse> responseInputStream =
                                    getByteRange(byteRange)) {
                        final long count = StreamUtil.streamToStream(responseInputStream, outputStream);
                        LOGGER.debug("initialiseByteRange() - Written {} bytes to {} from {} using range: {}",
                                count, tempFile, keyNameTemplate, frameRange);
                    } catch (final IOException e) {
                        LOGGER.error("Error fetching range {}, key {}, frameRange: {} - {}",
                                byteRange, keyNameTemplate, frameRange, LogUtil.exceptionMessage(e), e);
                        throw new UncheckedIOException(e);
                    }
                },
                executorProvider.get());

        rangeFutures.put(frameRange, completableFuture);
    }

    private ResponseInputStream<GetObjectResponse> getByteRange(final Range<Long> byteRange) {
        return s3Manager.getByteRange(
                meta,
                childStreamType,
                keyNameTemplate,
                byteRange);
    }

    private static FileChannel openForReadWrite(final Path sparseFile) throws IOException {
        return FileChannel.open(
                sparseFile,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE);
    }

    private boolean shouldDownloadAll(final ZstdSeekTable zstdSeekTable,
                                      final IntSortedSet includedFrameIndexes,
                                      final boolean includeAll) {
        final long totalUncompressedSize = getTotalUncompressedSize(includedFrameIndexes, includeAll);
        LOGGER.debug("shouldDownloadAll() - totalUncompressedSize: {}", totalUncompressedSize);
        if (totalUncompressedSize == 0) {
            return false;
        } else {
            if (includeAll) {
                return true;
            } else {
                final double percentageOfCompressed = zstdSeekTable.getPercentageOfCompressed(includedFrameIndexes);
                LOGGER.debug("shouldDownloadAll() - totalUncompressedSize: {}, percentageOfCompressed: {}",
                        totalUncompressedSize, percentageOfCompressed);
                return percentageOfCompressed > DOWNLOAD_ALL_PCT_THRESHOLD;
            }
        }
    }

    private void initDownloadAllFileFrameSupplier() {
        // Can't use async as this thread needs it
        s3Manager.download(meta, childStreamType, keyNameTemplate, tempFile, false);
        try {
            fileFrameSupplier = new FileFrameSupplierImpl(tempFile);
            fileFrameSupplier.initialise(zstdSeekTable, includedFrameIndexes, includeAll);
        } catch (final IOException e) {
            throw new UncheckedIOException(LogUtil.message(
                    "Error creating frame supplier for '{}' - {}",
                    tempFile.toAbsolutePath().normalize(), LogUtil.exceptionMessage(e)), e);
        }
    }

    @Override
    public InputStream next() {
        checkInitialised();
        final InputStream frameInputStream;
        final FrameLocation frameLocation;
        if (downloadAll) {
            // Grab the frameInputStream from the downloaded temp file
            frameInputStream = fileFrameSupplier.next();
            frameLocation = fileFrameSupplier.getCurrentFrameLocation();
            this.currentFrameLocation = frameLocation;
        } else {
            // Grab the frameInputStream from an S3 range GET directly
            frameLocation = nextFrameLocation();
            frameInputStream = getFrameInputStreamFromRangeGet(frameLocation);
        }
        LOGGER.debug("next() - returning input stream for frameLocation: {}", frameLocation);
        return frameInputStream;
    }

    @Override
    public FrameLocation getCurrentFrameLocation() {
        checkInitialised();
        return currentFrameLocation;
    }

    private InputStream getFrameInputStreamFromRangeGet(final FrameLocation frameLocation) {
        Objects.requireNonNull(frameLocation);
        if (currentInputStream != null) {
            throw new IllegalStateException("An responseInputStream is already open for frameLocation "
                                            + frameLocation);
        }

        // TODO See comment in initialise()
        final ResponseInputStream<GetObjectResponse> responseInputStream = getByteRange(frameLocation.asCompressedByteRange());

        final WrappedInputStream wrappedInputStream = new WrappedInputStream(responseInputStream) {
            @Override
            public void close() throws IOException {
                // Close responseInputStream
                super.close();
                LOGGER.debug("wrappedInputStream.close() - currentFrameLocation: {}", currentFrameLocation);
                // Null this out so we know it is closed
                currentInputStream = null;
                currentFrameLocation = null;
            }
        };
        this.currentInputStream = wrappedInputStream;
        this.currentFrameLocation = frameLocation;
        return wrappedInputStream;
    }

    @Override
    public void close() throws Exception {
        LOGGER.debug("close() - currentFrameLocation: {}, tempFile: {}", currentFrameLocation, tempFile);
        currentInputStream = NullSafeExtra.close(
                currentInputStream, true, "currentInputStream");
        fileFrameSupplier = NullSafeExtra.close(fileFrameSupplier, true, "fileFrameSupplier");
        sparseFileChannel = NullSafeExtra.close(sparseFileChannel, true, "sparseFileChannel");
        arena = NullSafeExtra.close(arena, true, "arena");

        if (tempFile != null) {
            try {
                FileUtil.deleteFile(tempFile);
            } catch (final Exception e) {
                // Just log and swallow it as it is only a temp file so limited onward harm
                LOGGER.error("Error deleting tempFile '{}' - {}",
                        tempFile.toAbsolutePath(), LogUtil.exceptionMessage(e), e);
            }
        }
    }

    private Path createTempFile() {
        final String ext = s3StreamTypeExtensions.getExtension(meta.getTypeName(), childStreamType);
        final long id = meta.getId();
        final Path tempFile = tempDir.resolve(id + "__" + UUID.randomUUID() + ext);
        LOGGER.debug("createTempFile() - Returning tempFile: {}", tempFile);
        return tempFile;
    }

    private Map<FrameLocation, FrameLocation> buildMappedFrameLocations() {
//        frameLocationTranslationMap = new HashMap<>(includedFrameIndexes.size());
        frameRanges = zstdSeekTable.getContiguousRanges(includedFrameIndexes);
        // TODO
        return null;
    }

//    private List<IntSortedSet> buildContiguousRanges(final IntSortedSet includedFrameIndexes) {
//        if (NullSafe.hasItems(includedFrameIndexes)) {
//            for (final Integer frameIndex : includedFrameIndexes) {
//
//            }
//        } else {
//            return Collections.emptyList();
//        }
//    }
}
