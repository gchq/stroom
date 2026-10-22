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

import stroom.ai.impl.tools.ToolResult;
import stroom.docref.DocRef;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.LuceneIndexDoc;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/// reprocess_streams (the Stroom MCP server's `tests/test_processing.py`, its reprocessing cases, and
/// `tests/test_off_path.py`'s wrong move for it): each case was seen in testing, as its comment says.
class TestReprocessStreamsTool {

    private static final DocRef P1 = ProcessingWorld.P1;
    private static final DocRef EV = ProcessingWorld.EV;
    private static final DocRef LUCENE = new DocRef(LuceneIndexDoc.TYPE, "ix", "ACME-INDEX");

    private ProcessingWorld w;

    @BeforeEach
    void setUp() {
        w = new ProcessingWorld();
    }

    @Test
    void reprocessingRunsOneTaskAtATimeAndLeavesSupersedingToStroom() {
        // An events pipeline in a build: the user approves, and the filter is created enabled (asked for by the
        // user).
        translationInBuild();
        w.idFilter("p1", 3, 5);
        w.output(6, 99, "Events", "p1", 3);

        final Gated gated = reprocessed(Map.of("stream_ids", List.of(5, 6)));

        assertThat(gated.statuses()).containsExactly("needs_approval");
        assertThat(String.valueOf(TestCreateProcessorFilterTool.details(gated.gates().getFirst())
                .get("earlier outputs"))).contains("superseded");
        assertThat(w.created.getLast().getMaxProcessingTasks()).isEqualTo(1);
        assertThat(w.created.getLast().isEnabled()).isTrue();
        assertThat(gated.result().get("filter_id")).isEqualTo(9);
        assertThat(gated.result().get("enabled")).isEqualTo(true);
        assertThat(String.valueOf(gated.result().get("hint"))).contains("filter_id=9");
        // Stroom's own reprocessing is left alone: a new filter on the pipeline, by stream id.
        Mockito.verify(w.filterService, Mockito.never()).reprocess(any());
        assertThat(w.created.getLast().isReprocess()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"forwarding", "reference"})
    void reprocessingAnyOtherPipelineLeavesItsFilterToTheUser(final String stage) {
        // Only events and indexing pipelines reprocess on the user's approval; a CEF output sends to Kafka.
        w.pipeline(P1, true, null);
        if ("forwarding".equals(stage)) {
            w.layers("p1", w.layer("p1", "xsltFilter", "XSLTFilter", "kafka", "StandardKafkaProducer"));
        } else {
            w.layers("p1", w.layer("p1", "xsltFilter", "XSLTFilter", "streamAppender", "StreamAppender")
                    .property("streamAppender", "streamType", "Reference"));
        }
        w.buildFeed("ACME");
        w.stream(5, "ACME", "Raw Events", null, null);
        w.idFilter("p1", 3, 5);

        final Gated gated = reprocessed(Map.of("stream_ids", List.of(5)));

        assertThat(gated.gates()).isEmpty();
        assertThat(w.created.getLast().isEnabled()).isFalse();
        assertThat(gated.result().get("enabled")).isEqualTo(false);
        assertThat(String.valueOf(gated.result().get("hint"))).contains("enable filter 9");
    }

    @ParameterizedTest
    @MethodSource("bounds")
    void reprocessingIsBoundedToTenStreamsItAlreadyProcessed(final List<Long> ids, final String message) {
        translationInBuild();
        w.idFilter("p1", 3, 5);

        final ToolResult result = call(Map.of("stream_ids", ids));

        assertThat(result.isError()).isTrue();
        assertThat(result.text()).contains(message);
        assertThat(w.created).isEmpty();
    }

    @Test
    void reprocessWhatWasNeverProcessedSaysToCreateAFilter() {
        // The Stroom MCP server's tests/test_off_path.py: the message names the call that puts it right.
        translationInBuild();

        final ToolResult result = call(Map.of("stream_ids", List.of(5)));

        assertThat(result.text()).contains("have not been processed").contains("create_processor_filter");
    }

    @Test
    void reprocessingIntoElasticsearchConfirmsTheTemplateInItsApproval() {
        w.pipeline(P1, true, ProcessingWorld.withAgreed(ProcessingWorld.agreed(
                "PUT _index_template/ecs-acme-v2\n{}")));
        w.elastic("p1");
        w.pipeline(EV, true, null);
        w.translation("ev");
        w.stream(5, "ACME-EVENTS", "Events", "ev", 4L);
        w.idFilter("p1", 3, 5);

        final Gated gated = reprocessed(Map.of("stream_ids", List.of(5), "source_pipeline_uuid", "ev"));

        assertThat(gated.statuses()).containsExactly("drop_first", "needs_approval");
        assertThat(TestCreateProcessorFilterTool.actions(gated.gates().getFirst()))
                .extracting(action -> action.get("request"))
                .containsExactly("DELETE ecs-acme-v2");
        final Map<String, Object> details = TestCreateProcessorFilterTool.details(gated.gates().getLast());
        assertThat(String.valueOf(details.get("index template"))).contains("index 'ecs-acme-v2'");
        assertThat(String.valueOf(details.get("already indexed")))
                .contains("drops it first, so it starts fresh: DELETE ecs-acme-v2.");
        assertThat(String.valueOf(gated.gates().getLast().get("summary"))).contains("once the index is dropped");
        assertThat(w.created.getLast().isEnabled()).isTrue();
        assertThat(gated.result().get("filter_id")).isEqualTo(9);
        assertThat(w.lastExpression().getChildren().get(1)).isEqualTo(ProcessorFilterWrites.pipelineTerm(EV));
    }

    @ParameterizedTest
    @MethodSource("luceneIndexes")
    void reprocessingIntoTheBuildsOwnLuceneIndexEmptiesItFirst(final boolean inBuild,
                                                                final boolean otherWriter,
                                                                final boolean emptied) {
        // Seen (eval, Haiku): an indexing XSLT fixed and its stream reprocessed left each event in the index twice.
        w.pipeline(P1, true, null);
        w.lucene("p1", LUCENE);
        w.buildFeed("ACME");
        w.stream(5, "ACME", "Raw Events", null, null);
        w.stream(6, "ACME", "Raw Events", null, null);
        w.idFilter("p1", 3, 5, 6);
        if (inBuild) {
            w.world.inBuild(ProcessingWorld.BUILD, LuceneIndexDoc.TYPE, LUCENE.getName(), LUCENE.getUuid());
        } else {
            w.world.add(LuceneIndexDoc.TYPE, LUCENE.getName(), w.world.folder("System/Indexes").uuid,
                    LUCENE.getUuid());
        }
        if (otherWriter) {
            final DocRef p2 = new DocRef("Pipeline", "p2", "Acme-Other");
            w.pipeline(p2, true, null);
            w.lucene("p2", LUCENE);
        }
        Mockito.when(w.indexResource.find(any(FindIndexShardCriteria.class))).thenReturn(new ResultPage<>(List.of(
                shard(434, IndexShardStatus.OPEN, 5), shard(400, IndexShardStatus.DELETED, 3))));

        final Gated gated = reprocessed(Map.of("stream_ids", List.of(5)));

        final Map<String, Object> details = TestCreateProcessorFilterTool.details(gated.gates().getLast());
        assertThat(gated.result().get("enabled")).isEqualTo(true);
        assertThat(w.created.getLast().isEnabled()).isTrue();
        // The server empties only its build's own index; any other is the user's, first, from its doc.
        if (emptied) {
            assertThat(gated.statuses()).containsExactly("needs_approval");
            assertThat(String.valueOf(details.get("earlier documents"))).contains("deleted first");
            assertThat(details).doesNotContainKey("already indexed");
            assertThat(w.deletedShards).containsExactly("node1a:[434]");
            final ArgumentCaptor<FindIndexShardCriteria> found = ArgumentCaptor.forClass(FindIndexShardCriteria.class);
            Mockito.verify(w.indexResource).find(found.capture());
            assertThat(found.getValue().getIndexUuidSet().getSet()).containsExactly("ix");
            assertThat(found.getValue().getIndexUuidSet().isMatchAll()).isFalse();
        } else {
            assertThat(gated.statuses()).containsExactly("drop_first", "needs_approval");
            assertThat(TestCreateProcessorFilterTool.actions(gated.gates().getFirst()))
                    .extracting(action -> action.get("action"))
                    .containsExactly("open_document");
            assertThat(String.valueOf(details.get("already indexed"))).contains("delete its shards");
            assertThat(details).doesNotContainKey("earlier documents");
            assertThat(w.deletedShards).isEmpty();
        }
    }

    static Stream<Arguments> bounds() {
        return Stream.of(
                Arguments.of(LongStream.rangeClosed(1, 11).boxed().toList(), "Give 1 to 10 streams"),
                Arguments.of(List.of(5L, 7L), "[7] have not been processed"));
    }

    static Stream<Arguments> luceneIndexes() {
        return Stream.of(
                // The build's own index: emptied first, starting fresh.
                Arguments.of(true, false, true),
                // Another pipeline of the build writes to it.
                Arguments.of(true, true, false),
                // Not the build's.
                Arguments.of(false, false, false));
    }

    // p1, a translation pipeline in the build, stepped clean, reading streams 5 and 6 of its feed ACME.
    private void translationInBuild() {
        w.pipeline(P1, true, null);
        w.translation("p1");
        w.buildFeed("ACME");
        w.steppedClean(P1);
        w.stream(5, "ACME", "Raw Events", null, null);
        w.stream(6, "ACME", "Raw Events", null, null);
    }

    private static IndexShard shard(final long id, final IndexShardStatus status, final int documents) {
        return IndexShard.builder()
                .id(id)
                .status(status)
                .documentCount(documents)
                .nodeName("node1a")
                .indexUuid("ix")
                .build();
    }

    private ToolResult call(final Map<String, Object> arguments) {
        final Map<String, Object> all = new LinkedHashMap<>(arguments);
        all.put("pipeline_uuid", "p1");
        return w.reprocessTool().execute(PortedToolCalls.call(ReprocessStreamsTool.NAME,
                JsonUtil.writeValueAsString(all)), ProcessingWorld.context());
    }

    // Calls as a user agreeing to every gate: the gates seen, and the final result.
    private Gated reprocessed(final Map<String, Object> arguments) {
        final Map<String, Object> given = new LinkedHashMap<>(arguments);
        final List<Map<String, Object>> gates = new ArrayList<>();
        while (true) {
            final ToolResult called = call(given);
            assertThat(called.isError()).as(called.text()).isFalse();
            final Map<String, Object> result = ProcessingWorld.json(called);
            final String status = String.valueOf(result.get("status"));
            if ("drop_first".equals(status)) {
                gates.add(result);
                given.put("index_dropped", true);
                continue;
            }
            if (!status.startsWith("needs_")) {
                return new Gated(gates, result);
            }
            gates.add(result);
            final String key = "needs_confirmation".equals(status)
                    ? "confirmation_id"
                    : "approval_id";
            given.put(key, result.get(key));
        }
    }


    // --------------------------------------------------------------------------------


    // The gates a call went through, and its result.
    private record Gated(List<Map<String, Object>> gates, Map<String, Object> result) {

        List<String> statuses() {
            return gates.stream().map(gate -> String.valueOf(gate.get("status"))).toList();
        }
    }
}
