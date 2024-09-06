package stroom.security.impl.db;

import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.security.api.UserIdentityFactory;
import stroom.security.impl.UserDao;
import stroom.security.impl.db.jooq.tables.StroomUser;
import stroom.security.impl.db.jooq.tables.records.StroomUserRecord;
import stroom.security.shared.User;
import stroom.util.NullSafe;
import stroom.util.filter.QuickFilterPredicateFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.Record1;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static stroom.security.impl.db.jooq.Tables.STROOM_USER;
import static stroom.security.impl.db.jooq.Tables.STROOM_USER_GROUP;

public class UserDaoImpl implements UserDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserDaoImpl.class);

    private static final Function<Record, User> RECORD_TO_USER_MAPPER = record -> {
        final User user = new User();
        // This is just the PK of the table.
        user.setId(record.get(STROOM_USER.ID));
        user.setVersion(record.get(STROOM_USER.VERSION));
        user.setCreateTimeMs(record.get(STROOM_USER.CREATE_TIME_MS));
        user.setCreateUser(record.get(STROOM_USER.CREATE_USER));
        user.setUpdateTimeMs(record.get(STROOM_USER.UPDATE_TIME_MS));
        user.setUpdateUser(record.get(STROOM_USER.UPDATE_USER));
        // This is the unique 'sub'/'oid' when using OIDC
        // or it is the username if using internal IDP
        // or it is the group name.
        user.setSubjectId(record.get(STROOM_USER.NAME));
        // Our own UUID for the user, independent of any identity provider
        user.setUuid(record.get(STROOM_USER.UUID));
        user.setGroup(record.get(STROOM_USER.IS_GROUP));
        // Optional
        user.setDisplayName(record.get(STROOM_USER.DISPLAY_NAME));
        // Optional
        user.setFullName(record.get(STROOM_USER.FULL_NAME));
        return user;
    };

    private static final BiFunction<User, StroomUserRecord, StroomUserRecord> USER_TO_RECORD_MAPPER =
            (user, record) -> {
                record.from(user);
                record.set(STROOM_USER.ID, user.getId());
                record.set(STROOM_USER.VERSION, user.getVersion());
                record.set(STROOM_USER.CREATE_TIME_MS, user.getCreateTimeMs());
                record.set(STROOM_USER.CREATE_USER, user.getCreateUser());
                record.set(STROOM_USER.UPDATE_TIME_MS, user.getUpdateTimeMs());
                record.set(STROOM_USER.UPDATE_USER, user.getUpdateUser());
                // This is the unique 'sub'/'oid' when using OIDC
                // or it is the username if using internal IDP
                // or it is the group name.
                record.set(STROOM_USER.NAME, user.getSubjectId());
                // Our own UUID for the user, independent of any identity provider
                record.set(STROOM_USER.UUID, user.getUuid());
                record.set(STROOM_USER.IS_GROUP, user.isGroup());
                record.set(STROOM_USER.ENABLED, true);
                // Optional
                record.set(STROOM_USER.DISPLAY_NAME, user.getDisplayName());
                // Optional
                record.set(STROOM_USER.FULL_NAME, user.getFullName());
                return record;
            };


    private final SecurityDbConnProvider securityDbConnProvider;
    private final GenericDao<StroomUserRecord, User, Integer> genericDao;
    private final UserIdentityFactory userIdentityFactory;

    @Inject
    public UserDaoImpl(final SecurityDbConnProvider securityDbConnProvider,
                       final UserIdentityFactory userIdentityFactory) {
        this.securityDbConnProvider = securityDbConnProvider;

        genericDao = new GenericDao<>(
                securityDbConnProvider,
                STROOM_USER,
                STROOM_USER.ID,
                USER_TO_RECORD_MAPPER,
                RECORD_TO_USER_MAPPER);
        this.userIdentityFactory = userIdentityFactory;
    }

    @Override
    public User create(final User user) {
        return genericDao.create(user);
    }

    @Override
    public User tryCreate(final User user, final Consumer<User> onUserCreateAction) {
        return genericDao.tryCreate(user, STROOM_USER.NAME, STROOM_USER.IS_GROUP, onUserCreateAction);
    }

    @Override
    public Optional<User> getById(final int id) {
        // By DB PK
        return genericDao.fetch(id);
    }

    @Override
    public Optional<User> getByUuid(final String uuid) {
        return JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select()
                        .from(STROOM_USER)
                        .where(STROOM_USER.UUID.eq(uuid))
                        .fetchOptional())
                .map(RECORD_TO_USER_MAPPER);
    }

    @Override
    public Set<User> getByUuids(final Collection<String> userUuids) {
        if (NullSafe.isEmptyCollection(userUuids)) {
            return Collections.emptySet();
        } else {
            return JooqUtil.contextResult(securityDbConnProvider, context -> context
                            .select()
                            .from(STROOM_USER)
                            .where(STROOM_USER.UUID.in(userUuids))
                            .fetch())
                    .stream()
                    .map(RECORD_TO_USER_MAPPER)
                    .collect(Collectors.toSet());
        }
    }

    @Override
    public Optional<User> getBySubjectId(final String subjectId) {
        // TODO the plan is to change user table so subject_id is fully unique,
        //  i.e. a group is uniquely identified by a uuid which goes in the subjectId col
        //  and the friendly group name goes in the displayName col,
        //  so once this is done this can be returned to the commented code
//        return JooqUtil.contextResult(securityDbConnProvider, context -> context
//                        .select()
//                        .from(STROOM_USER)
//                        .where(STROOM_USER.NAME.eq(subjectId))
//                        .fetchOptional())
//                .map(RECORD_TO_USER_MAPPER);

        final List<User> users = JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select()
                        .from(STROOM_USER)
                        .where(STROOM_USER.NAME.eq(subjectId))
                        .fetch())
                .stream()
                .map(RECORD_TO_USER_MAPPER)
                .toList();
        if (users.size() > 1) {
            throw new RuntimeException(LogUtil.message(
                    "Found more than one user/group ({}) with subject ID: '{}'", users.size(), subjectId));
        }
        return users.stream().findFirst();
    }

    @Override
    public Optional<User> getByDisplayName(final String displayName) {
        // The user name displayed in the UI could be the displayName or the unique name
        // depending on whether the user has a display name or not, so try both.
        final List<User> users = JooqUtil.contextResult(securityDbConnProvider, context -> context
                .select()
                .from(STROOM_USER)
                .where(STROOM_USER.DISPLAY_NAME.eq(displayName))
                .fetch(RECORD_TO_USER_MAPPER::apply));

        // Technically display name is not unique, (but it probably is) however things
        // like annotations need to map from a displayName to a user record so we can only return one.
        final Optional<User> optUser;
        if (users.size() > 1) {
            optUser = users.stream()
                    .min(Comparator.comparing(User::getCreateTimeMs));
            final String userUuid = optUser.map(User::getUuid).orElse(null);
            final String subjectId = optUser.map(User::getSubjectId).orElse(null);
            LOGGER.error("Found {} users with the same display_name ('{}'). " +
                            "Using user with subjectId: '{}' and userUuid: '{}'. Duplicate display names will cause " +
                            "problems for anything mapping from a display name back to a user (e.g. annotations).",
                    users.size(),
                    displayName,
                    subjectId,
                    userUuid);
        } else {
            optUser = users.stream()
                    .findFirst();
        }
        return optUser;
    }

    @Override
    public Optional<User> getBySubjectId(final String subjectId,
                                         final boolean isGroup) {
        return JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select()
                        .from(STROOM_USER)
                        .where(STROOM_USER.NAME.eq(subjectId))
                        .and(STROOM_USER.IS_GROUP.eq(isGroup))
                        .fetchOptional())
                .map(RECORD_TO_USER_MAPPER);
    }

    @Override
    public User update(final User user) {
        return genericDao.update(user);
    }

    @Override
    public void delete(final String uuid) {
        JooqUtil.context(securityDbConnProvider, context -> context
                .deleteFrom(STROOM_USER)
                .where(STROOM_USER.UUID.eq(uuid))
                .execute()
        );
    }

    @Override
    public List<User> find(final String quickFilterInput,
                           final boolean isGroup) {
        final Condition condition = STROOM_USER.IS_GROUP.eq(isGroup);

        return QuickFilterPredicateFactory.filterStream(
                quickFilterInput,
                FILTER_FIELD_MAPPERS,
                JooqUtil.contextResult(securityDbConnProvider, context -> context
                                .select()
                                .from(STROOM_USER)
                                .where(condition)
                                .and(getExcludedUsersCondition())
                                .orderBy(STROOM_USER.NAME)
                                .fetch())
                        .stream()
                        .map(RECORD_TO_USER_MAPPER)
        ).collect(Collectors.toList());
    }

    @Override
    public List<User> findUsersInGroup(final String groupUuid, final String quickFilterInput) {
        return QuickFilterPredicateFactory.filterStream(
                quickFilterInput,
                FILTER_FIELD_MAPPERS,
                JooqUtil.contextResult(securityDbConnProvider, context -> context
                                .select()
                                .from(STROOM_USER)
                                .join(STROOM_USER_GROUP)
                                .on(STROOM_USER.UUID.eq(STROOM_USER_GROUP.USER_UUID))
                                .where(STROOM_USER_GROUP.GROUP_UUID.eq(groupUuid))
                                .and(getExcludedUsersCondition())
                                .fetch())
                        .stream()
                        .map(RECORD_TO_USER_MAPPER)
        ).collect(Collectors.toList());
    }

    @Override
    public List<User> findGroupsForUser(final String userUuid, final String quickFilterInput) {
        return QuickFilterPredicateFactory.filterStream(
                quickFilterInput,
                FILTER_FIELD_MAPPERS,
                JooqUtil.contextResult(securityDbConnProvider, context -> context
                                .select()
                                .from(STROOM_USER)
                                .join(STROOM_USER_GROUP)
                                .on(STROOM_USER.UUID.eq(STROOM_USER_GROUP.GROUP_UUID))
                                .where(STROOM_USER_GROUP.USER_UUID.eq(userUuid))
                                .fetch())
                        .stream()
                        .map(RECORD_TO_USER_MAPPER)
        ).collect(Collectors.toList());
    }

    @Override
    public Set<String> findGroupUuidsForUser(final String userUuid) {
        return JooqUtil.contextResult(securityDbConnProvider, context ->
                        context.select(STROOM_USER_GROUP.GROUP_UUID)
                                .from(STROOM_USER_GROUP)
                                .where(STROOM_USER_GROUP.USER_UUID.eq(userUuid))
                                .fetch())
                .stream()
                .map(Record1::value1)
                .collect(Collectors.toSet());
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
                                .fetch())
                .map(RECORD_TO_USER_MAPPER::apply);
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

    private Condition getExcludedUsersCondition() {
        final String procUserSubjectId = userIdentityFactory.getServiceUserIdentity().getSubjectId();
        return STROOM_USER.NAME.notEqual(procUserSubjectId);
    }
}
