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

package stroom.query;

import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class TestDateExpressionParser {
    private final Instant instant = Instant.parse("2015-02-03T01:22:33.056Z");
    private final long nowEpochMilli = ZonedDateTime.ofInstant(instant, ZoneOffset.UTC).toInstant().toEpochMilli();
    private final DateExpressionParser dateExpressionParser = new DateExpressionParser();

    @Test
    public void testSimple() {
        testSimple("2015-02-03T01:22:33.056Z");
        testSimple("2016-01-01T00:00:00.000Z");
    }

    private void testSimple(final String time) {
        Assert.assertEquals(ZonedDateTime.parse(time), dateExpressionParser.parse(time, nowEpochMilli));
    }

    @Test
    public void testComplex1() {
        Assert.assertEquals(ZonedDateTime.parse("2017-02-03T01:22:33.056Z"), dateExpressionParser.parse("2015-02-03T01:22:33.056Z + 2y", nowEpochMilli));
    }

    @Test
    public void testComplex2() {
        Assert.assertEquals(ZonedDateTime.parse("2017-02-06T02:22:35.056Z"), dateExpressionParser.parse("2015-02-03T01:22:33.056Z + 2y+3d+1h+2s", nowEpochMilli));
    }

    @Test
    public void testComplex3() {
        Assert.assertEquals(ZonedDateTime.parse("2017-02-06T02:22:35.056Z"), dateExpressionParser.parse("2015-02-03T01:22:33.056Z + 2y3d1h2s", nowEpochMilli));
    }

    @Test
    public void testComplex4() {
        Assert.assertEquals(ZonedDateTime.parse("2017-01-31T00:22:31.056Z"), dateExpressionParser.parse("2015-02-03T01:22:33.056Z + 2y-3d1h2s", nowEpochMilli));
    }

    @Test
    public void testComplex5() {
        Assert.assertEquals(ZonedDateTime.parse("2017-01-31T00:22:35.056Z"), dateExpressionParser.parse("2015-02-03T01:22:33.056Z + 2y-3d1h+2s", nowEpochMilli));
    }

    @Test
    public void testComplex6() {
        Assert.assertEquals(ZonedDateTime.parse("2017-01-31T00:22:31.056Z"), dateExpressionParser.parse("2015-02-03T01:22:33.056Z + 2y-3d1h-2s", nowEpochMilli));
    }

    @Test
    public void testNow() {
        Assert.assertEquals(ZonedDateTime.parse("2015-02-03T01:22:33.056Z"), dateExpressionParser.parse("now()", nowEpochMilli));
    }

    @Test
    public void testSecond() {
        Assert.assertEquals(ZonedDateTime.parse("2015-02-03T01:22:33.000Z"), dateExpressionParser.parse("second()", nowEpochMilli));
    }

    @Test
    public void testMinute() {
        Assert.assertEquals(ZonedDateTime.parse("2015-02-03T01:22:00.000Z"), dateExpressionParser.parse("minute()", nowEpochMilli));
    }

    @Test
    public void testHour() {
        Assert.assertEquals(ZonedDateTime.parse("2015-02-03T01:00:00.000Z"), dateExpressionParser.parse("hour()", nowEpochMilli));
    }

    @Test
    public void testDay() {
        Assert.assertEquals(ZonedDateTime.parse("2015-02-03T00:00:00.000Z"), dateExpressionParser.parse("day()", nowEpochMilli));
    }

    @Test
    public void testWeek() {
        Assert.assertEquals(ZonedDateTime.parse("2015-02-02T00:00:00.000Z"), dateExpressionParser.parse("week()", nowEpochMilli));
    }

    @Test
    public void testMonth() {
        Assert.assertEquals(ZonedDateTime.parse("2015-02-01T00:00:00.000Z"), dateExpressionParser.parse("month()", nowEpochMilli));
    }

    @Test
    public void testYear() {
        Assert.assertEquals(ZonedDateTime.parse("2015-01-01T00:00:00.000Z"), dateExpressionParser.parse("year()", nowEpochMilli));
    }

    @Test
    public void testSecondPlus() {
        Assert.assertEquals(ZonedDateTime.parse("2015-02-07T01:22:33.000Z"), dateExpressionParser.parse("second()+4d", nowEpochMilli));
    }

    @Test
    public void testHourMinus() {
        Assert.assertEquals(ZonedDateTime.parse("2015-02-03T05:00:00.000Z"), dateExpressionParser.parse("hour()+5h-1h", nowEpochMilli));
    }

    @Test
    public void testWeekPlus() {
        Assert.assertEquals(ZonedDateTime.parse("2015-02-09T00:00:00.000Z"), dateExpressionParser.parse("week()+1w", nowEpochMilli));
    }
}
