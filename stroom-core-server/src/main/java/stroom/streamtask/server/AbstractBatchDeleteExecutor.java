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

package stroom.streamtask.server;

import stroom.node.server.StroomPropertyService;
import stroom.util.logging.StroomLogger;
import org.springframework.util.StringUtils;

import stroom.jobsystem.server.ClusterLockService;
import stroom.streamstore.shared.Stream;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;
import stroom.util.task.TaskMonitor;

public abstract class AbstractBatchDeleteExecutor {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(AbstractBatchDeleteExecutor.class);

    private final BatchIdTransactionHelper batchIdTransactionHelper;
    private final ClusterLockService clusterLockService;
    private final StroomPropertyService propertyService;
    private final TaskMonitor taskMonitor;

    private final String taskName;
    private final String clusterLockName;
    private final String deleteAgePropertyName;
    private final String deleteBatchSizePropertyName;
    private final int deleteBatchSizeDefaultValue;
    private final String tempIdTable;

    public AbstractBatchDeleteExecutor(final BatchIdTransactionHelper batchIdTransactionHelper,
            final ClusterLockService clusterLockService, final StroomPropertyService propertyService,
            final TaskMonitor taskMonitor, final String taskName, final String clusterLockName,
            final String deleteAgePropertyName, final String deleteBatchSizePropertyName,
            final int deleteBatchSizeDefaultValue, final String tempIdTable) {
        this.batchIdTransactionHelper = batchIdTransactionHelper;
        this.clusterLockService = clusterLockService;
        this.propertyService = propertyService;
        this.taskMonitor = taskMonitor;

        this.taskName = taskName;
        this.clusterLockName = clusterLockName;
        this.deleteAgePropertyName = deleteAgePropertyName;
        this.deleteBatchSizePropertyName = deleteBatchSizePropertyName;
        this.deleteBatchSizeDefaultValue = deleteBatchSizeDefaultValue;
        this.tempIdTable = tempIdTable;
    }

    protected final void lockAndDelete() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        LOGGER.info(taskName + " - start");
        if (clusterLockService.tryLock(clusterLockName)) {
            try {
                if (!taskMonitor.isTerminated()) {
                    final Long age = getDeleteAge(deleteAgePropertyName);
                    if (age != null) {
                        delete(age);
                    }
                    LOGGER.info(taskName + " - finished in %s", logExecutionTime);
                }
            } catch (final Throwable t) {
                LOGGER.error(t.getMessage(), t);
            } finally {
                clusterLockService.releaseLock(clusterLockName);
            }
        } else {
            LOGGER.info(taskName + " - Skipped as did not get lock in %s", logExecutionTime);
        }
    }

    public void delete(final long age) {
        if (!taskMonitor.isTerminated()) {
            long count = 0;
            long total = 0;

            final LogExecutionTime logExecutionTime = new LogExecutionTime();

            final int deleteBatchSize = getDeleteBatchSize(deleteBatchSizePropertyName, deleteBatchSizeDefaultValue);

            // Ensure the temp id table exists.
            createTempIdTable();

            // See if there are ids in the table already. There shouldn't be if
            // the task completed successfully last time.
            count = getIdCount(total);
            if (count > 0) {
                LOGGER.warn("%s ids found from previous delete that must not have completed successfully", count);
                // Try and delete the remaining batch.
                total += count;
                deleteCurrentBatch(total);
                // Remove the current batch of ids from the id table.
                truncateTempIdTable(total);
            }

            if (!taskMonitor.isTerminated()) {
                do {
                    // Insert a batch of ids into the temp id table and find out
                    // how many were inserted.
                    count = insertIntoTempIdTable(age, deleteBatchSize, total);

                    // If we inserted some ids then try and delete this batch.
                    if (count > 0) {
                        total += count;
                        deleteCurrentBatch(total);
                        // Remove the current batch of ids from the id table.
                        truncateTempIdTable(total);
                    }
                } while (!taskMonitor.isTerminated() && count >= deleteBatchSize);
            }

            LOGGER.debug("Deleted %s streams in %s.", total, logExecutionTime);
        }
    }

    protected abstract void deleteCurrentBatch(final long total);

    private void createTempIdTable() {
        info("Creating temp id table");
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        batchIdTransactionHelper.createTempIdTable(tempIdTable);
        LOGGER.debug("Created temp id table in %s", logExecutionTime);
    }

    private long getIdCount(final long total) {
        info("Getting id count (total=%s)", total);
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        final long count = batchIdTransactionHelper.getTempIdCount(tempIdTable);
        LOGGER.debug("Got %s ids in %s", count, logExecutionTime);
        return count;
    }

    private long insertIntoTempIdTable(final long age, final int batchSize, final long total) {
        info("Inserting ids for deletion into temp id table (total=%s)", total);
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        final String sql = getTempIdSelectSql(age, batchSize);
        final long count = batchIdTransactionHelper.insertIntoTempIdTable(tempIdTable, sql.toString());
        LOGGER.debug("Inserted %s ids in %s", count, logExecutionTime);
        return count;
    }

    protected abstract String getTempIdSelectSql(final long age, final int batchSize);

    protected final void deleteWithJoin(final String fromTable, final String fromColumn, final String type,
            final long total) {
        info("Deleting %s (total=%s)", type, total);
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        final long count = batchIdTransactionHelper.deleteWithJoin(fromTable, fromColumn, tempIdTable, Stream.ID);
        LOGGER.debug("Deleted %s %s in %s", count, type, logExecutionTime);
    }

    private void truncateTempIdTable(final long total) {
        info("Truncating temp id table (total=%s)", total);
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        batchIdTransactionHelper.truncateTempIdTable(tempIdTable);
        LOGGER.debug("Truncated temp id table in %s", logExecutionTime);
    }

    private void info(final Object... args) {
        taskMonitor.info(args);
        LOGGER.debug(args);
    }

    private Long getDeleteAge(final String property) {
        Long age = null;
        final String durationString = propertyService.getProperty(property);
        if (StringUtils.hasText(durationString)) {
            try {
                final long duration = ModelStringUtil.parseDurationString(durationString);
                age = System.currentTimeMillis() - duration;
            } catch (final Exception ex) {
                LOGGER.error("Error reading %s", property);
            }
        }
        return age;
    }

    private int getDeleteBatchSize(final String property, final int defaultSize) {
        return propertyService.getIntProperty(property, defaultSize);
    }
}
