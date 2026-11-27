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
import stroom.search.elastic.ElasticTestNode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A field holding the same value in every document, asked for by the user (the organisation the events are
/// from): planned as `{name, type: constant, value}`, mapped `constant_keyword` with its value by the index template,
/// not written by the indexing XSLT, and found in every document by a search on it, in Elasticsearch and through
/// Stroom (verify_index counting every event as holding it). Driving the REAL tools on a real Stroom and
/// Elasticsearch.
///
/// The node is [ElasticTestNode]'s (`STROOM_TEST_ELASTIC_URL`): skipped without one unless
/// `STROOM_TEST_ELASTIC_REQUIRED=true`. What the test made there is removed after it.
@SuppressWarnings("unchecked")
class TestE2eConstantFields extends AbstractE2eTest {

    private static final String FIXTURE_TEMPLATE = "E2E Events to Elasticsearch (constant)";
    private static final String FIXTURE_CLUSTER = "E2E Live Elasticsearch (constant)";
    private static final String STAMP = Long.toString(System.currentTimeMillis(), 36);
    private static final String ORGANISATION = "Acme Mail & Co";

    private ElasticTestNode node;
    private final List<String> indices = new ArrayList<>();
    private final List<String> indexTemplates = new ArrayList<>();

    @BeforeEach
    void setUpNode() {
        node = ElasticTestNode.getOrSkip();
    }

    @AfterEach
    void removeWhatWasIndexed() {
        if (node == null) {
            return;
        }
        for (final String index : indices) {
            node.request("DELETE", "/_data_stream/" + index, null);
            node.deleteIndices(index + "*");
        }
        indexTemplates.forEach(node::deleteIndexTemplate);
    }

    @Test
    void aConstantTheUserAskedForIsMappedNotWrittenAndFoundInEveryDocument() {
        final Onboarded logons = onboard(new Case("constant-" + STAMP, "Event Data (Text)", CSV_SAMPLE,
                CSV_SPLITTER, CSV_XSLT));
        final String index = "e2e-constant-" + STAMP + "-v1";
        indices.add(index);
        final DocRef cluster = liveElasticCluster(node, FIXTURE_CLUSTER);
        final String template = elasticEventsTemplate(FIXTURE_TEMPLATE, "Fixture for TestE2eConstantFields");

        // The plan, with the organisation the user asked for in every document.
        final Map<String, Object> plan = obj("backend", "elasticsearch", "index_name", index,
                "time_field", "@timestamp", "fields", List.of(
                        obj("name", "StreamId", "type", "id", "source", "@StreamId"),
                        obj("name", "EventId", "type", "id", "source", "@EventId"),
                        obj("name", "@timestamp", "type", "date", "source", "EventTime/TimeCreated"),
                        obj("name", "user.name", "type", "keyword", "source", "EventSource/User/Id"),
                        obj("name", "organisation", "type", "constant", "value", ORGANISATION,
                                "description", "The organisation the events are from")));
        final String xslt = uuidOf(client.json("save_xslt", args("build", logons.build(), "name", index + "-XSLT",
                "index_plan", plan)));
        assertThat(readXslt(xslt).getData()).as("Elasticsearch fills the constant: the XSLT doesn't write it")
                .doesNotContain("organisation").doesNotContain("Acme Mail");
        final String pipeline = uuidOf(agreed("create_indexing_pipeline", args("build", logons.build(),
                "name", index + " - Indexing", "template_uuid", template, "xslt_uuid", xslt, "index_name", index,
                "cluster_uuid", cluster.getUuid())));
        final Map<String, Object> stepped = stepSample(pipeline, List.of(logons.events()));
        assertThat(stepped).as("stepped clean: %s", stepped.get("groups")).containsEntry("verdict", "clean");

        // The template maps it constant_keyword with its value.
        final Map<String, Object> agreedTemplate = agreed("propose_index_template", args("pipeline_uuid", pipeline,
                "plan", plan, "events_stream_ids", List.of(logons.events()), "without_example", true));
        assertThat(agreedTemplate).containsEntry("agreed", true);
        final Map<String, Object> properties = map(map(map(map(agreedTemplate, "template"), "template"),
                "mappings"), "properties");
        assertThat(map(properties, "organisation")).as("the constant mapped with its value")
                .isEqualTo(obj("type", "constant_keyword", "value", ORGANISATION));
        apply((String) agreedTemplate.get("dev_tools"));

        // Indexed: a search on it in Elasticsearch finds every document.
        final Map<String, Object> started = agreed("create_processor_filter", args("pipeline_uuid", pipeline,
                "stream_ids", List.of(logons.events()), "source_pipeline_uuid", logons.pipelineUuid()));
        processAll();
        final Map<String, Object> done = client.json("wait_for_processing", args("pipeline_uuid", pipeline,
                "stream_ids", List.of(logons.events()), "expect_events", false, "filter_id",
                started.get("filter_id"), "timeout_seconds", 10));
        assertThat(done).as("indexed with no Error stream: %s", done.get("streams")).containsEntry("gate", "pass");
        node.refresh(index);
        assertThat(node.count(index, null)).as("the index holds the 3 events").isEqualTo(3);
        assertThat(node.count(index, "{\"term\": {\"organisation\": \"" + ORGANISATION + "\"}}"))
                .as("every document holds the constant").isEqualTo(3);
        assertThat(node.count(index, "{\"term\": {\"organisation\": \"Other\"}}")).isZero();

        // Through Stroom: verify_index counts every event as holding it, and the index finds them all.
        final String indexDoc = uuidOf(agreed("create_index_doc", args("build", logons.build(), "backend",
                "elasticsearch", "name", index, "time_field", "@timestamp", "index_name", index, "cluster_uuid",
                cluster.getUuid())));
        final Map<String, Object> verified = agreed("verify_index", args("build", logons.build(), "index_uuid",
                indexDoc, "backend", "elasticsearch", "stream_ids", List.of(logons.events()), "expected_documents", 3,
                "fields", List.of("StreamId", "EventId", "@timestamp", "user.name"), "pipeline_uuid", pipeline,
                "searches", List.of(obj("field", "organisation", "condition", "EQUALS", "value", ORGANISATION))));
        final List<Map<String, Object>> checks = list(verified, "checks");
        dump("verify_index checks", checks);
        assertThat(checks).as("every check passes: %s", checks).allSatisfy(check ->
                assertThat(check).containsEntry("pass", true));
        assertThat(checks).as("the constant's check expects every document, from the plan")
                .anySatisfy(check -> assertThat(check)
                        .containsEntry("check", "organisation EQUALS " + ORGANISATION)
                        .hasEntrySatisfying("expected", expected ->
                                assertThat(((Number) expected).longValue()).isEqualTo(3))
                        .hasEntrySatisfying("expected_from", from -> assertThat(String.valueOf(from))
                                .contains("the constant '" + ORGANISATION + "', in every document")));

        // Amended as the user asks (draft_index_mapping changes=): another constant, kept with the pipeline's plan.
        final Map<String, Object> amended = client.json("draft_index_mapping", args("backend", "elasticsearch",
                "index_name", index, "pipeline_uuid", pipeline, "changes", obj("fields", List.of(
                        obj("name", "site", "type", "constant", "value", "London")))));
        dump("draft_index_mapping changes", amended);
        assertThat(list(map(amended, "plan"), "fields")).as("the constant added to the kept plan")
                .anySatisfy(field -> assertThat(field).containsEntry("name", "site")
                        .containsEntry("type", "constant").containsEntry("value", "London"));
    }

    // The cluster admin applies a Dev Tools request the user was given.
    private void apply(final String devTools) {
        final String indexTemplate = applyDevTools(node, devTools);
        if (indexTemplate != null) {
            indexTemplates.add(indexTemplate);
        }
    }

    private static Map<String, Object> obj(final Object... keysAndValues) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            out.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return out;
    }
}
