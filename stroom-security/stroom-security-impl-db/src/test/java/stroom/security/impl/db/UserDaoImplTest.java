package stroom.security.impl.db;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import stroom.security.impl.TestModule;
import stroom.security.impl.UserDao;
import stroom.security.shared.User;
import stroom.util.AuditUtil;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class UserDaoImplTest {
    private static UserDao userDao;

    @BeforeAll
    static void beforeAll() {
        final Injector injector = Guice.createInjector(new SecurityDbModule(), new TestModule());
        userDao = injector.getInstance(UserDao.class);
    }

    @Test
    void createAndGetUser() {
        // Given
        final String userName = String.format("SomeTestPerson_%s", UUID.randomUUID());

        // When
        final User userCreated = createUser(userName, false);
        assertThat(userCreated).isNotNull();
        final User foundByUuid = userDao.getByUuid(userCreated.getUuid());
        final User foundByName = userDao.getByName(userName);
        final User foundById = userDao.getById(userCreated.getId());

        // Then
        assertThat(userCreated.getUuid()).isNotNull();
        assertThat(userCreated.isGroup()).isFalse();

        Stream.of(foundByUuid, foundByName, foundById).forEach(u -> {
            assertThat(u).isNotNull();
            assertThat(u.getUuid()).isEqualTo(userCreated.getUuid());
            assertThat(u.getName()).isEqualTo(userName);
            assertThat(u.isGroup()).isFalse();
        });
    }

    @Test
    void createUserGroup() {
        // Given
        final String userName = String.format("SomeTestPerson_%s", UUID.randomUUID());

        // When
        final User userCreated = createUser(userName, true);
        assertThat(userCreated).isNotNull();
        final User foundByUuid = userDao.getByUuid(userCreated.getUuid());
        final User foundByName = userDao.getByName(userName);
        final User foundById = userDao.getById(userCreated.getId());

        // Then
        assertThat(userCreated.getUuid()).isNotNull();
        assertThat(userCreated.isGroup()).isTrue();

        Stream.of(foundByUuid, foundByName, foundById).forEach(u -> {
            assertThat(u).isNotNull();
            assertThat(u.getUuid()).isEqualTo(userCreated.getUuid());
            assertThat(u.getName()).isEqualTo(userName);
            assertThat(u.isGroup()).isTrue();
        });
    }

    @Test
    void deleteUser() {
        // Given
        final String userName = String.format("SomeTestPerson_%s", UUID.randomUUID());

        // When
        final User userCreated = createUser(userName, false);
        final User userFoundBeforeDelete = userDao.getById(userCreated.getId());
        userDao.delete(userCreated.getUuid());
        final User userFoundAfterDelete = userDao.getById(userCreated.getId());

        // Then
        assertThat(userCreated).isNotNull();
        assertThat(userFoundBeforeDelete).isNotNull();
        assertThat(userFoundAfterDelete).isNull();
    }

    @Test
    void findUsersInGroup() {
        // Given
        final List<String> userNames = IntStream.range(0, 3)
                .mapToObj(i -> String.format("SomePerson_%s", UUID.randomUUID()))
                .collect(Collectors.toList());
        final String groupName = String.format("SomeGroup_%s", UUID.randomUUID());

        // When
        final User group = createUser(groupName, true);
        final List<User> users = userNames.stream()
                .map(name -> createUser(name, false))
                .peek(u -> userDao.addUserToGroup(u.getUuid(), group.getUuid()))
                .collect(Collectors.toList());
        final List<User> usersInGroup = userDao.findUsersInGroup(group.getUuid());

        // Then
        userNames.forEach(userName -> {
            assertThat(users.stream()
                    .anyMatch(u -> userName.equals(u.getName()))).isTrue();
            assertThat(usersInGroup.stream()
                    .anyMatch(u -> userName.equals(u.getName()))).isTrue();
        });
    }

    @Test
    void findGroupsForUser() {
        // Given
        final List<String> userNames = IntStream.range(0, 3)
                .mapToObj(i -> String.format("SomePerson_%s", UUID.randomUUID()))
                .collect(Collectors.toList());
        final List<String> groupNames = IntStream.range(0, 3)
                .mapToObj(i -> String.format("SomeGroup_%s", UUID.randomUUID()))
                .collect(Collectors.toList());
        final String userNameToTest = userNames.get(0);

        // When
        final List<User> users = userNames.stream()
                .map(name -> createUser(name, false))
                .collect(Collectors.toList());
        final User userToTest = users.stream()
                .filter(u -> userNameToTest.equals(u.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find user to test amongst created users"));
        final List<User> groups = groupNames.stream()
                .map(name -> createUser(name, true))
                .peek(g -> users.forEach(
                        u -> userDao.addUserToGroup(u.getUuid(), g.getUuid())))
                .collect(Collectors.toList());
        final List<User> groupsForUserToTest = userDao.findGroupsForUser(userToTest.getUuid());

        // Then
        groupNames.forEach(groupName -> assertThat(groupsForUserToTest.stream()
                .anyMatch(g -> groupName.equals(g.getName())))
                .isTrue());
    }

    private User createUser(final String name, boolean group) {
        User user = new User.Builder()
                .name(name)
                .uuid(UUID.randomUUID().toString())
                .group(group)
                .build();
        AuditUtil.stamp("test", user);
        return userDao.create(user);
    }
}
