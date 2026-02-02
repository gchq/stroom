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
import stroom.data.store.api.DataException;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.SegmentOutputStream;
import stroom.data.store.api.Target;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.data.store.impl.fs.standard.FileSystemUtil;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.query.api.datasource.QueryField;
import stroom.util.io.ByteCountOutputStream;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * A file system implementation of Target.
 */
public final class S3ZstdTarget implements Target {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3ZstdTarget.class);
    private static final int DEFAULT_ZSTD_COMPRESSION_LEVEL = 3;

    private final MetaService metaService;
    private final S3ZstdStore s3ZstdStore;
    private final S3Manager s3Manager;
    private final S3StreamTypeExtensions s3StreamTypeExtensions;
    private final HeapBufferPool heapBufferPool;
    private final DataVolume dataVolume;
    private final Map<Long, S3OutputStreamProvider> partMap = new HashMap<>();
    private final FileKey fileKey;
    private final String s3Bucket;
    private final String s3Key;
    private final Path tempFilePath;
    private final Path tempDir;
    private final S3ZstdTarget parentTarget;
    /**
     * childStreamTypeName => {@link S3ZstdTarget}
     */
    private final Map<String, S3ZstdTarget> childMap = new HashMap<>();

    private ByteCountOutputStream byteCountOutputStream;
    private ZstdSegmentOutputStream zstdSegmentOutputStream;
    private AttributeMap attributeMap;
    private Meta meta;
    private boolean closed;
    private boolean deleted;
    /**
     * One based
     */
    private long partNo;

    private S3ZstdTarget(final MetaService metaService,
                         final S3ZstdStore s3ZstdStore,
                         final S3Manager s3Manager,
                         final S3StreamTypeExtensions s3StreamTypeExtensions,
                         final HeapBufferPool heapBufferPool,
                         final Path tempDir,
                         final DataVolume dataVolume,
                         final Meta meta,
                         final S3ZstdTarget parentTarget,
                         final String childStreamType) {
        this.heapBufferPool = heapBufferPool;
        this.dataVolume = dataVolume;
        this.metaService = metaService;
        this.s3ZstdStore = s3ZstdStore;
        this.s3Manager = s3Manager;
        this.s3StreamTypeExtensions = s3StreamTypeExtensions;
        this.meta = meta;
        // This is specific to the metaId
        this.tempDir = tempDir;
        this.parentTarget = parentTarget;
        this.fileKey = FileKey.of(dataVolume, meta, childStreamType);
        this.s3Key = s3StreamTypeExtensions.getkey(fileKey);
        this.s3Bucket = s3Manager.getBucketNamePattern();
        this.tempFilePath = createTempFilePath(tempDir, s3Key);
        LOGGER.debug(() ->
                LogUtil.message(
                        "ctor() - tempDir: '{}', metaId: {}, streamTypeName: {}, childStreamType: {}, " +
                        "s3Bucket: {}, s3Key: '{}', tempFilePath: {}",
                        tempDir, meta.getId(), meta.getTypeName(), childStreamType, s3Bucket, s3Key, tempFilePath));
    }

    static S3ZstdTarget create(final MetaService metaService,
                               final S3ZstdStore s3ZstdStore,
                               final S3Manager s3Manager,
                               final S3StreamTypeExtensions s3StreamTypeExtensions,
                               final HeapBufferPool heapBufferPool,
                               final Path tempDir,
                               final DataVolume dataVolume,
                               final Meta meta) {
        return new S3ZstdTarget(
                metaService,
                s3ZstdStore,
                s3Manager,
                s3StreamTypeExtensions,
                heapBufferPool,
                tempDir,
                dataVolume,
                meta,
                null,
                null);
    }

    @Override
    public Meta getMeta() {
        return meta;
    }

    @Override
    public AttributeMap getAttributes() {
        attributeMap = Objects.requireNonNullElseGet(attributeMap, AttributeMap::new);
        return attributeMap;
    }

//    private void writeManifest() {
//        try {
//            final Path manifestFile = tempDir.resolve(S3FileExtensions.MANIFEST_FILE_NAME);
//            LOGGER.debug("writeManifest() - manifestFile: {}", manifestFile);
//            try (final OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(manifestFile))) {
//                AttributeMapUtil.write(getAttributes(), outputStream);
//            }
//        } catch (final IOException e) {
//            LOGGER.error(() -> "closeStreamTarget() - Error on writing Manifest " + this, e);
//        }
//    }


    private FileKey getFileKey() {
        return fileKey;
    }

    private void updateAttribute(final S3ZstdTarget target, final QueryField key, final String value) {
        final String keyFldName = key.getFldName();
        final AttributeMap targetAttributes = target.getAttributes();
        if (!targetAttributes.containsKey(keyFldName)) {
            LOGGER.debug("updateAttribute() - keyFldName: {}, value: {}", keyFldName, value);
            targetAttributes.put(keyFldName, value);
        }
    }

    private void updateAttribute(final S3ZstdTarget target, final QueryField key, final Collection<String> values) {
        final String keyFldName = key.getFldName();
        final AttributeMap targetAttributes = target.getAttributes();
        if (!targetAttributes.containsKey(keyFldName)) {
            LOGGER.debug("updateAttribute() - keyFldName: {}, values: {}", keyFldName, values);
            targetAttributes.putCollection(keyFldName, values);
        }
    }

    private void closeStreams() throws IOException {
        LOGGER.debug(() -> LogUtil.message("closeStreams() - fileKey: {}, parentFileKey: {}",
                fileKey, NullSafe.get(parentTarget, S3ZstdTarget::getFileKey)));
        // If we get error on closing the stream we must return it to the caller
        IOException exception = null;

        if (zstdSegmentOutputStream != null) {
            LOGGER.debug(() -> LogUtil.message(
                    "closeStreams() - Closing zstdSegmentOutputStream, " +
                    "fileKey: {}, averageUncompressedFrameSize: {}, segmentOutputStream: {}, zstdDictionary: {}",
                    fileKey,
                    zstdSegmentOutputStream.getAverageUncompressedFrameSize(),
                    zstdSegmentOutputStream.getSegmentCount(),
                    zstdSegmentOutputStream.getZstdDictionary()));

            try {
                zstdSegmentOutputStream.flush();
                zstdSegmentOutputStream.close();
            } catch (final IOException e) {
                LOGGER.error(() -> "closeStreams() - Error on closing stream " + this, e);
                exception = e;
            }
        }

        for (final S3ZstdTarget child : childMap.values()) {
            child.close();
        }

        // Close the streams.
        for (final OutputStreamProvider outputStreamProvider : partMap.values()) {
            try {
                outputStreamProvider.close();
            } catch (final ClosedByInterruptException e) {
                // WE expect these exceptions if a user is trying to terminate.
                LOGGER.debug(() -> "closeStreamTarget() - Error on closing stream " + this, e);
                exception = e;
            } catch (final IOException e) {
                LOGGER.error(() -> "closeStreamTarget() - Error on closing stream " + this, e);
                exception = e;
            }
        }

        if (exception != null) {
            throw exception;
        }
    }

    @Override
    public void close() {
        LOGGER.debug(() -> LogUtil.message("close() - fileKey: {}, parentFileKey: {}",
                fileKey, NullSafe.get(parentTarget, S3ZstdTarget::getFileKey)));
        if (closed) {
            throw new DataException("Target already closed");
        }

        try {
            if (!deleted) {
                // If we get error on closing the stream we must return it to the caller
                IOException streamCloseException = null;
                try {
                    // This will close the internal outputStream for this and any of our children,
                    // plus call close() on any children
                    closeStreams();
                } catch (final IOException e) {
                    streamCloseException = e;
                }

                // Update attributes and write the manifest.
                final long rawSize = NullSafe.getOrElse(zstdSegmentOutputStream,
                        SegmentOutputStream::getPosition,
                        0L);
                final long fileSize = NullSafe.getOrElse(byteCountOutputStream,
                        ByteCountOutputStream::getCount,
                        0L);

                if (parentTarget == null) {
                    // These only get set on the parent
                    updateAttribute(this, MetaFields.RAW_SIZE, String.valueOf(rawSize));
                    updateAttribute(this, MetaFields.FILE_SIZE, String.valueOf(fileSize));
                    // Record the child types to save us having to do an S3 list call.
                    // Sort them for consistency
                    updateAttribute(this, MetaFields.CHILD_TYPES, NullSafe.sort(childMap.keySet()));

                }
                // Record whether we are dealing with parts or segments so the reader knows how to treat it
                updateAttribute(this, MetaFields.SEGMENTATION_TYPE, getZstdSegmentationType().toString());

                NullSafe.consume(zstdSegmentOutputStream.getZstdDictionary(), zstdDictionary -> {
                    // The dict UUID is embedded in the file itself, but it may be helpful to have it here too
                    updateAttribute(this, MetaFields.ZSTD_DICTIONARY_UUID, zstdDictionary.getUuid().toString());
                });

                if (streamCloseException == null) {
                    try {
                        final AttributeMap attributeMap = uploadFile();
                        // Unlock will update the meta-data so set it back on the stream
                        // target so the client has the up to date copy
                        unlock(getMeta(), attributeMap);
                    } catch (final RuntimeException e) {
                        LOGGER.error(e::getMessage, e);
                        throw e;
                    }
                } else {
                    throw new UncheckedIOException(streamCloseException);
                }
            }
        } finally {
            closed = true;

            try {
                LOGGER.debug("close() - Deleting tempDir: {}", tempDir);
                FileUtil.deleteDir(tempDir);
            } catch (final RuntimeException e) {
                LOGGER.debug(e::getMessage, e);
            }
        }
    }

    private AttributeMap uploadFile() {
        final AttributeMap attributeMap = getAttributes();
        s3Manager.upload(s3Bucket, s3Key, meta, attributeMap, tempFilePath);
        LOGGER.debug("close() - Uploaded fileKey: {}, tempFilePath: {}, s3Bucket: {}, " +
                     "s3Key: {}, attributeMap: {}",
                fileKey, tempFilePath, s3Bucket, s3Key, attributeMap);
        return attributeMap;
    }

    /**
     * @return This target plus any children.
     */
    private List<S3ZstdTarget> getAllTargets() {
        if (parentTarget != null) {
            throw new RuntimeException("Should only be called on the parent target");
        }
        return Stream.concat(Stream.of(this), childMap.values().stream())
                .toList();
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
//        if (closed) {
//            throw new DataException("Target already closed");
//        }

        try {
            // Close the stream target.
            try {
                closeStreams();
            } catch (final IOException e) {
                LOGGER.debug(e::getMessage, e);
            }

            try {
                FileUtil.deleteDir(tempDir);
            } catch (final RuntimeException e) {
                LOGGER.debug(e::getMessage, e);
            }

            // Mark the target meta as deleted.
            this.meta = metaService.updateStatus(meta, Status.LOCKED, Status.DELETED);

        } finally {
            deleted = true;
        }
    }

//    private Long getStreamSize() {
//        long total = 0;
//        for (final S3OutputStreamProvider outputStreamProvider : partMap.values()) {
//            total += outputStreamProvider.getStreamSize();
//        }
//        return total;
//    }

    private Long getTotalFileSize() {
        final AtomicLong size = new AtomicLong();
        try (final Stream<Path> stream = Files.list(tempDir)) {
            stream.forEach(path -> {
                try {
                    final String fileName = path.getFileName().toString();
                    final int index = fileName.indexOf(".");
                    if (index >= 0) {
                        final String extension = fileName.substring(index);
                        if (extension.endsWith(S3FileExtensions.DATA_EXTENSION)) {
                            size.addAndGet(Files.size(path));
                        }
                    }
                } catch (final IOException e) {
                    LOGGER.debug(e::getMessage, e);
                }
            });
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return size.get();
    }

    @Override
    public OutputStreamProvider next() {
        final long no = ++partNo;
        LOGGER.debug("next() - meta: {}, no: {}, tempDir: {}", meta, no, tempDir);
        final S3OutputStreamProvider s3OutputStreamProvider = new S3OutputStreamProvider(this, no);
        partMap.put(no, s3OutputStreamProvider);
        return s3OutputStreamProvider;
    }

    @Override
    public String toString() {
        return "id=" + meta.getId();
    }

    /**
     * @return The target for the given streamTypeName or this if streamTypeName
     * is null
     */
    private S3ZstdTarget getTarget(final String childStreamTypeName) {
        if (closed) {
            throw new RuntimeException("Closed");
        }

        if (childStreamTypeName == null) {
            return this;
        } else {
            return childMap.computeIfAbsent(childStreamTypeName, this::createChildTarget);
        }
    }

    private S3ZstdTarget createChildTarget(final String streamTypeName) {
        Objects.requireNonNull(streamTypeName);
        return new S3ZstdTarget(
                metaService,
                s3ZstdStore,
                s3Manager,
                s3StreamTypeExtensions,
                heapBufferPool,
                tempDir,
                dataVolume,
                meta,
                this,
                streamTypeName);
    }

    private Path createTempFilePath(final Path tempDir, final String s3Key) {
        Objects.requireNonNull(s3Key);
        final int idx = s3Key.lastIndexOf("/");
        final String fileName = s3Key.substring(idx + 1);
        final Path tempFilePath = tempDir.resolve(fileName).normalize().toAbsolutePath();
        LOGGER.debug("createTempFilePath() - fileKey: {}, tempDir: {}, s3Key: {}, path: {}",
                fileKey, tempDir, s3Key, tempFilePath);
        return tempFilePath;
    }

    private ZstdSegmentationType getZstdSegmentationType() {
        // This is the last pertNo we have dealt with, i.e. the part count
        if (partNo <= 1) {
            // This may be a single part non-segmented stream, but we have no way of knowing
            return ZstdSegmentationType.SEGMENTS;
        } else {
            return ZstdSegmentationType.PARTS;
        }
    }

    private ZstdSegmentOutputStream getInternalOutputStream() {
        if (zstdSegmentOutputStream == null) {
            try {
                FileSystemUtil.mkdirs(tempFilePath.getParent());

                // If the file already exists then delete it.
                if (Files.exists(tempFilePath)) {
                    LOGGER.warn(() -> "About to overwrite file: " + FileUtil.getCanonicalPath(tempFilePath));
                    Files.delete(tempFilePath);
                }
                // Don't need to worry about locking the file as we are in a dedicated temp dir
                Files.createFile(tempFilePath);
                LOGGER.debug(() -> LogUtil.message(
                        "getOrCreateSegmentOutputStream() - Created file: {}, fileKey: {}",
                        FileUtil.getCanonicalPath(tempFilePath), fileKey));
                byteCountOutputStream = new ByteCountOutputStream(new FileOutputStream(tempFilePath.toFile()));
                final ZstdDictionary zstdDictionary = getZstdDictionary();
                zstdSegmentOutputStream = new ZstdSegmentOutputStream(
                        new BufferedOutputStream(byteCountOutputStream),
                        zstdDictionary,
                        heapBufferPool,
                        DEFAULT_ZSTD_COMPRESSION_LEVEL);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return zstdSegmentOutputStream;
    }

    private @Nullable ZstdDictionary getZstdDictionary() {
        final ZstdDictionaryKey zstdDictionaryKey = ZstdDictionaryKey.of(
                meta.getFeedName(),
                fileKey.streamType(),
                fileKey.childStreamType());
        final ZstdDictionaryService zstdDictionaryService = s3ZstdStore.getZstdDictionaryService();
        final ZstdDictionary zstdDictionary = zstdDictionaryService.getZstdDictionary(zstdDictionaryKey, dataVolume)
                .orElse(null);

        if (zstdDictionary == null) {
            // There is no dict available. We will have to compress without a dict.
            // Thus, we record the details of this file so that the service can (async) train a dict
            // from the data, recompress it using that dict and make the dict available for future use.
            zstdDictionaryService.createReCompressTask(zstdDictionaryKey, fileKey);
        }
        LOGGER.debug("getZstdDictionary() - zstdDictionaryKey: {}, zstdDictionary: {}",
                zstdDictionaryKey, zstdDictionary);
        return zstdDictionary;
    }

    private ZstdSegmentOutputStream getInternalOutputStream(final String childStreamTypeName) {
        final S3ZstdTarget target = getTarget(childStreamTypeName);
        return target.getInternalOutputStream();
    }


    // --------------------------------------------------------------------------------


    private static class S3OutputStreamProviderFactory {


    }


    // --------------------------------------------------------------------------------


    private static class S3OutputStreamProvider implements OutputStreamProvider {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3OutputStreamProvider.class);

        private final long partNo;
        private final boolean allowSegmentsInPart;
        private final S3ZstdTarget s3ZstdTarget;
        // childStreamType => segmentOutputStream
        private final Map<String, SegmentOutputStream> childStreamTypeToStreamMap = new HashMap<>(10);

        private SegmentOutputStream dataStream;

        public S3OutputStreamProvider(final S3ZstdTarget s3ZstdTarget, final long partNo) {
            this.partNo = partNo;
            this.allowSegmentsInPart = partNo == 0;
            this.s3ZstdTarget = s3ZstdTarget;
            LOGGER.debug("ctor() - partNo: {}", partNo);
        }

        @Override
        public SegmentOutputStream get() {
            if (dataStream != null) {
                throw new RuntimeException("Unexpected get(). Should only be called once partNo " + partNo);
            }
            LOGGER.debug("get()");
            dataStream = create(null);
            return dataStream;
        }

        @Override
        public SegmentOutputStream get(final String childStreamType) {
            LOGGER.debug("get() - childStreamType: {}", childStreamType);
            if (childStreamType == null) {
                return get();
            } else {
                if (childStreamTypeToStreamMap.containsKey(childStreamType)) {
                    throw new RuntimeException("Unexpected get(). Should only be called once partNo " + partNo);
                }
                final SegmentOutputStream segmentOutputStream = create(childStreamType);
                childStreamTypeToStreamMap.put(childStreamType, segmentOutputStream);
                return segmentOutputStream;
            }
        }

        private SegmentOutputStream create(final String childStreamType) {
            LOGGER.debug("create() - childStreamType: {}, partNo: {}", childStreamType, partNo);
            final ZstdSegmentOutputStream internalOutputStream = s3ZstdTarget.getInternalOutputStream(
                    childStreamType);

            // We do not support multipart and multi-segment at the same time.
            // Thus by wrapping it, we will make it error if the caller tries to add
            // any segments to this part.
            return new WrappedSegmentOutputStream(partNo, internalOutputStream, allowSegmentsInPart);
        }

        @Override
        public void close() throws IOException {
            IOException exception = null;
            for (final SegmentOutputStream segmentOutputStream : getAllSegmentOutputStreams()) {
                try {
                    segmentOutputStream.close();
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

        private @NonNull List<SegmentOutputStream> getAllSegmentOutputStreams() {
            return Stream.concat(Stream.of(dataStream),
                            childStreamTypeToStreamMap.values().stream())
                    .toList();
        }
    }


    // --------------------------------------------------------------------------------


    private static class WrappedSegmentOutputStream extends SegmentOutputStream {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(WrappedSegmentOutputStream.class);

        private final long partNo;
        private final SegmentOutputStream delegate;
        private final boolean allowSegments;

        private WrappedSegmentOutputStream(final long partNo,
                                           final SegmentOutputStream delegate,
                                           final boolean allowSegments) {
            this.partNo = partNo;
            this.delegate = delegate;
            this.allowSegments = allowSegments;
        }

        @Override
        public void addSegment() throws IOException {
            if (allowSegments) {
                delegate.addSegment();
            } else {
                throw new UnsupportedOperationException("Multiple segments are not supported");
            }
        }

        @Override
        public void addSegment(final long position) throws IOException {
            if (allowSegments) {
                delegate.addSegment(position);
            } else {
                throw new UnsupportedOperationException("Multiple segments are not supported");
            }
        }

        @Override
        public long getPosition() {
            return delegate.getPosition();
        }

        @Override
        public void write(final int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(final byte[] b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            // No-op as we don't want the part stream to close the underlying zstd stream.
            LOGGER.debug("close() - Close called and ignored for partNo: {}", partNo);
            // Ensure everything is flushed down though
            delegate.flush();
        }
    }
}
