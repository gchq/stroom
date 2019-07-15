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

package stroom.security.impl;


import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.User;
import stroom.test.common.util.test.FileSystemTestUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestDocumentPermissionsServiceImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestDocumentPermissionsServiceImpl.class);

    private static UserService userService;
    private static DocumentPermissionService documentPermissionService;
    private static UserGroupsCache userGroupsCache;
    private static UserDocumentPermissionsCache userDocumentPermissionsCache;

    @BeforeAll
    static void beforeAll() {
        final Injector injector = Guice.createInjector(new TestModule());

        userService = injector.getInstance(UserService.class);
        documentPermissionService = injector.getInstance(DocumentPermissionService.class);
        userGroupsCache = injector.getInstance(UserGroupsCache.class);
        userDocumentPermissionsCache = injector.getInstance(UserDocumentPermissionsCache.class);
    }

    @Test
    void test() {
        final User userGroup1 = createUserGroup(FileSystemTestUtil.getUniqueTestString());
        final User userGroup2 = createUserGroup(FileSystemTestUtil.getUniqueTestString());
        final User userGroup3 = createUserGroup(FileSystemTestUtil.getUniqueTestString());

        final DocRef docRef = createTestDocRef();
        final String[] permissions = DocumentPermissionNames.DOCUMENT_PERMISSIONS;
        final String c1 = permissions[0];
        final String p1 = permissions[1];
        final String p2 = permissions[2];

        final DocumentPermissions documentPermissions = documentPermissionService
                .getPermissionsForDocument(docRef.getUuid());

        addPermissions(docRef, userGroup1, c1, p1);
        addPermissions(docRef, userGroup2, c1, p2);
        addPermissions(docRef, userGroup3, c1);

        checkDocumentPermissions(docRef, userGroup1, c1, p1);
        checkDocumentPermissions(docRef, userGroup2, c1, p2);
        checkDocumentPermissions(docRef, userGroup3, c1);

        removePermissions(docRef, userGroup2, p2);
        checkDocumentPermissions(docRef, userGroup2, c1);

        // Check user permissions.
        final User user = createUser(FileSystemTestUtil.getUniqueTestString());
        userService.addUserToGroup(user.getUuid(), userGroup1.getUuid());
        userService.addUserToGroup(user.getUuid(), userGroup3.getUuid());
        checkUserPermissions(docRef, user, c1, p1);

        addPermissions(docRef, userGroup2, c1, p2);

        userService.addUserToGroup(user.getUuid(), userGroup2.getUuid());
        checkUserPermissions(docRef, user, c1, p1, p2);

        removePermissions(docRef, userGroup2, p2);
        checkUserPermissions(docRef, user, c1, p1);
    }

    private void addPermissions(final DocRef docRef, final User user, final String... permissions) {
        for (final String permission : permissions) {
            try {
                documentPermissionService.addPermission(docRef.getUuid(), user.getUuid(), permission);
            } catch (final Exception e) {
                LOGGER.info(e.getMessage());
            }
        }
    }

    private void removePermissions(final DocRef docRef, final User user, final String... permissions) {
        for (final String permission : permissions) {
            documentPermissionService.removePermission(docRef.getUuid(), user.getUuid(), permission);
        }
    }

    private void checkDocumentPermissions(final DocRef docRef, final User user, final String... permissions) {
        final DocumentPermissions documentPermissions = documentPermissionService
                .getPermissionsForDocument(docRef.getUuid());
        final Set<String> permissionSet = documentPermissions.getPermissionsForUser(user.getUuid());
        assertThat(permissionSet.size()).isEqualTo(permissions.length);
        for (final String permission : permissions) {
            assertThat(permissionSet.contains(permission)).isTrue();
        }

        checkUserPermissions(docRef, user, permissions);
    }

    private void checkUserPermissions(final DocRef docRef,
                                      final User user,
                                      final String... permissions) {
        final Set<User> allUsers = new HashSet<>();
        allUsers.add(user);
        allUsers.addAll(userService.findGroupsForUser(user.getUuid()));

        final Set<String> combinedPermissions = new HashSet<>();
        for (final User userRef : allUsers) {
            final DocumentPermissions documentPermissions = documentPermissionService.getPermissionsForDocument(docRef.getUuid());
            final Set<String> userPermissions = documentPermissions.getPermissionsForUser(userRef.getUuid());
            combinedPermissions.addAll(userPermissions);
        }

        assertThat(combinedPermissions.size()).isEqualTo(permissions.length);
        for (final String permission : permissions) {
            assertThat(combinedPermissions.contains(permission)).isTrue();
        }

        checkUserCachePermissions(docRef, user, permissions);
    }

    private void checkUserCachePermissions(final DocRef docRef,
                                           final User user,
                                           final String... permissions) {
        userGroupsCache.clear();
        userDocumentPermissionsCache.clear();

        final UserDocumentPermissions userDocumentPermissions = userDocumentPermissionsCache.get(user.getUuid());
        for (final String permission : permissions) {
            assertThat(userDocumentPermissions.hasDocumentPermission(docRef.getUuid(), permission)).isTrue();
        }
    }

    private User createUser(final String name) {
        User userRef = userService.createUser(name);
        assertThat(userRef).isNotNull();
        final User user = userService.loadByUuid(userRef.getUuid());
        assertThat(user).isNotNull();
        return user;
    }

    private User createUserGroup(final String name) {
        User userRef = userService.createUserGroup(name);
        assertThat(userRef).isNotNull();
        final User user = userService.loadByUuid(userRef.getUuid());
        assertThat(user).isNotNull();
        return user;
    }

    private DocRef createTestDocRef() {
        return new DocRef.Builder()
                .type("Index")
                .uuid(UUID.randomUUID().toString())
                .build();
    }
}
