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
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import stroom.entity.shared.MockitoEntityServiceUtil;
import stroom.node.server.MockStroomPropertyService;
import stroom.security.shared.UserStatus;
import stroom.util.date.DateUtil;
import stroom.util.test.FileSystemTestUtil;

@RunWith(MockitoJUnitRunner.class)
public class TestUserManagerImpl {
    private final MockStroomPropertyService mockProperty = new MockStroomPropertyService();

    @Mock
    private UserService userService;

    private UserManagerImpl getUserManager() {
        return new UserManagerImpl(userService, mockProperty);
    }

    @Test
    public void testSimple() {
        getUserManager();
    }

    @Test
    public void testLockOldAccounts() {
        MockitoEntityServiceUtil.apply(userService);
        MockitoEntityServiceUtil.applyEmptyFind(userService);

        final UserManagerImpl userManager = getUserManager();

        mockProperty.setProperty(UserManagerImpl.DAYS_TO_UNUSED_ACCOUNT_EXPIRY, "0");
        mockProperty.setProperty(UserManagerImpl.DAYS_TO_ACCOUNT_EXPIRY, "0");

        User user = new User();
        user.setName(FileSystemTestUtil.getUniqueTestString());
        user.updateStatus(UserStatus.ENABLED);
        user.updateValidLogin(0L);

        MockitoEntityServiceUtil.applySingleFind(userService, user);

        user = userService.save(user);

        userManager.disableUnusedAccounts();

        Assert.assertEquals(UserStatus.DISABLED, userService.load(user).getStatus());
    }

    @Test
    public void testLockOldUnusedAccounts() {
        MockitoEntityServiceUtil.apply(userService);
        MockitoEntityServiceUtil.applyEmptyFind(userService);

        final UserManagerImpl userManager = getUserManager();

        mockProperty.setProperty(UserManagerImpl.DAYS_TO_UNUSED_ACCOUNT_EXPIRY, "0");
        mockProperty.setProperty(UserManagerImpl.DAYS_TO_ACCOUNT_EXPIRY, "0");

        User user = new User();
        user.setName(FileSystemTestUtil.getUniqueTestString());
        user.updateStatus(UserStatus.ENABLED);

        user = userService.save(user);

        userManager.disableUnusedAccounts();
        Assert.assertEquals(UserStatus.ENABLED, userService.load(user).getStatus());

        user.updateValidLogin(DateUtil.parseNormalDateTimeString("2000-01-01T00:00:00.000Z"));
        user = userService.save(user);

        MockitoEntityServiceUtil.applySingleFind(userService, user);

        userManager.disableUnusedAccounts();
        Assert.assertEquals(UserStatus.DISABLED, userService.load(user).getStatus());

    }

    @Test
    public void testDaysToUnusedAccountExpiry1() {
        MockitoEntityServiceUtil.apply(userService);
        MockitoEntityServiceUtil.applyEmptyFind(userService);

        final UserManagerImpl userManager = getUserManager();

        User user = new User();
        user.setName(FileSystemTestUtil.getUniqueTestString());
        user.updateStatus(UserStatus.ENABLED);
        // Set a valid login of now
        user.setLoginValidMs(System.currentTimeMillis());

        user = userService.save(user);

        mockProperty.setProperty(UserManagerImpl.DAYS_TO_UNUSED_ACCOUNT_EXPIRY, "1");
        mockProperty.setProperty(UserManagerImpl.DAYS_TO_ACCOUNT_EXPIRY, "1");

        userManager.disableUnusedAccounts();
        user = userService.load(user);
        Assert.assertTrue(UserStatus.ENABLED.equals(user.getStatus()));

        mockProperty.setProperty(UserManagerImpl.DAYS_TO_UNUSED_ACCOUNT_EXPIRY, "0");
        mockProperty.setProperty(UserManagerImpl.DAYS_TO_ACCOUNT_EXPIRY, "1");

        MockitoEntityServiceUtil.applySingleFind(userService, user);

        userManager.disableUnusedAccounts();
        user = userService.load(user);
        Assert.assertTrue(UserStatus.DISABLED.equals(user.getStatus()));
    }

    @Test
    public void testDaysToUnusedAccountExpiry2() {
        MockitoEntityServiceUtil.apply(userService);
        MockitoEntityServiceUtil.applyEmptyFind(userService);

        final UserManagerImpl userManager = getUserManager();

        // Create a user that has logged in once
        User user = new User();
        user.setName(FileSystemTestUtil.getUniqueTestString());
        user.updateStatus(UserStatus.ENABLED);
        user.updateValidLogin(System.currentTimeMillis());

        user = userService.save(user);

        mockProperty.setProperty(UserManagerImpl.DAYS_TO_UNUSED_ACCOUNT_EXPIRY, "1");
        mockProperty.setProperty(UserManagerImpl.DAYS_TO_ACCOUNT_EXPIRY, "1");

        userManager.disableUnusedAccounts();
        user = userService.load(user);
        Assert.assertTrue(UserStatus.ENABLED.equals(user.getStatus()));

        mockProperty.setProperty(UserManagerImpl.DAYS_TO_UNUSED_ACCOUNT_EXPIRY, "0");
        mockProperty.setProperty(UserManagerImpl.DAYS_TO_ACCOUNT_EXPIRY, "1");

        // User should not get disabled as they have logged in
        userManager.disableUnusedAccounts();
        user = userService.load(user);
        Assert.assertTrue(UserStatus.ENABLED.equals(user.getStatus()));
    }

    @Test
    public void testDaysToAccountExpiry1() {
        MockitoEntityServiceUtil.apply(userService);
        MockitoEntityServiceUtil.applyEmptyFind(userService);

        final UserManagerImpl userManager = getUserManager();

        final long timeNowMs = System.currentTimeMillis();
        final long ninetyDaysAgoMs = timeNowMs - (UserManagerImpl.MS_IN_DAY * 90);

        User user = new User();
        user.setName(FileSystemTestUtil.getUniqueTestString());
        user.updateStatus(UserStatus.ENABLED);
        user.updateValidLogin(ninetyDaysAgoMs);

        user = userService.save(user);

        mockProperty.setProperty(UserManagerImpl.DAYS_TO_UNUSED_ACCOUNT_EXPIRY, "0");
        mockProperty.setProperty(UserManagerImpl.DAYS_TO_ACCOUNT_EXPIRY, "91");

        userManager.disableUnusedAccounts();
        user = userService.load(user);
        Assert.assertTrue(UserStatus.ENABLED.equals(user.getStatus()));

        mockProperty.setProperty(UserManagerImpl.DAYS_TO_UNUSED_ACCOUNT_EXPIRY, "0");
        mockProperty.setProperty(UserManagerImpl.DAYS_TO_ACCOUNT_EXPIRY, "90");

        MockitoEntityServiceUtil.applySingleFind(userService, user);

        userManager.disableUnusedAccounts();
        user = userService.load(user);
        Assert.assertTrue(UserStatus.DISABLED.equals(user.getStatus()));

        MockitoEntityServiceUtil.applyEmptyFind(userService);

        // Re-enable them
        user.updateStatus(UserStatus.ENABLED);
        user = userService.save(user);

        userManager.disableUnusedAccounts();
        user = userService.load(user);
        Assert.assertTrue(UserStatus.ENABLED.equals(user.getStatus()));
    }
}
