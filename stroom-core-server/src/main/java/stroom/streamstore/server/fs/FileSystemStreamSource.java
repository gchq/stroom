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

package stroom.streamstore.server.fs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import stroom.io.StreamCloser;
import stroom.streamstore.server.StreamSource;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.streamstore.shared.StreamVolume;
import stroom.util.logging.StroomLogger;
import stroom.util.zip.HeaderMap;

/**
 * A file system implementation of StreamSource.
 */
public final class FileSystemStreamSource implements StreamSource {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(FileSystemStreamSource.class);

    private Stream stream;
    private StreamVolume volume;
    private StreamType streamType;
    private HeaderMap attributeMap;
    private InputStream inputStream;
    private File file;

    private FileSystemStreamSource parent;

    private final StreamCloser streamCloser = new StreamCloser();

    /**
     * Creates a new file system stream source.
     *
     * @return A new file system stream source or null if a file cannot be
     *         created.
     */
    public static FileSystemStreamSource create(final Stream stream, final StreamVolume volume,
            final StreamType streamType) {
        return new FileSystemStreamSource(stream, volume, streamType);
    }

    private FileSystemStreamSource(final Stream stream, final StreamVolume volume, final StreamType streamType) {
        this.stream = stream;
        this.volume = volume;
        this.streamType = streamType;

        validate();
    }

    private FileSystemStreamSource(final FileSystemStreamSource parent, final StreamType streamType, final File file) {
        this.stream = parent.stream;
        this.volume = parent.volume;
        this.parent = parent;
        this.streamType = streamType;
        this.file = file;
        validate();
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

    public File getFile() {
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
                LOGGER.error("getInputStream", ioEx);
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
        File childFile = FileSystemStreamTypeUtil.createChildStreamFile(getFile(), type);
        boolean lazy = type.isStreamTypeLazy();
        boolean isFile = childFile.isFile();
        if (lazy || isFile) {
            final FileSystemStreamSource child = new FileSystemStreamSource(this, type, childFile);
            streamCloser.add(child);
            return child;
        } else {
            return null;
        }
    }

    @Override
    public HeaderMap getAttributeMap() {
        if (parent != null) {
            return parent.getAttributeMap();
        }
        if (attributeMap == null) {
            attributeMap = new HeaderMap();
            try {
                attributeMap.read(getChildStream(StreamType.MANIFEST).getInputStream(), true);
            } catch (Exception ex) {
                LOGGER.error("getAttributeMap()", ex);
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
