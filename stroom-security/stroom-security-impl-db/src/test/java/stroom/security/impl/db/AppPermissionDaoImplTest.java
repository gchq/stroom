package stroom.security.impl.db;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import stroom.security.impl.AppPermissionDao;
import stroom.security.impl.TestModule;
import stroom.security.impl.UserDao;
import stroom.security.shared.User;
import stroom.util.AuditUtil;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppPermissionDaoImplTest {
    private static UserDao userDao;
    private static AppPermissionDao appPermissionDao;

    private static final String PERMISSION_NAME_1 = "REBOOT_THE_MATRIX";
    private static final String PERMISSION_NAME_2 = "USE_THE_FANCY_TOWELS";

    @BeforeAll
    static void beforeAll() {
        Injector injector = Guice.createInjector(new SecurityDbModule(), new TestModule());

        userDao = injector.getInstance(UserDao.class);
        appPermissionDao = injector.getInstance(AppPermissionDao.class);
    }

    @Test
    void testPermissionInvalidUser() {
        // Given
        final String userUuid = UUID.randomUUID().toString();

        // When
        assertThrows(SecurityException.class, () -> appPermissionDao.addPermission(userUuid, PERMISSION_NAME_1));
    }

    @Test
    void testPermissionStory() {
        final String userName = String.format("SomePerson_%s", UUID.randomUUID());

        final User user = createUser(userName);
        appPermissionDao.addPermission(user.getUuid(), PERMISSION_NAME_1);
        appPermissionDao.addPermission(user.getUuid(), PERMISSION_NAME_2);

        final Set<String> permissionsFound1 = appPermissionDao.getPermissionsForUser(user.getUuid());
        assertThat(permissionsFound1).isEqualTo(Set.of(PERMISSION_NAME_1, PERMISSION_NAME_2));

        appPermissionDao.removePermission(user.getUuid(), PERMISSION_NAME_1);
        final Set<String> permissionsFound2 = appPermissionDao.getPermissionsForUser(user.getUuid());
        assertThat(permissionsFound2).isEqualTo(Set.of(PERMISSION_NAME_2));
    }

    private User createUser(final String name) {
        User user = new User.Builder()
                .name(name)
                .uuid(UUID.randomUUID().toString())
                .build();
        AuditUtil.stamp("test", user);
        return userDao.create(user);
    }
}
