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


import stroom.docref.DocRef;
import stroom.security.api.UserService;
import stroom.security.impl.db.SecurityDbConnProvider;
import stroom.security.impl.db.SecurityTestUtil;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.shared.UserRef;

import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class TestDocumentPermissionsServiceImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestDocumentPermissionsServiceImpl.class);

    @Inject
    private UserService userService;
    @Inject
    private DocumentPermissionServiceImpl documentPermissionService;
    @Inject
    private UserGroupsCache userGroupsCache;
    @Inject
    private UserDocumentPermissionsCache userDocumentPermissionsCache;
    @Inject
    private SecurityDbConnProvider securityDbConnProvider;

    @BeforeEach
    void beforeEach() {
        final Injector injector = Guice.createInjector(new TestModule());

        injector.injectMembers(this);
//        userService = injector.getInstance(UserService.class);
//        documentPermissionService = injector.getInstance(DocumentPermissionServiceImpl.class);
//        userGroupsCache = injector.getInstance(UserGroupsCache.class);
//        userDocumentPermissionsCache = injector.getInstance(UserDocumentPermissionsCache.class);
    }

    @AfterEach
    void tearDown() {
        SecurityTestUtil.teardown(securityDbConnProvider);
    }

    @Test
    void test() {
        final User userGroup1 = createUserGroup(FileSystemTestUtil.getUniqueTestString());
        final User userGroup2 = createUserGroup(FileSystemTestUtil.getUniqueTestString());
        final User userGroup3 = createUserGroup(FileSystemTestUtil.getUniqueTestString());

        final DocRef docRef = createTestDocRef();
        final DocumentPermission[] permissions = DocumentPermission.values();
        final DocumentPermission c1 = permissions[0];
        final DocumentPermission p1 = permissions[1];
        final DocumentPermission p2 = permissions[2];

//        final DocumentPermissionEnum documentPermissions = documentPermissionService
//                .getPermissionsForDocument(docRef.getUuid());

        setPermission(docRef, userGroup1, DocumentPermission.OWNER);
        setPermission(docRef, userGroup2, DocumentPermission.DELETE);
        setPermission(docRef, userGroup3, DocumentPermission.EDIT);

        checkDocumentPermissions(docRef, userGroup1, DocumentPermission.OWNER);
        checkDocumentPermissions(docRef, userGroup2, DocumentPermission.DELETE);
        checkDocumentPermissions(docRef, userGroup3, DocumentPermission.EDIT);

        setPermission(docRef, userGroup2, DocumentPermission.DELETE);
        checkDocumentPermissions(docRef, userGroup2, DocumentPermission.DELETE);

        // Check user permissions.
        final User user = createUser(FileSystemTestUtil.getUniqueTestString());
        userService.addUserToGroup(user.asRef(), userGroup1.asRef());
        userService.addUserToGroup(user.asRef(), userGroup3.asRef());
        checkUserPermissions(docRef, user, DocumentPermission.OWNER);

        setPermission(docRef, userGroup2, DocumentPermission.EDIT);

        userService.addUserToGroup(user.asRef(), userGroup2.asRef());
        checkUserPermissions(docRef, user, DocumentPermission.OWNER);

        setPermission(docRef, userGroup2, DocumentPermission.EDIT);
        checkUserPermissions(docRef, user, DocumentPermission.OWNER);
    }

    private void setPermission(final DocRef docRef,
                               final User user,
                               final DocumentPermission permission) {
        try {
            documentPermissionService.setPermission(docRef, user.asRef(), permission);
        } catch (final Exception e) {
            LOGGER.info(e.getMessage());
        }
    }

    private void checkDocumentPermissions(final DocRef docRef,
                                          final User user,
                                          final DocumentPermission permission) {
        final DocumentPermission documentPermission = documentPermissionService
                .getPermission(docRef, user.asRef());
        assertThat(documentPermission).isEqualTo(permission);
        checkUserPermissions(docRef, user, permission);
    }

    private void checkUserPermissions(final DocRef docRef,
                                      final User user,
                                      final DocumentPermission permission) {
        final Set<User> allUsers = new HashSet<>();
        allUsers.add(user);
        allUsers.addAll(userService.findGroupsForUser(user.getUuid(), new FindUserCriteria()).getValues());

        DocumentPermission combinedPermission = null;
        for (final User u : allUsers) {
            final DocumentPermission documentPermission = documentPermissionService
                    .getPermission(docRef, u.asRef());
            if (documentPermission != null) {
                if (combinedPermission == null ||
                    documentPermission.isEqualOrHigher(combinedPermission)) {
                    combinedPermission = documentPermission;
                }
            }
        }

        assertThat(combinedPermission).isNotNull();
        assertThat(combinedPermission).isEqualTo(permission);

        checkUserCachePermissions(docRef, user.asRef(), permission);
    }

    private void checkUserCachePermissions(final DocRef docRef,
                                           final UserRef userRef,
                                           final DocumentPermission permission) {
        userGroupsCache.clear();
        userDocumentPermissionsCache.clear();

        final Set<UserRef> allUsers = new HashSet<>();
        allUsers.add(userRef);
        allUsers.addAll(userGroupsCache.getGroups(userRef));

        for (final UserRef u : allUsers) {
            if (userDocumentPermissionsCache.hasDocumentPermission(u, docRef, permission)) {
                return;
            }
        }

        fail("Perm not found");
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

    private DocRef createTestDocRef() {
        return DocRef.builder()
                .type("Index")
                .uuid(UUID.randomUUID().toString())
                .build();
    }
}
