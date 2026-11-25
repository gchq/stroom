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
import stroom.ai.impl.tools.ToolResult;
import stroom.pipeline.shared.PipelineDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// build_status: what it says beside the plan's steps.
class TestBuildStatusTool {

    @Test
    void aBuildOfSeveralFeedsGivesEachFeedsStreamsAndDates() {
        // Decided by the user (2026-11-25): a dormant feed is included like the others, its volume stated.
        final FakeBuildFacts facts = new FakeBuildFacts();
        facts.sources = List.of("ACME-A", "ACME-OLD");
        final BuildWorld world = new BuildWorld();
        world.guard().buildFolder("acme");
        final BuildPlan plan = new BuildPlan(facts, world.guard(),
                new Consent(() -> Mockito.mock(AiAuditLog.class)), new BuildMemory());
        final FeedOverviews overviews = Mockito.mock(FeedOverviews.class);
        final List<Map<String, Object>> each = List.of(Map.of("feed", "ACME-A", "dormant", false),
                Map.of("feed", "ACME-OLD", "dormant", true));
        Mockito.when(overviews.overviews(List.of("ACME-A", "ACME-OLD"))).thenReturn(each);
        final BuildStatusTool tool = new BuildStatusTool(plan, new PendingDocumentation());
        tool.setOverviews(() -> overviews);

        final Map<String, Object> status = BuildToolCalls.json(tool.execute(BuildToolCalls.call(
                BuildStatusTool.NAME, "{\"build\": \"acme\"}"), BuildToolCalls.context()).text());

        assertThat(status.get(BuildStatusTool.SOURCE_FEEDS)).isEqualTo(each);
        // One feed: nothing more than before.
        facts.sources = List.of("ACME-A");
        assertThat(BuildToolCalls.json(tool.execute(BuildToolCalls.call(BuildStatusTool.NAME,
                "{\"build\": \"acme\"}"), BuildToolCalls.context()).text()))
                .doesNotContainKey(BuildStatusTool.SOURCE_FEEDS);
    }

    @Test
    void aBuildThatIsntThereIsRefusedNamingTheUsersBuilds() {
        // Chat 195: build_status for a name the workspace has no build of gave an empty plan, "step 1 of 16, create
        // the feed", as if a new build.
        final BuildWorld world = new BuildWorld();
        world.guard().buildFolder("endpoint-logs-v1.0");
        final BuildPlan plan = new BuildPlan(new FakeBuildFacts(), world.guard(),
                new Consent(() -> Mockito.mock(AiAuditLog.class)), new BuildMemory());
        final BuildStatusTool tool = new BuildStatusTool(plan, new PendingDocumentation());

        final ToolResult refused = tool.execute(BuildToolCalls.call(BuildStatusTool.NAME,
                "{\"build\": \"endpoint-logs\"}"), BuildToolCalls.context());

        assertThat(refused.isError()).isTrue();
        assertThat(refused.text()).contains("No build 'endpoint-logs' in the workspace")
                .contains("Your builds: endpoint-logs-v1.0.")
                .doesNotContain("create_feed");
        // A build that is there, with nothing in it yet (just started), has its plan.
        assertThat(tool.execute(BuildToolCalls.call(BuildStatusTool.NAME, "{\"build\": \"endpoint-logs-v1.0\"}"),
                BuildToolCalls.context()).isError()).isFalse();
        // With no builds at all, it says so.
        final BuildStatusTool none = new BuildStatusTool(new BuildPlan(new FakeBuildFacts(),
                new BuildWorld().guard(), new Consent(() -> Mockito.mock(AiAuditLog.class)), new BuildMemory()),
                new PendingDocumentation());
        assertThat(none.execute(BuildToolCalls.call(BuildStatusTool.NAME, "{\"build\": \"x1\"}"),
                BuildToolCalls.context()).text()).contains("You have no builds");
    }

    @Test
    void aNewEventsVersionsFollowOnPipelinesArePointedAtReviewCoverageBeforePromotion() {
        // Decided by the user (2026-11-17): promotion gives the pipelines reading the predecessor's Events filters
        // reading the new version's, so they are reviewed against its Events first (it can't process production
        // streams: changed_pipeline_uuid steps it).
        final ProcessorFilters filters = Mockito.mock(ProcessorFilters.class);
        Mockito.when(filters.followOnPipelines("ev1", Set.of())).thenReturn(List.of(
                Map.of("uuid", "cef", "name", "ACME-CEF", "filters", List.of(5)),
                Map.of("uuid", "ix2", "name", "ACME-V1.1-Indexing", "filters", List.of(9))));
        final List<Map<String, Object>> docs = List.of(
                Map.of("type", PipelineDoc.TYPE, "uuid", "ev2", "name", "ACME-V1.1-Events", "role", "Events",
                        "version_of", "ev1"),
                Map.of("type", PipelineDoc.TYPE, "uuid", "ix2", "name", "ACME-V1.1-Indexing", "role", "Indexing"));

        final List<String> said = BuildStatusTool.followOnReview(docs, filters);

        // The build's own indexing pipeline isn't one: it reads the new version already.
        assertThat(said).containsExactly("Promoted pipelines read the Events of the version 'ACME-V1.1-Events' "
                                         + "replaces: 'ACME-CEF'. Before promoting, review_coverage "
                                         + "pipeline_uuid=ev1 changed_pipeline_uuid=ev2 checks them against the new "
                                         + "version's Events. Promotion gives each a new filter, disabled, reading "
                                         + "the new version's Events; their own filters are left for the admin.");
        // An indexing pipeline's new version has no follow-ons to point at.
        assertThat(BuildStatusTool.followOnReview(List.of(Map.of("type", PipelineDoc.TYPE, "uuid", "ix3", "role",
                "Indexing", "version_of", "ix1")), filters)).isEmpty();
    }
}
