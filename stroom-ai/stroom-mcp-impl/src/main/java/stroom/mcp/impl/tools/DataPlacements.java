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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.validation.EventSchema;

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The fields a draft carries as Data, beside the schema elements that could hold them instead: EventSource's, and
/// each rule's action element's. The agent places each where one means it, keeping Data only for those with none
/// (asked for by the user: seen in testing, Netskope's Network/Connect events carried a city, a latitude and
/// longitude, a rule name, a URL, an HTTP method and a status code as Data, though the schema has elements for each,
/// and the agent mapped them once the user asked). Generic: the schema's own element names, no list of field names.
final class DataPlacements {

    // How deep below EventSource or an action element the elements are listed.
    private static final int DEPTH = 4;
    // The most elements listed under one root.
    private static final int MAX_ELEMENTS = 80;
    private static final String DATA = "Data";
    private static final String EVENT_DETAIL = "EventDetail/";

    private DataPlacements() {
        // Static only.
    }

    /// @param mapping A drafted mapping: `common` and `events`, each entry a `path`, and a Data entry's `data_name`.
    /// @param schema  The event-logging schema the mapping is for.
    /// @return `event_source` (EventSource's elements, below it) and `rules` (per rule carrying Data: `rule`,
    /// `data` (its Data entries' names), `action` and `action_elements`); empty when nothing is carried as Data.
    static Map<String, Object> of(final Map<String, Object> mapping, final EventSchema schema) {
        final List<Map<String, Object>> rules = new ArrayList<>();
        final List<String> commonData = dataNames(JsonValues.maps(mapping.get("common")));
        if (!commonData.isEmpty()) {
            rules.add(rule("common", commonData, null, schema));
        }
        for (final Map<String, Object> rule : JsonValues.maps(mapping.get("events"))) {
            final List<Map<String, Object>> fields = JsonValues.maps(rule.get("fields"));
            final List<String> data = dataNames(fields);
            if (!data.isEmpty()) {
                rules.add(rule(String.valueOf(rule.get("name")), data, action(fields), schema));
            }
        }
        if (rules.isEmpty()) {
            return Map.of();
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("event_source", elements(schema, "EventSource"));
        out.put("rules", rules);
        return out;
    }

    private static Map<String, Object> rule(final String name,
                                            final List<String> data,
                                            final String action,
                                            final EventSchema schema) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("rule", name);
        out.put("data", data);
        if (action != null) {
            out.put("action", action);
            out.put("action_elements", elements(schema, EVENT_DETAIL + action));
        }
        return out;
    }

    // The names of the Data entries among a rule's fields.
    private static List<String> dataNames(final List<Map<String, Object>> fields) {
        final Set<String> names = new LinkedHashSet<>();
        for (final Map<String, Object> field : fields) {
            final String path = String.valueOf(field.get("path"));
            if ((path.equals(DATA) || path.endsWith("/" + DATA)) && field.get("data_name") != null) {
                names.add(String.valueOf(field.get("data_name")));
            }
        }
        return new ArrayList<>(names);
    }

    // The action element a rule's fields write (EventDetail/Network/... gives Network), or null.
    private static String action(final List<Map<String, Object>> fields) {
        for (final Map<String, Object> field : fields) {
            final String path = String.valueOf(field.get("path"));
            if (path.startsWith(EVENT_DETAIL)) {
                final String rest = path.substring(EVENT_DETAIL.length());
                final int slash = rest.indexOf('/');
                return slash < 0
                        ? rest
                        : rest.substring(0, slash);
            }
        }
        return null;
    }

    // The leaf elements below a path, as paths relative to it (Device/Location/City), Data aside; none when the
    // schema has no such path.
    private static List<String> elements(final EventSchema schema, final String path) {
        final List<String> out = new ArrayList<>();
        try {
            final List<EventSchema.Child> chain = schema.resolve(path);
            walk(schema, chain.getLast().decl(), "", 1, out);
        } catch (final IllegalArgumentException e) {
            // Not an element of this schema: nothing to offer.
        }
        return out;
    }

    private static void walk(final EventSchema schema,
                             final Element decl,
                             final String prefix,
                             final int depth,
                             final List<String> out) {
        for (final EventSchema.Child child : schema.children(decl)) {
            if (out.size() >= MAX_ELEMENTS) {
                return;
            }
            if (DATA.equals(child.name())) {
                continue;
            }
            final String path = prefix + child.name();
            if (schema.isLeaf(child.decl())) {
                out.add(path);
            } else if (depth < DEPTH) {
                walk(schema, child.decl(), path + "/", depth + 1, out);
            }
        }
    }
}
