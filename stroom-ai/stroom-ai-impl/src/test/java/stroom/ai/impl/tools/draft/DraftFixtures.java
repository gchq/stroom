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
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.HeldSchemasWorld;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// What the draft mapping's tests share: the event-logging schemas, JSON reading, and the established results kept
/// as fixtures.
class DraftFixtures {

    /// The event-logging 4.1.0 schema.
    static final EventSchema SCHEMA = EventSchema.parse(HeldSchemasWorld.XSD_4_1_0);
    /// The event-logging 3.5.2 schema.
    static final EventSchema SCHEMA_352 = EventSchema.parse(
            resource("/stroom/ai/impl/tools/generation/event-logging-v3.5.2.xsd"));

    private static List<Map<String, Object>> drafts;
    private static List<Map<String, Object>> checks;

    DraftFixtures() {
        // Static only, and the base of the draft tests, which use its fixtures unqualified.
    }

    /// @param name A draft case's name (`draft_cases.json`), e.g. `format cef` or
    ///             `21_csv_connections_odd_fields (excerpt)`.
    /// @return The samples it drafts from, as the draft is given them.
    static synchronized Object samples(final String name) {
        if (drafts == null) {
            drafts = cases("draft_cases.json");
        }
        return drafts.stream().filter(entry -> name.equals(entry.get("name"))).findFirst()
                .map(entry -> entry.get("samples")).orElseThrow();
    }

    /// @param name A sample check case's name (`localcheck_cases.json`), e.g.
    ///             `12_xml_fragments_events reference`.
    /// @return The case: its `mapping`, `texts` and `splitter`.
    static synchronized Map<String, Object> check(final String name) {
        if (checks == null) {
            checks = cases("localcheck_cases.json");
        }
        return checks.stream().filter(entry -> name.equals(entry.get("name"))).findFirst().orElseThrow();
    }

    /// @param name A format sample's name, e.g. `cef`.
    /// @return Its text.
    @SuppressWarnings("unchecked")
    static String format(final String name) {
        return ((List<String>) samples("format " + name)).getFirst();
    }

    /// @param name A resource next to this class.
    /// @return Its text.
    static String resource(final String name) {
        try (final InputStream in = DraftFixtures.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("no resource " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// @param text JSON.
    /// @param <T>  What it is read as.
    /// @return It read by [ToolJson].
    @SuppressWarnings("unchecked")
    static <T> T json(final String text) {
        return (T) ToolJson.loads(text);
    }

    /// @param name A resource next to this class holding a JSON list of objects.
    /// @return The objects.
    static List<Map<String, Object>> cases(final String name) {
        return json(resource(name));
    }

    /// @param keysAndValues Keys and their values, in turn.
    /// @return A mutable map of them, in that order.
    static Map<String, Object> map(final Object... keysAndValues) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            out.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return out;
    }

    /// @param json A spec's JSON, or null.
    /// @return The spec, or null.
    @SuppressWarnings("unchecked")
    static SplitterSpec spec(final Object json) {
        return json instanceof final Map<?, ?> map
                ? SplitterSpec.fromMap((Map<String, ?>) map)
                : null;
    }

    /// @param json A spec's JSON.
    /// @return The spec.
    static SplitterSpec specOf(final String json) {
        return spec((Object) json(json));
    }

    /// @param json A mapping's JSON.
    /// @return The mapping, validated.
    static TranslationMapping mapping(final Object json) {
        return TranslationMapping.parse(json);
    }

    /// @param draft A draft ([DraftMapping#draft]).
    /// @return Its mapping's JSON.
    @SuppressWarnings("unchecked")
    static Map<String, Object> mappingOf(final Map<String, Object> draft) {
        return (Map<String, Object>) draft.get("mapping");
    }

    /// @param draft A draft.
    /// @return Its rules, by name.
    static Map<String, Map<String, Object>> rules(final Map<String, Object> draft) {
        final Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        JsonValues.maps(mappingOf(draft).get("events")).forEach(rule -> out.put((String) rule.get("name"), rule));
        return out;
    }

    /// @param draft A draft.
    /// @return Its common entries, by path.
    static Map<String, Map<String, Object>> common(final Map<String, Object> draft) {
        final Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        JsonValues.maps(mappingOf(draft).get("common")).forEach(entry -> out.put((String) entry.get("path"), entry));
        return out;
    }

    /// @param rule A rule.
    /// @return Its fields, by path (the last of a path standing).
    static Map<String, Map<String, Object>> fields(final Map<String, Object> rule) {
        final Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        JsonValues.maps(rule.get("fields")).forEach(entry -> out.put((String) entry.get("path"), entry));
        return out;
    }

    /// @param draft A draft.
    /// @return Its notes.
    @SuppressWarnings("unchecked")
    static List<String> notes(final Map<String, Object> draft) {
        return (List<String>) draft.get("notes");
    }

    /// @param texts Texts.
    /// @return A mutable list of them.
    static List<String> list(final String... texts) {
        return new ArrayList<>(List.of(texts));
    }

    /// Each rule's Data as it is drafted now, from what the rule's own records hold, where the fixtures give every
    /// rule the whole rest (see DraftMapping's held): checks the draft's is a subset of the fixture's, then cuts the
    /// fixture's to it, so the rest of the draft compares as it was.
    ///
    /// @param drafted  The mapping drafted now.
    /// @param recorded The fixture's mapping, cut.
    @SuppressWarnings("unchecked")
    static void asTheRulesRecordsHold(final Object drafted, final Object recorded) {
        if (!(drafted instanceof final Map<?, ?> draftedMapping)
            || !(recorded instanceof final Map<?, ?> recordedMapping)
            || !(draftedMapping.get("events") instanceof final List<?> draftedRules)
            || !(recordedMapping.get("events") instanceof final List<?> recordedRules)
            || draftedRules.size() != recordedRules.size()) {
            return;
        }
        for (int i = 0; i < draftedRules.size(); i++) {
            final List<Object> draftedFields = (List<Object>) ((Map<String, Object>) draftedRules.get(i))
                    .get("fields");
            final List<Object> recordedFields = (List<Object>) ((Map<String, Object>) recordedRules.get(i))
                    .get("fields");
            if (draftedFields == null || recordedFields == null) {
                continue;
            }
            final List<Object> draftedData = draftedFields.stream()
                    .filter(f -> ((Map<?, ?>) f).containsKey("data_name"))
                    .toList();
            assertThat(recordedFields).as("rule %s's Data, as recorded", i)
                    .containsAll(draftedData);
            recordedFields.removeIf(f -> ((Map<?, ?>) f).containsKey("data_name") && !draftedData.contains(f));
        }
    }
}
