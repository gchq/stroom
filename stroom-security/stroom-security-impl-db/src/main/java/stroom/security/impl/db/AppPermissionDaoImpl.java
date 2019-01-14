package stroom.security.impl.db;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

public class AppPermissionDaoImpl implements AppPermissionDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppPermissionDaoImpl.class);

    private final ConnectionProvider connectionProvider;

    private static final Table<Record> TABLE_APP_PERMISSION = table("app_permission");
    private static final Field<String> FIELD_PERMISSION = field("permission", String.class);
    private static final Field<String> FIELD_USER_UUID = field("user_uuid", String.class);

    @Inject
    public AppPermissionDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public Set<String> getPermissionsForUser(final String userUuid) {

        try (final Connection connection = connectionProvider.getConnection()) {
            return DSL.using(connection, SQLDialect.MYSQL)
                    .select()
                    .from(TABLE_APP_PERMISSION)
                    .where(FIELD_USER_UUID.equal(userUuid))
                    .fetchSet(FIELD_PERMISSION);
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public void addPermission(final String userUuid, final String permission) {

        try (final Connection connection = connectionProvider.getConnection()) {
            DSL.using(connection, SQLDialect.MYSQL)
                    .insertInto(TABLE_APP_PERMISSION)
                    .columns(FIELD_USER_UUID, FIELD_PERMISSION)
                    .values(userUuid, permission)
                    .execute();
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public void removePermission(final String userUuid, String permission) {
        try (final Connection connection = connectionProvider.getConnection()) {
            DSL.using(connection, SQLDialect.MYSQL)
                    .deleteFrom(TABLE_APP_PERMISSION)
                    .where(FIELD_USER_UUID.equal(userUuid))
                    .and(FIELD_PERMISSION.equal(permission))
                    .execute();
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
