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
import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.generation.Extraction;
import stroom.ai.impl.tools.generation.MappingFacts;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.ToolJson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// Whether a draft's records are recognised by conditions, by the one evaluator ([LocalCheck#holds]) over the
/// records as the draft reads them: for placing the catalogue's ways of recognising event types (which variant's
/// records they recognise, which of the draft's own rules took those records, which fields they hold). The draft
/// holds a JSON array as its text; it is read back as the array, so its items are compared as processing compares
/// them. A condition that can't be told here (an xpath, a dictionary, a record-scope field) is taken as holding.
final class WayCheck {

    // The mapping keys that say how records are read, kept from the draft's mapping.
    private static final List<String> READING = List.of("input", "json_layout", "json_fields", "xml_fields",
            "nil_values", "xml_namespace", "root", "record", "extract", "for_each");

    private final TranslationMapping mapping;
    private final MappingConditions conditions;
    private final boolean json;
    // The fields the extractions read from the record (not those an earlier one produces), in order.
    private final List<String> sources;
    // The extracted values by the texts they are read from: each record's worked out once however many ways test
    // it (seen in eval case 36 on Haiku: 60 extract entries run again for each of 33 ways over 1,000 records, 30 s
    // of a draft), and never another text's.
    private final Map<List<String>, Optional<Map<String, String>>> extracted = new HashMap<>();

    private WayCheck(final TranslationMapping mapping) {
        this.mapping = mapping;
        this.conditions = MappingConditions.of(mapping);
        this.json = "json".equals(mapping.input());
        this.sources = sources(mapping);
    }

    /// @param draftMapping The draft's mapping, as JSON-like values: how its records are read (input, held fields,
    ///                     nil values, extract), and its common entries (the transforms decoding a field).
    /// @return A check over its records.
    static WayCheck of(final Map<String, Object> draftMapping) {
        final Map<String, Object> json = new LinkedHashMap<>();
        READING.forEach(key -> {
            if (draftMapping.get(key) != null) {
                json.put(key, draftMapping.get(key));
            }
        });
        json.put("events", List.of(Map.of("name", "check", "allow_unknown", "a check",
                "fields", List.of(Map.of("path", "EventDetail/TypeId", "value", "Check")))));
        TranslationMapping read = null;
        if (draftMapping.get("common") != null) {
            final Map<String, Object> withCommon = new LinkedHashMap<>(json);
            withCommon.put("common", draftMapping.get("common"));
            read = parsed(withCommon);
        }
        if (read == null) {
            read = parsed(json);
        }
        if (read == null) {
            json.keySet().retainAll(List.of("input", "events"));
            read = parsed(json);
        }
        return new WayCheck(read);
    }

    private static TranslationMapping parsed(final Map<String, Object> json) {
        try {
            return TranslationMapping.fromJson(json);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    /// @param when   Conditions (all must hold).
    /// @param record A record, as the draft reads them.
    /// @return Whether they all hold for it (one that can't be told here taken as holding).
    boolean recognises(final List<Condition> when, final Map<String, Object> record) {
        final Object read = asRead(record);
        final Map<String, String> values = extractedValues(read);
        for (final Condition condition : when) {
            if (Boolean.FALSE.equals(conditions.holds(condition, read, values))) {
                return false;
            }
        }
        return true;
    }

    // A record's extracted values, as LocalCheck.extractedValues gives them (not to be changed); null without
    // extract entries.
    private Map<String, String> extractedValues(final Object read) {
        if (mapping.extract().isEmpty()) {
            return null;
        }
        final List<String> texts = new ArrayList<>(sources.size());
        sources.forEach(field -> texts.add(LocalCheck.valueOf(read, field)));
        return extracted.computeIfAbsent(texts, key -> Optional.ofNullable(LocalCheck.extractedValues(mapping, read))
                .map(Collections::unmodifiableMap)).orElse(null);
    }

    // The fields the extractions read from a record, as LocalCheck.extractedValues reads them: each extraction's
    // field, unless an earlier one produces it.
    private static List<String> sources(final TranslationMapping mapping) {
        final Set<String> produced = new HashSet<>();
        final Set<String> read = new LinkedHashSet<>();
        for (final Extraction ex : mapping.extract()) {
            if (ex.field() != null && !produced.contains(ex.field())) {
                read.add(ex.field());
            }
            ex.names().stream().filter(name -> name != null && !name.isEmpty()).forEach(produced::add);
        }
        return List.copyOf(read);
    }

    /// @param when    Conditions (all must hold).
    /// @param records Records, as the draft reads them.
    /// @return Those they recognise, in order.
    List<Map<String, Object>> recognised(final List<Condition> when, final List<Map<String, Object>> records) {
        return records.stream().filter(record -> recognises(when, record)).toList();
    }

    /// @param when A rule's `when`, as JSON-like values.
    /// @return Its conditions; null when one doesn't read.
    static List<Condition> conditions(final Object when) {
        final List<Condition> out = new ArrayList<>();
        for (final Map<String, Object> condition : JsonValues.maps(when)) {
            try {
                out.add(Condition.fromJson(condition));
            } catch (final IllegalArgumentException e) {
                return null;
            }
        }
        return out;
    }

    // A record as the evaluator reads it: a JSON field holding an array's text read back as the array.
    private Map<String, Object> asRead(final Map<String, Object> record) {
        Map<String, Object> out = record;
        for (final Map.Entry<String, Object> entry : record.entrySet()) {
            if (entry.getValue() instanceof final String text && text.startsWith("[") && isJsonField(entry.getKey())) {
                final Object array = array(text);
                if (array != null) {
                    if (out == record) {
                        out = new LinkedHashMap<>(record);
                    }
                    out.put(entry.getKey(), array);
                }
            }
        }
        return out;
    }

    private boolean isJsonField(final String field) {
        return json || MappingFacts.heldJsonField(mapping.jsonFields(), field) != null;
    }

    private static Object array(final String text) {
        try {
            final Object parsed = ToolJson.loads(text);
            return parsed instanceof List
                    ? parsed
                    : null;
        } catch (final ToolJson.JsonDecodeException e) {
            return null;
        }
    }
}
