/*
 * Copyright 2016 Crown Copyright
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

package stroom.data.store.impl.fs;

import stroom.data.store.api.DataException;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.Target;
import stroom.datasource.api.v2.AbstractField;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.util.io.FileUtil;
import stroom.util.io.SeekableOutputStream;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A file system implementation of Target.
 */
final class S3Target implements InternalTarget, SegmentOutputStreamProviderFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3Target.class);

    private final MetaService metaService;
    private final S3Manager s3Manager;
    private final S3PathHelper pathHelper;
    private final Map<String, S3Target> childMap = new HashMap<>();
    private final HashMap<String, SegmentOutputStreamProvider> outputStreamMap = new HashMap<>(10);
    private final Path volumePath;
    private final String streamType;
    private final S3Target parent;
    private AttributeMap attributeMap;
    private OutputStream outputStream;
    private Path file;

    private Meta meta;
    private boolean closed;
    private boolean deleted;
    private final boolean append;
    private long index;

    private S3Target(final MetaService metaService,
                     final S3Manager s3Manager,
                     final S3PathHelper pathHelper,
                     final Meta requestMetaData,
                     final String streamType,
                     final boolean append) {
        try {
            this.metaService = metaService;
            this.s3Manager = s3Manager;
            this.pathHelper = pathHelper;
            this.meta = requestMetaData;
            this.volumePath = Files.createTempDirectory("stroom");
            this.parent = null;
            this.streamType = streamType;
            this.append = append;

            validate();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private S3Target(final MetaService metaService,
                     final S3Manager s3Manager,
                     final S3PathHelper pathHelper,
                     final S3Target parent,
                     final String streamType,
                     final Path file) {
        this.metaService = metaService;
        this.s3Manager = s3Manager;
        this.pathHelper = pathHelper;
        this.meta = parent.meta;
        this.volumePath = parent.volumePath;
        this.parent = parent;
        this.append = parent.append;
        this.streamType = streamType;
        this.file = file;
        validate();
    }

    /**
     * Creates a new file system stream target.
     *
     * @return A new file system target.
     */
    static S3Target create(final MetaService metaService,
                           final S3Manager s3Manager,
                           final S3PathHelper pathHelper,
                           final Meta meta,
                           final String streamType,
                           final boolean append) {
        return new S3Target(metaService, s3Manager, pathHelper, meta, streamType, append);
    }

    private void validate() {
        if (streamType == null) {
            throw new IllegalStateException("Must have a stream type");
        }
    }

    @Override
    public Meta getMeta() {
        return meta;
    }

    @Override
    public AttributeMap getAttributes() {
        if (parent != null) {
            return parent.getAttributes();
        }
        if (attributeMap == null) {
            attributeMap = new AttributeMap();
            if (isAppend()) {
                readManifest(attributeMap);
            }
        }
        return attributeMap;
    }

    private void readManifest(final AttributeMap attributeMap) {
        final Path manifestFile = pathHelper.getChildPath(getFile(), InternalStreamTypeNames.MANIFEST);
        if (Files.isRegularFile(manifestFile)) {
            try (final InputStream inputStream = Files.newInputStream(manifestFile)) {
                AttributeMapUtil.read(inputStream, attributeMap);
            } catch (final IOException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
    }

    private void writeManifest() {
        try {
            boolean doneManifest = false;
            final Path manifestFile = pathHelper.getChildPath(getFile(),
                    InternalStreamTypeNames.MANIFEST);

            // Are we appending?
            if (isAppend()) {
                // Does the manifest exist ... overwrite it
                if (Files.isRegularFile(manifestFile)) {
                    try (final OutputStream outputStream = pathHelper.getOutputStream(
                            InternalStreamTypeNames.MANIFEST,
                            manifestFile)) {
                        AttributeMapUtil.write(getAttributes(), outputStream);
                    }
                    doneManifest = true;
                }
            }

            if (!doneManifest) {
                // No manifest done yet ... output one if the parent dir's exist
                if (Files.isDirectory(getFile().getParent())) {
                    try (final OutputStream outputStream = pathHelper.getOutputStream(
                            InternalStreamTypeNames.MANIFEST,
                            manifestFile)) {
                        AttributeMapUtil.write(getAttributes(), outputStream);
                    }
                } else {
                    LOGGER.warn(() -> "closeStreamTarget() - Closing target file with no directory present");
                }

            }
        } catch (final IOException e) {
            LOGGER.error(() -> "closeStreamTarget() - Error on writing Manifest " + this, e);
        }
    }

    private void updateAttribute(final S3Target target, final AbstractField key, final String value) {
        if (!target.getAttributes().containsKey(key.getName())) {
            target.getAttributes().put(key.getName(), value);
        }
    }

    Path getFile() {
        if (file == null) {
            if (parent == null) {
                file = pathHelper.getRootPath(volumePath, meta, streamType);
            } else {
                file = pathHelper.getChildPath(parent.getFile(), streamType);
            }
            LOGGER.debug(() -> "getFile() " + FileUtil.getCanonicalPath(file));
        }
        return file;
    }

    @Override
    public void close() {
        if (closed) {
            throw new DataException("Target already closed");
        }

        try {
            if (!deleted) {
                // If we get error on closing the stream we must return it to the caller
                IOException streamCloseException = null;

                // Close the stream target.
                try {
                    if (outputStream != null) {
                        outputStream.close();
                    }

                    // Close off any open kids .... closing the parent
                    // closes kids (the caller can also close the kid off if they like).
                    childMap.forEach((k, v) -> v.close());
                    childMap.clear();
                } catch (final ClosedByInterruptException e) {
                    // WE expect these exceptions if a user is trying to terminate.
                    LOGGER.debug(() -> "closeStreamTarget() - Error on closing stream " + this, e);
                    streamCloseException = e;
                } catch (final IOException e) {
                    LOGGER.error(() -> "closeStreamTarget() - Error on closing stream " + this, e);
                    streamCloseException = e;
                }

                // Only write meta for the root target.
                if (parent == null) {
                    // Update attributes and write the manifest.
                    updateAttribute(this, MetaFields.RAW_SIZE, String.valueOf(getStreamSize()));
                    updateAttribute(this, MetaFields.FILE_SIZE, String.valueOf(getTotalFileSize()));
                    writeManifest();

                    if (streamCloseException == null) {

                        Path zipFile = null;
                        try {
                            // Create zip.
                            try {
                                zipFile = Files.createTempFile("stroom", ".zip");
                                try (final ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(
                                        Files.newOutputStream(zipFile)))) {
                                    try (final Stream<Path> stream = Files.list(volumePath)) {
                                        stream.forEach(path -> {
                                            try (final InputStream inputStream = new BufferedInputStream(Files.newInputStream(
                                                    path))) {
                                                zipOutputStream.putNextEntry(new ZipEntry(path.getFileName().toString()));
                                                StreamUtil.streamToStream(inputStream, zipOutputStream);
                                            } catch (final IOException e) {
                                                throw new UncheckedIOException(e);
                                            }
                                        });
                                    }
                                }

                                // Upload the zip to S3.
                                s3Manager.upload(
                                        meta,
                                        meta.getTypeName(),
                                        getAttributes(),
                                        zipFile);

                            } catch (final IOException e) {
                                throw new UncheckedIOException(e);
                            } finally {
                                if (zipFile != null) {
                                    try {
                                        Files.delete(zipFile);
                                    } catch (final IOException e) {
                                        LOGGER.debug(e::getMessage, e);
                                    }
                                }
                                try {
                                    FileUtil.deleteDir(volumePath);
                                } catch (final RuntimeException e) {
                                    LOGGER.debug(e::getMessage, e);
                                }
                            }

                            // Unlock will update the meta data so set it back on the stream
                            // target so the client has the up to date copy
                            unlock(getMeta(), getAttributes());

                        } catch (final RuntimeException e) {
                            LOGGER.error(e::getMessage, e);
                            throw e;
                        }
                    } else {
                        throw new UncheckedIOException(streamCloseException);
                    }
                }
            }
        } finally {
            closed = true;
        }
    }

    private void unlock(final Meta meta, final AttributeMap attributeMap) {
        if (Status.UNLOCKED.equals(meta.getStatus())) {
            throw new IllegalStateException("Attempt to unlock data that is already unlocked");
        }

        // Write the meta data
        if (!attributeMap.isEmpty()) {
            try {
                metaService.addAttributes(meta, attributeMap);
            } catch (final RuntimeException e) {
                LOGGER.error(() -> "unlock() - Failed to persist attributes in new transaction... will ignore");
            }
        }

        LOGGER.debug(() -> "unlock() " + meta);
        this.meta = metaService.updateStatus(meta, Status.LOCKED, Status.UNLOCKED);
    }

    public void delete() {
        if (deleted) {
            throw new DataException("Target already deleted");
        }
        if (closed) {
            throw new DataException("Target already closed");
        }

        try {
            // Close the stream target.
            try {
                if (outputStream != null) {
                    outputStream.close();
                }

                // Close off any open kids .... closing the parent
                // closes kids (the caller can also close the kid off if they like).
                childMap.forEach((k, v) -> v.close());
                childMap.clear();

                try {
                    FileUtil.deleteDir(volumePath);
                } catch (final RuntimeException e) {
                    LOGGER.debug(e::getMessage, e);
                }
            } catch (final IOException e) {
                LOGGER.error(() -> "closeStreamTarget() - Error on closing stream " + this, e);
            }

            // Only delete the root target.
            if (parent == null) {
                // Mark the target meta as deleted.
                this.meta = metaService.updateStatus(meta, Status.LOCKED, Status.DELETED);
            }
        } finally {
            deleted = true;
        }
    }

    private Long getStreamSize() {
        try {
            long total = 0;
            if (outputStream != null) {
                total += ((SeekableOutputStream) outputStream).getSize();
            }
            return total;
        } catch (final IOException ioEx) {
            // Wrap it
            throw new RuntimeException(ioEx);
        }
    }

    private Long getTotalFileSize() {
        long total = 0;
        final Path file = getFile();
        try {
            if (Files.isRegularFile(file)) {
                total += Files.size(file);
            }
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
        }
        return total;
    }

    @Override
    public OutputStreamProvider next() {
        final OutputStreamProvider outputStreamProvider = new OutputStreamProviderImpl(meta, this, index);
        index++;
        return outputStreamProvider;
    }

    @Override
    public SegmentOutputStreamProvider getSegmentOutputStreamProvider(final String streamTypeName) {
        return outputStreamMap.computeIfAbsent(streamTypeName, k -> {
            final S3Target target = getChild(k);
            if (target == null) {
                return null;
            }
            return new SegmentOutputStreamProvider(target, k);
        });
    }

    private S3Target getChild(final String streamTypeName) {
        if (closed) {
            throw new RuntimeException("Closed");
        }

        if (streamTypeName == null) {
            return this;
        }

        return childMap.computeIfAbsent(streamTypeName, this::child);
    }

    private S3Target child(final String streamTypeName) {
        final Path childFile = pathHelper.getChildPath(getFile(), streamTypeName);
        return new S3Target(metaService, s3Manager, pathHelper, this, streamTypeName, childFile);
    }

    Target getParent() {
        return parent;
    }

    boolean isAppend() {
        return append;
    }

    @Override
    public String toString() {
        return "id=" + meta.getId();
    }

    /////////////////////////////////
    // START INTERNAL TARGET
    /////////////////////////////////

    /**
     * Gets the output stream for this stream target.
     */
    @Override
    public OutputStream getOutputStream() {
        if (outputStream == null) {
            try {
                // Get the file.
                file = getFile();

                // Make sure the parent path exists.
                if (!FileSystemUtil.mkdirs(volumePath, file.getParent())) {
                    // Unable to create path
                    throw new DataException("Unable to create directory for file " + file);
                }

                // If the file already exists then delete it.
                if (Files.exists(file)) {
                    LOGGER.warn(() -> "About to overwrite file: " + FileUtil.getCanonicalPath(file));
                    if (!FileSystemUtil.deleteAnyPath(file)) {
                        LOGGER.error(() -> "getOutputStream() - Unable to delete existing files for new stream target");
                        throw new DataException("Unable to delete existing files for new stream target " + file);
                    }
                }

                outputStream = pathHelper.getOutputStream(streamType, file);
            } catch (final IOException ioEx) {
                LOGGER.error(() -> "getOutputStream() - " + ioEx.getMessage());
                // No reason to get a IO on opening the out stream .... fail in
                // a heap
                throw new DataException(ioEx);
            }
        }
        return outputStream;
    }

    @Override
    public OutputStream getChildOutputStream(final String type) {
        final InternalTarget childTarget = getChild(type);
        if (childTarget != null) {
            return childTarget.getOutputStream();
        }
        return null;
    }

    /////////////////////////////////
    // END INTERNAL TARGET
    /////////////////////////////////
}