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
import stroom.security.shared.PermissionShowLevel;
import stroom.util.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.jooq.CommonTableExpression;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Name;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Select;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.types.UByte;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static stroom.security.impl.db.jooq.tables.PermissionApp.PERMISSION_APP;
import static stroom.security.impl.db.jooq.tables.StroomUser.STROOM_USER;
import static stroom.security.impl.db.jooq.tables.StroomUserGroup.STROOM_USER_GROUP;

public class AppPermissionDaoImpl implements AppPermissionDao {

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
        return JooqUtil.contextResult(securityDbConnProvider, context -> context
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
                .collect(Collectors.toSet());
    }

    @Override
    public void addPermission(final String userUuid, final AppPermission permission) {
        final UByte permissionId = UByte.valueOf(appPermissionIdDao.getOrCreateId(permission.getDisplayValue()));
        JooqUtil.context(securityDbConnProvider, context -> context
                .insertInto(PERMISSION_APP)
                .columns(PERMISSION_APP.USER_UUID, PERMISSION_APP.PERMISSION_ID)
                .values(userUuid, permissionId)
                .execute());
    }

    @Override
    public void removePermission(final String userUuid, AppPermission permission) {
        final UByte permissionId = UByte.valueOf(appPermissionIdDao.getOrCreateId(permission.getDisplayValue()));
        JooqUtil.context(securityDbConnProvider, context -> context
                .deleteFrom(PERMISSION_APP)
                .where(PERMISSION_APP.USER_UUID.eq(userUuid))
                .and(PERMISSION_APP.PERMISSION_ID.eq(permissionId)).execute());
    }

    @Override
    public ResultPage<AppUserPermissions> fetchAppUserPermissions(final FetchAppUserPermissionsRequest request) {
        Objects.requireNonNull(request, "Null request");
        final UserDaoImpl userDao = userDaoProvider.get();

        final int offset = JooqUtil.getOffset(request.getPageRequest());
        final int limit = JooqUtil.getLimit(request.getPageRequest(), true);

        final List<Condition> conditions = new ArrayList<>();
        conditions.add(userDao.getUserCondition(request.getExpression()));
        conditions.add(STROOM_USER.ENABLED.eq(true));

        final Collection<OrderField<?>> orderFields = userDao.createOrderFields(request);

        final List<AppUserPermissions> list;
        if (PermissionShowLevel.SHOW_ALL.equals(request.getShowLevel())) {
            list = JooqUtil.contextResult(securityDbConnProvider, context -> context
                            .select(
                                    STROOM_USER.UUID,
                                    STROOM_USER.NAME,
                                    STROOM_USER.DISPLAY_NAME,
                                    STROOM_USER.FULL_NAME,
                                    STROOM_USER.IS_GROUP)
                            .from(STROOM_USER)
                            .where(conditions)
                            .orderBy(orderFields)
                            .offset(offset)
                            .limit(limit)
                            .fetch())
                    .map(this::getAppUserPermissions);

        } else if (PermissionShowLevel.SHOW_EFFECTIVE.equals(request.getShowLevel())) {
            final StroomUser su = STROOM_USER.as("su");
            final StroomUserGroup sug = STROOM_USER_GROUP.as("sug");
            final PermissionApp pa = PERMISSION_APP.as("pa");
            final PermissionApp paParent = PERMISSION_APP.as("pa_parent");
            final Name cte = DSL.name("cte");
            final Field<String> cteUserUuid = DSL.field(cte.append("user_uuid"), String.class);
            final Field<String> cteGroupUuid = DSL.field(cte.append("group_uuid"), String.class);
            final Field<String> ctePerms = DSL.field(cte.append("perms"), String.class);
            final Field<String> cteInheritedPerms = DSL.field(cte.append("inherited_perms"), String.class);

            list = JooqUtil.contextResult(securityDbConnProvider, context -> {

                // Create a select to group permissions and parent permissions.
                final Select<?> select = context
                        .select(
                                su.UUID.as("user_uuid"),
                                sug.GROUP_UUID,
                                DSL.groupConcatDistinct(pa.PERMISSION_ID).as("perms"),
                                DSL.groupConcatDistinct(paParent.PERMISSION_ID).as("parent_perms"))
                        .from(su)
                        .leftOuterJoin(sug).on(sug.USER_UUID.eq(su.UUID))
                        .leftOuterJoin(pa).on(pa.USER_UUID.eq(su.UUID))
                        .leftOuterJoin(paParent).on(paParent.USER_UUID.eq(sug.GROUP_UUID))
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
                                                        DSL.if_(cteInheritedPerms.isNull(), vParentPerms,
                                                                DSL.if_(vParentPerms.isNull(),
                                                                        cteInheritedPerms,
                                                                        DSL.concat(DSL.concat(cteInheritedPerms,
                                                                                        ","),
                                                                                vParentPerms))))
                                                .from(DSL.table(cte))
                                                .join(v).on(vGroupUuid.eq(cteUserUuid))));

                // Apply `with recursive`
                final Table<?> recursive = context
                        .withRecursive(commonTableExpression)
                        .select(
                                cteUserUuid,
                                cteGroupUuid,
                                DSL.groupConcatDistinct(ctePerms).as("perms"),
                                DSL.groupConcatDistinct(cteInheritedPerms).as("inherited_perms"))
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

                // Join recursive select to user.
                return context
                        .select(STROOM_USER.UUID,
                                STROOM_USER.NAME,
                                STROOM_USER.DISPLAY_NAME,
                                STROOM_USER.FULL_NAME,
                                STROOM_USER.IS_GROUP,
                                recPerms,
                                recInheritedPerms)
                        .from(STROOM_USER)
                        .join(recursive).on(recUserUuid.eq(STROOM_USER.UUID))
                        .where(conditions)
                        .and(recPerms.isNotNull()
                                .or(recInheritedPerms.isNotNull()))
                        .orderBy(orderFields)
                        .offset(offset)
                        .limit(limit)
                        .fetch();

            }).map(r -> {
                final UserRef userRef = recordToUserRef(r);
                final String perms = r.get(ctePerms);
                final String inheritedPerms = r.get(cteInheritedPerms);
                final Set<AppPermission> permissions = getAppPermissionSet(perms);
                final Set<AppPermission> inherited = getAppPermissionSet(inheritedPerms);
                return new AppUserPermissions(userRef, permissions, inherited);
            });

        } else {
            // If we are only delivering users with specific permissions then join and select distinct.
            list = JooqUtil.contextResult(securityDbConnProvider, context -> context
                            .selectDistinct(
                                    STROOM_USER.UUID,
                                    STROOM_USER.NAME,
                                    STROOM_USER.DISPLAY_NAME,
                                    STROOM_USER.FULL_NAME,
                                    STROOM_USER.IS_GROUP)
                            .from(STROOM_USER)
                            .join(PERMISSION_APP).on(PERMISSION_APP.USER_UUID.eq(STROOM_USER.UUID))
                            .where(conditions)
                            .orderBy(orderFields)
                            .offset(offset)
                            .limit(limit)
                            .fetch())
                    .map(this::getAppUserPermissions);
        }
        return ResultPage.createCriterialBasedList(list, request);
    }

    private Set<AppPermission> getAppPermissionSet(final String perms) {
        if (NullSafe.isBlankString(perms)) {
            return Collections.emptySet();
        }

        final String[] parts = perms.split(",");
        final Set<AppPermission> permissions = new HashSet<>(parts.length);
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
                .build();
    }

    private AppUserPermissions getAppUserPermissions(final UserRef userRef) {
        final Set<AppPermission> permissions = new HashSet<>(JooqUtil
                .contextResult(securityDbConnProvider, context -> context
                        .select(
                                PERMISSION_APP.PERMISSION_ID)
                        .from(PERMISSION_APP)
                        .where(PERMISSION_APP.USER_UUID.eq(userRef.getUuid()))
                        .fetch())
                .map(r2 -> {
                    final int permissionId = r2.get(PERMISSION_APP.PERMISSION_ID).intValue();
                    final String permissionName = appPermissionIdDao.get(permissionId);
                    return AppPermission.getPermissionForName(permissionName);
                }));
        return new AppUserPermissions(userRef, permissions);
    }
}
