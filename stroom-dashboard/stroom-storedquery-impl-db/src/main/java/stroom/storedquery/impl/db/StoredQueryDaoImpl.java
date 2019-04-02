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
import stroom.util.shared.BaseResultList;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static stroom.storedquery.impl.db.jooq.Tables.QUERY;

class StoredQueryDaoImpl implements StoredQueryDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(stroom.storedquery.impl.StoredQueryDao.class);

    private static final Map<String, Field> FIELD_MAP = Map.of(
            FindStoredQueryCriteria.FIELD_ID, QUERY.ID,
            FindStoredQueryCriteria.FIELD_NAME, QUERY.NAME,
            FindStoredQueryCriteria.FIELD_TIME, QUERY.CREATE_TIME_MS);

    private final GenericDao<QueryRecord, StoredQuery, Integer> genericDao;
    private final ConnectionProvider connectionProvider;

    @Inject
    StoredQueryDaoImpl(final ConnectionProvider connectionProvider) {
        genericDao = new GenericDao<>(QUERY, QUERY.ID, StoredQuery.class, connectionProvider);
        this.connectionProvider = connectionProvider;
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
    public BaseResultList<StoredQuery> find(FindStoredQueryCriteria criteria) {
        List<StoredQuery> list = JooqUtil.contextResult(connectionProvider, context -> {
            final List<Condition> conditions = new ArrayList<>();
            if (criteria.getUserId() != null) {
                conditions.add(QUERY.CREATE_USER.eq(criteria.getUserId()));
            }
            JooqUtil.getStringCondition(QUERY.NAME, criteria.getName()).ifPresent(conditions::add);
            if (criteria.getDashboardUuid() != null) {
                conditions.add(QUERY.DASHBOARD_UUID.eq(criteria.getDashboardUuid()));
            }
            if (criteria.getComponentId() != null) {
                conditions.add(QUERY.COMPONENT_ID.eq(criteria.getComponentId()));
            }
            if (criteria.getFavourite() != null) {
                conditions.add(QUERY.FAVOURITE.eq(criteria.getFavourite()));
            }

            final OrderField[] orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);

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
        return BaseResultList.createUnboundedList(list);
    }

    @Override
    public void clean(final String user, final boolean favourite, final Integer oldestId, final long oldestCrtMs) {
        try {
            LOGGER.debug("Deleting old rows");

            final List<Condition> conditions = new ArrayList<>();
            conditions.add(QUERY.CREATE_USER.eq(user));
            conditions.add(QUERY.FAVOURITE.eq(favourite));

            if (oldestId != null) {
                conditions.add(QUERY.ID.le(oldestId).or(QUERY.CREATE_TIME_MS.lt(oldestCrtMs)));
            } else {
                conditions.add(QUERY.CREATE_TIME_MS.lt(oldestCrtMs));
            }

            final int rows = JooqUtil.contextResult(connectionProvider, context -> context
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
        final List<String> list = JooqUtil.contextResult(connectionProvider, context -> context
                .select(QUERY.CREATE_USER)
                .from(QUERY)
                .where(QUERY.FAVOURITE.eq(favourite))
                .groupBy(QUERY.CREATE_USER)
                .orderBy(QUERY.CREATE_USER)
                .fetch(QUERY.CREATE_USER));


//        final SqlBuilder sql = new SqlBuilder();
//        sql.append("SELECT ");
//        sql.append(StoredQuery.CREATE_USER);
//        sql.append(" FROM ");
//        sql.append(StoredQuery.TABLE_NAME);
//        sql.append(" WHERE ");
//        sql.append(StoredQuery.FAVOURITE);
//        sql.append(" = ");
//        sql.arg(favourite);
//        sql.append(" GROUP BY ");
//        sql.append(StoredQuery.CREATE_USER);
//        sql.append(" ORDER BY ");
//        sql.append(StoredQuery.CREATE_USER);
//
//        @SuppressWarnings("unchecked") final List<String> list = entityManager.executeNativeQueryResultList(sql);

        return list;
    }

    @Override
    public Integer getOldestId(final String user, final boolean favourite, final int retain) {
        final Optional<Integer> optional = JooqUtil.contextResult(connectionProvider, context -> context
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
}
