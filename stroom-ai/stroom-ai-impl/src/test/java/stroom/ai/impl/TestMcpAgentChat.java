/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.ai.api.AiService;
import stroom.ai.impl.tools.BuiltInTools;
import stroom.task.api.TaskContext;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class TestMcpAgentChat {

    private final AiService aiService = Mockito.mock(AiService.class);
    private final TaskContext taskContext = Mockito.mock(TaskContext.class);
    private final BuiltInTools builtInTools = Mockito.mock(BuiltInTools.class);
    private final McpAgentChat agentChat = new McpAgentChat(aiService, builtInTools);

    {
        Mockito.when(builtInTools.toolMap()).thenReturn(Map.of());
    }

    private final List<ChatMessage> messages = List.of(
            new SystemMessage("system prompt"),
            new UserMessage("how many errors today?"));

    @Test
    void chatWithToolsAnswersWithoutCallingATool() {
        final McpToolset toolset = toolset(Mockito.mock(McpSyncClient.class), searchTool());

        final ChatResponse response = agentChat.chat(new StubChatModel(), 1, messages, toolset, 99,
                new AtomicBoolean(false), taskContext, 1, new StringBuilder());

        // The stub model answers without calling a tool, so we just get its plain response back.
        assertThat(response.aiMessage().text()).contains("[Stub Response]");
    }

    @Test
    void progressAndDebugDetailDescribeWhyEachToolRuns() {
        final McpSyncClient client = Mockito.mock(McpSyncClient.class);
        Mockito.when(client.callTool(Mockito.any(CallToolRequest.class)))
                .thenReturn(CallToolResult.builder()
                        .content(List.of(new TextContent("42 errors")))
                        .isError(false)
                        .build());
        final McpToolset toolset = toolset(client, searchTool());

        // First model turn: rationale + a tool call; second turn: the final answer.
        final ChatModel model = new StubToolCallingModel(
                AiMessage.from("I need to check today's error count.",
                        List.of(ToolExecutionRequest.builder()
                                .id("1")
                                .name("test__search")
                                .arguments("{\"query\":\"level:ERROR AND @timestamp>now-1d\"}")
                                .build())),
                AiMessage.from("There were 42 errors today."));

        final StringBuilder debugLog = new StringBuilder();
        final ChatResponse response = agentChat.chat(model, 7, messages, toolset, 99,
                new AtomicBoolean(false), taskContext, 2, debugLog);

        assertThat(response.aiMessage().text()).isEqualTo("There were 42 errors today.");

        // The progress message names the tool and carries the model's reason for calling it.
        final ArgumentCaptor<String> progress = ArgumentCaptor.forClass(String.class);
        Mockito.verify(aiService, Mockito.atLeastOnce()).updateMessageText(Mockito.eq(99), progress.capture());
        assertThat(progress.getAllValues())
                .anySatisfy(msg -> assertThat(msg)
                        .contains("check today's error count")
                        .contains("search"));

        // The persistent request detail records the rationale, the arguments and the result.
        final String detail = debugLog.toString();
        assertThat(detail)
                .contains("Attempt 2 — Tool Use")
                .contains("Model rationale:")
                .contains("check today's error count")
                .contains("`test__search`")
                .contains("level:ERROR AND @timestamp>now-1d")
                .contains("42 errors");
    }

    @Test
    void unavailableServersAreMentionedToTheModel() {
        final List<ChatMessage> conversation = McpAgentChat.withUnavailableServersNote(messages,
                List.of("MCP tool server 'elastic' could not be reached"));

        assertThat(conversation).hasSize(3);
        assertThat(conversation.get(1)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) conversation.get(1)).text())
                .contains("unavailable")
                .contains("'elastic' could not be reached");
        assertThat(McpAgentChat.withUnavailableServersNote(messages, List.of())).isEqualTo(messages);
    }

    @Test
    void mcpToolCannotReplaceABuiltInTool() {
        final dev.langchain4j.agent.tool.ToolSpecification builtIn = dev.langchain4j.agent.tool.ToolSpecification
                .builder()
                .name("list_resources")
                .description("built in")
                .build();
        Mockito.when(builtInTools.toolMap()).thenReturn(Map.of(builtIn, (request, memoryId) -> "built-in result"));
        final McpSyncClient client = Mockito.mock(McpSyncClient.class);
        Mockito.when(client.getServerCapabilities()).thenReturn(
                io.modelcontextprotocol.spec.McpSchema.ServerCapabilities.builder().resources(false, false).build());
        final McpToolset toolset = toolset(client, searchTool());

        final ChatModel model = new StubToolCallingModel(
                AiMessage.from(List.of(ToolExecutionRequest.builder()
                        .id("1")
                        .name("list_resources")
                        .arguments("{}")
                        .build())),
                AiMessage.from("done"));
        final StringBuilder debugLog = new StringBuilder();
        agentChat.chat(model, 7, messages, toolset, 99, new AtomicBoolean(false), taskContext, 1, debugLog);

        assertThat(debugLog.toString()).contains("built-in result");
    }

    private static Tool searchTool() {
        return Tool.builder()
                .name("search")
                .description("run a search")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("query", Map.of("type", "string"))))
                .build();
    }

    private static McpToolset toolset(final McpSyncClient client, final Tool... tools) {
        Mockito.when(client.listTools()).thenReturn(new ListToolsResult(List.of(tools), null));
        return new McpToolset(List.of(new McpServerConnection("test", null, client, null)), List.of(), true);
    }

    /**
     * A minimal {@link ChatModel} that returns a fixed sequence of {@link AiMessage}s, one per call.
     */
    private static final class StubToolCallingModel implements ChatModel {

        private final List<AiMessage> turns;
        private int turn;

        private StubToolCallingModel(final AiMessage... turns) {
            this.turns = List.of(turns);
        }

        @Override
        public ChatResponse doChat(final ChatRequest chatRequest) {
            final AiMessage aiMessage = turns.get(Math.min(turn++, turns.size() - 1));
            return ChatResponse.builder().aiMessage(aiMessage).build();
        }
    }
}
