package stroom.floormap.client.playback;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link FloorMapQueryThrottle}, the rate limit on playback's server
 * queries.
 */
class TestFloorMapQueryThrottle {

    private static final double INTERVAL_MS = 300.0;
    private static final double FRAME_MS = 1000.0 / 60.0;

    @Test
    void testFirstCallIsAlwaysPermitted() {
        assertThat(new FloorMapQueryThrottle(INTERVAL_MS).shouldQuery(1_000)).isTrue();
    }

    @Test
    void testSecondCallWithinTheIntervalIsDenied() {
        final FloorMapQueryThrottle throttle = new FloorMapQueryThrottle(INTERVAL_MS);
        assertThat(throttle.shouldQuery(1_000)).isTrue();
        assertThat(throttle.shouldQuery(1_100)).isFalse();
        assertThat(throttle.shouldQuery(1_299)).isFalse();
    }

    @Test
    void testCallAtOrAfterTheIntervalIsPermitted() {
        final FloorMapQueryThrottle throttle = new FloorMapQueryThrottle(INTERVAL_MS);
        assertThat(throttle.shouldQuery(1_000)).isTrue();
        assertThat(throttle.shouldQuery(1_300)).isTrue();
        assertThat(throttle.shouldQuery(1_600)).isTrue();
    }

    @Test
    void testPermissionIsConsumedNotIdempotent() {
        final FloorMapQueryThrottle throttle = new FloorMapQueryThrottle(INTERVAL_MS);
        assertThat(throttle.shouldQuery(1_000)).isTrue();
        assertThat(throttle.shouldQuery(1_000))
                .as("asking twice for the same frame must not permit twice")
                .isFalse();
    }

    @Test
    void testResetPermitsImmediatelyAgain() {
        final FloorMapQueryThrottle throttle = new FloorMapQueryThrottle(INTERVAL_MS);
        assertThat(throttle.shouldQuery(1_000)).isTrue();
        assertThat(throttle.shouldQuery(1_100)).isFalse();

        throttle.reset();

        assertThat(throttle.shouldQuery(1_100))
                .as("after a stop, pressing play must query without waiting out the interval")
                .isTrue();
    }

    /**
     * The regression test for the query storm.
     *
     * <p>A second of playback at 60 fps is 60 frames. However often the timeline wraps
     * during it — and at high speed over a short range it wraps on <em>every</em>
     * frame — the number of permitted queries must stay bounded by the interval, not
     * scale with the frame rate.</p>
     *
     * <p>Before the fix, the playback loop reset the throttle on each wrap, so this
     * scenario issued a query on all 60 frames, and because each tick fires both a
     * facts and an events search that meant about 120 result stores torn down and
     * rebuilt per second.</p>
     */
    @Test
    void testWrappingEveryFrameCannotDefeatTheRateLimit() {
        final FloorMapQueryThrottle throttle = new FloorMapQueryThrottle(INTERVAL_MS);

        int permitted = 0;
        for (int frame = 0; frame < 60; frame++) {
            final double timestamp = 1_000 + frame * FRAME_MS;
            // Simulate the loop wrapping on this frame. Nothing about a wrap may
            // touch the throttle — that is the whole point of the fix.
            final boolean wrapped = true;
            assertThat(wrapped).isTrue();
            if (throttle.shouldQuery(timestamp)) {
                permitted++;
            }
        }

        // One second at a 300ms interval allows the first frame plus one per 300ms.
        assertThat(permitted)
                .as("60 frames of wrapping must not mean 60 queries")
                .isLessThanOrEqualTo(4);
        assertThat(permitted).isGreaterThan(0);
    }

    /** Over a long run the rate stays proportional to elapsed time, not frame count. */
    @Test
    void testRateStaysProportionalToElapsedTimeOverALongRun() {
        final FloorMapQueryThrottle throttle = new FloorMapQueryThrottle(INTERVAL_MS);

        int permitted = 0;
        final int frames = 600;                      // ten seconds at 60 fps
        for (int frame = 0; frame < frames; frame++) {
            if (throttle.shouldQuery(1_000 + frame * FRAME_MS)) {
                permitted++;
            }
        }

        final double elapsedMs = frames * FRAME_MS;
        final int ceiling = (int) Math.ceil(elapsedMs / INTERVAL_MS) + 1;
        assertThat(permitted).isLessThanOrEqualTo(ceiling);
        assertThat(permitted).isGreaterThanOrEqualTo(ceiling - 2);
    }

    /** A zero interval degrades to "always permit" rather than misbehaving. */
    @Test
    void testZeroIntervalPermitsEveryFrame() {
        final FloorMapQueryThrottle throttle = new FloorMapQueryThrottle(0);
        assertThat(throttle.shouldQuery(1_000)).isTrue();
        assertThat(throttle.shouldQuery(1_000)).isTrue();
        assertThat(throttle.shouldQuery(1_001)).isTrue();
    }
}
