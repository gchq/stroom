/*
 * Copyright 2016-2026 Crown Copyright
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

/// Rate limiter for the server queries timeline playback issues.
///
/// Playback moves the scrubber every animation frame — around 60 times a second —
/// but each query destroys and recreates a server-side result store, so the queries
/// have to be issued far less often than the visuals are updated. The scrubber and
/// the data therefore run on two different clocks, and the data can lag the scrubber
/// by up to one interval. That is the intended trade.
///
/// Holds no GWT types, so the rate limit can be tested on the JVM by driving
/// timestamps through it — which the presenter that owns it cannot be.
///
/// ### Why this is a class rather than two fields on the presenter
///
/// It used to be a `double` field, and the throttle was defeated by a single
/// line elsewhere in the playback loop that set it back to zero on every loop wrap,
/// to force a fresh query after the discontinuous jump. That is a reasonable thing to
/// want and a disastrous way to get it: at high speed over a short range the timeline
/// wraps on *every frame*, so the reset fired every frame, and the throttle
/// never applied. Playback then issued two searches per frame — roughly 120 a second
/// — each one tearing down and rebuilding a result store. Loop playback is the
/// default mode.
///
/// Keeping the state private, and offering no way to clear it except
/// [#reset()] with its contract stated, makes that mistake hard to repeat.
public final class FloorMapQueryThrottle {

    private final double intervalMs;

    /// Wall-clock timestamp of the last permitted query, or `0` when none has
    /// been issued since the last [#reset()].
    private double lastQueryTimestamp;

    /// @param intervalMs the minimum wall-clock gap between queries, in milliseconds
    public FloorMapQueryThrottle(final double intervalMs) {
        this.intervalMs = intervalMs;
    }

    /// Whether a query may be issued at `timestamp`, recording it if so.
    ///
    /// Returns `true` for the first call after construction or
    /// [#reset()], and thereafter no more than once per interval. Callers must
    /// treat a `true` result as permission consumed — it is not idempotent.
    ///
    /// @param timestamp the frame's wall-clock timestamp in milliseconds
    /// @return `true` if the caller should query now
    public boolean shouldQuery(final double timestamp) {
        if (lastQueryTimestamp == 0 || timestamp - lastQueryTimestamp >= intervalMs) {
            lastQueryTimestamp = timestamp;
            return true;
        }
        return false;
    }

    /// Forgets the last query, so the next [#shouldQuery] returns `true`.
    ///
    /// **Only call this when playback stops** — on pause, on reaching
    /// the end, or when the timeline is otherwise no longer running — so that pressing
    /// play again queries immediately rather than waiting out the remainder of an
    /// interval.
    ///
    /// **Do not call it on a loop wrap**, or for any other event that
    /// can occur while playback continues. Anything that happens per frame will, if it
    /// resets the throttle, remove the rate limit entirely; a wrap is exactly such an
    /// event at high speed or over a short range. The scrubber still moves smoothly
    /// across a wrap because the visual position is updated every frame regardless of
    /// this throttle, and fresh data follows within one interval.
    public void reset() {
        lastQueryTimestamp = 0;
    }
}
