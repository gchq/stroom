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

package stroom.data.store;

import stroom.data.meta.api.FindDataCriteria;
import stroom.data.meta.api.Data;
import stroom.data.meta.api.MetaDataSource;
import stroom.data.meta.api.DataMetaService;
import stroom.data.meta.api.DataStatus;
import stroom.entity.shared.BaseResultList;
import stroom.jobsystem.ClusterLockService;
import stroom.properties.api.PropertyService;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.streamtask.AbstractBatchDeleteExecutor;
import stroom.streamtask.BatchIdTransactionHelper;
import stroom.task.api.TaskContext;
import stroom.util.date.DateUtil;
import stroom.util.lifecycle.JobTrackedSchedule;
import stroom.util.lifecycle.StroomSimpleCronSchedule;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

public class StreamDeleteExecutor extends AbstractBatchDeleteExecutor {
    private static final String TASK_NAME = "Stream Delete Executor";
    private static final String LOCK_NAME = "StreamDeleteExecutor";
    private static final String STREAM_DELETE_PURGE_AGE_PROPERTY = "stroom.stream.deletePurgeAge";
    private static final String STREAM_DELETE_BATCH_SIZE_PROPERTY = "stroom.stream.deleteBatchSize";
    private static final int DEFAULT_STREAM_DELETE_BATCH_SIZE = 1000;
    private static final String TEMP_STRM_ID_TABLE = "TEMP_STRM_ID";

    private final DataMetaService streamMetaService;

    @Inject
    StreamDeleteExecutor(final BatchIdTransactionHelper batchIdTransactionHelper,
                         final ClusterLockService clusterLockService,
                         final PropertyService propertyService,
                         final TaskContext taskContext,
                         final DataMetaService streamMetaService) {
        super(batchIdTransactionHelper, clusterLockService, propertyService, taskContext, TASK_NAME, LOCK_NAME,
                STREAM_DELETE_PURGE_AGE_PROPERTY, STREAM_DELETE_BATCH_SIZE_PROPERTY, DEFAULT_STREAM_DELETE_BATCH_SIZE,
                TEMP_STRM_ID_TABLE);
        this.streamMetaService = streamMetaService;
    }

    @StroomSimpleCronSchedule(cron = "0 0 *")
    @JobTrackedSchedule(jobName = "Stream Delete", description = "Physically delete streams that have been logically deleted based on age of delete ("
            + STREAM_DELETE_PURGE_AGE_PROPERTY + ")")
    public void exec() {
        lockAndDelete();
    }

    @Override
    protected void deleteCurrentBatch(final long total) {
        // TODO : @66 MOVE THIS CODE INTO THE STREAM TASK SERVICE

//        // Delete stream tasks.
//        deleteWithJoin(ProcessorFilterTask.TABLE_NAME, "FK_STRM_ID", "stream tasks", total);

        // TODO : @66 MOVE THIS CODE INTO THE STREAM VOLUME SERVICE

//        // Delete stream volumes.
//        deleteWithJoin(StreamVolumeEntity.TABLE_NAME, "FK_STRM_ID", "stream volumes", total);


        // TODO : @66 MOVE THIS CODE INTO THE STREAM STORE META SERVICE

//        // Delete stream attribute values.
//        deleteWithJoin(StreamAttributeValue.TABLE_NAME, StreamAttributeValue.STREAM_ID, "stream attribute values",
//                total);

        // TODO : @66 MOVE THIS CODE INTO THE STREAM STORE META SERVICE

//        // Delete streams.
//        deleteWithJoin(StreamEntity.TABLE_NAME, StreamEntity.ID, "streams", total);
    }

    @Override
    protected List<Long> getDeleteIdList(final long age, final int batchSize) {
        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaDataSource.STATUS, Condition.EQUALS, DataStatus.DELETED.getDisplayValue())
                .addTerm(MetaDataSource.STATUS_TIME, Condition.LESS_THAN, DateUtil.createNormalDateTimeString(age))
                .build();
        final FindDataCriteria findStreamCriteria = new FindDataCriteria(expression);
        findStreamCriteria.setSort(MetaDataSource.STREAM_ID);
        findStreamCriteria.obtainPageRequest().setLength(batchSize);
        final BaseResultList<Data> streams = streamMetaService.find(findStreamCriteria);
        return streams.stream().map(Data::getId).collect(Collectors.toList());
    }
}
