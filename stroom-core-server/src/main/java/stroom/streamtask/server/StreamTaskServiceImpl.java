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

package stroom.streamtask.server;

import event.logging.BaseAdvancedQueryItem;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.server.CriteriaLoggingUtil;
import stroom.entity.server.QueryAppender;
import stroom.entity.server.SystemEntityServiceImpl;
import stroom.entity.server.util.FieldMap;
import stroom.entity.server.util.HqlBuilder;
import stroom.entity.server.util.SqlBuilder;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.SQLNameConstants;
import stroom.feed.shared.Feed;
import stroom.node.shared.Node;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.api.v2.DocRef;
import stroom.security.Secured;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.shared.FindStreamTaskCriteria;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.streamtask.shared.StreamTask;
import stroom.streamtask.shared.StreamTaskSummary;
import stroom.streamtask.shared.TaskStatus;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Base class the API stream task services.
 */
@Transactional
@Secured(StreamProcessor.MANAGE_PROCESSORS_PERMISSION)
@Component
public class StreamTaskServiceImpl extends SystemEntityServiceImpl<StreamTask, FindStreamTaskCriteria>
        implements StreamTaskService {
    public static final String TABLE_PREFIX_STREAM_TASK = "ST.";
    public static final String TABLE_PREFIX_FEED = "F.";
    public static final String TABLE_PREFIX_STREAM = "S.";
    public static final String TABLE_PREFIX_PIPELINE = "P.";

    private final StreamStore streamStore;

    @Inject
    public StreamTaskServiceImpl(final StroomEntityManager entityManager, final StreamStore streamStore) {
        super(entityManager);
        this.streamStore = streamStore;
    }

    @Override
    public Class<StreamTask> getEntityClass() {
        return StreamTask.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public BaseResultList<StreamTaskSummary> findSummary(final FindStreamTaskCriteria criteria) throws RuntimeException {
        final SqlBuilder sql = new SqlBuilder();
        sql.append("SELECT D.* FROM (");
        sql.append("SELECT P.");
        sql.append(PipelineEntity.UUID);
        sql.append(" PIPE_UUID, P.");
        sql.append(PipelineEntity.NAME);
        sql.append(" PIPE_NAME, F.");
        sql.append(Feed.UUID);
        sql.append(" FEED_UUID, F.");
        sql.append(Feed.NAME);
        sql.append(" FEED_NAME, SPF.");
        sql.append(StreamProcessorFilter.PRIORITY);
        sql.append(" PRIORITY, ST.");
        sql.append(StreamTask.STATUS);
        sql.append(" STAT_ID, COUNT(*)");
        sql.append(" FROM ");
        sql.append(StreamTask.TABLE_NAME);
        sql.append(" ST JOIN ");
        sql.append(Stream.TABLE_NAME);
        sql.append(" S ON (S.");
        sql.append(Stream.ID);
        sql.append(" = ST.");
        sql.append(Stream.FOREIGN_KEY);
        sql.append(") JOIN ");
        sql.append(Feed.TABLE_NAME);
        sql.append(" F ON (F.");
        sql.append(Feed.ID);
        sql.append(" = S.");
        sql.append(Feed.FOREIGN_KEY);
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
        sql.append(") JOIN ");
        sql.append(PipelineEntity.TABLE_NAME);
        sql.append(" P ON (P.");
        sql.append(PipelineEntity.ID);
        sql.append(" = SP.");
        sql.append(PipelineEntity.FOREIGN_KEY);
        sql.append(")");
        sql.append(" WHERE 1=1");

        sql.appendPrimitiveValueSetQuery("S." + Stream.STATUS, criteria.getStatusSet());
        sql.appendEntityIdSetQuery("P." + BaseEntity.ID, criteria.getPipelineIdSet());
        sql.appendEntityIdSetQuery("F." + BaseEntity.ID, criteria.getFeedIdSet());

        sql.append(" GROUP BY PIPE_UUID, FEED_UUID, PRIORITY, STAT_ID");
        sql.append(") D");

        sql.appendOrderBy(getSqlFieldMap().getSqlFieldMap(), criteria, null);

        final List<Object[]> list = getEntityManager().executeNativeQueryResultList(sql);

        final ArrayList<StreamTaskSummary> summaryData = new ArrayList<>(list.size());
        for (final Object[] row : list) {
            final DocRef pipeline = new DocRef(PipelineEntity.ENTITY_TYPE, (String) row[0], (String) row[1]);
            final DocRef feed = new DocRef(Feed.ENTITY_TYPE, (String) row[2], (String) row[3]);
            final String priority = String.valueOf(row[4]);
            final TaskStatus taskStatus = TaskStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue((byte) row[5]);
            final long count = ((Number) row[6]).longValue();
            final StreamTaskSummary summary = new StreamTaskSummary(pipeline, feed, priority, taskStatus, count);
            summaryData.add(summary);
        }

        return BaseResultList.createUnboundedList(summaryData);
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
        CriteriaLoggingUtil.appendEntityIdSet(items, "pipelineIdSet", criteria.getPipelineIdSet());
        CriteriaLoggingUtil.appendEntityIdSet(items, "feedIdSet", criteria.getFeedIdSet());
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
                .add(FindStreamTaskCriteria.FIELD_PIPELINE_UUID, "P_NAME", "streamProcessorFilter.streamProcessor.pipeline.uuid")
                .add(FindStreamTaskCriteria.FIELD_STATUS, "STAT_ID1", "pstatus")
                .add(FindStreamTaskCriteria.FIELD_COUNT, SQLNameConstants.COUNT, "NA")
                .add(FindStreamTaskCriteria.FIELD_NODE, null, "node.name");
    }

    private static class StreamTaskQueryAppender extends QueryAppender<StreamTask, FindStreamTaskCriteria> {
        StreamTaskQueryAppender(StroomEntityManager entityManager) {
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
                        || fetchSet.contains(PipelineEntity.ENTITY_TYPE)) {
                    sql.append(" JOIN FETCH " + alias + ".streamProcessorFilter AS spf");
                }
                if (fetchSet.contains(StreamProcessor.ENTITY_TYPE) || fetchSet.contains(PipelineEntity.ENTITY_TYPE)) {
                    sql.append(" JOIN FETCH spf.streamProcessor AS sp");
                }
                if (fetchSet.contains(PipelineEntity.ENTITY_TYPE)) {
                    sql.append(" JOIN FETCH sp.pipeline AS p");
                }
                if (fetchSet.contains(Stream.ENTITY_TYPE)) {
                    sql.append(" JOIN FETCH " + alias + ".stream AS s");
                }
                if (fetchSet.contains(Feed.ENTITY_TYPE)) {
                    sql.append(" JOIN FETCH s.feed AS f");
                }
                if (fetchSet.contains(StreamType.ENTITY_TYPE)) {
                    sql.append(" JOIN FETCH s.streamType AS st");
                }
            }
        }

        @Override
        protected void appendBasicCriteria(final HqlBuilder sql, final String alias,
                                           final FindStreamTaskCriteria criteria) {
            super.appendBasicCriteria(sql, alias, criteria);

            // Append all the criteria
            sql.appendPrimitiveValueSetQuery(alias + ".pstatus", criteria.getStreamTaskStatusSet());

            sql.appendEntityIdSetQuery(alias, criteria.getStreamTaskIdSet());

            sql.appendEntityIdSetQuery(alias + ".node", criteria.getNodeIdSet());

            sql.appendEntityIdSetQuery(alias + ".streamProcessorFilter.streamProcessor.pipeline",
                    criteria.obtainPipelineIdSet());

            sql.appendEntityIdSetQuery(alias + ".streamProcessorFilter", criteria.getStreamProcessorFilterIdSet());

            sql.appendValueQuery(alias + ".createMs", criteria.getCreateMs());

//            if (criteria.getStatusSet() != null || criteria.getFeedIdSet() != null || criteria.getPipelineIdSet() != null) {
                sql.appendEntityIdSetQuery(alias + ".stream", criteria.getStreamIdSet());

            sql.appendEntityIdSetQuery(alias + ".stream.streamType", criteria.getStreamTypeIdSet());

            sql.appendPrimitiveValueSetQuery(alias + ".stream.pstatus", criteria.getStatusSet());

                sql.appendEntityIdSetQuery(alias + ".streamProcessorFilter.streamProcessor.pipeline",
                        criteria.getPipelineIdSet());

//                sql.appendEntityIdSetQuery(alias + ".streamProcessorFilter.streamProcessor",
//                        criteria.getStreamProcessorIdSet());

            sql.appendEntityIdSetQuery(alias + ".stream.feed", criteria.getFeedIdSet());

                sql.appendRangeQuery(alias + ".stream.createMs", criteria.getCreatePeriod());
                sql.appendRangeQuery(alias + ".stream.effectiveMs", criteria.getEffectivePeriod());
        }
    }
}