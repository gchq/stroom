package stroom.storedquery.impl.db;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dashboard.shared.FindStoredQueryCriteria;
import stroom.dashboard.shared.StoredQuery;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.storedquery.impl.StoredQueryDao;
import stroom.storedquery.impl.db.jooq.tables.records.QueryRecord;
import stroom.util.shared.ResultPage;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static stroom.storedquery.impl.db.jooq.Tables.QUERY;

class StoredQueryDaoImpl implements StoredQueryDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(stroom.storedquery.impl.StoredQueryDao.class);

    private static final Map<String, Field<?>> FIELD_MAP = Map.of(
            FindStoredQueryCriteria.FIELD_ID, QUERY.ID,
            FindStoredQueryCriteria.FIELD_NAME, QUERY.NAME,
            FindStoredQueryCriteria.FIELD_TIME, QUERY.CREATE_TIME_MS);

    private final GenericDao<QueryRecord, StoredQuery, Integer> genericDao;
    private final StoredQueryDbConnProvider storedQueryDbConnProvider;

    @Inject
    StoredQueryDaoImpl(final StoredQueryDbConnProvider storedQueryDbConnProvider) {
        genericDao = new GenericDao<>(QUERY, QUERY.ID, StoredQuery.class, storedQueryDbConnProvider);
        this.storedQueryDbConnProvider = storedQueryDbConnProvider;
    }

    @Override
    public StoredQuery create(@Nonnull final StoredQuery storedQuery) {
        StoredQuerySerialiser.serialise(storedQuery);
        StoredQuery result = genericDao.create(storedQuery);
        StoredQuerySerialiser.deserialise(result);
        return result;
    }

    @Override
    public StoredQuery update(@Nonnull final StoredQuery storedQuery) {
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
    public ResultPage<StoredQuery> find(FindStoredQueryCriteria criteria) {
        List<StoredQuery> list = JooqUtil.contextResult(storedQueryDbConnProvider, context -> {
            final Collection<Condition> conditions = JooqUtil.conditions(
                    Optional.ofNullable(criteria.getUserId()).map(QUERY.CREATE_USER::eq),
                    JooqUtil.getStringCondition(QUERY.NAME, criteria.getName()),
                    Optional.ofNullable(criteria.getDashboardUuid()).map(QUERY.DASHBOARD_UUID::eq),
                    Optional.ofNullable(criteria.getComponentId()).map(QUERY.COMPONENT_ID::eq),
                    Optional.ofNullable(criteria.getFavourite()).map(QUERY.FAVOURITE::eq));

            final OrderField<?>[] orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);

            return context
                    .select()
                    .from(QUERY)
                    .where(conditions)
                    .orderBy(orderFields)
                    .limit(JooqUtil.getLimit(criteria.getPageRequest()))
                    .offset(JooqUtil.getOffset(criteria.getPageRequest()))
                    .fetch()
                    .into(StoredQuery.class);
        });

        list = list.stream().map(StoredQuerySerialiser::deserialise).collect(Collectors.toList());
        return ResultPage.createUnboundedList(list);
    }

    @Override
    public void clean(final String user, final boolean favourite, final Integer oldestId, final long oldestCrtMs) {
        try {
            LOGGER.debug("Deleting old rows");

            final Collection<Condition> conditions = JooqUtil.conditions(
                    Optional.ofNullable(user).map(QUERY.CREATE_USER::eq),
                    Optional.of(QUERY.FAVOURITE.eq(favourite)),
                    Optional.ofNullable(oldestId)
                            .map(id -> QUERY.ID.le(id).or(QUERY.CREATE_TIME_MS.lt(oldestCrtMs)))
                            .or(() -> Optional.of(QUERY.CREATE_TIME_MS.lt(oldestCrtMs))));

            final int rows = JooqUtil.contextResult(storedQueryDbConnProvider, context -> context
                    .deleteFrom(QUERY)
                    .where(conditions)
                    .execute());

            LOGGER.debug("Deleted " + rows + " rows");

        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Override
    public List<String> getUsers(final boolean favourite) {
        return JooqUtil.contextResult(storedQueryDbConnProvider, context -> context
                .select(QUERY.CREATE_USER)
                .from(QUERY)
                .where(QUERY.FAVOURITE.eq(favourite))
                .groupBy(QUERY.CREATE_USER)
                .orderBy(QUERY.CREATE_USER)
                .fetch(QUERY.CREATE_USER));
    }

    @Override
    public Integer getOldestId(final String user, final boolean favourite, final int retain) {
        final Optional<Integer> optional = JooqUtil.contextResult(storedQueryDbConnProvider, context -> context
                .select(QUERY.ID)
                .from(QUERY)
                .where(QUERY.CREATE_USER.eq(user)
                        .and(QUERY.FAVOURITE.eq(favourite)))
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
    public void clear() {
        JooqUtil.context(storedQueryDbConnProvider, context -> context
                .deleteFrom(QUERY)
                .execute());
    }
}
