/*
 * Copyright 2024 Crown Copyright
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

package stroom.storedquery.impl.db;

import stroom.dashboard.shared.FindStoredQueryCriteria;
import stroom.dashboard.shared.StoredQuery;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.storedquery.impl.StoredQueryDao;
import stroom.storedquery.impl.db.jooq.tables.records.QueryRecord;
import stroom.util.shared.ResultPage;
import stroom.util.shared.string.CIKey;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static stroom.storedquery.impl.db.jooq.Tables.QUERY;

class StoredQueryDaoImpl implements StoredQueryDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(stroom.storedquery.impl.StoredQueryDao.class);

    private static final Map<CIKey, Field<?>> FIELD_MAP = CIKey.mapOf(
            FindStoredQueryCriteria.FIELD_ID, QUERY.ID,
            FindStoredQueryCriteria.FIELD_NAME, QUERY.NAME,
            FindStoredQueryCriteria.FIELD_TIME, QUERY.CREATE_TIME_MS);

    private final GenericDao<QueryRecord, StoredQuery, Integer> genericDao;
    private final StoredQueryDbConnProvider storedQueryDbConnProvider;

    @Inject
    StoredQueryDaoImpl(final StoredQueryDbConnProvider storedQueryDbConnProvider) {
        genericDao = new GenericDao<>(storedQueryDbConnProvider, QUERY, QUERY.ID, StoredQuery.class);
        this.storedQueryDbConnProvider = storedQueryDbConnProvider;
    }

    @Override
    public StoredQuery create(@NotNull final StoredQuery storedQuery) {
        storedQuery.setUuid(UUID.randomUUID().toString());
        StoredQuerySerialiser.serialise(storedQuery);
        StoredQuery result = genericDao.create(storedQuery);
        StoredQuerySerialiser.deserialise(result);
        return result;
    }

    @Override
    public StoredQuery update(@NotNull final StoredQuery storedQuery) {
        StoredQuerySerialiser.serialise(storedQuery);
        StoredQuery result = genericDao.update(storedQuery);
        StoredQuerySerialiser.deserialise(result);
        return result;
    }

    @Override
    public boolean delete(int id) {
        return genericDao.delete(id);
    }

    @Override
    public Optional<StoredQuery> fetch(int id) {
        return genericDao.fetch(id).map(StoredQuerySerialiser::deserialise);
    }

    @Override
    public ResultPage<StoredQuery> find(final FindStoredQueryCriteria criteria) {
        List<StoredQuery> list = JooqUtil.contextResult(storedQueryDbConnProvider, context -> {
            final Collection<Condition> conditions = JooqUtil.conditions(
                    Optional.ofNullable(criteria.getOwnerUuid()).map(QUERY.OWNER_UUID::eq),
                    JooqUtil.getStringCondition(QUERY.NAME, criteria.getName()),
                    Optional.ofNullable(criteria.getDashboardUuid()).map(QUERY.DASHBOARD_UUID::eq),
                    Optional.ofNullable(criteria.getComponentId()).map(QUERY.COMPONENT_ID::eq),
                    Optional.ofNullable(criteria.getFavourite()).map(QUERY.FAVOURITE::eq));

            final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);
            final int offset = JooqUtil.getOffset(criteria.getPageRequest());
            final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);
            return context
                    .select()
                    .from(QUERY)
                    .where(conditions)
                    .orderBy(orderFields)
                    .limit(offset, limit)
                    .fetch()
                    .into(StoredQuery.class);
        });

        list = list.stream()
                .map(StoredQuerySerialiser::deserialise)
                .collect(Collectors.toList());
        return ResultPage.createCriterialBasedList(list, criteria);
    }

    @Override
    public void clean(final String ownerUuid, final boolean favourite, final Integer oldestId, final long oldestCrtMs) {
        try {
            LOGGER.debug("Deleting old rows");

            final Collection<Condition> conditions = JooqUtil.conditions(
                    Optional.ofNullable(ownerUuid).map(QUERY.OWNER_UUID::eq),
                    Optional.of(QUERY.FAVOURITE.eq(favourite)),
                    Optional.ofNullable(oldestId)
                            .map(id -> QUERY.ID.le(id).or(QUERY.CREATE_TIME_MS.lt(oldestCrtMs)))
                            .or(() -> Optional.of(QUERY.CREATE_TIME_MS.lt(oldestCrtMs))));

            final int rows = JooqUtil.contextResult(storedQueryDbConnProvider, context -> context
                    .deleteFrom(QUERY)
                    .where(conditions)
                    .execute());

            LOGGER.debug("Deleted {} rows for ownerUuid: {}", rows, ownerUuid);

        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Override
    public List<String> getUsers(final boolean favourite) {
        return JooqUtil.contextResult(storedQueryDbConnProvider, context -> context
                .select(QUERY.OWNER_UUID)
                .from(QUERY)
                .where(QUERY.FAVOURITE.eq(favourite))
                .groupBy(QUERY.OWNER_UUID)
                .orderBy(QUERY.OWNER_UUID)
                .fetch(QUERY.OWNER_UUID));
    }

    @Override
    public Integer getOldestId(final String ownerUuid, final boolean favourite, final int retain) {
        final Optional<Integer> optional = JooqUtil.contextResult(storedQueryDbConnProvider, context -> context
                .select(QUERY.ID)
                .from(QUERY)
                .where(QUERY.OWNER_UUID.eq(ownerUuid))
                .and(QUERY.FAVOURITE.eq(favourite))
                .orderBy(QUERY.ID.desc())
                .limit(retain, 1)
                .fetchOptional(QUERY.ID));

//        final SqlBuilder sql = new SqlBuilder();
//        sql.append("SELECT");
//        sql.append(" ID");
//        sql.append(" FROM ");
//        sql.append(StoredQuery.TABLE_NAME);
//        sql.append(" WHERE ");
//        sql.append(StoredQuery.CREATE_USER);
//        sql.append(" = ");
//        sql.arg(user);
//        sql.append(" AND ");
//        sql.append(StoredQuery.FAVOURITE);
//        sql.append(" = ");
//        sql.arg(favourite);
//        sql.append(" ORDER BY ID DESC LIMIT 1 OFFSET ");
//        sql.arg(retain);
//
//        @SuppressWarnings("unchecked") final List<Integer> list = entityManager.executeNativeQueryResultList(sql);
//
//        if (list.size() == 1) {
//            return list.get(0);
//        }

        return optional.orElse(null);
    }
}
