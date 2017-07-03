/*
 *
 *  * Copyright 2017 Crown Copyright
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package stroom.security.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.server.EntityServiceHelper;
import stroom.entity.server.FindServiceHelper;
import stroom.entity.server.QueryAppender;
import stroom.entity.server.util.FieldMap;
import stroom.entity.server.util.HqlBuilder;
import stroom.entity.server.util.SqlBuilder;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.FindNamedEntityCriteria;
import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.SQLNameConstants;
import stroom.security.Insecure;
import stroom.security.Secured;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.UserRef;
import stroom.util.config.StroomProperties;
import stroom.util.logging.StroomLogger;
import stroom.util.spring.StroomSpringProfiles;

import javax.inject.Inject;
import javax.persistence.PersistenceException;
import javax.persistence.Transient;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Transactional
@Profile(StroomSpringProfiles.PROD)
@Secured(FindUserCriteria.MANAGE_USERS_PERMISSION)
@Component("userService")
public class UserServiceImpl implements UserService {
    private static final String USER_NAME_PATTERN_PROPERTY = "stroom.security.userNamePattern";
    private static final String USER_NAME_PATTERN_VALUE = "^[a-zA-Z0-9_-]{3,}$";

    private static final StroomLogger LOGGER = StroomLogger.getLogger(UserServiceImpl.class);
    private static final String SQL_ADD_USER_TO_GROUP;
    private static final String SQL_REMOVE_USER_FROM_GROUP;
    /**
     * Password given out to default new users that has to be changed
     * immediately
     */
    private static final String TEMP_ADMIN_PASSWORD = "admin";

    static {
        SQL_ADD_USER_TO_GROUP = ""
                + "INSERT INTO "
                + UserGroupUser.TABLE_NAME
                + " ("
                + UserGroupUser.VERSION
                + ", "
                + UserGroupUser.USER_UUID
                + ", "
                + UserGroupUser.GROUP_UUID
                + ")"
                + " VALUES (?,?,?)";
    }

    static {
        SQL_REMOVE_USER_FROM_GROUP = ""
                + "DELETE FROM "
                + UserGroupUser.TABLE_NAME
                + " WHERE "
                + UserGroupUser.USER_UUID
                + " = ?"
                + " AND "
                + UserGroupUser.GROUP_UUID
                + " = ?";
    }

    private final StroomEntityManager entityManager;
    private final PasswordEncoder passwordEncoder;
    private final boolean neverExpire;

    private final EntityServiceHelper<User> entityServiceHelper;
    private final FindServiceHelper<User, FindUserCriteria> findServiceHelper;
    private final DocumentPermissionService documentPermissionService;

    private final QueryAppender<User, FindUserCriteria> queryAppender;

    private String entityType;
    private FieldMap fieldMap;

    @Inject
    UserServiceImpl(final StroomEntityManager entityManager,
                    final PasswordEncoder passwordEncoder,
                    @Value("#{propertyConfigurer.getProperty('stroom.developmentMode')}") final boolean neverExpire,
                    final DocumentPermissionService documentPermissionService) {
        this.entityManager = entityManager;
        this.passwordEncoder = passwordEncoder;
        this.neverExpire = neverExpire;
        this.documentPermissionService = documentPermissionService;

        this.queryAppender = createQueryAppender(entityManager);
        this.entityServiceHelper = new EntityServiceHelper<>(entityManager, getEntityClass(), queryAppender);
        this.findServiceHelper = new FindServiceHelper<>(entityManager, getEntityClass(), queryAppender);
    }

    protected QueryAppender<User, FindUserCriteria> createQueryAppender(final StroomEntityManager entityManager) {
        return new QueryAppender(entityManager);
    }


//    @Override
//    public String getNamePattern() {
//        return NAME_PATTERN;
//    }


    /**
     * @param criteria for the search
     * @return list of Users
     */
    @SuppressWarnings("unchecked")
    @Override
    @Transactional(readOnly = true)
    public BaseResultList<User> find(final FindUserCriteria criteria) {
        // Build up the HQL
        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT user FROM ");
        sql.append(User.class.getName());
        sql.append(" as user");

//        appendBasicJoin(sql, "user", criteria.getFetchSet());

        sql.append(" WHERE 1=1"); // Avoid conditional AND's

        sql.appendValueQuery("user.name", criteria.getName());

        sql.appendValueQuery("user.group", criteria.getGroup());

        sql.appendValueQuery("user.pstatus", criteria.getUserStatus());

        sql.appendRangeQuery("user.lastLoginMs", criteria.getLastLoginPeriod());

        sql.appendRangeQuery("user.loginValidMs", criteria.getLoginValidPeriod());

        sql.appendOrderBy(getFieldMap().getHqlFieldMap(), criteria, "user");

        // Create the query
        return BaseResultList.createCriterialBasedList(entityManager.executeQueryResultList(sql, criteria),
                criteria);
    }

    @Insecure
    @Override
    public UserRef getUserByName(final String name) {
        final FindUserCriteria findUserCriteria = new FindUserCriteria(name, false);
        final BaseResultList<User> users = find(findUserCriteria);
        if (users != null) {
            final User user = users.getFirst();
            if (user != null) {
                return UserRefFactory.create(user);
            }
        }

        return null;
    }

    @Insecure
    @Override
    public UserRef getUserGroupByName(final String name) {
        final FindUserCriteria findUserCriteria = new FindUserCriteria(name, true);
        final BaseResultList<User> users = find(findUserCriteria);
        if (users != null) {
            final User user = users.getFirst();
            if (user != null) {
                return UserRefFactory.create(user);
            }
        }

        return null;
    }

    @Insecure
    @Override
    public List<UserRef> findUsersInGroup(final UserRef userGroup) {
        final SqlBuilder sql = new SqlBuilder();
        sql.append("SELECT");
        sql.append(" u.*");
        sql.append(" FROM ");
        sql.append(User.TABLE_NAME);
        sql.append(" u");
        sql.append(" JOIN ");
        sql.append(UserGroupUser.TABLE_NAME);
        sql.append(" ugu");
        sql.append(" ON");
        sql.append(" (u.");
        sql.append(User.UUID);
        sql.append(" = ugu.");
        sql.append(UserGroupUser.USER_UUID);
        sql.append(")");
        sql.append(" WHERE");
        sql.append(" ugu.");
        sql.append(UserGroupUser.GROUP_UUID);
        sql.append(" = ");
        sql.arg(userGroup.getUuid());
        sql.append(" ORDER BY");
        sql.append(" u.");
        sql.append(User.NAME);

        return toRefList(entityManager.executeNativeQueryResultList(sql, User.class));
    }

    @Insecure
    @Override
    public List<UserRef> findGroupsForUser(final UserRef user) {
        final SqlBuilder sql = new SqlBuilder();
        sql.append("SELECT");
        sql.append(" u.*");
        sql.append(" FROM ");
        sql.append(User.TABLE_NAME);
        sql.append(" u");
        sql.append(" JOIN ");
        sql.append(UserGroupUser.TABLE_NAME);
        sql.append(" ugu");
        sql.append(" ON");
        sql.append(" (u.");
        sql.append(User.UUID);
        sql.append(" = ugu.");
        sql.append(UserGroupUser.GROUP_UUID);
        sql.append(")");
        sql.append(" WHERE");
        sql.append(" ugu.");
        sql.append(UserGroupUser.USER_UUID);
        sql.append(" = ");
        sql.arg(user.getUuid());
        sql.append(" ORDER BY");
        sql.append(" u.");
        sql.append(User.NAME);

        return toRefList(entityManager.executeNativeQueryResultList(sql, User.class));
    }

    @Override
    public UserRef createUser(final String name) {
        final User user = new User();
        user.setName(name);
        user.setGroup(false);
        return UserRefFactory.create(save(user));
    }

    @Override
    public UserRef createUserGroup(final String name) {
        final User user = new User();
        user.setName(name);
        user.setGroup(true);
        return UserRefFactory.create(save(user));
    }

    @Override
    public void addUserToGroup(final UserRef user, final UserRef userGroup) {
        try {
            final SqlBuilder sqlBuilder = new SqlBuilder(SQL_ADD_USER_TO_GROUP, 1, user.getUuid(), userGroup.getUuid());
            entityManager.executeNativeUpdate(sqlBuilder);
        } catch (final PersistenceException e) {
            // Expected exception.
            LOGGER.debug("addUserToGroup()", e);
            throw e;
        } catch (final RuntimeException e) {
            LOGGER.error("addUserToGroup()", e);
            throw e;
        }
    }

    @Override
    public void removeUserFromGroup(final UserRef user, final UserRef userGroup) {
        try {
            final SqlBuilder sqlBuilder = new SqlBuilder(SQL_REMOVE_USER_FROM_GROUP, user.getUuid(), userGroup.getUuid());
            entityManager.executeNativeUpdate(sqlBuilder);
        } catch (final RuntimeException e) {
            LOGGER.error("removeUserFromGroup()", e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    @Override
    public User load(final User entity) throws RuntimeException {
        return entityServiceHelper.load(entity);
    }

    @Transactional(readOnly = true)
    @Override
    public User load(final User entity, final Set<String> fetchSet) throws RuntimeException {
        return entityServiceHelper.load(entity, fetchSet);
    }

    @Override
    public final User loadByUuid(final String uuid) throws RuntimeException {
        return entityServiceHelper.loadByUuid(uuid);
    }

    @SuppressWarnings("unchecked")
    @Override
    public final User loadByUuid(final String uuid, final Set<String> fetchSet) throws RuntimeException {
        return entityServiceHelper.loadByUuid(uuid, fetchSet);
    }

    @Override
    public User save(final User user) {
        // If this is a new system user then create an initial password.
        if (!user.isPersistent()) {
            user.setUuid(UUID.randomUUID().toString());

            if (user.getPasswordHash() == null) {
                // Give the initial admin account admin as the password
                // otherwise generate a random one. The UI will then try and
                // reset this.
                final String rawPassword = INITIAL_ADMIN_ACCOUNT.equals(user.getName()) ? TEMP_ADMIN_PASSWORD
                        : PasswordGenerator.generatePassword();
                final String passwordHash = passwordEncoder.encode(rawPassword);
                user.setPasswordHash(passwordHash);
            }

            if (user.getPasswordExpiryMs() == null) {
                if (neverExpire) {
                    user.setLoginExpiry(false);
                } else {
                    user.setPasswordExpiryMs(System.currentTimeMillis());
                }
            }

            return entityServiceHelper.save(user);
        } else {
            return entityServiceHelper.save(user);
        }
    }

    @Override
    public Boolean delete(final User entity) throws RuntimeException {
        final Boolean success = entityServiceHelper.delete(entity);

        // Delete any document permissions associated with this user.
        try {
            if (documentPermissionService != null && Boolean.TRUE.equals(success)) {
                documentPermissionService.clearUserPermissions(UserRefFactory.create(entity));
            }
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        return success;
    }

    private List<UserRef> toRefList(final List<User> list) {
        final List<UserRef> refs = new ArrayList<>(list.size());
        list.stream().forEach(user -> refs.add(UserRefFactory.create(user)));
        return refs;
    }

    @Override
    public Class<User> getEntityClass() {
        return User.class;
    }

    @Override
    public String getEntityType() {
        if (entityType == null) {
            try {
                entityType = getEntityClass().newInstance().getType();
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }
        return entityType;
    }

    @Transient
    @Override
    public String getNamePattern() {
        return StroomProperties.getProperty(USER_NAME_PATTERN_PROPERTY, USER_NAME_PATTERN_VALUE);
    }

    @Override
    public FindUserCriteria createCriteria() {
        return new FindUserCriteria();
    }

    private FieldMap createFieldMap() {
        return new FieldMap()
                .add(BaseCriteria.FIELD_ID, BaseEntity.ID, "id")
                .add(FindNamedEntityCriteria.FIELD_NAME, NamedEntity.NAME, "name")
                .add(FindUserCriteria.FIELD_STATUS, SQLNameConstants.STATUS, "pstatus")
                .add(FindUserCriteria.FIELD_LAST_LOGIN, User.LAST_LOGIN_MS, "lastLoginMs");
    }

    private FieldMap getFieldMap() {
        if (fieldMap == null) {
            fieldMap = createFieldMap();
        }
        return fieldMap;
    }
}
