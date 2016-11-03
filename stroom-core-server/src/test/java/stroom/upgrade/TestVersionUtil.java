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

package stroom.upgrade;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import stroom.util.test.StroomUnitTest;
import stroom.util.test.StroomJUnit4ClassRunner;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestVersionUtil extends StroomUnitTest {
    @Test
    public void testParse() {
        Assert.assertEquals(1, VersionUtil.parseVersion("1.2.3").getMajor().intValue());
        Assert.assertEquals(2, VersionUtil.parseVersion("1.2.3").getMinor().intValue());
        Assert.assertEquals(3, VersionUtil.parseVersion("1.2.3").getPatch().intValue());

        Assert.assertEquals(11, VersionUtil.parseVersion("STROOM_11_22_33").getMajor().intValue());
        Assert.assertEquals(2, VersionUtil.parseVersion("STROOM_1_2_3.B1").getMinor().intValue());
        Assert.assertEquals(3, VersionUtil.parseVersion("1.2.3.B1").getPatch().intValue());

        Assert.assertEquals(0, VersionUtil.parseVersion("1").getPatch().intValue());
        Assert.assertEquals(0, VersionUtil.parseVersion("1").getMinor().intValue());

        Assert.assertEquals(3, VersionUtil.parseVersion("3.0.0.b4").getMajor().intValue());
        Assert.assertEquals(0, VersionUtil.parseVersion("3.0.0.b4").getMinor().intValue());
        Assert.assertEquals(0, VersionUtil.parseVersion("3.0.0.b4").getPatch().intValue());

    }

}
