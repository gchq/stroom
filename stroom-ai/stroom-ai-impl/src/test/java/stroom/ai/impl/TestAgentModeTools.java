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
import stroom.ai.api.TableSummariser;
import stroom.ai.impl.memory.AiMemoryService;
import stroom.ai.impl.tools.BuiltInTools;
import stroom.ai.impl.tools.BuiltInToolsModule;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.WorkspaceStore;
import stroom.ai.shared.AiAgentMode;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.TableAnalysisConfig;
import stroom.dashboard.impl.DashboardService;
import stroom.data.store.api.Store;
import stroom.dictionary.api.WordListProvider;
import stroom.docstore.api.DocDependencyService;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.explorer.api.ExplorerNodeService;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.query.impl.QueryService;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.multibindings.MapBinder;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// Which built-in tools the model is offered in each mode. Every bound tool must be listed here, so a new tool
/// can't be offered in Ask or Plan mode, which must not change anything, without someone deciding it should.
class TestAgentModeTools {

    /// Each built-in tool, and whether it changes anything (the workspace or memory).
    private static final Map<String, Boolean> CHANGES_THINGS = new TreeMap<>(Map.ofEntries(
            Map.entry("get_current_date", false),
            Map.entry("get_current_time", false),
            Map.entry("get_current_datetime", false),
            Map.entry("get_utc_time_offset", false),
            Map.entry("read_turn", false),
            Map.entry("search_history", false),
            Map.entry("memory", true),
            Map.entry("ls", false),
            Map.entry("read_file", false),
            Map.entry("grep", false),
            Map.entry("wc", false),
            Map.entry("write_file", true),
            Map.entry("str_replace", true),
            Map.entry("delete_file", true),
            Map.entry("jq", false),
            Map.entry("xpath", false),
            // These save their results to the workspace, which querying needs, but change nothing else.
            Map.entry("table", false),
            Map.entry("pipeline", false),
            Map.entry("run_stroomql", false),
            Map.entry("summarise_column", false),
            Map.entry("task_list", false),
            Map.entry("ask_user", false),
            Map.entry("fetch_events", false),
            Map.entry("search_events", false),
            Map.entry("document_info", false),
            Map.entry("read_document", false),
            Map.entry("document_documentation", false),
            // Saves its snapshot of the tab's results to the workspace, which querying needs.
            Map.entry("current_results", false),
            Map.entry("list_data_sources", false),
            Map.entry("describe_data_source", false),
            Map.entry("validate_stroomql", false),
            // Only offers the user a button: the user creates the query.
            Map.entry("propose_query", false),
            // Only offers the user a button: the user applies the change to the editor, unsaved.
            Map.entry("propose_code_change", false)));

    private final AiService aiService = Mockito.mock(AiService.class);
    private final AiDao aiDao = Mockito.mock(AiDao.class);
    private final BuiltInTools builtInTools = Guice.createInjector(new BuiltInToolsModule(), new AbstractModule() {
        @Override
        protected void configure() {
            bind(ChatWorkspace.class).toInstance(Mockito.mock(ChatWorkspace.class));
            bind(AiService.class).toInstance(aiService);
            bind(AiMemoryService.class).toInstance(Mockito.mock(AiMemoryService.class));
            bind(QueryService.class).toInstance(Mockito.mock(QueryService.class));
            bind(ResourceStore.class).toInstance(Mockito.mock(ResourceStore.class));
            bind(Store.class).toInstance(Mockito.mock(Store.class));
            bind(SecurityContext.class).toInstance(Mockito.mock(SecurityContext.class));
            bind(TableSummariser.class).toInstance(Mockito.mock(TableSummariser.class));
            bind(AskStroomAiConfig.class).toInstance(new AskStroomAiConfig());
            bind(McpElicitations.class).toInstance(Mockito.mock(McpElicitations.class));
            bind(AiChatRunManager.class).toInstance(Mockito.mock(AiChatRunManager.class));
            bind(ExplorerNodeService.class).toInstance(Mockito.mock(ExplorerNodeService.class));
            bind(DocDependencyService.class).toInstance(Mockito.mock(DocDependencyService.class));
            bind(WordListProvider.class).toInstance(Mockito.mock(WordListProvider.class));
            bind(AiAuditLog.class).toInstance(Mockito.mock(AiAuditLog.class));
            bind(TabSnapshots.class).toInstance(Mockito.mock(TabSnapshots.class));
            bind(TableDownloader.class).toInstance(Mockito.mock(TableDownloader.class));
            bind(DataSourceProviderRegistry.class).toInstance(Mockito.mock(DataSourceProviderRegistry.class));
            bind(DashboardService.class).toInstance(Mockito.mock(DashboardService.class));
            bind(TableAnalysisConfig.class).toInstance(new TableAnalysisConfig());
            MapBinder.newMapBinder(binder(), DocumentTypeName.class, DocumentActionHandler.class);
        }
    }).getInstance(BuiltInTools.class);

    @ParameterizedTest
    @EnumSource(AiAgentMode.class)
    void eachModeIsOfferedTheRightBuiltInTools(final AiAgentMode mode) {
        // Every bound tool has been classified.
        assertThat(builtInTools.toolMap().keySet().stream().map(ToolSpecification::name))
                .containsExactlyInAnyOrderElementsOf(CHANGES_THINGS.keySet());
        Mockito.when(aiDao.getAgentMode(1)).thenReturn(mode);
        Mockito.when(aiService.storeMessage(Mockito.anyInt(), Mockito.any(), Mockito.anyString()))
                .thenAnswer(invocation -> new AiChatMessage(1000, 1, 0L, invocation.getArgument(1), null,
                        invocation.getArgument(2), 1, null));
        final ToolConsent toolConsent = Mockito.mock(ToolConsent.class);
        final McpAgentChat agentChat = new McpAgentChat(aiService, builtInTools,
                Mockito.mock(AiChatRunManager.class), Mockito.mock(WorkspaceStore.class),
                Mockito.mock(ContextUsageTracker.class), toolConsent, Mockito.mock(AiAuditLog.class),
                new ModelStreamer(aiService), new ToolGroups.Activations(() -> aiDao), AiConfig::new, aiDao,
                new UserActions(Mockito.mock(UserActionLookup.class)));
        final List<ChatRequest> requests = new ArrayList<>();
        final ChatModel model = new ChatModel() {
            @Override
            public ChatResponse doChat(final ChatRequest chatRequest) {
                requests.add(chatRequest);
                return ChatResponse.builder().aiMessage(AiMessage.from("an answer")).build();
            }
        };

        agentChat.chat(model, null, 1, List.of(new SystemMessage("system"), new UserMessage("q")),
                McpToolset.EMPTY, 99, new AtomicBoolean(false), Mockito.mock(TaskContext.class), 1,
                new StringBuilder(), 0, ContextBudget.UNKNOWN);

        final Set<String> offered = requests.getFirst().toolSpecifications().stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toCollection(TreeSet::new));
        final Set<String> expected = CHANGES_THINGS.entrySet().stream()
                .filter(entry -> !mode.isReadOnly() || !entry.getValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(TreeSet::new));
        assertThat(offered).isEqualTo(expected);
    }
}
