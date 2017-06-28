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

package stroom.login.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.security.client.CurrentUser;
import stroom.security.client.event.LoginEvent;
import stroom.security.client.event.LoginFailedEvent;
import stroom.security.client.event.LogoutEvent;
import stroom.security.shared.AutoLoginAction;
import stroom.security.shared.LogoutAction;

public class LoginManager implements HasHandlers {
    private final EventBus eventBus;
    private final CurrentUser currentUser;
    private final ClientDispatchAsync dispatcher;

    @Inject
    public LoginManager(final EventBus eventBus, final CurrentUser currentUser, final ClientDispatchAsync dispatcher) {
        this.eventBus = eventBus;
        this.currentUser = currentUser;
        this.dispatcher = dispatcher;

        // Listen for logout events.
        eventBus.addHandler(LogoutEvent.getType(), event -> logout());
    }

    public void autoLogin() {
        // When we start the application we will try and auto login using a client certificates.
        dispatcher.exec(new AutoLoginAction(), "Logging on. Please wait...").onSuccess(userAndPermissions -> {
            if (userAndPermissions != null) {
                currentUser.setUserAndPermissions(userAndPermissions);
            } else if (!GWT.isProdMode()) {
                // If we are in development mode and failed to login
                // with a client certificates then try the default user name and
                // password.
                LoginEvent.fire(LoginManager.this, "admin", "admin");
            } else {
                LoginFailedEvent.fire(LoginManager.this, null);
            }
        }).onFailure(caught -> LoginFailedEvent.fire(LoginManager.this, caught.getMessage()));
    }

    private void logout() {
        // Clear everything we know about the current user.
        currentUser.clear();
        // When we start the application we will try and auto login using a client certificate.
        dispatcher.exec(new LogoutAction(), null);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
