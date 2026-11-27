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
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TestPeriodBetween {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "2023-01-15T09:00:00|2024-03-20T09:00:00|P1Y2M5D",
            // The last day only counts if its time of day has come.
            "2024-01-15T09:00:00|2024-01-16T08:59:59|P0D",
            "2024-01-15T09:00:00|2024-01-16T09:00:00|P1D",
            "2024-01-31T00:00:00|2024-02-29T00:00:00|P29D",
            "2024-01-31T00:00:00|2024-03-31T00:00:00|P2M",
            "2024-01-31T09:00:00|2024-03-31T08:00:00|P1M30D",
            // A day of 23 hours.
            "2024-03-30T12:00:00|2024-03-31T12:00:00|P1D",
            // Backwards.
            "2024-03-20T09:00:00|2023-01-15T09:00:00|P-1Y-2M-5D",
            "2024-01-16T09:00:00|2024-01-15T09:00:01|P0D",
            "2024-01-16T09:00:00|2024-01-15T08:59:59|P-1D",
            // Whole months and then days.
            "2024-03-31T00:00:00|2024-02-29T00:00:00|P-1M",
            "2024-03-31T00:00:00|2024-02-28T00:00:00|P-1M-1D",
            "2024-12-31T00:00:00|2024-06-30T00:00:00|P-6M",
            "2024-03-31T00:00:00|2023-02-28T00:00:00|P-1Y-1M",
            "2024-03-31T09:00:00|2024-02-29T10:00:00|P-30D",
            // Forwards as java.time has it.
            "2024-02-29T00:00:00|2024-03-31T00:00:00|P1M2D",
            "2024-01-15T09:00:00|2024-01-15T09:00:00|P0D"})
    void periods(final String start, final String end, final String expected) throws ParseException {
        assertThat(eval("periodBetween(" + start + ", " + end + ")"))
                .isEqualTo(ValPeriod.create(Period.parse(expected)));
    }

    // Adding the period doesn't go past the end, but a day more does.
    @ParameterizedTest
    @CsvSource({"2020-02-29T10:00:00", "2023-12-31T23:59:59", "2024-03-30T01:30:00", "2024-10-27T01:30:00"})
    void addingThePeriodDoesNotGoPastTheEnd(final String startText) throws ParseException {
        final ZoneId zone = ZoneId.of("Europe/London");
        final ZonedDateTime start = LocalDateTime.parse(startText).atZone(zone);
        for (int hours = 0; hours < 24 * 500; hours += 7) {
            final ZonedDateTime end = start.plusHours(hours);
            final Val val = eval("periodBetween(" + start.toInstant() + ", ${field})",
                    ValDate.create(end.toInstant().toEpochMilli()));
            final Period period = ((ValPeriod) val).toPeriod();
            assertThat(start.plus(period)).as(period + " to " + end).isBeforeOrEqualTo(end);
            assertThat(start.plus(period.plusDays(1))).as(period + " to " + end).isAfter(end);
        }
    }

    // Adding the period backwards doesn't go past the end, but a day more does. Every start is the end of a month, or
    // on a day when the clocks change.
    @ParameterizedTest
    @CsvSource({
            "2024-03-31T00:00:00", "2024-12-31T23:59:59", "2024-05-30T12:00:00", "2023-03-29T10:00:00",
            "2024-03-31T02:30:00", "2024-10-27T01:30:00"})
    void addingThePeriodBackwardsDoesNotGoPastTheEnd(final String startText) throws ParseException {
        final ZoneId zone = ZoneId.of("Europe/London");
        final ZonedDateTime start = LocalDateTime.parse(startText).atZone(zone);
        for (int hours = 1; hours < 24 * 500; hours += 7) {
            final ZonedDateTime end = start.minusHours(hours);
            final Val val = eval("periodBetween(" + start.toInstant() + ", ${field})",
                    ValDate.create(end.toInstant().toEpochMilli()));
            final Period period = ((ValPeriod) val).toPeriod();
            assertThat(start.plus(period)).as(period + " to " + end).isAfterOrEqualTo(end);
            assertThat(start.plus(period.minusDays(1))).as(period + " to " + end).isBefore(end);
            assertThat(start.plus(period.minusMonths(1))).as(period + " to " + end).isBefore(end);
        }
    }

    // Every ten minutes both ways across changes of the clocks, including over midnight.
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
    void clockChanges(final ZoneId zone, final Instant from) {
        final ZonedDateTime start = from.atZone(zone);
        for (final int direction : new int[]{1, -1}) {
            for (int i = 0; i < 24 * 6 * 75; i++) {
                final ZonedDateTime end = start.plusMinutes(i * 10L * direction);
                final Period period = PeriodBetween.between(start, end);
                final String what = period + " from " + start + " to " + end;
                assertThat(start.plus(period).compareTo(end) * direction).as(what).isLessThanOrEqualTo(0);
                assertThat(start.plus(period.plusDays(direction)).compareTo(end) * direction).as(what)
                        .isGreaterThan(0);
                assertThat(period.toTotalMonths()).as(what).isEqualTo(DateDiffUnit.MONTH.between(start, end));
                assertThat(period.toTotalMonths() * direction).as(what).isGreaterThanOrEqualTo(0);
                assertThat(period.getDays() * direction).as(what).isGreaterThanOrEqualTo(0);
            }
        }
    }

    @Test
    void clocksThatGoBackOverMidnight() {
        // In St John's on 7 November 2010 the clocks went from 00:01 back to 23:01.
        final ZoneId zone = ZoneId.of("America/St_Johns");
        // A day after the start has been, though the end is on the same day.
        assertThat(PeriodBetween.between(
                Instant.parse("2010-11-06T02:30:30Z").atZone(zone), Instant.parse("2010-11-07T02:40:30Z").atZone(zone)))
                .isEqualTo(Period.ofDays(1));
        assertThat(PeriodBetween.between(
                Instant.parse("2010-11-07T02:30:30Z").atZone(zone), Instant.parse("2010-11-07T03:01:00Z").atZone(zone)))
                .isEqualTo(Period.ZERO);
    }

    @Test
    void monthsThatGoBackToATimeThatIsSkipped() throws ParseException {
        // Two months before is 01:30 on 31 March, which doesn't happen.
        assertThat(eval("periodBetween(2024-05-31T00:30:00Z, 2024-03-30T02:00:00Z)"))
                .isEqualTo(ValPeriod.create(0, -2, 0));
        assertThat(eval("periodBetween(2024-05-31T00:30:00Z, 2024-03-30T01:30:00Z)"))
                .isEqualTo(ValPeriod.create(0, -2, -1));
    }

    @Test
    void zones() throws ParseException {
        final String dates = "2024-10-26T23:00:00Z, 2024-10-27T23:30:00Z";
        assertThat(eval("periodBetween(" + dates + ")")).isEqualTo(ValPeriod.create(0, 0, 0));
        assertThat(eval("periodBetween(" + dates + ", 'UTC')")).isEqualTo(ValPeriod.create(0, 0, 1));
        assertThat(eval("periodBetween(" + dates + ", ${field})", ValString.create("UTC")))
                .isEqualTo(ValPeriod.create(0, 0, 1));
    }

    // In a query whose time zone is Europe/London.
    private static Val eval(final String expression, final Val field) throws ParseException {
        return DateFunctionTestUtil.eval(DateFunctionTestUtil.LONDON, expression, field);
    }

    private static Val eval(final String expression) throws ParseException {
        return eval(expression, ValNull.INSTANCE);
    }
}
