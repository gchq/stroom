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
import stroom.util.logging.LogUtil;
import stroom.util.time.StroomDuration;
import stroom.util.time.TimeUtils;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;

public class PhysicalDeleteExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PhysicalDeleteExecutor.class);

    public static final String TASK_NAME = "Data Delete";
    private static final String LOCK_NAME = "FsDeleteExecutor";

    private final ClusterLockService clusterLockService;
    private final Provider<DataStoreServiceConfig> dataStoreServiceConfigProvider;
    private final FsPathHelper fileSystemStreamPathHelper;
    private final MetaService metaService;
    private final PhysicalDelete physicalDelete;
    private final DataVolumeDao dataVolumeDao;
    private final TaskContextFactory taskContextFactory;
    private final TaskContext taskContext;
    private final ExecutorProvider executorProvider;
    private final FsFileDeleter fsFileDeleter;

    @Inject
    PhysicalDeleteExecutor(
            final ClusterLockService clusterLockService,
            final Provider<DataStoreServiceConfig> dataStoreServiceConfigProvider,
            final FsPathHelper fileSystemStreamPathHelper,
            final MetaService metaService,
            final PhysicalDelete physicalDelete,
            final DataVolumeDao dataVolumeDao,
            final TaskContextFactory taskContextFactory,
            final TaskContext taskContext,
            final ExecutorProvider executorProvider,
            final FsFileDeleter fsFileDeleter) {
        this.clusterLockService = clusterLockService;
        this.dataStoreServiceConfigProvider = dataStoreServiceConfigProvider;
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.metaService = metaService;
        this.physicalDelete = physicalDelete;
        this.dataVolumeDao = dataVolumeDao;
        this.taskContextFactory = taskContextFactory;
        this.taskContext = taskContext;
        this.executorProvider = executorProvider;
        this.fsFileDeleter = fsFileDeleter;
    }

    public void exec() {
        LOGGER.debug(() -> TASK_NAME + " - Trying lock " + LOCK_NAME);
        clusterLockService.tryLock(LOCK_NAME, () -> {
            try {
                LOGGER.debug(() -> TASK_NAME + " - Acquired lock " + LOCK_NAME);
                if (!Thread.currentThread().isInterrupted()) {
                    final DataStoreServiceConfig dataStoreServiceConfig = dataStoreServiceConfigProvider.get();
                    // Monitors all the totals for the whole run
                    final Progress progress = Progress.start(dataStoreServiceConfig);
                    getDeleteAgeThreshold(dataStoreServiceConfig)
                            .ifPresent(ageThreshold ->
                                    delete(ageThreshold, progress));
                    progress.logSummaryToInfo("Finished. Summary:");
                }
            } catch (final RuntimeException e) {
                LOGGER.error(TASK_NAME + " - " + e.getClass().getSimpleName() + " - " + e.getMessage(), e);
            }
        });
    }

    public void delete(final Instant deleteThreshold, final Progress progress) {
        if (!Thread.currentThread().isInterrupted()) {
            final DataStoreServiceConfig dataStoreServiceConfig = dataStoreServiceConfigProvider.get();
            final int deleteBatchSize = dataStoreServiceConfig.getDeleteBatchSize();
            LOGGER.info(() -> LogUtil.message(
                    "{} - Starting physical data deletion, deleteThreshold: {}, batchSize: {}",
                    TASK_NAME, deleteThreshold, deleteBatchSize));

            Instant slidingDeleteThreshold = deleteThreshold;
            Instant lastSlidingDeleteThreshold = null;

            long count;
            long total = 0;
            if (slidingDeleteThreshold != null) {
                final Executor executor = getExecutor();
                final Set<Long> metaIdExcludeSet = new HashSet<>();

                do {
                    if (Thread.currentThread().isInterrupted()) {
                        LOGGER.debug("{} - Thread interrupted", TASK_NAME);
                        break;
                    }
                    if (progress.hasBreachedThreshold()) {
                        LOGGER.error("{} - Aborting as failure threshold {} exceeded. " +
                                        "See property {}",
                                TASK_NAME,
                                dataStoreServiceConfig.getDeleteFailureThreshold(),
                                dataStoreServiceConfig.getFullPath(
                                        DataStoreServiceConfig.PROP_NAME_DELETE_FAILURE_THRESHOLD));
                        break;
                    }
                    // Get a batch of meta ids that are ready for actual deletion (logically deleted).
                    // metaIdExcludeSet ensures we don't pick up any from previous runs
                    final List<SimpleMeta> simpleMetas = metaService.getLogicallyDeleted(
                            slidingDeleteThreshold,
                            deleteBatchSize,
                            metaIdExcludeSet);

                    count = simpleMetas.size();

                    // If we found some ids then try and physically delete this batch.
                    if (count > 0) {
                        LOGGER.debug("{} - slidingDeleteThreshold: {}", TASK_NAME, slidingDeleteThreshold);
                        progress.incrementBatchCount();
                        final long finalCount = count;
                        final long finalTotal = total;
                        info(() -> LogUtil.message("Deleting files for {} streams. Streams processed so far: {}",
                                finalCount, finalTotal));
                        total += count;

                        final WorkQueue workQueue = new WorkQueue(
                                executor,
                                dataStoreServiceConfig.getFileSystemCleanBatchSize(),
                                simpleMetas.size());

                        // Attempt to delete the files for the streams then the DB records
                        final Set<SimpleMeta> failedMetaIds = deleteCurrentBatch(
                                taskContext,
                                simpleMetas,
                                deleteThreshold, // slidingDeleteThreshold only used for the DB qry
                                workQueue,
                                progress);

                        // Advance the slidingDeleteThreshold backwards in time, so it is equal to the
                        // earliest one in our batch. If there are lots of metas with the same status time
                        // then slidingDeleteThreshold may not change.
                        lastSlidingDeleteThreshold = slidingDeleteThreshold;
                        slidingDeleteThreshold = simpleMetas
                                .stream()
                                .map(SimpleMeta::getStatusMs)
                                .filter(Objects::nonNull)
                                .map(Instant::ofEpochMilli)
                                .min(Comparator.naturalOrder())
                                .orElse(null);

                        LOGGER.debug("{} - slidingDeleteThreshold: {}, lastDeleteThreshold: {}",
                                TASK_NAME, slidingDeleteThreshold, lastSlidingDeleteThreshold);

                        if (NullSafe.hasItems(failedMetaIds)) {
                            LOGGER.error("{} - Failed to delete files for {} meta records. " +
                                            "Check logs for error messages.",
                                    TASK_NAME, failedMetaIds.size());
                            if (slidingDeleteThreshold != null) {

                                // As our next batch will be anything <= the new slidingDeleteThreshold, we need
                                // to exclude any metas that have the same status time as the new threshold
                                // else they will get picked up again.
                                final Instant currentDeleteThresholdCopy = slidingDeleteThreshold;
                                final Set<Long> newMetaIdExcludeSet = failedMetaIds.stream()
                                        .filter(failedMeta -> Objects.equals(
                                                currentDeleteThresholdCopy,
                                                failedMeta.getStatusMs()))
                                        .map(SimpleMeta::getId)
                                        .collect(Collectors.toSet());

                                // It is possible that we have >deleteBatchSize meta records with the same
                                // status time (e.g. from some bulk ingest) so need to allow for our exclude list
                                // growing on each iteration
                                if (!Objects.equals(slidingDeleteThreshold, lastSlidingDeleteThreshold)) {
                                    // Different threshold time, so we can bin our old exclude set values
                                    metaIdExcludeSet.clear();
                                }
                                metaIdExcludeSet.addAll(newMetaIdExcludeSet);
                            }
                        } else {
                            metaIdExcludeSet.clear();
                        }
                        progress.logSummaryToDebug(() ->
                                "Progress after batch " + progress.getBatchCount() + ":");
                    }
                } while (count >= deleteBatchSize);
            }
        }
    }

    private Executor getExecutor() {
        final ThreadPool threadPool = new ThreadPoolImpl("Data Delete#", Thread.MIN_PRIORITY);
        return executorProvider.get(threadPool);
    }

    /**
     * Deletes a batch of simpleMetas from the file system and the database.
     *
     * @param deleteThresholdEpoch The threshold for statusTime, i.e. records <= deleteThresholdEpoch
     * @param progress             Count of failures that spans multiple batches
     * @return Those simpleMeta items that could not be deleted for some reason.
     */
    private Set<SimpleMeta> deleteCurrentBatch(final TaskContext taskContext,
                                               final List<SimpleMeta> simpleMetas,
                                               final Instant deleteThresholdEpoch,
                                               final WorkQueue workQueue,
                                               final Progress progress) {
        Objects.requireNonNull(simpleMetas);
        Objects.requireNonNull(deleteThresholdEpoch);
        Objects.requireNonNull(workQueue);

        Set<SimpleMeta> failedMetasSet = null;
        try {
            final Map<Path, Path> dirToVolPathMap = new ConcurrentHashMap<>(); // dir => volumePath

            // Delete all the files associated with simpleMetas
            final LinkedBlockingQueue<Long> successfulMetaIdDeleteQueue = deleteMetaFiles(
                    taskContext,
                    simpleMetas,
                    workQueue,
                    progress,
                    dirToVolPathMap);

            if (!progress.hasBreachedThreshold()) {
                // Remove any empty directories (including their ancestors, but not the root volumePath)
                LOGGER.logDurationIfDebugEnabled(
                        () -> {
                            dirToVolPathMap.forEach((dir, volumePath) ->
                                    fsFileDeleter.tryDeleteDir(
                                            volumePath,
                                            dir,
                                            deleteThresholdEpoch.toEpochMilli(),
                                            progress::addDirDeletes));
                        },
                        LogUtil.message("{} - Deleting empty directories for {} directories, {} meta IDs",
                                TASK_NAME, dirToVolPathMap.size(), simpleMetas.size()));

                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }

                final Set<Long> successfulMetaIdSet = new HashSet<>(successfulMetaIdDeleteQueue.size());
                successfulMetaIdDeleteQueue.drainTo(successfulMetaIdSet);
                final int successCount = successfulMetaIdSet.size();

                failedMetasSet = simpleMetas.stream()
                        .filter(simpleMeta -> !successfulMetaIdSet.contains(simpleMeta.getId()))
                        .collect(Collectors.toSet());

                // Delete data volumes.
                info(() -> LogUtil.message("Deleting data volumes for {} meta IDs", successCount));
                LOGGER.logDurationIfDebugEnabled(
                        () -> dataVolumeDao.delete(successfulMetaIdSet),
                        LogUtil.message("{} - Delete data volume records for {} meta IDs",
                                TASK_NAME, successfulMetaIdSet.size()));

                // Physically delete meta data.
                info(() -> LogUtil.message("Deleting meta data for {} meta IDs", successCount));
                LOGGER.logDurationIfDebugEnabled(
                        () -> physicalDelete.cleanup(successfulMetaIdSet),
                        LogUtil.message("{} - Delete meta and meta value records for {} meta IDs",
                                TASK_NAME, successCount));
            } else {
                LOGGER.debug("{} - Aborting as failure threshold breached", TASK_NAME);
            }
        } catch (final InterruptedException e) {
            LOGGER.debug("{} - {}", TASK_NAME, e.getMessage(), e);

            LOGGER.trace(e::getMessage, e);
            // Keep interrupting this thread.
            Thread.currentThread().interrupt();
        }
        return NullSafe.nonNullSet(failedMetasSet);
    }

    @NotNull
    private LinkedBlockingQueue<Long> deleteMetaFiles(
            final TaskContext taskContext,
            final List<SimpleMeta> simpleMetas,
            final WorkQueue workQueue,
            final Progress progress,
            final Map<Path, Path> dirToVolPathMap) throws InterruptedException {

        final LinkedBlockingQueue<Long> successfulMetaIdDeleteQueue = new LinkedBlockingQueue<>();
        final DurationTimer durationTimer = DurationTimer.start();

        // Delete all matching files with concurrent threads working on a simpleMeta at a time
        for (final SimpleMeta simpleMeta : simpleMetas) {
            // No point starting jobs if we have gone over failure limit
            if (progress.hasBreachedThreshold()) {
                break;
            }
            final Runnable runnable = deleteFiles(
                    simpleMeta,
                    taskContext,
                    successfulMetaIdDeleteQueue,
                    progress,
                    dirToVolPathMap);

            workQueue.exec(runnable);
        }

        // Wait for all completable futures to complete.
        workQueue.join();
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        // Can't use logDurationIfDebugEnabled due to InterruptedException
        LOGGER.debug(() -> LogUtil.message("{} - Deleting files for {} meta records took {}. " +
                        "Success count:{}, failure count: {}",
                TASK_NAME,
                simpleMetas.size(),
                durationTimer.get(),
                progress.getSuccessCount(),
                progress.getFailureCount()));
        return successfulMetaIdDeleteQueue;
    }

//    private boolean shouldAbort(final Progress progress) {
//        return progress.get() > dataStoreServiceConfig.getDeleteFailureThreshold();
//    }

    private Runnable deleteFiles(final SimpleMeta simpleMeta,
                                 final TaskContext parentTaskContext,
                                 final Queue<Long> successfulMetaIdDeleteQueue,
                                 final Progress progress,
                                 final Map<Path, Path> dirToVolPathMap) {

        final DataStoreServiceConfig dataStoreServiceConfig = dataStoreServiceConfigProvider.get();
        return taskContextFactory.childContext(
                parentTaskContext,
                "Deleting files",
                taskContext -> {
                    try {
                        if (Thread.interrupted()) {
                            throw new InterruptedException();
                        }
                        // This all needs to be re-runnable so if we come back again with partially deleted
                        // state it will cope
                        if (progress.hasBreachedThreshold()) {
                            LOGGER.warn("{} - Skipping file deletion for stream {} as failure threshold exceeded {}. " +
                                            "See property {}",
                                    TASK_NAME,
                                    simpleMeta.getId(),
                                    dataStoreServiceConfig.getDeleteFailureThreshold(),
                                    dataStoreServiceConfig.getFullPath(
                                            DataStoreServiceConfig.PROP_NAME_DELETE_FAILURE_THRESHOLD));
                        } else {
                            info(() -> LogUtil.message(
                                    "Physically deleting everything associated with stream: {}, " +
                                            "feed: {}, type: {}, statusTime: {}",
                                    simpleMeta.getId(), simpleMeta.getFeedName(),
                                    simpleMeta.getTypeName(),
                                    LogUtil.instant(simpleMeta.getStatusMs())));

                            final DataVolume dataVolume = dataVolumeDao.findDataVolume(simpleMeta.getId());
                            final boolean isSuccessful;
                            if (dataVolume == null) {
                                LOGGER.warn(() -> TASK_NAME + " - Unable to find any volume for " + simpleMeta);
                                isSuccessful = true;
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
                                    isSuccessful = fsFileDeleter.deleteFilesByBaseName(
                                            simpleMeta.getId(), dir, baseName, progress::addFileDeletes);

                                    dirToVolPathMap.put(dir, volumePath);
                                } else {
                                    isSuccessful = true;
                                    LOGGER.warn(TASK_NAME + " - Directory does not exist '" +
                                            FileUtil.getCanonicalPath(dir) +
                                            "'");
                                }
                            }

                            if (isSuccessful) {
                                successfulMetaIdDeleteQueue.add(simpleMeta.getId());
                            }
                            progress.recordMetaFileDeleteSuccess(isSuccessful);
                        }
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
            LOGGER.debug(() -> TASK_NAME + " - " + message.get());
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private Optional<Instant> getDeleteAgeThreshold(final DataStoreServiceConfig config) {
        final StroomDuration deletePurgeAge = config.getDeletePurgeAge();
        return Optional.ofNullable(deletePurgeAge)
                .map(purgeAge -> {
                    try {
                        final Instant deleteThreshold = TimeUtils.durationToThreshold(purgeAge);
                        LOGGER.debug("{} - Using deleteThreshold: {} for deletePurgeAge: {}",
                                TASK_NAME, deleteThreshold, purgeAge);
                        return deleteThreshold;
                    } catch (Exception e) {
                        throw new RuntimeException(LogUtil.message("Invalid value {} for property '{}'",
                                deletePurgeAge,
                                config.getFullPathStr(DataStoreServiceConfig.PROP_NAME_DELETE_PURGE_AGE)), e);
                    }
                });
    }


    // --------------------------------------------------------------------------------


    static final class Progress {

        private final DataStoreServiceConfig dataStoreServiceConfig;
        private final int failureThreshold;

        private DurationTimer durationTimer = DurationTimer.start();

        // Total count of the number of metas that have been successfully deleted
        private final AtomicInteger successCounter = new AtomicInteger(0);

        // Total count of the number of metas that had failures during file deletion
        private final AtomicInteger failureCounter = new AtomicInteger(0);

        // Total number of files deleted
        private final LongAdder fileDeleteCounter = new LongAdder();

        // Total number of empty directories deleted
        private final LongAdder dirDeleteCounter = new LongAdder();

        // Number of batches processed, successful or not
        private final AtomicInteger batchCounter = new AtomicInteger(0);

        private Progress(final DataStoreServiceConfig dataStoreServiceConfig) {
            this.dataStoreServiceConfig = dataStoreServiceConfig;
            this.failureThreshold = dataStoreServiceConfig.getDeleteFailureThreshold();
        }

        static Progress start(final DataStoreServiceConfig dataStoreServiceConfig) {
            return new Progress(dataStoreServiceConfig);
        }

        void addFileDeletes(final long count) {
            fileDeleteCounter.add(count);
        }

        void addDirDeletes(final long count) {
            dirDeleteCounter.add(count);
        }

        void recordMetaFileDeleteSuccess(final boolean success) {
            if (success) {
                successCounter.incrementAndGet();
            } else {
                failureCounter.incrementAndGet();
            }
        }

        void incrementBatchCount() {
            batchCounter.addAndGet(1);
        }

        int getFailureCount() {
            return failureCounter.get();
        }

        int getSuccessCount() {
            return successCounter.get();
        }

        long getFileDeleteCount() {
            return fileDeleteCounter.sum();
        }

        long getDirDeleteCount() {
            return dirDeleteCounter.sum();
        }

        int getBatchCount() {
            return batchCounter.get();
        }

        boolean hasBreachedThreshold() {
            return failureCounter.get() > failureThreshold;
        }

        Duration getDuration() {
            return durationTimer.get();
        }

        String buildSummaryBox() {
            return LogUtil.inBox("""
                            Duration: {}
                            Total streams count: {}
                            Successful streams count: {}
                            Failed streams count: {}
                            File delete count: {}
                            Directory delete count: {}
                            Failed streams threshold: {}
                            Batch count: {}
                            Batch size: {}""",
                    durationTimer.get(),
                    successCounter.get() + failureCounter.get(),
                    successCounter.get(),
                    failureCounter.get(),
                    fileDeleteCounter.sum(),
                    dirDeleteCounter.sum(),
                    failureThreshold,
                    batchCounter.get(),
                    dataStoreServiceConfig.getDeleteBatchSize());
        }

        void logSummaryToInfo(final String msg) {
            LOGGER.info(() -> LogUtil.message("{} - {}\n{}", TASK_NAME, msg, buildSummaryBox()));
        }

        void logSummaryToDebug(final Supplier<String> msgSupplier) {
            LOGGER.debug(() -> LogUtil.message("{} - {}\n{}",
                    TASK_NAME, msgSupplier.get(), buildSummaryBox()));
        }

        @Override
        public String toString() {
            return buildSummaryBox();
        }
    }
}
