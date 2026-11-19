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

import stroom.util.shared.time.SimpleDuration;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The window maths of the StroomQL `window` clause.
class TestTimeWindows {

    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    // Midnight is skipped here when the clocks go forward.
    private static final ZoneId BEIRUT = ZoneId.of("Asia/Beirut");

    @Test
    void tumblingWindowHoldsATimeInOneWindow() {
        final TimeWindows windows = windows("1h", null, null, UTC);

        assertThat(windows.getWindowsPerTime()).isEqualTo(1);
        assertThat(starts(windows, "2026-03-10T10:25:00Z")).containsExactly("2026-03-10T10:00:00Z");
    }

    @Test
    void windowIncludesItsStartAndExcludesItsEnd() {
        final TimeWindows windows = windows("1h", null, null, UTC);

        assertThat(starts(windows, "2026-03-10T10:00:00Z")).containsExactly("2026-03-10T10:00:00Z");
        assertThat(starts(windows, "2026-03-10T10:59:59.999Z")).containsExactly("2026-03-10T10:00:00Z");
        assertThat(starts(windows, "2026-03-10T11:00:00Z")).containsExactly("2026-03-10T11:00:00Z");
    }

    @Test
    void fixedSizeIsAMultipleOfItsWholeSizeNotOfTheNextUnitUp() {
        // The old window code restarted 45 minute windows every hour, giving one of 15 minutes.
        final TimeWindows windows = windows("45m", null, null, UTC);

        final String first = starts(windows, "2026-03-10T10:00:00Z").getFirst();
        final String second = starts(windows, "2026-03-10T10:50:00Z").getFirst();

        assertThat(Duration.between(Instant.parse(first), Instant.parse(end(windows, first))))
                .isEqualTo(Duration.ofMinutes(45));
        assertThat(Duration.between(Instant.parse(second), Instant.parse(end(windows, second))))
                .isEqualTo(Duration.ofMinutes(45));
        assertThat(Instant.parse(first).toEpochMilli() % Duration.ofMinutes(45).toMillis()).isZero();
    }

    @Test
    void hoppingWindowHoldsATimeInEveryWindowThatCoversIt() {
        final TimeWindows windows = windows("1h", "30m", null, UTC);

        assertThat(windows.getWindowsPerTime()).isEqualTo(2);
        assertThat(starts(windows, "2026-03-10T10:40:00Z"))
                .containsExactly("2026-03-10T10:00:00Z", "2026-03-10T10:30:00Z");
        assertThat(starts(windows, "2026-03-10T10:10:00Z"))
                .containsExactly("2026-03-10T09:30:00Z", "2026-03-10T10:00:00Z");
    }

    @Test
    void latestEndedWindowIsTheOneBeforeTheWindowOfATime() {
        final TimeWindows windows = windows("1h", null, null, UTC);

        assertThat(ended(windows, "2026-03-10T10:25:00Z")).isEqualTo("2026-03-10T09:00:00Z");
        // A window that starts at the time has not ended, and nor has one that ends a moment later.
        assertThat(ended(windows, "2026-03-10T10:00:00Z")).isEqualTo("2026-03-10T09:00:00Z");
        assertThat(ended(windows, "2026-03-10T09:59:59.999Z")).isEqualTo("2026-03-10T08:00:00Z");
    }

    @Test
    void latestEndedHoppingWindowIsBeforeEveryWindowOfATime() {
        final TimeWindows windows = windows("1h", "15m", null, UTC);

        // The windows from 09:30 to 10:15 cover 10:25, so the latest to have ended is from 09:15.
        assertThat(ended(windows, "2026-03-10T10:25:00Z")).isEqualTo("2026-03-10T09:15:00Z");
    }

    @Test
    void latestEndedCalendarWindow() {
        assertThat(ended(windows("1M", null, null, UTC), "2026-03-10T10:25:00Z"))
                .isEqualTo("2026-02-01T00:00:00Z");
        // The day before the clocks go forward in London is a whole local day.
        assertThat(ended(windows("1d", null, null, LONDON), "2026-03-29T12:00:00Z"))
                .isEqualTo("2026-03-28T00:00:00Z");
    }

    @Test
    void hoppingWindowCountsWhatADesignExampleExpects() {
        // Events at 10:10, 10:40 and 11:20 with hour windows every 30 minutes: 10:00 holds two,
        // 10:30 holds two, 11:00 holds one.
        final TimeWindows windows = windows("1h", "30m", null, UTC);
        final List<String> all = new ArrayList<>();
        all.addAll(starts(windows, "2026-03-10T10:10:00Z"));
        all.addAll(starts(windows, "2026-03-10T10:40:00Z"));
        all.addAll(starts(windows, "2026-03-10T11:20:00Z"));

        assertThat(all).containsExactlyInAnyOrder(
                "2026-03-10T09:30:00Z",
                "2026-03-10T10:00:00Z",
                "2026-03-10T10:00:00Z",
                "2026-03-10T10:30:00Z",
                "2026-03-10T10:30:00Z",
                "2026-03-10T11:00:00Z");
    }

    @Test
    void everyWindowGivenCoversTheTime() {
        final TimeWindows windows = windows("1d", "1h", "20m", LONDON);
        final String time = "2026-06-15T13:47:11.123Z";
        final long timeMs = Instant.parse(time).toEpochMilli();
        final List<Long> keys = keys(windows, time);

        assertThat(keys).hasSize(24).isSorted().doesNotHaveDuplicates();
        assertThat(keys).allSatisfy(key -> {
            assertThat(windows.getWindowStart(key)).isLessThanOrEqualTo(timeMs);
            assertThat(windows.getWindowEnd(key)).isGreaterThan(timeMs);
        });
    }

    @Test
    void positiveOffsetMovesStartsLater() {
        final TimeWindows windows = windows("1d", null, "6h", UTC);

        assertThat(starts(windows, "2026-03-10T05:59:59Z")).containsExactly("2026-03-09T06:00:00Z");
        assertThat(starts(windows, "2026-03-10T06:00:00Z")).containsExactly("2026-03-10T06:00:00Z");
        assertThat(end(windows, "2026-03-10T06:00:00Z")).isEqualTo("2026-03-11T06:00:00Z");
    }

    @Test
    void negativeOffsetMovesStartsEarlier() {
        final TimeWindows windows = windows("1d", null, "-2h", UTC);

        assertThat(starts(windows, "2026-03-10T21:00:00Z")).containsExactly("2026-03-09T22:00:00Z");
        assertThat(starts(windows, "2026-03-10T22:00:00Z")).containsExactly("2026-03-10T22:00:00Z");
    }

    @Test
    void negativeMonthOffsetMovesAYearEarlier() {
        final TimeWindows windows = windows("1y", null, "-3M", UTC);

        assertThat(starts(windows, "2026-09-30T23:59:59Z")).containsExactly("2025-10-01T00:00:00Z");
        assertThat(starts(windows, "2026-10-01T00:00:00Z")).containsExactly("2026-10-01T00:00:00Z");
    }

    @Test
    void offsetMovesFixedWindowsToo() {
        final TimeWindows windows = windows("1h", null, "15m", UTC);

        assertThat(starts(windows, "2026-03-10T10:10:00Z")).containsExactly("2026-03-10T09:15:00Z");
    }

    @Test
    void dayWindowBreaksAtLocalMidnight() {
        final TimeWindows windows = windows("1d", null, null, KOLKATA);

        // Midnight in Kolkata is 18:30 UTC the day before.
        assertThat(starts(windows, "2026-03-10T18:29:59Z")).containsExactly("2026-03-09T18:30:00Z");
        assertThat(starts(windows, "2026-03-10T18:30:00Z")).containsExactly("2026-03-10T18:30:00Z");
    }

    @Test
    void hourWindowBreaksOnTheLocalHourInAHalfHourZone() {
        final TimeWindows windows = windows("1h", null, null, KOLKATA);

        // 10:25 UTC is 15:55 in Kolkata, and 15:00 there is 09:30 UTC.
        assertThat(starts(windows, "2026-03-10T10:25:00Z")).containsExactly("2026-03-10T09:30:00Z");
        assertThat(end(windows, "2026-03-10T10:25:00Z")).isEqualTo("2026-03-10T10:30:00Z");
    }

    @Test
    void twentyFourHoursFollowsStandardTimeAllYear() {
        final TimeWindows windows = windows("24h", null, null, NEW_YORK);

        // Midnight Eastern Standard Time is 05:00 UTC, which in summer is 01:00 on the wall clock.
        assertThat(starts(windows, "2026-01-15T12:00:00Z")).containsExactly("2026-01-15T05:00:00Z");
        assertThat(starts(windows, "2026-07-15T12:00:00Z")).containsExactly("2026-07-15T05:00:00Z");
        assertThat(end(windows, "2026-03-08T12:00:00Z")).isEqualTo("2026-03-09T05:00:00Z");
    }

    @Test
    void fixedOffsetZoneCountsFromItsOwnMidnight() {
        final TimeWindows windows = windows("6h", null, null, ZoneOffset.ofHoursMinutes(5, 45));

        // 06:00 at +05:45 is 00:15 UTC.
        assertThat(starts(windows, "2026-03-10T01:00:00Z")).containsExactly("2026-03-10T00:15:00Z");
    }

    @Test
    void dayIs23HoursWhenTheClocksGoForward() {
        final TimeWindows windows = windows("1d", null, null, LONDON);

        // Clocks went forward at 01:00 UTC on 29 March 2026.
        final String start = starts(windows, "2026-03-29T12:00:00Z").getFirst();
        assertThat(start).isEqualTo("2026-03-29T00:00:00Z");
        assertThat(end(windows, start)).isEqualTo("2026-03-29T23:00:00Z");
    }

    @Test
    void dayIs25HoursWhenTheClocksGoBack() {
        final TimeWindows windows = windows("1d", null, null, LONDON);

        // Clocks went back at 01:00 UTC on 25 October 2026.
        final String start = starts(windows, "2026-10-25T12:00:00Z").getFirst();
        assertThat(start).isEqualTo("2026-10-24T23:00:00Z");
        assertThat(end(windows, start)).isEqualTo("2026-10-26T00:00:00Z");
        // Both passes through the repeated hour are in the same day.
        assertThat(starts(windows, "2026-10-25T00:30:00Z")).containsExactly(start);
        assertThat(starts(windows, "2026-10-25T01:30:00Z")).containsExactly(start);
    }

    @Test
    void twentyFourHoursIsAlways24HoursAndDriftsOffLocalMidnight() {
        final TimeWindows windows = windows("24h", null, null, LONDON);

        final String start = starts(windows, "2026-03-29T12:00:00Z").getFirst();
        assertThat(start).isEqualTo("2026-03-29T00:00:00Z");
        assertThat(end(windows, start)).isEqualTo("2026-03-30T00:00:00Z");
    }

    @Test
    void dayThatSkipsMidnightStartsWhenTheClocksResume() {
        final TimeWindows windows = windows("1d", null, null, BEIRUT);

        // In Beirut 00:00 on 29 March 2026 did not happen: the clocks went from 23:59:59 to 01:00.
        final String start = starts(windows, "2026-03-29T10:00:00Z").getFirst();
        assertThat(start).isEqualTo("2026-03-28T22:00:00Z");
        assertThat(starts(windows, "2026-03-28T21:59:59Z")).containsExactly("2026-03-27T22:00:00Z");
        assertThat(end(windows, start)).isEqualTo("2026-03-29T21:00:00Z");
        assertThat(shifted(windows, start, -1)).isEqualTo("2026-03-27T22:00:00Z");
    }

    @Test
    void windowsThatWouldStartInASkippedHourStayTwoWindows() {
        final TimeWindows windows = windows("1d", "1h", null, LONDON);
        final List<Long> keys = keys(windows, "2026-03-29T12:00:00Z");

        // The windows of local 01:00 and 02:00 both start at 01:00 UTC, but are still two windows.
        assertThat(keys).hasSize(24).isSorted().doesNotHaveDuplicates();
        assertThat(starts(windows, "2026-03-29T12:00:00Z").stream().distinct()).hasSize(23);
        assertThat(keys).allSatisfy(key -> {
            assertThat(windows.shift(windows.shift(key, -1), 1)).isEqualTo(key);
            assertThat(windows.shift(windows.shift(key, 1), -1)).isEqualTo(key);
        });
    }

    @Test
    void advanceSmallerThanASkippedHourGivesEachWindowOnce() {
        final TimeWindows windows = windows("1d", "30m", null, LONDON);
        final List<Long> keys = keys(windows, "2026-03-29T12:00:00Z");

        // Local 01:00 and 01:30 are skipped, so the windows of those times start when the clocks
        // resume, with the window of 02:00. Each is still given once, so a time is counted once in
        // each window.
        assertThat(keys).hasSize(48).isSorted().doesNotHaveDuplicates();
        assertThat(starts(windows, "2026-03-29T12:00:00Z").stream().distinct()).hasSize(46);
    }

    @Test
    void windowThatWouldStartInASkippedHourStartsWhenTheClocksResume() {
        final TimeWindows windows = windows("1d", null, "90m", LONDON);

        // The window of local 01:30 on 29 March starts at 02:00 British Summer Time, which is
        // 01:00Z, so a time just after the clocks resume is not before the start of its window.
        assertThat(starts(windows, "2026-03-29T01:10:00Z")).containsExactly("2026-03-29T01:00:00Z");
        assertThat(starts(windows, "2026-03-29T00:59:59Z")).containsExactly("2026-03-28T01:30:00Z");
    }

    @Test
    void shiftedWindowsOnTheDayBeforeASkippedHourAreAllReachable() {
        final TimeWindows windows = windows("1d", "1h", null, LONDON);

        // The windows that start at local 01:00 and 02:00 on 28 March are the day before the two that
        // share a start on 29 March.
        final List<String> dayBefore = keys(windows, "2026-03-29T12:00:00Z")
                .stream()
                .map(key -> format(windows.getWindowStart(windows.shift(key, -1))))
                .toList();
        assertThat(dayBefore).contains("2026-03-28T01:00:00Z", "2026-03-28T02:00:00Z").doesNotHaveDuplicates();
    }

    @Test
    void timeInARepeatedHourIsPutInWindowsByItsWallClockTime() {
        final TimeWindows windows = windows("1d", "30m", null, LONDON);

        // 00:10 and 01:10 UTC on 25 October 2026 are both 01:10 on the wall clock.
        assertThat(keys(windows, "2026-10-25T00:10:00Z")).isEqualTo(keys(windows, "2026-10-25T01:10:00Z"));
        // A boundary at a whole hour is not inside the repeated hour, so each window still ends
        // after the times in it.
        final TimeWindows hourly = windows("1d", "1h", null, LONDON);
        final long secondPass = Instant.parse("2026-10-25T01:10:00Z").toEpochMilli();
        assertThat(keys(hourly, "2026-10-25T01:10:00Z")).allSatisfy(key -> {
            assertThat(hourly.getWindowStart(key)).isLessThanOrEqualTo(secondPass);
            assertThat(hourly.getWindowEnd(key)).isGreaterThan(secondPass);
        });
    }

    @Test
    void weekStartsOnMonday() {
        final TimeWindows windows = windows("1w", null, null, UTC);

        // 10 March 2026 is a Tuesday.
        assertThat(starts(windows, "2026-03-10T10:00:00Z")).containsExactly("2026-03-09T00:00:00Z");
        assertThat(starts(windows, "2026-03-15T23:59:59Z")).containsExactly("2026-03-09T00:00:00Z");
        assertThat(starts(windows, "2026-03-16T00:00:00Z")).containsExactly("2026-03-16T00:00:00Z");
    }

    @Test
    void weekOffsetMovesTheFirstDay() {
        final TimeWindows windows = windows("1w", null, "-1d", UTC);

        // Weeks now start on Sunday.
        assertThat(starts(windows, "2026-03-10T10:00:00Z")).containsExactly("2026-03-08T00:00:00Z");
    }

    @Test
    void sevenDayWindowIsNotCutShortAtTheEndOfAMonth() {
        // The old window code restarted 7 day windows on the first of each month.
        final TimeWindows windows = windows("7d", null, null, UTC);

        final String start = starts(windows, "2026-01-31T12:00:00Z").getFirst();
        assertThat(Duration.between(Instant.parse(start), Instant.parse(end(windows, start))))
                .isEqualTo(Duration.ofDays(7));
        assertThat(starts(windows, "2026-02-01T12:00:00Z")).containsExactly(start);
    }

    @Test
    void rollingSevenDaysEveryDay() {
        final TimeWindows windows = windows("7d", "1d", null, UTC);

        assertThat(windows.getWindowsPerTime()).isEqualTo(7);
        assertThat(starts(windows, "2026-03-10T10:00:00Z")).containsExactly(
                "2026-03-04T00:00:00Z",
                "2026-03-05T00:00:00Z",
                "2026-03-06T00:00:00Z",
                "2026-03-07T00:00:00Z",
                "2026-03-08T00:00:00Z",
                "2026-03-09T00:00:00Z",
                "2026-03-10T00:00:00Z");
    }

    @Test
    void monthWindowFollowsTheCalendar() {
        final TimeWindows windows = windows("1M", null, null, UTC);

        assertThat(starts(windows, "2026-02-28T23:59:59Z")).containsExactly("2026-02-01T00:00:00Z");
        assertThat(end(windows, "2026-02-01T00:00:00Z")).isEqualTo("2026-03-01T00:00:00Z");
        assertThat(starts(windows, "2026-03-01T00:00:00Z")).containsExactly("2026-03-01T00:00:00Z");
    }

    @Test
    void monthWindowStartsAtLocalMidnight() {
        final TimeWindows windows = windows("1M", null, null, LONDON);

        // British Summer Time: the first of July starts at 23:00 UTC on 30 June.
        assertThat(starts(windows, "2026-07-10T10:00:00Z")).containsExactly("2026-06-30T23:00:00Z");
    }

    @Test
    void rollingQuarterEveryMonth() {
        final TimeWindows windows = windows("3M", "1M", null, UTC);

        assertThat(windows.getWindowsPerTime()).isEqualTo(3);
        assertThat(starts(windows, "2026-03-10T10:00:00Z")).containsExactly(
                "2026-01-01T00:00:00Z",
                "2026-02-01T00:00:00Z",
                "2026-03-01T00:00:00Z");
        assertThat(end(windows, "2026-01-01T00:00:00Z")).isEqualTo("2026-04-01T00:00:00Z");
    }

    @Test
    void yearWindowWithAMonthOffsetIsAFinancialYear() {
        final TimeWindows windows = windows("1y", null, "3M", UTC);

        assertThat(starts(windows, "2026-03-31T23:59:59Z")).containsExactly("2025-04-01T00:00:00Z");
        assertThat(starts(windows, "2026-04-01T00:00:00Z")).containsExactly("2026-04-01T00:00:00Z");
        assertThat(end(windows, "2026-04-01T00:00:00Z")).isEqualTo("2027-04-01T00:00:00Z");
    }

    @Test
    void timesBefore1970GoInTheirOwnWindows() {
        // The old window code put every time before 1970 in the 1970 year window.
        assertThat(starts(windows("1y", null, null, UTC), "1965-06-15T10:00:00Z"))
                .containsExactly("1965-01-01T00:00:00Z");
        assertThat(starts(windows("1d", null, null, UTC), "1969-12-31T23:59:59.999Z"))
                .containsExactly("1969-12-31T00:00:00Z");
        assertThat(starts(windows("1h", null, null, UTC), "1969-12-31T23:59:59.999Z"))
                .containsExactly("1969-12-31T23:00:00Z");
        assertThat(starts(windows("1w", null, null, UTC), "1969-12-31T10:00:00Z"))
                .containsExactly("1969-12-29T00:00:00Z");
    }

    @Test
    void shiftMovesByWholeWindowSizes() {
        final TimeWindows days = windows("1d", null, null, UTC);
        assertThat(shifted(days, "2026-03-10T00:00:00Z", -1)).isEqualTo("2026-03-09T00:00:00Z");
        assertThat(shifted(days, "2026-03-10T00:00:00Z", 7)).isEqualTo("2026-03-17T00:00:00Z");
        assertThat(shifted(days, "2026-03-10T00:00:00Z", 0)).isEqualTo("2026-03-10T00:00:00Z");

        // A hopping window moves by its size, not by its slide.
        final TimeWindows hopping = windows("1h", "10m", null, UTC);
        assertThat(shifted(hopping, "2026-03-10T10:20:00Z", -1)).isEqualTo("2026-03-10T09:20:00Z");

        final TimeWindows months = windows("1M", null, null, UTC);
        assertThat(shifted(months, "2026-03-01T00:00:00Z", -1)).isEqualTo("2026-02-01T00:00:00Z");
        assertThat(shifted(months, "2026-03-01T00:00:00Z", -14)).isEqualTo("2025-01-01T00:00:00Z");

        final TimeWindows financialYears = windows("1y", null, "3M", UTC);
        assertThat(shifted(financialYears, "2026-05-01T00:00:00Z", -1)).isEqualTo("2025-04-01T00:00:00Z");
    }

    @Test
    void shiftAcrossAClockChangeKeepsLocalMidnight() {
        final TimeWindows windows = windows("1d", null, null, LONDON);

        assertThat(shifted(windows, "2026-03-28T00:00:00Z", 2)).isEqualTo("2026-03-29T23:00:00Z");
        final long key = key(windows, "2026-03-28T00:00:00Z");
        assertThat(windows.shift(windows.shift(key, 2), -2)).isEqualTo(key);
    }

    @Test
    void keysAreInTheOrderOfTheirWindows() {
        final TimeWindows months = windows("1M", null, null, LONDON);
        assertThat(key(months, "1969-11-15T00:00:00Z"))
                .isLessThan(key(months, "1970-01-15T00:00:00Z"));
        assertThat(key(months, "1970-01-15T00:00:00Z"))
                .isLessThan(key(months, "2026-03-15T00:00:00Z"));

        final TimeWindows days = windows("1d", null, null, LONDON);
        assertThat(key(days, "2026-10-24T12:00:00Z")).isLessThan(key(days, "2026-10-25T12:00:00Z"));
    }

    @Test
    void monthsThatDoNotDivideAYearAreCountedFrom1970() {
        final TimeWindows windows = windows("5M", null, null, UTC);

        assertThat(starts(windows, "1970-03-15T00:00:00Z")).containsExactly("1970-01-01T00:00:00Z");
        assertThat(starts(windows, "1970-07-15T00:00:00Z")).containsExactly("1970-06-01T00:00:00Z");
        assertThat(starts(windows, "1969-12-15T00:00:00Z")).containsExactly("1969-08-01T00:00:00Z");
    }

    @Test
    void sizesTooLargeToWorkWithAreRefusedInWords() {
        assertThatThrownBy(() -> windows("30d", "1ms", null, UTC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("advance of a window is too small for its size");
        assertThatThrownBy(() -> windows("9000000000000000000h", null, null, UTC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too large");
        assertThatThrownBy(() -> windows("9000000000000000000y", null, null, UTC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void zeroOrNegativeSizesAreRefused() {
        assertThatThrownBy(() -> windows("0m", null, null, UTC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("window size must be more than zero");
        // The old window code never finished working out the windows of a zero advance.
        assertThatThrownBy(() -> windows("1d", "0m", null, UTC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("advance must be more than zero");
    }

    @Test
    void sizeMustBeAWholeMultipleOfTheAdvance() {
        assertThatThrownBy(() -> windows("1h", "25m", null, UTC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1h is not a multiple of 25m");
        assertThatThrownBy(() -> windows("1d", "7h", null, UTC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1d is not a multiple of 7h");
        assertThatThrownBy(() -> windows("1h", "2h", null, UTC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2h is more than 1h");
    }

    @Test
    void monthsAndYearsCantAdvanceByDaysOrLess() {
        assertThatThrownBy(() -> windows("1M", "1d", null, UTC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be in months or years");
        assertThatThrownBy(() -> windows("1y", "1w", null, UTC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be in months or years");
        assertThatThrownBy(() -> windows("1y", "1h", null, UTC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be in months or years");
        assertThat(windows("2y", "1y", null, UTC).getWindowsPerTime()).isEqualTo(2);
    }

    @Test
    void rollingYearEveryMonth() {
        final TimeWindows windows = windows("1y", "1M", null, UTC);

        assertThat(windows.getWindowsPerTime()).isEqualTo(12);
        final List<String> starts = starts(windows, "2026-03-10T10:00:00Z");
        assertThat(starts).hasSize(12).startsWith("2025-04-01T00:00:00Z").endsWith("2026-03-01T00:00:00Z");
        assertThat(end(windows, "2026-03-10T10:00:00Z")).isEqualTo("2027-03-01T00:00:00Z");
        assertThat(shifted(windows, "2026-03-10T10:00:00Z", -1)).isEqualTo("2025-03-01T00:00:00Z");
    }

    @Test
    void rollingWeekEveryDayKeepsMondayAsAStart() {
        final TimeWindows windows = windows("1w", "1d", null, UTC);

        assertThat(windows.getWindowsPerTime()).isEqualTo(7);
        // 10 March 2026 is a Tuesday, so the week from Monday 9 March is one of its windows.
        assertThat(starts(windows, "2026-03-10T10:00:00Z"))
                .hasSize(7)
                .startsWith("2026-03-04T00:00:00Z")
                .contains("2026-03-09T00:00:00Z")
                .endsWith("2026-03-10T00:00:00Z");
        assertThat(windows("2w", "1w", null, UTC).getWindowsPerTime()).isEqualTo(2);
        assertThat(windows("1w", "12h", null, UTC).getWindowsPerTime()).isEqualTo(14);
    }

    @Test
    void smallerSizesTakeAnAdvanceInTheirOwnOrASmallerUnit() {
        assertThat(windows("1d", "1h", null, UTC).getWindowsPerTime()).isEqualTo(24);
        assertThat(windows("1h", "1s", null, UTC).getWindowsPerTime()).isEqualTo(3600);
        assertThatThrownBy(() -> windows("14d", "1w", null, UTC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be in days or a smaller unit");
        assertThatThrownBy(() -> windows("48h", "1d", null, UTC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be in hours or a smaller unit");
    }

    @Test
    void offsetUnitMustSuitTheSize() {
        assertThatThrownBy(() -> windows("1h", null, "1d", UTC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("offset of a window of 1h must be in hours or a smaller unit");
        assertThatThrownBy(() -> windows("1d", null, "1M", UTC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("offset of a window of 1d must be in weeks or a smaller unit");
    }

    @Test
    void nanosecondsAreRefused() {
        assertThatThrownBy(() -> windows("100ns", null, null, UTC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nanoseconds");
        assertThatThrownBy(() -> windows("1s", "100ns", null, UTC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nanoseconds");
    }

    private static TimeWindows windows(final String size,
                                       final String advance,
                                       final String offset,
                                       final ZoneId zoneId) {
        final boolean offsetEarlier = offset != null && offset.startsWith("-");
        final String unsignedOffset = offsetEarlier
                ? offset.substring(1)
                : offset;
        return TimeWindows.create(duration(size), duration(advance), duration(unsignedOffset), offsetEarlier, zoneId);
    }

    private static SimpleDuration duration(final String string) {
        return string == null
                ? null
                : SimpleDuration.parse(string);
    }

    private static List<Long> keys(final TimeWindows windows, final String time) {
        final List<Long> keys = new ArrayList<>();
        windows.forEachWindow(Instant.parse(time).toEpochMilli(), keys::add);
        return keys;
    }

    // The key of the latest window that covers a time, which for a window's own start is that window.
    private static long key(final TimeWindows windows, final String time) {
        return keys(windows, time).getLast();
    }

    private static List<String> starts(final TimeWindows windows, final String time) {
        return keys(windows, time)
                .stream()
                .map(key -> format(windows.getWindowStart(key)))
                .toList();
    }

    private static String end(final TimeWindows windows, final String time) {
        return format(windows.getWindowEnd(key(windows, time)));
    }

    private static String shifted(final TimeWindows windows, final String time, final int count) {
        return format(windows.getWindowStart(windows.shift(key(windows, time), count)));
    }

    private static String ended(final TimeWindows windows, final String time) {
        return format(windows.getWindowStart(windows.getLatestEndedWindow(Instant.parse(time).toEpochMilli())));
    }

    private static String format(final long timeMs) {
        return Instant.ofEpochMilli(timeMs).toString();
    }
}
