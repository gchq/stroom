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
import stroom.data.meta.api.AttributeMap;
import stroom.data.meta.api.Data;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.StreamException;
import stroom.data.store.api.StreamTarget;
import stroom.feed.AttributeMapUtil;
import stroom.io.SeekableOutputStream;

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
final class FileSystemStreamTarget implements StreamTarget, NestedOutputStreamFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemStreamTarget.class);

    private final FileSystemStreamPathHelper fileSystemStreamPathHelper;
    private final Set<String> volumePaths;
    private final String streamType;
    private final List<FileSystemStreamTarget> childrenAccessed = new ArrayList<>();
    private Data stream;
    private boolean closed;
    private boolean append;
    private AttributeMap attributeMap;
    private OutputStream outputStream;
    private Set<Path> files;
    private FileSystemStreamTarget parent;

    private FileSystemStreamTarget(final FileSystemStreamPathHelper fileSystemStreamPathHelper,
                                   final Data requestMetaData,
                                   final Set<String> volumePaths,
                                   final String streamType,
                                   final boolean append) {
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.stream = requestMetaData;
        this.volumePaths = volumePaths;
        this.streamType = streamType;
        this.append = append;

        validate();
    }

    private FileSystemStreamTarget(final FileSystemStreamPathHelper fileSystemStreamPathHelper,
                                   final FileSystemStreamTarget parent,
                                   final String streamType,
                                   final Set<Path> files) {
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.stream = parent.stream;
        this.volumePaths = parent.volumePaths;
        this.parent = parent;
        this.append = parent.append;

        this.streamType = streamType;
        this.files = files;

        validate();
    }

    /**
     * Creates a new file system stream target.
     */
    static FileSystemStreamTarget create(final FileSystemStreamPathHelper fileSystemStreamPathHelper,
                                         final Data stream,
                                         final Set<String> volumePaths,
                                         final String streamType,
                                         final boolean append) {
        return new FileSystemStreamTarget(fileSystemStreamPathHelper, stream, volumePaths, streamType, append);
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
    public Data getStream() {
        return stream;
    }

    Set<Path> getFiles(final boolean createPath) {
        if (files == null) {
            files = new HashSet<>();
            if (parent == null) {
                for (final String rootPath : volumePaths) {
                    final Path aFile = fileSystemStreamPathHelper.createRootStreamFile(rootPath, stream,
                            streamType);
                    if (createPath) {
                        final Path rootDir = Paths.get(rootPath);
                        if (!FileSystemUtil.mkdirs(rootDir, aFile.getParent())) {
                            // Unable to create path
                            throw new StreamException("Unable to create directory for file " + aFile);

                        }
                    }
                    files.add(aFile);
                }
            } else {
                files.addAll(parent.getFiles(false).stream()
                        .map(pFile -> fileSystemStreamPathHelper.createChildStreamFile(pFile, streamType))
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


    @Override
    public OutputStreamProvider getOutputStreamProvider() {
        return new OutputStreamProviderImpl(stream, this);
    }

//    @Override
//    SegmentOutputStream getSegmentOutputStream() {
//        return new RASegmentOutputStream(getOutputStream(),
//                () -> addChild(InternalStreamTypeNames.SEGMENT_INDEX).getOutputStream());
//    }

    void setMetaData(final Data stream) {
        this.stream = stream;
    }

    @Override
    public NestedOutputStreamFactory addChild(final String streamTypeName) {
        return add(streamTypeName);
    }

    FileSystemStreamTarget add(final String streamTypeName) {
        if (!closed && InternalStreamTypeNames.MANIFEST.equals(streamTypeName)) {
            throw new RuntimeException("Stream store is responsible for the child manifest stream");
        }
        final Set<Path> childFile = fileSystemStreamPathHelper.createChildStreamPath(getFiles(false), streamTypeName);
        final FileSystemStreamTarget child = new FileSystemStreamTarget(fileSystemStreamPathHelper, this, streamTypeName, childFile);
        childrenAccessed.add(child);
        return child;
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

                outputStream = fileSystemStreamPathHelper.getOutputStream(streamType, files);
            } catch (final IOException ioEx) {
                LOGGER.error("getOutputStream() - " + ioEx.getMessage());
                // No reason to get a IO on opening the out stream .... fail in
                // a heap
                throw new StreamException(ioEx);
            }
        }
        return outputStream;
    }

//    @Override
//    public StreamTarget addChildStream(final String typeName) {
//        return add(typeName);
//    }

    StreamTarget getParent() {
        return parent;
    }

//    private String getTypeName() {
//        return streamType;
//    }
//
//    //    @Override
//    StreamTarget getChildStream(final String typeName) {
//        for (final FileSystemStreamTarget child : childrenAccessed) {
//            if (Objects.equals(child.getTypeName(), typeName)) {
//                return child;
//            }
//        }
//        return null;
//    }

    boolean isAppend() {
        return append;
    }

    @Override
    public AttributeMap getAttributes() {
        if (parent != null) {
            return parent.getAttributes();
        }
        if (attributeMap == null) {
            attributeMap = new AttributeMap();
            if (isAppend()) {
                final Path manifestFile = fileSystemStreamPathHelper
                        .createChildStreamFile(getFiles(false).iterator().next(), InternalStreamTypeNames.MANIFEST);
                if (Files.isRegularFile(manifestFile)) {
                    try (final InputStream inputStream = Files.newInputStream(manifestFile)) {
                        AttributeMapUtil.read(inputStream, true, attributeMap);
                    } catch (final IOException e) {
                        LOGGER.error("getAttributes()", e);
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
