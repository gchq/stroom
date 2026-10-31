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

package stroom.mcp.impl.e2e;

import stroom.docref.DocRef;
import stroom.index.impl.IndexFieldService;
import stroom.index.shared.IndexFieldImpl;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.UpdateField;
import stroom.mcp.impl.McpTestClient;
import stroom.query.api.datasource.AnalyzerType;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.IndexField;
import stroom.util.shared.PageRequest;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// Every field type the index plans support, single and multi-valued, indexed into Lucene and verified through Stroom,
/// driving the REAL tools on a real Stroom.
///
/// Covered: part 1 (a JSON source whose Events hold, per record, a keyword, a text, a long, a double, a boolean, an
/// ip and a date, repeated Data elements for tags, ports, peer addresses and times, and a secret) and part 2
/// (Lucene: the plan drafted with a field of each type, those the schema lets repeat marked multiple; the index doc,
/// the secret set not to be indexed; the indexing XSLT saved from the plan, stepped, processed; verify_index with a
/// check per type, multi-valued ones by one of their values, each hit traced; a check on the secret refused).
///
/// Left out: part 3 (Elasticsearch: the template applied to a live cluster, the stored documents read back from
/// Elasticsearch, Stroom's search of it), which needs an Elasticsearch server. The secret is set not to be indexed
/// through Stroom's index field service, as the user would on the index doc's Fields tab.
@SuppressWarnings("unchecked")
class TestE2eFieldTypes extends AbstractE2eTest {

    // The run stamp put in every name; the test database is fresh for each run.
    private static final String STAMP = "101";
    /// `RECORDS`, as `json.dumps` writes them.
    private static final String SAMPLE = textResource("e2e_field_types-records.json").strip();
    /// `TRANSLATION`, at VERSION.
    private static final String TRANSLATION = textResource("e2e_field_types-translation.xsl");
    private static final String DATA = "EventDetail/Authenticate/Data[@Name='%s']/@Value";
    /// `EXTRA`: (name, type, source) beside the convention's own fields.
    private static final List<List<String>> EXTRA = List.of(
            List.of("port", "long", DATA.formatted("port")),
            List.of("ratio", "double", DATA.formatted("ratio")),
            List.of("ok", "boolean", "EventDetail/Authenticate/Outcome/Success"),
            List.of("tag", "keyword", DATA.formatted("tag")),
            List.of("ports", "long", DATA.formatted("ports")),
            List.of("peer", "ip", DATA.formatted("peer")),
            List.of("seen", "date", DATA.formatted("seen")),
            List.of("secret", "keyword", DATA.formatted("secret")));
    /// `MULTI`: the fields of several values.
    private static final Set<String> MULTI = Set.of("tag", "ports", "peer", "seen");

    @Inject
    private IndexFieldService indexFieldService;

    @Test
    void everyFieldTypeIsIndexedIntoLuceneAndVerifiedThroughStroom() {
        final Onboarded src = onboard(new Case("json", "Event Data (JSON)", SAMPLE, null, TRANSLATION));
        final Map<String, Object> waited = client.json("wait_for_processing", Map.of(
                "pipeline_uuid", src.pipelineUuid(), "stream_ids", List.of(src.raw()), "timeout_seconds", 10));
        final List<Long> events = ((List<Number>) ((List<Map<String, Object>>) waited.get("streams")).getFirst()
                .get("events")).stream().map(Number::longValue).toList();
        lucene(src, events);
    }

    private void lucene(final Onboarded src, final List<Long> events) {
        final String indexName = "E2E-TYPES-" + STAMP + "-INDEX";
        final List<Map<String, Object>> extra = EXTRA.stream()
                .map(field -> Map.<String, Object>of("name", capitalised(field.get(0)), "type", field.get(1),
                        "source", field.get(2)))
                .toList();
        final Map<String, Object> draft = client.json("draft_index_mapping", Map.of("backend", "lucene",
                "index_name", indexName, "convention", "stroom-flat", "events_stream_ids", events,
                "extra_fields", extra));
        final Map<String, Object> plan = map(draft, "plan");
        final Set<String> multiple = ((List<Map<String, Object>>) plan.get("fields")).stream()
                .filter(field -> Boolean.TRUE.equals(field.get("multiple")))
                .map(field -> (String) field.get("name"))
                .collect(Collectors.toSet());
        dump("plan", plan.get("fields"));
        assertThat(multiple).as("fields of several values marked so from the schema")
                .containsAll(MULTI.stream().map(TestE2eFieldTypes::capitalised).toList())
                .doesNotContain("UserId", "EventTime", "StreamId");
        final Map<String, Object> index = agreed("create_index_doc", Map.of("build", src.build(), "backend", "lucene",
                "name", indexName, "time_field", plan.get("time_field")));
        final DocRef ref = new DocRef(LuceneIndexDoc.TYPE, uuidOf(index), indexName);
        // FINDINGS (Indexing: Lucene): a KEYWORD field indexed and stored nothing, so a keyword is a TEXT field with
        // the KEYWORD analyser, as the content pack's Example Index has it.
        assertThat(field(ref, "Tag")).as("a keyword field").satisfies(tag -> {
            assertThat(tag.getFldType()).isEqualTo(FieldType.TEXT);
            assertThat(tag.getAnalyzerType()).isEqualTo(AnalyzerType.KEYWORD);
        });
        // The secret is kept (stored) but not indexed, as a user would set it on the index doc.
        final IndexField secret = field(ref, "Secret");
        asUser(admin, () -> indexFieldService.updateField(new UpdateField(ref, "Secret",
                new IndexFieldImpl.Builder(secret).indexed(false).build())));
        assertThat(field(ref, "Secret").isIndexed()).as("the secret set not to be indexed").isFalse();

        final String xsltUuid = uuidOf(client.json("save_xslt", Map.of("build", src.build(),
                "name", indexName + "-XSLT", "index_plan", plan)));
        final Map<String, Object> template = templates("indexing").stream()
                .filter(candidate -> "lucene".equals(candidate.get("backend")) && "Indexing".equals(
                        candidate.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Lucene 'Indexing' template"));
        final Map<String, Object> pipeline = agreed("create_indexing_pipeline", Map.of("build", src.build(),
                "name", indexName + " - Indexing", "template_uuid", template.get("uuid"), "xslt_uuid", xsltUuid,
                "index_uuid", uuidOf(index)));
        final String pipelineUuid = uuidOf(pipeline);
        final Map<String, Object> stepped = client.json("step_pipeline", Map.of("pipeline_uuid", pipelineUuid,
                "stream_id", events.getFirst(), "record", 0));
        final String output = String.valueOf(((Map<String, Object>) ((Map<String, Object>) stepped.get("elements"))
                .values().iterator().next()).get("output"));
        assertThat(count(output, "name=\"Ports\"")).as("stepped: a field of two values written as two values, not "
                                                       + "one joined by a space: %s", output).isEqualTo(2);
        assertThat(count(output, "name=\"Tag\"")).as("and the two tags too").isEqualTo(2);
        final Map<String, Object> sample = client.json("step_sample", Map.of("pipeline_uuid", pipelineUuid,
                "stream_ids", events));
        assertThat(sample).as("stepped clean").containsEntry("verdict", "clean");
        agreed("create_processor_filter", Map.of("pipeline_uuid", pipelineUuid, "stream_ids", events,
                "source_pipeline_uuid", src.pipelineUuid()));
        processAll();
        flushIndexShards();
        final Map<String, Object> gate = client.json("wait_for_processing", Map.of("pipeline_uuid", pipelineUuid,
                "stream_ids", events, "expect_events", false, "timeout_seconds", 10));
        assertThat(gate).as("indexed with no Error stream: %s", gate.get("streams")).containsEntry("gate", "pass");

        final List<String> columns = new ArrayList<>(List.of("EventTime", "UserId"));
        EXTRA.stream()
                .map(field -> field.getFirst())
                .filter(name -> !"secret".equals(name))
                .forEach(name -> columns.add(capitalised(name)));
        final Map<String, Object> common = new LinkedHashMap<>();
        common.put("build", src.build());
        common.put("index_uuid", uuidOf(index));
        common.put("backend", "lucene");
        common.put("stream_ids", events);
        common.put("expected_documents", 3);
        common.put("fields", columns);
        common.put("pipeline_uuid", pipelineUuid);
        final Map<String, Object> arguments = new LinkedHashMap<>(common);
        arguments.putAll(checks(TestE2eFieldTypes::capitalised, "Description", "EventTime",
                field -> search(field, "CONTAINS", "accepted", 2)));
        final Map<String, Object> result = agreed("verify_index", arguments);
        said(result);
        assertThat(result).as("every type verified, multi-valued fields by one of their values, each hit traced")
                .containsEntry("passed", true);
        final Map<String, Object> onSecret = new LinkedHashMap<>(common);
        onSecret.put("exact", List.of(Map.of("field", "Secret", "value", "s2")));
        final String refused = McpTestClient.text(client.call("verify_index", onSecret));
        assertThat(refused).as("a check on the field not indexed is refused, saying why")
                .contains("no search through Stroom finds it", "isn't indexed");
    }

    // verify_index's checks: one a type, the multi-valued fields by one of their values.
    private static Map<String, Object> checks(final Function<String, String> name,
                                              final String text,
                                              final String timeField,
                                              final Function<String, Map<String, Object>> textSearch) {
        final Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("exact", List.of(Map.of("field", name.apply("tag"), "value", "admin"),
                Map.of("field", name.apply("ports"), "value", "443"),
                Map.of("field", name.apply("peer"), "value", "10.1.0.2"),
                Map.of("field", name.apply("port"), "value", "8080")));
        checks.put("time_range", Map.of("field", timeField, "from", "2026-10-20T08:04:00.000Z",
                "to", "2026-10-20T08:11:00.000Z", "expected", 2));
        checks.put("searches", List.of(
                search(name.apply("port"), "GREATER_THAN", "100", 2),
                search(name.apply("ratio"), "GREATER_THAN", "0.5", 2),
                search(name.apply("ok"), "EQUALS", "true", 2),
                // One value of two, or the only one.
                search(name.apply("tag"), "EQUALS", "vpn", 2),
                search(name.apply("tag"), "IN", "vpn,admin", 2),
                search(name.apply("ports"), "GREATER_THAN", "1000", 1),
                search(name.apply("peer"), "EQUALS", "10.1.0.3", 1),
                search(name.apply("seen"), "BETWEEN", "2026-10-19T00:00:00.000Z,2026-10-19T23:59:59.000Z", 1),
                search(name.apply("seen"), "LESS_THAN", "2026-10-19T00:00:00.000Z", 1),
                textSearch.apply(text)));
        return checks;
    }

    private static Map<String, Object> search(final String field,
                                              final String condition,
                                              final String value,
                                              final int expected) {
        return Map.of("field", field, "condition", condition, "value", value, "expected", expected);
    }

    private static void said(final Map<String, Object> result) {
        for (final Map<String, Object> check : (List<Map<String, Object>>) result.get("checks")) {
            dump((Boolean.TRUE.equals(check.get("pass"))
                    ? "ok "
                    : "BAD ") + check.get("check"), check);
        }
    }

    private IndexField field(final DocRef ref, final String name) {
        final List<IndexField> fields = asUser(admin, () -> indexFieldService.findFields(new FindFieldCriteria(
                new PageRequest(0, 200), FindFieldCriteria.DEFAULT_SORT_LIST, ref, null, null)).getValues());
        return fields.stream()
                .filter(field -> name.equals(field.getFldName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No field " + name + " in " + fields));
    }

    private static int count(final String text, final String part) {
        int found = 0;
        int at = text.indexOf(part);
        while (at >= 0) {
            found++;
            at = text.indexOf(part, at + part.length());
        }
        return found;
    }

    // The name with its first letter in upper case (these names are all lower case).
    private static String capitalised(final String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
