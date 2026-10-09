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
import stroom.dispatch.client.RestFactory;
import stroom.dispatch.client.RestFactory.MethodExecutor;
import stroom.dispatch.client.RestFactory.Resource;
import stroom.dispatch.client.RestFactory.TaskExecutor;
import stroom.query.api.QueryKey;
import stroom.query.api.TableResult;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.Severity;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class TestQueryModel {

    private final List<Consumer<Object>> polls = new ArrayList<>();
    private final List<Boolean> searching = new ArrayList<>();
    private final List<List<ErrorMessage>> errors = new ArrayList<>();
    private ResultComponent table;
    private QueryModel queryModel;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // The query resource is made with GWT.create, which only works in a browser
        GWTMockUtilities.disarm();

        // Each search request's success handler is kept, so the test can answer the polls
        final MethodExecutor<?, ?> executor = Mockito.mock(MethodExecutor.class, Mockito.withSettings()
                .extraInterfaces(TaskExecutor.class)
                .defaultAnswer(Answers.RETURNS_SELF));
        Mockito.doAnswer(invocation -> {
            polls.add(invocation.getArgument(0));
            return executor;
        }).when(executor).onSuccess(Mockito.any());
        Mockito.doReturn(executor).when(executor).taskMonitorFactory(Mockito.any());
        Mockito.doReturn(executor).when(executor).taskMonitorFactory(Mockito.any(), Mockito.any());
        final Resource<?> resource = Mockito.mock(Resource.class);
        Mockito.doReturn(executor).when(resource).method(Mockito.any());
        final RestFactory restFactory = Mockito.mock(RestFactory.class);
        Mockito.doReturn(resource).when(restFactory).create(Mockito.any());

        queryModel = new QueryModel(
                new SimpleEventBus(),
                restFactory,
                Mockito.mock(DateTimeSettingsFactory.class),
                Mockito.mock(ResultStoreModel.class),
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
    void poll_resultsFailToShow() {
        // Regression test: if handling a reply threw, the search was never seen to finish, so the
        // client polled for ever with the search shown as running and no error
        Mockito.doThrow(new IllegalStateException("Bad row")).when(table).setData(Mockito.any());
        startSearch();

        polls.getFirst().accept(response(false));

        // No more polls, the search has ended and the error is shown
        assertThat(polls)
                .hasSize(1);
        assertThat(searching)
                .containsExactly(false, true, false);
        Mockito.verify(table).endSearch();
        assertThat(errors.getLast())
                .singleElement()
                .satisfies(error -> {
                    assertThat(error.getSeverity()).isEqualTo(Severity.ERROR);
                    assertThat(error.getMessage()).contains("Bad row");
                });
    }

    @Test
    void poll_incomplete() {
        startSearch();

        polls.getFirst().accept(response(false));

        // Still running, so it polls again
        assertThat(polls)
                .hasSize(2);
        assertThat(searching.getLast())
                .isTrue();
    }

    @Test
    void poll_complete() {
        startSearch();

        polls.getFirst().accept(response(true));

        assertThat(polls)
                .hasSize(1);
        assertThat(searching.getLast())
                .isFalse();
        Mockito.verify(table).endSearch();
    }

    private void startSearch() {
        queryModel.startNewSearch(QueryModel.TABLE_COMPONENT_ID, "Query", "from Events", List.of(), null,
                false, false, null, null);
        assertThat(polls)
                .hasSize(1);
        // Starting a search ends the last one, so only count what the replies do
        Mockito.clearInvocations(table);
    }

    private static DashboardSearchResponse response(final boolean complete) {
        return new DashboardSearchResponse(
                "node1",
                new QueryKey("query-key"),
                null,
                null,
                null,
                complete,
                List.of(TableResult.builder().componentId(QueryModel.TABLE_COMPONENT_ID).build()),
                null);
    }
}
