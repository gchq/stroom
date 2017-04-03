/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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

    @Test
    public void testSimple() {
        testSimple("2015-02-03T01:22:33.056Z");
        testSimple("2016-01-01T00:00:00.000Z");
    }

    private void testSimple(final String time) {
        Assert.assertEquals(
                ZonedDateTime.parse(time),
                DateExpressionParser.parse(time, nowEpochMilli).get());
    }

    @Test
    public void testComplex1() {
        Assert.assertEquals(
                ZonedDateTime.parse("2017-02-03T01:22:33.056Z"),
                DateExpressionParser.parse("2015-02-03T01:22:33.056Z + 2y", nowEpochMilli).get());
    }

    @Test
    public void testComplex2() {
        Assert.assertEquals(
                ZonedDateTime.parse("2017-02-06T02:22:35.056Z"),
                DateExpressionParser.parse("2015-02-03T01:22:33.056Z + 2y+3d+1h+2s", nowEpochMilli).get());
    }

    @Test
    public void testComplex3() {
        Assert.assertEquals(
                ZonedDateTime.parse("2017-02-06T02:22:35.056Z"),
                DateExpressionParser.parse("2015-02-03T01:22:33.056Z + 2y3d1h2s", nowEpochMilli).get());
    }

    @Test
    public void testComplex4() {
        Assert.assertEquals(
                ZonedDateTime.parse("2017-01-31T00:22:31.056Z"),
                DateExpressionParser.parse("2015-02-03T01:22:33.056Z + 2y-3d1h2s", nowEpochMilli).get());
    }

    @Test
    public void testComplex5() {
        Assert.assertEquals(
                ZonedDateTime.parse("2017-01-31T00:22:35.056Z"),
                DateExpressionParser.parse("2015-02-03T01:22:33.056Z + 2y-3d1h+2s", nowEpochMilli).get());
    }

    @Test
    public void testComplex6() {
        Assert.assertEquals(
                ZonedDateTime.parse("2017-01-31T00:22:31.056Z"),
                DateExpressionParser.parse("2015-02-03T01:22:33.056Z + 2y-3d1h-2s", nowEpochMilli).get());
    }

    @Test
    public void testNow() {
        Assert.assertEquals(
                ZonedDateTime.parse("2015-02-03T01:22:33.056Z"),
                DateExpressionParser.parse("now()", nowEpochMilli).get());
    }

    @Test
    public void testSecond() {
        Assert.assertEquals(
                ZonedDateTime.parse("2015-02-03T01:22:33.000Z"),
                DateExpressionParser.parse("second()", nowEpochMilli).get());
    }

    @Test
    public void testMinute() {
        Assert.assertEquals(
                ZonedDateTime.parse("2015-02-03T01:22:00.000Z"),
                DateExpressionParser.parse("minute()", nowEpochMilli).get());
    }

    @Test
    public void testHour() {
        Assert.assertEquals(
                ZonedDateTime.parse("2015-02-03T01:00:00.000Z"),
                DateExpressionParser.parse("hour()", nowEpochMilli).get());
    }

    @Test
    public void testDay() {
        Assert.assertEquals(
                ZonedDateTime.parse("2015-02-03T00:00:00.000Z"),
                DateExpressionParser.parse("day()", nowEpochMilli).get());
    }

    @Test
    public void testWeek() {
        Assert.assertEquals(
                ZonedDateTime.parse("2015-02-02T00:00:00.000Z"),
                DateExpressionParser.parse("week()", nowEpochMilli).get());
    }

    @Test
    public void testMonth() {
        Assert.assertEquals(
                ZonedDateTime.parse("2015-02-01T00:00:00.000Z"),
                DateExpressionParser.parse("month()", nowEpochMilli).get());
    }

    @Test
    public void testYear() {
        Assert.assertEquals(
                ZonedDateTime.parse("2015-01-01T00:00:00.000Z"),
                DateExpressionParser.parse("year()", nowEpochMilli).get());
    }

    @Test
    public void testSecondPlus() {
        Assert.assertEquals(
                ZonedDateTime.parse("2015-02-07T01:22:33.000Z"),
                DateExpressionParser.parse("second()+4d", nowEpochMilli).get());
    }

    @Test
    public void testHourMinus() {
        Assert.assertEquals(
                ZonedDateTime.parse("2015-02-03T05:00:00.000Z"),
                DateExpressionParser.parse("hour()+5h-1h", nowEpochMilli).get());
    }

    @Test
    public void testWeekPlus() {
        Assert.assertEquals(
                ZonedDateTime.parse("2015-02-09T00:00:00.000Z"),
                DateExpressionParser.parse("week()+1w", nowEpochMilli).get());
    }
}
