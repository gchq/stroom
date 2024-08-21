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

package stroom.security.impl;

import stroom.security.api.ContentPackUserService;
import stroom.security.api.SecurityContext;
import stroom.security.impl.event.PermissionChangeEvent;
import stroom.security.impl.event.PermissionChangeEventBus;
import stroom.security.shared.AppPermission;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.util.AuditUtil;
import stroom.util.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserDesc;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;

import java.util.Optional;
import java.util.function.Consumer;

class UserServiceImpl implements UserService, ContentPackUserService {

    private final SecurityContext securityContext;
    private final UserDao userDao;
    private final PermissionChangeEventBus permissionChangeEventBus;

    @Inject
    UserServiceImpl(final SecurityContext securityContext,
                    final UserDao userDao,
                    final PermissionChangeEventBus permissionChangeEventBus) {
        this.securityContext = securityContext;
        this.userDao = userDao;
        this.permissionChangeEventBus = permissionChangeEventBus;
    }

    @Override
    public User getOrCreateUser(final UserDesc userDesc, final Consumer<User> onCreateAction) {
        final Optional<User> optional = userDao.getUserBySubjectId(userDesc.getSubjectId());
        final User persisted = optional.orElseGet(() -> {
            final User user = new User();
            AuditUtil.stamp(securityContext, user);
            user.setSubjectId(userDesc.getSubjectId());
            user.setDisplayName(userDesc.getDisplayName());
            user.setFullName(userDesc.getFullName());
            user.setGroup(false);

            return securityContext.secureResult(AppPermission.MANAGE_USERS_PERMISSION, () ->
                    userDao.tryCreate(user, persistedUser -> {
                        fireUserChangeEvent(persistedUser.getUuid());
                        if (onCreateAction != null) {
                            onCreateAction.accept(persistedUser);
                        }
                    }));
        });

        return persisted;
    }

    @Override
    public User getOrCreateUserGroup(final String name, final Consumer<User> onCreateAction) {
        final Optional<User> optional = userDao.getGroupByName(name);
        final User persisted = optional.orElseGet(() -> {
            final User user = new User();
            AuditUtil.stamp(securityContext, user);
            user.setSubjectId(name);
            user.setDisplayName(name);
            user.setGroup(true);

            return securityContext.secureResult(AppPermission.MANAGE_USERS_PERMISSION, () ->
                    userDao.tryCreate(user, persistedUser -> {
                        fireUserChangeEvent(persistedUser.getUuid());
                        if (onCreateAction != null) {
                            onCreateAction.accept(persistedUser);
                        }
                    }));
        });

        return persisted;
    }

    @Override
    public Optional<User> getUserBySubjectId(final String subjectId) {
        if (!NullSafe.isBlankString(subjectId)) {
            return userDao.getUserBySubjectId(subjectId);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<User> getGroupByName(final String groupName) {
        if (!NullSafe.isBlankString(groupName)) {
            return userDao.getGroupByName(groupName);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<User> loadByUuid(final String uuid) {
        return userDao.getByUuid(uuid);
    }

    @Override
    public User update(User user) {
        AuditUtil.stamp(securityContext, user);
        return securityContext.secureResult(AppPermission.MANAGE_USERS_PERMISSION, () -> {
            final User updatedUser = userDao.update(user);

            // If the updated user is a group then we need to let all chldren know there has been a change as we cache
            // parent groups for children.
            if (updatedUser.isGroup()) {
                final ResultPage<User> resultPage = findUsersInGroup(updatedUser.getUuid(), new FindUserCriteria());
                for (final User child : resultPage.getValues()) {
                    fireUserChangeEvent(child.getUuid());
                }
            }

            fireUserChangeEvent(updatedUser.getUuid());
            return updatedUser;
        });
    }

    @Override
    public ResultPage<User> find(final FindUserCriteria criteria) {
        return securityContext.secureResult(() -> userDao.find(criteria));
    }

    @Override
    public ResultPage<User> findUsersInGroup(final String groupUuid, final FindUserCriteria criteria) {
        // See if the user is allowed to see the requested group.
        if (!securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)) {
            throw new PermissionException(
                    securityContext.getUserRef(),
                    "You do not have permission to manage users.");
        }
        return userDao.findUsersInGroup(groupUuid, criteria);
    }

    @Override
    public ResultPage<User> findGroupsForUser(final String userUuid, final FindUserCriteria criteria) {
        // See if the user is allowed to see for the requested user.
        if (!securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)) {
            if (!securityContext.getUserRef().getUuid().equals(userUuid)) {
                throw new PermissionException(
                        securityContext.getUserRef(),
                        "You are only allowed to see your own groups.");
            }
        }
        return userDao.findGroupsForUser(userUuid, criteria);
    }

    @Override
    public Boolean addUserToGroup(final String userUuid, final String groupUuid) {
        securityContext.secure(AppPermission.MANAGE_USERS_PERMISSION, () -> {
            userDao.addUserToGroup(userUuid, groupUuid);
            fireUserChangeEvent(userUuid);
        });
        return true;
    }

    @Override
    public Boolean removeUserFromGroup(final String userUuid, final String groupUuid) {
        securityContext.secure(AppPermission.MANAGE_USERS_PERMISSION, () -> {
            userDao.removeUserFromGroup(userUuid, groupUuid);
            fireUserChangeEvent(userUuid);
        });
        return true;
    }

    private void fireUserChangeEvent(final String userUuid) {
        PermissionChangeEvent.fire(permissionChangeEventBus, UserRef
                .builder()
                .uuid(userUuid)
                .build(), null);
    }

    @Deprecated
    @Override
    public UserRef getUserRef(final String subjectId, final boolean isGroup) {
        if (isGroup) {
            return userDao.getGroupByName(subjectId).map(User::asRef).orElse(null);
        }
        return userDao.getUserBySubjectId(subjectId).map(User::asRef).orElse(null);
    }
}
