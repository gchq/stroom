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

import stroom.ai.api.AiService;
import stroom.ai.api.TableSummariser;
import stroom.ai.api.TableSummaryRequest;
import stroom.ai.api.TableSummaryResult;
import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.AiConfig;
import stroom.ai.impl.AiDao;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.dao.AiDaoModule;
import stroom.ai.impl.db.AiDbConnProvider;
import stroom.ai.impl.db.AiDbModule;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.MockSourceDocModule;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.impl.workspace.WorkspaceStore;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChat;
import stroom.collection.api.CollectionService;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.data.store.api.Store;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.query.api.QueryKey;
import stroom.query.impl.QueryService;
import stroom.query.shared.DownloadQueryResultsRequest;
import stroom.query.shared.QuerySearchRequest;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.test.common.util.db.DbTestModule;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.UserRef;
import stroom.util.time.StroomDuration;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestDataPipelineTools {

    private static final List<String> TABLES = List.of(
            "ai_chat_file_chunk",
            "ai_chat_run",
            "ai_memory",
            "ai_tool_approval",
            "ai_chat_message",
            "ai_chat_attachment",
            "ai_chat");

    @TempDir
    Path tempDir;

    @Inject
    private AiDao aiDao;
    @Inject
    private WorkspaceStore workspaceStore;
    @Inject
    private AiDbConnProvider aiDbConnProvider;
    @Inject
    private ChatWorkspace workspace;

    private final AiService aiService = Mockito.mock(AiService.class);
    private final TableSummariser tableSummariser = Mockito.mock(TableSummariser.class);
    private final SecurityContext injectorSecurityContext = Mockito.mock(SecurityContext.class);
    private final CurrentResultsTool currentResultsTool = Mockito.mock(CurrentResultsTool.class);
    private AiConfig aiConfig = new AiConfig();
    private int chatId;
    private int otherChatId;

    @BeforeEach
    void setUp() throws SQLException {
        final Injector injector = Guice.createInjector(
                new DbTestModule(),
                new AiDbModule(),
                new AiDaoModule(),
                new MockSourceDocModule(),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(WordListProvider.class).toInstance(Mockito.mock(WordListProvider.class));
                        bind(CollectionService.class).toInstance(Mockito.mock(CollectionService.class));
                        bind(DocFinder.class).toInstance(Mockito.mock(DocFinder.class));
                        bind(TempDirProvider.class).toInstance(() -> tempDir);
                        bind(SecurityContext.class).toInstance(injectorSecurityContext);
                        bind(AiService.class).toInstance(aiService);
                        bind(AiConfig.class).toProvider(() -> aiConfig);
                    }
                });
        injector.injectMembers(this);
        try (final Connection connection = aiDbConnProvider.getConnection()) {
            DbTestUtil.clearTables(connection, TABLES);
        }
        final AiChat chat = aiDao.createChat(UserRef.builder().uuid("user1").build());
        final AiChat otherChat = aiDao.createChat(UserRef.builder().uuid("user1").build());
        chatId = chat.getId();
        otherChatId = otherChat.getId();
        Mockito.when(aiService.getChat(chatId)).thenReturn(chat);
        Mockito.when(aiService.getChat(otherChatId)).thenReturn(otherChat);

        final StringBuilder events = new StringBuilder();
        for (int i = 1; i <= 300; i++) {
            events.append(i % 3 == 0
                    ? "ERROR"
                    : "INFO").append(" event ").append(i).append(" host=fw").append(i % 2).append('\n');
        }
        workspace.write(chatId, WorkspacePath.parse("/outputs/events.log"), events.toString(), null);
        workspaceStore.createFile(chatId, AiAttachmentType.WORKSPACE_FILE, "/attachments/7.md", null,
                "| host | n |\n| --- | --- |\n| fw1 | 3 |\n");
    }

    @Test
    void testPipeline() {
        final PipelineTool pipeline = pipeline();

        assertThat(run(pipeline, "{\"steps\":["
                                 + "{\"tool\":\"grep\",\"args\":{\"path\":\"/outputs/events.log\","
                                 + "\"pattern\":\"ERROR\"}},"
                                 + "{\"tool\":\"grep\",\"args\":{\"pattern\":\"fw1\"}}]}"))
                .startsWith("Step 1 (grep): Wrote 100 matching line(s)")
                .contains("ERROR event 3 host=fw1")
                .doesNotContain("host=fw0");
        // Intermediate files are removed.
        assertThat(workspace.list(chatId, WorkspacePath.parse("/outputs/")))
                .extracting(entry -> entry.path().path())
                .containsExactly("/outputs/events.log");
    }

    @Test
    void testPipelineTimeLimitIsAWorkspaceStepsUnlessAStepTakesLonger() {
        final PipelineTool pipeline = pipeline();
        final Duration toolLimit = workspace.limits().getToolTimeLimit().getDuration();
        final Duration queryLimit = workspace.limits().getQueryTimeLimit().getDuration();
        Mockito.when(currentResultsTool.timeLimit(Mockito.any())).thenReturn(queryLimit);
        final ToolCallContext context = ToolCalls.inChat(chatId, workspace);

        assertThat(pipeline.timeLimit(request("{\"steps\":["
                                              + "{\"tool\":\"grep\",\"args\":{\"pattern\":\"x\"}},"
                                              + "{\"tool\":\"table\",\"args\":{}}]}"), context))
                .isEqualTo(toolLimit);
        assertThat(pipeline.timeLimit(request("{\"steps\":["
                                              + "{\"tool\":\"fetch_events\",\"args\":{}},"
                                              + "{\"tool\":\"table\",\"args\":{}}]}"), context))
                .isEqualTo(queryLimit);
        assertThat(pipeline.timeLimit(request("{\"steps\":[{\"tool\":\"current_results\",\"args\":{}}]}"),
                context))
                .isEqualTo(queryLimit);
        // Steps that can't be read get a workspace step's limit, and are refused when run.
        assertThat(pipeline.timeLimit(request("{\"steps\":\"nonsense\"}"), context))
                .isEqualTo(toolLimit);
    }

    @Test
    void testPipelineStartsFromASnapshotOfTheTabsResults() {
        Mockito.when(currentResultsTool.timeLimit(Mockito.any())).thenReturn(Duration.ofMinutes(1));
        Mockito.when(currentResultsTool.outcome(Mockito.any(), Mockito.argThat(inChat(chatId)))).thenReturn(
                new CurrentResultsTool.Outcome("Took a snapshot of Logons: 1 row(s).\nState: ok", "/attachments/7.md"));

        final String result = run(pipeline(), "{\"steps\":["
                                              + "{\"tool\":\"current_results\",\"args\":{\"withEventIds\":true}},"
                                              + "{\"tool\":\"table\",\"args\":{\"select\":[\"host\"]}}]}");

        assertThat(result)
                .startsWith("Step 1 (current_results): Took a snapshot of Logons: 1 row(s).")
                .contains("fw1");
        final ArgumentCaptor<ToolCall> step = ArgumentCaptor.forClass(ToolCall.class);
        Mockito.verify(currentResultsTool).outcome(step.capture(), Mockito.argThat(inChat(chatId)));
        assertThat(step.getValue().arguments()).contains("\"withEventIds\":true");
    }

    @Test
    void testPipelineStopsWhenNoSnapshotCanBeTaken() {
        Mockito.when(currentResultsTool.timeLimit(Mockito.any())).thenReturn(Duration.ofMinutes(1));
        Mockito.when(currentResultsTool.outcome(Mockito.any(), Mockito.argThat(inChat(chatId)))).thenReturn(
                CurrentResultsTool.Outcome.problem("Getting the events means running its search again."));

        assertThat(run(pipeline(), "{\"steps\":["
                                   + "{\"tool\":\"current_results\",\"args\":{\"withEventIds\":true}},"
                                   + "{\"tool\":\"table\",\"args\":{}}]}"))
                .isEqualTo("Getting the events means running its search again.");
    }

    @Test
    void testPipelineTakesASnapshotOnlyAsItsFirstStep() {
        assertThat(run(pipeline(), "{\"steps\":["
                                   + "{\"tool\":\"grep\",\"args\":{\"path\":\"/outputs/events.log\","
                                   + "\"pattern\":\"ERROR\"}},"
                                   + "{\"tool\":\"current_results\",\"args\":{}}]}"))
                .contains("current_results can only be the first step");
        Mockito.verify(currentResultsTool, Mockito.never()).outcome(Mockito.any(), Mockito.any());
    }

    @Test
    void testPipelineToAFile() {
        workspace.write(chatId, WorkspacePath.parse("/outputs/events.json"),
                "{\"host\":\"fw1\",\"n\":3}\n{\"host\":\"fw2\",\"n\":5}\n{\"host\":\"fw1\",\"n\":1}\n", null);

        assertThat(run(pipeline(), "{\"steps\":["
                                   + "{\"tool\":\"jq\",\"args\":{\"path\":\"/outputs/events.json\","
                                   + "\"query\":\".host\",\"raw\":true}},"
                                   + "{\"tool\":\"grep\",\"args\":{\"path\":\"$prev\",\"pattern\":\"fw1\"}}],"
                                   + "\"out\":\"/outputs/fw1.txt\"}"))
                .contains("Wrote 2 matching line(s) to /outputs/fw1.txt.");
    }

    @Test
    void testPipelineIsConfined() {
        final PipelineTool pipeline = pipeline();

        assertThat(run(pipeline, "{\"steps\":[{\"tool\":\"write_file\",\"args\":{}}]}"))
                .contains("cannot be a pipeline step");
        assertThat(run(pipeline, "{\"steps\":\"grep /etc/passwd | sh\"}")).startsWith("Error: Give the steps");
        assertThat(run(pipeline, "{\"steps\":[" + "{\"tool\":\"grep\",\"args\":{}},".repeat(10)
                                 + "{\"tool\":\"grep\",\"args\":{}}]}"))
                .contains("at most 10 steps");
        assertThat(run(pipeline, "{\"steps\":[{\"tool\":\"grep\",\"args\":{\"path\":\"/etc/passwd\","
                                 + "\"pattern\":\"root\"}}]}"))
                .startsWith("Error: Step 1 (grep) failed:");
        assertThat(run(pipeline, "{\"steps\":[{\"tool\":\"grep\",\"args\":{\"pattern\":\"x\"}}]}"))
                .contains("Step 1 needs a path");
    }

    @Test
    void testSummariseColumn_patternsThatFitAreGivenAsTheyAre() {
        workspace.write(chatId, WorkspacePath.parse("/outputs/messages.csv"),
                "message\nLogin failed for 10.0.0.1\nLogin failed for 10.0.0.2\nDisk 91% full\n", null);

        assertThat(run(summariseTool(), "{\"path\":\"/outputs/messages.csv\",\"column\":\"message\"}"))
                .contains("3 rows reduce to 2 distinct patterns")
                .contains("| Login failed for <ip> | 2 |")
                .contains("| Disk <n>% full | 1 |");
        Mockito.verifyNoInteractions(tableSummariser);
    }

    @Test
    void testSummariseColumn_tooManyPatternsAreSummarisedByTheModelOnce() {
        final StringBuilder csv = new StringBuilder("message\n");
        for (int i = 0; i < 2_000; i++) {
            csv.append("Unusual message kind ").append((char) ('a' + i % 26)).append((char) ('a' + i / 26 % 26))
                    .append((char) ('a' + i / 676)).append('\n');
        }
        workspace.write(chatId, WorkspacePath.parse("/outputs/many.csv"), csv.toString(), null);
        Mockito.when(tableSummariser.summarise(Mockito.any()))
                .thenReturn(TableSummaryResult.summarised("Mostly unusual messages."));
        // With the run's model document too, as in a real chat: a request takes a model or a model ref, not both.
        final OpenAIModelDoc modelDoc = OpenAIModelDoc.builder()
                .uuid("model-uuid")
                .name("model")
                .maxContextWindowTokens(8_000)
                .build();
        final ToolRunContext context = new ToolRunContext(Mockito.mock(ChatModel.class), modelDoc, () -> false);
        Mockito.when(tableSummariser.summarise(Mockito.any())).thenAnswer(invocation -> {
            final TableSummaryRequest request = invocation.getArgument(0);
            assertThat(request.getChatModel()).isNotNull();
            assertThat(request.getModelRef()).isNull();
            assertThat(request.getContextWindowTokens()).isEqualTo(8_000);
            return TableSummaryResult.summarised("Mostly unusual messages.");
        });

        final String first = context.run(() -> run(summariseTool(),
                "{\"path\":\"/outputs/many.csv\",\"column\":\"message\"}"));
        final String second = context.run(() -> run(summariseTool(),
                "{\"path\":\"/outputs/many.csv\",\"column\":\"message\"}"));

        assertThat(first).contains("too many to show").contains("Mostly unusual messages.");
        assertThat(second).contains("summarised before").contains("Mostly unusual messages.");
        Mockito.verify(tableSummariser, Mockito.times(1)).summarise(Mockito.any());
        // Without a run there is no model to summarise with.
        workspace.delete(chatId, WorkspacePath.parse("/outputs/summaries/many_csv-message.md"));
        assertThat(run(summariseTool(), "{\"path\":\"/outputs/many.csv\",\"column\":\"message\"}"))
                .contains("can only be done in a chat");
    }

    @Test
    void testTableFallsBackToSummarisingAfterTwoFailures() {
        workspace.write(chatId, WorkspacePath.parse("/outputs/messages.csv"),
                "message,n\nLogin failed for 10.0.0.1,1\nDisk 91% full,2\n", null);
        final TableTool table = new TableTool(workspace, columnSummariser());
        final ToolRunContext context = new ToolRunContext(Mockito.mock(ChatModel.class), null, () -> false);
        final String badQuery = "{\"path\":\"/outputs/messages.csv\",\"where\":[{\"column\":\"message\","
                                + "\"op\":\"like\",\"value\":\"x\"}]}";

        assertThat(context.run(() -> run(table, badQuery))).startsWith("Error:").doesNotContain("summary");
        assertThat(context.run(() -> run(table, badQuery)))
                .startsWith("Error:")
                .contains("failed 2 times, so here is a summary of column 'message' instead")
                .contains("| Login failed for <ip> | 1 |");
    }

    /// A mistake in an expression is the model's to fix, so however often it is made, the table's column isn't
    /// summarised instead.
    @Test
    void testExpressionMistakesDontFallBackToSummarising() {
        workspace.write(chatId, WorkspacePath.parse("/outputs/messages.csv"),
                "message,n\nLogin failed for 10.0.0.1,1\nDisk 91% full,2\n", null);
        final TableTool table = new TableTool(workspace, columnSummariser());
        final ToolRunContext context = new ToolRunContext(Mockito.mock(ChatModel.class), null, () -> false);
        final String badQuery = "{\"path\":\"/outputs/messages.csv\",\"select\":[\"message\"],"
                                + "\"eval\":[{\"as\":\"y\",\"expression\":\"year(${n})\"}]}";

        for (int i = 0; i < 3; i++) {
            assertThat(context.run(() -> run(table, badQuery)))
                    .startsWith("Error:")
                    .contains("expected 0, got 1")
                    // What year takes is shown with the error.
                    .contains("year()")
                    .doesNotContain("summary");
        }
    }

    @Test
    void testRunStroomQl() throws Exception {
        final QueryService queryService = Mockito.mock(QueryService.class);
        final ResourceStore resourceStore = Mockito.mock(ResourceStore.class);
        final QueryKey queryKey = new QueryKey("q1");
        Mockito.when(queryService.getReferencedDataSource(Mockito.anyString()))
                .thenReturn(Optional.of(new DocRef("View", "view-uuid")));
        Mockito.when(queryService.search(Mockito.any()))
                .thenReturn(response(queryKey, false, null), response(queryKey, true, null));
        final Path results = tempDir.resolve("results.md");
        Files.writeString(results, "| Host | N |\n| --- | --- |\n| fw1 | 3 |\n| fw2 | 5 |\n");
        final ResourceKey resourceKey = new ResourceKey("k", "results.md");
        Mockito.when(queryService.downloadSearchResults(Mockito.any()))
                .thenReturn(new ResourceGeneration(resourceKey, List.of()));
        Mockito.when(resourceStore.getTempFile(resourceKey)).thenReturn(results);
        final AgentSearches agentSearches = Mockito.mock(AgentSearches.class);
        final RunStroomQlTool tool = new RunStroomQlTool(workspace, () -> queryService, () -> resourceStore,
                agentSearches);
        Mockito.when(injectorSecurityContext.hasDocumentPermission(Mockito.any(), Mockito.any())).thenReturn(true);

        final String result = run(tool, "{\"query\":\"from \\\"My View\\\" select Host, count() as N group by "
                                        + "Host\",\"out\":\"/outputs/hosts.md\"}");

        assertThat(result)
                .startsWith("Ran the query. The results are in /outputs/hosts.md.")
                .contains("2 rows, 2 columns");
        assertThat(workspace.readLines(chatId, WorkspacePath.parse("/outputs/hosts.md"), 2, 2))
                .containsExactly("| fw1 | 3 |", "| fw2 | 5 |");
        // The results record their data source, for the S6 checks.
        assertThat(workspace.sourceDocUuid(chatId, WorkspacePath.parse("/outputs/hosts.md"))).isEqualTo("view-uuid");
        Mockito.verify(resourceStore).deleteTempFile(resourceKey);
        // The second search carried on the first rather than starting again.
        final ArgumentCaptor<QuerySearchRequest> requests = ArgumentCaptor.forClass(QuerySearchRequest.class);
        Mockito.verify(queryService, Mockito.times(2)).search(requests.capture());
        assertThat(requests.getAllValues().get(1).getQueryKey()).isEqualTo(queryKey);
        assertThat(requests.getAllValues().getFirst().isStoreHistory()).isFalse();
        // A query without a limit is given one, and told so.
        assertThat(requests.getAllValues().getFirst().getQuery())
                .isEqualTo("from \"My View\" limit 10000\nselect Host, count() as N group by Host");
        assertThat(result).contains("The query had no limit, so 'limit 10000'");
        // The search is removed once its results are written.
        Mockito.verify(agentSearches).destroy(queryKey);
        // All the rows the limit allows are downloaded, not just the few the search was polled for.
        final ArgumentCaptor<DownloadQueryResultsRequest> download =
                ArgumentCaptor.forClass(DownloadQueryResultsRequest.class);
        Mockito.verify(queryService).downloadSearchResults(download.capture());
        assertThat(download.getValue().getSearchRequest().getRequestedRange().getLength()).isEqualTo(10_000L);
    }

    @Test
    void theModelIsToldWhenTheRowLimitApplies() {
        final StroomQlLimit.Limited kept = new StroomQlLimit.Limited("q", StroomQlLimit.Change.NONE);

        assertThat(RunStroomQlTool.limitNote(kept, 100, 5)).isEmpty();
        assertThat(RunStroomQlTool.limitNote(new StroomQlLimit.Limited("q", StroomQlLimit.Change.LOWERED), 100, 5))
                .contains("The query's limit was over 100");
        // Rows that reach the most may not be all of them, and aren't a count.
        assertThat(RunStroomQlTool.limitNote(kept, 100, 100))
                .contains("there may be more")
                .contains("don't take the row count for a total")
                .contains("use count() with group by");
    }

    @Test
    void testRunStroomQl_errorsAndTimeLimit() {
        final QueryService queryService = Mockito.mock(QueryService.class);
        Mockito.when(queryService.getReferencedDataSource(Mockito.anyString())).thenReturn(Optional.empty());
        Mockito.when(queryService.search(Mockito.any()))
                .thenReturn(response(new QueryKey("q"), true, List.of("Unknown field 'Hst'")));
        final AgentSearches agentSearches = Mockito.mock(AgentSearches.class);
        final RunStroomQlTool tool = new RunStroomQlTool(workspace, () -> queryService,
                () -> Mockito.mock(ResourceStore.class), agentSearches);

        assertThat(run(tool, "{\"query\":\"from x select Hst\"}")).isEqualTo("Error: The query failed: Unknown "
                                                                          + "field 'Hst'");
        assertThat(run(tool, "{}")).isEqualTo("Error: Give a StroomQL query.");
        // A search that failed is removed too.
        Mockito.verify(agentSearches).destroy(new QueryKey("q"));

        // A search that fails after it has started says so, as it may have done some of its work.
        Mockito.when(queryService.search(Mockito.any()))
                .thenReturn(response(new QueryKey("q"), false, null),
                        response(new QueryKey("q"), true, List.of("Elasticsearch is unavailable")));
        assertThat(run(tool, "{\"query\":\"from x\"}")).isEqualTo("Error: The query started, but failed while it "
                                                              + "ran: Elasticsearch is unavailable");
        // As does one the query service gave no response for.
        Mockito.when(queryService.search(Mockito.any())).thenReturn(null);
        assertThat(run(tool, "{\"query\":\"from x\"}")).isEqualTo("Error: The query did not run: Stroom's query "
                                                              + "service gave no response.");
        Mockito.when(queryService.search(Mockito.any()))
                .thenReturn(response(new QueryKey("q"), false, null), null);
        assertThat(run(tool, "{\"query\":\"from x\"}")).isEqualTo("Error: The query started, but Stroom's query "
                                                              + "service then gave no response.");

        // A query that never finishes is stopped at the limit.
        aiConfig = new AiConfig(null, null, null, null, null, null, null, null, null,
                new AiWorkspaceConfig(null, null, null, null, null, null, StroomDuration.ofMillis(800),
                        null, null, null, null, null, null), null,
                null, null, null, null, null, null, null);
        Mockito.when(queryService.search(Mockito.any())).thenReturn(response(new QueryKey("q"), false, null));
        Mockito.clearInvocations(agentSearches);
        assertThat(run(tool, "{\"query\":\"from x\"}")).startsWith("Error: This took too long");
        // One stopped at the time limit is never left running.
        Mockito.verify(agentSearches).destroy(new QueryKey("q"));

        // The longest query an administrator allows.
        aiConfig = new AiConfig(null, null, null, null, null, null, null, null, null,
                new AiWorkspaceConfig(null, null, null, null, null, null, StroomDuration.ofMillis(800), 20, null,
                        null, null, null, null),
                        null, null, null, null, null,
                        null, null, null);
        assertThat(run(tool, "{\"query\":\"from x select a, b, c, d\"}"))
                .isEqualTo("Error: The query may be at most 20 characters.");
        Mockito.clearInvocations(queryService);
        run(tool, "{\"query\":\"from x select a\"}");
        Mockito.verify(queryService, Mockito.atLeastOnce()).search(Mockito.any());
    }

    private PipelineTool pipeline() {
        final ColumnSummariser summariser = columnSummariser();
        return new PipelineTool(workspace, new GrepTool(workspace), new JqTool(workspace), new XPathTool(workspace),
                new TableTool(workspace, summariser),
                new FetchEventsTool(workspace, () -> Mockito.mock(Store.class), () -> injectorSecurityContext,
                        Mockito.mock(AiAuditLog.class), Mockito.mock(StreamReader.class)),
                new SearchEventsTool(workspace), currentResultsTool);
    }

    private SummariseColumnTool summariseTool() {
        return new SummariseColumnTool(workspace, columnSummariser());
    }

    // Matches the context of a call in the chat.
    private static ArgumentMatcher<ToolCallContext> inChat(final int chatId) {
        return context -> context.session().equals(ToolSession.chat(chatId));
    }

    private ColumnSummariser columnSummariser() {
        return new ColumnSummariser(tableSummariser, () -> null);
    }

    private static DashboardSearchResponse response(final QueryKey queryKey,
                                                    final boolean complete,
                                                    final List<String> errors) {
        return new DashboardSearchResponse(null, queryKey, null, errors, null, complete, null, null);
    }

    private String run(final StroomTool tool, final String arguments) {
        return tool.execute(request(arguments), ToolCalls.inChat(chatId, workspace)).text();
    }

    private static ToolCall request(final String arguments) {
        return new ToolCall("x", "t", arguments);
    }
}
