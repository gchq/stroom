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
import stroom.io.StreamCloser;
import stroom.streamstore.StreamSource;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamstore.shared.StreamType;
import stroom.streamstore.shared.StreamVolume;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A file system implementation of StreamSource.
 */
public final class FileSystemStreamSource implements StreamSource {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemStreamSource.class);
    private final StreamCloser streamCloser = new StreamCloser();
    private Stream stream;
    private StreamVolume volume;
    private StreamType streamType;
    private MetaMap attributeMap;
    private InputStream inputStream;
    private Path file;
    private FileSystemStreamSource parent;

    private FileSystemStreamSource(final Stream stream, final StreamVolume volume, final StreamType streamType) {
        this.stream = stream;
        this.volume = volume;
        this.streamType = streamType;

        validate();
    }

    private FileSystemStreamSource(final FileSystemStreamSource parent, final StreamType streamType, final Path file) {
        this.stream = parent.stream;
        this.volume = parent.volume;
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
    public static FileSystemStreamSource create(final Stream stream, final StreamVolume volume,
                                                final StreamType streamType) {
        return new FileSystemStreamSource(stream, volume, streamType);
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
                file = FileSystemStreamTypeUtil.createRootStreamFile(volume.getVolume(), stream, getType());
            } else {
                file = FileSystemStreamTypeUtil.createChildStreamFile(parent.getFile(), getType());
            }
        }
        return file;
    }

    @Override
    public InputStream getInputStream() {
        // First Call?
        if (inputStream == null) {
            try {
                inputStream = FileSystemStreamTypeUtil.getInputStream(streamType, getFile());
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
    public StreamSource getChildStream(final StreamType type) {
        Path childFile = FileSystemStreamTypeUtil.createChildStreamFile(getFile(), type);
        boolean lazy = type.isStreamTypeLazy();
        boolean isFile = Files.isRegularFile(childFile);
        if (lazy || isFile) {
            final FileSystemStreamSource child = new FileSystemStreamSource(this, type, childFile);
            streamCloser.add(child);
            return child;
        } else {
            return null;
        }
    }

    @Override
    public MetaMap getAttributeMap() {
        if (parent != null) {
            return parent.getAttributeMap();
        }
        if (attributeMap == null) {
            attributeMap = new MetaMap();
            try {
                final StreamSource streamSource = getChildStream(StreamType.MANIFEST);
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
    public StreamType getType() {
        return streamType;
    }

    @Override
    public StreamSource getParent() {
        return parent;
    }

    public StreamVolume getStreamVolume() {
        return volume;
    }
}
