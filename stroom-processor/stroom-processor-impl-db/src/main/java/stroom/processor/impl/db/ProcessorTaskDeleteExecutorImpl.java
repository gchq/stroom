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

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;

class ProcessorTaskDeleteExecutorImpl implements ProcessorTaskDeleteExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorTaskDeleteExecutorImpl.class);

    private static final String TASK_NAME = "Processor Task Delete Executor";
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
        LOGGER.info(TASK_NAME + " - start");
        clusterLockService.tryLock(LOCK_NAME, () -> {
            try {
                if (!Thread.currentThread().isInterrupted()) {
                    final LogExecutionTime logExecutionTime = new LogExecutionTime();

                    final StroomDuration deleteAge = processorConfig.getDeleteAge();

                    if (!deleteAge.isZero()) {
                        final Instant deleteThreshold = TimeUtils.durationToThreshold(deleteAge);
                        LOGGER.info(TASK_NAME + " - using deleteAge: {}, deleteThreshold: {}",
                                deleteAge,
                                deleteThreshold);
                        // Delete qualifying records prior to this date.
                        delete(deleteThreshold);
                    }
                    LOGGER.info(TASK_NAME + " - finished in {}", logExecutionTime);
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
        processorTaskDao.logicalDeleteForDeletedProcessorFilters(deleteThreshold);

        // Logically delete COMPLETE processor filters with no outstanding tasks where the tracker last poll is older
        // than threshold
        processorFilterDao.logicallyDeleteOldProcessorFilters(deleteThreshold);

        // Physically delete tasks that are logically deleted or complete for longer than the threshold.
        processorTaskDao.physicallyDeleteOldTasks(deleteThreshold);

        // Physically delete old filters.
        processorFilterDao.physicalDeleteOldProcessorFilters(deleteThreshold);

        // Physically delete old processors.
        processorDao.physicalDeleteOldProcessors(deleteThreshold);
    }
}
