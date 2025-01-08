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
import stroom.security.api.SecurityContext;
import stroom.security.api.UserService;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.User;
import stroom.util.AuditUtil;
import stroom.util.NullSafe;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.shared.SimpleUserName;
import stroom.util.shared.UserName;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Inject;

class UserServiceImpl implements UserService {

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
    public User getOrCreateUser(final UserName userName, final Consumer<User> onCreateAction) {
        return getOrCreate(userName, false, onCreateAction);
    }

    @Override
    public User getOrCreateUserGroup(final String name, final Consumer<User> onCreateAction) {
        return getOrCreate(SimpleUserName.fromGroupName(name), true, onCreateAction);
    }

    private User getOrCreate(final UserName userName,
                             final boolean isGroup,
                             final Consumer<User> onCreateAction) {
        final Optional<User> optional = userDao.getBySubjectId(userName.getSubjectId(), isGroup);
        return optional.orElseGet(() -> {
            final User user = new User();
            AuditUtil.stamp(securityContext, user);
            user.setUuid(UUID.randomUUID().toString());
            user.setSubjectId(userName.getSubjectId());
            user.setDisplayName(userName.getDisplayName());
            user.setFullName(userName.getFullName());
            user.setGroup(isGroup);

            return securityContext.secureResult(PermissionNames.MANAGE_USERS_PERMISSION, () -> {
                final User newUser = userDao.tryCreate(user, persistedUser -> {
                    fireEntityChangeEvent(persistedUser, EntityAction.CREATE);
                    if (onCreateAction != null) {
                        onCreateAction.accept(persistedUser);
                    }
                });
                return newUser;
            });
        });
    }

    @Override
    public Optional<User> getUserBySubjectId(final String subjectId) {
        if (!NullSafe.isBlankString(subjectId)) {
            return userDao.getBySubjectId(subjectId)
                    .filter(user -> {
                        // TODO: 23/03/2023 Why is this here?
                        if (!user.getSubjectId().equals(subjectId)) {
                            throw new RuntimeException(
                                    "Unexpected: returned user name does not match requested user name");
                        }
                        return true;
                    });
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<User> getUserByDisplayName(final String displayName) {
        if (!NullSafe.isBlankString(displayName)) {
            return userDao.getByDisplayName(displayName);
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
        return securityContext.secureResult(PermissionNames.MANAGE_USERS_PERMISSION, () -> {
            final User updatedUser = userDao.update(user);
            fireEntityChangeEvent(updatedUser, EntityAction.UPDATE);
            return updatedUser;
        });
    }

    @Override
    public Boolean delete(final String userUuid) {
        securityContext.secure(PermissionNames.MANAGE_USERS_PERMISSION, () -> {
            userDao.delete(userUuid);

            fireEntityChangeEvent(userUuid, EntityAction.DELETE);
        });
        return true;
    }

    @Override
    public List<User> find(final FindUserCriteria criteria) {
        return userDao.find(criteria.getQuickFilterInput(), criteria.isGroup());
    }

    @Override
    public List<User> findUsersInGroup(final String groupUuid, final String quickFilterInput) {
        return userDao.findUsersInGroup(groupUuid, quickFilterInput);
    }


    @Override
    public List<User> findGroupsForUser(final String userUuid, final String quickFilterInput) {
        return userDao.findGroupsForUser(userUuid, quickFilterInput);
    }

    @Override
    public Set<String> findGroupUuidsForUser(final String userUuid) {
        return userDao.findGroupUuidsForUser(userUuid);
    }

    @Override
    public List<User> findGroupsForUserName(final String userName) {
        return userDao.findGroupsForUserName(userName);
    }

    @Override
    public Boolean addUserToGroup(final String userUuid, final String groupUuid) {
        securityContext.secure(PermissionNames.MANAGE_USERS_PERMISSION, () -> {
            userDao.addUserToGroup(userUuid, groupUuid);
            fireEntityChangeEvent(userUuid, EntityAction.UPDATE);
        });
        return true;
    }

    @Override
    public Boolean removeUserFromGroup(final String userUuid, final String groupUuid) {
        securityContext.secure(PermissionNames.MANAGE_USERS_PERMISSION, () -> {
            userDao.removeUserFromGroup(userUuid, groupUuid);
            fireEntityChangeEvent(userUuid, EntityAction.UPDATE);
        });
        return true;
    }

    @Override
    public List<UserName> getAssociates(final String filter) {
        final Set<User> userSet;

        final Predicate<User> userPredicate = user -> user.getUuid().length() > 5 && !user.isGroup();

        // An admin or a MANAGE_USERS user will see all.
        if (securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION)) {
            final FindUserCriteria findUserCriteria = new FindUserCriteria(filter, false);
            final List<User> users = find(findUserCriteria);

            userSet = new HashSet<>(users);
        } else {
            userSet = new HashSet<>();
            getUserBySubjectId(securityContext.getSubjectId())
                    .ifPresent(user -> {
                        userSet.add(user);

                        final List<User> groups = findGroupsForUser(user.getUuid());
                        groups.forEach(userGroup -> {
                            final List<User> usersInGroup = findUsersInGroup(userGroup.getUuid(), filter);
                            if (usersInGroup != null) {
                                userSet.addAll(usersInGroup);
                            }
                        });
                    });
        }

        return userSet
                .stream()
                .filter(userPredicate)
                .map(User::asUserName)
                .collect(Collectors.toList());
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
}
