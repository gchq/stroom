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
import stroom.util.logging.LogUtil;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.ResultPage;
import stroom.util.string.StringUtil;

import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.exception.IntegrityConstraintViolationException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private static final Map<String, Field<?>> SORT_FIELD_NAME_TO_FIELD_MAP = Map.of(
            UserFields.FIELD_IS_GROUP, STROOM_USER.IS_GROUP,
            UserFields.FIELD_UNIQUE_ID, STROOM_USER.NAME,
//            UserFields.FIELD_NAME, STROOM_USER.NAME,
            UserFields.FIELD_DISPLAY_NAME, STROOM_USER.DISPLAY_NAME,
            UserFields.FIELD_FULL_NAME, STROOM_USER.FULL_NAME,
            UserFields.FIELD_ENABLED, STROOM_USER.ENABLED);

    private static final Field<?> DEFAULT_SORT_FIELD = STROOM_USER.DISPLAY_NAME;
    private static final String DEFAULT_SORT_ID = UserFields.FIELD_DISPLAY_NAME;

    private static final Condition IGNORE_DELETED_CONDITION = STROOM_USER.DELETED.isFalse();

    private final SecurityDbConnProvider securityDbConnProvider;
    private final ExpressionMapper expressionMapper;

    @Inject
    public UserDaoImpl(final SecurityDbConnProvider securityDbConnProvider,
                       final ExpressionMapperFactory expressionMapperFactory) {
        this.securityDbConnProvider = securityDbConnProvider;

        expressionMapper = expressionMapperFactory.create()
                .map(UserFields.IS_GROUP, STROOM_USER.IS_GROUP, StringUtil::asBoolean)
                .map(UserFields.UNIQUE_ID, STROOM_USER.NAME, String::valueOf)
//                .map(UserFields.NAME, STROOM_USER.NAME, String::valueOf)
                .map(UserFields.DISPLAY_NAME, STROOM_USER.DISPLAY_NAME, String::valueOf)
                .map(UserFields.FULL_NAME, STROOM_USER.FULL_NAME, String::valueOf)
                .map(UserFields.ENABLED, STROOM_USER.ENABLED, StringUtil::asBoolean)
                .map(UserFields.PARENTS_OF, STROOM_USER_GROUP.USER_UUID, String::valueOf)
                .map(UserFields.CHILDREN_OF, STROOM_USER_GROUP.GROUP_UUID, String::valueOf);
    }

    @Override
    public User create(final User user) {
        return JooqUtil.contextResult(securityDbConnProvider, context ->
                create(context, user));
    }

    private User create(final DSLContext context, final User user) {
        user.setVersion(1);
        user.setUuid(UUID.randomUUID().toString());
        final Integer id = context
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
                        getDisplayNameOrSubjectId(user),
                        user.getFullName())
                .returning(STROOM_USER.ID)
                .fetchOne(STROOM_USER.ID);
        Objects.requireNonNull(id);
        user.setId(id);
        return user;
    }

    @Override
    public User tryCreate(final User user, final Consumer<User> onUserCreateAction) {
        try {
            LOGGER.debug("tryCreate user: {}", user);
            final User persisted = create(user);
            LOGGER.debug("Created new user: {}", user);
            onUserCreateAction.accept(persisted);
            return persisted;
        } catch (final IntegrityConstraintViolationException e) {
            LOGGER.debug(() -> LogUtil.message("User {} exists. " + LogUtil.exceptionMessage(e)
                                               + ". TRACE for stacktrace"));
            LOGGER.trace(e::getMessage, e);
        }

        if (user.isGroup()) {
            return getGroupByName(user.getSubjectId()).orElseThrow(() ->
                    new RuntimeException("Unable to create group"));
        }
        return getUserBySubjectId(user.getSubjectId()).orElseThrow(() ->
                new RuntimeException("Unable to create user"));
    }

    /**
     * Get user or group by their UUID.
     */
    @Override
    public Optional<User> getByUuid(final String uuid) {
        return JooqUtil.contextResult(securityDbConnProvider, context -> getByUuid(context, uuid));
    }

    /**
     * Get user or group by their UUID.
     */
    public Optional<User> getByUuid(final DSLContext context, final String uuid) {
        final Optional<User> optUser = context
                .select()
                .from(STROOM_USER)
                .where(STROOM_USER.UUID.eq(uuid))
                .and(IGNORE_DELETED_CONDITION)
                .fetchOptional()
                .map(RECORD_TO_USER_MAPPER);
        LOGGER.debug("getByUuid - uuid: {}, returning: {}", uuid, optUser);
        return optUser;
    }

    @Override
    public Optional<User> getUserBySubjectId(final String subjectId) {
        final Optional<User> optUser = JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select()
                        .from(STROOM_USER)
                        .where(STROOM_USER.NAME.eq(subjectId))
                        .and(IGNORE_DELETED_CONDITION)
                        .and(STROOM_USER.IS_GROUP.eq(false))
                        .fetchOptional())
                .map(RECORD_TO_USER_MAPPER);
        LOGGER.debug("getUserBySubjectId - subjectId: {}, returning: {}", subjectId, optUser);
        return optUser;
    }

    @Override
    public Optional<User> getGroupByName(final String groupName) {
        final Optional<User> optUser = JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select()
                        .from(STROOM_USER)
                        .where(STROOM_USER.NAME.eq(groupName))
                        .and(IGNORE_DELETED_CONDITION)
                        .and(STROOM_USER.IS_GROUP.eq(true))
                        .fetchOptional())
                .map(RECORD_TO_USER_MAPPER);
        LOGGER.debug("getGroupByName - groupName: {}, returning: {}", groupName, optUser);
        return optUser;
    }

    @Override
    public User update(final User user) {
        return JooqUtil.contextResult(securityDbConnProvider, context -> update(context, user));
    }

    private User update(final DSLContext context, final User user) {
        LOGGER.debug("update - user: {}", user);
        final int count = context
                .update(STROOM_USER)
                .set(STROOM_USER.VERSION, STROOM_USER.VERSION.plus(1))
                .set(STROOM_USER.UPDATE_TIME_MS, user.getUpdateTimeMs())
                .set(STROOM_USER.UPDATE_USER, user.getUpdateUser())
                .set(STROOM_USER.NAME, user.getSubjectId())
                .set(STROOM_USER.UUID, user.getUuid())
                .set(STROOM_USER.IS_GROUP, user.isGroup())
                .set(STROOM_USER.ENABLED, user.isEnabled())
                .set(STROOM_USER.DISPLAY_NAME, getDisplayNameOrSubjectId(user))
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

    private String getDisplayNameOrSubjectId(final User user) {
        Objects.requireNonNull(user);
        return Objects.requireNonNullElseGet(user.getDisplayName(), user::getSubjectId);
    }

    @Override
    public ResultPage<User> find(final FindUserCriteria criteria) {
        final List<String> fields = ExpressionUtil.fields(criteria.getExpression());

        final Condition condition = expressionMapper.apply(criteria.getExpression());
        final Collection<OrderField<?>> orderFields = createOrderFields(criteria);
        final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);
        final int offset = JooqUtil.getOffset(criteria.getPageRequest());

        LOGGER.debug("find - criteria: {}, fields: {}, condition: {}, limit: {}, offset: {}",
                criteria, fields, condition, limit, offset);

        final List<User> list;
        if (fields.contains(UserFields.PARENTS_OF.getFldName())) {
            list = getParentsOf(condition, orderFields, offset, limit);
        } else if (fields.contains(UserFields.CHILDREN_OF.getFldName())) {
            list = getChildrenOf(condition, orderFields, offset, limit);
        } else {
            list = getMatchingUsersOrGroups(condition, orderFields, offset, limit);
        }

        return ResultPage.createCriterialBasedList(list, criteria);
    }

    private List<User> getMatchingUsersOrGroups(final Condition condition,
                                                final Collection<OrderField<?>> orderFields,
                                                final int offset,
                                                final int limit) {
        LOGGER.debug("getMatchingUsersOrGroups - condition: {}", condition);
        final List<User> list;
        list = JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select()
                        .from(STROOM_USER)
                        .where(condition)
                        .and(IGNORE_DELETED_CONDITION)
                        .orderBy(orderFields)
                        .offset(offset)
                        .limit(limit)
                        .fetch())
                .stream()
                .map(RECORD_TO_USER_MAPPER)
                .toList();
        return list;
    }

    private List<User> getChildrenOf(final Condition condition,
                                     final Collection<OrderField<?>> orderFields,
                                     final int offset,
                                     final int limit) {
        // Get all direct children of a group
        LOGGER.debug("getChildrenOf - condition: {}", condition);
        final List<User> list;
        list = JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .selectDistinct()
                        .from(STROOM_USER)
                        .join(STROOM_USER_GROUP).on(STROOM_USER_GROUP.USER_UUID.eq(STROOM_USER.UUID))
                        .where(condition)
                        .and(IGNORE_DELETED_CONDITION)
                        .orderBy(orderFields)
                        .offset(offset)
                        .limit(limit)
                        .fetch())
                .stream()
                .map(RECORD_TO_USER_MAPPER)
                .toList();
        return list;
    }

    private List<User> getParentsOf(final Condition condition,
                                    final Collection<OrderField<?>> orderFields,
                                    final int offset,
                                    final int limit) {
        // Get all direct parents of a user/group
        LOGGER.debug("getParentsOf - condition: {}", condition);
        final List<User> list;
        list = JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .selectDistinct()
                        .from(STROOM_USER)
                        .join(STROOM_USER_GROUP).on(STROOM_USER_GROUP.GROUP_UUID.eq(STROOM_USER.UUID))
                        .where(condition)
                        .and(IGNORE_DELETED_CONDITION)
                        .orderBy(orderFields)
                        .offset(offset)
                        .limit(limit)
                        .fetch())
                .stream()
                .map(RECORD_TO_USER_MAPPER)
                .toList();
        return list;
    }

    Collection<OrderField<?>> createOrderFields(final BaseCriteria criteria) {
        final List<CriteriaFieldSort> sortList = new ArrayList<>(NullSafe
                .getOrElseGet(criteria, BaseCriteria::getSortList, Collections::emptyList));
        if (sortList.isEmpty()) {
            return Collections.singleton(DEFAULT_SORT_FIELD);
        }

        final ArrayList<OrderField<?>> sortFields = sortList.stream()
                .map(sort -> {
                    Field<?> field = SORT_FIELD_NAME_TO_FIELD_MAP.get(sort.getId());
                    if (field == null) {
                        field = DEFAULT_SORT_FIELD;
                    }
                    return asOrderField(field, sort.isDesc());
                })
                .collect(Collectors.toCollection(ArrayList::new));

        // Add displayName as a secondary sort
        if (!sortFields.contains(DEFAULT_SORT_FIELD)) {
            final OrderField<?> orderField = asOrderField(DEFAULT_SORT_FIELD, false);
            sortFields.add(orderField);
        }
        LOGGER.debug("createOrderFields - sortList: {}, sortFields: {}", sortList, sortFields);
        return sortFields;
    }

    private <T> OrderField<T> asOrderField(final Field<T> field, final boolean isDesc) {
        return isDesc
                ? field.desc()
                : field;
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
                        .and(IGNORE_DELETED_CONDITION)
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
                        .and(IGNORE_DELETED_CONDITION)
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
        final Integer count = JooqUtil.contextResult(securityDbConnProvider, context -> context
                .insertInto(STROOM_USER_GROUP)
                .columns(STROOM_USER_GROUP.USER_UUID, STROOM_USER_GROUP.GROUP_UUID)
                .values(userUuid, groupUuid)
                .onDuplicateKeyUpdate()
                .set(STROOM_USER_GROUP.GROUP_UUID, STROOM_USER_GROUP.GROUP_UUID)
                .execute());
        LOGGER.debug("addUserToGroup - userUuid: {}, groupUuid: {}, count: {}", userUuid, groupUuid, count);
    }

    @Override
    public void removeUserFromGroup(final String userUuid,
                                    final String groupUuid) {
        final Integer count = JooqUtil.contextResult(securityDbConnProvider, context -> context
                .deleteFrom(STROOM_USER_GROUP)
                .where(STROOM_USER_GROUP.USER_UUID.eq(userUuid))
                .and(STROOM_USER_GROUP.GROUP_UUID.eq(groupUuid))
                .execute());
        LOGGER.debug("addUserToGroup - userUuid: {}, groupUuid: {}, count: {}", userUuid, groupUuid, count);
    }

    @Override
    public boolean logicallyDelete(final String userUuid) {
        final Integer count = JooqUtil.contextResult(securityDbConnProvider, context -> context
                .update(STROOM_USER)
                .set(STROOM_USER.DELETED, true)
                .where(STROOM_USER.UUID.eq(userUuid))
                .execute());
        LOGGER.debug("logicallyDelete - userUuid: {}, count: {}", userUuid, count);
        return count > 0;
    }

    Condition getUserCondition(final ExpressionOperator expression) {
        return expressionMapper.apply(expression);
    }
}
