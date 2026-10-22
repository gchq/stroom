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

import stroom.ai.impl.tools.AgentInstructions;
import stroom.ai.impl.tools.ToolResult;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/// start_build (the Stroom MCP server's `tools/builds.py` `start_build`, and its test in
/// `tests/test_existing_data_plan.py`).
class TestStartBuildTool {

    private final FeedWorld feeds = new FeedWorld();
    private final AgentInstructions agentInstructions = Mockito.mock(AgentInstructions.class);
    private final StartBuildTool tool = new StartBuildTool(feeds.world.guard(), feeds.world.records(),
            feeds.feedNames, agentInstructions, feeds.plan);

    @Test
    void startBuildRecordsTheExistingFeedsItWorksFrom() {
        Mockito.when(agentInstructions.applicableInstructions(any(), anyString(), any()))
                .thenReturn(Map.of("instructions", List.of()));
        feeds.feedAt("System/Feeds/ACME", "ACME", "prod");
        feeds.buildFeed("bb", "ACME-COPY");

        final Map<String, Object> started = json(tool.execute(PortedToolCalls.call(StartBuildTool.NAME,
                "{\"build\": \"bb\", \"feeds\": [\"ACME\", \"ACME-COPY\", \"NEW\"]}"), PortedToolCalls.context()));

        // Only a feed outside the build counts: the build's own (a test feed) and one not made yet don't.
        assertThat(started).containsEntry("works_from", List.of("ACME"))
                .containsEntry("folder", "System/MCP Workspace/bb")
                .containsEntry("standing_instructions", Map.of("instructions", List.of()))
                .containsEntry("task_list", BuildPlan.TASK_LIST)
                .containsEntry("done", false)
                .containsKeys("uuid", "plan");
        assertThat(feeds.world.records().load("bb").get(BuildRecords.SOURCE_FEEDS)).isEqualTo(List.of("ACME"));
        // One not here is said: the user may mean another cluster.
        assertThat(String.valueOf(started.get("not_found"))).startsWith("No feed named 'NEW' in this Stroom.")
                .endsWith(" (A feed this build is to create is fine: it doesn't exist yet.)");
    }

    @Test
    void aBuildWithNoFeedsIsJustItsFolder() {
        final Map<String, Object> started = json(tool.execute(PortedToolCalls.call(StartBuildTool.NAME,
                "{\"build\": \"acme-door-v1.3\"}"), PortedToolCalls.context()));

        assertThat(started).containsEntry("build", "acme-door-v1.3").doesNotContainKeys("works_from", "not_found");
        assertThat(feeds.world.guard().buildFolder("acme-door-v1.3", false)).isPresent();
        assertThat(tool.execute(PortedToolCalls.call(StartBuildTool.NAME, "{\"build\": \"a b\"}"),
                PortedToolCalls.context()).text()).startsWith("Build names are 2 to 64 letters");
    }

    @Test
    void itMakesTheBuildsFolderWithoutAskingFirst() {
        assertThat(tool.isReadOnly()).isFalse();
        assertThat(tool.confirmsItself()).isFalse();
        assertThat(tool.definition().description()).startsWith("Onboarding start: ");
    }

    private static Map<String, Object> json(final ToolResult result) {
        assertThat(result.isError()).as(result.text()).isFalse();
        return PortedToolCalls.json(result.text());
    }
}
