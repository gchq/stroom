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

import stroom.security.openid.api.IdpType;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.User;
import stroom.util.AuditUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class AuthenticationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationService.class);

    private static final String ADMINISTRATORS_GROUP_SUBJECT_ID = "Administrators";
    private static final int GET_USER_ATTEMPTS = 2;

    private final UserDao userDao;
    private final AppPermissionDao appPermissionDao;
    private final Provider<StroomOpenIdConfig> openIdConfigProvider;

    @Inject
    AuthenticationService(
            final UserDao userDao,
            final AppPermissionDao appPermissionDao,
            final Provider<StroomOpenIdConfig> openIdConfigProvider) {
        this.userDao = userDao;
        this.appPermissionDao = appPermissionDao;
        this.openIdConfigProvider = openIdConfigProvider;
    }

    User getOrCreateUser(final String subjectId) {
        if (subjectId == null || subjectId.trim().length() == 0) {
            return null;
        }

        // Race conditions can mean that multiple processes kick off the creation of a user
        // The first one will succeed, but the others may clash. So we retrieve/create the user
        // in a loop to allow the failures caused by the race to be absorbed without failure
        int attempts = 0;
        Optional<User> optUser = Optional.empty();

        while (optUser.isEmpty()) {
            optUser = getUser(subjectId);

            if (optUser.isEmpty()) {
                // At this point the user has been authenticated using JWT.
                // If the user doesn't exist in the DB then we need to create them an account here, so Stroom has
                // some way of sensibly referencing the user and something to attach permissions to.
                // We need to elevate the user because no one is currently logged in.
                try {
                    optUser = Optional.of(create(subjectId, false));
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
                LOGGER.error("Failed to create user {} after {} attempts", subjectId, attempts);
                break;
            }
        }

        return optUser.orElseThrow(() -> new RuntimeException("Should have a user by this point"));
    }

    private User create(final String subjectId, final boolean isGroup) {
        return create(subjectId, UUID.randomUUID().toString(), isGroup);
    }

    private User create(final String subjectId,
                        final String userUuid,
                        final boolean isGroup) {
        Objects.requireNonNull(subjectId);
        Objects.requireNonNull(userUuid);
        final User user = new User();
        AuditUtil.stamp(() -> "AuthenticationServiceImpl", user);

        // This is the identifier for the stroom user, for authorisation, not authentication
        user.setUuid(userUuid);

        // This is the unique identifier that links the stroom User to the stroom account or an IDP account.
        // The id field/column is the surrogate primary key in the DB, unrelated to the IDP user ID, stroom account
        // or stroom user UUID.
        user.setSubjectId(subjectId);

        user.setGroup(isGroup);

        return userDao.tryCreate(user, createdUser -> {
            LOGGER.info("Created new stroom_user record, type: '{}', subjectId: '{}', userUuid: '{}'",
                    (isGroup
                            ? "group"
                            : "user"),
                    createdUser.getSubjectId(),
                    createdUser.getUuid());
        });
    }

    public Optional<User> getUser(final String subjectId) {
        Optional<User> optUser;

        try {
            optUser = userDao.getBySubjectId(subjectId, false);
            if (optUser.isEmpty() && shouldCreateAdminUser(subjectId)) {

                // TODO @AT Probably should be an explicit command to create this to avoid the accidental
                //   running of stroom in UseInternal mode which then leaves admin/admin open
                // Using our internal identity provider so ensure the admin user is present
                // Can't do this for 3rd party IDPs as we don't know what the user name is
                optUser = Optional.of(createOrRefreshUser(User.ADMIN_SUBJECT_ID));
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }

        return optUser;
    }

    private boolean shouldCreateAdminUser(final String subjectId) {
        return User.ADMIN_SUBJECT_ID.equals(subjectId)
                && (
                IdpType.INTERNAL_IDP.equals(openIdConfigProvider.get().getIdentityProviderType())
                        || IdpType.TEST_CREDENTIALS.equals(openIdConfigProvider.get().getIdentityProviderType()));
    }

    public User createOrRefreshUser(final String subjectId) {
        return createOrRefreshUserOrGroup(subjectId, false);
    }

//    public User createOrRefreshGroup(final String name) {
//        return createOrRefreshUserOrGroup(name, true);
//    }

    private User createOrRefreshUserOrGroup(final String subjectId, final boolean isGroup) {
        return userDao.getBySubjectId(subjectId, isGroup)
                .orElseGet(() -> {
                    LOGGER.info("Creating {} '{}'",
                            (isGroup
                                    ? "group"
                                    : "user"),
                            subjectId);

                    final User userRef = create(subjectId, isGroup);

                    // Creating the admin user so create its group too
                    if (shouldCreateAdminUser(subjectId)) {
                        try {
                            User userGroup = createOrRefreshAdminUserGroup(ADMINISTRATORS_GROUP_SUBJECT_ID);
                            userDao.addUserToGroup(userRef.getUuid(), userGroup.getUuid());
                        } catch (final RuntimeException e) {
                            // Expected.
                            LOGGER.debug(e.getMessage());
                        }
                    }
                    return userRef;
                });
    }

//    /**
//     * Enusure the admin user groups are created
//     *
//     * @return the full admin user group
//     */
//    private User createOrRefreshAdminUserGroup() {
//        return createOrRefreshAdminUserGroup(ADMINISTRATORS_GROUP_SUBJECT_ID);
//    }

    private User createOrRefreshAdminUserGroup(final String subjectId) {
        return userDao.getBySubjectId(subjectId, true)
                .orElseGet(() -> {
                    final User newUserGroup = create(subjectId, true);
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
