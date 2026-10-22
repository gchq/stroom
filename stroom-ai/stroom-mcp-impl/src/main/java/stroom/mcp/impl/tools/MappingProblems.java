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

import stroom.ai.impl.tools.generation.MappingError;
import stroom.ai.impl.tools.generation.MappingValidationException;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.util.json.JsonUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/// What is wrong with a translation mapping that doesn't validate, for the agent (the Stroom MCP server's
/// `tools/generation.py` `mapping_problems`, `_described`, `MAPPING_EXAMPLE` and `ESCAPING`): each error where it
/// is (`events[2].fields[0].path`) with the value given, and for a key left out, what the key is.
///
/// What each key is comes from the server's models' own descriptions (`mapping-descriptions.json`, made from them).
final class MappingProblems {

    /// How a regex or xpath travels: written once, as XPath reads it; the call's JSON doubles each backslash; the
    /// tools write it into the XSLT as it is. Seen: agents escaping again for XSLT and for JSON, a hundred times
    /// over.
    static final String ESCAPING = "Regexes and xpaths are written once, as XPath reads them (a literal [ in a "
                                   + "regex is \\[). Only the JSON of your call doubles each backslash (\\[ is "
                                   + "\"\\\\[\" in JSON); the server puts them in the XSLT as they are, so nothing "
                                   + "escapes them again.";
    /// The shape of a mapping, whole, for a reply that refuses one: seen, a mapping sent without input and a rule
    /// without name, after the agent guessed the structure from the description.
    static final Map<String, Object> MAPPING_EXAMPLE = example();

    private static final Set<String> STRUCTURAL = Set.of("missing", "model_type", "model_attributes_type",
            "list_type", "dict_type");
    private static final String TOP = "TranslationMapping";
    private static final Map<String, Map<String, Map<String, Object>>> MODELS = models();

    private MappingProblems() {
        // Static only.
    }

    /// @param e           Why the mapping doesn't validate.
    /// @param unknownKeys The keys given that a mapping has no place for.
    /// @return Its errors (the first 8), each where it is, with the value given (or, missing, what the key is).
    static List<String> problems(final MappingValidationException e, final List<String> unknownKeys) {
        final List<String> out = new ArrayList<>();
        final List<MappingError> errors = e.errors();
        for (final MappingError error : errors.subList(0, Math.min(8, errors.size()))) {
            final String where = error.where();
            final Object given = error.input();
            String shown = given == null || given instanceof Map || given instanceof List
                    ? ""
                    : " (given " + cut(PyJson.dumps(given instanceof String || given instanceof Number
                                                    || given instanceof Boolean
                    ? given
                    : String.valueOf(given)), 80) + ")";
            if ("missing".equals(error.type())) {
                // "input: Field required" said nothing of what input is.
                final String about = described(error.loc());
                shown = about == null
                        ? ""
                        : " (" + about + ")";
            }
            out.add((where.isEmpty()
                    ? "mapping"
                    : where) + ": " + error.message() + shown);
        }
        if (errors.size() > 8) {
            out.add("and " + (errors.size() - 8) + " more");
        }
        if (!unknownKeys.isEmpty()) {
            out.add("keys that are not part of a mapping: " + SampleArguments.pythonList(unknownKeys) + " (it has "
                    + SampleArguments.pythonList(mappingKeys()) + ")");
        }
        return out;
    }

    /// @param e Why the mapping doesn't validate.
    /// @return True if its shape is what's wrong (a key missing or misplaced), not a value: the whole shape is then
    /// shown.
    static boolean structural(final MappingValidationException e) {
        return e.errors().stream().anyMatch(error -> STRUCTURAL.contains(error.type())
                                                     || error.message().contains("holds more than a path"));
    }

    /// @return The keys a mapping has, sorted.
    static List<String> mappingKeys() {
        return List.copyOf(new TreeSet<>(MODELS.get(TOP).keySet()));
    }

    /// @param loc Where a key is, e.g. `["events", 1, "name"]`.
    /// @return The description of the mapping key there, cut near 160 characters, or null when there is none.
    static String described(final List<Object> loc) {
        String model = TOP;
        Map<String, Object> info = null;
        for (final Object part : loc) {
            if (part instanceof Integer) {
                continue;
            }
            final Map<String, Map<String, Object>> fields = model == null
                    ? null
                    : MODELS.get(model);
            if (fields == null || !fields.containsKey(String.valueOf(part))) {
                return null;
            }
            info = fields.get(String.valueOf(part));
            model = info.get("model") == null
                    ? null
                    : String.valueOf(info.get("model"));
        }
        final String text = info == null || info.get("description") == null
                ? null
                : String.valueOf(info.get("description"));
        if (text == null || text.isEmpty() || text.length() <= 160) {
            return text == null || text.isEmpty()
                    ? null
                    : text;
        }
        final String start = text.substring(0, 150);
        final int space = start.lastIndexOf(' ');
        return (space < 0
                ? start
                : start.substring(0, space)) + " …";
    }

    private static String cut(final String text, final int max) {
        return text.length() <= max
                ? text
                : text.substring(0, max);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Map<String, Object>>> models() {
        try (final InputStream in = MappingProblems.class.getResourceAsStream("mapping-descriptions.json")) {
            if (in == null) {
                throw new IllegalStateException("mapping-descriptions.json is missing");
            }
            return JsonUtil.readValue(new String(in.readAllBytes(), StandardCharsets.UTF_8),
                    Map.class);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, Object> example() {
        final Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("input", "xml_fragments");
        mapping.put("record", "Event");
        mapping.put("xml_namespace", "records:2");
        mapping.put("common", List.of(
                entry("path", "EventTime/TimeCreated", "xpath", "System/TimeCreated/@SystemTime",
                        "time_format", "yyyy-MM-dd'T'HH:mm:ss.SSSX"),
                entry("path", "EventSource/System/Name", "value", "AppAudit"),
                entry("path", "EventSource/System/Environment", "value", "Prod"),
                entry("path", "EventSource/Generator", "value", "AppAudit"),
                entry("path", "EventSource/Device/HostName", "field", "Computer")));
        mapping.put("events", List.of(entry("name", "view",
                "when", List.of(entry("field", "EventID", "equals", "4663")),
                "fields", List.of(entry("path", "EventDetail/TypeId", "field", "EventID"),
                        entry("path", "EventDetail/View/Document/Id", "xpath",
                                "EventData/Data[@Name=\"ObjectName\"]")))));
        return mapping;
    }

    private static Map<String, Object> entry(final Object... keysAndValues) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            entry.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
        }
        return entry;
    }
}
