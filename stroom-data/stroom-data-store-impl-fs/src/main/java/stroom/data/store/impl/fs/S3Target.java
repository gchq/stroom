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

import stroom.aws.s3.impl.S3FileExtensions;
import stroom.data.store.api.DataException;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.SegmentOutputStream;
import stroom.data.store.api.Target;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.query.api.datasource.QueryField;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * A file system implementation of Target.
 */
final class S3Target implements Target {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3Target.class);

    private final MetaService metaService;
    private final S3Store s3Store;
    private final DataVolume dataVolume;
    private final Map<Long, S3OutputStreamProvider> partMap = new HashMap<>();
    private final Path tempDir;
    private AttributeMap attributeMap;

    private Meta meta;
    private boolean closed;
    private boolean deleted;
    private long partNo;

    public S3Target(final MetaService metaService,
                    final S3Store s3Store,
                    final Path tempDir,
                    final DataVolume dataVolume,
                    final Meta meta) {
        this.dataVolume = dataVolume;
        this.metaService = metaService;
        this.s3Store = s3Store;
        this.meta = meta;
        this.tempDir = tempDir;
    }

    @Override
    public Meta getMeta() {
        return meta;
    }

    @Override
    public AttributeMap getAttributes() {
        if (attributeMap == null) {
            attributeMap = new AttributeMap();
        }
        return attributeMap;
    }

    private void writeManifest() {
        try {
            final Path manifestFile = tempDir.resolve(S3FileExtensions.MANIFEST_FILE_NAME);
            try (final OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(manifestFile))) {
                AttributeMapUtil.write(getAttributes(), outputStream);
            }
        } catch (final IOException e) {
            LOGGER.error(() -> "closeStreamTarget() - Error on writing Manifest " + this, e);
        }
    }

    private void updateAttribute(final S3Target target, final QueryField key, final String value) {
        if (!target.getAttributes().containsKey(key.getFldName())) {
            target.getAttributes().put(key.getFldName(), value);
        }
    }

    private void closeAllStreams() throws IOException {
        // If we get error on closing the stream we must return it to the caller
        IOException streamCloseException = null;

        // Close the streams.
        for (final OutputStreamProvider outputStreamProvider : partMap.values()) {
            try {
                outputStreamProvider.close();
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
            throw new DataException("Target already closed");
        }

        try {
            if (!deleted) {
                // If we get error on closing the stream we must return it to the caller
                IOException streamCloseException = null;
                try {
                    closeAllStreams();
                } catch (final IOException e) {
                    streamCloseException = e;
                }
                partMap.clear();

                // Update attributes and write the manifest.
                final String totalFileSize = String.valueOf(getTotalFileSize());
                updateAttribute(this, MetaFields.RAW_SIZE, totalFileSize);
                updateAttribute(this, MetaFields.FILE_SIZE, totalFileSize);
                writeManifest();

                if (streamCloseException == null) {

                    try {
                        final AttributeMap attributeMap = getAttributes();

                        // Zip and upload.
                        s3Store.upload(tempDir, dataVolume, meta, attributeMap);

                        // Unlock will update the meta data so set it back on the stream
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
                FileUtil.deleteDir(tempDir);
            } catch (final RuntimeException e) {
                LOGGER.debug(e::getMessage, e);
            }
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
                closeAllStreams();
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
        final S3OutputStreamProvider s3OutputStreamProvider = new S3OutputStreamProvider(tempDir, no);
        partMap.put(no, s3OutputStreamProvider);
        return s3OutputStreamProvider;
    }

    @Override
    public String toString() {
        return "id=" + meta.getId();
    }

    private static class S3OutputStreamProvider implements OutputStreamProvider {

        private final Path dir;
        private final String partString;
        private final List<SegmentOutputStream> segmentOutputStreams = new ArrayList<>();
        private SegmentOutputStream dataStream;

        public S3OutputStreamProvider(final Path dir, final long partNo) {
            this.dir = dir;
            partString = FsPrefixUtil.padId(partNo);
        }

        @Override
        public SegmentOutputStream get() {
            if (dataStream != null) {
                throw new RuntimeException("Unexpected get");
            }
            dataStream = create(S3FileExtensions.DATA_EXTENSION);
            return dataStream;
        }

        @Override
        public SegmentOutputStream get(final String childStreamType) {
            if (childStreamType == null) {
                return get();
            }

            final String extension = S3FileExtensions.EXTENSION_MAP.get(childStreamType);
            if (extension == null) {
                throw new RuntimeException("Unexpected child stream type: " + childStreamType);
            }
            return create(extension);
        }

        private SegmentOutputStream create(final String extension) {
            try {
                final String fileName = partString + extension;
                final Path dataFile = dir.resolve(fileName);
                final Path indexFile = dir.resolve(fileName + S3FileExtensions.INDEX_EXTENSION);
                final OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(dataFile));
                final SegmentOutputStream segmentOutputStream = new RASegmentOutputStream(outputStream, () ->
                        new BufferedOutputStream(Files.newOutputStream(indexFile)));
                segmentOutputStreams.add(segmentOutputStream);
                return segmentOutputStream;
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void close() throws IOException {
            IOException exception = null;
            for (final SegmentOutputStream segmentOutputStream : segmentOutputStreams) {
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
    }
}
