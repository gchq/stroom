/*
 * Copyright 2026 Crown Copyright
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.text.ParseException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDateDiff {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "year|2023-07-01T00:00:00|2024-06-30T23:59:59|0",
            "year|2023-07-01T00:00:00|2024-07-01T00:00:00|1",
            "quarter|2024-01-15T00:00:00|2024-07-15T00:00:00|2",
            // Whole months, so 31 January to 1 February is none.
            "month|2024-01-31T00:00:00|2024-02-01T00:00:00|0",
            "month|2024-01-31T00:00:00|2024-03-31T00:00:00|2",
            "month|2024-03-31T00:00:00|2024-01-31T00:00:00|-2",
            "week|2024-01-01T00:00:00|2024-01-14T23:59:59|1",
            // Days are counted on the calendar, so a day of 23 hours is one and 24 hours of one is not.
            "day|2024-03-30T12:00:00|2024-03-31T12:00:00|1",
            "day|2024-03-30T12:00:00|2024-03-31T11:59:59|0",
            // Shorter units are exact.
            "hour|2024-03-30T12:00:00|2024-03-31T12:00:00|23",
            "hour|2024-10-26T12:00:00|2024-10-27T12:00:00|25",
            "minute|2024-01-01T00:00:00|2024-01-01T01:00:59|60",
            "second|2024-01-01T00:00:00|2024-01-01T00:01:00|60",
            "millisecond|2024-01-01T00:00:00|2024-01-01T00:00:01|1000",
            "day|2024-01-02T00:00:00|2024-01-01T12:00:00|0",
            // A month that is too short is not whole until it is over, as java.time has it.
            "month|2024-01-31T00:00:00|2024-02-29T00:00:00|0",
            "month|2024-01-31T00:00:00|2024-02-29T23:59:59|0",
            "month|2024-01-31T00:00:00|2024-03-01T00:00:00|1",
            "quarter|2023-11-30T00:00:00|2024-02-29T12:00:00|0",
            "year|2024-02-29T00:00:00|2025-02-28T12:00:00|0",
            "year|2024-02-29T00:00:00|2025-03-01T00:00:00|1",
            "month|2024-03-31T00:00:00|2024-02-29T00:00:00|-1",
            "month|2024-03-31T00:00:00|2024-02-29T12:00:00|0",
            "quarter|2024-02-29T10:00:00|2022-05-28T13:00:00|-7",
            // The second 01:10 of 27 October.
            "day|2024-10-26T00:30:00Z|2024-10-27T00:30:00Z|1",
            "day|2024-10-26T00:30:00Z|2024-10-27T01:10:00Z|1"})
    void units(final String unit, final String start, final String end, final long expected) throws ParseException {
        assertThat(eval("dateDiff('" + unit + "', " + start + ", " + end + ")")).isEqualTo(ValLong.create(expected));
        assertThat(eval("dateDiff(${field}, " + start + ", " + end + ")", ValString.create(unit)))
                .isEqualTo(ValLong.create(expected));
    }

    // The count is the most units that can be added without going past the end, and it never goes back as the end moves
    // away from the start.
    @ParameterizedTest
    @CsvSource({
            "Europe/London,2024-10-26T00:30:00Z",
            "Europe/London,2024-03-30T01:30:00Z",
            "Europe/London,2024-03-31T10:00:00Z",
            "Europe/London,2024-12-31T23:30:00Z",
            "America/St_Johns,2010-11-06T02:30:30Z",
            "America/St_Johns,2010-11-07T02:30:30Z",
            "America/Havana,2024-11-02T04:30:00Z",
            "Asia/Beirut,2024-03-29T22:30:00Z",
            "Australia/Lord_Howe,2024-04-05T15:15:00Z",
            "Pacific/Apia,2011-12-28T10:30:00Z"})
    void countIsTheMostThatDoesNotGoPastTheEnd(final ZoneId zone, final Instant from) {
        final ZonedDateTime start = from.atZone(zone);
        for (final DateDiffUnit unit : DateDiffUnit.values()) {
            for (final int direction : new int[]{1, -1}) {
                long last = 0;
                for (int i = 1; i < 24 * 6 * 20; i++) {
                    final ZonedDateTime end = start.plusMinutes(i * 10L * direction);
                    final String what = unit + " from " + start + " to " + end;
                    final long count = unit.between(start, end);
                    assertThat(count * direction).as(what).isGreaterThanOrEqualTo(last);
                    assertThat(add(start, count, unit).compareTo(end) * direction).as(what)
                            .isLessThanOrEqualTo(0);
                    assertThat(add(start, count + direction, unit).compareTo(end) * direction).as(what)
                            .isGreaterThan(0);
                    last = count * direction;
                }
            }
        }
    }

    // Where no clocks change it is the same as java.time.
    @ParameterizedTest
    @CsvSource({"2024-01-31T10:00:00Z", "2024-02-29T10:00:00Z", "2023-11-30T00:00:00Z", "2024-12-31T23:59:59Z"})
    void sameAsJavaTimeWithNoClockChanges(final Instant from) {
        final ZonedDateTime start = from.atZone(ZoneOffset.UTC);
        for (final DateDiffUnit unit : DateDiffUnit.values()) {
            for (int hours = -24 * 800; hours < 24 * 800; hours += 5) {
                final ZonedDateTime end = start.plusHours(hours);
                assertThat(unit.between(start, end)).as(unit + " from " + start + " to " + end)
                        .isEqualTo(getTemporalUnit(unit).between(start, end));
            }
        }
    }

    private static ZonedDateTime add(final ZonedDateTime start, final long count, final DateDiffUnit unit) {
        return unit == DateDiffUnit.QUARTER
                ? start.plusMonths(count * 3)
                : start.plus(count, getTemporalUnit(unit));
    }

    private static TemporalUnit getTemporalUnit(final DateDiffUnit unit) {
        return switch (unit) {
            case YEAR -> ChronoUnit.YEARS;
            case QUARTER -> IsoFields.QUARTER_YEARS;
            case MONTH -> ChronoUnit.MONTHS;
            case WEEK -> ChronoUnit.WEEKS;
            case DAY -> ChronoUnit.DAYS;
            case HOUR -> ChronoUnit.HOURS;
            case MINUTE -> ChronoUnit.MINUTES;
            case SECOND -> ChronoUnit.SECONDS;
            case MILLISECOND -> ChronoUnit.MILLIS;
        };
    }

    @Test
    void theTimeZoneIsOnlyNeededForLongerUnits() throws ParseException {
        assertThat(DateFunctionTestUtil.eval(DateFunctionTestUtil.UNKNOWN_ZONE,
                "dateDiff('hour', 2024-01-01T00:00:00Z, 2024-01-01T02:00:00Z)", ValNull.INSTANCE))
                .isEqualTo(ValLong.create(2));
        assertThat(DateFunctionTestUtil.eval(DateFunctionTestUtil.UNKNOWN_ZONE,
                "dateDiff('day', 2024-01-01T00:00:00Z, 2024-01-01T02:00:00Z)", ValNull.INSTANCE).toString())
                .contains("Unknown time zone 'Mars/Base'");
    }

    @Test
    void zones() throws ParseException {
        // 25 hours in London on this day but 24 in UTC.
        final String dates = "2024-10-26T23:00:00Z, 2024-10-27T23:30:00Z";
        assertThat(eval("dateDiff('day', " + dates + ")")).isEqualTo(ValLong.create(0));
        assertThat(eval("dateDiff('day', " + dates + ", 'UTC')")).isEqualTo(ValLong.create(1));
        assertThat(eval("dateDiff('day', " + dates + ", ${field})", ValString.create("UTC")))
                .isEqualTo(ValLong.create(1));
    }

    @Test
    void nulls() throws ParseException {
        assertThat(eval("dateDiff('day', ${field}, 2024-01-01T00:00:00Z)")).isEqualTo(ValNull.INSTANCE);
        assertThat(eval("dateDiff('day', 2024-01-01T00:00:00Z, ${field})")).isEqualTo(ValNull.INSTANCE);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "dateDiff(${field}, 2024-01-01T00:00:00Z, 2024-01-01T00:00:00Z)|Unknown unit 'soon'. Use one of year",
            "dateDiff('day', ${field}, 2024-01-01T00:00:00Z)|'soon' is not a date",
            "dateDiff('day', 1d, 2024-01-01T00:00:00Z)|A period is not a date"})
    void errors(final String expression, final String message) throws ParseException {
        final Val val = eval(expression, ValString.create("soon"));
        assertThat(val.type()).as(val.toString()).isEqualTo(Type.ERR);
        assertThat(val.toString()).contains(message);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "dateDiff('days', ${field}, ${field})|Unknown unit 'days'",
            "dateDiff('day', ${field}, ${field}, 'Mars/Base')|Unknown time zone"})
    void mistakesThatAreReportedWhenTheExpressionIsParsed(final String expression, final String message) {
        assertThatThrownBy(() -> eval(expression)).hasMessageContaining(message);
    }

    // In a query whose time zone is Europe/London.
    private static Val eval(final String expression, final Val field) throws ParseException {
        return DateFunctionTestUtil.eval(DateFunctionTestUtil.LONDON, expression, field);
    }

    private static Val eval(final String expression) throws ParseException {
        return eval(expression, ValNull.INSTANCE);
    }
}
