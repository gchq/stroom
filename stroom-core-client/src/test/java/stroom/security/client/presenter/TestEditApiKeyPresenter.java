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

import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.EditApiKeyPresenter.EditApiKeyView;
import stroom.security.client.presenter.EditApiKeyPresenter.Mode;
import stroom.security.shared.AppPermission;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.UserRef;

import com.google.gwt.core.client.GWT;
import com.google.web.bindery.event.shared.EventBus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestEditApiKeyPresenter {

    private static final UserRef CURRENT_USER = UserRef.forUserUuid("current-user");
    private static final UserRef OTHER_USER = UserRef.forUserUuid("other-user");

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
