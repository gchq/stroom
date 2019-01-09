package stroom.security.impl.db;

import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

public class UserDaoImpl implements UserDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserDaoImpl.class);

    private final ConnectionProvider connectionProvider;

    private static final Table<Record> TABLE = table("stroom_user");
    private static final Field<ULong> FIELD_ID = field("id", ULong.class);
    private static final Field<ULong> FIELD_VERSION = field("version", ULong.class);
    private static final Field<String> FIELD_NAME = field("name", String.class);
    private static final Field<String> FIELD_UUID = field("uuid", String.class);
    private static final Field<Boolean> FIELD_IS_GROUP = field("is_group", Boolean.class);

//    private static final String SQL_ADD_USER_TO_GROUP;
//    private static final String SQL_REMOVE_USER_FROM_GROUP;
//
//    static {
//        SQL_ADD_USER_TO_GROUP = ""
//                + "INSERT INTO "
//                + UserGroupUser.TABLE_NAME
//                + " ("
//                + UserGroupUser.VERSION
//                + ", "
//                + UserGroupUser.USER_UUID
//                + ", "
//                + UserGroupUser.GROUP_UUID
//                + ")"
//                + " VALUES (?,?,?)";
//    }
//
//    static {
//        SQL_REMOVE_USER_FROM_GROUP = ""
//                + "DELETE FROM "
//                + UserGroupUser.TABLE_NAME
//                + " WHERE "
//                + UserGroupUser.USER_UUID
//                + " = ?"
//                + " AND "
//                + UserGroupUser.GROUP_UUID
//                + " = ?";
//    }

    @Inject
    public UserDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public UserJooq getById(final long id) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final Record record = DSL.using(connection, SQLDialect.MYSQL)
                    .select()
                    .from(TABLE)
                    .where(FIELD_ID.equal(ULong.valueOf(id)))
                    .fetchOne();

            return record.into(UserJooq.class);
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public UserJooq getByUuid(final String uuid) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final Record record = DSL.using(connection, SQLDialect.MYSQL)
                    .select()
                    .from(TABLE)
                    .where(FIELD_UUID.equal(uuid))
                    .fetchOne();

            return record.into(UserJooq.class);
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public UserJooq getUserByName(final String name) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final Record record = DSL.using(connection, SQLDialect.MYSQL)
                    .select()
                    .from(TABLE)
                    .where(FIELD_NAME.equal(name))
                    .fetchOne();

            return record.into(UserJooq.class);
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public List<UserJooq> findUsersInGroup(final String groupUuid) {


//        final SqlBuilder sql = new SqlBuilder();
//        sql.append("SELECT");
//        sql.append(" u.*");
//        sql.append(" FROM ");
//        sql.append(User.TABLE_NAME);
//        sql.append(" u");
//        sql.append(" JOIN ");
//        sql.append(UserGroupUser.TABLE_NAME);
//        sql.append(" ugu");
//        sql.append(" ON");
//        sql.append(" (u.");
//        sql.append(User.UUID);
//        sql.append(" = ugu.");
//        sql.append(UserGroupUser.USER_UUID);
//        sql.append(")");
//        sql.append(" WHERE");
//        sql.append(" ugu.");
//        sql.append(UserGroupUser.GROUP_UUID);
//        sql.append(" = ");
//        sql.arg(userGroup.getUuid());
//        sql.append(" ORDER BY");
//        sql.append(" u.");
//        sql.append(User.NAME);

        return null;
    }

    @Override
    public List<UserJooq> findGroupsForUser(final String userUuid) {
//        final SqlBuilder sql = new SqlBuilder();
//        sql.append("SELECT");
//        sql.append(" u.*");
//        sql.append(" FROM ");
//        sql.append(User.TABLE_NAME);
//        sql.append(" u");
//        sql.append(" JOIN ");
//        sql.append(UserGroupUser.TABLE_NAME);
//        sql.append(" ugu");
//        sql.append(" ON");
//        sql.append(" (u.");
//        sql.append(User.UUID);
//        sql.append(" = ugu.");
//        sql.append(UserGroupUser.GROUP_UUID);
//        sql.append(")");
//        sql.append(" WHERE");
//        sql.append(" ugu.");
//        sql.append(UserGroupUser.USER_UUID);
//        sql.append(" = ");
//        sql.arg(user.getUuid());
//        sql.append(" ORDER BY");
//        sql.append(" u.");
//        sql.append(User.NAME);

        return null;
    }

    @Override
    public UserJooq createUser(final String name) {
        return null;
    }

    @Override
    public UserJooq createUserGroup(final String name) {
        return null;
    }

    @Override
    public Boolean deleteUser(String uuid) {
        return Boolean.FALSE;
    }

    @Override
    public void addUserToGroup(final String userUuid, final String groupUuid) {

    }

    @Override
    public void removeUserFromGroup(final String userUuid, final String groupUuid) {

    }
}
