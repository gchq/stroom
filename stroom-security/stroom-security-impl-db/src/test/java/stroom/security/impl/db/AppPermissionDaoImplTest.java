package stroom.security.impl.db;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.testcontainers.containers.MySQLContainer;
import stroom.security.dao.AppPermissionDao;
import stroom.security.dao.UserDao;
import stroom.security.shared.User;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AppPermissionDaoImplTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppPermissionDaoImplTest.class);

    private static MySQLContainer dbContainer = new MySQLContainer()
            .withDatabaseName(Stroom.STROOM.getName());//= null;//

    private static UserDao userDao;
    private static AppPermissionDao appPermissionDao;

    private static final String PERMISSION_NAME_1 = "REBOOT_THE_MATRIX";
    private static final String PERMISSION_NAME_2 = "USE_THE_FANCY_TOWELS";

    @BeforeAll
    public static void beforeAll() {
        LOGGER.info(() -> "Before All - Start Database");
        Optional.ofNullable(dbContainer).ifPresent(MySQLContainer::start);

        Injector injector = Guice.createInjector(new SecurityDbModule(), new TestModule(dbContainer));

        userDao = injector.getInstance(UserDao.class);
        appPermissionDao = injector.getInstance(AppPermissionDao.class);
    }

    @Test
    public void testPermissionInvalidUser() {
        // Given
        final String userUuid = UUID.randomUUID().toString();

        // When
        assertThrows(SecurityException.class, () -> appPermissionDao.addPermission(userUuid, PERMISSION_NAME_1));
    }

    @Test
    public void testPermissionStory() {
        final String userName = String.format("SomePerson_%s", UUID.randomUUID());

        final User user = userDao.createUser(userName);
        appPermissionDao.addPermission(user.getUuid(), PERMISSION_NAME_1);
        appPermissionDao.addPermission(user.getUuid(), PERMISSION_NAME_2);

        final Set<String> permissionsFound1 = appPermissionDao.getPermissionsForUser(user.getUuid());
        assertThat(permissionsFound1).isEqualTo(Set.of(PERMISSION_NAME_1, PERMISSION_NAME_2));

        appPermissionDao.removePermission(user.getUuid(), PERMISSION_NAME_1);
        final Set<String> permissionsFound2 = appPermissionDao.getPermissionsForUser(user.getUuid());
        assertThat(permissionsFound2).isEqualTo(Set.of(PERMISSION_NAME_2));
    }

    @AfterAll
    public static void afterAll() {
        LOGGER.info(() -> "After All - Stop Database");
        Optional.ofNullable(dbContainer).ifPresent(MySQLContainer::stop);
    }
}
