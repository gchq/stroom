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


import stroom.aws.s3.impl.S3Manager;
import stroom.meta.shared.Meta;
import stroom.util.io.FileUtil;
import stroom.util.io.WrappedInputStream;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.ints.IntSortedSets;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Objects;
import java.util.UUID;

/**
 * Gets each frame directly from S3 using a GET with byte range.
 * Intended for use when only one segment or only a small percentage of the stream is required.
 */
public class S3FrameSupplierImpl implements ZstdFrameSupplier {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3FrameSupplierImpl.class);
    // TODO needs to come from config
    // If we need a critical mass of the stream we might as well download it all to a temp file and
    // grab the bits we need from there.  Maybe it should be based on % of total frame count rather than
    // on size as transfer cost is free, but requests have a cost.
    private static final double DOWNLOAD_ALL_PCT_THRESHOLD = 50;

    private final S3Manager s3Manager;
    private final Meta meta;
    private final String childStreamType;
    private final String keyNameTemplate;
    private final Path tempDir;
    private final S3StreamTypeExtensions s3StreamTypeExtensions;

    private ZstdSeekTable zstdSeekTable = null;
    private IntSortedSet includedFrameIndexes = IntSortedSets.emptySet();
    private boolean includeAll = false;
    private Iterator<FrameLocation> frameLocationIterator = null;

    private InputStream currentInputStream = null;
    private FrameLocation currentFrameLocation = null;
    private boolean downloadAll = false;
    private FileFrameSupplierImpl fileFrameSupplier = null;
    private Path tempFile = null;

    public S3FrameSupplierImpl(final S3Manager s3Manager,
                               final Meta meta,
                               final String childStreamType,
                               final String keyNameTemplate,
                               final Path tempDir,
                               final S3StreamTypeExtensions s3StreamTypeExtensions) {
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
        if (includeAll) {
            if (NullSafe.hasItems(includedFrameIndexes)) {
                throw new IllegalArgumentException("Cannot set includeAll and includedFrameIndexes");
            }
        }
        if (this.zstdSeekTable != null) {
            throw new IllegalStateException("Already initialised");
        }
        this.zstdSeekTable = Objects.requireNonNull(zstdSeekTable);
        this.includeAll = includeAll;
        this.includedFrameIndexes = Objects.requireNonNullElseGet(
                includedFrameIndexes,
                IntSortedSets::emptySet);
        this.frameLocationIterator = includeAll
                ? zstdSeekTable.iterator()
                : zstdSeekTable.iterator(includedFrameIndexes);
        this.downloadAll = shouldDownloadAll(zstdSeekTable, includedFrameIndexes, includeAll);

        if (downloadAll) {
            initFileFrameSupplier();
        }
    }

    private void initFileFrameSupplier() {
        this.tempFile = createTempFile();
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

    private boolean shouldDownloadAll(final ZstdSeekTable zstdSeekTable,
                                      final IntSortedSet includedFrameIndexes,
                                      final boolean includeAll) {
        final long totalUncompressedSize = getTotalUncompressedSize(includedFrameIndexes, includeAll);
        LOGGER.debug("shouldDownloadAll() - meta: {}, childStreamType: {}, totalUncompressedSize: {}",
                meta, childStreamType, totalUncompressedSize);
        if (totalUncompressedSize == 0) {
            return false;
        } else {
            if (includeAll) {
                return true;
            } else {
                final double percentageOfCompressed = zstdSeekTable.getPercentageOfCompressed(includedFrameIndexes);
                LOGGER.debug("shouldDownloadAll() - meta: {}, childStreamType: {}, " +
                             "totalUncompressedSize: {}, percentageOfCompressed: {}",
                        meta, childStreamType, totalUncompressedSize, percentageOfCompressed);
                return percentageOfCompressed > DOWNLOAD_ALL_PCT_THRESHOLD;
            }
        }
    }

    private long getTotalUncompressedSize(final IntSortedSet includedFrameIndexes, final boolean includeAll) {
        if (includeAll) {
            return zstdSeekTable.getTotalUncompressedSize();
        } else {
            return zstdSeekTable.getTotalUncompressedSize(includedFrameIndexes);
        }
    }

    private void checkInitialised() {
        if (zstdSeekTable == null) {
            throw new IllegalStateException("Not initialised");
        }
    }

    @Override
    public boolean hasNext() {
        LOGGER.debug("hasNext()");
        checkInitialised();
        final boolean hasNext = frameLocationIterator.hasNext();
        LOGGER.debug("hasNext() - currentFrameLocation: {}, returning: {}", currentFrameLocation, hasNext);
        return hasNext;
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
        } else {
            // Grab the frameInputStream from an S3 range GET directly
            frameLocation = frameLocationIterator.next();
            frameInputStream = getFrameInputStreamFromRangeGet(frameLocation);
        }
        this.currentFrameLocation = frameLocation;
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

        // TODO When we want the whole stream we want a different approach
        final ResponseInputStream<GetObjectResponse> responseInputStream = s3Manager.getByteRange(
                meta,
                childStreamType,
                keyNameTemplate,
                frameLocation.asRange());

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
        if (currentInputStream != null) {
            currentInputStream.close();
        }
        if (fileFrameSupplier != null) {
            fileFrameSupplier.close();
        }
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
        return tempDir.resolve(id + "__" + UUID.randomUUID() + ext);
    }
}
