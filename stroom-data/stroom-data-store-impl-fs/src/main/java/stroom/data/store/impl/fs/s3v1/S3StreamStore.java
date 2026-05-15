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

package stroom.data.store.impl.fs.s3v1;

import stroom.aws.s3.client.S3Util;
import stroom.aws.s3.impl.S3FileExtensions;
import stroom.aws.s3.impl.S3Manager;
import stroom.aws.s3.impl.S3ManagerFactory;
import stroom.cache.api.TemplateCache;
import stroom.data.store.api.DataException;
import stroom.data.store.api.Source;
import stroom.data.store.api.Target;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.data.store.impl.fs.PhysicalDeleteExecutor.Progress;
import stroom.data.store.impl.fs.StreamStore;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.SimpleMeta;
import stroom.util.io.FileUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.string.CIKey;
import stroom.util.string.StringIdUtil;
import stroom.util.string.TemplateUtil;
import stroom.util.string.TemplateUtil.Template;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Singleton
public class S3StreamStore implements StreamStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3StreamStore.class);

    private static final int MAX_CACHED_ITEMS = 10;
    private static final CIKey FEED_VAR = CIKey.internStaticKey("feed");
    private static final CIKey TYPE_VAR = CIKey.internStaticKey("type");
    private static final CIKey ID_VAR = CIKey.internStaticKey("id");
    private static final CIKey ID_PATH_VAR = CIKey.internStaticKey("idPath");
    private static final CIKey ID_PADDED_VAR = CIKey.internStaticKey("idPadded");

    private final Map<Long, TrackedSource> cache = new ConcurrentHashMap<>();
    private final Set<TrackedSource> evictable = new HashSet<>();
    private final MetaService metaService;
    private final TemplateCache templateCache;
    private final S3ManagerFactory s3ManagerFactory;
    private final Path tempDir;

    @Inject
    S3StreamStore(final TempDirProvider tempDirProvider,
                  final MetaService metaService,
                  final S3ManagerFactory s3ManagerFactory,
                  final TemplateCache templateCache) {
        this.metaService = metaService;
        this.s3ManagerFactory = s3ManagerFactory;
        this.templateCache = templateCache;

        try {
            // TODO should this be in the temp dir?  It could be very big.
            tempDir = tempDirProvider.get().resolve("s3_cache");
            Files.createDirectories(tempDir);
            FileUtil.deleteContents(tempDir);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void physicallyDelete(final Collection<DataVolume> dataVolumes) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public Source openSource(final Meta meta, final DataVolume dataVolume) throws DataException {
        final TrackedSource trackedSource = cache.compute(meta.getId(), (k, v) -> {
            if (v == null) {
                final Path tempPath = createTempPath(meta.getId());
                try {
                    // Create zip.
                    Path zipFile = null;
                    try {
                        zipFile = tempPath.resolve(S3FileExtensions.ZIP_FILE_NAME);
                        // Download the zip from S3.
                        final S3Manager s3Manager = createS3Manager(dataVolume);
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
                synchronized (S3StreamStore.this) {
                    evictable.remove(v);
                }
                v.getUseCount().incrementAndGet();
                return v;
            }
        });

        return new S3Source(this, trackedSource.getPath(), getS3Path(dataVolume, meta), meta);
    }

    @Override
    public FsVolumeType getVolumeType() {
        return FsVolumeType.S3_V1;
    }

    @Override
    public Target openTarget(final Meta meta, final DataVolume dataVolume) throws DataException {
        final Path tempDir = createTempPath(meta.getId());
        return new S3Target(metaService, this, tempDir, dataVolume, meta);
    }

    @Override
    public PhysicalDeleteOutcome physicallyDelete(final SimpleMeta simpleMeta,
                                                  final DataVolume dataVolume,
                                                  final Progress progress) {
        final String s3Path = getS3Path(dataVolume, simpleMeta);

        final S3Manager s3Manager = createS3Manager(dataVolume);
//        s3Manager.delete(m)


        throw new UnsupportedOperationException("TODO");
    }

    private String getS3Path(final DataVolume dataVolume, final SimpleMeta simpleMeta) {
        final S3Manager s3Manager = createS3Manager(dataVolume);
        return "S3 > " +
               createBucketName(s3Manager.getBucketNamePattern(), simpleMeta) +
               " > " +
               createKey(s3Manager.getKeyNamePattern(), simpleMeta);
    }

    void release(final Meta meta, final Path path) {
        cache.compute(meta.getId(), (ignored, v) -> {
            if (v == null) {
                deleteDir("Release deleting: ", path);
            } else {
                final int count = v.getUseCount().decrementAndGet();
                assert count >= 0;
                if (count == 0) {
                    synchronized (S3StreamStore.this) {
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
            synchronized (S3StreamStore.this) {
                list = new ArrayList<>(evictable);
            }
            list.sort(Comparator.comparing(TrackedSource::getCreateTime));

            for (final TrackedSource trackedSource : list) {
                if (cache.size() > MAX_CACHED_ITEMS) {
                    cache.compute(trackedSource.metaId, (ignored, v) -> {
                        if (v == null || v.getUseCount().get() == 0) {
                            deleteDir("Evict delete dir: ", trackedSource.getPath());
                            synchronized (S3StreamStore.this) {
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

    /**
     * Zips the contents of tempDir into a single ZIP file then uploads it to S3
     */
    public void upload(final Path tempDir,
                       final DataVolume dataVolume,
                       final Meta meta,
                       final AttributeMap attributeMap) {
        LOGGER.debug(() -> LogUtil.message("upload() - tempDir: {}, metaId: {}, attributeMap: {}",
                tempDir, NullSafe.get(meta, Meta::getId), attributeMap));
        // Create zip.
        Path zipFile = null;
        try {
            zipFile = tempDir.resolve(S3FileExtensions.ZIP_FILE_NAME);
            ZipUtil.zip(zipFile, tempDir);

            // Upload the zip to S3.
            final S3Manager s3Manager = createS3Manager(dataVolume);
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

    private S3Manager createS3Manager(final DataVolume dataVolume) {
        return s3ManagerFactory.createS3Manager(dataVolume.volume().getS3ClientConfig());
    }

    public String createBucketName(final String bucketNamePattern,
                                   final SimpleMeta simpleMeta) {
        final Template template = templateCache.getTemplate(bucketNamePattern);
        String bucketName = template.buildExecutor()
                .addLazyReplacement(FEED_VAR, simpleMeta::getFeedName)
                .addLazyReplacement(TYPE_VAR, simpleMeta::getTypeName)
                .execute();

        bucketName = S3Util.cleanBucketName(bucketName);
        final int len = bucketName.length();
        if (len < 3) {
            LOGGER.error("Bucket name too short, must be >=3. bucketName: '{}'", bucketName);
            throw new RuntimeException(LogUtil.message("Bucket name too short, must be >=3. bucketName: '{}'",
                    bucketName));
        } else if (len > 63) {
            LOGGER.warn("Truncating bucket name: '{}'. Length must be >=3 and <=63.", bucketName);
            return bucketName.substring(0, 63);
        }

        return bucketName;
    }

    public String createKey(final String keyPattern, final SimpleMeta meta) {
        return createKey(keyPattern, meta, null);
    }

    public String createKey(final String keyPattern, final SimpleMeta meta, final String childStreamType) {
        final Template template;
        if (TemplateUtil.isStaticTemplate(keyPattern)) {
            // No point hitting the cache if our keyPattern is a static one containing something
            // with high cardinality like a meta id.
            template = TemplateUtil.parseTemplate(keyPattern);
        } else {
            template = templateCache.getTemplate(keyPattern);
        }
        final Supplier<ZonedDateTime> zonedDateTimeSupplier = () -> ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(meta.getCreateMs()), ZoneOffset.UTC);

        String keyName = template.buildExecutor()
                .addStandardTimeReplacements(zonedDateTimeSupplier)
                .addLazyReplacement(FEED_VAR, meta::getFeedName)
                .addLazyReplacement(TYPE_VAR, meta::getTypeName)
                .addLazyReplacement(ID_VAR, () -> String.valueOf(meta.getId()))
                .addLazyReplacement(ID_PATH_VAR, () -> StringIdUtil.getIdPath(meta.getId()))
                .addLazyReplacement(ID_PADDED_VAR, () -> StringIdUtil.idToString(meta.getId()))
                .execute();

        keyName = S3Util.cleanKeyName(keyName);

        final int keyBytesLen = keyName.getBytes(StandardCharsets.UTF_8).length;
        if (keyBytesLen > 1024) {
            throw new RuntimeException(LogUtil.message("Key name '{}' too long {}, must be less than 1,024 bytes",
                    keyName, keyBytesLen));
        }

        return keyName;
    }


    // --------------------------------------------------------------------------------


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
