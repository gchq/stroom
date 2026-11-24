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

/// A field written as it is to an element of the event (`EventSource/Device/HostName` from `host`) that a rule also
/// carries as Data, named after it: the Data a copy of what the element holds. The draft carries each field it
/// doesn't place as Data, until the agent places it; seen in chat 194, `prefix2` placed at Device/HostName by the
/// agent's change stayed Data in all 12 rules, and took four more builds to remove entry by entry. So a placement
/// takes the field's Data copies with it: those of the rules it reaches (a common entry reaches every rule that
/// doesn't write the element from another field, a rule's entry its own rule), and the common ones for a common
/// entry.
/// Only plain entries count, the field read as it is: a placement that changes the value (a format, a map, a
/// default, a transform) leaves the original worth keeping, and a Data entry given in the same changes is kept on
/// purpose.
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
    private static final Set<String> PLACEMENT_KEYS = Set.of(PATH, FIELD);
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
            if (plainPlacement(entry)) {
                placements.add(new Placement(rule, field, stripped(entry)));
            } else if (JsonValues.truthy(entry.get(DATA_NAME))) {
                kept.add(List.of(Objects.requireNonNullElse(rule, COMMON), field));
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
                        stripped(entry)) && !placement.field().equals(entry.get(FIELD)));
    }

    private static boolean plainPlacement(final Map<String, Object> entry) {
        final String path = stripped(entry);
        return !JsonValues.truthy(entry.get(DATA_NAME)) && !path.isEmpty() && !path.endsWith("/Data")
               && !TYPE_ID.equals(path) && PLACEMENT_KEYS.containsAll(entry.keySet());
    }

    private static boolean copyOf(final Map<?, ?> entry, final String field) {
        return field.equals(entry.get(FIELD)) && field.equals(entry.get(DATA_NAME))
               && COPY_KEYS.containsAll(entry.keySet());
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
