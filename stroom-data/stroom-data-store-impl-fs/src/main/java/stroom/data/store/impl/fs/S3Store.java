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
import stroom.aws.s3.impl.S3Manager;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
class S3Store {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3Store.class);

    private static final int MAX_CACHED_ITEMS = 10;

    private final PathCreator pathCreator;
    private final Map<Long, TrackedSource> cache = new ConcurrentHashMap<>();
    private final Set<TrackedSource> evictable = new HashSet<>();
    private final MetaService metaService;
    private final Path tempDir;

    @Inject
    S3Store(final TempDirProvider tempDirProvider,
            final PathCreator pathCreator,
            final MetaService metaService) {
        this.pathCreator = pathCreator;
        this.metaService = metaService;

        try {
            tempDir = tempDirProvider.get().resolve("s3_cache");
            Files.createDirectories(tempDir);
            FileUtil.deleteContents(tempDir);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public S3Source getSource(final DataVolume dataVolume, final Meta meta) {
        final TrackedSource trackedSource = cache.compute(meta.getId(), (k, v) -> {
            if (v == null) {
                final Path tempPath = createTempPath(meta.getId());
                try {
                    // Create zip.
                    Path zipFile = null;
                    try {
                        zipFile = tempPath.resolve(S3FileExtensions.ZIP_FILE_NAME);
                        // Download the zip from S3.
                        final S3Manager s3Manager =
                                new S3Manager(pathCreator, dataVolume.getVolume().getS3ClientConfig());
                        s3Manager.download(meta, zipFile);

                        ZipUtil.unzip(zipFile, tempPath);
                    } catch (final IOException e) {
                        LOGGER.error(e::getMessage, e);
                        throw new UncheckedIOException(e);
                    } finally {
                        deleteFile("Deleting source zip: ", zipFile);
                    }
                } catch (final RuntimeException e) {
                    LOGGER.debug(e::getMessage, e);
                    deleteDir("Deleting source dir: ", tempPath);

                    throw e;
                }

                return new TrackedSource(meta.getId(), tempPath, Instant.now(), new AtomicInteger(1));

            } else {
                synchronized (S3Store.this) {
                    evictable.remove(v);
                }
                v.getUseCount().incrementAndGet();
                return v;
            }
        });

        return new S3Source(this, trackedSource.getPath(), getS3Path(dataVolume, meta), meta);
    }

    public S3Target getTarget(final DataVolume dataVolume, final Meta meta) {
        final Path tempDir = createTempPath(meta.getId());
        return new S3Target(metaService, this, tempDir, dataVolume, meta);
    }

    private String getS3Path(final DataVolume dataVolume, final Meta meta) {
        final S3Manager s3Manager = new S3Manager(pathCreator, dataVolume.getVolume().getS3ClientConfig());
        return "S3 > " +
                s3Manager.createBucketName(s3Manager.getBucketNamePattern(), meta) +
                " > " +
                s3Manager.createKey(s3Manager.getKeyNamePattern(), meta);
    }

    public void release(final Meta meta, final Path path) {
        cache.compute(meta.getId(), (k, v) -> {
            if (v == null) {
                deleteDir("Release deleting: ", path);
            } else {
                final int count = v.getUseCount().decrementAndGet();
                assert count >= 0;
                if (count == 0) {
                    synchronized (S3Store.this) {
                        evictable.add(v);
                    }
                }
            }
            return v;
        });

        evict();
    }

    private void evict() {
        if (cache.size() > MAX_CACHED_ITEMS) {
            final List<TrackedSource> list;
            synchronized (S3Store.this) {
                list = new ArrayList<>(evictable);
            }
            list.sort(Comparator.comparing(TrackedSource::getCreateTime));

            for (final TrackedSource trackedSource : list) {
                if (cache.size() > MAX_CACHED_ITEMS) {
                    cache.compute(trackedSource.metaId, (k, v) -> {
                        if (v == null || v.getUseCount().get() == 0) {
                            deleteDir("Evict delete dir: ", trackedSource.getPath());
                            synchronized (S3Store.this) {
                                evictable.remove(trackedSource);
                            }
                            return null;
                        }
                        return v;
                    });
                }
            }
        }
    }

    public void upload(final Path tempDir,
                       final DataVolume dataVolume,
                       final Meta meta,
                       final AttributeMap attributeMap) {
        // Create zip.
        Path zipFile = null;
        try {
            zipFile = tempDir.resolve(S3FileExtensions.ZIP_FILE_NAME);
            ZipUtil.zip(zipFile, tempDir);

            // Upload the zip to S3.
            final S3Manager s3Manager = new S3Manager(pathCreator, dataVolume.getVolume().getS3ClientConfig());
            s3Manager.upload(meta, attributeMap, zipFile);

        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        } finally {
            deleteFile("Deleting target zip: ", zipFile);
        }
    }

    private Path createTempPath(final Long metaId) {
        try {
            final Path path = tempDir.resolve(metaId + "__" + UUID.randomUUID());
            Files.createDirectories(path);
            return path;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void deleteDir(final String message, final Path dir) {
        if (dir != null) {
            try {
                LOGGER.debug(() -> message + FileUtil.getCanonicalPath(dir));
                FileUtil.deleteDir(dir);
            } catch (final RuntimeException e2) {
                LOGGER.debug(e2::getMessage, e2);
            }
        }
    }

    private void deleteFile(final String message, final Path file) {
        if (file != null) {
            try {
                LOGGER.debug(() -> message + FileUtil.getCanonicalPath(file));
                Files.delete(file);
            } catch (final IOException e) {
                LOGGER.debug(e::getMessage, e);
            }
        }
    }

    private static class TrackedSource {

        private final Long metaId;

        private final Path path;
        private final Instant createTime;
        private final AtomicInteger useCount;

        public TrackedSource(final Long metaId,
                             final Path path,
                             final Instant createTime,
                             final AtomicInteger useCount) {
            this.metaId = metaId;
            this.path = path;
            this.createTime = createTime;
            this.useCount = useCount;
        }

        public Path getPath() {
            return path;
        }

        public Instant getCreateTime() {
            return createTime;
        }

        public AtomicInteger getUseCount() {
            return useCount;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final TrackedSource that = (TrackedSource) o;
            return Objects.equals(metaId, that.metaId) && Objects.equals(path, that.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(metaId, path);
        }
    }
}
