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

import stroom.db.util.JooqUtil;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.query.shared.QueryDoc;
import stroom.script.shared.ScriptDoc;
import stroom.security.impl.TestModule;
import stroom.security.impl.UserDao;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.User;
import stroom.util.AuditUtil;
import stroom.util.shared.UserRef;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestDocumentPermissionDaoImpl {

    @Inject
    private UserDao userDao;
    @Inject
    private DocumentPermissionDaoImpl documentPermissionDao;
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
    void deletePermissionsForUser() {
        final String docUuid1 = "doc1uuid";
        final String docUuid2 = "doc2uuid";
        final UserRef user1 = createUser("user1");
        final UserRef user2 = createUser("user2");
        final String uuid1 = user1.getUuid();
        final String uuid2 = user2.getUuid();

        documentPermissionDao.setDocumentUserPermission(
                docUuid1, uuid1, DocumentPermission.OWNER);
        documentPermissionDao.setDocumentUserPermission(
                docUuid2, uuid1, DocumentPermission.VIEW);
        documentPermissionDao.setDocumentUserCreatePermissions(
                docUuid1, uuid1, Set.of(DictionaryDoc.TYPE, ScriptDoc.TYPE));
        documentPermissionDao.setDocumentUserCreatePermissions(
                docUuid2, uuid1, Set.of(DictionaryDoc.TYPE, ScriptDoc.TYPE));

        documentPermissionDao.setDocumentUserPermission(
                docUuid1, uuid2, DocumentPermission.VIEW);
        documentPermissionDao.setDocumentUserPermission(
                docUuid2, uuid2, DocumentPermission.OWNER);
        documentPermissionDao.setDocumentUserCreatePermissions(
                docUuid1, uuid2, Set.of(DictionaryDoc.TYPE, QueryDoc.TYPE));
        documentPermissionDao.setDocumentUserCreatePermissions(
                docUuid2, uuid2, Set.of(DictionaryDoc.TYPE, QueryDoc.TYPE));

        final Runnable assertUser1Perms = () -> {
            assertThat(documentPermissionDao.getPermissionsForUser(uuid1).getPermissions())
                    .containsExactlyInAnyOrderEntriesOf(Map.of(
                            docUuid1, DocumentPermission.OWNER,
                            docUuid2, DocumentPermission.VIEW));
            assertThat(documentPermissionDao.getDocumentUserCreatePermissions(docUuid1, uuid1))
                    .containsExactlyInAnyOrder(
                            DictionaryDoc.TYPE,
                            ScriptDoc.TYPE);
            assertThat(documentPermissionDao.getDocumentUserCreatePermissions(docUuid2, uuid1))
                    .containsExactlyInAnyOrder(
                            DictionaryDoc.TYPE,
                            ScriptDoc.TYPE);
        };
        assertUser1Perms.run();

        assertThat(documentPermissionDao.getPermissionsForUser(uuid2).getPermissions())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        docUuid1, DocumentPermission.VIEW,
                        docUuid2, DocumentPermission.OWNER));
        assertThat(documentPermissionDao.getDocumentUserCreatePermissions(docUuid1, uuid2))
                .containsExactlyInAnyOrder(
                        DictionaryDoc.TYPE,
                        QueryDoc.TYPE);
        assertThat(documentPermissionDao.getDocumentUserCreatePermissions(docUuid2, uuid2))
                .containsExactlyInAnyOrder(
                        DictionaryDoc.TYPE,
                        QueryDoc.TYPE);

        final Integer delCount = JooqUtil.contextResult(securityDbConnProvider, context ->
                documentPermissionDao.deletePermissionsForUser(context, uuid2));

        assertThat(delCount)
                .isEqualTo(6);

        assertUser1Perms.run();

        assertThat(documentPermissionDao.getPermissionsForUser(uuid2).getPermissions())
                .isEmpty();
        assertThat(documentPermissionDao.getDocumentUserCreatePermissions(docUuid1, uuid2))
                .isEmpty();
        assertThat(documentPermissionDao.getDocumentUserCreatePermissions(docUuid2, uuid2))
                .isEmpty();
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
