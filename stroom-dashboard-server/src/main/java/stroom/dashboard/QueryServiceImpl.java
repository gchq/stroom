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
 */

package stroom.dashboard;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dashboard.shared.FindQueryCriteria;
import stroom.dashboard.shared.QueryEntity;
import stroom.entity.DocumentEntityServiceImpl;
import stroom.entity.QueryAppender;
import stroom.entity.StroomEntityManager;
import stroom.entity.util.FieldMap;
import stroom.entity.util.HqlBuilder;
import stroom.entity.util.SqlBuilder;
import stroom.importexport.ImportExportHelper;
import stroom.security.SecurityContext;
import stroom.spring.EntityManagerSupport;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
// @Transactional
public class QueryServiceImpl extends DocumentEntityServiceImpl<QueryEntity, FindQueryCriteria> implements QueryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryServiceImpl.class);
    private final StroomEntityManager entityManager;
    private final SecurityContext securityContext;

    @Inject
    public QueryServiceImpl(final StroomEntityManager entityManager,
                            final EntityManagerSupport entityManagerSupport,
                            final ImportExportHelper importExportHelper,
                            final SecurityContext securityContext) {
        super(entityManager, entityManagerSupport, importExportHelper, securityContext);
        this.entityManager = entityManager;
        this.securityContext = securityContext;
    }

    @Override
    public Class<QueryEntity> getEntityClass() {
        return QueryEntity.class;
    }

    @Override
    public FindQueryCriteria createCriteria() {
        return new FindQueryCriteria();
    }

    @Override
    public QueryEntity create(final String name) throws RuntimeException {
        final QueryEntity entity = super.create(name);

        // Create the initial user permissions for this new document.
        securityContext.addDocumentPermissions(null, null, entity.getType(), entity.getUuid(), true);

        return entity;
    }

    @Override
    public void clean(final String user, final boolean favourite, final Integer oldestId, final long oldestCrtMs) {
        try {
            LOGGER.debug("Deleting old rows");

            final SqlBuilder sql = new SqlBuilder();
            sql.append("DELETE");
            sql.append(" FROM ");
            sql.append(QueryEntity.TABLE_NAME);
            sql.append(" WHERE ");
            sql.append(QueryEntity.CREATE_USER);
            sql.append(" = ");
            sql.arg(user);
            sql.append(" AND ");
            sql.append(QueryEntity.FAVOURITE);
            sql.append(" = ");
            sql.arg(favourite);
            sql.append(" AND ");

            if (oldestId != null) {
                sql.append("(");
                sql.append(QueryEntity.ID);
                sql.append(" <= ");
                sql.arg(oldestId);
                sql.append(" OR ");
                sql.append(QueryEntity.CREATE_TIME);
                sql.append(" < ");
                sql.arg(oldestCrtMs);
                sql.append(")");
            } else {
                sql.append(QueryEntity.CREATE_TIME);
                sql.append(" < ");
                sql.arg(oldestCrtMs);
            }

            final long rows = entityManager.executeNativeUpdate(sql);
            LOGGER.debug("Deleted " + rows + " rows");

        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    // @Transactional
    @Override
    public List<String> getUsers(final boolean favourite) {
        final SqlBuilder sql = new SqlBuilder();
        sql.append("SELECT ");
        sql.append(QueryEntity.CREATE_USER);
        sql.append(" FROM ");
        sql.append(QueryEntity.TABLE_NAME);
        sql.append(" WHERE ");
        sql.append(QueryEntity.FAVOURITE);
        sql.append(" = ");
        sql.arg(favourite);
        sql.append(" GROUP BY ");
        sql.append(QueryEntity.CREATE_USER);
        sql.append(" ORDER BY ");
        sql.append(QueryEntity.CREATE_USER);

        @SuppressWarnings("unchecked") final List<String> list = entityManager.executeNativeQueryResultList(sql);

        return list;
    }

    // @Transactional
    @Override
    public Integer getOldestId(final String user, final boolean favourite, final int retain) {
        final SqlBuilder sql = new SqlBuilder();
        sql.append("SELECT");
        sql.append(" ID");
        sql.append(" FROM ");
        sql.append(QueryEntity.TABLE_NAME);
        sql.append(" WHERE ");
        sql.append(QueryEntity.CREATE_USER);
        sql.append(" = ");
        sql.arg(user);
        sql.append(" AND ");
        sql.append(QueryEntity.FAVOURITE);
        sql.append(" = ");
        sql.arg(favourite);
        sql.append(" ORDER BY ID DESC LIMIT 1 OFFSET ");
        sql.arg(retain);

        @SuppressWarnings("unchecked") final List<Integer> list = entityManager.executeNativeQueryResultList(sql);

        if (list.size() == 1) {
            return list.get(0);
        }

        return null;
    }

    @Override
    protected void checkUpdatePermission(final QueryEntity entity) {
        // Ignore.
    }

//    @Override
//    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindQueryCriteria criteria) {
//        CriteriaLoggingUtil.appendLongTerm(items, "dashboardId", criteria.getDashboardId());
//        CriteriaLoggingUtil.appendStringTerm(items, "queryId", criteria.getQueryId());
//        super.appendCriteria(items, criteria);
//    }

    @Override
    protected QueryAppender<QueryEntity, FindQueryCriteria> createQueryAppender(final StroomEntityManager entityManager) {
        return new QueryQueryAppender(entityManager);
    }

    @Override
    public String getNamePattern() {
        // Unnamed queries are valid.
        return null;
    }

    @Override
    protected FieldMap createFieldMap() {
        return super.createFieldMap()
                .add(FindQueryCriteria.FIELD_TIME, QueryEntity.CREATE_TIME, "createTime");
    }

    private static class QueryQueryAppender extends QueryAppender<QueryEntity, FindQueryCriteria> {
        private final QueryEntityMarshaller marshaller;

        QueryQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
            marshaller = new QueryEntityMarshaller();
        }

        @Override
        protected void appendBasicCriteria(final HqlBuilder sql, final String alias, final FindQueryCriteria criteria) {
            super.appendBasicCriteria(sql, alias, criteria);

            if (criteria.getFavourite() != null) {
                sql.appendValueQuery(alias + ".favourite", criteria.getFavourite());
            }

            sql.appendValueQuery(alias + ".dashboardId", criteria.getDashboardId());
            sql.appendValueQuery(alias + ".queryId", criteria.getQueryId());
        }

        @Override
        protected void preSave(final QueryEntity entity) {
            super.preSave(entity);
            marshaller.marshal(entity);
        }

        @Override
        protected void postLoad(final QueryEntity entity) {
            marshaller.unmarshal(entity);
            super.postLoad(entity);
        }
    }
}
