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

package stroom.floormap.shared;

import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestFloorMapEventExpiry {

    private static final long HOUR = 3_600_000L;
    private static final long T = 1_767_225_600_000L;   // 2026-01-01

    private static SimpleDuration hours(final long n) {
        return SimpleDuration.builder().time(n).timeUnit(TimeUnit.HOURS).build();
    }

    @Test
    void anAbsentDurationMeansTwentyFourHoursRatherThanOff() {
        // Every document written before this feature has no value, and lasting forever is the
        // behaviour being removed.
        assertThat(FloorMapEventExpiry.millis(null)).isEqualTo(24 * HOUR);
    }

    @Test
    void configuredDurationIsUsed() {
        assertThat(FloorMapEventExpiry.millis(hours(2))).isEqualTo(2 * HOUR);
    }

    @Test
    void nonPositiveDurationsFallBackRatherThanHidingEverything() {
        assertThat(FloorMapEventExpiry.millis(hours(0))).isEqualTo(24 * HOUR);
        assertThat(FloorMapEventExpiry.millis(hours(-5))).isEqualTo(24 * HOUR);
    }

    @Test
    void expiryIsMeasuredFromTheTimelinePositionNotTheWallClock() {
        // Scrubbing back a year must show what was current then, not an empty map.
        final long lastYear = T - (365L * 24 * HOUR);
        assertThat(FloorMapEventExpiry.isVisible(lastYear - HOUR, lastYear, hours(24))).isTrue();
    }

    @Test
    void anEntityOlderThanTheDurationIsHidden() {
        assertThat(FloorMapEventExpiry.isVisible(T - (25 * HOUR), T, hours(24))).isFalse();
    }

    @Test
    void anEntityInsideTheDurationIsShown() {
        assertThat(FloorMapEventExpiry.isVisible(T - (23 * HOUR), T, hours(24))).isTrue();
    }

    @Test
    void theBoundaryItselfIsShown() {
        assertThat(FloorMapEventExpiry.isVisible(T - (24 * HOUR), T, hours(24))).isTrue();
        assertThat(FloorMapEventExpiry.isVisible(T - (24 * HOUR) - 1, T, hours(24))).isFalse();
    }

    @Test
    void anEventAtTheTimelinePositionIsShown() {
        assertThat(FloorMapEventExpiry.isVisible(T, T, hours(24))).isTrue();
    }

    @Test
    void anUnknownEffectiveTimeIsKept() {
        // Hiding an entity because its time column would not parse turns a formatting problem into
        // missing data.
        assertThat(FloorMapEventExpiry.isVisible(null, T, hours(24))).isTrue();
    }

    @Test
    void anEarlyPositionDoesNotUnderflowIntoHidingEverything() {
        // time - expiry wraps for a position near the start of the epoch; a wrapped cutoff is a
        // large positive number, which would hide every entity rather than none.
        final long early = Long.MIN_VALUE + 1000L;
        assertThat(FloorMapEventExpiry.cutoff(early, hours(24))).isEqualTo(Long.MIN_VALUE);
        assertThat(FloorMapEventExpiry.isVisible(Long.MIN_VALUE + 1, early, hours(24))).isTrue();
    }

    @Test
    void theDefaultIsTwentyFourHours() {
        assertThat(FloorMapEventExpiry.DEFAULT.getApproxMillis()).isEqualTo(24 * HOUR);
    }
}
