/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.streamtask;

import event.logging.BaseAdvancedQueryItem;
import stroom.entity.CriteriaLoggingUtil;
import stroom.entity.QueryAppender;
import stroom.entity.StroomEntityManager;
import stroom.entity.SystemEntityServiceImpl;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.SQLNameConstants;
import stroom.entity.shared.SummaryDataRow;
import stroom.entity.util.FieldMap;
import stroom.entity.util.HqlBuilder;
import stroom.entity.util.SqlBuilder;
import stroom.node.shared.Node;
import stroom.pipeline.shared.PipelineDoc;
import stroom.security.Security;
import stroom.security.shared.PermissionNames;
import stroom.streamstore.FeedService;
import stroom.streamstore.StreamTypeService;
import stroom.streamstore.shared.FeedEntity;
import stroom.streamstore.shared.StreamEntity;
import stroom.streamstore.shared.StreamStatusId;
import stroom.streamstore.shared.StreamTypeEntity;
import stroom.streamtask.shared.FindStreamTaskCriteria;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.streamtask.shared.StreamTask;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Set;

/**
 * Base class the API stream task services.
 */
@Singleton
public class StreamTaskServiceImpl extends SystemEntityServiceImpl<StreamTask, FindStreamTaskCriteria>
        implements StreamTaskService {
    private static final String TABLE_PREFIX_STREAM_TASK = "ST.";
    private final FeedService feedService;
    private final StreamTypeService streamTypeService;
    private final Security security;

    @Inject
    StreamTaskServiceImpl(final StroomEntityManager entityManager,
                          final FeedService feedService,
                          final StreamTypeService streamTypeService,
                          final Security security) {
        super(entityManager, security);
        this.feedService = feedService;
        this.streamTypeService = streamTypeService;
        this.security = security;
    }

    @Override
    public Class<StreamTask> getEntityClass() {
        return StreamTask.class;
    }

    @Override
    public BaseResultList<SummaryDataRow> findSummary(final FindStreamTaskCriteria criteria) {
        return security.secureResult(permission(), () -> {
            final SqlBuilder sql = new SqlBuilder();
            sql.append("SELECT D.* FROM (");
            sql.append("SELECT 0");
//            sql.append(StreamProcessor.PIPELINE_UUID);
//            sql.append(" PIPE_UUID,");
            sql.append(", SP.");
            sql.append(StreamProcessor.PIPELINE_UUID);
            sql.append(" PIPE_UUID, F.");
            sql.append(FeedEntity.ID);
            sql.append(" FEED_ID, F.");
            sql.append(SQLNameConstants.NAME);
            sql.append(" F_NAME, SPF.");
            sql.append(StreamProcessorFilter.PRIORITY);
            sql.append(" PRIORITY_1, SPF.");
            sql.append(StreamProcessorFilter.PRIORITY);
            sql.append(" PRIORITY_2, ST.");
            sql.append(StreamTask.STATUS);
            sql.append(" STAT_ID1, ST.");
            sql.append(StreamTask.STATUS);
            sql.append(" STAT_ID2, COUNT(*) AS ");
            sql.append(SQLNameConstants.COUNT);
            sql.append(" FROM ");
            sql.append(StreamTask.TABLE_NAME);
            sql.append(" ST JOIN ");
            sql.append(StreamEntity.TABLE_NAME);
            sql.append(" S ON (S.");
            sql.append(StreamEntity.ID);
            sql.append(" = ST.");
            sql.append(StreamEntity.FOREIGN_KEY);
            sql.append(") JOIN ");
            sql.append(FeedEntity.TABLE_NAME);
            sql.append(" F ON (F.");
            sql.append(FeedEntity.ID);
            sql.append(" = S.");
            sql.append(FeedEntity.FOREIGN_KEY);
            sql.append(") JOIN ");
            sql.append(StreamProcessorFilter.TABLE_NAME);
            sql.append(" SPF ON (SPF.");
            sql.append(StreamProcessorFilter.ID);
            sql.append(" = ST.");
            sql.append(StreamProcessorFilter.FOREIGN_KEY);
            sql.append(") JOIN ");
            sql.append(StreamProcessor.TABLE_NAME);
            sql.append(" SP ON (SP.");
            sql.append(StreamProcessor.ID);
            sql.append(" = SPF.");
            sql.append(StreamProcessor.FOREIGN_KEY);
            sql.append(")");
            sql.append(" WHERE 1=1");

            sql.appendPrimitiveValueSetQuery("S." + StreamEntity.STATUS, StreamStatusId.convertStatusSet(criteria.getStatusSet()));
            sql.appendDocRefSetQuery("SP." + StreamProcessor.PIPELINE_UUID, criteria.getPipelineSet());
            sql.appendEntityIdSetQuery("F." + BaseEntity.ID, feedService.convertNameSet(criteria.getFeedNameSet()));

            sql.append(" GROUP BY PIPE_UUID, FEED_ID, PRIORITY_1, STAT_ID1");
            sql.append(") D");

            sql.appendOrderBy(getSqlFieldMap().getSqlFieldMap(), criteria, null);

            return getEntityManager().executeNativeQuerySummaryDataResult(sql, 4);
        });
    }

    @Override
    public FindStreamTaskCriteria createCriteria() {
        return new FindStreamTaskCriteria();
    }

    @Override
    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindStreamTaskCriteria criteria) {
        CriteriaLoggingUtil.appendCriteriaSet(items, "streamTaskStatusSet", criteria.getStreamTaskStatusSet());
        CriteriaLoggingUtil.appendEntityIdSet(items, "streamIdSet", criteria.getStreamIdSet());
        CriteriaLoggingUtil.appendEntityIdSet(items, "nodeIdSet", criteria.getNodeIdSet());
        CriteriaLoggingUtil.appendEntityIdSet(items, "streamTaskIdSet", criteria.getStreamTaskIdSet());
        CriteriaLoggingUtil.appendEntityIdSet(items, "streamProcessorFilterIdSet", criteria.getStreamProcessorFilterIdSet());
        CriteriaLoggingUtil.appendCriteriaSet(items, "statusSet", criteria.getStatusSet());
        CriteriaLoggingUtil.appendCriteriaSet(items, "pipelineSet", criteria.getPipelineSet());
        CriteriaLoggingUtil.appendCriteriaSet(items, "feedNameSet", criteria.getFeedNameSet());
        CriteriaLoggingUtil.appendDateTerm(items, "createMs", criteria.getCreateMs());
        CriteriaLoggingUtil.appendRangeTerm(items, "createPeriod", criteria.getCreatePeriod());
        CriteriaLoggingUtil.appendRangeTerm(items, "effectivePeriod", criteria.getEffectivePeriod());
        super.appendCriteria(items, criteria);
    }

    @Override
    protected QueryAppender<StreamTask, FindStreamTaskCriteria> createQueryAppender(StroomEntityManager entityManager) {
        return new StreamTaskQueryAppender(entityManager);
    }

    @Override
    protected FieldMap createFieldMap() {
        return super.createFieldMap()
                .add(FindStreamTaskCriteria.FIELD_CREATE_TIME, TABLE_PREFIX_STREAM_TASK + StreamTask.CREATE_MS, "createMs")
                .add(FindStreamTaskCriteria.FIELD_START_TIME, TABLE_PREFIX_STREAM_TASK + StreamTask.START_TIME_MS, "startTimeMs")
                .add(FindStreamTaskCriteria.FIELD_END_TIME_DATE, TABLE_PREFIX_STREAM_TASK + StreamTask.END_TIME_MS, "endTimeMs")
                .add(FindStreamTaskCriteria.FIELD_FEED_NAME, "F_NAME", "stream.feed.name")
                .add(FindStreamTaskCriteria.FIELD_PRIORITY, "PRIORITY_1", "streamProcessorFilter.priority")
                .add(FindStreamTaskCriteria.FIELD_PIPELINE_NAME, "P_NAME", "streamProcessorFilter.streamProcessor.pipeline.name")
                .add(FindStreamTaskCriteria.FIELD_STATUS, "STAT_ID1", "pstatus")
                .add(FindStreamTaskCriteria.FIELD_COUNT, SQLNameConstants.COUNT, "NA")
                .add(FindStreamTaskCriteria.FIELD_NODE, null, "node.name");
    }

    @Override
    protected String permission() {
        return PermissionNames.MANAGE_PROCESSORS_PERMISSION;
    }

    private class StreamTaskQueryAppender extends QueryAppender<StreamTask, FindStreamTaskCriteria> {

        StreamTaskQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
        }

        @Override
        protected void appendBasicJoin(final HqlBuilder sql, final String alias, final Set<String> fetchSet) {
            super.appendBasicJoin(sql, alias, fetchSet);

            if (fetchSet != null) {
                if (fetchSet.contains(Node.ENTITY_TYPE)) {
                    sql.append(" LEFT JOIN FETCH " + alias + ".node AS node");
                }
                if (fetchSet.contains(StreamProcessorFilter.ENTITY_TYPE) || fetchSet.contains(StreamProcessor.ENTITY_TYPE)
                        || fetchSet.contains(PipelineDoc.DOCUMENT_TYPE)) {
                    sql.append(" JOIN FETCH " + alias + ".streamProcessorFilter AS spf");
                }
                if (fetchSet.contains(StreamProcessor.ENTITY_TYPE) || fetchSet.contains(PipelineDoc.DOCUMENT_TYPE)) {
                    sql.append(" JOIN FETCH spf.streamProcessor AS sp");
                }
                if (fetchSet.contains(StreamEntity.ENTITY_TYPE)) {
                    sql.append(" JOIN FETCH " + alias + ".stream AS s");
                }
                if (fetchSet.contains(FeedEntity.ENTITY_TYPE)) {
                    sql.append(" JOIN FETCH s.feed AS f");
                }
                if (fetchSet.contains(StreamTypeEntity.ENTITY_TYPE)) {
                    sql.append(" JOIN FETCH s.streamType AS st");
                }
            }
        }

        @Override
        protected void appendBasicCriteria(final HqlBuilder sql,
                                           final String alias,
                                           final FindStreamTaskCriteria criteria) {
            super.appendBasicCriteria(sql, alias, criteria);

            // Append all the criteria
            sql.appendPrimitiveValueSetQuery(alias + ".pstatus", criteria.getStreamTaskStatusSet());

            sql.appendEntityIdSetQuery(alias, criteria.getStreamTaskIdSet());

            sql.appendEntityIdSetQuery(alias + ".node", criteria.getNodeIdSet());

            sql.appendDocRefSetQuery(alias + ".streamProcessorFilter.streamProcessor.pipelineUuid",
                    criteria.obtainPipelineSet());

            sql.appendEntityIdSetQuery(alias + ".streamProcessorFilter", criteria.getStreamProcessorFilterIdSet());

            sql.appendValueQuery(alias + ".createMs", criteria.getCreateMs());

//            if (criteria.getStatusSet() != null || criteria.getFeedIdSet() != null || criteria.getPipelineSet() != null) {
            sql.appendEntityIdSetQuery(alias + ".stream", criteria.getStreamIdSet());

            sql.appendEntityIdSetQuery(alias + ".stream.streamType", streamTypeService.convertNameSet(criteria.getStreamTypeNameSet()));

            sql.appendPrimitiveValueSetQuery(alias + ".stream.pstatus", StreamStatusId.convertStatusSet(criteria.getStatusSet()));

//            sql.appendEntityIdSetQuery(alias + ".streamProcessorFilter.streamProcessor.pipeline",
//                    criteria.getPipelineSet());
//
//                sql.appendEntityIdSetQuery(alias + ".streamProcessorFilter.streamProcessor",
//                        criteria.getStreamProcessorIdSet());

            sql.appendEntityIdSetQuery(alias + ".stream.feed", feedService.convertNameSet(criteria.getFeedNameSet()));

            sql.appendRangeQuery(alias + ".stream.createMs", criteria.getCreatePeriod());
            sql.appendRangeQuery(alias + ".stream.effectiveMs", criteria.getEffectivePeriod());
        }
    }
}