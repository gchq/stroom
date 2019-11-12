package stroom.security.impl.db;

import org.jooq.Condition;
import org.jooq.Record;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.security.impl.UserDao;
import stroom.security.impl.db.jooq.tables.StroomUser;
import stroom.security.impl.db.jooq.tables.records.StroomUserRecord;
import stroom.security.shared.User;

import javax.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static stroom.security.impl.db.jooq.Tables.STROOM_USER;
import static stroom.security.impl.db.jooq.Tables.STROOM_USER_GROUP;

public class UserDaoImpl implements UserDao {
    private static final Function<Record, User> RECORD_TO_USER_MAPPER = record -> {
        final User user = new User();
        user.setId(record.get(STROOM_USER.ID));
        user.setVersion(record.get(STROOM_USER.VERSION));
        user.setCreateTimeMs(record.get(STROOM_USER.CREATE_TIME_MS));
        user.setCreateUser(record.get(STROOM_USER.CREATE_USER));
        user.setUpdateTimeMs(record.get(STROOM_USER.UPDATE_TIME_MS));
        user.setUpdateUser(record.get(STROOM_USER.UPDATE_USER));
        user.setName(record.get(STROOM_USER.NAME));
        user.setUuid(record.get(STROOM_USER.UUID));
        user.setGroup(record.get(STROOM_USER.IS_GROUP));
        user.setEnabled(record.get(STROOM_USER.ENABLED));
        return user;
    };

    private static final BiFunction<User, StroomUserRecord, StroomUserRecord> USER_TO_RECORD_MAPPER = (user, record) -> {
        record.from(user);
        record.set(STROOM_USER.ID, user.getId());
        record.set(STROOM_USER.VERSION, user.getVersion());
        record.set(STROOM_USER.CREATE_TIME_MS, user.getCreateTimeMs());
        record.set(STROOM_USER.CREATE_USER, user.getCreateUser());
        record.set(STROOM_USER.UPDATE_TIME_MS, user.getUpdateTimeMs());
        record.set(STROOM_USER.UPDATE_USER, user.getUpdateUser());
        record.set(STROOM_USER.NAME, user.getName());
        record.set(STROOM_USER.UUID, user.getUuid());
        record.set(STROOM_USER.IS_GROUP, user.isGroup());
        record.set(STROOM_USER.ENABLED, user.isEnabled());
        return record;
    };

    private final SecurityDbConnProvider securityDbConnProvider;
    private final GenericDao<StroomUserRecord, User, Integer> genericDao;

    @Inject
    public UserDaoImpl(final SecurityDbConnProvider securityDbConnProvider) {
        this.securityDbConnProvider = securityDbConnProvider;
        genericDao = new GenericDao<>(STROOM_USER, STROOM_USER.ID, User.class, securityDbConnProvider);
        genericDao.setObjectToRecordMapper(USER_TO_RECORD_MAPPER);
        genericDao.setRecordToObjectMapper(RECORD_TO_USER_MAPPER);
    }

    @Override
    public User create(final User user) {
        return genericDao.create(user);
    }

    @Override
    public User getById(final int id) {
        return genericDao.fetch(id).orElse(null);
    }

    @Override
    public User getByUuid(final String uuid) {
        return JooqUtil.contextResult(securityDbConnProvider, context ->
                context.select().from(STROOM_USER)
                        .where(STROOM_USER.UUID.eq(uuid))
                        .fetchOptional()
                        .map(RECORD_TO_USER_MAPPER)
                        .orElse(null));
    }

    @Override
    public User getByName(final String name) {
        return JooqUtil.contextResult(securityDbConnProvider, context ->
                context.select().from(STROOM_USER)
                        .where(STROOM_USER.NAME.eq(name))
                        .fetchOptional()
                        .map(RECORD_TO_USER_MAPPER)
                        .orElse(null));
    }

    @Override
    public User update(final User user) {
        return genericDao.update(user);
    }

    @Override
    public void delete(final String uuid) {
        JooqUtil.context(securityDbConnProvider, context ->
                context.deleteFrom(STROOM_USER)
                        .where(STROOM_USER.UUID.eq(uuid))
                        .execute()
        );
    }

    @Override
    public List<User> find(final String name, final Boolean group) {
        final Collection<Condition> conditions = JooqUtil.conditions(
                Optional.ofNullable(group).map(STROOM_USER.IS_GROUP::eq),
                Optional.ofNullable(name).map(STROOM_USER.NAME::eq));

        return JooqUtil.contextResult(securityDbConnProvider, context ->
                context.select().from(STROOM_USER)
                        .where(conditions)
                        .fetch()
                        .stream()
                        .map(RECORD_TO_USER_MAPPER)
                        .collect(Collectors.toList()));
    }

    @Override
    public List<User> findUsersInGroup(final String groupUuid) {
        return JooqUtil.contextResult(securityDbConnProvider, context ->
                context.select()
                        .from(STROOM_USER)
                        .join(STROOM_USER_GROUP)
                        .on(STROOM_USER.UUID.eq(STROOM_USER_GROUP.USER_UUID))
                        .where(STROOM_USER_GROUP.GROUP_UUID.eq(groupUuid))
                        .orderBy(STROOM_USER.NAME)
                        .fetch()
                        .stream()
                        .map(RECORD_TO_USER_MAPPER)
                        .collect(Collectors.toList()));
    }

    @Override
    public List<User> findGroupsForUser(final String userUuid) {
        return JooqUtil.contextResult(securityDbConnProvider, context ->
                context.select()
                        .from(STROOM_USER)
                        .join(STROOM_USER_GROUP)
                        .on(STROOM_USER.UUID.eq(STROOM_USER_GROUP.GROUP_UUID))
                        .where(STROOM_USER_GROUP.USER_UUID.eq(userUuid))
                        .orderBy(STROOM_USER.NAME)
                        .fetch()
                        .stream()
                        .map(RECORD_TO_USER_MAPPER)
                        .collect(Collectors.toList()));
    }

    @Override
    public List<User> findGroupsForUserName(final String userName) {
        StroomUser userUser = STROOM_USER.as("userUser");
        StroomUser groupUser = STROOM_USER.as("groupUser");
        return JooqUtil.contextResult(securityDbConnProvider, context ->
                context.select()
                        .from(groupUser)
                        // group users -> groups
                        .join(STROOM_USER_GROUP)
                        .on(groupUser.UUID.eq(STROOM_USER_GROUP.GROUP_UUID))
                        // users -> groups
                        .join(userUser)
                        .on(userUser.UUID.eq(STROOM_USER_GROUP.USER_UUID))
                        .where(userUser.NAME.eq(userName))
                        .orderBy(groupUser.NAME)
                        .fetch()
                        .stream()
                        .map(RECORD_TO_USER_MAPPER)
                        .collect(Collectors.toList()));
    }

    @Override
    public void addUserToGroup(final String userUuid,
                               final String groupUuid) {
        JooqUtil.context(securityDbConnProvider, context ->
                context.insertInto(STROOM_USER_GROUP)
                        .columns(STROOM_USER_GROUP.USER_UUID, STROOM_USER_GROUP.GROUP_UUID)
                        .values(userUuid, groupUuid)
                        .onDuplicateKeyIgnore()
                        .execute());
    }

    @Override
    public void removeUserFromGroup(final String userUuid,
                                    final String groupUuid) {
        JooqUtil.context(securityDbConnProvider, context ->
                context.deleteFrom(STROOM_USER_GROUP)
                        .where(STROOM_USER_GROUP.USER_UUID.eq(userUuid))
                        .and(STROOM_USER_GROUP.GROUP_UUID.eq(groupUuid))
                        .execute());
    }
}
