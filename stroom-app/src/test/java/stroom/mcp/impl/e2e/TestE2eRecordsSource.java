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

import stroom.mcp.impl.McpTestClient;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// The Stroom MCP server's `dev/e2e_records_source.py`, ported: a source whose own XML is
/// `<records><record>...` is never taken for an Events stream, nor for a Data Splitter's records, through the REAL
/// tools on a real Stroom, for two sources: `<records>` in no namespace, and `<records>` declaring records:2 itself.
///
/// Covered, as the script numbers them, for both sources: 1 (profiled as XML for the XMLParser, which
/// create_pipeline takes; the Data Splitter template refused), 2 (the XSLT generated in the source's own namespace;
/// a hand-written XSLT reading records by bare name saved for the source in no namespace and refused for the
/// records:2 one), 3 (the translation's output valid event-logging Events; a source record said not to be Events,
/// or validated against the records schema) and 4 (the raw stream refused as the Events to index; the Events
/// indexed into Lucene, the indexing XSLT's records:2 output valid against the records schema, every record indexed
/// and found by its user).
@SuppressWarnings("unchecked")
class TestE2eRecordsSource extends AbstractE2eTest {

    private static final String XSL = "http://www.w3.org/1999/XSL/Transform";
    // Written by hand, reading the source's elements by bare name with no xpath-default-namespace.
    private static final String HAND = "<xsl:stylesheet xmlns:xsl=\"" + XSL + "\" version=\"2.0\"><xsl:template "
                                       + "match=\"records\"><Events xmlns=\"event-logging:3\"><xsl:apply-templates "
                                       + "select=\"record\"/></Events></xsl:template><xsl:template match=\"record\"/>"
                                       + "</xsl:stylesheet>";
    private static final Pattern DEFAULT_NAMESPACE = Pattern.compile(
            "<xsl:stylesheet[^>]*\\sxpath-default-namespace=\"([^\"]*)\"");

    @ParameterizedTest
    @ValueSource(strings = {"", "records:2"})
    void aRecordsSourceIsNeverTakenForEventsOrADataSplittersRecords(final String namespace) {
        final String plain = (String) jsonResource("format_samples.json").get("xml_records");
        final String sample = namespace.isEmpty()
                ? plain
                : plain.replace("<records>", "<records xmlns=\"records:2\">");
        final String tag = namespace.isEmpty()
                ? "plain"
                : "rec2";
        final String build = "records-" + tag;
        final String feed = "RECORDS-" + tag.toUpperCase();

        // 1. The template.
        final Map<String, Object> profile = json("profile_sample", args("sample", sample));
        assertThat(profile).containsEntry("format", "xml").containsEntry("root", "records");
        assertThat(profile.get("namespace") == null
                ? ""
                : profile.get("namespace")).isEqualTo(namespace);
        assertThat(String.valueOf(profile.get("suggested_parser"))).contains("XMLParser");
        createFeed(build, feed);
        final long raw = upload(feed, sample);
        final String textTemplate = templateUuid("translation", "Event Data (Text)");
        final String xmlTemplate = templateUuid("translation", "Event Data (XML)");
        final String refused = refusal("create_pipeline", args("build", build, "name", feed + "-TEXT",
                "template_uuid", textTemplate));
        assertThat(refused).as("the Data Splitter template is refused for it").isNotBlank();

        // 2. The XSLT.
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", List.of(raw),
                "source_name", "Acme", "system_name", "Acme", "environment", "Test"));
        final Map<String, Object> mapping = decide(map(draft, "mapping"));
        assertThat(mapping).containsEntry("input", "xml").containsEntry("root", "records")
                .containsEntry("record", "record");
        assertThat(mapping.get("xml_namespace") == null
                ? ""
                : mapping.get("xml_namespace")).as("drafted as the source's own XML").isEqualTo(namespace);
        Map<String, Object> saved = json("build_translation_xslt", args("mapping", mapping,
                "schema_version", draft.get("schema_version"), "stream_ids", List.of(raw), "build", build,
                "name", feed + "-Events", "include_xslt", true));
        while ("needs_review".equals(saved.get("status"))) {
            saved = json("build_translation_xslt", args("mapping", mapping, "schema_version",
                    draft.get("schema_version"), "stream_ids", List.of(raw), "build", build, "name", feed + "-Events",
                    "include_xslt", true, "reviewed", saved.get("review_id")));
        }
        assertThat(saved).as("saved: %s", saved.get("problems")).containsEntry("ok", true);
        final Matcher declared = DEFAULT_NAMESPACE.matcher(String.valueOf(saved.get("xslt")));
        assertThat(declared.find()
                ? declared.group(1)
                : "").as("the XSLT reads the input in the source's namespace").isEqualTo(namespace);
        final CallToolResult hand = client.call("save_xslt", args("build", build, "name", feed + "-HAND",
                "code", HAND));
        final String handText = Boolean.TRUE.equals(hand.isError())
                ? McpTestClient.text(hand)
                : "";
        if (namespace.isEmpty()) {
            assertThat(handText).as("a hand-written XSLT reading its own records bare is saved").isEmpty();
        } else {
            assertThat(handText).as("a hand-written XSLT reading records bare is refused")
                    .contains("namespace records:2");
        }
        final String savedUuid = (String) map(saved, "saved").get("uuid");
        final Map<String, Object> pipeline = json("create_pipeline", args("build", build, "name", feed + "-Events",
                "template_uuid", xmlTemplate, "set_properties", List.of(
                        docProperty("translationFilter", "xslt", savedUuid, "XSLT"))));
        final String pipelineUuid = (String) pipeline.get("uuid");

        // 3. Validation.
        final Map<String, Object> stepped = json("step_sample", args("pipeline_uuid", pipelineUuid,
                "stream_ids", List.of(raw)));
        assertThat(stepped).as("stepped clean: %s", stepped.get("groups")).containsEntry("verdict", "clean");
        assertThat(number(stepped.get("records_stepped"))).isEqualTo(3);
        final String output = translationOutput(pipelineUuid, raw, 0, 0);
        final Map<String, Object> valid = json("check_events", args("events_xml", output));
        assertThat(map(valid, "schema")).containsEntry("valid", true);
        assertThat(String.valueOf(map(valid, "schema").get("schema"))).contains("event-logging");
        final String record = sample.substring(sample.indexOf("<records"));
        final Map<String, Object> checked = json("check_events", args("events_xml", record));
        dump("check_events of a source record", checked);
        if (namespace.isEmpty()) {
            assertThat(map(checked, "schema").get("schema")).isNull();
            assertThat(String.valueOf(map(checked, "schema").get("errors"))).contains("Not an Events document");
        } else {
            assertThat(map(checked, "schema")).containsEntry("schema", "file://records-v2.0.xsd");
        }

        // 4. The index.
        json("create_processor_filter", args("pipeline_uuid", pipelineUuid, "stream_ids", List.of(raw)));
        processAll();
        final Map<String, Object> done = json("wait_for_processing", args("pipeline_uuid", pipelineUuid,
                "stream_ids", List.of(raw), "timeout_seconds", 10));
        assertThat(done).as("processed into one Events stream: %s", done.get("streams"))
                .containsEntry("gate", "pass");
        final List<Object> events = (List<Object>) ((Map<String, Object>) list(done, "streams").getFirst())
                .get("events");
        final String indexName = "E2E-RECORDS-" + tag.toUpperCase();
        final String wrong = refusal("draft_index_mapping", args("backend", "lucene", "index_name", indexName,
                "convention", "stroom-flat", "events_stream_ids", List.of(raw)));
        assertThat(wrong).as("the raw stream is refused as the Events to index").contains("not Events");
        final Map<String, Object> indexDraft = json("draft_index_mapping", args("backend", "lucene",
                "index_name", indexName, "convention", "stroom-flat", "events_stream_ids", events));
        final Map<String, Object> plan = map(indexDraft, "plan");
        final String timeField = (String) plan.get("time_field");
        final String userField = ((List<Map<String, Object>>) plan.get("fields")).stream()
                .filter(field -> "EventSource/User/Id".equals(field.get("source")))
                .map(field -> (String) field.get("name"))
                .findFirst()
                .orElseThrow();
        final Map<String, Object> index = json("create_index_doc", args("build", build, "backend", "lucene",
                "name", indexName, "time_field", timeField, "plan", plan));
        final String indexUuid = (String) index.get("uuid");
        final Map<String, Object> xslt = json("save_xslt", args("build", build, "name", indexName + "-XSLT",
                "index_plan", plan));
        final String indexingTemplate = ((List<Map<String, Object>>) json("find_pipeline_templates",
                args("stage", "indexing")).get("candidates")).stream()
                .filter(candidate -> "lucene".equals(candidate.get("backend")))
                .map(candidate -> (String) candidate.get("uuid"))
                .findFirst()
                .orElseThrow();
        final Map<String, Object> indexer = json("create_indexing_pipeline", args("build", build,
                "name", indexName + " - Indexing", "template_uuid", indexingTemplate,
                "xslt_uuid", xslt.get("uuid"), "index_uuid", indexUuid));
        final String indexerUuid = (String) indexer.get("uuid");
        final Map<String, Object> indexStepped = json("step_sample", args("pipeline_uuid", indexerUuid,
                "stream_ids", events));
        assertThat(indexStepped).as("the indexing pipeline steps the Events clean: %s", indexStepped.get("groups"))
                .containsEntry("verdict", "clean");
        final Map<String, Object> written = map(json("step_pipeline", args("pipeline_uuid", indexerUuid,
                "stream_id", number(events.getFirst()), "record", "0")), "elements");
        final String out = written.values().stream()
                .map(element -> String.valueOf(((Map<String, Object>) element).get("output")))
                .filter(text -> text.contains("records:2"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No records:2 output in " + written.keySet()));
        final Map<String, Object> recordsValid = json("check_events", args("events_xml", out));
        assertThat(map(recordsValid, "schema")).as("its output is records:2: %s", recordsValid)
                .containsEntry("valid", true)
                .containsEntry("schema", "file://records-v2.0.xsd");
        json("create_processor_filter", args("pipeline_uuid", indexerUuid, "stream_ids", events,
                "source_pipeline_uuid", pipelineUuid));
        processAll();
        final Map<String, Object> gate = json("wait_for_processing", args("pipeline_uuid", indexerUuid,
                "stream_ids", events, "expect_events", false, "timeout_seconds", 10));
        assertThat(gate).as("indexed with no Error stream: %s %s", gate.get("streams"), gate.get("problems"))
                .containsEntry("gate", "pass");
        final Map<String, Object> found = json("verify_index", args("build", build, "index_uuid", indexUuid,
                "backend", "lucene", "stream_ids", events, "expected_documents", 3,
                "fields", List.of("StreamId", "EventId", timeField, userField),
                "exact", List.of(Map.of("field", userField, "value", "bob")), "pipeline_uuid", indexerUuid));
        dump("verify_index", found);
        assertThat(found).as("every record indexed, and found by its user").containsEntry("passed", true);
    }

    // The script's decide() (from e2e_formats.py) for this source: the user from its user field, and a host.
    private static Map<String, Object> decide(final Map<String, Object> draft) {
        final Map<String, Object> mapping = new LinkedHashMap<>(draft);
        final List<Map<String, Object>> common = new ArrayList<>(
                (List<Map<String, Object>>) mapping.getOrDefault("common", List.of()));
        final Set<String> paths = new HashSet<>();
        common.forEach(entry -> paths.add(String.valueOf(entry.get("path"))));
        if (paths.stream().noneMatch(path -> path.startsWith("EventSource/Device/"))) {
            common.add(Map.of("path", "EventSource/Device/HostName", "value", "acme-gw01"));
        }
        if (!paths.contains("EventSource/User/Id")) {
            common.add(Map.of("path", "EventSource/User/Id", "field", "user"));
        }
        mapping.put("common", common);
        return mapping;
    }
}
