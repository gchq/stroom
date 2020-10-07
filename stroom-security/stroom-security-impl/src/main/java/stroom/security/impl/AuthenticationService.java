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

import stroom.security.shared.PermissionNames;
import stroom.security.shared.User;
import stroom.util.AuditUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.UUID;

@Singleton
class AuthenticationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationService.class);

    private static final String ADMINISTRATORS = "Administrators";
    private static final int GET_USER_ATTEMPTS = 2;

    private final UserDao userDao;
    private final AppPermissionDao appPermissionDao;
    private final OpenIdConfig openIdConfig;

    @Inject
    AuthenticationService(
            final UserDao userDao,
            final AppPermissionDao appPermissionDao,
            final OpenIdConfig openIdConfig) {
        this.userDao = userDao;
        this.appPermissionDao = appPermissionDao;
        this.openIdConfig = openIdConfig;
    }

    User getOrCreateUser(final String userId) {
        if (userId == null || userId.trim().length() == 0) {
            return null;
        }

        // Race conditions can mean that multiple processes kick off the creation of a user
        // The first one will succeed, but the others may clash. So we retrieve/create the user
        // in a loop to allow the failures caused by the race to be absorbed without failure
        int attempts = 0;
        Optional<User> optUser = Optional.empty();

        while (optUser.isEmpty()) {
            optUser = getUser(userId);

            if (optUser.isEmpty()) {
                // At this point the user has been authenticated using JWT.
                // If the user doesn't exist in the DB then we need to create them an account here, so Stroom has
                // some way of sensibly referencing the user and something to attach permissions to.
                // We need to elevate the user because no one is currently logged in.
                try {
                    optUser = Optional.of(create(userId, false));
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

        return optUser.orElseThrow(() -> new RuntimeException("Should have a user by this point"));
    }

    private User create(final String name, final boolean isGroup) {
        User user = new User();
        AuditUtil.stamp("AuthenticationServiceImpl", user);
        user.setUuid(UUID.randomUUID().toString());
        user.setName(name);
        user.setGroup(isGroup);

        return userDao.create(user);
    }

    public Optional<User> getUser(final String username) {
        Optional<User> optUser;

        try {
            optUser = userDao.getByName(username);
            if (optUser.isEmpty()
                    && openIdConfig.isUseInternal()
                    && User.ADMIN_USER_NAME.equals(username)) {

                // TODO @AT Probably should be an explicit command to create this to avoid the accidental
                //   running of stroom in UseInternal mode which then leaves admin/admin open
                // Using our internal identity provider so ensure the admin user is present
                // Can't do this for 3rd party IDPs as we don't know what the user name is
                optUser = Optional.of(createOrRefreshUser(User.ADMIN_USER_NAME));
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }

        return optUser;
    }

    public User createOrRefreshUser(final String name) {
        return createOrRefreshUserOrGroup(name, false);
    }

    public User createOrRefreshGroup(final String name) {
        return createOrRefreshUserOrGroup(name, true);
    }

    private User createOrRefreshUserOrGroup(final String name, final boolean isGroup) {
        return userDao.getByName(name)
                .orElseGet(() -> {
                    LOGGER.info("Creating {} {}", (isGroup ? "group" : "user"), name);

                    final User userRef = create(name, false);

                    // Creating the admin user so create its group too
                    if (User.ADMIN_USER_NAME.equals(name) && openIdConfig.isUseInternal()) {
                        try {
                            User userGroup = createOrRefreshAdminUserGroup();
                            userDao.addUserToGroup(userRef.getUuid(), userGroup.getUuid());
                        } catch (final RuntimeException e) {
                            // Expected.
                            LOGGER.debug(e.getMessage());
                        }
                    }
                    return userRef;
                });
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
        return userDao.getByName(userGroupName)
                .orElseGet(() -> {
                    final User newUserGroup = create(userGroupName, true);
                    try {
                        appPermissionDao.addPermission(newUserGroup.getUuid(), PermissionNames.ADMINISTRATOR);
                    } catch (final RuntimeException e) {
                        // Expected.
                        LOGGER.debug(e.getMessage());
                    }

                    return newUserGroup;
                });
    }
}
