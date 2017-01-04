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
import stroom.AbstractCoreIntegrationTest;
import stroom.CommonTestScenarioCreator;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.DocRefUtil;
import stroom.index.shared.Index;
import stroom.index.shared.IndexService;
import stroom.security.shared.DocumentPermissionKeySet;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.User;
import stroom.security.shared.UserRef;
import stroom.security.shared.UserService;
import stroom.util.logging.StroomLogger;
import stroom.util.test.FileSystemTestUtil;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.persistence.PersistenceException;
import java.util.Set;

public class TestDocumentPermissionsServiceImpl extends AbstractCoreIntegrationTest {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(TestDocumentPermissionsServiceImpl.class);

    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private UserService userService;
    @Inject
    private IndexService indexService;
    @Inject
    private DocumentPermissionService documentPermissionService;
    @Resource
    private UserPermissionsCache userPermissionCache;

    @Test
    public void test() {
        final UserRef userGroup1 = createUserGroup(FileSystemTestUtil.getUniqueTestString());
        final UserRef userGroup2 = createUserGroup(FileSystemTestUtil.getUniqueTestString());
        final UserRef userGroup3 = createUserGroup(FileSystemTestUtil.getUniqueTestString());

        final Index doc = commonTestScenarioCreator.createIndex(FileSystemTestUtil.getUniqueTestString());
        final String[] permissions = indexService.getPermissions();
        final String c1 = permissions[0];
        final String p1 = permissions[1];
        final String p2 = permissions[2];

        final DocumentPermissions documentPermissions = documentPermissionService
                .getPermissionsForDocument(DocRefUtil.create(doc));
        Assert.assertArrayEquals(permissions, documentPermissions.getAllPermissions());

        addPermissions(userGroup1, doc, c1, p1);
        addPermissions(userGroup2, doc, c1, p2);
        addPermissions(userGroup3, doc, c1);

        checkDocumentPermissions(userGroup1, doc, c1, p1);
        checkDocumentPermissions(userGroup2, doc, c1, p2);
        checkDocumentPermissions(userGroup3, doc, c1);

        removePermissions(userGroup2, doc, p2);
        checkDocumentPermissions(userGroup2, doc, c1);

        // Check user permissions.
        final UserRef user = createUser(FileSystemTestUtil.getUniqueTestString());
        userService.addUserToGroup(user, userGroup1);
        userService.addUserToGroup(user, userGroup3);
        checkUserPermissions(user, doc, c1, p1);

        addPermissions(userGroup2, doc, c1, p2);

        userService.addUserToGroup(user, userGroup2);
        checkUserPermissions(user, doc, c1, p1, p2);

        removePermissions(userGroup2, doc, p2);
        checkUserPermissions(user, doc, c1, p1);
    }

    private void addPermissions(final UserRef user, final BaseEntity entity, final String... permissions) {
        final DocRef docRef = DocRefUtil.create(entity);
        for (final String permission : permissions) {
            try {
                documentPermissionService.addPermission(user, docRef, permission);
            } catch (final PersistenceException e) {
                LOGGER.info(e.getMessage());
            }
        }
    }

    private void removePermissions(final UserRef user, final BaseEntity entity, final String... permissions) {
        final DocRef docRef = DocRefUtil.create(entity);
        for (final String permission : permissions) {
            documentPermissionService.removePermission(user, docRef, permission);
        }
    }

    private void checkDocumentPermissions(final UserRef user, final BaseEntity entity, final String... permissions) {
        final DocRef docRef = DocRefUtil.create(entity);
        final DocumentPermissions documentPermissions = documentPermissionService
                .getPermissionsForDocument(docRef);
        final Set<String> permissionSet = documentPermissions.getPermissionsForUser(user);
        Assert.assertEquals(permissions.length, permissionSet.size());
        for (final String permission : permissions) {
            Assert.assertTrue(permissionSet.contains(permission));
        }

        checkUserPermissions(user, entity, permissions);
    }

    private void checkUserPermissions(final UserRef user, final BaseEntity entity, final String... permissions) {
        final DocRef docRef = DocRefUtil.create(entity);
        final DocumentPermissionKeySet documentPermissionKeySet = documentPermissionService
                .getPermissionKeySetForUser(user);
        Assert.assertEquals(permissions.length, documentPermissionKeySet.size());
        for (final String permission : permissions) {
            Assert.assertTrue(documentPermissionKeySet.checkPermission(docRef.getType(),
                    docRef.getUuid(), permission));
        }

        checkUserCachePermissions(user, entity, permissions);
    }

    private void checkUserCachePermissions(final UserRef user, final BaseEntity entity, final String... permissions) {
        final DocRef docRef = DocRefUtil.create(entity);
        userPermissionCache.clear();
        final UserPermissions userPermissions = userPermissionCache.get(user);
        final DocumentPermissionKeySet documentPermissionKeySet = userPermissions.getDocumentPermissionKeySet();
        Assert.assertEquals(permissions.length, documentPermissionKeySet.size());
        for (final String permission : permissions) {
            Assert.assertTrue(documentPermissionKeySet.checkPermission(docRef.getType(),
                    docRef.getUuid(), permission));
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
