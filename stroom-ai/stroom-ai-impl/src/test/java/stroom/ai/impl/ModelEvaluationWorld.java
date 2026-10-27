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
import stroom.ai.api.CodeChangeStepper;
import stroom.ai.api.ElasticIndexTemplates;
import stroom.ai.api.TableSummariser;
import stroom.ai.impl.memory.AiMemoryService;
import stroom.ai.impl.tools.BuiltInTools;
import stroom.ai.impl.tools.BuiltInToolsModule;
import stroom.ai.shared.AiAgentMode;
import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AiTabSearch;
import stroom.ai.shared.AiTabSnapshot;
import stroom.ai.shared.AiTabSnapshotPart;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.DashboardTableContext;
import stroom.ai.shared.TableAnalysisConfig;
import stroom.cluster.api.ClusterNodeManager;
import stroom.config.global.api.GlobalConfigReader;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.OverrideValue;
import stroom.dashboard.impl.DashboardService;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.data.store.api.AttributeMapFactory;
import stroom.data.store.api.DataService;
import stroom.data.store.api.Store;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.docstore.api.ContentIndex;
import stroom.docstore.api.ContentIndexable;
import stroom.docstore.api.DocDependencyService;
import stroom.docstore.api.DocFinder;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.entity.shared.ExpressionCriteria;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.feed.api.FeedProperties;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.job.api.JobStates;
import stroom.meta.api.MetaService;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.openai.shared.McpToolServerConfig;
import stroom.pipeline.cache.SchemaPool;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.xmlschema.XmlSchemaStore;
import stroom.processor.api.ProcessingDiagnostics;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.FetchProcessorRequest;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.processor.shared.QueryData;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.QueryKey;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.impl.QueryService;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.shared.TaskId;
import stroom.task.shared.TaskProgress;
import stroom.task.shared.TaskProgressResponse;
import stroom.task.shared.TaskResource;
import stroom.util.io.TempDirProvider;
import stroom.util.json.JsonUtil;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult.Action;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

/// A small Stroom for [TestModelEvaluation] to run the real agent loop in: the real built-in tools, chat
/// workspace, tab snapshots, context assembly and consent, over mocks of Stroom's services that hold a fixed set of
/// documents, processor tasks, server tasks, system properties and query results, and one fake MCP server with an
/// index template tool that asks for review. No database is needed.
///
/// The documents are all in the folder `/Events`: the feeds `WINDOWS-EVENTS`, `LINUX-EVENTS` and `DNS-EVENTS`,
/// the pipeline `Event processing` (whose processor filter selects the first two feeds), its XSLT, the index
/// `Events index` and the dashboard `Logons`, whose table `Events` searches the index.
final class ModelEvaluationWorld {

    static final int CHAT_ID = 1;
    static final String USER_UUID = "eval-user-uuid";

    static final DocRef FOLDER = new DocRef(ExplorerConstants.FOLDER_TYPE, "folder-events-uuid", "Events");
    static final DocRef WINDOWS_FEED = new DocRef(FeedDoc.TYPE, "feed-windows-uuid", "WINDOWS-EVENTS");
    static final DocRef LINUX_FEED = new DocRef(FeedDoc.TYPE, "feed-linux-uuid", "LINUX-EVENTS");
    static final DocRef DNS_FEED = new DocRef(FeedDoc.TYPE, "feed-dns-uuid", "DNS-EVENTS");
    static final DocRef PIPELINE = new DocRef(PipelineDoc.TYPE, "pipeline-uuid", "Event processing");
    static final DocRef XSLT = new DocRef(XsltDoc.TYPE, "xslt-uuid", "Event translation");
    static final DocRef INDEX = new DocRef("Index", "index-uuid", "Events index");
    static final DocRef DASHBOARD = new DocRef(DashboardDoc.TYPE, "dashboard-uuid", "Logons");
    static final DocRef FILTER = new DocRef(ProcessorFilter.ENTITY_TYPE, "filter-uuid", null);
    /// The fake MCP server's index template tool.
    static final String INDEX_TEMPLATE_TOOL = "create_index_template";

    /// The dashboard's settings, as the tab snapshot gives them.
    private static final String DASHBOARD_SETTINGS = """
            Time range: last 24 hours
            ## Query 'Query' (id query-1)
            Data source: Index 'Events index' (uuid index-uuid)
            ## Table 'Events' (id table-1)
            - EventTime
            - UserId
            - EventType
            - Host
            """;
    /// What every StroomQL query returns, as the download gives it.
    private static final String QUERY_RESULTS = """
            | UserId | Count |
            | --- | --- |
            | alice | 42 |
            | bob | 17 |
            | carol | 5 |
            """;
    private static final long NOW = System.currentTimeMillis();
    private static final long MINUTE = Duration.ofMinutes(1).toMillis();

    private final Path tempDir;
    private final Set<AppPermission> appPermissions;
    private final List<AiChatMessage> messages = new CopyOnWriteArrayList<>();
    private final AtomicInteger nextMessageId = new AtomicInteger(1);
    private final List<DocRef> documents = List.of(WINDOWS_FEED, LINUX_FEED, DNS_FEED, PIPELINE, XSLT, INDEX,
            DASHBOARD);
    private final List<Dependency> edges = List.of(
            new Dependency(FILTER, WINDOWS_FEED, true),
            new Dependency(FILTER, LINUX_FEED, true),
            new Dependency(FILTER, PIPELINE, true),
            new Dependency(PIPELINE, XSLT, true),
            new Dependency(DASHBOARD, INDEX, true));
    private final InMemoryWorkspaceDao workspaceDao = new InMemoryWorkspaceDao();
    private final Injector injector;

    /// @param tempDir        A folder for the workspace's files.
    /// @param appPermissions The application permissions the user has.
    ModelEvaluationWorld(final Path tempDir, final Collection<AppPermission> appPermissions) {
        this.tempDir = tempDir;
        this.appPermissions = Set.copyOf(appPermissions);
        final AiService aiService = aiService();
        final AiDao aiDao = Mockito.mock(AiDao.class);
        Mockito.when(aiDao.getAgentMode(Mockito.anyInt())).thenReturn(AiAgentMode.AGENT);
        final McpElicitations elicitations = Mockito.mock(McpElicitations.class);
        // A question for the user ends the run: the report shows what was asked.
        Mockito.when(elicitations.askUser(Mockito.anyInt(), Mockito.anyString(), Mockito.any(),
                Mockito.anyString(), Mockito.any())).thenReturn(new ElicitResult(Action.CANCEL, null, null));
        final SecurityContext securityContext = securityContext();
        final ExplorerNodeService explorerNodeService = explorerNodeService();
        final Executor executor = runnable -> Thread.ofVirtual().start(runnable);
        final ExecutorProvider executorProvider = Mockito.mock(ExecutorProvider.class);
        Mockito.when(executorProvider.get()).thenReturn(executor);
        injector = Guice.createInjector(new BuiltInToolsModule(), new AbstractModule() {
            @Override
            protected void configure() {
                bind(AiService.class).toInstance(aiService);
                bind(AiDao.class).toInstance(aiDao);
                bind(AiWorkspaceDao.class).toInstance(workspaceDao);
                bind(AiToolApprovalDao.class).toInstance(Mockito.mock(AiToolApprovalDao.class));
                bind(AiConfig.class).toProvider(AiConfig::new);
                bind(TempDirProvider.class).toInstance(() -> tempDir);
                bind(AiMemoryService.class).toInstance(Mockito.mock(AiMemoryService.class));
                bind(AiChatRunManager.class).toInstance(Mockito.mock(AiChatRunManager.class));
                bind(ContextUsageTracker.class).toInstance(Mockito.mock(ContextUsageTracker.class));
                bind(McpElicitations.class).toInstance(elicitations);
                bind(UserActionLookup.class).toInstance(Mockito.mock(UserActionLookup.class));
                bind(AskStroomAiConfig.class).toInstance(new AskStroomAiConfig());
                bind(TableAnalysisConfig.class).toInstance(new TableAnalysisConfig());
                bind(SecurityContext.class).toInstance(securityContext);
                bind(StroomEventLoggingService.class).toInstance(Mockito.mock(StroomEventLoggingService.class));
                bind(ExplorerNodeService.class).toInstance(explorerNodeService);
                bind(ExplorerService.class).toInstance(Mockito.mock(ExplorerService.class));
                bind(DocDependencyService.class).toInstance(dependencyService());
                bind(DocFinder.class).toInstance(docFinder());
                bind(XmlSchemaStore.class).toInstance(Mockito.mock(XmlSchemaStore.class));
                bind(SchemaPool.class).toInstance(Mockito.mock(SchemaPool.class));
                bind(ProcessorFilterService.class).toInstance(processorFilterService());
                bind(ProcessorTaskService.class).toInstance(processorTaskService());
                bind(ProcessingDiagnostics.class).toInstance(Mockito.mock(ProcessingDiagnostics.class));
                bind(JobStates.class).toInstance(Mockito.mock(JobStates.class));
                bind(ClusterNodeManager.class).toInstance(Mockito.mock(ClusterNodeManager.class));
                bind(MetaService.class).toInstance(metaService());
                bind(TaskResource.class).toInstance(taskResource());
                bind(NodeService.class).toInstance(nodeService());
                bind(NodeInfo.class).toInstance(nodeInfo());
                bind(GlobalConfigReader.class).toInstance(globalConfigReader());
                bind(QueryService.class).toInstance(queryService());
                bind(ResourceStore.class).toInstance(resourceStore());
                bind(DataSourceProviderRegistry.class).toInstance(dataSourceProviderRegistry());
                bind(ExecutorProvider.class).toInstance(executorProvider);
                bind(TaskContextFactory.class).toInstance(new SimpleTaskContextFactory());
                bind(Store.class).toInstance(Mockito.mock(Store.class));
                bind(DashboardBuilder.class).toInstance(Mockito.mock(DashboardBuilder.class));
                bind(TableSummariser.class).toInstance(Mockito.mock(TableSummariser.class));
                bind(WordListProvider.class).toInstance(Mockito.mock(WordListProvider.class));
                bind(TableDownloader.class).toInstance(Mockito.mock(TableDownloader.class));
                bind(DashboardService.class).toInstance(Mockito.mock(DashboardService.class));
                bind(ResultStoreManager.class).toInstance(Mockito.mock(ResultStoreManager.class));
                bind(CodeChangeStepper.class).toInstance(Mockito.mock(CodeChangeStepper.class));
                bind(ElasticIndexTemplates.class).toInstance(Mockito.mock(ElasticIndexTemplates.class));
                bind(AttributeMapFactory.class).toInstance(Mockito.mock(AttributeMapFactory.class));
                bind(DataService.class).toInstance(Mockito.mock(DataService.class));
                bind(FeedProperties.class).toInstance(Mockito.mock(FeedProperties.class));
                bind(ContentIndex.class).toInstance(Mockito.mock(ContentIndex.class));
                Multibinder.newSetBinder(binder(), ContentIndexable.class);
                @SuppressWarnings("rawtypes") final MapBinder<DocumentTypeName, DocumentActionHandler> handlers =
                        MapBinder.newMapBinder(binder(), DocumentTypeName.class, DocumentActionHandler.class);
                for (final String type : List.of(FeedDoc.TYPE, PipelineDoc.TYPE, XsltDoc.TYPE, "Index",
                        DashboardDoc.TYPE)) {
                    handlers.addBinding(new DocumentTypeName(type)).toInstance(
                            Mockito.mock(DocumentActionHandler.class));
                }
            }
        });
    }

    /// Asks the agent a question, as the chat does: the question is stored (with a snapshot of the tab, if one is
    /// attached), the model is given the context the chat would give it, and the agent loop runs until the model
    /// answers, asks the user something, or needs the user's approval.
    ///
    /// @param model  The model.
    /// @param prompt The question.
    /// @param tab    The tab attached: `pipeline`, `dashboard`, or null for none.
    /// @return What happened.
    Run ask(final ChatModel model, final String prompt, final String tab) {
        final AiTabSnapshot snapshot = tabSnapshot(tab);
        final Integer snapshotId = snapshot == null
                ? null
                : injector.getInstance(TabSnapshots.class).store(CHAT_ID, snapshot).snapshotId();
        store(AiMessageType.USER_MESSAGE, snapshotId, prompt);
        final AskStroomAiRequest request = new AskStroomAiRequest(chat(), new AskStroomAiConfig(), null, prompt);
        final List<ChatMessage> context = new ArrayList<>(injector.getInstance(ContextAssembler.class)
                .assemble(request, CHAT_ID, Map.of(), List.copyOf(messages), CompactionRecord.NONE, true));
        final McpAgentChat agentChat = injector.getInstance(McpAgentChat.class);
        final long startMs = System.currentTimeMillis();
        String answer = null;
        String stoppedBecause = null;
        try (final McpToolset toolset = mcpToolset()) {
            final ChatResponse response = agentChat.chat(model, null, CHAT_ID, context, toolset, 0,
                    new AtomicBoolean(false), Mockito.mock(TaskContext.class), 1, new StringBuilder(), 0,
                    ContextBudget.UNKNOWN);
            answer = response == null
                    ? null
                    : response.aiMessage().text();
        } catch (final AiChatRunManager.WaitingForUserException e) {
            stoppedBecause = "Waiting for the user's approval: " + e.getMessage();
        } catch (final RuntimeException e) {
            stoppedBecause = "Failed: " + e;
        }
        return new Run(toolCalls(), answer, stoppedBecause, System.currentTimeMillis() - startMs);
    }

    /// @return The built-in tools, as the agent is offered them.
    BuiltInTools builtInTools() {
        return injector.getInstance(BuiltInTools.class);
    }

    private AiChat chat() {
        return new AiChat(CHAT_ID, NOW, NOW, USER_UUID, "Model evaluation");
    }

    // The tool calls made, in order, with what each returned.
    private List<ToolCall> toolCalls() {
        final Map<String, ToolResultRecord> results = new HashMap<>();
        for (final AiChatMessage message : messages) {
            if (message.getMessageType() == AiMessageType.TOOL_RESULT) {
                final ToolResultRecord result = JsonUtil.readValue(message.getMessage(), ToolResultRecord.class);
                results.put(result.callId(), result);
            }
        }
        final List<ToolCall> calls = new ArrayList<>();
        for (final AiChatMessage message : messages) {
            if (message.getMessageType() == AiMessageType.TOOL_CALL) {
                final ToolCallRecord call = JsonUtil.readValue(message.getMessage(), ToolCallRecord.class);
                final ToolResultRecord result = results.get(call.callId());
                calls.add(new ToolCall(call.tool(), call.arguments(), result == null
                        ? null
                        : result.text()));
            }
        }
        return calls;
    }

    private AiChatMessage store(final AiMessageType type, final Integer attachmentId, final String text) {
        final AiChatMessage message = new AiChatMessage(nextMessageId.getAndIncrement(), CHAT_ID,
                System.currentTimeMillis(), type, attachmentId, text, 1, null);
        messages.add(message);
        return message;
    }

    private AiService aiService() {
        final AiService aiService = Mockito.mock(AiService.class);
        Mockito.when(aiService.getChat(Mockito.anyInt())).thenAnswer(invocation -> chat());
        Mockito.when(aiService.getMessages(Mockito.anyInt())).thenAnswer(invocation -> List.copyOf(messages));
        Mockito.when(aiService.storeMessage(Mockito.anyInt(), Mockito.any(), Mockito.anyString()))
                .thenAnswer(invocation -> store(invocation.getArgument(1), null, invocation.getArgument(2)));
        Mockito.when(aiService.storeMessage(Mockito.anyInt(), Mockito.any(), Mockito.any(), Mockito.anyString()))
                .thenAnswer(invocation -> store(invocation.getArgument(1), invocation.getArgument(2),
                        invocation.getArgument(3)));
        Mockito.when(aiService.hasMessageOfType(Mockito.anyInt(), Mockito.any())).thenAnswer(invocation ->
                messages.stream().anyMatch(message -> message.getMessageType() == invocation.getArgument(1)));
        return aiService;
    }

    // The user can view and use every document, and has only the application permissions given.
    private SecurityContext securityContext() {
        final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.hasAppPermission(Mockito.any(AppPermission.class))).thenAnswer(invocation ->
                appPermissions.contains(invocation.<AppPermission>getArgument(0)));
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(DocRef.class),
                Mockito.any(DocumentPermission.class))).thenReturn(true);
        Mockito.when(securityContext.getUserRef()).thenReturn(UserRef.builder().uuid(USER_UUID)
                .subjectId("eval-user").displayName("eval-user").build());
        Mockito.when(securityContext.getUserIdentityForAudit()).thenReturn("eval-user");
        Mockito.when(securityContext.asUserResult(Mockito.<UserIdentity>any(), Mockito.any())).thenAnswer(invocation ->
                invocation.<Supplier<?>>getArgument(1).get());
        Mockito.when(securityContext.useAsReadResult(Mockito.any())).thenAnswer(invocation ->
                invocation.<Supplier<?>>getArgument(0).get());
        return securityContext;
    }

    private ExplorerNodeService explorerNodeService() {
        final ExplorerNodeService service = Mockito.mock(ExplorerNodeService.class);
        Mockito.when(service.getNodeByUuid(Mockito.anyString())).thenAnswer(invocation ->
                find(invocation.getArgument(0)).map(ModelEvaluationWorld::node));
        Mockito.when(service.getNode(Mockito.any())).thenAnswer(invocation ->
                find(invocation.<DocRef>getArgument(0).getUuid()).map(ModelEvaluationWorld::node));
        Mockito.when(service.getPath(Mockito.any())).thenAnswer(invocation -> FOLDER.equals(invocation.getArgument(0))
                ? List.of(ExplorerConstants.SYSTEM_NODE)
                : List.of(ExplorerConstants.SYSTEM_NODE, node(FOLDER)));
        Mockito.when(service.getParent(Mockito.any())).thenAnswer(invocation -> FOLDER.equals(invocation.getArgument(0))
                ? Optional.of(ExplorerConstants.SYSTEM_NODE)
                : Optional.of(node(FOLDER)));
        Mockito.when(service.getChildren(Mockito.any())).thenAnswer(invocation -> {
            final DocRef folder = invocation.getArgument(0);
            if (FOLDER.equals(folder)) {
                return documents.stream().map(ModelEvaluationWorld::node).toList();
            }
            return ExplorerConstants.SYSTEM_DOC_REF.equals(folder)
                    ? List.of(node(FOLDER))
                    : List.of();
        });
        Mockito.when(service.getNodesByName(Mockito.any(), Mockito.anyString())).thenAnswer(invocation ->
                FOLDER.getName().equalsIgnoreCase(invocation.getArgument(1))
                        ? List.of(node(FOLDER))
                        : documents.stream()
                                .filter(docRef -> docRef.getName().equalsIgnoreCase(invocation.getArgument(1)))
                                .map(ModelEvaluationWorld::node)
                                .toList());
        return service;
    }

    private Optional<DocRef> find(final String uuid) {
        if (FOLDER.getUuid().equals(uuid)) {
            return Optional.of(FOLDER);
        }
        return documents.stream().filter(docRef -> docRef.getUuid().equals(uuid)).findFirst();
    }

    private static ExplorerNode node(final DocRef docRef) {
        return ExplorerNode.builder().docRef(docRef).build();
    }

    // The dependency graph, answering the quick filter queries the tools make ("to:=uuid or from:=uuid").
    private DocDependencyService dependencyService() {
        final DocDependencyService service = Mockito.mock(DocDependencyService.class);
        Mockito.when(service.fetchDependencies(Mockito.any(), Mockito.any())).thenAnswer(invocation -> {
            final DependencyCriteria criteria = invocation.getArgument(0);
            final List<Dependency> found = new ArrayList<>();
            for (final String term : Objects.requireNonNullElse(criteria.getPartialName(), "").split(" or ")) {
                final String[] parts = term.split(":=");
                if (parts.length != 2) {
                    continue;
                }
                final boolean to = DependencyCriteria.FIELD_DEF_TO_UUID.getFilterQualifier().equals(parts[0]);
                edges.stream()
                        .filter(edge -> (to
                                ? edge.getTo()
                                : edge.getFrom()).getUuid().equals(parts[1]))
                        .forEach(found::add);
            }
            return new ResultPage<>(found);
        });
        return service;
    }

    private DocFinder docFinder() {
        final DocFinder docFinder = Mockito.mock(DocFinder.class);
        Mockito.when(docFinder.findByName(Mockito.any(), Mockito.anyString(), Mockito.anyBoolean()))
                .thenAnswer(invocation -> documents.stream()
                        .filter(docRef -> invocation.getArgument(0) == null
                                          || docRef.getType().equals(invocation.getArgument(0)))
                        .filter(docRef -> docRef.getName().equalsIgnoreCase(invocation.getArgument(1)))
                        .toList());
        Mockito.doCallRealMethod().when(docFinder).findByName(Mockito.any(), Mockito.anyString());
        return docFinder;
    }

    // The pipeline's one processor filter, selecting the Windows and Linux feeds.
    private static ProcessorFilter processorFilter() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(ExpressionTerm.builder()
                        .field("Feed")
                        .condition(Condition.IN)
                        .value(WINDOWS_FEED.getName() + "," + LINUX_FEED.getName())
                        .build())
                .addTerm(ExpressionTerm.builder().field("Type").condition(Condition.EQUALS).value("Raw Events")
                        .build())
                .build();
        return ProcessorFilter.builder()
                .id(1)
                .uuid(FILTER.getUuid())
                .pipelineUuid(PIPELINE.getUuid())
                .pipelineName(PIPELINE.getName())
                .enabled(true)
                .priority(10)
                .queryData(QueryData.builder().expression(expression).build())
                .build();
    }

    private ProcessorFilterService processorFilterService() {
        final ProcessorFilterService service = Mockito.mock(ProcessorFilterService.class);
        Mockito.when(service.fetchByUuid(FILTER.getUuid())).thenReturn(Optional.of(processorFilter()));
        final List<ProcessorListRow> rows = List.of(new ProcessorFilterRow(processorFilter()));
        Mockito.when(service.find(Mockito.any(FetchProcessorRequest.class)))
                .thenReturn(ResultPage.createUnboundedList(rows));
        return service;
    }

    // Processor tasks across three nodes: the Linux feed's fail on every node, one Windows task failed on node2.
    private ProcessorTaskService processorTaskService() {
        final ProcessorFilter filter = processorFilter();
        final List<ProcessorTask> tasks = new ArrayList<>();
        final List<String> nodes = List.of("node1", "node2", "node3");
        long id = 1;
        for (int i = 0; i < 12; i++) {
            tasks.add(task(id++, TaskStatus.FAILED, nodes.get(i % 3), LINUX_FEED, filter, NOW - i * 5 * MINUTE));
        }
        tasks.add(task(id++, TaskStatus.FAILED, "node2", WINDOWS_FEED, filter, NOW - 40 * MINUTE));
        for (int i = 0; i < 30; i++) {
            tasks.add(task(id++, TaskStatus.COMPLETE, nodes.get(i % 3), WINDOWS_FEED, filter, NOW - i * 2 * MINUTE));
        }
        final ProcessorTaskService service = Mockito.mock(ProcessorTaskService.class);
        Mockito.when(service.find(Mockito.any())).thenAnswer(invocation -> {
            final ExpressionCriteria criteria = invocation.getArgument(0);
            return new ResultPage<>(tasks.stream()
                    .filter(task -> matches(criteria.getExpression(), field -> taskValue(task, field)))
                    .sorted(Comparator.comparingLong(ProcessorTask::getId).reversed())
                    .toList());
        });
        Mockito.when(service.findSummary(Mockito.any())).thenAnswer(invocation -> {
            final ExpressionCriteria criteria = invocation.getArgument(0);
            final Map<List<Object>, Long> counts = new LinkedHashMap<>();
            tasks.stream()
                    .filter(task -> matches(criteria.getExpression(), field -> taskValue(task, field)))
                    .forEach(task -> counts.merge(List.of(task.getFeedName(), task.getStatus()), 1L, Long::sum));
            final List<ProcessorTaskSummary> summaries = new ArrayList<>();
            counts.forEach((key, count) -> summaries.add(new ProcessorTaskSummary(PIPELINE, (String) key.get(0), 10,
                    (TaskStatus) key.get(1), count)));
            return new ResultPage<>(summaries);
        });
        return service;
    }

    private static ProcessorTask task(final long id,
                                      final TaskStatus status,
                                      final String node,
                                      final DocRef feed,
                                      final ProcessorFilter filter,
                                      final long statusTimeMs) {
        return new ProcessorTask(id, 1, 1000 + id, null, node, feed.getName(), statusTimeMs - MINUTE, statusTimeMs,
                statusTimeMs - 30_000, statusTimeMs, status, filter);
    }

    private static String taskValue(final ProcessorTask task, final String field) {
        if (field.equals(ProcessorTaskFields.STATUS.getFldName())) {
            return task.getStatus().name();
        } else if (field.equals(ProcessorTaskFields.NODE_NAME.getFldName())) {
            return task.getNodeName();
        } else if (field.equals(ProcessorTaskFields.FEED.getFldName())) {
            return task.getFeedName();
        } else if (field.equals(ProcessorTaskFields.PIPELINE.getFldName())) {
            return task.getProcessorFilter().getPipelineUuid();
        } else if (field.equals(ProcessorTaskFields.PROCESSOR_FILTER_ID.getFldName())) {
            return String.valueOf(task.getProcessorFilter().getId());
        } else if (field.equals(ProcessorTaskFields.META_ID.getFldName())) {
            return String.valueOf(task.getMetaId());
        } else if (field.equals(ProcessorTaskFields.TASK_ID.getFldName())) {
            return String.valueOf(task.getId());
        } else if (field.equals(ProcessorTaskFields.STATUS_TIME_MS.getFldName())) {
            return String.valueOf(task.getStatusTimeMs());
        }
        // Not held here, so not filtered on.
        return null;
    }

    // True if the values match the expression, for the terms the tools make. A term on a value not held matches.
    private static boolean matches(final ExpressionItem item, final Function<String, String> values) {
        if (item == null) {
            return true;
        }
        if (item instanceof final ExpressionOperator operator) {
            final List<ExpressionItem> children = Objects.requireNonNullElse(operator.getChildren(), List.of());
            if (operator.getOp() == Op.NOT) {
                return children.isEmpty() || children.stream().noneMatch(child -> matches(child, values));
            }
            return operator.getOp() == Op.OR
                    ? children.isEmpty() || children.stream().anyMatch(child -> matches(child, values))
                    : children.stream().allMatch(child -> matches(child, values));
        }
        final ExpressionTerm term = (ExpressionTerm) item;
        final String value = values.apply(term.getField());
        if (value == null) {
            return true;
        }
        try {
            return switch (term.getCondition()) {
                case EQUALS -> term.getValue().equalsIgnoreCase(value);
                case NOT_EQUALS -> !term.getValue().equalsIgnoreCase(value);
                case IN -> Arrays.stream(term.getValue().split(","))
                        .anyMatch(option -> option.strip().equalsIgnoreCase(value));
                case IS_DOC_REF -> term.getDocRef() == null || term.getDocRef().getUuid().equals(value);
                case GREATER_THAN, GREATER_THAN_OR_EQUAL_TO -> Long.parseLong(value) >= Long.parseLong(
                        term.getValue());
                case LESS_THAN, LESS_THAN_OR_EQUAL_TO -> Long.parseLong(value) <= Long.parseLong(term.getValue());
                default -> true;
            };
        } catch (final NumberFormatException e) {
            return true;
        }
    }

    // No streams are held, so no Error streams are found.
    private static MetaService metaService() {
        final MetaService service = Mockito.mock(MetaService.class);
        Mockito.when(service.find(Mockito.any())).thenReturn(new ResultPage<>(List.of()));
        Mockito.when(service.findRows(Mockito.any())).thenReturn(new ResultPage<>(List.of()));
        return service;
    }

    // Server tasks: three users' searches, with their sub-tasks, and the data processors.
    private static TaskResource taskResource() {
        final Map<String, List<TaskProgress>> tasks = new HashMap<>();
        final TaskProgress aliceSearch = taskProgress(tasks, "node1", "1", null, "Search", "alice", 4 * MINUTE,
                "Searching 'Events index'");
        taskProgress(tasks, "node1", "2", aliceSearch, "Search Index Shard", "alice", 4 * MINUTE, "Shard 3");
        taskProgress(tasks, "node2", "3", aliceSearch, "Search Index Shard", "alice", 4 * MINUTE, "Shard 7");
        final TaskProgress bobSearch = taskProgress(tasks, "node2", "4", null, "Search", "bob", 2 * MINUTE,
                "Searching 'Events index'");
        taskProgress(tasks, "node2", "5", bobSearch, "Search Index Shard", "bob", 2 * MINUTE, "Shard 1");
        taskProgress(tasks, "node1", "6", null, "Search", "carol", 30_000, "Searching 'Events index'");
        taskProgress(tasks, "node1", "7", null, "Data Processor", "INTERNAL_PROCESSING_USER", 10 * MINUTE,
                "Processing stream 1001");
        taskProgress(tasks, "node2", "8", null, "Data Processor", "INTERNAL_PROCESSING_USER", 3 * MINUTE,
                "Processing stream 1002");
        final TaskResource resource = Mockito.mock(TaskResource.class);
        Mockito.when(resource.find(Mockito.anyString(), Mockito.any())).thenAnswer(invocation ->
                new TaskProgressResponse(tasks.getOrDefault(invocation.<String>getArgument(0), List.of()), List.of(),
                        null));
        return resource;
    }

    private static TaskProgress taskProgress(final Map<String, List<TaskProgress>> tasks,
                                             final String nodeName,
                                             final String id,
                                             final TaskProgress parent,
                                             final String name,
                                             final String user,
                                             final long ageMs,
                                             final String info) {
        final TaskProgress task = new TaskProgress(new TaskId(id, parent == null
                ? null
                : parent.getId()), name, info,
                UserRef.builder().uuid(user + "-uuid").subjectId(user).displayName(user).build(),
                "thread-" + id, nodeName, NOW - ageMs, NOW, null, null);
        tasks.computeIfAbsent(nodeName, k -> new ArrayList<>()).add(task);
        return task;
    }

    private static NodeService nodeService() {
        final NodeService service = Mockito.mock(NodeService.class);
        Mockito.when(service.findNodeNames(Mockito.any())).thenReturn(List.of("node1", "node2", "node3"));
        return service;
    }

    private static NodeInfo nodeInfo() {
        final NodeInfo nodeInfo = Mockito.mock(NodeInfo.class);
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn("node1");
        return nodeInfo;
    }

    // System properties, of which the default maximum results has been lowered in the database.
    private static GlobalConfigReader globalConfigReader() {
        final Map<String, ConfigProperty> properties = new LinkedHashMap<>();
        for (final ConfigProperty property : List.of(
                property("stroom.ui.defaultMaxResults", "String", "1000000,100,10,1",
                        "The default maximum number of search results that new dashboard tables will request.")
                        .databaseOverrideValue("500,100,10,1")
                        .updateUser("admin")
                        .updateTimeMs(NOW - Duration.ofDays(3).toMillis())
                        .build(),
                property("stroom.search.resultStore.maxSortedItems", "Integer", "500000",
                        "Maximum number of results that can be returned in a single page if the results are "
                        + "sorted.").build(),
                property("stroom.search.resultStore.map.trimmedSizeLimit", "Integer", "500000",
                        "The trimmed size of sorted results for on heap result stores.").build(),
                property("stroom.statistics.sql.search.maxResults", "Integer", "100000",
                        "The maximum number of records that can be returned from the statistics DB in a single "
                        + "query prior to aggregation").build(),
                property("stroom.ai.workspace.maxQueryRows", "Integer", "10000",
                        "The most rows a StroomQL query run by the agent (run_stroomql) may return.").build(),
                property("stroom.ui.htmlTitle", "String", "Stroom",
                        "The title to use for the application in the browser.").build())) {
            properties.put(property.getNameAsString(), property);
        }
        final GlobalConfigReader reader = Mockito.mock(GlobalConfigReader.class);
        Mockito.when(reader.list()).thenAnswer(invocation -> new ArrayList<>(properties.values()));
        Mockito.when(reader.fetch(Mockito.any())).thenAnswer(invocation ->
                Optional.ofNullable(properties.get(invocation.<PropertyPath>getArgument(0).toString())));
        Mockito.when(reader.fetchYamlOverrideValue(Mockito.any(), Mockito.anyString()))
                .thenReturn(OverrideValue.unSet(String.class));
        return reader;
    }

    private static ConfigProperty.Builder property(final String path,
                                                   final String type,
                                                   final String defaultValue,
                                                   final String description) {
        return ConfigProperty.builder()
                .name(path)
                .dataTypeName(type)
                .defaultValue(defaultValue)
                .description(description)
                .editable(true);
    }

    // The index's fields; every query reads the index, is valid, and returns the same rows.
    private QueryService queryService() {
        final QueryService service = Mockito.mock(QueryService.class);
        Mockito.when(service.findFields(Mockito.any())).thenReturn(new ResultPage<>(List.of(
                QueryField.createDate("EventTime"),
                QueryField.createText("UserId"),
                QueryField.createText("EventType"),
                QueryField.createText("Host"))));
        Mockito.when(service.getReferencedDataSource(Mockito.anyString())).thenReturn(Optional.of(INDEX));
        Mockito.when(service.validateQuery(Mockito.anyString())).thenAnswer(invocation ->
                new ValidateExpressionResult(true, null,
                        invocation.<String>getArgument(0).toLowerCase().contains("group by")));
        Mockito.when(service.search(Mockito.any())).thenReturn(new DashboardSearchResponse(null,
                new QueryKey("eval-query"), null, null, null, true, null, null));
        Mockito.when(service.downloadSearchResults(Mockito.any())).thenAnswer(invocation -> {
            Files.writeString(resultsFile(), QUERY_RESULTS, StandardCharsets.UTF_8);
            return new ResourceGeneration(new ResourceKey("eval-results", "results.md"), List.of());
        });
        return service;
    }

    private Path resultsFile() {
        return tempDir.resolve("query-results.md");
    }

    private ResourceStore resourceStore() {
        final ResourceStore store = Mockito.mock(ResourceStore.class);
        Mockito.when(store.getTempFile(Mockito.any())).thenAnswer(invocation -> resultsFile());
        return store;
    }

    private static DataSourceProviderRegistry dataSourceProviderRegistry() {
        final DataSourceProviderRegistry registry = Mockito.mock(DataSourceProviderRegistry.class);
        Mockito.when(registry.getDataSourceDocRefs()).thenReturn(List.of(INDEX));
        Mockito.when(registry.getDataSourceTypes()).thenReturn(Set.of(INDEX.getType()));
        Mockito.when(registry.findDataSourceByUuid(Mockito.anyString())).thenAnswer(invocation ->
                INDEX.getUuid().equals(invocation.getArgument(0))
                        ? Optional.of(INDEX)
                        : Optional.empty());
        Mockito.when(registry.getDataSourceProvider(Mockito.anyString())).thenReturn(Optional.empty());
        Mockito.when(registry.fetchDocumentation(Mockito.any())).thenReturn(Optional.empty());
        return registry;
    }

    private static AiTabSnapshot tabSnapshot(final String tab) {
        if (tab == null || tab.isBlank()) {
            return null;
        }
        return switch (tab) {
            case "pipeline" -> new AiTabSnapshot(PIPELINE, PIPELINE.getName(), "Structure", false, List.of(),
                    List.of(), null, "pipeline-tab");
            case "dashboard" -> new AiTabSnapshot(DASHBOARD, DASHBOARD.getName(), null, false,
                    List.of(new AiTabSnapshotPart(AiTabSnapshotPart.DASHBOARD, "text/markdown",
                            DASHBOARD_SETTINGS)),
                    List.of(new AiTabSearch("Events", "table-1", null,
                            new DashboardTableContext("Dashboard 'Logons'", null, DashboardSearchRequest.builder()
                                    .search(Search.builder().dataSourceRef(INDEX).build())
                                    .build()),
                            List.of("EventTime", "UserId", "EventType", "Host"), 25L, false)),
                    null, "dashboard-tab");
            default -> throw new IllegalArgumentException("Unknown tab '" + tab + "': use pipeline or dashboard");
        };
    }

    // One trusted MCP server, `stroom`, with a tool that makes an index template only once the user has reviewed
    // it, as the Stroom MCP server's does.
    private static McpToolset mcpToolset() {
        final McpSyncClient client = Mockito.mock(McpSyncClient.class);
        final Tool tool = Tool.builder()
                .name(INDEX_TEMPLATE_TOOL)
                .description("Creates an Elasticsearch index template for a feed's events. Without confirm, it "
                             + "creates nothing and returns the proposed template with status needs_review: show "
                             + "the user the template in full and ask them to confirm it. Only call it again with "
                             + "confirm=true once the user has confirmed.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "feed", Map.of("type", "string", "description", "The feed's name."),
                                "template_name", Map.of("type", "string"),
                                "confirm", Map.of("type", "boolean", "description",
                                        "True only once the user has confirmed the template.")),
                        "required", List.of("feed")))
                .annotations(new ToolAnnotations(null, false, false, false, false, null))
                .build();
        Mockito.when(client.listTools()).thenReturn(new ListToolsResult(List.of(tool), null));
        Mockito.when(client.callTool(Mockito.any(CallToolRequest.class))).thenAnswer(invocation -> {
            final Map<String, Object> arguments = invocation.<CallToolRequest>getArgument(0).arguments();
            final boolean confirmed = arguments != null && Boolean.TRUE.equals(arguments.get("confirm"));
            final String text = confirmed
                    ? "{\"status\":\"created\",\"template\":\"windows-events\"}"
                    : """
                            {"status":"needs_review",
                             "message":"Nothing has been created. Show the user this template in full and ask \
                            them to confirm it before calling again with confirm=true.",
                             "template":{"index_patterns":["windows-events-*"],
                              "mappings":{"properties":{
                               "EventTime":{"type":"date"},
                               "UserId":{"type":"keyword"},
                               "EventType":{"type":"keyword"},
                               "Host":{"type":"keyword"}}}}}""";
            return CallToolResult.builder().content(List.of(new TextContent(text))).isError(false).build();
        });
        final McpToolServerConfig config = McpToolServerConfig.builder()
                .name("stroom")
                .endpoint("http://localhost/mcp")
                .trusted(true)
                .build();
        return new McpToolset(List.of(new McpServerConnection("stroom", config, client, null)), List.of(), true);
    }


    // --------------------------------------------------------------------------------


    /// What happened when the agent was asked a question.
    ///
    /// @param toolCalls      The tool calls made, in order.
    /// @param answer         The model's answer, or null if it didn't give one.
    /// @param stoppedBecause Why the run stopped without an answer (e.g. waiting for approval), or null.
    /// @param durationMs     How long the run took.
    record Run(List<ToolCall> toolCalls, String answer, String stoppedBecause, long durationMs) {

    }

    /// A tool call made in a run.
    ///
    /// @param tool      The tool, as the model named it.
    /// @param arguments Its arguments, as the model gave them.
    /// @param result    What the model was given back, or null if it wasn't run.
    record ToolCall(String tool, String arguments, String result) {

    }

    /// A chat's workspace files held in memory, as the database would hold them.
    private static final class InMemoryWorkspaceDao implements AiWorkspaceDao {

        private final Map<Integer, FileInfo> files = Collections.synchronizedMap(new LinkedHashMap<>());
        private final Map<Integer, List<Chunk>> chunks = Collections.synchronizedMap(new HashMap<>());
        private final AtomicInteger nextId = new AtomicInteger(100);

        @Override
        public int createFile(final int chatId,
                              final AiAttachmentType type,
                              final String path,
                              final Integer sourceMessageId) {
            final int id = nextId.getAndIncrement();
            files.put(id, new FileInfo(id, chatId, path, AiAttachmentStatus.READY, null, null, null, 0));
            return id;
        }

        @Override
        public Optional<Integer> findFile(final int chatId, final String path) {
            return files.values().stream()
                    .filter(info -> info.chatId() == chatId && info.path().equals(path))
                    .map(FileInfo::id)
                    .findFirst();
        }

        @Override
        public void storeContent(final int fileId, final String path, final List<Chunk> chunks, final long sizeBytes) {
            this.chunks.put(fileId, List.copyOf(chunks));
            files.computeIfPresent(fileId, (id, info) -> new FileInfo(id, info.chatId(), path, info.status(),
                    info.sourceDocUuid(), info.expireTimeMs(), info.errorMessage(), sizeBytes));
        }

        @Override
        public List<ChunkInfo> getChunkInfo(final int fileId) {
            final List<ChunkInfo> infos = new ArrayList<>();
            final List<Chunk> held = chunks.getOrDefault(fileId, List.of());
            for (int seq = 0; seq < held.size(); seq++) {
                final Chunk chunk = held.get(seq);
                infos.add(new ChunkInfo(seq, chunk.firstLine(), chunk.lineCount(), chunk.rawSize()));
            }
            return infos;
        }

        @Override
        public Optional<byte[]> getChunkData(final int fileId, final int seq) {
            final List<Chunk> held = chunks.getOrDefault(fileId, List.of());
            return seq < held.size()
                    ? Optional.of(held.get(seq).data())
                    : Optional.empty();
        }

        @Override
        public long getSize(final int fileId) {
            return Optional.ofNullable(files.get(fileId)).map(FileInfo::sizeBytes).orElse(0L);
        }

        @Override
        public void setRetention(final int fileId, final String sourceDocUuid, final Long expireTimeMs) {
            files.computeIfPresent(fileId, (id, info) -> new FileInfo(id, info.chatId(), info.path(),
                    info.status(), sourceDocUuid, expireTimeMs, info.errorMessage(), info.sizeBytes()));
        }

        @Override
        public Optional<FileInfo> getFileInfo(final int fileId) {
            return Optional.ofNullable(files.get(fileId));
        }

        @Override
        public List<Integer> findExpired(final long nowMs, final int limit) {
            return List.of();
        }

        @Override
        public void makeUnavailable(final int fileId, final String reason) {
            files.computeIfPresent(fileId, (id, info) -> new FileInfo(id, info.chatId(), info.path(),
                    AiAttachmentStatus.UNAVAILABLE, info.sourceDocUuid(), info.expireTimeMs(), reason,
                    info.sizeBytes()));
        }

        @Override
        public List<FileInfo> listFiles(final int chatId) {
            return files.values().stream()
                    .filter(info -> info.chatId() == chatId)
                    .sorted(Comparator.comparing(FileInfo::path))
                    .toList();
        }

        @Override
        public void deleteFile(final int fileId) {
            files.remove(fileId);
            chunks.remove(fileId);
        }

        @Override
        public long getTotalSizeForUser(final String userUuid) {
            return files.values().stream().mapToLong(FileInfo::sizeBytes).sum();
        }

        @Override
        public List<Integer> findAvailable(final List<Integer> fileIds) {
            return fileIds.stream()
                    .filter(id -> Optional.ofNullable(files.get(id))
                            .filter(info -> info.status() != AiAttachmentStatus.UNAVAILABLE)
                            .isPresent())
                    .toList();
        }

        @Override
        public long getTotalSize(final int chatId) {
            return listFiles(chatId).stream().mapToLong(FileInfo::sizeBytes).sum();
        }
    }
}
