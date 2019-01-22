package stroom.security.impl.db;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.testcontainers.containers.MySQLContainer;
import stroom.security.dao.UserDao;
import stroom.security.model.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class UserDaoImplTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserDaoImplTest.class);

    private static MySQLContainer dbContainer = new MySQLContainer()
            .withDatabaseName(Stroom.STROOM.getName());//= null;//

    private static UserDao userDao;

    @BeforeAll
    public static void beforeAll() {
        LOGGER.info(() -> "Before All - Start Database");
        Optional.ofNullable(dbContainer)
                .ifPresent(MySQLContainer::start);

        final Injector injector = Guice.createInjector(new SecurityDbModule(), new TestModule(dbContainer));

        userDao = injector.getInstance(UserDao.class);
    }

    @Test
    public void createAndGetUser() {
        // Given
        final String userName = String.format("SomeTestPerson_%s", UUID.randomUUID());

        // When
        final User userCreated = userDao.createUser(userName);
        assertThat(userCreated).isNotNull();
        final User foundByUuid = userDao.getByUuid(userCreated.getUuid());
        final User foundByName = userDao.getUserByName(userName);
        final User foundById = userDao.getById(userCreated.getId());

        // Then
        assertThat(userCreated.getUuid()).isNotNull();
        assertThat(userCreated.getIsGroup()).isFalse();

        Stream.of(foundByUuid, foundByName, foundById).forEach(u -> {
            assertThat(u).isNotNull();
            assertThat(u.getUuid()).isEqualTo(userCreated.getUuid());
            assertThat(u.getName()).isEqualTo(userName);
            assertThat(u.getIsGroup()).isFalse();
        });
    }

    @Test
    public void createUserGroup() {
        // Given
        final String userName = String.format("SomeTestPerson_%s", UUID.randomUUID());

        // When
        final User userCreated = userDao.createUserGroup(userName);
        assertThat(userCreated).isNotNull();
        final User foundByUuid = userDao.getByUuid(userCreated.getUuid());
        final User foundByName = userDao.getUserByName(userName);
        final User foundById = userDao.getById(userCreated.getId());

        // Then
        assertThat(userCreated.getUuid()).isNotNull();
        assertThat(userCreated.getIsGroup()).isTrue();

        Stream.of(foundByUuid, foundByName, foundById).forEach(u -> {
            assertThat(u).isNotNull();
            assertThat(u.getUuid()).isEqualTo(userCreated.getUuid());
            assertThat(u.getName()).isEqualTo(userName);
            assertThat(u.getIsGroup()).isTrue();
        });
    }

    @Test
    public void deleteUser() {
        // Given
        final String userName = String.format("SomeTestPerson_%s", UUID.randomUUID());

        // When
        final User userCreated = userDao.createUser(userName);
        final User userFoundBeforeDelete = userDao.getById(userCreated.getId());
        userDao.deleteUser(userCreated.getUuid());
        final User userFoundAfterDelete = userDao.getById(userCreated.getId());

        // Then
        assertThat(userCreated).isNotNull();
        assertThat(userFoundBeforeDelete).isNotNull();
        assertThat(userFoundAfterDelete).isNull();
    }

    @Test
    public void findUsersInGroup() {
        // Given
        final List<String> userNames = IntStream.range(0, 3)
                .mapToObj(i -> String.format("SomePerson_%s", UUID.randomUUID()))
                .collect(Collectors.toList());
        final String groupName = String.format("SomeGroup_%s", UUID.randomUUID());

        // When
        final User group = userDao.createUserGroup(groupName);
        final List<User> users = userNames.stream()
                .map(userDao::createUser)
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
    public void findGroupsForUser() {
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
                .map(userDao::createUser)
                .collect(Collectors.toList());
        final User userToTest = users.stream()
                .filter(u -> userNameToTest.equals(u.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find user to test amongst created users"));
        final List<User> groups = groupNames.stream()
                .map(userDao::createUserGroup)
                .peek(g -> users.forEach(
                        u -> userDao.addUserToGroup(u.getUuid(), g.getUuid())))
                .collect(Collectors.toList());
        final List<User> groupsForUserToTest = userDao.findGroupsForUser(userToTest.getUuid());

        // Then
        groupNames.forEach(groupName -> assertThat(groupsForUserToTest.stream()
                .anyMatch(g -> groupName.equals(g.getName())))
                .isTrue());
    }

    @AfterAll
    public static void afterAll() {
        LOGGER.info(() -> "After All - Stop Database");
        Optional.ofNullable(dbContainer)
                .ifPresent(MySQLContainer::stop);
    }
}
