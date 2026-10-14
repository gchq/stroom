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
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolExecutionResult;
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

    /**
     * Regression: {@code ToolProviderRequest} requires a non-null invocationContext and userMessage, so
     * building the request without them threw "invocationContext cannot be null" as soon as a tool server
     * was configured.
     */
    @Test
    void chatWithToolsDoesNotThrowOnRequestBuild() {
        final McpToolset toolset = toolset(Mockito.mock(McpClient.class), toolSpec());

        final ChatResponse response = agentChat.chat(new StubChatModel(), 1, messages, toolset, 99,
                new AtomicBoolean(false), taskContext, 1, new StringBuilder());

        // The stub model answers without calling a tool, so we just get its plain response back.
        assertThat(response.aiMessage().text()).contains("[Stub Response]");
    }

    @Test
    void progressAndDebugDetailDescribeWhyEachToolRuns() {
        final McpClient client = Mockito.mock(McpClient.class);
        Mockito.when(client.executeTool(Mockito.any(ToolExecutionRequest.class),
                        Mockito.any(InvocationContext.class)))
                .thenReturn(ToolExecutionResult.builder().resultText("42 errors").build());
        final McpToolset toolset = toolset(client, toolSpec());

        // First model turn: rationale + a tool call; second turn: the final answer.
        final ChatModel model = new StubToolCallingModel(
                AiMessage.from("I need to check today's error count.",
                        List.of(ToolExecutionRequest.builder()
                                .id("1")
                                .name("search")
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
                .contains("`search`")
                .contains("level:ERROR AND @timestamp>now-1d")
                .contains("42 errors");
    }

    private static ToolSpecification toolSpec() {
        return ToolSpecification.builder().name("search").description("run a search").build();
    }

    private static McpToolset toolset(final McpClient client, final ToolSpecification... tools) {
        Mockito.when(client.key()).thenReturn("test");
        Mockito.when(client.listTools()).thenReturn(List.of(tools));
        final McpToolProvider provider = McpToolProvider.builder()
                .mcpClients(List.of(client))
                .failIfOneServerFails(false)
                .build();
        return new McpToolset(List.of(client), provider);
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
