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
    void groupsTurnedOnBeforeAreFoundFromTheChat() {
        // As after Stroom restarts: the chat shows the groups were turned on, so the model carries on as if they
        // were.
        final AiDao aiDao = Mockito.mock(AiDao.class);
        Mockito.when(aiDao.getMessages(1)).thenReturn(List.of(
                message(1, AiMessageType.TOOL_CALL, "{\"callId\":\"a\",\"tool\":\"activate_onboarding\"}"),
                message(2, AiMessageType.TOOL_RESULT, "{\"tool\":\"activate_explore\"}"),
                message(3, AiMessageType.TOOL_CALL, "{\"callId\":\"b\",\"tool\":\"build_status\"}"),
                message(4, AiMessageType.TOOL_CALL, "not json"),
                message(5, AiMessageType.TOOL_CALL, "{\"callId\":\"c\",\"tool\":\"activate_builds\"}")));
        final ToolGroups.Activations activations = new ToolGroups.Activations(() -> aiDao);

        assertThat(activations.active(1)).containsExactlyInAnyOrder("onboarding", "builds");
        activations.activate(1, "explore");
        assertThat(activations.active(1)).containsExactlyInAnyOrder("onboarding", "builds", "explore");
        // The chat is only read once.
        Mockito.verify(aiDao, Mockito.times(1)).getMessages(1);
    }

    private static AiChatMessage message(final int id, final AiMessageType type, final String text) {
        return new AiChatMessage(id, 1, 0L, type, null, text, 0, 0);
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
