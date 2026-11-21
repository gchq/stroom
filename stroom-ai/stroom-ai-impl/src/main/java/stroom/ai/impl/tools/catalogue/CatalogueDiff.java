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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/// What changed in a catalogue of event types since the one promoted with the feed's current version (DESIGN.md,
/// Event type catalogue, After the first build: a build of a feed's next version starts from the promoted catalogue,
/// and the review shows the event types added, changed and removed). Event types are matched by TypeId.
public final class CatalogueDiff {

    // The parts of an event type compared, each with the word the review says it by.
    private static final Map<String, Function<EventType, Object>> PARTS = parts();

    private CatalogueDiff() {
        // Static only.
    }

    /// @param promoted The catalogue promoted with the feed's current version, or null when it had none.
    /// @param current  The build's catalogue now, or null when it has none.
    /// @return The event types added, changed (with what changed) and removed, in each catalogue's order, and
    /// whether the catch-all changed.
    public static Diff between(final Catalogue promoted, final Catalogue current) {
        final Catalogue before = Objects.requireNonNullElse(promoted, new Catalogue(null, null, null));
        final Catalogue now = Objects.requireNonNullElse(current, new Catalogue(null, null, null));
        final List<String> added = new ArrayList<>();
        final List<Changed> changed = new ArrayList<>();
        for (final EventType type : now.eventTypes()) {
            final EventType was = before.eventType(type.typeId());
            if (was == null) {
                added.add(type.typeId());
                continue;
            }
            final List<String> what = new ArrayList<>();
            PARTS.forEach((word, part) -> {
                if (!Objects.equals(part.apply(was), part.apply(type))) {
                    what.add(word);
                }
            });
            if (!what.isEmpty()) {
                changed.add(new Changed(type.typeId(), what));
            }
        }
        final List<String> removed = new ArrayList<>();
        for (final EventType type : before.eventTypes()) {
            if (now.eventType(type.typeId()) == null) {
                removed.add(type.typeId());
            }
        }
        final boolean catchAllChanged = !Objects.equals(before.catchAll().translation(),
                now.catchAll().translation());
        return new Diff(added, changed, removed, catchAllChanged);
    }

    private static Map<String, Function<EventType, Object>> parts() {
        final Map<String, Function<EventType, Object>> parts = new LinkedHashMap<>();
        parts.put("recognised by", EventType::recognisedBy);
        parts.put("title", EventType::title);
        parts.put("description", EventType::description);
        parts.put("action element", EventType::eventDetail);
        parts.put("Action", EventType::action);
        parts.put("outcome", EventType::success);
        parts.put("who acted", EventType::actor);
        parts.put("acted on", type -> Objects.toString(type.affected(), "") + "|"
                                      + Objects.toString(type.affectedAs(), ""));
        parts.put("typed fields", type -> type.placements().stream().map(Placement::entry).toList());
        parts.put("dropped", EventType::drop);
        return parts;
    }


    // --------------------------------------------------------------------------------


    /// What changed since the promoted catalogue.
    ///
    /// @param added           The TypeIds of event types the promoted catalogue hadn't.
    /// @param changed         The event types of both whose parts differ.
    /// @param removed         The TypeIds of the promoted catalogue's event types no longer in it.
    /// @param catchAllChanged True when the catch-all writes otherwise (its own translation, or Unknown).
    public record Diff(List<String> added, List<Changed> changed, List<String> removed, boolean catchAllChanged) {

        /// Copies the lists given.
        public Diff {
            added = List.copyOf(added);
            changed = List.copyOf(changed);
            removed = List.copyOf(removed);
        }

        /// @return True when nothing changed.
        public boolean none() {
            return added.isEmpty() && changed.isEmpty() && removed.isEmpty() && !catchAllChanged;
        }

        /// @return It as JSON: `added`, `changed` (each `type_id` and `what`), `removed`, and `catch_all` when it
        /// changed.
        public Map<String, Object> toJson() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("added", added);
            out.put("changed", changed.stream().map(Changed::toJson).toList());
            out.put("removed", removed);
            if (catchAllChanged) {
                out.put("catch_all", "changed");
            }
            return out;
        }

        /// @return It in words, for the review: a line each of added, changed and removed event types (none when
        /// nothing changed).
        public List<String> said() {
            final List<String> lines = new ArrayList<>();
            if (!added.isEmpty()) {
                lines.add("added: " + String.join(", ", added));
            }
            changed.forEach(one -> lines.add("changed: " + one.typeId() + " (" + String.join(", ", one.what())
                                             + ")"));
            if (!removed.isEmpty()) {
                lines.add("removed: " + String.join(", ", removed));
            }
            if (catchAllChanged) {
                lines.add("changed: the catch-all (what uncatalogued records are written as)");
            }
            return lines;
        }
    }


    // --------------------------------------------------------------------------------


    /// An event type changed since the promoted catalogue.
    ///
    /// @param typeId Its TypeId.
    /// @param what   What of it changed, in words (`recognised by`, `Action`).
    public record Changed(String typeId, List<String> what) {

        /// @return It as JSON.
        public Map<String, Object> toJson() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("type_id", typeId);
            out.put("what", what);
            return out;
        }
    }
}
