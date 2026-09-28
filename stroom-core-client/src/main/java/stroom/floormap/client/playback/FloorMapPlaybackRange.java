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

/**
 * Whether a proposed timeline playback range is usable.
 *
 * <p>Kept free of GWT types so the rules can be tested on the JVM; the timeline
 * presenter that applies them cannot be.</p>
 */
public final class FloorMapPlaybackRange {

    /**
     * Sentinel for "no time entered", which is what the settings view's
     * {@code getTimeOrZero} returns for a cleared date box.
     */
    private static final long NO_TIME = 0L;

    private FloorMapPlaybackRange() {
        // Utility class.
    }

    /**
     * Whether {@code [start, end]} can be used as a playback range.
     *
     * <p>Two things disqualify a range:</p>
     * <ul>
     *   <li><strong>Either end is absent.</strong> A cleared date box reads back as
     *       {@code 0}, so storing it verbatim silently moved the timeline to 1970
     *       rather than leaving the range alone.</li>
     *   <li><strong>The start is not before the end.</strong> An inverted or
     *       zero-length range made {@code stepBy} and the progress bar no-op, and
     *       made playback wrap on every frame — the timeline advances, immediately
     *       exceeds the end, wraps, and repeats.</li>
     * </ul>
     *
     * <p>Note this makes the instant {@code 1970-01-01T00:00:00Z} unrepresentable as
     * a range boundary. That is inherited from the view's use of {@code 0} as its
     * "cleared" sentinel and is a deliberate trade: a floor map timeline is not a
     * plausible place to want that instant, whereas a cleared box is an everyday
     * occurrence. The same sentinel is already assumed by the timeline's
     * {@code formatTime}, which renders {@code 0} as blank.</p>
     *
     * @param start the proposed range start, in epoch milliseconds
     * @param end   the proposed range end, in epoch milliseconds
     * @return {@code true} if the range should be applied
     */
    public static boolean isUsable(final long start, final long end) {
        if (start == NO_TIME || end == NO_TIME) {
            return false;
        }
        return start < end;
    }
}
