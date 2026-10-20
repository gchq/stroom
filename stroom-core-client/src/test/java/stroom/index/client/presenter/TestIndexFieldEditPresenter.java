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

package stroom.index.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.index.client.presenter.IndexFieldEditPresenter.IndexFieldEditView;
import stroom.index.shared.IndexFieldImpl;
import stroom.query.api.datasource.FieldType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.event.shared.GwtEvent;
import com.google.web.bindery.event.shared.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

class TestIndexFieldEditPresenter {

    private EventBus eventBus;
    private IndexFieldEditView view;
    private IndexFieldEditPresenter presenter;
    private HidePopupRequestEvent hideRequest;
    private BiConsumer<IndexFieldImpl, HidePopupRequestEvent> onOk;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        eventBus = Mockito.mock(EventBus.class);
        view = Mockito.mock(IndexFieldEditView.class);
        Mockito.when(view.getType()).thenReturn(FieldType.TEXT);
        presenter = new IndexFieldEditPresenter(
                eventBus,
                view,
                Mockito.mock(DenseVectorFieldPresenter.class));
        hideRequest = Mockito.mock(HidePopupRequestEvent.class);
        onOk = Mockito.mock(BiConsumer.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void okWithNoNameShowsErrorAndReEnablesDialog(final String fieldName) {
        Mockito.when(hideRequest.isOk()).thenReturn(true);
        Mockito.when(view.getFieldName()).thenReturn(fieldName);

        presenter.onHideRequest(hideRequest, onOk);

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

        presenter.onHideRequest(hideRequest, onOk);

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

        presenter.onHideRequest(hideRequest, onOk);

        Mockito.verify(hideRequest).hide();
        Mockito.verifyNoInteractions(onOk);
    }

    private AlertEvent captureAlertEvent() {
        final ArgumentCaptor<GwtEvent<?>> captor = ArgumentCaptor.forClass(GwtEvent.class);
        Mockito.verify(eventBus).fireEventFromSource(captor.capture(), Mockito.eq(presenter));
        assertThat(captor.getValue())
                .isInstanceOf(AlertEvent.class);
        return (AlertEvent) captor.getValue();
    }
}
