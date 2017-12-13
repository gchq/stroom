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

package stroom.streamstore.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.entity.server.util.PeriodUtil;
import stroom.entity.shared.Period;
import stroom.feed.server.FeedService;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FindFeedCriteria;
import stroom.jobsystem.server.ClusterLockService;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.StreamDataSource;
import stroom.streamstore.shared.StreamStatus;
import stroom.util.date.DateUtil;
import stroom.util.logging.LogExecutionTime;
import stroom.util.spring.StroomScope;
import stroom.util.spring.StroomSimpleCronSchedule;
import stroom.util.task.TaskMonitor;

import javax.inject.Inject;
import java.util.List;

/**
 * Factory for creating stream clean tasks.
 */
@Component
@Scope(value = StroomScope.TASK)
public class StreamRetentionExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamRetentionExecutor.class);

    private static final String LOCK_NAME = "StreamRetentionExecutor";
    private static final int DELETE_STREAM_BATCH_SIZE = 1000;

    private final FeedService feedService;
    private final StreamStore streamStore;
    private final TaskMonitor taskMonitor;
    private final ClusterLockService clusterLockService;

    @Inject
    StreamRetentionExecutor(final FeedService feedService, final StreamStore streamStore, final TaskMonitor taskMonitor, final ClusterLockService clusterLockService) {
        this.feedService = feedService;
        this.streamStore = streamStore;
        this.taskMonitor = taskMonitor;
        this.clusterLockService = clusterLockService;
    }

    /**
     * Gets a task if one is available, returns null otherwise.
     *
     * @return A task.
     */
    @StroomSimpleCronSchedule(cron = "0 0 *")
    @JobTrackedSchedule(jobName = "Stream Retention", description = "Job to delete data that has past it's retention period")
    public void exec() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        LOGGER.info("Stream Retention Executor - start");
        if (clusterLockService.tryLock(LOCK_NAME)) {
            try {
                final FindFeedCriteria findFeedCriteria = new FindFeedCriteria();
                final List<Feed> feedList = feedService.find(findFeedCriteria);
                for (final Feed feed : feedList) {
                    if (!taskMonitor.isTerminated()) {
                        processFeed(feed);
                    }
                }
                LOGGER.info("Stream Retention Executor - finished in {}", logExecutionTime);
            } catch (final Throwable t) {
                LOGGER.error(t.getMessage(), t);
            } finally {
                clusterLockService.releaseLock(LOCK_NAME);
            }
        } else {
            LOGGER.info("Stream Retention Executor - Skipped as did not get lock in {}", logExecutionTime);
        }
    }

    private void processFeed(final Feed feed) {
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

            taskMonitor.info("{} deleting range {} .. {}", new Object[]{
                    feed.getName(),
                    DateUtil.createNormalDateTimeString(createPeriod.getFrom()),
                    DateUtil.createNormalDateTimeString(createPeriod.getTo())
            });

            final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                    .addTerm(StreamDataSource.CREATE_TIME, Condition.BETWEEN, DateUtil.createNormalDateTimeString(createPeriod.getFromMs()) + "," + DateUtil.createNormalDateTimeString(createPeriod.getToMs()))
                    .addTerm(StreamDataSource.FEED, Condition.EQUALS, feed.getName())
                    // we only want it to logically delete UNLOCKED items and not ones
                    // already marked as DELETED
                    .addTerm(StreamDataSource.STATUS, Condition.EQUALS, StreamStatus.UNLOCKED.getDisplayValue())
                    .build();

            // Delete anything received older than -1 * retention age
            final FindStreamCriteria criteria = new FindStreamCriteria();
            criteria.setExpression(expression);
            criteria.obtainPageRequest().setLength(DELETE_STREAM_BATCH_SIZE);

            long total = 0;
            long deleted = 0;
            do {
                final Long recordsDeleted = streamStore.findDelete(criteria);
                if (recordsDeleted != null) {
                    deleted = recordsDeleted;
                    total += deleted;
                } else {
                    deleted = 0;
                }
            } while (deleted >= DELETE_STREAM_BATCH_SIZE);

            LOGGER.info("processFeed() - {} Deleted {}", feed.getName(), total);
        } catch (final Exception ex) {
            LOGGER.error("processFeed() - {} Error", feed.getName(), ex);
        }
    }
}
