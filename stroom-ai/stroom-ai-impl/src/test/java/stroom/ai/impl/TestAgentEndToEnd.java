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

import stroom.ai.api.AiService;
import stroom.ai.impl.tools.BuiltInTools;
import stroom.ai.impl.workspace.WorkspaceStore;
import stroom.ai.shared.AiAgentMode;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.docref.DocRef;
import stroom.openai.shared.McpAuthMode;
import stroom.openai.shared.McpToolServerConfig;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.security.api.SecurityContext;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.task.api.TaskContext;
import stroom.util.http.HttpClientConfiguration;
import stroom.util.jersey.HttpClientProvider;
import stroom.util.jersey.HttpClientProviderCache;
import stroom.util.json.JsonUtil;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/// Runs the agent end to end against an in-process OpenAI-compatible model server and MCP server: the real
/// model client, HTTP client and streaming, the real MCP client over Streamable HTTP, and the agent loop. Only
/// storage, consent and Stroom's own state are stand-ins. Bugs that unit tests with stub models missed (an
/// answer that never finished streaming, the shared HTTP client closed after an answer) show up here.
class TestAgentEndToEnd {

    private static final int CHAT_ID = 7;
    private static final String PIPELINE_UUID = "3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90";
    private static final String INSTRUCTIONS = "Follow each result's next step.";

    private final List<AiChatMessage> stored = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger nextId = new AtomicInteger(100);
    private final AiService aiService = Mockito.mock(AiService.class);
    private final AiDao aiDao = Mockito.mock(AiDao.class);
    private final UserActionLookup lookup = Mockito.mock(UserActionLookup.class);
    private final CloseableHttpClient httpClient = HttpClients.createDefault();

    private FakeOpenAiServer model;
    private McpTestServer mcp;
    private McpAgentChat agentChat;
    private OpenAIModelDoc modelDoc;

    @BeforeEach
    void setUp() throws Exception {
        model = FakeOpenAiServer.start();
        mcp = McpTestServer.start(List.of(createFilterTool()), INSTRUCTIONS);
        modelDoc = OpenAIModelDoc.builder()
                .uuid("model-uuid")
                .name("model")
                .modelId("fake")
                .baseUrl(model.baseUrl())
                .toolsEnabled(true)
                .showReasoning(false)
                .mcpToolServers(List.of(McpToolServerConfig.builder()
                        .enabled(true)
                        .name("s")
                        .endpoint(mcp.endpoint())
                        .authMode(McpAuthMode.NONE)
                        .trusted(true)
                        .build()))
                .build();

        // Messages are kept in memory, as the database would keep them.
        Mockito.when(aiService.storeMessage(Mockito.anyInt(), Mockito.any(), Mockito.anyString()))
                .thenAnswer(invocation -> {
                    final AiChatMessage message = new AiChatMessage(nextId.getAndIncrement(), CHAT_ID, 0L,
                            invocation.getArgument(1), null, invocation.getArgument(2), 1, null);
                    stored.add(message);
                    return message;
                });
        Mockito.doAnswer(invocation -> stored.removeIf(m -> m.getId() == (int) invocation.getArgument(0)))
                .when(aiService).deleteMessage(Mockito.anyInt());
        Mockito.when(aiService.getMessages(CHAT_ID)).thenAnswer(invocation -> List.copyOf(stored));
        Mockito.when(aiService.getMessagesSince(Mockito.eq(CHAT_ID), Mockito.anyInt())).thenReturn(List.of());
        final OpenAiStreamingChatModel streamingModel = streamingModel();
        Mockito.when(aiService.getStreamingChatModel(Mockito.any())).thenReturn(streamingModel);
        Mockito.when(aiDao.getAgentMode(CHAT_ID)).thenReturn(AiAgentMode.AGENT);
        Mockito.when(lookup.find(new DocRef("Pipeline", PIPELINE_UUID)))
                .thenReturn(Optional.of(new DocRef("Pipeline", PIPELINE_UUID, "Acme-Events")));

        final ToolConsent toolConsent = Mockito.mock(ToolConsent.class);
        Mockito.when(toolConsent.decide(Mockito.anyInt(), Mockito.anyString(), Mockito.any(), Mockito.any(),
                Mockito.anyBoolean())).thenReturn(ToolConsent.Verdict.RUN);
        // No built-in tools: this is about the model and MCP server.
        final BuiltInTools builtInTools = Mockito.mock(BuiltInTools.class);
        Mockito.when(builtInTools.toolMap()).thenReturn(Map.of());
        agentChat = new McpAgentChat(aiService, builtInTools, Mockito.mock(AiChatRunManager.class),
                Mockito.mock(WorkspaceStore.class), Mockito.mock(ContextUsageTracker.class), toolConsent,
                Mockito.mock(AiAuditLog.class), new ModelStreamer(aiService), new ToolGroups.Activations(() -> aiDao),
                AiConfig::new, aiDao, new UserActions(lookup));
    }

    @AfterEach
    void tearDown() throws Exception {
        model.close();
        mcp.close();
        httpClient.close();
    }

    @Test
    void aRunCallsAnMcpToolThenStreamsTheAnswer() throws Exception {
        model.callTool("create_filter", "{\"feed\":\"ACME\"}")
                .answer("Filter 31 is ready for you to enable.");

        final ChatResponse response = run("set up feed ACME");

        assertThat(response.aiMessage().text()).isEqualTo("Filter 31 is ready for you to enable.");
        final List<Map<String, Object>> requests = model.requests();
        assertThat(requests).hasSize(2);
        // The trusted server's instructions are in the system prompt, and its tool is offered.
        assertThat(JsonUtil.writeValueAsString(requests.getFirst().get("messages"), false)).contains(INSTRUCTIONS);
        assertThat(JsonUtil.writeValueAsString(requests.getFirst().get("tools"), false))
                .contains("create_filter");
        // The tool's result went back to the model.
        assertThat(JsonUtil.writeValueAsString(requests.get(1).get("messages"), false))
                .contains("Filter 31 created, disabled.");

        // The call and its result are kept, with the checked step for the user.
        final ToolResultRecord result = JsonUtil.readValue(stored.stream()
                .filter(m -> m.getMessageType() == AiMessageType.TOOL_RESULT)
                .findFirst()
                .orElseThrow()
                .getMessage(), ToolResultRecord.class);
        assertThat(result.userActions())
                .singleElement()
                .extracting(UserActionRecord::label)
                .isEqualTo("Open Pipeline 'Acme-Events'");
        // The rows used while streaming are gone.
        assertThat(stored).noneMatch(m -> m.getMessageType() == AiMessageType.STREAMING_ANSWER
                                          || m.getMessageType() == AiMessageType.STREAMING_THINKING);
    }

    @Test
    void aSecondRunWorksAfterAStreamedAnswer() throws Exception {
        model.answer("First answer.").answer("Second answer.");

        assertThat(run("first").aiMessage().text()).isEqualTo("First answer.");
        // The shared HTTP client must still be open.
        assertThat(run("second").aiMessage().text()).isEqualTo("Second answer.");
    }

    private ChatResponse run(final String question) throws Exception {
        final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        try (final McpToolset toolset = new McpToolServerFactory(() -> null, null, null, () -> securityContext,
                () -> null,
                new McpClientCache(), () -> null, AiConfig::new, this::openIdConfiguration, () -> null)
                .createToolset(modelDoc)) {
            return agentChat.chat(notStreamed(), modelDoc, CHAT_ID,
                    List.of(new SystemMessage("system prompt"), new UserMessage(question)), toolset, 99,
                    new AtomicBoolean(false), Mockito.mock(TaskContext.class), 1, new StringBuilder(), 0,
                    ContextBudget.UNKNOWN);
        }
    }

    private OpenIdConfiguration openIdConfiguration() {
        final OpenIdConfiguration configuration = Mockito.mock(OpenIdConfiguration.class);
        Mockito.when(configuration.getIdentityProviderType()).thenReturn(IdpType.EXTERNAL_IDP);
        return configuration;
    }

    // The real streaming model client, over the real (shared) HTTP client.
    private OpenAiStreamingChatModel streamingModel() {
        final HttpClientProvider provider = Mockito.mock(HttpClientProvider.class);
        Mockito.when(provider.get()).thenReturn(httpClient);
        final HttpClientProviderCache cache = Mockito.mock(HttpClientProviderCache.class);
        Mockito.when(cache.get(Mockito.any())).thenReturn(provider);
        return OpenAiStreamingChatModel.builder()
                .baseUrl(model.baseUrl())
                .apiKey("key")
                .modelName("fake")
                .httpClientBuilder(new ApacheHttpClientBuilder(cache, HttpClientConfiguration.builder().build(),
                        Map.of()))
                .build();
    }

    // The model is only ever streamed, as it is in a chat.
    private static ChatModel notStreamed() {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(final ChatRequest chatRequest) {
                throw new AssertionError("The model should be streamed");
            }
        };
    }

    private static SyncToolSpecification createFilterTool() {
        final Tool tool = Tool.builder()
                .name("create_filter")
                .description("Creates a disabled processor filter")
                .inputSchema(Map.of("type", "object", "properties", Map.of("feed", Map.of("type", "string"))))
                .build();
        return new SyncToolSpecification(tool, (exchange, request) -> CallToolResult.builder()
                .addTextContent("Filter 31 created, disabled.")
                .structuredContent(Map.of("user_actions", List.of(Map.of(
                        "id", "open",
                        "action", "open_document",
                        "doc", Map.of("type", "Pipeline", "uuid", PIPELINE_UUID, "name", "x")))))
                .isError(false)
                .build());
    }
}
