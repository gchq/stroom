/*
 * Copyright 2016 Crown Copyright
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

package stroom.security.client;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.Window;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.security.client.event.LogoutEvent;
import stroom.security.shared.FetchUserAndPermissionsAction;
import stroom.security.shared.LogoutAction;

public class LoginManager implements HasHandlers {
    private final EventBus eventBus;
    private final CurrentUser currentUser;
    private final ClientDispatchAsync dispatcher;

    @Inject
    public LoginManager(
            final EventBus eventBus,
            final CurrentUser currentUser,
            final ClientDispatchAsync dispatcher) {
        this.eventBus = eventBus;
        this.currentUser = currentUser;
        this.dispatcher = dispatcher;

        // Listen for logout events.
        eventBus.addHandler(LogoutEvent.getType(), event -> logout());
    }

    public void fetchUserAndPermissions() {
        // When we start the application we will try and auto login using a client certificates.
        dispatcher.exec(new FetchUserAndPermissionsAction(), "Loading. Please wait...").onSuccess(userAndPermissions -> {
            if (userAndPermissions != null) {
                currentUser.setUserAndPermissions(userAndPermissions);
            } else {
                logout();
            }
        }).onFailure(caught -> AlertEvent.fireErrorFromException(LoginManager.this, caught, null));
    }

    private void logout() {
        // Clear everything we know about the current user.
        currentUser.clear();
        // Perform logout on the server
        dispatcher.exec(new LogoutAction(), null)
                .onSuccess(r -> {
                    // Reload the page.
                    Window.Location.reload();
                })
                .onFailure(t -> AlertEvent.fireErrorFromException(LoginManager.this, t, null));
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
