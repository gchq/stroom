/*
 * Copyright 2024 Crown Copyright
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

package stroom.query.language.functions;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.UserTimeZone;
import stroom.query.api.UserTimeZone.Use;
import stroom.query.api.token.TokenException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestParseDate extends AbstractFunctionTest<ParseDate> {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // With no offset the date is in the zone of the query, an hour ahead of UTC in July.
            "2024-07-01T09:00:00.000||2024-07-01T08:00:00Z",
            "2024-07-01T09:00:00||2024-07-01T08:00:00Z",
            "2024-01-01T09:00:00||2024-01-01T09:00:00Z",
            "2024-07-01||2024-06-30T23:00:00Z",
            "2024-01-01||2024-01-01T00:00:00Z",
            "2024 07 01|, 'yyyy MM dd'|2024-06-30T23:00:00Z",
            // An offset or zone in the text or the call wins.
            "2024-07-01T09:00:00Z||2024-07-01T09:00:00Z",
            "2024-07-01T09:00:00.000+0200||2024-07-01T07:00:00Z",
            "2024 07 01 09 +02|, 'yyyy MM dd HH X'|2024-07-01T07:00:00Z",
            "2024 07 01|, 'yyyy MM dd', 'UTC'|2024-07-01T00:00:00Z",
            // Text that says it is in UTC is.
            "2024-07-01T08:00:00Z|, \"yyyy-MM-dd'T'HH:mm:ss'Z'\"|2024-07-01T08:00:00Z"})
    void parseDateUsesTheZoneOfTheQuery(final String text, final String otherParams, final Instant expected)
            throws ParseException {
        final String params = otherParams == null
                ? ""
                : otherParams;
        final Val date = ValDate.create(expected.toEpochMilli());

        assertThat(evalInLondon("parseDate('" + text + "'" + params + ")", ValNull.INSTANCE)).isEqualTo(date);
        assertThat(evalInLondon("parseDate(${field}" + params + ")", ValString.create(text))).isEqualTo(date);
    }

    @Test
    void dateThatIsTooLarge() throws ParseException {
        final String text = "+999999999-01-01T00:00:00Z";
        final String message = "'" + text + "' is too large to be a date";

        assertThatThrownBy(() -> evalInLondon("parseDate('" + text + "')", ValNull.INSTANCE))
                .hasMessageContaining(message);
        assertThat(evalInLondon("parseDate(${field})", ValString.create(text)))
                .isEqualTo(ValErr.create(message));
    }

    @Test
    void parseDateOfAConstantGivesADate() throws ParseException {
        final long millis = Instant.parse("2024-07-01T08:00:00Z").toEpochMilli();

        assertThat(evalInLondon("typeOf(parseDate('2024-07-01T08:00:00Z'))", ValNull.INSTANCE))
                .isEqualTo(ValString.create("date"));
        assertThat(evalInLondon("parseDate(" + millis + ")", ValNull.INSTANCE)).isEqualTo(ValDate.create(millis));
    }

    @Test
    void zoneOfEachRow() throws ParseException {
        final String expression = "parseDate('2024 07 01', 'yyyy MM dd', ${field})";

        assertThat(evalInLondon(expression, ValString.create("America/New_York")))
                .isEqualTo(ValDate.create(Instant.parse("2024-07-01T04:00:00Z").toEpochMilli()));
        assertThat(evalInLondon(expression, ValString.create("UTC")))
                .isEqualTo(ValDate.create(Instant.parse("2024-07-01T00:00:00Z").toEpochMilli()));
        assertThat(evalInLondon(expression, ValString.create("Mars/Base")).toString())
                .contains("Unknown time zone 'Mars/Base'");
        assertThat(evalInLondon(expression, ValNull.INSTANCE).toString()).contains("There is no time zone");
    }

    // Without a pattern a number is milliseconds, but a pattern is of text.
    @Test
    void numbers() throws ParseException {
        final ValDate date = ValDate.create(Instant.parse("2024-07-01T08:00:00Z").toEpochMilli());
        assertThat(evalInLondon("parseDate(1719820800000)", ValNull.INSTANCE)).isEqualTo(date);
        assertThat(evalInLondon("parseDate(2024-07-01T08:00:00Z)", ValNull.INSTANCE)).isEqualTo(date);
        assertThat(evalInLondon("parseDate(${field})", ValLong.create(1719820800000L))).isEqualTo(date);
        assertThat(evalInLondon("parseDate(${field})", date)).isEqualTo(date);

        final ValDate day = ValDate.create(Instant.parse("2024-06-30T23:00:00Z").toEpochMilli());
        assertThat(evalInLondon("parseDate(20240701, 'yyyyMMdd')", ValNull.INSTANCE)).isEqualTo(day);
        assertThat(evalInLondon("parseDate(${field}, 'yyyyMMdd')", ValLong.create(20240701))).isEqualTo(day);
    }

    @Test
    void valuesThatAreNotDates() throws ParseException {
        for (final String pattern : new String[]{"", ", 'yyyy'", ", 'yyyy', 'UTC'"}) {
            for (final Val val : List.of(ValDuration.create(2024), ValPeriod.create(0, 0, 1),
                    ValDouble.create(Double.NaN), ValBoolean.TRUE)) {
                assertThat(evalInLondon("parseDate(${field}" + pattern + ")", val).type())
                        .as(val + pattern)
                        .isEqualTo(Type.ERR);
            }
            for (final String literal : new String[]{"2h", "1d"}) {
                assertThatThrownBy(() -> evalInLondon("parseDate(" + literal + pattern + ")", ValNull.INSTANCE))
                        .as(literal + pattern)
                        .hasMessageContaining(" is not a date");
            }
            for (final String literal : new String[]{"2h", "1d", "'soon'"}) {
                assertThatThrownBy(() -> evalInLondon("parseDate(" + literal + pattern + ")", ValNull.INSTANCE))
                        .as(literal + pattern)
                        .isInstanceOf(TokenException.class);
            }
        }
    }

    @Test
    void blanks() throws ParseException {
        for (final String params : new String[]{"", ", 'yyyy'", ", 'yyyy', 'UTC'", ", 'yyyy', ${field}"}) {
            assertThat(evalInLondon("parseDate(' '" + params + ")", ValString.create("Mars/Base")))
                    .isEqualTo(ValNull.INSTANCE);
            assertThat(evalInLondon("parseDate(${field}" + params + ")", ValString.create(" ")))
                    .isEqualTo(ValNull.INSTANCE);
        }
    }

    // Not as a mistake by the query's author.
    @Test
    void faultIsNotHidden() {
        assertThatThrownBy(() -> new ParseDate(DateFunctionTestUtil.LONDON, "parseDate").setParams(new Param[]{null}))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FormatDate(DateFunctionTestUtil.LONDON, "formatDate").setParams(new Param[]{null}))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void badPatternOrZoneIsReportedWithAPosition() {
        for (final String params : new String[]{"'qqqqqq'", "'yyyy', 'BST'", "'yyyy', 'Mars/Base'"}) {
            for (final String name : new String[]{"parseDate", "formatDate"}) {
                assertThatThrownBy(() -> evalInLondon(name + "(${field}, " + params + ")", ValNull.INSTANCE))
                        .as(name + params)
                        .isInstanceOf(TokenException.class);
            }
        }
    }

    @Test
    void errorOfTheZoneIsKept() throws ParseException {
        assertThat(evalInLondon("parseDate('2024 07 01', 'yyyy MM dd', ${field})", ValErr.create("lookup failed")))
                .isEqualTo(ValErr.create("lookup failed"));
    }

    @Test
    void zoneNames() throws ParseException {
        final ValDate date = ValDate.create(Instant.parse("2024-07-01T04:00:00Z").toEpochMilli());
        assertThat(evalInLondon("parseDate('2024 07 01', 'yyyy MM dd', 'America/New_York')", ValNull.INSTANCE))
                .isEqualTo(date);
        // A blank that is written is UTC, as it always has been.
        assertThat(evalInLondon("parseDate('2024 07 01', 'yyyy MM dd', '')", ValNull.INSTANCE))
                .isEqualTo(ValDate.create(Instant.parse("2024-07-01T00:00:00Z").toEpochMilli()));
        // A short name is refused, whether it is written or comes from a field.
        assertThatThrownBy(() -> evalInLondon("parseDate('2024 07 01', 'yyyy MM dd', 'BST')", ValNull.INSTANCE))
                .hasMessageContaining("Unknown time zone 'BST'");
        assertThat(evalInLondon("parseDate('2024 07 01', 'yyyy MM dd', ${field})", ValString.create("BST")).toString())
                .contains("Unknown time zone 'BST'");
    }

    @Test
    void offsetOfADateWithNoTime() throws ParseException {
        final ValDate date = ValDate.create(Instant.parse("2024-06-30T22:00:00Z").toEpochMilli());

        assertThat(evalInLondon("parseDate('2024 07 01 +02', 'yyyy MM dd X')", ValNull.INSTANCE)).isEqualTo(date);
        assertThat(evalInLondon("parseDate(${field}, 'yyyy MM dd X')", ValString.create("2024 07 01 +02")))
                .isEqualTo(date);
        assertThat(evalInLondon(
                "parseDate('2024 07 01 +02', 'yyyy MM dd X', 'America/New_York')", ValNull.INSTANCE))
                .isEqualTo(date);
    }

    @Test
    void theTimeZoneIsOnlyNeededForTextWithNoOffset() throws ParseException {
        final ExpressionContext unknown = DateFunctionTestUtil.UNKNOWN_ZONE;
        final ValDate date = ValDate.create(Instant.parse("2024-07-01T09:00:00Z").toEpochMilli());
        assertThat(DateFunctionTestUtil.eval(unknown, "parseDate('2024-07-01T09:00:00Z')", ValNull.INSTANCE))
                .isEqualTo(date);
        assertThat(DateFunctionTestUtil.eval(
                unknown, "parseDate(${field})", ValString.create("2024-07-01T09:00:00Z")))
                .isEqualTo(date);
        assertThat(DateFunctionTestUtil.eval(
                unknown, "parseDate(${field})", ValString.create("2024-07-01T09:00:00")).toString())
                .contains("Unknown time zone 'Mars/Base'");
        // With a position, as any mistake in a constant has.
        for (final String text : new String[]{"2024-07-01T09:00:00", "soon"}) {
            assertThatThrownBy(() -> DateFunctionTestUtil.eval(
                    unknown, "parseDate('" + text + "')", ValNull.INSTANCE))
                    .isInstanceOf(TokenException.class);
        }
    }

    @Test
    void badTextInAFieldGivesAnError() throws ParseException {
        assertThat(evalInLondon("parseDate(${field})", ValString.create("yesterday")).type()).isEqualTo(Type.ERR);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "yesterday|Text 'yesterday' could not be parsed at index 0",
            "2024-07|Text '2024-07' could not be parsed at index 7",
            "20240701|Text '20240701' could not be parsed at index 0",
            "2024-07-01Z|Text '2024-07-01Z' could not be parsed at index 10",
            "2024-02-30|Text '2024-02-30' could not be parsed: Invalid date 'FEBRUARY 30'",
            "2024-02-30T10:00:00|Text '2024-02-30T10:00:00' could not be parsed: Invalid date 'FEBRUARY 30'",
            "2024-13-01|Text '2024-13-01' could not be parsed: Invalid value for MonthOfYear (valid values 1 - 12): 13",
            "2024-13-01T10:00:00|Text '2024-13-01T10:00:00' could not be parsed: Invalid value for MonthOfYear "
            + "(valid values 1 - 12): 13"})
    void theErrorSaysWhatIsWrong(final String text, final String message) throws ParseException {
        assertThat(evalInLondon("parseDate(${field})", ValString.create(text))).isEqualTo(ValErr.create(message));
        assertThatThrownBy(() -> evalInLondon("parseDate('" + text + "')", ValNull.INSTANCE))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining(message);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "2024-02-30 10:00|yyyy-MM-dd HH:mm",
            "2023-02-29|yyyy-MM-dd",
            "2023-02-29|uuuu-MM-dd",
            "30 Feb 24|dd MMM yy",
            "2024-04-31 +02|yyyy-MM-dd X",
            "2024-04-31 10:00 +02|yyyy-MM-dd HH:mm X",
            "2024-04-31 10:00 Europe/Paris|yyyy-MM-dd HH:mm VV",
            "2024-02-30 24:00|yyyy-MM-dd HH:mm",
            // Each is a different day in the time zone.
            "2024-02-30 01:00 +02:00 America/New_York|yyyy-MM-dd HH:mm XXX VV",
            "2024-02-30 12:00 +02:00 America/New_York|yyyy-MM-dd HH:mm XXX VV",
            "2024-02-30 23:00 -10:00 Asia/Tokyo|yyyy-MM-dd HH:mm XXX VV",
            "2024-02-30 24:00 -18:00 Pacific/Kiritimati|yyyy-MM-dd HH:mm XXX VV",
            "2023-02-29 00:00 +18:00 Pacific/Pago_Pago|yyyy-MM-dd HH:mm XXX VV",
            "2024-04-31 1|yyyy-MM-dd[ HH:mm][ H]"})
    void dayThatTheMonthDoesNotHave(final String text, final String pattern) throws ParseException {
        assertThat(evalInLondon("parseDate(${field}, '" + pattern + "')", ValString.create(text)).toString())
                .contains("Text '" + text + "' could not be parsed: Invalid date");
        assertThatThrownBy(() -> evalInLondon("parseDate('" + text + "', '" + pattern + "')", ValNull.INSTANCE))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("Invalid date");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "2024-02-29|yyyy-MM-dd|2024-02-29T00:00:00Z",
            "2023-02-28|yyyy-MM-dd|2023-02-28T00:00:00Z",
            "2024-01-31|yyyy-MM-dd|2024-01-31T00:00:00Z",
            "2023-12-31 24:00|yyyy-MM-dd HH:mm|2024-01-01T00:00:00Z",
            "2024-02-29 24:00|yyyy-MM-dd HH:mm|2024-03-01T00:00:00Z",
            "2023-02-28 24:00|yyyy-MM-dd HH:mm|2023-03-01T00:00:00Z",
            "2024-11-30 24:00|yyyy-MM-dd HH:mm|2024-12-01T00:00:00Z",
            "2024-02-29 23:00 -10:00 Asia/Tokyo|yyyy-MM-dd HH:mm XXX VV|2024-03-01T09:00:00Z",
            "2023-12-31 23:00 -10:00 Asia/Tokyo|yyyy-MM-dd HH:mm XXX VV|2024-01-01T09:00:00Z",
            "2024-01-01 00:00 +10:00 Pacific/Pago_Pago|yyyy-MM-dd HH:mm XXX VV|2023-12-31T14:00:00Z",
            "2024-060|yyyy-DDD|2024-02-29T00:00:00Z",
            "2024-091|yyyy-DDD|2024-03-31T00:00:00Z",
            "2024-060 02|yyyy-DDD MM|2024-02-29T00:00:00Z",
            "2024-060 29|yyyy-DDD dd|2024-02-29T00:00:00Z",
            "2024-W18-3|YYYY-'W'ww-e|2024-04-30T00:00:00Z",
            "29 Feb 24|dd MMM yy|2024-02-29T00:00:00Z"})
    void dayThatTheMonthHas(final String text, final String pattern, final Instant expected) throws ParseException {
        assertThat(evalInLondon("parseDate(${field}, \"" + pattern + "\", 'UTC')", ValString.create(text)))
                .isEqualTo(ValDate.create(expected.toEpochMilli()));
    }

    @Test
    void dateWithNoTimeThatHasNoMidnight() throws ParseException {
        assertThat(evalInLondon("parseDate(${field}, 'yyyy-MM-dd', 'Africa/Cairo')", ValString.create("2024-04-26")))
                .isEqualTo(ValDate.create(Instant.parse("2024-04-25T22:00:00Z").toEpochMilli()));
        final ExpressionContext cairo = ExpressionContext.builder()
                .dateTimeSettings(DateTimeSettings.builder()
                        .timeZone(UserTimeZone.builder().use(Use.ID).id("Africa/Cairo").build())
                        .build())
                .build();
        assertThat(DateFunctionTestUtil.eval(cairo, "parseDate(${field})", ValString.create("2024-04-26")))
                .isEqualTo(ValDate.create(Instant.parse("2024-04-25T22:00:00Z").toEpochMilli()));
    }

    @Test
    void dateWithNoTimeIsReadAsToDateReadsIt() throws ParseException {
        for (final String text : new String[]{"2024-07-01", " 2024-07-01 ", "2024-03-31", "1969-12-31", "0001-01-01"}) {
            assertThat(evalInLondon("parseDate(${field})", ValString.create(text)))
                    .isEqualTo(evalInLondon("toDate(${field})", ValString.create(text)));
        }
        assertThat(evalInLondon("parseDate(${field})", ValString.create("+999999999-01-01")))
                .isEqualTo(ValErr.create("'+999999999-01-01' is too large to be a date"));
        assertThat(DateFunctionTestUtil.eval(
                DateFunctionTestUtil.UNKNOWN_ZONE, "parseDate(${field})", ValString.create("2024-07-01")).toString())
                .contains("Unknown time zone 'Mars/Base'");
    }

    @Test
    void noSettingsIsUtc() throws ParseException {
        assertThat(evalWithNoSettings("parseDate(${field})", ValString.create("2024-07-01T08:00:00")))
                .isEqualTo(ValDate.create(Instant.parse("2024-07-01T08:00:00Z").toEpochMilli()));
    }

    @Override
    Class<ParseDate> getFunctionType() {
        return ParseDate.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "null",
                        ValNull.INSTANCE,
                        Val.create(null)),
                TestCase.of(
                        "",
                        ValNull.INSTANCE,
                        Val.create("")),
                TestCase.of(
                        "0 milli",
                        ValDate.create(Instant.parse("2010-01-01T23:59:59Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59Z")),
                TestCase.of(
                        "1 milli",
                        ValDate.create(Instant.parse("2010-01-01T23:59:59.1Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59.1Z")),
                TestCase.of(
                        "2 milli",
                        ValDate.create(Instant.parse("2010-01-01T23:59:59.12Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59.12Z")),
                TestCase.of(
                        "3 milli",
                        ValDate.create(Instant.parse("2010-01-01T23:59:59.123Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59.123Z")),
                TestCase.of(
                        "4 milli",
                        ValDate.create(Instant.parse("2010-01-01T23:59:59.123Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59.1234Z")),
                TestCase.of(
                        "+0000 offset",
                        ValDate.create(Instant.parse("2010-01-01T23:59:59.123Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59.123+0000")),
                TestCase.of(
                        "+0200 offset",
                        ValDate.create(Instant.parse("2010-01-01T21:59:59.123Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59.123+0200")),
                TestCase.of(
                        "+00:00 offset",
                        ValDate.create(Instant.parse("2010-01-01T23:59:59.123Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59.123+00:00")),
                TestCase.of(
                        "+02:00 offset",
                        ValDate.create(Instant.parse("2010-01-01T21:59:59.123Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59.123+02:00")),


                TestCase.of(
                        "3 milli + format",
                        ValDate.create(Instant.parse("2010-01-01T23:59:59.123Z").toEpochMilli()),
                        Val.create("20100101 235959123Z"),
                        ValString.create("yyyyMMdd HHmmssSSSXX")),

                TestCase.of(
                        "3 milli + format + zone",
                        ValDate.create(Instant.parse("2010-01-01T23:59:59.123Z")
                                .minus(2, ChronoUnit.HOURS)
                                .toEpochMilli()),
                        Val.create("20100101 235959123"),
                        ValString.create("yyyyMMdd HHmmssSSS"),
                        ValString.create("GMT+2"))
        );
    }

    private static Val evalInLondon(final String input, final Val field) throws ParseException {
        return DateFunctionTestUtil.eval(DateFunctionTestUtil.LONDON, input, field);
    }

    private static Val evalWithNoSettings(final String input, final Val field) throws ParseException {
        return DateFunctionTestUtil.eval(DateFunctionTestUtil.NO_SETTINGS, input, field);
    }

    @Test
    void spacesAroundTextWithNoPattern() throws ParseException {
        final Val expected = ValDate.create(1_720_000_800_000L);
        final String text = " 2024-07-03T10:00:00Z ";
        assertThat(DateFunctionTestUtil.eval(
                DateFunctionTestUtil.LONDON, "parseDate('" + text + "')", ValNull.INSTANCE)).isEqualTo(expected);
        assertThat(DateFunctionTestUtil.eval(
                DateFunctionTestUtil.LONDON, "parseDate(${field})", ValString.create(text))).isEqualTo(expected);
        // A pattern can have spaces of its own.
        assertThat(DateFunctionTestUtil.eval(
                DateFunctionTestUtil.LONDON, "parseDate(${field}, ' yyyy-MM-dd')", ValString.create(" 2024-07-03")))
                .isEqualTo(ValDate.create(1_719_961_200_000L));
        assertThat(DateFunctionTestUtil.eval(
                DateFunctionTestUtil.LONDON, "parseDate(${field}, 'yyyy-MM-dd')", ValString.create(" 2024-07-03"))
                .type()).isEqualTo(Type.ERR);
    }
}
