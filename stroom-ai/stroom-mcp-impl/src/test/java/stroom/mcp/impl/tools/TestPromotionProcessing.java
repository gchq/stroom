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
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.ProcessorFilter;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.search.elastic.shared.ElasticIndexDoc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    void aNewEventsVersionsFollowOnPipelinesGainADisabledFilterReadingItsEventsTheirOwnLeftAsTheyAre() {
        // Decided by the user (2026-11-17): a CEF flattener (or an indexing pipeline) reading the predecessor's
        // Events gains a new filter, disabled, of the same selection reading the new version's; its own filter is
        // the admin's to disable or remove, never changed here.
        final DocRef version = new DocRef(PipelineDoc.TYPE, "ev2", "Acme-V1.1-Events");
        final DocRef cef = new DocRef(PipelineDoc.TYPE, "cef", "Acme-CEF");
        w.pipeline(cef, false, null);
        final ExpressionOperator reading = ProcessorFilterWrites.allOf(List.of(
                ProcessorFilterWrites.term("Type", "Events"), PIPELINE_TERM));
        final ProcessorFilter own = w.filter("cef", 5, true, reading, null);
        // A sample's filter (by stream id) and one reading another pipeline's Events: not followed.
        w.filter("cef", 6, true, ProcessorFilterWrites.allOf(List.of(ProcessorFilterWrites.term("Id", 8),
                PIPELINE_TERM)), null);
        w.filter("other", 7, true, ProcessorFilterWrites.allOf(List.of(ProcessorFilterWrites.term("Type",
                "Events"), ProcessorFilterWrites.pipelineTerm(SP))), null);
        final ProcessorFilters filters = Mockito.mock(ProcessorFilters.class);
        Mockito.when(filters.all()).thenAnswer(inv -> w.filters.values().stream().flatMap(List::stream).toList());
        w.promotion.setProcessorFilters(() -> filters);

        final List<PromotionProcessing.FollowOn> found = w.promotion.followOns(Map.of(version, ProcessingWorld.EV),
                pipeline -> true);

        assertThat(found).extracting(followOn -> followOn.pipeline().getUuid() + ":" + followOn.reading().getId())
                .containsExactly("cef:5");
        assertThat(found.getFirst().said()).contains("'Acme-CEF': a new filter, disabled", "'Acme-V1.1-Events'",
                "filter 5 is left as it is");
        // A pipeline in a build is passed over: its build gives it its own filters.
        assertThat(w.promotion.followOns(Map.of(version, ProcessingWorld.EV), pipeline -> false)).isEmpty();

        w.nextFilterId = 30;
        final List<Map<String, Object>> made = w.promotion.createFollowOnFilters(StroomLinks.CHAT, found,
                List.of(Map.of("filter_id", 29, "pipeline", "Acme-V1.1-Events", "enabled", false)));

        assertThat(w.created).hasSize(1);
        assertThat(w.created.getFirst().isEnabled()).isFalse();
        assertThat(w.lastExpression().getChildren()).containsExactly(ProcessorFilterWrites.term("Type", "Events"),
                ProcessorFilterWrites.pipelineTerm(version));
        assertThat(made.getFirst()).containsEntry("filter_id", 30).containsEntry("copied_from", 5)
                .containsEntry("enabled", false).containsEntry("reads", "Acme-V1.1-Events")
                .containsEntry("in_place_of", "Acme-Events");
        assertThat(String.valueOf(made.getFirst().get("user_actions"))).contains("enable-30", "after=[enable-29]");
        // Its own filter as it was: enabled, reading the predecessor's Events.
        assertThat(w.filters.get("cef").getFirst()).isSameAs(own);
        assertThat(own.isEnabled()).isTrue();
        assertThat(ProcessorFilterWrites.expressionOf(own)).isEqualTo(reading);
        // Promoted again: not made twice.
        assertThat(w.promotion.createFollowOnFilters(StroomLinks.CHAT, found, List.of()).getFirst())
                .containsEntry("existing", true).containsEntry("filter_id", 30);
        assertThat(w.created).hasSize(1);
    }

    @Test
    void aFollowOnIndexingTheNewEventsIntoAnIndexAPromotedPipelineIndexesThemIntoIsPassedOver() {
        // Seen in an eval (case 61): the build's indexing pipeline, made afresh rather than as a new version of the
        // production one, was promoted reading the new version's Events, and the production indexing pipeline
        // gained a follow-on filter reading them too: both indexed into the same index, each event twice.
        final DocRef version = new DocRef(PipelineDoc.TYPE, "ev2", "Acme-V1.1-Events");
        final DocRef production = new DocRef(PipelineDoc.TYPE, "pi", "Acme - Indexing");
        final DocRef otherIndexing = new DocRef(PipelineDoc.TYPE, "oi", "Acme-Other - Indexing");
        final DocRef cef = new DocRef(PipelineDoc.TYPE, "cef", "Acme-CEF");
        final DocRef built = new DocRef(PipelineDoc.TYPE, "ip2", "Acme - Indexing V1.1");
        final DocRef index = new DocRef("Index", "idx", "ACME-INDEX");
        final DocRef testIndex = new DocRef("Index", "tidx", "ACME-INDEX-MCP-TEST");
        w.pipeline(production, false, null);
        w.pipeline(otherIndexing, false, null);
        w.pipeline(cef, false, null);
        w.pipeline(built, true, null);
        w.lucene("pi", index);
        w.lucene("oi", new DocRef("Index", "idx-other", "ACME-OTHER"));
        w.layers("cef", w.layer("cef", "xsltFilter", "XSLTFilter"));
        // The build's pipeline writes to its test index while in the build: the production index it stands in for.
        w.lucene("ip2", testIndex);
        final ExpressionOperator reading = ProcessorFilterWrites.allOf(List.of(
                ProcessorFilterWrites.term("Type", "Events"), PIPELINE_TERM));
        final List<PromotionProcessing.FollowOn> followOns = List.of(
                new PromotionProcessing.FollowOn(production, w.filter("pi", 5, true, reading, null),
                        ProcessingWorld.EV, version),
                new PromotionProcessing.FollowOn(otherIndexing, w.filter("oi", 6, true, reading, null),
                        ProcessingWorld.EV, version),
                new PromotionProcessing.FollowOn(cef, w.filter("cef", 7, true, reading, null), ProcessingWorld.EV,
                        version));
        final List<PromotionProcessing.Entry> processing = List.of(new PromotionProcessing.Entry(built, null,
                "Events", List.of(ProcessorFilterWrites.pipelineTerm(version))));

        final PromotionProcessing.Followed followed = w.promotion.withoutDoubleIndexing(followOns, processing,
                Map.of("tidx", "idx"));

        assertThat(followed.followOns()).extracting(followOn -> followOn.pipeline().getUuid())
                .containsExactly("oi", "cef");
        assertThat(followed.passedOver()).singleElement().satisfies(said -> assertThat(said).contains(
                "'Acme - Indexing': no new filter", "'Acme - Indexing V1.1' already indexes the Events of "
                                                   + "'Acme-V1.1-Events' into the same index",
                "filter 5 is left as it is"));

        // Without the build's pipeline reading the new Events, every follow-on is made.
        assertThat(w.promotion.withoutDoubleIndexing(followOns, List.of(), Map.of("tidx", "idx")).followOns())
                .hasSize(3);
        // Two follow-ons into the same index from the same new Events: only the first is made.
        w.lucene("oi", index);
        assertThat(w.promotion.withoutDoubleIndexing(followOns, List.of(), Map.of()).followOns())
                .extracting(followOn -> followOn.pipeline().getUuid())
                .containsExactly("pi", "cef");
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
    void anIndexReadingAWorkingCopysEventsReadsItsOriginalsOncePromoted() {
        // An aggregated feed's group pipeline changed through a working copy: the copy is deleted at promotion and
        // the original goes on writing the group's Events, which its index alone reads.
        w.pipeline(IP, true, null);
        w.layers("ip", w.layer("ip", "xsltFilter", "XSLTFilter"));
        final DocRef copy = new DocRef("Pipeline", "wc", "Acme-Events");
        w.filter("ip", 4, true, ExpressionOperator.builder().op(Op.AND)
                .addOperator(ExpressionOperator.builder().op(Op.OR).addTerm(ProcessorFilterWrites.term("Id", 8))
                        .build())
                .addTerm(ProcessorFilterWrites.pipelineTerm(copy))
                .build(), null);
        w.stream(8, "ACME-MCP-TEST", "Events", "wc", null);

        final List<PromotionProcessing.Entry> plan = w.promotion.promotionProcessing(List.of(IP), List.of("ACME"),
                List.of("ACME-MCP-TEST"), Map.of("wc", "tp"));

        assertThat(plan).singleElement().satisfies(entry -> {
            assertThat(entry.feed()).isNull();
            assertThat(entry.streamType()).isEqualTo("Events");
            assertThat(ProcessorFilterWrites.pipelineUuids(entry.extraTerms())).containsExactly("tp");
        });
        // Without a working copy, the term is kept as it was.
        assertThat(PromotionProcessing.toOriginals(List.of(PIPELINE_TERM), Map.of("wc", "tp")))
                .containsExactly(PIPELINE_TERM);
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
    void aProductionLuceneIndexIsNeverPutUpForEmptyingOnlyAnIndexTheBuildMadeIs() {
        // Seen in an eval (case 61, a source type joining its promoted group as V1.1): the handover asked the user
        // to delete the shards of the group's production index, its V1.0 history with them, before enabling the new
        // version's indexing filter.
        final DocRef version = new DocRef(PipelineDoc.TYPE, "ev2", "Acme-V1.1-Events");
        w.pipeline(IP, true, null);
        w.lucene("ip", new DocRef("Index", "idx", "ACME-INDEX"));
        final PromotionProcessing.Entry entry = new PromotionProcessing.Entry(IP, null, "Events",
                List.of(ProcessorFilterWrites.pipelineTerm(version)));

        final List<Map<String, Object>> production = w.promotion.createPromotionFilters(StroomLinks.CHAT,
                List.of(entry), Set.of("ip"));

        assertThat(String.valueOf(production.getFirst().get("before_enabling")))
                .contains("'ACME-INDEX' is not emptied: it is a production index",
                        "indexes the Events of 'Acme-V1.1-Events' into it beside what it holds",
                        "none of those Events are in it yet", "the user decides")
                .doesNotContain("delete the shards");
        assertThat(TestCreateProcessorFilterTool.actions(production.getFirst())).singleElement()
                .satisfies(action -> assertThat(action.get("action")).isEqualTo("enable_processor_filter"));

        // The build's own index, promoted with it, holding the build's sample: emptied first, as before.
        final List<Map<String, Object>> own = w.promotion.createPromotionFilters(StroomLinks.CHAT, List.of(entry),
                Set.of("ip", "idx"));

        assertThat(String.valueOf(own.getFirst().get("before_enabling"))).contains("delete the shards of index "
                                                                                   + "'ACME-INDEX' first");
        assertThat(TestCreateProcessorFilterTool.actions(own.getFirst())).extracting(action -> action.get("action"))
                .containsExactly("open_document", "enable_processor_filter");
    }

    @Test
    void aNewVersionsOwnIndexIsDroppedOnlyWhileNothingElseClaimsIt() {
        // Its own index (the free name the build made, holding only the sample), named by the promoted pipeline and
        // the Elastic Index doc the build made for it: dropped first, as before.
        promotedElastic();
        w.world.add(ElasticIndexDoc.TYPE, "Acme-V2", w.world.folder("System/Feeds/Acme").uuid, "es-v2");
        w.world.docs.put("es-v2", ElasticIndexDoc.builder().uuid("es-v2").name("Acme-V2").version("v0")
                .indexName("ecs-acme-v2").build());
        final PromotionProcessing.Entry entry = new PromotionProcessing.Entry(IP, null, "Events",
                List.of(PIPELINE_TERM));

        final List<Map<String, Object>> own = w.promotion.createPromotionFilters(StroomLinks.CHAT, List.of(entry),
                Set.of("ip", "es-v2"));

        assertThat(String.valueOf(own.getFirst().get("before_enabling"))).contains("drops 'ecs-acme-v2' first");

        // Another pipeline outside the build writes to it too: production's, and not dropped.
        w.productionPipeline(new DocRef("Pipeline", "other", "Other-Indexing"), PipelineLayers.layer("other")
                .property("elasticIndexingFilter", "indexName", "ecs-acme-v2"));

        final List<Map<String, Object>> claimed = w.promotion.createPromotionFilters(StroomLinks.CHAT,
                List.of(entry), Set.of("ip", "es-v2"));

        assertThat(String.valueOf(claimed.getFirst().get("before_enabling")))
                .contains("'ecs-acme-v2' is not dropped: it is a production index")
                .contains("pipeline 'Other-Indexing' writes to 'ecs-acme-v2'")
                .doesNotContain("DELETE");
        assertThat(TestCreateProcessorFilterTool.actions(claimed.getFirst())).singleElement()
                .satisfies(action -> assertThat(action.get("action")).isEqualTo("enable_processor_filter"));
    }

    // IP, a promoted Elasticsearch indexing pipeline writing to ecs-acme-v2, with an agreed data stream template.
    private void promotedElastic() {
        w.listsEveryPipelineAndIndexDoc();
        w.pipeline(IP, false, ProcessingWorld.withAgreed(ProcessingWorld.agreed(
                "PUT _index_template/ecs-acme-v2\n{\"data_stream\": {}}")));
        w.elastic("ip");
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
