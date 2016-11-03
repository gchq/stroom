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

package stroom.security.testclient.presenter;

import stroom.util.test.StroomUnitTest;
import org.junit.Assert;
import org.junit.Test;

import stroom.security.client.presenter.PasswordUtil;

public class TestPasswordUtil extends StroomUnitTest {
    @Test
    public void testGood() {
        Assert.assertTrue(PasswordUtil.isOkPassword("Abcdefg1"));
    }

    @Test
    public void testBad() {
        Assert.assertFalse(PasswordUtil.isOkPassword("abcdefgh"));
        Assert.assertFalse(PasswordUtil.isOkPassword("ABCDEFGH"));
        Assert.assertFalse(PasswordUtil.isOkPassword("abcdefg1"));
        Assert.assertFalse(PasswordUtil.isOkPassword("ABCDEFG1"));
        Assert.assertFalse(PasswordUtil.isOkPassword("12345678"));
        Assert.assertFalse(PasswordUtil.isOkPassword("Abcdef1"));
    }
}
