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


import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestUserServiceImpl {
    private static UserService userService;

    @BeforeAll
    static void beforeAll() {
        final Injector injector = Guice.createInjector(new TestModule());
        userService = injector.getInstance(UserService.class);
    }

    @Test
    void testSaveAndGetBasic() {
        createUser("saveAndGet");
    }

    @Test
    void testSaveAndGetUserGroups() {
        final User user1 = createUser("saveGetUser1");
        final User user2 = createUser("saveGetUser2");
        final User userGroup1 = createUserGroup("saveGetGroup1");
        final User userGroup2 = createUserGroup("saveGetGroup2");

        checkGroupsForUser(user1);
        checkGroupsForUser(user2);
        checkUsersInGroup(userGroup1);
        checkUsersInGroup(userGroup2);

        userService.addUserToGroup(user1.getUuid(), userGroup1.getUuid());

        checkGroupsForUser(user1, userGroup1);
        checkGroupsForUser(user2);
        checkUsersInGroup(userGroup1, user1);
        checkUsersInGroup(userGroup2);

        userService.addUserToGroup(user1.getUuid(), userGroup2.getUuid());

        checkGroupsForUser(user1, userGroup1, userGroup2);
        checkGroupsForUser(user2);
        checkUsersInGroup(userGroup1, user1);
        checkUsersInGroup(userGroup2, user1);

        userService.addUserToGroup(user2.getUuid(), userGroup1.getUuid());

        checkGroupsForUser(user1, userGroup1, userGroup2);
        checkGroupsForUser(user2, userGroup1);
        checkUsersInGroup(userGroup1, user1, user2);
        checkUsersInGroup(userGroup2, user1);

        userService.removeUserFromGroup(user2.getUuid(), userGroup1.getUuid());

        checkGroupsForUser(user1, userGroup1, userGroup2);
        checkGroupsForUser(user2);
        checkUsersInGroup(userGroup1, user1);
        checkUsersInGroup(userGroup2, user1);
    }

    private void checkGroupsForUser(final User user, final User... groups) {
        final List<User> list = userService.findGroupsForUser(user.getUuid());
        assertThat(list.size()).isEqualTo(groups.length);
        for (final User group : groups) {
            assertThat(list.contains(group)).isTrue();
        }
    }

    private void checkUsersInGroup(final User group, final User... users) {
        final List<User> list = userService.findUsersInGroup(group.getUuid());
        assertThat(list.size()).isEqualTo(users.length);
        for (final User user : users) {
            assertThat(list.contains(user)).isTrue();
        }
    }

    @Test
    void testFindUsers() {
        final User user1 = createUser("findUser1");
        final User user2 = createUser("findUser2");
        final User userGroup1 = createUserGroup("findGroup1");
        final User userGroup2 = createUserGroup("findGroup2");

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

    private User createUser(final String baseName) {
        User userRef = userService.createUser(String.format("%s_%s", baseName, UUID.randomUUID()));
        assertThat(userRef).isNotNull();
        final Optional<User> user = userService.loadByUuid(userRef.getUuid());
        assertThat(user).isPresent();
        return user.get();
    }

    private User createUserGroup(final String baseName) {
        User userRef = userService.createUserGroup(String.format("%s_%s", baseName, UUID.randomUUID()));
        assertThat(userRef).isNotNull();
        final Optional<User> user = userService.loadByUuid(userRef.getUuid());
        assertThat(user).isPresent();
        return user.get();
    }
}
