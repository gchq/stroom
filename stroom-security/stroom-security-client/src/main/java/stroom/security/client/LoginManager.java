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

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.event.LogoutEvent;
import stroom.security.shared.AppPermissionResource;
import stroom.security.shared.SessionResource;
import stroom.security.shared.UrlResponse;
import stroom.security.shared.UserAndPermissions;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;

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
        // Tell the server we want to logout and the server will provide a logout URL.
        final Rest<UrlResponse> rest = restFactory.create();
        rest
                .onSuccess(response -> setLocation(response.getUrl()))
                .onFailure(throwable -> AlertEvent.fireErrorFromException(LoginManager.this, throwable, null))
                .call(STROOM_SESSION_RESOURCE)
                .logout(getLocation());
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
