package stroom.security.impl.db;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.security.dao.UserDao;
import stroom.security.shared.UserJooq;

import javax.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

public class UserDaoImpl implements UserDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserDaoImpl.class);

    private final ConnectionProvider connectionProvider;

    // Stroom User table
    private static final Table<Record> TABLE_STROOM_USER = table("stroom_user");
    private static final Field<Long> FIELD_ID = field("id", Long.class);
    private static final Field<String> FIELD_NAME = field("name", String.class);
    private static final Field<String> FIELD_UUID = field("uuid", String.class);
    private static final Field<Boolean> FIELD_IS_GROUP = field("is_group", Boolean.class);

    // Stroom User Groups table
    private static final Table<Record> TABLE_STROOM_USER_GROUPS = table("stroom_user_groups");
    private static final Field<String> FIELD_USER_UUID = field("user_uuid", String.class);
    private static final Field<String> FIELD_GROUP_UUID = field("group_uuid", String.class);

    @Inject
    public UserDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    private static UserJooq mapFromRecord(final Record record) {
        return new UserJooq.Builder()
                .id(record.get(FIELD_ID))
                .uuid(record.get(FIELD_UUID))
                .name(record.get(FIELD_NAME))
                .isGroup(record.get(FIELD_IS_GROUP))
                .build();
    }

    @Override
    public List<UserJooq> find(Boolean isGroup, String name) {
        try (final Connection connection = connectionProvider.getConnection()) {
            return DSL.using(connection, SQLDialect.MYSQL)
                    .select()
                    .from(TABLE_STROOM_USER)
                    .where(DSL.condition(isGroup == null).or(FIELD_IS_GROUP.equal(isGroup))
                    .and(DSL.condition(name == null).or(FIELD_NAME.equal(name))))
                    .fetch(UserDaoImpl::mapFromRecord);
        } catch (final SQLException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw new SecurityException(e.getMessage(), e);
        }
    }

    @Override
    public UserJooq getById(final long id) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final Record record = DSL.using(connection, SQLDialect.MYSQL)
                    .select()
                    .from(TABLE_STROOM_USER)
                    .where(FIELD_ID.equal(id))
                    .fetchOne();
            return Optional.ofNullable(record)
                    .map(UserDaoImpl::mapFromRecord)
                    .orElse(null);
        } catch (final SQLException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw new SecurityException(e.getMessage(), e);
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
            return Optional.ofNullable(record)
                    .map(UserDaoImpl::mapFromRecord)
                    .orElse(null);
        } catch (final SQLException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw new SecurityException(e.getMessage(), e);
        }
    }

    @Override
    public UserJooq getUserByName(final String name) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final Record record = DSL.using(connection, SQLDialect.MYSQL)
                    .select()
                    .from(TABLE_STROOM_USER)
                    .where(FIELD_NAME.equal(name))
                    .fetchOne();
            return Optional.ofNullable(record)
                    .map(UserDaoImpl::mapFromRecord)
                    .orElse(null);
        } catch (final SQLException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw new SecurityException(e.getMessage(), e);
        }
    }

    @Override
    public List<UserJooq> findUsersInGroup(final String groupUuid) {
        try (final Connection connection = connectionProvider.getConnection()) {
            return DSL.using(connection, SQLDialect.MYSQL)
                    .select()
                    .from(TABLE_STROOM_USER
                            .join(TABLE_STROOM_USER_GROUPS)
                            .on(FIELD_UUID.equal(FIELD_USER_UUID)))
                    .where(FIELD_GROUP_UUID.equal(groupUuid))
                    .orderBy(FIELD_NAME)
                    .fetch(UserDaoImpl::mapFromRecord);
        } catch (final SQLException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw new SecurityException(e.getMessage(), e);
        }
    }

    @Override
    public List<UserJooq> findGroupsForUser(final String userUuid) {
        try (final Connection connection = connectionProvider.getConnection()) {
            return DSL.using(connection, SQLDialect.MYSQL)
                .select()
                .from(TABLE_STROOM_USER
                        .join(TABLE_STROOM_USER_GROUPS)
                        .on(FIELD_UUID.equal(FIELD_GROUP_UUID)))
                .where(FIELD_USER_UUID.equal(userUuid))
                .orderBy(FIELD_NAME)
                .fetch(UserDaoImpl::mapFromRecord);
        } catch (final SQLException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw new SecurityException(e.getMessage(), e);
        }
    }

    @Override
    public UserJooq createUser(final String name) {
        final String userUuid = UUID.randomUUID().toString();

        try (final Connection connection = connectionProvider.getConnection()) {
            DSL.using(connection, SQLDialect.MYSQL)
                    .insertInto(TABLE_STROOM_USER)
                    .columns(FIELD_UUID, FIELD_NAME, FIELD_IS_GROUP)
                    .values(userUuid, name, Boolean.FALSE)
                    .execute();

            return DSL.using(connection, SQLDialect.MYSQL)
                    .select()
                    .from(TABLE_STROOM_USER)
                    .where(FIELD_NAME.equal(name))
                    .fetchOne()
                    .map(UserDaoImpl::mapFromRecord);
        } catch (final SQLException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw new SecurityException(e.getMessage(), e);
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
                    .map(UserDaoImpl::mapFromRecord);
        } catch (final SQLException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw new SecurityException(e.getMessage(), e);
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
        } catch (final SQLException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw new SecurityException(e.getMessage(), e);
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
        } catch (final SQLException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw new SecurityException(e.getMessage(), e);
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
        } catch (final SQLException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw new SecurityException(e.getMessage(), e);
        }
    }
}
