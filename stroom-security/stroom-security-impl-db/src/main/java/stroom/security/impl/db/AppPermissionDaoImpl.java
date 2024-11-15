package stroom.security.impl.db;

import stroom.db.util.JooqUtil;
import stroom.security.impl.AppPermissionDao;
import stroom.security.impl.AppPermissionIdDao;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppUserPermissions;
import stroom.security.shared.FetchAppUserPermissionsRequest;
import stroom.security.shared.PermissionShowLevel;
import stroom.util.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
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
import static stroom.security.impl.db.jooq.tables.VPermissionAppInheritedPerms.V_PERMISSION_APP_INHERITED_PERMS;

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

        List<AppUserPermissions> list = null;
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
            list = JooqUtil.contextResult(securityDbConnProvider, context -> context
                            .select(
                                    STROOM_USER.UUID,
                                    STROOM_USER.NAME,
                                    STROOM_USER.DISPLAY_NAME,
                                    STROOM_USER.FULL_NAME,
                                    STROOM_USER.IS_GROUP,
                                    V_PERMISSION_APP_INHERITED_PERMS.PERMS,
                                    V_PERMISSION_APP_INHERITED_PERMS.INHERITED_PERMS)
                            .from(STROOM_USER)
                            .join(V_PERMISSION_APP_INHERITED_PERMS)
                            .on(V_PERMISSION_APP_INHERITED_PERMS.UUID.eq(STROOM_USER.UUID))
                            .where(conditions)
                            .orderBy(orderFields)
                            .offset(offset)
                            .limit(limit)
                            .fetch())
                    .map(r -> {
                        final UserRef userRef = recordToUserRef(r);
                        final String perms = r.get(V_PERMISSION_APP_INHERITED_PERMS.PERMS);
                        final String inheritedPerms = r.get(V_PERMISSION_APP_INHERITED_PERMS.INHERITED_PERMS);
                        final Set<AppPermission> permissions = getAppPermissionSet(perms);
                        final Set<AppPermission> inherited = getAppPermissionSet(inheritedPerms);
                        return new AppUserPermissions(userRef, permissions, inherited);
                    });


//
////            // If we are only delivering users with specific permissions then join and select distinct.
//            list = JooqUtil.contextResult(securityDbConnProvider, context -> {
////                var view = context.createView(DSL.name("permission_app_view"))
////                        .as(context
////                            .select(
////                                    STROOM_USER.UUID,
////                                    STROOM_USER.NAME,
////                                    STROOM_USER.DISPLAY_NAME,
////                                    STROOM_USER.FULL_NAME,
////                                    DSL.groupConcat(PERMISSION_APP.PERMISSION_ID).as("perms"),
////                                    DSL.field("", String.class).as("group_uuid"))
////                                .from(STROOM_USER)
////                            .leftOuterJoin(PERMISSION_APP).on(PERMISSION_APP.USER_UUID.eq(STROOM_USER.UUID))
////                                            .groupBy(STROOM_USER.UUID)
////                                    .union(context
////                                            .select(
////                                                    STROOM_USER.UUID,
////                                                    STROOM_USER.NAME,
////                                                    STROOM_USER.DISPLAY_NAME,
////                                                    STROOM_USER.FULL_NAME,
////                                                    DSL.groupConcat(PERMISSION_APP.PERMISSION_ID).as("perms"),
////                                                    STROOM_USER_GROUP.GROUP_UUID)
////                                            .from(STROOM_USER)
////                                            .leftOuterJoin(PERMISSION_APP).on(PERMISSION_APP.USER_UUID.eq(STROOM_USER.UUID))
////                                                    .join(STROOM_USER_GROUP).on(STROOM_USER_GROUP.USER_UUID.eq(STROOM_USER.UUID))
////                                                    .groupBy(STROOM_USER.UUID, STROOM_USER_GROUP.GROUP_UUID)))
////                        .execute();
////
////
//
//
////                final Field<String> uuid = DSL.field(DSL.name("uuid"), String.class);
//
//
//                final VPermissionApp vpa = V_PERMISSION_APP.as("vpa");
//                final Name cte = DSL.name("cte");
//                final Name uuid = DSL.name("uuid");
//                final Name name = DSL.name("name");
//                final Name displayName = DSL.name("display_name");
//                final Name fullName = DSL.name("full_name");
//                final Name isGroup = DSL.name("is_group");
//                final Name perms = DSL.name("perms");
//                final Name combinedPerms = DSL.name("combined_perms");
//                final Field<String> combinedPermsField = DSL.field(cte.append(combinedPerms), String.class);
////                "uuid",
////                        "name",
////                        "display_name",
////                        "full_name",
////                        "perms",
////                        "combined_perms"
//
//                final CommonTableExpression<?> commonTableExpression = cte
//                        .fields(uuid, name, displayName, fullName, isGroup, perms, combinedPerms)
//                        .as(context
//                                .select(
//                                        vpa.UUID,
//                                        vpa.NAME,
//                                        vpa.DISPLAY_NAME,
//                                        vpa.FULL_NAME,
//                                        vpa.IS_GROUP,
//                                        vpa.PERMS,
//                                        vpa.PERMS)
//                                .from(vpa)
//                                .where(vpa.GROUP_UUID.isNull())
//                                .unionAll(context
//                                        .select(vpa.UUID,
//                                                vpa.NAME,
//                                                vpa.DISPLAY_NAME,
//                                                vpa.FULL_NAME,
//                                                vpa.IS_GROUP,
//                                                vpa.PERMS,
////                                                DSL.iif(combinedPermsField.isNull(),
////                                                        vpa.PERMS,
////                                                        DSL.iif(
////                                                                vpa.PERMS.isNull(),
////                                                                combinedPermsField,
////                                                                DSL.concat(vpa.PERMS, combinedPermsField)
////                                                        ))
////
//                                                concatWs(
//                                                        ",",
//                                                        combinedPermsField,
//                                                        vpa.PERMS)
//                                        )
//                                        .from(DSL.table(cte))
//                                        .join(vpa)
//                                        .on(DSL.field(cte.append(uuid)).eq(vpa.GROUP_UUID))));
//
//
////                WITH RECURSIVE cte (
////                        uuid,
////                        name,
////                        display_name,
////                        full_name,
////                        perms,
////                        combined_perms
////                ) AS (
////                        select
////                        pav.uuid,
////                        pav.name,
////                        pav.display_name,
////                        pav.full_name,
////                        pav.perms,
////                        pav.perms
////                        from v_permission_app pav
////                        where pav.group_uuid is null
////                UNION ALL
////                select
////                pav.uuid,
////                        pav.name,
////                        pav.display_name,
////                        pav.full_name,
////                        pav.perms,
////                        concat_ws('/', cte.combined_perms, pav.perms)
////                from cte
////                join v_permission_app pav
////                on cte.uuid = pav.group_uuid
////)
////                SELECT
////                        uuid,
////                        name,
////                        display_name,
////                        full_name,
////                group_concat(distinct perms),
////                        group_concat(distinct combined_perms) FROM cte group by uuid, name, display_name, full_name;
////
////
////
//
//
//                final var result1 = context
//                        .withRecursive(commonTableExpression)
//                        .selectFrom(commonTableExpression)
//                        .fetch();
//
//
//                final var result = context
//                        .withRecursive(commonTableExpression)
//                        .select(
//                                DSL.field(uuid),
//                                DSL.field(name),
//                                DSL.field(displayName),
//                                DSL.field(fullName),
//                                DSL.field(isGroup),
//                                DSL.groupConcat(DSL.field(perms)).as(perms),
//                                DSL.groupConcat(DSL.field(combinedPerms)).as(combinedPerms))
//                        .from(commonTableExpression)
//                        .groupBy(DSL.field(uuid),
//                                DSL.field(name),
//                                DSL.field(displayName),
//                                DSL.field(fullName),
//                                DSL.field(isGroup))
//                        .fetch();
//
//                return result.map(r -> {
//                    final String ruuid = r.get(uuid, String.class);
//                    final String rname = r.get(name, String.class);
//                    final String rdisplayName = r.get(displayName, String.class);
//                    final String rfullName = r.get(fullName, String.class);
//                    final boolean rIsGroup = r.get(isGroup, Boolean.class);
//                    final String rperms = r.get(perms, String.class);
//                    final String rcombinedPerms = r.get(combinedPerms, String.class);
//
//                    final UserRef userRef = new UserRef(ruuid, rname, rdisplayName, rfullName, rIsGroup);
//
//                    final Set<AppPermission> permissions = getAppPermissionSet(rperms);
//                    final Set<AppPermission> inherited = getAppPermissionSet(rcombinedPerms);
//                    return new AppUserPermissions(userRef, permissions, inherited);
//                });
//
//
////                                DIRECTORY.LABEL,
////                                DIRECTORY.LABEL)
////                                .from(DIRECTORY)
////                                .where(DIRECTORY.PARENT_ID.isNull())
////                                .unionAll(
////                                        select(
////                                                DIRECTORY.ID,
////                                                DIRECTORY.LABEL,
////                                                field(name("t", "path"), VARCHAR)
////                                                        .concat("\\")
////                                                        .concat(DIRECTORY.LABEL))
////                                                .from(table(name("t")))
////                                                .join(DIRECTORY)
////                                                .on(field(name("t", "id"), INTEGER)
////                                                        .eq(DIRECTORY.PARENT_ID)))
////                );
//
////                System.out.println(
////                        create.withRecursive(cte)
////                                .selectFrom(cte)
////                                .fetch()
////                );
//
////                UNION ALL
////                select
////                pav.uuid,
////                        pav.name,
////                        pav.display_name,
////                        pav.full_name,
////                        pav.perms,
////                        concat_ws('/', cte.combined_perms, pav.perms)
////                from cte
////                join permission_app_view pav
////                on cte.uuid = pav.group_uuid
////)
////                SELECT
////                        uuid,
////                        name,
////                        display_name,
////                        full_name,
////                group_concat(distinct perms),
////                        group_concat(distinct combined_perms) FROM cte group by uuid, name, display_name, full_name;
////
////
////
////
////                select
////                pav.uuid,
////                        pav.name,
////                        pav.display_name,
////                        pav.full_name,
////                        pav.perms,
////                        pav.name
////                from permission_app_view pav
////                where pav.group_uuid is null;
//
//            });
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

    public static Field<String> concatWs(final String delimiter,
                                         final Field<String> left,
                                         final Field<String> right) {
        return DSL.field("concat_ws({0}, {1}, {2})", String.class, delimiter, left, right);
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
        final Set<AppPermission> permissions = JooqUtil
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
                })
                .stream()
                .collect(Collectors.toSet());
        return new AppUserPermissions(userRef, permissions);
    }
}
