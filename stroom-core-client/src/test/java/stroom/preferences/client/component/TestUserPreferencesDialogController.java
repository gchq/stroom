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

package stroom.preferences.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestError;
import stroom.dispatch.client.RestErrorHandler;
import stroom.preferences.client.UserPreferencesManager;
import stroom.security.client.api.ClientSecurityContext;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.shared.UserPreferences;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.fusesource.restygwt.client.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class TestUserPreferencesDialogController {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<AlertEvent> alerts = new ArrayList<>();
    private UserPreferencesManager userPreferencesManager;

    @BeforeEach
    void setUp() {
        // The manager makes its REST resource with GWT.create, which only works in a browser
        GWTMockUtilities.disarm();
        userPreferencesManager = Mockito.mock(UserPreferencesManager.class);
        eventBus.addHandler(AlertEvent.getType(), alerts::add);
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void aFailedSaveLetsTheDialogBeUsedAgain() {
        final HidePopupRequestEvent okPressed = showAndPressOk();

        final ArgumentCaptor<RestErrorHandler> errorHandler = ArgumentCaptor.forClass(RestErrorHandler.class);
        Mockito.verify(userPreferencesManager).update(
                any(UserPreferences.class),
                any(),
                errorHandler.capture(),
                any(TaskMonitorFactory.class));
        // A request with no response, e.g. one the server refused or a lost connection
        errorHandler.getValue().onError(new RestError(Mockito.mock(Method.class), new RuntimeException("Refused")));

        // The failure is reported, and once the alert is closed the dialog's buttons work again
        assertThat(alerts).hasSize(1);
        Mockito.verify(okPressed, Mockito.never()).reset();
        alerts.get(0).getCallback().onClose();
        Mockito.verify(okPressed).reset();
        Mockito.verify(okPressed, Mockito.never()).hide();
    }

    @Test
    void aSuccessfulSaveClosesTheDialog() {
        final HidePopupRequestEvent okPressed = showAndPressOk();

        @SuppressWarnings("unchecked") final ArgumentCaptor<Consumer<Boolean>> onSaved =
                ArgumentCaptor.forClass(Consumer.class);
        Mockito.verify(userPreferencesManager).update(
                any(UserPreferences.class),
                onSaved.capture(),
                any(RestErrorHandler.class),
                any(TaskMonitorFactory.class));
        onSaved.getValue().accept(true);

        Mockito.verify(okPressed).hide();
        Mockito.verify(okPressed, Mockito.never()).reset();
    }

    // Reads preferences that differ from those last saved, so that OK saves them, and presses OK.
    private HidePopupRequestEvent showAndPressOk() {
        final UserPreferences saved = UserPreferences.builder().theme("Light").build();
        final UserPreferences changed = UserPreferences.builder().theme("Dark").build();
        Mockito.when(userPreferencesManager.getCurrentUserPreferences()).thenReturn(changed);

        final UserPreferencesDialogController controller = new UserPreferencesDialogController(
                eventBus,
                Mockito.mock(UserPreferencesDialog.class),
                userPreferencesManager,
                Mockito.mock(ThemePreferencesController.class),
                Mockito.mock(EditorPreferencesController.class),
                Mockito.mock(TimePreferencesController.class),
                Mockito.mock(KeyBindingPreferencesController.class),
                Mockito.mock(ClientSecurityContext.class));
        controller.read(saved);

        final HidePopupRequestEvent okPressed = Mockito.mock(HidePopupRequestEvent.class);
        Mockito.when(okPressed.isOk()).thenReturn(true);
        controller.onHideRequest(okPressed);
        Mockito.verify(userPreferencesManager).setCurrentPreferences(eq(changed));
        return okPressed;
    }
}
