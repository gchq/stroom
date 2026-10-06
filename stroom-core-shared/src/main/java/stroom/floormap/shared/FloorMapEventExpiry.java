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

/// How long an entity stays on the map after its last event.
///
/// The events store answers "where was everything at T" by giving each entity's most recent
/// position at or before T, however long ago that was. Left alone, an entity that stopped emitting a
/// year ago is still drawn — which is the defect this exists to fix: **an entity whose latest event
/// predates `T − D` is hidden**, not greyed and not annotated.
///
/// **The rule is applied on read, against the timeline position rather than the wall clock.**
/// Scrubbing to last Tuesday shows what was current last Tuesday, so expiry has to be measured from
/// where the scrubber is. Measuring from "now" would empty the map for any position more than
/// `D` in the past.
///
/// **Absent means the default, not "off".** Every document that exists today has no value, and
/// events lasting forever is the behaviour being removed — so a null duration is twenty-four hours.
/// There is deliberately no way to disable expiry.
public final class FloorMapEventExpiry {

    /// What a document gets when it says nothing, which is every document written before this.
    public static final SimpleDuration DEFAULT = SimpleDuration
            .builder()
            .time(24)
            .timeUnit(TimeUnit.HOURS)
            .build();

    private FloorMapEventExpiry() {
        // Utility class.
    }

    /// The configured duration in milliseconds, or the default where none is set.
    ///
    /// A zero or negative duration would hide everything, including the entity that just moved, so
    /// it is treated as unset rather than obeyed. That is a guard against a bad document rather than a
    /// supported setting.
    public static long millis(final SimpleDuration duration) {
        if (duration == null) {
            return DEFAULT.getApproxMillis();
        }
        final long millis = duration.getApproxMillis();
        return millis > 0
                ? millis
                : DEFAULT.getApproxMillis();
    }

    /// The earliest effective time still shown at timeline position `time`.
    ///
    /// Clamped at [Long#MIN_VALUE] rather than allowed to wrap: a position early in the epoch
    /// minus a long duration underflows, and a wrapped cutoff is a large positive number that hides
    /// every entity instead of none.
    public static long cutoff(final long time, final SimpleDuration duration) {
        final long expiry = millis(duration);
        return time - expiry > time
                ? Long.MIN_VALUE
                : time - expiry;
    }

    /// Whether an entity last seen at `effectiveTime` is still shown at timeline position
    /// `time`.
    ///
    /// An unknown effective time is kept. The alternative is hiding an entity because its time
    /// column could not be read, which turns a formatting problem into missing data — and the column
    /// arrives as text rendered to the viewing user's preferences, so it is not always parseable.
    public static boolean isVisible(final Long effectiveTime, final long time, final SimpleDuration duration) {
        return effectiveTime == null || effectiveTime >= cutoff(time, duration);
    }
}
