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

import stroom.docref.DocRef;
import stroom.security.api.ContentPackUserService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.util.AuditUtil;
import stroom.util.NullSafe;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserDesc;
import stroom.util.shared.UserDocRefUtil;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

class UserServiceImpl implements UserService, ContentPackUserService {

    private final SecurityContext securityContext;
    private final UserDao userDao;
    private final EntityEventBus eventBus;

    @Inject
    UserServiceImpl(final SecurityContext securityContext,
                    final UserDao userDao,
                    final EntityEventBus eventBus) {
        this.securityContext = securityContext;
        this.userDao = userDao;
        this.eventBus = eventBus;
    }

    @Override
    public User getOrCreateUser(final UserDesc userDesc, final Consumer<User> onCreateAction) {
        final Optional<User> optional = userDao.getUserBySubjectId(userDesc.getSubjectId());
        return optional.orElseGet(() -> {
            final User user = new User();
            AuditUtil.stamp(securityContext, user);
            user.setUuid(UUID.randomUUID().toString());
            user.setSubjectId(userDesc.getSubjectId());
            user.setDisplayName(userDesc.getDisplayName());
            user.setFullName(userDesc.getFullName());
            user.setGroup(false);

            return securityContext.secureResult(AppPermission.MANAGE_USERS_PERMISSION, () ->
                    userDao.tryCreate(user, persistedUser -> {
                        fireEntityChangeEvent(persistedUser, EntityAction.CREATE);
                        if (onCreateAction != null) {
                            onCreateAction.accept(persistedUser);
                        }
                    }));
        });
    }

    @Override
    public User getOrCreateUserGroup(final String name, final Consumer<User> onCreateAction) {
        final Optional<User> optional = userDao.getGroupByName(name);
        return optional.orElseGet(() -> {
            final User user = new User();
            AuditUtil.stamp(securityContext, user);
            user.setUuid(UUID.randomUUID().toString());
            user.setSubjectId(name);
            user.setDisplayName(name);
            user.setGroup(true);

            return securityContext.secureResult(AppPermission.MANAGE_USERS_PERMISSION, () ->
                    userDao.tryCreate(user, persistedUser -> {
                        fireEntityChangeEvent(persistedUser, EntityAction.CREATE);
                        if (onCreateAction != null) {
                            onCreateAction.accept(persistedUser);
                        }
                    }));
        });
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
            fireEntityChangeEvent(updatedUser, EntityAction.UPDATE);
            return updatedUser;
        });
    }

    @Override
    public Boolean delete(final String userUuid) {
        securityContext.secure(AppPermission.MANAGE_USERS_PERMISSION, () -> {
            userDao.delete(userUuid);
            fireEntityChangeEvent(userUuid, EntityAction.DELETE);
        });
        return true;
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
            fireEntityChangeEvent(userUuid, EntityAction.UPDATE);
        });
        return true;
    }

    @Override
    public Boolean removeUserFromGroup(final String userUuid, final String groupUuid) {
        securityContext.secure(AppPermission.MANAGE_USERS_PERMISSION, () -> {
            userDao.removeUserFromGroup(userUuid, groupUuid);
            fireEntityChangeEvent(userUuid, EntityAction.UPDATE);
        });
        return true;
    }

    private void fireEntityChangeEvent(final User user, final EntityAction entityAction) {
        EntityEvent.fire(
                eventBus,
                DocRef.builder()
                        .name(user.getSubjectId())
                        .uuid(user.getUuid())
                        .type(UserDocRefUtil.USER)
                        .build(),
                entityAction);
    }

    private void fireEntityChangeEvent(final String userUuid, final EntityAction entityAction) {
        EntityEvent.fire(
                eventBus,
                DocRef.builder()
                        .uuid(userUuid)
                        .type(UserDocRefUtil.USER)
                        .build(),
                entityAction);
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
