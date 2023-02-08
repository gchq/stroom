/*
 * Copyright 2019 Crown Copyright
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

import stroom.cluster.lock.api.ClusterLockService;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.meta.api.MetaService;
import stroom.meta.api.PhysicalDelete;
import stroom.meta.shared.SimpleMeta;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.NullSafe;
import stroom.util.concurrent.WorkQueue;
import stroom.util.io.FileUtil;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.LogUtil;
import stroom.util.shared.StringUtil;
import stroom.util.time.StroomDuration;
import stroom.util.time.TimeUtils;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class PhysicalDeleteExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PhysicalDeleteExecutor.class);

    private static final String TASK_NAME = "Fs Delete Executor";
    private static final String LOCK_NAME = "FsDeleteExecutor";

    private final ClusterLockService clusterLockService;
    private final DataStoreServiceConfig dataStoreServiceConfig;
    private final FsPathHelper fileSystemStreamPathHelper;
    private final MetaService metaService;
    private final PhysicalDelete physicalDelete;
    private final DataVolumeDao dataVolumeDao;
    private final TaskContextFactory taskContextFactory;
    private final TaskContext taskContext;
    private final ExecutorProvider executorProvider;
    private final DataStoreServiceConfig config;

    @Inject
    PhysicalDeleteExecutor(
            final ClusterLockService clusterLockService,
            final DataStoreServiceConfig dataStoreServiceConfig,
            final FsPathHelper fileSystemStreamPathHelper,
            final MetaService metaService,
            final PhysicalDelete physicalDelete,
            final DataVolumeDao dataVolumeDao,
            final TaskContextFactory taskContextFactory,
            final TaskContext taskContext,
            final ExecutorProvider executorProvider,
            final DataStoreServiceConfig config) {
        this.clusterLockService = clusterLockService;
        this.dataStoreServiceConfig = dataStoreServiceConfig;
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.metaService = metaService;
        this.physicalDelete = physicalDelete;
        this.dataVolumeDao = dataVolumeDao;
        this.taskContextFactory = taskContextFactory;
        this.taskContext = taskContext;
        this.executorProvider = executorProvider;
        this.config = config;
    }

    public void exec() {
        LOGGER.info(() -> TASK_NAME + " - start");
        clusterLockService.tryLock(LOCK_NAME, () -> {
            try {
                if (!Thread.currentThread().isInterrupted()) {
                    final LogExecutionTime logExecutionTime = new LogExecutionTime();
                    getDeleteThreshold(dataStoreServiceConfig)
                            .ifPresent(this::delete);
                    LOGGER.info("{} - finished in {}", TASK_NAME, logExecutionTime);
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        });
    }

    public void delete(final Instant deleteThreshold) {
        if (!Thread.currentThread().isInterrupted()) {

            final LogExecutionTime logExecutionTime = LogExecutionTime.start();

            final int deleteBatchSize = dataStoreServiceConfig.getDeleteBatchSize();

            Instant currentDeleteThreshold = deleteThreshold;
            Instant lastDeleteThreshold = null;

            long count;
            long total = 0;
            if (!Thread.currentThread().isInterrupted() && currentDeleteThreshold != null) {
                final ThreadPool threadPool = new ThreadPoolImpl("Data Delete#", Thread.MIN_PRIORITY);
                final Executor executor = executorProvider.get(threadPool);
                final Set<Long> metaIdExcludeSet = new HashSet<>();

                do {
                    // Get a batch of meta ids that are ready for actual deletion.
                    final List<SimpleMeta> simpleMetas = metaService.getLogicallyDeleted(
                            currentDeleteThreshold,
                            deleteBatchSize,
                            metaIdExcludeSet);

                    count = simpleMetas.size();

                    // If we found some ids then try and physically delete this batch.
                    if (count > 0) {
                        LOGGER.debug("currentDeleteThreshold: {}, count:{}, total: {}",
                                currentDeleteThreshold, count, total);
                        final long finalCount = count;
                        final long finalTotal = total;
                        info(() -> LogUtil.message("Deleting files for {} stream{}. Stream{} processed so far: {}",
                                finalCount,
                                StringUtil.pluralSuffix(finalCount),
                                StringUtil.pluralSuffix(finalTotal),
                                finalTotal));
                        total += count;

                        final WorkQueue workQueue = new WorkQueue(
                                executor,
                                config.getFileSystemCleanBatchSize(),
                                simpleMetas.size());

                        final Set<SimpleMeta> failedMetaIds = deleteCurrentBatch(
                                taskContext,
                                simpleMetas,
                                currentDeleteThreshold,
                                workQueue);

                        // Advance the currentDeleteThreshold backwards in time so it is equal to the
                        // earliest one in our batch
                        lastDeleteThreshold = currentDeleteThreshold;
                        currentDeleteThreshold = simpleMetas
                                .stream()
                                .map(SimpleMeta::getStatusTime)
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .min(Comparator.naturalOrder())
                                .orElse(null);

                        LOGGER.debug("currentDeleteThreshold: {}, lastDeleteThreshold: {}",
                                currentDeleteThreshold, lastDeleteThreshold);

                        if (NullSafe.hasItems(failedMetaIds)) {
                            LOGGER.error("Failed to delete files for {} meta records. Check logs for error messages.",
                                    failedMetaIds.size());
                            if (currentDeleteThreshold != null) {

                                // As our next batch will be anything <= the new currentDeleteThreshold, we need
                                // to exclude any metas that have the same status time as the new threshold
                                // else they will get picked up again.
                                final Instant currentDeleteThresholdCopy = currentDeleteThreshold;
                                final Set<Long> newMetaIdExcludeSet = failedMetaIds.stream()
                                        .filter(failedMeta -> Objects.equals(
                                                currentDeleteThresholdCopy,
                                                failedMeta.getStatusTime().orElse(null)))
                                        .map(SimpleMeta::getId)
                                        .collect(Collectors.toSet());

                                // It is possible that we have >deleteBatchSize meta records with the same
                                // status time (e.g. from some bulk ingest) so need to allow for our exclude list
                                // growing on each iteration
                                if (Objects.equals(currentDeleteThreshold, lastDeleteThreshold)) {
                                    metaIdExcludeSet.addAll(newMetaIdExcludeSet);
                                } else {
                                    metaIdExcludeSet.clear();
                                }
                            }
                        } else {
                            metaIdExcludeSet.clear();
                        }
                    } else {
                        currentDeleteThreshold = null;
                    }
                } while (!Thread.currentThread().isInterrupted()
                        && count >= deleteBatchSize
                        && currentDeleteThreshold != null);
            }

            LOGGER.info("{} - Deleted {} streams in {}.", TASK_NAME, total, logExecutionTime);
        }
    }

    private Set<SimpleMeta> deleteCurrentBatch(final TaskContext taskContext,
                                               final List<SimpleMeta> simpleMetas,
                                               final Instant deleteThresholdEpoch,
                                               final WorkQueue workQueue) {
        Objects.requireNonNull(simpleMetas);
        Objects.requireNonNull(deleteThresholdEpoch);
        Objects.requireNonNull(workQueue);

        Set<SimpleMeta> failedMetasSet = null;
        try {
            final LinkedBlockingQueue<Long> successfulMetaIdDeleteQueue = new LinkedBlockingQueue<>();
            final Map<Path, Path> directoryMap = new ConcurrentHashMap<>();


            final DurationTimer durationTimer = DurationTimer.start();
            // Delete all matching files.
            for (final SimpleMeta simpleMeta : simpleMetas) {
                final Runnable runnable = deleteFiles(simpleMeta,
                        taskContext,
                        successfulMetaIdDeleteQueue,
                        directoryMap);
                workQueue.exec(runnable);
            }

            // Wait for all completable futures to complete.
            workQueue.join();
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            // Can't use logDurationIfDebugEnabled due to InterruptedException
            LOGGER.debug(() -> LogUtil.message("Deleting files for {} meta records took {}",
                    simpleMetas.size(), durationTimer.get()));

            // Cleanup empty directories.
            LOGGER.logDurationIfDebugEnabled(
                    () -> directoryMap.forEach((dir, root) ->
                            tryDeleteDir(root, dir, deleteThresholdEpoch.toEpochMilli())),
                    LogUtil.message("Deleting empty directories for {} meta records", simpleMetas.size()));

            if (Thread.interrupted()) {
                throw new InterruptedException();
            }

            final Set<Long> successfulMetaIdSet = new HashSet<>(successfulMetaIdDeleteQueue.size());
            successfulMetaIdDeleteQueue.drainTo(successfulMetaIdSet);

            failedMetasSet = simpleMetas.stream()
                    .filter(simpleMeta -> !successfulMetaIdSet.contains(simpleMeta.getId()))
                    .collect(Collectors.toSet());

            // Delete data volumes.
            info(() -> "Deleting data volumes");
            LOGGER.logDurationIfDebugEnabled(
                    () -> dataVolumeDao.delete(successfulMetaIdSet),
                    LogUtil.message("Delete data volume records for {} meta IDs", successfulMetaIdSet.size()));

            // Physically delete meta data.
            info(() -> "Deleting meta data");
            LOGGER.logDurationIfDebugEnabled(
                    () -> physicalDelete.cleanup(successfulMetaIdSet),
                    LogUtil.message("Delete meta and meta value records for {} meta IDs", successfulMetaIdSet.size()));

        } catch (final InterruptedException e) {
            LOGGER.debug("{} - {}", TASK_NAME, e.getMessage(), e);

            LOGGER.trace(e::getMessage, e);
            // Keep interrupting this thread.
            Thread.currentThread().interrupt();
        }
        return Objects.requireNonNullElseGet(failedMetasSet, Collections::emptySet);
    }

    private void tryDeleteDir(final Path root,
                              final Path dir,
                              final long oldFileTime) {
        try {
            final String canonicalRoot = FileUtil.getCanonicalPath(root);
            final String canonicalDir = FileUtil.getCanonicalPath(dir);

            if (canonicalRoot.length() > 2
                    && canonicalDir.startsWith(canonicalRoot)
                    && !Files.isSameFile(root, dir)) {
                final long lastModified = Files.getLastModifiedTime(dir).toMillis();

                if (lastModified < oldFileTime) {
                    try {
                        Files.delete(dir);
                        LOGGER.debug("tryDelete() - Deleted dir {}", canonicalDir);

                        // Recurse.
                        tryDeleteDir(root, dir.getParent(), oldFileTime);
                    } catch (final IOException e) {
                        LOGGER.debug("tryDelete() - Failed to delete dir {}", canonicalDir);
                    }

                } else {
                    LOGGER.debug("tryDelete() - Dir too new to delete {}", canonicalDir);
                }
            }
        } catch (final IOException e) {
            LOGGER.error("tryDelete() - Failed to delete dir {}", FileUtil.getCanonicalPath(dir), e);
        }
    }

    private Runnable deleteFiles(final SimpleMeta simpleMeta,
                                 final TaskContext parentTaskContext,
                                 final Queue<Long> successfulMetaIdDeleteQueue,
                                 final Map<Path, Path> directoryMap) {
        return taskContextFactory.childContext(
                parentTaskContext,
                "Deleting files",
                taskContext -> {
                    try {
                        if (Thread.interrupted()) {
                            throw new InterruptedException();
                        }

                        info(() -> "Deleting everything associated with " + simpleMeta);

                        final DataVolume dataVolume = dataVolumeDao.findDataVolume(simpleMeta.getId());
                        if (dataVolume == null) {
                            LOGGER.warn(() -> "Unable to find any volume for " + simpleMeta);

                        } else {
                            final Path volumePath = Paths.get(dataVolume.getVolumePath());
                            final Path file = fileSystemStreamPathHelper.getRootPath(
                                    volumePath,
                                    simpleMeta,
                                    simpleMeta.getTypeName());
                            final Path dir = file.getParent();
                            String baseName = file.getFileName().toString();
                            baseName = baseName.substring(0, baseName.indexOf("."));

                            if (Files.isDirectory(dir)) {
                                final String glob = baseName + ".*";
                                try (final DirectoryStream<Path> stream = Files.newDirectoryStream(dir, glob)) {
                                    stream.forEach(f -> {
                                        try {
                                            info(() -> "Deleting file: '"
                                                    + FileUtil.getCanonicalPath(f)
                                                    + "' for stream "
                                                    + simpleMeta.getId());
                                            Files.deleteIfExists(f);
                                        } catch (final IOException e) {
                                            LOGGER.debug(e.getMessage(), e);
                                            LOGGER.error("Error deleting file '" +
                                                    FileUtil.getCanonicalPath(f) +
                                                    "' for meta ID " +
                                                    simpleMeta.getId() +
                                                    " " +
                                                    e.getMessage());
                                        }
                                    });
                                } catch (final IOException e) {
                                    LOGGER.debug(e.getMessage(), e);
                                    LOGGER.error("Error creating directory stream '" +
                                            FileUtil.getCanonicalPath(dir) +
                                            "' glob=" +
                                            glob +
                                            " " +
                                            e.getMessage());
                                }

                                directoryMap.put(dir, volumePath);
                            } else {
                                LOGGER.warn("Directory does not exist '" +
                                        FileUtil.getCanonicalPath(dir) +
                                        "'");
                            }
                        }

                        successfulMetaIdDeleteQueue.add(simpleMeta.getId());

                    } catch (final InterruptedException e) {
                        LOGGER.trace(e::getMessage, e);
                        // Keep interrupting this thread.
                        Thread.currentThread().interrupt();
                    }
                });
    }

    private void info(final Supplier<String> message) {
        try {
            taskContext.info(message);
            LOGGER.debug(message);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private Optional<Instant> getDeleteThreshold(final DataStoreServiceConfig config) {
        final StroomDuration deletePurgeAge = config.getDeletePurgeAge();
        return Optional.ofNullable(deletePurgeAge)
                .map(purgeAge -> {
                    try {
                        final Instant deleteThreshold = TimeUtils.durationToThreshold(purgeAge);
                        LOGGER.debug("Using deleteThreshold: {} for deletePurgeAge: {}", deleteThreshold, purgeAge);
                        return deleteThreshold;
                    } catch (Exception e) {
                        throw new RuntimeException(LogUtil.message("Invalid value {} for property '{}'",
                                deletePurgeAge,
                                config.getFullPathStr(DataStoreServiceConfig.PROP_NAME_DELETE_PURGE_AGE)), e);
                    }
                });
    }
}
