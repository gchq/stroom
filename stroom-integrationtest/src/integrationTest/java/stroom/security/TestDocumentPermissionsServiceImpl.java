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
 *
 */

package stroom.security;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.UserRef;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.util.test.FileSystemTestUtil;

import javax.inject.Inject;
import javax.persistence.PersistenceException;
import java.util.HashSet;
import java.util.Set;

public class TestDocumentPermissionsServiceImpl extends AbstractCoreIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestDocumentPermissionsServiceImpl.class);

    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private UserService userService;
    @Inject
    private DocumentPermissionService documentPermissionService;
    @Inject
    private UserGroupsCache userGroupsCache;
    @Inject
    private DocumentPermissionsCache documentPermissionsCache;

    @Test
    public void test() {
        final UserRef userGroup1 = createUserGroup(FileSystemTestUtil.getUniqueTestString());
        final UserRef userGroup2 = createUserGroup(FileSystemTestUtil.getUniqueTestString());
        final UserRef userGroup3 = createUserGroup(FileSystemTestUtil.getUniqueTestString());

        final DocRef docRef = commonTestScenarioCreator.createIndex(FileSystemTestUtil.getUniqueTestString());
        final String[] permissions = DocumentPermissionNames.DOCUMENT_PERMISSIONS;
        final String c1 = permissions[0];
        final String p1 = permissions[1];
        final String p2 = permissions[2];

        final DocumentPermissions documentPermissions = documentPermissionService
                .getPermissionsForDocument(docRef);
        Assert.assertArrayEquals(permissions, documentPermissions.getAllPermissions());

        addPermissions(userGroup1, docRef, c1, p1);
        addPermissions(userGroup2, docRef, c1, p2);
        addPermissions(userGroup3, docRef, c1);

        checkDocumentPermissions(userGroup1, docRef, c1, p1);
        checkDocumentPermissions(userGroup2, docRef, c1, p2);
        checkDocumentPermissions(userGroup3, docRef, c1);

        removePermissions(userGroup2, docRef, p2);
        checkDocumentPermissions(userGroup2, docRef, c1);

        // Check user permissions.
        final UserRef user = createUser(FileSystemTestUtil.getUniqueTestString());
        userService.addUserToGroup(user, userGroup1);
        userService.addUserToGroup(user, userGroup3);
        checkUserPermissions(user, docRef, c1, p1);

        addPermissions(userGroup2, docRef, c1, p2);

        userService.addUserToGroup(user, userGroup2);
        checkUserPermissions(user, docRef, c1, p1, p2);

        removePermissions(userGroup2, docRef, p2);
        checkUserPermissions(user, docRef, c1, p1);
    }

    private void addPermissions(final UserRef user, final DocRef docRef, final String... permissions) {
        for (final String permission : permissions) {
            try {
                documentPermissionService.addPermission(user, docRef, permission);
            } catch (final PersistenceException e) {
                LOGGER.info(e.getMessage());
            }
        }
    }

    private void removePermissions(final UserRef user, final DocRef docRef, final String... permissions) {
        for (final String permission : permissions) {
            documentPermissionService.removePermission(user, docRef, permission);
        }
    }

    private void checkDocumentPermissions(final UserRef user, final DocRef docRef, final String... permissions) {
        final DocumentPermissions documentPermissions = documentPermissionService
                .getPermissionsForDocument(docRef);
        final Set<String> permissionSet = documentPermissions.getPermissionsForUser(user);
        Assert.assertEquals(permissions.length, permissionSet.size());
        for (final String permission : permissions) {
            Assert.assertTrue(permissionSet.contains(permission));
        }

        checkUserPermissions(user, docRef, permissions);
    }

    private void checkUserPermissions(final UserRef user, final DocRef docRef, final String... permissions) {
        final Set<UserRef> allUsers = new HashSet<>();
        allUsers.add(user);
        allUsers.addAll(userService.findGroupsForUser(user));

        final Set<String> combinedPermissions = new HashSet<>();
        for (final UserRef userRef : allUsers) {
            final DocumentPermissions documentPermissions = documentPermissionService.getPermissionsForDocument(docRef);
            final Set<String> userPermissions = documentPermissions.getPermissionsForUser(userRef);
            combinedPermissions.addAll(userPermissions);
        }

        Assert.assertEquals(permissions.length, combinedPermissions.size());
        for (final String permission : permissions) {
            Assert.assertTrue(combinedPermissions.contains(permission));
        }

        checkUserCachePermissions(user, docRef, permissions);
    }

    private void checkUserCachePermissions(final UserRef user, final DocRef docRef, final String... permissions) {
        userGroupsCache.clear();
        documentPermissionsCache.clear();

        final Set<UserRef> allUsers = new HashSet<>();
        allUsers.add(user);
        allUsers.addAll(userGroupsCache.get(user));

        final Set<String> combinedPermissions = new HashSet<>();
        for (final UserRef userRef : allUsers) {
            final DocumentPermissions documentPermissions = documentPermissionsCache.get(docRef);
            final Set<String> userPermissions = documentPermissions.getPermissionsForUser(userRef);
            combinedPermissions.addAll(userPermissions);
        }

        Assert.assertEquals(permissions.length, combinedPermissions.size());
        for (final String permission : permissions) {
            Assert.assertTrue(combinedPermissions.contains(permission));
        }
    }

    private UserRef createUser(final String name) {
        UserRef userRef = userService.createUser(name);
        Assert.assertNotNull(userRef);
        final User user = userService.loadByUuid(userRef.getUuid());
        Assert.assertNotNull(user);
        return UserRefFactory.create(user);
    }

    private UserRef createUserGroup(final String name) {
        UserRef userRef = userService.createUserGroup(name);
        Assert.assertNotNull(userRef);
        final User user = userService.loadByUuid(userRef.getUuid());
        Assert.assertNotNull(user);
        return UserRefFactory.create(user);
    }
}
