package stroom.security.impl.db;

import org.jooq.Record;
import stroom.db.util.JooqUtil;
import stroom.security.impl.AppPermissionDao;
import stroom.security.impl.db.jooq.tables.StroomUser;
import stroom.security.impl.db.jooq.tables.records.AppPermissionRecord;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.Set;

import static stroom.security.impl.db.jooq.Tables.STROOM_USER_GROUP;
import static stroom.security.impl.db.jooq.tables.AppPermission.APP_PERMISSION;
import static stroom.security.impl.db.jooq.tables.StroomUser.STROOM_USER;

public class AppPermissionDaoImpl implements AppPermissionDao {
    private final SecurityDbConnProvider securityDbConnProvider;

    @Inject
    public AppPermissionDaoImpl(final SecurityDbConnProvider securityDbConnProvider) {
        this.securityDbConnProvider = securityDbConnProvider;
    }

    @Override
    public Set<String> getPermissionsForUser(final String userUuid) {
        return JooqUtil.contextResult(securityDbConnProvider, context ->
                context.select()
                        .from(APP_PERMISSION)
                        .where(APP_PERMISSION.USER_UUID.eq(userUuid))
                        .fetchSet(APP_PERMISSION.PERMISSION));
    }

    @Override
    public Set<String> getPermissionsForUserName(String userName) {
        Set<String> permissions = new HashSet<>();
        // Get all permissions for this user
        permissions.addAll(JooqUtil.contextResult(securityDbConnProvider, context ->
                context.select()
                        .from(APP_PERMISSION)
                        .join(STROOM_USER)
                        .on(STROOM_USER.UUID.eq(APP_PERMISSION.USER_UUID))
                        .where(STROOM_USER.NAME.eq(userName))
                        .fetchSet(APP_PERMISSION.PERMISSION)));


        // Get all permissions for this user's groups
        StroomUser userUser = STROOM_USER.as("userUser");
        StroomUser groupUser = STROOM_USER.as("groupUser");
        permissions.addAll(JooqUtil.contextResult(securityDbConnProvider, context ->
                context.select()
                        .from(APP_PERMISSION)
                        // app_permission -> group user
                        .join(groupUser)
                        .on(APP_PERMISSION.USER_UUID.eq(groupUser.UUID))
                        // group user -> stroom user group
                        .join(STROOM_USER_GROUP)
                        .on(groupUser.UUID.eq(STROOM_USER_GROUP.GROUP_UUID))
                        // stroom user group -> user
                        .join(userUser)
                        .on(userUser.UUID.eq(STROOM_USER_GROUP.USER_UUID))
                        .where(userUser.NAME.eq(userName))
                        .fetchSet(APP_PERMISSION.PERMISSION)));

        return permissions;
    }

    @Override
    public void addPermission(final String userUuid, final String permission) {
        JooqUtil.context(securityDbConnProvider, context -> {
            final Record user = context.fetchOne(STROOM_USER, STROOM_USER.UUID.eq(userUuid));
            if (null == user) {
                throw new SecurityException(String.format("Could not find user: %s", userUuid));
            }

            final AppPermissionRecord r = context.newRecord(APP_PERMISSION);
            r.setUserUuid(userUuid);
            r.setPermission(permission);
            r.store();
        });
    }

    @Override
    public void removePermission(final String userUuid, String permission) {
        JooqUtil.context(securityDbConnProvider, context ->
                context.deleteFrom(APP_PERMISSION)
                        .where(APP_PERMISSION.USER_UUID.eq(userUuid))
                        .and(APP_PERMISSION.PERMISSION.eq(permission)).execute());
    }
}
