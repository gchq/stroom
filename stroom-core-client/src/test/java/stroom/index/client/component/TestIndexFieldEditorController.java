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

package stroom.index.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.index.shared.IndexFieldImpl;
import stroom.query.api.datasource.FieldType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.EventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

class TestIndexFieldEditorController {

    private EventBus eventBus;
    private IndexFieldEditor view;
    private IndexFieldEditorController controller;
    private HidePopupRequestEvent hideRequest;
    private BiConsumer<IndexFieldImpl, HidePopupRequestEvent> onOk;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        // The content is a widget, which can only be made in a browser
        GWTMockUtilities.disarm();
        eventBus = Mockito.mock(EventBus.class);
        view = Mockito.mock(IndexFieldEditor.class);
        Mockito.when(view.getType()).thenReturn(FieldType.TEXT);
        final DenseVectorFieldController denseVectorField = Mockito.mock(DenseVectorFieldController.class);
        Mockito.when(denseVectorField.attach(Mockito.any())).thenReturn(denseVectorField);
        controller = new IndexFieldEditorController(
                eventBus,
                view,
                denseVectorField);
        hideRequest = Mockito.mock(HidePopupRequestEvent.class);
        onOk = Mockito.mock(BiConsumer.class);
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void okWithNoNameShowsErrorAndReEnablesDialog(final String fieldName) {
        Mockito.when(hideRequest.isOk()).thenReturn(true);
        Mockito.when(view.getFieldName()).thenReturn(fieldName);

        controller.onHideRequest(hideRequest, onOk);

        Mockito.verifyNoInteractions(onOk);
        Mockito.verify(hideRequest, Mockito.never()).hide();
        Mockito.verify(hideRequest, Mockito.never()).reset();

        // The buttons must be re-enabled once the error has been dismissed
        final AlertEvent alertEvent = captureAlertEvent();
        alertEvent.getCallback().onClose();
        Mockito.verify(hideRequest).reset();
    }

    @Test
    void okWithNamePassesFieldOn() {
        Mockito.when(hideRequest.isOk()).thenReturn(true);
        Mockito.when(view.getFieldName()).thenReturn("  myField  ");

        controller.onHideRequest(hideRequest, onOk);

        final ArgumentCaptor<IndexFieldImpl> captor = ArgumentCaptor.forClass(IndexFieldImpl.class);
        Mockito.verify(onOk).accept(captor.capture(), Mockito.eq(hideRequest));
        assertThat(captor.getValue().getFldName())
                .isEqualTo("myField");
        assertThat(captor.getValue().getFldType())
                .isEqualTo(FieldType.TEXT);
        Mockito.verify(eventBus, Mockito.never()).fireEventFromSource(Mockito.any(), Mockito.any());
    }

    @Test
    void cancelHidesDialog() {
        Mockito.when(hideRequest.isOk()).thenReturn(false);

        controller.onHideRequest(hideRequest, onOk);

        Mockito.verify(hideRequest).hide();
        Mockito.verifyNoInteractions(onOk);
    }

    private AlertEvent captureAlertEvent() {
        final ArgumentCaptor<GwtEvent<?>> captor = ArgumentCaptor.forClass(GwtEvent.class);
        Mockito.verify(eventBus).fireEventFromSource(captor.capture(), Mockito.eq(controller));
        assertThat(captor.getValue())
                .isInstanceOf(AlertEvent.class);
        return (AlertEvent) captor.getValue();
    }
}
