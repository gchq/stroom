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

package stroom.data.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.meta.api.FindDataCriteria;
import stroom.data.meta.api.MetaDataSource;
import stroom.data.meta.api.DataMetaService;
import stroom.data.meta.api.DataStatus;
import stroom.docref.DocRef;
import stroom.entity.shared.Period;
import stroom.entity.util.PeriodUtil;
import stroom.feed.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.jobsystem.ClusterLockService;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.task.TaskContext;
import stroom.util.date.DateUtil;
import stroom.util.lifecycle.JobTrackedSchedule;
import stroom.util.lifecycle.StroomSimpleCronSchedule;
import stroom.util.logging.LogExecutionTime;

import javax.inject.Inject;
import java.util.List;

/**
 * Factory for creating stream clean tasks.
 */
public class StreamRetentionExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamRetentionExecutor.class);

    private static final String LOCK_NAME = "StreamRetentionExecutor";
    private static final int DELETE_STREAM_BATCH_SIZE = 1000;

    private final FeedStore feedStore;
    private final DataMetaService streamMetaService;
    private final TaskContext taskContext;
    private final ClusterLockService clusterLockService;

    @Inject
    StreamRetentionExecutor(final FeedStore feedStore,
                            final DataMetaService streamMetaService,
                            final TaskContext taskContext,
                            final ClusterLockService clusterLockService) {
        this.feedStore = feedStore;
        this.streamMetaService = streamMetaService;
        this.taskContext = taskContext;
        this.clusterLockService = clusterLockService;
    }

    @StroomSimpleCronSchedule(cron = "0 0 *")
    @JobTrackedSchedule(jobName = "Stream Retention", description = "Delete data that exceeds the retention period specified by feed")
    public void exec() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        LOGGER.info("Stream Retention Executor - start");
        if (clusterLockService.tryLock(LOCK_NAME)) {
            try {
                final List<DocRef> feedRefs = feedStore.list();
                for (final DocRef feedRef : feedRefs) {
                    if (!Thread.currentThread().isInterrupted()) {
                        processFeed(feedRef);
                    }
                }
                LOGGER.info("Stream Retention Executor - finished in {}", logExecutionTime);
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            } finally {
                clusterLockService.releaseLock(LOCK_NAME);
            }
        } else {
            LOGGER.info("Stream Retention Executor - Skipped as did not get lock in {}", logExecutionTime);
        }
    }

    private void processFeed(final DocRef feedRef) {
        final FeedDoc feed = feedStore.readDocument(feedRef);

        if (feed.getRetentionDayAge() == null) {
            LOGGER.info("processFeed() - {} Skipping as no retention set", feed.getName());
            return;
        }
        try {
            final Period createPeriod = PeriodUtil.createToDateWithOffset(System.currentTimeMillis(),
                    -1 * feed.getRetentionDayAge());

            LOGGER.info("processFeed() - {} deleting range {} .. {}", feed.getName(),
                    DateUtil.createNormalDateTimeString(createPeriod.getFrom()),
                    DateUtil.createNormalDateTimeString(createPeriod.getTo())
            );

            taskContext.info("{} deleting range {} .. {}", new Object[]{
                    feed.getName(),
                    DateUtil.createNormalDateTimeString(createPeriod.getFrom()),
                    DateUtil.createNormalDateTimeString(createPeriod.getTo())
            });

            final ExpressionOperator expression = periodToExpression(MetaDataSource.CREATE_TIME, createPeriod)
                    .addTerm(MetaDataSource.FEED, Condition.EQUALS, feed.getName())
                    // we only want it to logically delete UNLOCKED items and not ones
                    // already marked as DELETED
                    .addTerm(MetaDataSource.STATUS, Condition.EQUALS, DataStatus.UNLOCKED.getDisplayValue())
                    .build();

            // Delete anything received older than -1 * retention age
            final FindDataCriteria criteria = new FindDataCriteria();
            criteria.setExpression(expression);
            criteria.obtainPageRequest().setLength(DELETE_STREAM_BATCH_SIZE);

            long total = 0;
            int deleted;
            do {
                deleted = streamMetaService.updateStatus(criteria, DataStatus.DELETED);
                total += deleted;
            } while (deleted >= DELETE_STREAM_BATCH_SIZE);

            LOGGER.info("processFeed() - {} Deleted {}", feed.getName(), total);
        } catch (final RuntimeException e) {
            LOGGER.error("processFeed() - {} Error", feed.getName(), e);
        }
    }

    private ExpressionOperator.Builder periodToExpression(final String field, final Period period) {
        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(Op.AND);
        if (period != null) {
            if (period.getFromMs() != null && period.getToMs() != null) {
                builder
                        .addTerm(field, Condition.GREATER_THAN_OR_EQUAL_TO, DateUtil.createNormalDateTimeString(period.getFromMs()))
                        .addTerm(field, Condition.LESS_THAN, DateUtil.createNormalDateTimeString(period.getToMs()));
            }
            if (period.getFromMs() != null) {
                builder
                        .addTerm(field, Condition.GREATER_THAN_OR_EQUAL_TO, DateUtil.createNormalDateTimeString(period.getFromMs()));
            }
            if (period.getToMs() != null) {
                builder
                        .addTerm(field, Condition.LESS_THAN, DateUtil.createNormalDateTimeString(period.getToMs()));
            }
        }
        return builder;
    }
}
