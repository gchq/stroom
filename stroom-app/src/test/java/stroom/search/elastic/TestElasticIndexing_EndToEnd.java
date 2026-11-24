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

package stroom.search.elastic;

import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.processor.api.ProcessorResult;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/// Indexes events to a real Elasticsearch node with an Elasticsearch indexing pipeline, then checks what
/// the node holds, independently of Stroom's search. See [ElasticTestNode] for how the node is found.
class TestElasticIndexing_EndToEnd extends AbstractCoreIntegrationTest {

    @Inject
    private ElasticEndToEndTestHelper helper;

    private ElasticTestNode node;
    private DocRef clusterRef;
    private final List<String> indexPatternsToDelete = new ArrayList<>();
    private final List<String> templatesToDelete = new ArrayList<>();

    @BeforeEach
    void setUp() {
        node = ElasticTestNode.getOrSkip();
        clusterRef = helper.createCluster(node);
    }

    @AfterEach
    void tearDown() {
        if (node != null) {
            indexPatternsToDelete.forEach(node::deleteIndices);
            templatesToDelete.forEach(node::deleteIndexTemplate);
        }
    }

    @Test
    void testIndexesEveryEvent() {
        final String indexName = ElasticTestNode.createIndexName("network");
        indexPatternsToDelete.add(indexName);
        helper.indexNetworkEvents(node, clusterRef, indexName);

        assertThat(node.count(indexName, null))
                .isEqualTo(ElasticEndToEndTestHelper.NETWORK_EVENT_COUNT);

        // Each event is a document with its own event id, all from the same stream
        final JsonNode hits = node.search(indexName, """
                {
                  "size": 100,
                  "sort": [{"EventId": "asc"}]
                }""").at("/hits/hits");
        final List<Long> eventIds = new ArrayList<>();
        final List<Long> streamIds = new ArrayList<>();
        for (final JsonNode hit : hits) {
            eventIds.add(hit.at("/_source/EventId").asLong());
            streamIds.add(hit.at("/_source/StreamId").asLong());
        }
        assertThat(eventIds)
                .containsExactlyElementsOf(LongStream
                        .rangeClosed(1, ElasticEndToEndTestHelper.NETWORK_EVENT_COUNT)
                        .boxed()
                        .toList());
        assertThat(streamIds)
                .doesNotContainNull()
                .containsOnly(streamIds.getFirst());

        // The first event, an authentication failure
        final JsonNode first = hits.get(0).get("_source");
        assertThat(first.get("Feed").asString()).isEqualTo(ElasticEndToEndTestHelper.NETWORK_FEED);
        assertThat(first.get("UserId").asString()).isEqualTo("user1");
        assertThat(first.get("HostName").asString()).isEqualTo("server1");
        assertThat(first.get("Action").asString()).isEqualTo("Authenticate");
        assertThat(first.has("Command")).isFalse();

        // The mapped field types are used, e.g. dates and keywords can be queried as such
        assertThat(node.count(indexName, """
                {"range": {"EventTime": {"gte": "2007-08-18T00:00:00Z", "lt": "2007-08-19T00:00:00Z"}}}"""))
                .isEqualTo(19);
        assertThat(node.count(indexName, """
                {"term": {"UserId": "user5"}}"""))
                .isEqualTo(5);
        assertThat(node.count(indexName, """
                {"term": {"Command.keyword": "msg=foo bar"}}"""))
                .isEqualTo(19);
    }

    @Test
    void testIndexNameVariablesAndSkippedDocuments() {
        final String indexBaseName = ElasticTestNode.createIndexName("typed");
        templatesToDelete.add(indexBaseName);
        indexPatternsToDelete.add(indexBaseName + "-*");
        helper.indexTypedEvents(node, clusterRef, indexBaseName);

        // Each document goes to the index named by its own root `year` property. The nested `Meta.year`
        // property must neither name the index nor be treated as a second value for the variable.
        final Map<String, Long> docCountByIndex = new HashMap<>();
        for (final JsonNode index : node.catIndices(indexBaseName + "-*")) {
            // The `_cat/indices` doc count includes the hidden documents of nested objects, so count the
            // events themselves
            final String indexName = index.get("index").asString();
            docCountByIndex.put(indexName, node.count(indexName, null));
        }
        assertThat(docCountByIndex).containsOnly(
                Map.entry(indexBaseName + "-2025", 1L),
                Map.entry(indexBaseName + "-2026", 2L));

        // The skipped (empty) second event must not leave anything behind to corrupt the third, which follows
        // it in the same bulk request
        final JsonNode hits = node.search(indexBaseName + "-*", """
                {
                  "size": 10,
                  "sort": [{"EventId": "asc"}]
                }""").at("/hits/hits");
        final List<Long> eventIds = new ArrayList<>();
        hits.forEach(hit -> eventIds.add(hit.at("/_source/EventId").asLong()));
        assertThat(eventIds).containsExactly(1L, 3L, 4L);

        final JsonNode third = hits.get(1).get("_source");
        assertThat(third.get("Host").asString()).isEqualTo("beta");
        assertThat(third.get("Time").asString()).isEqualTo("2026-01-01T00:00:00.000000001Z");
        assertThat(third.get("Ip").asString()).isEqualTo("10.0.0.2");
        assertThat(third.get("Bytes").asString()).isEqualTo("9223372036854775808");
        assertThat(third.get("year").asInt()).isEqualTo(2026);
        assertThat(third.at("/Meta/year").asInt()).isEqualTo(1999);

        // A value beyond the range of a long is kept intact
        assertThat(hits.get(0).at("/_source/Bytes").asString()).isEqualTo("18446744073709551615");

        // Arrays of objects, within arrays of objects, keep their structure
        final JsonNode users = hits.get(0).at("/_source/Users");
        assertThat(users.size()).isEqualTo(2);
        assertThat(users.get(0).get("Name").asString()).isEqualTo("alice");
        assertThat(users.get(1).get("Name").asString()).isEqualTo("bob");
        assertThat(users.get(1).at("/Sessions/1/Device").asString()).isEqualTo("tablet");
    }

    @Test
    void testReprocessingDeletesTheStreamsDocumentsFirst() {
        final String indexBaseName = ElasticTestNode.createIndexName("reprocess");
        templatesToDelete.add(indexBaseName);
        indexPatternsToDelete.add(indexBaseName + "-*");
        // The skipped event logs a warning, giving the stream an Error stream for the reprocessing filter to
        // select, while the other three events are indexed
        final DocRef pipelineRef = helper.indexTypedEvents(node, clusterRef, indexBaseName, true);
        assertThat(helper.countStreams(ElasticEndToEndTestHelper.TYPED_FEED, StreamTypeNames.ERROR)).isOne();
        final Set<String> firstIds = getDocumentIds(indexBaseName + "-*");
        assertThat(firstIds).hasSize(3);

        reprocess(pipelineRef, indexBaseName + "-*");

        // The documents from the first run were deleted, and the stream's events indexed again, in both indices
        // the index name variable gives
        final Set<String> secondIds = getDocumentIds(indexBaseName + "-*");
        assertThat(secondIds)
                .hasSize(3)
                .doesNotContainAnyElementsOf(firstIds);
        assertThat(node.count(indexBaseName + "-2025", null)).isOne();
        assertThat(node.count(indexBaseName + "-2026", null)).isEqualTo(2);
    }

    @Test
    void testReprocessingWithoutPurgeIndexesTheStreamAgain() {
        final String indexBaseName = ElasticTestNode.createIndexName("reprocess");
        templatesToDelete.add(indexBaseName);
        indexPatternsToDelete.add(indexBaseName + "-*");
        final DocRef pipelineRef = helper.indexTypedEvents(node, clusterRef, indexBaseName, false);
        final Set<String> firstIds = getDocumentIds(indexBaseName + "-*");

        reprocess(pipelineRef, indexBaseName + "-*");

        // Without the purge, the first run's documents remain alongside the second's
        final Set<String> secondIds = getDocumentIds(indexBaseName + "-*");
        assertThat(secondIds)
                .hasSize(6)
                .containsAll(firstIds);
    }

    @Test
    void testRejectedDocumentsAreReportedAsErrors() {
        final String indexName = ElasticTestNode.createIndexName("rejected");
        indexPatternsToDelete.add(indexName);
        // The index only allows the fields it maps, so the documents the network XSLT produces (with fields
        // such as UserId that aren't mapped here) are all rejected by Elasticsearch
        node.createIndex(indexName, """
                {
                  "mappings": {
                    "dynamic": "strict",
                    "properties": {
                      "StreamId": {"type": "long"},
                      "EventId": {"type": "long"}
                    }
                  }
                }""");

        helper.translateNetworkEvents();
        helper.createIndexingPipeline(
                "Rejected network events indexing",
                ElasticEndToEndTestHelper.NETWORK_XSLT,
                clusterRef,
                indexName,
                ElasticEndToEndTestHelper.NETWORK_FEED);
        final List<ProcessorResult> results = helper.processAll();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getMarkerCount(Severity.ERROR, Severity.FATAL_ERROR))
                .as("Processing errors for the rejected documents")
                .isPositive();
        node.refresh(indexName);
        assertThat(node.count(indexName, null)).isZero();
    }

    /// Reprocess the streams that a pipeline produced an Error stream for, checking it had no errors.
    private void reprocess(final DocRef pipelineRef, final String indexPattern) {
        helper.createProcessorFilter(pipelineRef, ElasticEndToEndTestHelper.TYPED_FEED, StreamTypeNames.ERROR, true);
        final List<ProcessorResult> results = helper.processAll();
        assertThat(results).hasSize(1);
        ElasticEndToEndTestHelper.assertNoErrors(results.getFirst());
        node.refresh(indexPattern);
    }

    private Set<String> getDocumentIds(final String indexPattern) {
        final Set<String> ids = new HashSet<>();
        node.search(indexPattern, """
                {
                  "size": 100,
                  "_source": false
                }""").at("/hits/hits").forEach(hit -> ids.add(hit.get("_id").asString()));
        return ids;
    }
}
