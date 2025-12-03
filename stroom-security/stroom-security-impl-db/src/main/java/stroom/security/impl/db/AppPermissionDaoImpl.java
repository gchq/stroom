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

import stroom.db.util.JooqUtil;
import stroom.security.impl.AppPermissionDao;
import stroom.security.impl.AppPermissionIdDao;
import stroom.security.impl.db.jooq.tables.PermissionApp;
import stroom.security.impl.db.jooq.tables.StroomUser;
import stroom.security.impl.db.jooq.tables.StroomUserGroup;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppUserPermissions;
import stroom.security.shared.FetchAppUserPermissionsRequest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.util.string.StringUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.jooq.CommonTableExpression;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Name;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Record8;
import org.jooq.Select;
import org.jooq.Table;
import org.jooq.exception.DataAccessException;
import org.jooq.exception.DataTypeException;
import org.jooq.impl.DSL;
import org.jooq.types.UByte;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static stroom.security.impl.db.jooq.tables.PermissionApp.PERMISSION_APP;
import static stroom.security.impl.db.jooq.tables.StroomUser.STROOM_USER;
import static stroom.security.impl.db.jooq.tables.StroomUserGroup.STROOM_USER_GROUP;

public class AppPermissionDaoImpl implements AppPermissionDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AppPermissionDaoImpl.class);
    private static final Field<String> COMMA = DSL.val(",");
    private static final String CONTAINS_NUMBER_PATTERN = "[0-9]";

    private final SecurityDbConnProvider securityDbConnProvider;
    private final AppPermissionIdDao appPermissionIdDao;
    private final Provider<UserDaoImpl> userDaoProvider;

    @Inject
    public AppPermissionDaoImpl(final SecurityDbConnProvider securityDbConnProvider,
                                final AppPermissionIdDao appPermissionIdDao,
                                final Provider<UserDaoImpl> userDaoProvider) {
        this.securityDbConnProvider = securityDbConnProvider;
        this.appPermissionIdDao = appPermissionIdDao;
        this.userDaoProvider = userDaoProvider;
    }

    @Override
    public Set<AppPermission> getPermissionsForUser(final String userUuid) {
        final EnumSet<AppPermission> appPermissions = JooqUtil.contextResult(
                        securityDbConnProvider, context -> context
                                .select()
                                .from(PERMISSION_APP)
                                .where(PERMISSION_APP.USER_UUID.eq(userUuid))
                                .fetch())
                .stream()
                .map(r -> {
                    final int permissionId = r.get(PERMISSION_APP.PERMISSION_ID).intValue();
                    final String permissionName = appPermissionIdDao.get(permissionId);
                    return AppPermission.getPermissionForName(permissionName);
                })
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AppPermission.class)));

        return Collections.unmodifiableSet(appPermissions);
    }

    @Override
    public void addPermission(final String userUuid, final AppPermission permission) {
        final UByte permissionId = UByte.valueOf(appPermissionIdDao.getOrCreateId(permission.getDisplayValue()));
        JooqUtil.context(securityDbConnProvider, context -> context
                .insertInto(PERMISSION_APP)
                .columns(PERMISSION_APP.USER_UUID, PERMISSION_APP.PERMISSION_ID)
                .values(userUuid, permissionId)
                .onDuplicateKeyUpdate()
                .set(PERMISSION_APP.ID, PERMISSION_APP.ID)
                .execute());
    }

    @Override
    public void removePermission(final String userUuid, final AppPermission permission) {
        final UByte permissionId = UByte.valueOf(appPermissionIdDao.getOrCreateId(permission.getDisplayValue()));
        JooqUtil.context(securityDbConnProvider, context -> context
                .deleteFrom(PERMISSION_APP)
                .where(PERMISSION_APP.USER_UUID.eq(userUuid))
                .and(PERMISSION_APP.PERMISSION_ID.eq(permissionId)).execute());
    }

    /**
     * Get application permissions for users plus any that they inherit from their group membership.
     *
     * @param request The Fetch request.
     * @return A result page of user permissions.
     */
    @Override
    public ResultPage<AppUserPermissions> fetchAppUserPermissions(final FetchAppUserPermissionsRequest request) {
        Objects.requireNonNull(request, "Null request");
        final UserDaoImpl userDao = userDaoProvider.get();

        final int offset = JooqUtil.getOffset(request.getPageRequest());
        final int limit = JooqUtil.getLimit(request.getPageRequest(), true);

        final Collection<OrderField<?>> orderFields = userDao.createOrderFields(request);

        final List<Condition> conditions = new ArrayList<>();
        conditions.add(userDao.getUserCondition(request.getExpression()));
        if (request.getUserRef() != null) {
            conditions.add(STROOM_USER.UUID.eq(request.getUserRef().getUuid()));
        }

        final StroomUser su = STROOM_USER.as("su");
        final StroomUserGroup sug = STROOM_USER_GROUP.as("sug");
        final PermissionApp pa = PERMISSION_APP.as("pa");
        final PermissionApp paParent = PERMISSION_APP.as("pa_parent");

        final Name cte = DSL.name("cte");
        final Field<String> cteUserUuid = DSL.field(cte.append("user_uuid"), String.class);
        final Field<String> cteGroupUuid = DSL.field(cte.append("group_uuid"), String.class);
        final Field<String> ctePerms = DSL.field(cte.append("perms"), String.class);
        final Field<String> cteInheritedPerms = DSL.field(cte.append("inherited_perms"), String.class);

        final List<AppUserPermissions> list = JooqUtil.contextResult(securityDbConnProvider, context -> {

            // Create a select to group permissions and parent permissions.
            final Select<?> select = context
                    .select(
                            su.UUID.as("user_uuid"),
                            sug.GROUP_UUID,
                            DSL.groupConcatDistinct(DSL.ifnull(pa.PERMISSION_ID, ""))
                                    .as("perms"),
                            DSL.groupConcatDistinct(DSL.ifnull(paParent.PERMISSION_ID, ""))
                                    .as("parent_perms"))
                    .from(su)
                    .leftOuterJoin(sug)
                    .on(sug.USER_UUID.eq(su.UUID))
                    .leftOuterJoin(pa)
                    .on(pa.USER_UUID.eq(su.UUID))
                    .leftOuterJoin(paParent)
                    .on(paParent.USER_UUID.eq(sug.GROUP_UUID))
                    .groupBy(su.UUID, sug.GROUP_UUID);

            final Table<?> v = select.asTable("v");
            final Field<String> vUserUuid = v.field("user_uuid", String.class);
            final Field<String> vGroupUuid = v.field("group_uuid", String.class);
            final Field<String> vPerms = v.field("perms", String.class);
            final Field<String> vParentPerms = v.field("parent_perms", String.class);
            assert vUserUuid != null;
            assert vGroupUuid != null;
            assert vPerms != null;
            assert vParentPerms != null;

            // Create a view to recursively aggregate parent permissions for users and groups so we can see all
            // inherited permissions.
            // Create common table expression to apply `with recursive`.
            final CommonTableExpression<?> commonTableExpression = cte
                    .as(context
                            .select(
                                    vUserUuid,
                                    vGroupUuid,
                                    vPerms,
                                    vParentPerms.as("inherited_perms"))
                            .from(v)
                            .unionAll(
                                    context.select(
                                                    vUserUuid,
                                                    vGroupUuid,
                                                    vPerms,
                                                    DSL.concat(
                                                            DSL.ifnull(cteInheritedPerms, ""),
                                                            COMMA,
                                                            DSL.ifnull(vParentPerms, "")))
                                            .from(DSL.table(cte))
                                            .join(v).on(vGroupUuid.eq(cteUserUuid))));

            // Apply `with recursive`
            final Table<?> recursive = context
                    .withRecursive(commonTableExpression)
                    .select(
                            cteUserUuid,
                            cteGroupUuid,
                            DSL.groupConcatDistinct(DSL.ifnull(ctePerms, "")).as("perms"),
                            DSL.groupConcatDistinct(DSL.ifnull(cteInheritedPerms, ""))
                                    .as("inherited_perms"))
                    .from(commonTableExpression)
                    .groupBy(cteUserUuid, cteGroupUuid)
                    .asTable();

            final Field<String> recUserUuid = recursive.field("user_uuid", String.class);
            final Field<String> recGroupUuid = recursive.field("group_uuid", String.class);
            final Field<String> recPerms = recursive.field("perms", String.class);
            final Field<String> recInheritedPerms = recursive.field("inherited_perms", String.class);
            assert recUserUuid != null;
            assert recGroupUuid != null;
            assert recPerms != null;
            assert recInheritedPerms != null;

            // Add additional conditions if we want to just show effective or explicit permissions.
            switch (request.getShowLevel()) {
//                case SHOW_EFFECTIVE -> conditions.add(recPerms.isNotNull()
//                        .or(recInheritedPerms.isNotNull()));
//                case SHOW_EXPLICIT -> conditions.add(recPerms.isNotNull());

                // Because we have treated nulls as empty strings, we may have a value like ',,,,,'
                // so use presence of numbers to indicate presence of a perm
                case SHOW_EFFECTIVE -> conditions.add(recPerms.likeRegex(CONTAINS_NUMBER_PATTERN)
                        .or(recInheritedPerms.likeRegex(CONTAINS_NUMBER_PATTERN)));
                case SHOW_EXPLICIT -> conditions.add(recPerms.likeRegex(CONTAINS_NUMBER_PATTERN));
            }

            // Join recursive select to user.
            try {
                final var sql = context
                        .select(STROOM_USER.UUID,
                                STROOM_USER.NAME,
                                STROOM_USER.DISPLAY_NAME,
                                STROOM_USER.FULL_NAME,
                                STROOM_USER.IS_GROUP,
                                STROOM_USER.ENABLED,
                                DSL.groupConcatDistinct(DSL.ifnull(recPerms, ""))
                                        .as(ctePerms.getName()),
                                DSL.groupConcatDistinct(DSL.ifnull(recInheritedPerms, ""))
                                        .as(cteInheritedPerms.getName()))
                        .from(STROOM_USER)
                        .join(recursive).on(recUserUuid.eq(STROOM_USER.UUID))
                        .where(conditions)
                        .groupBy(
                                STROOM_USER.UUID,
                                STROOM_USER.NAME,
                                STROOM_USER.DISPLAY_NAME,
                                STROOM_USER.FULL_NAME,
                                STROOM_USER.IS_GROUP,
                                STROOM_USER.ENABLED)
                        .orderBy(orderFields)
                        .offset(offset)
                        .limit(limit);

                LOGGER.debug("fetchAppUserPermissions sql:\n{}", sql);
                return sql.fetch();
            } catch (final DataAccessException e) {
                throw new RuntimeException(e);
            }

        }).map((final Record8<?, ?, ?, ?, ?, ?, ?, ?> r) -> {
            try {
                final UserRef userRef = recordToUserRef(r);
                final String perms = r.get(ctePerms.getName(), String.class);
                final String inheritedPerms = r.get(cteInheritedPerms.getName(), String.class);
                final Set<AppPermission> permissions = getAppPermissionSet(perms);
                final Set<AppPermission> inherited = getAppPermissionSet(inheritedPerms);
                return new AppUserPermissions(userRef, permissions, inherited);
            } catch (final IllegalArgumentException | DataTypeException e) {
                throw new RuntimeException(e);
            }
        });

        return ResultPage.createCriterialBasedList(list, request);
    }

    int deletePermissionsForUser(final DSLContext context, final String userUuid) {
        Objects.requireNonNull(userUuid);
        final int delCount = context.deleteFrom(PERMISSION_APP)
                .where(PERMISSION_APP.USER_UUID.eq(userUuid))
                .execute();
        LOGGER.debug(() -> LogUtil.message("Deleted {} {} records for userUuid {}",
                delCount, PERMISSION_APP.getName(), userUuid));
        return delCount;
    }

    private Set<AppPermission> getAppPermissionSet(final String perms) {
        if (NullSafe.isBlankString(perms)) {
            return Collections.emptySet();
        }

        // To simplify the concatenation, we treat nulls as empty strings, so we may get
        // repeated/leading/trailing delimiters. Therefore we remove them now.
        final String[] parts = StringUtil.deDupDelimiters(perms, ',')
                .split(",");
        final Set<AppPermission> permissions = EnumSet.noneOf(AppPermission.class);
        for (final String part : parts) {
            final String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                final int permissionId = Integer.parseInt(trimmed);
                final String permissionName = appPermissionIdDao.get(permissionId);
                permissions.add(AppPermission.getPermissionForName(permissionName));
            }
        }
        return permissions;
    }

    private AppUserPermissions getAppUserPermissions(final Record r) {
        final UserRef userRef = recordToUserRef(r);
        return getAppUserPermissions(userRef);
    }

    private UserRef recordToUserRef(final Record r) {
        return UserRef
                .builder()
                .uuid(r.get(STROOM_USER.UUID))
                .subjectId(r.get(STROOM_USER.NAME))
                .displayName(r.get(STROOM_USER.DISPLAY_NAME))
                .fullName(r.get(STROOM_USER.FULL_NAME))
                .group(r.get(STROOM_USER.IS_GROUP))
                .enabled(r.get(STROOM_USER.ENABLED))
                .build();
    }

    private AppUserPermissions getAppUserPermissions(final UserRef userRef) {
        final EnumSet<AppPermission> appPermissions = JooqUtil
                .contextResult(securityDbConnProvider, context -> context
                        .select(
                                PERMISSION_APP.PERMISSION_ID)
                        .from(PERMISSION_APP)
                        .where(PERMISSION_APP.USER_UUID.eq(userRef.getUuid()))
                        .fetch())
                .stream()
                .map(r2 -> {
                    final int permissionId = r2.get(PERMISSION_APP.PERMISSION_ID).intValue();
                    final String permissionName = appPermissionIdDao.get(permissionId);
                    return AppPermission.getPermissionForName(permissionName);
                })
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AppPermission.class)));
        return new AppUserPermissions(userRef, appPermissions);
    }
}
