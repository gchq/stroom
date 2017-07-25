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
import stroom.security.shared.UserStatus;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestUser extends StroomUnitTest {
    @Test
    public void testSimple() {
        final User user = new User();
        user.updateStatus(UserStatus.ENABLED);
        Assert.assertTrue(user.isStatusEnabled());
        user.updateStatus(UserStatus.LOCKED);
        Assert.assertFalse(user.isStatusEnabled());

        user.setStatusEnabled(false);
        Assert.assertEquals(UserStatus.LOCKED, user.getStatus());
        user.setStatusEnabled(true);
        Assert.assertEquals(UserStatus.ENABLED, user.getStatus());

        user.updateStatus(UserStatus.DISABLED);
        Assert.assertFalse(user.isStatusEnabled());
        user.setStatusEnabled(true);
        Assert.assertTrue(user.isStatusEnabled());
    }
}
