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
import stroom.dispatch.client.FakeRestFactory;
import stroom.security.client.api.ClientSecurityContext;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.ExtendedUiConfig;
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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

class TestEditApiKeyController {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<AlertEvent> alerts = new ArrayList<>();
    private EditApiKey view;
    private EditApiKeyController controller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // The REST resource is made with GWT.create, and the form is a widget, which only work in
        // a browser
        GWTMockUtilities.disarm();
        eventBus.addHandler(AlertEvent.getType(), alerts::add);
        final ExtendedUiConfig uiConfig = Mockito.mock(ExtendedUiConfig.class);
        Mockito.when(uiConfig.getMaxApiKeyExpiryAgeMs()).thenReturn(TimeUnit.DAYS.toMillis(365));
        final UiConfigCache uiConfigCache = Mockito.mock(UiConfigCache.class);
        Mockito.doAnswer(invocation -> {
            final Consumer<ExtendedUiConfig> consumer = invocation.getArgument(0);
            consumer.accept(uiConfig);
            return null;
        }).when(uiConfigCache).get(any(), any());
        view = Mockito.mock(EditApiKey.class);
        Mockito.when(view.getMode()).thenReturn(EditApiKeyMode.PRE_CREATE);
        Mockito.when(view.getName()).thenReturn("My key");
        final UserRefSelectionController owner = Mockito.mock(UserRefSelectionController.class);
        final DateTimeBoxController expiresOn = Mockito.mock(DateTimeBoxController.class);
        controller = new EditApiKeyController(
                eventBus,
                view,
                new FakeRestFactory(),
                Mockito.mock(ClientSecurityContext.class),
                uiConfigCache,
                () -> owner,
                () -> expiresOn);
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void noExpiryDateSaysSo() {
        // Regression test: a blank or unreadable expiry date was told it must be no later than the
        // maximum, which didn't say what was wrong.
        Mockito.when(view.getExpiresOnMs()).thenReturn(null);

        final HidePopupRequestEvent okPressed = pressOk();

        assertThat(alerts)
                .extracting(alert -> alert.getMessage().asString())
                .containsExactly("A valid expiry date must be provided for the API key.");
        alerts.getFirst().getCallback().onClose();
        Mockito.verify(okPressed).reset();
    }

    @Test
    void anExpiryDateInThePastSaysSo() {
        // The message once ended with the latest date allowed, which isn't what is wrong
        Mockito.when(view.getExpiresOnMs()).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1));

        pressOk();

        assertThat(alerts)
                .extracting(alert -> alert.getMessage().asString())
                .containsExactly("API Key expiry date cannot be in the past.");
    }

    // Presses OK on the create dialog.
    private HidePopupRequestEvent pressOk() {
        final HidePopupRequestEvent okPressed = Mockito.mock(HidePopupRequestEvent.class);
        Mockito.when(okPressed.isOk()).thenReturn(true);
        controller.onHideRequest(okPressed);
        return okPressed;
    }
}
