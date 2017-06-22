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

package stroom.dashboard.server;

import event.logging.BaseAdvancedQueryItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.dashboard.shared.FindQueryCriteria;
import stroom.dashboard.shared.Query;
import stroom.dashboard.shared.QueryService;
import stroom.entity.server.AutoMarshal;
import stroom.entity.server.CriteriaLoggingUtil;
import stroom.entity.server.DocumentEntityServiceImpl;
import stroom.entity.server.QueryAppender;
import stroom.entity.server.util.SQLBuilder;
import stroom.entity.server.util.SQLUtil;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.EntityServiceException;
import stroom.importexport.server.ImportExportHelper;
import stroom.security.SecurityContext;
import stroom.util.spring.StroomSpringProfiles;

import javax.inject.Inject;
import java.util.List;
import java.util.UUID;

@Profile(StroomSpringProfiles.PROD)
@Component("queryService")
@Transactional
@AutoMarshal
public class QueryServiceImpl extends DocumentEntityServiceImpl<Query, FindQueryCriteria> implements QueryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryServiceImpl.class);
    private final StroomEntityManager entityManager;
    private final SecurityContext securityContext;

    @Inject
    QueryServiceImpl(final StroomEntityManager entityManager,
                     final ImportExportHelper importExportHelper,
                     final SecurityContext securityContext) {
        super(entityManager, importExportHelper, securityContext);
        this.entityManager = entityManager;
        this.securityContext = securityContext;
    }

    @Override
    public Class<Query> getEntityClass() {
        return Query.class;
    }

    @Override
    public FindQueryCriteria createCriteria() {
        return new FindQueryCriteria();
    }

    // TODO : Remove this when document entities no longer reference a folder.
    // Don't do any create permission checking as a query doesn't live in a folder and all users are allowed to create queries.
    @Override
    public Query create(final DocRef folder, final String name) throws RuntimeException {
        // Create a new entity instance.
        Query entity;
        try {
            entity = getEntityClass().newInstance();
        } catch (final IllegalAccessException | InstantiationException e) {
            throw new EntityServiceException(e.getMessage());
        }

        entity.setName(name);
        if (entity.getUuid() == null) {
            entity.setUuid(UUID.randomUUID().toString());
        }
        entity = super.create(entity);

        // Create the initial user permissions for this new document.
        securityContext.addDocumentPermissions(null, null, entity.getType(), entity.getUuid(), true);

        return entity;
    }

    @Override
    public void clean(final String user, final boolean favourite, final Integer oldestId, final long oldestCrtMs) {
        try {
            LOGGER.debug("Deleting old rows");

            final SQLBuilder sql = new SQLBuilder();
            sql.append("DELETE");
            sql.append(" FROM ");
            sql.append(Query.TABLE_NAME);
            sql.append(" WHERE ");
            sql.append(Query.CREATE_USER);
            sql.append(" = ");
            sql.arg(user);
            sql.append(" AND ");
            sql.append(Query.FAVOURITE);
            sql.append(" = ");
            sql.arg(favourite);
            sql.append(" AND ");

            if (oldestId != null) {
                sql.append("(");
                sql.append(Query.ID);
                sql.append(" <= ");
                sql.arg(oldestId);
                sql.append(" OR ");
                sql.append(Query.CREATE_TIME);
                sql.append(" < ");
                sql.arg(oldestCrtMs);
                sql.append(")");
            } else {
                sql.append(Query.CREATE_TIME);
                sql.append(" < ");
                sql.arg(oldestCrtMs);
            }

            final long rows = entityManager.executeNativeUpdate(sql);
            LOGGER.debug("Deleted " + rows + " rows");

        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    @Override
    public List<String> getUsers(final boolean favourite) {
        final SQLBuilder sql = new SQLBuilder();
        sql.append("SELECT ");
        sql.append(Query.CREATE_USER);
        sql.append(" FROM ");
        sql.append(Query.TABLE_NAME);
        sql.append(" WHERE ");
        sql.append(Query.FAVOURITE);
        sql.append(" = ");
        sql.arg(favourite);
        sql.append(" GROUP BY ");
        sql.append(Query.CREATE_USER);
        sql.append(" ORDER BY ");
        sql.append(Query.CREATE_USER);

        @SuppressWarnings("unchecked") final List<String> list = entityManager.executeNativeQueryResultList(sql);

        return list;
    }

    @Transactional(readOnly = true)
    @Override
    public Integer getOldestId(final String user, final boolean favourite, final int retain) {
        final SQLBuilder sql = new SQLBuilder();
        sql.append("SELECT");
        sql.append(" ID");
        sql.append(" FROM ");
        sql.append(Query.TABLE_NAME);
        sql.append(" WHERE ");
        sql.append(Query.CREATE_USER);
        sql.append(" = ");
        sql.arg(user);
        sql.append(" AND ");
        sql.append(Query.FAVOURITE);
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
    protected void checkUpdatePermission(final Query entity) {
        // Ignore.
    }

    @Override
    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindQueryCriteria criteria) {
        CriteriaLoggingUtil.appendLongTerm(items, "dashboardId", criteria.getDashboardId());
        CriteriaLoggingUtil.appendStringTerm(items, "queryId", criteria.getQueryId());
        super.appendCriteria(items, criteria);
    }

    @Override
    protected QueryAppender<Query, FindQueryCriteria> createQueryAppender(final StroomEntityManager entityManager) {
        return new QueryQueryAppender(entityManager);
    }

    private static class QueryQueryAppender extends QueryAppender<Query, FindQueryCriteria> {
        public QueryQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
        }

        @Override
        protected void appendBasicCriteria(final SQLBuilder sql, final String alias, final FindQueryCriteria criteria) {
            super.appendBasicCriteria(sql, alias, criteria);

            if (criteria.getFavourite() != null) {
                SQLUtil.appendValueQuery(sql, alias + ".favourite", criteria.getFavourite());
            }

            SQLUtil.appendValueQuery(sql, alias + ".dashboardId", criteria.getDashboardId());
            SQLUtil.appendValueQuery(sql, alias + ".queryId", criteria.getQueryId());
        }
    }

    @Override
    public String getNamePattern() {
        // Unnamed queries are valid.
        return null;
    }
}
