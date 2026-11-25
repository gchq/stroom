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

import stroom.ai.impl.tools.generation.MappingError;
import stroom.ai.impl.tools.generation.MappingValidationException;
import stroom.ai.impl.tools.generation.ModelReader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// A source's catalogue of event types, as the build's source notes hold it (their `event_types` block): the event
/// types in the catalogue's order, the catch-all for uncatalogued records, and the scope it is for.
///
/// @param eventTypes `event_types`: the event types, in the catalogue's order (more specific first).
/// @param catchAll   `catch_all`: the catch-all's agreement.
/// @param scope      The source type group it is for.
/// @param checkedFeeds `checked_feeds`: the feeds whose records it was checked against (every feed of every
///                     pipeline a translation inducted in place serves, decided 2026-11-25); empty when not
///                     recorded.
public record Catalogue(List<EventType> eventTypes, CatchAll catchAll, Scope scope, List<String> checkedFeeds) {

    /// The key of the feeds it was checked against.
    public static final String CHECKED_FEEDS = "checked_feeds";

    /// Fills in the defaults for null parts: no event types, a proposed catch-all, no scope, no feeds.
    public Catalogue {
        eventTypes = List.copyOf(Objects.requireNonNullElse(eventTypes, List.of()));
        catchAll = Objects.requireNonNullElse(catchAll, CatchAll.PROPOSED);
        scope = Objects.requireNonNullElse(scope, Scope.NONE);
        checkedFeeds = List.copyOf(Objects.requireNonNullElse(checkedFeeds, List.of()));
    }

    /// A catalogue with no feeds recorded it was checked against.
    ///
    /// @param eventTypes The event types, in the catalogue's order.
    /// @param catchAll   The catch-all's agreement.
    /// @param scope      The source type group it is for.
    public Catalogue(final List<EventType> eventTypes, final CatchAll catchAll, final Scope scope) {
        this(eventTypes, catchAll, scope, List.of());
    }

    /// @param json A catalogue as JSON-like values.
    /// @return It read, every error collected.
    public static Read parse(final Object json) {
        final ModelReader reader = new ModelReader();
        final Catalogue catalogue = read(reader, json, List.of());
        return new Read(reader.errors().isEmpty()
                ? catalogue
                : null, reader.errors());
    }

    /// @param json A catalogue as JSON-like values.
    /// @return It read.
    /// @throws MappingValidationException if it doesn't read, with every error found.
    public static Catalogue fromJson(final Map<String, Object> json) {
        final ModelReader reader = new ModelReader();
        return reader.orThrow("Catalogue", read(reader, json, List.of()));
    }

    /// Reads one from JSON-like values, laxly, every error collected.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't read.
    public static Catalogue read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "Catalogue");
        if (fields == null) {
            return null;
        }
        final Catalogue catalogue = new Catalogue(
                fields.models("event_types", (value, at) -> EventType.read(reader, value, at)),
                fields.optionalModel("catch_all", (value, at) -> CatchAll.read(reader, value, at)),
                fields.optionalModel("scope", (value, at) -> Scope.read(reader, value, at)),
                fields.input().get(CHECKED_FEEDS) instanceof final List<?> feeds
                        ? feeds.stream().map(String::valueOf).toList()
                        : List.of());
        return fields.ok()
                ? catalogue
                : null;
    }

    /// @return It as JSON, as the source notes hold it.
    public Map<String, Object> toJson() {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("event_types", eventTypes.stream().map(EventType::toJson).toList());
        out.put("catch_all", catchAll.toJson());
        out.put("scope", scope.toJson());
        if (!checkedFeeds.isEmpty()) {
            out.put(CHECKED_FEEDS, checkedFeeds);
        }
        return out;
    }

    /// @param typeId A TypeId.
    /// @return The event type of that TypeId, or null.
    public EventType eventType(final String typeId) {
        return eventTypes.stream().filter(type -> Objects.equals(type.typeId(), typeId)).findFirst().orElse(null);
    }

    /// @param typeIds The TypeIds the user confirmed in the form.
    /// @return It with those event types confirmed by the user ([EventType#confirmedByUser()]).
    public Catalogue confirmedByUser(final Set<String> typeIds) {
        final List<EventType> confirmed = new ArrayList<>();
        for (final EventType type : eventTypes) {
            confirmed.add(typeIds.contains(type.typeId())
                    ? type.confirmedByUser()
                    : type);
        }
        return new Catalogue(confirmed, catchAll, scope, checkedFeeds);
    }

    /// @return It with its catch-all confirmed by the user (its reason and own translation kept).
    public Catalogue withCatchAllConfirmedByUser() {
        return new Catalogue(eventTypes, new CatchAll(EventType.CONFIRMED, catchAll.allowUnknown(),
                catchAll.translation()), scope, checkedFeeds);
    }

    /// @param allowUnknown The user's reason for keeping uncatalogued records as Unknown, or null to keep the one it
    ///                     has.
    /// @return It with its catch-all confirmed by the user, with that reason (its own translation kept).
    public Catalogue withCatchAllConfirmedByUser(final String allowUnknown) {
        return new Catalogue(eventTypes, new CatchAll(EventType.CONFIRMED, allowUnknown == null
                ? catchAll.allowUnknown()
                : allowUnknown, catchAll.translation()), scope, checkedFeeds);
    }

    /// @param given The catch-all.
    /// @return It with that catch-all.
    public Catalogue withCatchAll(final CatchAll given) {
        return new Catalogue(eventTypes, given, scope, checkedFeeds);
    }

    /// @param feeds The feeds its records were checked against.
    /// @return It recording those feeds, in that order.
    public Catalogue withCheckedFeeds(final List<String> feeds) {
        return new Catalogue(eventTypes, catchAll, scope, feeds);
    }

    /// @param types The event types.
    /// @return It with those event types, in that order.
    public Catalogue withEventTypes(final List<EventType> types) {
        return new Catalogue(types, catchAll, scope, checkedFeeds);
    }


    // --------------------------------------------------------------------------------


    /// The catch-all's agreement: records no event type recognises are translated as Unknown, their fields kept as
    /// Data. Not an event type.
    ///
    /// @param status       `proposed` or `confirmed`.
    /// @param allowUnknown `allow_unknown`: the user's reason for the uncatalogued records' Unknown, or null.
    /// @param translation  `translation`: what the catch-all writes instead of Unknown, when a hand-written last
    ///                     rule taking every remaining record wrote a real event (read back by rebuild_mapping);
    ///                     null for Unknown.
    public record CatchAll(String status, String allowUnknown, Translation translation) {

        /// The catch-all not yet confirmed.
        public static final CatchAll PROPOSED = new CatchAll(EventType.PROPOSED);
        /// The catch-all confirmed by the user.
        public static final CatchAll CONFIRMED = new CatchAll(EventType.CONFIRMED);

        /// @param status `proposed` or `confirmed`.
        public CatchAll(final String status) {
            this(status, null, null);
        }

        /// Reads one from JSON-like values, laxly, every error collected.
        ///
        /// @param reader The reader, collecting the errors.
        /// @param input  The value given.
        /// @param loc    Where it is.
        /// @return It, or null (with the errors noted) if it doesn't read.
        public static CatchAll read(final ModelReader reader, final Object input, final List<Object> loc) {
            final ModelReader.Fields fields = reader.fields(input, loc, "CatchAll");
            if (fields == null) {
                return null;
            }
            final String status = fields.literal("status", EventType.PROPOSED, EventType.STATUSES);
            final String allowUnknown = fields.optionalString("allow_unknown");
            final Translation translation = fields.optionalModel("translation",
                    (value, at) -> Translation.read(reader, value, at));
            return fields.ok()
                    ? new CatchAll(status, allowUnknown, translation)
                    : null;
        }

        /// @return It confirmed.
        public boolean confirmed() {
            return EventType.CONFIRMED.equals(status);
        }

        /// @return It as JSON: its status, and its reason and own translation when it has them.
        public Map<String, Object> toJson() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", status);
            if (allowUnknown != null) {
                out.put("allow_unknown", allowUnknown);
            }
            if (translation != null) {
                out.put("translation", translation.toJson());
            }
            return out;
        }


        // --------------------------------------------------------------------------------


        /// What a catch-all writes instead of Unknown: a hand-written last rule's real event.
        ///
        /// @param typeId      `type_id`: its fixed TypeId.
        /// @param eventDetail `event_detail`: its action element (`Authenticate`).
        /// @param action      Its fixed Action, or null.
        /// @param description Its fixed Description, or null.
        public record Translation(String typeId, String eventDetail, String action, String description) {

            /// Reads one from JSON-like values, laxly, every error collected.
            ///
            /// @param reader The reader, collecting the errors.
            /// @param input  The value given.
            /// @param loc    Where it is.
            /// @return It, or null (with the errors noted) if it doesn't read.
            public static Translation read(final ModelReader reader, final Object input, final List<Object> loc) {
                final ModelReader.Fields fields = reader.fields(input, loc, "Translation");
                if (fields == null) {
                    return null;
                }
                final Translation translation = new Translation(fields.optionalString("type_id"),
                        fields.optionalString("event_detail"), fields.optionalString("action"),
                        fields.optionalString("description"));
                return fields.ok()
                        ? translation
                        : null;
            }

            /// @return It as JSON.
            public Map<String, Object> toJson() {
                final Map<String, Object> out = new LinkedHashMap<>();
                out.put("type_id", typeId);
                out.put("event_detail", eventDetail);
                out.put("action", action);
                out.put("description", description);
                return out;
            }
        }
    }


    // --------------------------------------------------------------------------------


    /// The source type group a catalogue is for.
    ///
    /// @param sourceTypeField `source_type_field`: the build's source type field (its reading, e.g.
    ///                        `event>log_type`), or null when the build has none.
    /// @param sourceTypes     `source_types`: the source types of the group.
    public record Scope(String sourceTypeField, List<String> sourceTypes) {

        /// No scope: a build of one source type.
        public static final Scope NONE = new Scope(null, List.of());

        /// Fills in an empty list for a null one.
        public Scope {
            sourceTypes = List.copyOf(Objects.requireNonNullElse(sourceTypes, List.of()));
        }

        /// Reads one from JSON-like values, laxly, every error collected.
        ///
        /// @param reader The reader, collecting the errors.
        /// @param input  The value given.
        /// @param loc    Where it is.
        /// @return It, or null (with the errors noted) if it doesn't read.
        public static Scope read(final ModelReader reader, final Object input, final List<Object> loc) {
            final ModelReader.Fields fields = reader.fields(input, loc, "Scope");
            if (fields == null) {
                return null;
            }
            final Scope scope = new Scope(fields.optionalString("source_type_field"), fields.strings("source_types"));
            return fields.ok()
                    ? scope
                    : null;
        }

        /// @return It as JSON.
        public Map<String, Object> toJson() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("source_type_field", sourceTypeField);
            out.put("source_types", sourceTypes);
            return out;
        }
    }


    // --------------------------------------------------------------------------------


    /// A catalogue read laxly.
    ///
    /// @param catalogue It, or null when it didn't read.
    /// @param errors    What didn't read, each with where.
    public record Read(Catalogue catalogue, List<MappingError> errors) {

    }
}
