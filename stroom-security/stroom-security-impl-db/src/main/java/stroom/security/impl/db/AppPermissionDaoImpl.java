package stroom.security.impl.db;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Set;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

public class AppPermissionDaoImpl implements AppPermissionDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppPermissionDaoImpl.class);

    private final ConnectionProvider connectionProvider;

    private static final Table<Record> TABLE = table("app_permission");
    private static final Field<ULong> FIELD_ID = field("id", ULong.class);
    private static final Field<ULong> FIELD_VERSION = field("version", ULong.class);
    private static final Field<String> FIELD_USER_UUID = field("user_uid", String.class);
    private static final Field<String> FIELD_PERMISSION = field("permission", String.class);

    private String userUuid;
    private String docType;
    private String docUuid;
    private String permission;

    @Inject
    public AppPermissionDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public Set<String> getPermissionsForUser(final String userUuid) {
        return null;
    }

    @Override
    public void addPermission(final String userUuid, final String permission) {

    }

    @Override
    public void removePermission(final String userUuid, String permission) {

    }
}
