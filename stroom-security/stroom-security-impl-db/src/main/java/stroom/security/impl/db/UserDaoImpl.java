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

package stroom.security.impl.db;

import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.JooqUtil;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionUtil;
import stroom.security.impl.UserDao;
import stroom.security.impl.db.jooq.tables.PermissionDocCreate;
import stroom.security.shared.FindUserContext;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.security.shared.UserFields;
import stroom.util.exception.DataChangedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserInfo;
import stroom.util.shared.UserRef;
import stroom.util.string.StringUtil;

import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;
import org.jooq.exception.IntegrityConstraintViolationException;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.val;
import static stroom.security.impl.db.jooq.Tables.PERMISSION_APP;
import static stroom.security.impl.db.jooq.Tables.PERMISSION_DOC;
import static stroom.security.impl.db.jooq.Tables.PERMISSION_DOC_CREATE;
import static stroom.security.impl.db.jooq.Tables.STROOM_USER;
import static stroom.security.impl.db.jooq.Tables.STROOM_USER_ARCHIVE;
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

    private final SecurityDbConnProvider securityDbConnProvider;
    private final ExpressionMapper expressionMapper;
    // Have to hold the impls as we need to pass in the DSLContext which can't
    // be exposed on the iface
    private final AppPermissionDaoImpl appPermissionDaoImpl;
    private final DocumentPermissionDaoImpl documentPermissionDaoImpl;
    private final ApiKeyDaoImpl apiKeyDaoImpl;

    @Inject
    public UserDaoImpl(final SecurityDbConnProvider securityDbConnProvider,
                       final ExpressionMapperFactory expressionMapperFactory,
                       final AppPermissionDaoImpl appPermissionDaoImpl,
                       final DocumentPermissionDaoImpl documentPermissionDaoImpl,
                       final ApiKeyDaoImpl apiKeyDaoImpl) {
        this.securityDbConnProvider = securityDbConnProvider;
        this.appPermissionDaoImpl = appPermissionDaoImpl;
        this.documentPermissionDaoImpl = documentPermissionDaoImpl;
        this.apiKeyDaoImpl = apiKeyDaoImpl;

        this.expressionMapper = expressionMapperFactory.create()
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
        return JooqUtil.transactionResult(securityDbConnProvider, context ->
                create(context, user));
    }

    private User create(final DSLContext context, final User user) {
        user.setVersion(1);
        user.setUuid(UUID.randomUUID().toString());
        // DB requires a display name so default to the subjectId if there isn't one
        user.setDisplayName(getDisplayNameOrSubjectId(user));
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
                        user.getDisplayName(),
                        user.getFullName())
                .returning(STROOM_USER.ID)
                .fetchOne(STROOM_USER.ID);
        Objects.requireNonNull(id);
        user.setId(id);

        insertOrUpdateStroomUserArchiveRecord(context, user.getUuid());
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

    @Override
    public Optional<User> getByUuid(final String uuid, final String currentUserUuid, final FindUserContext context) {
        return JooqUtil.contextResult(securityDbConnProvider, ctx -> {
            Condition condition = STROOM_USER.UUID.eq(uuid);
            condition = addRelatedUserCondition(condition, currentUserUuid, context);
            final Optional<User> optUser = get(ctx, condition);
            LOGGER.debug("getByUuid - uuid: {}, returning: {}", uuid, optUser);
            return optUser;
        });
    }

    /**
     * Get user or group by their UUID.
     */
    public Optional<User> getByUuid(final DSLContext context,
                                    final String uuid) {
        final Optional<User> optUser = get(context, STROOM_USER.UUID.eq(uuid));
        LOGGER.debug("getByUuid - uuid: {}, returning: {}", uuid, optUser);
        return optUser;
    }

    private Optional<User> get(final DSLContext context,
                               final Condition condition) {
        return context
                .select()
                .from(STROOM_USER)
                .where(condition)
                .fetchOptional()
                .map(RECORD_TO_USER_MAPPER);
    }

    @Override
    public Optional<User> getUserBySubjectId(final String subjectId) {
        final Optional<User> optUser = JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select()
                        .from(STROOM_USER)
                        .where(STROOM_USER.NAME.eq(subjectId))
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
                        .and(STROOM_USER.IS_GROUP.eq(true))
                        .fetchOptional())
                .map(RECORD_TO_USER_MAPPER);
        LOGGER.debug("getGroupByName - groupName: {}, returning: {}", groupName, optUser);
        return optUser;
    }

    @Override
    public User update(final User user) {
        return JooqUtil.transactionResultWithOptimisticLocking(
                securityDbConnProvider,
                context ->
                        update(context, user));
    }

    @Override
    public void copyGroupsAndPermissions(final String fromUserUuid, final String toUserUuid) {
        JooqUtil.transaction(securityDbConnProvider, txnContext -> {

            LOGGER.debug("Copy groups and permissions from: {} to: {}", fromUserUuid, toUserUuid);

            final int numberGroups = txnContext.insertInto(STROOM_USER_GROUP)
                    .columns(STROOM_USER_GROUP.USER_UUID, STROOM_USER_GROUP.GROUP_UUID)
                    .select(txnContext.select(val(toUserUuid), STROOM_USER_GROUP.GROUP_UUID)
                            .from(STROOM_USER_GROUP)
                            .where(STROOM_USER_GROUP.USER_UUID.eq(fromUserUuid)))
                    .onDuplicateKeyUpdate()
                    .set(STROOM_USER_GROUP.USER_UUID, val(toUserUuid))
                    .execute();

            final int numberAppPermissions = txnContext.insertInto(PERMISSION_APP)
                    .columns(PERMISSION_APP.USER_UUID, PERMISSION_APP.PERMISSION_ID)
                    .select(txnContext.select(val(toUserUuid), PERMISSION_APP.PERMISSION_ID)
                            .from(PERMISSION_APP)
                            .where(PERMISSION_APP.USER_UUID.eq(fromUserUuid)))
                    .onDuplicateKeyUpdate()
                    .set(PERMISSION_APP.USER_UUID, val(toUserUuid))
                    .execute();

            final int numberDocPermissions = txnContext.insertInto(PERMISSION_DOC)
                    .columns(PERMISSION_DOC.USER_UUID, PERMISSION_DOC.DOC_UUID, PERMISSION_DOC.PERMISSION_ID)
                    .select(txnContext.select(val(toUserUuid), PERMISSION_DOC.DOC_UUID, PERMISSION_DOC.PERMISSION_ID)
                            .from(PERMISSION_DOC)
                            .where(PERMISSION_DOC.USER_UUID.eq(fromUserUuid)))
                    .onDuplicateKeyUpdate()
                    .set(PERMISSION_DOC.USER_UUID, val(toUserUuid))
                    .execute();

            final int numberDocCreatePermissions = txnContext.insertInto(PERMISSION_DOC_CREATE)
                    .columns(PermissionDocCreate.PERMISSION_DOC_CREATE.DOC_UUID,
                            PermissionDocCreate.PERMISSION_DOC_CREATE.USER_UUID,
                            PermissionDocCreate.PERMISSION_DOC_CREATE.DOC_TYPE_ID)
                    .select(txnContext.select(PERMISSION_DOC_CREATE.DOC_UUID, val(toUserUuid),
                            PERMISSION_DOC_CREATE.DOC_TYPE_ID)
                            .from(PERMISSION_DOC_CREATE)
                            .where(PERMISSION_DOC_CREATE.USER_UUID.eq(fromUserUuid)))
                    .onDuplicateKeyUpdate()
                    .set(PERMISSION_DOC_CREATE.USER_UUID, val(toUserUuid))
                    .execute();

            LOGGER.debug("""
                            Groups and permissions copied.
                            Groups: {}
                            App Permissions: {}
                            Doc Permissions: {}
                            Doc Create Permissions: {}""",
                    numberGroups, numberAppPermissions, numberDocPermissions, numberDocCreatePermissions);

        });
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

        insertOrUpdateStroomUserArchiveRecord(context, user.getUuid());

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

        Condition condition = expressionMapper.apply(criteria.getExpression());
        if (FindUserContext.ANNOTATION_ASSIGNMENT.equals(criteria.getContext()) ||
            FindUserContext.RUN_AS.equals(criteria.getContext())) {
            condition = condition.and(STROOM_USER.ENABLED.isTrue());
        }

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

    @Override
    public ResultPage<User> findRelatedUsers(final String currentUserUuid, final FindUserCriteria criteria) {
        final List<String> fields = ExpressionUtil.fields(criteria.getExpression());

        Condition condition = expressionMapper.apply(criteria.getExpression());
        condition = addRelatedUserCondition(condition, currentUserUuid, criteria.getContext());

        final Collection<OrderField<?>> orderFields = createOrderFields(criteria);
        final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);
        final int offset = JooqUtil.getOffset(criteria.getPageRequest());

        LOGGER.debug("findRelatedUsers - criteria: {}, fields: {}, condition: {}, limit: {}, offset: {}",
                criteria, fields, condition, limit, offset);

        final List<User> list = getMatchingUsersOrGroups(condition, orderFields, offset, limit);
        return ResultPage.createCriterialBasedList(list, criteria);
    }

    private Condition addRelatedUserCondition(final Condition condition,
                                              final String currentUserUuid,
                                              final FindUserContext context) {
        if (FindUserContext.ANNOTATION_ASSIGNMENT.equals(context)) {
            // Get immediate parent groups for the supplied user.
            final var selectParentGroupUuids = DSL
                    .selectDistinct(STROOM_USER_GROUP.GROUP_UUID)
                    .from(STROOM_USER_GROUP)
                    .where(STROOM_USER_GROUP.USER_UUID.eq(currentUserUuid));
            // Get siblings users for all parent groups (this will obviously include the supplied user).
            final var selectSiblingUsers = DSL
                    .selectDistinct(STROOM_USER_GROUP.USER_UUID)
                    .from(STROOM_USER_GROUP)
                    .where(STROOM_USER_GROUP.GROUP_UUID.in(selectParentGroupUuids));
            return condition
                    .and(STROOM_USER.ENABLED.isTrue())
                    .and(STROOM_USER.UUID.in(selectParentGroupUuids).or(STROOM_USER.UUID.in(selectSiblingUsers)));

        } else if (FindUserContext.RUN_AS.equals(context)) {
            // Get immediate parent groups for the supplied user.
            final var selectParentGroupUuids = DSL
                    .selectDistinct(STROOM_USER_GROUP.GROUP_UUID)
                    .from(STROOM_USER_GROUP)
                    .where(STROOM_USER_GROUP.USER_UUID.eq(currentUserUuid));
            return condition
                    .and(STROOM_USER.ENABLED.isTrue())
                    .and(STROOM_USER.UUID.in(selectParentGroupUuids).or(STROOM_USER.UUID.eq(currentUserUuid)));
        }

        return condition
                .and(STROOM_USER.ENABLED.isTrue())
                .and(STROOM_USER.UUID.eq(currentUserUuid));
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
        return createOrderFields(criteria, null);
    }

    /**
     * @param additionalFieldMappings Any additional field name to {@link Field} mappings. Can be null.
     */
    Collection<OrderField<?>> createOrderFields(final BaseCriteria criteria,
                                                final Map<String, Field<?>> additionalFieldMappings) {

        final Map<String, Field<?>> nameToFieldMap;
        if (additionalFieldMappings != null) {
            nameToFieldMap = new HashMap<>(SORT_FIELD_NAME_TO_FIELD_MAP);
            nameToFieldMap.putAll(additionalFieldMappings);
        } else {
            nameToFieldMap = SORT_FIELD_NAME_TO_FIELD_MAP;
        }

        final List<CriteriaFieldSort> sortList = NullSafe.mutableList(
                NullSafe.get(criteria, BaseCriteria::getSortList));
        if (sortList.isEmpty()) {
            return Collections.singleton(DEFAULT_SORT_FIELD);
        }

        final ArrayList<OrderField<?>> sortFields = sortList.stream()
                .map(sort -> {
                    Field<?> field = nameToFieldMap.get(sort.getId());
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
        try {
            final Integer insertCount = JooqUtil.contextResult(securityDbConnProvider, context -> context
                    .insertInto(STROOM_USER_GROUP)
                    .columns(STROOM_USER_GROUP.USER_UUID, STROOM_USER_GROUP.GROUP_UUID)
                    .values(userUuid, groupUuid)
                    .onDuplicateKeyUpdate()
                    .set(STROOM_USER_GROUP.GROUP_UUID, STROOM_USER_GROUP.GROUP_UUID)
                    .execute());
            LOGGER.debug("addUserToGroup - userUuid: {}, groupUuid: {}, count: {}",
                    userUuid, groupUuid, insertCount);
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "Error adding user to group - userUuid: {}, groupUuid: {}", userUuid, groupUuid), e);
        }
    }

    @Override
    public void removeUserFromGroup(final String userUuid,
                                    final String groupUuid) {
        try {
            final Integer updateCount = JooqUtil.contextResult(securityDbConnProvider, context -> context
                    .deleteFrom(STROOM_USER_GROUP)
                    .where(STROOM_USER_GROUP.USER_UUID.eq(userUuid))
                    .and(STROOM_USER_GROUP.GROUP_UUID.eq(groupUuid))
                    .execute());
            LOGGER.debug("removeUserFromGroup - userUuid: {}, groupUuid: {}, count: {}",
                    userUuid, groupUuid, updateCount);
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "Error removing user from group - userUuid: {}, groupUuid: {}", userUuid, groupUuid), e);
        }
    }

    /**
     * IMPORTANT - This must be called after any insert/update to STROOM_USER (and
     * as part of the same txn) to ensure STROOM_USER_ARCHIVE is up-to-date.
     * NOT to be called after a delete statement as we want to preserve the details
     * of deleted users.
     */
    private void insertOrUpdateStroomUserArchiveRecord(final DSLContext context,
                                                       final String userUuid) {
        Objects.requireNonNull(userUuid);

        try {
            final int changeCount = context.insertInto(STROOM_USER_ARCHIVE,
                            STROOM_USER_ARCHIVE.UUID,
                            STROOM_USER_ARCHIVE.NAME,
                            STROOM_USER_ARCHIVE.DISPLAY_NAME,
                            STROOM_USER_ARCHIVE.FULL_NAME,
                            STROOM_USER_ARCHIVE.IS_GROUP)
                    .select(context.select(
                                    STROOM_USER.UUID,
                                    STROOM_USER.NAME,
                                    STROOM_USER.DISPLAY_NAME,
                                    STROOM_USER.FULL_NAME,
                                    STROOM_USER.IS_GROUP)
                            .from(STROOM_USER)
                            .where(STROOM_USER.UUID.eq(userUuid)))
                    .onDuplicateKeyUpdate()
                    .set(STROOM_USER_ARCHIVE.NAME, STROOM_USER.NAME)
                    .set(STROOM_USER_ARCHIVE.DISPLAY_NAME, STROOM_USER.DISPLAY_NAME)
                    .set(STROOM_USER_ARCHIVE.FULL_NAME, STROOM_USER.FULL_NAME)
                    .set(STROOM_USER_ARCHIVE.IS_GROUP, STROOM_USER.IS_GROUP)
                    .execute();

            LOGGER.debug("insertOrUpdateStroomUserArchiveRecord - changeCount: {} for userUuid: {}",
                    changeCount, userUuid);
        } catch (final DataAccessException e) {
            throw new RuntimeException(LogUtil.message("Error upserting archive record for userUuid {}", userUuid), e);
        }
    }

    @Override
    public boolean deleteUser(final UserRef userRef) {
        Objects.requireNonNull(userRef);
        final Integer userCount = JooqUtil.transactionResult(securityDbConnProvider, txnContext -> {
            final String userInfoStr = userRef.toInfoString();
            final String userUuid = userRef.getUuid();

            final User user = getByUuid(txnContext, userUuid)
                    .orElseThrow(() -> new RuntimeException(LogUtil.message(
                            "User {} does not exist", userInfoStr)));

            // First ensure the stroom_user_archive record is up-to-date before we delete
            // the user, so we have the latest picture of the various names of the user.
            insertOrUpdateStroomUserArchiveRecord(txnContext, userUuid);

            // Now remove the user from any groups it is a member of
            int count = txnContext.deleteFrom(STROOM_USER_GROUP)
                    .where(STROOM_USER_GROUP.USER_UUID.eq(userUuid))
                    .execute();
            LOGGER.debug("Removed {} group memberships for user {}", count, userInfoStr);

            count = appPermissionDaoImpl.deletePermissionsForUser(txnContext, userUuid);
            LOGGER.debug("Removed {} app permission records for user {}", count, userInfoStr);

            count = documentPermissionDaoImpl.deletePermissionsForUser(txnContext, userUuid);
            LOGGER.debug("Removed {} doc permission records for user {}", count, userInfoStr);

            count = apiKeyDaoImpl.deleteByOwner(txnContext, userUuid);
            LOGGER.debug("Removed {} API key records for user {}", count, userInfoStr);

            count = txnContext.deleteFrom(STROOM_USER)
                    .where(STROOM_USER.UUID.eq(userUuid))
                    .execute();
            LOGGER.debug("Deleted {} record(s) for user {}", count, userInfoStr);

            if (count == 0 || count > 1) {
                throw new RuntimeException(LogUtil.message(
                        "Deleted {} records for user {} but expected to delete exactly one.", count, userInfoStr));
            }

            LOGGER.info("Deleted user - subjectId: '{}', displayName: '{}', userUuid: {}",
                    user.getSubjectId(), user.getDisplayName(), userUuid);
            return count;
        });
        return userCount > 0;
    }

    @Override
    public Optional<UserInfo> getUserInfoByUserUuid(final String userUuid) {
        Objects.requireNonNull(userUuid);
        // Left join to stroom_user, so we can get the enabled state
        return JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select(
                                STROOM_USER_ARCHIVE.NAME,
                                STROOM_USER_ARCHIVE.DISPLAY_NAME,
                                STROOM_USER_ARCHIVE.FULL_NAME,
                                STROOM_USER_ARCHIVE.IS_GROUP,
                                STROOM_USER.ENABLED,
                                STROOM_USER.ID)
                        .from(STROOM_USER_ARCHIVE)
                        .leftOuterJoin(STROOM_USER).on(STROOM_USER_ARCHIVE.UUID.eq(STROOM_USER.UUID))
                        .where(STROOM_USER_ARCHIVE.UUID.eq(userUuid))
                        .fetchOptional())
                .map(rec -> UserInfo.builder()
                        .uuid(userUuid)
                        .subjectId(rec.get(STROOM_USER_ARCHIVE.NAME))
                        .displayName(rec.get(STROOM_USER_ARCHIVE.DISPLAY_NAME))
                        .fullName(rec.get(STROOM_USER_ARCHIVE.FULL_NAME))
                        .group(rec.get(STROOM_USER_ARCHIVE.IS_GROUP))
                        .enabled(Objects.requireNonNullElse(rec.get(STROOM_USER.ENABLED), false))
                        .deleted(rec.get(STROOM_USER.ID) == null)
                        .build());
    }

    Condition getUserCondition(final ExpressionOperator expression) {
        return expressionMapper.apply(expression);
    }
}
