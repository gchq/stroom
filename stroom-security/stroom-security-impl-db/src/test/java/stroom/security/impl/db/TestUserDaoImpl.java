package stroom.security.impl.db;

import stroom.security.impl.TestModule;
import stroom.security.impl.UserDao;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.util.AuditUtil;
import stroom.util.shared.ResultPage;

import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestUserDaoImpl {

    @Inject
    private UserDao userDao;
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
    void createAndGetUser() {
        // Given
        final String userName = String.format("SomeTestPerson_%s", UUID.randomUUID());

        // When
        final User userCreated = createUser(userName, false);
        assertThat(userCreated).isNotNull();
        final Optional<User> foundByUuid = userDao.getByUuid(userCreated.getUuid());
        final Optional<User> foundByName = userDao.getUserBySubjectId(userName);

        // Then
        assertThat(userCreated.getUuid()).isNotNull();
        assertThat(userCreated.isGroup()).isFalse();

        Stream.of(foundByUuid, foundByName)
                .forEach(u -> {
                    assertThat(u).isPresent();
                    assertThat(u.map(User::getUuid).get()).isEqualTo(userCreated.getUuid());
                    assertThat(u.map(User::getSubjectId).get()).isEqualTo(userName);
                    assertThat(u.map(User::isGroup).get()).isFalse();
                });
    }

    @Test
    void createUserGroup() {
        // Given
        final String groupName = String.format("SomeTestGroup_%s", UUID.randomUUID());

        // When
        final User userCreated = createUser(groupName, true);
        assertThat(userCreated).isNotNull();
        final Optional<User> foundByUuid = userDao.getByUuid(userCreated.getUuid());
        final Optional<User> foundByName = userDao.getGroupByName(groupName);

        // Then
        assertThat(userCreated.getUuid()).isNotNull();
        assertThat(userCreated.isGroup()).isTrue();

        Stream.of(foundByUuid, foundByName).forEach(u -> {
            assertThat(u).isPresent();
            assertThat(u.map(User::getUuid).get()).isEqualTo(userCreated.getUuid());
            assertThat(u.map(User::getSubjectId).get()).isEqualTo(groupName);
            assertThat(u.map(User::isGroup).get()).isTrue();
        });
    }

    @Test
    void setEnabledUser() {
        // Given
        final String userName = String.format("SomeTestPerson_%s", UUID.randomUUID());

        // When
        final User userCreated = createUser(userName, false);
        final Optional<User> userFoundBeforeDelete = userDao.getByUuid(userCreated.getUuid());
        userCreated.setEnabled(false);
        userDao.update(userCreated);
        final Optional<User> userFoundAfterDelete = userDao.getByUuid(userCreated.getUuid());

        // Then
        assertThat(userCreated).isNotNull();
        assertThat(userFoundBeforeDelete).isPresent();
        assertThat(userFoundAfterDelete).isEmpty();
    }

    @Test
    void findUsersInGroup() {
        // Given
        final List<String> userNames = IntStream.range(0, 3)
                .mapToObj(i -> String.format("SomePerson_%s", UUID.randomUUID()))
                .toList();
        final String groupName = String.format("SomeGroup_%s", UUID.randomUUID());

        // When
        final User group = createUser(groupName, true);
        final List<User> users = userNames.stream()
                .map(name -> createUser(name, false))
                .peek(u -> userDao.addUserToGroup(u.getUuid(), group.getUuid()))
                .toList();
        final ResultPage<User> usersInGroup = userDao
                .findUsersInGroup(group.getUuid(), new FindUserCriteria());

        // Then
        userNames.forEach(userName -> {
            assertThat(users.stream()
                    .anyMatch(u -> userName.equals(u.getSubjectId()))).isTrue();
            assertThat(usersInGroup.stream()
                    .anyMatch(u -> userName.equals(u.getSubjectId()))).isTrue();
        });
    }

    @Test
    void findGroupsForUser() {
        // Given
        final List<String> userNames = IntStream.range(0, 3)
                .mapToObj(i -> String.format("SomePerson_%s", UUID.randomUUID()))
                .toList();
        final List<String> groupNames = IntStream.range(0, 3)
                .mapToObj(i -> String.format("SomeGroup_%s", UUID.randomUUID()))
                .toList();
        final String userNameToTest = userNames.getFirst();

        // When
        final List<User> users = userNames.stream()
                .map(name -> createUser(name, false))
                .toList();
        final User userToTest = users.stream()
                .filter(u -> userNameToTest.equals(u.getSubjectId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find user to test amongst created users"));
        final List<User> groups = groupNames.stream()
                .map(name -> createUser(name, true))
                .peek(g -> users.forEach(
                        u -> userDao.addUserToGroup(u.getUuid(), g.getUuid())))
                .toList();
        final ResultPage<User> groupsForUserToTest = userDao
                .findGroupsForUser(userToTest.getUuid(), new FindUserCriteria());

        // Then
        groupNames.forEach(groupName -> assertThat(groupsForUserToTest.stream()
                .anyMatch(g -> groupName.equals(g.getSubjectId())))
                .isTrue());

        // Try adding all of the users to the same groups again.
        groups.forEach(g -> users.forEach(
                u -> userDao.addUserToGroup(u.getUuid(), g.getUuid())));

        final ResultPage<User> groupsForUserToTest2 = userDao
                .findGroupsForUser(userToTest.getUuid(), new FindUserCriteria());

        // Then
        groupNames.forEach(groupName -> assertThat(groupsForUserToTest2.stream()
                .anyMatch(g -> groupName.equals(g.getSubjectId())))
                .isTrue());

    }

    @Test
    void getBySubjectId_notFound() {

        final Optional<User> optUser = userDao.getUserBySubjectId("foo");

        assertThat(optUser)
                .isEmpty();
    }

    @Test
    void getBySubjectId_foundOne_user() {

        final User user = createUser("foo", false);
        final Optional<User> optUser = userDao.getUserBySubjectId("foo");

        assertThat(optUser)
                .contains(user);
    }

    @Test
    void getGroupByName_foundOne_group() {

        final User grp = createUser("grp4", true);
        final Optional<User> optUser = userDao.getGroupByName("grp4");

        assertThat(optUser)
                .contains(grp);
    }

    @Test
    void getBySubjectId_foundMultiple() {
        final User user = createUser("foo", false);
        final User group = createUser("foo", true);

        assertThat(userDao.getUserBySubjectId("foo").orElse(null)).isEqualTo(user);
        assertThat(userDao.getGroupByName("foo").orElse(null)).isEqualTo(group);
    }

    private User createUser(final String name, boolean group) {
        User user = User.builder()
                .subjectId(name)
                .uuid(UUID.randomUUID().toString())
                .group(group)
                .build();
        AuditUtil.stamp(() -> "test", user);
        return userDao.create(user);
    }
}
