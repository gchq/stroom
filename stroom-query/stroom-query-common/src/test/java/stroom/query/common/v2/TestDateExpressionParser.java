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
import stroom.query.api.UserTimeZone;
import stroom.query.api.UserTimeZone.Use;
import stroom.query.api.token.AbstractToken;
import stroom.query.api.token.TokenException;
import stroom.query.language.token.StructureBuilder;
import stroom.query.language.token.Tokeniser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = {"2024-02-30T10:00:00Z", "2024-02-30T10:00:00.000Z", "2024-02-30T10:00:00+01:00",
            "2024-02-30T10:00:00+0100", "2024-02-30T10:00:00", "2024-02-30T24:00:00Z"})
    void dayThatTheMonthDoesNotHave(final String text) {
        for (final String value : new String[]{text, text + " + 1h"}) {
            assertThatThrownBy(() -> DateExpressionParser.parse(value, nowEpochMilli))
                    .isInstanceOf(DateTimeException.class)
                    .hasMessageContaining("Invalid date 'FEBRUARY 30'");
        }
        assertThatThrownBy(() -> DateExpressionParser.getMs("Time", text))
                .hasMessageContaining("Invalid date 'FEBRUARY 30'");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "2024-13-01T10:00:00Z|Invalid value for MonthOfYear (valid values 1 - 12): 13",
            "2024-13-01T10:00:00+0100|Invalid value for MonthOfYear (valid values 1 - 12): 13",
            "2024-13-01T10:00:00|Invalid value for MonthOfYear (valid values 1 - 12): 13",
            "2024-07-01T25:00:00Z|Invalid value for HourOfDay (valid values 0 - 23): 25",
            "2024-02-30T10:00:00Z[Europe/London]|Invalid date 'FEBRUARY 30'",
            "2024-07-01T10:00:00Zsoon|unparsed text found at index 19",
            "soon|could not be parsed at index 0"})
    void theErrorSaysWhatIsWrong(final String text, final String message) {
        assertThatThrownBy(() -> DateExpressionParser.parse(text, nowEpochMilli))
                .isInstanceOf(DateTimeException.class)
                .hasMessageContaining(message);
    }

    @Test
    void dayThatTheMonthHas() {
        for (final String text : new String[]{"2024-02-29T10:00:00Z", "2024-02-29T11:00:00+0100",
                "2024-02-29T11:00:00+01"}) {
            assertThat(DateExpressionParser.parse(text, nowEpochMilli).orElseThrow().toInstant())
                    .isEqualTo(Instant.parse("2024-02-29T10:00:00Z"));
        }
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
    }

    @Test
    void testBadDurationTokens() {
        assertBadDurationToken("1H", "Unknown unit 'H'");
        assertBadDurationToken("1ns", "held to the millisecond");
        assertBadDurationToken("99999999999999999999s", "too large");
        // These can be read, but take the time out of range.
        assertBadDurationToken("9223372036854775807s", "");
        assertBadDurationToken("9223372036854775807w", "");
    }

    private void assertBadDurationToken(final String duration, final String message) {
        final DateTimeSettings dateTimeSettings = DateTimeSettings.builder().referenceTime(nowEpochMilli).build();
        assertThatThrownBy(() -> DateExpressionParser.parse(tokens("second()+" + duration), dateTimeSettings))
                .isInstanceOfSatisfying(TokenException.class, e ->
                        assertThat(e.getToken().getText()).isEqualTo(duration))
                .hasMessageContaining(message);
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

    @Test
    void testQueryZone() {
        // 23:30 on 30 June in UTC is 00:30 on 1 July in London.
        final DateTimeSettings london = DateTimeSettings.builder()
                .referenceTime(Instant.parse("2024-06-30T23:30:00Z").toEpochMilli())
                .timeZone(UserTimeZone.builder().use(Use.ID).id("Europe/London").build())
                .build();
        assertInstant("now()", london, "2024-06-30T23:30:00Z");
        assertInstant("day()", london, "2024-06-30T23:00:00Z");
        assertInstant("week()", london, "2024-06-30T23:00:00Z");
        assertInstant("month()", london, "2024-06-30T23:00:00Z");
        assertInstant("year()", london, "2024-01-01T00:00:00Z");
        // The clocks go back on 27 October.
        assertInstant("day()+5M", london, "2024-12-01T00:00:00Z");
        // A date with an offset keeps it.
        assertInstant("2024-06-30T23:30:00.000Z+5M", london, "2024-11-30T23:30:00Z");
    }

    @Test
    void testHourInZoneWithHalfHourOffset() {
        final DateTimeSettings kolkata = DateTimeSettings.builder()
                .referenceTime(Instant.parse("2024-06-30T23:45:00Z").toEpochMilli())
                .timeZone(UserTimeZone.builder().use(Use.ID).id("Asia/Kolkata").build())
                .build();
        assertInstant("hour()", kolkata, "2024-06-30T23:30:00Z");
    }

    @Test
    void testNoZoneIsUtc() {
        final DateTimeSettings noZone = DateTimeSettings.builder()
                .referenceTime(Instant.parse("2024-06-30T23:30:00Z").toEpochMilli())
                .build();
        assertInstant("day()", noZone, "2024-06-30T00:00:00Z");
    }

    @Test
    void testOffsets() {
        final DateTimeSettings noZone = DateTimeSettings.builder().referenceTime(nowEpochMilli).build();
        assertInstant("2024-07-01T09:00:00+01:00", noZone, "2024-07-01T08:00:00Z");
        // As formatDate() writes an offset.
        assertInstant("2024-07-01T09:00:00.000+0100", noZone, "2024-07-01T08:00:00Z");
        assertInstant("2024-07-01T09:00:00.000+0100-1d", noZone, "2024-06-30T08:00:00Z");
        assertInstant("2024-07-01T09:00:00.000-0530+1h", noZone, "2024-07-01T15:30:00Z");
    }

    @Test
    void testCompound() {
        final DateTimeSettings noZone = DateTimeSettings.builder().referenceTime(nowEpochMilli).build();
        assertInstant("second()-1d2h3ms", noZone, "2015-02-01T23:22:32.997Z");
    }

    @Test
    void testUnreadableDurationsSayWhy() {
        assertReason("now()-1H", "Unknown unit 'H' in '1H'");
        assertReason("now() - 1H", "Unknown unit 'H' in '1H'");
        assertReason("now()-1mo", "Unknown unit 'mo' in '1mo'");
        assertReason("now()-1d-1H", "Unknown unit 'H' in '1H'");
        assertReason("now()-1d2H", "Unknown unit 'H' in '1d2H'");
        assertReason("2015-02-03T01:22:33.056Z+1D", "Unknown unit 'D' in '1D'");
        assertReason("2015-02-03T01:22:33.056-1H", "Unknown unit 'H' in '1H'");
        // The hyphens of a date that can't be read are not durations.
        assertReason("2015-02-03T99:22:33.056Z", "could not be parsed");
        assertReason("yesterday", "could not be parsed");
    }

    @Test
    void testGetMsGivesReason() {
        assertThatThrownBy(() -> DateExpressionParser.getMs("EventTime", "now()-1H"))
                .hasMessageStartingWith("Expected a standard date value for field \"EventTime\" but was given "
                                        + "string \"now()-1H\": Unknown unit 'H' in '1H'");
        assertThatThrownBy(() -> DateExpressionParser.getMs("EventTime", "now()-1ns"))
                .hasMessageContaining("held to the millisecond");
        assertThatThrownBy(() -> DateExpressionParser.getMs("EventTime", " "))
                .hasMessage("Expected a standard date value for field \"EventTime\" but was given string \" \": "
                            + "There is no date");
        assertThatThrownBy(() -> DateExpressionParser.getMs("EventTime", null))
                .hasMessage("Expected a standard date value for field \"EventTime\" but was given string \"null\"");
    }

    @Test
    void testUnreadableDateTokenSaysWhy() {
        final DateTimeSettings dateTimeSettings = DateTimeSettings.builder().referenceTime(nowEpochMilli).build();
        assertThatThrownBy(() -> DateExpressionParser.parse(tokens("2024-13-01T00:00:00Z-1d"), dateTimeSettings))
                .isInstanceOfSatisfying(TokenException.class, e ->
                        assertThat(e.getToken().getText()).isEqualTo("2024-13-01T00:00:00Z"))
                .hasMessageContaining("2024-13-01T00:00:00Z")
                .hasMessageContaining("could not be parsed");
    }

    @Test
    void testUnknownZone() {
        final DateTimeSettings unknown = DateTimeSettings.builder()
                .referenceTime(nowEpochMilli)
                .timeZone(UserTimeZone.builder().use(Use.ID).id("Mars/Base").build())
                .build();
        assertThatThrownBy(() -> DateExpressionParser.getMs("EventTime", "now()-1d", unknown))
                .hasMessageEndingWith("Unknown time zone 'Mars/Base'. Use a name such as Europe/London");
        assertThatThrownBy(() -> DateExpressionParser.getMs("EventTime", "2024-07-01T00:00:00", unknown))
                .hasMessageEndingWith("Unknown time zone 'Mars/Base'. Use a name such as Europe/London");
        assertThat(DateExpressionParser.getMs("EventTime", "2024-07-01T00:00:00Z", unknown))
                .isEqualTo(Instant.parse("2024-07-01T00:00:00Z").toEpochMilli());
    }

    private void assertReason(final String expression, final String reason) {
        assertThatThrownBy(() -> DateExpressionParser.parse(expression, nowEpochMilli))
                .isInstanceOf(DateTimeException.class)
                .hasMessageContaining(reason);
    }

    // Checks that text means the same time as it does when a query's where clause gives it as tokens.
    private static void assertInstant(final String expression,
                                      final DateTimeSettings dateTimeSettings,
                                      final String expected) {
        assertThat(DateExpressionParser.parse(expression, dateTimeSettings).orElseThrow().toInstant())
                .as(expression)
                .isEqualTo(Instant.parse(expected));
        assertThat(DateExpressionParser.parse(tokens(expression), dateTimeSettings).orElseThrow().toInstant())
                .as("Tokens of " + expression)
                .isEqualTo(Instant.parse(expected));
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
