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

import stroom.data.pager.client.RefreshButton;
import stroom.query.api.Result;
import stroom.query.client.presenter.QueryResultVisPresenter.QueryResultVisView;

import com.google.gwt.junit.GWTMockUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatCode;

class TestQueryResultVisPresenter {

    private QueryResultVisPresenter presenter;
    private RefreshButton refreshButton;
    private QueryModel queryModel;

    @BeforeEach
    void setUp() {
        // The presenter's REST resources are made with GWT.create, which only works in a browser
        GWTMockUtilities.disarm();

        // The presenter's constructor builds GWT widgets, which only works in a browser, so mock
        // it and call the real methods under test
        presenter = Mockito.mock(QueryResultVisPresenter.class);
        Mockito.doCallRealMethod().when(presenter).setQueryModel(Mockito.any());
        Mockito.doCallRealMethod().when(presenter).refresh();

        refreshButton = Mockito.mock(RefreshButton.class);
        final QueryResultVisView view = Mockito.mock(QueryResultVisView.class);
        Mockito.when(view.getRefreshButton()).thenReturn(refreshButton);
        Mockito.when(presenter.getView()).thenReturn(view);

        queryModel = Mockito.mock(QueryModel.class);
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void refreshShowsRefreshingUntilTheResultArrives() {
        presenter.setQueryModel(queryModel);
        Mockito.when(queryModel.isSearching()).thenReturn(false);

        presenter.refresh();
        Mockito.verify(refreshButton).setRefreshing(true);

        captureRefreshCallback().accept(null);
        Mockito.verify(refreshButton).setRefreshing(false);
    }

    @Test
    void refreshWithNoQueryModelDoesNothing() {
        presenter.refresh();

        Mockito.verifyNoInteractions(refreshButton);
    }

    @Test
    void refreshResultArrivingAfterRemovalIsIgnored() {
        presenter.setQueryModel(queryModel);
        presenter.refresh();
        final Consumer<Result> callback = captureRefreshCallback();
        Mockito.clearInvocations(refreshButton);

        // Removing the presenter (e.g. when a search ends with no vis data) drops its query model
        // while the refresh is still in flight
        presenter.setQueryModel(null);

        assertThatCode(() -> callback.accept(null))
                .doesNotThrowAnyException();
        Mockito.verifyNoInteractions(refreshButton);
    }

    @SuppressWarnings("unchecked")
    private Consumer<Result> captureRefreshCallback() {
        final ArgumentCaptor<Consumer<Result>> captor = ArgumentCaptor.forClass(Consumer.class);
        Mockito.verify(queryModel).refresh(Mockito.eq(QueryModel.VIS_COMPONENT_ID), captor.capture());
        return captor.getValue();
    }
}
