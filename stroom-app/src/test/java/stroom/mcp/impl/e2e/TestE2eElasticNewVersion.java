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
import stroom.meta.shared.MetaFields;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.ProcessorFilter;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.ElasticTestNode;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A new version of a promoted Elasticsearch indexing pipeline, walked through the REAL tools on a real Stroom and
/// Elasticsearch as an agent would (decided by the user, 2026-11-17: a promoted pipeline changes only by a new
/// version, and a production index is never written into or dropped):
///
/// * a promoted events pipeline and a promoted indexing pipeline writing a production index `<name>-v1`, its
///   documents indexed;
/// * copy_pipeline of the indexing pipeline proposes the next names (`V1.0` to `V1.1`, the index `-v1` to `-v2`), and
///   refuses a candidate index already on the cluster, proposing the next free one (`-v3`); once it is free, the copy
///   is made with an Elastic Index doc of its own;
/// * the template proposed for it has the exact index pattern; processed in the build, its documents go to `-v2`,
///   which verify_index sees;
/// * the production index's document count and mapping are unchanged throughout;
/// * promoted, the layout is role first (`<feed>/Indexing/V1.1` beside `<feed>/Indexing/V1.0`);
/// * a new events version (the events pipeline's name holding no version: `V1.0`, so `-V1.1`) promoted while both
///   indexing versions read the old Events: each gains a new filter, disabled, reading the new version's Events, its
///   old filter left as it was (enabled or not), and the next steps name both.
///
/// The node is [ElasticTestNode]'s (`STROOM_TEST_ELASTIC_URL`): skipped without one unless
/// `STROOM_TEST_ELASTIC_REQUIRED=true`. What the test made there is removed after it.
@SuppressWarnings("unchecked")
class TestE2eElasticNewVersion extends AbstractE2eTest {

    private static final String FIXTURE_TEMPLATE = "E2E Events to Elasticsearch (new version)";
    private static final String FIXTURE_CLUSTER = "E2E Live Elasticsearch (new version)";
    private static final String STAMP = Long.toString(System.currentTimeMillis(), 36);
    private static final String DEST = "System/E2E Promoted/EsVersions";
    private static final int EVENTS = 3;

    @Inject
    private ProcessorFilterService processorFilterService;
    @Inject
    private ElasticIndexStore elasticIndexStore;

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
    void aNewVersionOfAPromotedIndexingPipelineWritesAVersionedIndexAndLeavesProductionAlone() {
        final Onboarded source = onboard(new Case("esver-" + STAMP, "Event Data (Text)", CSV_SAMPLE, CSV_SPLITTER,
                CSV_XSLT));
        final String base = "e2e-esver-" + STAMP;
        indices.add(base);
        final String production = base + "-v1";
        final String candidate = base + "-v2";
        final String names = source.feed();
        final DocRef cluster = liveElasticCluster(node, FIXTURE_CLUSTER);
        final String template = elasticEventsTemplate(FIXTURE_TEMPLATE, "Fixture for TestE2eElasticNewVersion");
        final List<Long> events = List.of(source.events());

        // The first build: the indexing pipeline writing the production index, indexed, verified and promoted with
        // the events pipeline.
        final Map<String, Object> plan = obj("backend", "elasticsearch", "index_name", production,
                "time_field", "@timestamp", "fields", List.of(
                        obj("name", "StreamId", "type", "id", "source", "@StreamId"),
                        obj("name", "EventId", "type", "id", "source", "@EventId"),
                        obj("name", "@timestamp", "type", "date", "source", "EventTime/TimeCreated"),
                        obj("name", "user.name", "type", "keyword", "source", "EventSource/User/Id")));
        final String xslt = uuidOf(client.json("save_xslt", args("build", source.build(), "name",
                names + "-V1.0-Indexing-XSLT", "index_plan", plan)));
        final String indexing = uuidOf(agreed("create_indexing_pipeline", args("build", source.build(),
                "name", names + "-V1.0-Indexing", "template_uuid", template, "xslt_uuid", xslt, "index_name",
                production, "cluster_uuid", cluster.getUuid())));
        assertThat(stepSample(indexing, events)).containsEntry("verdict", "clean");
        final Map<String, Object> productionTemplate = agreed("propose_index_template", args("pipeline_uuid",
                indexing, "plan", plan, "events_stream_ids", events, "without_example", true));
        assertThat((List<Object>) map(productionTemplate, "template").get("index_patterns"))
                .containsExactly(production);
        apply((String) productionTemplate.get("dev_tools"));
        final String indexDoc = uuidOf(agreed("create_index_doc", args("build", source.build(), "backend",
                "elasticsearch", "name", names + "-V1.0", "time_field", "@timestamp", "index_name", production,
                "cluster_uuid", cluster.getUuid())));
        indexInto(indexing, events, source.pipelineUuid(), production);
        verified(source.build(), indexDoc, indexing, events);
        final String mapping = mappingOf(production);
        final Map<String, Object> destinations = Map.of("Feed", DEST, "Pipeline", DEST, "XSLT", DEST,
                "TextConverter", DEST, "Documentation", DEST, "ElasticIndex", DEST);
        promote(source.build(), destinations);
        assertThat(folderOf(source.pipelineUuid())).isEqualTo(DEST + "/Events/V1.0");
        assertThat(folderOf(indexing)).isEqualTo(DEST + "/Indexing/V1.0");
        assertUnchanged(production, mapping);

        // A later build: a new version of the indexing pipeline. Its candidate index is already on the cluster: the
        // copy is refused before anything is made, the next free name proposed.
        final String later = "esver-next-" + STAMP;
        node.createIndex(candidate, "{}");
        final String taken = refusal("copy_pipeline", args("build", later, "source_uuid", indexing, "new_name",
                names + "-V1.0-Indexing"));
        dump("copy_pipeline candidate taken", taken);
        assertThat(taken).contains("(looked up as '" + candidate + "') is not free", "'" + base + "-v3' is free",
                "Nothing was copied")
                .doesNotContain("DELETE");
        assertThat(findFolder("System/MCP Workspace/" + later)).isEmpty();
        assertUnchanged(production, mapping);
        node.deleteIndices(candidate);

        // Free now: the names proposed are the next version's, the user agreeing to them.
        final Map<String, Object> copied = agreed("copy_pipeline", args("build", later, "source_uuid", indexing,
                "new_name", names + "-V1.0-Indexing"));
        dump("copy_pipeline new version", copied);
        final String copy = uuidOf(copied);
        assertThat(copied).containsEntry("name", names + "-V1.1-Indexing")
                .containsEntry("copy_of", names + "-V1.0-Indexing")
                .containsEntry("index_name", candidate)
                .doesNotContainKey("working_copy");
        assertThat(list(copied, "copied_documents")).singleElement().satisfies(doc ->
                assertThat(doc).containsEntry("type", "XSLT").containsEntry("name", names + "-V1.1-Indexing-XSLT"));
        final Map<String, Object> newIndexDoc = list(copied, "index_docs").getFirst();
        assertThat(newIndexDoc).containsEntry("name", names + "-V1.1");
        final String copyIndexDoc = (String) newIndexDoc.get("uuid");
        assertThat(readIndexDoc(copyIndexDoc).getIndexName()).isEqualTo(candidate);
        assertThat(readIndexDoc(indexDoc).getIndexName()).isEqualTo(production);

        // The template proposed for it covers its own index exactly, nothing else.
        final Map<String, Object> candidatePlan = new LinkedHashMap<>(plan);
        candidatePlan.put("index_name", candidate);
        final Map<String, Object> candidateTemplate = agreed("propose_index_template", args("pipeline_uuid", copy,
                "plan", candidatePlan, "events_stream_ids", events, "without_example", true));
        dump("propose_index_template new version", candidateTemplate);
        assertThat((List<Object>) map(candidateTemplate, "template").get("index_patterns"))
                .containsExactly(candidate);
        apply((String) candidateTemplate.get("dev_tools"));

        // Stepped clean and processed in the build: into the new version's index; verify_index sees its documents
        // there.
        assertThat(stepSample(copy, events)).containsEntry("verdict", "clean");
        indexInto(copy, events, source.pipelineUuid(), candidate);
        verified(later, copyIndexDoc, copy, events);
        assertUnchanged(production, mapping);

        // Promoted beside the first version, role first; the production index still as it was, and never named for
        // dropping (the new version's own index, holding the build's test documents, may be).
        final Map<String, Object> laterPromoted = promote(later, Map.of());
        assertThat(jsonOf(laterPromoted.get("user_actions"))).doesNotContain("'" + production + "'",
                "/" + production + "\"");
        assertThat(folderOf(copy)).isEqualTo(DEST + "/Indexing/V1.1");
        assertThat(folderOf(indexing)).isEqualTo(DEST + "/Indexing/V1.0");
        assertThat(readIndexDoc(indexDoc).getIndexName()).isEqualTo(production);
        assertUnchanged(production, mapping);
        node.refresh(candidate);
        assertThat(node.count(candidate, null)).isEqualTo(EVENTS);

        newEventsVersion(source, indexing, copy, production, mapping);
    }

    // A new events version promoted while both promoted indexing versions read the old Events (their promotion
    // filters, the first enabled as the admin would have, the second left disabled): each gains a filter, disabled,
    // reading the new version's Events; the old ones are left as they were, and the next steps name both.
    private void newEventsVersion(final Onboarded source,
                                  final String indexing,
                                  final String copy,
                                  final String production,
                                  final String mapping) {
        final ProcessorFilter firstReading = feedWideFilter(indexing);
        final ProcessorFilter secondReading = feedWideFilter(copy);
        assertThat(jsonOf(firstReading.getQueryData())).contains(source.pipelineUuid());
        assertThat(jsonOf(secondReading.getQueryData())).contains(source.pipelineUuid());
        asUser(admin, () -> processorFilterService.setEnabled(firstReading.getId(), true));
        assertThat(fetch(secondReading.getId()).isEnabled()).isFalse();

        final String build = "esver-events-" + STAMP;
        final Map<String, Object> copied = agreed("copy_pipeline", args("build", build, "source_uuid",
                source.pipelineUuid(), "new_name", source.pipelineName()));
        dump("copy_pipeline events new version", copied);
        // Its name holds no version: promotion filed it in V1.0, so its next version is V1.1 (not -V2).
        final String versionName = source.pipelineName() + "-V1.1";
        assertThat(copied).containsEntry("name", versionName);
        final String version = uuidOf(copied);

        final Map<String, Object> review = json("promote_build", args("build", build, "destinations", Map.of()));
        assertThat(review).as("the layout first: %s", review).containsEntry("status", "needs_review");
        assertThat(strings(review, "follow_on_filters")).hasSize(2)
                .anySatisfy(said -> assertThat(said).contains("'" + source.feed() + "-V1.0-Indexing'",
                        "filter " + firstReading.getId() + " is left as it is", "'" + versionName + "'"))
                .anySatisfy(said -> assertThat(said).contains("'" + source.feed() + "-V1.1-Indexing'",
                        "filter " + secondReading.getId() + " is left as it is"));
        final Map<String, Object> promoted = json("promote_build", args("build", build, "destinations", Map.of(),
                "reviewed", review.get("review_id")));
        dump("promote_build events new version", promoted);
        assertThat(promoted).as("promoted: %s", promoted).containsKey("promoted");
        assertThat(folderOf(version)).isEqualTo(DEST + "/Events/V1.1");
        assertThat(jsonOf(promoted.get("user_actions"))).doesNotContain("'" + production + "'",
                "/" + production + "\"");

        final List<Map<String, Object>> following = list(promoted, "follow_on_filters");
        assertThat(following).hasSize(2);
        for (final ProcessorFilter reading : List.of(firstReading, secondReading)) {
            final Map<String, Object> made = following.stream()
                    .filter(entry -> number(entry.get("copied_from")) == reading.getId())
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No follow-on filter for " + reading.getId() + ": "
                                                          + following));
            assertThat(made).containsEntry("reads", versionName)
                    .containsEntry("in_place_of", source.pipelineName())
                    .containsEntry("enabled", false);
            final ProcessorFilter stored = fetch((int) number(made.get("filter_id")));
            assertThat(stored.isEnabled()).as("the new filter is the user's to enable").isFalse();
            assertThat(stored.getPipelineUuid()).isEqualTo(reading.getPipelineUuid());
            assertThat(jsonOf(stored.getQueryData())).contains(version).doesNotContain(source.pipelineUuid());
            // The filter it follows is left as it was, enabled or not.
            final ProcessorFilter before = fetch(reading.getId());
            assertThat(before.isEnabled()).isEqualTo(reading.getId() == firstReading.getId());
            assertThat(jsonOf(before.getQueryData())).isEqualTo(jsonOf(reading.getQueryData()));
        }
        final String nextSteps = String.join("\n", strings(promoted, "next_steps"));
        assertThat(nextSteps).contains("'" + source.feed() + "-V1.0-Indexing'", "'" + source.feed()
                                                                               + "-V1.1-Indexing'",
                "'" + source.pipelineName() + "'");
        assertUnchanged(production, mapping);
    }

    // --- the steps ---

    // The indexing pipeline's filter over the sample's Events from the events pipeline, run, and its documents in
    // the index.
    private void indexInto(final String pipeline,
                           final List<Long> events,
                           final String eventsPipeline,
                           final String index) {
        final Map<String, Object> started = agreed("create_processor_filter", args("pipeline_uuid", pipeline,
                "stream_ids", events, "source_pipeline_uuid", eventsPipeline));
        processAll();
        final Map<String, Object> done = client.json("wait_for_processing", args("pipeline_uuid", pipeline,
                "stream_ids", events, "expect_events", false, "filter_id", started.get("filter_id"),
                "timeout_seconds", 10));
        assertThat(done).as("indexed with no Error stream: %s", done.get("streams")).containsEntry("gate", "pass");
        node.refresh(index);
        assertThat(node.count(index, null)).as("index %s holds the sample's events", index).isEqualTo(EVENTS);
    }

    // verify_index of the Elastic Index doc: every check passes.
    private void verified(final String build, final String indexDoc, final String pipeline, final List<Long> events) {
        final Map<String, Object> verified = agreed("verify_index", args("build", build, "index_uuid", indexDoc,
                "backend", "elasticsearch", "stream_ids", events, "expected_documents", EVENTS,
                "fields", List.of("StreamId", "EventId", "@timestamp", "user.name"), "pipeline_uuid", pipeline));
        dump("verify_index " + build, verified);
        assertThat(list(verified, "checks")).as("every check passes").allSatisfy(check ->
                assertThat(check).containsEntry("pass", true));
    }

    // promote_build: the layout shown first (needs_review), then promoted once the user agrees.
    private Map<String, Object> promote(final String build, final Map<String, Object> destinations) {
        final Map<String, Object> review = json("promote_build", args("build", build, "destinations",
                destinations));
        assertThat(review).as("the layout first: %s", review).containsEntry("status", "needs_review");
        final Map<String, Object> promoted = json("promote_build", args("build", build, "destinations",
                destinations, "reviewed", review.get("review_id")));
        dump("promote_build " + build, promoted);
        assertThat(promoted).as("promoted: %s", promoted).containsKey("promoted");
        return promoted;
    }

    // The production index holds the sample's documents alone, mapped as it was.
    private void assertUnchanged(final String index, final String mapping) {
        node.refresh(index);
        assertThat(node.count(index, null)).as("the production index's documents").isEqualTo(EVENTS);
        assertThat(mappingOf(index)).as("the production index's mapping").isEqualTo(mapping);
    }

    // An index's mapping, as Elasticsearch gives it.
    private String mappingOf(final String index) {
        final ElasticTestNode.Response response = node.request("GET", "/" + index + "/_mapping", null);
        assertThat(response.isSuccess()).as("mapping of %s: %s", index, response.body()).isTrue();
        return JsonUtil.writeValueAsString(JsonUtil.readValue(response.body(), Map.class), false);
    }

    // A pipeline's only filter selecting no streams by id: the one promotion made it.
    private ProcessorFilter feedWideFilter(final String pipeline) {
        final List<ProcessorFilter> filters = asUser(admin, () -> processorFilterService.find(
                new DocRef("Pipeline", pipeline))).getValues().stream()
                .filter(filter -> !selectsIds(filter.getQueryData().getExpression()))
                .toList();
        assertThat(filters).as("pipeline %s's promotion filter", pipeline).hasSize(1);
        return filters.getFirst();
    }

    // Whether an expression selects streams by id (a build's sample filter), at any depth.
    private static boolean selectsIds(final ExpressionItem item) {
        if (item instanceof final ExpressionOperator operator) {
            return NullSafe.list(operator.getChildren()).stream().anyMatch(TestE2eElasticNewVersion::selectsIds);
        }
        return item instanceof final ExpressionTerm term && MetaFields.ID.getFldName().equals(term.getField());
    }

    private ProcessorFilter fetch(final int id) {
        return asUser(admin, () -> processorFilterService.fetch(id).orElseThrow());
    }

    private ElasticIndexDoc readIndexDoc(final String uuid) {
        return asUser(admin, () -> elasticIndexStore.readDocument(new DocRef(ElasticIndexDoc.TYPE, uuid)));
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
