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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.store.api.DataException;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.Target;
import stroom.io.SeekableOutputStream;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.shared.AttributeMap;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFieldNames;
import stroom.meta.shared.MetaService;
import stroom.meta.shared.Status;
import stroom.util.io.FileUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * A file system implementation of Target.
 */
final class FileSystemStreamTarget implements InternalTarget, SegmentOutputStreamProviderFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemStreamTarget.class);

    private final MetaService metaService;
    private final FileSystemStreamPathHelper fileSystemStreamPathHelper;
    private final Map<String, FileSystemStreamTarget> childMap = new HashMap<>();
    private final HashMap<String, SegmentOutputStreamProvider> outputStreamMap = new HashMap<>(10);
    private final String rootPath;
    private final String streamType;
    private final FileSystemStreamTarget parent;
    private AttributeMap attributeMap;
    private OutputStream outputStream;
    private Path file;

    private Meta meta;
    private boolean closed;
    private boolean deleted;
    private boolean append;
    private long index;

    private FileSystemStreamTarget(final MetaService metaService,
                                   final FileSystemStreamPathHelper fileSystemStreamPathHelper,
                                   final Meta requestMetaData,
                                   final String rootPath,
                                   final String streamType,
                                   final boolean append) {
        this.metaService = metaService;
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.meta = requestMetaData;
        this.rootPath = rootPath;
        this.parent = null;
        this.streamType = streamType;
        this.append = append;

        validate();
    }

    private FileSystemStreamTarget(final MetaService metaService,
                                   final FileSystemStreamPathHelper fileSystemStreamPathHelper,
                                   final FileSystemStreamTarget parent,
                                   final String streamType,
                                   final Path file) {
        this.metaService = metaService;
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.meta = parent.meta;
        this.rootPath = parent.rootPath;
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
    static FileSystemStreamTarget create(final MetaService metaService,
                                         final FileSystemStreamPathHelper fileSystemStreamPathHelper,
                                         final Meta meta,
                                         final String rootPath,
                                         final String streamType,
                                         final boolean append) {
        return new FileSystemStreamTarget(metaService, fileSystemStreamPathHelper, meta, rootPath, streamType, append);
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
        final Path manifestFile = fileSystemStreamPathHelper.getChildPath(getFile(), InternalStreamTypeNames.MANIFEST);
        if (Files.isRegularFile(manifestFile)) {
            try (final InputStream inputStream = Files.newInputStream(manifestFile)) {
                AttributeMapUtil.read(inputStream, attributeMap);
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    private void writeManifest() {
        try {
            boolean doneManifest = false;
            final Path manifestFile = fileSystemStreamPathHelper.getChildPath(getFile(), InternalStreamTypeNames.MANIFEST);

            // Are we appending?
            if (isAppend()) {
                // Does the manifest exist ... overwrite it
                if (Files.isRegularFile(manifestFile)) {
                    try (final OutputStream outputStream = fileSystemStreamPathHelper.getOutputStream(InternalStreamTypeNames.MANIFEST, manifestFile)) {
                        AttributeMapUtil.write(getAttributes(), outputStream);
                    }
                    doneManifest = true;
                }
            }

            if (!doneManifest) {
                // No manifest done yet ... output one if the parent dir's exist
                if (Files.isDirectory(getFile().getParent())) {
                    try (final OutputStream outputStream = fileSystemStreamPathHelper.getOutputStream(InternalStreamTypeNames.MANIFEST, manifestFile)) {
                        AttributeMapUtil.write(getAttributes(), outputStream);
                    }
                } else {
                    LOGGER.warn("closeStreamTarget() - Closing target file with no directory present");
                }

            }
        } catch (final IOException e) {
            LOGGER.error("closeStreamTarget() - Error on writing Manifest {}", this, e);
        }
    }

    private void updateAttribute(final FileSystemStreamTarget target, final String key, final String value) {
        if (!target.getAttributes().containsKey(key)) {
            target.getAttributes().put(key, value);
        }
    }

    Path getFile() {
        if (file == null) {
            if (parent == null) {
                file = fileSystemStreamPathHelper.getRootPath(rootPath, meta, streamType);
            } else {
                file = fileSystemStreamPathHelper.getChildPath(parent.getFile(), streamType);
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("getFile() " + FileUtil.getCanonicalPath(file));
            }
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
                } catch (final IOException e) {
                    LOGGER.error("closeStreamTarget() - Error on closing stream {}", this, e);
                    streamCloseException = e;
                }

                // Only write meta for the root target.
                if (parent == null) {
                    // Update attributes and write the manifest.
                    updateAttribute(this, MetaFieldNames.RAW_SIZE, String.valueOf(getStreamSize()));
                    updateAttribute(this, MetaFieldNames.FILE_SIZE, String.valueOf(getTotalFileSize()));
                    writeManifest();

                    if (streamCloseException == null) {
                        // Unlock will update the meta data so set it back on the stream
                        // target so the client has the up to date copy
                        unlock(getMeta(), getAttributes());
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
                LOGGER.error("unlock() - Failed to persist attributes in new transaction... will ignore");
            }
        }

        LOGGER.debug("unlock() " + meta);
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
            } catch (final IOException e) {
                LOGGER.error("closeStreamTarget() - Error on closing stream {}", this, e);
            }

            // Only write meta for the root target.
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
                LOGGER.error(e.getMessage(), e);
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
            final FileSystemStreamTarget target = getChild(k);
            if (target == null) {
                return null;
            }
            return new SegmentOutputStreamProvider(target, k);
        });
    }

    private FileSystemStreamTarget getChild(final String streamTypeName) {
        if (closed) {
            throw new RuntimeException("Closed");
        }

        if (streamTypeName == null) {
            return this;
        }

        return childMap.computeIfAbsent(streamTypeName, this::child);
    }

    private FileSystemStreamTarget child(final String streamTypeName) {
        final Path childFile = fileSystemStreamPathHelper.getChildPath(getFile(), streamTypeName);
        return new FileSystemStreamTarget(metaService, fileSystemStreamPathHelper, this, streamTypeName, childFile);
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
                final Path rootDir = Paths.get(rootPath);
                if (!FileSystemUtil.mkdirs(rootDir, file.getParent())) {
                    // Unable to create path
                    throw new DataException("Unable to create directory for file " + file);
                }

                // If the file already exists then delete it.
                if (Files.exists(file)) {
                    LOGGER.warn("About to overwrite file: " + FileUtil.getCanonicalPath(file));
                    if (!FileSystemUtil.deleteAnyPath(file)) {
                        LOGGER.error("getOutputStream() - Unable to delete existing files for new stream target");
                        throw new DataException("Unable to delete existing files for new stream target " + file);
                    }
                }

                outputStream = fileSystemStreamPathHelper.getOutputStream(streamType, file);
            } catch (final IOException ioEx) {
                LOGGER.error("getOutputStream() - " + ioEx.getMessage());
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
