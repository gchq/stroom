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

package stroom.security.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.FakeRestFactory;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.EditApiKeyPresenter.EditApiKeyView;
import stroom.security.client.presenter.EditApiKeyPresenter.Mode;
import stroom.security.shared.AppPermission;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.ExtendedUiConfig;
import stroom.util.shared.UserRef;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.core.client.GWT;
import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestEditApiKeyPresenter {

    private static final UserRef CURRENT_USER = UserRef.forUserUuid("current-user");
    private static final UserRef OTHER_USER = UserRef.forUserUuid("other-user");

    private final EventBus eventBus = new SimpleEventBus();
    private final List<AlertEvent> alerts = new ArrayList<>();
    private EditApiKeyView view;
    private EditApiKeyPresenter presenter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // The REST resource is made with GWT.create, which only works in a browser
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
        view = Mockito.mock(EditApiKeyView.class);
        Mockito.when(view.getMode()).thenReturn(Mode.PRE_CREATE);
        Mockito.when(view.getName()).thenReturn("My key");
        presenter = new EditApiKeyPresenter(
                eventBus,
                view,
                new FakeRestFactory(),
                Mockito.mock(ClientSecurityContext.class),
                uiConfigCache,
                Mockito.mock(UserRefSelectionBoxPresenter.class));
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
        presenter.onHideRequest(okPressed);
        return okPressed;
    }

    @Test
    void testOwnerSelectionRequiresManageUsersPermission() {
        assertOwnerSelection(false, null, CURRENT_USER, false);
    }

    @Test
    void testScopedDialogUsesSpecifiedOwner() {
        assertOwnerSelection(true, OTHER_USER, OTHER_USER, false);
    }

    @Test
    void testUnscopedDialogDefaultsToCurrentUser() {
        assertOwnerSelection(true, null, CURRENT_USER, true);
    }

    @Test
    void testScopedDialogCannotBypassManageUsersPermission() {
        assertOwnerSelection(false, OTHER_USER, CURRENT_USER, false);
    }

    @Test
    void testOwnUserTabKeepsOwnerFixed() {
        assertOwnerSelection(true, CURRENT_USER, CURRENT_USER, false);
    }

    private void assertOwnerSelection(final boolean canManageUsers,
                                     final UserRef scopedOwner,
                                     final UserRef expectedOwner,
                                     final boolean expectedEnabled) {
        try (final MockedStatic<GWT> ignored = mockStatic(GWT.class)) {
            final ClientSecurityContext securityContext = mock(ClientSecurityContext.class);
            when(securityContext.getUserRef()).thenReturn(CURRENT_USER);
            when(securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION))
                    .thenReturn(canManageUsers);
            final UserRefSelectionBoxPresenter ownerPresenter = mock(UserRefSelectionBoxPresenter.class);
            final EditApiKeyPresenter presenter = new EditApiKeyPresenter(
                    mock(EventBus.class),
                    mock(EditApiKeyView.class),
                    mock(RestFactory.class),
                    securityContext,
                    mock(UiConfigCache.class),
                    ownerPresenter);

            presenter.showCreateDialog(Mode.PRE_CREATE, () -> {}, scopedOwner);

            final ArgumentCaptor<Boolean> enabled = ArgumentCaptor.forClass(Boolean.class);
            verify(ownerPresenter, atLeastOnce()).setEnabled(enabled.capture());
            assertThat(enabled.getValue()).isEqualTo(expectedEnabled);
            verify(ownerPresenter).setSelected(expectedOwner);
        }
    }
}
