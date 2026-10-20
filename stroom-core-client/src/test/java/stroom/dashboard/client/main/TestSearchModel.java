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

package stroom.dashboard.client.main;

import stroom.dashboard.shared.DashboardResource;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dispatch.client.FakeRestFactory;
import stroom.dispatch.client.FakeRestFactory.FakeRequest;
import stroom.docref.DocRef;
import stroom.query.api.DestroyReason;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.QueryKey;
import stroom.query.api.Result;
import stroom.query.api.ResultRequest.Fetch;
import stroom.query.api.TableResult;
import stroom.query.client.presenter.DateTimeSettingsFactory;
import stroom.query.client.presenter.ResultStoreModel;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.Severity;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestSearchModel {

    private static final String TABLE_ID = "table-1";
    private static final String NODE = "node1";
    private static final QueryKey QUERY_KEY = new QueryKey("query-key");
    private static final DocRef INDEX = new DocRef("Index", "index-uuid", "Index");

    private final FakeRestFactory restFactory = new FakeRestFactory();
    private final List<Boolean> searching = new ArrayList<>();
    private final List<List<ErrorMessage>> errors = new ArrayList<>();
    private IndexLoader indexLoader;
    private ResultComponent table;
    private ResultStoreModel resultStoreModel;
    private SearchModel searchModel;

    @BeforeEach
    void setUp() {
        // The dashboard resource is made with GWT.create, which only works in a browser
        GWTMockUtilities.disarm();
        indexLoader = Mockito.mock(IndexLoader.class);
        Mockito.when(indexLoader.getLoadedDataSourceRef()).thenReturn(INDEX);
        resultStoreModel = Mockito.mock(ResultStoreModel.class);
        searchModel = new SearchModel(
                new SimpleEventBus(),
                restFactory,
                indexLoader,
                Mockito.mock(DateTimeSettingsFactory.class),
                resultStoreModel);
        searchModel.init(new DocRef("Dashboard", "dashboard-uuid", "Dashboard"), "query-1");
        table = Mockito.mock(ResultComponent.class);
        Mockito.when(table.getSettings()).thenReturn(TableComponentSettings.builder().build());
        searchModel.addComponent(TABLE_ID, table);
        searchModel.addSearchStateListener(searching::add);
        searchModel.addSearchErrorListener(errors::add);
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void startNewSearch_noDataSource() {
        Mockito.when(indexLoader.getLoadedDataSourceRef()).thenReturn(null);

        searchModel.startNewSearch(ExpressionOperator.builder().build(), List.of(), null, false, false, null,
                null, null);

        assertThat(restFactory.getRequests())
                .isEmpty();
        assertThat(searching)
                .containsExactly(false);
    }

    @Test
    void poll_requests() {
        searchModel.startNewSearch(ExpressionOperator.builder().build(), List.of(), null, false, true, null,
                null, null);

        // The first poll stores the query in the history, and fetches all of each component's data
        final DashboardSearchRequest first = searchRequest(restFactory.getRequests().getFirst(), null);
        assertThat(first.getQueryKey())
                .isNull();
        assertThat(first.isStoreHistory())
                .isTrue();
        assertThat(first.getSearch().getDataSourceRef())
                .isEqualTo(INDEX);
        Mockito.verify(table).getResultRequest(Fetch.ALL);

        restFactory.getRequests().getFirst().succeed(response(false));

        // The next poll is for the node and key the reply gave, isn't stored again, and fetches changes
        final DashboardSearchRequest second = searchRequest(restFactory.getLastRequest(), NODE);
        assertThat(second.getQueryKey())
                .isEqualTo(QUERY_KEY);
        assertThat(second.isStoreHistory())
                .isFalse();
        Mockito.verify(table).getResultRequest(Fetch.CHANGES);
    }

    @Test
    void poll_pausedComponent() {
        // A paused component asks for none of its data
        Mockito.when(table.isPaused()).thenReturn(true);

        searchModel.startNewSearch(ExpressionOperator.builder().build(), List.of(), null, false, false, null,
                null, null);

        Mockito.verify(table).getResultRequest(Fetch.NONE);
    }

    @Test
    void startNewSearch_resume() {
        // Resuming a search carries on with the search the server already has
        searchModel.startNewSearch(ExpressionOperator.builder().build(), List.of(), null, false, false, null,
                NODE, QUERY_KEY);

        assertThat(searchRequest(restFactory.getLastRequest(), NODE).getQueryKey())
                .isEqualTo(QUERY_KEY);
    }

    @Test
    void poll_incomplete() {
        startSearch();

        restFactory.getRequests().getFirst().succeed(response(false));

        // Still running, so it polls again
        assertThat(restFactory.getRequests())
                .hasSize(2);
        assertThat(searching.getLast())
                .isTrue();
        Mockito.verify(table).setData(Mockito.any());
    }

    @Test
    void poll_complete() {
        startSearch();

        restFactory.getRequests().getFirst().succeed(response(true));

        assertThat(restFactory.getRequests())
                .hasSize(1);
        assertThat(searching.getLast())
                .isFalse();
        Mockito.verify(table).endSearch();
    }

    @Test
    void poll_nullReply() {
        // A search the server has finished with replies with null
        startSearch();

        restFactory.getRequests().getFirst().succeed(null);

        assertThat(restFactory.getRequests())
                .hasSize(1);
        assertThat(searching.getLast())
                .isFalse();
        Mockito.verify(table).endSearch();
    }

    @Test
    void poll_errorMessages() {
        startSearch();
        final ErrorMessage error = new ErrorMessage(Severity.WARNING, "Some shards were unavailable");

        restFactory.getRequests().getFirst().succeed(response(true, List.of(error)));

        assertThat(errors.getLast())
                .containsExactly(error);
    }

    @Test
    void poll_resultsFailToShow() {
        // Regression test: if handling a reply threw, the search was never seen to finish, so the
        // client polled for ever with the search shown as running and no error
        Mockito.doThrow(new IllegalStateException("Bad row")).when(table).setData(Mockito.any());
        startSearch();

        restFactory.getRequests().getFirst().succeed(response(false));

        expectEndedWithError("Bad row");
        // No one will poll it again, so the search is stopped on the server
        expectTerminated();
    }

    @Test
    void poll_fails() {
        // Regression test: a failed poll (e.g. the connection dropped) showed its error but left the
        // search shown as running, so the Query button stayed at Stop and no auto refresh was
        // scheduled again
        startSearch();

        restFactory.getRequests().getFirst().fail(new RuntimeException("Connection reset"));

        expectEndedWithError("Connection reset");
        // The first poll failed, so there is no search on the server to stop
        Mockito.verify(resultStoreModel, Mockito.never())
                .terminate(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void poll_failsLater() {
        // A poll that fails after the server has started the search stops it there
        startSearch();
        restFactory.getRequests().getFirst().succeed(response(false));

        restFactory.getLastRequest().fail(new RuntimeException("Connection reset"));

        assertThat(restFactory.getRequests())
                .hasSize(2);
        assertThat(searching.getLast())
                .isFalse();
        expectTerminated();
    }

    @Test
    void poll_failsForEarlierSearch() {
        // Regression test: a failed poll of an earlier search polled the current search again, so
        // it was polled twice over
        startSearch();
        final FakeRequest<?, ?> earlierPoll = restFactory.getRequests().getFirst();
        startSearch(false, 2);

        earlierPoll.fail(new RuntimeException("Connection reset"));

        // Ignored: no error, and the current search is still polled once
        assertThat(errors)
                .isEmpty();
        assertThat(restFactory.getRequests())
                .hasSize(2);
        assertThat(searching.getLast())
                .isTrue();
    }

    @Test
    void poll_replyWithResultsToEarlierSearch() {
        // A reply with results to an earlier search is not shown, and its store is destroyed
        startSearch();
        final FakeRequest<?, ?> earlierPoll = restFactory.getRequests().getFirst();
        startSearch(false, 2);

        earlierPoll.succeed(response(false));

        Mockito.verify(table, Mockito.never()).setData(Mockito.any());
        Mockito.verify(resultStoreModel).destroy(
                Mockito.eq(NODE),
                Mockito.eq(QUERY_KEY),
                Mockito.eq(DestroyReason.NO_LONGER_NEEDED),
                Mockito.any(),
                Mockito.any());
        assertThat(restFactory.getRequests())
                .hasSize(2);
    }

    @Test
    void stop() {
        startSearch();
        restFactory.getRequests().getFirst().succeed(response(false));
        Mockito.clearInvocations(table);

        searchModel.stop();

        Mockito.verify(resultStoreModel).terminate(Mockito.eq(NODE), Mockito.eq(QUERY_KEY), Mockito.any(),
                Mockito.any());
        Mockito.verify(table).endSearch();
        assertThat(searching.getLast())
                .isFalse();
        // A reply to a poll already sent doesn't start polling again
        restFactory.getLastRequest().succeed(response(false));
        assertThat(restFactory.getRequests())
                .hasSize(2);
    }

    @Test
    void refresh_beforeSearch() {
        // Nothing has been searched, so there is nothing to refresh and no request
        final List<Result> results = new ArrayList<>();

        searchModel.refresh(TABLE_ID, results::add);

        assertThat(results)
                .containsExactly((Result) null);
        assertThat(restFactory.getRequests())
                .isEmpty();
    }

    @Test
    void forceNewSearch() {
        startSearch();
        final List<Result> results = new ArrayList<>();

        searchModel.forceNewSearch(TABLE_ID, results::add);

        // A forced search has no key, so the server searches again
        assertThat(searchRequest(restFactory.getLastRequest(), null).getQueryKey())
                .isNull();
        restFactory.getLastRequest().succeed(response(true));
        assertThat(results)
                .singleElement()
                .extracting(Result::getComponentId)
                .isEqualTo(TABLE_ID);
    }

    @Test
    void forceNewSearch_fails() {
        // Regression test: a forced new search (as a table makes to re-run its search) has no query
        // key, so its failure handler threw a NullPointerException and the error was never shown
        startSearch();
        final List<Result> results = new ArrayList<>();
        searchModel.forceNewSearch(TABLE_ID, results::add);

        restFactory.getLastRequest().fail(new RuntimeException("Search failed"));

        assertThat(errors.getLast())
                .singleElement()
                .satisfies(error -> assertThat(error.getMessage()).contains("Search failed"));
        // The table is still told there is no result
        assertThat(results)
                .containsExactly((Result) null);
    }

    @Test
    void forceNewSearch_failsAfterANewSearch() {
        // The failure of a forced search for an earlier search isn't shown against the new one
        startSearch();
        searchModel.forceNewSearch(TABLE_ID, result -> {
        });
        final FakeRequest<?, ?> forced = restFactory.getLastRequest();
        startSearch(false, 3);

        forced.fail(new RuntimeException("Search failed"));

        assertThat(errors)
                .isEmpty();
    }

    private void startSearch() {
        startSearch(false, 1);
    }

    // Starts a search, which sends its first poll; the given number of requests have then been sent.
    private void startSearch(final boolean storeHistory, final int requestCount) {
        searchModel.startNewSearch(ExpressionOperator.builder().build(), List.of(), null, false, storeHistory,
                null, null, null);
        assertThat(restFactory.getRequests())
                .hasSize(requestCount);
        // Starting a search ends the last one, so only count what the replies do
        Mockito.clearInvocations(table);
    }

    // The search request the request sends, to the given node.
    private static DashboardSearchRequest searchRequest(final FakeRequest<?, ?> request, final String node) {
        final DashboardResource resource = Mockito.mock(DashboardResource.class);
        request.callOn(resource);
        final ArgumentCaptor<DashboardSearchRequest> searchRequest =
                ArgumentCaptor.forClass(DashboardSearchRequest.class);
        Mockito.verify(resource).search(Mockito.eq(node), searchRequest.capture());
        return searchRequest.getValue();
    }

    private void expectTerminated() {
        Mockito.verify(resultStoreModel).terminate(Mockito.eq(NODE), Mockito.eq(QUERY_KEY), Mockito.any(),
                Mockito.any());
    }

    private void expectEndedWithError(final String message) {
        assertThat(restFactory.getRequests())
                .hasSize(1);
        assertThat(searching.getLast())
                .isFalse();
        Mockito.verify(table).endSearch();
        assertThat(errors.getLast())
                .singleElement()
                .satisfies(error -> {
                    assertThat(error.getSeverity()).isEqualTo(Severity.ERROR);
                    assertThat(error.getMessage()).contains(message);
                });
    }

    private static DashboardSearchResponse response(final boolean complete) {
        return response(complete, null);
    }

    private static DashboardSearchResponse response(final boolean complete, final List<ErrorMessage> errors) {
        return new DashboardSearchResponse(
                NODE,
                QUERY_KEY,
                null,
                null,
                null,
                complete,
                List.of(TableResult.builder().componentId(TABLE_ID).build()),
                errors);
    }
}
