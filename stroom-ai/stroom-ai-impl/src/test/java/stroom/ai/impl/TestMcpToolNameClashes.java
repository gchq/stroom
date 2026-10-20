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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// Fixes, in a reference file, how MCP tools whose names clash with the built-in tools' names, or with each other
/// across servers, are offered to the model: the name each is given (see [McpToolNames]) and which tool each
/// offered name runs, found by having a scripted model call every offered name through the real agent loop.
class TestMcpToolNameClashes {

    private static final String REFERENCE = "permissions-audit-consent/mcp-tool-name-clashes.txt";

    @TempDir
    Path tempDir;

    @Test
    void clashingNamesAreOfferedAndRunAsToday() {
        // A server whose tools' names equal, differ only in case from, are made equal to (once made safe), or
        // start with a built-in tool's name, or equal the name a clash would be given.
        final FakeMcpServer first = new FakeMcpServer("Stroom", true, List.of(), List.of(
                FakeMcpServer.tool("read_document"),
                FakeMcpServer.tool("Read_Document"),
                FakeMcpServer.tool("read document"),
                FakeMcpServer.tool("read.document"),
                FakeMcpServer.tool("read_document_v2"),
                FakeMcpServer.tool("read"),
                FakeMcpServer.tool("stroom__read_document"),
                FakeMcpServer.tool("ls"),
                FakeMcpServer.tool("find_docs"),
                FakeMcpServer.tool("list_resources")));
        // A second server with tools of the same names, and a third whose name makes the same prefix as the first.
        final FakeMcpServer second = new FakeMcpServer("other", true, List.of(), List.of(
                FakeMcpServer.tool("find_docs"),
                FakeMcpServer.tool("read_document"),
                FakeMcpServer.tool("stroom__read_document")));
        final FakeMcpServer third = new FakeMcpServer("stroom", true, List.of(), List.of(
                FakeMcpServer.tool("find_docs"),
                FakeMcpServer.tool("read_document")));
        final List<FakeMcpServer> servers = List.of(first, second, third);

        final StringBuilder sb = new StringBuilder();
        sb.append("# How MCP tools with clashing names are offered, and what each offered name runs.\n");
        for (final FakeMcpServer server : servers) {
            sb.append("# Server '").append(server.label()).append("' (prefix '")
                    .append(McpToolNames.serverPrefix(server.label())).append("') lists: ")
                    .append(server.listedNames()).append('\n');
        }

        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            final McpToolset toolset = FakeMcpServer.toolset(first, second, third);
            final ToolConsent toolConsent = Mockito.mock(ToolConsent.class);
            Mockito.when(toolConsent.decide(Mockito.anyInt(), Mockito.anyString(), Mockito.any(), Mockito.any(),
                    Mockito.anyBoolean())).thenReturn(ToolConsent.Verdict.RUN);
            final McpAgentChat agentChat = world.agentChat(toolConsent, Mockito.mock(AiChatRunManager.class));

            // First see what is offered.
            final BuiltInToolWorld.ScriptedModel looking = new BuiltInToolWorld.ScriptedModel(List.of());
            BuiltInToolWorld.chat(agentChat, looking, toolset);
            final List<String> offered = looking.firstOffered();
            final Set<String> builtIns = new HashSet<>(world.toolNames());
            final List<String> mcpNames = offered.stream()
                    .filter(name -> !builtIns.contains(name))
                    .toList();
            // Every built-in tool is still offered under its own name, ahead of the MCP tools.
            assertThat(offered.subList(0, builtIns.size())).containsExactlyInAnyOrderElementsOf(builtIns);
            assertThat(offered).doesNotHaveDuplicates();

            // Then call every MCP name, and the clashed-with built-in names, and see what runs.
            final List<String> called = new ArrayList<>(mcpNames);
            called.add("read_document");
            called.add("ls");
            final List<ToolExecutionRequest> requests = new ArrayList<>();
            for (int i = 0; i < called.size(); i++) {
                requests.add(ToolExecutionRequest.builder()
                        .id("call-" + i)
                        .name(called.get(i))
                        .arguments("read_document".equals(called.get(i))
                                ? "{\"uuid\":\"xslt-uuid\"}"
                                : "{\"path\":\"/\"}")
                        .build());
            }
            final BuiltInToolWorld.ScriptedModel calling = new BuiltInToolWorld.ScriptedModel(List.of(requests));
            BuiltInToolWorld.chat(agentChat, calling, toolset);
            final Map<String, String> results = results(calling.requests().get(1));

            sb.append("\n## The MCP tools offered, in order, and what each name runs\n");
            for (int i = 0; i < called.size(); i++) {
                final String name = called.get(i);
                final String result = results.getOrDefault("call-" + i, "(no result)");
                final String runs = builtIns.contains(name)
                        ? "built-in: " + world.normalise(result).lines().findFirst().orElse("")
                        : "MCP: " + result;
                sb.append("- ").append(name).append(" -> ").append(runs).append('\n');
            }
        }
        GoldenFiles.assertMatches(REFERENCE, sb.toString());
    }

    // The result given to the model for each call, by the call's id.
    private static Map<String, String> results(final ChatRequest request) {
        final Map<String, String> results = new LinkedHashMap<>();
        for (final ChatMessage message : request.messages()) {
            if (message instanceof final ToolExecutionResultMessage result) {
                results.put(result.id(), result.text());
            }
        }
        return results;
    }
}
