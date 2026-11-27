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

import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.docref.DocRef;
import stroom.docstore.api.DocDependencyService;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.index.shared.IndexResource;
import stroom.index.shared.LuceneIndexDoc;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/// The tidy-up at promotion: which of a build's documents are its test documents, which of them are proven its own
/// to tidy up, and the order it is carried out in (streams, shards, then documents), each failure kept to its item.
class TestPromotionTidyUp {

    private static final String BUILD = "bb";

    private final BuildWorld world = new BuildWorld();
    private final ProcessorFilters processorFilters = Mockito.mock(ProcessorFilters.class);
    private final MetaService metaService = Mockito.mock(MetaService.class);
    private final IndexResource indexResource = Mockito.mock(IndexResource.class);
    private final DocDependencyService dependencies = Mockito.mock(DocDependencyService.class);
    private final PromotionTidyUp tidyUp = new PromotionTidyUp(world.guard(), world.records(), world.documentAccess,
            () -> processorFilters, () -> metaService, () -> indexResource, () -> dependencies);
    private final List<Map<String, Object>> docs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Mockito.when(processorFilters.canManageProcessors()).thenReturn(true);
    }

    @Test
    void theTestDocumentsAreTheNamedOrRecordedTestFeedsTheTestIndexesAndTheirDashboards() {
        inBuild(FeedDoc.TYPE, "ACME-MCP-TEST", "named");
        final DocRef recorded = inBuild(FeedDoc.TYPE, "ACME-OUT", "recorded");
        world.records().update(BUILD, recorded, entry -> entry.put(BuildFeeds.OUTPUT_FOR, List.of("ACME")));
        // A source type group's own output feed is promoted with the build: not a test feed.
        final DocRef group = inBuild(FeedDoc.TYPE, "ACME-GROUP-EVENTS", "group");
        world.records().update(BUILD, group, entry -> {
            entry.put(BuildFeeds.OUTPUT_FOR, List.of("ACME"));
            entry.put(BuildFeeds.GROUP_OUTPUT, "Group");
        });
        inBuild(FeedDoc.TYPE, "ACME", "feed");
        inBuild(LuceneIndexDoc.TYPE, "ACME-INDEX-MCP-TEST", "ti");
        inBuild(LuceneIndexDoc.TYPE, "ACME-INDEX", "own-index");
        dashboard("on-test", "ACME-INDEX-MCP-TEST-VERIFY", "ti");
        dashboard("on-own", "ACME-INDEX-VERIFY", "own-index");
        dashboard("on-both", "BOTH-VERIFY", "ti", "own-index");
        dashboard("not-verify", "ACME overview", "ti");

        assertThat(tidyUp.testDocs(BUILD, docs, Map.of("ti", "prod-ix")))
                .containsExactlyInAnyOrder("named", "recorded", "ti", "on-test");
        // No test index: no dashboard is a test document.
        assertThat(tidyUp.testDocs(BUILD, docs, Map.of())).containsExactlyInAnyOrder("named", "recorded");
    }

    @Test
    void anIndexTheAgentMadeToTestWithThatNothingPromotedUsesIsATestIndexWithItsDashboard() {
        // Seen in an eval (case 61): the agent's own ACME-INDEX-TEST, and the verification dashboard on it, were
        // promoted beside the new version's pipeline.
        inBuild(LuceneIndexDoc.TYPE, "ACME-INDEX-TEST", "own-test");
        inBuild(LuceneIndexDoc.TYPE, "ACME-INDEX", "own-index");
        dashboard("on-own-test", "ACME-INDEX-TEST-VERIFY", "own-test");
        dashboard("on-own", "ACME-INDEX-VERIFY", "own-index");

        assertThat(tidyUp.testDocs(BUILD, docs, Map.of(), Set.of("own-test", "on-own-test")))
                .containsExactlyInAnyOrder("own-test", "on-own-test");
        // Unused alone doesn't make an index a test index: PromotionUses leaves out only one its name marks so.
        assertThat(tidyUp.testDocs(BUILD, docs, Map.of(), Set.of())).isEmpty();
    }

    @Test
    void aSampleFilterWhoseStreamsAreAllDeletedReadsNothing() {
        // Seen in an eval (case 61): an earlier build's sample filter, left on its promoted indexing pipeline, picked
        // a stream of that build's test feed, deleted by its tidy-up; the later build's test feed, of the same name,
        // was then said to be read by the production pipeline, and never tidied up.
        Mockito.when(metaService.getMeta(6L, true)).thenReturn(Meta.builder().id(6L).status(Status.DELETED).build());
        Mockito.when(metaService.getMeta(7L, true)).thenReturn(Meta.builder().id(7L).status(Status.UNLOCKED).build());
        final ExpressionTerm pipeline = ProcessorFilterWrites.pipelineTerm(new DocRef(PipelineDoc.TYPE, "ev"));

        assertThat(tidyUp.readsLiveStreams(filter(ProcessorFilterWrites.term("Id", 6), pipeline))).isFalse();
        // A stream gone altogether: read by nothing.
        assertThat(tidyUp.readsLiveStreams(filter(ProcessorFilterWrites.term("Id", 8)))).isFalse();
        // One of its streams still there: it reads that.
        assertThat(tidyUp.readsLiveStreams(filter(ExpressionOperator.builder().op(Op.OR)
                .addTerm(ProcessorFilterWrites.term("Id", 6))
                .addTerm(ProcessorFilterWrites.term("Id", 7))
                .build()))).isTrue();
        // A feed named, or no stream picked: what arrives in the feed is read.
        assertThat(tidyUp.readsLiveStreams(filter(ProcessorFilterWrites.term("Id", 6),
                ProcessorFilterWrites.term("Feed", "ACME-MCP-TEST")))).isTrue();
        assertThat(tidyUp.readsLiveStreams(filter(ProcessorFilterWrites.term("Type", "Events"), pipeline)))
                .isTrue();
    }

    @Test
    void onlyWhatTheBuildProvablyMadeAndNothingOutsideItUsesIsTidied() {
        final DocRef tidied = inBuild(FeedDoc.TYPE, "A-MCP-TEST", "a");
        // Not tagged as the tools': made by hand in the workspace.
        world.add(FeedDoc.TYPE, "B-MCP-TEST", world.folder("System/" + BuildWorld.WORKSPACE + "/" + BUILD).uuid, "b");
        docs.add(FakeBuildFacts.doc(FeedDoc.TYPE, "b", "B-MCP-TEST"));
        // Outside the workspace.
        world.add(FeedDoc.TYPE, "C-MCP-TEST", world.folder("System/Feeds").uuid, "c");
        docs.add(FakeBuildFacts.doc(FeedDoc.TYPE, "c", "C-MCP-TEST"));
        // A feed the build works from.
        inBuild(FeedDoc.TYPE, "D-MCP-TEST", "d");
        // Named by a pipeline outside the build.
        inBuild(FeedDoc.TYPE, "E-MCP-TEST", "e");
        world.add(PipelineDoc.TYPE, "PROD", world.folder("System/Feeds").uuid, "prod");
        Mockito.when(dependencies.fetchDependencies(any(), any())).thenAnswer(inv -> {
            final String asked = inv.getArgument(0, DependencyCriteria.class).getPartialName();
            final Dependency prod = new Dependency(new DocRef(PipelineDoc.TYPE, "prod", "PROD"),
                    new DocRef(FeedDoc.TYPE, "e", "E-MCP-TEST"), true);
            return asked.endsWith(":=e")
                    ? new ResultPage<>(List.of(prod))
                    : new ResultPage<>(List.of());
        });
        // Its streams read by the build's own pipeline: still the build's.
        world.add(PipelineDoc.TYPE, "OWN", world.folder("System/" + BuildWorld.WORKSPACE + "/" + BUILD).uuid, "own",
                WriteGuard.MANAGED);
        Mockito.when(processorFilters.pipelinesReading(eq("A-MCP-TEST"), any())).thenReturn(List.of(Map.of("uuid",
                "own", "name", "OWN")));

        final PromotionTidyUp.Plan plan = tidyUp.plan(BUILD, docs, kept(), Set.of("a", "b", "c", "d", "e"),
                List.of("D-MCP-TEST"));

        assertThat(plan.items()).extracting(PromotionTidyUp.Item::doc).containsExactly(tidied);
        assertThat(plan.passedOver()).containsExactly(
                "test feed 'B-MCP-TEST': the tools didn't make it",
                "test feed 'C-MCP-TEST': it isn't in the build's workspace",
                "test feed 'D-MCP-TEST': it is a feed the build works from",
                "test feed 'E-MCP-TEST': used outside the build, by Pipeline 'PROD'");
        // Not kept by the promotion (promoted): never tidied.
        assertThat(tidyUp.plan(BUILD, docs, List.of(), Set.of("a"), List.of()).items()).isEmpty();
    }

    @Test
    void aTestIndexADocumentThePromotionMovesSearchesIsSaidUpFrontAndKept() {
        // Found in review (2026-11-18): a dashboard of the build not named -VERIFY, searching the test index, is
        // promoted; the test index was tidied up from under it, as only what was outside the build counted.
        final DocRef index = inBuild(LuceneIndexDoc.TYPE, "IX-MCP-TEST", "ti");
        world.records().update(BUILD, index, entry -> entry.put(BuildTestIndexes.TEST_INDEX_FOR, "prod-ix"));
        dashboard("overview", "ACME overview", "ti");
        dependsOn("ti", new DocRef(DashboardDoc.TYPE, "overview", "ACME overview"));
        final List<PromotionLayout.Step> steps = docs.stream()
                .map(doc -> new PromotionLayout.Step(doc, "overview".equals(doc.get("uuid"))
                        ? PromotionLayout.MOVE
                        : PromotionLayout.KEEP, "left", null))
                .toList();

        final PromotionTidyUp.Plan plan = tidyUp.plan(BUILD, docs, steps, tidyUp.testDocs(BUILD, docs,
                Map.of("ti", "prod-ix")), List.of());

        assertThat(plan.items()).isEmpty();
        assertThat(plan.passedOver()).containsExactly("test index 'IX-MCP-TEST': used outside the build, by "
                                                      + "Dashboard 'ACME overview' (the promotion moves it to "
                                                      + "production)");
    }

    @Test
    void aTestFeedAPromotedDocumentNamesIsCheckedAgainOnceItIsPromotedAndKept() {
        // Found in review (2026-11-18): a promoted pipeline naming the test feed in a property the promotion
        // doesn't rewrite. Before the promotion it is the build's (a pipeline's names may yet be rewritten): once
        // promoted, it is outside the build, so the test feed and its streams are kept.
        inBuild(FeedDoc.TYPE, "A-MCP-TEST", "a");
        final DocRef pipeline = world.inBuild(BUILD, PipelineDoc.TYPE, "ACME-Events", "pp");
        docs.add(FakeBuildFacts.doc(PipelineDoc.TYPE, "pp", "ACME-Events"));
        dependsOn("a", pipeline);
        final List<PromotionLayout.Step> steps = docs.stream()
                .map(doc -> new PromotionLayout.Step(doc, "pp".equals(doc.get("uuid"))
                        ? PromotionLayout.MOVE
                        : PromotionLayout.KEEP, "left", null))
                .toList();
        final PromotionTidyUp.Plan plan = tidyUp.plan(BUILD, docs, steps, Set.of("a"), List.of());
        assertThat(plan.items()).extracting(PromotionTidyUp.Item::doc).extracting(DocRef::getUuid)
                .containsExactly("a");
        // Promoted.
        world.move("pp", world.folder("System/Feeds/Acme").uuid);

        final List<Map<String, Object>> done = tidyUp.apply(BUILD, plan);

        assertThat(done).containsExactly(Map.of("document", "Feed 'A-MCP-TEST'", "deleted", false, "skipped",
                "left in the workspace: used outside the build since it was promoted, by Pipeline 'ACME-Events'"));
        Mockito.verify(metaService, Mockito.never()).updateStatus(any(FindMetaCriteria.class), any(), any());
        assertThat(world.nodes).containsKey("a");
    }

    @Test
    void aPromotedPipelinesOwnProcessorFilterOnTheTestFeedDoesNotKeepIt() {
        // Its filter, made in the build, read the sample's streams in the test feed for the build only.
        inBuild(FeedDoc.TYPE, "A-MCP-TEST", "a");
        world.inBuild(BUILD, PipelineDoc.TYPE, "ACME-Indexing", "pi");
        docs.add(FakeBuildFacts.doc(PipelineDoc.TYPE, "pi", "ACME-Indexing"));
        Mockito.when(processorFilters.pipelinesReading(eq("A-MCP-TEST"), any())).thenReturn(List.of(Map.of("uuid",
                "pi", "name", "ACME-Indexing")));
        Mockito.when(metaService.updateStatus(any(FindMetaCriteria.class), Mockito.<Status>isNull(),
                Mockito.eq(Status.DELETED))).thenReturn(2);
        final List<PromotionLayout.Step> steps = docs.stream()
                .map(doc -> new PromotionLayout.Step(doc, "pi".equals(doc.get("uuid"))
                        ? PromotionLayout.MOVE
                        : PromotionLayout.KEEP, "left", null))
                .toList();
        final PromotionTidyUp.Plan plan = tidyUp.plan(BUILD, docs, steps, Set.of("a"), List.of());
        world.move("pi", world.folder("System/Feeds/Acme").uuid);

        final List<Map<String, Object>> done = tidyUp.apply(BUILD, plan);

        assertThat(done).containsExactly(Map.of("document", "Feed 'A-MCP-TEST'", "streams_marked_deleted", 2L,
                "deleted", true));
    }

    @Test
    void whatReadsATestFeedUncheckedLeavesItAlone() {
        // Fail closed: without Manage Processors the filters reading it can't be read.
        inBuild(FeedDoc.TYPE, "A-MCP-TEST", "a");
        Mockito.when(processorFilters.canManageProcessors()).thenReturn(false);

        final PromotionTidyUp.Plan plan = tidyUp.plan(BUILD, docs, kept(), Set.of("a"), List.of());

        assertThat(plan.items()).isEmpty();
        assertThat(plan.passedOver()).containsExactly("test feed 'A-MCP-TEST': what uses it couldn't be checked (the "
                                                      + "processor filters reading it need Manage Processors)");
    }

    @Test
    void aShardFailureKeepsTheIndexAndItsDashboardWhileTheRestIsTidied() {
        inBuild(FeedDoc.TYPE, "A-MCP-TEST", "a");
        final DocRef index = inBuild(LuceneIndexDoc.TYPE, "IX-MCP-TEST", "ti");
        world.records().update(BUILD, index, entry -> entry.put(BuildTestIndexes.TEST_INDEX_FOR, "prod-ix"));
        dashboard("vd", "IX-MCP-TEST-VERIFY", "ti");
        // An Elastic Index doc recorded as a test index: the doc only, never the index on the cluster.
        final DocRef elastic = inBuild(ElasticIndexDoc.TYPE, "ES-MCP-TEST", "es");
        world.records().update(BUILD, elastic, entry -> entry.put(BuildTestIndexes.TEST_INDEX_FOR, "prod-es"));
        Mockito.when(metaService.updateStatus(any(FindMetaCriteria.class), Mockito.<Status>isNull(),
                Mockito.eq(Status.DELETED))).thenReturn(4);
        Mockito.when(indexResource.reset("ti")).thenThrow(new RuntimeException("You do not have permission"));
        final PromotionTidyUp.Plan plan = tidyUp.plan(BUILD, docs, kept(), tidyUp.testDocs(BUILD, docs,
                Map.of("ti", "prod-ix", "es", "prod-es")), List.of());

        final List<Map<String, Object>> done = tidyUp.apply(BUILD, plan);

        assertThat(done).containsExactly(
                Map.of("document", "Feed 'A-MCP-TEST'", "streams_marked_deleted", 4L, "deleted", true),
                Map.of("document", "Index 'IX-MCP-TEST'", "deleted", false, "failed", "deleting its shards: You do "
                       + "not have permission (its later steps were skipped; it is left in the workspace)"),
                Map.of("document", "ElasticIndex 'ES-MCP-TEST'", "deleted", true),
                Map.of("document", "Dashboard 'IX-MCP-TEST-VERIFY'", "deleted", false, "skipped", "left in the "
                       + "workspace: the test index it searches wasn't tidied up"));
        assertThat(world.nodes).containsKeys("ti", "vd").doesNotContainKeys("a", "es");
        Mockito.verify(indexResource, Mockito.never()).reset("es");
        // What the record kept of what was deleted goes with it; of what stays, it stays.
        assertThat(world.records().entry(BUILD, "es")).isEmpty();
        assertThat(world.records().entry(BUILD, "ti")).containsKey(BuildTestIndexes.TEST_INDEX_FOR);
    }

    @Test
    void aDocumentStroomDoesntDeleteIsSaidAsAFailure() {
        final DocRef feed = inBuild(FeedDoc.TYPE, "A-MCP-TEST", "a");
        final PromotionTidyUp.Plan plan = tidyUp.plan(BUILD, docs, kept(), Set.of("a"), List.of());
        Mockito.doReturn(null).when(world.explorerService).delete(any());

        final List<Map<String, Object>> done = tidyUp.apply(BUILD, plan);

        assertThat(done).singleElement().satisfies(item -> assertThat(String.valueOf(item.get("failed")))
                .startsWith("deleting it: Stroom didn't delete it"));
        assertThat(world.guard().buildOf(feed)).contains(BUILD);
    }

    @Test
    void theReviewSaysEachItemAndTheArgumentTurnsItOff() {
        final PromotionTidyUp.Item feed = new PromotionTidyUp.Item(PromotionTidyUp.Kind.FEED,
                new DocRef(FeedDoc.TYPE, "a", "A-MCP-TEST"), 3, null, 0, Set.of());

        assertThat(feed.said()).isEqualTo("test feed 'A-MCP-TEST': its 3 stream(s) (size not known) marked deleted, "
                                          + "as Stroom's Data screen deletes them (data retention purges them "
                                          + "later), then the feed deleted");
        assertThat(PromotionTidyUp.wanted(null)).isTrue();
        assertThat(PromotionTidyUp.wanted(true)).isTrue();
        assertThat(PromotionTidyUp.wanted("true")).isTrue();
        assertThat(PromotionTidyUp.wanted(false)).isFalse();
        assertThat(PromotionTidyUp.wanted("false")).isFalse();
        assertThat(PromotionTidyUp.Plan.OFF.on()).isFalse();
        assertThat(PromotionTidyUp.Plan.OFF.offeredIn(Consent.Request.approval("promote_build", "Promote",
                Map.of())).options()).isEmpty();
    }

    // The document named depends on the one given (only).
    private void dependsOn(final String uuid, final DocRef from) {
        Mockito.when(dependencies.fetchDependencies(any(), any())).thenAnswer(inv -> {
            final String asked = inv.getArgument(0, DependencyCriteria.class).getPartialName();
            return asked.endsWith(":=" + uuid)
                    ? new ResultPage<>(List.of(new Dependency(from, new DocRef("Any", uuid), true)))
                    : new ResultPage<>(List.of());
        });
    }

    // Everything listed, left in the workspace by the promotion.
    private List<PromotionLayout.Step> kept() {
        return docs.stream()
                .map(doc -> new PromotionLayout.Step(doc, PromotionLayout.KEEP, "left", null))
                .toList();
    }

    private DocRef inBuild(final String type, final String name, final String uuid) {
        docs.add(FakeBuildFacts.doc(type, uuid, name));
        return world.inBuild(BUILD, type, name, uuid);
    }

    // A dashboard in the build, its queries searching the indexes given.
    private static ProcessorFilter filter(final ExpressionItem... items) {
        return ProcessorFilter.builder().id(1).pipelineUuid("pi")
                .queryData(QueryData.builder().expression(ProcessorFilterWrites.allOf(List.of(items))).build())
                .build();
    }

    private void dashboard(final String uuid, final String name, final String... indexes) {
        inBuild(DashboardDoc.TYPE, name, uuid);
        final List<ComponentConfig> components = new ArrayList<>();
        for (final String index : indexes) {
            components.add(ComponentConfig.builder().type("query").id("query-" + index).name("Query")
                    .settings(QueryComponentSettings.builder()
                            .dataSource(new DocRef(LuceneIndexDoc.TYPE, index)).build())
                    .build());
        }
        world.docs.put(uuid, DashboardDoc.builder().uuid(uuid).name(name).version("v0")
                .dashboardConfig(DashboardConfig.builder().components(components).build())
                .build());
    }
}
