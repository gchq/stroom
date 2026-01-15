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

import stroom.dictionary.shared.DictionaryDoc;
import stroom.query.shared.QueryDoc;
import stroom.script.shared.ScriptDoc;
import stroom.security.impl.AppPermissionDao;
import stroom.security.impl.DocumentPermissionDao;
import stroom.security.impl.TestModule;
import stroom.security.impl.UserDao;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.FindUserContext;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.util.AuditUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserInfo;

import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.jooq.exception.IntegrityConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestUserDaoImpl {

    @Inject
    private UserDao userDao;
    @Inject
    private AppPermissionDao appPermissionDao;
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
        assertThat(!userFoundAfterDelete.get().isEnabled()).isTrue();
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
    void findAnnotationAssignmentUsers() {
        // Given
        final List<String> userNames = IntStream.range(0, 3)
                .mapToObj(i -> String.format("SomePerson_%s", UUID.randomUUID()))
                .toList();
        final String groupName = String.format("SomeGroup_%s", UUID.randomUUID());

        final List<String> otherUserNames = IntStream.range(0, 3)
                .mapToObj(i -> String.format("OtherPerson_%s", UUID.randomUUID()))
                .toList();
        final String otherGroupName = String.format("OtherGroup_%s", UUID.randomUUID());

        // Create others.
        final User otherGroup = createUser(otherGroupName, true);
        final List<User> otherUsers = otherUserNames.stream()
                .map(name -> createUser(name, false))
                .peek(u -> userDao.addUserToGroup(u.getUuid(), otherGroup.getUuid()))
                .toList();

        // Create users.
        final User group = createUser(groupName, true);
        final List<User> users = userNames.stream()
                .map(name -> createUser(name, false))
                .peek(u -> userDao.addUserToGroup(u.getUuid(), group.getUuid()))
                .toList();

        final String otherGroupName2 = String.format("OtherGroup2_%s", UUID.randomUUID());
        final User otherGroup2 = createUser(otherGroupName2, true);
        users.forEach(u -> userDao.addUserToGroup(u.getUuid(), otherGroup2.getUuid()));

        // Make sure we can only see users.
        final ResultPage<User> visibleUsers = userDao
                .findRelatedUsers(users.getFirst().getUuid(), new FindUserCriteria.Builder()
                        .context(FindUserContext.ANNOTATION_ASSIGNMENT).build());

        assertThat(visibleUsers.size()).isEqualTo(5);
        assertThat(visibleUsers.getValues()).containsAll(users);
        assertThat(visibleUsers.getValues()).contains(group);
        assertThat(visibleUsers.getValues()).contains(otherGroup2);
    }

    @Test
    void findRunAsUsers() {
        // Given
        final List<String> userNames = IntStream.range(0, 3)
                .mapToObj(i -> String.format("SomePerson_%s", UUID.randomUUID()))
                .toList();
        final String groupName = String.format("SomeGroup_%s", UUID.randomUUID());

        final List<String> otherUserNames = IntStream.range(0, 3)
                .mapToObj(i -> String.format("OtherPerson_%s", UUID.randomUUID()))
                .toList();
        final String otherGroupName = String.format("OtherGroup_%s", UUID.randomUUID());

        // Create others.
        final User otherGroup = createUser(otherGroupName, true);
        final List<User> otherUsers = otherUserNames.stream()
                .map(name -> createUser(name, false))
                .peek(u -> userDao.addUserToGroup(u.getUuid(), otherGroup.getUuid()))
                .toList();

        // Create users.
        final User group = createUser(groupName, true);
        final List<User> users = userNames.stream()
                .map(name -> createUser(name, false))
                .peek(u -> userDao.addUserToGroup(u.getUuid(), group.getUuid()))
                .toList();

        final String otherGroupName2 = String.format("OtherGroup2_%s", UUID.randomUUID());
        final User otherGroup2 = createUser(otherGroupName2, true);
        users.forEach(u -> userDao.addUserToGroup(u.getUuid(), otherGroup2.getUuid()));

        // Make sure we can only see users.
        final ResultPage<User> visibleUsers = userDao
                .findRelatedUsers(users.getFirst().getUuid(), new FindUserCriteria.Builder()
                        .context(FindUserContext.RUN_AS).build());

        assertThat(visibleUsers.size()).isEqualTo(3);
        assertThat(visibleUsers.getValues()).contains(users.getFirst());
        assertThat(visibleUsers.getValues()).contains(group);
        assertThat(visibleUsers.getValues()).contains(otherGroup2);
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

        assertThat(userDao.getUserBySubjectId("foo"))
                .hasValue(user);
        assertThat(userDao.getGroupByName("foo"))
                .hasValue(group);
    }

    @Test
    void delete_noMembership() {
        final User user = createUser("foo", false);
        final User group = createUser("group4", true);

        UserInfo userInfo = userDao.getUserInfoByUserUuid(user.getUuid())
                .orElseThrow();
        assertThat(userInfo.getUuid())
                .isEqualTo(user.getUuid());
        assertThat(userInfo.getSubjectId())
                .isEqualTo(user.getSubjectId());
        assertThat(userInfo.getDisplayName())
                .isEqualTo(user.getDisplayName());
        assertThat(userInfo.getFullName())
                .isEqualTo(user.getFullName());
        assertThat(userInfo.isGroup())
                .isEqualTo(user.isGroup());
        assertThat(userInfo.isEnabled())
                .isEqualTo(user.isEnabled());
        assertThat(userInfo.isDeleted())
                .isEqualTo(false);

        userDao.deleteUser(user.asRef());

        final Optional<User> optUser = userDao.getByUuid(user.getUuid());
        assertThat(optUser)
                .isEmpty();

        userInfo = userDao.getUserInfoByUserUuid(user.getUuid())
                .orElseThrow();
        assertThat(userInfo.getUuid())
                .isEqualTo(user.getUuid());
        assertThat(userInfo.getSubjectId())
                .isEqualTo(user.getSubjectId());
        assertThat(userInfo.getDisplayName())
                .isEqualTo(user.getDisplayName());
        assertThat(userInfo.getFullName())
                .isEqualTo(user.getFullName());
        assertThat(userInfo.isGroup())
                .isEqualTo(user.isGroup());
        assertThat(userInfo.isEnabled())
                .isEqualTo(false);  // Has been deleted
        assertThat(userInfo.isDeleted())
                .isEqualTo(true); // Has been deleted
    }

    @Test
    void delete_withMembershipAndPerms() {
        final String docUuid1 = "doc1uuid";
        final String docUuid2 = "doc2uuid";
        final User user1 = createUser("foo", false);
        final User user2 = createUser("bar", false);
        final User group4 = createUser("group4", true);
        final String uuid1 = user1.getUuid();
        final String uuid2 = user2.getUuid();
        final String uuid4 = group4.getUuid();

        // Set up memberships
        userDao.addUserToGroup(uuid1, uuid4);
        userDao.addUserToGroup(uuid2, uuid4);

        // Set up app perms
        appPermissionDao.addPermission(user1.getUuid(), AppPermission.STEPPING_PERMISSION);
        appPermissionDao.addPermission(user1.getUuid(), AppPermission.VIEW_DATA_PERMISSION);

        appPermissionDao.addPermission(user2.getUuid(), AppPermission.CHANGE_OWNER_PERMISSION);
        appPermissionDao.addPermission(user2.getUuid(), AppPermission.MANAGE_TASKS_PERMISSION);

        appPermissionDao.addPermission(group4.getUuid(), AppPermission.ADMINISTRATOR);
        appPermissionDao.addPermission(group4.getUuid(), AppPermission.ANNOTATIONS);

        // Set up doc perms
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


        // Assert memberships pre-delete
        assertThat(userDao.findUsersInGroup(uuid4, new FindUserCriteria()).getValues())
                .containsExactlyInAnyOrder(user1, user2);

        // Assert app perms pre-delete
        assertThat(appPermissionDao.getPermissionsForUser(user1.getUuid()))
                .containsExactlyInAnyOrder(
                        AppPermission.STEPPING_PERMISSION,
                        AppPermission.VIEW_DATA_PERMISSION);
        assertThat(appPermissionDao.getPermissionsForUser(user2.getUuid()))
                .containsExactlyInAnyOrder(
                        AppPermission.CHANGE_OWNER_PERMISSION,
                        AppPermission.MANAGE_TASKS_PERMISSION);
        assertThat(appPermissionDao.getPermissionsForUser(group4.getUuid()))
                .containsExactlyInAnyOrder(
                        AppPermission.ADMINISTRATOR,
                        AppPermission.ANNOTATIONS);

        // Assert doc perms pre-delete
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

        final Runnable assertUser2Perms = () -> {
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
        };
        assertUser2Perms.run();

        // Now delete the user
        userDao.deleteUser(user1.asRef());

        Optional<User> optUser = userDao.getByUuid(uuid1);
        assertThat(optUser)
                .isEmpty();

        optUser = userDao.getByUuid(uuid2);
        assertThat(optUser)
                .isPresent();

        // Assert memberships post-delete
        assertThat(userDao.findUsersInGroup(uuid4, new FindUserCriteria()).getValues())
                .containsExactlyInAnyOrder(user2);

        // Assert app perms post-delete
        assertThat(appPermissionDao.getPermissionsForUser(user1.getUuid()))
                .isEmpty();
        assertThat(appPermissionDao.getPermissionsForUser(user2.getUuid()))
                .containsExactlyInAnyOrder(
                        AppPermission.CHANGE_OWNER_PERMISSION,
                        AppPermission.MANAGE_TASKS_PERMISSION);
        assertThat(appPermissionDao.getPermissionsForUser(group4.getUuid()))
                .containsExactlyInAnyOrder(
                        AppPermission.ADMINISTRATOR,
                        AppPermission.ANNOTATIONS);

        // Assert doc perms post-delete
        assertThat(documentPermissionDao.getPermissionsForUser(uuid1).getPermissions())
                .isEmpty();
        assertThat(documentPermissionDao.getDocumentUserCreatePermissions(docUuid1, uuid1))
                .isEmpty();
        assertThat(documentPermissionDao.getDocumentUserCreatePermissions(docUuid2, uuid1))
                .isEmpty();

        assertUser2Perms.run();
    }

    @Test
    void testCopyGroupsAndPermissions() {
        // given we have two users with app and doc permissions and two groups containing one user each
        final String docUuid1 = "doc1uuid";
        final String docUuid2 = "doc2uuid";
        final User user1 = createUser("user1", false);
        final User user2 = createUser("user2", false);
        final User group1 = createUser("group1", true);
        final User group2 = createUser("group2", true);

        userDao.addUserToGroup(user1.getUuid(), group1.getUuid());
        userDao.addUserToGroup(user2.getUuid(), group2.getUuid());

        appPermissionDao.addPermission(user1.getUuid(), AppPermission.STEPPING_PERMISSION);
        appPermissionDao.addPermission(user1.getUuid(), AppPermission.CHANGE_OWNER_PERMISSION);
        appPermissionDao.addPermission(user2.getUuid(), AppPermission.CHANGE_OWNER_PERMISSION);
        appPermissionDao.addPermission(user2.getUuid(), AppPermission.MANAGE_CACHE_PERMISSION);

        documentPermissionDao.setDocumentUserPermission(docUuid1, user1.getUuid(), DocumentPermission.OWNER);
        documentPermissionDao.setDocumentUserPermission(docUuid2, user1.getUuid(), DocumentPermission.VIEW);
        documentPermissionDao.setDocumentUserCreatePermissions(
                docUuid1, user1.getUuid(), Set.of(DictionaryDoc.TYPE, ScriptDoc.TYPE));
        documentPermissionDao.setDocumentUserCreatePermissions(
                docUuid2, user1.getUuid(), Set.of(DictionaryDoc.TYPE, ScriptDoc.TYPE));

        assertThat(appPermissionDao.getPermissionsForUser(user1.getUuid()).size()).isEqualTo(2);
        assertThat(appPermissionDao.getPermissionsForUser(user2.getUuid()).size()).isEqualTo(2);
        assertThat(userDao.findUsersInGroup(group1.getUuid(), new FindUserCriteria()).size()).isEqualTo(1);
        assertThat(userDao.findUsersInGroup(group2.getUuid(), new FindUserCriteria()).size()).isEqualTo(1);

        assertThat(documentPermissionDao.getPermissionsForUser(user2.getUuid()).getPermissions()).isEmpty();
        assertThat(documentPermissionDao.getDocumentUserCreatePermissions(docUuid1, user2.getUuid())).isEmpty();
        assertThat(documentPermissionDao.getDocumentUserCreatePermissions(docUuid2, user2.getUuid())).isEmpty();

        // when we copy the groups and permissions from one user to the other
        userDao.copyGroupsAndPermissions(user1.getUuid(), user2.getUuid());

        // then the 'to' user is found in the group of the 'from' user
        final List<User> usersInGroup1 = userDao.findUsersInGroup(group1.getUuid(), new FindUserCriteria()).getValues();
        assertThat(usersInGroup1).containsExactlyInAnyOrder(user1, user2);

        // and the 'to' user is still in it original group
        assertThat(userDao.findGroupsForUser(user2.getUuid(), new FindUserCriteria()).getValues())
                .containsExactlyInAnyOrder(group1, group2);

        // and the app and doc permissions have been copied to the user
        assertThat(appPermissionDao.getPermissionsForUser(user1.getUuid()).size()).isEqualTo(2);

        final Set<AppPermission> user2AppPermissions = appPermissionDao.getPermissionsForUser(user2.getUuid());
        assertThat(user2AppPermissions).containsExactlyInAnyOrder(AppPermission.STEPPING_PERMISSION,
                AppPermission.CHANGE_OWNER_PERMISSION, AppPermission.MANAGE_CACHE_PERMISSION);

        assertThat(documentPermissionDao.getPermissionsForUser(user2.getUuid()).getPermissions())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        docUuid1, DocumentPermission.OWNER,
                        docUuid2, DocumentPermission.VIEW));
        assertThat(documentPermissionDao.getDocumentUserCreatePermissions(docUuid1, user2.getUuid()))
                .containsExactlyInAnyOrder(
                        DictionaryDoc.TYPE,
                        ScriptDoc.TYPE);
        assertThat(documentPermissionDao.getDocumentUserCreatePermissions(docUuid2, user2.getUuid()))
                .containsExactlyInAnyOrder(
                        DictionaryDoc.TYPE,
                        ScriptDoc.TYPE);
    }

    @Test
    void testCopyGroupsAndPermissionsThrowsException() {
        // given a user in a group
        final User user1 = createUser("user1", false);
        final User group1 = createUser("group1", true);

        userDao.addUserToGroup(user1.getUuid(), group1.getUuid());

        // when we copy the groups and permissions to a user that doesnt exist
        // then an exception is thrown
        assertThatThrownBy(() -> userDao.copyGroupsAndPermissions(user1.getUuid(), "doesnotexist"))
                .isInstanceOf(IntegrityConstraintViolationException.class);
    }

    private User createUser(final String name, final boolean group) {
        final User userOrGroup = User.builder()
                .subjectId(name)
                .uuid(UUID.randomUUID().toString())
                .group(group)
                .build();
        AuditUtil.stamp(() -> "test", userOrGroup);
        userDao.create(userOrGroup);

        if (group) {
            assertThat(userDao.getGroupByName(name))
                    .hasValue(userOrGroup)
                    .get()
                    .extracting(User::isGroup)
                    .isEqualTo(true);
        } else {
            assertThat(userDao.getUserBySubjectId(name))
                    .hasValue(userOrGroup)
                    .get()
                    .extracting(User::isGroup)
                    .isEqualTo(false);
        }

        return userOrGroup;
    }
}
