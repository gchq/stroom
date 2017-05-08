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

package stroom.security.server;

import org.junit.Assert;
import org.junit.Test;
import stroom.AbstractCoreIntegrationTest;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.security.shared.UserRef;
import stroom.security.shared.UserService;
import stroom.util.test.FileSystemTestUtil;

import javax.annotation.Resource;
import java.util.List;

public class TestUserServiceImpl extends AbstractCoreIntegrationTest {
    @Resource
    private UserService userService;

    @Test
    public void testSaveAndGetBasic() {
        createUser(FileSystemTestUtil.getUniqueTestString());
    }

    @Test
    public void testSaveAndGetUserGroups() {
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
        Assert.assertEquals(groups.length, list.size());
        for (final UserRef group : groups) {
            Assert.assertTrue(list.contains(group));
        }
    }

    private void checkUsersInGroup(final UserRef group, final UserRef... users) {
        final List<UserRef> list = userService.findUsersInGroup(group);
        Assert.assertEquals(users.length, list.size());
        for (final UserRef user : users) {
            Assert.assertTrue(list.contains(user));
        }
    }

    @Test
    public void testFindUsers() {
        final UserRef user1 = createUser(FileSystemTestUtil.getUniqueTestString());
        final UserRef user2 = createUser(FileSystemTestUtil.getUniqueTestString());
        final UserRef userGroup1 = createUserGroup(FileSystemTestUtil.getUniqueTestString());
        final UserRef userGroup2 = createUserGroup(FileSystemTestUtil.getUniqueTestString());

        Assert.assertEquals(1, userService.find(new FindUserCriteria(user1.getName(), false)).size());
        Assert.assertEquals(1, userService.find(new FindUserCriteria(user2.getName(), false)).size());
        Assert.assertEquals(1, userService.find(new FindUserCriteria(userGroup1.getName(), true)).size());
        Assert.assertEquals(1, userService.find(new FindUserCriteria(userGroup2.getName(), true)).size());

        Assert.assertEquals(0, userService.find(new FindUserCriteria(user1.getName(), true)).size());
        Assert.assertEquals(0, userService.find(new FindUserCriteria(user2.getName(), true)).size());
        Assert.assertEquals(0, userService.find(new FindUserCriteria(userGroup1.getName(), false)).size());
        Assert.assertEquals(0, userService.find(new FindUserCriteria(userGroup2.getName(), false)).size());

        Assert.assertEquals(2, userService.find(new FindUserCriteria(false)).size());
        Assert.assertEquals(2, userService.find(new FindUserCriteria(true)).size());
    }

    private UserRef createUser(final String name) {
        User user = userService.createUser(name);
        Assert.assertNotNull(user);
        user = userService.load(user);
        Assert.assertNotNull(user);
        return UserRef.create(user);
    }

    private UserRef createUserGroup(final String name) {
        User user = userService.createUserGroup(name);
        Assert.assertNotNull(user);
        user = userService.load(user);
        Assert.assertNotNull(user);
        return UserRef.create(user);
    }
}
