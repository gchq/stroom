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

package stroom.ai.impl.tools.timeline;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/// A pair of event types bounding spans: an end closes the latest open start of the same key (the lane, and the
/// value at the key path where one is given).
///
/// @param source The source it is of, by its place, or -1 for any.
/// @param start  The type that starts a span (a TypeId, or an element and Action such as `Process/Execute`).
/// @param end    The type that ends it.
/// @param by     The key path the two are matched by (alternatives separated by `|`, the first an event holds), or
///               null for the lane alone.
/// @param label  The key path whose value labels the span (`=` and text for the text itself), or null for the
///               start's own label.
/// @param origin Where it came from.
/// @param share  The share of keys where the two alternate, where it was inferred or checked; else 1.
record PairRule(int source, String start, String end, String by, String label, Origin origin, double share) {

    /// The schema's own pairs: a process from its start to its end, a logon to its logoff, a lock to its unlock.
    static final List<PairRule> SCHEMA = List.of(
            new PairRule(-1, "Process/Execute", "Process/Terminate", "ProcessId|Command", null, Origin.SCHEMA, 1),
            new PairRule(-1, "Process/Startup", "Process/Shutdown", "ProcessId|Command", null, Origin.SCHEMA, 1),
            new PairRule(-1, "Authenticate/Logon", "Authenticate/Logoff", null, "=logged on", Origin.SCHEMA, 1),
            new PairRule(-1, "Authenticate/ScreenLock", "Authenticate/ScreenUnlock", null, "=locked", Origin.SCHEMA,
                    1));

    /// @param keys An event's values by their paths.
    /// @return The value it is matched by: of the first key path it holds; "" for the lane alone; null if it holds
    /// none of the paths.
    String keyOf(final Map<String, String> keys) {
        if (by == null || by.isBlank()) {
            return "";
        }
        for (final String path : by.split("\\|")) {
            final String value = keys.get(path.strip());
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /// @return Where it came from, as the result says it.
    String originName() {
        return origin.name().toLowerCase(Locale.ROOT);
    }


    // --------------------------------------------------------------------------------


    /// Where a pair came from, in the order they are applied.
    enum Origin {
        /// The skill's Event chains.
        SKILL,
        /// The call's `pairs`.
        CALL,
        /// The event-logging schema's.
        SCHEMA,
        /// Inferred from the user's events.
        INFERRED
    }
}
