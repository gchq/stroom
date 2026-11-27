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

import stroom.ai.impl.tools.processing.MergedPipeline;
import stroom.ai.impl.tools.processing.MergedPipeline.TranslationDoc;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.processor.shared.ProcessorFilter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/// Every pipeline running an XSLT, found live: those naming it in their own layer and their children inheriting it
/// (confirmed by their effective properties), never a build's own; and the feeds their filters read, counted, for
/// the survey's breadth stated before it starts.
class TestXsltUsers {

    private static final String XSLT = "xslt-1";

    private final PipelineIndex pipelineIndex = Mockito.mock(PipelineIndex.class);
    private final PipelineShapes pipelineShapes = Mockito.mock(PipelineShapes.class);
    private final ProcessorFilters processorFilters = Mockito.mock(ProcessorFilters.class);
    private final FeedVolumes feedVolumes = Mockito.mock(FeedVolumes.class);
    private final WriteGuard guard = Mockito.mock(WriteGuard.class);
    private final XsltUsers users = new XsltUsers(pipelineIndex, pipelineShapes, () -> processorFilters,
            feedVolumes, guard);
    private final Map<String, PipelineIndex.Entry> index = new LinkedHashMap<>();
    private final Map<String, Set<String>> feeds = new LinkedHashMap<>();
    private final List<ProcessorFilter> filters = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Mockito.when(pipelineIndex.read()).thenReturn(index);
        Mockito.when(processorFilters.all()).thenReturn(filters);
        Mockito.when(processorFilters.feedsByPipeline(any())).thenReturn(feeds);
        Mockito.when(processorFilters.canManageProcessors()).thenReturn(true);
        Mockito.when(guard.buildOf(any())).thenReturn(Optional.empty());
        Mockito.when(feedVolumes.volume(Mockito.anyString(), Mockito.eq(XsltUsers.RAW_EVENTS))).thenAnswer(inv ->
                new FeedVolumes.Volume(inv.getArgument(0), XsltUsers.RAW_EVENTS, 10, 12L * 1024 * 1024, false,
                        null, null, false));
    }

    @Test
    void everyPipelineRunningItAndTheirChildrenAreFoundWithTheirFeeds() {
        pipeline("a", "ACME-A-EVENTS", null, true, "ACME-A");
        pipeline("b", "ACME-B-EVENTS", null, true, "ACME-B", "ACME-C");
        // A child inheriting it from a, and one setting another XSLT in its place.
        pipeline("a-child", "ACME-A-CHILD", "a", false, "ACME-D");
        pipeline("a-other", "ACME-A-OTHER", "a", false, "ACME-E");
        runs("a", "a-child", "b");
        pipeline("unrelated", "OTHER-EVENTS", null, false, "OTHER");

        final XsltUsers.Users found = users.of(XSLT);

        assertThat(found.users()).extracting(user -> user.pipeline().getName())
                .containsExactly("ACME-A-EVENTS", "ACME-B-EVENTS", "ACME-A-CHILD");
        assertThat(found.feeds()).containsExactly("ACME-A", "ACME-B", "ACME-C", "ACME-D");
        assertThat(found.streams()).isEqualTo(40);
        // 12 MiB a feed: three survey calls each (5 MB a call).
        assertThat(found.surveyCalls()).isEqualTo(12);
        assertThat(found.said()).startsWith("It runs in 3 pipelines ('ACME-A-EVENTS', 'ACME-B-EVENTS', "
                                            + "'ACME-A-CHILD'), whose filters read 4 feeds: ACME-A (Raw Events): "
                                            + "10 streams")
                .contains("takes about 12 survey calls (roughly 6 minutes)");
    }

    @Test
    void aBuildsOwnPipelineIsNeverOne() {
        pipeline("a", "ACME-A-EVENTS", null, true, "ACME-A");
        pipeline("copy", "ACME-A-EVENTS-V1.1", null, true, "ACME-A");
        runs("a", "copy");
        Mockito.when(guard.buildOf(new DocRef(PipelineDoc.TYPE, "copy"))).thenReturn(Optional.of("acme-v1-1"));

        assertThat(users.of(XSLT).users()).extracting(user -> user.pipeline().getUuid()).containsExactly("a");
    }

    @Test
    void aFeedWithNoStreamsNeedsNoSurveyCallAndNoneRunningItSaysSo() {
        pipeline("a", "ACME-A-EVENTS", null, true, "DORMANT");
        runs("a");
        Mockito.when(feedVolumes.volume("DORMANT", XsltUsers.RAW_EVENTS)).thenReturn(new FeedVolumes.Volume(
                "DORMANT", XsltUsers.RAW_EVENTS, 0, 0L, false, null, null, false));

        final XsltUsers.Users found = users.of(XSLT);

        assertThat(found.surveyCalls()).isZero();
        assertThat(found.said()).contains("DORMANT (Raw Events): no streams yet");
        assertThat(users.of("nothing").users()).isEmpty();
        assertThat(users.of("nothing").said()).contains("0 pipelines", "0 feeds: none");
    }

    @Test
    void theUsersChoiceNarrowsTheFeedsByNameInAnyCase() {
        pipeline("a", "ACME-A-EVENTS", null, true, "ACME-A", "ACME-AB");
        runs("a");
        final XsltUsers.Users found = users.of(XSLT);

        assertThat(found.narrowedTo(List.of("acme-a", "NOT-ONE"))).containsExactly("ACME-A");
        // Named in typed words: whole names only (ACME-A is no part of ACME-AB).
        assertThat(found.namedIn("Just acme-ab please")).containsExactly("ACME-AB");
        assertThat(found.namedIn("ACME-A and ACME-AB")).containsExactly("ACME-A", "ACME-AB");
        assertThat(found.namedIn("neither")).isEmpty();
        // A name ending the user's sentence (seen in eval case 107 on Haiku: "EVAL-107 and EVAL-107-B." kept one).
        assertThat(found.namedIn("Both feeds: acme-a and acme-ab.")).containsExactly("ACME-A", "ACME-AB");
        assertThat(found.namedIn("acme-ab.x")).isEmpty();
        assertThat(found.narrowedTo(null)).isEmpty();
    }

    // A pipeline: its own layer names the XSLT where `names` is true; its filters read the feeds given.
    private void pipeline(final String uuid,
                          final String name,
                          final String parent,
                          final boolean names,
                          final String... feedsRead) {
        final PipelineDataBuilder data = new PipelineDataBuilder();
        if (names) {
            data.addProperty(PipelineProperty.builder().element("translationFilter").name("xslt")
                    .value(new PipelinePropertyValue(new DocRef(XsltDoc.TYPE, XSLT))).build());
        } else if (uuid.endsWith("other")) {
            data.addProperty(PipelineProperty.builder().element("translationFilter").name("xslt")
                    .value(new PipelinePropertyValue(new DocRef(XsltDoc.TYPE, "another"))).build());
        }
        final PipelineData own = data.build();
        index.put(uuid, new PipelineIndex.Entry(uuid, name, "System/Feeds", parent, own));
        feeds.put(uuid, new LinkedHashSet<>(List.of(feedsRead)));
        filters.add(ProcessorFilter.builder().pipelineUuid(uuid).build());
        final MergedPipeline merged = Mockito.mock(MergedPipeline.class);
        Mockito.when(merged.translationDocs(uuid)).thenReturn(List.of());
        Mockito.when(pipelineShapes.merged(uuid)).thenReturn(merged);
    }

    // The pipelines that run the XSLT as Stroom runs them.
    private void runs(final String... uuids) {
        for (final String uuid : uuids) {
            final MergedPipeline merged = Mockito.mock(MergedPipeline.class);
            Mockito.when(merged.translationDocs(uuid)).thenReturn(List.of(new TranslationDoc("translationFilter",
                    "XSLTFilter", new DocRef(XsltDoc.TYPE, XSLT), uuid, false)));
            Mockito.when(pipelineShapes.merged(uuid)).thenReturn(merged);
        }
    }
}
