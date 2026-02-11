/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.cluster.lock.api.ClusterLockService;
import stroom.dashboard.shared.FindStoredQueryCriteria;
import stroom.dashboard.shared.StoredQuery;
import stroom.db.util.JooqUtil;
import stroom.security.user.api.UserRefLookup;
import stroom.storedquery.impl.StoredQueryDao;
import stroom.util.exception.DataChangedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.validation.constraints.NotNull;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static stroom.storedquery.impl.db.jooq.tables.Query.QUERY;

class StoredQueryDaoImpl implements StoredQueryDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StoredQueryDaoImpl.class);

    private static final String LOCK_NAME = "StoredQueryClean";

    private static final Map<String, Field<?>> FIELD_MAP = Map.of(
            FindStoredQueryCriteria.FIELD_ID, QUERY.ID,
            FindStoredQueryCriteria.FIELD_NAME, QUERY.NAME,
            FindStoredQueryCriteria.FIELD_TIME, QUERY.CREATE_TIME_MS);

    private final ClusterLockService clusterLockService;
    private final StoredQueryDbConnProvider storedQueryDbConnProvider;
    private final QueryJsonSerialiser queryJsonSerialiser;
    private final Function<Record, StoredQuery> recordToStoredQueryMapper;

    @Inject
    StoredQueryDaoImpl(final ClusterLockService clusterLockService,
                       final StoredQueryDbConnProvider storedQueryDbConnProvider,
                       final QueryJsonSerialiser queryJsonSerialiser,
                       final Provider<UserRefLookup> userRefLookupProvider) {
        this.clusterLockService = clusterLockService;
        this.storedQueryDbConnProvider = storedQueryDbConnProvider;
        this.queryJsonSerialiser = queryJsonSerialiser;
        recordToStoredQueryMapper = new RecordToStoredQueryMapper(
                queryJsonSerialiser,
                userRefLookupProvider);
    }

    @Override
    public StoredQuery create(@NotNull final StoredQuery storedQuery) {
        return JooqUtil.contextResult(storedQueryDbConnProvider, context -> create(context, storedQuery));
    }

    private StoredQuery create(final DSLContext context, final StoredQuery storedQuery) {
        storedQuery.setVersion(1);
        storedQuery.setUuid(UUID.randomUUID().toString());
        final String data = queryJsonSerialiser.serialise(storedQuery.getQuery());
        final Integer id = context
                .insertInto(QUERY)
                .columns(QUERY.VERSION,
                        QUERY.CREATE_TIME_MS,
                        QUERY.CREATE_USER,
                        QUERY.UPDATE_TIME_MS,
                        QUERY.UPDATE_USER,
                        QUERY.DASHBOARD_UUID,
                        QUERY.COMPONENT_ID,
                        QUERY.NAME,
                        QUERY.DATA,
                        QUERY.FAVOURITE,
                        QUERY.UUID,
                        QUERY.OWNER_UUID)
                .values(storedQuery.getVersion(),
                        storedQuery.getCreateTimeMs(),
                        storedQuery.getCreateUser(),
                        storedQuery.getUpdateTimeMs(),
                        storedQuery.getUpdateUser(),
                        storedQuery.getDashboardUuid(),
                        storedQuery.getComponentId(),
                        storedQuery.getName(),
                        data,
                        storedQuery.isFavourite(),
                        storedQuery.getUuid(),
                        NullSafe.get(storedQuery.getOwner(), UserRef::getUuid))
                .returning(QUERY.ID)
                .fetchOne(QUERY.ID);
        storedQuery.setId(id);
        return storedQuery;
    }

    @Override
    public StoredQuery update(@NotNull final StoredQuery storedQuery) {
        return JooqUtil.contextResult(storedQueryDbConnProvider, context -> update(context, storedQuery));
    }

    private StoredQuery update(final DSLContext context, final StoredQuery storedQuery) {
        final String data = queryJsonSerialiser.serialise(storedQuery.getQuery());
        final int count = context
                .update(QUERY)
                .set(QUERY.VERSION, QUERY.VERSION.plus(1))
                .set(QUERY.UPDATE_TIME_MS, storedQuery.getUpdateTimeMs())
                .set(QUERY.UPDATE_USER, storedQuery.getUpdateUser())
                .set(QUERY.DASHBOARD_UUID, storedQuery.getDashboardUuid())
                .set(QUERY.COMPONENT_ID, storedQuery.getComponentId())
                .set(QUERY.NAME, storedQuery.getName())
                .set(QUERY.DATA, data)
                .set(QUERY.FAVOURITE, storedQuery.isFavourite())
                .set(QUERY.UUID, storedQuery.getUuid())
                .set(QUERY.OWNER_UUID, NullSafe.get(storedQuery.getOwner(), UserRef::getUuid))
                .where(QUERY.ID.eq(storedQuery.getId()))
                .and(QUERY.VERSION.eq(storedQuery.getVersion()))
                .execute();

        if (count == 0) {
            throw new DataChangedException("Failed to update stored query, " +
                                           "it may have been updated by another user or deleted");
        }

        return fetch(storedQuery.getId()).orElseThrow(() ->
                new RuntimeException("Error fetching updated stored query"));
    }


    @Override
    public boolean delete(final int id) {
        final int count = JooqUtil.contextResult(storedQueryDbConnProvider, context -> context
                .deleteFrom(QUERY)
                .where(QUERY.ID.eq(id))
                .execute());
        return count > 0;
    }

    @Override
    public Optional<StoredQuery> fetch(final int id) {
        return JooqUtil.contextResult(storedQueryDbConnProvider, context -> context
                        .select()
                        .from(QUERY)
                        .where(QUERY.ID.eq(id))
                        .fetchOptional())
                .map(recordToStoredQueryMapper);
    }

    @Override
    public ResultPage<StoredQuery> find(final FindStoredQueryCriteria criteria) {
        final List<StoredQuery> list = JooqUtil.contextResult(storedQueryDbConnProvider, context -> {
            final Collection<Condition> conditions = JooqUtil.conditions(
                    Optional.ofNullable(criteria.getOwner()).map(UserRef::getUuid).map(QUERY.OWNER_UUID::eq),
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
                    .fetch();
        }).map(recordToStoredQueryMapper::apply);
        return ResultPage.createCriterialBasedList(list, criteria);
    }

    @Override
    public void clean(final String ownerUuid,
                      final int historyItemsRetention,
                      final long oldestCrtMs) {
        LOGGER.debug(() -> "Trying lock " + LOCK_NAME);
        clusterLockService.tryLock(LOCK_NAME, () -> {
            try {
                LOGGER.debug("Deleting old rows for ownerUuid: {}, historyItemsRetention: {}, oldestCrtMs: {}",
                        ownerUuid, historyItemsRetention, oldestCrtMs);
                final int rows = JooqUtil.contextResult(storedQueryDbConnProvider, context -> {

                    final Field<Integer> rowNumField = DSL.rowNumber()
                            .over(DSL.orderBy(QUERY.ID.desc()))
                            .as("rn");

                    // Rank the rows with lowest ronNum being the most recent
                    // and highest rowNum being the oldest
                    final Table<?> inner = context
                            .select(
                                    QUERY.ID,
                                    rowNumField,
                                    QUERY.CREATE_TIME_MS)
                            .from(QUERY)
                            .where(QUERY.OWNER_UUID.eq(ownerUuid))
                            .and(QUERY.FAVOURITE.eq(false)) // Don't want to delete favourites
                            .asTable("inner");
                    final Field<Integer> innerId = inner.field(QUERY.ID);

                    return context
                            .deleteFrom(QUERY)
                            .where(DSL.exists(
                                    context.select(DSL.inline((String) null))
                                            .from(inner)
                                            .where(rowNumField.greaterThan(historyItemsRetention)
                                                    .or(QUERY.CREATE_TIME_MS.lessThan(oldestCrtMs)))
                                            .and(QUERY.ID.eq(innerId))
                            ))
                            .execute();
                });

                LOGGER.debug("Deleted {} rows for ownerUuid: {}", rows, ownerUuid);
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    @Override
    public Set<String> getUsersWithNonFavourites() {
        return JooqUtil.contextResult(storedQueryDbConnProvider, context -> new HashSet<>(context
                .selectDistinct(QUERY.OWNER_UUID)
                .from(QUERY)
                .where(QUERY.FAVOURITE.eq(false))
                .fetch(QUERY.OWNER_UUID)));
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

    @Override
    public int delete(final UserRef ownerUserRef) {
        Objects.requireNonNull(ownerUserRef);
        final int delCount = JooqUtil.contextResult(storedQueryDbConnProvider, dslContext -> dslContext
                .deleteFrom(QUERY)
                .where(QUERY.OWNER_UUID.eq(ownerUserRef.getUuid()))
                .execute());

        LOGGER.debug(() -> LogUtil.message("Deleted {} {} records for ownerUserRef {}",
                delCount, QUERY.getName(), ownerUserRef.toInfoString()));
        return delCount;
    }
}
