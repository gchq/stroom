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

import stroom.activity.client.CurrentActivity;
import stroom.activity.client.SplashPresenter;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.api.event.CurrentUserChangedEvent;
import stroom.security.client.api.event.RequestLogoutEvent;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppUserPermissions;
import stroom.security.shared.CheckDocumentPermissionRequest;
import stroom.security.shared.DocPermissionResource;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.UserRef;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Singleton;

@Singleton
public class CurrentUser implements ClientSecurityContext, HasHandlers {

    private static final DocPermissionResource DOC_PERMISSION_RESOURCE = GWT.create(DocPermissionResource.class);

    private final EventBus eventBus;
    private final RestFactory restFactory;
    private final Provider<SplashPresenter> splashPresenterProvider;
    private final CurrentActivity currentActivity;
    private UserRef userRef;
    private Set<AppPermission> permissions;

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
        this.userRef = null;
        this.permissions = null;
    }

    public void setUserAndPermissions(final AppUserPermissions userAndPermissions) {
        setUserAndPermissions(userAndPermissions, true);
    }

    public void setUserAndPermissions(final AppUserPermissions userAndPermissions,
                                      final boolean fireUserChangedEvent) {
        clear();
        if (userAndPermissions != null) {
            this.userRef = userAndPermissions.getUserRef();
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
    public UserRef getUserRef() {
        return userRef;
    }

    @Override
    public boolean isCurrentUser(final UserRef userRef) {
        return Objects.equals(getUserRef(), userRef);
    }

    @Override
    public boolean isLoggedIn() {
        return userRef != null;
    }

    private boolean isAdmin() {
        if (permissions != null) {
            return permissions.contains(AppPermission.ADMINISTRATOR);
        }
        return false;
    }

    @Override
    public boolean hasAppPermission(final AppPermission name) {
        if (permissions != null) {
            return permissions.contains(name) || isAdmin();
        }
        return false;
    }

    @Override
    public void hasDocumentPermission(final DocRef docRef,
                                      final DocumentPermission permission,
                                      final Consumer<Boolean> consumer,
                                      final Consumer<Throwable> errorHandler,
                                      final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(DOC_PERMISSION_RESOURCE)
                .method(res ->
                        res.checkDocumentPermission(new CheckDocumentPermissionRequest(docRef, permission)))
                .onSuccess(consumer)
                .onFailure(t -> errorHandler.accept(t.getException()))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
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
