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

package stroom.ai.impl.tools.catalogue;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/// What an event type means to an activity timeline (decided with the user 2026-11-25): each event type of a
/// catalogue gets one, filled from the schema where its EventDetail element and Action settle it
/// ([TimelineRoles#bySchema]), else the user's.
public enum TimelineRole {
    /// A window or application gained focus: a session starts.
    FOCUS("focus"),
    /// The user is active from here: a logon, an unlock, a reconnect.
    ACTIVE("active"),
    /// The user is idle from here: a logoff, a lock, a disconnect.
    IDLE("idle"),
    /// Something opened: a process started.
    OPEN("open"),
    /// Something closed: a process ended.
    CLOSE("close"),
    /// Something the user did, of the kind its element and Action name (a view, a copy, a send).
    ACTION("action"),
    /// Not shown on a timeline at all.
    IGNORE("ignore");

    /// Every role's name, as the catalogue and a proposal give it.
    public static final List<String> NAMES = Arrays.stream(values()).map(TimelineRole::label).toList();

    private final String label;

    TimelineRole(final String label) {
        this.label = label;
    }

    /// @return Its name as the catalogue holds it, e.g. `focus`.
    public String label() {
        return label;
    }

    /// @param name A role's name, in any case, or null.
    /// @return The role of that name; empty if it names none.
    public static Optional<TimelineRole> of(final String name) {
        if (name == null) {
            return Optional.empty();
        }
        final String given = name.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(role -> role.label.equals(given)).findFirst();
    }
}
