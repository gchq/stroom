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

package stroom.data.store.impl;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.meta.api.MetaService;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import stroom.entity.util.PeriodUtil;

public class FeedDataRetentionExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeedDataRetentionExecutor.class);

    private static final String LOCK_NAME = "StreamRetentionExecutor";
    private static final int DELETE_STREAM_BATCH_SIZE = 1000;

    private final MetaService metaService;
    private final ClusterLockService clusterLockService;

    @Inject
    FeedDataRetentionExecutor(final MetaService metaService,
                              final ClusterLockService clusterLockService) {
        this.metaService = metaService;
        this.clusterLockService = clusterLockService;
    }

    // TODO : @66 Reimplement feed based data retention???
    public void exec() {
//        final LogExecutionTime logExecutionTime = new LogExecutionTime();
//        LOGGER.info("Stream Retention Executor - start");
//        if (clusterLockService.tryLock(LOCK_NAME)) {
//            try {
//                final List<DocRef> feedRefs = feedStore.list();
//                for (final DocRef feedRef : feedRefs) {
//                    if (!Thread.currentThread().isInterrupted()) {
//                        processFeed(feedRef);
//                    }
//                }
//                LOGGER.info("Stream Retention Executor - finished in {}", logExecutionTime);
//            } catch (final RuntimeException e) {
//                LOGGER.error(e.getMessage(), e);
//            } finally {
//                clusterLockService.releaseLock(LOCK_NAME);
//            }
//        } else {
//            LOGGER.info("Stream Retention Executor - Skipped as did not get lock in {}", logExecutionTime);
//        }
    }
//
//    private void processFeed(final DocRef feedRef) {
//        final FeedDoc feed = feedStore.readDocument(feedRef);
//
//        if (feed.getRetentionDayAge() == null) {
//            LOGGER.info("processFeed() - {} Skipping as no retention set", feed.getName());
//            return;
//        }
//        try {
//            final Period createPeriod = PeriodUtil.createToDateWithOffset(System.currentTimeMillis(),
//                    -1 * feed.getRetentionDayAge());
//
//            LOGGER.info("processFeed() - {} deleting range {} .. {}", feed.getName(),
//                    DateUtil.createNormalDateTimeString(createPeriod.getFrom()),
//                    DateUtil.createNormalDateTimeString(createPeriod.getTo())
//            );
//
//            taskContext.info("{} deleting range {} .. {}", new Object[]{
//                    feed.getName(),
//                    DateUtil.createNormalDateTimeString(createPeriod.getFrom()),
//                    DateUtil.createNormalDateTimeString(createPeriod.getTo())
//            });
//
//            final ExpressionOperator expression = periodToExpression(MetaFieldNames.CREATE_TIME, createPeriod)
//                    .addTerm(MetaFieldNames.FEED_NAME, Condition.EQUALS, feed.getName())
//                    // we only want it to logically delete UNLOCKED items and not ones
//                    // already marked as DELETED
//                    .addTerm(MetaFieldNames.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
//                    .build();
//
//            // Delete anything received older than -1 * retention age
//            final FindMetaCriteria criteria = new FindMetaCriteria();
//            criteria.setExpression(expression);
//            criteria.obtainPageRequest().setLength(DELETE_STREAM_BATCH_SIZE);
//
//            long total = 0;
//            int deleted;
//            do {
//                deleted = metaService.updateStatus(criteria, Status.DELETED);
//                total += deleted;
//            } while (deleted >= DELETE_STREAM_BATCH_SIZE);
//
//            LOGGER.info("processFeed() - {} Deleted {}", feed.getName(), total);
//        } catch (final RuntimeException e) {
//            LOGGER.error("processFeed() - {} Error", feed.getName(), e);
//        }
//    }
//
//    private ExpressionOperator.Builder periodToExpression(final String field, final Period period) {
//        final ExpressionOperator.Builder builder = ExpressionOperator.builder();
//        if (period != null) {
//            if (period.getFromMs() != null && period.getToMs() != null) {
//                builder
//                        .addTerm(field, Condition.GREATER_THAN_OR_EQUAL_TO,
//                        DateUtil.createNormalDateTimeString(period.getFromMs()))
//                        .addTerm(field, Condition.LESS_THAN, DateUtil.createNormalDateTimeString(period.getToMs()));
//            }
//            if (period.getFromMs() != null) {
//                builder
//                        .addTerm(field, Condition.GREATER_THAN_OR_EQUAL_TO,
//                        DateUtil.createNormalDateTimeString(period.getFromMs()));
//            }
//            if (period.getToMs() != null) {
//                builder
//                        .addTerm(field, Condition.LESS_THAN, DateUtil.createNormalDateTimeString(period.getToMs()));
//            }
//        }
//        return builder;
//    }
}
