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

import org.jooq.Condition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.lock.api.ClusterLockService;
import stroom.db.util.JooqUtil;
import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.impl.ProcessorConfig;
import stroom.processor.impl.ProcessorFilterDao;
import stroom.processor.impl.ProcessorTaskDeleteExecutor;
import stroom.processor.impl.ProcessorTaskManager;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterDataSource;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.task.api.TaskContext;
import stroom.util.date.DateUtil;

import javax.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK;

class ProcessorTaskDeleteExecutorImpl extends AbstractBatchDeleteExecutor implements ProcessorTaskDeleteExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorTaskDeleteExecutorImpl.class);

    private static final String TASK_NAME = "Processor Task Delete Executor";
    private static final String LOCK_NAME = "ProcessorTaskDeleteExecutor";
    private static final String TEMP_STRM_TASK_ID_TABLE = "TEMP_PROCESSOR_TASK_ID";

    private final ConnectionProvider connectionProvider;
    private final ProcessorFilterDao processorFilterDao;
    private final ProcessorTaskManager processorTaskManager;

    @Inject
    ProcessorTaskDeleteExecutorImpl(final ConnectionProvider connectionProvider,
                                    final ClusterLockService clusterLockService,
                                    final ProcessorConfig processorConfig,
                                    final TaskContext taskContext,
                                    final ProcessorFilterDao processorFilterDao,
                                    final ProcessorTaskManager processorTaskManager) {
        super(clusterLockService, taskContext, TASK_NAME, LOCK_NAME, processorConfig, TEMP_STRM_TASK_ID_TABLE);

        this.connectionProvider = connectionProvider;
        this.processorFilterDao = processorFilterDao;
        this.processorTaskManager = processorTaskManager;

        final BatchIdTransactionHelper batchIdTransactionHelper = new BatchIdTransactionHelper(connectionProvider, TEMP_STRM_TASK_ID_TABLE);
        setBatchIdTransactionHelper(batchIdTransactionHelper);
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

    @Override
    public void delete(final long age) {
        super.delete(age);
        deleteOldFilters(age);
    }

    @Override
    protected void deleteCurrentBatch(final long total) {
        // Delete stream tasks.
        deleteWithJoin(PROCESSOR_TASK, PROCESSOR_TASK.ID, "stream tasks", total);
    }

    @Override
    protected List<Long> getDeleteIdList(final long age, final int batchSize) {
        final Collection<Condition> conditions = JooqUtil.conditions(
                Optional.of(PROCESSOR_TASK.STATUS.in(TaskStatus.COMPLETE.getPrimitiveValue(), TaskStatus.FAILED.getPrimitiveValue())),
                Optional.of(PROCESSOR_TASK.CREATE_TIME_MS.isNull().or(PROCESSOR_TASK.CREATE_TIME_MS.lessThan(age))));

        return JooqUtil.contextResult(connectionProvider, context ->
                context
                        .select(PROCESSOR_TASK.ID)
                        .from(PROCESSOR_TASK)
                        .where(conditions)
                        .orderBy(PROCESSOR_TASK.ID)
                        .limit(batchSize)
                        .fetch(PROCESSOR_TASK.ID));


//        final SqlBuilder sql = new SqlBuilder();
//        sql.append("SELECT ");
//        sql.append(ProcessorTask.ID);
//        sql.append(" FROM ");
//        sql.append(ProcessorTask.TABLE_NAME);
//        sql.append(" WHERE ");
//        sql.append(ProcessorTask.STATUS);
//        sql.append(" IN (");
//        sql.append(TaskStatus.COMPLETE.getPrimitiveValue());
//        sql.append(", ");
//        sql.append(TaskStatus.FAILED.getPrimitiveValue());
//        sql.append(") AND (");
//        sql.append(ProcessorTask.CREATE_MS);
//        sql.append(" IS NULL OR ");
//        sql.append(ProcessorTask.CREATE_MS);
//        sql.append(" < ");
//        sql.arg(age);
//        sql.append(")");
//        sql.append(" ORDER BY ");
//        sql.append(ProcessorTask.ID);
//        sql.append(" LIMIT ");
//        sql.arg(batchSize);
//        return stroomEntityManager.executeNativeQueryResultList(sql);
    }

    private void deleteOldFilters(final long age) {
        try {
            // Get all filters that have not been polled for a while.
            final ExpressionOperator expression = new ExpressionOperator.Builder()
                    .addTerm(ProcessorFilterDataSource.LAST_POLL_MS, ExpressionTerm.Condition.LESS_THAN, age)
                    .build();
            final ExpressionCriteria criteria = new ExpressionCriteria(expression);
//            criteria.setLastPollPeriod(new Period(null, age));
            final List<ProcessorFilter> filters = processorFilterDao.find(criteria);
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
