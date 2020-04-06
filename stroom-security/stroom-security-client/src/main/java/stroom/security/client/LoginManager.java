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

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.Window;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.event.LogoutEvent;
import stroom.security.shared.AppPermissionResource;
import stroom.security.shared.AuthenticationResource;
import stroom.security.shared.UserAndPermissions;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.ResourcePaths;

public class LoginManager implements HasHandlers {
    private static final AuthenticationResource AUTHENTICATION_RESOURCE = GWT.create(AuthenticationResource.class);
    private static final AppPermissionResource APP_PERMISSION_RESOURCE = GWT.create(AppPermissionResource.class);

    private final EventBus eventBus;
    private final CurrentUser currentUser;
    private final RestFactory restFactory;
    private final UiConfigCache clientPropertyCache;

    @Inject
    public LoginManager(
            final EventBus eventBus,
            final CurrentUser currentUser,
            final RestFactory restFactory,
            final UiConfigCache clientPropertyCache) {
        this.eventBus = eventBus;
        this.currentUser = currentUser;
        this.restFactory = restFactory;
        this.clientPropertyCache = clientPropertyCache;

        // Listen for logout events.
        eventBus.addHandler(LogoutEvent.getType(), event -> logout());
    }

    public void fetchUserAndPermissions() {
        // When we start the application we will try and auto login using a client certificates.
        TaskStartEvent.fire(this, "Fetching permissions...");
        final Rest<UserAndPermissions> rest = restFactory.create();
        rest
                .onSuccess(userAndPermissions -> {
                    if (userAndPermissions != null) {
                        currentUser.setUserAndPermissions(userAndPermissions);
                    } else {
                        logout();
                    }
                    TaskEndEvent.fire(LoginManager.this);
                })
                .onFailure(throwable -> {
                    AlertEvent.fireInfo(LoginManager.this, throwable.getMessage(), this::logout);
                    TaskEndEvent.fire(LoginManager.this);
                })
                .call(APP_PERMISSION_RESOURCE).getUserAndPermissions();
    }

    private void logout() {
        // Perform logout on the server
        final Rest<Boolean> rest = restFactory.create();
        rest
                .onSuccess(response -> {
                    // Redirect the page to logout.
                    clientPropertyCache
                            .get()
                            .onSuccess(result -> {
                                // TODO should be using the apigateway url for this, but that is not in UiConfig at
                                //   the mo.
                                final String authServiceUrl = result.getUrl().getAuthenticationService();
                                // Send the user's browser to the remote Authentication Service's logout endpoint.
                                // By adding 'prompt=login' we ask the Identity Provider to prompt the user for a login,
                                // bypassing certificate checks. We need this to enable username/password
                                // logins in an environment where the user's browser always presents a certificate.
                                String redirectUrl = URL.encode(result.getUrl().getUi() + "?prompt=login");
                                Window.Location.replace(authServiceUrl + ResourcePaths.NO_AUTH + "/logout?redirect_url=" + redirectUrl);
                            });
                })
                .onFailure(throwable -> AlertEvent.fireErrorFromException(LoginManager.this, throwable, null))
                .call(AUTHENTICATION_RESOURCE)
                .logout();
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
