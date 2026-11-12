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

package stroom.ai.impl.tools.generation;

import stroom.ai.impl.tools.JsonValues;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// What an aggregated feed's dispatcher ([DispatcherXslt]) is generated from: the feed's input as its translations
/// read it, its source type field, the registry's rows in the order they are tried, and the source type groups it
/// imports, each with the document it imports (a build's working copy, or the promoted original: the caller says which)
/// and the source types it translates. Kept with the dispatcher's XSLT, in its description, so the stepping tools can
/// generate a temporary dispatcher with an unsaved group inlined.
///
/// @param feed    The feed's name.
/// @param mode    The dispatcher's mode, the prefix of its own names.
/// @param input   The input as the groups' mappings read it: `input`, `root`, `record`, `json_layout`,
///                `xml_namespace` (as a mapping writes them).
/// @param field   The source type field (dotted for a JSON field's keys, `/` between a Data Splitter's names).
/// @param helpers The import of the feed's helpers module, or null.
/// @param rows    The registry's rows, in the order they are tried.
/// @param groups  The source type groups imported, in the order they are imported.
/// @param events  The Events it writes: `version`, `namespace` and `location` (their xsi:schemaLocation), as last
///                generated; what a temporary dispatcher writes when no schema is given.
public record DispatcherSpec(String feed,
                             String mode,
                             Map<String, Object> input,
                             String field,
                             String helpers,
                             List<Row> rows,
                             List<Group> groups,
                             Map<String, String> events) {

    /// Fills in empty lists and maps for null ones.
    public DispatcherSpec {
        input = Map.copyOf(Objects.requireNonNullElse(input, Map.of()));
        rows = List.copyOf(Objects.requireNonNullElse(rows, List.of()));
        groups = List.copyOf(Objects.requireNonNullElse(groups, List.of()));
        events = Map.copyOf(Objects.requireNonNullElse(events, Map.of()));
    }

    /// The input of a mapping, as a dispatcher reads it.
    ///
    /// @param mapping A source type group's mapping.
    /// @return Its input settings.
    public static Map<String, Object> inputOf(final TranslationMapping mapping) {
        final Map<String, Object> input = new LinkedHashMap<>();
        input.put("input", mapping.input());
        if (mapping.root() != null) {
            input.put("root", mapping.root());
        }
        if (mapping.record() != null) {
            input.put("record", mapping.record());
        }
        input.put("json_layout", mapping.jsonLayout());
        input.put("xml_namespace", mapping.xmlNamespace());
        return input;
    }

    /// @return The input as a mapping reads it (one with no rules of its own), for the generator's helpers.
    TranslationMapping inputMapping() {
        final Map<String, Object> json = new LinkedHashMap<>(input);
        json.put("events", List.of(Map.of("name", "dispatch")));
        return TranslationMapping.parse(json);
    }

    /// @param json A spec as [#toJson()] writes it, or null.
    /// @return It; empty when it isn't one.
    public static Optional<DispatcherSpec> fromJson(final Object json) {
        final Map<String, Object> map = JsonValues.mapOrEmpty(json);
        if (map.isEmpty() || map.get("field") == null) {
            return Optional.empty();
        }
        final List<Row> rows = new ArrayList<>();
        JsonValues.maps(map.get("rows")).forEach(row -> rows.add(new Row(JsonValues.text(row.get("name")),
                JsonValues.text(row.get("match")), JsonValues.text(row.get("value")),
                JsonValues.text(row.get("status")))));
        final List<Group> groups = new ArrayList<>();
        JsonValues.maps(map.get("groups")).forEach(group -> groups.add(new Group(JsonValues.text(group.get("name")),
                JsonValues.text(group.get("mode")), (String) group.get("href"),
                JsonValues.strings(group.get("source_types")))));
        final Map<String, String> events = new LinkedHashMap<>();
        JsonValues.mapOrEmpty(map.get("events")).forEach((key, value) -> {
            if (value != null) {
                events.put(key, JsonValues.text(value));
            }
        });
        return Optional.of(new DispatcherSpec(JsonValues.text(map.get("feed")), JsonValues.text(map.get("mode")),
                JsonValues.mapOrEmpty(map.get("input")), JsonValues.text(map.get("field")),
                (String) map.get("helpers"), rows, groups, events));
    }

    /// @return It as JSON.
    public Map<String, Object> toJson() {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("feed", feed);
        out.put("mode", mode);
        out.put("input", new LinkedHashMap<>(input));
        out.put("field", field);
        out.put("helpers", helpers);
        out.put("rows", rows.stream().map(Row::toJson).toList());
        out.put("groups", groups.stream().map(Group::toJson).toList());
        out.put("events", new LinkedHashMap<>(events));
        return out;
    }

    /// @param group A source type group, new or changed.
    /// @return This spec with it imported in place of any group of its mode (or its name), last if it is new; the
    ///         source types it takes taken from any other group.
    public DispatcherSpec withGroup(final Group group) {
        final List<Group> out = new ArrayList<>();
        boolean placed = false;
        for (final Group other : groups) {
            if (other.mode().equals(group.mode()) || other.name().equals(group.name())) {
                if (!placed) {
                    out.add(group);
                    placed = true;
                }
            } else {
                final List<String> kept = other.sourceTypes().stream()
                        .filter(type -> !group.sourceTypes().contains(type))
                        .toList();
                out.add(new Group(other.name(), other.mode(), other.href(), kept));
            }
        }
        if (!placed) {
            out.add(group);
        }
        return new DispatcherSpec(feed, mode, input, field, helpers, rows, out, events);
    }

    /// @param helpers The helpers module's href.
    /// @return This spec importing that.
    public DispatcherSpec withHelpers(final String helpers) {
        return new DispatcherSpec(feed, mode, input, field, helpers, rows, groups, events);
    }

    /// @param version  The event-logging version the Events are written in.
    /// @param location Their xsi:schemaLocation (the namespace, then the schema's system id).
    /// @return This spec, saying so.
    public DispatcherSpec withEvents(final String version, final String location) {
        final Map<String, String> written = new LinkedHashMap<>();
        written.put("version", version);
        written.put("namespace", location.split(" ", 2)[0]);
        written.put("location", location);
        return new DispatcherSpec(feed, mode, input, field, helpers, rows, groups, written);
    }


    // --------------------------------------------------------------------------------


    /// A registry row, as the dispatcher tries it.
    ///
    /// @param name   The source type's name.
    /// @param match  `value` or `pattern`.
    /// @param value  The value matched exactly, or the anchored pattern.
    /// @param status Its status in the registry (`translated`, `parked`, ...), for the dispatcher's comments.
    public record Row(String name, String match, String value, String status) {

        /// @return It as JSON.
        Map<String, Object> toJson() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("name", name);
            out.put("match", match);
            out.put("value", value);
            out.put("status", status);
            return out;
        }
    }

    /// A source type group the dispatcher imports.
    ///
    /// @param name        Its name.
    /// @param mode        The mode its records are handed to.
    /// @param href        Its import (`uuid='…', name='…'`); null for one inlined only.
    /// @param sourceTypes The source types (row names) it translates.
    public record Group(String name, String mode, String href, List<String> sourceTypes) {

        /// Fills in an empty list for a null one.
        public Group {
            sourceTypes = List.copyOf(Objects.requireNonNullElse(sourceTypes, List.of()));
        }

        /// @return It as JSON.
        Map<String, Object> toJson() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("name", name);
            out.put("mode", mode);
            out.put("href", href);
            out.put("source_types", sourceTypes);
            return out;
        }
    }
}
