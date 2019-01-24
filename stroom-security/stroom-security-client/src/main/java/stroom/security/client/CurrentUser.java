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
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.activity.client.CurrentActivity;
import stroom.activity.client.SplashPresenter;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.security.client.event.CurrentUserChangedEvent;
import stroom.security.client.event.RequestLogoutEvent;
import stroom.security.shared.CheckDocumentPermissionAction;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.UserAndPermissions;
import stroom.security.shared.UserRef;

import javax.inject.Singleton;
import java.util.Set;

@Singleton
public class CurrentUser implements ClientSecurityContext, HasHandlers {
    private final EventBus eventBus;
    private final Provider<ClientDispatchAsync> dispatcherProvider;
    private final Provider<SplashPresenter> splashPresenterProvider;
    private final CurrentActivity currentActivity;
    private UserRef userRef;
    private Set<String> permissions;

    @Inject
    public CurrentUser(final EventBus eventBus,
                       final Provider<ClientDispatchAsync> dispatcherProvider,
                       final Provider<SplashPresenter> splashPresenterProvider,
                       final CurrentActivity currentActivity) {
        this.eventBus = eventBus;
        this.dispatcherProvider = dispatcherProvider;
        this.splashPresenterProvider = splashPresenterProvider;
        this.currentActivity = currentActivity;
    }

    public void clear() {
        this.userRef = null;
        this.permissions = null;
    }

    public void setUserAndPermissions(final UserAndPermissions userAndPermissions) {
        setUserAndPermissions(userAndPermissions, true);
    }

    public void setUserAndPermissions(final UserAndPermissions userAndPermissions, final boolean fireUserChangedEvent) {
        clear();
        if (userAndPermissions != null) {
            this.userRef = userAndPermissions.getUserRef();
            this.permissions = userAndPermissions.getAppPermissionSet();
        }

        if (fireUserChangedEvent) {
            if (userAndPermissions != null) {
                showSplash();
            } else {
                RequestLogoutEvent.fire(this);
            }
        }
    }

    public UserRef getUserRef() {
        return userRef;
    }

    @Override
    public String getUserId() {
        if (userRef == null) {
            return null;
        }
        return userRef.getName();
    }

    @Override
    public boolean isLoggedIn() {
        return userRef != null;
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
    public Future<Boolean> hasDocumentPermission(final String documentType, final String documentId, final String permission) {
        final FutureImpl<Boolean> future = new FutureImpl<>();
        // Set the default behaviour of the future to show an error.
        future.onFailure(throwable -> AlertEvent.fireErrorFromException(CurrentUser.this, throwable, null));

        final ClientDispatchAsync dispatcher = dispatcherProvider.get();

        dispatcher.exec(new CheckDocumentPermissionAction(documentType, documentId, permission))
                .onSuccess(result -> future.setResult(result.getBoolean()))
                .onFailure(future::setThrowable);

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
