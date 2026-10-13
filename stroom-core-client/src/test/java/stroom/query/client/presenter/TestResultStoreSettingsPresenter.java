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

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.CommonAlertEvent.Level;
import stroom.dispatch.client.RestError;
import stroom.dispatch.client.RestErrorHandler;
import stroom.query.api.LifespanInfo;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultStoreInfo;
import stroom.query.client.presenter.ResultStoreSettingsPresenter.ResultStoreSettingsView;
import stroom.query.shared.UpdateStoreRequest;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.fusesource.restygwt.client.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TestResultStoreSettingsPresenter {

    private static final LifespanInfo LIFESPAN = new LifespanInfo("1h", "1d", true, true);

    private final EventBus eventBus = new SimpleEventBus();
    private final List<ShowPopupEvent> shown = new ArrayList<>();
    private final List<AlertEvent> alerts = new ArrayList<>();
    private final List<Boolean> saved = new ArrayList<>();
    private ResultStoreSettingsView view;
    private ResultStoreModel resultStoreModel;
    private ResultStoreSettingsPresenter presenter;

    @BeforeEach
    void setUp() {
        // The result store resource is made with GWT.create, which only works in a browser
        GWTMockUtilities.disarm();
        eventBus.addHandler(ShowPopupEvent.getType(), shown::add);
        eventBus.addHandler(AlertEvent.getType(), alerts::add);
        view = Mockito.mock(ResultStoreSettingsView.class);
        Mockito.when(view.getSearchProcessTimeToIdle()).thenReturn("1h");
        Mockito.when(view.getSearchProcessTimeToLive()).thenReturn("1d");
        Mockito.when(view.getStoreTimeToIdle()).thenReturn("1h");
        Mockito.when(view.getStoreTimeToLive()).thenReturn("1d");
        resultStoreModel = Mockito.mock(ResultStoreModel.class);
        presenter = new ResultStoreSettingsPresenter(eventBus, view, resultStoreModel);
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void ok_durationNotSet() {
        // Regression test: a blank duration was sent, the server failed to read it, and the dialog
        // closed with nothing said and nothing changed
        Mockito.when(view.getStoreTimeToLive()).thenReturn(" ");

        final HidePopupRequestEvent okPressed = showAndPressOk();

        assertThat(alerts)
                .extracting(AlertEvent::getLevel, alert -> alert.getMessage().asString())
                .containsExactly(tuple(
                        Level.WARN,
                        "Store Time To Live must be set, e.g. 10m, 1h or 1d."));
        Mockito.verifyNoInteractions(resultStoreModel);
        alerts.getFirst().getCallback().onClose();
        Mockito.verify(okPressed).reset();
        Mockito.verify(okPressed, Mockito.never()).hide();
    }

    @Test
    void ok_saved() {
        final HidePopupRequestEvent okPressed = showAndPressOk();

        final SaveCall saveCall = captureSave();
        assertThat(saveCall.request().getStoreLifespan().getTimeToLive())
                .isEqualTo("1d");
        saveCall.onSaved().accept(true);

        assertThat(saved)
                .containsExactly(true);
        Mockito.verify(okPressed).hide();
        assertThat(alerts)
                .isEmpty();
    }

    @Test
    void ok_saveFails() {
        // Regression test: a failed save (e.g. a duration the server can't read) closed the dialog
        // with nothing said
        final HidePopupRequestEvent okPressed = showAndPressOk();

        captureSave().errorHandler().onError(new RestError(
                Mockito.mock(Method.class),
                new RuntimeException("Unable to parse soon as a duration")));

        assertThat(alerts)
                .extracting(alert -> alert.getMessage().asString())
                .containsExactly("Unable to parse soon as a duration");
        assertThat(saved)
                .isEmpty();
        Mockito.verify(okPressed, Mockito.never()).hide();
        alerts.getFirst().getCallback().onClose();
        Mockito.verify(okPressed).reset();
    }

    private HidePopupRequestEvent showAndPressOk() {
        final ResultStoreInfo resultStoreInfo = new ResultStoreInfo(null, new QueryKey("query-key"), null, null,
                "node1", null, true, null, LIFESPAN, LIFESPAN);
        // The popup's size is worked out from the browser window's
        try (final MockedStatic<PopupSize> ignored = Mockito.mockStatic(PopupSize.class)) {
            presenter.show(resultStoreInfo, "Result Store Settings", saved::add);
        }
        assertThat(shown)
                .hasSize(1);
        final HidePopupRequestEvent okPressed = Mockito.mock(HidePopupRequestEvent.class);
        Mockito.when(okPressed.isOk()).thenReturn(true);
        shown.getFirst().getHideRequestHandler().onHideRequest(okPressed);
        return okPressed;
    }

    @SuppressWarnings("unchecked")
    private SaveCall captureSave() {
        final ArgumentCaptor<UpdateStoreRequest> request = ArgumentCaptor.forClass(UpdateStoreRequest.class);
        final ArgumentCaptor<Consumer<Boolean>> onSaved = ArgumentCaptor.forClass(Consumer.class);
        final ArgumentCaptor<RestErrorHandler> errorHandler = ArgumentCaptor.forClass(RestErrorHandler.class);
        Mockito.verify(resultStoreModel).updateSettings(
                Mockito.eq("node1"),
                request.capture(),
                onSaved.capture(),
                errorHandler.capture(),
                Mockito.any());
        return new SaveCall(request.getValue(), onSaved.getValue(), errorHandler.getValue());
    }

    private record SaveCall(UpdateStoreRequest request,
                            Consumer<Boolean> onSaved,
                            RestErrorHandler errorHandler) {

    }
}
