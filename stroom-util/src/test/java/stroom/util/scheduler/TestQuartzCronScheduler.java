/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.util.scheduler;

import stroom.util.date.DateUtil;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class TestQuartzCronScheduler {

    private Long currentTime = null;

    @Test
    void testMinRollAny1() {
        textNext("* * *", "2010-01-01T04:00:00.000Z", "2010-01-01T04:01:00.000Z");
        textNext("* * *", "2010-01-01T04:04:20.000Z", "2010-01-01T04:05:00.000Z");
        textNext("* * *", "2010-01-01T04:59:20.000Z", "2010-01-01T05:00:00.000Z");
        textNext("* * *", "2010-01-01T23:59:59.000Z", "2010-01-02T00:00:00.000Z");
    }

    @Test
    void testMinRollExact1() {
        textNext("1,2,3,4,55 * *", "2010-01-01T04:00:00.000Z", "2010-01-01T04:01:00.000Z");
        textNext("1,2,3,4,55 * *", "2010-01-01T04:01:00.000Z", "2010-01-01T04:02:00.000Z");
        textNext("1,2,3,4,55 * *", "2010-01-01T04:02:00.000Z", "2010-01-01T04:03:00.000Z");
        textNext("1,2,3,4,55 * *", "2010-01-01T04:03:00.000Z", "2010-01-01T04:04:00.000Z");
        textNext("1,2,3,4,55 * *", "2010-01-01T04:04:00.000Z", "2010-01-01T04:55:00.000Z");
        textNext("1,2,3,4,55 * *", "2010-01-01T04:55:00.000Z", "2010-01-01T05:01:00.000Z");
        textNext("1,2,3,4,55 * *", "2010-01-01T05:01:00.000Z", "2010-01-01T05:02:00.000Z");

        textLast("1,2,3,4,55 * *", "2010-01-01T04:00:00.000Z", "2010-01-01T03:55:00.000Z");
        textLast("1,2,3,4,55 * *", "2010-01-01T03:55:00.000Z", "2010-01-01T03:04:00.000Z");
        textLast("1,2,3,4,55 * *", "2010-01-01T03:04:00.000Z", "2010-01-01T03:03:00.000Z");
        textLast("1,2,3,4,55 * *", "2010-01-01T03:03:00.000Z", "2010-01-01T03:02:00.000Z");
        textLast("1,2,3,4,55 * *", "2010-01-01T03:02:00.000Z", "2010-01-01T03:01:00.000Z");
        textLast("1,2,3,4,55 * *", "2010-01-01T03:01:00.000Z", "2010-01-01T02:55:00.000Z");
        textLast("1,2,3,4,55 * *", "2010-01-01T02:55:00.000Z", "2010-01-01T02:04:00.000Z");
    }

    @Test
    void testHourRollAny1() {
        textNext("0 * *", "2010-01-01T04:00:00.000Z", "2010-01-01T05:00:00.000Z");
        textNext("0 * *", "2010-01-01T04:30:00.000Z", "2010-01-01T05:00:00.000Z");
        textNext("0 * *", "2010-01-01T04:59:59.000Z", "2010-01-01T05:00:00.000Z");
    }

    @Test
    void testHourRollAny2() {
        textNext("3,57 * *", "2010-01-01T04:00:00.000Z", "2010-01-01T04:03:00.000Z");
        textNext("3,57 * *", "2010-01-01T04:03:00.000Z", "2010-01-01T04:57:00.000Z");
        textNext("3,57 * *", "2010-01-01T04:57:00.000Z", "2010-01-01T05:03:00.000Z");
    }

    @Test
    void testHourEveryMinOnHours1Forward() {
        textNext("* 1,2 *", "2010-01-01T00:00:00.000Z", "2010-01-01T01:00:00.000Z");
        textNext("* 1,2 *", "2010-01-01T01:00:00.000Z", "2010-01-01T01:01:00.000Z");
        textNext("* 1,2 *", "2010-01-01T02:00:00.000Z", "2010-01-01T02:01:00.000Z");
        textNext("* 1,2 *", "2010-01-01T02:58:00.000Z", "2010-01-01T02:59:00.000Z");
        textNext("* 1,2 *", "2010-01-01T02:59:00.000Z", "2010-01-02T01:00:00.000Z");
    }

    @Test
    void testHourEveryMinOnHours1Backward() {
        textLast("* 1,2 *", "2010-01-01T00:00:00.000Z", "2009-12-31T02:59:00.000Z");
        textLast("* 1,2 *", "2010-01-01T01:00:00.000Z", "2009-12-31T02:59:00.000Z");
        textLast("* 1,2 *", "2010-01-01T02:00:00.000Z", "2010-01-01T01:59:00.000Z");
        textLast("* 1,2 *", "2010-01-01T02:58:00.000Z", "2010-01-01T02:57:00.000Z");
        textLast("* 1,2 *", "2010-01-01T02:59:00.000Z", "2010-01-01T02:58:00.000Z");
    }

    @Test
    void testWrapBoundaries() {
        textNext("0 0 1,29", "2010-01-01T00:00:00.000Z", "2010-01-29T00:00:00.000Z");
        textLast("0 0 1,29", "2010-01-01T00:00:00.000Z", "2009-12-29T00:00:00.000Z");
        textNext("0 0 1,29", "2009-12-29T00:00:00.000Z", "2010-01-01T00:00:00.000Z");
        textLast("0 0 1,29", "2009-12-29T00:00:00.000Z", "2009-12-01T00:00:00.000Z");

        textNext("* * 1,29", "2010-01-01T00:00:00.000Z", "2010-01-01T00:01:00.000Z");
        textLast("* * 1,29", "2010-01-01T00:00:00.000Z", "2009-12-29T23:59:00.000Z");
        textNext("* * 1,29", "2009-12-29T00:00:00.000Z", "2009-12-29T00:01:00.000Z");
        textNext("* * 1,29", "2009-12-29T23:59:00.000Z", "2010-01-01T00:00:00.000Z");
        textLast("* * 1,29", "2009-12-29T00:00:00.000Z", "2009-12-01T23:59:00.000Z");
    }

    @Test
    void testACoupleOfDaysPerMonthTwiceInADay1() {
        String startTime = "2010-01-02T00:00:00.000Z";
        startTime = textNext("0 1,2 14,15", startTime, "2010-01-14T01:00:00.000Z");
        startTime = textNext("0 1,2 14,15", startTime, "2010-01-14T02:00:00.000Z");
        startTime = textNext("0 1,2 14,15", startTime, "2010-01-15T01:00:00.000Z");
        startTime = textNext("0 1,2 14,15", startTime, "2010-01-15T02:00:00.000Z");
        textNext("0 1,2 14,15", startTime, "2010-02-14T01:00:00.000Z");
    }

    @Test
    void testACoupleOfDaysPerMonthTwiceInADay2() {
        textNext("0 1,2 14,15", "2010-01-15T02:00:00.000Z", "2010-02-14T01:00:00.000Z");
    }

    @Test
    void testAEveryDayPerMonth1() {
        String startTime = "2010-01-02T00:00:00.000Z";
        startTime = textNext("0 0 *", startTime, "2010-01-03T00:00:00.000Z");
        startTime = textNext("0 0 *", startTime, "2010-01-04T00:00:00.000Z");
        startTime = textNext("0 0 *", startTime, "2010-01-05T00:00:00.000Z");
        startTime = textNext("0 0 *", "2010-01-30T00:00:00.000Z", "2010-01-31T00:00:00.000Z");
        textNext("0 0 *", startTime, "2010-02-01T00:00:00.000Z");
    }

    @Test
    void testOverBSTPeriod1() {
        String startTime = "2010-03-26T00:00:00.000Z";
        startTime = textNext("0 0 *", startTime, "2010-03-27T00:00:00.000Z");
        startTime = textNext("0 0 *", startTime, "2010-03-28T00:00:00.000Z");
        textNext("0 0 *", startTime, "2010-03-29T00:00:00.000Z");
    }

    @Test
    public void testOnceEveryTenMinutes() {
        String startTime = "2010-03-26T00:00:00.000Z";
        startTime = textNext("*/10 * *", startTime, "2010-03-26T00:10:00.000Z");
        startTime = textNext("*/10 * *", startTime, "2010-03-26T00:20:00.000Z");
        startTime = textNext("*/10 * *", startTime, "2010-03-26T00:30:00.000Z");
        startTime = textNext("*/10 * *", startTime, "2010-03-26T00:40:00.000Z");
        startTime = textNext("*/10 * *", startTime, "2010-03-26T00:50:00.000Z");
        textNext("*/10 * *", startTime, "2010-03-26T01:00:00.000Z");
    }

    @Test
    public void testOnceEveryTenHours() {
        String startTime = "2010-03-26T00:00:00.000Z";
        startTime = textNext("0 */10 *", startTime, "2010-03-26T10:00:00.000Z");
        startTime = textNext("0 */10 *", startTime, "2010-03-26T20:00:00.000Z");
        startTime = textNext("0 */10 *", startTime, "2010-03-27T00:00:00.000Z");
        textNext("0 */10 *", startTime, "2010-03-27T10:00:00.000Z");
    }

    @Test
    public void testEveryTwoDays() {
        String startTime = "2010-03-26T00:00:00.000Z";
        startTime = textNext("0 0 */2", startTime, "2010-03-27T00:00:00.000Z");
        startTime = textNext("0 0 */2", startTime, "2010-03-29T00:00:00.000Z");
        textNext("0 0 */2", startTime, "2010-03-31T00:00:00.000Z");
    }

    @Test
    public void testEveryHourAtNight() {
        String startTime = "2010-03-26T00:00:00.000Z";
        startTime = textNext("0 20-23 *", startTime, "2010-03-26T20:00:00.000Z");
        startTime = textNext("0 20-23 *", startTime, "2010-03-26T21:00:00.000Z");
        textNext("0 20-23 *", startTime, "2010-03-26T22:00:00.000Z");
    }

    public String textNext(final String expression, final String start, final String end) {
        try {
            final Trigger cron = new CronTrigger(expression);
            final Instant time = DateUtil.parseNormalDateTimeStringToInstant(start);

            final String timeString = DateUtil.createNormalDateTimeString(time);
            assertThat(timeString).isEqualTo(start);
            final Instant nextTime = cron.getNextExecutionTimeAfter(time);
            final String nextTimeString = DateUtil.createNormalDateTimeString(nextTime);
            assertThat(nextTimeString).isEqualTo(end);

        } catch (final RuntimeException e) {
            fail(e.getMessage());
        }
        return end;
    }

    public String textLast(final String expression, final String start, final String end) {
        try {
            final Trigger cron = new CronTrigger(expression);
            final Long time = DateUtil.parseNormalDateTimeString(start);

            assertThat(DateUtil.createNormalDateTimeString(time)).isEqualTo(start);

//            assertThat(DateUtil.createNormalDateTimeString(cron.getLastTime(time))).isEqualTo(end);

        } catch (final RuntimeException e) {
            fail(e.getMessage());
        }
        return end;
    }

    @Test
    void testUnderLoadTypical() {
        // Every Day
        final SimpleScheduleExec executor = new SimpleScheduleExec(new CronTrigger("0 0 *"));

        // Application starts at 2pm
        Instant currentTime = DateUtil.parseNormalDateTimeStringToInstant("2010-03-29T14:00:00.000Z");

        assertThat(executor.execute(currentTime)).isFalse();
        assertThat(executor.execute(currentTime)).isFalse();

        // Our 1 minute thread timer fires a bit later
        currentTime = DateUtil.parseNormalDateTimeStringToInstant("2010-03-29T14:01:10.000Z");
        assertThat(executor.execute(currentTime)).isFalse();
        // And Again
        currentTime = DateUtil.parseNormalDateTimeStringToInstant("2010-03-29T14:02:11.000Z");
        assertThat(executor.execute(currentTime)).isFalse();

        // And just before it rolls
        currentTime = DateUtil.parseNormalDateTimeStringToInstant("2010-03-29T23:59:59.999Z");
        assertThat(executor.execute(currentTime)).isFalse();

        // Now it should roll but we are under load to we don't fire under a lot
        // later
        currentTime = DateUtil.parseNormalDateTimeStringToInstant("2010-03-30T01:59:59.999Z");
        assertThat(executor.execute(currentTime)).isTrue();
        assertThat(executor.execute(currentTime)).isFalse();
    }

    @Test
    void testMove() {
        final Trigger cron = new CronTrigger("0,10,20,30,40,50 * *");

        assertThat(DateUtil.createNormalDateTimeString(
                cron.getNextExecutionTimeAfter(
                        DateUtil.parseNormalDateTimeStringToInstant(
                                "2010-01-01T08:00:00.000Z")))).isEqualTo("2010-01-01T08:10:00.000Z");

        assertThat(DateUtil.createNormalDateTimeString(
                cron.getNextExecutionTimeAfter(DateUtil
                        .parseNormalDateTimeStringToInstant(
                                "2010-01-01T08:05:00.000Z")))).isEqualTo("2010-01-01T08:10:00.000Z");

        assertThat(DateUtil.createNormalDateTimeString(
                cron.getNextExecutionTimeAfter(DateUtil
                        .parseNormalDateTimeStringToInstant(
                                "2010-01-01T08:09:00.000Z")))).isEqualTo("2010-01-01T08:10:00.000Z");
    }

    @Test
    void testNextAndLast() {
        final Instant time = Instant.ofEpochMilli(1331803020017L);
        assertThat(DateUtil.createNormalDateTimeString(time)).isEqualTo("2012-03-15T09:17:00.017Z");
        Trigger cron = new CronTrigger("* * *");
        Instant nextExecute = cron.getNextExecutionTimeAfter(time);
        assertThat(DateUtil.createNormalDateTimeString(nextExecute)).isEqualTo("2012-03-15T09:18:00.000Z");
//        long lastExecute = cron.getLastTime(time);
//        assertThat(DateUtil.createNormalDateTimeString(lastExecute)).isEqualTo("2012-03-15T09:17:00.000Z");

        cron = new CronTrigger("0,10,20,30,40,50 * *");
        nextExecute = cron.getNextExecutionTimeAfter(time);
        assertThat(DateUtil.createNormalDateTimeString(nextExecute)).isEqualTo("2012-03-15T09:20:00.000Z");
//        lastExecute = cron.getLastTime(time);
//        assertThat(DateUtil.createNormalDateTimeString(lastExecute)).isEqualTo("2012-03-15T09:10:00.000Z");

        cron = new CronTrigger("0 * *");
        nextExecute = cron.getNextExecutionTimeAfter(time);
        assertThat(DateUtil.createNormalDateTimeString(nextExecute)).isEqualTo("2012-03-15T10:00:00.000Z");
//        lastExecute = cron.getLastTime(time);
//        assertThat(DateUtil.createNormalDateTimeString(lastExecute)).isEqualTo("2012-03-15T09:00:00.000Z");

        cron = new CronTrigger("0 0 *");
        nextExecute = cron.getNextExecutionTimeAfter(time);
        assertThat(DateUtil.createNormalDateTimeString(nextExecute)).isEqualTo("2012-03-16T00:00:00.000Z");
//        lastExecute = cron.getLastTime(time);
//        assertThat(DateUtil.createNormalDateTimeString(lastExecute)).isEqualTo("2012-03-15T00:00:00.000Z");

        cron = new CronTrigger("0 0 1");
        nextExecute = cron.getNextExecutionTimeAfter(time);
        assertThat(DateUtil.createNormalDateTimeString(nextExecute)).isEqualTo("2012-04-01T00:00:00.000Z");
//        lastExecute = cron.getLastTime(time);
//        assertThat(DateUtil.createNormalDateTimeString(lastExecute)).isEqualTo("2012-03-01T00:00:00.000Z");
    }
}
