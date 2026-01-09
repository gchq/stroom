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
import stroom.aws.s3.impl.S3ManagerFactory;
import stroom.data.store.api.Source;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.util.io.FileUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

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
public class S3ZstdStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3ZstdStore.class);

//    public static final String KEY_NAME_TEMPLATE_BASE = "${type}/${year}/${month}/${day}/${idPath}/${feed}/${idPadded}";

    private static final int MAX_CACHED_ITEMS = 10;

    //    private final TemplateCache templateCache;
    private final Map<Long, TrackedSource> cache = new ConcurrentHashMap<>();
    private final Set<TrackedSource> evictable = new HashSet<>();
    private final MetaService metaService;
    //    private final S3MetaFieldsMapper s3MetaFieldsMapper;
    private final S3StreamTypeExtensions s3StreamTypeExtensions;
    //    private final S3ClientPool s3ClientPool;
    private final ZstdSeekTableCache zstdSeekTableCache;
    private final HeapBufferPool heapBufferPool;
    private final S3ManagerFactory s3ManagerFactory;
    private final ZstdDictionaryService zstdDictionaryService;
    private final Path tempDir;

    @Inject
    S3ZstdStore(
//            final TemplateCache templateCache,
            final TempDirProvider tempDirProvider,
            final MetaService metaService,
//                final S3MetaFieldsMapper s3MetaFieldsMapper,
            final S3StreamTypeExtensions s3StreamTypeExtensions,
//                final S3ClientPool s3ClientPool,
            final ZstdSeekTableCache zstdSeekTableCache,
            final HeapBufferPool heapBufferPool,
            final S3ManagerFactory s3ManagerFactory,
            final ZstdDictionaryService zstdDictionaryService) {
//        this.templateCache = templateCache;
        this.metaService = metaService;
//        this.s3MetaFieldsMapper = s3MetaFieldsMapper;
        this.s3StreamTypeExtensions = s3StreamTypeExtensions;
//        this.s3ClientPool = s3ClientPool;
        this.zstdSeekTableCache = zstdSeekTableCache;
        this.heapBufferPool = heapBufferPool;
        this.s3ManagerFactory = s3ManagerFactory;
        this.zstdDictionaryService = zstdDictionaryService;

        try {
            tempDir = tempDirProvider.get().resolve("s3v2_cache");
            LOGGER.debug("ctor() - Ensuring and clearing tempDir: {}", tempDir);
            Files.createDirectories(tempDir);
            FileUtil.deleteContents(tempDir);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Source getSource(final DataVolume dataVolume, final Meta meta) {
        LOGGER.debug("getSource() - dataVolume: {}, meta: {}", dataVolume, meta);
        final S3Manager s3Manager = createS3Manager(dataVolume);
        final TrackedSource trackedSource = cache.compute(
                meta.getId(),
                (ignored, aTrackedSource) -> {
                    if (aTrackedSource == null) {
                        final Path tempPath = createTempDir(meta.getId());
//                try {
//                    // Create zip.
//                    Path zipFile = null;
//                    try {
//                        zipFile = tempPath.resolve(S3FileExtensions.ZIP_FILE_NAME);
//                        // Download the zip from S3.
//                        s3Manager.download(meta, zipFile);
//
//                        ZipUtil.unzip(zipFile, tempPath);
//                    } catch (final IOException e) {
//                        LOGGER.error(e::getMessage, e);
//                        throw new UncheckedIOException(e);
//                    } finally {
//                        deleteFile("Deleting source zip: ", zipFile);
//                    }
//                } catch (final RuntimeException e) {
//                    LOGGER.debug(e::getMessage, e);
//                    deleteDir("Deleting source dir: ", tempPath);
//
//                    throw e;
//                }

                        final TrackedSource trackedSource2 = new TrackedSource(
                                meta.getId(),
                                tempPath,
                                Instant.now(),
                                new AtomicInteger(1));
                        LOGGER.debug("getSource() - Creating trackedSource: {}", trackedSource2);
                        return trackedSource2;
                    } else {
                        synchronized (S3ZstdStore.this) {
                            evictable.remove(aTrackedSource);
                        }
                        aTrackedSource.useCount().incrementAndGet();
                        return aTrackedSource;
                    }
                });

        return new S3ZstdSource(
                this,
                trackedSource.path(),
                getS3KeyPrefix(dataVolume, meta),
                s3Manager,
                meta,
                dataVolume);
    }

    public S3ZstdTarget getTarget(final DataVolume dataVolume, final Meta meta) {
        LOGGER.debug("getTarget() - dataVolume: {}, meta: {}", dataVolume, meta);
        final Path tempDir = createTempDir(meta.getId());
        final S3Manager s3Manager = createS3Manager(dataVolume);
        return S3ZstdTarget.create(
                metaService,
                this,
                s3Manager,
                s3StreamTypeExtensions,
                heapBufferPool,
                tempDir,
                dataVolume,
                meta);
    }

    /**
     * The key prefix for all items belonging to this meta.
     */
    private String getS3KeyPrefix(final DataVolume dataVolume, final Meta meta) {
        final S3Manager s3Manager = createS3Manager(dataVolume);
        return String.join(
                " > ",
                "S3",
                s3Manager.getBucketNamePattern(),
                S3StreamTypeExtensions.getPrefix(meta.getId()));
    }

    private S3Manager createS3Manager(final DataVolume dataVolume) {
        return s3ManagerFactory.createS3Manager(dataVolume.volume().getS3ClientConfig());
    }

    public void release(final Meta meta, final Path path) {
        cache.compute(meta.getId(), (k, v) -> {
            if (v == null) {
                deleteDir("Release deleting: ", path);
            } else {
                final int count = v.useCount().decrementAndGet();
                assert count >= 0;
                if (count == 0) {
                    synchronized (S3ZstdStore.this) {
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
            synchronized (S3ZstdStore.this) {
                list = new ArrayList<>(evictable);
            }
            list.sort(Comparator.comparing(TrackedSource::createTime));

            for (final TrackedSource trackedSource : list) {
                if (cache.size() > MAX_CACHED_ITEMS) {
                    cache.compute(trackedSource.metaId, (k, v) -> {
                        if (v == null || v.useCount().get() == 0) {
                            deleteDir("Evict delete dir: ", trackedSource.path());
                            synchronized (S3ZstdStore.this) {
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

//    /**
//     * Zips the contents of tempDir into a single ZIP file then uploads it to S3
//     */
//    public void upload(final Path tempDir,
//                       final DataVolume dataVolume,
//                       final Meta meta,
//                       final AttributeMap attributeMap) {
//        LOGGER.debug(() -> LogUtil.message("upload() - tempDir: {}, metaId: {}, attributeMap: {}",
//                tempDir, NullSafe.get(meta, Meta::getId), attributeMap));
//        // Create zip.
//        Path zipFile = null;
//        try {
//            zipFile = tempDir.resolve(S3FileExtensions.ZIP_FILE_NAME);
//            ZipUtil.zip(zipFile, tempDir);
//
//            // Upload the zip to S3.
//            final S3Manager s3Manager = createS3Manager(dataVolume);
//            s3Manager.upload(meta, attributeMap, zipFile);
//
//        } catch (final IOException e) {
//            LOGGER.error(e::getMessage, e);
//            throw new UncheckedIOException(e);
//        } finally {
//            deleteFile("Deleting target zip: ", zipFile);
//        }
//    }

    private Path createTempDir(final Long metaId) {
        try {
            final Path tempDir = this.tempDir.resolve(metaId + "__" + UUID.randomUUID());
            Files.createDirectories(tempDir);
            LOGGER.debug("createTempPath() - Returning tempDir: {}", tempDir);
            return tempDir;
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

    ZstdDictionaryService getZstdDictionaryService() {
        return zstdDictionaryService;
    }

    ZstdSeekTableCache getZstdSeekTableCache() {
        return zstdSeekTableCache;
    }

    //    Optional<ZstdDictionary> getZstdDictionary(final ZstdDictionaryKey zstdDictionaryKey,
//                                               final DataVolume dataVolume) {
//        return zstdDictionaryService.getZstdDictionary(zstdDictionaryKey, dataVolume);
//    }
//
//    void createRecompressTask(final ZstdDictionaryKey zstdDictionaryKey,
//                              final long metaId,
//                              final DataVolume dataVolume)


    // --------------------------------------------------------------------------------


    private record TrackedSource(Long metaId,
                                 Path path,
                                 Instant createTime,
                                 AtomicInteger useCount) {

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
