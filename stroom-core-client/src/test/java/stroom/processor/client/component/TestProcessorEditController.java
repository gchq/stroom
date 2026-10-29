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
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.dispatch.client.FakeRestFactory;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.client.ProcessorProfileClient;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorType;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.client.DateTimeSettingsFactory;
import stroom.query.client.component.ExpressionEditorController;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.component.UserRefSelectionController;
import stroom.widget.datepicker.client.DateTimeBoxController;
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

class TestProcessorEditController {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<AlertEvent> alerts = new ArrayList<>();
    private final List<ProcessorFilter> saved = new ArrayList<>();
    private final FakeRestFactory restFactory = new FakeRestFactory();

    @BeforeEach
    void setUp() {
        // The REST resources are made with GWT.create, which only works in a browser
        GWTMockUtilities.disarm();
        eventBus.addHandler(AlertEvent.getType(), alerts::add);
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void anInvalidExpressionLetsTheDialogBeUsedAgain() {
        // Regression test: an invalid expression was only shown, so the dialog was left with OK and
        // Cancel disabled.
        final HidePopupRequestEvent okPressed = showAndPressOk();

        restFactory.getLastRequest().succeed(new ValidateExpressionResult(false, "Field 'Bogus' is not valid", false));

        expectAlertThenUsable(okPressed);
    }

    @Test
    void aFailedValidationLetsTheDialogBeUsedAgain() {
        final HidePopupRequestEvent okPressed = showAndPressOk();

        restFactory.getLastRequest().fail(new RuntimeException("Refused"));

        expectAlertThenUsable(okPressed);
    }

    @Test
    void aFailedCreateLetsTheDialogBeUsedAgain() {
        // Regression test: creating a filter had no failure handler (updating one had), so when it
        // failed the dialog was left with OK and Cancel disabled.
        final HidePopupRequestEvent okPressed = showAndPressOk();
        restFactory.getLastRequest().succeed(new ValidateExpressionResult(true, null, false));
        // The expression has a Feed term, so nothing is asked before the filter is created
        assertThat(restFactory.getRequests())
                .hasSize(2);

        restFactory.getLastRequest().fail(new RuntimeException("Refused"));

        expectAlertThenUsable(okPressed);
    }

    @Test
    void aCreatedFilterClosesTheDialog() {
        final HidePopupRequestEvent okPressed = showAndPressOk();
        restFactory.getLastRequest().succeed(new ValidateExpressionResult(true, null, false));
        final ProcessorFilter created = ProcessorFilter.builder().id(7).build();

        restFactory.getLastRequest().succeed(created);

        Mockito.verify(okPressed).hide();
        assertThat(saved)
                .containsExactly(created);
        assertThat(alerts)
                .isEmpty();
    }

    // Prepares the dialog for a new filter whose expression has a Feed term, and presses OK.
    private HidePopupRequestEvent showAndPressOk() {
        final ProcessorEdit view = Mockito.mock(ProcessorEdit.class);
        Mockito.when(view.getProfile()).thenReturn(null);
        Mockito.when(view.getMaxTaskCreationDelay()).thenReturn(null);
        // A date-time box can't be made (or mocked) outside a browser; the controllers are mocks
        Mockito.doReturn(null).when(view).getMinMetaCreateTimeBox();
        Mockito.doReturn(null).when(view).getMaxMetaCreateTimeBox();
        final ExpressionEditorController editExpressionController = Mockito.mock(ExpressionEditorController.class);
        // Attached to the editor the dialog's template declares
        Mockito.when(editExpressionController.attach(Mockito.any())).thenReturn(editExpressionController);
        Mockito.when(editExpressionController.write()).thenReturn(ExpressionOperator.builder()
                .addTerm(ExpressionTerm.builder().field("Feed").condition(Condition.EQUALS).value("TEST").build())
                .build());
        final ProcessorEditController controller = new ProcessorEditController(
                eventBus,
                view,
                editExpressionController,
                restFactory,
                Mockito.mock(DateTimeSettingsFactory.class),
                () -> Mockito.mock(UserRefSelectionController.class),
                Mockito.mock(ClientSecurityContext.class),
                () -> null,
                Mockito.mock(ProcessorProfileClient.class),
                () -> Mockito.mock(DateTimeBoxController.class));

        // What the dialog shows when it is shown (a dialog can't be shown outside a browser)
        controller.prepare(
                ProcessorType.PIPELINE,
                DocRef.builder().type(PipelineDoc.TYPE).uuid("p1").build(),
                null,
                ExpressionOperator.builder().build(),
                null,
                null,
                null,
                null,
                saved::add);

        final HidePopupRequestEvent okPressed = Mockito.mock(HidePopupRequestEvent.class);
        Mockito.when(okPressed.isOk()).thenReturn(true);
        controller.onHideRequest(okPressed);
        // The expression is validated first
        assertThat(restFactory.getRequests())
                .hasSize(1);
        return okPressed;
    }

    // Checks one message was shown, and that once it is closed the dialog can be used again.
    private void expectAlertThenUsable(final HidePopupRequestEvent okPressed) {
        assertThat(alerts)
                .hasSize(1);
        Mockito.verify(okPressed, Mockito.never()).reset();
        alerts.get(0).getCallback().onClose();
        Mockito.verify(okPressed).reset();
        Mockito.verify(okPressed, Mockito.never()).hide();
        assertThat(saved)
                .isEmpty();
    }
}
