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
import stroom.ai.api.CodeChangeStepper;
import stroom.ai.api.ElasticEql;
import stroom.ai.api.ElasticEsql;
import stroom.ai.api.ElasticIndexTemplates;
import stroom.ai.api.TableSummariser;
import stroom.ai.impl.memory.AiMemoryService;
import stroom.ai.impl.tools.BuiltInTools;
import stroom.ai.impl.tools.BuiltInToolsModule;
import stroom.ai.impl.tools.StroomQlSources;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.WorkspaceArguments;
import stroom.ai.impl.workspace.WorkspaceStore;
import stroom.ai.shared.AiAgentMode;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.TableAnalysisConfig;
import stroom.cluster.api.ClusterNodeManager;
import stroom.config.global.api.GlobalConfigReader;
import stroom.dashboard.impl.DashboardService;
import stroom.dashboard.impl.script.ScriptStore;
import stroom.dashboard.impl.visualisation.VisualisationStore;
import stroom.data.retention.api.DataRetentionRulesProvider;
import stroom.data.store.api.AttributeMapFactory;
import stroom.data.store.api.DataService;
import stroom.data.store.api.FsVolumeStates;
import stroom.data.store.api.Store;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.docstore.api.ContentIndex;
import stroom.docstore.api.ContentIndexable;
import stroom.docstore.api.DocDependencyService;
import stroom.docstore.api.DocFinder;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.feed.api.FeedProperties;
import stroom.index.api.IndexVolumeStates;
import stroom.index.shared.IndexResource;
import stroom.job.api.JobStates;
import stroom.meta.api.MetaService;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.pipeline.cache.SchemaPool;
import stroom.pipeline.stepping.SteppingService;
import stroom.pipeline.xmlschema.XmlSchemaStore;
import stroom.pipeline.xslt.XsltStore;
import stroom.processor.api.ProcessingDiagnostics;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorTaskService;
import stroom.query.api.datasource.DataSourceProvider;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.impl.QueryService;
import stroom.resource.api.ResourceStore;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.shared.TaskResource;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
            Map.entry("esql_query", false),
            Map.entry("sequence_query", false),
            Map.entry("summarise_column", false),
            Map.entry("task_list", false),
            Map.entry("ask_user", false),
            Map.entry("fetch_events", false),
            Map.entry("search_events", false),
            Map.entry("document_info", false),
            Map.entry("read_document", false),
            Map.entry("document_documentation", false),
            Map.entry("folder_contents", false),
            Map.entry("get_instructions", false),
            // Saves its snapshot of the tab's results to the workspace, which querying needs.
            Map.entry("current_results", false),
            Map.entry("list_data_sources", false),
            Map.entry("describe_data_source", false),
            Map.entry("validate_stroomql", false),
            // Only offers the user a button: the user creates the query.
            Map.entry("propose_query", false),
            // Only offers the user a button: the user creates the dashboard.
            Map.entry("propose_dashboard", false),
            // Only offers the user a button: the user applies the change to the Query's editor, unsaved.
            Map.entry("propose_query_change", false),
            Map.entry("list_visualisations", false),
            // Only offers the user a button: the user creates the visualisation, added to the dashboard unsaved.
            Map.entry("propose_visualisation", false),
            // Only offers the user a button: the user applies the change to the editor, unsaved.
            Map.entry("propose_code_change", false),
            Map.entry("stream_meta", false),
            Map.entry("processor_tasks", false),
            Map.entry("explain_processing", false),
            Map.entry("explain_retention", false),
            Map.entry("server_tasks", false),
            Map.entry("volumes", false),
            Map.entry("instruction_evidence", false),
            // Only offers the user a button: the user applies the change to the editor (or creates the doc).
            Map.entry("propose_instructions", false),
            Map.entry("document_links", false),
            Map.entry("find_in_content", false),
            Map.entry("xslt_lookups", false),
            // Only offers the user a button: the user applies the change to the dashboard, unsaved.
            Map.entry("propose_dashboard_change", false),
            // Only tries an extraction on events, by stepping: it changes nothing.
            Map.entry("preview_extraction", false),
            // Only offers the user a button: the user confirms what is written.
            Map.entry("propose_extraction_pipeline", false),
            // Only offers the user a button: the user applies the change to the Documentation tab, unsaved.
            Map.entry("propose_documentation_change", false),
            Map.entry("config_properties", false),
            Map.entry("elastic_index_templates", false)));

    private static final DocRef ELASTIC_INDEX = new DocRef(ElasticIndexDoc.TYPE, "elastic-uuid", "Events");
    private static final String ACTIVATE_SEQUENCE_HUNTING = ToolGroups.ACTIVATE_PREFIX
                                                           + BuiltInToolGroups.SEQUENCE_HUNTING;

    private final AiService aiService = Mockito.mock(AiService.class);
    private final AiDao aiDao = Mockito.mock(AiDao.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final DataSourceProviderRegistry dataSourceRegistry = Mockito.mock(DataSourceProviderRegistry.class);
    private final DataSourceProvider elasticProvider = Mockito.mock(DataSourceProvider.class);
    private final BuiltInTools builtInTools = Guice.createInjector(new BuiltInToolsModule(), new AbstractModule() {
        @Override
        protected void configure() {
            bind(ChatWorkspace.class).toInstance(Mockito.mock(ChatWorkspace.class));
            bind(AiService.class).toInstance(aiService);
            bind(AiMemoryService.class).toInstance(Mockito.mock(AiMemoryService.class));
            bind(QueryService.class).toInstance(Mockito.mock(QueryService.class));
            bind(DashboardBuilder.class).toInstance(Mockito.mock(DashboardBuilder.class));
            bind(StroomQlSources.class).toInstance(Mockito.mock(StroomQlSources.class));
            bind(ResourceStore.class).toInstance(Mockito.mock(ResourceStore.class));
            bind(Store.class).toInstance(Mockito.mock(Store.class));
            bind(SecurityContext.class).toInstance(securityContext);
            bind(TableSummariser.class).toInstance(Mockito.mock(TableSummariser.class));
            bind(AskStroomAiConfig.class).toInstance(new AskStroomAiConfig());
            bind(McpElicitations.class).toInstance(Mockito.mock(McpElicitations.class));
            bind(AiChatRunManager.class).toInstance(Mockito.mock(AiChatRunManager.class));
            bind(ExplorerNodeService.class).toInstance(Mockito.mock(ExplorerNodeService.class));
            bind(ExplorerService.class).toInstance(Mockito.mock(ExplorerService.class));
            bind(DocDependencyService.class).toInstance(Mockito.mock(DocDependencyService.class));
            bind(WordListProvider.class).toInstance(Mockito.mock(WordListProvider.class));
            bind(AiAuditLog.class).toInstance(Mockito.mock(AiAuditLog.class));
            bind(TabSnapshots.class).toInstance(Mockito.mock(TabSnapshots.class));
            bind(TableDownloader.class).toInstance(Mockito.mock(TableDownloader.class));
            bind(DataSourceProviderRegistry.class).toInstance(dataSourceRegistry);
            bind(IndexResource.class).toInstance(Mockito.mock(IndexResource.class));
            bind(DashboardService.class).toInstance(Mockito.mock(DashboardService.class));
            bind(TableAnalysisConfig.class).toInstance(new TableAnalysisConfig());
            MapBinder.newMapBinder(binder(), DocumentTypeName.class, DocumentActionHandler.class);
            bind(MetaService.class).toInstance(Mockito.mock(MetaService.class));
            bind(ProcessorFilterService.class).toInstance(Mockito.mock(ProcessorFilterService.class));
            bind(ProcessorTaskService.class).toInstance(Mockito.mock(ProcessorTaskService.class));
            bind(ProcessingDiagnostics.class).toInstance(Mockito.mock(ProcessingDiagnostics.class));
            bind(JobStates.class).toInstance(Mockito.mock(JobStates.class));
            bind(FsVolumeStates.class).toInstance(Mockito.mock(FsVolumeStates.class));
            bind(IndexVolumeStates.class).toInstance(Mockito.mock(IndexVolumeStates.class));
            bind(DataRetentionRulesProvider.class).toInstance(Mockito.mock(DataRetentionRulesProvider.class));
            bind(ClusterNodeManager.class).toInstance(Mockito.mock(ClusterNodeManager.class));
            bind(TaskResource.class).toInstance(Mockito.mock(TaskResource.class));
            bind(TaskContextFactory.class).toInstance(new SimpleTaskContextFactory());
            bind(NodeService.class).toInstance(Mockito.mock(NodeService.class));
            bind(ResultStoreManager.class).toInstance(Mockito.mock(ResultStoreManager.class));
            bind(CodeChangeStepper.class).toInstance(Mockito.mock(CodeChangeStepper.class));
            bind(ElasticIndexTemplates.class).toInstance(Mockito.mock(ElasticIndexTemplates.class));
            bind(ElasticEsql.class).toInstance(Mockito.mock(ElasticEsql.class));
            bind(ElasticEql.class).toInstance(Mockito.mock(ElasticEql.class));
            bind(AttributeMapFactory.class).toInstance(Mockito.mock(AttributeMapFactory.class));
            bind(DataService.class).toInstance(Mockito.mock(DataService.class));
            bind(FeedProperties.class).toInstance(Mockito.mock(FeedProperties.class));
            bind(DocFinder.class).toInstance(Mockito.mock(DocFinder.class));
            bind(XmlSchemaStore.class).toInstance(Mockito.mock(XmlSchemaStore.class));
            bind(ScriptStore.class).toInstance(Mockito.mock(ScriptStore.class));
            bind(XsltStore.class).toInstance(Mockito.mock(XsltStore.class));
            bind(SteppingService.class).toInstance(Mockito.mock(SteppingService.class));
            bind(VisualisationStore.class).toInstance(Mockito.mock(VisualisationStore.class));
            bind(DocumentPermissionService.class).toInstance(Mockito.mock(DocumentPermissionService.class));
            bind(SchemaPool.class).toInstance(Mockito.mock(SchemaPool.class));
            bind(ContentIndex.class).toInstance(Mockito.mock(ContentIndex.class));
            bind(StroomEventLoggingService.class).toInstance(Mockito.mock(StroomEventLoggingService.class));
            bind(GlobalConfigReader.class).toInstance(Mockito.mock(GlobalConfigReader.class));
            bind(NodeInfo.class).toInstance(Mockito.mock(NodeInfo.class));
            bind(ExecutorProvider.class).toInstance(Mockito.mock(ExecutorProvider.class));
            Multibinder.newSetBinder(binder(), ContentIndexable.class);
        }
    }).getInstance(BuiltInTools.class);

    @BeforeEach
    void setUp() {
        // The user can use an Elastic Index, so the tools offered only with one are offered.
        Mockito.when(dataSourceRegistry.getDataSourceProvider(ElasticIndexDoc.TYPE))
                .thenReturn(Optional.of(elasticProvider));
        Mockito.when(elasticProvider.getDataSourceDocRefs()).thenReturn(List.of(ELASTIC_INDEX));
        Mockito.when(securityContext.hasDocumentPermission(ELASTIC_INDEX, DocumentPermission.USE)).thenReturn(true);
        Mockito.when(aiService.storeMessage(Mockito.anyInt(), Mockito.any(), Mockito.anyString()))
                .thenAnswer(invocation -> new AiChatMessage(1000, invocation.getArgument(0), 0L,
                        invocation.getArgument(1), null, invocation.getArgument(2), 1, null));
    }

    @ParameterizedTest
    @EnumSource(AiAgentMode.class)
    void eachModeIsOfferedTheRightBuiltInTools(final AiAgentMode mode) {
        // Every bound tool has been classified.
        assertThat(builtInTools.toolMap().keySet().stream().map(ToolSpecification::name))
                .containsExactlyInAnyOrderElementsOf(CHANGES_THINGS.keySet());
        Mockito.when(aiDao.getAgentMode(1)).thenReturn(mode);

        final Set<String> offered = offered(firstRequest(1, "system"));
        // A tool in a group is offered as the tool that turns its group on.
        final Set<String> expected = OfferedToolNames.withNoGroupsOn(CHANGES_THINGS.entrySet().stream()
                .filter(entry -> !mode.isReadOnly() || !entry.getValue())
                .map(Map.Entry::getKey)
                .toList());
        assertThat(offered).isEqualTo(expected);
    }

    @Test
    void eqlSequenceAndItsGuidanceAreOnlyForAUserWhoCanUseAnElasticIndex() {
        // Its definition is large, and most users of most chats have no Elastic Index to hunt sequences in: its
        // group's activator isn't offered without one, and the guidance on it is left out.
        final String systemPrompt = "system\n\n" + ContextAssembler.TOOL_GUIDANCE;
        final ChatRequest withIndex = firstRequest(1, systemPrompt);
        Mockito.when(securityContext.hasDocumentPermission(ELASTIC_INDEX, DocumentPermission.USE)).thenReturn(false);
        final ChatRequest withoutIndex = firstRequest(2, systemPrompt);

        assertThat(offered(withIndex)).contains(ACTIVATE_SEQUENCE_HUNTING, "esql_query")
                .doesNotContain("sequence_query");
        assertThat(systemPrompt(withIndex)).isEqualTo(systemPrompt)
                .contains(ContextAssembler.SEQUENCE_GUIDANCE);
        assertThat(offered(withoutIndex)).doesNotContain("sequence_query", ACTIVATE_SEQUENCE_HUNTING)
                .contains("esql_query");
        assertThat(systemPrompt(withoutIndex)).doesNotContain("sequence_query").doesNotContain("sequence_hunting")
                .contains("For totals, top N and grouped questions on an Elastic Index, use esql_query")
                .contains("- \"Query\" can mean two things.");
    }

    @Test
    void theElasticHuntingActivatorSaysWhatItIsForAndNamesEqlSequence() {
        final ToolSpecification activator = firstRequest(1, "system").toolSpecifications().stream()
                .filter(specification -> specification.name().equals(ACTIVATE_SEQUENCE_HUNTING))
                .findFirst()
                .orElseThrow();

        assertThat(activator.description()).isEqualTo("Turns on the 'sequence_hunting' group of 1 tool, to hunt "
                                                       + "across Elastic Indexes for ordered sequences of events (X, "
                                                       + "then Y, by the same user or host, within N; N in a row; X "
                                                       + "and then not Y), so you can call it from your next step: "
                                                       + "sequence_query. Turn on a group only when you need its "
                                                       + "tools.");
    }

    @Test
    void turningOnElasticHuntingOffersEqlSequenceFromTheNextStep() {
        final List<ChatRequest> requests = requests(1, "system",
                AiMessage.from(List.of(ToolExecutionRequest.builder()
                        .id("1")
                        .name(ACTIVATE_SEQUENCE_HUNTING)
                        .arguments("{}")
                        .build())),
                AiMessage.from("an answer"));

        assertThat(requests).hasSize(2);
        assertThat(offered(requests.get(0))).contains(ACTIVATE_SEQUENCE_HUNTING).doesNotContain("sequence_query");
        assertThat(offered(requests.get(1))).contains("sequence_query").doesNotContain(ACTIVATE_SEQUENCE_HUNTING);
    }

    @Test
    void elasticHuntingStaysOnWhileTheChatsLatestQuestionsUseIt() {
        // The latest question called sequence_query, so its group is on as the next run starts.
        Mockito.when(aiDao.getMessages(1)).thenReturn(List.of(
                new AiChatMessage(1, 1, 0L, AiMessageType.USER_MESSAGE, null, "q", 10, 0),
                new AiChatMessage(2, 1, 0L, AiMessageType.TOOL_CALL, null,
                        "{\"callId\":\"a\",\"tool\":\"sequence_query\"}", 10, 0)));

        assertThat(offered(firstRequest(1, "system"))).contains("sequence_query")
                .doesNotContain(ACTIVATE_SEQUENCE_HUNTING);
        // Three questions on, it is off again.
        Mockito.when(aiDao.getMessages(1)).thenReturn(List.of(
                new AiChatMessage(1, 1, 0L, AiMessageType.USER_MESSAGE, null, "q", 10, 0),
                new AiChatMessage(2, 1, 0L, AiMessageType.TOOL_CALL, null,
                        "{\"callId\":\"a\",\"tool\":\"sequence_query\"}", 10, 0),
                new AiChatMessage(3, 1, 0L, AiMessageType.USER_MESSAGE, null, "q2", 11, 0),
                new AiChatMessage(4, 1, 0L, AiMessageType.USER_MESSAGE, null, "q3", 12, 0)));

        assertThat(offered(firstRequest(1, "system"))).contains(ACTIVATE_SEQUENCE_HUNTING)
                .doesNotContain("sequence_query");
    }

    @Test
    void aChatKeepsElasticHuntingOnceOfferedAndOffersItOnceTheUserHasAnElasticIndex() {
        // Kept, so the tools (and the start of every request) stay the same from question to question; the call
        // itself checks Use on each doc.
        assertThat(offered(firstRequest(1, "system"))).contains(ACTIVATE_SEQUENCE_HUNTING);
        Mockito.when(securityContext.hasDocumentPermission(ELASTIC_INDEX, DocumentPermission.USE)).thenReturn(false);
        assertThat(offered(firstRequest(1, "system"))).contains(ACTIVATE_SEQUENCE_HUNTING);

        // Not found usable, it is looked for again as each run starts: e.g. once an index is made in the chat.
        assertThat(offered(firstRequest(2, "system"))).doesNotContain(ACTIVATE_SEQUENCE_HUNTING);
        Mockito.when(securityContext.hasDocumentPermission(ELASTIC_INDEX, DocumentPermission.USE)).thenReturn(true);
        assertThat(offered(firstRequest(2, "system"))).contains(ACTIVATE_SEQUENCE_HUNTING);
    }

    @Test
    void aUserWithNoElasticIndexStoreIsntOfferedEqlSequence() {
        Mockito.when(dataSourceRegistry.getDataSourceProvider(ElasticIndexDoc.TYPE)).thenReturn(Optional.empty());

        assertThat(offered(firstRequest(1, "system"))).doesNotContain("sequence_query", ACTIVATE_SEQUENCE_HUNTING);
    }

    // The first request a run of the chat makes of the model, with the system prompt given.
    private ChatRequest firstRequest(final int chatId, final String systemPrompt) {
        return requests(chatId, systemPrompt, AiMessage.from("an answer")).getFirst();
    }

    // The requests a run of the chat makes of the model, which replies with each message in turn.
    private List<ChatRequest> requests(final int chatId, final String systemPrompt, final AiMessage... replies) {
        final ToolConsent toolConsent = Mockito.mock(ToolConsent.class);
        final AiChatRunManager runManager = Mockito.mock(AiChatRunManager.class);
        final McpAgentChat agentChat = new McpAgentChat(aiService, builtInTools,
                runManager, Mockito.mock(WorkspaceStore.class),
                Mockito.mock(ContextUsageTracker.class), ConsentGates.chat(toolConsent, aiService, runManager),
                Mockito.mock(AiAuditLog.class),
                new ModelStreamer(aiService), new ToolGroups.Activations(() -> aiDao), new TurnSummaries(),
                AiConfig::new, aiDao,
                new UserActions(Mockito.mock(UserActionLookup.class)),
                new WorkspaceArguments(Mockito.mock(ChatWorkspace.class)), ToolGrants::all);
        final List<ChatRequest> requests = new ArrayList<>();
        final ChatModel model = new ChatModel() {
            @Override
            public ChatResponse doChat(final ChatRequest chatRequest) {
                requests.add(chatRequest);
                return ChatResponse.builder().aiMessage(replies[Math.min(requests.size(), replies.length) - 1])
                        .build();
            }
        };

        agentChat.chat(model, null, chatId, List.of(new SystemMessage(systemPrompt), new UserMessage("q")),
                McpToolset.EMPTY, 99, new AtomicBoolean(false), Mockito.mock(TaskContext.class), 1,
                new StringBuilder(), 0, ContextBudget.UNKNOWN);
        return requests;
    }

    private static Set<String> offered(final ChatRequest request) {
        return request.toolSpecifications().stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static String systemPrompt(final ChatRequest request) {
        return ((SystemMessage) request.messages().getFirst()).text();
    }
}
