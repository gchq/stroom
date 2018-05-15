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

package stroom.util;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.util.date.DateUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestDateUtil extends StroomUnitTest {
    @Test
    public void testSimpleZuluTimes() {
        doTest("2008-11-18T09:47:50.548Z");
        doTest("2008-11-18T09:47:00.000Z");
        doTest("2008-11-18T13:47:00.000Z");
        doTest("2008-01-01T13:47:00.000Z");
        doTest("2008-08-01T13:47:00.000Z");
    }

    private void doTest(final String dateString) {
        final long date = DateUtil.parseNormalDateTimeString(dateString);

        // Convert Back to string
        Assert.assertEquals(dateString, DateUtil.createNormalDateTimeString(date));
    }

    @Test
    public void testSimple() {
        Assert.assertEquals("2010-01-01T23:59:59.000Z",
                DateUtil.createNormalDateTimeString(DateUtil.parseNormalDateTimeString("2010-01-01T23:59:59.000Z")));

    }

    @Test
    public void testSimpleFileFormat() {
        Assert.assertEquals("2010-01-01T23#59#59,000Z",
                DateUtil.createFileDateTimeString(DateUtil.parseNormalDateTimeString("2010-01-01T23:59:59.000Z")));
    }
}
