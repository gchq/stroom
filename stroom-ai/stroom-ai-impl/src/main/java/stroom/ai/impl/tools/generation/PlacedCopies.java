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
import stroom.ai.impl.tools.ToolText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// A field written as it is to an element of the event (`EventSource/Device/HostName` from `host`) that a rule also
/// carries as Data, named after it: the Data a copy of what the element holds. The draft carries each field it
/// doesn't place as Data, until the agent places it; seen in chat 194, `prefix2` placed at Device/HostName by the
/// agent's change stayed Data in all 12 rules, and took four more builds to remove entry by entry. So a placement
/// takes the field's Data copies with it: those of the rules it reaches (a common entry reaches every rule that
/// doesn't write the element from another field, a rule's entry its own rule), and the common ones for a common
/// entry.
/// Only plain entries count: the field read as it is, or given in the element's terms (a time format, a lookup, a
/// dictionary or a default); a transform or a map ([MappingFacts#keepsOriginal(Map)], as the
/// catalogue's placements decide it) leaves the original worth keeping, and a Data entry given in the same changes is
/// kept on purpose. An xpath giving one field as it is ([XPathFieldReads#plainField(String)], a key of the JSON a field
/// holds) places that field. A copy is a Data entry reading the same field, whatever its Data is named (the data_names
/// styling, or a vendor code's short name).
final class PlacedCopies {

    private static final String COMMON = "common";
    private static final String EVENTS = "events";
    private static final String FIELDS = "fields";
    private static final String DATA = "data";
    private static final String NAME = "name";
    private static final String PATH = "path";
    private static final String FIELD = "field";
    private static final String DATA_NAME = "data_name";
    private static final String TYPE_ID = "EventDetail/TypeId";
    private static final String XPATH = "xpath";
    // The conversions giving the field in the element's terms: they take its Data copy's place as a plain read does;
    // a transform or a map keeps it ([MappingFacts#keepsOriginal(Map)]; decided by the user 2026-11-27).
    private static final Set<String> CONVERSIONS = Set.of("time_format", "lookup", "dictionary", "default");
    private static final Set<String> PLACEMENT_KEYS = Stream.concat(Stream.of(PATH, FIELD), CONVERSIONS.stream())
            .collect(Collectors.toUnmodifiableSet());
    private static final Set<String> XPATH_PLACEMENT_KEYS = Stream.concat(Stream.of(PATH, XPATH),
            CONVERSIONS.stream()).collect(Collectors.toUnmodifiableSet());
    private static final Set<String> COPY_KEYS = Set.of(PATH, FIELD, DATA_NAME, "data_parent");

    private PlacedCopies() {
        // Static only.
    }

    /// Removes the Data copies of the fields that changes place, from the merged mapping.
    ///
    /// @param merged The mapping with the changes merged in, changed in place.
    /// @param given  The changes, as given (`common`, `events`).
    /// @return What was removed, a line per field placed, for the changes' record of what each did.
    static List<String> afterChanges(final Map<String, Object> merged, final Map<String, Object> given) {
        final List<Placement> placements = new ArrayList<>();
        final Set<List<String>> kept = new HashSet<>();
        for (final Map<String, Object> entry : JsonValues.maps(given.get(COMMON))) {
            placed(entry, null, placements, kept);
        }
        for (final Map<String, Object> rule : JsonValues.maps(given.get(EVENTS))) {
            if (JsonValues.truthy(rule.get("remove"))) {
                continue;
            }
            final String name = JsonValues.text(rule.get(NAME));
            for (final Map<String, Object> entry : JsonValues.maps(rule.get(FIELDS))) {
                placed(entry, name, placements, kept);
            }
            if (rule.get(DATA) instanceof final List<?> data) {
                data.forEach(field -> kept.add(List.of(name, String.valueOf(field))));
            }
        }
        return drop(merged, placements, kept);
    }

    // An entry given: a placement of a field as it is, or a Data entry kept (by the rule, or null for common).
    private static void placed(final Map<String, Object> entry,
                               final String rule,
                               final List<Placement> placements,
                               final Set<List<String>> kept) {
        if (JsonValues.truthy(entry.get("remove"))) {
            return;
        }
        if (entry.get(FIELD) instanceof final String field && !field.isEmpty()) {
            if (plainPlacement(entry, PLACEMENT_KEYS)) {
                placements.add(new Placement(rule, field, stripped(entry)));
            } else if (JsonValues.truthy(entry.get(DATA_NAME))) {
                kept.add(List.of(Objects.requireNonNullElse(rule, COMMON), field));
            }
        } else if (entry.get(XPATH) instanceof final String xpath && plainPlacement(entry, XPATH_PLACEMENT_KEYS)) {
            // A field read as it is by an xpath (a key of the JSON a field holds, read through json-to-xml()).
            final String field = XPathFieldReads.plainField(xpath);
            if (field != null) {
                placements.add(new Placement(rule, field, stripped(entry)));
            }
        }
    }

    private static List<String> drop(final Map<String, Object> mapping,
                                     final List<Placement> placements,
                                     final Set<List<String>> kept) {
        final List<String> out = new ArrayList<>();
        for (final Placement placement : placements) {
            final List<String> from = new ArrayList<>();
            if (placement.rule() == null && !kept.contains(List.of(COMMON, placement.field()))
                && dropCopies(mapping, COMMON, placement.field())) {
                from.add(COMMON);
            }
            for (final Map<String, Object> rule : JsonValues.maps(mapping.get(EVENTS))) {
                final String name = JsonValues.text(rule.get(NAME));
                final boolean reached = placement.rule() == null
                        ? !writesOtherwise(rule, placement)
                        : placement.rule().equals(name);
                if (reached && !kept.contains(List.of(name, placement.field())) && dropFrom(rule,
                        placement.field())) {
                    from.add("rule " + name);
                }
            }
            if (!from.isEmpty()) {
                out.add(ToolText.repr(placement.field()) + " is written as " + placement.path() + ", so its Data "
                        + "entries were removed (" + String.join(", ", from) + "): the element holds it. Send one "
                        + "back (changes=) only if it is kept on purpose.");
            }
        }
        return out;
    }

    // The rule's Data copies of the field removed (its fields' entries and its data list); whether any were.
    private static boolean dropFrom(final Map<String, Object> rule, final String field) {
        final boolean fromFields = dropCopies(rule, FIELDS, field);
        if (rule.get(DATA) instanceof final List<?> data && data.contains(field)) {
            rule.put(DATA, new ArrayList<>(data.stream().filter(name -> !field.equals(name)).toList()));
            return true;
        }
        return fromFields;
    }

    // The entries at a key with the field's Data copies left out; whether any were.
    private static boolean dropCopies(final Map<String, Object> holder, final String key, final String field) {
        if (!(holder.get(key) instanceof final List<?> entries)) {
            return false;
        }
        final List<Object> left = new ArrayList<>(entries.stream()
                .filter(entry -> !(entry instanceof final Map<?, ?> map && copyOf(map, field)))
                .toList());
        if (left.size() == entries.size()) {
            return false;
        }
        holder.put(key, left);
        return true;
    }

    // A rule writing the placement's element from something else: a common entry doesn't reach it there.
    private static boolean writesOtherwise(final Map<String, Object> rule, final Placement placement) {
        return JsonValues.maps(rule.get(FIELDS)).stream()
                .anyMatch(entry -> !JsonValues.truthy(entry.get(DATA_NAME)) && placement.path().equals(
                        stripped(entry)) && !placement.field().equals(plainRead(entry)));
    }

    private static boolean plainPlacement(final Map<String, Object> entry, final Set<String> keys) {
        final String path = stripped(entry);
        return !JsonValues.truthy(entry.get(DATA_NAME)) && !path.isEmpty() && !path.endsWith("/Data")
               && !TYPE_ID.equals(path) && !MappingFacts.keepsOriginal(entry) && keys.containsAll(entry.keySet());
    }

    // A Data entry reading the field as it is, whatever its Data is named (the draft's data_names styling, or a
    // vendor code's short name, names it apart from the field).
    private static boolean copyOf(final Map<?, ?> entry, final String field) {
        return field.equals(entry.get(FIELD)) && JsonValues.truthy(entry.get(DATA_NAME))
               && COPY_KEYS.containsAll(entry.keySet());
    }

    // The field an entry reads as it is: its field, or the one its xpath gives as it is; else null.
    private static String plainRead(final Map<String, Object> entry) {
        if (entry.get(FIELD) instanceof final String field) {
            return field;
        }
        return entry.get(XPATH) instanceof final String xpath
                ? XPathFieldReads.plainField(xpath)
                : null;
    }

    private static String stripped(final Map<?, ?> entry) {
        final String path = JsonValues.text(entry.get(PATH));
        return path.startsWith("Event/")
                ? path.substring("Event/".length())
                : path;
    }


    // --------------------------------------------------------------------------------


    // A field written as it is to an element, by a rule's entry (rule named) or a common one (rule null).
    private record Placement(String rule, String field, String path) {

    }
}
