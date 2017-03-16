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

package stroom.security.server;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.aop.framework.Advised;
import stroom.AbstractCoreIntegrationTest;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.User;
import stroom.security.shared.UserAppPermissions;
import stroom.security.shared.UserRef;
import stroom.security.shared.UserService;
import stroom.util.logging.StroomLogger;
import stroom.util.test.FileSystemTestUtil;

import javax.annotation.Resource;
import javax.persistence.PersistenceException;
import java.util.HashSet;
import java.util.Set;

public class TestAppPermissionServiceImpl extends AbstractCoreIntegrationTest {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(TestAppPermissionServiceImpl.class);

    @Resource
    private UserService userService;
    @Resource
    private UserAppPermissionService userAppPermissionService;
    @Resource
    private UserGroupsCache userGroupsCache;
    @Resource
    private UserAppPermissionsCache userAppPermissionsCache;

    @Test
    public void test() {
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
        userAppPermissionService.createAll(appPermissionSet);

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

    @Test
    public void test_getRequiredPermissionSet() throws Exception {
        final UserAppPermissionServiceImpl userAppPermissionServiceImpl = (UserAppPermissionServiceImpl) ((Advised) userAppPermissionService)
                .getTargetSource().getTarget();
        final Set<String> set = userAppPermissionServiceImpl.getRequiredPermissionSet();
        Assert.assertNotNull(set);
        Assert.assertEquals(18, set.size());

        final Permission permission = new Permission();
        permission.setName(PermissionNames.ADMINISTRATOR);
        final String search = permission.getName();

        for (final String test : set) {
            if (test.equals(search)) {
                return;
            }
        }
        Assert.fail();
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
        Assert.assertEquals(permissions.length, permissionSet.size());
        for (final String permission : permissions) {
            Assert.assertTrue(permissionSet.contains(permission));
        }

        checkUserPermissions(user, permissions);
    }

    private void checkUserPermissions(final UserRef user, final String... permissions) {
        final UserAppPermissions userAppPermissions = userAppPermissionService
                .getPermissionsForUser(user);
        final Set<String> permissionSet = userAppPermissions.getUserPermissons();
        Assert.assertEquals(permissions.length, permissionSet.size());
        for (final String permission : permissions) {
            Assert.assertTrue(userAppPermissions.getUserPermissons().contains(permission));
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

        Assert.assertEquals(permissions.length, combinedPermissions.size());
        for (final String permission : permissions) {
            Assert.assertTrue(combinedPermissions.contains(permission));
        }
    }

    private UserRef createUser(final String name) {
        User user = userService.createUser(name);
        Assert.assertNotNull(user);
        user = userService.load(user);
        Assert.assertNotNull(user);
        return UserRef.create(user);
    }

    private UserRef createUserGroup(final String name) {
        User user = userService.createUserGroup(name);
        Assert.assertNotNull(user);
        user = userService.load(user);
        Assert.assertNotNull(user);
        return UserRef.create(user);
    }
}
