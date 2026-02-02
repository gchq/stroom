/*
 * Copyright 2016-2026 Crown Copyright
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
import stroom.aws.s3.impl.S3Manager.S3ObjectInfo;
import stroom.data.store.api.DataException;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.Source;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.meta.api.AttributeMap;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.task.api.ExecutorProvider;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A file system implementation of Source.
 */
final class S3ZstdSource implements Source {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3ZstdSource.class);

    private final Map<Long, S3InputStreamProvider> partMap = new HashMap<>();
    private final Path tempDir;
    private final String s3Location;
    private final S3StreamTypeExtensions s3StreamTypeExtensions;
    private final ExecutorProvider executorProvider;
    private final S3Manager s3Manager;
    private final S3ZstdStore s3ZstdStore;
    private final Meta meta;
    private final DataVolume dataVolume;
    /**
     * The {@link FileKey} of the main stream type
     */
    private final FileKey parentFileKey;

    private boolean closed;
    private AttributeMap attributeMap;
    private Set<String> childTypes;
    private ZstdSegmentationType zstdSegmentationType;

    public S3ZstdSource(final S3ZstdStore s3ZstdStore,
                        final Path tempDir,
                        final String s3Location,
                        final S3Manager s3Manager,
                        final Meta meta,
                        final DataVolume dataVolume,
                        final S3StreamTypeExtensions s3StreamTypeExtensions,
                        final ExecutorProvider executorProvider) {
        this.s3StreamTypeExtensions = s3StreamTypeExtensions;
        this.executorProvider = executorProvider;
        LOGGER.debug("ctor() - tempDir: {}, s3Location: {}, meta: {}, dataVolume: {}",
                tempDir, s3Location, meta, dataVolume);
        this.s3ZstdStore = s3ZstdStore;
        this.tempDir = tempDir;
        this.s3Location = s3Location;
        this.s3Manager = s3Manager;
        this.meta = meta;
        this.dataVolume = dataVolume;
//        this.counts = countTypes();
        this.parentFileKey = FileKey.of(dataVolume, meta);
    }

    @Override
    public Meta getMeta() {
        return meta;
    }

    @Override
    public AttributeMap getAttributes() {
        if (attributeMap == null) {
            attributeMap = new AttributeMap();
            readManifest(attributeMap);
        }
        return attributeMap;
    }

    private Set<String> getChildTypes() {
        if (childTypes == null) {
            childTypes = new HashSet<>(getAttributes().getAsList(MetaFields.CHILD_TYPES.getFldName()));
            LOGGER.debug(() -> LogUtil.message("getChildTypes() - childTypes: {}", NullSafe.sort(childTypes)));
        }
        return childTypes;
    }

    private ZstdSegmentationType getSegmentationType() {
        if (zstdSegmentationType == null) {
            final String segmentationType = getAttributes().get(MetaFields.SEGMENTATION_TYPE.getFldName());
            LOGGER.debug("getSegmentationType() - segmentationType: {}", segmentationType);
            zstdSegmentationType = ZstdSegmentationType.fromString(segmentationType);
            return Objects.requireNonNull(zstdSegmentationType, () ->
                    "Unknown zstdSegmentationType for meta: " + meta);
        }
        return zstdSegmentationType;
    }

    private void readManifest(final AttributeMap attributeMap) {
        LOGGER.debug("readManifest() - attributeMap: {}", attributeMap);
        final S3ObjectInfo objectInfo = s3Manager.getObjectInfo(meta, null);
        final AttributeMap manifest = objectInfo.manifest();
        LOGGER.debug("readManifest() - manifest: {}", manifest);
        attributeMap.putAll(manifest);

        attributeMap.put("S3 Location", s3Location);

        try {
            try (final Stream<Path> stream = Files.list(tempDir)) {
                final String fileNames = stream
                        .map(FileUtil::getCanonicalPath)
                        .sorted()
                        .collect(Collectors.joining("\n"));
                attributeMap.put("Temp Files", fileNames);
            }
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private void closeAllStreams() throws IOException {
        LOGGER.debug("closeAllStreams()");
        // If we get error on closing the stream we must return it to the caller
        IOException streamCloseException = null;

        // Close the streams.
        for (final InputStreamProvider inputStreamProvider : partMap.values()) {
            try {
                inputStreamProvider.close();
            } catch (final ClosedByInterruptException e) {
                // WE expect these exceptions if a user is trying to terminate.
                LOGGER.debug(() -> "closeStreamTarget() - Error on closing stream " + this, e);
                streamCloseException = e;
            } catch (final IOException e) {
                LOGGER.error(() -> "closeStreamTarget() - Error on closing stream " + this, e);
                streamCloseException = e;
            }
        }

        if (streamCloseException != null) {
            throw streamCloseException;
        }
    }

    @Override
    public void close() {
        if (closed) {
            throw new DataException("Source already closed");
        }

        try {
            // Close the stream target.
            // If we get error on closing the stream we must return it to the caller
            IOException streamCloseException = null;
            try {
                closeAllStreams();
            } catch (final IOException e) {
                streamCloseException = e;
            }
            partMap.clear();

            if (streamCloseException != null) {
                try {
                    FileUtil.deleteDir(tempDir);
                } catch (final RuntimeException e) {
                    LOGGER.debug(e::getMessage, e);
                }

                LOGGER.error("closeStreamSource() - Error on closing stream {}", this, streamCloseException);
                throw new UncheckedIOException(streamCloseException);
            } else {
                s3ZstdStore.release(meta, tempDir);
            }

        } finally {
            closed = true;
        }
    }

    @Override
    public InputStreamProvider get(final long partIdx) {
        final S3InputStreamProvider s3InputStreamProvider = new S3InputStreamProvider(
                tempDir, partIdx, this, meta);
        partMap.put(partIdx, s3InputStreamProvider);
        return s3InputStreamProvider;
    }

    private long getPartCount(final String childStreamType) {
        if (getSegmentationType() == ZstdSegmentationType.PARTS) {
            final Optional<ZstdSeekTable> optSeekTable = s3ZstdStore.getZstdSeekTableCache().getSeekTable(
                    s3Manager,
                    dataVolume,
                    meta,
                    childStreamType);
            LOGGER.debug("getPartCount() - parentFileKey: {}, childStreamType: {}, optSeekTable: {}",
                    parentFileKey, childStreamType, optSeekTable);
            // If there is no seek table then it is a normal non-seekable zstd file, i.e. just one part
            return optSeekTable.map(ZstdSeekTable::getFrameCount)
                    .orElse(1);
        } else {
            // Only ever one part for segmented data
            return 1;
        }
    }

    @Override
    public long count() {
        final long count = getPartCount(null);
        LOGGER.debug("count() - Returning count: {}", count);
        return count;
    }

    @Override
    public long count(final String childStreamType) throws IOException {
        final long count = getPartCount(childStreamType);
        LOGGER.debug("count() - childStreamType: {}, returning count: {}", childStreamType, count);
        return count;
    }

    private ZstdSeekTable getZstdSeekTable(final String childStreamType) {
        return s3ZstdStore.getZstdSeekTableCache()
                .getSeekTable(s3Manager, dataVolume, meta, childStreamType)
                .orElse(null);
    }

    private ZstdFrameSupplier getZstdFrameSupplier(final String childStreamType) {
        final FileKey fileKey = getFileKey(childStreamType);
        final String s3Key = s3StreamTypeExtensions.getkey(fileKey);
        return new S3FrameSupplierImpl(
                executorProvider,
                s3Manager,
                meta,
                childStreamType,
                s3Key,
                tempDir,
                s3StreamTypeExtensions);
    }

    private ZstdDictionary getZstdDictionary(final ZstdSeekTable zstdSeekTable) {
        if (zstdSeekTable != null && zstdSeekTable.hasDictionary()) {
            final String uuid = zstdSeekTable.getDictionaryUuid().map(UUID::toString).orElse(null);
            return s3ZstdStore.getZstdDictionaryService()
                    .getZstdDictionary(uuid, dataVolume)
                    .orElseThrow();
        } else {
            return null;
        }
    }

    private ZstdSegmentInputStream createZstdSegmentInputStream(final String childStreamType) {
        final ZstdSeekTable zstdSeekTable = getZstdSeekTable(childStreamType);
        final ZstdFrameSupplier zstdFrameSupplier = getZstdFrameSupplier(childStreamType);
        final ZstdDictionary zstdDictionary = getZstdDictionary(zstdSeekTable);
        return new ZstdSegmentInputStream(
                zstdSeekTable,
                zstdFrameSupplier,
                zstdDictionary,
                s3ZstdStore.getHeapBufferPool());
    }

    private FileKey getFileKey(final String childStreamType) {
        return parentFileKey.withChildStreamType(childStreamType);
    }


    // --------------------------------------------------------------------------------


    private static class S3InputStreamProvider implements InputStreamProvider {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3InputStreamProvider.class);

        private final Path dir;
        private final long partIdx;
        private final long partNo;
        private final S3ZstdSource s3ZstdSource;
        private final Meta meta;
        private final Map<String, SegmentInputStream> typeToInputStreamMap = new HashMap<>(10);
        private SegmentInputStream dataStream;

        public S3InputStreamProvider(final Path dir,
                                     final long partIdx,
                                     final S3ZstdSource s3ZstdSource,
                                     final Meta meta) {
            this.dir = dir;
            this.partIdx = partIdx;
            this.partNo = partIdx + 1;
            this.s3ZstdSource = s3ZstdSource;
            this.meta = meta;
            LOGGER.debug(() -> LogUtil.message("ctor() - dir: {}, meta: {}, partIdx: {}, dataVolume: {}",
                    dir, meta, partIdx, s3ZstdSource.dataVolume));
        }

        @Override
        public SegmentInputStream get() {
            LOGGER.debug("get() - meta: {}, dir: {}, partIdx: {}", meta, dir, partIdx);
            if (dataStream != null) {
                throw new RuntimeException("Unexpected get(). Should only be called once partNo " + partNo);
            }
            dataStream = create(null);
            return dataStream;
        }

        @Override
        public SegmentInputStream get(final String childStreamType) {
            LOGGER.debug("get() - meta: {}, childStreamType: {}, dir: {}, partIdx: {}",
                    meta, childStreamType, dir, partIdx);
            if (childStreamType == null) {
                return get();
            } else {
                if (typeToInputStreamMap.containsKey(childStreamType)) {
                    throw new RuntimeException("Unexpected get(). Should only be called once partNo " + partNo);
                }
                return typeToInputStreamMap.computeIfAbsent(childStreamType, ignored ->
                        create(childStreamType));
            }
        }

        private SegmentInputStream create(final String childStreamType) {
            final ZstdSegmentInputStream zstdSegmentInputStream = s3ZstdSource.createZstdSegmentInputStream(
                    childStreamType);

            // TODO We could do with a better way to determine the type of file we are dealing with
            final ZstdSegmentationType segmentationType = s3ZstdSource.getSegmentationType();

            LOGGER.debug("create() - meta: {}, childStreamType: {}, segmentationType: {}",
                    meta, childStreamType, segmentationType);

            final SegmentInputStream segmentInputStream;
            if (ZstdSegmentationType.PARTS == segmentationType) {
                zstdSegmentInputStream.include(partIdx);
                // Wrap the stream to prevent the caller from trying to access segments in within the
                // part.
                segmentInputStream = new WrappedInputStream(partNo, zstdSegmentInputStream, false);
            } else {
                segmentInputStream = zstdSegmentInputStream;
            }
            return segmentInputStream;
        }

        @Override
        public Set<String> getChildTypes() {
            return s3ZstdSource.getChildTypes();
        }

        private List<SegmentInputStream> getAllInputStreams() {
            return Stream.concat(
                            Stream.of(dataStream),
                            typeToInputStreamMap.values().stream())
                    .toList();
        }

        @Override
        public void close() throws IOException {
            IOException exception = null;
            for (final SegmentInputStream segmentInputStream : getAllInputStreams()) {
                try {
                    segmentInputStream.close();
                } catch (final IOException e) {
                    LOGGER.debug(e::getMessage, e);
                    if (exception == null) {
                        exception = e;
                    }
                }
            }
            if (exception != null) {
                throw exception;
            }
        }
    }


    // --------------------------------------------------------------------------------


    private static class WrappedInputStream extends SegmentInputStream {

        private final long partNo;
        private final SegmentInputStream delegate;
        private final boolean allowSegments;

        private WrappedInputStream(final long partNo,
                                   final SegmentInputStream delegate,
                                   final boolean allowSegments) {
            this.partNo = partNo;
            this.delegate = delegate;
            this.allowSegments = allowSegments;
        }

        @Override
        public long count() {
            return allowSegments
                    ? delegate.count()
                    : 1;
        }

        @Override
        public void include(final long segment) {
            if (allowSegments) {
                delegate.include(segment);
            } else {
                throw new UnsupportedOperationException("Multiple segments are not supported");
            }
        }

        @Override
        public void includeAll() {
            if (allowSegments) {
                delegate.includeAll();
            } else {
                throw new UnsupportedOperationException("Multiple segments are not supported");
            }
        }

        @Override
        public void exclude(final long segment) {
            throw new UnsupportedOperationException("Deprecated method");
        }

        @Override
        public void excludeAll() {
            throw new UnsupportedOperationException("Deprecated method");
        }

        @Override
        public long size() {
            return delegate.size();
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(final byte @NonNull [] b) throws IOException {
            return delegate.read(b);
        }

        @Override
        public int read(final byte @NonNull [] b, final int off, final int len) throws IOException {
            return delegate.read(b, off, len);
        }
    }
}
