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

package stroom.streamstore.fs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.MetaMap;
import stroom.io.SeekableOutputStream;
import stroom.streamstore.StreamException;
import stroom.streamstore.StreamTarget;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.streamstore.shared.StreamVolume;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A file system implementation of StreamTarget.
 */
public final class FileSystemStreamTarget implements StreamTarget {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemStreamTarget.class);

    private final Set<StreamVolume> metaDataVolume;
    private final StreamType streamType;
    private final List<FileSystemStreamTarget> childrenAccessed = new ArrayList<>();
    private Stream stream;
    private boolean closed;
    private boolean append;
    private MetaMap attributeMap;
    private OutputStream outputStream;
    private Set<Path> files;
    private FileSystemStreamTarget parent;

    private FileSystemStreamTarget(final Stream requestMetaData, final Set<StreamVolume> metaDataVolume,
                                   final StreamType streamType, final boolean append) {
        this.stream = requestMetaData;
        this.metaDataVolume = metaDataVolume;
        this.streamType = streamType;
        this.append = append;

        validate();
    }

    private FileSystemStreamTarget(final FileSystemStreamTarget aParent, final StreamType aStreamType,
                                   final Set<Path> aFiles) {
        this.stream = aParent.stream;
        this.metaDataVolume = aParent.metaDataVolume;
        this.parent = aParent;
        this.append = aParent.append;

        this.streamType = aStreamType;
        this.files = aFiles;

        validate();
    }

    /**
     * Creates a new file system stream target.
     */
    public static FileSystemStreamTarget create(final Stream stream, final Set<StreamVolume> metaDataVolume,
                                                final StreamType streamType, final boolean append) {
        return new FileSystemStreamTarget(stream, metaDataVolume, streamType, append);
    }

    private void validate() {
        if (streamType == null) {
            throw new IllegalStateException("Must have a stream type");
        }
    }

    @Override
    public void close() throws IOException {
        if (outputStream != null && !closed) {
            closed = true;
            outputStream.close();
        }
        closed = true;
        // Close off any open kids .... closing the parent
        // closes kids (the caller can also close the kid off if they like).
        for (final FileSystemStreamTarget child : childrenAccessed) {
            child.close();
        }
    }

    Long getStreamSize() {
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

    Long getTotalFileSize() {
        long total = 0;
        final Set<Path> fileSet = getFiles(false);

        for (final Path file : fileSet) {
            try {
                total += Files.size(file);
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
        return total;
    }

    @Override
    public Stream getStream() {
        return stream;
    }

    Set<Path> getFiles(final boolean createPath) {
        if (files == null) {
            files = new HashSet<>();
            if (parent == null) {
                for (final StreamVolume smVolume : metaDataVolume) {
                    final Path aFile = FileSystemStreamTypeUtil.createRootStreamFile(smVolume.getVolume(), stream,
                            streamType);
                    if (createPath) {
                        final Path rootDir = Paths.get(smVolume.getVolume().getPath());
                        if (!FileSystemUtil.mkdirs(rootDir, aFile.getParent())) {
                            // Unable to create path
                            throw new StreamException("Unable to create directory for file " + aFile);

                        }
                    }
                    files.add(aFile);
                }
            } else {
                files.addAll(parent.getFiles(false).stream()
                        .map(pFile -> FileSystemStreamTypeUtil.createChildStreamFile(pFile, getType()))
                        .collect(Collectors.toList()));
            }
            if (LOGGER.isDebugEnabled()) {
                for (final Path fileItem : files) {
                    LOGGER.debug("getFile() " + fileItem);
                }
            }
        }
        return files;
    }

    /**
     * Gets the output stream for this stream target.
     */
    @Override
    public OutputStream getOutputStream() {
        if (outputStream == null) {
            try {
                // Ensure File Is New and the path exists
                files = getFiles(true);

                if (!FileSystemUtil.deleteAnyPath(files)) {
                    LOGGER.error("getOutputStream() - Unable to delete existing files for new stream target");
                    throw new StreamException("Unable to delete existing files for new stream target " + files);
                }

                outputStream = FileSystemStreamTypeUtil.getOutputStream(streamType, files);
            } catch (final IOException ioEx) {
                LOGGER.error("getOutputStream() - " + ioEx.getMessage());
                // No reason to get a IO on opening the out stream .... fail in
                // a heap
                throw new StreamException(ioEx);
            }
        }
        return outputStream;
    }

    public void setMetaData(final Stream stream) {
        this.stream = stream;
    }

    @Override
    public StreamTarget addChildStream(final StreamType type) {
        if (!closed && StreamType.MANIFEST.equals(type)) {
            throw new RuntimeException("Stream store is responsible for the child manifest stream");
        }
        final Set<Path> childFile = FileSystemStreamTypeUtil.createChildStreamPath(getFiles(false), type);
        final FileSystemStreamTarget child = new FileSystemStreamTarget(this, type, childFile);
        childrenAccessed.add(child);
        return child;
    }

    @Override
    public StreamTarget getParent() {
        return parent;
    }

    @Override
    public StreamType getType() {
        return streamType;
    }

    @Override
    public StreamTarget getChildStream(final StreamType type) {
        for (final FileSystemStreamTarget child : childrenAccessed) {
            if (child.getType() == type) {
                return child;
            }
        }
        return null;
    }

    @Override
    public boolean isAppend() {
        return append;
    }

    @Override
    public MetaMap getAttributeMap() {
        if (parent != null) {
            return parent.getAttributeMap();
        }
        if (attributeMap == null) {
            attributeMap = new MetaMap();
            if (isAppend()) {
                final Path manifestFile = FileSystemStreamTypeUtil
                        .createChildStreamFile(getFiles(false).iterator().next(), StreamType.MANIFEST);
                if (Files.isRegularFile(manifestFile)) {
                    try (final InputStream inputStream = Files.newInputStream(manifestFile)) {
                        attributeMap.read(inputStream, true);
                    } catch (final IOException e) {
                        LOGGER.error("getAttributeMap()", e);
                    }
                }
            }
        }
        return attributeMap;
    }

    @Override
    public String toString() {
        return "streamId=" + stream.getId();
    }
}
