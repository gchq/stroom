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

import stroom.entity.server.CriteriaLoggingUtil;
import stroom.entity.server.QueryAppender;
import stroom.entity.server.SystemEntityServiceImpl;
import stroom.entity.server.UserManagerQueryUtil;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.server.util.SQLBuilder;
import stroom.entity.server.util.SQLUtil;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Folder;
import stroom.entity.shared.SQLNameConstants;
import stroom.entity.shared.SummaryDataRow;
import stroom.feed.shared.Feed;
import stroom.node.shared.Node;
import stroom.pipeline.shared.PipelineEntity;
import stroom.security.Secured;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.shared.FindStreamTaskCriteria;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.streamtask.shared.StreamTask;
import stroom.streamtask.shared.StreamTaskService;
import event.logging.BaseAdvancedQueryItem;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
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
    public BaseResultList<SummaryDataRow> findSummary(final FindStreamTaskCriteria criteria) throws RuntimeException {
        final SQLBuilder sql = new SQLBuilder();
        sql.append("SELECT D.* FROM (");
        sql.append("SELECT P.");
        sql.append(PipelineEntity.ID);
        sql.append(" PIPE_ID, P.");
        sql.append(SQLNameConstants.NAME);
        sql.append(" P_NAME, F.");
        sql.append(Feed.ID);
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

        SQLUtil.appendSetQuery(sql, false, "S." + Stream.STATUS, criteria.obtainFindStreamCriteria().getStatusSet(),
                false);
        SQLUtil.appendSetQuery(sql, false, "P." + BaseEntity.ID,
                criteria.obtainFindStreamCriteria().getPipelineIdSet());
        SQLUtil.appendIncludeExcludeSetQuery(sql, false, "F." + BaseEntity.ID,
                criteria.obtainFindStreamCriteria().getFeeds());

        UserManagerQueryUtil.appendFolderCriteria(criteria.obtainFindStreamCriteria().getFolderIdSet(),
                "P." + Folder.FOREIGN_KEY, sql, false, getEntityManager());

        sql.append(" GROUP BY PIPE_ID, FEED_ID, PRIORITY_1, STAT_ID1");
        sql.append(") D");

        SQLUtil.appendOrderBy(sql, false, criteria, null);

        return getEntityManager().executeNativeQuerySummaryDataResult(sql, 4);
    }

    @Override
    public FindStreamTaskCriteria createCriteria() {
        return new FindStreamTaskCriteria();
    }

    @Override
    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindStreamTaskCriteria criteria) {
        CriteriaLoggingUtil.appendCriteriaSet(items, "streamTaskStatusSet", criteria.getStreamTaskStatusSet());
        CriteriaLoggingUtil.appendEntityIdSet(items, "nodeIdSet", criteria.getNodeIdSet());
        CriteriaLoggingUtil.appendEntityIdSet(items, "streamTaskIdSet", criteria.getStreamTaskIdSet());
        CriteriaLoggingUtil.appendEntityIdSet(items, "streamProcessorFilterIdSet",
                criteria.getStreamProcessorFilterIdSet());
        CriteriaLoggingUtil.appendEntityIdSet(items, "pipelineIdSet", criteria.getPipelineIdSet());
        CriteriaLoggingUtil.appendDateTerm(items, "createMs", criteria.getCreateMs());

        streamStore.appendCriteria(items, criteria.getFindStreamCriteria());

        super.appendCriteria(items, criteria);
    }

    @Override
    protected QueryAppender<StreamTask, FindStreamTaskCriteria> createQueryAppender(StroomEntityManager entityManager) {
        return new StreamTaskQueryAppender(entityManager);
    }

    private static class StreamTaskQueryAppender extends QueryAppender<StreamTask, FindStreamTaskCriteria> {
        public StreamTaskQueryAppender(StroomEntityManager entityManager) {
            super(entityManager);
        }

        @Override
        protected void appendBasicJoin(final SQLBuilder sql, final String alias, final Set<String> fetchSet) {
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
        protected void appendBasicCriteria(final SQLBuilder sql, final String alias,
                                           final FindStreamTaskCriteria criteria) {
            super.appendBasicCriteria(sql, alias, criteria);

            // Append all the criteria
            SQLUtil.appendSetQuery(sql, true, alias + ".pstatus", criteria.getStreamTaskStatusSet(), false);

            SQLUtil.appendSetQuery(sql, true, alias, criteria.getStreamTaskIdSet());

            SQLUtil.appendSetQuery(sql, true, alias + ".node", criteria.getNodeIdSet());

            SQLUtil.appendSetQuery(sql, true, alias + ".streamProcessorFilter.streamProcessor.pipeline",
                    criteria.obtainPipelineIdSet());

            SQLUtil.appendSetQuery(sql, true, alias + ".streamProcessorFilter", criteria.getStreamProcessorFilterIdSet());

            SQLUtil.appendValueQuery(sql, alias + ".createMs", criteria.getCreateMs());

            final FindStreamCriteria findStreamCriteria = criteria.getFindStreamCriteria();
            if (findStreamCriteria != null) {
                SQLUtil.appendSetQuery(sql, true, alias + ".stream", findStreamCriteria.getStreamIdSet());

                SQLUtil.appendSetQuery(sql, true, alias + ".stream.streamType", findStreamCriteria.getStreamTypeIdSet());

                SQLUtil.appendSetQuery(sql, true, alias + ".stream.pstatus", findStreamCriteria.getStatusSet(), false);

                SQLUtil.appendSetQuery(sql, true, alias + ".streamProcessorFilter.streamProcessor.pipeline",
                        findStreamCriteria.getPipelineIdSet());

                SQLUtil.appendSetQuery(sql, true, alias + ".streamProcessorFilter.streamProcessor",
                        findStreamCriteria.getStreamProcessorIdSet());

                SQLUtil.appendIncludeExcludeSetQuery(sql, true, alias + ".stream.feed", findStreamCriteria.getFeeds());

                SQLUtil.appendRangeQuery(sql, alias + ".stream.createMs", findStreamCriteria.getCreatePeriod());
                SQLUtil.appendRangeQuery(sql, alias + ".stream.effectiveMs", findStreamCriteria.getEffectivePeriod());
            }

            UserManagerQueryUtil.appendFolderCriteria(criteria.obtainFindStreamCriteria().getFolderIdSet(),
                    alias + ".streamProcessorFilter.streamProcessor.pipeline.folder", sql, true, getEntityManager());
        }
    }
}
