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

import stroom.security.api.SecurityContext;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.User;
import stroom.util.AuditUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Singleton
class UserServiceImpl implements UserService {
    private final SecurityContext securityContext;
    private final UserDao userDao;

    @Inject
    UserServiceImpl(final SecurityContext securityContext,
                    final UserDao userDao) {
        this.securityContext = securityContext;
        this.userDao = userDao;
    }

    @Override
    public User createUser(final String name) {
        return create(name, false);
    }

    @Override
    public User createUserGroup(final String name) {
        return create(name, true);
    }

    private User create(final String name, final boolean isGroup) {
        User user = new User();
        AuditUtil.stamp(securityContext.getUserId(), user);
        user.setUuid(UUID.randomUUID().toString());
        user.setName(name);
        user.setGroup(isGroup);

        return securityContext.secureResult(PermissionNames.MANAGE_USERS_PERMISSION, () ->
                userDao.create(user));
    }

    @Override
    public Optional<User> getUserByName(final String name) {
        if (name != null && name.trim().length() > 0) {
            return userDao.getByName(name)
                    .filter(user -> {
                        if (!user.getName().equals(name)) {
                            throw new RuntimeException("Unexpected: returned user name does not match requested user name");
                        }
                        return true;
                    });
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
        AuditUtil.stamp(securityContext.getUserId(), user);
        return securityContext.secureResult(PermissionNames.MANAGE_USERS_PERMISSION, () ->
                userDao.update(user));
    }

    @Override
    public Boolean delete(final String userUuid) {
        securityContext.secure(PermissionNames.MANAGE_USERS_PERMISSION, () ->
                userDao.delete(userUuid));
        return true;
    }

    @Override
    public List<User> find(final FindUserCriteria criteria) {
        return userDao.find(criteria.getQuickFilterInput(), criteria.getGroup());
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
        securityContext.secure(PermissionNames.MANAGE_USERS_PERMISSION, () ->
                userDao.addUserToGroup(userUuid, groupUuid));
        return true;
    }

    @Override
    public Boolean removeUserFromGroup(final String userUuid, final String groupUuid) {
        securityContext.secure(PermissionNames.MANAGE_USERS_PERMISSION, () ->
                userDao.removeUserFromGroup(userUuid, groupUuid));
        return true;
    }

    @Override
    public List<String> getAssociates(final String filter) {
        final Set<User> userSet;

        final Predicate<User> userPredicate = user ->
                user.isEnabled()
                        && user.getUuid().length() > 5
                        && !user.isGroup();

        // Admin users will see all.
        if (securityContext.isAdmin()) {
            final FindUserCriteria findUserCriteria = new FindUserCriteria(filter, false);
            final List<User> users = find(findUserCriteria);

            userSet = new HashSet<>(users);

        } else {
            userSet = new HashSet<>();
            getUserByName(securityContext.getUserId())
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
                .map(User::getName)
                .sorted()
                .collect(Collectors.toList());
    }
}
