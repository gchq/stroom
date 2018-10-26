/*
 * Copyright 2018 Crown Copyright
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

package stroom.pipeline.server.xsltfunctions;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.pipeline.state.StreamHolder;
import stroom.streamstore.shared.Stream;
import stroom.util.date.DateUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

import java.time.Instant;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestFormatDate extends StroomUnitTest {
    @Test
    public void testDayOfWeekAndWeekAndWeakYear() {
        final FormatDate formatDate = createFormatDate("2010-03-01T12:45:22.643Z");
        Assert.assertEquals("2018-01-01T00:00:00.000Z", test(formatDate, "ccc/w/YYYY", "Mon/1/2018"));
        Assert.assertEquals("2018-01-01T00:00:00.000Z", test(formatDate, "E/w/YYYY", "Mon/1/2018"));
    }

    @Test
    public void testDayOfWeekAndWeek() {
        final FormatDate formatDate = createFormatDate("2010-03-04T12:45:22.643Z");
        Assert.assertEquals("2010-01-08T00:00:00.000Z", test(formatDate, "ccc/w", "Fri/2"));
        Assert.assertEquals("2010-01-08T00:00:00.000Z", test(formatDate, "E/w", "Fri/2"));
        Assert.assertEquals("2009-10-02T00:00:00.000Z", test(formatDate, "ccc/w", "Fri/40"));
        Assert.assertEquals("2009-10-02T00:00:00.000Z", test(formatDate, "E/w", "Fri/40"));
    }

    @Test
    public void testDayOfWeek() {
        final FormatDate formatDate = createFormatDate("2010-03-04T12:45:22.643Z");
        Assert.assertEquals("2010-03-01T00:00:00.000Z", test(formatDate, "ccc", "Mon"));
        Assert.assertEquals("2010-03-01T00:00:00.000Z", test(formatDate, "E", "Mon"));
        Assert.assertEquals("2010-02-26T00:00:00.000Z", test(formatDate, "ccc", "Fri"));
        Assert.assertEquals("2010-02-26T00:00:00.000Z", test(formatDate, "E", "Fri"));
    }

    @Test
    public void testDateWithNoYear() {
        final Stream stream = new Stream();
        stream.setCreateMs(DateUtil.parseNormalDateTimeString("2010-03-01T12:45:22.643Z"));

        final StreamHolder streamHolder = new StreamHolder();
        streamHolder.setStream(stream);

        final FormatDate formatDate = new FormatDate(streamHolder);

        Assert.assertEquals("2010-01-01T00:00:00.000Z", test(formatDate, "dd/MM", "01/01"));
        Assert.assertEquals("2009-04-01T00:00:00.000Z", test(formatDate, "dd/MM", "01/04"));
        Assert.assertEquals("2010-01-01T00:00:00.000Z", test(formatDate, "MM", "01"));
        Assert.assertEquals("2009-04-01T00:00:00.000Z", test(formatDate, "MM", "04"));
        Assert.assertEquals("2010-03-01T00:00:00.000Z", test(formatDate, "dd", "01"));
        Assert.assertEquals("2010-02-04T00:00:00.000Z", test(formatDate, "dd", "04"));
        Assert.assertEquals("2010-03-01T12:00:00.000Z", test(formatDate, "HH", "12"));
        Assert.assertEquals("2010-03-01T12:30:00.000Z", test(formatDate, "HH:mm", "12:30"));
    }

    @Test
    public void testCaseSensitivity_upperCaseMonth() {
        ZonedDateTime time = parseUtcDate("dd-MMM-yy", "18-APR-18");
        Assert.assertEquals(Month.APRIL, time.getMonth());
    }

    @Test
    public void testCaseSensitivity_sentenceCaseMonth() {
        ZonedDateTime time = parseUtcDate("dd-MMM-yy", "18-Apr-18");
        Assert.assertEquals(Month.APRIL, time.getMonth());
    }

    @Test
    public void testCaseSensitivity_lowerCaseMonth() {
        ZonedDateTime time = parseUtcDate("dd-MMM-yy", "18-apr-18");
        Assert.assertEquals(Month.APRIL, time.getMonth());
    }

    @Test
    public void testWithTimeZoneInStr1() {
        ZonedDateTime time = parseUtcDate("dd-MM-yy HH:mm:ss xxx", "18-04-18 01:01:01 +00:00");
    }

    @Test
    public void testWithTimeZoneInStr2() {
        ZonedDateTime time = parseUtcDate("dd-MM-yy HH:mm:ss Z", "18-04-18 01:01:01 +0000");
    }

    @Test
    public void testWithTimeZoneInStr3() {
        ZonedDateTime time = parseUtcDate("dd-MM-yy HH:mm:ss Z", "18-04-18 01:01:01 -0000");
    }

    @Test
    public void testWithTimeZoneInStr4() {
        ZonedDateTime time = parseUtcDate("dd-MM-yy HH:mm:ss xxx", "18-04-18 01:01:01 -00:00");
    }

    @Test
    public void testWithTimeZoneInStr5() {
        ZonedDateTime time = parseUtcDate("dd-MM-yy HH:mm:ss VV", "18-04-18 01:01:01 Europe/London");
    }

    private ZonedDateTime parseUtcDate(final String pattern, final String dateStr) {
        final FormatDate formatDate = createFormatDate("2010-03-01T12:45:22.643Z");

        long timeMs = formatDate.parseDate(null, "UTC", pattern, dateStr);
        ZonedDateTime time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeMs), ZoneOffset.UTC);
        return time;
    }

    private String test(final FormatDate formatDate, final String pattern, final String date) {
        return DateUtil.createNormalDateTimeString(formatDate.parseDate(null, "UTC", pattern, date));
    }

    private FormatDate createFormatDate(final String referenceDate) {
        final Stream stream = new Stream();
        stream.setCreateMs(DateUtil.parseNormalDateTimeString("2010-03-01T12:45:22.643Z"));

        final StreamHolder streamHolder = new StreamHolder();
        streamHolder.setStream(stream);

        return new FormatDate(streamHolder);
    }
}
