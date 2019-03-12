/*
 * Copyright 2016 Crown Copyright
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

package stroom.security.impl;


import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MySQLContainer;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.security.shared.UserRef;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestUserServiceImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestUserServiceImpl.class);

    private static MySQLContainer dbContainer = new MySQLContainer()
            .withDatabaseName(TestModule.DATABASE_NAME);//= null;//pu

    private static Injector injector;

    private static UserService userService;


    @BeforeAll
    public static void beforeAll() {
        LOGGER.info("Before All - Start Database");
        Optional.ofNullable(dbContainer).ifPresent(MySQLContainer::start);

        injector = Guice.createInjector(new TestModule(dbContainer));

        userService = injector.getInstance(UserService.class);
    }

    @Test
    void testSaveAndGetBasic() {
        createUser("saveAndGet");
    }

    @Test
    void testSaveAndGetUserGroups() {
        final UserRef user1 = createUser("saveGetUser1");
        final UserRef user2 = createUser("saveGetUser2");
        final UserRef userGroup1 = createUserGroup("saveGetGroup1");
        final UserRef userGroup2 = createUserGroup("saveGetGroup2");

        checkGroupsForUser(user1);
        checkGroupsForUser(user2);
        checkUsersInGroup(userGroup1);
        checkUsersInGroup(userGroup2);

        userService.addUserToGroup(user1, userGroup1);

        checkGroupsForUser(user1, userGroup1);
        checkGroupsForUser(user2);
        checkUsersInGroup(userGroup1, user1);
        checkUsersInGroup(userGroup2);

        userService.addUserToGroup(user1, userGroup2);

        checkGroupsForUser(user1, userGroup1, userGroup2);
        checkGroupsForUser(user2);
        checkUsersInGroup(userGroup1, user1);
        checkUsersInGroup(userGroup2, user1);

        userService.addUserToGroup(user2, userGroup1);

        checkGroupsForUser(user1, userGroup1, userGroup2);
        checkGroupsForUser(user2, userGroup1);
        checkUsersInGroup(userGroup1, user1, user2);
        checkUsersInGroup(userGroup2, user1);

        userService.removeUserFromGroup(user2, userGroup1);

        checkGroupsForUser(user1, userGroup1, userGroup2);
        checkGroupsForUser(user2);
        checkUsersInGroup(userGroup1, user1);
        checkUsersInGroup(userGroup2, user1);
    }

    private void checkGroupsForUser(final UserRef user, final UserRef... groups) {
        final List<UserRef> list = userService.findGroupsForUser(user);
        assertThat(list.size()).isEqualTo(groups.length);
        for (final UserRef group : groups) {
            assertThat(list.contains(group)).isTrue();
        }
    }

    private void checkUsersInGroup(final UserRef group, final UserRef... users) {
        final List<UserRef> list = userService.findUsersInGroup(group);
        assertThat(list.size()).isEqualTo(users.length);
        for (final UserRef user : users) {
            assertThat(list.contains(user)).isTrue();
        }
    }

    @Test
    void testFindUsers() {
        final UserRef user1 = createUser("findUser1");
        final UserRef user2 = createUser("findUser2");
        final UserRef userGroup1 = createUserGroup("findGroup1");
        final UserRef userGroup2 = createUserGroup("findGroup2");

        assertThat(userService.find(new FindUserCriteria(user1.getName(), false)).size()).isEqualTo(1);
        assertThat(userService.find(new FindUserCriteria(user2.getName(), false)).size()).isEqualTo(1);
        assertThat(userService.find(new FindUserCriteria(userGroup1.getName(), true)).size()).isEqualTo(1);
        assertThat(userService.find(new FindUserCriteria(userGroup2.getName(), true)).size()).isEqualTo(1);

        assertThat(userService.find(new FindUserCriteria(user1.getName(), true)).size()).isEqualTo(0);
        assertThat(userService.find(new FindUserCriteria(user2.getName(), true)).size()).isEqualTo(0);
        assertThat(userService.find(new FindUserCriteria(userGroup1.getName(), false)).size()).isEqualTo(0);
        assertThat(userService.find(new FindUserCriteria(userGroup2.getName(), false)).size()).isEqualTo(0);

        final Set<String> findUsers = userService.find(new FindUserCriteria(false))
                .stream()
                .map(User::getUuid)
                .collect(Collectors.toSet());
        final Set<String> findGroups = userService.find(new FindUserCriteria(true))
                .stream()
                .map(User::getUuid)
                .collect(Collectors.toSet());

        assertThat(findUsers).contains(user1.getUuid(), user2.getUuid());
        assertThat(findUsers).doesNotContain(userGroup1.getUuid(), userGroup2.getUuid());
        assertThat(findGroups).contains(userGroup1.getUuid(), userGroup2.getUuid());
        assertThat(findGroups).doesNotContain(user1.getUuid(), user2.getUuid());


    }

    private UserRef createUser(final String baseName) {
        UserRef userRef = userService.createUser(String.format("%s_%s", baseName, UUID.randomUUID()));
        assertThat(userRef).isNotNull();
        final User user = userService.loadByUuid(userRef.getUuid());
        assertThat(user).isNotNull();
        return UserRefFactory.create(user);
    }

    private UserRef createUserGroup(final String baseName) {
        UserRef userRef = userService.createUserGroup(String.format("%s_%s", baseName, UUID.randomUUID()));
        assertThat(userRef).isNotNull();
        final User user = userService.loadByUuid(userRef.getUuid());
        assertThat(user).isNotNull();
        return UserRefFactory.create(user);
    }
}
