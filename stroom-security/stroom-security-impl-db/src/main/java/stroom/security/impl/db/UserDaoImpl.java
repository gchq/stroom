package stroom.security.impl.db;

import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

public class UserDaoImpl implements UserDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserDaoImpl.class);

    private final ConnectionProvider connectionProvider;

    private static final UInteger FIRST_VERSION = UInteger.valueOf(1);

    private static final Table<Record> TABLE_STROOM_USER = table("stroom_user");
    private static final Field<ULong> FIELD_ID = field("id", ULong.class);
    private static final Field<UInteger> FIELD_VERSION = field("version", UInteger.class);
    private static final Field<String> FIELD_NAME = field("name", String.class);
    private static final Field<String> FIELD_UUID = field("uuid", String.class);
    private static final Field<Boolean> FIELD_IS_GROUP = field("is_group", Boolean.class);

    private static final Table<Record> TABLE_STROOM_USER_GROUPS = table("stroom_user_groups");
    private static final Field<String> FIELD_USER_UUID = field("user_uuid", String.class);
    private static final Field<String> FIELD_GROUP_UUID = field("group_uuid", String.class);

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
                    .from(TABLE_STROOM_USER)
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
                    .from(TABLE_STROOM_USER)
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
            return DSL.using(connection, SQLDialect.MYSQL)
                    .select()
                    .from(TABLE_STROOM_USER)
                    .where(FIELD_NAME.equal(name))
                    .fetchOne()
                    .into(UserJooq.class);
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
        final String userUuid = UUID.randomUUID().toString();

        try (final Connection connection = connectionProvider.getConnection()) {
            DSL.using(connection, SQLDialect.MYSQL)
                    .insertInto(TABLE_STROOM_USER)
                    .columns(FIELD_VERSION, FIELD_UUID, FIELD_NAME, FIELD_IS_GROUP)
                    .values(FIRST_VERSION, userUuid, name, Boolean.FALSE)
                    .execute();

            return DSL.using(connection, SQLDialect.MYSQL)
                    .select()
                    .from(TABLE_STROOM_USER)
                    .where(FIELD_NAME.equal(name))
                    .fetchOne()
                    .into(UserJooq.class);
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public UserJooq createUserGroup(final String name) {
        final String userUuid = UUID.randomUUID().toString();

        try (final Connection connection = connectionProvider.getConnection()) {
            DSL.using(connection, SQLDialect.MYSQL)
                    .insertInto(TABLE_STROOM_USER)
                    .columns(FIELD_UUID, FIELD_NAME, FIELD_IS_GROUP)
                    .values(userUuid, name, Boolean.TRUE)
                    .execute();

            return DSL.using(connection, SQLDialect.MYSQL)
                    .select()
                    .from(TABLE_STROOM_USER)
                    .where(FIELD_NAME.equal(name))
                    .fetchOne()
                    .into(UserJooq.class);
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public Boolean deleteUser(final String uuid) {
        try (final Connection connection = connectionProvider.getConnection()) {
            // Clean up any group memberships
            DSL.using(connection, SQLDialect.MYSQL)
                    .deleteFrom(TABLE_STROOM_USER_GROUPS)
                    .where(FIELD_USER_UUID.equal(uuid))
                    .or(FIELD_GROUP_UUID.equal(uuid))
                    .execute();

            int rowsAffected = DSL.using(connection, SQLDialect.MYSQL)
                    .deleteFrom(TABLE_STROOM_USER)
                    .where(FIELD_UUID.equal(uuid))
                    .execute();

            return rowsAffected == 1;
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public Boolean addUserToGroup(final String userUuid,
                               final String groupUuid) {
        try (final Connection connection = connectionProvider.getConnection()) {
            // Clean up any group memberships
            int rowsAffected = DSL.using(connection, SQLDialect.MYSQL)
                    .insertInto(TABLE_STROOM_USER_GROUPS)
                    .columns(FIELD_USER_UUID, FIELD_GROUP_UUID)
                    .values(userUuid, groupUuid)
                    .execute();

            return rowsAffected == 1;
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public Boolean removeUserFromGroup(final String userUuid,
                                    final String groupUuid) {
        try (final Connection connection = connectionProvider.getConnection()) {
            // Clean up any group memberships
            int rowsAffected = DSL.using(connection, SQLDialect.MYSQL)
                    .deleteFrom(TABLE_STROOM_USER_GROUPS)
                    .where(FIELD_USER_UUID.equal(userUuid))
                    .and(FIELD_GROUP_UUID.equal(groupUuid))
                    .execute();

            return rowsAffected == 1;
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
