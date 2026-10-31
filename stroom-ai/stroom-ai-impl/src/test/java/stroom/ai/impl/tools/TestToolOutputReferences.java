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

package stroom.ai.impl.tools;

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.FakeTabWorkspace;
import stroom.ai.impl.GoldenFiles;
import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.TableDownloader;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiTabSearch;
import stroom.ai.shared.QueryTableContext;
import stroom.ai.shared.TableAnalysisConfig;
import stroom.config.global.api.GlobalConfigReader;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.OverrideValue;
import stroom.dashboard.impl.DashboardService;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.data.store.api.AttributeMapFactory;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.dictionary.api.WordListProvider;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.dictionary.shared.WordList;
import stroom.docref.DocRef;
import stroom.docstore.api.ContentIndexable;
import stroom.docstore.api.DocFinder;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.feed.shared.FeedDoc;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.SelectionSummary;
import stroom.meta.shared.Status;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.FetchProcessorRequest;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorFilterTrackerStatus;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.processor.shared.QueryData;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.QueryKey;
import stroom.query.api.TableResult;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.query.shared.QueryContext;
import stroom.query.shared.QuerySearchRequest;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.shared.FindTaskProgressRequest;
import stroom.task.shared.TaskId;
import stroom.task.shared.TaskProgress;
import stroom.task.shared.TaskProgressResponse;
import stroom.task.shared.TaskResource;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.Range;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/// The exact output of the tools with the most involved results, for fixed inputs, in reference files (see
/// [GoldenFiles]), with the links they make and what they write to the workspace. Each tool is called as the model
/// calls it (`execute`), so errors are as the model sees them. The worlds are copied from each tool's own test,
/// with every time fixed; where a tool says when it was called (taken from the clock, which no tool can be given),
/// that time is replaced by a fixed placeholder, and only that.
class TestToolOutputReferences {

    private static final int CHAT = 1;
    private static final String FOLDER = "tools/output/";
    /// Stands in for times a tool takes from the clock when it is called.
    private static final String CALL_TIME = "<time of the call>";
    private static final Pattern ISO_TIME = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
    private static final long TASK_NOW = Instant.parse("2026-10-01T12:00:00Z").toEpochMilli();
    private static final long MINUTE = Duration.ofMinutes(1).toMillis();
    private static final long HOUR = Duration.ofHours(1).toMillis();

    @TempDir
    Path tempDir;

    // --------------------------------------------------------------------------------
    // processor_tasks
    // --------------------------------------------------------------------------------

    @Test
    void processorTasks() throws IOException {
        final ProcessorTasksReferenceWorld world = new ProcessorTasksReferenceWorld(tempDir);
        final String window = ProcessorTasksReferenceWorld.WINDOW;

        assertOutput("processor_tasks-failures", "{" + window + "}", world::run, world.written());
        assertOutput("processor_tasks-failures-sampled", "{" + window + ",\"sample\":1,\"stuck_after\":\"2m\"}",
                world::run, world.written());
        assertOutput("processor_tasks-list", "{\"mode\":\"list\"," + window + "}", world::run, world.written());
        assertOutput("processor_tasks-list-out", "{\"mode\":\"list\",\"statuses\":[\"failed\",\"complete\"],"
                                                 + "\"out\":\"/outputs/tasks.csv\"}", world::run, world.written());
        assertOutput("processor_tasks-summary", "{\"mode\":\"summary\"}", world::run, world.written());
        assertOutput("processor_tasks-summary-by-node", "{\"mode\":\"summary\",\"group_by\":[\"node\",\"status\"]}",
                world::run, world.written());
        assertOutput("processor_tasks-summary-by-filter", "{\"mode\":\"summary\",\"group_by\":[\"filter\"]}",
                world::run, world.written());
    }

    // --------------------------------------------------------------------------------
    // config_properties
    // --------------------------------------------------------------------------------

    @Test
    void configProperties() {
        final ToolTestFixture fixture = new ToolTestFixture(tempDir);
        final ExecutorService executorService = Executors.newCachedThreadPool();
        try {
            final ConfigPropertiesTool tool = configPropertiesTool(fixture, executorService);
            final ToolRunner run = arguments -> tool.execute(request(ConfigPropertiesTool.NAME, arguments),
                ToolCalls.inChat(CHAT, fixture.workspace)).text();

            assertOutput("config_properties-search", "{\"mode\":\"search\",\"query\":\"max results\"}", run,
                    fixture.written);
            assertOutput("config_properties-search-overridden", "{\"mode\":\"search\",\"query\":\"max\","
                                                                + "\"differs_from_default\":true}", run,
                    fixture.written);
            assertOutput("config_properties-get", "{\"mode\":\"get\",\"paths\":["
                                                  + "\"stroom.statistics.sql.search.maxResults\","
                                                  + "\"stroom.ui.htmlTitle\",\"stroom.ai.workspace.toolTimeLimit\","
                                                  + "\"stroom.search.resultStore.lmdb.maxStoreSize\","
                                                  + "\"stroom.ui.defaultMaxResults\"]}", run, fixture.written);
            assertOutput("config_properties-get-nodes", "{\"mode\":\"get\",\"paths\":["
                                                        + "\"stroom.statistics.sql.search.maxResults\"],"
                                                        + "\"nodes\":[\"node1\",\"node2\",\"node3\"]}", run,
                    fixture.written);
            assertOutput("config_properties-get-masked", "{\"mode\":\"get\",\"paths\":["
                                                         + "\"stroom.analytics.smtp.password\","
                                                         + "\"stroom.security.authentication.openId.clientSecret\","
                                                         + "\"stroom.ui.helpUrl\",\"stroom.ui.extraHeaders\","
                                                         + "\"stroom.kafka.skeletonConfigContent\"],"
                                                         + "\"nodes\":[\"node1\",\"node2\"]}", run,
                    fixture.written);
            assertOutput("config_properties-get-unknown", "{\"mode\":\"get\",\"paths\":[\"stroom.ui.maxResults\"]}",
                    run, fixture.written);
            assertOutput("config_properties-list", "{\"mode\":\"list\",\"prefix\":\"stroom.ui.\"}", run,
                    fixture.written);
            assertOutput("config_properties-list-top", "{\"mode\":\"list\"}", run, fixture.written);
        } finally {
            executorService.shutdownNow();
        }
    }

    // As TestConfigPropertiesTool builds it, without the node that never answers (which is about timing).
    private ConfigPropertiesTool configPropertiesTool(final ToolTestFixture fixture,
                                                      final ExecutorService executorService) {
        final GlobalConfigReader reader = Mockito.mock(GlobalConfigReader.class);
        final NodeService nodeService = Mockito.mock(NodeService.class);
        final NodeInfo nodeInfo = Mockito.mock(NodeInfo.class);
        final Map<String, ConfigProperty> properties = new LinkedHashMap<>();
        final Map<String, Map<String, OverrideValue<String>>> nodeYaml = new LinkedHashMap<>();
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.MANAGE_PROPERTIES_PERMISSION))
                .thenReturn(true);
        Mockito.when(fixture.securityContext.asUserResult(Mockito.<UserIdentity>any(), Mockito.any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
        stubTimeLimit(fixture.workspace);
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn("node1");
        Mockito.when(nodeService.findNodeNames(Mockito.any())).thenReturn(List.of("node2", "node1"));
        Mockito.when(reader.list()).thenAnswer(invocation -> new ArrayList<>(properties.values()));
        Mockito.when(reader.fetch(Mockito.any())).thenAnswer(invocation ->
                Optional.ofNullable(properties.get(invocation.<PropertyPath>getArgument(0).toString())));
        Mockito.when(reader.fetchYamlOverrideValue(Mockito.any(), Mockito.anyString())).thenAnswer(invocation -> {
            final String path = invocation.<PropertyPath>getArgument(0).toString();
            final Map<String, OverrideValue<String>> yaml = nodeYaml.get(invocation.<String>getArgument(1));
            if (yaml == null) {
                throw new RuntimeException("Unable to connect to http://admin:letmein@"
                                           + invocation.getArgument(1) + ":8080");
            }
            return yaml.getOrDefault(path, OverrideValue.unSet(String.class));
        });
        final ExecutorProvider executorProvider = Mockito.mock(ExecutorProvider.class);
        Mockito.when(executorProvider.get()).thenReturn(executorService);

        final long setAt = Instant.parse("2026-10-01T09:30:00Z").toEpochMilli();
        final long firstSetAt = Instant.parse("2026-09-01T08:00:00Z").toEpochMilli();
        final List<ConfigProperty.Builder> builders = List.of(
                property("stroom.ui.defaultMaxResults", "String", "1000000,100,10,1",
                        "The default maximum number of search results that new dashboard tables will request."),
                property("stroom.statistics.sql.search.maxResults", "Integer", "100000",
                        "The maximum number of records that can be returned from the statistics DB in a single "
                        + "query prior to aggregation")
                        .databaseOverrideValue("50000")
                        .createUser("alice")
                        .createTimeMs(firstSetAt)
                        .updateUser("admin")
                        .updateTimeMs(setAt),
                property("stroom.search.resultStore.maxSortedItems", "Integer", "500000",
                        "Maximum number of results that can be returned in a single page if the results are "
                        + "sorted. This will affect downloading all search results if results are sorted."),
                property("stroom.search.resultStore.map.trimmedSizeLimit", "Integer", "500000",
                        "The trimmed size of sorted results for on heap result stores."),
                property("stroom.ai.workspace.maxQueryRows", "Integer", "10000",
                        "The most rows a StroomQL query run by the agent (run_stroomql) may return."),
                property("stroom.ui.process.defaultRecordLimit", "Integer", "1000000",
                        "The default number of records that batch search processing will be limited by."),
                property("stroom.ui.htmlTitle", "String", "Stroom",
                        "The title to use for the application in the browser.")
                        .databaseOverrideValue("Database title")
                        .yamlOverrideValue("My Stroom"),
                property("stroom.ui.helpUrl", "String", "https://bob:hunter2@docs.example.com/help",
                        "The URL of the help pages."),
                property("stroom.ai.workspace.toolTimeLimit", "StroomDuration", "PT30S",
                        "How long a tool call may take.")
                        .editable(false)
                        .requireRestart(true),
                property("stroom.search.resultStore.lmdb.maxStoreSize", "ByteSize", "10GiB",
                        "The maximum size of the LMDB store.")
                        .requireUiRestart(true),
                property("stroom.security.authentication.openId.clientSecret", "String", null,
                        "The client secret of the identity provider.")
                        .yamlOverrideValue("abc123topsecret"),
                property("stroom.analytics.smtp.password", "String", "defpass", "The SMTP password.")
                        .password(true)
                        .databaseOverrideValue("dbpass")
                        .yamlOverrideValue("yamlpass"),
                property("stroom.ui.extraHeaders", "Map of String, String", "|=user=bob|password=s3cret|host=mail",
                        "Extra headers."),
                property("stroom.kafka.skeletonConfigContent", "String",
                        "bootstrap.servers=kafka:9092\nssl.key.password=kafkapass\n",
                        "The skeleton Kafka config."));
        for (final ConfigProperty.Builder builder : builders) {
            final ConfigProperty property = builder.build();
            properties.put(property.getNameAsString(), property);
        }
        nodeYaml.put("node1", new LinkedHashMap<>());
        nodeYaml.put("node2", new TreeMap<>(Map.of(
                "stroom.statistics.sql.search.maxResults", OverrideValue.with("2000"),
                "stroom.analytics.smtp.password", OverrideValue.with("nodepass"))));
        return new ConfigPropertiesTool(fixture.workspace, () -> reader, fixture.securityContext,
                () -> nodeService, () -> nodeInfo, executorProvider, fixture.auditLog(), Duration.ofSeconds(30));
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

    // --------------------------------------------------------------------------------
    // server_tasks
    // --------------------------------------------------------------------------------

    @Test
    void serverTasks() {
        final ToolTestFixture fixture = new ToolTestFixture(tempDir);
        final Map<String, List<TaskProgress>> tasks = new HashMap<>();
        final TaskResource taskResource = Mockito.mock(TaskResource.class);
        final NodeService nodeService = Mockito.mock(NodeService.class);
        final ExecutorProvider executorProvider = Mockito.mock(ExecutorProvider.class);
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.MANAGE_TASKS_PERMISSION))
                .thenReturn(true);
        stubTimeLimit(fixture.workspace);
        Mockito.when(nodeService.findNodeNames(Mockito.any())).thenReturn(List.of("node1", "node2", "node3"));
        Mockito.when(taskResource.find(Mockito.anyString(), Mockito.any(FindTaskProgressRequest.class)))
                .thenAnswer(invocation -> {
                    final String nodeName = invocation.getArgument(0);
                    if ("node3".equals(nodeName)) {
                        return new TaskProgressResponse(List.of(), List.of("node3 is not responding"), null);
                    }
                    return new TaskProgressResponse(tasks.getOrDefault(nodeName, List.of()), List.of(), null);
                });
        Mockito.when(executorProvider.get()).thenReturn(Runnable::run);
        // A Lucene search by alice, run from node1, with sub-tasks on both nodes.
        final TaskProgress search = task(tasks, "node1", "s1", null, "FederatedSearchTask", "alice", 3 * MINUTE,
                "Search 'abc'");
        final TaskProgress node1 = task(tasks, "node1", "s1a", search, "Search node: node1", "alice", 3 * MINUTE,
                "");
        final TaskProgress node2 = task(tasks, "node1", "s1b", search, "Search node: node2", "alice", 3 * MINUTE,
                "");
        task(tasks, "node1", "s1c", node1, "Cluster Search", "alice", 3 * MINUTE, "");
        final TaskProgress cluster = task(tasks, "node2", "s1d", node2, "Cluster Search", "alice", 2 * MINUTE, "");
        task(tasks, "node2", "s1e", cluster, "Search Index Shard", "alice", MINUTE, "shard 7");
        // An Elasticsearch search by bob, and one by alice that has finished and is held for the UI.
        task(tasks, "node2", "s2", null, "Elasticsearch Cluster Search", "bob", 20 * MINUTE, "Search 'def'");
        task(tasks, "node1", "s3", null, "FederatedSearchTask", "alice", HOUR + MINUTE,
                "Search 'ghi' - staying alive for UI requests");
        // Not searches: a poll for results, processing, and a long-running job.
        task(tasks, "node1", "p1", null, "Dashboard Search", "alice", 0, "Polling for new search results");
        final TaskProgress processor = task(tasks, "node2", "p2", null, "Data Processor", "processor", 10 * MINUTE,
                "");
        task(tasks, "node2", "p3", processor, "Pipeline Step", "processor", 9 * MINUTE, "");
        task(tasks, "node2", "j1", null, "Index Shard Manager", "admin", 2 * HOUR, "Flushing");
        final ServerTasksTool tool = new ServerTasksTool(fixture.workspace, fixture.securityContext,
                () -> taskResource, () -> nodeService, new SimpleTaskContextFactory(), executorProvider,
                fixture.auditLog());
        final ToolRunner run = arguments -> tool.execute(request(ServerTasksTool.NAME, arguments),
                ToolCalls.inChat(CHAT, fixture.workspace)).text();

        assertOutput("server_tasks-summary", "{}", run, fixture.written);
        assertOutput("server_tasks-search", "{\"kind\":\"search\"}", run, fixture.written);
        assertOutput("server_tasks-search-list", "{\"kind\":\"search\",\"mode\":\"list\"}", run, fixture.written);
        assertOutput("server_tasks-list", "{\"mode\":\"list\"}", run, fixture.written);
        assertOutput("server_tasks-tree", "{\"mode\":\"tree\",\"id\":\"s1\"}", run, fixture.written);
        assertOutput("server_tasks-tree-sub-task", "{\"mode\":\"tree\",\"id\":\"s1d\"}", run, fixture.written);
    }

    private static TaskProgress task(final Map<String, List<TaskProgress>> tasks,
                                     final String nodeName,
                                     final String id,
                                     final TaskProgress parent,
                                     final String name,
                                     final String user,
                                     final long ageMs,
                                     final String info) {
        final TaskId taskId = new TaskId(id, parent == null
                ? null
                : parent.getId());
        final TaskProgress task = new TaskProgress(taskId, name, info,
                UserRef.builder().uuid(user + "-uuid").subjectId(user).displayName(user).build(),
                "thread-" + id, nodeName, TASK_NOW - ageMs, TASK_NOW, null, null);
        tasks.computeIfAbsent(nodeName, k -> new ArrayList<>()).add(task);
        return task;
    }

    // --------------------------------------------------------------------------------
    // document_links
    // --------------------------------------------------------------------------------

    @Test
    void documentLinks() {
        final ToolTestFixture fixture = new ToolTestFixture(tempDir);
        final ProcessorFilterService processorFilterService = Mockito.mock(ProcessorFilterService.class);
        final DocRef feed = fixture.add(new DocRef(FeedDoc.TYPE, "feed", "WINDOWS-EVENTS"), null, true);
        final DocRef hiddenFeed = fixture.add(new DocRef(FeedDoc.TYPE, "hidden-feed", "SECRET-EVENTS"), null, false);
        final DocRef pipeline = fixture.add(new DocRef(PipelineDoc.TYPE, "pipeline", "Windows pipeline"), null,
                true);
        final DocRef hiddenPipeline = fixture.add(new DocRef(PipelineDoc.TYPE, "hidden-pipeline", "Secret pipeline"),
                null, false);
        final DocRef xslt = fixture.add(new DocRef(XsltDoc.TYPE, "xslt", "Windows XSLT"), null, true);
        final DocRef textConverter = fixture.add(new DocRef(TextConverterDoc.TYPE, "converter", "Secret converter"),
                null, false);
        final DocRef dictionary = fixture.add(new DocRef(DictionaryDoc.TYPE, "dictionary", "Admins"), null, true);
        fixture.folders.put("pipeline", List.of("Windows", "Pipelines"));
        // Processor filters aren't documents: the graph names them by UUID only.
        final DocRef filter = new DocRef(ProcessorFilter.ENTITY_TYPE, "filter", null);
        final DocRef otherFilter = new DocRef(ProcessorFilter.ENTITY_TYPE, "other-filter", null);
        final DocRef hiddenFilter = new DocRef(ProcessorFilter.ENTITY_TYPE, "hidden-filter", null);
        fixture.link(filter, feed);
        fixture.link(filter, hiddenFeed);
        fixture.link(filter, pipeline);
        fixture.link(otherFilter, pipeline);
        fixture.link(hiddenFilter, feed);
        fixture.link(hiddenFilter, hiddenPipeline);
        fixture.link(pipeline, xslt);
        fixture.link(pipeline, textConverter);
        fixture.link(xslt, dictionary);
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION))
                .thenReturn(true);
        stubTimeLimit(fixture.workspace);
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addDocRefTerm(MetaFields.FEED, Condition.IS_DOC_REF, feed)
                .addTextTerm(MetaFields.TYPE, Condition.EQUALS, "Raw Events")
                .build();
        Mockito.when(processorFilterService.fetchByUuid("filter")).thenReturn(Optional.of(ProcessorFilter.builder()
                .id(7)
                .uuid("filter")
                .pipelineUuid("pipeline")
                .pipelineName("Windows pipeline")
                .enabled(true)
                .queryData(QueryData.builder().expression(expression).build())
                .build()));
        // One that selects everything.
        Mockito.when(processorFilterService.fetchByUuid("other-filter")).thenReturn(Optional.of(
                ProcessorFilter.builder()
                        .id(8)
                        .uuid("other-filter")
                        .pipelineUuid("pipeline")
                        .pipelineName("Windows pipeline")
                        .build()));
        final DocumentLinksTool tool = new DocumentLinksTool(fixture.workspace, () -> fixture.dependencyService,
                fixture.documentAccess(), fixture.processorFilterAccess(processorFilterService),
                fixture.auditLog());
        final ToolRunner run = arguments -> tool.execute(request(DocumentLinksTool.NAME, arguments),
                ToolCalls.inChat(CHAT, fixture.workspace)).text();

        assertOutput("document_links-dependants-of-a-feed", "{\"uuids\":[\"feed\"],\"direction\":\"dependants\","
                                                            + "\"depth\":3}", run, fixture.written);
        assertOutput("document_links-pipeline-filters", "{\"uuids\":[\"pipeline\"],\"depth\":1}", run,
                fixture.written);
        assertOutput("document_links-dependencies", "{\"uuids\":[\"pipeline\",\"hidden-pipeline\"],"
                                                    + "\"direction\":\"dependencies\",\"depth\":2}", run,
                fixture.written);
        assertOutput("document_links-limit-and-out", "{\"uuids\":[\"feed\"],\"depth\":3,\"limit\":1,"
                                                     + "\"out\":\"/outputs/links.txt\"}", run, fixture.written);
    }

    // --------------------------------------------------------------------------------
    // read_document
    // --------------------------------------------------------------------------------

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void readDocument() {
        final DocRef xslt = new DocRef(XsltDoc.TYPE, "xslt-uuid", "Event translation");
        final DocRef top = new DocRef(DictionaryDoc.TYPE, "top-uuid", "Malicious IPs");
        final DocRef middle = new DocRef(DictionaryDoc.TYPE, "middle-uuid", "Known bad");
        final DocRef bottom = new DocRef(DictionaryDoc.TYPE, "bottom-uuid", "Botnets");
        final DocRef secret = new DocRef(DictionaryDoc.TYPE, "secret-uuid", "Secret IPs");
        final DocRef pipeline = new DocRef(PipelineDoc.TYPE, "pipe-uuid", "Firewall pipeline");
        final DocRef feed = new DocRef(FeedDoc.TYPE, "feed-uuid", "FIREWALL-EVENTS");
        final ExplorerNodeService explorerNodeService = Mockito.mock(ExplorerNodeService.class);
        final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
        final WordListProvider wordListProvider = Mockito.mock(WordListProvider.class);
        final TabSnapshots tabSnapshots = Mockito.mock(TabSnapshots.class);
        final ProcessorFilterService processorFilterService = Mockito.mock(ProcessorFilterService.class);
        final Map<DocumentTypeName, DocumentActionHandler> handlers = new HashMap<>();
        final List<String> viewable = List.of(xslt.getUuid(), top.getUuid(), middle.getUuid(), bottom.getUuid(),
                pipeline.getUuid());
        final DocumentAccess documentAccess = new DocumentAccess(() -> explorerNodeService, securityContext,
                () -> handlers, Mockito.mock(AiAuditLog.class));
        final ReadDocumentTool tool = new ReadDocumentTool(workspace, documentAccess,
                new ProcessorFilterAccess(securityContext, () -> processorFilterService, documentAccess),
                () -> wordListProvider, tabSnapshots);
        Mockito.when(tabSnapshots.unsavedPart(Mockito.anyInt(), Mockito.any(), Mockito.any()))
                .thenReturn(Optional.empty());
        Mockito.when(tabSnapshots.forLatestQuestion(Mockito.anyInt())).thenReturn(Optional.empty());
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        stubTimeLimit(workspace);
        Mockito.when(securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION)).thenReturn(true);
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(), Mockito.eq(DocumentPermission.VIEW)))
                .thenAnswer(invocation -> viewable.contains(invocation.<DocRef>getArgument(0).getUuid()));
        Mockito.when(explorerNodeService.getNodeByUuid(Mockito.anyString())).thenAnswer(invocation ->
                List.of(xslt, top, middle, bottom, secret, pipeline).stream()
                        .filter(docRef -> docRef.getUuid().equals(invocation.getArgument(0)))
                        .findFirst()
                        .map(docRef -> ExplorerNode.builder().docRef(docRef).build()));
        Mockito.when(explorerNodeService.getPath(Mockito.any())).thenReturn(List.of(ExplorerConstants.SYSTEM_NODE,
                ExplorerNode.builder().docRef(new DocRef(ExplorerConstants.FOLDER_TYPE, "folder-uuid", "Firewalls"))
                        .build()));
        // An XSLT, read through its indexable content.
        final DocumentActionHandler<?> xsltHandler = Mockito.mock(DocumentActionHandler.class,
                Mockito.withSettings().extraInterfaces(ContentIndexable.class));
        Mockito.when(((ContentIndexable) xsltHandler).getIndexableData(xslt)).thenReturn(Map.of("xsl", """
                <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
                  <xsl:template match="/">
                    <Events/>
                  </xsl:template>
                </xsl:stylesheet>"""));
        handlers.put(new DocumentTypeName(XsltDoc.TYPE), xsltHandler);
        // Dictionaries importing others two deep, one the user can't view.
        final Map<String, DictionaryDoc> dictionaries = new HashMap<>();
        dictionaries.put(top.getUuid(), dictionary(top, "1.1.1.1", middle, secret));
        dictionaries.put(middle.getUuid(), dictionary(middle, "2.2.2.2", bottom));
        dictionaries.put(bottom.getUuid(), dictionary(bottom, "3.3.3.3\n1.1.1.1"));
        final DocumentActionHandler<?> dictionaryHandler = Mockito.mock(DocumentActionHandler.class);
        Mockito.when(dictionaryHandler.readDocument(Mockito.any())).thenAnswer(invocation ->
                dictionaries.get(invocation.<DocRef>getArgument(0).getUuid()));
        handlers.put(new DocumentTypeName(DictionaryDoc.TYPE), dictionaryHandler);
        // As Stroom combines them: imports first, duplicates removed.
        final WordList.Builder combined = WordList.builder(true);
        combined.addWord("3.3.3.3", bottom);
        combined.addWord("1.1.1.1", bottom);
        combined.addWord("2.2.2.2", middle);
        Mockito.when(wordListProvider.getCombinedWordList(top)).thenReturn(combined.build());
        // A processor filter, with what it selects.
        Mockito.when(processorFilterService.fetchByUuid("filter-uuid")).thenReturn(Optional.of(
                ProcessorFilter.builder()
                        .id(7)
                        .uuid("filter-uuid")
                        .pipelineUuid(pipeline.getUuid())
                        .pipelineName(pipeline.getName())
                        .enabled(true)
                        .priority(10)
                        .maxProcessingTasks(5)
                        .queryData(QueryData.builder().expression(ExpressionOperator.builder()
                                .addDocRefTerm(MetaFields.FEED, Condition.IS_DOC_REF, feed)
                                .addTextTerm(MetaFields.TYPE, Condition.EQUALS, "Raw Events")
                                .build()).build())
                        .minMetaCreateTimeMs(Instant.parse("2026-01-01T00:00:00Z").toEpochMilli())
                        .processorFilterTracker(ProcessorFilterTracker.builder()
                                .status(ProcessorFilterTrackerStatus.COMPLETE)
                                .metaCount(3L)
                                .build())
                        .build()));
        final ToolRunner run = arguments -> tool.execute(request(ReadDocumentTool.NAME, arguments),
                ToolCalls.inChat(CHAT, workspace)).text();

        assertOutput("read_document-xslt", "{\"uuid\":\"xslt-uuid\"}", run, new HashMap<>());
        assertOutput("read_document-processor-filter", "{\"uuid\":\"filter-uuid\"}", run, new HashMap<>());
        assertOutput("read_document-dictionary-with-imports", "{\"uuid\":\"top-uuid\"}", run, new HashMap<>());
        assertOutput("read_document-not-viewable", "{\"uuid\":\"secret-uuid\"}", run, new HashMap<>());
    }

    private static DictionaryDoc dictionary(final DocRef docRef, final String words, final DocRef... imports) {
        return new DictionaryDoc(docRef.getUuid(), docRef.getName(), null, null, null, null, null, null, words,
                List.of(imports));
    }

    // --------------------------------------------------------------------------------
    // folder_contents
    // --------------------------------------------------------------------------------

    @Test
    void folderContents() {
        final DocRef security = folder("security-uuid", "Security");
        final DocRef firewalls = folder("firewalls-uuid", "Firewalls");
        final DocRef secretFolder = folder("secret-folder-uuid", "Secret");
        final DocRef feed = new DocRef(FeedDoc.TYPE, "feed-uuid", "FIREWALL-EVENTS");
        final DocRef secretFeed = new DocRef(FeedDoc.TYPE, "secret-feed-uuid", "SECRET-EVENTS");
        final DocRef pipeline = new DocRef(PipelineDoc.TYPE, "pipe-uuid", "Firewall pipeline");
        final DocRef secretPipeline = new DocRef(PipelineDoc.TYPE, "secret-pipe-uuid", "Secret pipe");
        final DocRef xslt = new DocRef(XsltDoc.TYPE, "xslt-uuid", "Firewall XSLT");
        final ToolTestFixture fixture = new ToolTestFixture(tempDir);
        final MetaService metaService = Mockito.mock(MetaService.class);
        final ProcessorFilterService processorFilterService = Mockito.mock(ProcessorFilterService.class);
        final ProcessorTaskService processorTaskService = Mockito.mock(ProcessorTaskService.class);
        final ExplorerService explorerService = Mockito.mock(ExplorerService.class);
        final DocFinder docFinder = Mockito.mock(DocFinder.class);
        final Map<String, List<DocRef>> children = new HashMap<>();
        for (final DocRef docRef : List.of(security, firewalls, feed, pipeline, xslt)) {
            fixture.add(docRef, null, true);
        }
        for (final DocRef docRef : List.of(secretFolder, secretFeed, secretPipeline)) {
            fixture.add(docRef, null, false);
        }
        children.put(ExplorerConstants.SYSTEM_DOC_REF.getUuid(), List.of(security));
        children.put(security.getUuid(), List.of(firewalls, secretFolder));
        children.put(firewalls.getUuid(), List.of(xslt, feed, pipeline, secretFeed));
        Mockito.when(fixture.explorerNodeService.getChildren(Mockito.any())).thenAnswer(invocation ->
                children.getOrDefault(invocation.<DocRef>getArgument(0).getUuid(), List.of()).stream()
                        .map(docRef -> ExplorerNode.builder().docRef(docRef).build())
                        .toList());
        Mockito.when(fixture.explorerNodeService.getNodesByName(Mockito.any(), Mockito.anyString()))
                .thenAnswer(invocation -> children.getOrDefault(invocation.<ExplorerNode>getArgument(0).getUuid(),
                                List.of()).stream()
                        .filter(docRef -> docRef.getName().equals(invocation.getArgument(1)))
                        .map(docRef -> ExplorerNode.builder().docRef(docRef).build())
                        .toList());
        fixture.folders.put(security.getUuid(), List.of());
        fixture.folders.put(firewalls.getUuid(), List.of("Security"));
        fixture.folders.put(secretFolder.getUuid(), List.of("Security"));
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION))
                .thenReturn(true);
        Mockito.when(docFinder.findByName(FeedDoc.TYPE, feed.getName())).thenReturn(List.of(feed));
        Mockito.when(docFinder.findByName(FeedDoc.TYPE, secretFeed.getName())).thenReturn(List.of(secretFeed));
        final long day = Duration.ofDays(1).toMillis();
        final long created = Instant.parse("2026-10-01T00:00:00Z").toEpochMilli();
        Mockito.when(metaService.getSelectionSummary(Mockito.any())).thenReturn(new SelectionSummary(42, 1,
                Set.of("FIREWALL-EVENTS"), 2, Set.of("Raw Events"), 1, 1, 1,
                Set.of("Unlocked"), new Range<>(created, created + day)));
        Mockito.when(metaService.findRows(Mockito.any())).thenReturn(new ResultPage<>(List.of(
                new MetaRow(stream(7, "Events", created + HOUR), pipeline, Map.of()),
                new MetaRow(stream(6, "Raw Events", created), null, Map.of()))));
        final ProcessorFilterTracker tracker = ProcessorFilterTracker.builder()
                .status(ProcessorFilterTrackerStatus.COMPLETE)
                .metaCount(12L)
                .eventCount(3_400L)
                .build();
        final List<ProcessorListRow> rows = new ArrayList<>();
        rows.add(new ProcessorFilterRow(filter(1, pipeline).priority(20).enabled(true)
                .processorFilterTracker(tracker).build()));
        rows.add(new ProcessorFilterRow(filter(2, secretPipeline).enabled(true).build()));
        rows.add(new ProcessorFilterRow(filter(3, pipeline).deleted(true).build()));
        rows.add(new ProcessorFilterRow(filter(4, pipeline).enabled(false)
                .queryData(QueryData.builder().expression(ExpressionOperator.builder()
                        .addDocRefTerm(MetaFields.FEED, Condition.IS_DOC_REF, feed)
                        .addTextTerm(MetaFields.TYPE, Condition.EQUALS, "Raw Events")
                        .build()).build())
                .minMetaCreateTimeMs(created)
                .maxMetaCreateTimeMs(created + day)
                .build()));
        Mockito.when(processorFilterService.find(Mockito.any(FetchProcessorRequest.class)))
                .thenReturn(ResultPage.createUnboundedList(rows));
        Mockito.when(processorTaskService.findSummary(Mockito.any())).thenReturn(ResultPage.createUnboundedList(
                List.of(new ProcessorTaskSummary(pipeline, feed.getName(), 10, TaskStatus.COMPLETE, 30L),
                        new ProcessorTaskSummary(pipeline, feed.getName(), 10, TaskStatus.QUEUED, 4L),
                        new ProcessorTaskSummary(pipeline, secretFeed.getName(), 10, TaskStatus.QUEUED, 9L),
                        new ProcessorTaskSummary(secretPipeline, feed.getName(), 10, TaskStatus.QUEUED, 5L))));
        final FolderContentsTool tool = new FolderContentsTool(fixture.documentAccess(),
                fixture.processorFilterAccess(processorFilterService), () -> metaService,
                () -> processorFilterService, () -> processorTaskService, () -> docFinder,
                fixture.agentInstructions(), fixture.auditLog(), () -> explorerService);
        final ToolRunner run = arguments -> tool.execute(request(FolderContentsTool.NAME, arguments),
                ToolCalls.inChat(CHAT, fixture.workspace)).text();

        assertOutput("folder_contents-documents", "{\"folder\":\"firewalls-uuid\"}", run, fixture.written);
        assertOutput("folder_contents-tree", "{\"folder\":\"/Security\",\"depth\":3}", run, fixture.written);
        assertOutput("folder_contents-everything", "{\"folder\":\"firewalls-uuid\",\"show\":[\"documents\","
                                                   + "\"streams\",\"processors\",\"tasks\"]}", run,
                fixture.written);
    }

    private static DocRef folder(final String uuid, final String name) {
        return new DocRef(ExplorerConstants.FOLDER_TYPE, uuid, name);
    }

    private static Meta stream(final long id, final String type, final long createMs) {
        return Meta.builder()
                .id(id)
                .feedName("FIREWALL-EVENTS")
                .typeName(type)
                .status(Status.UNLOCKED)
                .createMs(createMs)
                .build();
    }

    private static ProcessorFilter.Builder filter(final int id, final DocRef pipeline) {
        return ProcessorFilter.builder()
                .id(id)
                .uuid("filter-" + id + "-uuid")
                .pipelineUuid(pipeline.getUuid())
                .pipelineName(pipeline.getName());
    }

    // --------------------------------------------------------------------------------
    // run_stroomql
    // --------------------------------------------------------------------------------

    @Test
    void runStroomQl() throws IOException {
        final ToolTestFixture fixture = new ToolTestFixture(tempDir);
        stubTimeLimit(fixture.workspace);
        Mockito.when(fixture.workspace.localFile(Mockito.anyInt(), Mockito.any(WorkspacePath.class)))
                .thenAnswer(invocation -> {
                    final Path file = Files.createTempFile(tempDir, "local", ".md");
                    Files.writeString(file, fixture.written.get(invocation.<WorkspacePath>getArgument(1).path()),
                            StandardCharsets.UTF_8);
                    return file;
                });
        final QueryService queryService = Mockito.mock(QueryService.class);
        final ResourceStore resourceStore = Mockito.mock(ResourceStore.class);
        final QueryKey queryKey = new QueryKey("q1");
        Mockito.when(queryService.getReferencedDataSource(Mockito.anyString()))
                .thenReturn(Optional.of(new DocRef("View", "view-uuid", "Firewall events")));
        Mockito.when(queryService.search(Mockito.any()))
                .thenReturn(new DashboardSearchResponse(null, queryKey, null, null, null, true, null, null));
        final ResourceKey resourceKey = new ResourceKey("k", "results.md");
        Mockito.when(queryService.downloadSearchResults(Mockito.any()))
                .thenReturn(new ResourceGeneration(resourceKey, List.of()));
        Mockito.when(resourceStore.getTempFile(resourceKey)).thenAnswer(invocation -> {
            final Path results = tempDir.resolve("results.md");
            if (!Files.exists(results)) {
                Files.writeString(results, """
                        | Host | N | StreamId | EventId |
                        | --- | --- | --- | --- |
                        | fw1 | 3 | 100 | 1 |
                        | fw2 | 5 | 100 | 2 |
                        | fw3 | 5 | 101 | 1 |
                        """, StandardCharsets.UTF_8);
            }
            return results;
        });
        final RunStroomQlTool tool = new RunStroomQlTool(fixture.workspace, () -> queryService,
                () -> resourceStore, Mockito.mock(AgentSearches.class));
        final ToolRunner run = arguments -> tool.execute(request(RunStroomQlTool.NAME, arguments),
                ToolCalls.inChat(CHAT, fixture.workspace)).text();

        assertOutput("run_stroomql-result", "{\"query\":\"from \\\"Firewall events\\\" select Host, count() as N "
                                            + "group by Host\",\"out\":\"/outputs/hosts.md\"}", run,
                fixture.written);
        Mockito.when(queryService.search(Mockito.any()))
                .thenReturn(new DashboardSearchResponse(null, queryKey, null, List.of("Unknown field 'Hst'"), null,
                        true, null, null));
        assertOutput("run_stroomql-error", "{\"query\":\"from \\\"Firewall events\\\" select Hst limit 10\","
                                           + "\"out\":\"/outputs/hosts-2.md\"}", run, new HashMap<>());
    }

    // --------------------------------------------------------------------------------
    // current_results
    // --------------------------------------------------------------------------------

    @Test
    void currentResults() throws IOException {
        final DocRef queryDoc = new DocRef("Query", "query-uuid", "Logons");
        final DocRef view = new DocRef("View", "view-uuid", "Windows events");
        final String ran = "from \"Windows events\" select User";
        final QueryKey tabKey = new QueryKey("tab-key");
        final FakeTabWorkspace files = new FakeTabWorkspace(tempDir);
        final TabSnapshots tabSnapshots = Mockito.mock(TabSnapshots.class);
        final TableDownloader tableDownloader = Mockito.mock(TableDownloader.class);
        final DataSourceAccess dataSourceAccess = Mockito.mock(DataSourceAccess.class);
        final QueryService queryService = Mockito.mock(QueryService.class);
        final long available = 25;
        Mockito.when(queryService.getReferencedDataSource(Mockito.anyString())).thenReturn(Optional.of(view));
        Mockito.when(dataSourceAccess.canUse(view)).thenReturn(true);
        Mockito.when(queryService.search(Mockito.any())).thenAnswer(invocation -> {
            final QuerySearchRequest request = invocation.getArgument(0);
            return new DashboardSearchResponse("node1", request.getQueryKey(), null, null, null, true,
                    List.of(new TableResult("table", null, null, null, available, null, null)), null);
        });
        Mockito.when(queryService.findFields(Mockito.any())).thenReturn(new ResultPage<>(List.of(
                QueryField.createId("StreamId"), QueryField.createId("EventId"), QueryField.createText("User"))));
        Mockito.when(tableDownloader.download(Mockito.any(), Mockito.anyLong(), Mockito.any())).thenAnswer(
                invocation -> {
                    final long max = invocation.getArgument(1);
                    final Path target = invocation.getArgument(2);
                    final long rows = Math.min(max, available);
                    final StringBuilder sb = new StringBuilder("| User |\n| --- |\n");
                    for (int i = 0; i < rows; i++) {
                        sb.append("| user").append(i % 4).append(" |\n");
                    }
                    Files.writeString(target, sb.toString(), StandardCharsets.UTF_8);
                    return new TableDownloader.Downloaded(rows, 1, available > max);
                });
        final AiTabSearch search = new AiTabSearch("Logons", null, queryContext(ran, tabKey),
                queryContext(ran, null), List.of("User"), available, false);
        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.of(new StoredTabSnapshot(5, 1,
                queryDoc, "/Security", "Logons", "Query", false, null, "h", 0L, List.of(), List.of(search),
                null)));
        final CurrentResultsTool tool = new CurrentResultsTool(files.workspace(), tabSnapshots, tableDownloader,
                dataSourceAccess, () -> queryService, () -> Mockito.mock(DashboardService.class),
                TableAnalysisConfig::new, Mockito.mock(AiAuditLog.class), Mockito.mock(AgentSearches.class));
        final ToolRunner run = arguments -> tool.execute(request(CurrentResultsTool.NAME, arguments),
                ToolCalls.inChat(CHAT, files.workspace())).text();
        final Map<String, String> written = new TreeMap<>();
        // Gives the files the call wrote or changed.
        final ToolRunner runAndRead = arguments -> {
            final Map<String, String> before = new HashMap<>();
            files.paths().forEach(path -> before.put(path, files.content(path).orElse("")));
            final String result = run.run(arguments);
            written.clear();
            for (final String path : files.paths()) {
                final String content = files.content(path).orElse("");
                if (!content.equals(before.get(path))) {
                    written.put(path, content);
                }
            }
            return result;
        };

        assertOutput("current_results-snapshot", "{}", runAndRead, written);
        assertOutput("current_results-first-rows", "{\"rows\":10}", runAndRead, written);
        assertOutput("current_results-more-rows-than-held", "{\"rows\":5000}", runAndRead, written);
    }

    private static QueryTableContext queryContext(final String query, final QueryKey queryKey) {
        return new QueryTableContext("Query 'Logons'", "node1", QuerySearchRequest.builder()
                .query(query)
                .queryKey(queryKey)
                .queryContext(QueryContext.builder().build())
                .build());
    }

    // --------------------------------------------------------------------------------
    // stream_meta
    // --------------------------------------------------------------------------------

    @Test
    void streamMeta() throws IOException {
        final long day1 = Instant.parse("2026-10-01T10:00:00Z").toEpochMilli();
        final long day2 = Instant.parse("2026-10-02T11:00:00Z").toEpochMilli();
        final ToolTestFixture fixture = new ToolTestFixture(tempDir);
        final MetaService metaService = Mockito.mock(MetaService.class);
        final AttributeMapFactory attributeMapFactory = Mockito.mock(AttributeMapFactory.class);
        final Store store = Mockito.mock(Store.class);
        fixture.add(new DocRef(FeedDoc.TYPE, "11111111-1111-1111-1111-111111111111", "WINDOWS-EVENTS"), null, true);
        fixture.add(new DocRef(PipelineDoc.TYPE, "33333333-3333-3333-3333-333333333333", "Windows pipeline"), null,
                true);
        stubTimeLimit(fixture.workspace);
        final List<MetaRow> rows = List.of(
                metaRow(1, day1, Map.of("Raw Size", "100", "Read Count", "5")),
                // The database's values for this stream have aged off.
                metaRow(2, day1, Map.of()),
                metaRow(3, day2, Map.of("Raw Size", "50")));
        final Map<Long, Map<String, String>> storeAttributes = Map.of(2L, new TreeMap<>(Map.of("Raw Size", "200",
                "Feed", "WINDOWS-EVENTS")));
        final Map<Long, List<Map<String, String>>> storeParts = Map.of(
                1L, List.of(Map.of("RemoteHost", "fw1"), Map.of("RemoteHost", "fw2")),
                2L, List.of(Map.of("RemoteHost", "fw3")),
                3L, List.of(new TreeMap<>(Map.of("RemoteHost", "fw1", "Compression", "ZIP"))));
        Mockito.when(metaService.findRows(Mockito.any())).thenAnswer(invocation -> new ResultPage<>(rows));
        Mockito.when(attributeMapFactory.getAttributes(Mockito.anyLong())).thenAnswer(invocation ->
                storeAttributes.getOrDefault(invocation.<Long>getArgument(0), Map.of()));
        Mockito.when(attributeMapFactory.getAttributeMapForPart(Mockito.anyLong(), Mockito.anyLong()))
                .thenAnswer(invocation -> new AttributeMap(storeParts.get(invocation.<Long>getArgument(0))
                        .get(invocation.<Long>getArgument(1).intValue())));
        Mockito.when(store.openSource(Mockito.anyLong())).thenAnswer(invocation -> {
            final Source source = Mockito.mock(Source.class);
            Mockito.when(source.count()).thenReturn((long) storeParts.get(invocation.<Long>getArgument(0)).size());
            return source;
        });
        final StreamMetaTool tool = new StreamMetaTool(fixture.workspace, () -> metaService,
                () -> attributeMapFactory, () -> store, fixture.documentAccess(), fixture.auditLog(),
                Mockito.mock(StreamReader.class), fixture.feedAccess());
        final ToolRunner run = arguments -> tool.execute(request(StreamMetaTool.NAME, arguments),
                ToolCalls.inChat(CHAT, fixture.workspace)).text();

        assertOutput("stream_meta-attributes", "{\"feeds\":[\"WINDOWS-EVENTS\"],\"attributes\":[\"Raw Size\"],"
                                               + "\"from\":\"2026-10-01\",\"to\":\"2026-10-03\"}", run,
                fixture.written);
        assertOutput("stream_meta-parts", "{\"feeds\":[\"WINDOWS-EVENTS\"],\"parts\":true,"
                                          + "\"attributes\":[\"RemoteHost\"]}", run, fixture.written);
        assertOutput("stream_meta-grouped", "{\"feeds\":[\"WINDOWS-EVENTS\"],\"groupBy\":[\"day\"],"
                                            + "\"aggregates\":[\"count\",\"sum(Raw Size)\",\"max:Raw Size\"]}", run,
                fixture.written);
    }

    private static MetaRow metaRow(final long id, final long createMs, final Map<String, String> attributes) {
        final Meta meta = Meta.builder()
                .id(id)
                .feedName("WINDOWS-EVENTS")
                .typeName("Raw Events")
                .status(Status.UNLOCKED)
                .createMs(createMs)
                .build();
        return new MetaRow(meta, null, new HashMap<>(attributes));
    }

    // --------------------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------------------

    /// Calls the tool and checks what it gives, and what it has written to the workspace, against the reference
    /// file. The workspace files are cleared once checked, so each reference holds only what its call wrote.
    private static void assertOutput(final String name,
                                     final String arguments,
                                     final ToolRunner runner,
                                     final Map<String, String> written) {
        final String output = runner.run(arguments);
        final StringBuilder sb = new StringBuilder();
        sb.append("Arguments: ").append(arguments).append("\n\n");
        sb.append("=== Output ===\n").append(withCallTimesFixed(name, output)).append('\n');
        new TreeMap<>(written).forEach((path, content) -> sb.append("\n=== Workspace file ").append(path)
                .append(" ===\n").append(withCallTimesFixed(name, content.replace("\r\n", "\n"))).append('\n'));
        written.clear();
        GoldenFiles.assertMatches(FOLDER + name + ".txt", sb.toString());
    }

    // Times a tool takes from the clock when it is called, which no tool can be given: when the server tasks were
    // read, and when a snapshot of results was taken. Every other time in the output is from the fixture.
    private static String withCallTimesFixed(final String name, final String text) {
        if (name.startsWith("server_tasks")) {
            return text.replaceFirst("a snapshot as of " + ISO_TIME.pattern(), "a snapshot as of " + CALL_TIME);
        }
        if (name.startsWith("current_results")) {
            return text
                    .replaceAll("taken " + ISO_TIME.pattern(), "taken " + CALL_TIME)
                    // The record of a snapshot holds when it was taken.
                    .replaceAll("\"takenAtMs\" ?: ?\\d+", "\"takenAtMs\" : 0");
        }
        return text;
    }

    private static ToolCall request(final String name, final String arguments) {
        return new ToolCall("call-1", name, arguments);
    }

    private static void stubTimeLimit(final ChatWorkspace workspace) {
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
    }


    // --------------------------------------------------------------------------------


    /// Runs a tool with the given arguments, as the model calls it.
    @FunctionalInterface
    private interface ToolRunner {

        String run(String arguments);
    }
}
