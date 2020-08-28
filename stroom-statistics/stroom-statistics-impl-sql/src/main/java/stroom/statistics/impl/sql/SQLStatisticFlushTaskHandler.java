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

package stroom.statistics.impl.sql;

import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;

import javax.inject.Inject;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;


public class SQLStatisticFlushTaskHandler {
    /**
     * The number of records to flush to the DB in one go.
     */
    private static final int BATCH_SIZE = 5000;
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SQLStatisticFlushTaskHandler.class);
    private final SQLStatisticValueBatchSaveService sqlStatisticValueBatchSaveService;
    private final TaskContextFactory taskContextFactory;
    private final SecurityContext securityContext;

    private LogExecutionTime logExecutionTime;
    private int counter;
    private int savedCount;
    private int total;

    @Inject
    public SQLStatisticFlushTaskHandler(final SQLStatisticValueBatchSaveService sqlStatisticValueBatchSaveService,
                                        final TaskContextFactory taskContextFactory,
                                        final SecurityContext securityContext) {
        this.sqlStatisticValueBatchSaveService = sqlStatisticValueBatchSaveService;
        this.taskContextFactory = taskContextFactory;
        this.securityContext = securityContext;
    }

    public void exec(final SQLStatisticAggregateMap map) {
        taskContextFactory.context("Flush SQL Statistics", taskContext -> exec(taskContext, map))
                .run();
    }

    private void exec(final TaskContext taskContext, final SQLStatisticAggregateMap map) {
        securityContext.secure(() -> flush(taskContext, map));
    }

    private void flush(final TaskContext taskContext, final SQLStatisticAggregateMap map) {
        if (map != null) {
            logExecutionTime = new LogExecutionTime();
            counter = 0;
            savedCount = 0;
            total = map.size();

            final int mapSize = map.size();

            final Supplier<String> messageSupplier = () ->
                    "Flushing " + mapSize + " statistics with batch size " + BATCH_SIZE;
            LOGGER.info(messageSupplier);
            taskContext.info(messageSupplier);

            final List<SQLStatValSourceDO> batchInsert = new ArrayList<>();
            // Store all aggregated entries.
            for (final Entry<SQLStatKey, LongAdder> entry : map.countEntrySet()) {
                if (!Thread.currentThread().isInterrupted()) {
                    final long ms = entry.getKey().getMs();
                    final String name = entry.getKey().getName();
                    final long count = entry.getValue().longValue();

                    addEntry(taskContext, batchInsert, SQLStatValSourceDO.createCountStat(ms, name, count));
                } else {
                    LOGGER.warn("Thread interrupted");
                }
            }
            for (final Entry<SQLStatKey, SQLStatisticAggregateMap.ValueStatValue> entry : map.valueEntrySet()) {
                if (!Thread.currentThread().isInterrupted()) {
                    final long ms = entry.getKey().getMs();
                    final String name = entry.getKey().getName();
                    // TODO should not be storing this as a long in the db
                    final long value = (long) entry.getValue().getValue();
                    final long count = entry.getValue().getCount();

                    addEntry(taskContext, batchInsert, SQLStatValSourceDO.createValueStat(ms, name, value, count));
                } else {
                    LOGGER.warn("Thread interrupted");
                }
            }

            if (!Thread.currentThread().isInterrupted()) {
                if (batchInsert.size() > 0) {
                    doSaveBatch(taskContext, batchInsert);
                }
            }
        }
    }

    private void addEntry(final TaskContext taskContext,
                          final List<SQLStatValSourceDO> batchInsert,
                          final SQLStatValSourceDO insert) {
        batchInsert.add(insert);
        counter++;
        if (batchInsert.size() >= BATCH_SIZE) {
            doSaveBatch(taskContext, batchInsert);
        }
    }

    private void doSaveBatch(final TaskContext taskContext,
                             final List<SQLStatValSourceDO> batchInsert) {
        try {
            final int seconds = (int) (logExecutionTime.getDuration() / 1000L);

            if (seconds > 0) {
                taskContext.info(() -> LogUtil.message("Saving {}/{} ({}/sec)",
                        ModelStringUtil.formatCsv(counter),
                        ModelStringUtil.formatCsv(total),
                        ModelStringUtil.formatCsv(savedCount / seconds)));
            } else {
                taskContext.info(() -> LogUtil.message("Saving {}/{} (? ps)",
                        ModelStringUtil.formatCsv(counter),
                        ModelStringUtil.formatCsv(total)));
            }

            sqlStatisticValueBatchSaveService.saveBatchStatisticValueSource_String(batchInsert);

            savedCount += batchInsert.size();
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            LOGGER.warn(() -> LogUtil.message(
                    "doSaveBatch() - Failed to insert {} records will try slower PreparedStatement method - {}",
                    batchInsert.size(), e.getMessage()));

            try {
                sqlStatisticValueBatchSaveService.saveBatchStatisticValueSource_PreparedStatement(batchInsert);
                savedCount += batchInsert.size();
            } catch (final SQLException e2) {
                int[] successfulInserts = new int[0];
                int successCount = 0;

                if (e2 instanceof BatchUpdateException) {
                    successfulInserts = ((BatchUpdateException) e2).getUpdateCounts();
                }

                final List<SQLStatValSourceDO> revisedBatch = new ArrayList<>();

                for (int i = 0, lenBatch = batchInsert.size(), lenArr = successfulInserts.length; i < lenBatch; i++) {
                    if (i < lenArr && successfulInserts[i] == 1) {
                        successCount++;
                        // ignore this item as it has already been processed
                    } else {
                        revisedBatch.add(batchInsert.get(i));
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
