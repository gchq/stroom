package stroom.security.impl.db;

import stroom.db.util.JooqUtil;
import stroom.security.impl.TestModule;
import stroom.security.impl.UserDao;
import stroom.security.shared.User;
import stroom.util.AuditUtil;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.security.impl.db.jooq.Tables.STROOM_USER;
import static stroom.security.impl.db.jooq.Tables.STROOM_USER_GROUP;

class UserDaoImplTest {

    @Inject
    private UserDao userDao;
    @Inject
    private Injector injector;
    @Inject
    private SecurityDbConnProvider securityDbConnProvider;

    @BeforeEach
    void beforeAll() {
        final Injector injector = Guice.createInjector(
                new SecurityDbModule(),
                new SecurityDaoModule(),
                new TestModule());

        injector.injectMembers(this);
    }

    @AfterEach
    void tearDown() {
        JooqUtil.context(securityDbConnProvider, context -> {
            JooqUtil.deleteAll(context, STROOM_USER_GROUP);
            JooqUtil.deleteAll(context, STROOM_USER);
        });
    }

    @Test
    void createAndGetUser() {
        // Given
        final String userName = String.format("SomeTestPerson_%s", UUID.randomUUID());

        // When
        final User userCreated = createUser(userName, false);
        assertThat(userCreated).isNotNull();
        final Optional<User> foundByUuid = userDao.getByUuid(userCreated.getUuid());
        final Optional<User> foundByName = userDao.getBySubjectId(userName);
        final Optional<User> foundById = userDao.getById(userCreated.getId());

        // Then
        assertThat(userCreated.getUuid()).isNotNull();
        assertThat(userCreated.isGroup()).isFalse();

        Stream.of(foundByUuid, foundByName, foundById)
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
        final String userName = String.format("SomeTestPerson_%s", UUID.randomUUID());

        // When
        final User userCreated = createUser(userName, true);
        assertThat(userCreated).isNotNull();
        final Optional<User> foundByUuid = userDao.getByUuid(userCreated.getUuid());
        final Optional<User> foundByName = userDao.getBySubjectId(userName);
        final Optional<User> foundById = userDao.getById(userCreated.getId());

        // Then
        assertThat(userCreated.getUuid()).isNotNull();
        assertThat(userCreated.isGroup()).isTrue();

        Stream.of(foundByUuid, foundByName, foundById).forEach(u -> {
            assertThat(u).isPresent();
            assertThat(u.map(User::getUuid).get()).isEqualTo(userCreated.getUuid());
            assertThat(u.map(User::getSubjectId).get()).isEqualTo(userName);
            assertThat(u.map(User::isGroup).get()).isTrue();
        });
    }

    @Test
    void deleteUser() {
        // Given
        final String userName = String.format("SomeTestPerson_%s", UUID.randomUUID());

        // When
        final User userCreated = createUser(userName, false);
        final Optional<User> userFoundBeforeDelete = userDao.getById(userCreated.getId());
        userDao.delete(userCreated.getUuid());
        final Optional<User> userFoundAfterDelete = userDao.getById(userCreated.getId());

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
                .collect(Collectors.toList());
        final String groupName = String.format("SomeGroup_%s", UUID.randomUUID());

        // When
        final User group = createUser(groupName, true);
        final List<User> users = userNames.stream()
                .map(name -> createUser(name, false))
                .peek(u -> userDao.addUserToGroup(u.getUuid(), group.getUuid()))
                .collect(Collectors.toList());
        final List<User> usersInGroup = userDao.findUsersInGroup(group.getUuid(), null);

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
        final String userNameToTest = userNames.get(0);

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
        final List<User> groupsForUserToTest = userDao.findGroupsForUser(userToTest.getUuid(), null);

        // Then
        groupNames.forEach(groupName -> assertThat(groupsForUserToTest.stream()
                .anyMatch(g -> groupName.equals(g.getSubjectId())))
                .isTrue());
    }

    @Test
    void getBySubjectId_notFound() {

        final Optional<User> optUser = userDao.getBySubjectId("foo");

        assertThat(optUser)
                .isEmpty();
    }

    @Test
    void getBySubjectId_foundOne_user() {

        final User user = createUser("foo", false);
        final Optional<User> optUser = userDao.getBySubjectId("foo");

        assertThat(optUser)
                .contains(user);
    }

    @Test
    void getBySubjectId_foundOne_group() {

        final User grp = createUser("grp4", true);
        final Optional<User> optUser = userDao.getBySubjectId("grp4");

        assertThat(optUser)
                .contains(grp);
    }

    @Test
    void getBySubjectId_foundMultiple() {

        final User user = createUser("foo", false);
        final User grp = createUser("foo", true);

        Assertions.assertThatThrownBy(() -> {
                    userDao.getBySubjectId("foo");
                }).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Found more than one user/group");
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
