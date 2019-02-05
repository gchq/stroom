package stroom.security.impl.db;

import org.jooq.impl.DSL;
import stroom.db.util.JooqUtil;
import stroom.security.dao.UserDao;
import stroom.security.impl.db.tables.records.StroomUserGroupsRecord;
import stroom.security.impl.db.tables.records.StroomUserRecord;
import stroom.security.shared.User;

import javax.inject.Inject;
import java.util.List;
import java.util.UUID;

import static stroom.security.impl.db.Tables.STROOM_USER;
import static stroom.security.impl.db.Tables.STROOM_USER_GROUPS;

public class UserDaoImpl implements UserDao {

    private final ConnectionProvider connectionProvider;

    @Inject
    public UserDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public List<User> find(final Boolean isGroup,
                           final String name) {
        return JooqUtil.contextResult(connectionProvider, context ->
                context.select().from(STROOM_USER)
                        .where(DSL.condition(isGroup == null).or(STROOM_USER.IS_GROUP.eq(isGroup))
                        .and(DSL.condition(name == null).or(STROOM_USER.NAME.eq(name))))
                        .fetchInto(User.class)
        );
    }

    @Override
    public User getById(final long id) {
        return JooqUtil.contextResult(connectionProvider, context ->
                context.select().from(STROOM_USER)
                        .where(STROOM_USER.ID.eq(id))
                        .fetchOneInto(User.class)
        );
    }

    @Override
    public User getByUuid(final String uuid) {
        return JooqUtil.contextResult(connectionProvider, context ->
                context.select().from(STROOM_USER)
                        .where(STROOM_USER.UUID.eq(uuid))
                        .fetchOneInto(User.class)
        );
    }

    @Override
    public User getUserByName(final String name) {
        return JooqUtil.contextResult(connectionProvider, context ->
                context.select().from(STROOM_USER)
                        .where(STROOM_USER.NAME.eq(name))
                        .fetchOneInto(User.class)
        );
    }

    @Override
    public List<User> findUsersInGroup(final String groupUuid) {
        return JooqUtil.contextResult(connectionProvider, context ->
                context.select()
                        .from(STROOM_USER)
                        .join(STROOM_USER_GROUPS)
                        .on(STROOM_USER.UUID.eq(STROOM_USER_GROUPS.USER_UUID))
                        .where(STROOM_USER_GROUPS.GROUP_UUID.eq(groupUuid))
                        .orderBy(STROOM_USER.NAME)
                        .fetchInto(User.class)
        );
    }

    @Override
    public List<User> findGroupsForUser(final String userUuid) {
        return JooqUtil.contextResult(connectionProvider, context ->
                context.select()
                        .from(STROOM_USER)
                        .join(STROOM_USER_GROUPS)
                        .on(STROOM_USER.UUID.eq(STROOM_USER_GROUPS.GROUP_UUID))
                        .where(STROOM_USER_GROUPS.USER_UUID.eq(userUuid))
                        .orderBy(STROOM_USER.NAME)
                        .fetchInto(User.class)
        );
    }

    @Override
    public User createUser(final String name) {
        final String userUuid = UUID.randomUUID().toString();

        return JooqUtil.contextResult(connectionProvider, context -> {
            final StroomUserRecord r = context.newRecord(STROOM_USER);
            r.setUuid(userUuid);
            r.setName(name);
            r.setIsGroup(false);
            r.store();
            return r.into(User.class);
        });
    }

    @Override
    public User createUserGroup(final String name) {
        final String userUuid = UUID.randomUUID().toString();

        return JooqUtil.contextResult(connectionProvider, context -> {
            final StroomUserRecord r = context.newRecord(STROOM_USER);
            r.setUuid(userUuid);
            r.setName(name);
            r.setIsGroup(true);
            r.store();
            return r.into(User.class);
        });
    }

    @Override
    public void deleteUser(final String uuid) {
        JooqUtil.context(connectionProvider, context ->
            context.deleteFrom(STROOM_USER)
                    .where(STROOM_USER.UUID.eq(uuid))
                    .execute()
        );
    }

    @Override
    public void addUserToGroup(final String userUuid,
                               final String groupUuid) {
        JooqUtil.context(connectionProvider, context -> {
            final StroomUserGroupsRecord r = context.newRecord(STROOM_USER_GROUPS);
            r.setUserUuid(userUuid);
            r.setGroupUuid(groupUuid);
            r.store();
        });
    }

    @Override
    public void removeUserFromGroup(final String userUuid,
                                    final String groupUuid) {
        JooqUtil.context(connectionProvider, context ->
                context.deleteFrom(STROOM_USER_GROUPS)
                        .where(STROOM_USER_GROUPS.USER_UUID.eq(userUuid))
                        .and(STROOM_USER_GROUPS.GROUP_UUID.eq(groupUuid))
                        .execute()
        );
    }
}
