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

package stroom.streamstore.server;

import stroom.entity.server.util.SqlBuilder;
import stroom.jobsystem.server.ClusterLockService;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.node.server.StroomPropertyService;
import stroom.streamstore.shared.StreamAttributeValue;
import stroom.streamtask.server.AbstractBatchDeleteExecutor;
import stroom.streamtask.server.BatchIdTransactionHelper;
import stroom.util.spring.StroomFrequencySchedule;
import stroom.util.task.TaskMonitor;

import javax.inject.Inject;

class StreamAttributeValueDeleteExecutor extends AbstractBatchDeleteExecutor {
    private static final String TASK_NAME = "Stream Attribute Delete Executor";
    private static final String LOCK_NAME = "StreamAttributeDeleteExecutor";
    private static final String STREAM_ATTRIBUTE_DELETE_AGE_PROPERTY = "stroom.streamAttribute.deleteAge";
    private static final String STREAM_ATTRIBUTE_DELETE_BATCH_SIZE_PROPERTY = "stroom.streamAttribute.deleteBatchSize";
    private static final int DEFAULT_STREAM_ATTRIBUTE_DELETE_BATCH_SIZE = 1000;
    private static final String TEMP_STRM_ATTRIBUTE_ID_TABLE = "TEMP_STRM_ATTRIBUTE_ID";

    @Inject
    StreamAttributeValueDeleteExecutor(final BatchIdTransactionHelper batchIdTransactionHelper,
                                       final ClusterLockService clusterLockService,
                                       final StroomPropertyService propertyService,
                                       final TaskMonitor taskMonitor) {
        super(batchIdTransactionHelper, clusterLockService, propertyService, taskMonitor, TASK_NAME, LOCK_NAME,
                STREAM_ATTRIBUTE_DELETE_AGE_PROPERTY, STREAM_ATTRIBUTE_DELETE_BATCH_SIZE_PROPERTY,
                DEFAULT_STREAM_ATTRIBUTE_DELETE_BATCH_SIZE, TEMP_STRM_ATTRIBUTE_ID_TABLE);
    }

    @StroomFrequencySchedule("1h")
    @JobTrackedSchedule(jobName = "Stream Attributes Retention", description = "Delete attributes older than system property "
            + STREAM_ATTRIBUTE_DELETE_AGE_PROPERTY + ")")
    public void exec() {
        lockAndDelete();
    }

    @Override
    protected void deleteCurrentBatch(final long total) {
        // Delete stream attributes.
        deleteWithJoin(StreamAttributeValue.TABLE_NAME, StreamAttributeValue.ID, "stream attributes", total);
    }

    @Override
    protected SqlBuilder getTempIdSelectSql(final long age, final int batchSize) {
        final SqlBuilder sql = new SqlBuilder();
        sql.append("SELECT ");
        sql.append(StreamAttributeValue.ID);
        sql.append(" FROM ");
        sql.append(StreamAttributeValue.TABLE_NAME);
        sql.append(" WHERE ");
        sql.append(StreamAttributeValue.CREATE_MS);
        sql.append(" < ");
        sql.arg(age);
        sql.append(" ORDER BY ");
        sql.append(StreamAttributeValue.ID);
        sql.append(" LIMIT ");
        sql.arg(batchSize);
        return sql;
    }
}
