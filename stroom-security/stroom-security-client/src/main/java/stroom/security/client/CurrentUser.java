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
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.activity.client.CurrentActivity;
import stroom.activity.client.SplashPresenter;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.api.Future;
import stroom.security.client.api.FutureImpl;
import stroom.security.client.api.event.CurrentUserChangedEvent;
import stroom.security.client.api.event.RequestLogoutEvent;
import stroom.security.shared.CheckDocumentPermissionRequest;
import stroom.security.shared.DocPermissionResource;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.UserAndPermissions;

import javax.inject.Singleton;
import java.util.Set;

@Singleton
public class CurrentUser implements ClientSecurityContext, HasHandlers {
    private static final DocPermissionResource DOC_PERMISSION_RESOURCE = GWT.create(DocPermissionResource.class);

    private final EventBus eventBus;
    private final RestFactory restFactory;
    private final Provider<SplashPresenter> splashPresenterProvider;
    private final CurrentActivity currentActivity;
    private String userId;
    private Set<String> permissions;

    @Inject
    public CurrentUser(final EventBus eventBus,
                       final RestFactory restFactory,
                       final Provider<SplashPresenter> splashPresenterProvider,
                       final CurrentActivity currentActivity) {
        this.eventBus = eventBus;
        this.restFactory = restFactory;
        this.splashPresenterProvider = splashPresenterProvider;
        this.currentActivity = currentActivity;
    }

    public void clear() {
        this.userId = null;
        this.permissions = null;
    }

    public void setUserAndPermissions(final UserAndPermissions userAndPermissions) {
        setUserAndPermissions(userAndPermissions, true);
    }

    public void setUserAndPermissions(final UserAndPermissions userAndPermissions, final boolean fireUserChangedEvent) {
        clear();
        if (userAndPermissions != null) {
            this.userId = userAndPermissions.getUserId();
            this.permissions = userAndPermissions.getPermissions();
        }

        if (fireUserChangedEvent) {
            if (userAndPermissions != null) {
                showSplash();
            } else {
                RequestLogoutEvent.fire(this);
            }
        }
    }

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public boolean isLoggedIn() {
        return userId != null;
    }

    private boolean isAdmin() {
        if (permissions != null) {
            return permissions.contains(PermissionNames.ADMINISTRATOR);
        }
        return false;
    }

    @Override
    public boolean hasAppPermission(final String name) {
        if (permissions != null) {
            return permissions.contains(name) || isAdmin();
        }
        return false;
    }

    @Override
    public Future<Boolean> hasDocumentPermission(final String documentUuid, final String permission) {
        final FutureImpl<Boolean> future = new FutureImpl<>();
        // Set the default behaviour of the future to show an error.
        future.onFailure(throwable -> AlertEvent.fireErrorFromException(CurrentUser.this, throwable, null));

        final Rest<Boolean> rest = restFactory.create();
        rest
                .onSuccess(future::setResult)
                .onFailure(future::setThrowable)
                .call(DOC_PERMISSION_RESOURCE)
                .checkDocumentPermission(new CheckDocumentPermissionRequest(documentUuid, permission));

        return future;
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }

    private void showSplash() {
        splashPresenterProvider.get().show(ok -> {
            if (ok) {
                currentActivity.showInitialActivityChooser(activity -> CurrentUserChangedEvent.fire(CurrentUser.this));
            }
        });
    }
}
