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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.streams.StreamReader;
import stroom.docref.DocRef;
import stroom.index.shared.LuceneIndexDoc;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.ExpressionTerm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// One index per feed ([FeedIndexes]): decided by the user (2026-11-25), where a build's feeds' readers differ each
/// feed's events go into an index of its own, by an indexing pipeline of its own processing only that feed's Events,
/// in the build and once promoted.
class TestFeedIndexes {

    private static final String BUILD = "acme";

    private final BuildWorld world = new BuildWorld();
    private final FakeBuildFacts facts = new FakeBuildFacts();
    private final IndexingRuns indexingRuns = Mockito.mock(IndexingRuns.class);
    private final IndexTemplateAgreements agreements = Mockito.mock(IndexTemplateAgreements.class);
    private final StreamReader streamReader = Mockito.mock(StreamReader.class);
    private final BuildPlan plan = new BuildPlan(facts, world.guard(),
            new Consent(() -> Mockito.mock(AiAuditLog.class)), new BuildMemory());
    private final FeedIndexes indexes = new FeedIndexes(plan, world.records(), indexingRuns, agreements,
            streamReader);

    @BeforeEach
    void setUp() {
        world.folder("System/" + BuildWorld.WORKSPACE + "/" + BUILD);
        facts.sources = List.of("ACME-A", "ACME-B");
        Mockito.when(indexingRuns.luceneIndex(Mockito.anyString())).thenReturn(Optional.empty());
        Mockito.when(indexingRuns.luceneIndex("pa")).thenReturn(Optional.of(new DocRef(LuceneIndexDoc.TYPE, "ia",
                "ACME-A-Index")));
        Mockito.when(agreements.elasticDestination(Mockito.anyString())).thenReturn(Optional.empty());
        Mockito.when(agreements.elasticDestination("pb")).thenReturn(Optional.of(Map.of("index name",
                "acme-b-events")));
        stream(1L, "ACME-A-MCP-TEST", 11L, "ACME-A");
        stream(2L, "ACME-A-MCP-TEST", 12L, "ACME-B");
    }

    @Test
    void aSharedIndexIsAsBefore() {
        assertThat(indexes.perFeed(BUILD)).isEmpty();
        assertThat(indexes.feedOf(BUILD, "pa")).isEmpty();
        assertThat(indexes.feedOfIndex(BUILD, "Shared-Index")).isEmpty();
        indexes.refuseOtherFeeds(BUILD, "pa", List.of(1L, 2L));
        world.records().rememberFeedComparison(BUILD, Map.of("index", BuildRecords.INDEX_SHARED));
        assertThat(indexes.perFeed(BUILD)).isEmpty();
    }

    @Test
    void eachIndexingPipelineIsForTheFeedItsIndexIsNamedFor() {
        world.records().rememberFeedComparison(BUILD, Map.of("index", BuildRecords.INDEX_PER_FEED));

        assertThat(indexes.perFeed(BUILD)).containsExactly("ACME-A", "ACME-B");
        assertThat(indexes.feedOf(BUILD, "pa")).contains("ACME-A");
        assertThat(indexes.feedOf(BUILD, "pb")).contains("ACME-B");
        assertThat(indexes.feedOf(BUILD, "other")).isEmpty();
        assertThat(indexes.feedOfIndex(BUILD, "ACME-B-Index acme-b-events")).contains("ACME-B");
        assertThatThrownBy(() -> indexes.feedOfIndex(BUILD, "Acme-Index"))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("'Acme-Index' names none of ACME-A, ACME-B")
                .hasMessageContaining("one indexing pipeline for each feed's index");
    }

    @Test
    void anIndexingPipelineOfOneFeedIsRefusedAnotherFeedsEvents() {
        world.records().rememberFeedComparison(BUILD, Map.of("index", BuildRecords.INDEX_PER_FEED));

        indexes.refuseOtherFeeds(BUILD, "pa", List.of(1L));
        assertThatThrownBy(() -> indexes.refuseOtherFeeds(BUILD, "pa", List.of(1L, 2L)))
                .hasMessageContaining("writes ACME-A's index")
                .hasMessageContaining("stream(s) 2 (ACME-B) are of another feed")
                .hasMessageContaining("Give only ACME-A's Events streams ([1])");
    }

    @Test
    void eachIndexingPipelinesPromotedFilterSelectsItsOwnFeed() {
        world.records().rememberFeedComparison(BUILD, Map.of("index", BuildRecords.INDEX_PER_FEED));
        final ExpressionTerm source = ProcessorFilterWrites.pipelineTerm(new DocRef(PipelineDoc.TYPE, "ev",
                "ACME-Events"));
        final List<PromotionProcessing.Entry> planned = List.of(
                new PromotionProcessing.Entry(new DocRef(PipelineDoc.TYPE, "pa"), List.of(), "Events",
                        List.of(source)),
                new PromotionProcessing.Entry(new DocRef(PipelineDoc.TYPE, "ev"), List.of("ACME-A", "ACME-B"),
                        "Raw Events", List.of()));

        final List<PromotionProcessing.Entry> narrowed = indexes.narrowed(BUILD, planned);

        assertThat(narrowed.getFirst().feeds()).containsExactly("ACME-A");
        assertThat(narrowed.getFirst().extraTerms()).containsExactly(source);
        assertThat(narrowed.get(1)).isEqualTo(planned.get(1));
        world.records().rememberFeedComparison(BUILD, Map.of("index", BuildRecords.INDEX_SHARED));
        assertThat(indexes.narrowed(BUILD, planned)).isEqualTo(planned);
    }

    private void stream(final long id, final String feed, final long parent, final String parentFeed) {
        Mockito.when(streamReader.meta(id)).thenReturn(Meta.builder().id(id).feedName(feed).parentDataId(parent)
                .build());
        Mockito.when(streamReader.meta(parent)).thenReturn(Meta.builder().id(parent).feedName(parentFeed).build());
    }
}
