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

import stroom.dispatch.client.AsyncCallbackAdaptor;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.security.client.event.CurrentUserChangedEvent;
import stroom.security.client.event.RequestLogoutEvent;
import stroom.security.shared.CheckDocumentPermissionAction;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.User;
import stroom.security.shared.UserAndPermissions;
import stroom.util.shared.SharedBoolean;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Set;

@Singleton
public class CurrentUser extends ClientSecurityContext implements HasHandlers {
    private final EventBus eventBus;
    private final Provider<ClientDispatchAsync> dispatcherProvider;
    private User user;
    private Set<String> permissions;

    @Inject
    public CurrentUser(final EventBus eventBus, final Provider<ClientDispatchAsync> dispatcherProvider) {
        this.eventBus = eventBus;
        this.dispatcherProvider = dispatcherProvider;
    }

    public void clear() {
        this.user = null;
        this.permissions = null;
    }

    public void setUserAndPermissions(final UserAndPermissions userAndPermissions) {
        setUserAndPermissions(userAndPermissions, true);
    }

    public void setUserAndPermissions(final UserAndPermissions userAndPermissions, final boolean fireUserChangedEvent) {
        clear();
        if (userAndPermissions != null) {
            this.user = userAndPermissions.getUser();
            this.permissions = userAndPermissions.getAppPermissionSet();
        }

        if (fireUserChangedEvent) {
            if (userAndPermissions != null) {
                CurrentUserChangedEvent.fire(CurrentUser.this);
            } else {
                RequestLogoutEvent.fire(this);
            }
        }
    }

    public User getUser() {
        return user;
    }

    @Override
    public String getUserId() {
        if (user == null) {
            return null;
        }
        return user.getName();
    }

    @Override
    public boolean isLoggedIn() {
        return user != null;
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
    public void hasDocumentPermission(final String documentType, final String documentId, final String permission, final AsyncCallback<Boolean> callback) {
        final ClientDispatchAsync dispatcher = dispatcherProvider.get();
        dispatcher.execute(new CheckDocumentPermissionAction(documentType, documentId, permission), new AsyncCallbackAdaptor<SharedBoolean>() {
            @Override
            public void onSuccess(final SharedBoolean result) {
                callback.onSuccess(result.getBoolean());
            }
        });
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
