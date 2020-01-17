/*
 * Copyright 2016 Crown Copyright
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

package stroom.security.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.User;
import stroom.util.AuditUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.UUID;

@Singleton
class AuthenticationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationService.class);

    private static final String ADMINISTRATORS = "Administrators";
    private static final int GET_USER_ATTEMPTS = 2;

    private final UserDao userDao;
    private final AppPermissionDao appPermissionDao;

    @Inject
    AuthenticationService(
            final UserDao userDao,
            final AppPermissionDao appPermissionDao) {
        this.userDao = userDao;
        this.appPermissionDao = appPermissionDao;
    }

    User getUser(final String userId) {
        if (userId == null || userId.trim().length() == 0) {
            return null;
        }

        // Race conditions can mean that multiple processes kick off the creation of a user
        // The first one will succeed, but the others may clash. So we retrieve/create the user
        // in a loop to allow the failures caused by the race to be absorbed without failure
        int attempts = 0;
        User user = null;

        while (user == null) {
            user = loadUserByUsername(userId);

            if (user == null) {
                // At this point the user has been authenticated using JWT.
                // If the user doesn't exist in the DB then we need to create them an account here, so Stroom has
                // some way of sensibly referencing the user and something to attach permissions to.
                // We need to elevate the user because no one is currently logged in.
                try {
                    user = create(userId, false);
                } catch (final Exception e) {
                    final String msg = String.format("Could not create user, this is attempt %d", attempts);
                    if (attempts == 0) {
                        LOGGER.warn(msg);
                    } else {
                        LOGGER.info(msg);
                    }
                }
            }

            if (attempts++ > GET_USER_ATTEMPTS) {
                break;
            }
        }

        return user;
    }

    private User create(final String name, final boolean isGroup) {
        User user = new User();
        AuditUtil.stamp("AuthenticationServiceImpl", user);
        user.setUuid(UUID.randomUUID().toString());
        user.setName(name);
        user.setGroup(isGroup);

        return userDao.create(user);
    }

    private User loadUserByUsername(final String username) {
        User userRef;

        try {
            userRef = userDao.getByName(username);
            if (userRef == null) {
                // The requested system user does not exist.
                if (User.ADMIN_USER_NAME.equals(username)) {
                    userRef = createOrRefreshUser(User.ADMIN_USER_NAME);
                }
            }

        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }

        return userRef;
    }

    private User createOrRefreshUser(final String name) {
        User userRef = userDao.getByName(name);
        if (userRef == null) {
            if (User.ADMIN_USER_NAME.equals(name)) {
                LOGGER.info("Creating user {}", name);
            }

            userRef = create(name, false);

            final User userGroup = createOrRefreshAdminUserGroup();
            try {
                userDao.addUserToGroup(userRef.getUuid(), userGroup.getUuid());
            } catch (final RuntimeException e) {
                // Expected.
                LOGGER.debug(e.getMessage());
            }
        }

        return userRef;
    }

    /**
     * Enusure the admin user groups are created
     *
     * @return the full admin user group
     */
    private User createOrRefreshAdminUserGroup() {
        return createOrRefreshAdminUserGroup(ADMINISTRATORS);
    }

    private User createOrRefreshAdminUserGroup(final String userGroupName) {
        final User existing = userDao.getByName(userGroupName);
        if (existing != null) {
            return existing;
        }

        final User newUserGroup = create(userGroupName, true);
        try {
            appPermissionDao.addPermission(newUserGroup.getUuid(), PermissionNames.ADMINISTRATOR);
        } catch (final RuntimeException e) {
            // Expected.
            LOGGER.debug(e.getMessage());
        }

        return newUserGroup;
    }
}
