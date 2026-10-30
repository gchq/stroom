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
import stroom.ai.api.ElasticIndexTemplates.ComponentTemplate;
import stroom.ai.api.ElasticIndexTemplates.IndexTemplate;
import stroom.ai.api.ElasticIndexTemplates.IndexTemplates;
import stroom.ai.api.ElasticIndexTemplates.Status;
import stroom.ai.api.TableSummariser;
import stroom.ai.impl.TabSnapshots.StoredPart;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.TabSnapshots.UnsavedPart;
import stroom.ai.impl.memory.AiMemoryService;
import stroom.ai.impl.tools.AgentInstructions;
import stroom.ai.impl.tools.BuiltInTools;
import stroom.ai.impl.tools.BuiltInToolsModule;
import stroom.ai.impl.tools.ToolRunContext;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.WorkspaceArguments;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.impl.workspace.WorkspaceStore;
import stroom.ai.shared.AiAgentMode;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiSteppingContext;
import stroom.ai.shared.AiSteppingElement;
import stroom.ai.shared.AiTabSearch;
import stroom.ai.shared.AiTabSnapshotPart;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.QueryTableContext;
import stroom.ai.shared.TableAnalysisConfig;
import stroom.cluster.api.ClusterNodeManager;
import stroom.cluster.api.ClusterState;
import stroom.config.global.api.GlobalConfigReader;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.OverrideValue;
import stroom.dashboard.impl.DashboardService;
import stroom.dashboard.impl.script.ScriptStore;
import stroom.dashboard.impl.visualisation.VisualisationStore;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.data.retention.api.DataRetentionRulesProvider;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.store.api.AttributeMapFactory;
import stroom.data.store.api.DataService;
import stroom.data.store.api.FsVolumeStates;
import stroom.data.store.api.Store;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolume.VolumeUseStatus;
import stroom.data.store.impl.fs.shared.FsVolumeState;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.dictionary.api.WordListProvider;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.ContentIndex;
import stroom.docstore.api.ContentIndexable;
import stroom.docstore.api.DocDependencyService;
import stroom.docstore.api.DocFinder;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.documentation.shared.DocumentationDoc;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.feed.api.FeedProperties;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.shared.Dependency;
import stroom.index.api.IndexVolumeStates;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeUseState;
import stroom.job.api.JobStates;
import stroom.meta.api.MetaService;
import stroom.meta.shared.MetaFields;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.pipeline.cache.SchemaPool;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.stepping.SteppingService;
import stroom.pipeline.xmlschema.XmlSchemaStore;
import stroom.pipeline.xslt.XsltStore;
import stroom.processor.api.ProcessingDiagnostics;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorTaskService;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.impl.QueryService;
import stroom.query.shared.QueryContext;
import stroom.query.shared.QuerySearchRequest;
import stroom.resource.api.ResourceStore;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.shared.TaskResource;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

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
import dev.langchain4j.service.tool.ToolExecutor;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/// A small, fixed Stroom for running every built-in tool as McpAgentChat runs them: through
/// [BuiltInTools#toolMap()], built by Guice from [BuiltInToolsModule], with mocks of the services behind the tools.
/// The user's app and document permissions can be set (document permissions hold for every document), an
/// administrator answers every permission check with yes, and every audit log call and event logged is recorded.
///
/// No database is needed. Each world is used for one scenario: make a new one for the next.
public final class BuiltInToolWorld implements AutoCloseable {

    /// The chat every call is made in.
    public static final int CHAT_ID = 1;

    public static final DocRef FOLDER = new DocRef(ExplorerConstants.FOLDER_TYPE, "folder-uuid", "Test");
    public static final DocRef FEED = new DocRef(FeedDoc.TYPE, "feed-uuid", "TEST-FEED");
    public static final DocRef PIPELINE = new DocRef(PipelineDoc.TYPE, "pipeline-uuid", "Test pipeline");
    public static final DocRef XSLT = new DocRef(XsltDoc.TYPE, "xslt-uuid", "Test XSLT");
    public static final DocRef DICTIONARY = new DocRef(DictionaryDoc.TYPE, "dictionary-uuid", "Test words");
    public static final DocRef INDEX = new DocRef("Index", "index-uuid", "Test index");
    public static final DocRef QUERY = new DocRef("Query", "query-uuid", "Test query");
    public static final DocRef DASHBOARD = new DocRef("Dashboard", "dashboard-uuid", "Test dashboard");
    public static final DocRef AGENTS = new DocRef(DocumentationDoc.TYPE, "agents-uuid", AgentInstructions.DOC_NAME);
    /// An Elastic Index, kept out of the folder's listing so the other tools' outputs don't change with it.
    public static final DocRef ELASTIC_INDEX = new DocRef(ElasticIndexDoc.TYPE, "elastic-index-uuid",
            "Test elastic index");

    /// The XSLT's text: it makes a lookup, so the lookup tools have something to find.
    public static final String XSLT_TEXT = """
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns:stroom="stroom">
              <xsl:template match="/">
                <xsl:value-of select="stroom:lookup('HOST_TO_LOCATION', 'host1')"/>
              </xsl:template>
            </xsl:stylesheet>
            """;

    /// A query over the world's index.
    public static final String STROOMQL = "from \"Test index\"\nselect UserId\n";

    /// The fixed arguments each built-in tool is called with, by tool name.
    public static final Map<String, String> ARGUMENTS = Map.ofEntries(
            Map.entry("get_current_date", "{}"),
            Map.entry("get_current_time", "{}"),
            Map.entry("get_current_datetime", "{}"),
            Map.entry("get_utc_time_offset", "{\"timeZone\":\"UTC\"}"),
            Map.entry("read_turn", "{\"turnId\":1}"),
            Map.entry("search_history", "{\"text\":\"feed\"}"),
            Map.entry("memory", "{\"command\":\"list\"}"),
            Map.entry("ls", "{\"path\":\"/\"}"),
            Map.entry("read_file", "{\"path\":\"/outputs/a.txt\"}"),
            Map.entry("grep", "{\"pattern\":\"a\",\"path\":\"/outputs\"}"),
            Map.entry("wc", "{\"path\":\"/outputs/a.txt\"}"),
            Map.entry("write_file", "{\"path\":\"/outputs/a.txt\",\"content\":\"a\"}"),
            Map.entry("str_replace", "{\"path\":\"/outputs/a.txt\",\"oldText\":\"a\",\"newText\":\"b\"}"),
            Map.entry("delete_file", "{\"path\":\"/outputs/a.txt\"}"),
            Map.entry("jq", "{\"path\":\"/outputs/a.json\",\"query\":\".a\"}"),
            Map.entry("xpath", "{\"path\":\"/outputs/a.xml\",\"expression\":\"/\"}"),
            Map.entry("task_list", "{\"tasks\":[{\"title\":\"Look\",\"status\":\"not_started\"}]}"),
            Map.entry("ask_user", "{\"intro\":\"Which feed?\",\"questions\":[{\"id\":\"feed\",\"question\":\"Feed?\","
                                  + "\"type\":\"text\"}]}"),
            Map.entry("fetch_events", "{\"path\":\"/outputs/a.csv\"}"),
            Map.entry("preview_extraction", "{\"table\":\"Users\",\"path\":\"/outputs/a.csv\",\"fields\":"
                                            + "[{\"name\":\"Org\",\"xpath\":\"Org\"}]}"),
            Map.entry("propose_extraction_pipeline", "{\"table\":\"Users\",\"name\":\"Users "
                                                     + "extraction\",\"fields\":[{\"name\":\"Org\",\"xpath\":"
                                                     + "\"Org\"}]}"),
            Map.entry("search_events", "{\"path\":\"/outputs/a.xml\",\"text\":\"a\"}"),
            Map.entry("table", "{\"help\":true}"),
            Map.entry("pipeline", "{\"steps\":[]}"),
            Map.entry("run_stroomql", json(Map.of("query", STROOMQL))),
            Map.entry("summarise_column", "{\"path\":\"/outputs/a.csv\",\"column\":\"UserId\"}"),
            Map.entry("document_info", "{\"uuid\":\"xslt-uuid\"}"),
            Map.entry("read_document", "{\"uuid\":\"xslt-uuid\"}"),
            Map.entry("document_documentation", "{\"uuid\":\"dictionary-uuid\"}"),
            Map.entry("folder_contents", "{\"folder\":\"folder-uuid\"}"),
            Map.entry("get_instructions", "{\"folders\":[\"/Test\"]}"),
            Map.entry("current_results", "{}"),
            Map.entry("list_data_sources", "{}"),
            Map.entry("describe_data_source", "{\"data_source\":\"index-uuid\"}"),
            Map.entry("validate_stroomql", json(Map.of("query", STROOMQL))),
            Map.entry("propose_query", json(Map.of("name", "Users", "query", STROOMQL))),
            Map.entry("propose_dashboard", json(Map.of("name", "Users", "query", STROOMQL,
                    "query_type", DashboardBuilder.STROOMQL, "preview", false))),
            Map.entry("propose_query_change", json(Map.of("query", STROOMQL.replace("select", "where UserId = "
                                                                                              + "\"alice\"\nselect"),
                    "summary", "Only alice."))),
            Map.entry("propose_code_change", json(Map.of("element", "xsltFilter",
                    "code", XSLT_TEXT.replace("host1", "host2"), "summary", "Look up host2."))),
            Map.entry("stream_meta", "{\"feeds\":[\"TEST-FEED\"]}"),
            Map.entry("processor_tasks", "{\"mode\":\"list\"}"),
            Map.entry("explain_processing", "{\"pipeline_uuid\":\"pipeline-uuid\"}"),
            Map.entry("server_tasks", "{}"),
            Map.entry("volumes", "{}"),
            Map.entry("explain_retention", "{\"feed\":\"TEST-FEED\"}"),
            Map.entry("document_links", "{\"uuids\":[\"pipeline-uuid\"]}"),
            Map.entry("find_in_content", "{\"pattern\":\"HOST_TO_LOCATION\",\"uuids\":[\"xslt-uuid\"]}"),
            Map.entry("xslt_lookups", "{\"uuids\":[\"xslt-uuid\"]}"),
            Map.entry("propose_dashboard_change", "{\"component\":\"table-1\",\"changes\":[{\"op\":\"hide_column\","
                                                  + "\"column\":\"UserId\"}],\"summary\":\"Hide the user.\"}"),
            Map.entry("propose_documentation_change", json(Map.of("uuid", "dictionary-uuid",
                    "markdown", "# Test words\n\nThe words to look for.", "summary", "Says what the words are."))),
            Map.entry("config_properties", "{\"mode\":\"get\",\"paths\":[\"stroom.ui.defaultMaxResults\"]}"),
            Map.entry("elastic_index_templates", "{\"uuid\":\"elastic-index-uuid\"}"));

    private final Set<AppPermission> appPermissions;
    private final Set<DocumentPermission> documentPermissions;
    private final boolean admin;

    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final StroomEventLoggingService eventLoggingService = Mockito.mock(StroomEventLoggingService.class);
    private final AiAuditLog auditLog = Mockito.spy(new AiAuditLog(() -> eventLoggingService));
    private final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
    private final ExplorerNodeService explorerNodeService = Mockito.mock(ExplorerNodeService.class);
    private final DocFinder docFinder = Mockito.mock(DocFinder.class);
    private final DocDependencyService dependencyService = Mockito.mock(DocDependencyService.class);
    private final DataSourceProviderRegistry dataSourceRegistry = Mockito.mock(DataSourceProviderRegistry.class);
    private final QueryService queryService = Mockito.mock(QueryService.class);
    private final TabSnapshots tabSnapshots = Mockito.mock(TabSnapshots.class);
    private final AiService aiService = Mockito.mock(AiService.class);
    private final AiDao aiDao = Mockito.mock(AiDao.class);
    private final NodeService nodeService = Mockito.mock(NodeService.class);
    private final NodeInfo nodeInfo = Mockito.mock(NodeInfo.class);
    private final ExecutorProvider executorProvider = Mockito.mock(ExecutorProvider.class);
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final GlobalConfigReader globalConfigReader = Mockito.mock(GlobalConfigReader.class);
    private final ElasticIndexTemplates elasticIndexTemplates = Mockito.mock(ElasticIndexTemplates.class);
    /// The workspace's files, by path.
    private final Map<String, String> files = new LinkedHashMap<>();
    private final Map<String, DocRef> docRefs = new LinkedHashMap<>();
    private final Map<String, Object> documents = new HashMap<>();
    @SuppressWarnings("rawtypes")
    private final Map<String, DocumentActionHandler> handlers = new HashMap<>();
    private final Path tempDir;
    private final BuiltInTools builtInTools;

    /// @param tempDir             A folder for the workspace's temporary files.
    /// @param appPermissions      The app permissions the user has.
    /// @param documentPermissions The permissions the user has on every document.
    /// @param admin               True if the user is an administrator, who passes every check.
    public BuiltInToolWorld(final Path tempDir,
                            final Set<AppPermission> appPermissions,
                            final Set<DocumentPermission> documentPermissions,
                            final boolean admin) {
        this.tempDir = tempDir;
        this.appPermissions = appPermissions.isEmpty()
                ? EnumSet.noneOf(AppPermission.class)
                : EnumSet.copyOf(appPermissions);
        this.documentPermissions = documentPermissions.isEmpty()
                ? EnumSet.noneOf(DocumentPermission.class)
                : EnumSet.copyOf(documentPermissions);
        this.admin = admin;
        addDocuments();
        stubSecurity();
        stubWorkspace();
        stubExplorer();
        stubQueries();
        stubChat();
        stubConfig();
        stubElasticIndexTemplates();
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn("node1");
        Mockito.when(nodeService.findNodeNames(Mockito.any())).thenReturn(List.of("node1"));
        Mockito.when(executorProvider.get()).thenReturn(executorService);
        builtInTools = injector().getInstance(BuiltInTools.class);
    }

    /// @return A world where the user has every app and document permission, but is not an administrator.
    public static BuiltInToolWorld withEverything(final Path tempDir) {
        return new BuiltInToolWorld(tempDir, EnumSet.allOf(AppPermission.class),
                EnumSet.allOf(DocumentPermission.class), false);
    }

    /// @return The built-in tools, as the agent loop is given them.
    public BuiltInTools builtInTools() {
        return builtInTools;
    }

    /// @return The audit log the tools and the agent loop record to (a spy over the real one).
    public AiAuditLog auditLog() {
        return auditLog;
    }

    /// @return Where the audit log logs its events (a mock that records them).
    public StroomEventLoggingService eventLoggingService() {
        return eventLoggingService;
    }

    /// @return The chat service, which keeps the chat's messages in memory.
    public AiService aiService() {
        return aiService;
    }

    /// @return The chat DAO (a mock).
    public AiDao aiDao() {
        return aiDao;
    }

    /// @return The chat's workspace, held in memory.
    public ChatWorkspace workspace() {
        return workspace;
    }

    /// @return The names of the built-in tools, in name order.
    public List<String> toolNames() {
        return builtInTools.toolMap().keySet().stream()
                .map(ToolSpecification::name)
                .sorted()
                .toList();
    }

    /// Makes the document the user has open in the chat's tab, for the tools that work on the tab.
    ///
    /// @param docRef The document.
    /// @param part   The part of the tab's snapshot that holds its content, e.g. [AiTabSnapshotPart#QUERY].
    /// @param text   What that part holds.
    public void openTab(final DocRef docRef, final String part, final String text) {
        final StoredTabSnapshot snapshot = new StoredTabSnapshot(5, 1, docRef, "/Test", docRef.getName(), null,
                false, null, "snap-hash", 0L, List.of(new StoredPart(part, "text/plain", "/tabs/1/" + part,
                text.length(), text.length(), false)), List.of(), null);
        Mockito.when(tabSnapshots.forLatestQuestion(Mockito.anyInt())).thenReturn(Optional.of(snapshot));
        Mockito.when(tabSnapshots.part(Mockito.anyInt(), Mockito.any(), Mockito.eq(part)))
                .thenReturn(Optional.of(new UnsavedPart(text, false, "/tabs/1/" + part)));
    }

    /// Opens the tab a tool works on, if it works on the user's tab, so it gets as far as its checks.
    ///
    /// @param toolName The tool.
    public void prepareFor(final String toolName) {
        switch (toolName) {
            case "propose_query_change" -> openTab(QUERY, AiTabSnapshotPart.QUERY, STROOMQL);
            case "propose_code_change" -> openStepping();
            case "current_results" -> openQueryResults();
            case "propose_dashboard_change", "preview_extraction", "propose_extraction_pipeline" -> openTab(
                    DASHBOARD, AiTabSnapshotPart.QUERY, STROOMQL);
            default -> {
                // Nothing to open.
            }
        }
    }

    // The user has the query open, and has run it.
    private void openQueryResults() {
        final QueryTableContext ran = new QueryTableContext("Query 'Test query'", "node1",
                QuerySearchRequest.builder()
                        .query(STROOMQL)
                        .queryContext(QueryContext.builder().build())
                        .build());
        final AiTabSearch search = new AiTabSearch("Users", null, ran, ran, List.of("UserId"), 1L, false);
        final StoredTabSnapshot snapshot = new StoredTabSnapshot(5, 1, QUERY, "/Test", QUERY.getName(), "Query",
                false, null, "snap-hash", 0L, List.of(), List.of(search), null);
        Mockito.when(tabSnapshots.forLatestQuestion(Mockito.anyInt())).thenReturn(Optional.of(snapshot));
    }

    // The user is stepping the pipeline, with its XSLT selected.
    private void openStepping() {
        final AiSteppingContext stepping = new AiSteppingContext(FEED.getName(), 12L, "Raw Events", null, 1L, 5L,
                "xsltFilter", List.of(new AiSteppingElement("xsltFilter", "XSLTFilter", XSLT, true, false, 0, 0,
                List.of())), List.of());
        final StoredTabSnapshot snapshot = new StoredTabSnapshot(5, 1, PIPELINE, "/Test", PIPELINE.getName(),
                "Stepping", false, null, "snap-hash", 0L, List.of(new StoredPart(AiTabSnapshotPart.STEP_CODE,
                "application/xslt+xml", "/tabs/1/code.xsl", XSLT_TEXT.length(), XSLT_TEXT.length(), false)),
                List.of(), stepping);
        Mockito.when(tabSnapshots.forLatestQuestion(Mockito.anyInt())).thenReturn(Optional.of(snapshot));
        Mockito.when(tabSnapshots.part(Mockito.anyInt(), Mockito.any(), Mockito.eq(AiTabSnapshotPart.STEP_CODE)))
                .thenReturn(Optional.of(new UnsavedPart(XSLT_TEXT, false, "/tabs/1/code.xsl")));
    }

    /// Runs a built-in tool, as McpAgentChat does, in a run of its own.
    ///
    /// @param name      The tool's name.
    /// @param arguments Its arguments, as JSON.
    /// @return What the tool returned.
    public String run(final String name, final String arguments) {
        final ToolExecutor executor = builtInTools.toolMap().entrySet().stream()
                .filter(entry -> entry.getKey().name().equals(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No built-in tool " + name));
        final ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call-1")
                .name(name)
                .arguments(arguments)
                .build();
        final ToolRunContext context = new ToolRunContext(null, null, () -> false);
        return context.run(() -> executor.execute(request, CHAT_ID));
    }

    /// Builds the agent loop over this world's built-in tools, auditing to this world's audit log.
    ///
    /// @param toolConsent Decides whether each tool call runs.
    /// @param runManager  The chat's runs.
    /// @return The agent loop.
    McpAgentChat agentChat(final ToolConsent toolConsent, final AiChatRunManager runManager) {
        return new McpAgentChat(aiService, builtInTools, runManager, Mockito.mock(WorkspaceStore.class),
                Mockito.mock(ContextUsageTracker.class), ConsentGates.chat(toolConsent, aiService, runManager),
                auditLog, new ModelStreamer(aiService),
                new ToolGroups.Activations(() -> aiDao), new TurnSummaries(), AiConfig::new, aiDao,
                new UserActions(Mockito.mock(UserActionLookup.class)),
                new WorkspaceArguments(Mockito.mock(ChatWorkspace.class)), ToolGrants::all);
    }

    /// Runs one question through the agent loop, with a model that follows a script.
    ///
    /// @param agentChat The agent loop.
    /// @param model     The model.
    /// @param toolset   The MCP tools offered, or [McpToolset#EMPTY].
    public static void chat(final McpAgentChat agentChat, final ScriptedModel model, final McpToolset toolset) {
        agentChat.chat(model, null, CHAT_ID, List.of(new SystemMessage("system"), new UserMessage("q")), toolset,
                99, new AtomicBoolean(false), Mockito.mock(TaskContext.class), 1, new StringBuilder(), 0,
                ContextBudget.UNKNOWN);
    }

    /// @return The calls made to the audit log, in order, each as its method and arguments.
    public List<Invocation> auditLogCalls() {
        return Mockito.mockingDetails(auditLog).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getDeclaringClass() == AiAuditLog.class)
                .filter(invocation -> java.lang.reflect.Modifier.isPublic(invocation.getMethod().getModifiers()))
                .toList();
    }

    /// @return The events logged, in order, each as the arguments of its `log` call.
    public List<Invocation> loggedEvents() {
        return Mockito.mockingDetails(eventLoggingService).getInvocations().stream()
                .filter(invocation -> "log".equals(invocation.getMethod().getName()))
                .toList();
    }

    /// @return The app permissions the tools have asked the user's security context about so far, whatever the
    /// answer, in the order of [AppPermission].
    public Set<AppPermission> appPermissionsChecked() {
        final Set<AppPermission> checked = EnumSet.noneOf(AppPermission.class);
        Mockito.mockingDetails(securityContext).getInvocations().stream()
                .filter(invocation -> "hasAppPermission".equals(invocation.getMethod().getName()))
                .forEach(invocation -> checked.add(invocation.getArgument(0)));
        return checked;
    }

    /// @return The document permissions the tools have asked the user's security context about so far, on any
    /// document and whatever the answer, in the order of [DocumentPermission].
    public Set<DocumentPermission> documentPermissionsChecked() {
        final Set<DocumentPermission> checked = EnumSet.noneOf(DocumentPermission.class);
        Mockito.mockingDetails(securityContext).getInvocations().stream()
                .filter(invocation -> "hasDocumentPermission".equals(invocation.getMethod().getName()))
                .forEach(invocation -> checked.add(invocation.getArgument(1)));
        return checked;
    }

    /// Forgets the audit log calls and events recorded so far.
    public void clearRecorded() {
        Mockito.clearInvocations(auditLog, eventLoggingService);
    }

    @Override
    public void close() {
        executorService.shutdownNow();
        try {
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Injector injector() {
        return Guice.createInjector(new BuiltInToolsModule(), new AbstractModule() {
            @Override
            @SuppressWarnings("rawtypes")
            protected void configure() {
                bind(ChatWorkspace.class).toInstance(workspace);
                bind(AiService.class).toInstance(aiService);
                bind(AiMemoryService.class).toInstance(Mockito.mock(AiMemoryService.class));
                bind(QueryService.class).toInstance(queryService);
                bind(DashboardBuilder.class).toInstance(Mockito.mock(DashboardBuilder.class));
                bind(ResourceStore.class).toInstance(Mockito.mock(ResourceStore.class));
                bind(Store.class).toInstance(Mockito.mock(Store.class));
                bind(SecurityContext.class).toInstance(securityContext);
                bind(TableSummariser.class).toInstance(Mockito.mock(TableSummariser.class));
                bind(AskStroomAiConfig.class).toInstance(new AskStroomAiConfig());
                bind(McpElicitations.class).toInstance(Mockito.mock(McpElicitations.class));
                bind(AiChatRunManager.class).toInstance(Mockito.mock(AiChatRunManager.class));
                bind(ExplorerNodeService.class).toInstance(explorerNodeService);
                bind(ExplorerService.class).toInstance(Mockito.mock(ExplorerService.class));
                bind(DocDependencyService.class).toInstance(dependencyService);
                bind(WordListProvider.class).toInstance(Mockito.mock(WordListProvider.class));
                bind(AiAuditLog.class).toInstance(auditLog);
                bind(TabSnapshots.class).toInstance(tabSnapshots);
                bind(TableDownloader.class).toInstance(Mockito.mock(TableDownloader.class));
                bind(DataSourceProviderRegistry.class).toInstance(dataSourceRegistry);
                bind(DashboardService.class).toInstance(Mockito.mock(DashboardService.class));
                bind(TableAnalysisConfig.class).toInstance(new TableAnalysisConfig());
                final MapBinder<DocumentTypeName, DocumentActionHandler> handlerBinder = MapBinder.newMapBinder(
                        binder(), DocumentTypeName.class, DocumentActionHandler.class);
                handlers.forEach((type, handler) ->
                        handlerBinder.addBinding(new DocumentTypeName(type)).toInstance(handler));
                bind(MetaService.class).toInstance(Mockito.mock(MetaService.class));
                bind(ProcessorFilterService.class).toInstance(Mockito.mock(ProcessorFilterService.class));
                bind(ProcessorTaskService.class).toInstance(Mockito.mock(ProcessorTaskService.class));
                bind(ProcessingDiagnostics.class).toInstance(processingDiagnostics());
                bind(JobStates.class).toInstance(Mockito.mock(JobStates.class));
                bind(FsVolumeStates.class).toInstance(fsVolumeStates());
                bind(IndexVolumeStates.class).toInstance(indexVolumeStates());
                bind(DataRetentionRulesProvider.class).toInstance(retentionRules());
                bind(ClusterNodeManager.class).toInstance(clusterNodeManager());
                bind(TaskResource.class).toInstance(Mockito.mock(TaskResource.class));
                bind(TaskContextFactory.class).toInstance(new SimpleTaskContextFactory());
                bind(NodeService.class).toInstance(nodeService);
                bind(ResultStoreManager.class).toInstance(Mockito.mock(ResultStoreManager.class));
                bind(CodeChangeStepper.class).toInstance(Mockito.mock(CodeChangeStepper.class));
                bind(ElasticIndexTemplates.class).toInstance(elasticIndexTemplates);
                bind(AttributeMapFactory.class).toInstance(Mockito.mock(AttributeMapFactory.class));
                bind(DataService.class).toInstance(Mockito.mock(DataService.class));
                bind(FeedProperties.class).toInstance(Mockito.mock(FeedProperties.class));
                bind(DocFinder.class).toInstance(docFinder);
                bind(XmlSchemaStore.class).toInstance(Mockito.mock(XmlSchemaStore.class));
                bind(ScriptStore.class).toInstance(Mockito.mock(ScriptStore.class));
                bind(XsltStore.class).toInstance(Mockito.mock(XsltStore.class));
                bind(SteppingService.class).toInstance(Mockito.mock(SteppingService.class));
                bind(VisualisationStore.class).toInstance(Mockito.mock(VisualisationStore.class));
                bind(DocumentPermissionService.class)
                        .toInstance(Mockito.mock(DocumentPermissionService.class));
                bind(SchemaPool.class).toInstance(Mockito.mock(SchemaPool.class));
                bind(ContentIndex.class).toInstance(Mockito.mock(ContentIndex.class));
                bind(StroomEventLoggingService.class).toInstance(eventLoggingService);
                bind(GlobalConfigReader.class).toInstance(globalConfigReader);
                bind(NodeInfo.class).toInstance(nodeInfo);
                bind(ExecutorProvider.class).toInstance(executorProvider);
                Multibinder.newSetBinder(binder(), ContentIndexable.class);
            }
        });
    }

    private void addDocuments() {
        add(FOLDER, null);
        add(FEED, null);
        add(PIPELINE, null);
        add(XSLT, XsltDoc.builder().uuid(XSLT.getUuid()).name(XSLT.getName()).data(XSLT_TEXT).build());
        add(DICTIONARY, new DictionaryDoc(DICTIONARY.getUuid(), DICTIONARY.getName(), null, null, null, null, null,
                null, "alpha\nbeta\n", List.of()));
        add(INDEX, null);
        add(QUERY, null);
        add(DASHBOARD, null);
        add(AGENTS, new DocumentationDoc(AGENTS.getUuid(), AGENTS.getName(), null, null, null, null, null, null,
                "Feeds in /Test are test feeds."));
        // Found by its UUID, but with no handler, as its templates are read by ElasticIndexTemplates.
        docRefs.put(ELASTIC_INDEX.getUuid(), ELASTIC_INDEX);
    }

    private void add(final DocRef docRef, final Object document) {
        docRefs.put(docRef.getUuid(), docRef);
        if (document != null) {
            documents.put(docRef.getUuid(), document);
        }
        handlers.computeIfAbsent(docRef.getType(), type -> {
            @SuppressWarnings("rawtypes") final DocumentActionHandler handler = Mockito.mock(
                    DocumentActionHandler.class);
            Mockito.when(handler.readDocument(Mockito.any())).thenAnswer(invocation ->
                    documents.get(invocation.<DocRef>getArgument(0).getUuid()));
            return handler;
        });
    }

    private boolean hasDocumentPermission(final DocRef docRef, final DocumentPermission permission) {
        if (admin) {
            return true;
        }
        // A permission implies the ones below it, as Stroom's do: Use needs View, and so on.
        return documentPermissions.stream().anyMatch(held -> held.isEqualOrHigher(permission))
               && docRef != null && docRefs.containsKey(docRef.getUuid());
    }

    @SuppressWarnings("unchecked")
    private void stubSecurity() {
        Mockito.when(securityContext.hasAppPermission(Mockito.any(AppPermission.class))).thenAnswer(invocation ->
                admin || appPermissions.contains(invocation.<AppPermission>getArgument(0)));
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(DocRef.class),
                Mockito.any(DocumentPermission.class))).thenAnswer(invocation ->
                hasDocumentPermission(invocation.getArgument(0), invocation.getArgument(1)));
        Mockito.when(securityContext.useAsReadResult(Mockito.any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        Mockito.when(securityContext.asUserResult(Mockito.<UserIdentity>any(), Mockito.any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        Mockito.when(securityContext.asUserResult(Mockito.<UserRef>any(), Mockito.any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        Mockito.when(securityContext.getUserIdentityForAudit()).thenReturn("tester");
        Mockito.when(securityContext.asProcessingUserResult(Mockito.any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        Mockito.doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(securityContext).asProcessingUser(Mockito.any(Runnable.class));
    }

    // A workspace held in memory, with a few files to work on.
    @SuppressWarnings("unchecked")
    private void stubWorkspace() {
        files.put("/outputs/a.txt", "a\n");
        files.put("/outputs/a.csv", "StreamId,EventId,UserId\n1,1,alice\n");
        files.put("/outputs/a.json", "{\"a\":1}\n");
        files.put("/outputs/a.xml", "<a/>\n");
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        Mockito.when(workspace.tempFile()).thenAnswer(invocation -> Files.createTempFile(tempDir, "tool", ".tmp"));
        Mockito.when(workspace.find(Mockito.anyInt(), Mockito.any(WorkspacePath.class))).thenAnswer(invocation ->
                fileId(path(invocation.getArgument(1))));
        Mockito.when(workspace.fileId(Mockito.anyInt(), Mockito.any(WorkspacePath.class))).thenAnswer(invocation ->
                fileId(path(invocation.getArgument(1))).orElseThrow(() -> new WorkspaceException(
                        "No such file: " + path(invocation.getArgument(1)))));
        Mockito.when(workspace.localFile(Mockito.anyInt(), Mockito.any(WorkspacePath.class))).thenAnswer(invocation -> {
            final String text = content(path(invocation.getArgument(1)));
            final Path file = Files.createTempFile(tempDir, "file", ".tmp");
            Files.writeString(file, text, StandardCharsets.UTF_8);
            return file;
        });
        Mockito.when(workspace.readLines(Mockito.anyInt(), Mockito.any(WorkspacePath.class), Mockito.anyLong(),
                Mockito.anyInt())).thenAnswer(invocation -> content(path(invocation.getArgument(1))).lines()
                .skip(Math.max(0, invocation.<Long>getArgument(2) - 1))
                .limit(invocation.<Integer>getArgument(3))
                .toList());
        Mockito.when(workspace.lineCount(Mockito.anyInt(), Mockito.any(WorkspacePath.class))).thenAnswer(invocation ->
                content(path(invocation.getArgument(1))).lines().count());
        Mockito.when(workspace.size(Mockito.anyInt(), Mockito.any(WorkspacePath.class))).thenAnswer(invocation ->
                (long) content(path(invocation.getArgument(1))).getBytes(StandardCharsets.UTF_8).length);
        Mockito.when(workspace.list(Mockito.anyInt(), Mockito.any(WorkspacePath.class))).thenAnswer(invocation -> {
            final String dir = path(invocation.getArgument(1));
            final String prefix = dir.endsWith("/")
                    ? dir
                    : dir + "/";
            return files.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(prefix))
                    .map(entry -> new ChatWorkspace.Entry(WorkspacePath.parse(entry.getKey()),
                            entry.getValue().getBytes(StandardCharsets.UTF_8).length, null))
                    .toList();
        });
        Mockito.when(workspace.write(Mockito.anyInt(), Mockito.any(WorkspacePath.class), Mockito.any(Path.class),
                Mockito.any())).thenAnswer(invocation -> {
                    files.put(path(invocation.getArgument(1)),
                            Files.readString(invocation.getArgument(2), StandardCharsets.UTF_8));
                    return fileId(path(invocation.getArgument(1))).orElseThrow();
                });
        Mockito.when(workspace.write(Mockito.anyInt(), Mockito.any(WorkspacePath.class), Mockito.anyString(),
                Mockito.any())).thenAnswer(invocation -> {
                    files.put(path(invocation.getArgument(1)), invocation.getArgument(2));
                    return fileId(path(invocation.getArgument(1))).orElseThrow();
                });
        Mockito.doAnswer(invocation -> files.remove(path(invocation.getArgument(1))))
                .when(workspace).delete(Mockito.anyInt(), Mockito.any(WorkspacePath.class));
    }


    // The processing settings as Stroom's defaults have them, for explain_processing.
    private static ProcessingDiagnostics processingDiagnostics() {
        final ProcessingDiagnostics diagnostics = Mockito.mock(ProcessingDiagnostics.class);
        Mockito.when(diagnostics.getSettings()).thenReturn(new ProcessingDiagnostics.Settings(false, true, true, true,
                true, 1000, 1000, Duration.ofMinutes(10), Duration.ofMinutes(1)));
        return diagnostics;
    }

    // One data volume, half full, in the default group, for volumes.
    private static FsVolumeStates fsVolumeStates() {
        final FsVolumeStates states = Mockito.mock(FsVolumeStates.class);
        Mockito.when(states.volumesByGroup()).thenReturn(Map.of("Default Volume Group", List.of(FsVolume.builder()
                .path("/stroom/volumes/defaultStreamVolume")
                .status(VolumeUseStatus.ACTIVE)
                .byteLimit(900L * 1024 * 1024 * 1024)
                .volumeType(FsVolumeType.STANDARD)
                .volumeState(new FsVolumeState(1, 1, 500L * 1024 * 1024 * 1024, 500L * 1024 * 1024 * 1024,
                        1000L * 1024 * 1024 * 1024, 0L))
                .build())));
        Mockito.when(states.defaultGroup()).thenReturn(Optional.of("Default Volume Group"));
        return states;
    }

    // One index volume on node1, full, in the default group, for volumes.
    private static IndexVolumeStates indexVolumeStates() {
        final IndexVolumeStates states = Mockito.mock(IndexVolumeStates.class);
        Mockito.when(states.volumesByGroup()).thenReturn(Map.of("Default Volume Group", List.of(IndexVolume.builder()
                .nodeName("node1")
                .path("/stroom/volumes/defaultIndexVolume")
                .state(VolumeUseState.ACTIVE)
                .bytesLimit(100L * 1024 * 1024 * 1024)
                .bytesUsed(100L * 1024 * 1024 * 1024)
                .bytesFree(900L * 1024 * 1024 * 1024)
                .bytesTotal(1000L * 1024 * 1024 * 1024)
                .build())));
        Mockito.when(states.defaultGroup()).thenReturn(Optional.of("Default Volume Group"));
        return states;
    }

    // A rule deleting TEST-FEED's data after 30 days, for explain_retention.
    private static DataRetentionRulesProvider retentionRules() {
        final DataRetentionRulesProvider provider = Mockito.mock(DataRetentionRulesProvider.class);
        final DataRetentionRules rules = DataRetentionRules.builder()
                .uuid("rules-uuid")
                .name("Data Retention Rules")
                .rules(List.of(DataRetentionRule.ageRule(1, 0L, "Test feed", true, ExpressionOperator.builder()
                        .addTextTerm(MetaFields.FEED, Condition.EQUALS, "TEST-FEED")
                        .build(), 30, stroom.util.shared.time.TimeUnit.DAYS)))
                .build();
        Mockito.when(provider.get()).thenReturn(Optional.of(rules));
        return provider;
    }

    // One enabled node that is the master, for explain_processing.
    private static ClusterNodeManager clusterNodeManager() {
        final ClusterState state = new ClusterState();
        state.setAllNodes(List.of("node1"));
        state.setEnabledNodes(List.of("node1"));
        state.addEnabledActiveNode("node1");
        state.setMasterNodeName("node1");
        final ClusterNodeManager manager = Mockito.mock(ClusterNodeManager.class);
        Mockito.when(manager.getClusterState()).thenReturn(state);
        return manager;
    }

    private static String path(final WorkspacePath path) {
        return path.path();
    }

    private String content(final String path) {
        final String text = files.get(path);
        if (text == null) {
            throw new WorkspaceException("No such file: " + path);
        }
        return text;
    }

    // Files are numbered in the order they were first written.
    private Optional<Integer> fileId(final String path) {
        final List<String> paths = new ArrayList<>(files.keySet());
        final int index = paths.indexOf(path);
        return index < 0
                ? Optional.empty()
                : Optional.of(index + 1);
    }

    private void stubConfig() {
        final ConfigProperty property = ConfigProperty.builder()
                .name("stroom.ui.defaultMaxResults")
                .dataTypeName("String")
                .defaultValue("1000000,100,10,1")
                .description("The default maximum number of search results that new dashboard tables will request.")
                .editable(true)
                .build();
        Mockito.when(globalConfigReader.list()).thenReturn(List.of(property));
        Mockito.when(globalConfigReader.fetch(Mockito.any())).thenAnswer(invocation ->
                property.getName().equals(invocation.getArgument(0))
                        ? Optional.of(property)
                        : Optional.empty());
        Mockito.when(globalConfigReader.fetchYamlOverrideValue(Mockito.any(), Mockito.anyString()))
                .thenReturn(OverrideValue.unSet(String.class));
    }

    // The Elastic Index's templates: one index template, composed of two component templates.
    private void stubElasticIndexTemplates() {
        Mockito.when(elasticIndexTemplates.read(Mockito.any())).thenReturn(new IndexTemplates(
                "test-events",
                "test-events-000001",
                null,
                new IndexTemplate(Status.OK, "test-events",
                        "{\"index_patterns\":[\"test-events*\"],\"composed_of\":[\"test-mappings\","
                        + "\"test-settings\"],\"priority\":200}",
                        List.of(), null),
                List.of(new ComponentTemplate("test-mappings", Status.OK,
                                "{\"template\":{\"mappings\":{\"properties\":{\"UserId\":{\"type\":"
                                + "\"keyword\"}}}}}", null),
                        new ComponentTemplate("test-settings", Status.OK,
                                "{\"template\":{\"settings\":{\"number_of_shards\":1}}}", null))));
    }

    private void stubExplorer() {
        Mockito.when(explorerNodeService.getNodeByUuid(Mockito.anyString())).thenAnswer(invocation ->
                Optional.ofNullable(docRefs.get(invocation.<String>getArgument(0))).map(BuiltInToolWorld::node));
        Mockito.when(explorerNodeService.getNode(Mockito.any())).thenAnswer(invocation ->
                Optional.ofNullable(invocation.<DocRef>getArgument(0)).map(BuiltInToolWorld::node));
        Mockito.when(explorerNodeService.getPath(Mockito.any())).thenAnswer(invocation -> {
            final DocRef docRef = invocation.getArgument(0);
            final List<ExplorerNode> path = new ArrayList<>();
            path.add(ExplorerConstants.SYSTEM_NODE);
            if (!FOLDER.equals(docRef)) {
                path.add(node(FOLDER));
            }
            return path;
        });
        Mockito.when(explorerNodeService.getParent(Mockito.any())).thenAnswer(invocation ->
                Optional.of(FOLDER.equals(invocation.getArgument(0))
                        ? ExplorerConstants.SYSTEM_NODE
                        : node(FOLDER)));
        Mockito.when(explorerNodeService.getChildren(Mockito.any())).thenAnswer(invocation -> {
            final DocRef folder = invocation.getArgument(0);
            if (ExplorerConstants.SYSTEM_DOC_REF.equals(folder)) {
                return List.of(node(FOLDER));
            }
            if (FOLDER.equals(folder)) {
                return docRefs.values().stream()
                        .filter(docRef -> !FOLDER.equals(docRef))
                        .filter(docRef -> !ELASTIC_INDEX.equals(docRef))
                        .map(BuiltInToolWorld::node)
                        .toList();
            }
            return List.of();
        });
        Mockito.when(explorerNodeService.getNodesByName(Mockito.any(), Mockito.anyString())).thenAnswer(invocation ->
                FOLDER.getName().equals(invocation.getArgument(1))
                        ? List.of(node(FOLDER))
                        : List.of());
        Mockito.when(docFinder.findByName(Mockito.anyString(), Mockito.anyString(), Mockito.anyBoolean()))
                .thenAnswer(invocation -> docRefs.values().stream()
                        .filter(docRef -> docRef.getType().equals(invocation.getArgument(0)))
                        .filter(docRef -> docRef.getName().equalsIgnoreCase(invocation.getArgument(1)))
                        .filter(docRef -> hasDocumentPermission(docRef, DocumentPermission.VIEW))
                        .toList());
        Mockito.doCallRealMethod().when(docFinder).findByName(Mockito.anyString(), Mockito.anyString());
        // The pipeline reads the feed and uses the XSLT, which looks up in the dictionary.
        final List<Dependency> edges = List.of(
                new Dependency(PIPELINE, FEED, true),
                new Dependency(PIPELINE, XSLT, true),
                new Dependency(XSLT, DICTIONARY, true));
        Mockito.when(dependencyService.fetchDependencies(Mockito.any(), Mockito.any()))
                .thenReturn(new ResultPage<>(edges));
    }

    private void stubQueries() {
        Mockito.when(dataSourceRegistry.getDataSourceDocRefs()).thenReturn(List.of(INDEX));
        Mockito.when(dataSourceRegistry.findDataSourceByUuid(Mockito.anyString())).thenAnswer(invocation ->
                INDEX.getUuid().equals(invocation.getArgument(0))
                        ? Optional.of(INDEX)
                        : Optional.empty());
        Mockito.when(dataSourceRegistry.fetchDocumentation(Mockito.any())).thenReturn(Optional.empty());
        Mockito.when(dataSourceRegistry.getDataSourceProvider(Mockito.anyString())).thenReturn(Optional.empty());
        Mockito.when(queryService.getReferencedDataSource(Mockito.anyString())).thenReturn(Optional.of(INDEX));
        Mockito.when(queryService.validateQuery(Mockito.anyString()))
                .thenReturn(new ValidateExpressionResult(true, "ok", false));
        Mockito.when(queryService.findFields(Mockito.any())).thenReturn(new ResultPage<>(List.of(
                QueryField.createDate("EventTime"),
                QueryField.createText("UserId"))));
        Mockito.when(queryService.fetchDocumentation(Mockito.any())).thenReturn(Optional.empty());
    }

    private void stubChat() {
        final List<AiChatMessage> messages = new ArrayList<>();
        Mockito.when(aiService.getMessages(Mockito.anyInt())).thenAnswer(invocation -> List.copyOf(messages));
        Mockito.when(aiService.storeMessage(Mockito.anyInt(), Mockito.any(), Mockito.anyString()))
                .thenAnswer(invocation -> {
                    final AiChatMessage message = new AiChatMessage(1000 + messages.size(), CHAT_ID, 0L,
                            invocation.getArgument(1), null, invocation.getArgument(2), 1, null);
                    messages.add(message);
                    return message;
                });
        Mockito.when(aiDao.getAgentMode(Mockito.anyInt())).thenReturn(AiAgentMode.AGENT);
    }

    private static ExplorerNode node(final DocRef docRef) {
        return ExplorerNode.builder().docRef(docRef).build();
    }

    /// @param text What a tool returned, or an audit record.
    /// @return It with what changes from run to run (times, temporary paths) replaced by placeholders.
    public String normalise(final String text) {
        if (text == null) {
            return "null";
        }
        return text
                .replace(tempDir.toString(), "<temp>")
                .replaceAll("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z", "<time>")
                .replaceAll("\\b\\d{2}:\\d{2}:\\d{2}Z", "<time>")
                .replaceAll("\\b\\d{4}-\\d{2}-\\d{2}\\b", "<date>")
                .replaceAll("\\b1[6-9]\\d{11}\\b", "<epoch-ms>");
    }

    /// @param value Anything Jackson can write.
    /// @return It as JSON, keys sorted, for tool arguments.
    public static String json(final Map<String, ?> value) {
        // Sorted, as Map.of's order changes from run to run.
        return JsonUtil.writeValueAsString(new TreeMap<>(value), false);
    }

    /// A model that makes the tool calls it is given, one step each, then answers.
    public static final class ScriptedModel implements ChatModel {

        private final List<List<ToolExecutionRequest>> steps;
        private final List<ChatRequest> requests = new ArrayList<>();

        /// @param steps The tool calls to make at each step, in order. Once they are used up it answers.
        public ScriptedModel(final List<List<ToolExecutionRequest>> steps) {
            this.steps = new ArrayList<>(steps);
        }

        @Override
        public ChatResponse doChat(final ChatRequest chatRequest) {
            requests.add(chatRequest);
            if (steps.isEmpty()) {
                return ChatResponse.builder().aiMessage(AiMessage.from("an answer")).build();
            }
            return ChatResponse.builder().aiMessage(AiMessage.from(steps.removeFirst())).build();
        }

        /// @return What the model was asked, in order.
        public List<ChatRequest> requests() {
            return requests;
        }

        /// @return The names of the tools the model was offered at its first step, in the order offered.
        public List<String> firstOffered() {
            return requests.getFirst().toolSpecifications().stream()
                    .map(ToolSpecification::name)
                    .toList();
        }
    }
}
