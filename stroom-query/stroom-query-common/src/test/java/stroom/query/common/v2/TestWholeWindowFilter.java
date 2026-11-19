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
    void windowInsideTheRangeIsWhole() {
        final WholeWindowFilter filter = filter("2026-03-10T00:00:00Z", "2026-03-13T00:00:00Z");

        assertThat(filter.isWhole(day("2026-03-10"))).isTrue();
        assertThat(filter.isWhole(day("2026-03-12"))).isTrue();
    }

    @Test
    void windowCutByEitherEndOfTheRangeIsNotWhole() {
        final WholeWindowFilter filter = filter("2026-03-10T06:00:00Z", "2026-03-13T06:00:00Z");

        assertThat(filter.isWhole(day("2026-03-10"))).isFalse();
        assertThat(filter.isWhole(day("2026-03-11"))).isTrue();
        assertThat(filter.isWhole(day("2026-03-12"))).isTrue();
        assertThat(filter.isWhole(day("2026-03-13"))).isFalse();
    }

    @Test
    void rowWithLeadNeedsTheLaterWindowToBeWhole() {
        final WholeWindowFilter filter = filter("2026-03-10T00:00:00Z", "2026-03-13T00:00:00Z", -1);

        assertThat(filter.isWhole(day("2026-03-11"))).isTrue();
        // The day after the 12th is outside the range.
        assertThat(filter.isWhole(day("2026-03-12"))).isFalse();
    }

    @Test
    void rowWithLagNeedsTheEarlierWindowToBeInTheRange() {
        final WholeWindowFilter filter = filter("2026-03-10T00:00:00Z", "2026-03-13T00:00:00Z", 1);

        // The 9th is before the range, so its events were not searched and lag would give its
        // default for a day that may have had events.
        assertThat(filter.isWhole(day("2026-03-10"))).isFalse();
        assertThat(filter.isWhole(day("2026-03-11"))).isTrue();
    }

    @Test
    void rowWithLagDoesNotReadAWindowThatTheStartOfTheRangeCuts() {
        final WholeWindowFilter filter = filter("2026-03-09T06:00:00Z", "2026-03-13T00:00:00Z", 1);

        // The 9th is partly inside the range, so its count is of part of its events.
        assertThat(filter.isWhole(day("2026-03-10"))).isFalse();
        assertThat(filter.isWhole(day("2026-03-11"))).isTrue();
    }

    @Test
    void rangeWithNoStartCutsNoWindowAtItsStart() {
        final WholeWindowFilter filter = new WholeWindowFilter(
                DAYS,
                new int[]{1},
                new TimeFilter(0L, Instant.parse("2026-03-13T00:00:00Z").toEpochMilli()));

        assertThat(filter.isWhole(day("2026-03-10"))).isTrue();
    }

    private static WholeWindowFilter filter(final String from, final String to, final int... windowShifts) {
        return new WholeWindowFilter(
                DAYS,
                windowShifts,
                new TimeFilter(Instant.parse(from).toEpochMilli(), Instant.parse(to).toEpochMilli()));
    }

    private static long day(final String date) {
        final long[] key = new long[1];
        TIME_WINDOWS.forEachWindow(Instant.parse(date + "T12:00:00Z").toEpochMilli(), windowKey -> key[0] = windowKey);
        return key[0];
    }
}
