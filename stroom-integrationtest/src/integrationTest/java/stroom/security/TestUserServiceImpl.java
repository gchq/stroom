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

package stroom.security;


import org.junit.jupiter.api.Test;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.UserRef;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.test.FileSystemTestUtil;

import javax.inject.Inject;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestUserServiceImpl extends AbstractCoreIntegrationTest {
    @Inject
    private UserService userService;

    @Test
    void testSaveAndGetBasic() {
        createUser(FileSystemTestUtil.getUniqueTestString());
    }

    @Test
    void testSaveAndGetUserGroups() {
        final UserRef user1 = createUser(FileSystemTestUtil.getUniqueTestString());
        final UserRef user2 = createUser(FileSystemTestUtil.getUniqueTestString());
        final UserRef userGroup1 = createUserGroup(FileSystemTestUtil.getUniqueTestString());
        final UserRef userGroup2 = createUserGroup(FileSystemTestUtil.getUniqueTestString());

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
        final UserRef user1 = createUser(FileSystemTestUtil.getUniqueTestString());
        final UserRef user2 = createUser(FileSystemTestUtil.getUniqueTestString());
        final UserRef userGroup1 = createUserGroup(FileSystemTestUtil.getUniqueTestString());
        final UserRef userGroup2 = createUserGroup(FileSystemTestUtil.getUniqueTestString());

        assertThat(userService.find(new FindUserCriteria(user1.getName(), false)).size()).isEqualTo(1);
        assertThat(userService.find(new FindUserCriteria(user2.getName(), false)).size()).isEqualTo(1);
        assertThat(userService.find(new FindUserCriteria(userGroup1.getName(), true)).size()).isEqualTo(1);
        assertThat(userService.find(new FindUserCriteria(userGroup2.getName(), true)).size()).isEqualTo(1);

        assertThat(userService.find(new FindUserCriteria(user1.getName(), true)).size()).isEqualTo(0);
        assertThat(userService.find(new FindUserCriteria(user2.getName(), true)).size()).isEqualTo(0);
        assertThat(userService.find(new FindUserCriteria(userGroup1.getName(), false)).size()).isEqualTo(0);
        assertThat(userService.find(new FindUserCriteria(userGroup2.getName(), false)).size()).isEqualTo(0);

        assertThat(userService.find(new FindUserCriteria(false)).size()).isEqualTo(2);
        assertThat(userService.find(new FindUserCriteria(true)).size()).isEqualTo(2);
    }

    private UserRef createUser(final String name) {
        UserRef userRef = userService.createUser(name);
        assertThat(userRef).isNotNull();
        final User user = userService.loadByUuid(userRef.getUuid());
        assertThat(user).isNotNull();
        return UserRefFactory.create(user);
    }

    private UserRef createUserGroup(final String name) {
        UserRef userRef = userService.createUserGroup(name);
        assertThat(userRef).isNotNull();
        final User user = userService.loadByUuid(userRef.getUuid());
        assertThat(user).isNotNull();
        return UserRefFactory.create(user);
    }
}
