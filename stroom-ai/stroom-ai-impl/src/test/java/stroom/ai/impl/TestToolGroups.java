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

package stroom.ai.impl;

import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.openai.shared.McpToolServerConfig;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestToolGroups {

    @Test
    void fewToolsAreNotGrouped() {
        final McpToolset toolset = toolset(null, tool("pipeline_create"), tool("pipeline_step"));

        final ToolGroups groups = ToolGroups.of(toolset.tools(), toolset, 5);

        assertThat(groups.isGrouped()).isFalse();
        assertThat(names(groups.offered(Set.of(), group -> {
        }))).containsExactly("pipeline_create", "pipeline_step");
    }

    @Test
    void toolsAreGroupedByTheirServersGroupThenSharedNamePrefixThenServer() {
        final McpToolset toolset = toolset(null,
                tool("pipeline_create"),
                tool("pipeline_step"),
                tool("index_create"),
                tool("index_status"),
                tool("onboard", Map.of("group", "Onboarding")),
                tool("check_feed", Map.of("group", "Onboarding")),
                tool("whoami"));

        final ToolGroups groups = ToolGroups.of(toolset.tools(), toolset, 2);

        assertThat(groups.groupNames()).containsExactly("pipeline", "index", "onboarding", "fastmcp");
        assertThat(names(groups.offered(Set.of(), group -> {
        }))).containsExactly("activate_pipeline", "activate_index", "activate_onboarding", "activate_fastmcp");
    }

    @Test
    void callingAnActivationToolTurnsTheGroupOn() {
        final McpToolset toolset = toolset(null,
                tool("pipeline_create"), tool("pipeline_step"), tool("index_create"), tool("index_status"));
        final ToolGroups groups = ToolGroups.of(toolset.tools(), toolset, 2);
        final Set<String> active = new HashSet<>();

        final Map<ToolSpecification, ToolExecutor> before = groups.offered(active, active::add);
        final ToolSpecification activate = before.keySet().stream()
                .filter(specification -> specification.name().equals("activate_pipeline"))
                .findFirst()
                .orElseThrow();
        assertThat(activate.description()).contains("pipeline_create", "pipeline_step");

        final String result = before.get(activate).execute(ToolExecutionRequest.builder()
                .id("1")
                .name("activate_pipeline")
                .arguments("{}")
                .build(), null);

        assertThat(result).contains("now available").contains("pipeline_create");
        assertThat(active).containsExactly("pipeline");
        assertThat(names(groups.offered(active, active::add)))
                .containsExactly("pipeline_create", "pipeline_step", "activate_index");
        assertThat(ToolGroups.isActivationTool("activate_index")).isTrue();
        assertThat(ToolGroups.isActivationTool("index_create")).isFalse();
    }

    @Test
    void activationsAreKeptPerChat() {
        final ToolGroups.Activations activations = new ToolGroups.Activations(() -> Mockito.mock(AiDao.class));

        activations.activate(1, "pipeline");

        assertThat(activations.active(1)).containsExactly("pipeline");
        assertThat(activations.active(2)).isEmpty();
    }

    @Test
    void groupsTheLatestQuestionsUsedAreOnAsARunStartsAndOlderOnesAreOff() {
        // Seen: groups turned on once stayed on for the rest of the chat, so a long chat was sent the definitions
        // of every group it had ever used.
        final AiDao aiDao = Mockito.mock(AiDao.class);
        Mockito.when(aiDao.getMessages(1)).thenReturn(List.of(
                message(1, 10, AiMessageType.USER_MESSAGE, "first"),
                message(2, 10, AiMessageType.TOOL_CALL, "{\"callId\":\"a\",\"tool\":\"activate_indexing\"}"),
                message(3, 20, AiMessageType.USER_MESSAGE, "second"),
                message(4, 20, AiMessageType.TOOL_CALL, "{\"callId\":\"b\",\"tool\":\"activate_onboarding\"}"),
                message(5, 20, AiMessageType.TOOL_RESULT, "{\"tool\":\"activate_explore\"}"),
                message(6, 30, AiMessageType.USER_MESSAGE, "third"),
                // A tool of a group that stayed on keeps it on.
                message(7, 30, AiMessageType.TOOL_CALL, "{\"callId\":\"c\",\"tool\":\"build_status\"}"),
                message(8, 30, AiMessageType.TOOL_CALL, "not json")));
        final ToolGroups.Activations activations = new ToolGroups.Activations(() -> aiDao);

        activations.startRun(1, tool -> "build_status".equals(tool)
                ? java.util.Optional.of("build")
                : java.util.Optional.empty(), Set.of("dashboards"));

        // The first question's group is off: only the latest two questions count.
        assertThat(activations.active(1)).containsExactlyInAnyOrder("onboarding", "build", "dashboards");
        activations.activate(1, "explore");
        assertThat(activations.active(1)).containsExactlyInAnyOrder("onboarding", "build", "dashboards", "explore");
        // Worked out afresh as the next run starts.
        activations.startRun(1, tool -> java.util.Optional.empty(), Set.of());
        assertThat(activations.active(1)).containsExactly("onboarding");
    }

    @Test
    void builtInToolsAreGroupedWithWhatEachGroupIsFor() {
        final Map<ToolSpecification, ToolExecutor> builtIns = builtIns("read_document", "ls", "start_onboarding",
                "save_xslt", "step_pipeline", "config_properties");

        final ToolGroups groups = ToolGroups.of(builtIns, Map.of(), McpToolset.EMPTY, 2);

        assertThat(groups.groupNames()).containsExactly("build", "processing", "administration");
        final Map<ToolSpecification, ToolExecutor> offered = groups.offered(Set.of("processing"), group -> {
        });
        assertThat(names(offered)).containsExactly("read_document", "ls", "activate_build", "step_pipeline",
                "activate_administration");
        assertThat(offered.keySet().stream()
                .filter(specification -> specification.name().equals("activate_build"))
                .findFirst()
                .orElseThrow()
                .description())
                .contains("to onboard a feed")
                .contains("start_onboarding, save_xslt");
        assertThat(groups.groupOf("save_xslt")).contains("build");
        assertThat(groups.groupOf("ls")).isEmpty();
    }

    @Test
    void fewBuiltInToolsAreNotGrouped() {
        final ToolGroups groups = ToolGroups.of(builtIns("ls", "start_onboarding"), Map.of(), McpToolset.EMPTY,
                5);

        assertThat(groups.isGrouped()).isFalse();
        assertThat(names(groups.offered(Set.of(), group -> {
        }))).containsExactly("ls", "start_onboarding");
        assertThat(ToolGroups.of(builtIns("ls", "start_onboarding", "save_xslt"), Map.of(), McpToolset.EMPTY, 0)
                .isGrouped()).isFalse();
    }

    @Test
    void anMcpGroupIsntMergedIntoABuiltInGroupOfTheSameName() {
        final McpToolset toolset = toolset(null,
                tool("build_one"), tool("build_two"), tool("index_create"), tool("index_status"));

        final ToolGroups groups = ToolGroups.of(builtIns("ls", "start_onboarding", "save_xslt"), toolset.tools(),
                toolset, 2);

        assertThat(groups.groupNames()).containsExactly("build", "mcp_build", "index");
        assertThat(groups.groupOf("build_one")).contains("mcp_build");
    }

    @Test
    void aDashboardTabsGuidanceNeedsTheDashboardTools() {
        assertThat(BuiltInToolGroups.neededBy("system\n\n" + ContextAssembler.DASHBOARD_GUIDANCE))
                .containsExactly(BuiltInToolGroups.DASHBOARDS);
        assertThat(BuiltInToolGroups.neededBy("system")).isEmpty();
        assertThat(BuiltInToolGroups.neededBy(null)).isEmpty();
    }

    private static Map<ToolSpecification, ToolExecutor> builtIns(final String... names) {
        final Map<ToolSpecification, ToolExecutor> tools = new java.util.LinkedHashMap<>();
        for (final String name : names) {
            tools.put(ToolSpecification.builder().name(name).description("does " + name).build(),
                    (request, memoryId) -> name);
        }
        return tools;
    }

    private static AiChatMessage message(final int id,
                                         final int turnId,
                                         final AiMessageType type,
                                         final String text) {
        return new AiChatMessage(id, 1, 0L, type, null, text, turnId, 0);
    }

    private static List<String> names(final Map<ToolSpecification, ToolExecutor> tools) {
        return tools.keySet().stream().map(ToolSpecification::name).toList();
    }

    private static Tool tool(final String name) {
        return tool(name, null);
    }

    private static Tool tool(final String name, final Map<String, Object> meta) {
        return Tool.builder()
                .name(name)
                .description("does " + name)
                .inputSchema(Map.of("type", "object"))
                .meta(meta)
                .build();
    }

    private static McpToolset toolset(final McpToolServerConfig config, final Tool... tools) {
        final McpSyncClient client = Mockito.mock(McpSyncClient.class);
        Mockito.when(client.listTools()).thenReturn(new ListToolsResult(List.of(tools), null));
        return new McpToolset(List.of(new McpServerConnection("fastmcp", config, client, null)), List.of(), true);
    }
}
