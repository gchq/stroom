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

import stroom.aws.s3.impl.S3FileExtensions;
import stroom.aws.s3.impl.S3Manager;
import stroom.aws.s3.impl.S3Manager.S3ObjectInfo;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.DataException;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.Source;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.data.store.impl.fs.FsPrefixUtil;
import stroom.data.store.impl.fs.RASegmentInputStream;
import stroom.data.store.impl.fs.UncompressedInputStream;
import stroom.meta.api.AttributeMap;
import stroom.meta.shared.Meta;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    private AttributeMap attributeMap;

    private final S3Manager s3Manager;
    private final S3ZstdStore s3ZstdStore;
    private final Meta meta;
    private final DataVolume dataVolume;
    private boolean closed;
    /**
     * The {@link FileKey} of the main stream type
     */
    private final FileKey parentFileKey;
    //    private final Map<String, Long> counts;
//    private final Map<FileKey, Long> fileSizesMap = new HashMap<>();
    private final Map<FileKey, InputStreamProvider> inputStreamProviderMap = new HashMap<>();

    public S3ZstdSource(final S3ZstdStore s3ZstdStore,
                        final Path tempDir,
                        final String s3Location,
                        final S3Manager s3Manager,
                        final Meta meta,
                        final DataVolume dataVolume) {
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
    public InputStreamProvider get(final long index) {
        final long partNo = index + 1;
        final S3InputStreamProvider s3InputStreamProvider = new S3InputStreamProvider(tempDir, partNo);
        partMap.put(partNo, s3InputStreamProvider);
        return s3InputStreamProvider;
    }

    private long getPartCount(final String childStreamType) {
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

//    private Map<String, Long> countTypes() {
//        final Map<String, Long> counts = new HashMap<>();
//        try (final Stream<Path> stream = Files.list(tempDir)) {
//            stream.forEach(path -> {
//                final String fileName = path.getFileName().toString();
//                final int index = fileName.indexOf(".");
//                if (index >= 0) {
//                    final String extension = fileName.substring(index);
//                    final String numPart = fileName.substring(0, index);
//                    final long partNo = StringUtil.dePadLong(numPart);
//                    counts.compute(extension, (ignored, v) -> {
//                        if (v == null) {
//                            return partNo;
//                        } else {
//                            return Math.max(v, partNo);
//                        }
//                    });
//                }
//            });
//        } catch (final IOException e) {
//            throw new UncheckedIOException(e);
//        }
//        return counts;
//    }


    // --------------------------------------------------------------------------------


    private static class S3InputStreamProvider implements InputStreamProvider {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3InputStreamProvider.class);

        private final Path dir;
        private final String partString;
        private final List<SegmentInputStream> segmentInputStreams = new ArrayList<>();
        private SegmentInputStream dataStream;

        public S3InputStreamProvider(final Path dir, final long partNo) {
            this.dir = dir;
            this.partString = FsPrefixUtil.padId(partNo);
            LOGGER.debug(() -> LogUtil.message("ctor() - dir: {}, partNo: {}, partString: {}",
                    dir, partNo, partString));
        }

        @Override
        public SegmentInputStream get() {
            if (dataStream != null) {
                throw new RuntimeException("Unexpected get");
            }
            LOGGER.debug("get()");
            dataStream = create(S3FileExtensions.DATA_EXTENSION);
            return dataStream;
        }

        @Override
        public SegmentInputStream get(final String childStreamType) {
            LOGGER.debug("get() - childStreamType: {}, dir: {}", childStreamType, dir);
            if (childStreamType == null) {
                return get();
            }

            final String extension = S3FileExtensions.EXTENSION_MAP.get(childStreamType);
            Objects.requireNonNull(extension, () -> "Unexpected child stream type: " + childStreamType);
            return create(extension);
        }

        private SegmentInputStream create(final String extension) {
            try {
                final String fileName = partString + extension;
                final Path dataFile = dir.resolve(fileName);
                final Path indexFile = dir.resolve(fileName + S3FileExtensions.INDEX_EXTENSION);
                LOGGER.debug("create() - dataFile: {}, indexFile: {}", dataFile, indexFile);
                final InputStream inputStream = new UncompressedInputStream(dataFile, false);
                final InputStream indexStream = new UncompressedInputStream(indexFile, true);
                final SegmentInputStream segmentInputStream = new RASegmentInputStream(inputStream, indexStream);
                segmentInputStreams.add(segmentInputStream);
                return segmentInputStream;
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public Set<String> getChildTypes() {
            final Set<String> childTypes = new HashSet<>();
            if (Files.exists(dir.resolve(partString + S3FileExtensions.META_EXTENSION))) {
                childTypes.add(StreamTypeNames.META);
            }
            if (Files.exists(dir.resolve(partString + S3FileExtensions.CONTEXT_EXTENSION))) {
                childTypes.add(StreamTypeNames.CONTEXT);
            }
            return childTypes;
        }

        @Override
        public void close() throws IOException {
            IOException exception = null;
            for (final SegmentInputStream segmentInputStream : segmentInputStreams) {
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
}
