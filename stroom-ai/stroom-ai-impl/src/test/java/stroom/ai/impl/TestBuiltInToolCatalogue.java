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
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.WorkspaceArguments;
import stroom.ai.impl.workspace.WorkspaceStore;
import stroom.ai.shared.AiAgentMode;
import stroom.ai.shared.AiChatMessage;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.task.api.TaskContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.JsonSchemaElementUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/// The catalogue of built-in tools the model is shown, in a reference file (as `expected.yaml` is for
/// configuration): each tool's name, full description, parameter schema, the flags that decide how its calls and
/// results are handled, and which tools are offered in each chat mode, found by running a chat in each mode as
/// [McpAgentChat] does. A change to any of it fails until it is accepted (see [GoldenFiles]), so a refactor of the
/// tools can be shown to change nothing the model sees.
class TestBuiltInToolCatalogue {

    private static final String CATALOGUE_FILE = "tools/catalogue.txt";
    private static final int CHAT_ID = 1;
    // Sorted keys and indented, so the schema reads the same whatever order it was built in.
    private static final ObjectMapper SCHEMA_MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    private final AiService aiService = Mockito.mock(AiService.class);
    private final AiDao aiDao = Mockito.mock(AiDao.class);
    private final BuiltInToolsTestWiring wiring = new BuiltInToolsTestWiring(aiService);
    private final BuiltInTools builtInTools = wiring.builtInTools();

    @Test
    void theToolCatalogueIsAsItWas() {
        final Map<String, List<String>> offeredByMode = new TreeMap<>();
        for (final AiAgentMode mode : AiAgentMode.values()) {
            offeredByMode.put(mode.name(), offered(mode, null));
        }
        final List<String> withoutTools = offered(AiAgentMode.AGENT,
                OpenAIModelDoc.builder().uuid("model-uuid").name("A model").toolsEnabled(false).build());

        final List<ToolSpecification> specifications = builtInTools.toolMap().keySet().stream()
                .sorted(Comparator.comparing(ToolSpecification::name))
                .toList();
        // Each tool once, so a tool can't be registered twice under one name.
        assertThat(specifications).extracting(ToolSpecification::name).doesNotHaveDuplicates();
        assertThat(wiring.tools()).hasSameSizeAs(specifications);

        final StringBuilder sb = new StringBuilder();
        sb.append("# The built-in tools the model is shown\n\n");
        sb.append("Built-in tools: ").append(specifications.size()).append("\n\n");
        sb.append("## Offered in each mode (in a chat with no MCP servers)\n");
        offeredByMode.forEach((mode, names) -> sb.append("\n### ").append(mode).append(" (")
                .append(names.size()).append(" tool(s))\n").append(lines(names)).append('\n'));
        sb.append("\n### A model with tools turned off (").append(withoutTools.size()).append(" tool(s))\n")
                .append(lines(withoutTools)).append('\n');
        for (final ToolSpecification specification : specifications) {
            sb.append("\n").append("=".repeat(80)).append("\n");
            appendTool(sb, specification, offeredByMode);
        }

        GoldenFiles.assertMatches(CATALOGUE_FILE, sb.toString());
    }

    @Test
    void aToolProposesChangesToADocumentsDocumentation() {
        // Seen in testing: asked to document a dashboard, the agent could not. document_documentation only reads
        // a Documentation tab, and no built-in tool proposes a change to one, for any type of document.
        assertThat(builtInTools.toolMap().keySet())
                .extracting(ToolSpecification::name)
                .contains("document_documentation", "propose_documentation_change");
    }

    private void appendTool(final StringBuilder sb,
                            final ToolSpecification specification,
                            final Map<String, List<String>> offeredByMode) {
        final String name = specification.name();
        final List<String> modes = offeredByMode.entrySet().stream()
                .filter(entry -> entry.getValue().contains(name))
                .map(Map.Entry::getKey)
                .toList();
        sb.append("## ").append(name).append("\n\n");
        sb.append("isReadOnly: ").append(builtInTools.isReadOnly(name)).append('\n');
        sb.append("readsUntrustedContent: ").append(builtInTools.readsUntrustedContent(name)).append('\n');
        sb.append("boundsItsOutput: ").append(builtInTools.boundsItsOutput(name)).append('\n');
        sb.append("strict: ").append(specification.strict()).append('\n');
        sb.append("metadata: ").append(json(new TreeMap<>(specification.metadata()))).append('\n');
        sb.append("offered in modes: ").append(String.join(", ", modes)).append("\n\n");
        sb.append("### Description\n\n").append(specification.description()).append("\n\n");
        sb.append("### Parameters\n\n").append(specification.parameters() == null
                ? "(none)"
                : json(JsonSchemaElementUtils.toMap(specification.parameters()))).append('\n');
    }

    // The tools offered for the first model call of a chat in the mode, as McpAgentChat chooses them.
    private List<String> offered(final AiAgentMode mode, final OpenAIModelDoc modelDoc) {
        Mockito.when(aiDao.getAgentMode(CHAT_ID)).thenReturn(mode);
        Mockito.when(aiService.storeMessage(Mockito.anyInt(), Mockito.any(), Mockito.anyString()))
                .thenAnswer(invocation -> new AiChatMessage(1000, CHAT_ID, 0L, invocation.getArgument(1), null,
                        invocation.getArgument(2), 1, null));
        final AiChatRunManager runManager = Mockito.mock(AiChatRunManager.class);
        final McpAgentChat agentChat = new McpAgentChat(aiService, builtInTools,
                runManager, Mockito.mock(WorkspaceStore.class),
                Mockito.mock(ContextUsageTracker.class),
                ConsentGates.chat(Mockito.mock(ToolConsent.class), aiService, runManager),
                Mockito.mock(AiAuditLog.class), new ModelStreamer(aiService),
                new ToolGroups.Activations(() -> aiDao), AiConfig::new, aiDao,
                new UserActions(Mockito.mock(UserActionLookup.class)),
                new WorkspaceArguments(Mockito.mock(ChatWorkspace.class)));
        final List<ChatRequest> requests = new ArrayList<>();
        final ChatModel model = new ChatModel() {
            @Override
            public ChatResponse doChat(final ChatRequest chatRequest) {
                requests.add(chatRequest);
                return ChatResponse.builder().aiMessage(AiMessage.from("an answer")).build();
            }
        };

        agentChat.chat(model, modelDoc, CHAT_ID, List.of(new SystemMessage("system"), new UserMessage("q")),
                McpToolset.EMPTY, 99, new AtomicBoolean(false), Mockito.mock(TaskContext.class),
                1, new StringBuilder(), 0, ContextBudget.UNKNOWN);

        assertThat(requests).hasSize(1);
        final List<ToolSpecification> specifications = requests.getFirst().toolSpecifications();
        return specifications == null
                ? List.of()
                : specifications.stream().map(ToolSpecification::name).sorted().toList();
    }

    private static String lines(final List<String> names) {
        return names.isEmpty()
                ? "(none)"
                : String.join("\n", names);
    }

    private static String json(final Object value) {
        try {
            return SCHEMA_MAPPER.writeValueAsString(value);
        } catch (final JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
