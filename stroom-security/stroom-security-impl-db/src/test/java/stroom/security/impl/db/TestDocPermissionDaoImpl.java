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

package stroom.security.impl.db;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.security.impl.DocumentPermissionDao;
import stroom.security.impl.TestModule;
import stroom.security.impl.UserDao;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.FetchDocumentUserPermissionsRequest;
import stroom.security.shared.PermissionShowLevel;
import stroom.security.shared.User;
import stroom.util.AuditUtil;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestDocPermissionDaoImpl {

    @Inject
    private UserDao userDao;
    @Inject
    private DocumentPermissionDao documentPermissionDao;
    @Inject
    private SecurityDbConnProvider securityDbConnProvider;

    @BeforeEach
    void beforeEach() {
        final Injector injector = Guice.createInjector(
                new SecurityDbModule(),
                new SecurityDaoModule(),
                new TestModule());

        injector.injectMembers(this);
    }

    @AfterEach
    void tearDown() {
        SecurityTestUtil.teardown(securityDbConnProvider);
    }

    @Test
    void testSetPermission() {
        final UserRef user = createUser("User1");
        final DocRef docRef = createTestDocRef();

        documentPermissionDao.setDocumentUserPermission(docRef.getUuid(), user.getUuid(), DocumentPermission.USE);

        DocumentPermission docPermission = documentPermissionDao
                .getDocumentUserPermission(docRef.getUuid(), user.getUuid());
        assertThat(docPermission).isEqualTo(DocumentPermission.USE);

        // Check that we can add the same perm again without error
        documentPermissionDao.setDocumentUserPermission(docRef.getUuid(), user.getUuid(), DocumentPermission.USE);

        docPermission = documentPermissionDao.getDocumentUserPermission(docRef.getUuid(), user.getUuid());
        assertThat(docPermission).isEqualTo(DocumentPermission.USE);

        documentPermissionDao.setDocumentUserPermission(docRef.getUuid(), user.getUuid(), DocumentPermission.VIEW);

        docPermission = documentPermissionDao.getDocumentUserPermission(docRef.getUuid(), user.getUuid());
        assertThat(docPermission).isEqualTo(DocumentPermission.VIEW);
    }

    private Map<UserRef, Set<DocumentPermission>> getPermissionsForDocument(final DocRef docRef,
                                                                            final Set<UserRef> users) {
        return users.stream().collect(Collectors.toMap(Function.identity(), userRef -> {
            final DocumentPermission documentPermission =
                    documentPermissionDao.getDocumentUserPermission(docRef.getUuid(), userRef.getUuid());
            return Arrays
                    .stream(DocumentPermission.values())
                    .filter(permission -> documentPermission != null &&
                                          documentPermission.isEqualOrHigher(permission))
                    .collect(Collectors.toSet());
        }));
    }

    @Test
    void testDocPermissions() {
        final String userName1 = String.format("SomePerson_1_%s", UUID.randomUUID());
        final String userName2 = String.format("SomePerson_2_%s", UUID.randomUUID());
        final String userName3 = String.format("SomePerson_3_%s", UUID.randomUUID());
        final DocRef docRef1 = createTestDocRef();
        final DocRef docRef2 = createTestDocRef();

        final UserRef user1 = createUser(userName1);
        final UserRef user2 = createUser(userName2);
        final UserRef user3 = createUser(userName3);

        // Create permissions for multiple documents to check that document selection is working correctly
        Stream.of(docRef1, docRef2)
                .map(DocRef::getUuid)
                .forEach(docRefUuid -> {
                    documentPermissionDao
                            .setDocumentUserPermission(docRefUuid, user1.getUuid(), DocumentPermission.USE);
                    documentPermissionDao
                            .setDocumentUserPermission(docRefUuid, user1.getUuid(), DocumentPermission.VIEW);
                    documentPermissionDao
                            .setDocumentUserPermission(docRefUuid, user2.getUuid(), DocumentPermission.USE);
                    documentPermissionDao
                            .setDocumentUserPermission(docRefUuid, user3.getUuid(), DocumentPermission.USE);
                    documentPermissionDao
                            .setDocumentUserPermission(docRefUuid, user3.getUuid(), DocumentPermission.EDIT);
                });

        // Get the permissions for all users to this document
        final Map<UserRef, Set<DocumentPermission>> permissionsFound1 =
                getPermissionsForDocument(docRef1, Set.of(user1, user2, user3));

        final Set<DocumentPermission> permissionsFound1_user1 = permissionsFound1.get(user1);
        assertThat(permissionsFound1_user1).isEqualTo(Set.of(
                DocumentPermission.VIEW,
                DocumentPermission.USE));

        final Set<DocumentPermission> permissionsFound1_user2 = permissionsFound1.get(user2);
        assertThat(permissionsFound1_user2).isEqualTo(Set.of(
                DocumentPermission.USE));

        final Set<DocumentPermission> permissionsFound1_user3 = permissionsFound1.get(user3);
        assertThat(permissionsFound1_user3).isEqualTo(Set.of(
                DocumentPermission.USE,
                DocumentPermission.VIEW,
                DocumentPermission.EDIT));

        // Check permissions per user per document
        final DocumentPermission permissionsUser3Doc2_1 = documentPermissionDao
                .getDocumentUserPermission(docRef2.getUuid(), user3.getUuid());
        assertThat(permissionsUser3Doc2_1).isEqualTo(DocumentPermission.EDIT);

        // Remove a couple of permission from docRef1 (leaving docRef2 in place)
        documentPermissionDao.setDocumentUserPermission(docRef1.getUuid(), user1.getUuid(), DocumentPermission.USE);
        documentPermissionDao.setDocumentUserPermission(docRef1.getUuid(), user3.getUuid(), DocumentPermission.USE);

        // Get the permissions for all users to this document again
        final Map<UserRef, Set<DocumentPermission>> permissionsFound2 =
                getPermissionsForDocument(docRef1, Set.of(user1, user2, user3));

        final Set<DocumentPermission> permissionsFound2_user1 = permissionsFound2.get(user1);
        assertThat(permissionsFound2_user1).isEqualTo(Set.of(DocumentPermission.USE));

        final Set<DocumentPermission> permissionsFound2_user2 = permissionsFound2.get(user2);
        assertThat(permissionsFound2_user2).isEqualTo(Set.of(DocumentPermission.USE));

        final Set<DocumentPermission> permissionsFound2_user3 = permissionsFound2.get(user3);
        assertThat(permissionsFound2_user3).isEqualTo(Set.of(DocumentPermission.USE));

        // Check permissions per user per document
        final DocumentPermission permissionsUser3Doc2_2 = documentPermissionDao
                .getDocumentUserPermission(docRef2.getUuid(), user3.getUuid());
        assertThat(permissionsUser3Doc2_2).isEqualTo(DocumentPermission.EDIT);

        final DocumentPermission permissionsUser3Doc1_2 = documentPermissionDao
                .getDocumentUserPermission(docRef1.getUuid(), user3.getUuid());
        assertThat(permissionsUser3Doc1_2).isEqualTo(DocumentPermission.USE);

        // Clear permission from docRef1 (leaving docRef2 in place)
        documentPermissionDao.removeDocumentUserPermission(docRef1.getUuid(), user1.getUuid());
        documentPermissionDao.removeDocumentUserPermission(docRef1.getUuid(), user3.getUuid());

        // Get the permissions for all users to this document again
        final Map<UserRef, Set<DocumentPermission>> permissionsFound3 =
                getPermissionsForDocument(docRef1, Set.of(user1, user2, user3));

        final Set<DocumentPermission> permissionsFound3_user1 = permissionsFound3.get(user1);
        assertThat(permissionsFound3_user1).isEqualTo(Set.of());

        final Set<DocumentPermission> permissionsFound3_user2 = permissionsFound3.get(user2);
        assertThat(permissionsFound3_user2).isEqualTo(Set.of(DocumentPermission.USE));

        final Set<DocumentPermission> permissionsFound3_user3 = permissionsFound3.get(user3);
        assertThat(permissionsFound3_user3).isEqualTo(Set.of());

        // Check permissions per user per document
        final DocumentPermission permissionsUser3Doc2_3 = documentPermissionDao
                .getDocumentUserPermission(docRef2.getUuid(), user3.getUuid());
        assertThat(permissionsUser3Doc2_3).isEqualTo(DocumentPermission.EDIT);

        final DocumentPermission permissionsUser3Doc1_3 = documentPermissionDao
                .getDocumentUserPermission(docRef1.getUuid(), user3.getUuid());
        assertThat(permissionsUser3Doc1_3).isNull();
    }

    @Test
    void testEffectivePermissions1() {
        final DocRef docRef1 = createTestDocRef();
        final UserRef user1 = createUser("user1");
        documentPermissionDao
                .setDocumentUserPermission(docRef1.getUuid(), user1.getUuid(), DocumentPermission.EDIT);
        assertThat(documentPermissionDao.getDocumentUserPermission(docRef1.getUuid(), user1.getUuid()))
                .isEqualTo(DocumentPermission.EDIT);

        final FetchDocumentUserPermissionsRequest request = new FetchDocumentUserPermissionsRequest(
                PageRequest.unlimited(),
                Collections.emptyList(),
                ExpressionOperator.builder().build(),
                docRef1,
                null,
                PermissionShowLevel.SHOW_EFFECTIVE);
        final ResultPage<DocumentUserPermissions> resultPage = documentPermissionDao.fetchDocumentUserPermissions(
                request);
        assertThat(resultPage.size()).isOne();
        validateDocPermissions(
                resultPage,
                "user1",
                DocumentPermission.EDIT,
                null,
                Set.of(),
                Set.of());
    }

    @Test
    void testEffectivePermissions1PlusCreate() {
        final DocRef docRef1 = createTestFolderDocRef();
        final UserRef user1 = createUser("user1");
        documentPermissionDao
                .setDocumentUserPermission(docRef1.getUuid(), user1.getUuid(), DocumentPermission.EDIT);
        documentPermissionDao
                .addDocumentUserCreatePermission(docRef1.getUuid(), user1.getUuid(), "Dashboard");
        assertThat(documentPermissionDao.getDocumentUserPermission(docRef1.getUuid(), user1.getUuid()))
                .isEqualTo(DocumentPermission.EDIT);

        final FetchDocumentUserPermissionsRequest request = new FetchDocumentUserPermissionsRequest(
                PageRequest.unlimited(),
                Collections.emptyList(),
                ExpressionOperator.builder().build(),
                docRef1,
                null,
                PermissionShowLevel.SHOW_EFFECTIVE);
        final ResultPage<DocumentUserPermissions> resultPage = documentPermissionDao.fetchDocumentUserPermissions(
                request);
        assertThat(resultPage.size()).isOne();
        validateDocPermissions(
                resultPage,
                "user1",
                DocumentPermission.EDIT,
                null,
                Set.of("Dashboard"),
                Set.of());
    }

    @Test
    void testEffectivePermissions2() {
        final DocRef docRef1 = createTestDocRef();
        final UserRef user1 = createUser("user1");
        final UserRef group1 = createGroup("group1");
        userDao.addUserToGroup(user1.getUuid(), group1.getUuid());
        documentPermissionDao
                .setDocumentUserPermission(docRef1.getUuid(), group1.getUuid(), DocumentPermission.EDIT);

        assertThat(documentPermissionDao.getDocumentUserPermission(docRef1.getUuid(), user1.getUuid())).isNull();
        assertThat(documentPermissionDao.getDocumentUserPermission(docRef1.getUuid(), group1.getUuid()))
                .isEqualTo(DocumentPermission.EDIT);

        final FetchDocumentUserPermissionsRequest request = new FetchDocumentUserPermissionsRequest(
                PageRequest.unlimited(),
                Collections.emptyList(),
                ExpressionOperator.builder().build(),
                docRef1,
                null,
                PermissionShowLevel.SHOW_EFFECTIVE);
        final ResultPage<DocumentUserPermissions> resultPage = documentPermissionDao.fetchDocumentUserPermissions(
                request);
        assertThat(resultPage.size()).isEqualTo(2);
        validateDocPermissions(resultPage,
                "user1",
                null,
                DocumentPermission.EDIT,
                Set.of(),
                Set.of());
        validateDocPermissions(resultPage,
                "group1",
                DocumentPermission.EDIT,
                null,
                Set.of(),
                Set.of());
    }

    @Test
    void testEffectivePermissions2PlusCreate() {
        final DocRef docRef1 = createTestFolderDocRef();
        final UserRef user1 = createUser("user1");
        final UserRef group1 = createGroup("group1");
        userDao.addUserToGroup(user1.getUuid(), group1.getUuid());
        documentPermissionDao
                .setDocumentUserPermission(docRef1.getUuid(), group1.getUuid(), DocumentPermission.EDIT);
        documentPermissionDao
                .addDocumentUserCreatePermission(docRef1.getUuid(), group1.getUuid(), "Dashboard");

        assertThat(documentPermissionDao.getDocumentUserPermission(docRef1.getUuid(), user1.getUuid())).isNull();
        assertThat(documentPermissionDao.getDocumentUserPermission(docRef1.getUuid(), group1.getUuid()))
                .isEqualTo(DocumentPermission.EDIT);

        final FetchDocumentUserPermissionsRequest request = new FetchDocumentUserPermissionsRequest(
                PageRequest.unlimited(),
                Collections.emptyList(),
                ExpressionOperator.builder().build(),
                docRef1,
                null,
                PermissionShowLevel.SHOW_EFFECTIVE);
        final ResultPage<DocumentUserPermissions> resultPage = documentPermissionDao.fetchDocumentUserPermissions(
                request);
        assertThat(resultPage.size()).isEqualTo(2);
        validateDocPermissions(resultPage,
                "user1",
                null,
                DocumentPermission.EDIT,
                Set.of(),
                Set.of("Dashboard"));
        validateDocPermissions(resultPage,
                "group1",
                DocumentPermission.EDIT,
                null,
                Set.of("Dashboard"),
                Set.of());
    }

    @Test
    void testEffectivePermissions3PlusCreate() {
        final DocRef docRef1 = createTestFolderDocRef();
        final UserRef user1 = createUser("user1");
        final UserRef group1 = createGroup("group1");
        final UserRef group2 = createGroup("group2");
        userDao.addUserToGroup(user1.getUuid(), group1.getUuid());
        userDao.addUserToGroup(group1.getUuid(), group2.getUuid());
        documentPermissionDao
                .setDocumentUserPermission(docRef1.getUuid(), group2.getUuid(), DocumentPermission.EDIT);
        documentPermissionDao
                .addDocumentUserCreatePermission(docRef1.getUuid(), group2.getUuid(), "Dashboard");

        assertThat(documentPermissionDao.getDocumentUserPermission(docRef1.getUuid(), user1.getUuid())).isNull();
        assertThat(documentPermissionDao.getDocumentUserPermission(docRef1.getUuid(), group1.getUuid())).isNull();
        assertThat(documentPermissionDao.getDocumentUserPermission(docRef1.getUuid(), group2.getUuid()))
                .isEqualTo(DocumentPermission.EDIT);

        final FetchDocumentUserPermissionsRequest request = new FetchDocumentUserPermissionsRequest(
                PageRequest.unlimited(),
                Collections.emptyList(),
                ExpressionOperator.builder().build(),
                docRef1,
                null,
                PermissionShowLevel.SHOW_EFFECTIVE);
        final ResultPage<DocumentUserPermissions> resultPage = documentPermissionDao.fetchDocumentUserPermissions(
                request);
        assertThat(resultPage.size()).isEqualTo(3);
        validateDocPermissions(resultPage,
                "user1",
                null,
                DocumentPermission.EDIT,
                Set.of(),
                Set.of("Dashboard"));
        validateDocPermissions(resultPage,
                "group1",
                null,
                DocumentPermission.EDIT,
                Set.of(),
                Set.of("Dashboard"));
        validateDocPermissions(resultPage,
                "group2",
                DocumentPermission.EDIT,
                null,
                Set.of("Dashboard"),
                Set.of());
    }

    @Test
    void testEffectivePermissions3None() {
        final DocRef docRef1 = createTestFolderDocRef();
        final UserRef user1 = createUser("user1");
        final UserRef group1 = createGroup("group1");
        final UserRef group2 = createGroup("group2");
        userDao.addUserToGroup(user1.getUuid(), group1.getUuid());
        userDao.addUserToGroup(group1.getUuid(), group2.getUuid());

        assertThat(documentPermissionDao.getDocumentUserPermission(docRef1.getUuid(), user1.getUuid())).isNull();
        assertThat(documentPermissionDao.getDocumentUserPermission(docRef1.getUuid(), group1.getUuid())).isNull();
        assertThat(documentPermissionDao.getDocumentUserPermission(docRef1.getUuid(), group2.getUuid())).isNull();

        final FetchDocumentUserPermissionsRequest request = new FetchDocumentUserPermissionsRequest(
                PageRequest.unlimited(),
                Collections.emptyList(),
                ExpressionOperator.builder().build(),
                docRef1,
                null,
                PermissionShowLevel.SHOW_EFFECTIVE);
        final ResultPage<DocumentUserPermissions> resultPage = documentPermissionDao.fetchDocumentUserPermissions(
                request);
        assertThat(resultPage.size()).isEqualTo(0);
    }

    @Test
    void testEffectivePermissions3SplitPlusCreate() {
        final DocRef docRef1 = createTestFolderDocRef();
        final UserRef user1 = createUser("user1");
        final UserRef group1 = createGroup("group1");
        final UserRef group2 = createGroup("group2");
        userDao.addUserToGroup(user1.getUuid(), group1.getUuid());
        userDao.addUserToGroup(group1.getUuid(), group2.getUuid());
        documentPermissionDao
                .setDocumentUserPermission(docRef1.getUuid(), group1.getUuid(), DocumentPermission.EDIT);
        documentPermissionDao
                .setDocumentUserPermission(docRef1.getUuid(), group2.getUuid(), DocumentPermission.VIEW);
        documentPermissionDao
                .addDocumentUserCreatePermission(docRef1.getUuid(), group1.getUuid(), "Dashboard");
        documentPermissionDao
                .addDocumentUserCreatePermission(docRef1.getUuid(), group2.getUuid(), "Query");

        assertThat(documentPermissionDao.getDocumentUserPermission(docRef1.getUuid(), user1.getUuid())).isNull();
        assertThat(documentPermissionDao.getDocumentUserPermission(docRef1.getUuid(), group1.getUuid()))
                .isEqualTo(DocumentPermission.EDIT);
        assertThat(documentPermissionDao.getDocumentUserPermission(docRef1.getUuid(), group2.getUuid()))
                .isEqualTo(DocumentPermission.VIEW);

        final FetchDocumentUserPermissionsRequest request = new FetchDocumentUserPermissionsRequest(
                PageRequest.unlimited(),
                Collections.emptyList(),
                ExpressionOperator.builder().build(),
                docRef1,
                null,
                PermissionShowLevel.SHOW_EFFECTIVE);
        final ResultPage<DocumentUserPermissions> resultPage = documentPermissionDao.fetchDocumentUserPermissions(
                request);
        assertThat(resultPage.size()).isEqualTo(3);
        validateDocPermissions(resultPage,
                "user1",
                null,
                DocumentPermission.EDIT,
                Set.of(),
                Set.of("Query", "Dashboard"));
        validateDocPermissions(resultPage,
                "group1",
                DocumentPermission.EDIT,
                DocumentPermission.VIEW,
                Set.of("Dashboard"),
                Set.of("Query"));
        validateDocPermissions(resultPage,
                "group2",
                DocumentPermission.VIEW,
                null,
                Set.of("Query"),
                Set.of());
    }

    @Test
    void testEffectivePermissions3MidPlusCreate() {
        final DocRef docRef1 = createTestFolderDocRef();
        final UserRef user1 = createUser("user1");
        final UserRef group1 = createGroup("group1");
        final UserRef group2 = createGroup("group2");
        userDao.addUserToGroup(user1.getUuid(), group1.getUuid());
        userDao.addUserToGroup(group1.getUuid(), group2.getUuid());
        documentPermissionDao
                .setDocumentUserPermission(docRef1.getUuid(), group1.getUuid(), DocumentPermission.EDIT);
        documentPermissionDao
                .addDocumentUserCreatePermission(docRef1.getUuid(), group1.getUuid(), "Dashboard");
        documentPermissionDao
                .addDocumentUserCreatePermission(docRef1.getUuid(), group1.getUuid(), "Query");

        assertThat(documentPermissionDao.getDocumentUserPermission(docRef1.getUuid(), user1.getUuid())).isNull();
        assertThat(documentPermissionDao.getDocumentUserPermission(docRef1.getUuid(), group1.getUuid()))
                .isEqualTo(DocumentPermission.EDIT);
        assertThat(documentPermissionDao.getDocumentUserPermission(docRef1.getUuid(), group2.getUuid())).isNull();

        final FetchDocumentUserPermissionsRequest request = new FetchDocumentUserPermissionsRequest(
                PageRequest.unlimited(),
                Collections.emptyList(),
                ExpressionOperator.builder().build(),
                docRef1,
                null,
                PermissionShowLevel.SHOW_EFFECTIVE);
        final ResultPage<DocumentUserPermissions> resultPage = documentPermissionDao.fetchDocumentUserPermissions(
                request);
        assertThat(resultPage.size()).isEqualTo(2);
        validateDocPermissions(resultPage,
                "user1",
                null,
                DocumentPermission.EDIT,
                Set.of(),
                Set.of("Query", "Dashboard"));
        validateDocPermissions(resultPage,
                "group1",
                DocumentPermission.EDIT,
                null,
                Set.of("Query", "Dashboard"),
                Set.of());
    }


    private void validateDocPermissions(final ResultPage<DocumentUserPermissions> resultPage,
                                        final String userName,
                                        final DocumentPermission expectedPermission,
                                        final DocumentPermission expectedInherited,
                                        final Set<String> expectedCreatePermissions,
                                        final Set<String> expectedCreateInherited) {
        final DocumentUserPermissions documentUserPermissions = get(resultPage, userName);
        validateDocPermissions(
                documentUserPermissions,
                expectedPermission,
                expectedInherited,
                expectedCreatePermissions,
                expectedCreateInherited);
    }

    private void validateDocPermissions(final DocumentUserPermissions appUserPermissions,
                                        final DocumentPermission expectedPermission,
                                        final DocumentPermission expectedInherited,
                                        final Set<String> expectedCreatePermissions,
                                        final Set<String> expectedCreateInherited) {
        assertThat(appUserPermissions.getPermission()).isEqualTo(expectedPermission);
        assertThat(appUserPermissions.getInheritedPermission()).isEqualTo(expectedInherited);
        validatePermissionSet(appUserPermissions.getDocumentCreatePermissions(), expectedCreatePermissions);
        validatePermissionSet(appUserPermissions.getInheritedDocumentCreatePermissions(), expectedCreateInherited);
    }

    private void validatePermissionSet(final Set<String> actual,
                                       final Set<String> expected) {
        assertThat(actual.size()).isEqualTo(expected.size());
        assertThat(actual).containsAll(expected);
    }

    private DocumentUserPermissions get(final ResultPage<DocumentUserPermissions> resultPage,
                                        final String userName) {
        final Optional<DocumentUserPermissions> optional = resultPage
                .getValues()
                .stream()
                .filter(aup -> userName.equals(aup.getUserRef().getSubjectId()))
                .findAny();
        assertThat(optional).isPresent();
        return optional.get();
    }

    private DocRef createTestFolderDocRef() {
        return DocRef.builder()
                .type("Folder")
                .uuid(UUID.randomUUID().toString())
                .build();
    }

    private DocRef createTestDocRef() {
        return DocRef.builder()
                .type("Simple")
                .uuid(UUID.randomUUID().toString())
                .build();
    }

    private UserRef createUser(final String name) {
        return createUserOrGroup(name, false);
    }

    private UserRef createGroup(final String name) {
        return createUserOrGroup(name, true);
    }

    private UserRef createUserOrGroup(final String name, final boolean group) {
        final User user = User.builder()
                .subjectId(name)
                .displayName(name)
                .uuid(UUID.randomUUID().toString())
                .group(group)
                .build();
        AuditUtil.stamp(() -> "test", user);
        return userDao.create(user).asRef();
    }
}
