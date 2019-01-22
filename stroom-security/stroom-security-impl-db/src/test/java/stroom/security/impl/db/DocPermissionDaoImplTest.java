package stroom.security.impl.db;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.testcontainers.containers.MySQLContainer;
import stroom.docref.DocRef;
import stroom.security.dao.DocumentPermissionDao;
import stroom.security.dao.UserDao;
import stroom.security.model.DocumentPermissionJooq;
import stroom.security.model.User;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DocPermissionDaoImplTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserDaoImplTest.class);

    private static MySQLContainer dbContainer = new MySQLContainer()
            .withDatabaseName(Stroom.STROOM.getName());//= null;//

    private static UserDao userDao;
    private static DocumentPermissionDao documentPermissionDao;

    private static final String PERMISSION_READ = "READ";
    private static final String PERMISSION_USE = "USE";
    private static final String PERMISSION_UPDATE = "UPDATE";

    @BeforeAll
    public static void beforeAll() {
        LOGGER.info(() -> "Before All - Start Database");
        Optional.ofNullable(dbContainer).ifPresent(MySQLContainer::start);

        Injector injector = Guice.createInjector(new SecurityDbModule(), new TestModule(dbContainer));

        userDao = injector.getInstance(UserDao.class);
        documentPermissionDao = injector.getInstance(DocumentPermissionDao.class);
    }

    @Test
    public void testMissingUser() {
        // Given
        final String userUuid = UUID.randomUUID().toString();
        final DocRef docRef = createTestDocRef();

        // When
        assertThrows(SecurityException.class, () -> documentPermissionDao.addPermission(userUuid, docRef, "USE"));
    }

    @Test
    public void testDocPermissions() {
        final String userName1 = String.format("SomePerson_1_%s", UUID.randomUUID());
        final String userName2 = String.format("SomePerson_2_%s", UUID.randomUUID());
        final String userName3 = String.format("SomePerson_3_%s", UUID.randomUUID());
        final DocRef docRef1 = createTestDocRef();
        final DocRef docRef2 = createTestDocRef();

        final User user1 = userDao.createUser(userName1);
        final User user2 = userDao.createUser(userName2);
        final User user3 = userDao.createUser(userName3);

        // Create permissions for multiple documents to check that document selection is working correctly
        Stream.of(docRef1, docRef2).forEach(d -> {
            documentPermissionDao.addPermission(user1.getUuid(), d, PERMISSION_READ);
            documentPermissionDao.addPermission(user1.getUuid(), d, PERMISSION_USE);
            documentPermissionDao.addPermission(user2.getUuid(), d, PERMISSION_USE);
            documentPermissionDao.addPermission(user3.getUuid(), d, PERMISSION_USE);
            documentPermissionDao.addPermission(user3.getUuid(), d, PERMISSION_UPDATE);
        });

        // Get the permissions for all users to this document
        final DocumentPermissionJooq permissionsFound1 = documentPermissionDao.getPermissionsForDocument(docRef1);
        assertThat(permissionsFound1.getDocType()).isEqualTo(docRef1.getType());
        assertThat(permissionsFound1.getDocUuid()).isEqualTo(docRef1.getUuid());

        final Set<String> permissionsFound1_user1 = permissionsFound1.getPermissionForUser(user1.getUuid());
        assertThat(permissionsFound1_user1).isEqualTo(Set.of(PERMISSION_READ, PERMISSION_USE));

        final Set<String> permissionsFound1_user2 = permissionsFound1.getPermissionForUser(user2.getUuid());
        assertThat(permissionsFound1_user2).isEqualTo(Set.of(PERMISSION_USE));

        final Set<String> permissionsFound1_user3 = permissionsFound1.getPermissionForUser(user3.getUuid());
        assertThat(permissionsFound1_user3).isEqualTo(Set.of(PERMISSION_USE, PERMISSION_UPDATE));

        // Check permissions per user per document
        final Set<String> permissionsUser3Doc2_1 = documentPermissionDao.getPermissionsForDocumentForUser(docRef2, user3.getUuid());
        assertThat(permissionsUser3Doc2_1).isEqualTo(Set.of(PERMISSION_USE, PERMISSION_UPDATE));

        // Remove a couple of permission from docRef1 (leaving docRef2 in place)
        documentPermissionDao.removePermission(user1.getUuid(), docRef1, PERMISSION_READ);
        documentPermissionDao.removePermission(user3.getUuid(), docRef1, PERMISSION_UPDATE);

        // Get the permissions for all users to this document again
        final DocumentPermissionJooq permissionsFound2 = documentPermissionDao.getPermissionsForDocument(docRef1);
        assertThat(permissionsFound2.getDocType()).isEqualTo(docRef1.getType());
        assertThat(permissionsFound2.getDocUuid()).isEqualTo(docRef1.getUuid());

        final Set<String> permissionsFound2_user1 = permissionsFound2.getPermissionForUser(user1.getUuid());
        assertThat(permissionsFound2_user1).isEqualTo(Set.of(PERMISSION_USE));

        final Set<String> permissionsFound2_user2 = permissionsFound2.getPermissionForUser(user2.getUuid());
        assertThat(permissionsFound2_user2).isEqualTo(Set.of(PERMISSION_USE));

        final Set<String> permissionsFound2_user3 = permissionsFound2.getPermissionForUser(user3.getUuid());
        assertThat(permissionsFound2_user3).isEqualTo(Set.of(PERMISSION_USE));

        // Check permissions per user per document
        final Set<String> permissionsUser3Doc2_2 = documentPermissionDao.getPermissionsForDocumentForUser(docRef2, user3.getUuid());
        assertThat(permissionsUser3Doc2_2).isEqualTo(Set.of(PERMISSION_USE, PERMISSION_UPDATE));

        final Set<String> permissionsUser3Doc1_2 = documentPermissionDao.getPermissionsForDocumentForUser(docRef1, user3.getUuid());
        assertThat(permissionsUser3Doc1_2).isEqualTo(Set.of(PERMISSION_USE));
    }

    @Test
    public void testClearUserPermissions() {
        // Given
        final String userName1 = String.format("SomePerson_1_%s", UUID.randomUUID());
        final String userName2 = String.format("SomePerson_2_%s", UUID.randomUUID());
        final DocRef docRef1 = createTestDocRef();

        final User user1 = userDao.createUser(userName1);
        final User user2 = userDao.createUser(userName2);

        // Create permissions for multiple documents to check that document selection is working correctly
        documentPermissionDao.addPermission(user1.getUuid(), docRef1, PERMISSION_READ);
        documentPermissionDao.addPermission(user1.getUuid(), docRef1, PERMISSION_USE);
        documentPermissionDao.addPermission(user2.getUuid(), docRef1, PERMISSION_USE);

        final Set<String> user1Doc1Before = documentPermissionDao.getPermissionsForDocumentForUser(docRef1, user1.getUuid());
        assertThat(user1Doc1Before).isEqualTo(Set.of(PERMISSION_READ, PERMISSION_USE));

        final Set<String> user2Doc1Before = documentPermissionDao.getPermissionsForDocumentForUser(docRef1, user2.getUuid());
        assertThat(user2Doc1Before).isEqualTo(Set.of(PERMISSION_USE));

        // When
        documentPermissionDao.clearUserPermissions(user1.getUuid());

        // Then
        final Set<String> user1Doc1After = documentPermissionDao.getPermissionsForDocumentForUser(docRef1, user1.getUuid());
        assertThat(user1Doc1After).isEmpty();

        final Set<String> user2Doc1After = documentPermissionDao.getPermissionsForDocumentForUser(docRef1, user2.getUuid());
        assertThat(user2Doc1After).isEqualTo(Set.of(PERMISSION_USE));
    }

    @Test
    public void testClearDocumentPermissions() {
        // Given
        final String userName1 = String.format("SomePerson_1_%s", UUID.randomUUID());
        final DocRef docRef1 = createTestDocRef();
        final DocRef docRef2 = createTestDocRef();

        final User user1 = userDao.createUser(userName1);

        // Create permissions for multiple documents to check that document selection is working correctly
        Stream.of(docRef1, docRef2).forEach(d -> {
            documentPermissionDao.addPermission(user1.getUuid(), d, PERMISSION_READ);
            documentPermissionDao.addPermission(user1.getUuid(), d, PERMISSION_USE);
        });

        Stream.of(docRef1, docRef2).forEach(d -> {
            final Set<String> permissionsBefore = documentPermissionDao.getPermissionsForDocumentForUser(docRef1, user1.getUuid());
            assertThat(permissionsBefore).isEqualTo(Set.of(PERMISSION_READ, PERMISSION_USE));
        });

        // When
        documentPermissionDao.clearDocumentPermissions(docRef1);

        // Then
        // The two documents will now have different permissions
        final Set<String> user1Doc1After = documentPermissionDao.getPermissionsForDocumentForUser(docRef1, user1.getUuid());
        assertThat(user1Doc1After).isEmpty();

        final Set<String> user2Doc1After = documentPermissionDao.getPermissionsForDocumentForUser(docRef2, user1.getUuid());
        assertThat(user2Doc1After).isEqualTo(Set.of(PERMISSION_READ, PERMISSION_USE));
    }

    @AfterAll
    public static void afterAll() {
        LOGGER.info(() -> "After All - Stop Database");
        Optional.ofNullable(dbContainer).ifPresent(MySQLContainer::stop);
    }

    private DocRef createTestDocRef() {
        return new DocRef.Builder()
                .type("Simple")
                .uuid(UUID.randomUUID().toString())
                .build();
    }
}
