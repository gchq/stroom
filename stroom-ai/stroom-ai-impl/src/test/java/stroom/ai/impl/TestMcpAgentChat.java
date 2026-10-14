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
import stroom.openai.shared.OpenAIModelDoc;
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
    private final AiChatRunManager runManager = Mockito.mock(AiChatRunManager.class);
    private final stroom.ai.impl.workspace.WorkspaceStore workspaceStore =
            Mockito.mock(stroom.ai.impl.workspace.WorkspaceStore.class);
    private final McpAgentChat agentChat = new McpAgentChat(aiService, builtInTools, runManager, workspaceStore,
            Mockito.mock(ContextUsageTracker.class));

    {
        // Stored messages get an id and turn, as the DAO would give them.
        Mockito.when(aiService.storeMessage(Mockito.anyInt(), Mockito.any(), Mockito.anyString()))
                .thenAnswer(invocation -> new stroom.ai.shared.AiChatMessage(1000, invocation.getArgument(0), 0L,
                        invocation.getArgument(1), null, invocation.getArgument(2), 500, null));
    }

    {
        Mockito.when(builtInTools.toolMap()).thenReturn(Map.of());
    }

    private final List<ChatMessage> messages = List.of(
            new SystemMessage("system prompt"),
            new UserMessage("how many errors today?"));

    @Test
    void chatWithToolsAnswersWithoutCallingATool() {
        final McpToolset toolset = toolset(Mockito.mock(McpSyncClient.class), searchTool());

        final ChatResponse response = agentChat.chat(new StubChatModel(), null, 1, messages, toolset, 99,
                new AtomicBoolean(false), taskContext, 1, new StringBuilder(), 0);

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
        final ChatResponse response = agentChat.chat(model, null, 7, messages, toolset, 99,
                new AtomicBoolean(false), taskContext, 2, debugLog, 0);

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
        agentChat.chat(model, null, 7, messages, toolset, 99, new AtomicBoolean(false), taskContext, 1, debugLog, 0);

        assertThat(debugLog.toString()).contains("built-in result");
    }

    @Test
    void toolsTurnedOffGivesAPlainCall() {
        final McpSyncClient client = Mockito.mock(McpSyncClient.class);
        final McpToolset toolset = toolset(client, searchTool());
        final OpenAIModelDoc modelDoc = OpenAIModelDoc.builder().uuid("u").name("m").toolsEnabled(false).build();
        final StubToolCallingModel model = new StubToolCallingModel(AiMessage.from("an answer"));

        agentChat.chat(model, modelDoc, 1, messages, toolset, 99,
                new AtomicBoolean(false), taskContext, 1, new StringBuilder(), 0);

        assertThat(model.requests).singleElement()
                .satisfies(request -> assertThat(request.toolSpecifications()).isNullOrEmpty());
        Mockito.verify(client, Mockito.never()).listTools();
    }

    @Test
    void toolsPastTheBudgetAreLeftOut() {
        final Map<dev.langchain4j.agent.tool.ToolSpecification, dev.langchain4j.service.tool.ToolExecutor> tools =
                new java.util.LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            tools.put(dev.langchain4j.agent.tool.ToolSpecification.builder().name("t" + i).build(),
                    (request, memoryId) -> "");
        }

        McpAgentChat.limitTools(tools, 3, 1);
        assertThat(tools.keySet()).extracting(dev.langchain4j.agent.tool.ToolSpecification::name)
                .containsExactly("t1", "t2", "t3");

        // 0 means no limit.
        McpAgentChat.limitTools(tools, 0, 1);
        assertThat(tools).hasSize(3);
    }

    @Test
    void unattendedRunDoesNotRunToolsThatCouldChangeSomething() {
        Mockito.when(runManager.isUnattended(7)).thenReturn(true);
        final McpSyncClient client = Mockito.mock(McpSyncClient.class);
        final McpToolset toolset = toolset(client, searchTool());

        final ChatModel model = new StubToolCallingModel(
                AiMessage.from(List.of(ToolExecutionRequest.builder()
                        .id("1")
                        .name("test__search")
                        .arguments("{}")
                        .build())),
                AiMessage.from("done"));
        final StringBuilder debugLog = new StringBuilder();
        agentChat.chat(model, null, 7, messages, toolset, 99, new AtomicBoolean(false), taskContext, 1, debugLog, 0);

        // The server is not trusted, so the tool is not known to be read-only, and is not run.
        Mockito.verify(client, Mockito.never()).callTool(Mockito.any());
        assertThat(debugLog.toString()).contains("the user is away");
    }

    @Test
    void unattendedRunStillRunsReadOnlyTools() {
        Mockito.when(runManager.isUnattended(7)).thenReturn(true);
        final McpSyncClient client = Mockito.mock(McpSyncClient.class);
        Mockito.when(client.callTool(Mockito.any(CallToolRequest.class)))
                .thenReturn(CallToolResult.builder().content(List.of(new TextContent("42 errors"))).build());
        final Tool readOnlyTool = Tool.builder()
                .name("search")
                .inputSchema(Map.of("type", "object"))
                .annotations(new io.modelcontextprotocol.spec.McpSchema.ToolAnnotations(
                        null, true, false, true, false, null))
                .build();
        Mockito.when(client.listTools()).thenReturn(new ListToolsResult(List.of(readOnlyTool), null));
        final stroom.openai.shared.McpToolServerConfig trusted = stroom.openai.shared.McpToolServerConfig.builder()
                .name("test")
                .trusted(true)
                .build();
        final McpToolset toolset = new McpToolset(
                List.of(new McpServerConnection("test", trusted, client, null)), List.of(), true);

        final ChatModel model = new StubToolCallingModel(
                AiMessage.from(List.of(ToolExecutionRequest.builder()
                        .id("1")
                        .name("test__search")
                        .arguments("{}")
                        .build())),
                AiMessage.from("done"));
        agentChat.chat(model, null, 7, messages, toolset, 99, new AtomicBoolean(false), taskContext, 1,
                new StringBuilder(), 0);

        Mockito.verify(client).callTool(Mockito.any());
    }

    @Test
    void steeringMessagesArePickedUp() {
        final McpSyncClient client = Mockito.mock(McpSyncClient.class);
        Mockito.when(client.callTool(Mockito.any(CallToolRequest.class)))
                .thenReturn(CallToolResult.builder().content(List.of(new TextContent("42 errors"))).build());
        final McpToolset toolset = toolset(client, searchTool());
        // The user sends a message while the tool runs.
        Mockito.when(aiService.getMessagesSince(7, 100)).thenReturn(List.of(
                new stroom.ai.shared.AiChatMessage(101, 7, 0L, stroom.ai.shared.AiMessageType.STEER_MESSAGE,
                        null, "only count firewall errors", 1, null)));
        Mockito.when(aiService.getMessagesSince(7, 101)).thenReturn(List.of());

        final StubToolCallingModel model = new StubToolCallingModel(
                AiMessage.from(List.of(ToolExecutionRequest.builder()
                        .id("1")
                        .name("test__search")
                        .arguments("{}")
                        .build())),
                AiMessage.from("There were 3 firewall errors."));
        agentChat.chat(model, null, 7, messages, toolset, 99, new AtomicBoolean(false), taskContext, 1,
                new StringBuilder(), 100);

        // The second model call includes the user's message.
        assertThat(model.requests).hasSize(2);
        assertThat(model.requests.get(1).messages())
                .anySatisfy(message -> assertThat(message).isInstanceOf(UserMessage.class)
                        .extracting(m -> ((UserMessage) m).singleText())
                        .asString()
                        .contains("only count firewall errors")
                        .startsWith(McpAgentChat.STEER_PREFIX));
    }

    @Test
    void steeringMessageSentAsTheModelFinishesGetsAnAnswer() {
        Mockito.when(builtInTools.toolMap()).thenReturn(Map.of());
        Mockito.when(aiService.getMessagesSince(7, 100)).thenReturn(List.of(
                new stroom.ai.shared.AiChatMessage(101, 7, 0L, stroom.ai.shared.AiMessageType.STEER_MESSAGE,
                        null, "and yesterday?", 1, null)));
        Mockito.when(aiService.getMessagesSince(7, 101)).thenReturn(List.of());
        final StubToolCallingModel model = new StubToolCallingModel(
                AiMessage.from("Today there were 5."),
                AiMessage.from("Today 5, yesterday 2."));

        final ChatResponse response = agentChat.chat(model, null, 7, messages, McpToolset.EMPTY, 99,
                new AtomicBoolean(false), taskContext, 1, new StringBuilder(), 100);

        assertThat(response.aiMessage().text()).isEqualTo("Today 5, yesterday 2.");
        assertThat(model.requests).hasSize(2);
    }

    @Test
    void toolCallsAndResultsAreRecorded() {
        final McpSyncClient client = Mockito.mock(McpSyncClient.class);
        Mockito.when(client.callTool(Mockito.any(CallToolRequest.class)))
                .thenReturn(CallToolResult.builder().content(List.of(new TextContent("42 errors"))).build());
        final McpToolset toolset = toolset(client, searchTool());
        final ChatModel model = new StubToolCallingModel(
                AiMessage.from("Counting.", List.of(ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("test__search")
                        .arguments("{\"query\":\"errors\"}")
                        .build())),
                AiMessage.from("42."));

        agentChat.chat(model, null, 7, messages, toolset, 99, new AtomicBoolean(false), taskContext, 1,
                new StringBuilder(), 0);

        final ArgumentCaptor<String> call = ArgumentCaptor.forClass(String.class);
        Mockito.verify(aiService).storeMessage(Mockito.eq(7), Mockito.eq(stroom.ai.shared.AiMessageType.TOOL_CALL),
                call.capture());
        assertThat(call.getValue()).contains("\"tool\":\"test__search\"").contains("errors").contains("Counting.");
        final ArgumentCaptor<String> result = ArgumentCaptor.forClass(String.class);
        Mockito.verify(aiService).storeMessage(Mockito.eq(7),
                Mockito.eq(stroom.ai.shared.AiMessageType.TOOL_RESULT), result.capture());
        assertThat(result.getValue()).contains("\"text\":\"42 errors\"").contains("\"error\":false");
        Mockito.verifyNoInteractions(workspaceStore);
    }

    @Test
    void largeToolOutputIsSavedAndTheModelGetsAStub() {
        final String bigOutput = "line\n".repeat(5_000);
        final McpSyncClient client = Mockito.mock(McpSyncClient.class);
        Mockito.when(client.callTool(Mockito.any(CallToolRequest.class)))
                .thenReturn(CallToolResult.builder().content(List.of(new TextContent(bigOutput))).build());
        final McpToolset toolset = toolset(client, searchTool());
        final StubToolCallingModel model = new StubToolCallingModel(
                AiMessage.from(List.of(ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("test__search")
                        .arguments("{}")
                        .build())),
                AiMessage.from("done"));

        agentChat.chat(model, null, 7, messages, toolset, 99, new AtomicBoolean(false), taskContext, 1,
                new StringBuilder(), 0);

        final String path = ToolOutputs.outputPath(500, 1000);
        Mockito.verify(workspaceStore).createFile(7, stroom.ai.shared.AiAttachmentType.TOOL_OUTPUT, path, 1000,
                bigOutput.strip());
        // The second model call was given the stub, not the whole output.
        assertThat(model.requests.get(1).messages().getLast())
                .isInstanceOf(dev.langchain4j.data.message.ToolExecutionResultMessage.class)
                .extracting(m -> ((dev.langchain4j.data.message.ToolExecutionResultMessage) m).text())
                .asString()
                .contains("too large to show in full")
                .contains(path)
                .hasSizeLessThan(ToolOutputs.MAX_INLINE_CHARS);
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
        private final List<ChatRequest> requests = new java.util.ArrayList<>();
        private int turn;

        private StubToolCallingModel(final AiMessage... turns) {
            this.turns = List.of(turns);
        }

        @Override
        public ChatResponse doChat(final ChatRequest chatRequest) {
            requests.add(chatRequest);
            final AiMessage aiMessage = turns.get(Math.min(turn++, turns.size() - 1));
            return ChatResponse.builder().aiMessage(aiMessage).build();
        }
    }
}
