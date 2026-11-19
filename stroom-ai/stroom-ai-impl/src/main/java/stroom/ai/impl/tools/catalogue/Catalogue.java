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
public record Catalogue(List<EventType> eventTypes, CatchAll catchAll, Scope scope) {

    /// Fills in the defaults for null parts: no event types, a proposed catch-all, no scope.
    public Catalogue {
        eventTypes = List.copyOf(Objects.requireNonNullElse(eventTypes, List.of()));
        catchAll = Objects.requireNonNullElse(catchAll, CatchAll.PROPOSED);
        scope = Objects.requireNonNullElse(scope, Scope.NONE);
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
                fields.optionalModel("scope", (value, at) -> Scope.read(reader, value, at)));
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
        return new Catalogue(confirmed, catchAll, scope);
    }

    /// @return It with its catch-all confirmed by the user.
    public Catalogue withCatchAllConfirmedByUser() {
        return new Catalogue(eventTypes, CatchAll.CONFIRMED, scope);
    }

    /// @param types The event types.
    /// @return It with those event types, in that order.
    public Catalogue withEventTypes(final List<EventType> types) {
        return new Catalogue(types, catchAll, scope);
    }


    // --------------------------------------------------------------------------------


    /// The catch-all's agreement: records no event type recognises are translated as Unknown, their fields kept as
    /// Data. Not an event type.
    ///
    /// @param status `proposed` or `confirmed`.
    public record CatchAll(String status) {

        /// The catch-all not yet confirmed.
        public static final CatchAll PROPOSED = new CatchAll(EventType.PROPOSED);
        /// The catch-all confirmed by the user.
        public static final CatchAll CONFIRMED = new CatchAll(EventType.CONFIRMED);

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
            return fields.ok()
                    ? new CatchAll(status)
                    : null;
        }

        /// @return It confirmed.
        public boolean confirmed() {
            return EventType.CONFIRMED.equals(status);
        }

        /// @return It as JSON.
        public Map<String, Object> toJson() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", status);
            return out;
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
