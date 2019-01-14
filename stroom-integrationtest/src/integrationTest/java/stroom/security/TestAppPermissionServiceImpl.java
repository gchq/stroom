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

package stroom.security;


import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.UserAppPermissions;
import stroom.security.shared.UserRef;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.test.FileSystemTestUtil;

import javax.inject.Inject;
import javax.persistence.PersistenceException;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class TestAppPermissionServiceImpl extends AbstractCoreIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestAppPermissionServiceImpl.class);

    @Inject
    private UserService userService;
    @Inject
    private UserAppPermissionService userAppPermissionService;
    @Inject
    private UserGroupsCache userGroupsCache;
    @Inject
    private UserAppPermissionsCache userAppPermissionsCache;

    @Test
    void test() {
        final UserRef userGroup1 = createUserGroup(FileSystemTestUtil.getUniqueTestString());
        final UserRef userGroup2 = createUserGroup(FileSystemTestUtil.getUniqueTestString());
        final UserRef userGroup3 = createUserGroup(FileSystemTestUtil.getUniqueTestString());

        final String c1 = "C1";
        final String p1 = "P1";
        final String p2 = "P2";

        final Set<String> appPermissionSet = new HashSet<>();
        appPermissionSet.add(c1);
        appPermissionSet.add(p1);
        appPermissionSet.add(p2);

        addPermissions(userGroup1, c1, p1);
        addPermissions(userGroup2, c1, p2);
        addPermissions(userGroup3, c1);

        checkPermissions(userGroup1, c1, p1);
        checkPermissions(userGroup2, c1, p2);
        checkPermissions(userGroup3, c1);

        removePermissions(userGroup2, p2);
        checkPermissions(userGroup2, c1);

        // Check user permissions.
        final UserRef user = createUser(FileSystemTestUtil.getUniqueTestString());
        userService.addUserToGroup(user, userGroup1);
        userService.addUserToGroup(user, userGroup3);
        checkUserPermissions(user, c1, p1);

        addPermissions(userGroup2, c1, p2);

        userService.addUserToGroup(user, userGroup2);
        checkUserPermissions(user, c1, p1, p2);

        removePermissions(userGroup2, p2);
        checkUserPermissions(user, c1, p1);
    }

    private void addPermissions(final UserRef user, final String... permissions) {
        for (final String permission : permissions) {
            try {
                userAppPermissionService.addPermission(user, permission);
            } catch (final PersistenceException e) {
                LOGGER.info(e.getMessage());
            }
        }
    }

    private void removePermissions(final UserRef user, final String... permissions) {
        for (final String permission : permissions) {
            userAppPermissionService.removePermission(user, permission);
        }
    }

    private void checkPermissions(final UserRef user, final String... permissions) {
        final UserAppPermissions userAppPermissions = userAppPermissionService
                .getPermissionsForUser(user);
        final Set<String> permissionSet = userAppPermissions.getUserPermissons();
        assertThat(permissionSet.size()).isEqualTo(permissions.length);
        for (final String permission : permissions) {
            assertThat(permissionSet.contains(permission)).isTrue();
        }

        checkUserPermissions(user, permissions);
    }

    private void checkUserPermissions(final UserRef user, final String... permissions) {
        final Set<UserRef> allUsers = new HashSet<>();
        allUsers.add(user);
        allUsers.addAll(userService.findGroupsForUser(user));

        final Set<String> combinedPermissions = new HashSet<>();
        for (final UserRef userRef : allUsers) {
            final UserAppPermissions userAppPermissions = userAppPermissionService.getPermissionsForUser(userRef);
            final Set<String> userPermissions = userAppPermissions.getUserPermissons();
            combinedPermissions.addAll(userPermissions);
        }

        assertThat(combinedPermissions.size()).isEqualTo(permissions.length);
        for (final String permission : permissions) {
            assertThat(combinedPermissions.contains(permission)).isTrue();
        }

        checkUserCachePermissions(user, permissions);
    }

    private void checkUserCachePermissions(final UserRef user, final String... permissions) {
        userGroupsCache.clear();
        userAppPermissionsCache.clear();

        final Set<UserRef> allUsers = new HashSet<>();
        allUsers.add(user);
        allUsers.addAll(userGroupsCache.get(user));

        final Set<String> combinedPermissions = new HashSet<>();
        for (final UserRef userRef : allUsers) {
            final UserAppPermissions userAppPermissions = userAppPermissionsCache.get(userRef);
            final Set<String> userPermissions = userAppPermissions.getUserPermissons();
            combinedPermissions.addAll(userPermissions);
        }

        assertThat(combinedPermissions.size()).isEqualTo(permissions.length);
        for (final String permission : permissions) {
            assertThat(combinedPermissions.contains(permission)).isTrue();
        }
    }

    private UserRef createUser(final String name) {
        UserRef userRef = userService.createUser(name);
        assertThat(userRef).isNotNull();
        final User user = userService.loadByUuid(userRef.getUuid());
        assertThat(user).isNotNull();
        return UserRefFactory.create(user);
    }

    private UserRef createUserGroup(final String name) {
        UserRef userRef = userService.createUserGroup(name);
        assertThat(userRef).isNotNull();
        final User user = userService.loadByUuid(userRef.getUuid());
        assertThat(user).isNotNull();
        return UserRefFactory.create(user);
    }
}
