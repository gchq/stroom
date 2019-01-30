package stroom.security.impl.db;

import org.jooq.Record;
import stroom.db.util.JooqUtil;
import stroom.security.dao.AppPermissionDao;
import stroom.security.impl.db.tables.records.AppPermissionRecord;

import javax.inject.Inject;
import java.util.Set;

import static stroom.security.impl.db.tables.AppPermission.APP_PERMISSION;
import static stroom.security.impl.db.tables.StroomUser.STROOM_USER;

public class AppPermissionDaoImpl implements AppPermissionDao {

    private final ConnectionProvider connectionProvider;

    @Inject
    public AppPermissionDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public Set<String> getPermissionsForUser(final String userUuid) {
        return JooqUtil.contextResult(connectionProvider, context ->
                context.select()
                        .from(APP_PERMISSION)
                        .where(APP_PERMISSION.USER_UUID.eq(userUuid))
                        .fetchSet(APP_PERMISSION.PERMISSION));
    }

    @Override
    public void addPermission(final String userUuid, final String permission) {
        JooqUtil.context(connectionProvider, context -> {
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
        JooqUtil.context(connectionProvider, context ->
                context.deleteFrom(APP_PERMISSION)
                        .where(APP_PERMISSION.USER_UUID.eq(userUuid))
                        .and(APP_PERMISSION.PERMISSION.eq(permission)).execute());
    }
}
