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
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// create_processor_filter (the Stroom MCP server's `tests/test_processing.py`, its filter cases, and
/// `tests/test_off_path.py`'s wrong moves for it): each case was seen in testing, as its comment says.
class TestCreateProcessorFilterTool {

    private static final DocRef P1 = ProcessingWorld.P1;
    private static final DocRef EV = ProcessingWorld.EV;
    private static final ExpressionTerm PIPELINE_TERM = ProcessorFilterWrites.pipelineTerm(EV);

    private ProcessingWorld w;

    @BeforeEach
    void setUp() {
        w = new ProcessingWorld();
    }

    @Test
    void processingRefusesAPipelineThatWasNotSteppedClean() {
        translationInBuild(false);
        w.stream(6, "ACME", "Raw Events", null, null);

        final ToolResult result = call(Map.of("stream_ids", List.of(6)));

        assertThat(result.isError()).isTrue();
        assertThat(result.text()).contains("no clean step of its current code recorded").contains("step_sample");
        assertThat(w.created).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void streamsThePipelineAlreadyProcessedGoThroughReprocessing(final boolean filtered) {
        translationInBuild(true);
        w.stream(5, "ACME", "Raw Events", null, null);
        w.stream(6, "ACME", "Raw Events", null, null);
        if (filtered) {
            w.idFilter("p1", 3, 5);
        } else {
            w.output(5, 99, "Events", "p1", 3);
        }

        final ToolResult result = call(Map.of("stream_ids", List.of(5, 6)));

        assertThat(result.text()).contains("already processed stream(s) [5]: use reprocess_streams");
        assertThat(w.created).isEmpty();
    }

    @Test
    void theSamplesFilterIsCreatedEnabledOnceTheUserApproves() {
        // Asked for by the user: an events pipeline's sample filter, left disabled for them to enable in Stroom,
        // wasn't.
        translationInBuild(true);
        w.stream(6, "ACME", "Raw Events", null, null);

        final Gated gated = gatedThrough(Map.of("stream_ids", List.of(6)));

        assertThat(gated.statuses()).containsExactly("needs_approval");
        assertThat(details(gated.gates().getFirst()).get("scope")).isEqualTo("streams [6]");
        assertThat(gated.result().get("filter_id")).isEqualTo(9);
        assertThat(gated.result().get("enabled")).isEqualTo(true);
        assertThat(w.created).hasSize(1);
        assertThat(w.created.getLast().isEnabled()).isTrue();
        assertThat(String.valueOf(gated.result().get("hint"))).contains("filter_id=9");
    }

    @Test
    void sampleFiltersRunOneTaskAtATime() {
        translationInBuild(true);
        w.stream(6, "ACME", "Raw Events", null, null);

        gatedThrough(Map.of("stream_ids", List.of(6)));

        assertThat(w.created.getLast().getMaxProcessingTasks()).isEqualTo(1);
    }

    @Test
    void aFeedsFilterInABuildRunsOnceTheUserApproves() {
        // Seen in testing: the build's events pipeline given the feed, its filter made disabled, and the agent asked
        // the user to enable it between its steps. Approved in a form instead, as a sample is (asked for by the
        // user).
        translationInBuild(true);
        w.stream(Meta.builder().id(12).feedName("ACME").typeName("Raw Events").status(Status.UNLOCKED)
                .createMs(ProcessingWorld.FEED_MADE + 100_000_000L).build());

        final Gated gated = gatedThrough(Map.of("feed", "ACME", "created_after", "2026-10-10T00:00:00Z"));

        assertThat(gated.statuses()).containsExactly("needs_approval");
        assertThat(String.valueOf(gated.gates().getFirst().get("summary"))).contains("process the feed");
        assertThat(String.valueOf(details(gated.gates().getFirst()).get("scope")))
                .startsWith("feed ACME (Raw Events) created after");
        assertThat(w.created.getLast().isEnabled()).isTrue();
        assertThat(gated.result().get("enabled")).isEqualTo(true);
    }

    @Test
    void aFilterOnOneSendingElsewhereIsLeftDisabledForTheUser() {
        // A pipeline sending to Kafka: disabled, no form; enabling it in Stroom is the user's approval.
        inBuild();
        w.layers("p1", w.layer("p1", "xsltFilter", "XSLTFilter", "kafka", "StandardKafkaProducer"));
        w.steppedClean(P1);
        w.buildFeed("ACME");
        w.stream(6, "ACME", "Raw Events", null, null);

        final Gated gated = gatedThrough(Map.of("stream_ids", List.of(6)));

        assertThat(gated.gates()).isEmpty();
        assertThat(gated.result().get("filter_id")).isEqualTo(9);
        assertThat(w.created).hasSize(1);
        assertThat(w.created.getLast().isEnabled()).isFalse();
        assertThat(gated.result().get("enabled")).isEqualTo(false);
        assertThat(String.valueOf(gated.result().get("pipeline_link"))).contains("docUuid=p1");
        assertThat(String.valueOf(gated.result().get("hint")))
                .contains("enable filter 9 on its Processors tab")
                .contains("not before");
        assertThat(String.valueOf(details(gated.result(), "enabling_it").get("sends"))).contains("Kafka record");
        // As a button (asked for by the user), with nothing to do first here.
        final List<Map<String, Object>> actions = actions(gated.result());
        assertThat(actions).hasSize(1);
        assertThat(actions.getFirst().get("action")).isEqualTo("enable_processor_filter");
        assertThat(actions.getFirst().get("filter_id")).isEqualTo(9);
        assertThat(actions.getFirst()).doesNotContainKey("after");
    }

    @Test
    void elasticsearchIndexingIsApprovedOnceTheTemplateIsCommitted() {
        // The approval also confirms the admin has committed the agreed template.
        elasticInBuild(ProcessingWorld.agreed("PUT _index_template/ecs-acme-v2\n{}"));

        final Gated gated = gatedThrough(Map.of("stream_ids", List.of(6), "source_pipeline_uuid", "ev"));

        assertThat(gated.statuses()).last().isEqualTo("needs_approval");
        assertThat(String.valueOf(gated.gates().getLast().get("summary"))).contains("template is committed");
        assertThat(details(gated.gates().getLast()).get("index template")).isEqualTo(
                "the agreed index template 'ecs-acme-v2' for Elasticsearch index 'ecs-acme-v2' is committed to "
                + "cluster ES_DEV");
        assertThat(w.created.getLast().isEnabled()).isTrue();
        assertThat(gated.result().get("enabled")).isEqualTo(true);
        // While it is developed in the build, Elasticsearch indexes in small batches.
        assertThat(String.valueOf(gated.result().get("batch_size"))).startsWith("batch size 10 while developing");
        assertThat(PipelineDocs.addedProperties(w.pipelineDoc("p1")))
                .extracting(PipelineProperty::getName)
                .containsExactly("batchSize");
    }

    @ParameterizedTest
    @MethodSource("drops")
    void anIndexThePipelineWroteToIsDroppedFirst(final String devTools, final String drop) {
        // Seen in testing: the translation amended and the sample processed again, its new Events indexed, the
        // index held the superseded Events' documents beside them. The whole index is dropped and starts fresh
        // (asked for by the user): the pipeline indexed Events stream 4 before.
        elasticInBuild(ProcessingWorld.agreed(devTools));
        w.idFilter("p1", 3, 4);

        final Gated gated = gatedThrough(Map.of("stream_ids", List.of(6), "source_pipeline_uuid", "ev"));

        // Asked for by the user: the drop comes first, as a button, not only as a line in the approval form.
        assertThat(gated.statuses()).containsExactly("drop_first", "needs_approval");
        final List<Map<String, Object>> buttons = actions(gated.gates().getFirst());
        assertThat(buttons).hasSize(1);
        assertThat(buttons.getFirst().get("action")).isEqualTo("elasticsearch_request");
        assertThat(buttons.getFirst().get("request")).isEqualTo(drop.substring(0, drop.length() - 1));
        assertThat(String.valueOf(gated.gates().getFirst().get("hint"))).contains("index_dropped=true");
        final String said = String.valueOf(details(gated.gates().getLast()).get("already indexed"));
        assertThat(gated.gates().getFirst().get("already_indexed")).isEqualTo(said);
        assertThat(said).contains("the cluster admin drops it first, so it starts fresh: " + drop)
                .doesNotContain("_delete_by_query");
        assertThat(String.valueOf(gated.gates().getLast().get("summary"))).contains("once the index is dropped");
        assertThat(gated.result().get("enabled")).isEqualTo(true);
        assertThat(w.created).hasSize(1);
    }

    @Test
    void anIndexNothingWasIndexedIntoYetNeedsNoDrop() {
        elasticInBuild(ProcessingWorld.agreed("PUT _index_template/ecs-acme-v2\n{}"));

        final Gated gated = gatedThrough(Map.of("stream_ids", List.of(6), "source_pipeline_uuid", "ev"));

        assertThat(details(gated.gates().getFirst())).doesNotContainKey("already indexed");
    }

    @ParameterizedTest
    @MethodSource("agreements")
    void indexingIntoElasticsearchNeedsATemplateAgreedForTheCurrentPipeline(final Map<String, Object> agreed,
                                                                             final String message) {
        // Only a template the user confirmed, for this index and the documents the XSLT now writes, is asked about.
        elasticInBuild(agreed);

        final ToolResult result = call(Map.of("stream_ids", List.of(6), "source_pipeline_uuid", "ev"));

        assertThat(result.isError()).isTrue();
        assertThat(result.text()).contains(message);
        assertThat(w.created).isEmpty();
    }

    @Test
    void indexingFilterOnlySelectsEventsFromTheSourceEventsPipeline() {
        elasticInBuild(ProcessingWorld.agreed("PUT _index_template/ecs-acme-v2\n{}"));
        w.stream(7, "ACME-EVENTS", "Events", "ev", 4L);

        final Gated gated = gatedThrough(Map.of("stream_ids", List.of(6, 7), "source_pipeline_uuid", "ev"));

        assertThat(w.lastExpression()).isEqualTo(ExpressionOperator.builder().op(Op.AND)
                .addOperator(ExpressionOperator.builder().op(Op.OR)
                        .addTerm(ProcessorFilterWrites.term("Id", 6))
                        .addTerm(ProcessorFilterWrites.term("Id", 7))
                        .build())
                .addTerm(PIPELINE_TERM)
                .build());
        assertThat(String.valueOf(gated.result().get("scope"))).contains("only Events from pipeline 'Acme-Events'");
        assertThat(gated.result().get("events_from_pipeline")).isEqualTo("Acme-Events");
    }

    @Test
    void feedWideIndexingFilterCarriesThePipelineCondition() {
        elasticInBuild(ProcessingWorld.agreed("PUT _index_template/ecs-acme-v2\n{}"));

        gatedThrough(Map.of("feed", "ACME", "stream_type", "Events", "created_after", "2026-09-29T00:00:00Z",
                "source_pipeline_uuid", "ev"));

        final List<ExpressionItem> children = w.lastExpression().getChildren();
        // The source pipeline says which Events: no feed condition beside it (asked for by the user).
        assertThat(children.get(1)).isEqualTo(PIPELINE_TERM);
        assertThat(((ExpressionOperator) children.getFirst()).getChildren())
                .extracting(item -> ((ExpressionTerm) item).getField())
                .containsExactly("Type");
    }

    @ParameterizedTest
    @MethodSource("untied")
    void indexingRefusesEventsItCannotTieToTheSource(final List<Meta> streams,
                                                     final List<Integer> ids,
                                                     final String source,
                                                     final String message) {
        elasticInBuild(ProcessingWorld.agreed("PUT _index_template/ecs-acme-v2\n{}"));
        streams.forEach(w::stream);
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("stream_ids", ids);
        if (source != null) {
            arguments.put("source_pipeline_uuid", source);
        }

        final ToolResult result = call(arguments);

        assertThat(result.text()).contains(message);
        assertThat(w.created).isEmpty();
    }

    @Test
    void translationPipelinesTakeNoSourcePipeline() {
        translationInBuild(true);
        w.stream(6, "ACME", "Raw Events", null, null);

        final ToolResult result = call(Map.of("stream_ids", List.of(6), "source_pipeline_uuid", "ev"));

        assertThat(result.text()).contains("only for indexing pipelines");
    }

    @Test
    void indexingEventsFromAnotherPipelineNeedsTheUsersConfirmation() {
        inBuild();
        w.steppedClean(P1);
        w.layers("p1", w.layer("p1", "indexingFilter", "IndexingFilter"));
        // Not one this server built.
        w.pipeline(EV, false, null);
        w.translation("ev");
        w.stream(6, "ACME-EVENTS", "Events", "ev", 4L);
        final Map<String, Object> arguments = new LinkedHashMap<>(Map.of("stream_ids", List.of(6),
                "source_pipeline_uuid", "ev"));

        final Map<String, Object> first = ProcessingWorld.json(call(arguments));
        assertThat(first.get("status")).isEqualTo("needs_confirmation");
        assertThat(String.valueOf(first.get("summary"))).contains("which this server did not build");
        arguments.put("source_confirmation_id", first.get("confirmation_id"));
        final Map<String, Object> approve = ProcessingWorld.json(call(arguments));
        assertThat(approve.get("status")).isEqualTo("needs_approval");
        arguments.put("approval_id", approve.get("approval_id"));
        final Map<String, Object> done = ProcessingWorld.json(call(arguments));

        assertThat(done.get("events_from_pipeline")).isEqualTo("Acme-Events");
        assertThat(w.lastExpression().getChildren().get(1)).isEqualTo(PIPELINE_TERM);
    }

    @Test
    void translationPipelinesOnlyProcessTheBuildsFeeds() {
        translationInBuild(true);
        w.feedAt("System/Feeds", "PROD-FEED", "pf", "Raw Events");
        w.stream(6, "PROD-FEED", "Raw Events", null, null);

        final ToolResult refused = call(Map.of("stream_ids", List.of(6)));
        final ToolResult byFeed = call(Map.of("feed", "PROD-FEED", "created_after", "2026-09-29T00:00:00Z"));

        assertThat(refused.text()).contains("Feed(s) ['PROD-FEED'] are not in this build");
        // The way to work on that feed's data is start_build feeds=; never a copy of its records (seen: told to
        // copy them into a test feed, Haiku made one, and a workspace's sample files would have gone in).
        assertThat(refused.text()).contains("start_build").doesNotContain("upload_sample");
        assertThat(byFeed.text()).contains("not in this build");
        assertThat(w.created).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aBuildsFeedIsFoundWhateverCaseTheStreamDataSpellsIt(final boolean earlierFeed) {
        // Qwen in VS Code: DELINEA-SECRETSERVER-V1.0 in the build, an earlier Delinea-SecretServer-V1.0 in another;
        // Stroom filed the new feed's sample under the earlier spelling, and processing was refused as another
        // build's feed. Gemma in VS Code: the earlier feed deleted, so no feed has the stream data's spelling at
        // all.
        otherInBuild();
        w.world.createTimeMs = 0;
        w.feeds.buildFeed(ProcessingWorld.BUILD, "DELINEA-SECRETSERVER-V1.0");
        if (earlierFeed) {
            w.feeds.buildFeed("a-earlier", "Delinea-SecretServer-V1.0");
        }
        w.stream(6, "Delinea-SecretServer-V1.0", "Raw Events", null, null);

        final Map<String, Object> made = ProcessingWorld.json(call(Map.of("stream_ids", List.of(6))));

        assertThat(made.get("filter_id")).isEqualTo(9);
        assertThat(w.created).hasSize(1);
    }

    @Test
    void aFeedWideFilterStartsNoEarlierThanItsFeed() {
        // Seen (Qwen, VS Code): created_after a week before the feed was made also selects an earlier, deleted
        // namesake's streams, which Stroom keeps under the same name.
        otherInBuild();
        w.buildFeed("ACME");
        w.stream(6, "ACME", "Raw Events", null, null);

        final Gated gated = gatedThrough(Map.of("feed", "ACME", "created_after", "2026-10-01T00:00:00Z"));

        assertThat(w.created.getLast().getMinMetaCreateTimeMs()).isEqualTo(ProcessingWorld.FEED_MADE);
        assertThat(String.valueOf(gated.result().get("scope")))
                .contains("created after 2026-10-09T21:06:40.000Z (when the feed was created");
    }

    @Test
    void aFeedWideFilterThatSelectsNoneOfTheFeedsStreamsIsRefused() {
        // Seen (Gemma, VS Code): created_after midnight UTC, after the sample arrived: nothing was ever selected.
        otherInBuild();
        w.buildFeed("ACME");
        w.stream(6, "ACME", "Raw Events", null, null);
        // A namesake's.
        w.stream(Meta.builder().id(2).feedName("ACME").typeName("Raw Events").status(Status.UNLOCKED)
                .createMs(ProcessingWorld.FEED_MADE - 60_000).build());

        final ToolResult result = call(Map.of("feed", "ACME", "created_after", "2026-10-10T00:00:00Z"));

        assertThat(result.text()).contains("selects none of the feed's streams")
                .contains(": 6 (2026-10-09T21:07:40.000Z). To process the sample, give stream_ids");
        assertThat(w.created).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aFeedWideFilterForNewDataOnlyFollowsTheProcessedSample(final boolean filtered) {
        // The discovery flow: the sample processed by stream id, then the feed's new data from now on.
        otherInBuild();
        w.buildFeed("ACME");
        w.stream(6, "ACME", "Raw Events", null, null);
        if (filtered) {
            w.idFilter("p1", 3, 6);
        } else {
            w.output(6, 99, "Events", "p1", 3);
        }

        gatedThrough(Map.of("feed", "ACME", "created_after", "2026-10-10T00:00:00Z"));

        assertThat(w.created).hasSize(1);
    }

    @Test
    void theSameFeedWideFilterIsNotMadeTwice() {
        otherInBuild();
        w.buildFeed("ACME");
        w.filter("p1", 775, true, ExpressionOperator.builder()
                .addTerm(ProcessorFilterWrites.term("Feed", "ACME"))
                .addTerm(ProcessorFilterWrites.term("Type", "Raw Events"))
                .build(), ProcessingWorld.FEED_MADE);

        final ToolResult result = call(Map.of("feed", "ACME", "created_after", "2026-10-09T22:00:00Z"));

        assertThat(result.text()).contains("Processor filter 775 on this pipeline already selects these streams");
        assertThat(w.created).isEmpty();
    }

    @Test
    void anExistingFeedsStreamsAreProcessedIntoATestFeedInTheBuild() {
        // Asked for by the user: a build on an existing feed's data processes its streams where they are, the
        // Events written to a test feed the server makes in the build through the StreamAppender's feed property;
        // nothing is uploaded and no feed is made for raw samples.
        otherInBuild();
        w.feeds.facts.sources = List.of("PROD-FEED");
        w.feedAt("System/Feeds", "PROD-FEED", "pf", "Raw Events");
        w.stream(6, "PROD-FEED", "Raw Events", null, null);

        final Map<String, Object> result = ProcessingWorld.json(call(Map.of("stream_ids", List.of(6))));

        assertThat(w.created).hasSize(1);
        assertThat(result.get("output_feed")).isEqualTo("PROD-FEED-MCP-TEST");
        assertThat(result.get("enabled")).isEqualTo(false);
        assertThat(String.valueOf(result.get("output_note"))).contains("not into the production feed")
                .contains("Nothing is uploaded").contains("(made now)");
        assertThat(PipelineDocs.addedProperties(w.pipelineDoc("p1")))
                .extracting(property -> property.getElement() + "." + property.getName() + "="
                                        + property.getValue().getEntity().getName())
                .containsExactly("streamAppender.feed=PROD-FEED-MCP-TEST");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aPromotedPipelineGetsNoSampleFilterEnabledOrOtherwise(final boolean indexing) {
        // Asked by the user: sample filters are created enabled only in a build. Promotion takes mcp-managed off
        // (only mcp-generated stays), and the write guard then refuses the pipeline before any form or filter.
        w.pipeline(P1, false, null, WriteGuard.GENERATED);
        if (indexing) {
            w.elastic("p1");
        } else {
            w.translation("p1");
        }
        w.stream(6, "ACME", "Raw Events", null, null);

        final ToolResult created = call(Map.of("stream_ids", List.of(6)));
        final ToolResult reprocessed = w.reprocessTool().execute(PortedToolCalls.call(ReprocessStreamsTool.NAME,
                "{\"pipeline_uuid\": \"p1\", \"stream_ids\": [6]}"), ProcessingWorld.context());

        assertThat(created.text()).contains("was not created by this server");
        assertThat(reprocessed.text()).contains("was not created by this server");
        assertThat(w.created).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wrongMoves")
    void aWrongMoveIsStoppedAndSaysWhatToDoInstead(final String name,
                                                   final Map<String, Object> arguments,
                                                   final boolean stepped,
                                                   final boolean elastic,
                                                   final List<String> says) {
        // The Stroom MCP server's tests/test_off_path.py: each is refused, and the message names the call that puts
        // it right, so an agent that reads it gets back on the path.
        inBuild();
        if (stepped) {
            w.steppedClean(P1);
        }
        if (elastic) {
            w.elastic("p1");
            w.pipeline(EV, true, null);
            w.translation("ev");
            w.stream(5, "ACME-EVENTS", "Events", "ev", 4L);
        } else {
            w.translation("p1");
            w.buildFeed("ACME");
            w.stream(5, "ACME", "Raw Events", null, null);
            w.idFilter("p1", 3, 5);
        }

        final ToolResult result = call(arguments);

        assertThat(result.isError()).isTrue();
        assertThat(result.text()).contains(says);
        // Never a template the cluster could be given unagreed.
        assertThat(result.text()).doesNotContain("\"dev_tools\"");
    }

    static Stream<Arguments> drops() {
        return Stream.of(
                Arguments.of("PUT _index_template/ecs-acme-v2\n{}", "DELETE ecs-acme-v2."),
                Arguments.of("PUT _index_template/ecs-acme-v2\n{\"data_stream\": {}}",
                        "DELETE _data_stream/ecs-acme-v2."));
    }

    static Stream<Arguments> agreements() {
        final Map<String, Object> otherIndex = ProcessingWorld.agreed("PUT _index_template/ecs-acme-v2\n{}");
        otherIndex.put("index", "ecs-acme-v1");
        final Map<String, Object> changed = ProcessingWorld.agreed("PUT _index_template/ecs-acme-v2\n{}");
        changed.put("xslt", "feedfacefeedface");
        return Stream.of(
                Arguments.of(null, "No Elasticsearch index template has been agreed with the user for index "
                                   + "'ecs-acme-v2'"),
                Arguments.of(otherIndex, "No Elasticsearch index template has been agreed"),
                Arguments.of(changed, "The indexing XSLT changed since index template 'ecs-acme-v2' was agreed"));
    }

    static Stream<Arguments> untied() {
        final Meta fromOther = Meta.builder().id(6).feedName("ACME-EVENTS").typeName("Events").pipelineUuid("other")
                .status(Status.UNLOCKED).createMs(ProcessingWorld.FEED_MADE).build();
        final Meta raw = Meta.builder().id(7).feedName("ACME").typeName("Raw Events").status(Status.UNLOCKED)
                .createMs(ProcessingWorld.FEED_MADE).build();
        return Stream.of(
                Arguments.of(List.of(), List.of(6), null, "needs source_pipeline_uuid"),
                Arguments.of(List.of(fromOther), List.of(6), "ev", "Stream(s) [6] were not produced by "
                                                                   + "'Acme-Events'"),
                Arguments.of(List.of(raw), List.of(6, 7), "ev", "Mixed stream types"));
    }

    static Stream<Arguments> wrongMoves() {
        return Stream.of(
                Arguments.of("process before a clean step", Map.of("stream_ids", List.of(6)), false, false,
                        List.of("no clean step", "step_sample")),
                Arguments.of("index before the template is agreed (VS Code)",
                        Map.of("stream_ids", List.of(5), "source_pipeline_uuid", "ev"), true, true,
                        List.of("No Elasticsearch index template has been agreed",
                                "propose_index_template pipeline_uuid=p1", "example_template=",
                                "template_committed_by_user=true")),
                Arguments.of("both a sample and a feed scope", Map.of("stream_ids", List.of(5), "feed", "ACME"),
                        true, false, List.of("Give either stream_ids")),
                Arguments.of("a whole feed with no start time", Map.of("feed", "ACME"), true, false,
                        List.of("needs created_after")),
                Arguments.of("process a stream again with a new filter", Map.of("stream_ids", List.of(5)), true,
                        false, List.of("already processed", "reprocess_streams")));
    }

    // p1, a translation pipeline, in the build, its feed ACME the build's, optionally stepped clean.
    private void translationInBuild(final boolean stepped) {
        inBuild();
        w.translation("p1");
        w.buildFeed("ACME");
        if (stepped) {
            w.steppedClean(P1);
        }
    }

    // p1 in the build, neither translation nor indexing (a StreamAppender with no stream type), stepped clean.
    private void otherInBuild() {
        inBuild();
        w.layers("p1", w.layer("p1", "xsltFilter", "XSLTFilter", "streamAppender", "StreamAppender"));
        w.steppedClean(P1);
    }

    // p1, an Elasticsearch indexing pipeline in the build with the agreed template (or none), reading Events
    // stream 6 from ev, an events pipeline of the build.
    private void elasticInBuild(final Map<String, Object> agreed) {
        w.pipeline(P1, true, agreed == null
                ? "Indexes Acme."
                : ProcessingWorld.withAgreed(agreed));
        w.elastic("p1");
        w.steppedClean(P1);
        w.pipeline(EV, true, null);
        w.translation("ev");
        w.stream(6, "ACME-EVENTS", "Events", "ev", 4L);
    }

    private void inBuild() {
        w.pipeline(P1, true, null);
    }

    private ToolResult call(final Map<String, Object> arguments) {
        final Map<String, Object> all = new LinkedHashMap<>(arguments);
        all.put("pipeline_uuid", "p1");
        return w.createTool().execute(PortedToolCalls.call(CreateProcessorFilterTool.NAME,
                JsonUtil.writeValueAsString(all)), ProcessingWorld.context());
    }

    // Calls as a user agreeing to every gate: the gates seen, and the final result.
    private Gated gatedThrough(final Map<String, Object> arguments) {
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

    static Map<String, Object> details(final Map<String, Object> gate) {
        return details(gate, "details");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> details(final Map<String, Object> result, final String key) {
        return (Map<String, Object>) result.get(key);
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> actions(final Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("user_actions");
    }


    // --------------------------------------------------------------------------------


    // The gates a call went through, and its result.
    record Gated(List<Map<String, Object>> gates, Map<String, Object> result) {

        List<String> statuses() {
            return gates.stream().map(gate -> String.valueOf(gate.get("status"))).toList();
        }
    }
}
