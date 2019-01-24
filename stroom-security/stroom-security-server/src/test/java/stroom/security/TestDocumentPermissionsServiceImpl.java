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


import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MySQLContainer;
import stroom.docref.DocRef;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.User;
import stroom.security.shared.UserRef;
import stroom.util.test.FileSystemTestUtil;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestDocumentPermissionsServiceImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestDocumentPermissionsServiceImpl.class);

    private static MySQLContainer dbContainer = new MySQLContainer();//= null;//

    private static Injector injector;

    private static UserService userService;
    private static DocumentPermissionService documentPermissionService;
    private static UserGroupsCache userGroupsCache;
    private static DocumentPermissionsCache documentPermissionsCache;

    @BeforeAll
    public static void beforeAll() {
        LOGGER.info("Before All - Start Database");
        Optional.ofNullable(dbContainer).ifPresent(MySQLContainer::start);

        injector = Guice.createInjector(new TestModule(dbContainer));

        userService = injector.getInstance(UserService.class);
        documentPermissionService = injector.getInstance(DocumentPermissionService.class);
        userGroupsCache = injector.getInstance(UserGroupsCache.class);
        documentPermissionsCache = injector.getInstance(DocumentPermissionsCache.class);
    }

    @Test
    void test() {
        final UserRef userGroup1 = createUserGroup(FileSystemTestUtil.getUniqueTestString());
        final UserRef userGroup2 = createUserGroup(FileSystemTestUtil.getUniqueTestString());
        final UserRef userGroup3 = createUserGroup(FileSystemTestUtil.getUniqueTestString());

        final DocRef docRef = createTestDocRef();
        final String[] permissions = DocumentPermissionNames.DOCUMENT_PERMISSIONS;
        final String c1 = permissions[0];
        final String p1 = permissions[1];
        final String p2 = permissions[2];

        final DocumentPermissions documentPermissions = documentPermissionService
                .getPermissionsForDocument(docRef);
        assertThat(documentPermissions.getAllPermissions()).isEqualTo(permissions);

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
            } catch (final Exception e) {
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
        assertThat(permissionSet.size()).isEqualTo(permissions.length);
        for (final String permission : permissions) {
            assertThat(permissionSet.contains(permission)).isTrue();
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

        assertThat(combinedPermissions.size()).isEqualTo(permissions.length);
        for (final String permission : permissions) {
            assertThat(combinedPermissions.contains(permission)).isTrue();
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

    private DocRef createTestDocRef() {
        return new DocRef.Builder()
                .type("Index")
                .uuid(UUID.randomUUID().toString())
                .build();
    }
}
