package stroom.security.impl.db;

import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.v2.ExpressionOperator;
import stroom.security.impl.UserDao;
import stroom.security.impl.db.jooq.tables.records.StroomUserRecord;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.security.shared.UserFields;
import stroom.util.NullSafe;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static stroom.security.impl.db.jooq.Tables.STROOM_USER;
import static stroom.security.impl.db.jooq.Tables.STROOM_USER_GROUP;

public class UserDaoImpl implements UserDao {

    private static final Function<Record, User> RECORD_TO_USER_MAPPER = record -> {
        final User user = new User();
        // This is just the PK of the table.
        user.setId(record.get(STROOM_USER.ID));
        user.setVersion(record.get(STROOM_USER.VERSION));
        user.setCreateTimeMs(record.get(STROOM_USER.CREATE_TIME_MS));
        user.setCreateUser(record.get(STROOM_USER.CREATE_USER));
        user.setUpdateTimeMs(record.get(STROOM_USER.UPDATE_TIME_MS));
        user.setUpdateUser(record.get(STROOM_USER.UPDATE_USER));
        // This is the unique 'sub'/'oid' when using OIDC
        // or it is the username if using internal IDP
        // or it is the group name.
        user.setSubjectId(record.get(STROOM_USER.NAME));
        // Our own UUID for the user, independent of any identity provider
        user.setUuid(record.get(STROOM_USER.UUID));
        user.setGroup(record.get(STROOM_USER.IS_GROUP));
        // Optional
        user.setDisplayName(record.get(STROOM_USER.DISPLAY_NAME));
        // Optional
        user.setFullName(record.get(STROOM_USER.FULL_NAME));
        return user;
    };

    private static final BiFunction<User, StroomUserRecord, StroomUserRecord> USER_TO_RECORD_MAPPER =
            (user, record) -> {
                record.from(user);
                record.set(STROOM_USER.ID, user.getId());
                record.set(STROOM_USER.VERSION, user.getVersion());
                record.set(STROOM_USER.CREATE_TIME_MS, user.getCreateTimeMs());
                record.set(STROOM_USER.CREATE_USER, user.getCreateUser());
                record.set(STROOM_USER.UPDATE_TIME_MS, user.getUpdateTimeMs());
                record.set(STROOM_USER.UPDATE_USER, user.getUpdateUser());
                // This is the unique 'sub'/'oid' when using OIDC
                // or it is the username if using internal IDP
                // or it is the group name.
                record.set(STROOM_USER.NAME, user.getSubjectId());
                // Our own UUID for the user, independent of any identity provider
                record.set(STROOM_USER.UUID, user.getUuid());
                record.set(STROOM_USER.IS_GROUP, user.isGroup());
                record.set(STROOM_USER.ENABLED, true);
                // Optional
                record.set(STROOM_USER.DISPLAY_NAME, user.getDisplayName());
                // Optional
                record.set(STROOM_USER.FULL_NAME, user.getFullName());
                return record;
            };


    private final SecurityDbConnProvider securityDbConnProvider;
    private final GenericDao<StroomUserRecord, User, Integer> genericDao;
    private final ExpressionMapper expressionMapper;

    @Inject
    public UserDaoImpl(final SecurityDbConnProvider securityDbConnProvider,
                       final ExpressionMapperFactory expressionMapperFactory) {
        this.securityDbConnProvider = securityDbConnProvider;

        genericDao = new GenericDao<>(
                securityDbConnProvider,
                STROOM_USER,
                STROOM_USER.ID,
                USER_TO_RECORD_MAPPER,
                RECORD_TO_USER_MAPPER);

        expressionMapper = expressionMapperFactory.create();
        expressionMapper.map(UserFields.IS_GROUP, STROOM_USER.IS_GROUP, Boolean::valueOf);
        expressionMapper.map(UserFields.NAME, STROOM_USER.NAME, String::valueOf);
        expressionMapper.map(UserFields.DISPLAY_NAME, STROOM_USER.DISPLAY_NAME, String::valueOf);
        expressionMapper.map(UserFields.FULL_NAME, STROOM_USER.FULL_NAME, String::valueOf);
    }

    @Override
    public User create(final User user) {
        return genericDao.create(user);
    }

    @Override
    public User tryCreate(final User user, final Consumer<User> onUserCreateAction) {
        return genericDao.tryCreate(user, STROOM_USER.NAME, STROOM_USER.IS_GROUP, onUserCreateAction);
    }

    @Override
    public Optional<User> getByUuid(final String uuid) {
        return JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select()
                        .from(STROOM_USER)
                        .where(STROOM_USER.UUID.eq(uuid))
                        .fetchOptional())
                .map(RECORD_TO_USER_MAPPER);
    }

    @Override
    public Optional<User> getUserBySubjectId(final String subjectId) {
        return JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select()
                        .from(STROOM_USER)
                        .where(STROOM_USER.NAME.eq(subjectId))
                        .and(STROOM_USER.IS_GROUP.eq(false))
                        .fetchOptional())
                .map(RECORD_TO_USER_MAPPER);
    }

    @Override
    public Optional<User> getGroupByName(final String groupName) {
        return JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select()
                        .from(STROOM_USER)
                        .where(STROOM_USER.NAME.eq(groupName))
                        .and(STROOM_USER.IS_GROUP.eq(true))
                        .fetchOptional())
                .map(RECORD_TO_USER_MAPPER);
    }

    @Override
    public User update(final User user) {
        return genericDao.update(user);
    }

    /**
     * Only do a logical delete.
     * @param uuid
     */
    @Override
    public void delete(final String uuid) {
        JooqUtil.context(securityDbConnProvider, context -> context
                .update(STROOM_USER)
                .set(STROOM_USER.ENABLED, false)
                .where(STROOM_USER.UUID.eq(uuid))
                .execute()
        );
    }

    @Override
    public ResultPage<User> find(final FindUserCriteria criteria) {
        final Condition condition = expressionMapper.apply(criteria.getExpression());
        final Collection<OrderField<?>> orderFields = createOrderFields(criteria);
        final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);
        final int offset = JooqUtil.getOffset(criteria.getPageRequest());

        final List<User> list = JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select()
                        .from(STROOM_USER)
                        .where(condition)
                        .and(STROOM_USER.ENABLED.eq(true))
                        .orderBy(orderFields)
                        .offset(offset)
                        .limit(limit)
                        .fetch())
                .stream()
                .map(RECORD_TO_USER_MAPPER)
                .toList();

        return ResultPage.createCriterialBasedList(list, criteria);
    }

    Collection<OrderField<?>> createOrderFields(final BaseCriteria criteria) {
        final List<CriteriaFieldSort> sortList = NullSafe
                .getOrElseGet(criteria, BaseCriteria::getSortList, Collections::emptyList);
        if (sortList.isEmpty()) {
            return Collections.singleton(STROOM_USER.DISPLAY_NAME);
        }

        return sortList.stream().map(sort -> {
            Field<?> field;
            if (UserFields.IS_GROUP.getFldName().equals(sort.getId())) {
                field = STROOM_USER.IS_GROUP;
            } else if (UserFields.NAME.getFldName().equals(sort.getId())) {
                field = STROOM_USER.NAME;
            } else if (UserFields.DISPLAY_NAME.getFldName().equals(sort.getId())) {
                field = STROOM_USER.DISPLAY_NAME;
            } else if (UserFields.FULL_NAME.getFldName().equals(sort.getId())) {
                field = STROOM_USER.FULL_NAME;
            } else {
                field = STROOM_USER.DISPLAY_NAME;
            }

            OrderField<?> orderField = field;
            if (sort.isDesc()) {
                orderField = field.desc();
            }

            return orderField;
        }).collect(Collectors.toList());
    }

    @Override
    public ResultPage<User> findUsersInGroup(final String groupUuid, final FindUserCriteria criteria) {
        final Condition condition = getUserCondition(criteria.getExpression());
        final Collection<OrderField<?>> orderFields = createOrderFields(criteria);
        final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);
        final int offset = JooqUtil.getOffset(criteria.getPageRequest());
        final List<User> list = JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select()
                        .from(STROOM_USER)
                        .join(STROOM_USER_GROUP)
                        .on(STROOM_USER.UUID.eq(STROOM_USER_GROUP.USER_UUID))
                        .where(STROOM_USER_GROUP.GROUP_UUID.eq(groupUuid))
                        .and(condition)
                        .and(STROOM_USER.ENABLED.eq(true))
                        .orderBy(orderFields)
                        .offset(offset)
                        .limit(limit)
                        .fetch())
                .stream()
                .map(RECORD_TO_USER_MAPPER)
                .toList();
        return ResultPage.createCriterialBasedList(list, criteria);
    }

    @Override
    public ResultPage<User> findGroupsForUser(final String userUuid, final FindUserCriteria criteria) {
        final Condition condition = expressionMapper.apply(criteria.getExpression());
        final Collection<OrderField<?>> orderFields = createOrderFields(criteria);
        final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);
        final int offset = JooqUtil.getOffset(criteria.getPageRequest());
        final List<User> list = JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select()
                        .from(STROOM_USER)
                        .join(STROOM_USER_GROUP)
                        .on(STROOM_USER.UUID.eq(STROOM_USER_GROUP.GROUP_UUID))
                        .where(STROOM_USER_GROUP.USER_UUID.eq(userUuid))
                        .and(STROOM_USER.ENABLED.eq(true))
                        .and(condition)
                        .orderBy(orderFields)
                        .offset(offset)
                        .limit(limit)
                        .fetch())
                .stream()
                .map(RECORD_TO_USER_MAPPER)
                .toList();
        return ResultPage.createCriterialBasedList(list, criteria);
    }

    @Override
    public void addUserToGroup(final String userUuid,
                               final String groupUuid) {
        JooqUtil.context(securityDbConnProvider, context -> context
                .insertInto(STROOM_USER_GROUP)
                .columns(STROOM_USER_GROUP.USER_UUID, STROOM_USER_GROUP.GROUP_UUID)
                .values(userUuid, groupUuid)
                .onDuplicateKeyUpdate()
                .set(STROOM_USER_GROUP.GROUP_UUID, STROOM_USER_GROUP.GROUP_UUID)
                .execute());
    }

    @Override
    public void removeUserFromGroup(final String userUuid,
                                    final String groupUuid) {
        JooqUtil.context(securityDbConnProvider, context -> context
                .deleteFrom(STROOM_USER_GROUP)
                .where(STROOM_USER_GROUP.USER_UUID.eq(userUuid))
                .and(STROOM_USER_GROUP.GROUP_UUID.eq(groupUuid))
                .execute());
    }

    Condition getUserCondition(final ExpressionOperator expression) {
        return expressionMapper.apply(expression);
    }
}
