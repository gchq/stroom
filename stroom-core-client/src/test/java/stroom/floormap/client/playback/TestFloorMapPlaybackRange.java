package stroom.floormap.client.playback;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link FloorMapPlaybackRange}.
 */
class TestFloorMapPlaybackRange {

    private static final long HOUR = 60L * 60 * 1000;

    @Test
    void testOrdinaryRangeIsUsable() {
        assertThat(FloorMapPlaybackRange.isUsable(1_000_000, 2_000_000)).isTrue();
    }

    /**
     * An inverted range is the case that made playback wrap on every frame: the
     * timeline advances, immediately exceeds the end, wraps to the start, and repeats.
     */
    @Test
    void testInvertedRangeIsNotUsable() {
        assertThat(FloorMapPlaybackRange.isUsable(2_000_000, 1_000_000)).isFalse();
    }

    /**
     * A zero-length range is equally unusable, and it is not hypothetical — a store
     * holding a single effective time reports min == max.
     */
    @Test
    void testZeroLengthRangeIsNotUsable() {
        assertThat(FloorMapPlaybackRange.isUsable(1_000_000, 1_000_000)).isFalse();
    }

    /**
     * A cleared date box reads back as {@code 0}, which must not be stored as a
     * boundary — doing so silently moved the timeline to 1970.
     */
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

    /**
     * Times before 1970 are negative and remain usable, so a historical range is not
     * rejected — only the literal {@code 0} sentinel is.
     */
    @Test
    void testNegativeTimesAreStillUsable() {
        assertThat(FloorMapPlaybackRange.isUsable(-2 * HOUR, -HOUR)).isTrue();
        assertThat(FloorMapPlaybackRange.isUsable(-HOUR, HOUR))
                .as("a range straddling the epoch")
                .isTrue();
    }

    /** A one-millisecond range is degenerate but ordered, so it is allowed. */
    @Test
    void testOneMillisecondRangeIsUsable() {
        assertThat(FloorMapPlaybackRange.isUsable(1_000_000, 1_000_001)).isTrue();
    }

    /** Extremes do not overflow into the wrong answer. */
    @Test
    void testExtremesAreHandled() {
        assertThat(FloorMapPlaybackRange.isUsable(Long.MIN_VALUE, Long.MAX_VALUE)).isTrue();
        assertThat(FloorMapPlaybackRange.isUsable(Long.MAX_VALUE, Long.MIN_VALUE)).isFalse();
    }
}
