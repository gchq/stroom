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
import stroom.data.store.api.InputStreamProvider;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A file system implementation of Source.
 */
final class FsSource implements InternalSource, SegmentInputStreamProviderFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsSource.class);

    private final FsPathHelper fileSystemStreamPathHelper;
    private final Map<String, FsSource> childMap = new HashMap<>();
    private final Map<String, SegmentInputStreamProvider> inputStreamMap = new HashMap<>(10);
    private final Path volumePath;
    private final String streamType;
    private final FsSource parent;
    private AttributeMap attributeMap;
    private InputStream inputStream;
    private Path file;

    private final Meta meta;
    private boolean closed;
    private Long count;

    private FsSource(final FsPathHelper fileSystemStreamPathHelper,
                     final Meta meta,
                     final Path volumePath,
                     final String streamType) {
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.meta = meta;
        this.volumePath = volumePath;
        this.parent = null;
        this.streamType = streamType;

        validate();
    }

    private FsSource(final FsPathHelper fileSystemStreamPathHelper,
                     final FsSource parent,
                     final String streamType,
                     final Path file) {
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.meta = parent.meta;
        this.volumePath = parent.volumePath;
        this.parent = parent;
        this.streamType = streamType;
        this.file = file;
        validate();
    }

    /**
     * Creates a new file system stream source.
     *
     * @return A new file system source.
     */
    static FsSource create(final FsPathHelper fileSystemStreamPathHelper,
                           final Meta meta,
                           final Path rootPath,
                           final String streamType) {
        return new FsSource(fileSystemStreamPathHelper, meta, rootPath, streamType);
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
            readManifest(attributeMap);
        }
        return attributeMap;
    }

    private void readManifest(final AttributeMap attributeMap) {
        final Path file = getFile();
        final Path manifestFile = fileSystemStreamPathHelper.getChildPath(file, InternalStreamTypeNames.MANIFEST);
        if (Files.isRegularFile(manifestFile)) {
            try (final InputStream inputStream = Files.newInputStream(manifestFile)) {
                AttributeMapUtil.read(inputStream, attributeMap);
            } catch (final IOException e) {
                LOGGER.error(e::getMessage, e);
            }

            try {
                final List<Path> files = fileSystemStreamPathHelper.getFiles(file);
                attributeMap.putCollection("Files", files.stream()
                        .map(FileUtil::getCanonicalPath)
                        .toList());
            } catch (final IOException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
    }

    private Path getFile() {
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
            throw new DataException("Source already closed");
        }

        try {
            // Close the stream target.
            try {
                if (inputStream != null) {
                    inputStream.close();
                }

                // Close off any open kids .... closing the parent
                // closes kids (the caller can also close the kid off if they like).
                childMap.forEach((k, v) -> v.close());
                childMap.clear();
            } catch (final IOException e) {
                LOGGER.error("closeStreamSource() - Error on closing stream {}", this, e);
                throw new UncheckedIOException(e);
            }

        } finally {
            closed = true;
        }
    }

    @Override
    public InputStreamProvider get(final long index) {
        return new InputStreamProviderImpl(meta, this, index);
    }

    @Override
    public long count() {
        if (count == null) {
            final InputStream data = getInputStream();
            final InputStream boundaryIndex = getChildInputStream(InternalStreamTypeNames.BOUNDARY_INDEX);
            count = new RASegmentInputStream(data, boundaryIndex).count();
        }
        return count;
    }

    @Override
    public long count(final String childStreamType) throws IOException {
        final InternalSource childSource = getChild(childStreamType);
        Objects.requireNonNull(childSource, () ->
                LogUtil.message("No source found for childStreamType {}", childStreamType));

        return childSource.count();
    }

    @Override
    public SegmentInputStreamProvider getSegmentInputStreamProvider(final String streamTypeName) {
        return inputStreamMap.computeIfAbsent(streamTypeName, k -> {
            final InternalSource source = getChild(k);
            if (source == null) {
                return null;
            }
            return new SegmentInputStreamProvider(source, k);
        });
    }

    @Override
    public Set<String> getChildTypes() {

        // You may have files like this:
        // ./store/RAW_EVENTS/2020/09/16/DATA_FETCHER=003.revt.bdy.dat
        // ./store/RAW_EVENTS/2020/09/16/DATA_FETCHER=003.revt.bgz
        // ./store/RAW_EVENTS/2020/09/16/DATA_FETCHER=003.revt.ctx.bdy.dat
        // ./store/RAW_EVENTS/2020/09/16/DATA_FETCHER=003.revt.ctx.bgz
        // ./store/RAW_EVENTS/2020/09/16/DATA_FETCHER=003.revt.meta.bdy.dat
        // ./store/RAW_EVENTS/2020/09/16/DATA_FETCHER=003.revt.meta.bgz
        // ./store/RAW_EVENTS/2020/09/16/DATA_FETCHER=003.revt.mf.dat
        // We want to ignore the internal .bdy., .seg. and .mf. ones
        // and boil it down to (in this case) Raw Events, Meta & Context

        final List<Path> allDescendantStreamFileList = fileSystemStreamPathHelper.findAllDescendantStreamFileList(
                getFile());
        return allDescendantStreamFileList.stream()
                .map(fileSystemStreamPathHelper::decodeChildStreamType)
                .collect(Collectors.toSet());
    }

    private InternalSource getChild(final String streamTypeName) {
        if (closed) {
            throw new RuntimeException("Closed");
        }

        if (streamTypeName == null) {
            return this;
        }

        return childMap.computeIfAbsent(streamTypeName, this::child);
    }

    private FsSource child(final String streamTypeName) {
        final Path childFile = fileSystemStreamPathHelper.getChildPath(getFile(), streamTypeName);
        final boolean lazy = fileSystemStreamPathHelper.isStreamTypeLazy(streamTypeName);
        final boolean isFile = Files.isRegularFile(childFile);
        if (lazy || isFile) {
            return new FsSource(fileSystemStreamPathHelper, this, streamTypeName, childFile);
        } else {
            return null;
        }
    }

//    Source getParent() {
//        return parent;
//    }

    /////////////////////////////////
    // START INTERNAL SOURCE
    /////////////////////////////////

    @Override
    public InputStream getInputStream() {
        // First Call?
        if (inputStream == null) {
            try {
                inputStream = fileSystemStreamPathHelper.getInputStream(streamType, getFile());
            } catch (final ClosedByInterruptException ioEx) {
                // Sometimes we deliberately interrupt reading so don't log the error here.
                throw new UncheckedIOException(ioEx);

            } catch (final IOException ioEx) {
                // Don't log this as an error if we expect this stream to have been deleted or be locked.
                if (meta == null || Status.UNLOCKED.equals(meta.getStatus())) {
                    LOGGER.error("getInputStream", ioEx);
                }

                throw new UncheckedIOException(ioEx);
            }
        }
        return inputStream;
    }

    @Override
    public InputStream getChildInputStream(final String type) {
        final InternalSource childSource = getChild(type);
        if (childSource != null) {
            return childSource.getInputStream();
        }
        return null;
    }

    /////////////////////////////////
    // END INTERNAL SOURCE
    /////////////////////////////////
}
