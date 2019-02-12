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

package stroom.data.store.impl.fs;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaFieldNames;
import stroom.entity.shared.BaseResultList;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.task.api.TaskContext;
import stroom.util.date.DateUtil;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

public class StreamDeleteExecutor {//extends AbstractBatchDeleteExecutor {
    private static final String TASK_NAME = "Stream Delete Executor";
    private static final String LOCK_NAME = "StreamDeleteExecutor";
    private static final String TEMP_STRM_ID_TABLE = "TEMP_STRM_ID";

//    private final MetaService metaService;
//
//    @Inject
//    StreamDeleteExecutor(
//                         final ClusterLockService clusterLockService,
//                         final DataStoreServiceConfig dataStoreServiceConfig,
//                         final TaskContext taskContext,
//                         final MetaService metaService) {
//        super(batchIdTransactionHelper, clusterLockService, taskContext, TASK_NAME, LOCK_NAME, dataStoreServiceConfig, TEMP_STRM_ID_TABLE);
//        this.metaService = metaService;
//    }
//
    public void exec() {
//        lockAndDelete();
    }
//
//    @Override
    protected void deleteCurrentBatch(final long total) {
        // TODO : @66 MOVE THIS CODE INTO THE STREAM TASK SERVICE

//        // Delete stream tasks.
//        deleteWithJoin(ProcessorFilterTask.TABLE_NAME, "FK_STRM_ID", "stream tasks", total);

        // TODO : @66 MOVE THIS CODE INTO THE STREAM VOLUME SERVICE

//        // Delete stream volumes.
//        deleteWithJoin(StreamVolumeEntity.TABLE_NAME, "FK_STRM_ID", "stream volumes", total);


        // TODO : @66 MOVE THIS CODE INTO THE STREAM STORE META SERVICE

//        // Delete stream attribute values.
//        deleteWithJoin(StreamAttributeValue.TABLE_NAME, StreamAttributeValue.ID, "stream attribute values",
//                total);

        // TODO : @66 MOVE THIS CODE INTO THE STREAM STORE META SERVICE

//        // Delete streams.
//        deleteWithJoin(StreamEntity.TABLE_NAME, StreamEntity.ID, "streams", total);
    }

//    @Override
//    protected List<Long> getDeleteIdList(final long age, final int batchSize) {
//        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
//                .addTerm(MetaFieldNames.STATUS, Condition.EQUALS, Status.DELETED.getDisplayValue())
//                .addTerm(MetaFieldNames.STATUS_TIME, Condition.LESS_THAN, DateUtil.createNormalDateTimeString(age))
//                .build();
//        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria(expression);
//        findMetaCriteria.setSort(MetaFieldNames.ID);
//        findMetaCriteria.obtainPageRequest().setLength(batchSize);
//        final BaseResultList<Meta> streams = metaService.find(findMetaCriteria);
//        return streams.stream().map(Meta::getId).collect(Collectors.toList());
//    }
}
