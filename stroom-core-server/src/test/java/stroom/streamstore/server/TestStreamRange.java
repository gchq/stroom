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

package stroom.streamstore.server;

import stroom.util.test.StroomUnitTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import stroom.util.date.DateUtil;
import stroom.util.test.StroomJUnit4ClassRunner;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestStreamRange extends StroomUnitTest {
    @Test
    public void testParseNothing() {
        final StreamRange streamRange = new StreamRange("");
        Assert.assertFalse(streamRange.isFileLocation());
    }

    @Test
    public void testParseEvents() {
        final StreamRange streamRange = new StreamRange("EVENTS");
        Assert.assertFalse(streamRange.isFileLocation());
    }

    @Test
    public void testParseShortDate() {
        final StreamRange streamRange = new StreamRange("EVENTS/2011/01");
        Assert.assertFalse(streamRange.isFileLocation());
    }

    @Test
    public void testParseSmallRange() {
        final StreamRange streamRange = new StreamRange("EVENTS/2011/01/01");
        Assert.assertTrue(streamRange.isFileLocation());
        Assert.assertFalse(streamRange.isInvalidPath());
        Assert.assertEquals(1L, streamRange.getFrom().longValue());
        Assert.assertEquals(1000L, streamRange.getTo().longValue());
        Assert.assertTrue(streamRange.getCreatePeriod().getFrom()
                .equals(DateUtil.parseNormalDateTimeString("2011-01-01T00:00:00.000Z")));
        Assert.assertTrue(streamRange.getCreatePeriod().getTo()
                .equals(DateUtil.parseNormalDateTimeString("2011-01-02T00:00:00.000Z")));
    }

    @Test
    public void testParseBigRange() {
        final StreamRange streamRange = new StreamRange("EVENTS/2011/01/01/101/500");
        Assert.assertTrue(streamRange.isFileLocation());
        Assert.assertFalse(streamRange.isInvalidPath());
        Assert.assertEquals(101500000L, streamRange.getFrom().longValue());
        Assert.assertEquals(101501000L, streamRange.getTo().longValue());
        Assert.assertTrue(streamRange.getCreatePeriod().getFrom()
                .equals(DateUtil.parseNormalDateTimeString("2011-01-01T00:00:00.000Z")));
        Assert.assertTrue(streamRange.getCreatePeriod().getTo()
                .equals(DateUtil.parseNormalDateTimeString("2011-01-02T00:00:00.000Z")));
    }

    @Test
    public void testParseBigRangeOddFolder() {
        final StreamRange streamRange1 = new StreamRange("EVENTS/2011/01/01/101/500/X");
        Assert.assertTrue(streamRange1.isInvalidPath());
        final StreamRange streamRange2 = new StreamRange("EVENTS/X/X/X");
        Assert.assertTrue(streamRange2.isInvalidPath());
    }
}
