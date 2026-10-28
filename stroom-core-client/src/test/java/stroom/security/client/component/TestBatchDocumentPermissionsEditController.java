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

package stroom.security.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.CommonAlertEvent.Level;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.client.DocumentTypeCache;
import stroom.explorer.client.component.DocSelectionController;
import stroom.query.api.ExpressionOperator;
import stroom.util.shared.PageResponse;
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

class TestBatchDocumentPermissionsEditController {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<AlertEvent> alerts = new ArrayList<>();
    private BatchDocumentPermissionsEditController controller;

    @BeforeEach
    void setUp() {
        // The pickers and REST resources are made with GWT.create, which only works in a browser
        GWTMockUtilities.disarm();
        eventBus.addHandler(AlertEvent.getType(), alerts::add);
        final DocSelectionController docSelection = Mockito.mock(DocSelectionController.class);
        final UserRefSelectionController userRefSelection = Mockito.mock(UserRefSelectionController.class);
        controller = new BatchDocumentPermissionsEditController(
                eventBus,
                Mockito.mock(BatchDocumentPermissionsEdit.class),
                () -> docSelection,
                () -> userRefSelection,
                Mockito.mock(RestFactory.class),
                Mockito.mock(DocumentTypeCache.class));
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void ok_noDocuments() {
        // Regression test: the message was fired as the Properties screen's ErrorEvent, which nothing
        // here handles, so OK did nothing and said nothing.
        final HidePopupRequestEvent okPressed = pressOk(0);

        assertThat(alerts)
                .extracting(AlertEvent::getLevel, alert -> alert.getMessage().asString())
                .containsExactly(tuple(
                        Level.WARN,
                        "No documents are included in the current filter for this permission change."));
        // Once the warning is closed the dialog can be used again
        Mockito.verify(okPressed, Mockito.never()).reset();
        alerts.getFirst().getCallback().onClose();
        Mockito.verify(okPressed).reset();
        Mockito.verify(okPressed, Mockito.never()).hide();
    }

    @Test
    void ok_documents() {
        final HidePopupRequestEvent okPressed = pressOk(3);

        // Nothing is wrong, so OK asks for confirmation rather than warning
        assertThat(alerts)
                .isEmpty();
        Mockito.verify(okPressed, Mockito.never()).reset();
        Mockito.verify(okPressed, Mockito.never()).hide();
    }

    // Presses OK in the dialog for a filter matching the given number of documents.
    private HidePopupRequestEvent pressOk(final long documentCount) {
        final HidePopupRequestEvent okPressed = Mockito.mock(HidePopupRequestEvent.class);
        Mockito.when(okPressed.isOk()).thenReturn(true);
        controller.onHideRequest(
                okPressed,
                ExpressionOperator.builder().build(),
                PageResponse.builder().total(documentCount).build(),
                () -> {
                });
        return okPressed;
    }
}
