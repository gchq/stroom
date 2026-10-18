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
import stroom.ai.impl.FakeTabWorkspace;
import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.TableDownloader;
import stroom.ai.impl.tools.CurrentResultsTool.Assessment;
import stroom.ai.impl.tools.CurrentResultsTool.ResultsMeta;
import stroom.ai.impl.tools.CurrentResultsTool.State;
import stroom.ai.shared.AiTabSearch;
import stroom.ai.shared.AskStroomAiContext;
import stroom.ai.shared.QueryTableContext;
import stroom.ai.shared.TableAnalysisConfig;
import stroom.dashboard.impl.DashboardService;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.docref.DocRef;
import stroom.query.api.QueryKey;
import stroom.query.api.TableResult;
import stroom.query.impl.QueryService;
import stroom.query.shared.QueryContext;
import stroom.query.shared.QuerySearchRequest;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestCurrentResultsTool {

    private static final int CHAT = 1;
    private static final DocRef QUERY_DOC = new DocRef("Query", "query-uuid", "Logons");
    private static final DocRef VIEW = new DocRef("View", "view-uuid", "Windows events");
    private static final String RAN = "from \"Windows events\" select User";
    private static final QueryKey TAB_KEY = new QueryKey("tab-key");
    private static final QueryKey NEW_KEY = new QueryKey("new-key");

    @TempDir
    Path tempDir;

    private final TabSnapshots tabSnapshots = Mockito.mock(TabSnapshots.class);
    private final TableDownloader tableDownloader = Mockito.mock(TableDownloader.class);
    private final DataSourceAccess dataSourceAccess = Mockito.mock(DataSourceAccess.class);
    private final QueryService queryService = Mockito.mock(QueryService.class);
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private TableAnalysisConfig tableAnalysisConfig = new TableAnalysisConfig();
    private FakeTabWorkspace files;
    private CurrentResultsTool tool;
    // How many rows the table has, and whether its search has finished.
    private long available = 100;
    private boolean finished = true;

    @BeforeEach
    void setUp() throws Exception {
        files = new FakeTabWorkspace(tempDir);
        tool = new CurrentResultsTool(files.workspace(), tabSnapshots, tableDownloader, dataSourceAccess,
                () -> queryService, () -> Mockito.mock(DashboardService.class), () -> tableAnalysisConfig,
                auditLog);
        Mockito.when(queryService.getReferencedDataSource(Mockito.anyString())).thenReturn(Optional.of(VIEW));
        Mockito.when(dataSourceAccess.canUse(VIEW)).thenReturn(true);
        Mockito.when(queryService.search(Mockito.any())).thenAnswer(invocation -> {
            final QuerySearchRequest request = invocation.getArgument(0);
            final QueryKey key = request.getQueryKey() != null
                    ? request.getQueryKey()
                    : NEW_KEY;
            return new DashboardSearchResponse("node1", key, null, null, null, finished,
                    List.of(new TableResult("table", null, null, null, available, null, null)), null);
        });
        Mockito.when(tableDownloader.download(Mockito.any(), Mockito.anyLong(), Mockito.any())).thenAnswer(
                invocation -> {
                    final long max = invocation.getArgument(1);
                    final Path target = invocation.getArgument(2);
                    final long rows = Math.min(max, available);
                    final StringBuilder sb = new StringBuilder("| User |\n| --- |\n");
                    for (int i = 0; i < rows; i++) {
                        sb.append("| user").append(i).append(" |\n");
                    }
                    Files.writeString(target, sb.toString(), StandardCharsets.UTF_8);
                    return new TableDownloader.Downloaded(rows, 1, available > max);
                });
    }

    @Test
    void noSharedTabSaysSo() {
        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.empty());

        assertThat(run("{}")).isEqualTo(CurrentResultsTool.NO_TAB);
        Mockito.verifyNoInteractions(tableDownloader);
    }

    @Test
    void aSnapshotOfTheRowsShownIsTakenAndLogged() {
        tab(RAN, RAN);

        final String result = run("{}");

        assertThat(result)
                .startsWith("Took a snapshot of Logons in Query 'Logons': 100 row(s) of 100 found")
                .contains("State: ok")
                .contains("The rows are in /tabs/results/query-uuid/results.md");
        assertThat(files.content("/tabs/results/query-uuid/results.md")).hasValueSatisfying(table ->
                assertThat(table).contains("| user99 |"));
        // The data source is recorded as its source, so losing Use on it removes the snapshot.
        assertThat(files.source("/tabs/results/query-uuid/results.md")).contains(VIEW.getUuid());
        // Taken from the results the tab shows, without running the search again.
        final ArgumentCaptor<AskStroomAiContext> downloaded = ArgumentCaptor.forClass(AskStroomAiContext.class);
        Mockito.verify(tableDownloader).download(downloaded.capture(), Mockito.eq(50_000L), Mockito.any());
        assertThat(ResultsRequests.queryKey(downloaded.getValue())).isEqualTo(TAB_KEY);
        Mockito.verify(auditLog).searched(Mockito.eq(CHAT), Mockito.eq(VIEW), Mockito.eq(RAN), Mockito.eq(100L),
                Mockito.contains("current_results"));
    }

    @Test
    void rowsWithinTheSnapshotUseOnlyThoseRows() {
        tab(RAN, RAN);
        run("{}");

        final String result = run("{\"rows\":10}");

        assertThat(result)
                .startsWith("Using the snapshot already taken")
                .contains("State: ok")
                .contains("The first 10 row(s), as asked, are in /tabs/results/query-uuid/results-first-10.md");
        assertThat(files.content("/tabs/results/query-uuid/results-first-10.md")).hasValueSatisfying(table ->
                assertThat(table).contains("| user9 |").doesNotContain("| user10 |"));
        Mockito.verify(tableDownloader, Mockito.times(1)).download(Mockito.any(), Mockito.anyLong(),
                Mockito.any());
    }

    @Test
    void rowsBeyondTheSnapshotButWithinTheLimitAreIncompleteWithAnOffer() {
        tab(RAN, RAN);
        run("{\"rows\":5}");

        final String result = run("{\"rows\":50}");

        assertThat(result)
                .contains("State: incomplete")
                .contains("it holds 5 row(s), fewer than the 50 asked for, and more exist")
                .contains("call again with refresh and rows 50");
        // It isn't taken again without refresh.
        Mockito.verify(tableDownloader, Mockito.times(1)).download(Mockito.any(), Mockito.anyLong(),
                Mockito.any());
    }

    @Test
    void rowsBeyondTheGlobalLimitAreRefusedNamingTheLimit() {
        tableAnalysisConfig = TableAnalysisConfig.builder().maxTotalRows(20).build();
        tab(RAN, RAN);

        assertThat(run("{\"rows\":30}"))
                .startsWith("Refused: a snapshot can hold at most 20 rows")
                .contains("Offer the user the first 20 instead");
        Mockito.verifyNoInteractions(tableDownloader);
    }

    @Test
    void aTableSmallerThanTheRowsAskedForIsUsedInFull() {
        available = 7;
        tab(RAN, RAN);

        assertThat(run("{\"rows\":10}"))
                .contains("State: ok")
                .contains("The table has only 7 row(s), fewer than the 10 asked for, so all of them are used.");
    }

    @Test
    void aChangedQueryMakesTheSnapshotStale() {
        tab(RAN, RAN);
        run("{}");
        // The user has edited the query in the tab since.
        tab(RAN, RAN + ", Host");

        assertThat(run("{}"))
                .contains("State: stale - the tab's search has changed since the snapshot was taken (the query)")
                .contains("Offer the user to run it again (refresh)");
        // It is not run again without refresh.
        Mockito.verify(queryService, Mockito.never()).search(Mockito.argThat(request ->
                request.getQueryKey() == null));
    }

    @Test
    void anEditedButUnrunQueryIsStaleAtOnce() {
        tab(RAN, RAN + ", Host");

        assertThat(run("{}")).contains("State: stale").contains("(the query)");
    }

    @Test
    void anUnfinishedSearchIsIncomplete() {
        finished = false;
        tab(RAN, RAN);

        assertThat(run("{}"))
                .contains("State: incomplete - the search hadn't finished when it was taken")
                .contains("Say so before giving figures");
    }

    @Test
    void theSearchIsOnlyRunAgainWithRefresh() {
        tab(RAN, RAN);
        run("{}");
        tab(RAN, RAN + ", Host");
        run("{}");
        Mockito.verify(queryService, Mockito.never()).search(Mockito.argThat(request ->
                request.getQueryKey() == null));

        final String result = run("{\"refresh\":true}");

        assertThat(result)
                .startsWith("Ran the search again and took a snapshot")
                .contains("State: ok");
        // A new search, from the tab as it now stands, whose results are downloaded.
        Mockito.verify(queryService).search(Mockito.argThat(request -> request.getQueryKey() == null
                                                                        && request.getQuery().endsWith("Host")));
        final ArgumentCaptor<AskStroomAiContext> downloaded = ArgumentCaptor.forClass(AskStroomAiContext.class);
        Mockito.verify(tableDownloader, Mockito.times(2)).download(downloaded.capture(), Mockito.anyLong(),
                Mockito.any());
        assertThat(ResultsRequests.queryKey(downloaded.getAllValues().getLast())).isEqualTo(NEW_KEY);
    }

    @Test
    void rerunningTheSearchInTheTabTakesANewSnapshotOfWhatItShows() {
        tab(RAN, RAN);
        run("{}");
        // The user ran the search again in the tab, so it shows other results.
        final QueryKey rerun = new QueryKey("rerun-key");
        tab(RAN, RAN, rerun);

        assertThat(run("{}")).startsWith("Took a snapshot");
        Mockito.verify(tableDownloader, Mockito.times(2)).download(Mockito.any(), Mockito.anyLong(),
                Mockito.any());
    }

    @Test
    void resultsFromADataSourceTheUserCantUseAreRefused() {
        Mockito.when(dataSourceAccess.canUse(VIEW)).thenReturn(false);
        tab(RAN, RAN);

        assertThat(run("{}")).contains("You can't use the data source these results come from");
        Mockito.verifyNoInteractions(tableDownloader);
        Mockito.verify(auditLog, Mockito.never()).searched(Mockito.anyInt(), Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any());
    }

    @Test
    void aQueryThatHasntBeenRunHasNoResults() {
        final QueryTableContext live = context(RAN, null);
        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.of(snapshot(
                new AiTabSearch("Logons", null, null, live, List.of(), null, false))));

        assertThat(run("{}")).contains("hasn't been run").contains("refresh");
        Mockito.verifyNoInteractions(tableDownloader);
    }

    @Test
    void theStateOfASnapshotIsWorkedOut() {
        final ResultsMeta whole = meta(100, 100, false, 100L, true, false);
        assertThat(CurrentResultsTool.assess(whole, null, "h", 50_000).state()).isEqualTo(State.OK);
        assertThat(CurrentResultsTool.assess(whole, null, "other", 50_000).state()).isEqualTo(State.STALE);
        // Not known how the tab now stands: not said to be stale.
        assertThat(CurrentResultsTool.assess(whole, null, null, 50_000).state()).isEqualTo(State.OK);

        final ResultsMeta cut = meta(20, 20, true, 500L, true, true);
        final Assessment cutAssessment = CurrentResultsTool.assess(cut, null, "h", 20);
        assertThat(cutAssessment.state()).isEqualTo(State.INCOMPLETE);
        assertThat(cutAssessment.incomplete()).containsExactly("the global row limit of 20 cut it");

        final ResultsMeta firstFew = meta(5, 5, true, 100L, true, false);
        final Assessment fewAssessment = CurrentResultsTool.assess(firstFew, null, "h", 50_000);
        assertThat(fewAssessment.state()).isEqualTo(State.INCOMPLETE);
        assertThat(fewAssessment.notes()).singleElement().asString().contains("call again with refresh");
    }

    private String run(final String arguments) {
        return tool.execute(ToolExecutionRequest.builder().id("c1").name(CurrentResultsTool.NAME)
                .arguments(arguments).build(), CHAT);
    }

    // The latest question's tab: a query whose results came from one query, and whose editor holds another.
    private void tab(final String ran, final String edited) {
        tab(ran, edited, TAB_KEY);
    }

    private void tab(final String ran, final String edited, final QueryKey resultsKey) {
        final AiTabSearch search = new AiTabSearch("Logons", null, context(ran, resultsKey), context(edited, null),
                List.of("User"), available, false);
        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.of(snapshot(search)));
    }

    private static StoredTabSnapshot snapshot(final AiTabSearch search) {
        return new StoredTabSnapshot(5, 1, QUERY_DOC, "/", "Logons", "Query", false, null, "h", 0L, List.of(),
                List.of(search));
    }

    private static QueryTableContext context(final String query, final QueryKey queryKey) {
        return new QueryTableContext("Query 'Logons'", "node1", QuerySearchRequest.builder()
                .query(query)
                .queryKey(queryKey)
                .queryContext(QueryContext.builder().build())
                .build());
    }

    private static ResultsMeta meta(final long held,
                                    final long wanted,
                                    final boolean truncated,
                                    final Long total,
                                    final boolean finished,
                                    final boolean cutByLimit) {
        return new ResultsMeta(QUERY_DOC, "Logons", null, "/tabs/results/q/results.md", "h", null, 0L, held,
                wanted, truncated, total, finished, cutByLimit, false, "k", null);
    }
}
