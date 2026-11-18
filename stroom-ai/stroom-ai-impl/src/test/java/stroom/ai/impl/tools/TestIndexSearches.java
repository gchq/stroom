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

import stroom.ai.impl.tools.IndexSearches.Counted;
import stroom.ai.impl.tools.IndexSearches.Found;
import stroom.ai.impl.tools.IndexSearches.Probe;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.dashboard.impl.DashboardService;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.TableResultRequest;
import stroom.docref.DocRef;
import stroom.query.api.DestroyReason;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultStoreInfo;
import stroom.query.api.Row;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchRequestSource.SourceType;
import stroom.query.api.TableResult;
import stroom.query.common.v2.ResultStoreManager;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Severity;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The searches search_events makes of an index, as a dashboard makes them: a search, and a count.
class TestIndexSearches {

    private static final QueryKey KEY = new QueryKey("k");

    private final DashboardService dashboardService = Mockito.mock(DashboardService.class);
    private final AgentSearches agentSearches = Mockito.mock(AgentSearches.class);
    private final IndexSearches indexSearches = new IndexSearches(() -> dashboardService, agentSearches);
    private final Probe probe = new Probe(new DocRef("ElasticIndex", "i", "x"), "x search",
            ExpressionOperator.builder().build(), List.of(IndexSearches.column("documents")), 100);

    @Test
    void aSearchIsDestroyedOnceReadAndOneCutShortSaysSo() {
        // Seen: three whole-index scans carried on in Stroom after the survey stopped waiting for them.
        Mockito.when(dashboardService.search(Mockito.any())).thenReturn(response(false, "12"));

        final Found found = ToolDeadline.run(Duration.ofSeconds(3), () ->
                indexSearches.search(ToolCalls.inChat(1), probe, 10, null));

        assertThat(found.rows()).isEqualTo(List.of(Map.of("documents", "12")));
        assertThat(found.complete()).isFalse();
        Mockito.verify(agentSearches).destroy(KEY);
    }

    @Test
    void aSearchWhoseFirstRequestFailsIsDestroyedByItsOwner() {
        // Before, a first request that failed after Stroom started the search left it running: its key was never
        // read. It is found by the owner made up for it alone.
        Mockito.when(dashboardService.search(Mockito.any())).thenThrow(new IllegalStateException("Search failed"));

        assertThatThrownBy(() -> indexSearches.search(ToolCalls.inChat(1), probe, 10, null))
                .hasMessage("Search failed");

        final ArgumentCaptor<DashboardSearchRequest> sent = ArgumentCaptor.forClass(DashboardSearchRequest.class);
        Mockito.verify(dashboardService).search(sent.capture());
        Mockito.verify(agentSearches).destroyOwnedBy(sent.getValue().getSearchRequestSource().getOwnerDocRef()
                .getUuid());
        Mockito.verify(agentSearches, Mockito.never()).destroy(Mockito.any());
    }

    @Test
    void anAgentsSearchesAreFoundByTheirOwnerAndDestroyed() {
        final ResultStoreManager manager = Mockito.mock(ResultStoreManager.class);
        final QueryKey mine = new QueryKey("mine");
        final ResultStoreInfo agents = Mockito.mock(ResultStoreInfo.class);
        Mockito.when(agents.getSearchRequestSource()).thenReturn(SearchRequestSource.builder()
                .ownerDocRef(new DocRef("Dashboard", "made-up")).build());
        Mockito.when(agents.getQueryKey()).thenReturn(mine);
        final ResultStoreInfo users = Mockito.mock(ResultStoreInfo.class);
        Mockito.when(users.getSearchRequestSource()).thenReturn(SearchRequestSource.builder()
                .ownerDocRef(new DocRef("Dashboard", "a-real-dashboard")).build());
        Mockito.when(users.getQueryKey()).thenReturn(KEY);
        Mockito.when(manager.find(Mockito.any())).thenReturn(new ResultPage<>(List.of(agents, users)));

        new AgentSearches(() -> manager).destroyOwnedBy("made-up");

        Mockito.verify(manager).destroy(mine, DestroyReason.NO_LONGER_NEEDED);
        Mockito.verify(manager, Mockito.never()).destroy(Mockito.eq(KEY), Mockito.any());
    }

    @Test
    void aSearchPollIsNeverHeldPastItsWait() {
        Mockito.when(dashboardService.search(Mockito.any())).thenReturn(response(true, "1"));

        ToolDeadline.run(Duration.ofSeconds(4), () -> indexSearches.search(ToolCalls.inChat(1), probe, 10, null));

        final ArgumentCaptor<DashboardSearchRequest> sent = ArgumentCaptor.forClass(DashboardSearchRequest.class);
        Mockito.verify(dashboardService).search(sent.capture());
        // What is left of the wait (the call's time limit less a margin): a little under 2 s.
        assertThat(sent.getValue().getTimeout()).isBetween(1500L, 2000L);
    }

    @Test
    void aSearchGivenAShorterWaitIsNeverHeldPastIt() {
        // survey_index's sample searches wait a few seconds in all.
        Mockito.when(dashboardService.search(Mockito.any())).thenReturn(response(true, "1"));

        indexSearches.search(ToolCalls.inChat(1), probe, 10, null, Duration.ofSeconds(2));

        final ArgumentCaptor<DashboardSearchRequest> sent = ArgumentCaptor.forClass(DashboardSearchRequest.class);
        Mockito.verify(dashboardService).search(sent.capture());
        assertThat(sent.getValue().getTimeout()).isBetween(1900L, 2000L);
    }

    @Test
    void aSearchIsMadeAsAnUnsavedDashboardMakesItAndKeepsNoHistory() {
        Mockito.when(dashboardService.search(Mockito.any())).thenReturn(response(true, "1"));

        indexSearches.search(ToolCalls.inChat(1), probe, 10, null);

        final ArgumentCaptor<DashboardSearchRequest> sent = ArgumentCaptor.forClass(DashboardSearchRequest.class);
        Mockito.verify(dashboardService).search(sent.capture());
        final DashboardSearchRequest request = sent.getValue();
        assertThat(request.isStoreHistory()).isFalse();
        assertThat(request.getSearchRequestSource().getSourceType()).isEqualTo(SourceType.DASHBOARD_UI);
        assertThat(request.getSearch().getDataSourceRef()).isEqualTo(probe.dataSource());
        final TableResultRequest table = (TableResultRequest) request.getComponentResultRequests().getFirst();
        assertThat(table.getRequestedRange().getLength()).isEqualTo(10L);
        assertThat(table.getTableSettings().getMaxResults()).containsExactly(100L);
        assertThat(table.getTableSettings().getColumns().getFirst().getExpression()).isEqualTo("${documents}");
        assertThat(table.getTableSettings().extractValues()).isFalse();
    }

    @Test
    void eachSearchIsToldToTheClientAsItRuns() {
        // Asked for by the user: describe_document on an index ran for minutes with nothing to show what it was
        // doing.
        Mockito.when(dashboardService.search(Mockito.any()))
                .thenReturn(response(false, "1"))
                .thenReturn(response(true, "1"));
        final List<String> said = new ArrayList<>();
        final ToolCallContext context = ToolCallContext.builder(ToolSession.chat(1))
                .progress(said::add)
                .build();

        indexSearches.search(context, probe, 10, "Searching x for a EQUALS b");

        assertThat(said).hasSize(2);
        assertThat(said.getFirst()).isEqualTo("Searching x for a EQUALS b");
        assertThat(said.get(1)).startsWith("Searching x for a EQUALS b: still searching, ");
        // Polled with the search's key once it started.
        final ArgumentCaptor<DashboardSearchRequest> sent = ArgumentCaptor.forClass(DashboardSearchRequest.class);
        Mockito.verify(dashboardService, Mockito.times(2)).search(sent.capture());
        assertThat(sent.getAllValues().get(0).getQueryKey()).isNull();
        assertThat(sent.getAllValues().get(1).getQueryKey()).isEqualTo(KEY);
    }

    @Test
    void aCountIsOneGroupedRowAndOneCutShortIsAFloor() {
        // Seen: 32,000 documents counted as 100, the rows of a search stopped at its first page.
        final List<DashboardSearchRequest> sent = new ArrayList<>();
        Mockito.when(dashboardService.search(Mockito.any())).thenAnswer(invocation -> {
            sent.add(invocation.getArgument(0));
            return response(true, "all", "1200.0");
        });

        final Counted counted = indexSearches.count(ToolCalls.inChat(1), probe, null);

        assertThat(counted.count()).isEqualTo(1200L);
        assertThat(counted.complete()).isTrue();
        final TableResultRequest table = (TableResultRequest) sent.getFirst().getComponentResultRequests().getFirst();
        assertThat(table.getTableSettings().getColumns()).extracting(column -> column.getExpression())
                .containsExactly("\"all\"", "count()");
        assertThat(table.getRequestedRange().getLength()).isEqualTo(1L);
    }

    @Test
    void noRowsIsACountOfNoneUnlessStroomGaveAnError() {
        Mockito.when(dashboardService.search(Mockito.any()))
                .thenReturn(new DashboardSearchResponse("node1", KEY, null, null, null, true,
                        List.of(new TableResult("table-FIND", List.of(), List.of(), null, 0L, null, null)), null))
                .thenReturn(new DashboardSearchResponse("node1", KEY, null, null, null, true, List.of(),
                        List.of(new ErrorMessage(Severity.ERROR, "Index unavailable"))));

        assertThat(indexSearches.count(ToolCalls.inChat(1), probe, null).count()).isZero();
        final Counted failed = indexSearches.count(ToolCalls.inChat(1), probe, null);
        assertThat(failed.count()).isNull();
        assertThat(failed.errors()).containsExactly("Index unavailable");
    }

    @Test
    void noResponseIsAnError() {
        Mockito.when(dashboardService.search(Mockito.any())).thenReturn(null);

        final Found found = indexSearches.search(ToolCalls.inChat(1), probe, 10, null);

        assertThat(found.rows()).isEmpty();
        assertThat(found.errors()).containsExactly("Stroom's dashboard search gave no response");
        // No key was given: any search Stroom started is found by its owner.
        Mockito.verify(agentSearches).destroyOwnedBy(Mockito.anyString());
        Mockito.verify(agentSearches, Mockito.never()).destroy(Mockito.any());
    }

    private static DashboardSearchResponse response(final boolean complete, final String... values) {
        return new DashboardSearchResponse("node1", KEY, null, null, null, complete,
                List.of(new TableResult("table-FIND", List.of(), List.of(Row.builder().values(List.of(values)).build()),
                        null, 1L, null, null)), null);
    }
}
