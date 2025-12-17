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

package stroom.data.store.impl.fs;

import stroom.data.store.api.DataException;
import stroom.data.store.api.OutputStreamProvider;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.query.api.datasource.QueryField;
import stroom.util.io.FileUtil;
import stroom.util.io.SeekableOutputStream;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * A file system implementation of Target.
 */
final class FsTarget implements InternalTarget, SegmentOutputStreamProviderFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsTarget.class);

    private final MetaService metaService;
    private final FsPathHelper fileSystemStreamPathHelper;
    private final Map<String, FsTarget> childMap = new HashMap<>();
    private final Map<String, SegmentOutputStreamProvider> outputStreamMap = new HashMap<>(10);
    private final Path volumePath;
    private final String streamType;
    private final FsTarget parent;
    private AttributeMap attributeMap;
    private OutputStream outputStream;
    private Path file;

    private Meta meta;
    private boolean closed;
    private boolean deleted;
    private long index;

    private FsTarget(final MetaService metaService,
                     final FsPathHelper fileSystemStreamPathHelper,
                     final Meta requestMetaData,
                     final Path volumePath,
                     final String streamType) {
        this.metaService = metaService;
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.meta = requestMetaData;
        this.volumePath = volumePath;
        this.parent = null;
        this.streamType = streamType;

        validate();
    }

    private FsTarget(final MetaService metaService,
                     final FsPathHelper fileSystemStreamPathHelper,
                     final FsTarget parent,
                     final String streamType,
                     final Path file) {
        this.metaService = metaService;
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.meta = parent.meta;
        this.volumePath = parent.volumePath;
        this.parent = parent;
        this.streamType = streamType;
        this.file = file;
        validate();
    }

    /**
     * Creates a new file system stream target.
     *
     * @return A new file system target.
     */
    static FsTarget create(final MetaService metaService,
                           final FsPathHelper fileSystemStreamPathHelper,
                           final Meta meta,
                           final Path rootPath,
                           final String streamType) {
        return new FsTarget(metaService, fileSystemStreamPathHelper, meta, rootPath, streamType);
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
        }
        return attributeMap;
    }

    private void writeManifest() {
        try {
            final Path manifestFile = fileSystemStreamPathHelper.getChildPath(getFile(),
                    InternalStreamTypeNames.MANIFEST);
            // No manifest done yet ... output one if the parent dir's exist
            if (Files.isDirectory(getFile().getParent())) {
                try (final OutputStream outputStream = fileSystemStreamPathHelper.getOutputStream(
                        InternalStreamTypeNames.MANIFEST,
                        manifestFile)) {
                    AttributeMapUtil.write(getAttributes(), outputStream);
                }
            } else {
                LOGGER.warn(() -> "closeStreamTarget() - Closing target file with no directory present");
            }
        } catch (final IOException e) {
            LOGGER.error(() -> "closeStreamTarget() - Error on writing Manifest " + this, e);
        }
    }

    private void updateAttribute(final FsTarget target, final QueryField key, final String value) {
        if (!target.getAttributes().containsKey(key.getFldName())) {
            target.getAttributes().put(key.getFldName(), value);
        }
    }

    Path getFile() {
        if (file == null) {
            if (parent == null) {
                file = fileSystemStreamPathHelper.getRootPath(volumePath, meta, streamType);
            } else {
                file = fileSystemStreamPathHelper.getChildPath(parent.getFile(), streamType);
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
                RuntimeException streamCloseException = null;
                try {
                    closeStreams();
                } catch (final RuntimeException e) {
                    streamCloseException = e;
                } finally {
                    // Only write meta for the root target.
                    if (parent == null) {
                        // Update attributes and write the manifest.
                        updateAttribute(this, MetaFields.RAW_SIZE, String.valueOf(getStreamSize()));
                        updateAttribute(this, MetaFields.FILE_SIZE, String.valueOf(getTotalFileSize()));
                        writeManifest();

                        if (streamCloseException == null) {
                            // Unlock will update the meta data so set it back on the stream
                            // target so the client has the up to date copy
                            unlock(getMeta(), getAttributes());
                        } else {
                            throw streamCloseException;
                        }
                    }
                }
            }
        } finally {
            outputStream = null;
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

        try {
            // Close the stream target.
            closeStreams();
        } finally {
            try {
                // Only delete the root target.
                if (parent == null) {
                    // Mark the target meta as deleted.
                    this.meta = metaService.updateStatus(meta, Status.LOCKED, Status.DELETED);
                }
            } finally {
                outputStream = null;
                deleted = true;
            }
        }
    }

    private void closeStreams() {
        RuntimeException exception = null;

        // Close the stream target.
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (final IOException e) {
                LOGGER.error(() -> "closeStreams() - Error on closing stream " + this, e);
                exception = new UncheckedIOException(e);
            }
        }

        // Close off any open kids .... closing the parent
        // closes kids (the caller can also close the kid off if they like).
        for (final FsTarget child : childMap.values()) {
            try {
                child.close();
            } catch (final RuntimeException e) {
                LOGGER.error(() -> "closeStreams() - Error on closing child stream " + this, e);
                if (exception != null) {
                    exception = e;
                }
            }
        }
        childMap.clear();

        if (exception != null) {
            throw exception;
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
            final FsTarget target = getChild(k);
            if (target == null) {
                return null;
            }
            return new SegmentOutputStreamProvider(target, k);
        });
    }

    private FsTarget getChild(final String streamTypeName) {
        if (closed) {
            throw new RuntimeException("Closed");
        }

        if (streamTypeName == null) {
            return this;
        }

        return childMap.computeIfAbsent(streamTypeName, this::child);
    }

    private FsTarget child(final String streamTypeName) {
        final Path childFile = fileSystemStreamPathHelper.getChildPath(getFile(), streamTypeName);
        return new FsTarget(metaService, fileSystemStreamPathHelper, this, streamTypeName, childFile);
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

                outputStream = fileSystemStreamPathHelper.getOutputStream(streamType, file);
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
