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

import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;


// @NotThreadSafe // Each thread should construct its own instance for each call to exec
public class SQLStatisticFlushTaskHandler {

    /**
     * The number of records to flush to the DB in one go.
     */
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SQLStatisticFlushTaskHandler.class);
    private final SQLStatisticValueBatchSaveService sqlStatisticValueBatchSaveService;
    private final TaskContextFactory taskContextFactory;
    private final SecurityContext securityContext;
    private final Provider<SQLStatisticsConfig> sqlStatisticsConfigProvider;

    private LogExecutionTime logExecutionTime;
    private int counter;
    private int savedCount;
    private int total;
    private int batchCount;

    @Inject
    public SQLStatisticFlushTaskHandler(final SQLStatisticValueBatchSaveService sqlStatisticValueBatchSaveService,
                                        final TaskContextFactory taskContextFactory,
                                        final SecurityContext securityContext,
                                        final Provider<SQLStatisticsConfig> sqlStatisticsConfigProvider) {
        this.sqlStatisticValueBatchSaveService = sqlStatisticValueBatchSaveService;
        this.taskContextFactory = taskContextFactory;
        this.securityContext = securityContext;
        this.sqlStatisticsConfigProvider = sqlStatisticsConfigProvider;
    }

    public void exec(final SQLStatisticAggregateMap map) {
        taskContextFactory.context("Flush SQL Statistics", taskContext -> exec(taskContext, map))
                .run();
    }

    private void exec(final TaskContext taskContext, final SQLStatisticAggregateMap map) {
        securityContext.secure(() -> flush(taskContext, map));
    }

    /**
     * We can't drop out if interrupted as that would mean losing data.
     * We need to flush it all the DB before we can return.
     */
    private void flush(final TaskContext taskContext, final SQLStatisticAggregateMap map) {
        if (map != null) {
            logExecutionTime = new LogExecutionTime();
            counter = 0;
            savedCount = 0;
            total = map.size();

            final int mapSize = map.size();
            final int batchSize = sqlStatisticsConfigProvider.get().getStatisticFlushBatchSize();

            final Supplier<String> messageSupplier = () ->
                    "Flushing " + ModelStringUtil.formatCsv(mapSize)
                            + " statistics with batch size " + ModelStringUtil.formatCsv(batchSize);
            taskContext.info(messageSupplier);

            final List<SQLStatValSourceDO> batchInsert = new ArrayList<>();
            // Store all aggregated COUNT entries.
            for (final Entry<SQLStatKey, LongAdder> entry : map.countEntrySet()) {
                final long ms = entry.getKey().getMs();
                final String name = entry.getKey().getName();
                final long count = entry.getValue().longValue();

                // This does a flush when the batch is full
                addEntryToBatch(
                        taskContext,
                        batchInsert,
                        SQLStatValSourceDO.createCountStat(ms, name, count),
                        batchSize);
            }
            // Store all aggregated VALUE entries.
            for (final Entry<SQLStatKey, SQLStatisticAggregateMap.ValueStatValue> entry : map.valueEntrySet()) {
                final long ms = entry.getKey().getMs();
                final String name = entry.getKey().getName();
                // TODO should not be storing this as a long in the db
                final long value = (long) entry.getValue().getValue();
                final long count = entry.getValue().getCount();

                // This does a flush when the batch is full
                addEntryToBatch(
                        taskContext,
                        batchInsert,
                        SQLStatValSourceDO.createValueStat(ms, name, value, count),
                        batchSize);
            }

            // Flush of any remaining
            if (batchInsert.size() > 0) {
                doSaveBatch(taskContext, batchInsert);
            }
            LOGGER.info("Flushed {} stats to the database ({}) in {} ({}/sec)",
                    ModelStringUtil.formatCsv(total),
                    SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME,
                    logExecutionTime.getDuration(),
                    total / ((double) logExecutionTime.getDurationMs() / 1_000));
        }
    }

    private void addEntryToBatch(final TaskContext taskContext,
                                 final List<SQLStatValSourceDO> batchInsert,
                                 final SQLStatValSourceDO insert,
                                 final int batchSize) {
        batchInsert.add(insert);
        counter++;
        if (batchInsert.size() >= batchSize) {
            doSaveBatch(taskContext, batchInsert);
        }
    }

    private void doSaveBatch(final TaskContext taskContext,
                             final List<SQLStatValSourceDO> batchInsert) {
        try {
            // Capture the values locally so the info shows the state as it is now and not
            // as it is then the supplier is called.
            final int seconds = (int) (logExecutionTime.getDurationMs() / 1_000L);
            final int localBatchCount = ++batchCount;
            final int localSavedCount = savedCount;

            final Supplier<String> msgSupplier = () -> {
                final String rate = seconds > 0
                        ? Double.toString(((double) localSavedCount) / seconds)
                        : "?";

                return LogUtil.message("Saving batch no. {} with {} records, progress so far: {}/{} ({}/sec)",
                        ModelStringUtil.formatCsv(localBatchCount),
                        ModelStringUtil.formatCsv(batchInsert.size()),
                        ModelStringUtil.formatCsv(localSavedCount),
                        ModelStringUtil.formatCsv(total),
                        rate);
            };

            taskContext.info(msgSupplier);

            if (Thread.currentThread().isInterrupted()) {
                // We can't drop out here our we will lose data but at least log something so the admin can
                // see something is happening in the logs.
                LOGGER.info("Waiting for flush to complete. {}", msgSupplier.get());
            } else {
                LOGGER.debug(msgSupplier);
            }

            // First try to insert the batch using the fastest approach,
            // i.e. a single massive insert into X (..) values (..), (..), (..) ...
            // with a prepared statement
            sqlStatisticValueBatchSaveService.saveBatchStatisticValueSource_SinglePreparedStatement(batchInsert);

            savedCount += batchInsert.size();
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            LOGGER.warn(() -> LogUtil.message(
                    "doSaveBatch() - Failed to insert {} records will try slower one row at a time method - {}",
                    batchInsert.size(), e.getMessage()));

            try {
                // Single massive statement failed so try doing it as a batch of individual inserts
                sqlStatisticValueBatchSaveService.saveBatchStatisticValueSource_BatchPreparedStatement(batchInsert);
                savedCount += batchInsert.size();
            } catch (final SQLException e2) {
                int[] successfulInserts = new int[0];
                int successCount = 0;

                if (e2 instanceof BatchUpdateException) {
                    // Get the array of insert counts by idx in the batch
                    successfulInserts = ((BatchUpdateException) e2).getUpdateCounts();
                }

                final List<SQLStatValSourceDO> revisedBatch = new ArrayList<>();
                for (int i = 0, lenBatch = batchInsert.size(), lenArr = successfulInserts.length; i < lenBatch; i++) {
                    if (i < lenArr && successfulInserts[i] == 1) {
                        successCount++;
                        // ignore this item as it has already been processed
                    } else {
                        // Failed item so add it to the revised batch
                        final SQLStatValSourceDO failedItem = batchInsert.get(i);
                        if (i < 5 || LOGGER.isTraceEnabled()) {
                            LOGGER.debug("Batch item {} {} failed with error: {} " +
                                    "(only showing first 5 failures, enable debug to see all)",
                                    i, failedItem, e2);
                        }
                        revisedBatch.add(failedItem);
                    }
                }

                LOGGER.error("doSaveBatch() - Failed to insert {} records out of a batch size of {} using " +
                                "PreparedStatement (though succeeded in inserting {}), will try much slower " +
                                "IndividualPreparedStatements method",
                        batchInsert.size() - successCount, batchInsert.size(), successCount, e2);

                final int insertedCount = sqlStatisticValueBatchSaveService
                        .saveBatchStatisticValueSource_IndividualPreparedStatements(revisedBatch);
                savedCount += insertedCount;
                revisedBatch.clear();
            }

        } finally {
            batchInsert.clear();
        }
    }
}
