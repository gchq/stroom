/*
 * Copyright 2016 Crown Copyright
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

package stroom.statistics.server.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import stroom.entity.server.util.EntityServiceExceptionUtil;
import stroom.entity.server.util.StroomDatabaseInfo;
import stroom.jobsystem.server.ClusterLockService;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.util.date.DateUtil;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;
import stroom.util.spring.StroomSimpleCronSchedule;
import stroom.util.task.TaskMonitor;

import javax.inject.Inject;
import java.sql.SQLException;
import java.util.concurrent.locks.ReentrantLock;

public class SQLStatisticAggregationManager {
    /**
     * The number of records to add to the aggregate from the aggregate source
     * table on each pass
     */
    public static final int DEFAULT_BATCH_SIZE = 1000000;
    private static final Logger LOGGER = LoggerFactory.getLogger(SQLStatisticAggregationManager.class);
    /**
     * The cluster lock to acquire to prevent other nodes from concurrently
     * aggregating statistics.
     */
    private static final String LOCK_NAME = "SQLStatisticAggregationManager";
    private static final ReentrantLock guard = new ReentrantLock();
    private final ClusterLockService clusterLockService;
    private final SQLStatisticAggregationTransactionHelper helper;
    private final TaskMonitor taskMonitor;
    private final StroomDatabaseInfo stroomDatabaseInfo;
    private int batchSize;

    @Inject
    SQLStatisticAggregationManager(final ClusterLockService clusterLockService,
                                   final SQLStatisticAggregationTransactionHelper helper,
                                   final TaskMonitor taskMonitor,
                                   final StroomDatabaseInfo stroomDatabaseInfo,
                                   @Value("#{propertyConfigurer.getProperty('stroom.statistics.sql.statisticAggregationBatchSize')}") String batchSizeString) {
        this.clusterLockService = clusterLockService;
        this.helper = helper;
        this.taskMonitor = taskMonitor;
        this.stroomDatabaseInfo = stroomDatabaseInfo;

        Integer batchSize = DEFAULT_BATCH_SIZE;
        try {
            batchSize = ModelStringUtil.parseNumberStringAsInt(batchSizeString);
            if (batchSize == null || batchSize == 0) {
                batchSize = DEFAULT_BATCH_SIZE;
            }
        } catch (final Exception e) {

        }

        this.batchSize = batchSize;
    }

    @StroomSimpleCronSchedule(cron = "5,15,25,35,45,55 * *")
    @JobTrackedSchedule(jobName = "SQL Stats Database Aggregation", description = "Run SQL stats database aggregation")
    public void aggregate() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        LOGGER.info("SQL Statistic Aggregation - start");
        if (clusterLockService.tryLock(LOCK_NAME)) {
            try {
                aggregate(System.currentTimeMillis());
                LOGGER.info("SQL Statistic Aggregation - finished in {}", logExecutionTime);
            } catch (final Throwable t) {
                LOGGER.error(t.getMessage(), t);
            } finally {
                clusterLockService.releaseLock(LOCK_NAME);
            }
        } else {
            LOGGER.info("SQL Statistic Aggregation - Skipped as did not get lock in {}", logExecutionTime);
        }
    }

    /**
     * Step 1 - Move source values into value table with precision 1<br/>
     * Step 2 - Reduce precisions possibly creating duplicates in same table
     * <br/>
     * Step 3 - Remove duplicates using temporary table<br/>
     */
    public void aggregate(final long timeNow) {
        if (stroomDatabaseInfo.isMysql()) {
            guard.lock();
            try {
                LOGGER.debug("aggregate() Called for SQL stats - Start timeNow = {}",
                        DateUtil.createNormalDateTimeString(timeNow));
                final LogExecutionTime logExecutionTime = new LogExecutionTime();

                // TODO delete any rows in SQL_STAT_VAL that have a data older
                // than
                // (now minus maxProcessingAge) rounded
                // to the most coarse precision. Needs to be done first so we
                // don't
                // have to aggregate any old data
                try {
                    helper.deleteOldStats(timeNow, taskMonitor);

                    long processedCount = 0;
                    int iteration = 0;
                    // Process batches of records until we have processed one
                    // that
                    // was not a full batch
                    do {
                        processedCount = helper.aggregateConfigStage1(taskMonitor, "Iteration: " + ++iteration + "",
                                batchSize, timeNow);

                    } while (processedCount == batchSize && !taskMonitor.isTerminated());

                    if (!taskMonitor.isTerminated()) {
                        helper.aggregateConfigStage2(taskMonitor, "Final Reduce", timeNow);
                    }

                } catch (final SQLException ex) {
                    throw EntityServiceExceptionUtil.create(ex);
                } finally {
                    LOGGER.debug("aggregate() - Finished for SQL stats in {} timeNowOverride = {}", logExecutionTime,
                            DateUtil.createNormalDateTimeString(timeNow));
                }
            } catch (final Throwable t) {
                throw EntityServiceExceptionUtil.create(t);
            } finally {
                guard.unlock();
            }
        }
    }

    public void setBatchSize(final int batchSize) {
        this.batchSize = batchSize;
    }
}
