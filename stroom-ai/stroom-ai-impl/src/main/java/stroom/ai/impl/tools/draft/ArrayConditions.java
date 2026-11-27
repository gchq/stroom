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

package stroom.ai.impl.tools.draft;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.generation.MappingFacts;
import stroom.ai.impl.tools.sample.ToolJson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// A drafted rule's conditions on a JSON array field written over the array's items, as the generated XPath compares
/// them (any item), not the array's JSON text the draft reads its records with. Seen in eval case 46 (2026-11-19):
/// `event.category equals '["authentication"]'`, which no record's items ever are.
///
/// * `equals` an array's text: one `equals` per distinct item (each must be among the record's items).
/// * `one_of` texts that are all one-item arrays: `one_of` their items.
///
/// Anything else (an array holding objects or arrays, a `one_of` of arrays of several items) is left as it is.
final class ArrayConditions {

    private ArrayConditions() {
        // Static only.
    }

    /// Rewrites the rules' and drop rules' conditions on JSON fields in place.
    ///
    /// @param mapping The draft's mapping: `input`, `json_fields`, `events` and `drop_when`.
    static void overItems(final Map<String, Object> mapping) {
        final boolean json = "json".equals(mapping.get("input"));
        final List<String> jsonFields = JsonValues.strings(mapping.get("json_fields"));
        final List<Map<String, Object>> rules = new ArrayList<>(JsonValues.maps(mapping.get("events")));
        rules.addAll(JsonValues.maps(mapping.get("drop_when")));
        for (final Map<String, Object> rule : rules) {
            if (rule.get("when") instanceof final List<?> when) {
                rule.put("when", overItems(JsonValues.maps(when), json, jsonFields));
            }
        }
    }

    /// @param when       Conditions.
    /// @param json       Whether the input is JSON.
    /// @param jsonFields The fields holding JSON text.
    /// @return The conditions, those on a JSON field holding an array's text written over its items.
    static List<Map<String, Object>> overItems(final List<Map<String, Object>> when,
                                               final boolean json,
                                               final List<String> jsonFields) {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final Map<String, Object> condition : when) {
            final String field = condition.get("field") instanceof final String name
                    ? name
                    : null;
            final boolean jsonField = field != null && condition.get("xpath") == null
                                      && (json || MappingFacts.heldJsonField(jsonFields, field) != null);
            if (!jsonField) {
                out.add(condition);
            } else if (condition.get("equals") instanceof final String text && items(text) != null) {
                for (final String item : new LinkedHashSet<>(items(text))) {
                    final Map<String, Object> one = new LinkedHashMap<>(condition);
                    one.put("equals", item);
                    out.add(one);
                }
            } else if (condition.get("one_of") instanceof final List<?> listed && oneItemEach(listed) != null) {
                final Map<String, Object> one = new LinkedHashMap<>(condition);
                one.put("one_of", oneItemEach(listed));
                out.add(one);
            } else {
                out.add(condition);
            }
        }
        return out;
    }

    // The items of a JSON array's text, each a string, number or boolean; null when it isn't one, or is empty.
    private static List<String> items(final String text) {
        if (!text.startsWith("[")) {
            return null;
        }
        final Object parsed;
        try {
            parsed = ToolJson.loads(text);
        } catch (final ToolJson.JsonDecodeException e) {
            return null;
        }
        if (!(parsed instanceof final List<?> list) || list.isEmpty()) {
            return null;
        }
        final List<String> out = new ArrayList<>();
        for (final Object item : list) {
            if (item == null || item instanceof Map || item instanceof List) {
                return null;
            }
            out.add(item instanceof final String string
                    ? string
                    : JsonValues.jsonText(item));
        }
        return out;
    }

    // The one item of each one-item array's text listed; null unless every one is.
    private static List<String> oneItemEach(final List<?> listed) {
        final Set<String> out = new LinkedHashSet<>();
        for (final Object each : listed) {
            final List<String> items = each instanceof final String text
                    ? items(text)
                    : null;
            if (items == null || items.size() != 1) {
                return null;
            }
            out.add(items.getFirst());
        }
        return new ArrayList<>(out);
    }
}
