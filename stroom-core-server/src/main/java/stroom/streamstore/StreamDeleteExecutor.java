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

package stroom.streamstore;

import stroom.entity.shared.SQLNameConstants;
import stroom.entity.util.SqlBuilder;
import stroom.jobsystem.ClusterLockService;
import stroom.jobsystem.JobTrackedSchedule;
import stroom.properties.StroomPropertyService;
import stroom.streamstore.shared.StreamEntity;
import stroom.streamstore.shared.StreamAttributeValue;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamstore.shared.StreamStatusId;
import stroom.streamstore.shared.StreamVolume;
import stroom.streamtask.AbstractBatchDeleteExecutor;
import stroom.streamtask.BatchIdTransactionHelper;
import stroom.streamtask.shared.StreamTask;
import stroom.util.lifecycle.StroomSimpleCronSchedule;
import stroom.task.TaskContext;

import javax.inject.Inject;

public class StreamDeleteExecutor extends AbstractBatchDeleteExecutor {
    private static final String TASK_NAME = "Stream Delete Executor";
    private static final String LOCK_NAME = "StreamDeleteExecutor";
    private static final String STREAM_DELETE_PURGE_AGE_PROPERTY = "stroom.stream.deletePurgeAge";
    private static final String STREAM_DELETE_BATCH_SIZE_PROPERTY = "stroom.stream.deleteBatchSize";
    private static final int DEFAULT_STREAM_DELETE_BATCH_SIZE = 1000;
    private static final String TEMP_STRM_ID_TABLE = "TEMP_STRM_ID";

    @Inject
    StreamDeleteExecutor(final BatchIdTransactionHelper batchIdTransactionHelper,
                         final ClusterLockService clusterLockService,
                         final StroomPropertyService propertyService,
                         final TaskContext taskContext) {
        super(batchIdTransactionHelper, clusterLockService, propertyService, taskContext, TASK_NAME, LOCK_NAME,
                STREAM_DELETE_PURGE_AGE_PROPERTY, STREAM_DELETE_BATCH_SIZE_PROPERTY, DEFAULT_STREAM_DELETE_BATCH_SIZE,
                TEMP_STRM_ID_TABLE);
    }

    @StroomSimpleCronSchedule(cron = "0 0 *")
    @JobTrackedSchedule(jobName = "Stream Delete", description = "Physically delete streams that have been logically deleted based on age of delete ("
            + STREAM_DELETE_PURGE_AGE_PROPERTY + ")")
    public void exec() {
        lockAndDelete();
    }

    @Override
    protected void deleteCurrentBatch(final long total) {
        // Delete stream tasks.
        deleteWithJoin(StreamTask.TABLE_NAME, StreamEntity.FOREIGN_KEY, "stream tasks", total);

        // Delete stream volumes.
        deleteWithJoin(StreamVolume.TABLE_NAME, StreamEntity.FOREIGN_KEY, "stream volumes", total);

        // Delete stream attribute values.
        deleteWithJoin(StreamAttributeValue.TABLE_NAME, StreamAttributeValue.STREAM_ID, "stream attribute values",
                total);

        // Delete streams.
        deleteWithJoin(StreamEntity.TABLE_NAME, StreamEntity.ID, "streams", total);
    }

    @Override
    protected SqlBuilder getTempIdSelectSql(final long age, final int batchSize) {
        final SqlBuilder sql = new SqlBuilder();
        sql.append("SELECT ");
        sql.append(StreamEntity.ID);
        sql.append(" FROM ");
        sql.append(StreamEntity.TABLE_NAME);
        sql.append(" WHERE ");
        sql.append(SQLNameConstants.STATUS);
        sql.append(" = ");
        sql.arg(StreamStatusId.DELETED);
        sql.append(" AND ");
        sql.append(StreamEntity.STATUS_MS);
        sql.append(" < ");
        sql.arg(age);
        sql.append(" ORDER BY ");
        sql.append(StreamEntity.ID);
        sql.append(" LIMIT ");
        sql.arg(batchSize);
        return sql;
    }
}
