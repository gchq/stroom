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
import stroom.ai.impl.tools.streams.StreamReader;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.PipelineDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// What a check covered of each feed of a build of several ([FeedCoverage]): decided by the user (2026-11-25),
/// coverage per feed, so a feed missing from it is seen.
class TestFeedCoverage {

    @Test
    void eachFeedsStreamsAndRecordsAreCountedAndAFeedWithNoneIsMissing() {
        final Map<Long, String> sources = new LinkedHashMap<>();
        sources.put(10L, "acme-a");
        sources.put(11L, "ACME-A");
        sources.put(12L, null);

        final Map<String, Object> coverage = FeedCoverage.coverage(List.of("ACME-A", "ACME-B"), sources,
                Map.of(10L, 5L, 11L, 3L));

        assertThat(coverage.get("by_feed")).isEqualTo(Map.of(
                "ACME-A", Map.of("streams", 2L, "records", 8L),
                "ACME-B", Map.of("streams", 0L, "records", 0L),
                "(not known)", Map.of("streams", 1L, "records", 0L)));
        assertThat(coverage.get("missing")).isEqualTo(List.of("ACME-B"));
        assertThat(String.valueOf(coverage.get("said"))).startsWith("ACME-A: 2 streams, 8 records; ACME-B: none")
                .contains("Not covered: ACME-B (no stream of it among those checked");
        // Without record counts, streams only; every feed covered says so.
        final Map<String, Object> streamsOnly = FeedCoverage.coverage(List.of("A", "B"), Map.of(1L, "A", 2L, "B"),
                Map.of());
        assertThat(streamsOnly.get("by_feed")).isEqualTo(Map.of("A", Map.of("streams", 1L), "B",
                Map.of("streams", 1L)));
        assertThat(streamsOnly.get("said")).isEqualTo("A: 1 stream; B: 1 stream.");
    }

    @Test
    void anEventsStreamIsOfItsRawParentsFeedAndOneFeedGetsNone() {
        final StreamReader reader = Mockito.mock(StreamReader.class);
        Mockito.when(reader.meta(20L)).thenReturn(Meta.builder().id(20L).feedName("ACME-A-MCP-TEST")
                .parentDataId(5L).pipelineUuid("ev").build());
        Mockito.when(reader.meta(5L)).thenReturn(Meta.builder().id(5L).feedName("ACME-B").build());
        final BuildWorld world = new BuildWorld();
        world.inBuild("acme", PipelineDoc.TYPE, "ACME-Events", "ev");
        final FakeBuildFacts facts = new FakeBuildFacts();
        facts.sources = List.of("ACME-A", "ACME-B");
        final BuildPlan plan = new BuildPlan(facts, world.guard(), new Consent(() -> Mockito.mock(AiAuditLog.class)),
                new BuildMemory());
        final FeedCoverage coverage = new FeedCoverage(reader, world.guard(), plan);

        final Map<String, Object> found = coverage.ofStreams(List.of(20L), Map.of(20L, 4L)).orElseThrow();

        assertThat(found.get("missing")).isEqualTo(List.of("ACME-A"));
        assertThat(((Map<?, ?>) found.get("by_feed")).get("ACME-B")).isEqualTo(Map.of("streams", 1L, "records",
                4L));
        facts.sources = List.of("ACME-A");
        assertThat(coverage.ofStreams(List.of(20L), Map.of())).isEmpty();
        assertThat(coverage.ofStreams(List.of(), Map.of())).isEmpty();
    }
}
