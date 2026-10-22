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

import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// The build's test feed (the Stroom MCP server's `tests/test_processing.py`, its cases for `output_feed` and
/// `restore_output_feed`).
class TestOutputFeeds {

    private static final DocRef P1 = ProcessingWorld.P1;

    private ProcessingWorld w;

    @BeforeEach
    void setUp() {
        w = new ProcessingWorld();
        // A pipeline inheriting a template whose StreamAppender is 'streamAppender'.
        w.pipeline(P1, true, null);
        PipelineLayers.given(w.world.pipelineService, "p1", PipelineLayers.of(
                PipelineLayers.layer("template").element("streamAppender", "StreamAppender"),
                PipelineLayers.layer("p1")));
        final DocRef source = w.feedAt("System/Feeds", "PROD-FEED", "pf", "Raw Events");
        w.world.docs.put("pf", w.feedDoc("pf").copy().encoding("UTF-16LE").build());
        assertThat(source.getName()).isEqualTo("PROD-FEED");
    }

    @Test
    void theTestFeedIsMadeOnceAndSetOnTheStreamAppender() {
        final OutputFeeds.OutputFeed made = w.outputFeeds.outputFeed(P1, List.of("PROD-FEED"), ProcessingWorld.BUILD)
                .orElseThrow();

        assertThat(made.name()).isEqualTo("PROD-FEED-MCP-TEST");
        assertThat(made.made()).isTrue();
        assertThat(made.worksFrom()).containsExactly("PROD-FEED");
        // The source feed's settings.
        assertThat(w.feedDoc(made.uuid()).getEncoding()).isEqualTo("UTF-16LE");
        assertThat(PipelineDocs.addedProperties(w.pipelineDoc("p1"))).containsExactly(new PipelineProperty(
                "streamAppender", "feed", new PipelinePropertyValue(new DocRef(FeedDoc.TYPE, made.uuid(),
                "PROD-FEED-MCP-TEST"))));
        // Kept in the build's record as an output feed: upload_sample refuses it (no user_samples).
        assertThat(w.world.records().entry(ProcessingWorld.BUILD, made.uuid()).get(BuildFeeds.OUTPUT_FOR))
                .isEqualTo(List.of("PROD-FEED"));

        // Once made, the same: nothing made again, and the property is already set.
        final OutputFeeds.OutputFeed again = w.outputFeeds.outputFeed(P1, List.of("PROD-FEED"),
                ProcessingWorld.BUILD).orElseThrow();
        assertThat(again.uuid()).isEqualTo(made.uuid());
        assertThat(again.made()).isFalse();
    }

    @Test
    void theTestFeedTakesAFreeName() {
        // Seen in Ask Stroom AI: an earlier build's PROD-FEED-MCP-TEST held the name, and every filter failed on
        // Stroom's 500 ("A feed named ... already exists"); the user's own name for it was refused.
        w.feedAt("System/Feeds", "PROD-FEED-MCP-TEST", "other-1", "Raw Events");
        w.feedAt("System/Feeds", "PROD-FEED-MCP-TEST-2", "other-2", "Raw Events");

        final OutputFeeds.OutputFeed made = w.outputFeeds.outputFeed(P1, List.of("PROD-FEED"), ProcessingWorld.BUILD)
                .orElseThrow();

        assertThat(made.name()).isEqualTo("PROD-FEED-MCP-TEST-3");
        assertThat(made.made()).isTrue();
    }

    @Test
    void theNewestTestFeedTheUserGaveIsWrittenTo() {
        // The user's, made by create_feed test_feed=true after the server's own: the newest is written to.
        final DocRef old = w.buildFeed("PROD-FEED-MCP-TEST");
        final DocRef mine = w.buildFeed("MINE");
        w.world.records().update(ProcessingWorld.BUILD, old, entry -> entry.put(BuildFeeds.OUTPUT_FOR,
                List.of("PROD-FEED")));
        w.world.records().update(ProcessingWorld.BUILD, mine, entry -> entry.put(BuildFeeds.OUTPUT_FOR,
                List.of("PROD-FEED")));
        final int made = w.world.created.size();

        final OutputFeeds.OutputFeed used = w.outputFeeds.outputFeed(P1, List.of("PROD-FEED"), ProcessingWorld.BUILD)
                .orElseThrow();

        assertThat(used.toMap()).isEqualTo(Map.of("name", "MINE", "uuid", mine.getUuid(), "made", false,
                "works_from", List.of("PROD-FEED")));
        assertThat(w.world.created).hasSize(made);
        assertThat(PipelineDocs.addedProperties(w.pipelineDoc("p1")).getFirst().getValue().getEntity().getUuid())
                .isEqualTo(mine.getUuid());
    }

    @Test
    void promotionTakesTheTestFeedOffTheStreamAppender() {
        final PipelineProperty feed = new PipelineProperty("streamAppender", "feed",
                new PipelinePropertyValue(new DocRef(FeedDoc.TYPE, "tf")));
        final PipelineProperty xslt = new PipelineProperty("translationFilter", "xslt",
                new PipelinePropertyValue(new DocRef("XSLT", "x")));
        final PipelineDoc doc = PipelineDocs.withAddedProperties(w.pipelineDoc("p1"), List.of(feed, xslt));
        w.world.docs.put("p1", doc);

        assertThat(w.outputFeeds.restoreOutputFeed("p1", Set.of("tf"))).isTrue();
        assertThat(PipelineDocs.addedProperties(w.pipelineDoc("p1"))).containsExactly(xslt);
        // A feed property naming a feed outside the build (set by the user) is left alone.
        w.world.docs.put("p1", doc);
        assertThat(w.outputFeeds.restoreOutputFeed("p1", Set.of("other"))).isFalse();
        assertThat(PipelineDocs.addedProperties(w.pipelineDoc("p1"))).containsExactly(feed, xslt);
    }

    @Test
    void aPipelineWithNoStreamAppenderWritesToNoFeed() {
        w.layers("p1", w.layer("p1", "kafka", "StandardKafkaProducer"));

        assertThat(w.outputFeeds.outputFeed(P1, List.of("PROD-FEED"), ProcessingWorld.BUILD)).isEmpty();
    }
}
