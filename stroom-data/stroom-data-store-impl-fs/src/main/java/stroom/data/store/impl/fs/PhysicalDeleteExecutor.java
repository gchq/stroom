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
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.time.StroomDuration;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

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
            final ExecutorProvider executorProvider,
            final DataStoreServiceConfig config) {
        this.clusterLockService = clusterLockService;
        this.dataStoreServiceConfig = dataStoreServiceConfig;
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.metaService = metaService;
        this.physicalDelete = physicalDelete;
        this.dataVolumeDao = dataVolumeDao;
        this.taskContextFactory = taskContextFactory;
        this.executorProvider = executorProvider;
        this.config = config;
    }

    public void exec() {
        taskContextFactory.context("Physically Delete Data", this::lockAndDelete).run();
    }

    final void lockAndDelete(final TaskContext taskContext) {
        LOGGER.info(() -> TASK_NAME + " - start");
        clusterLockService.tryLock(LOCK_NAME, () -> {
            try {
                if (!Thread.currentThread().isInterrupted()) {
                    final LogExecutionTime logExecutionTime = new LogExecutionTime();
                    final long deleteThresholdEpochMs = getDeleteThresholdEpochMs(dataStoreServiceConfig);
                    if (deleteThresholdEpochMs > 0) {
                        delete(taskContext, deleteThresholdEpochMs);
                    }
                    LOGGER.info(() -> TASK_NAME + " - finished in " + logExecutionTime);
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        });
    }

    public void delete(final TaskContext taskContext, final long deleteThresholdEpochMs) {
        if (!Thread.currentThread().isInterrupted()) {
            long count;
            long total = 0;

            final LogExecutionTime logExecutionTime = new LogExecutionTime();

            final int deleteBatchSize = dataStoreServiceConfig.getDeleteBatchSize();

            long minId = 0;
            if (!Thread.currentThread().isInterrupted()) {
                final ThreadPool threadPool = new ThreadPoolImpl("Data Delete#", 1, 1, config.getFileSystemCleanBatchSize(), Integer.MAX_VALUE);
                final Executor executor = executorProvider.get(threadPool);

                do {
                    // Increment the minimum id.
                    //
                    // Using a minimum id ensures we don't get stuck if we are unable to delete some meta data due to
                    // difficulties removing files.
                    minId++;

                    // Get a batch of meta ids that are ready for actual deletion.
                    final List<Meta> metaList = getDeleteList(minId, deleteThresholdEpochMs, deleteBatchSize);
                    count = metaList.size();

                    // If we inserted some ids then try and delete this batch.
                    if (count > 0) {
                        // Calculate the next minimum id to query for.
                        final Optional<Long> maxId = metaList
                                .stream()
                                .map(Meta::getId)
                                .max(Comparator.naturalOrder());
                        if (maxId.isPresent()) {
                            minId = Math.max(minId, maxId.get());
                        }

                        total += count;
                        deleteCurrentBatch(taskContext, metaList, deleteThresholdEpochMs, executor);
                    }
                } while (!Thread.currentThread().isInterrupted() && count >= deleteBatchSize);
            }

            // Done with if as total is not final so can't be in a lambda
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Deleted {} streams in {}.", total, logExecutionTime);
            }
        }
    }

    private void deleteCurrentBatch(final TaskContext taskContext, final List<Meta> metaList, final long deleteThresholdEpochMs, final Executor executor) {
        try {
            final LinkedBlockingQueue<Long> successfulMetaIdDeleteQueue = new LinkedBlockingQueue<>();
            final Map<Path, Path> directoryMap = new ConcurrentHashMap<>();

            // Delete all matching files.
            final List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (final Meta meta : metaList) {
                final Runnable runnable = deleteFiles(meta, taskContext, successfulMetaIdDeleteQueue, directoryMap);
                final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(runnable, executor);
                futures.add(completableFuture);
            }

            // Wait for all completable futures to complete.
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }

            // Cleanup empty directories.
            directoryMap.forEach((dir, root) ->
                    tryDeleteDir(root, dir, deleteThresholdEpochMs));

            if (Thread.interrupted()) {
                throw new InterruptedException();
            }

            final List<Long> metaIdList = new ArrayList<>();
            successfulMetaIdDeleteQueue.drainTo(metaIdList);

            // Delete data volumes.
            info(taskContext, () -> "Deleting data volumes");
            dataVolumeDao.delete(metaIdList);

            // Physically delete meta data.
            info(taskContext, () -> "Deleting meta data");
            physicalDelete.cleanup(metaIdList);

        } catch (final InterruptedException e) {
            LOGGER.debug(e::getMessage, e);

            // Continue to interrupt.
            Thread.currentThread().interrupt();
        }
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

    private Runnable deleteFiles(final Meta meta,
                                 final TaskContext parentTaskContext,
                                 final Queue<Long> successfulMetaIdDeleteQueue,
                                 final Map<Path, Path> directoryMap) {
        return taskContextFactory.context(parentTaskContext, "Deleting files", taskContext -> {
            try {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }

                info(taskContext, () -> "Deleting everything associated with " + meta);

                final DataVolume dataVolume = dataVolumeDao.findDataVolume(meta.getId());
                if (dataVolume == null) {
                    LOGGER.warn(() -> "Unable to find any volume for " + meta);

                } else {
                    final Path volumePath = Paths.get(dataVolume.getVolumePath());
                    final Path file = fileSystemStreamPathHelper.getRootPath(volumePath, meta, meta.getTypeName());
                    final Path dir = file.getParent();
                    String baseName = file.getFileName().toString();
                    baseName = baseName.substring(0, baseName.indexOf("."));

                    try (final DirectoryStream<Path> stream = Files.newDirectoryStream(dir, baseName + ".*")) {
                        stream.forEach(f -> {
                            try {
                                info(taskContext, () -> "Deleting file: " + FileUtil.getCanonicalPath(f));
                                Files.deleteIfExists(f);
                            } catch (final IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }

                    directoryMap.put(dir, volumePath);
                }

                successfulMetaIdDeleteQueue.add(meta.getId());

            } catch (final InterruptedException e) {
                LOGGER.debug(e::getMessage, e);

                // Continue to interrupt.
                Thread.currentThread().interrupt();
            }
        });
    }

    private void info(final TaskContext taskContext, final Supplier<String> message) {
        try {
            taskContext.info(message);
            LOGGER.debug(message);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private List<Meta> getDeleteList(final long minId, final long deleteThresholdEpochMs, final int batchSize) {
        final ExpressionOperator expression = new ExpressionOperator.Builder()
                .addTerm(
                        MetaFields.STATUS,
                        Condition.EQUALS,
                        Status.DELETED.getDisplayValue())
                .addTerm(
                        MetaFields.STATUS_TIME,
                        Condition.LESS_THAN,
                        DateUtil.createNormalDateTimeString(deleteThresholdEpochMs))
                .addTerm(
                        MetaFields.ID,
                        Condition.GREATER_THAN_OR_EQUAL_TO,
                        minId
                )
                .build();

        final FindMetaCriteria criteria = new FindMetaCriteria(expression);
        criteria.setSort(MetaFields.ID.getName());
        criteria.obtainPageRequest().setLength(batchSize);

        return metaService.find(criteria).getValues();
    }

    private Long getDeleteThresholdEpochMs(final DataStoreServiceConfig config) {
        Long deleteThresholdEpochMs = null;
        final StroomDuration deletePurgeAge = config.getDeletePurgeAge();
        if (deletePurgeAge != null) {
            try {
                deleteThresholdEpochMs = System.currentTimeMillis() - deletePurgeAge.toMillis();
            } catch (final RuntimeException e) {
                LOGGER.error(() -> "Error reading config");
            }
        }
        return deleteThresholdEpochMs;
    }
}
