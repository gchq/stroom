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

package stroom.statistics.impl.sql;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

class SQLStatisticAggregationManager {

    /**
     * The number of records to add to the aggregate from the aggregate source
     * table on each pass
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(SQLStatisticAggregationManager.class);
    /**
     * The cluster lock to acquire to prevent other nodes from concurrently
     * aggregating statistics.
     */
    private static final String LOCK_NAME = "SQLStatisticAggregationManager";
    private static final ReentrantLock guard = new ReentrantLock();
    private final ClusterLockService clusterLockService;
    private final SQLStatisticAggregationTransactionHelper helper;
    private final TaskContextFactory taskContextFactory;
    private int stage1BatchSize;
    private int stage2BatchSize;

    @Inject
    SQLStatisticAggregationManager(final ClusterLockService clusterLockService,
                                   final SQLStatisticAggregationTransactionHelper helper,
                                   final TaskContextFactory taskContextFactory,
                                   final SQLStatisticsConfig sqlStatisticsConfig) {
        this.clusterLockService = clusterLockService;
        this.helper = helper;
        this.stage1BatchSize = sqlStatisticsConfig.getStatisticAggregationBatchSize();
        this.stage2BatchSize = sqlStatisticsConfig.getStatisticAggregationStageTwoBatchSize();
        this.taskContextFactory = taskContextFactory;
    }

    void aggregate() {
        LOGGER.info("SQL Statistic Aggregation - start");
        clusterLockService.tryLock(LOCK_NAME, () -> {
            try {
                final LogExecutionTime logExecutionTime = new LogExecutionTime();
                aggregate(Instant.now());
                LOGGER.info("SQL Statistic Aggregation - finished in {}", logExecutionTime);
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    /**
     * Step 1 - Move source values into value table with precision 1<br/>
     * Step 2 - Reduce precisions possibly creating duplicates in same table
     * <br/>
     * Step 3 - Remove duplicates using temporary table<br/>
     */
    void aggregate(final Instant timeNow) {
        final TaskContext taskContext = taskContextFactory.current();
        guard.lock();
        try {
            LOGGER.info("Starting statistics aggregation " +
                            "(stage1BatchSize: {}, stage2BatchSize: {}, timeNow: {})",
                    ModelStringUtil.formatCsv(stage1BatchSize),
                    ModelStringUtil.formatCsv(stage2BatchSize),
                    timeNow);

            long totalStage1Count = 0;
            long totalStage2Count = 0;
            long oldStatsDeletedCount = 0;
            int unusedKeysDeletedCount = 0;
            final LogExecutionTime logExecutionTime = new LogExecutionTime();
            try {
                oldStatsDeletedCount = helper.deleteOldStats(timeNow, taskContext);

                long processedCount;
                int batchNo = 0;
                // Process batches of records until we have processed one
                // that was not a full batch
                do {
                    processedCount = helper.aggregateConfigStage1(
                            taskContext,
                            "Aggregation Stage 1 - batch no: " + ++batchNo + "",
                            stage1BatchSize,
                            timeNow);
                    totalStage1Count += processedCount;
                } while (processedCount > 0 && !Thread.currentThread().isInterrupted());

                LOGGER.info("Completed stage 1 aggregation with {} iterations in {}{}",
                        batchNo,
                        logExecutionTime.getDuration(),
                        (Thread.currentThread().isInterrupted()
                                ? " (INTERRUPTED)"
                                : ""));

                if (!Thread.currentThread().isInterrupted()) {
                    totalStage2Count = helper.aggregateConfigStage2(
                            taskContext,
                            "Aggregation Stage 2",
                            stage2BatchSize,
                            timeNow);
                }

                // We hold the cluster lock so can safely deleted orphaned stat keys
                unusedKeysDeletedCount = helper.deleteUnusedKeys(taskContext);

            } catch (final SQLException ex) {
                throw new RuntimeException(ex.getMessage(), ex);
            } finally {
                LOGGER.info("Finished SQL stats aggregation in {}, " +
                                "oldStatsDeletedCount: {}, " +
                                "totalStage1Count: {}, " +
                                "totalStage2Count: {}, " +
                                "unusedKeysDeletedCount: {} " +
                                "(stage1BatchSize: {}, stage2BatchSize: {}, timeNowOverride: {})",
                        logExecutionTime.getDuration(),
                        ModelStringUtil.formatCsv(oldStatsDeletedCount),
                        ModelStringUtil.formatCsv(totalStage1Count),
                        ModelStringUtil.formatCsv(totalStage2Count),
                        ModelStringUtil.formatCsv(unusedKeysDeletedCount),
                        ModelStringUtil.formatCsv(stage1BatchSize),
                        ModelStringUtil.formatCsv(stage2BatchSize),
                        timeNow);
            }
        } finally {
            guard.unlock();
        }
    }

    /**
     * For testing
     */
    void setStage1BatchSize(final int batchSize) {
        this.stage1BatchSize = batchSize;
    }

    /**
     * For testing
     */
    public void setStage2BatchSize(final int stage2BatchSize) {
        this.stage2BatchSize = stage2BatchSize;
    }
}
