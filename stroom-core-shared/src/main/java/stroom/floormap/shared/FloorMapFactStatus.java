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

import stroom.util.shared.NullSafe;

/// The lifecycle state carried by a fact version through the [FloorMapFieldMapping.Role#STATUS]
/// role.
///
/// Ending a fact is just another version, one whose status is [#DELETED], so it is hidden from
/// that version's effective time onwards and still shown before it. A status rather than a
/// boolean leaves room for further states.
///
/// ### The rules every reader follows
///
/// - **No status is [#ACTIVE].** Facts written without the field must keep drawing.
/// - **Visibility is decided per state by [#isHidden()]**, never by "anything other than
///   ACTIVE". A state added later is then visible unless it says otherwise.
/// - **An unrecognised value is visible.** [#fromValue(String)] returns `null` for it, and the
///   caller warns rather than hides: hiding a fact because of a value we do not understand would
///   turn a data quirk into missing data.
///
/// Not to be confused with the events query's `Status` column, which is the event's own status
/// and is not read by the floor map.
public enum FloorMapFactStatus {

    /// The fact exists and is drawn. Also what an absent or blank status means.
    ACTIVE(false),

    /// The fact has ended: it is hidden from this version's effective time onwards, until a later
    /// version brings it back.
    DELETED(true);

    private final boolean hidden;

    FloorMapFactStatus(final boolean hidden) {
        this.hidden = hidden;
    }

    /// Whether a fact whose current version has this status is left off the map.
    ///
    /// @return `true` only for [#DELETED]
    public boolean isHidden() {
        return hidden;
    }

    /// Reads a status value as stored in a fact version.
    ///
    /// Matching ignores case and surrounding whitespace, so `" deleted "` is [#DELETED].
    ///
    /// @param value the raw status value; may be `null`
    /// @return [#ACTIVE] for a `null` or blank value, the matching status, or `null` when the
    ///         value is not a known status — which the caller treats as visible
    public static FloorMapFactStatus fromValue(final String value) {
        if (NullSafe.isBlankString(value)) {
            return ACTIVE;
        }
        final String trimmed = value.trim();
        for (final FloorMapFactStatus status : values()) {
            if (status.name().equalsIgnoreCase(trimmed)) {
                return status;
            }
        }
        return null;
    }
}
