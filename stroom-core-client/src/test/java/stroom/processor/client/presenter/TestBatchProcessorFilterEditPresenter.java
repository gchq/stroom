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

package stroom.processor.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.CommonAlertEvent.Level;
import stroom.dispatch.client.RestFactory;
import stroom.processor.client.presenter.BatchProcessorFilterEditPresenter.BatchProcessorFilterEditView;
import stroom.processor.shared.ProcessorFilterChange;
import stroom.query.api.ExpressionOperator;
import stroom.security.client.presenter.UserRefSelectionBoxPresenter;
import stroom.util.shared.PageResponse;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TestBatchProcessorFilterEditPresenter {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<ShowPopupEvent> shown = new ArrayList<>();
    private final List<AlertEvent> alerts = new ArrayList<>();
    private BatchProcessorFilterEditView view;
    private UserRefSelectionBoxPresenter userRefSelectionBoxPresenter;
    private RestFactory restFactory;
    private BatchProcessorFilterEditPresenter presenter;

    @BeforeEach
    void setUp() {
        // The user picker and REST resources are made with GWT.create, which only works in a browser
        GWTMockUtilities.disarm();
        eventBus.addHandler(ShowPopupEvent.getType(), shown::add);
        eventBus.addHandler(AlertEvent.getType(), alerts::add);
        view = Mockito.mock(BatchProcessorFilterEditView.class);
        userRefSelectionBoxPresenter = Mockito.mock(UserRefSelectionBoxPresenter.class);
        restFactory = Mockito.mock(RestFactory.class);
        presenter = new BatchProcessorFilterEditPresenter(eventBus, view, userRefSelectionBoxPresenter, restFactory);
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void ok_noChange() {
        // Regression test: the message was fired as the Properties screen's ErrorEvent, which nothing
        // here handles, so OK did nothing and said nothing.
        Mockito.when(view.getChange()).thenReturn(null);

        final HidePopupRequestEvent okPressed = showAndPressOk(10);

        expectWarningThenUsable(okPressed, "No change selected.");
    }

    @Test
    void ok_runAsUserWithNoUser() {
        Mockito.when(view.getChange()).thenReturn(ProcessorFilterChange.SET_RUN_AS_USER);
        Mockito.when(userRefSelectionBoxPresenter.getSelected()).thenReturn(null);

        final HidePopupRequestEvent okPressed = showAndPressOk(10);

        expectWarningThenUsable(okPressed, "No user selected.");
    }

    @Test
    void ok_noProcessors() {
        Mockito.when(view.getChange()).thenReturn(ProcessorFilterChange.ENABLE);

        final HidePopupRequestEvent okPressed = showAndPressOk(0);

        expectWarningThenUsable(okPressed, "No processors are included in the current filter.");
    }

    @Test
    void ok_processors() {
        Mockito.when(view.getChange()).thenReturn(ProcessorFilterChange.ENABLE);

        final HidePopupRequestEvent okPressed = showAndPressOk(2);

        // Nothing is wrong, so OK asks for confirmation rather than warning
        assertThat(alerts)
                .isEmpty();
        Mockito.verify(okPressed, Mockito.never()).reset();
        Mockito.verify(okPressed, Mockito.never()).hide();
    }

    // Shows the dialog for a filter matching the given number of processors, and presses OK.
    private HidePopupRequestEvent showAndPressOk(final long processorCount) {
        presenter.show(
                ExpressionOperator.builder().build(),
                PageResponse.builder().total(processorCount).build(),
                () -> {
                });
        assertThat(shown)
                .hasSize(1);

        final HidePopupRequestEvent okPressed = Mockito.mock(HidePopupRequestEvent.class);
        Mockito.when(okPressed.isOk()).thenReturn(true);
        shown.getFirst().getHideRequestHandler().onHideRequest(okPressed);
        return okPressed;
    }

    // Checks the warning was shown, and that once it is closed the dialog can be used again.
    private void expectWarningThenUsable(final HidePopupRequestEvent okPressed, final String message) {
        assertThat(alerts)
                .extracting(AlertEvent::getLevel, alert -> alert.getMessage().asString())
                .containsExactly(tuple(Level.WARN, message));
        Mockito.verify(okPressed, Mockito.never()).reset();
        alerts.getFirst().getCallback().onClose();
        Mockito.verify(okPressed).reset();
        Mockito.verify(okPressed, Mockito.never()).hide();
    }
}
