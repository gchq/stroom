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

package stroom.processor.impl.db;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.processor.impl.ProcessorConfig;
import stroom.processor.impl.ProcessorDao;
import stroom.processor.impl.ProcessorFilterDao;
import stroom.processor.impl.ProcessorModule;
import stroom.processor.impl.ProcessorTaskDao;
import stroom.processor.impl.ProcessorTaskDeleteExecutor;
import stroom.processor.shared.ProcessorFilter;
import stroom.security.api.DocumentPermissionService;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.LogUtil;
import stroom.util.time.StroomDuration;
import stroom.util.time.TimeUtils;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Singleton
class ProcessorTaskDeleteExecutorImpl implements ProcessorTaskDeleteExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorTaskDeleteExecutorImpl.class);

    private static final String TASK_NAME = ProcessorModule.PROCESSOR_TASK_RETENTION_JOB_NAME;
    private static final String LOCK_NAME = "ProcessorTaskDeleteExecutor";

    private final ClusterLockService clusterLockService;
    private final ProcessorConfig processorConfig;
    private final ProcessorDao processorDao;
    private final ProcessorFilterDao processorFilterDao;
    private final ProcessorTaskDao processorTaskDao;
    private final TaskContextFactory taskContextFactory;
    private final DocumentPermissionService documentPermissionService;

    @Inject
    ProcessorTaskDeleteExecutorImpl(final ClusterLockService clusterLockService,
                                    final ProcessorConfig processorConfig,
                                    final ProcessorDao processorDao,
                                    final ProcessorFilterDao processorFilterDao,
                                    final ProcessorTaskDao processorTaskDao,
                                    final TaskContextFactory taskContextFactory,
                                    final DocumentPermissionService documentPermissionService) {
        this.clusterLockService = clusterLockService;
        this.processorConfig = processorConfig;
        this.processorDao = processorDao;
        this.processorFilterDao = processorFilterDao;
        this.processorTaskDao = processorTaskDao;
        this.taskContextFactory = taskContextFactory;
        this.documentPermissionService = documentPermissionService;
    }

    /**
     * Synchronised as we don't want to run more than once concurrently.
     */
    public synchronized void exec() {
        try {
            final TaskContext taskContext = taskContextFactory.current();
            taskContext.info(() -> "Deleting old processor tasks");
            lockAndDelete();
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    final void lockAndDelete() {
        LOGGER.info(TASK_NAME + " - Start");
        clusterLockService.tryLock(LOCK_NAME, () -> {
            try {
                if (!Thread.currentThread().isInterrupted()) {
                    final LogExecutionTime logExecutionTime = new LogExecutionTime();

                    final StroomDuration deleteAge = processorConfig.getDeleteAge();

                    if (!deleteAge.isZero()) {
                        final Instant deleteThreshold = TimeUtils.durationToThreshold(deleteAge);
                        LOGGER.info(TASK_NAME + " - Using deleteAge: {}, deleteThreshold: {}",
                                deleteAge,
                                deleteThreshold);
                        // Delete qualifying records prior to this date.
                        delete(deleteThreshold);
                    }
                    LOGGER.info(TASK_NAME + " - Finished in {}", logExecutionTime);
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    // Only public for testing by stroom-app it seems
    @Override
    public void delete(final Instant deleteThreshold) {
        final TaskContext taskContext = taskContextFactory.current();

        // Logically delete tasks that are associated with filters that have been logically deleted for longer than the
        // threshold.
        final AtomicInteger totalCount = new AtomicInteger();
        taskContext.info(() -> "Logically deleting processor tasks for deleted processor filters");
        runWithCountAndTimeLogging(() -> processorTaskDao.logicalDeleteForDeletedProcessorFilters(deleteThreshold),
                totalCount,
                "Logically deleted {} processor tasks for deleted processor filters");

        // Logically delete COMPLETE processor filters with no outstanding tasks where the tracker last poll is older
        // than the threshold. Note that COMPLETE just means that we have finished producing tasks on the DB, but we
        // can't delete the filter until all associated tasks have been processed otherwise they will never be picked
        // up.
        taskContext.info(() -> "Logically deleting old processor filters with a state of COMPLETED and no tasks");
        runWithCountAndTimeLogging(() -> processorFilterDao.logicallyDeleteOldProcessorFilters(deleteThreshold),
                totalCount,
                "Logically deleted {} old processor filters with a state of " + "COMPLETED and no tasks");

        // Physically delete tasks that are logically deleted or complete for longer than the threshold.
        taskContext.info(() -> "Physically deleting old processor tasks with a state of (COMPLETED|DELETED)");
        runWithCountAndTimeLogging(() -> processorTaskDao.physicallyDeleteOldTasks(deleteThreshold),
                totalCount,
                "Physically deleted {} old processor tasks with a state of " + "(COMPLETED|DELETED)");

        // Physically delete old filters.
        taskContext.info(() -> "Physically deleting old processor filters with state of DELETED");
        runWithCountAndTimeLogging(() -> deletingOldProcessorFilters(deleteThreshold),
                totalCount,
                "Physically deleted {} old processor filters with state of DELETED");

        // Physically delete old processors.
        // Not sure what the point of this is as we never logically delete processors.
        taskContext.info(() -> "Physically deleting old processors with state of DELETED");
        runWithCountAndTimeLogging(() -> processorDao.physicalDeleteOldProcessors(deleteThreshold),
                totalCount,
                "Physically deleted {} old processors with state of DELETED");

        if (totalCount.get() == 0) {
            LOGGER.info("{} - No records logically or physically deleted", TASK_NAME);
        }
    }

    private int deletingOldProcessorFilters(final Instant deleteThreshold) {
        final Set<String> procFilterUuids = processorFilterDao.physicalDeleteOldProcessorFilters(deleteThreshold);

        if (!procFilterUuids.isEmpty()) {
            // Now delete any perms for the deleted filters
            LOGGER.debug(() -> LogUtil.message("Deleting doc_permission records for {} doc UUIDs",
                    procFilterUuids.size()));
            final Set<DocRef> docRefs = procFilterUuids
                    .stream()
                    .map(uuid -> new DocRef(ProcessorFilter.ENTITY_TYPE, uuid))
                    .collect(Collectors.toSet());
            documentPermissionService.removeAllDocumentPermissions(docRefs);
        }

        return procFilterUuids.size();
    }

    private void runWithCountAndTimeLogging(final Supplier<Integer> action,
                                            final AtomicInteger totalCount,
                                            final String messageTemplate) {

        final Integer count;
        if (LOGGER.isInfoEnabled()) {
            final Instant startTime = Instant.now();
            count = action.get();
            if (count > 0) {
                try {
                    LOGGER.info("{} - " + messageTemplate + " in {}",
                            TASK_NAME,
                            count,
                            Duration.between(startTime, Instant.now()));
                } catch (final RuntimeException e) {
                    LOGGER.error("Error logging message - messageTemplate: '{}', error: {}",
                            messageTemplate,
                            e.getMessage(),
                            e);
                }
            }
        } else {
            count = action.get();
        }
        totalCount.addAndGet(count);
    }
}
