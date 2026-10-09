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

package stroom.query.client.presenter;

import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dispatch.client.FakeRestFactory;
import stroom.dispatch.client.FakeRestFactory.FakeRequest;
import stroom.query.api.DestroyReason;
import stroom.query.api.QueryKey;
import stroom.query.api.Result;
import stroom.query.api.TableResult;
import stroom.query.shared.QueryResource;
import stroom.query.shared.QuerySearchRequest;
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

class TestQueryModel {

    private static final String NODE = "node1";
    private static final QueryKey QUERY_KEY = new QueryKey("query-key");

    private final FakeRestFactory restFactory = new FakeRestFactory();
    private final List<Boolean> searching = new ArrayList<>();
    private final List<List<ErrorMessage>> errors = new ArrayList<>();
    private ResultComponent table;
    private ResultStoreModel resultStoreModel;
    private QueryModel queryModel;

    @BeforeEach
    void setUp() {
        // The query resource is made with GWT.create, which only works in a browser
        GWTMockUtilities.disarm();
        resultStoreModel = Mockito.mock(ResultStoreModel.class);
        queryModel = new QueryModel(
                new SimpleEventBus(),
                restFactory,
                Mockito.mock(DateTimeSettingsFactory.class),
                resultStoreModel,
                () -> null);
        table = Mockito.mock(ResultComponent.class);
        queryModel.addResultComponent(QueryModel.TABLE_COMPONENT_ID, table);
        queryModel.addSearchStateListener(searching::add);
        queryModel.addSearchErrorListener(errors::add);
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void startNewSearch_noQuery() {
        queryModel.startNewSearch(QueryModel.TABLE_COMPONENT_ID, "Query", null, List.of(), null,
                false, false, null, null);

        assertThat(restFactory.getRequests())
                .isEmpty();
        assertThat(searching)
                .containsExactly(false);
    }

    @Test
    void poll_requests() {
        startSearch(true);

        // The first poll stores the query in the history, and has no node or key yet
        final QuerySearchRequest first = searchRequest(restFactory.getRequests().getFirst(), null);
        assertThat(first.getQueryKey())
                .isNull();
        assertThat(first.isStoreHistory())
                .isTrue();

        restFactory.getRequests().getFirst().succeed(response(false));

        // The next poll is for the node and key the reply gave, and isn't stored again
        final QuerySearchRequest second = searchRequest(restFactory.getLastRequest(), NODE);
        assertThat(second.getQueryKey())
                .isEqualTo(QUERY_KEY);
        assertThat(second.isStoreHistory())
                .isFalse();
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
    void poll_replyToEarlierSearch() {
        // Regression test: a reply to an earlier search arriving after a new one started threw a
        // NullPointerException (shown as an alert) when it was the finished search's null reply
        startSearch();
        final FakeRequest<?, ?> earlierPoll = restFactory.getRequests().getFirst();
        startSearch(false, 2);

        earlierPoll.succeed(null);

        // Nothing to clean up, and the new search carries on
        Mockito.verifyNoInteractions(resultStoreModel);
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
        final FakeRequest<?, ?> poll = restFactory.getRequests().getFirst();
        poll.succeed(response(false));
        Mockito.clearInvocations(table);

        queryModel.stop();

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
    void startNewSearch_destroysTheLastSearchsStore() {
        startSearch();
        restFactory.getRequests().getFirst().succeed(response(true));

        startSearch(false, 2);

        Mockito.verify(resultStoreModel).destroy(
                Mockito.eq(NODE),
                Mockito.eq(QUERY_KEY),
                Mockito.eq(DestroyReason.NO_LONGER_NEEDED),
                Mockito.any(),
                Mockito.any());
    }

    @Test
    void refresh_beforeSearch() {
        // Nothing has been searched, so there is nothing to refresh and no request
        final List<Result> results = new ArrayList<>();

        queryModel.refresh(QueryModel.TABLE_COMPONENT_ID, results::add);

        assertThat(results)
                .containsExactly((Result) null);
        assertThat(restFactory.getRequests())
                .isEmpty();
    }

    @Test
    void refresh() {
        startSearch();
        restFactory.getRequests().getFirst().succeed(response(true));
        final List<Result> results = new ArrayList<>();

        queryModel.refresh(QueryModel.TABLE_COMPONENT_ID, results::add);

        // The refresh asks for the search's results by its key
        assertThat(searchRequest(restFactory.getLastRequest(), NODE).getQueryKey())
                .isEqualTo(QUERY_KEY);
        restFactory.getLastRequest().succeed(response(true));
        assertThat(results)
                .singleElement()
                .extracting(Result::getComponentId)
                .isEqualTo(QueryModel.TABLE_COMPONENT_ID);
    }

    @Test
    void forceNewSearch() {
        startSearch();
        final List<Result> results = new ArrayList<>();

        queryModel.forceNewSearch(QueryModel.TABLE_COMPONENT_ID, results::add);

        // A forced search has no key, so the server searches again
        assertThat(searchRequest(restFactory.getLastRequest(), null).getQueryKey())
                .isNull();
        restFactory.getLastRequest().succeed(response(true));
        assertThat(results)
                .singleElement()
                .extracting(Result::getComponentId)
                .isEqualTo(QueryModel.TABLE_COMPONENT_ID);
    }

    @Test
    void forceNewSearch_unknownComponent() {
        startSearch();
        final List<Result> results = new ArrayList<>();

        queryModel.forceNewSearch("not-a-component", results::add);

        assertThat(results)
                .containsExactly((Result) null);
        assertThat(restFactory.getRequests())
                .hasSize(1);
    }

    @Test
    void forceNewSearch_fails() {
        // Regression test: a forced new search (as a table makes to re-run its search) has no query
        // key, so its failure handler threw a NullPointerException and the error was never shown
        startSearch();
        final List<Result> results = new ArrayList<>();
        queryModel.forceNewSearch(QueryModel.TABLE_COMPONENT_ID, results::add);

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
        queryModel.forceNewSearch(QueryModel.TABLE_COMPONENT_ID, result -> {
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

    private void startSearch(final boolean storeHistory) {
        startSearch(storeHistory, 1);
    }

    // Starts a search, which sends its first poll; the given number of requests have then been sent.
    private void startSearch(final boolean storeHistory, final int requestCount) {
        queryModel.startNewSearch(QueryModel.TABLE_COMPONENT_ID, "Query", "from Events", List.of(), null,
                false, storeHistory, null, null);
        assertThat(restFactory.getRequests())
                .hasSize(requestCount);
        // Starting a search ends the last one, so only count what the replies do
        Mockito.clearInvocations(table);
    }

    // The search request the request sends, to the given node.
    private static QuerySearchRequest searchRequest(final FakeRequest<?, ?> request, final String node) {
        final QueryResource resource = Mockito.mock(QueryResource.class);
        request.callOn(resource);
        final ArgumentCaptor<QuerySearchRequest> searchRequest = ArgumentCaptor.forClass(QuerySearchRequest.class);
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
                List.of(TableResult.builder().componentId(QueryModel.TABLE_COMPONENT_ID).build()),
                errors);
    }
}
