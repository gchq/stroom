/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.processor.impl.db;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.processor.impl.ProcessorConfig;
import stroom.processor.impl.ProcessorDao;
import stroom.processor.impl.ProcessorFilterDao;
import stroom.processor.impl.ProcessorModule;
import stroom.processor.impl.ProcessorTaskDao;
import stroom.processor.impl.ProcessorTaskDeleteExecutor;
import stroom.processor.impl.ProcessorTaskManager;
import stroom.task.api.TaskContext;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.time.StroomDuration;
import stroom.util.time.TimeUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import javax.inject.Inject;

class ProcessorTaskDeleteExecutorImpl implements ProcessorTaskDeleteExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorTaskDeleteExecutorImpl.class);

    private static final String TASK_NAME = ProcessorModule.PROCESSOR_TASK_RETENTION_JOB_NAME;
    private static final String LOCK_NAME = "ProcessorTaskDeleteExecutor";

    private final ClusterLockService clusterLockService;
    private final ProcessorConfig processorConfig;
    private final ProcessorDao processorDao;
    private final ProcessorFilterDao processorFilterDao;
    private final ProcessorTaskDao processorTaskDao;
    private final ProcessorTaskManager processorTaskManager;
    private final TaskContext taskContext;

    @Inject
    ProcessorTaskDeleteExecutorImpl(final ClusterLockService clusterLockService,
                                    final ProcessorConfig processorConfig,
                                    final ProcessorDao processorDao,
                                    final ProcessorFilterDao processorFilterDao,
                                    final ProcessorTaskDao processorTaskDao,
                                    final ProcessorTaskManager processorTaskManager,
                                    final TaskContext taskContext) {
        this.clusterLockService = clusterLockService;
        this.processorConfig = processorConfig;
        this.processorDao = processorDao;
        this.processorFilterDao = processorFilterDao;
        this.processorTaskDao = processorTaskDao;
        this.processorTaskManager = processorTaskManager;
        this.taskContext = taskContext;
    }

    public void exec() {
        final AtomicLong nextDeleteMs = processorTaskManager.getNextDeleteMs();
        try {
            if (nextDeleteMs.get() == 0) {
                LOGGER.debug("deleteSchedule() - no schedule set .... maybe we aren't in charge of creating tasks");
            } else {
                LOGGER.debug("deleteSchedule() - nextDeleteMs={}",
                        DateUtil.createNormalDateTimeString(nextDeleteMs.get()));
                // Have we gone past our next delete schedule?
                if (nextDeleteMs.get() < System.currentTimeMillis()) {
                    taskContext.info(() -> "Deleting old processor tasks");
                    lockAndDelete();
                }
            }
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
        // Logically delete tasks that are associated with filters that have been logically deleted for longer than the
        // threshold.
        final AtomicInteger totalCount = new AtomicInteger();
        taskContext.info(() -> "Logically deleting processor tasks for deleted processor filters");
        runWithCountAndTimeLogging(
                () -> processorTaskDao.logicalDeleteForDeletedProcessorFilters(deleteThreshold),
                totalCount,
                "Logically deleted {} processor tasks for deleted processor filters");

        // Logically delete COMPLETE processor filters with no outstanding tasks where the tracker last poll is older
        // than the threshold. Note that COMPLETE just means that we have finished producing tasks on the DB, but we
        // can't delete the filter until all associated tasks have been processed otherwise they will never be picked
        // up.
        taskContext.info(() -> "Logically deleting old processor filters with a state of COMPLETED and no tasks");
        runWithCountAndTimeLogging(
                () -> processorFilterDao.logicallyDeleteOldProcessorFilters(deleteThreshold),
                totalCount,
                "Logically deleted {} old processor filters with a state of " +
                        "COMPLETED and no tasks");

        // Physically delete tasks that are logically deleted or complete for longer than the threshold.
        taskContext.info(() -> "Physically deleting old processor tasks with a state of (COMPLETED|DELETED)");
        runWithCountAndTimeLogging(
                () -> processorTaskDao.physicallyDeleteOldTasks(deleteThreshold),
                totalCount,
                "Physically deleted {} old processor tasks with a state of " +
                        "(COMPLETED|DELETED)");

        // Physically delete old filters.
        taskContext.info(() -> "Physically deleting old processor filters with state of DELETED");
        runWithCountAndTimeLogging(
                () -> processorFilterDao.physicalDeleteOldProcessorFilters(deleteThreshold),
                totalCount,
                "Physically deleted {} old processor filters with state of DELETED");

        // Physically delete old processors.
        taskContext.info(() -> "Physically deleting old processors with state of DELETED");
        runWithCountAndTimeLogging(
                () -> processorDao.physicalDeleteOldProcessors(deleteThreshold),
                totalCount,
                "Physically deleted {} old processors with state of DELETED");

        if (totalCount.get() == 0) {
            LOGGER.info("{} - No records logically or physically deleted", TASK_NAME);
        }
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
                            messageTemplate, e.getMessage(), e);
                }
            }
        } else {
            count = action.get();
        }
        totalCount.addAndGet(count);
    }

    private void logCount(final int count, final AtomicInteger totalCount, final String message) {
        totalCount.addAndGet(count);
        if (count > 0) {
            LOGGER.info(TASK_NAME + " - " + message, count);
        }
    }
}
