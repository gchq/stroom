/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.security.api.AppPermissionService;
import stroom.security.api.UserService;
import stroom.security.impl.db.SecurityDbConnProvider;
import stroom.security.impl.db.SecurityTestUtil;
import stroom.security.shared.AppPermission;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(TestModule.class)
class TestAppPermissionServiceImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestAppPermissionServiceImpl.class);

    @Inject
    private UserService userService;
    @Inject
    private AppPermissionService userAppPermissionService;
    @Inject
    private UserGroupsCache userGroupsCache;
    @Inject
    private UserAppPermissionsCache userAppPermissionsCache;
    @Inject
    private SecurityDbConnProvider securityDbConnProvider;

    @AfterEach
    void tearDown() {
        SecurityTestUtil.teardown(securityDbConnProvider);
    }

    @Test
    void test() {
        final User userGroup1 = createUserGroup(String.format("Group_1_%s", UUID.randomUUID()));
        final User userGroup2 = createUserGroup(String.format("Group_2_%s", UUID.randomUUID()));
        final User userGroup3 = createUserGroup(String.format("Group_3_%s", UUID.randomUUID()));

        // No idea what the distinction is between c and p
        final AppPermission c1 = AppPermission.IMPORT_DATA_PERMISSION;
        final AppPermission p1 = AppPermission.MANAGE_DB_PERMISSION;
        final AppPermission p2 = AppPermission.MANAGE_PROCESSORS_PERMISSION;

        addPermissions(userGroup1, c1, p1);
        addPermissions(userGroup2, c1, p2);
        addPermissions(userGroup3, c1);

        checkPermissions(userGroup1, c1, p1);
        checkPermissions(userGroup2, c1, p2);
        checkPermissions(userGroup3, c1);

        removePermissions(userGroup2, p2);
        checkPermissions(userGroup2, c1);

        // Check user permissions.
        final User user = createUser(FileSystemTestUtil.getUniqueTestString());
        userService.addUserToGroup(user.asRef(), userGroup1.asRef());
        userService.addUserToGroup(user.asRef(), userGroup3.asRef());
        checkUserPermissions(user, c1, p1);

        addPermissions(userGroup2, c1, p2);

        userService.addUserToGroup(user.asRef(), userGroup2.asRef());
        checkUserPermissions(user, c1, p1, p2);

        removePermissions(userGroup2, p2);
        checkUserPermissions(user, c1, p1);
    }

    private void addPermissions(final User user, final AppPermission... permissions) {
        for (final AppPermission permission : permissions) {
            try {
                userAppPermissionService.addPermission(user.asRef(), permission);
            } catch (final Exception e) {
                LOGGER.info(e.getMessage());
            }
        }
    }

    private void removePermissions(final User user, final AppPermission... permissions) {
        for (final AppPermission permission : permissions) {
            userAppPermissionService.removePermission(user.asRef(), permission);
        }
    }

    private void checkPermissions(final User user, final AppPermission... permissions) {
        final Set<AppPermission> permissionSet = userAppPermissionService
                .getDirectAppUserPermissions(user.asRef());
        assertThat(permissionSet.size()).isEqualTo(permissions.length);
        for (final AppPermission permission : permissions) {
            assertThat(permissionSet.contains(permission)).isTrue();
        }

        checkUserPermissions(user, permissions);
    }

    private void checkUserPermissions(final User user, final AppPermission... permissions) {
        final Set<User> allUsers = new HashSet<>();
        allUsers.add(user);
        allUsers.addAll(userService.findGroupsForUser(user.getUuid(), new FindUserCriteria()).getValues());

        final Set<AppPermission> combinedPermissions = new HashSet<>();
        for (final User userRef : allUsers) {
            final Set<AppPermission> permissionSet = userAppPermissionService
                    .getDirectAppUserPermissions(userRef.asRef());
            combinedPermissions.addAll(permissionSet);
        }

        assertThat(combinedPermissions.size()).isEqualTo(permissions.length);
        for (final AppPermission permission : permissions) {
            assertThat(combinedPermissions.contains(permission)).isTrue();
        }

        checkUserCachePermissions(user, permissions);
    }

    private void checkUserCachePermissions(final User user, final AppPermission... permissions) {
        userGroupsCache.clear();
        userAppPermissionsCache.clear();

        final Set<UserRef> allUsers = new HashSet<>();
        allUsers.add(user.asRef());
        allUsers.addAll(userGroupsCache.getGroups(user.asRef()));

        final Set<AppPermission> combinedPermissions = new HashSet<>();
        for (final UserRef userRef : allUsers) {
            final Set<AppPermission> permissionSet = userAppPermissionsCache.get(userRef);
            combinedPermissions.addAll(permissionSet);
        }

        assertThat(combinedPermissions.size()).isEqualTo(permissions.length);
        for (final AppPermission permission : permissions) {
            assertThat(combinedPermissions.contains(permission)).isTrue();
        }
    }

    private User createUser(final String name) {
        final User userRef = userService.getOrCreateUser(name);
        assertThat(userRef).isNotNull();
        final Optional<User> user = userService.loadByUuid(userRef.getUuid());
        assertThat(user).isPresent();
        return user.get();
    }

    private User createUserGroup(final String name) {
        final User userRef = userService.getOrCreateUserGroup(name);
        assertThat(userRef).isNotNull();
        final Optional<User> user = userService.loadByUuid(userRef.getUuid());
        assertThat(user).isPresent();
        return user.get();
    }
}
