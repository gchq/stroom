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

package stroom.streamtask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.jobsystem.ClusterLockService;
import stroom.properties.api.PropertyService;
import stroom.task.api.TaskContext;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;

import java.util.Arrays;
import java.util.List;

public abstract class AbstractBatchDeleteExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractBatchDeleteExecutor.class);

    private final BatchIdTransactionHelper batchIdTransactionHelper;
    private final ClusterLockService clusterLockService;
    private final PropertyService propertyService;
    private final TaskContext taskContext;

    private final String taskName;
    private final String clusterLockName;
    private final String deleteAgePropertyName;
    private final String deleteBatchSizePropertyName;
    private final int deleteBatchSizeDefaultValue;
    private final String tempIdTable;

    public AbstractBatchDeleteExecutor(final BatchIdTransactionHelper batchIdTransactionHelper,
                                       final ClusterLockService clusterLockService, final PropertyService propertyService,
                                       final TaskContext taskContext, final String taskName, final String clusterLockName,
                                       final String deleteAgePropertyName, final String deleteBatchSizePropertyName,
                                       final int deleteBatchSizeDefaultValue, final String tempIdTable) {
        this.batchIdTransactionHelper = batchIdTransactionHelper;
        this.clusterLockService = clusterLockService;
        this.propertyService = propertyService;
        this.taskContext = taskContext;

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
                if (!Thread.currentThread().isInterrupted()) {
                    final Long age = getDeleteAge(deleteAgePropertyName);
                    if (age != null) {
                        delete(age);
                    }
                    LOGGER.info(taskName + " - finished in {}", logExecutionTime);
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            } finally {
                clusterLockService.releaseLock(clusterLockName);
            }
        } else {
            LOGGER.info(taskName + " - Skipped as did not get lock in {}", logExecutionTime);
        }
    }

    public void delete(final long age) {
        if (!Thread.currentThread().isInterrupted()) {
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
                LOGGER.warn("{} ids found from previous delete that must not have completed successfully", count);
                // Try and delete the remaining batch.
                total += count;
                deleteCurrentBatch(total);
                // Remove the current batch of ids from the id table.
                truncateTempIdTable(total);
            }

            if (!Thread.currentThread().isInterrupted()) {
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
                } while (!Thread.currentThread().isInterrupted() && count >= deleteBatchSize);
            }

            LOGGER.debug("Deleted {} streams in {}.", total, logExecutionTime);
        }
    }

    protected abstract void deleteCurrentBatch(final long total);

    private void createTempIdTable() {
        info("Creating temp id table");
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        batchIdTransactionHelper.createTempIdTable(tempIdTable);
        LOGGER.debug("Created temp id table in {}", logExecutionTime);
    }

    private long getIdCount(final long total) {
        info("Getting id count (total={})", total);
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        final long count = batchIdTransactionHelper.getTempIdCount(tempIdTable);
        LOGGER.debug("Got {} ids in {}", count, logExecutionTime);
        return count;
    }

    private long insertIntoTempIdTable(final long age, final int batchSize, final long total) {
        info("Inserting ids for deletion into temp id table (total={})", total);
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        final List<Long> idList = getDeleteIdList(age, batchSize);
        final long count = batchIdTransactionHelper.insertIntoTempIdTable(tempIdTable, idList);
        LOGGER.debug("Inserted {} ids in {}", count, logExecutionTime);
        return count;
    }

    protected abstract List<Long> getDeleteIdList(final long age, final int batchSize);

    protected final void deleteWithJoin(final String fromTable, final String fromColumn, final String type,
                                        final long total) {
        info("Deleting {} (total={})", type, total);
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        // TODO : @66 REMOVE JOIN TO STREAM TABLE.

        final long count = batchIdTransactionHelper.deleteWithJoin(fromTable, fromColumn, tempIdTable, "FK_STRM_ID");
        LOGGER.debug("Deleted {} {} in {}", new Object[]{count, type, logExecutionTime});
    }

    private void truncateTempIdTable(final long total) {
        info("Truncating temp id table (total={})", total);
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        batchIdTransactionHelper.truncateTempIdTable(tempIdTable);
        LOGGER.debug("Truncated temp id table in {}", logExecutionTime);
    }

    private void info(final Object... args) {
        taskContext.info(args);
        Arrays.asList(args).forEach(arg -> LOGGER.debug(arg.toString()));
    }

    private Long getDeleteAge(final String property) {
        Long age = null;
        final String durationString = propertyService.getProperty(property);
        if (durationString != null && !durationString.isEmpty()) {
            try {
                final long duration = ModelStringUtil.parseDurationString(durationString);
                age = System.currentTimeMillis() - duration;
            } catch (final RuntimeException e) {
                LOGGER.error("Error reading {}", property);
            }
        }
        return age;
    }

    private int getDeleteBatchSize(final String property, final int defaultSize) {
        return propertyService.getIntProperty(property, defaultSize);
    }
}
