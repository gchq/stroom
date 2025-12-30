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

import it.unimi.dsi.fastutil.ints.IntSortedSet;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * Gets each frame directly from S3 using a GET with byte range.
 * Intended for use when only one segment or only a small percentage of the stream is required.
 */
public class S3FrameSupplierImpl extends AbstractZstdFrameSupplier {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3FrameSupplierImpl.class);

    private final S3Manager s3Manager;
    private final Meta meta;
    private final String childStreamType;
    private final String keyNameTemplate;
    private final Path tempDir;
    private final S3StreamTypeExtensions s3StreamTypeExtensions;

    private InputStream currentInputStream = null;
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
        super.initialise(zstdSeekTable, includedFrameIndexes, includeAll);
        if (downloadAll) {
            // We need to download the whole file as we need all or most of it
            initFileFrameSupplier();
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
        final Path tempFile = tempDir.resolve(id + "__" + UUID.randomUUID() + ext);
        LOGGER.debug("createTempFile() - Returning tempFile: {}", tempFile);
        return tempFile;
    }
}
