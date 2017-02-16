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

import java.sql.BatchUpdateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import javax.inject.Inject;

import stroom.util.spring.StroomScope;
import org.apache.commons.lang.mutable.MutableLong;
import org.springframework.context.annotation.Scope;

import stroom.statistics.shared.StatisticType;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.VoidResult;
import stroom.util.task.TaskMonitor;

@TaskHandlerBean(task = SQLStatisticFlushTask.class)
@Scope(value = StroomScope.TASK)
public class SQLStatisticFlushTaskHandler extends AbstractTaskHandler<SQLStatisticFlushTask, VoidResult> {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(SQLStatisticFlushTaskHandler.class);
    /**
     * The number of records to flush to the DB in one go.
     */
    public static final int BATCH_SIZE = 5000;

    private final SQLStatisticValueBatchSaveService sqlStatisticValueBatchSaveService;
    private final TaskMonitor taskMonitor;

    private LogExecutionTime logExecutionTime;
    private int count;
    private int savedCount;
    private int total;

    @Inject
    public SQLStatisticFlushTaskHandler(final SQLStatisticValueBatchSaveService sqlStatisticValueBatchSaveService,
            final TaskMonitor taskMonitor) {
        this.sqlStatisticValueBatchSaveService = sqlStatisticValueBatchSaveService;
        this.taskMonitor = taskMonitor;
    }

    @Override
    public VoidResult exec(final SQLStatisticFlushTask task) {
        flush(task.getMap());
        return new VoidResult();
    }

    private void flush(final SQLStatisticAggregateMap map) {
        if (map != null) {
            logExecutionTime = new LogExecutionTime();
            count = 0;
            savedCount = 0;
            total = map.size();

            final int batchSizetoUse = BATCH_SIZE;

            LOGGER.info("Flushing statistics (batch size=%s)", batchSizetoUse);
            taskMonitor.info("Flushing statistics (batch size=%s)", batchSizetoUse);

            final List<SQLStatisticValueSourceDO> batchInsert = new ArrayList<SQLStatisticValueSourceDO>();
            // Store all aggregated entries.
            for (final Entry<SQLStatKey, MutableLong> entry : map.countEntrySet()) {
                if (!taskMonitor.isTerminated()) {
                    final long ms = entry.getKey().getMs();
                    final String name = entry.getKey().getName();
                    final long value = entry.getValue().longValue();

                    final SQLStatisticValueSourceDO insert = new SQLStatisticValueSourceDO();
                    insert.setCreateMs(ms);
                    insert.setName(name);
                    insert.setType(StatisticType.COUNT);
                    insert.setValue(value);

                    batchInsert.add(insert);

                    count++;

                    if (batchInsert.size() >= batchSizetoUse) {
                        doSaveBatch(batchInsert);
                    }
                }
            }
            for (final Entry<SQLStatKey, Double> entry : map.valueEntrySet()) {
                if (!taskMonitor.isTerminated()) {
                    final long ms = entry.getKey().getMs();
                    final String name = entry.getKey().getName();
                    final long value = entry.getValue().longValue();

                    final SQLStatisticValueSourceDO insert = new SQLStatisticValueSourceDO();
                    insert.setCreateMs(ms);
                    insert.setName(name);
                    insert.setType(StatisticType.VALUE);
                    insert.setValue(value);

                    batchInsert.add(insert);

                    count++;

                    if (batchInsert.size() >= batchSizetoUse) {
                        doSaveBatch(batchInsert);
                    }
                }
            }

            if (!taskMonitor.isTerminated()) {
                if (batchInsert.size() > 0) {
                    doSaveBatch(batchInsert);
                }
            }
        }
    }

    private void doSaveBatch(final List<SQLStatisticValueSourceDO> batchInsert) {
        try {
            final int seconds = (int) (logExecutionTime.getDuration() / 1000L);

            if (seconds > 0) {
                taskMonitor.info("Saving %s/%s (%s ps)", ModelStringUtil.formatCsv(count),
                        ModelStringUtil.formatCsv(total), ModelStringUtil.formatCsv(savedCount / seconds));
            } else {
                taskMonitor.info("Saving %s/%s (? ps)", ModelStringUtil.formatCsv(count),
                        ModelStringUtil.formatCsv(total));

            }

            sqlStatisticValueBatchSaveService.saveBatchStatisticValueSource_String(batchInsert);

            savedCount += batchInsert.size();
        } catch (final Exception ex) {
            LOGGER.debug(ex.getMessage(), ex);
            LOGGER.warn("doSaveBatch() - Failed to insert %s records will try slower PreparedStatement method - %s",
                    batchInsert.size(), ex.getMessage());

            try {
                sqlStatisticValueBatchSaveService.saveBatchStatisticValueSource_PreparedStatement(batchInsert);
                savedCount += batchInsert.size();
            } catch (final Exception e) {
                int[] successfullInserts = new int[0];
                int successCount = 0;

                if (e instanceof BatchUpdateException) {
                    successfullInserts = ((BatchUpdateException) e).getUpdateCounts();
                }

                final List<SQLStatisticValueSourceDO> revisedBatch = new ArrayList<SQLStatisticValueSourceDO>();

                for (int i = 0, lenBatch = batchInsert.size(), lenArr = successfullInserts.length; i < lenBatch; i++) {
                    if (i < lenArr && successfullInserts[i] == 1) {
                        successCount++;
                        // ignore this item as it has already been processed
                    } else {
                        revisedBatch.add(batchInsert.get(i));
                    }
                }

                LOGGER.error(
                        "doSaveBatch() - Failed to insert %s records out of a batch size of %s using PreparedStatement (though succeeded in inserting %s), will try much slower IndividualPreparedStatements method",
                        batchInsert.size() - successCount, batchInsert.size(), successCount, e);

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
