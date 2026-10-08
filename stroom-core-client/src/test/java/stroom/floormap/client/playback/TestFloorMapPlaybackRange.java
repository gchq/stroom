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

package stroom.floormap.client.playback;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests for [FloorMapPlaybackRange].
class TestFloorMapPlaybackRange {

    private static final long HOUR = 60L * 60 * 1000;

    @Test
    void testOrdinaryRangeIsUsable() {
        assertThat(FloorMapPlaybackRange.isUsable(1_000_000, 2_000_000)).isTrue();
    }

    /// An inverted range is the case that made playback wrap on every frame: the
    /// timeline advances, immediately exceeds the end, wraps to the start, and repeats.
    @Test
    void testInvertedRangeIsNotUsable() {
        assertThat(FloorMapPlaybackRange.isUsable(2_000_000, 1_000_000)).isFalse();
    }

    /// A zero-length range is equally unusable, and it is not hypothetical — a store
    /// holding a single effective time reports min == max.
    @Test
    void testZeroLengthRangeIsNotUsable() {
        assertThat(FloorMapPlaybackRange.isUsable(1_000_000, 1_000_000)).isFalse();
    }

    /// A cleared date box reads back as `0`, which must not be stored as a
    /// boundary — doing so silently moved the timeline to 1970.
    @Test
    void testClearedBoundaryIsNotUsable() {
        assertThat(FloorMapPlaybackRange.isUsable(0, 2_000_000))
                .as("cleared start")
                .isFalse();
        assertThat(FloorMapPlaybackRange.isUsable(1_000_000, 0))
                .as("cleared end")
                .isFalse();
        assertThat(FloorMapPlaybackRange.isUsable(0, 0))
                .as("both cleared")
                .isFalse();
    }

    /// Times before 1970 are negative and remain usable, so a historical range is not
    /// rejected — only the literal `0` sentinel is.
    @Test
    void testNegativeTimesAreStillUsable() {
        assertThat(FloorMapPlaybackRange.isUsable(-2 * HOUR, -HOUR)).isTrue();
        assertThat(FloorMapPlaybackRange.isUsable(-HOUR, HOUR))
                .as("a range straddling the epoch")
                .isTrue();
    }

    /// A one-millisecond range is degenerate but ordered, so it is allowed.
    @Test
    void testOneMillisecondRangeIsUsable() {
        assertThat(FloorMapPlaybackRange.isUsable(1_000_000, 1_000_001)).isTrue();
    }

    /// Extremes do not overflow into the wrong answer.
    @Test
    void testExtremesAreHandled() {
        assertThat(FloorMapPlaybackRange.isUsable(Long.MIN_VALUE, Long.MAX_VALUE)).isTrue();
        assertThat(FloorMapPlaybackRange.isUsable(Long.MAX_VALUE, Long.MIN_VALUE)).isFalse();
    }

    /// An ordinary extent can be fitted to.
    @Test
    void testOrdinaryExtentCanBeFitted() {
        assertThat(FloorMapPlaybackRange.canFitTo(1_000_000, 2_000_000)).isTrue();
    }

    /// Regression: a store whose events all share one timestamp reports min == max. The button
    /// used to be enabled for it while the click handler declined it, so Show All looked live and
    /// did nothing. One rule now decides both, and it says no.
    @Test
    void testSingleInstantExtentCannotBeFitted() {
        assertThat(FloorMapPlaybackRange.canFitTo(1_000_000, 1_000_000)).isFalse();
    }

    /// An inverted extent cannot be fitted to.
    @Test
    void testInvertedExtentCannotBeFitted() {
        assertThat(FloorMapPlaybackRange.canFitTo(2_000_000, 1_000_000)).isFalse();
    }

    /// The timeline's "no extent yet" sentinels must read as no extent, or Show All would be
    /// enabled before any data had been seen.
    @Test
    void testUnsetExtentCannotBeFitted() {
        assertThat(FloorMapPlaybackRange.canFitTo(Long.MAX_VALUE, Long.MIN_VALUE)).isFalse();
    }

    /// A playhead already inside the new range is left alone, so no needless re-read is issued.
    @Test
    void testClampLeavesTimeInsideRangeAlone() {
        assertThat(FloorMapPlaybackRange.clampInto(5 * HOUR, 2 * HOUR, 10 * HOUR))
                .isEqualTo(5 * HOUR);
    }

    /// The boundaries themselves are inside the range.
    @Test
    void testClampLeavesTimeOnBoundaryAlone() {
        assertThat(FloorMapPlaybackRange.clampInto(2 * HOUR, 2 * HOUR, 10 * HOUR))
                .as("on the start")
                .isEqualTo(2 * HOUR);
        assertThat(FloorMapPlaybackRange.clampInto(10 * HOUR, 2 * HOUR, 10 * HOUR))
                .as("on the end")
                .isEqualTo(10 * HOUR);
    }

    /// Regression: the case that stranded the playhead. The Map tab opens at the wall clock; a
    /// range set to span older data left it past the end, with nothing re-read. It now moves to
    /// the end of the new range.
    @Test
    void testClampMovesTimeAfterRangeToEnd() {
        assertThat(FloorMapPlaybackRange.clampInto(100 * HOUR, 2 * HOUR, 10 * HOUR))
                .isEqualTo(10 * HOUR);
    }

    /// A playhead before a range set later than it moves to the start.
    @Test
    void testClampMovesTimeBeforeRangeToStart() {
        assertThat(FloorMapPlaybackRange.clampInto(HOUR, 2 * HOUR, 10 * HOUR))
                .isEqualTo(2 * HOUR);
    }
}
