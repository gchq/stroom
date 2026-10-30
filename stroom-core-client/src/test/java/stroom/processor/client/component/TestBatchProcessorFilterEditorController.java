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

package stroom.processor.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.CommonAlertEvent.Level;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.FakeRestFactory;
import stroom.processor.shared.ProcessorFilterChange;
import stroom.query.api.ExpressionOperator;
import stroom.security.client.component.UserRefSelectionController;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

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

class TestBatchProcessorFilterEditorController {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<AlertEvent> alerts = new ArrayList<>();
    private final List<ConfirmEvent> confirms = new ArrayList<>();
    private BatchProcessorFilterEditor view;
    private UserRefSelectionController userRefSelection;
    private FakeRestFactory restFactory;
    private BatchProcessorFilterEditorController controller;

    @BeforeEach
    void setUp() {
        // The user picker and REST resources are made with GWT.create, which only works in a browser
        GWTMockUtilities.disarm();
        eventBus.addHandler(AlertEvent.getType(), alerts::add);
        eventBus.addHandler(ConfirmEvent.getType(), confirms::add);
        view = Mockito.mock(BatchProcessorFilterEditor.class);
        userRefSelection = Mockito.mock(UserRefSelectionController.class);
        restFactory = new FakeRestFactory();
        controller = new BatchProcessorFilterEditorController(eventBus, view, () -> userRefSelection, restFactory);
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

        final HidePopupRequestEvent okPressed = pressOk(10);

        expectWarningThenUsable(okPressed, "No change selected.");
    }

    @Test
    void ok_runAsUserWithNoUser() {
        Mockito.when(view.getChange()).thenReturn(ProcessorFilterChange.SET_RUN_AS_USER);
        Mockito.when(userRefSelection.getSelected()).thenReturn(null);

        final HidePopupRequestEvent okPressed = pressOk(10);

        expectWarningThenUsable(okPressed, "No user selected.");
    }

    @Test
    void ok_noProcessors() {
        Mockito.when(view.getChange()).thenReturn(ProcessorFilterChange.ENABLE);

        final HidePopupRequestEvent okPressed = pressOk(0);

        expectWarningThenUsable(okPressed, "No processors are included in the current filter.");
    }

    @Test
    void ok_processors() {
        Mockito.when(view.getChange()).thenReturn(ProcessorFilterChange.ENABLE);

        final HidePopupRequestEvent okPressed = pressOk(2);

        // Nothing is wrong, so OK asks for confirmation, with the server's count, rather than warning
        assertThat(alerts)
                .isEmpty();
        assertThat(confirms)
                .extracting(confirm -> confirm.getMessage().asString())
                .containsExactly("Are you sure you want to change 2 processors?");
        Mockito.verify(okPressed, Mockito.never()).reset();
        Mockito.verify(okPressed, Mockito.never()).hide();
    }

    @Test
    void ok_oneProcessor() {
        Mockito.when(view.getChange()).thenReturn(ProcessorFilterChange.ENABLE);

        pressOk(1);

        assertThat(confirms)
                .extracting(confirm -> confirm.getMessage().asString())
                .containsExactly("Are you sure you want to change this processor?");
    }

    @Test
    void ok_countFails() {
        // The count has a failure handler, so if the server can't count the processors the error is
        // shown and the dialog can be used again, rather than being left with OK and Cancel disabled
        Mockito.when(view.getChange()).thenReturn(ProcessorFilterChange.ENABLE);
        final HidePopupRequestEvent okPressed = pressOkWithoutCount();
        assertThat(restFactory.getRequests())
                .hasSize(1);
        assertThat(restFactory.getLastRequest().hasFailureHandler())
                .isTrue();

        restFactory.getLastRequest().fail(new RuntimeException("Can't count"));

        assertThat(confirms)
                .isEmpty();
        assertThat(alerts)
                .hasSize(1);
        Mockito.verify(okPressed, Mockito.never()).hide();
        alerts.getFirst().getCallback().onClose();
        Mockito.verify(okPressed).reset();
    }

    @Test
    void ok_changeFails() {
        // Regression test: the change had no failure handler, so when it failed (e.g. the server
        // refused it) the dialog was left with OK and Cancel disabled.
        Mockito.when(view.getChange()).thenReturn(ProcessorFilterChange.ENABLE);
        final HidePopupRequestEvent okPressed = pressOk(2);
        assertThat(confirms)
                .hasSize(1);
        confirms.getFirst().getCallback().onResult(true);

        restFactory.getLastRequest().fail(new RuntimeException("Refused"));

        assertThat(alerts)
                .hasSize(1);
        Mockito.verify(okPressed, Mockito.never()).reset();
        alerts.getFirst().getCallback().onClose();
        Mockito.verify(okPressed).reset();
        Mockito.verify(okPressed, Mockito.never()).hide();
    }

    // Presses OK in the dialog for a filter and, if the controller then asks the server how many
    // processor filters the change would apply to, answers with the given count.
    private HidePopupRequestEvent pressOk(final int filterCount) {
        final HidePopupRequestEvent okPressed = pressOkWithoutCount();
        if (!restFactory.getRequests().isEmpty()) {
            restFactory.getLastRequest().succeed(filterCount);
        }
        return okPressed;
    }

    // Presses OK in the dialog for a filter, leaving any request the controller makes pending.
    private HidePopupRequestEvent pressOkWithoutCount() {
        final HidePopupRequestEvent okPressed = Mockito.mock(HidePopupRequestEvent.class);
        Mockito.when(okPressed.isOk()).thenReturn(true);
        controller.onHideRequest(
                okPressed,
                ExpressionOperator.builder().build(),
                () -> {
                });
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
