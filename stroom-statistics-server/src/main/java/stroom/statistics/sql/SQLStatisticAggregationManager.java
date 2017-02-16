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

package stroom.statistics.sql;

import java.sql.SQLException;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Resource;

import stroom.entity.server.util.StroomDatabaseInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import stroom.entity.server.util.EntityServiceExceptionUtil;
import stroom.jobsystem.server.ClusterLockService;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.util.date.DateUtil;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;
import stroom.util.spring.StroomScope;
import stroom.util.spring.StroomSimpleCronSchedule;
import stroom.util.task.TaskMonitor;

@Component
@Scope(value = StroomScope.TASK)
public class SQLStatisticAggregationManager {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(SQLStatisticAggregationManager.class);

    /**
     * The cluster lock to acquire to prevent other nodes from concurrently
     * aggregating statistics.
     */
    private static final String LOCK_NAME = "SQLStatisticAggregationManager";

    @Resource
    private ClusterLockService clusterLockService;
    @Resource
    private SQLStatisticAggregationTransactionHelper helper;
    @Resource
    private TaskMonitor taskMonitor;
    @Resource
    private StroomDatabaseInfo stroomDatabaseInfo;

    private static final ReentrantLock guard = new ReentrantLock();

    /**
     * The number of records to add to the aggregate from the aggregate source
     * table on each pass
     */
    public static final int DEFAULT_BATCH_SIZE = 1000000;
    public Integer batchSize = DEFAULT_BATCH_SIZE;

    @StroomSimpleCronSchedule(cron = "5,15,25,35,45,55 * *")
    @JobTrackedSchedule(jobName = "SQL Stats Database Aggregation", description = "Run SQL stats database aggregation")
    public void aggregate() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        LOGGER.info("SQL Statistic Aggregation - start");
        if (clusterLockService.tryLock(LOCK_NAME)) {
            try {
                aggregate(System.currentTimeMillis());
                LOGGER.info("SQL Statistic Aggregation - finished in %s", logExecutionTime);
            } catch (final Throwable t) {
                LOGGER.error(t.getMessage(), t);
            } finally {
                clusterLockService.releaseLock(LOCK_NAME);
            }
        } else {
            LOGGER.info("SQL Statistic Aggregation - Skipped as did not get lock in %s", logExecutionTime);
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
                LOGGER.debug("aggregate() Called for SQL stats - Start timeNow = %s",
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
                    LOGGER.debug("aggregate() - Finished for SQL stats in %s timeNowOverride = %s", logExecutionTime,
                            DateUtil.createNormalDateTimeString(timeNow));
                }
            } catch (final Throwable t) {
                throw EntityServiceExceptionUtil.create(t);
            } finally {
                guard.unlock();
            }
        }
    }

    public void setTaskMonitor(final TaskMonitor taskMonitor) {
        this.taskMonitor = taskMonitor;
    }

    public void setHelper(final SQLStatisticAggregationTransactionHelper helper) {
        this.helper = helper;
    }

    @Value("#{propertyConfigurer.getProperty('stroom.stats.sql.statisticAggregationBatchSize')}")
    public void setBatchSize(final String fileSystemCleanBatchSize) {
        this.batchSize = ModelStringUtil.parseNumberStringAsInt(fileSystemCleanBatchSize);
        if (batchSize == null || batchSize == 0) {
            batchSize = DEFAULT_BATCH_SIZE;
        }
    }
}
