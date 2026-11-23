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

package stroom.query.common.v2;

import stroom.query.api.HoppingWindow;
import stroom.query.api.TimeFilter;
import stroom.query.language.functions.TimeWindows;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TestWholeWindowFilter {

    private static final HoppingWindow DAYS = new HoppingWindow("EventTime", "1d", null, null, "Z");
    private static final TimeWindows TIME_WINDOWS = TimeWindows.create(DAYS);

    @Test
    void windowInsideTheRangeIsJudged() {
        final WholeWindowFilter filter = filter("2026-03-10T00:00:00Z", "2026-03-13T00:00:00Z");

        assertThat(filter.isJudged(day("2026-03-09"))).isFalse();
        assertThat(filter.isJudged(day("2026-03-10"))).isTrue();
        assertThat(filter.isJudged(day("2026-03-12"))).isTrue();
        assertThat(filter.isJudged(day("2026-03-13"))).isFalse();
        assertThat(filter.getSearchFrom()).isEqualTo(time("2026-03-10T00:00:00Z"));
        assertThat(filter.searchesBeforeRange()).isFalse();
    }

    @Test
    void windowThatEndsInTheRangeIsJudgedAndSearchedFromItsStart() {
        final WholeWindowFilter filter = filter("2026-03-10T06:00:00Z", "2026-03-13T06:00:00Z");

        // The 10th ends in the range, so it is judged in this run, and the 13th in the next.
        assertThat(filter.isJudged(day("2026-03-10"))).isTrue();
        assertThat(filter.isJudged(day("2026-03-12"))).isTrue();
        assertThat(filter.isJudged(day("2026-03-13"))).isFalse();
        assertThat(filter.getSearchFrom()).isEqualTo(time("2026-03-10T00:00:00Z"));
    }

    @Test
    void runsWhoseRangesFollowOnJudgeEachWindowOnce() {
        final WholeWindowFilter first = filter("2026-03-10T06:00:00Z", "2026-03-11T06:00:00Z");
        final WholeWindowFilter second = filter("2026-03-11T06:00:00Z", "2026-03-12T06:00:00Z");

        assertThat(first.isJudged(day("2026-03-10"))).isTrue();
        assertThat(second.isJudged(day("2026-03-10"))).isFalse();
        assertThat(first.isJudged(day("2026-03-11"))).isFalse();
        assertThat(second.isJudged(day("2026-03-11"))).isTrue();
    }

    @Test
    void rowWithLagIsSearchedBackToTheWindowItReads() {
        final WholeWindowFilter filter = filter("2026-03-10T00:00:00Z", "2026-03-11T00:00:00Z", 1);

        // The 10th reads the 9th, which is before the range, so the run searches the 9th as well.
        assertThat(filter.isJudged(day("2026-03-09"))).isFalse();
        assertThat(filter.isJudged(day("2026-03-10"))).isTrue();
        assertThat(filter.getSearchFrom()).isEqualTo(time("2026-03-09T00:00:00Z"));
        assertThat(filter.searchesBeforeRange()).isTrue();
    }

    @Test
    void rowWithLagIsSearchedBackFromTheWindowThatTheStartOfTheRangeCuts() {
        final WholeWindowFilter filter = filter("2026-03-09T06:00:00Z", "2026-03-13T00:00:00Z", 1, 2);

        // The 9th ends in the range and reads back to the 7th with its furthest lag.
        assertThat(filter.isJudged(day("2026-03-09"))).isTrue();
        assertThat(filter.isJudged(day("2026-03-12"))).isTrue();
        assertThat(filter.getSearchFrom()).isEqualTo(time("2026-03-07T00:00:00Z"));
    }

    @Test
    void rowWithLeadIsJudgedOnceTheWindowItReadsHasEnded() {
        final WholeWindowFilter filter = filter("2026-03-10T00:00:00Z", "2026-03-13T00:00:00Z", -1);

        // The 9th reads the 10th, which ends in the range, so the 9th is judged in this run.
        assertThat(filter.isJudged(day("2026-03-09"))).isTrue();
        assertThat(filter.isJudged(day("2026-03-11"))).isTrue();
        // The 12th reads the 13th, which ends after the range, so it is judged in the next run.
        assertThat(filter.isJudged(day("2026-03-12"))).isFalse();
        assertThat(filter.getSearchFrom()).isEqualTo(time("2026-03-09T00:00:00Z"));
    }

    @Test
    void rowWithLagAndLeadIsSearchedBackForBoth() {
        final WholeWindowFilter filter = filter("2026-03-10T00:00:00Z", "2026-03-11T00:00:00Z", 1, -1);

        // The 9th is ready when the 10th ends and reads back to the 8th.
        assertThat(filter.isJudged(day("2026-03-09"))).isTrue();
        assertThat(filter.isJudged(day("2026-03-10"))).isFalse();
        assertThat(filter.getSearchFrom()).isEqualTo(time("2026-03-08T00:00:00Z"));
    }

    @Test
    void hoppingWindowsAreSearchedFromTheEarliestWindowJudged() {
        final WholeWindowFilter filter = new WholeWindowFilter(
                new HoppingWindow("EventTime", "1d", "6h", null, "Z"),
                new int[]{1},
                new TimeFilter(time("2026-03-10T00:00:00Z"), time("2026-03-11T00:00:00Z")));

        // The first window to end after the start of the range starts at 06:00 the day before, and
        // reads the day before that with lag.
        assertThat(filter.getSearchFrom()).isEqualTo(time("2026-03-08T06:00:00Z"));
    }

    @Test
    void monthWindowsAreSearchedFromTheStartOfTheMonthRead() {
        final WholeWindowFilter filter = new WholeWindowFilter(
                new HoppingWindow("EventTime", "1M", null, null, "Z"),
                new int[]{1},
                new TimeFilter(time("2026-03-01T00:00:00Z"), time("2026-04-01T00:00:00Z")));

        assertThat(filter.getSearchFrom()).isEqualTo(time("2026-02-01T00:00:00Z"));
    }

    @Test
    void rangeWithNoStartIsNotSearchedBack() {
        final WholeWindowFilter filter = new WholeWindowFilter(
                DAYS,
                new int[]{1},
                new TimeFilter(WholeWindowFilter.NO_START, time("2026-03-13T00:00:00Z")));

        assertThat(filter.isJudged(day("2026-03-10"))).isTrue();
        assertThat(filter.isJudged(day("2026-03-13"))).isFalse();
        assertThat(filter.getSearchFrom()).isEqualTo(WholeWindowFilter.NO_START);
        assertThat(filter.searchesBeforeRange()).isFalse();
    }

    @Test
    void rangeThatStartsBeforeTheEpochIsSearchedBack() {
        final WholeWindowFilter filter = filter("1969-12-31T12:00:00Z", "1970-01-02T00:00:00Z", 1);

        // The 31st ends in the range and reads the 30th with lag.
        assertThat(filter.isJudged(day("1969-12-31"))).isTrue();
        assertThat(filter.getSearchFrom()).isEqualTo(time("1969-12-30T00:00:00Z"));
        assertThat(filter.searchesBeforeRange()).isTrue();
    }

    private static WholeWindowFilter filter(final String from, final String to, final int... windowShifts) {
        return new WholeWindowFilter(
                DAYS,
                windowShifts,
                new TimeFilter(time(from), time(to)));
    }

    private static long time(final String time) {
        return Instant.parse(time).toEpochMilli();
    }

    private static long day(final String date) {
        final long[] key = new long[1];
        TIME_WINDOWS.forEachWindow(time(date + "T12:00:00Z"), windowKey -> key[0] = windowKey);
        return key[0];
    }
}
