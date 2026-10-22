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

import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.ai.impl.tools.sample.SplitterDryRun;
import stroom.ai.impl.tools.sample.SplitterSpec;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The Stroom MCP server's own results, kept as fixtures, against the port's: `draft_mapping` over every
/// evaluation case's samples (as the user shows them, with the case's own splitter where it has one, and the first
/// lines of its files) and `dev/format_samples.py`'s; `sample_records`, `check_mapping` and `check_schema_values`
/// for each case's reference mapping and for each draft; and `generate_splitter` for every spec those infer or
/// give. The fixtures were made by running the Python server's code over the same samples.
class TestDraftParity extends DraftFixtures {

    private static final List<Map<String, Object>> DRAFTS = cases("draft_parity.json");

    @TestFactory
    Stream<DynamicTest> theDraftsAreThePythonServers() {
        return DRAFTS.stream().map(entry -> DynamicTest.dynamicTest((String) entry.get("name"), () -> {
            final Map<String, Object> expected = result(entry);
            final SplitterSpec splitter = spec(entry.get("splitter"));
            if (expected.containsKey("error")) {
                assertThatThrownBy(() -> drafted(entry, splitter)).isInstanceOf(RuntimeException.class);
                return;
            }
            final Map<String, Object> actual = drafted(entry, splitter);
            assertThat(PyJson.dumps(inCatalogueOrder(actual), 1))
                    .isEqualTo(PyJson.dumps(inCatalogueOrder(expected), 1));
        }));
    }

    @TestFactory
    Stream<DynamicTest> theSampleChecksAreThePythonServers() {
        final Map<String, Object> samples = new LinkedHashMap<>();
        DRAFTS.forEach(entry -> samples.put((String) entry.get("name"), entry.get("samples")));
        return cases("localcheck_parity.json").stream()
                .map(entry -> DynamicTest.dynamicTest((String) entry.get("name"), () -> {
                    final Map<String, Object> expected = result(entry);
                    final String error = (String) expected.get("error");
                    if (error != null && error.startsWith("AttributeError")) {
                        // A number in a JSON record where the Python server read text: it failed, the port reads
                        // the number's text.
                        return;
                    }
                    final List<String> texts = entry.containsKey("texts")
                            ? texts(entry.get("texts"))
                            : texts(samples.get((String) entry.get("draft")));
                    if (error != null) {
                        assertThatThrownBy(() -> checked(entry, texts)).isInstanceOf(RuntimeException.class);
                        return;
                    }
                    assertThat(PyJson.dumps(checked(entry, texts), 1)).isEqualTo(PyJson.dumps(expected, 1));
                }));
    }

    @TestFactory
    Stream<DynamicTest> theSplittersAreThePythonServers() {
        return cases("splitter_parity.json").stream()
                .map(entry -> DynamicTest.dynamicTest((String) entry.get("name"), () -> {
                    final SplitterSpec spec = spec(entry.get("spec"));
                    assertThat(SplitterXml.generateSplitter(spec)).isEqualTo(entry.get("xml"));
                }));
    }

    // The values the catalogue doesn't list, sorted: the Python server listed them in the order of a set of the
    // catalogue's fields, which differs from run to run; the port lists them in the catalogue's order.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> inCatalogueOrder(final Map<String, Object> draft) {
        if (!(draft.get("source_notes") instanceof final Map<?, ?> applied)) {
            return draft;
        }
        final List<String> missing = new ArrayList<>((List<String>) applied.get("not_in_catalogue"));
        if (missing.isEmpty()) {
            return draft;
        }
        final String said = "The catalogue does not list " + String.join(", ", missing) + ":";
        final List<String> sorted = missing.stream().sorted().toList();
        ((Map<String, Object>) applied).put("not_in_catalogue", sorted);
        final List<String> notes = (List<String>) draft.get("notes");
        notes.replaceAll(note -> note.startsWith(said)
                ? "The catalogue does not list " + String.join(", ", sorted) + ":" + note.substring(said.length())
                : note);
        return draft;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> drafted(final Map<String, Object> entry, final SplitterSpec splitter) {
        final Function<String, String> detailCheck = Boolean.TRUE.equals(entry.get("detail_check"))
                ? detail -> SourceNotes.detailProblem(SCHEMA, detail)
                : null;
        return DraftMapping.draft(entry.get("samples"), (String) entry.get("source_name"),
                (String) entry.get("system_name"), (String) entry.get("environment"),
                (Map<String, Object>) entry.get("source_notes"), detailCheck, splitter);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(final Map<String, Object> entry) {
        return (Map<String, Object>) entry.get("result");
    }

    @SuppressWarnings("unchecked")
    private static List<String> texts(final Object samples) {
        if (samples instanceof final Map<?, ?> map) {
            return new ArrayList<>((Collection<String>) map.values());
        }
        if (samples instanceof final String text) {
            return new ArrayList<>(List.of(text));
        }
        return new ArrayList<>((List<String>) samples);
    }

    private static Map<String, Object> checked(final Map<String, Object> entry, final List<String> texts) {
        final TranslationMapping mapping = mapping(entry.get("mapping"));
        SplitterSpec spec = null;
        if ("data_splitter".equals(mapping.input())) {
            final Object given = entry.get("splitter");
            if ("infer".equals(given)) {
                spec = inferred(texts.getFirst());
            } else {
                spec = spec(given);
            }
        }
        final LocalCheck.SampleRecords records = LocalCheck.sampleRecords(mapping, texts, spec);
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("splitter", spec == null
                ? null
                : spec.toMap());
        out.put("note", records.note());
        out.put("records", records.records().size());
        out.put("check", LocalCheck.checkMapping(mapping, records.records()));
        out.put("schema_values", LocalCheck.checkSchemaValues(mapping, records.records(), SCHEMA));
        return out;
    }

    private static SplitterSpec inferred(final String text) {
        try {
            return SplitterDryRun.inferSpec(text).spec();
        } catch (final RuntimeException e) {
            return null;
        }
    }
}
