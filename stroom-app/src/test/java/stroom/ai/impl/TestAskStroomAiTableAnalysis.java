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
import stroom.ai.api.OpenAIModelStore;
import stroom.ai.api.TableSource;
import stroom.ai.api.TableSummariser;
import stroom.ai.api.TableSummaryRequest;
import stroom.ai.api.TableSummaryResult;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.shared.AiAgentMode;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AiTabSearch;
import stroom.ai.shared.AiTabSnapshot;
import stroom.ai.shared.AiToolAccess;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.AskStroomAiContext;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.DashboardTableContext;
import stroom.ai.shared.QueryTableContext;
import stroom.ai.shared.TableAnalysisConfig;
import stroom.dashboard.impl.DashboardService;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.TableResultRequest;
import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.PermissionInheritance;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.node.api.NodeInfo;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Format;
import stroom.query.api.OffsetRange;
import stroom.query.api.ResultRequest.Fetch;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchRequestSource.SourceType;
import stroom.query.api.TableSettings;
import stroom.query.impl.QueryService;
import stroom.query.shared.QueryContext;
import stroom.query.shared.QueryDoc;
import stroom.query.shared.QuerySearchRequest;
import stroom.test.AbstractAiIntegrationTest;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// Ask Stroom AI analysing a large table of results the user has open, end to end: a real search (a StroomQL
/// query, or a dashboard table) over thousands of streams in the real stream store, the chat run through the real
/// chat service and tools, and only the chat model replaced by a scripted model server ([FakeModelServer]). The
/// model takes the table into its workspace with `current_results` and works it out there with `table`, so the
/// rows never pass through its context; and the table summariser reads the same rows in batches.
class TestAskStroomAiTableAnalysis extends AbstractAiIntegrationTest {

    // Streams of three feeds, so the table has more rows than a model could be sent, with counts to work out.
    private static final Map<String, Integer> STREAMS_BY_FEED = Map.of(
            "TABLE-A", 1_500,
            "TABLE-B", 1_000,
            "TABLE-C", 500);
    private static final int TOTAL_STREAMS = 3_000;
    // The rows the user's tab shows, of all the search found.
    private static final long ROWS_SHOWN = 100;
    private static final String STROOM_QL = "from \"Stream Store\" select Feed, Type";
    private static final String TABLE_ID = "table-1";

    @Inject
    private AskStroomAIService askStroomAIService;
    @Inject
    private AiService aiService;
    @Inject
    private OpenAIModelStore modelStore;
    @Inject
    private ExplorerService explorerService;
    @Inject
    private ExplorerNodeService explorerNodeService;
    @Inject
    private MetaService metaService;
    @Inject
    private QueryService queryService;
    @Inject
    private DashboardService dashboardService;
    @Inject
    private NodeInfo nodeInfo;
    @Inject
    private TableDownloader tableDownloader;
    @Inject
    private TableSummariser tableSummariser;
    @Inject
    private AiChatRunManager runManager;

    private FakeModelServer model;
    private UserRef user;

    @BeforeEach
    void setUp() throws IOException {
        model = FakeModelServer.start();
        user = administrator();
        toolPolicy("Everything", AiToolAccess.READ_WRITE, user);
        asUser(user, () -> STREAMS_BY_FEED.forEach((feed, count) -> {
            for (int i = 0; i < count; i++) {
                metaService.create(MetaProperties.builder()
                        .feedName(feed)
                        .typeName(StreamTypeNames.RAW_EVENTS)
                        .createMs(Instant.now().toEpochMilli())
                        .build(), Status.UNLOCKED);
            }
        }));
    }

    @AfterEach
    void tearDown() {
        model.close();
    }

    @Test
    void aLargeQueryTableIsTakenIntoTheWorkspaceAndCountedThereNotInTheContext() {
        final DocRef query = asUser(user, () -> create(QueryDoc.TYPE, "Streams"));
        final QueryTableContext results = asUser(user, this::queryResults);
        final String snapshot = snapshotPath(query, null);
        model.callTool("current_results", "{}")
                .callTool("table", JsonUtil.writeValueAsString(Map.of("path", snapshot,
                        "groupBy", List.of("Feed")), false))
                .answer("TABLE-A has 1500 streams.");

        final Map<String, ToolResultRecord> byTool = ask(query, "Query 'Streams'", search("Results", null, results),
                "Which feed has the most streams?");

        // Every row the search found is in the snapshot, not just those the tab shows.
        final ToolResultRecord taken = byTool.get("current_results");
        assertThat(taken.error()).as(taken.text()).isFalse();
        assertThat(taken.text()).contains(snapshot, String.valueOf(TOTAL_STREAMS));
        // And counted there, over all of them.
        final ToolResultRecord counted = byTool.get("table");
        assertThat(counted.error()).as(counted.text()).isFalse();
        assertThat(counted.text()).startsWith(TOTAL_STREAMS + " of " + TOTAL_STREAMS + " rows matched");
        assertCounts(counted.text());
        assertRowsNotSentToTheModel();
    }

    @Test
    void aSnapshotOfFewerRowsThanTheSearchFoundSaysSoAndOnlyThoseAreCounted() {
        final DocRef query = asUser(user, () -> create(QueryDoc.TYPE, "Streams"));
        final QueryTableContext results = asUser(user, this::queryResults);
        final String snapshot = snapshotPath(query, null);
        model.callTool("current_results", "{\"rows\": 1000}")
                .callTool("table", JsonUtil.writeValueAsString(Map.of("path", snapshot,
                        "groupBy", List.of("Feed")), false))
                .answer("Of the first 1000 streams, most are TABLE-A's.");

        final Map<String, ToolResultRecord> byTool = ask(query, "Query 'Streams'", search("Results", null, results),
                "Which feed has the most streams?");

        // The snapshot says it holds only some of the rows the search found.
        final ToolResultRecord taken = byTool.get("current_results");
        assertThat(taken.error()).as(taken.text()).isFalse();
        assertThat(taken.text()).contains("1000", String.valueOf(TOTAL_STREAMS));
        // The counts are of those rows only.
        final ToolResultRecord counted = byTool.get("table");
        assertThat(counted.error()).as(counted.text()).isFalse();
        assertThat(counted.text()).startsWith("1000 of 1000 rows matched");
        assertThat(rowCounts(counted.text()).values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(1_000);
    }

    @Test
    void aLargeDashboardTableIsTakenIntoTheWorkspaceAndCountedThereNotInTheContext() {
        final DocRef dashboard = asUser(user, () -> create(DashboardDoc.TYPE, "Streams"));
        final DashboardTableContext results = asUser(user, this::dashboardResults);
        final String snapshot = snapshotPath(dashboard, TABLE_ID);
        model.callTool("current_results", "{\"table\": \"Streams table\"}")
                .callTool("table", JsonUtil.writeValueAsString(Map.of("path", snapshot,
                        "groupBy", List.of("Feed")), false))
                .answer("TABLE-A has 1500 streams.");

        final Map<String, ToolResultRecord> byTool = ask(dashboard, "Dashboard 'Streams'",
                search("Streams table", TABLE_ID, results), "Which feed has the most streams?");

        final ToolResultRecord taken = byTool.get("current_results");
        assertThat(taken.error()).as(taken.text()).isFalse();
        assertThat(taken.text()).contains(snapshot, String.valueOf(TOTAL_STREAMS));
        final ToolResultRecord counted = byTool.get("table");
        assertThat(counted.error()).as(counted.text()).isFalse();
        assertThat(counted.text()).startsWith(TOTAL_STREAMS + " of " + TOTAL_STREAMS + " rows matched");
        assertCounts(counted.text());
        assertRowsNotSentToTheModel();
    }

    @Test
    void aLargeDashboardTableIsSummarisedInBatchesThenMerged() throws IOException {
        final DocRef modelRef = asUser(user, this::fakeModel);
        final DashboardTableContext results = asUser(user, this::dashboardResults);
        final Path file = Files.createTempFile("streams", ".md");
        try {
            final TableDownloader.Downloaded downloaded = asUser(user, () ->
                    tableDownloader.download(results, TableAnalysisConfig.DEFAULT_MAX_TOTAL_ROWS, file));
            assertThat(downloaded.rows()).isEqualTo(TOTAL_STREAMS);
            assertThat(downloaded.truncated()).isFalse();

            final TableSummaryResult summary = asUser(user, () -> tableSummariser.summarise(TableSummaryRequest
                    .builder()
                    .source(new TableSource("dashboard 'Streams'", file, false))
                    .modelRef(modelRef)
                    .config(TableAnalysisConfig.builder().maxRowsPerBatch(1_000).build())
                    .query("Which feed has the most streams?")
                    .build()));

            assertThat(summary.summarised()).as(summary.text()).isTrue();
            assertThat(summary.text()).contains(FakeModelServer.SIDE_ANSWER);
            // Three batches of 1000 rows, each with the table's header, then one request to merge what they found.
            final List<String> requests = model.sideRequests().stream()
                    .map(request -> JsonUtil.writeValueAsString(request.get("messages"), false))
                    .toList();
            final List<String> batches = requests.stream()
                    .filter(request -> request.contains("TABLE-"))
                    .toList();
            assertThat(batches).hasSize(3);
            assertThat(batches).allSatisfy(batch -> assertThat(batch).contains("Feed"));
            assertThat(requests).hasSize(4);
        } finally {
            ChatWorkspace.deleteQuietly(file);
        }
    }

    // Asks a question with the tab attached, and gives each tool's result by its tool.
    private Map<String, ToolResultRecord> ask(final DocRef tab,
                                              final String tabLabel,
                                              final AiTabSearch search,
                                              final String question) {
        final DocRef modelRef = asUser(user, this::fakeModel);
        final AiChat chat = asUser(user, () -> askStroomAIService.createChat());
        final AiTabSnapshot snapshot = new AiTabSnapshot(tab, tabLabel, null, false, List.of(), List.of(search),
                null, null, "tab-hash");
        asUser(user, () -> askStroomAIService.askStroomAi(new AskStroomAiRequest(chat,
                AskStroomAiConfig.builder().modelRef(modelRef).build(), null, question, AiAgentMode.AGENT,
                snapshot)));
        final List<AiChatMessage> messages = asUser(user, () -> answered(chat.getId()));
        return messages.stream()
                .filter(message -> message.getMessageType() == AiMessageType.TOOL_RESULT)
                .map(message -> JsonUtil.readValue(message.getMessage(), ToolResultRecord.class))
                .collect(Collectors.toMap(ToolResultRecord::tool, result -> result, (first, second) -> second));
    }

    private static AiTabSearch search(final String name,
                                      final String componentId,
                                      final AskStroomAiContext results) {
        return new AiTabSearch(name, componentId, results, results, List.of("Feed", "Type"), ROWS_SHOWN, false);
    }

    // The query's results, as the user's tab shows them: the search run as the user, with its key.
    private QueryTableContext queryResults() {
        final QuerySearchRequest request = QuerySearchRequest.builder()
                .searchRequestSource(SearchRequestSource.builder().sourceType(SourceType.QUERY_UI).build())
                .query(STROOM_QL)
                .queryContext(QueryContext.builder().dateTimeSettings(DateTimeSettings.builder().build()).build())
                .incremental(false)
                .timeout(30_000L)
                .storeHistory(false)
                .requestedRange(new OffsetRange(0L, ROWS_SHOWN))
                .build();
        DashboardSearchResponse response = queryService.search(request);
        final long deadline = System.currentTimeMillis() + 30_000;
        while (response != null && NullSafe.isEmptyCollection(response.getErrors()) && !response.isComplete()
               && System.currentTimeMillis() < deadline) {
            response = queryService.search(request.copy().queryKey(response.getQueryKey()).build());
        }
        assertThat(response).isNotNull();
        assertThat(response.getErrors()).as("errors").isNullOrEmpty();
        assertThat(response.isComplete()).isTrue();
        return new QueryTableContext("Query 'Streams'", nodeInfo.getThisNodeName(),
                request.copy().queryKey(response.getQueryKey()).build());
    }

    // The dashboard table's results, as the user's tab shows them: the search run as the user, with its key.
    private DashboardTableContext dashboardResults() {
        final TableSettings table = TableSettings.builder()
                .addColumns(Column.builder().id("feed").name("Feed").expression("${Feed}")
                        .format(Format.TEXT).build())
                .addColumns(Column.builder().id("type").name("Type").expression("${Type}")
                        .format(Format.TEXT).build())
                .addMaxResults((long) TableAnalysisConfig.DEFAULT_MAX_TOTAL_ROWS)
                .build();
        final DashboardSearchRequest request = DashboardSearchRequest.builder()
                .searchRequestSource(SearchRequestSource.builder().sourceType(SourceType.DASHBOARD_UI).build())
                .search(Search.builder()
                        .dataSourceRef(MetaFields.STREAM_STORE_DOC_REF)
                        .expression(ExpressionOperator.builder().build())
                        .incremental(false)
                        .build())
                .componentResultRequests(List.of(TableResultRequest.builder()
                        .componentId(TABLE_ID)
                        .tableSettings(table)
                        .requestedRange(new OffsetRange(0L, ROWS_SHOWN))
                        .fetch(Fetch.ALL)
                        .build()))
                .dateTimeSettings(DateTimeSettings.builder().build())
                .timeout(30_000L)
                .storeHistory(false)
                .build();
        DashboardSearchResponse response = dashboardService.search(request);
        final long deadline = System.currentTimeMillis() + 30_000;
        while (response != null && NullSafe.isEmptyCollection(response.getErrors()) && !response.isComplete()
               && System.currentTimeMillis() < deadline) {
            response = dashboardService.search(request.copy().queryKey(response.getQueryKey()).build());
        }
        assertThat(response).isNotNull();
        assertThat(response.getErrors()).as("errors").isNullOrEmpty();
        assertThat(response.isComplete()).isTrue();
        return new DashboardTableContext("Dashboard 'Streams' table 'Streams table'", nodeInfo.getThisNodeName(),
                request.copy().queryKey(response.getQueryKey()).build());
    }

    // Where current_results keeps the snapshot of a tab's table.
    private static String snapshotPath(final DocRef tab, final String componentId) {
        return "/tabs/results/" + tab.getUuid() + "/" + NullSafe.nonBlankStringElse(componentId, "results") + ".md";
    }

    private static void assertCounts(final String tableResult) {
        assertThat(rowCounts(tableResult)).isEqualTo(STREAMS_BY_FEED);
    }

    // Each feed's count in a grouped table result: its rows that start with a feed's name.
    private static Map<String, Integer> rowCounts(final String tableResult) {
        return tableResult.lines()
                .map(line -> line.replace('|', ' ').replace(',', ' ').strip().split("\\s+"))
                .filter(cells -> cells.length >= 2 && STREAMS_BY_FEED.containsKey(cells[0])
                                 && cells[cells.length - 1].matches("\\d+"))
                .collect(Collectors.toMap(cells -> cells[0], cells -> Integer.parseInt(cells[cells.length - 1])));
    }

    // The rows were never sent to the model: each request it was sent holds only a few of the feeds' names (in the
    // counts and the question), not one per row.
    private void assertRowsNotSentToTheModel() {
        assertThat(model.agentRequests()).allSatisfy(request -> {
            final String messages = JsonUtil.writeValueAsString(request.get("messages"), false);
            assertThat(messages.split("TABLE-A", -1).length - 1).isLessThan(20);
        });
    }

    // A model doc pointing at the fake model server, with tools turned on.
    private DocRef fakeModel() {
        final DocRef ref = modelStore.createDocument("Fake model");
        final OpenAIModelDoc doc = modelStore.readDocument(ref).copy()
                .baseUrl(model.baseUrl())
                .apiKey("key")
                .modelId("fake")
                .toolsEnabled(true)
                .build();
        modelStore.writeDocument(doc);
        return ref;
    }

    private DocRef create(final String type, final String name) {
        return explorerService.create(type, name, explorerNodeService.getRoot(), PermissionInheritance.DESTINATION)
                .getDocRef();
    }

    // The chat's messages once the run has answered (or failed), as the user's client would poll for them, and has
    // ended, so that nothing it does is left to happen once the test's database has gone.
    private List<AiChatMessage> answered(final int chatId) {
        final Instant until = Instant.now().plus(Duration.ofMinutes(2));
        while (Instant.now().isBefore(until)) {
            final List<AiChatMessage> messages = aiService.getMessages(chatId);
            final Optional<AiChatMessage> last = messages.stream()
                    .filter(message -> message.getMessageType() == AiMessageType.AI_RESPONSE
                                       || message.getMessageType() == AiMessageType.ERROR)
                    .findFirst();
            if (last.isPresent() && !runManager.isRunningHere(chatId)) {
                assertThat(last.get().getMessageType())
                        .as("The run failed: %s", last.get().getMessage())
                        .isEqualTo(AiMessageType.AI_RESPONSE);
                return messages;
            }
            try {
                Thread.sleep(200);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("The chat was not answered: " + aiService.getMessages(chatId));
    }
}
