package stroom.security.impl.db;

import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.JooqUtil;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
import stroom.security.impl.UserDao;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.security.shared.UserFields;
import stroom.util.NullSafe;
import stroom.util.exception.DataChangedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static stroom.security.impl.db.jooq.Tables.STROOM_USER;
import static stroom.security.impl.db.jooq.Tables.STROOM_USER_GROUP;

public class UserDaoImpl implements UserDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserDaoImpl.class);

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
        user.setEnabled(record.get(STROOM_USER.ENABLED));
        return user;
    };

    private final SecurityDbConnProvider securityDbConnProvider;
    private final ExpressionMapper expressionMapper;

    @Inject
    public UserDaoImpl(final SecurityDbConnProvider securityDbConnProvider,
                       final ExpressionMapperFactory expressionMapperFactory) {
        this.securityDbConnProvider = securityDbConnProvider;

        expressionMapper = expressionMapperFactory.create();
        expressionMapper.map(UserFields.IS_GROUP, STROOM_USER.IS_GROUP, Boolean::valueOf);
        expressionMapper.map(UserFields.NAME, STROOM_USER.NAME, String::valueOf);
        expressionMapper.map(UserFields.DISPLAY_NAME, STROOM_USER.DISPLAY_NAME, String::valueOf);
        expressionMapper.map(UserFields.FULL_NAME, STROOM_USER.FULL_NAME, String::valueOf);
        expressionMapper.map(UserFields.PARENT_GROUP, STROOM_USER_GROUP.USER_UUID, String::valueOf);
        expressionMapper.map(UserFields.GROUP_CONTAINS, STROOM_USER_GROUP.GROUP_UUID, String::valueOf);
    }

    @Override
    public User create(final User user) {
        return JooqUtil.contextResult(securityDbConnProvider, context -> create(context, user));
    }

    private User create(final DSLContext context, final User user) {
        user.setVersion(1);
        user.setUuid(UUID.randomUUID().toString());
        final int id = context
                .insertInto(STROOM_USER)
                .columns(STROOM_USER.VERSION,
                        STROOM_USER.CREATE_TIME_MS,
                        STROOM_USER.CREATE_USER,
                        STROOM_USER.UPDATE_TIME_MS,
                        STROOM_USER.UPDATE_USER,
                        STROOM_USER.NAME,
                        STROOM_USER.UUID,
                        STROOM_USER.IS_GROUP,
                        STROOM_USER.ENABLED,
                        STROOM_USER.DISPLAY_NAME,
                        STROOM_USER.FULL_NAME)
                .values(user.getVersion(),
                        user.getCreateTimeMs(),
                        user.getCreateUser(),
                        user.getUpdateTimeMs(),
                        user.getUpdateUser(),
                        user.getSubjectId(),
                        user.getUuid(),
                        user.isGroup(),
                        user.isEnabled(),
                        user.getDisplayName(),
                        user.getFullName())
                .returning(STROOM_USER.ID)
                .fetchOne(STROOM_USER.ID);
        user.setId(id);
        return user;
    }

    @Override
    public User tryCreate(final User user, final Consumer<User> onUserCreateAction) {
        try {
            final User persisted = create(user);
            onUserCreateAction.accept(persisted);
            return persisted;
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
        }

        if (user.isGroup()) {
            return getGroupByName(user.getSubjectId()).orElseThrow(() ->
                    new RuntimeException("Unable to create group"));
        }
        return getUserBySubjectId(user.getSubjectId()).orElseThrow(() ->
                new RuntimeException("Unable to create user"));
    }

    @Override
    public Optional<User> getByUuid(final String uuid) {
        return JooqUtil.contextResult(securityDbConnProvider, context -> getByUuid(context, uuid));
    }

    public Optional<User> getByUuid(final DSLContext context, final String uuid) {
        return context
                .select()
                .from(STROOM_USER)
                .where(STROOM_USER.UUID.eq(uuid))
                .fetchOptional()
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
        return JooqUtil.contextResult(securityDbConnProvider, context -> update(context, user));
    }

    private User update(final DSLContext context, final User user) {
        final int count = context
                .update(STROOM_USER)
                .set(STROOM_USER.VERSION, STROOM_USER.VERSION.plus(1))
                .set(STROOM_USER.UPDATE_TIME_MS, user.getUpdateTimeMs())
                .set(STROOM_USER.UPDATE_USER, user.getUpdateUser())
                .set(STROOM_USER.NAME, user.getSubjectId())
                .set(STROOM_USER.UUID, user.getUuid())
                .set(STROOM_USER.IS_GROUP, user.isGroup())
                .set(STROOM_USER.ENABLED, user.isEnabled())
                .set(STROOM_USER.DISPLAY_NAME, user.getDisplayName())
                .set(STROOM_USER.FULL_NAME, user.getFullName())
                .where(STROOM_USER.ID.eq(user.getId()))
                .and(STROOM_USER.VERSION.eq(user.getVersion()))
                .execute();

        if (count == 0) {
            throw new DataChangedException("Failed to update user, " +
                    "it may have been updated by another user or deleted");
        }

        return getByUuid(context, user.getUuid()).orElseThrow(() ->
                new RuntimeException("Error fetching updated user"));
    }

    @Override
    public ResultPage<User> find(final FindUserCriteria criteria) {
        final List<String> fields = ExpressionUtil.fields(criteria.getExpression());

        final Condition condition = expressionMapper.apply(criteria.getExpression());
        final Collection<OrderField<?>> orderFields = createOrderFields(criteria);
        final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);
        final int offset = JooqUtil.getOffset(criteria.getPageRequest());

        List<User> list = null;
        if (fields.contains(UserFields.PARENT_GROUP.getFldName())) {
            list = JooqUtil.contextResult(securityDbConnProvider, context -> context
                            .selectDistinct()
                            .from(STROOM_USER)
                            .join(STROOM_USER_GROUP).on(STROOM_USER_GROUP.GROUP_UUID.eq(STROOM_USER.UUID))
                            .where(condition)
                            .and(STROOM_USER.ENABLED.eq(true))
                            .orderBy(orderFields)
                            .offset(offset)
                            .limit(limit)
                            .fetch())
                    .stream()
                    .map(RECORD_TO_USER_MAPPER)
                    .toList();

        } else if (fields.contains(UserFields.GROUP_CONTAINS.getFldName())) {
            list = JooqUtil.contextResult(securityDbConnProvider, context -> context
                            .selectDistinct()
                            .from(STROOM_USER)
                            .join(STROOM_USER_GROUP).on(STROOM_USER_GROUP.USER_UUID.eq(STROOM_USER.UUID))
                            .where(condition)
                            .and(STROOM_USER.ENABLED.eq(true))
                            .orderBy(orderFields)
                            .offset(offset)
                            .limit(limit)
                            .fetch())
                    .stream()
                    .map(RECORD_TO_USER_MAPPER)
                    .toList();

        } else {
            list = JooqUtil.contextResult(securityDbConnProvider, context -> context
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
        }

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
