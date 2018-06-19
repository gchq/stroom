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
import stroom.io.StreamCloser;
import stroom.data.store.api.StreamSource;
import stroom.data.meta.api.Stream;
import stroom.data.meta.api.StreamStatus;
import stroom.streamstore.shared.StreamTypeNames;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A file system implementation of StreamSource.
 */
public final class FileSystemStreamSource implements StreamSource {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemStreamSource.class);

    private final FileSystemStreamPathHelper fileSystemStreamPathHelper;
    private final StreamCloser streamCloser = new StreamCloser();
    private Stream stream;
    private String rootPath;
    private String streamType;
    private AttributeMap attributeMap;
    private InputStream inputStream;
    private Path file;
    private FileSystemStreamSource parent;

    private FileSystemStreamSource(final FileSystemStreamPathHelper fileSystemStreamPathHelper,
                                   final Stream stream,
                                   final String rootPath,
                                   final String streamType) {
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.stream = stream;
        this.rootPath = rootPath;
        this.streamType = streamType;

        validate();
    }

    private FileSystemStreamSource(final FileSystemStreamPathHelper fileSystemStreamPathHelper,
                                   final FileSystemStreamSource parent,
                                   final String streamType,
                                   final Path file) {
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.stream = parent.stream;
        this.rootPath = parent.rootPath;
        this.parent = parent;
        this.streamType = streamType;
        this.file = file;
        validate();
    }

    /**
     * Creates a new file system stream source.
     *
     * @return A new file system stream source or null if a file cannot be
     * created.
     */
    static FileSystemStreamSource create(final FileSystemStreamPathHelper fileSystemStreamPathHelper,
                                         final Stream stream,
                                         final String rootPath,
                                         final String streamType) {
        return new FileSystemStreamSource(fileSystemStreamPathHelper, stream, rootPath, streamType);
    }

    private void validate() {
        if (streamType == null) {
            throw new IllegalStateException("Must have a stream type");
        }
    }

    @Override
    public void close() throws IOException {
        streamCloser.close();
    }

    public Path getFile() {
        if (file == null) {
            if (parent == null) {
                file = fileSystemStreamPathHelper.createRootStreamFile(rootPath, stream, getStreamTypeName());
            } else {
                file = fileSystemStreamPathHelper.createChildStreamFile(parent.getFile(), getStreamTypeName());
            }
        }
        return file;
    }

    @Override
    public InputStream getInputStream() {
        // First Call?
        if (inputStream == null) {
            try {
                inputStream = fileSystemStreamPathHelper.getInputStream(streamType, getFile());
                streamCloser.add(inputStream);
            } catch (IOException ioEx) {
                // Don't log this as an error if we expect this stream to have been deleted or be locked.
                if (stream == null || StreamStatus.UNLOCKED.equals(stream.getStatus())) {
                    LOGGER.error("getInputStream", ioEx);
                }

                throw new RuntimeException(ioEx);
            }
        }
        return inputStream;
    }

    @Override
    public Stream getStream() {
        return stream;
    }

    public void setStream(final Stream stream) {
        this.stream = stream;
    }

    @Override
    public String getStreamTypeName() {
        return streamType;
    }

    @Override
    public StreamSource getChildStream(final String streamTypeName) {
        Path childFile = fileSystemStreamPathHelper.createChildStreamFile(getFile(), streamTypeName);
        boolean lazy = fileSystemStreamPathHelper.isStreamTypeLazy(streamTypeName);
        boolean isFile = Files.isRegularFile(childFile);
        if (lazy || isFile) {
            final FileSystemStreamSource child = new FileSystemStreamSource(fileSystemStreamPathHelper, this, streamTypeName, childFile);
            streamCloser.add(child);
            return child;
        } else {
            return null;
        }
    }

    @Override
    public AttributeMap getAttributeMap() {
        if (parent != null) {
            return parent.getAttributeMap();
        }
        if (attributeMap == null) {
            attributeMap = new AttributeMap();
            try {
                final StreamSource streamSource = getChildStream(StreamTypeNames.MANIFEST);
                if (streamSource != null) {
                    attributeMap.read(streamSource.getInputStream(), true);
                }
            } catch (final RuntimeException | IOException e) {
                LOGGER.error("getAttributeMap()", e);
            }
        }
        return attributeMap;
    }

    @Override
    public StreamSource getParent() {
        return parent;
    }
}
