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

package stroom.dashboard.server;

import event.logging.BaseAdvancedQueryItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import stroom.node.server.StroomPropertyService;
import stroom.security.SecurityContext;
import stroom.util.spring.StroomSpringProfiles;

import javax.inject.Inject;
import java.time.ZonedDateTime;
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
    private final StroomPropertyService propertyService;

    @Inject
    QueryServiceImpl(final StroomEntityManager entityManager,
                     final ImportExportHelper importExportHelper,
                     final SecurityContext securityContext,
                     final StroomPropertyService propertyService) {
        super(entityManager, importExportHelper, securityContext);
        this.entityManager = entityManager;
        this.securityContext = securityContext;
        this.propertyService = propertyService;
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
    public Query save(final Query entity) throws RuntimeException {
        final Query query = super.save(entity);

        // Make sure we only keep 100 items in the query history per user.
        if (query != null && !query.isFavourite()) {
            try {

                final long historyItemsRetention = propertyService.getIntProperty("stroom.query.history.itemsRetention", 100);
                final int historyDaysRetention = propertyService.getIntProperty("stroom.query.history.daysRetention", 365);

                final long oldestCrtMs = ZonedDateTime.now().minusDays(historyDaysRetention).toInstant().toEpochMilli();

                LOGGER.debug("Deleting old rows");

                final SQLBuilder sql = new SQLBuilder();
                sql.append("DELETE");
                sql.append(" FROM ");
                sql.append(Query.TABLE_NAME);
                sql.append(" WHERE ");
                sql.append(Query.CREATE_USER);
                sql.append(" = ");
                sql.arg(query.getCreateUser());
                sql.append(" AND ");
                sql.append(Query.FAVOURITE);
                sql.append(" = ");
                sql.arg(query.isFavourite());
                sql.append(" AND ");
                sql.append("(");

                sql.append(Query.ID);
                sql.append(" <= (");

                sql.append("SELECT");
                sql.append(" ID");
                sql.append(" FROM (");

                sql.append("SELECT");
                sql.append(" ID");
                sql.append(" FROM ");
                sql.append(Query.TABLE_NAME);
                sql.append(" WHERE ");
                sql.append(Query.CREATE_USER);
                sql.append(" = ");
                sql.arg(query.getCreateUser());
                sql.append(" AND ");
                sql.append(Query.FAVOURITE);
                sql.append(" = ");
                sql.arg(query.isFavourite());
                sql.append(" ORDER BY ID DESC LIMIT 1 OFFSET ");
                sql.arg(historyItemsRetention);

                sql.append(") foo");

                sql.append(")");

                sql.append(" OR ");
                sql.append(Query.CREATE_TIME);
                sql.append(" < ");
                sql.arg(oldestCrtMs);

                sql.append(")");

                final long rows = entityManager.executeNativeUpdate(sql);
                LOGGER.debug("Deleted " + rows + " rows");

            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        return query;
    }

    @Override
    protected void checkUpdatePermission(final Query entity) {
        // Ignore.
    }

    @Override
    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindQueryCriteria criteria) {
        CriteriaLoggingUtil.appendEntityIdSet(items, "dashboardIdSet", criteria.getDashboardIdSet());
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

            SQLUtil.appendSetQuery(sql, true, alias + ".dashboard", criteria.getDashboardIdSet());
        }
    }

    @Override
    public String getNamePattern() {
        // Unnamed queries are valid.
        return null;
    }
}
