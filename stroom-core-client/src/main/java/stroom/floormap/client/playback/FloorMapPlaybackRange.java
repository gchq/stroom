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

/// Whether a proposed timeline playback range is usable.
///
/// Kept free of GWT types so the rules can be tested on the JVM; the timeline
/// presenter that applies them cannot be.
public final class FloorMapPlaybackRange {

    /// Sentinel for "no time entered", which is what the settings view's
    /// `getTimeOrZero` returns for a cleared date box.
    private static final long NO_TIME = 0L;

    private FloorMapPlaybackRange() {
        // Utility class.
    }

    /// Whether the range from `start` to `end` can be used for playback.
    ///
    /// Two things disqualify a range:
    ///
    /// - **Either end is absent.** A cleared date box reads back as
    ///   `0`, so storing it verbatim silently moved the timeline to 1970
    ///   rather than leaving the range alone.
    /// - **The start is not before the end.** An inverted or
    ///   zero-length range made `stepBy` and the progress bar no-op, and
    ///   made playback wrap on every frame — the timeline advances, immediately
    ///   exceeds the end, wraps, and repeats.
    ///
    /// Note this makes the instant `1970-01-01T00:00:00Z` unrepresentable as
    /// a range boundary. That is inherited from the view's use of `0` as its
    /// "cleared" sentinel and is a deliberate trade: a floor map timeline is not a
    /// plausible place to want that instant, whereas a cleared box is an everyday
    /// occurrence. The same sentinel is already assumed by the timeline's
    /// `formatTime`, which renders `0` as blank.
    ///
    /// @param start the proposed range start, in epoch milliseconds
    /// @param end   the proposed range end, in epoch milliseconds
    /// @return `true` if the range should be applied
    public static boolean isUsable(final long start, final long end) {
        if (start == NO_TIME || end == NO_TIME) {
            return false;
        }
        return start < end;
    }

    /// Whether a data extent from `min` to `max` is one "Show All" can fit the timeline to.
    ///
    /// The one rule both the button's enabled state and its click handler apply, so the two
    /// cannot disagree. They used to: the button was enabled on `min <= max` while the handler
    /// required `min < max`, so a store whose events all share one timestamp got a button that
    /// looked live and did nothing when pressed.
    ///
    /// @param min the earliest event time in the data, in epoch milliseconds
    /// @param max the latest event time in the data, in epoch milliseconds
    /// @return `true` if Show All should be offered for this extent
    public static boolean canFitTo(final long min, final long max) {
        return min < max;
    }

    /// Where the playhead belongs once the range around it has changed.
    ///
    /// A time inside the range from `start` to `end` stays where it is; one outside is
    /// moved to the nearer boundary. The caller compares the answer with the time it passed in
    /// to decide whether the playhead actually moved, and so whether a re-read is due.
    ///
    /// @param time  the playhead's current time, in epoch milliseconds
    /// @param start the start of the range, in epoch milliseconds
    /// @param end   the end of the range, in epoch milliseconds; not before `start`
    /// @return `time`, clamped into the range
    public static long clampInto(final long time, final long start, final long end) {
        return Math.max(start, Math.min(end, time));
    }

    /// How far one step of the step buttons or the keyboard nudge moves the playhead.
    ///
    /// One histogram bar, so a step lands where the next bar starts rather than a fraction of the
    /// way into it. Where the bars are bucketed that is the bucket width. Dividing the range by
    /// the bar count is not the same thing, because the first and last bars hang over the range
    /// edges. Where there are no bucketed bars (the Editor tab draws none) it falls back to that
    /// division.
    ///
    /// @param start         the start of the visible range, in epoch milliseconds
    /// @param end           the end of the visible range, in epoch milliseconds
    /// @param binCount      the number of bars, used only for the fallback
    /// @param bucketWidthMs the time one bar spans, or `0` or less where unknown
    /// @return the step in milliseconds, zero only for an empty range with no bucket width
    public static long stepMs(final long start,
                              final long end,
                              final int binCount,
                              final long bucketWidthMs) {
        if (bucketWidthMs > 0) {
            return bucketWidthMs;
        }
        return Math.max(0, end - start) / Math.max(1, binCount);
    }
}
