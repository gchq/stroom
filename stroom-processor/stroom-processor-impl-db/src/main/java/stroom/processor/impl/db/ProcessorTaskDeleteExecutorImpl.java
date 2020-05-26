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
import stroom.db.util.JooqUtil;
import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.impl.ProcessorConfig;
import stroom.processor.impl.ProcessorFilterDao;
import stroom.processor.impl.ProcessorTaskDeleteExecutor;
import stroom.processor.impl.ProcessorTaskManager;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterFields;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.util.date.DateUtil;
import stroom.util.logging.LogExecutionTime;
import stroom.util.time.StroomDuration;
import stroom.util.time.TimeUtils;

import org.jooq.Condition;
import org.jooq.Record1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static stroom.processor.impl.db.jooq.tables.Processor.PROCESSOR;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilter.PROCESSOR_FILTER;
import static stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK;

class ProcessorTaskDeleteExecutorImpl implements ProcessorTaskDeleteExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorTaskDeleteExecutorImpl.class);

    private static final String TASK_NAME = "Processor Task Delete Executor";
    private static final String LOCK_NAME = "ProcessorTaskDeleteExecutor";

    private final ProcessorDbConnProvider processorDbConnProvider;
    private final ClusterLockService clusterLockService;
    private final ProcessorConfig processorConfig;
    private final ProcessorFilterDao processorFilterDao;
    private final ProcessorTaskManager processorTaskManager;

    @Inject
    ProcessorTaskDeleteExecutorImpl(final ProcessorDbConnProvider processorDbConnProvider,
                                    final ClusterLockService clusterLockService,
                                    final ProcessorConfig processorConfig,
                                    final ProcessorFilterDao processorFilterDao,
                                    final ProcessorTaskManager processorTaskManager) {
        this.processorDbConnProvider = processorDbConnProvider;
        this.clusterLockService = clusterLockService;
        this.processorConfig = processorConfig;
        this.processorFilterDao = processorFilterDao;
        this.processorTaskManager = processorTaskManager;
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
                        delete(deleteThreshold);
                    }
                    LOGGER.info(TASK_NAME + " - finished in {}", logExecutionTime);
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    @Override
    public void delete(final Instant deleteThreshold) {
        deleteOldTasks(deleteThreshold);
        deleteOldFilters(deleteThreshold);
        deleteDeletedTasksAndProcessors();
    }

    private int deleteOldTasks(final Instant deleteThreshold) {
        final Collection<Condition> conditions = JooqUtil.conditions(
                Optional.of(PROCESSOR_TASK.STATUS.in(
                    TaskStatus.COMPLETE.getPrimitiveValue(),
                    TaskStatus.FAILED.getPrimitiveValue())),
                Optional.of(PROCESSOR_TASK.CREATE_TIME_MS.isNull()
                    .or(PROCESSOR_TASK.CREATE_TIME_MS.lessThan(deleteThreshold.toEpochMilli()))));

        return JooqUtil.contextResult(processorDbConnProvider, context ->
                context
                        .deleteFrom(PROCESSOR_TASK)
                        .where(conditions)
                        .execute());
    }

    private int deleteDeletedTasksAndProcessors() {
        // Get deleted processors.
        List<Integer> deletedProcessors = JooqUtil.contextResult(processorDbConnProvider, context -> context
                        .select(PROCESSOR.ID)
                        .from(PROCESSOR)
                        .where(PROCESSOR.DELETED.eq(true))
                        .fetch()
                        .map(Record1::value1));

        List<Integer> deletedProcessorFilters = JooqUtil.contextResult(processorDbConnProvider, context -> context
                .select(PROCESSOR_FILTER.ID)
                .from(PROCESSOR_FILTER)
                .where(PROCESSOR_FILTER.DELETED.eq(true))
                .or(PROCESSOR_FILTER.FK_PROCESSOR_ID.in(deletedProcessors))
                .fetch()
                .map(Record1::value1));

        // Delete tasks.
        int count = JooqUtil.contextResult(processorDbConnProvider, context ->
                context
                        .deleteFrom(PROCESSOR_TASK)
                        .where(PROCESSOR_TASK.STATUS.eq(TaskStatus.DELETED.getPrimitiveValue()))
                        .or(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID.in(deletedProcessorFilters))
                        .execute());

        // Delete filters one by one as there may still be some constraint failures.
        for (final int id : deletedProcessorFilters) {
            try {
                JooqUtil.context(processorDbConnProvider, context ->
                        context
                                .deleteFrom(PROCESSOR_FILTER)
                                .where(PROCESSOR_FILTER.ID.eq(id))
                                .execute());
            } catch (final RuntimeException e) {
                LOGGER.debug(e.getMessage(), e);
            }
        }

        // Delete processors one by one as there may still be some constraint failures.
        for (final int id : deletedProcessors) {
            try {
                JooqUtil.context(processorDbConnProvider, context ->
                        context
                                .deleteFrom(PROCESSOR)
                                .where(PROCESSOR.ID.eq(id))
                                .execute());
            } catch (final RuntimeException e) {
                LOGGER.debug(e.getMessage(), e);
            }
        }

        return count;
    }

    private void deleteOldFilters(final Instant deleteThreshold) {
        try {
            // Get all filters that have not been polled for a while.
            final ExpressionOperator expression = new ExpressionOperator.Builder()
                    .addTerm(
                        ProcessorFilterFields.LAST_POLL_MS,
                        ExpressionTerm.Condition.LESS_THAN,
                        deleteThreshold.toEpochMilli())
                    .build();
            final ExpressionCriteria criteria = new ExpressionCriteria(expression);
//            criteria.setLastPollPeriod(new Period(null, age));
            final List<ProcessorFilter> filters = processorFilterDao.find(criteria).getValues();
            for (final ProcessorFilter filter : filters) {
                final ProcessorFilterTracker tracker = filter.getProcessorFilterTracker();

                if (tracker != null && ProcessorFilterTracker.COMPLETE.equals(tracker.getStatus())) {
                    // The tracker thinks that no more tasks will ever be
                    // created for this filter so we can delete it if there are
                    // no remaining tasks for this filter.
                    //
                    // The database constraint will not allow filters to be
                    // deleted that still have associated tasks.
                    try {
                        LOGGER.debug("deleteCompleteOrFailedTasks() - Removing old complete filter {}", filter);
                        processorFilterDao.delete(filter.getId());

                    } catch (final RuntimeException e) {
                        // The database constraint will not allow filters to be
                        // deleted that still have associated tasks. This is
                        // what we want to happen but output debug here to help
                        // diagnose problems.
                        LOGGER.debug("deleteCompleteOrFailedTasks() - Failed as tasks still remain for this filter - "
                                + e.getMessage(), e);
                    }
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
