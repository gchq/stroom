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

import stroom.query.language.functions.TimeRounder.Mode;
import stroom.query.language.functions.TimeRounder.Origin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestTimeRounder {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");

    // Each has a time, what to round to, then its floor, ceiling and nearest.
    @ParameterizedTest
    @CsvSource({
            // Weeks start on a Monday, and so do 7 days.
            "2024-05-15T10:00:00,P1W,2024-05-13T00:00:00,2024-05-20T00:00:00,2024-05-13T00:00:00",
            "2024-05-15T10:00:00,P7D,2024-05-13T00:00:00,2024-05-20T00:00:00,2024-05-13T00:00:00",
            "2024-05-15T10:00:00,P2W,2024-05-13T00:00:00,2024-05-27T00:00:00,2024-05-13T00:00:00",
            "1965-03-10T10:00:00,P1W,1965-03-08T00:00:00,1965-03-15T00:00:00,1965-03-08T00:00:00",
            "2024-05-15T10:00:00,P1D,2024-05-15T00:00:00,2024-05-16T00:00:00,2024-05-15T00:00:00",
            "1970-01-04T10:00:00,P10D,1970-01-01T00:00:00,1970-01-11T00:00:00,1970-01-01T00:00:00",
            "1969-12-31T10:00:00,P10D,1969-12-22T00:00:00,1970-01-01T00:00:00,1970-01-01T00:00:00",
            // Quarters, and the middles of months and years.
            "2024-05-15T10:00:00,P3M,2024-04-01T00:00:00,2024-07-01T00:00:00,2024-04-01T00:00:00",
            "1965-03-10T10:00:00,P3M,1965-01-01T00:00:00,1965-04-01T00:00:00,1965-04-01T00:00:00",
            "2024-02-15T12:00:00,P1M,2024-02-01T00:00:00,2024-03-01T00:00:00,2024-03-01T00:00:00",
            "2024-02-15T11:59:59,P1M,2024-02-01T00:00:00,2024-03-01T00:00:00,2024-02-01T00:00:00",
            "2025-01-15T12:00:00,P1M,2025-01-01T00:00:00,2025-02-01T00:00:00,2025-01-01T00:00:00",
            "2025-01-16T12:00:00,P1M,2025-01-01T00:00:00,2025-02-01T00:00:00,2025-02-01T00:00:00",
            "2025-07-02T12:00:00,P1Y,2025-01-01T00:00:00,2026-01-01T00:00:00,2026-01-01T00:00:00",
            "2024-05-15T10:00:00,P10Y,2020-01-01T00:00:00,2030-01-01T00:00:00,2020-01-01T00:00:00",
            // On a multiple.
            "2024-04-01T00:00:00,P3M,2024-04-01T00:00:00,2024-04-01T00:00:00,2024-04-01T00:00:00",
            "2024-05-13T00:00:00,P1W,2024-05-13T00:00:00,2024-05-13T00:00:00,2024-05-13T00:00:00",
            // Durations.
            "2024-05-15T10:07:30,PT15M,2024-05-15T10:00:00,2024-05-15T10:15:00,2024-05-15T10:15:00",
            "2024-05-15T10:07:29,PT15M,2024-05-15T10:00:00,2024-05-15T10:15:00,2024-05-15T10:00:00",
            "2024-05-15T10:00:00,PT168H,2024-05-09T00:00:00,2024-05-16T00:00:00,2024-05-16T00:00:00"})
    void times(final LocalDateTime time,
               final String multiple,
               final LocalDateTime floor,
               final LocalDateTime ceiling,
               final LocalDateTime nearest) {
        assertThat(round(Mode.FLOOR, time, multiple, null)).isEqualTo(floor);
        assertThat(round(Mode.CEILING, time, multiple, null)).isEqualTo(ceiling);
        assertThat(round(Mode.ROUND, time, multiple, null)).isEqualTo(nearest);
    }

    @ParameterizedTest
    @CsvSource({
            // A financial year, weeks from a Sunday, weeks of a fixed length from a Monday and days from 06:00.
            "2024-03-15T10:00:00,P1Y,2020-04-01T00:00:00,2023-04-01T00:00:00,2024-04-01T00:00:00",
            "2024-05-15T10:00:00,P1W,2024-01-07T00:00:00,2024-05-12T00:00:00,2024-05-19T00:00:00",
            "2024-05-15T10:00:00,PT168H,2024-01-01T00:00:00,2024-05-13T00:00:00,2024-05-20T00:00:00",
            "2024-05-15T05:00:00,P1D,2024-01-01T06:00:00,2024-05-14T06:00:00,2024-05-15T06:00:00",
            "2024-05-15T10:20:00,PT1H,2024-01-01T00:15:00,2024-05-15T10:15:00,2024-05-15T11:15:00",
            // The origin can be after the time.
            "2024-03-15T10:00:00,P1Y,2030-04-01T00:00:00,2023-04-01T00:00:00,2024-04-01T00:00:00",
            "2024-03-15T10:00:00,P1M,2024-06-10T00:00:00,2024-03-10T00:00:00,2024-04-10T00:00:00",
            // A month too short for the day of the origin.
            "2024-02-29T00:00:00,P1M,2024-01-31T00:00:00,2024-02-29T00:00:00,2024-02-29T00:00:00",
            "2024-02-29T00:00:01,P1M,2024-01-31T00:00:00,2024-02-29T00:00:00,2024-03-31T00:00:00",
            "2024-02-28T23:00:00,P1M,2024-01-31T00:00:00,2024-01-31T00:00:00,2024-02-29T00:00:00",
            "2024-04-30T00:00:00,P1M,2024-01-31T00:00:00,2024-04-30T00:00:00,2024-04-30T00:00:00",
            "2023-11-30T12:00:00,P1M,2024-01-31T00:00:00,2023-11-30T00:00:00,2023-12-31T00:00:00"})
    void timesFromAnOrigin(final LocalDateTime time,
                           final String multiple,
                           final LocalDateTime origin,
                           final LocalDateTime floor,
                           final LocalDateTime ceiling) {
        assertThat(round(Mode.FLOOR, time, multiple, zone -> origin)).isEqualTo(floor);
        assertThat(round(Mode.CEILING, time, multiple, zone -> origin)).isEqualTo(ceiling);
    }

    @ParameterizedTest
    @CsvSource({
            // 27 October 2024 is 25 hours long, so its middle is 12.5 hours after midnight, at 11:30.
            "2024-10-27T11:29:59Z,P1D,2024-10-26T23:00:00Z",
            "2024-10-27T11:30:00Z,P1D,2024-10-28T00:00:00Z",
            // 31 March 2024 is 23 hours long, so its middle is 11.5 hours after midnight, at 12:30.
            "2024-03-31T11:29:59Z,P1D,2024-03-31T00:00:00Z",
            "2024-03-31T11:30:00Z,P1D,2024-03-31T23:00:00Z"})
    void nearestIsByTheExactTimeBetween(final Instant time, final String multiple, final Instant expected) {
        assertThat(create(Mode.ROUND, multiple, null).round(time.atZone(LONDON)).toInstant()).isEqualTo(expected);
    }

    // Each has a zone, a time and what to round to, then its floor, ceiling and nearest.
    @ParameterizedTest
    @CsvSource({
            // On 31 March 2024 the clocks in Beirut went from 00:00 to 01:00, so the day started at 01:00.
            "Asia/Beirut,2024-03-31T10:00:00Z,P1D,2024-03-30T22:00:00Z,2024-03-31T21:00:00Z,2024-03-31T21:00:00Z",
            "Asia/Beirut,2024-03-30T10:00:00Z,P1D,2024-03-29T22:00:00Z,2024-03-30T22:00:00Z,2024-03-30T22:00:00Z",
            "Asia/Beirut,2024-03-30T22:00:00Z,P1D,2024-03-30T22:00:00Z,2024-03-30T22:00:00Z,2024-03-30T22:00:00Z",
            "Asia/Beirut,2024-03-30T22:00:00Z,PT24H,2024-03-30T22:00:00Z,2024-03-30T22:00:00Z,2024-03-30T22:00:00Z",
            "Asia/Beirut,2024-03-30T22:00:00Z,PT1H,2024-03-30T22:00:00Z,2024-03-30T22:00:00Z,2024-03-30T22:00:00Z",
            // On 27 October 2024 they went from 00:00 back to 23:00 on the day before.
            "Asia/Beirut,2024-10-26T21:30:00Z,P1D,2024-10-25T21:00:00Z,2024-10-26T22:00:00Z,2024-10-26T22:00:00Z",
            // On 3 November 2024 the clocks in Havana went from 01:00 back to 00:00, so there were two midnights.
            "America/Havana,2024-11-03T04:30:00Z,P1D,2024-11-03T04:00:00Z,2024-11-04T05:00:00Z,2024-11-03T04:00:00Z",
            "America/Havana,2024-11-03T15:00:00Z,P1D,2024-11-03T04:00:00Z,2024-11-04T05:00:00Z,2024-11-03T04:00:00Z",
            "America/Havana,2024-11-03T05:00:00Z,P1D,2024-11-03T04:00:00Z,2024-11-04T05:00:00Z,2024-11-03T04:00:00Z",
            "America/Havana,2024-11-03T15:00:00Z,PT24H,2024-11-03T04:00:00Z,2024-11-04T05:00:00Z,2024-11-03T04:00:00Z",
            "America/Havana,2024-11-03T15:00:00Z,PT12H,2024-11-03T04:00:00Z,2024-11-03T17:00:00Z,2024-11-03T17:00:00Z",
            "America/Havana,2024-11-02T15:00:00Z,P1D,2024-11-02T04:00:00Z,2024-11-03T04:00:00Z,2024-11-02T04:00:00Z",
            "America/Havana,2024-11-03T15:00:00Z,P1M,2024-11-01T04:00:00Z,2024-12-01T05:00:00Z,2024-11-01T04:00:00Z",
            // On 27 October 2024 the clocks in London went from 02:00 back to 01:00.
            "Europe/London,2024-10-27T00:55:00Z,PT15M,2024-10-27T00:45:00Z,2024-10-27T01:00:00Z,2024-10-27T01:00:00Z",
            "Europe/London,2024-10-27T01:05:00Z,PT15M,2024-10-27T01:00:00Z,2024-10-27T01:15:00Z,2024-10-27T01:00:00Z",
            "Europe/London,2024-10-27T00:45:00Z,PT1H,2024-10-27T00:00:00Z,2024-10-27T01:00:00Z,2024-10-27T01:00:00Z",
            "Europe/London,2024-10-27T01:45:00Z,PT1H,2024-10-27T01:00:00Z,2024-10-27T02:00:00Z,2024-10-27T02:00:00Z",
            // 01:30 is only a multiple of 90 minutes the first time, as 90 minutes is longer than the change.
            "Europe/London,2024-10-27T01:05:00Z,PT90M,2024-10-27T00:30:00Z,2024-10-27T03:00:00Z,2024-10-27T00:30:00Z",
            "Europe/London,2024-10-27T01:30:00Z,PT90M,2024-10-27T00:30:00Z,2024-10-27T03:00:00Z,2024-10-27T00:30:00Z",
            "Europe/London,2024-10-27T01:50:00Z,PT2H,2024-10-26T23:00:00Z,2024-10-27T02:00:00Z,2024-10-27T02:00:00Z",
            // On 31 March 2024 they went from 01:00 to 02:00, so 01:30 is the moment of the change.
            "Europe/London,2024-03-31T00:50:00Z,PT90M,2024-03-31T00:00:00Z,2024-03-31T01:00:00Z,2024-03-31T01:00:00Z",
            "Europe/London,2024-03-31T01:10:00Z,PT90M,2024-03-31T01:00:00Z,2024-03-31T02:00:00Z,2024-03-31T01:00:00Z",
            "Europe/London,2024-03-31T01:10:00Z,PT3H,2024-03-31T00:00:00Z,2024-03-31T02:00:00Z,2024-03-31T02:00:00Z",
            // Samoa had no 30 December 2011.
            "Pacific/Apia,2011-12-31T00:00:00Z,P1D,2011-12-30T10:00:00Z,2011-12-31T10:00:00Z,2011-12-31T10:00:00Z",
            "Pacific/Apia,2011-12-30T00:00:00Z,P1D,2011-12-29T10:00:00Z,2011-12-30T10:00:00Z,2011-12-30T10:00:00Z"})
    void clockChanges(final ZoneId zone,
                      final Instant time,
                      final String multiple,
                      final Instant floor,
                      final Instant ceiling,
                      final Instant nearest) {
        final ZonedDateTime zoned = time.atZone(zone);
        assertThat(create(Mode.FLOOR, multiple, null).round(zoned).toInstant()).isEqualTo(floor);
        assertThat(create(Mode.CEILING, multiple, null).round(zoned).toInstant()).isEqualTo(ceiling);
        assertThat(create(Mode.ROUND, multiple, null).round(zoned).toInstant()).isEqualTo(nearest);
    }

    // Whatever the zone and multiple, floors and ceilings are multiples, are on the right side of the time, have no
    // multiple between them, and never go back as time goes on.
    @ParameterizedTest
    @CsvSource({
            "Europe/London,2024-10-25T00:00:00Z",
            "Europe/London,2024-03-29T00:00:00Z",
            "Asia/Beirut,2024-03-29T00:00:00Z",
            "Asia/Beirut,2024-10-25T00:00:00Z",
            "America/Havana,2024-11-01T00:00:00Z",
            "America/Havana,2024-03-08T00:00:00Z",
            "Australia/Lord_Howe,2024-04-05T00:00:00Z",
            "Australia/Lord_Howe,2024-10-04T00:00:00Z",
            "Pacific/Apia,2011-12-28T00:00:00Z",
            "Asia/Kolkata,2024-03-29T00:00:00Z"})
    void floorsAndCeilingsAgree(final ZoneId zone, final Instant from) {
        for (final String multiple : List.of(
                "PT7M", "PT15M", "PT30M", "PT45M", "PT1H", "PT90M", "PT2H", "PT5H", "PT12H", "PT24H", "PT36H",
                "P1D", "P1W", "P1M")) {
            final TimeRounder floorer = create(Mode.FLOOR, multiple, null);
            final TimeRounder ceiler = create(Mode.CEILING, multiple, null);
            Instant lastFloor = Instant.MIN;
            Instant lastCeiling = Instant.MIN;
            for (int i = 0; i < 4 * 24 * 12; i++) {
                final Instant time = from.plus(Duration.ofMinutes(i * 5L));
                final String what = multiple + " at " + time;
                final Instant floor = floorer.round(time.atZone(zone)).toInstant();
                final Instant ceiling = ceiler.round(time.atZone(zone)).toInstant();

                assertThat(floor).as(what).isBeforeOrEqualTo(time).isAfterOrEqualTo(lastFloor);
                assertThat(ceiling).as(what).isAfterOrEqualTo(time).isAfterOrEqualTo(lastCeiling);
                assertThat(floorer.round(floor.atZone(zone)).toInstant()).as(what).isEqualTo(floor);
                assertThat(ceiler.round(floor.atZone(zone)).toInstant()).as(what).isEqualTo(floor);
                assertThat(floorer.round(ceiling.atZone(zone)).toInstant()).as(what).isEqualTo(ceiling);
                assertThat(ceiler.round(ceiling.atZone(zone)).toInstant()).as(what).isEqualTo(ceiling);
                assertThat(floor.equals(time)).as(what).isEqualTo(ceiling.equals(time));
                if (!floor.equals(time)) {
                    assertThat(floorer.round(ceiling.minusMillis(1).atZone(zone)).toInstant())
                            .as(what)
                            .isEqualTo(floor);
                }
                lastFloor = floor;
                lastCeiling = ceiling;
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
            "2024-05-15T10:20:00Z,2024-05-15T10:00:00Z",
            "2024-05-15T10:20:00+02:00,2024-05-15T08:00:00Z",
            // In London, which is an hour ahead of UTC in May.
            "2024-05-15T10:20:00,2024-05-15T09:00:00Z",
            "1715768400000,2024-05-15T10:00:00Z"})
    void textDates(final String text, final Instant expected) {
        assertThat(create(Mode.FLOOR, "PT1H", null).round(ValString.create(text), LONDON))
                .isEqualTo(ValDate.create(expected.toEpochMilli()));
    }

    @ParameterizedTest
    @CsvSource({
            "100m,3600000",
            "PT100M,3600000",
            "1h40m,3600000",
            "1.5h,3600000"})
    void textDurations(final String text, final long expected) {
        assertThat(create(Mode.FLOOR, "PT1H", null).round(ValString.create(text), LONDON))
                .isEqualTo(ValDuration.create(expected));
    }

    // Each has a duration, what to round to, then its floor, ceiling and nearest, in milliseconds.
    @ParameterizedTest
    @CsvSource({
            "5400000,PT1H,3600000,7200000,7200000",
            "5399999,PT1H,3600000,7200000,3600000",
            "-5400000,PT1H,-7200000,-3600000,-3600000",
            "3600000,PT1H,3600000,3600000,3600000",
            "100000000,P1D,86400000,172800000,86400000",
            "0,PT1H,0,0,0"})
    void durations(final long millis,
                   final String multiple,
                   final long floor,
                   final long ceiling,
                   final long nearest) {
        final ValDuration duration = ValDuration.create(millis);
        assertThat(create(Mode.FLOOR, multiple, null).round(duration, LONDON)).isEqualTo(ValDuration.create(floor));
        assertThat(create(Mode.CEILING, multiple, null).round(duration, LONDON))
                .isEqualTo(ValDuration.create(ceiling));
        assertThat(create(Mode.ROUND, multiple, null).round(duration, LONDON))
                .isEqualTo(ValDuration.create(nearest));
    }

    @Test
    void valuesThatCantBeRounded() {
        assertThat(create(Mode.FLOOR, "P1M", null).round(ValDuration.create(1), LONDON).toString())
                .contains("Can't round a duration by a period of months or years");
        assertThat(create(Mode.FLOOR, "PT1H", zone -> LocalDateTime.MIN)
                .round(ValDuration.create(1), LONDON)
                .toString())
                .contains("An origin can only be used to round a date");
        assertThat(create(Mode.FLOOR, "PT1H", null).round(ValPeriod.create(0, 0, 1), LONDON).toString())
                .contains("Can't round a period");
        assertThat(create(Mode.FLOOR, "PT1H", null).round(ValString.create("foo"), LONDON).toString())
                .contains("'foo' is not a date or a duration");
        assertThat(create(Mode.FLOOR, "PT1H", null).round(ValString.create(" "), LONDON))
                .isEqualTo(ValNull.INSTANCE);
        assertThat(create(Mode.CEILING, "PT1H", null).round(ValDuration.create(Long.MAX_VALUE), LONDON).type())
                .isEqualTo(Type.ERR);
        assertThat(create(Mode.CEILING, "P1Y", null).round(ValDate.create(Long.MAX_VALUE), LONDON).type())
                .isEqualTo(Type.ERR);
    }

    @ParameterizedTest
    @CsvSource({
            "PT0S,at least one millisecond",
            "PT-1H,at least one millisecond",
            "P0D,must be more than zero",
            "P-1M,must be more than zero",
            "P1Y-1M,must be more than zero",
            "P1M2D,must only have one of years",
            "P1Y6M,must only have one of years"})
    void badMultiples(final String multiple, final String message) {
        assertThatThrownBy(() -> create(Mode.FLOOR, multiple, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(message);
    }

    private static LocalDateTime round(final Mode mode,
                                       final LocalDateTime time,
                                       final String multiple,
                                       final Origin origin) {
        return create(mode, multiple, origin).round(time.atZone(ZoneOffset.UTC)).toLocalDateTime();
    }

    private static TimeRounder create(final Mode mode, final String multiple, final Origin origin) {
        return multiple.contains("T")
                ? TimeRounder.ofDuration(mode, Duration.parse(multiple).toMillis(), origin)
                : TimeRounder.ofPeriod(mode, Period.parse(multiple), origin);
    }
}
