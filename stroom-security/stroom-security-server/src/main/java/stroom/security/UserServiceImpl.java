/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.QueryAppender;
import stroom.entity.StroomEntityManager;
import stroom.security.impl.db.UserDao;
import stroom.security.impl.db.UserJooq;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.UserRef;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.Transient;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
class UserServiceImpl implements UserService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);

    private final Security security;

    private final DocumentPermissionService documentPermissionService;
    private final AuthenticationConfig securityConfig;
    private final UserDao userDao;

    @Inject
    UserServiceImpl(final Security security,
                    final DocumentPermissionService documentPermissionService,
                    final AuthenticationConfig securityConfig,
                    final UserDao userDao) {
        this.security = security;
        this.documentPermissionService = documentPermissionService;
        this.securityConfig = securityConfig;
        this.userDao = userDao;
    }

    private QueryAppender<User, FindUserCriteria> createQueryAppender(final StroomEntityManager entityManager) {
        return new QueryAppender<>(entityManager);
    }

    @Override
    public UserRef getUserByName(final String name) {
        if (name != null && name.trim().length() > 0) {
            UserJooq user = userDao.getUserByName(name);
            if (user != null) {
                // Make sure this is the user that was requested.
                if (!user.getName().equals(name)) {
                    throw new RuntimeException("Unexpected: returned user name does not match requested user name");
                }
                return UserRefFactory.create(user);
            }
        }

        return null;
    }

    @Override
    public List<User> find(final FindUserCriteria criteria) {
        // TODO I.O.U one implementation of this
        return Collections.emptyList();
    }

    @Override
    public List<UserRef> findUsersInGroup(final UserRef userGroup) {
        return userDao.findUsersInGroup(userGroup.getUuid()).stream()
                .map(UserRefFactory::create)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserRef> findGroupsForUser(final UserRef user) {
        return userDao.findGroupsForUser(user.getUuid()).stream()
                .map(UserRefFactory::create)
                .collect(Collectors.toList());
    }

    @Override
    public UserRef createUser(final String name) {
        return security.secureResult(PermissionNames.MANAGE_USERS_PERMISSION,
                () -> Optional.of(userDao.createUser(name)).map(UserRefFactory::create).orElse(null)
        );
    }

    @Override
    public UserRef createUserGroup(final String name) {
        return security.secureResult(PermissionNames.MANAGE_USERS_PERMISSION,
                () -> Optional.of(userDao.createUserGroup(name)).map(UserRefFactory::create).orElse(null)
        );
    }

    @Override
    public User loadByUuid(final String uuid) {
        return Optional.of(userDao.getByUuid(uuid))
                .map(this::fromJooq)
                .orElse(null);
    }

    @Override
    public User save(User user) {
        // TODO
        return null;
    }

    private User fromJooq(final UserJooq userJooq) {
        final User user = new User();
        user.setId(userJooq.getId());
        user.setUuid(userJooq.getUuid());
        user.setName(userJooq.getName());
        user.setGroup(userJooq.isGroup());
        return user;
    }

    @Override
    public Boolean delete(User user) {
        return security.secureResult(PermissionNames.MANAGE_USERS_PERMISSION,
                () -> userDao.deleteUser(user.getUuid()));
    }

    @Override
    public void addUserToGroup(final UserRef user, final UserRef userGroup) {
        security.secure(PermissionNames.MANAGE_USERS_PERMISSION, () -> {
            userDao.addUserToGroup(user.getUuid(), userGroup.getUuid());
        });
    }

    @Override
    public void removeUserFromGroup(final UserRef user, final UserRef userGroup) {
        security.secure(PermissionNames.MANAGE_USERS_PERMISSION, () -> {
            userDao.removeUserFromGroup(user.getUuid(), userGroup.getUuid());
        });
    }


    @Transient
    @Override
    public String getNamePattern() {
        return securityConfig.getUserNamePattern();
    }
}
