/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.event.LogoutEvent;
import stroom.security.shared.AppPermissionResource;
import stroom.security.shared.SessionResource;
import stroom.task.client.DefaultTaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class LoginManager implements HasHandlers {

    private static final SessionResource STROOM_SESSION_RESOURCE =
            GWT.create(SessionResource.class);
    private static final AppPermissionResource APP_PERMISSION_RESOURCE = GWT.create(AppPermissionResource.class);

    private final EventBus eventBus;
    private final CurrentUser currentUser;
    private final RestFactory restFactory;

    @Inject
    public LoginManager(
            final EventBus eventBus,
            final CurrentUser currentUser,
            final RestFactory restFactory) {
        this.eventBus = eventBus;
        this.currentUser = currentUser;
        this.restFactory = restFactory;

        // Listen for logout events.
        eventBus.addHandler(LogoutEvent.getType(), event -> logout());
    }

    public void fetchUserAndPermissions() {
        // When we start the application we will try and auto login using a client certificates.
        restFactory
                .create(APP_PERMISSION_RESOURCE)
                .method(AppPermissionResource::getEffectiveAppPermissions)
                .onSuccess(userAndPermissions -> {
                    if (userAndPermissions != null) {
                        currentUser.setUserAndPermissions(userAndPermissions);
                    } else {
                        logout();
                    }
                })
                .onFailure(throwable -> {
                    AlertEvent.fireInfo(LoginManager.this, throwable.getMessage(), this::logout);
                })
                .taskMonitorFactory(new DefaultTaskMonitorFactory(this), "Fetching permissions...")
                .exec();
    }

    private void logout() {
        // Tell the server we want to logout and the server will provide a logout URL.
        restFactory
                .create(STROOM_SESSION_RESOURCE)
                .method(res -> res.logout(getLocation()))
                .onSuccess(response -> setLocation(response.getUrl()))
                .onFailure(restError -> AlertEvent
                        .fireErrorFromException(LoginManager.this, restError.getException(), null))
                .taskMonitorFactory(new DefaultTaskMonitorFactory(this))
                .exec();
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }

    private static native String getLocation() /*-{
        return window.top.location.href;
    }-*/;

    private static native void setLocation(String uri) /*-{
        window.top.location.href = uri;
    }-*/;
}
