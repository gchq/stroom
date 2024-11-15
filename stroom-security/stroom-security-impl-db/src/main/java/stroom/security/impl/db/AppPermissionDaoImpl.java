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
import org.jooq.OrderField;
import org.jooq.Record;
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
                            .on(V_PERMISSION_APP_INHERITED_PERMS.USER_UUID.eq(STROOM_USER.UUID))
                            .where(conditions)
                            .and(V_PERMISSION_APP_INHERITED_PERMS.PERMS.isNotNull()
                                    .or(V_PERMISSION_APP_INHERITED_PERMS.INHERITED_PERMS.isNotNull()))
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
