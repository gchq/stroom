/*
 * Copyright 2020 Crown Copyright
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

package stroom.query.common.v2;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.token.AbstractToken;
import stroom.query.language.token.StructureBuilder;
import stroom.query.language.token.Tokeniser;

import org.junit.jupiter.api.Test;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDateExpressionParser {

    private final Instant instant = Instant.parse("2015-02-03T01:22:33.056Z");
    private final long nowEpochMilli = ZonedDateTime.ofInstant(instant, ZoneOffset.UTC).toInstant().toEpochMilli();

    @Test
    void testSimple() {
        testSimple("2015-02-03T01:22:33.056Z");
        testSimple("2016-01-01T00:00:00.000Z");
    }

    private void testSimple(final String time) {
        assertThat(DateExpressionParser.parse(time, nowEpochMilli).get()).isEqualTo(ZonedDateTime.parse(time));
    }

    @Test
    void testComplex1() {
        assertThat(DateExpressionParser.parse("2015-02-03T01:22:33.056Z + 2y", nowEpochMilli).get())
                .isEqualTo(ZonedDateTime.parse("2017-02-03T01:22:33.056Z"));
    }

    @Test
    void testComplex2() {
        assertThat(DateExpressionParser.parse("2015-02-03T01:22:33.056Z + 2y+3d+1h+2s", nowEpochMilli).get())
                .isEqualTo(ZonedDateTime.parse("2017-02-06T02:22:35.056Z"));
    }

    @Test
    void testComplex3() {
        assertThat(DateExpressionParser.parse("2015-02-03T01:22:33.056Z + 2y3d1h2s", nowEpochMilli).get())
                .isEqualTo(ZonedDateTime.parse("2017-02-06T02:22:35.056Z"));
    }

    @Test
    void testComplex4() {
        assertThat(DateExpressionParser.parse("2015-02-03T01:22:33.056Z + 2y-3d1h2s", nowEpochMilli).get())
                .isEqualTo(ZonedDateTime.parse("2017-01-31T00:22:31.056Z"));
    }

    @Test
    void testComplex5() {
        assertThat(DateExpressionParser.parse("2015-02-03T01:22:33.056Z + 2y-3d1h+2s", nowEpochMilli).get())
                .isEqualTo(ZonedDateTime.parse("2017-01-31T00:22:35.056Z"));
    }

    @Test
    void testComplex6() {
        assertThat(DateExpressionParser.parse("2015-02-03T01:22:33.056Z + 2y-3d1h-2s", nowEpochMilli).get())
                .isEqualTo(ZonedDateTime.parse("2017-01-31T00:22:31.056Z"));
    }

    @Test
    void testNow() {
        assertThat(DateExpressionParser.parse("now()", nowEpochMilli).get())
                .isEqualTo(ZonedDateTime.parse("2015-02-03T01:22:33.056Z"));
    }

    @Test
    void testSecond() {
        assertThat(DateExpressionParser.parse("second()", nowEpochMilli).get())
                .isEqualTo(ZonedDateTime.parse("2015-02-03T01:22:33.000Z"));
    }

    @Test
    void testMinute() {
        assertThat(DateExpressionParser.parse("minute()", nowEpochMilli).get())
                .isEqualTo(ZonedDateTime.parse("2015-02-03T01:22:00.000Z"));
    }

    @Test
    void testHour() {
        assertThat(DateExpressionParser.parse("hour()", nowEpochMilli).get())
                .isEqualTo(ZonedDateTime.parse("2015-02-03T01:00:00.000Z"));
    }

    @Test
    void testDay() {
        assertThat(DateExpressionParser.parse("day()", nowEpochMilli).get())
                .isEqualTo(ZonedDateTime.parse("2015-02-03T00:00:00.000Z"));
    }

    @Test
    void testWeek() {
        assertThat(DateExpressionParser.parse("week()", nowEpochMilli).get())
                .isEqualTo(ZonedDateTime.parse("2015-02-02T00:00:00.000Z"));
    }

    @Test
    void testMonth() {
        assertThat(DateExpressionParser.parse("month()", nowEpochMilli).get())
                .isEqualTo(ZonedDateTime.parse("2015-02-01T00:00:00.000Z"));
    }

    @Test
    void testYear() {
        assertThat(DateExpressionParser.parse("year()", nowEpochMilli).get())
                .isEqualTo(ZonedDateTime.parse("2015-01-01T00:00:00.000Z"));
    }

    @Test
    void testSecondPlus() {
        assertThat(DateExpressionParser.parse("second()+4d", nowEpochMilli).get())
                .isEqualTo(ZonedDateTime.parse("2015-02-07T01:22:33.000Z"));
    }

    @Test
    void testHourMinus() {
        assertThat(DateExpressionParser.parse("hour()+5h-1h", nowEpochMilli).get())
                .isEqualTo(ZonedDateTime.parse("2015-02-03T05:00:00.000Z"));
    }

    @Test
    void testWeekPlus() {
        assertThat(DateExpressionParser.parse("week()+1w", nowEpochMilli).get())
                .isEqualTo(ZonedDateTime.parse("2015-02-09T00:00:00.000Z"));
    }

    @Test
    void testMilliseconds() {
        assertThat(DateExpressionParser.parse("second()+5ms", nowEpochMilli).get())
                .isEqualTo(ZonedDateTime.parse("2015-02-03T01:22:33.005Z"));
        assertThat(DateExpressionParser.parse("second()-5ms", nowEpochMilli).get())
                .isEqualTo(ZonedDateTime.parse("2015-02-03T01:22:32.995Z"));
        assertThat(DateExpressionParser.parse("second()+1m5ms", nowEpochMilli).get())
                .isEqualTo(ZonedDateTime.parse("2015-02-03T01:23:33.005Z"));
        assertThat(DateExpressionParser.parse("second()+1000000ns", nowEpochMilli).get())
                .isEqualTo(ZonedDateTime.parse("2015-02-03T01:22:33.001Z"));
    }

    @Test
    void testUnitsAreCaseSensitive() {
        assertThat(DateExpressionParser.parse("second()+1m", nowEpochMilli).get())
                .isEqualTo(ZonedDateTime.parse("2015-02-03T01:23:33.000Z"));
        assertThat(DateExpressionParser.parse("second()+1M", nowEpochMilli).get())
                .isEqualTo(ZonedDateTime.parse("2015-03-03T01:22:33.000Z"));
    }

    @Test
    void testFinerThanMillisecond() {
        testError("now()+1ns", "'1ns' can't be used as a duration, as durations are held to the millisecond");
    }

    @Test
    void testTokens() {
        final DateTimeSettings dateTimeSettings = DateTimeSettings.builder().referenceTime(nowEpochMilli).build();
        assertThat(DateExpressionParser.parse(tokens("second()+5ms"), dateTimeSettings).orElseThrow())
                .isEqualTo(ZonedDateTime.parse("2015-02-03T01:22:33.005Z"));
        assertThat(DateExpressionParser.parse(tokens("second()+1M"), dateTimeSettings).orElseThrow())
                .isEqualTo(ZonedDateTime.parse("2015-03-03T01:22:33.000Z"));
        assertThatThrownBy(() -> DateExpressionParser.parse(tokens("second()+1H"), dateTimeSettings))
                .isInstanceOf(DateTimeException.class)
                .hasMessageContaining("Unknown unit 'H'");
    }

    @Test
    void testDayAndWeekAcrossClockChange() {
        // The clocks go forward on 31 March 2024, so the day is 23 hours long.
        final String time = "2024-03-30T12:00:00.000+00:00[Europe/London]";
        assertThat(DateExpressionParser.parse(time + " + 1d", nowEpochMilli).orElseThrow().toInstant())
                .isEqualTo(Instant.parse("2024-03-31T11:00:00Z"));
        assertThat(DateExpressionParser.parse(time + " + 24h", nowEpochMilli).orElseThrow().toInstant())
                .isEqualTo(Instant.parse("2024-03-31T12:00:00Z"));
        assertThat(DateExpressionParser.parse(time + " + 1w", nowEpochMilli).orElseThrow().toInstant())
                .isEqualTo(Instant.parse("2024-04-06T11:00:00Z"));
    }

    // The tokens of a StroomQL term's value, as the query language passes them on.
    private static List<AbstractToken> tokens(final String value) {
        return StructureBuilder.createBasic(Tokeniser.parse(value)).getChildren();
    }

    @Test
    public void testMissingTime() {
        testError("+1w", "You must specify a time or time constant before adding or " +
                "subtracting duration '1w'.");
    }

    @Test
    public void testTwoTimes1() {
        testError("now()+now()", "Text '+' could not be parsed at index 1");
    }

    @Test
    public void testTwoTimes2() {
        testError("now() now()", "Attempt to set the date and time twice with 'now()'. " +
                "You cannot have more than one declaration of date and time.");
    }


    @Test
    public void testMissingSign1() {
        testError("now() 1w", "You must specify a plus or minus operation before " +
                "duration '1w'.");
    }

    @Test
    public void testMissingSign2() {
        testError("1w", "You must specify a plus or minus operation before duration '1w'.");
    }

    private void testError(final String expression, final String expectedMessage) {
        DateTimeException dateTimeException = null;
        try {
            DateExpressionParser.parse(expression, nowEpochMilli).get();
        } catch (final DateTimeException e) {
            dateTimeException = e;
        }
        assertThat(dateTimeException.getMessage())
                .isEqualTo(expectedMessage);
    }
}
