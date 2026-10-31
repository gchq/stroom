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

import stroom.ai.impl.tools.StroomLinks;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// What promoted pipelines go on processing, and the filters promotion creates.
class TestPromotionProcessing {

    private static final DocRef TP = new DocRef("Pipeline", "tp", "Acme-Events");
    private static final DocRef IP = new DocRef("Pipeline", "ip", "Acme - Indexing");
    private static final DocRef SP = new DocRef("Pipeline", "sp", "Old-Events");
    private static final ExpressionTerm PIPELINE_TERM = ProcessorFilterWrites.pipelineTerm(ProcessingWorld.EV);

    private ProcessingWorld w;

    @BeforeEach
    void setUp() {
        w = new ProcessingWorld();
    }

    @Test
    void promotionPlansNewDataFiltersFromSampleFiltersAndSurveys() {
        for (final DocRef pipeline : List.of(TP, IP, SP)) {
            w.pipeline(pipeline, true, null);
            w.layers(pipeline.getUuid(), w.layer(pipeline.getUuid(), "xsltFilter", "XSLTFilter"));
        }
        w.idFilter("tp", 3, 6, 7);
        w.filter("ip", 4, true, ExpressionOperator.builder().op(Op.AND)
                .addOperator(ExpressionOperator.builder().op(Op.OR).addTerm(ProcessorFilterWrites.term("Id", 8))
                        .build())
                .addTerm(PIPELINE_TERM)
                .build(), null);
        w.stream(6, "ACME", "Raw Events", null, null);
        w.stream(7, "ACME-MCP-TEST", "Raw Events", null, null);
        w.stream(8, "ACME", "Events", "ev", null);

        final List<PromotionProcessing.Entry> plan = w.promotion.promotionProcessing(List.of(TP, IP, SP),
                List.of("OLD-FEED"), List.of());

        assertThat(plan).extracting(entry -> entry.pipeline().getName() + "|" + entry.feed() + "|"
                                             + entry.streamType() + "|" + !entry.extraTerms().isEmpty())
                .containsExactly(
                        // The test feed is left out.
                        "Acme-Events|ACME|Raw Events|false",
                        // Its Pipeline condition, no feed.
                        "Acme - Indexing|null|Events|true",
                        // Stepping-only build: the surveyed feed.
                        "Old-Events|OLD-FEED|Raw Events|false");
        // A build on an existing feed: its test feed, whatever it is called, gives way to the feed it works from
        // (seen in VS Code: the promoted filter selected the test feed).
        assertThat(w.promotion.promotionProcessing(List.of(TP), List.of("ACME-PROD"), List.of("ACME")))
                .extracting(entry -> entry.feed() + "|" + entry.streamType())
                .containsExactly("ACME-PROD|Raw Events");

        w.nextFilterId = 21;
        final List<Map<String, Object>> made = w.promotion.createPromotionFilters(StroomLinks.CHAT,
                plan.subList(1, 2));

        // No minimum creation time (asked for by the user): enabled, it processes the feed's existing data too.
        assertThat(w.created.getLast().isEnabled()).isFalse();
        assertThat(w.created.getLast().getMinMetaCreateTimeMs()).isNull();
        assertThat(w.created.getLast().getMaxProcessingTasks()).isEqualTo(2);
        final List<ExpressionItem> children = w.lastExpression().getChildren();
        assertThat(children).extracting(item -> ((ExpressionTerm) item).getField())
                .containsExactly("Type", "Pipeline");
        assertThat(children.get(1)).isEqualTo(PIPELINE_TERM);
        // What it processed in the build too (asked for by the user: no NOT over those streams); the index starts
        // fresh.
        assertThat(made.getFirst()).doesNotContainKey("except_processed_in_build");
        assertThat(String.valueOf(made.getFirst().get("pipeline_link"))).endsWith("docUuid=ip");
    }

    @Test
    void aPromotedIndexingPipelinesIndexStartsFreshBeforeItsFilterIsEnabled() {
        // Asked for by the user: no NOT over the streams the build indexed; the index is dropped first instead.
        w.pipeline(IP, true, ProcessingWorld.withAgreed(ProcessingWorld.agreed(
                "PUT _index_template/ecs-acme-v2\n{\"data_stream\": {}}")));
        w.elastic("ip");
        w.nextFilterId = 23;

        final List<Map<String, Object>> made = w.promotion.createPromotionFilters(StroomLinks.CHAT, List.of(
                new PromotionProcessing.Entry(IP, null, "Events", List.of(PIPELINE_TERM))));

        assertThat(String.valueOf(made.getFirst().get("before_enabling")))
                .contains("drops 'ecs-acme-v2' first")
                .contains("DELETE _data_stream/ecs-acme-v2.");
        // As buttons (asked for by the user): the drop, for the cluster admin to copy, then the enable, after it.
        final List<Map<String, Object>> actions = TestCreateProcessorFilterTool.actions(made.getFirst());
        assertThat(actions).hasSize(2);
        final Map<String, Object> drop = new LinkedHashMap<>();
        drop.put("id", "drop-ecs-acme-v2");
        drop.put("action", "elasticsearch_request");
        drop.put("by", "cluster admin");
        drop.put("request", "DELETE _data_stream/ecs-acme-v2");
        drop.put("summary", "Drop 'ecs-acme-v2' so it starts fresh");
        assertThat(actions.getFirst()).isEqualTo(drop);
        assertThat(actions.getLast().get("action")).isEqualTo("enable_processor_filter");
        assertThat(actions.getLast().get("filter_id")).isEqualTo(23);
        assertThat(actions.getLast().get("after")).isEqualTo(List.of("drop-ecs-acme-v2"));
        assertThat(w.created.getLast().isEnabled()).isFalse();
    }

    @Test
    void aFeedIsNamedByReferenceSoARenamedFeedIsStillSelected() {
        // Asked for by the user: Feed IS_DOC_REF, not EQUALS a name, so Stroom keeps the filter as a dependency of
        // the feed.
        w.feedAt("System/Feeds", "ACME", "feed-uuid", "Raw Events");
        w.pipeline(TP, true, null);
        w.translation("tp");

        w.promotion.createPromotionFilters(StroomLinks.CHAT, List.of(
                new PromotionProcessing.Entry(TP, "ACME", "Raw Events", List.of())));

        final ExpressionTerm feed = (ExpressionTerm) w.lastExpression().getChildren().getFirst();
        assertThat(feed).isEqualTo(ExpressionTerm.builder().field("Feed").condition(Condition.IS_DOC_REF)
                .docRef(new DocRef("Feed", "feed-uuid", "ACME")).build());
        // Read back: shown by name, and matched against a stream's feed.
        final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND).addTerm(feed).build();
        assertThat(PipelineFeeds.terms(expression)).containsExactly("Feed IS_DOC_REF ACME");
        assertThat(StreamProcessing.selects(expression, Meta.builder().feedName("acme").build())).isTrue();
        assertThat(StreamProcessing.selects(expression, Meta.builder().feedName("OTHER").build())).isFalse();
    }

    @Test
    void streamsProcessedIntoATestFeedAreProcessedAgainOncePromoted() {
        // Their Events stayed in the build's test feed, in the workspace: production needs its own.
        w.pipeline(TP, true, null);
        w.translation("tp");
        w.idFilter("tp", 9, 8);
        w.stream(8, "ACME", "Raw Events", null, null);
        w.output(8, 80, "Events", "tp", 9);

        final List<PromotionProcessing.Entry> plan = w.promotion.promotionProcessing(List.of(TP), List.of("ACME"),
                List.of("ACME-MCP-TEST"));

        assertThat(plan).extracting(PromotionProcessing.Entry::feed).containsExactly("ACME");
    }

    @Test
    void promotionMakesNoSecondFilterWhereAnEnabledOneSelectsWhatArrives() {
        // Asked for by the user: an enabled filter already selecting the feed's new streams gets no twin; a build's
        // sample filter, by stream id, selects nothing new, so a filter is made beside it.
        w.feedAt("System/Feeds", "ACME", "acme", "Raw Events");
        w.pipeline(TP, true, null);
        w.translation("tp");
        w.pipeline(IP, true, null);
        w.layers("ip", w.layer("ip", "xsltFilter", "XSLTFilter"));
        w.filter("tp", 5, true, ExpressionOperator.builder().op(Op.AND)
                .addTerm(ProcessorFilterWrites.term("Feed", "ACME"))
                .addTerm(ProcessorFilterWrites.term("Type", "Raw Events"))
                .build(), null);
        w.filter("ip", 6, true, ExpressionOperator.builder().op(Op.AND)
                .addOperator(ExpressionOperator.builder().op(Op.OR).addTerm(ProcessorFilterWrites.term("Id", 8))
                        .build())
                .addTerm(PIPELINE_TERM)
                .build(), null);
        w.nextFilterId = 30;

        final List<Map<String, Object>> made = w.promotion.createPromotionFilters(StroomLinks.CHAT, List.of(
                new PromotionProcessing.Entry(TP, "ACME", "Raw Events", List.of()),
                new PromotionProcessing.Entry(IP, null, "Events", List.of(PIPELINE_TERM))));

        assertThat(made.getFirst().get("existing")).isEqualTo(true);
        assertThat(made.getFirst().get("filter_id")).isEqualTo(5);
        assertThat(String.valueOf(made.getFirst().get("note"))).contains("no new filter");
        assertThat(made.get(1).get("filter_id")).isEqualTo(30);
        assertThat(made.get(1)).doesNotContainKey("existing");
        assertThat(w.created).hasSize(1);
    }

    @Test
    void anIndexReadingAPipelinePromotedWithItIsEnabledAfterThatPipelinesFilter() {
        // Seen in e2e: both enabled together, the index took the build's Events and then their re-translation too.
        w.feedAt("System/Feeds", "ACME", "acme", "Raw Events");
        w.pipeline(ProcessingWorld.EV, true, null);
        w.translation("ev");
        w.pipeline(IP, true, ProcessingWorld.withAgreed(ProcessingWorld.agreed("PUT _index_template/ecs-acme-v2\n{}")));
        w.elastic("ip");
        w.nextFilterId = 31;

        final List<Map<String, Object>> made = w.promotion.createPromotionFilters(StroomLinks.CHAT, List.of(
                new PromotionProcessing.Entry(ProcessingWorld.EV, "ACME", "Raw Events", List.of()),
                new PromotionProcessing.Entry(IP, null, "Events", List.of(PIPELINE_TERM))));

        assertThat(String.valueOf(made.get(1).get("before_enabling")))
                .contains("only once filter 31 (pipeline 'Acme-Events') has processed what the build processed "
                          + "again");
        assertThat(made.getFirst()).doesNotContainKey("before_enabling");
        assertThat(TestCreateProcessorFilterTool.actions(made.getFirst()))
                .extracting(action -> action.get("id"))
                .containsExactly("enable-31");
        assertThat(TestCreateProcessorFilterTool.actions(made.get(1)).getLast().get("after")).isEqualTo(List.of(
                "drop-ecs-acme-v2", "enable-31", "filter 31 has processed what the build processed again"));
    }
}
