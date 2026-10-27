/*
 * Copyright 2024 Crown Copyright
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

package stroom.security.client.component;

import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.UserResource;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.UserRef;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// The Info tab of a user's or group's screen ([UserInfoTab]): shows the user given with
/// [#setUserRef(UserRef)] and, for those who may manage users, saves a change to whether they are
/// enabled as soon as it is made.
public class UserInfoTabController extends Controller {

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final UserInfoTab view;
    private final RestFactory restFactory;
    private final ClientSecurityContext securityContext;
    private UserRef userRef;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus        The application's event bus.
    /// @param view            The content.
    /// @param restFactory     Saves the enabled state.
    /// @param securityContext Says whether the user may manage users, so change the enabled state.
    @Inject
    public UserInfoTabController(final EventBus eventBus,
                                 final UserInfoTab view,
                                 final RestFactory restFactory,
                                 final ClientSecurityContext securityContext) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.securityContext = securityContext;
        initWidget(view);

        view.setOnEnabledChange(isEnabled -> {
            if (isEnabled != null) {
                onEnabledStateChange(isEnabled);
            }
        });
    }

    private void onEnabledStateChange(final boolean isEnabled) {
        if (userRef != null) {
            if (isEnabled != userRef.isEnabled()) {
                restFactory
                        .create(USER_RESOURCE)
                        .method(resource -> resource.fetch(userRef.getUuid()))
                        .onSuccess(user -> {
                            if (isEnabled != user.isEnabled()) {
                                restFactory
                                        .create(USER_RESOURCE)
                                        .method(resource ->
                                                resource.update(user.copy().enabled(isEnabled).build()))
                                        .onSuccess(aVoid ->
                                                userRef = user.asRef())
                                        .owner(this)
                                        .taskMonitorFactory(taskMonitor())
                                        .exec();
                            }
                        })
                        .owner(this)
                        .taskMonitorFactory(taskMonitor())
                        .exec();
            }
        }
    }

    /// Shows a user or group.
    ///
    /// @param userRef The user or group, or `null` for an unknown one.
    public void setUserRef(final UserRef userRef) {
        this.userRef = userRef;
        view.setUserRef(userRef);
        view.setReadOnly(!hasPermissionToEdit());
    }

    private boolean hasPermissionToEdit() {
        return securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION);
    }

    private TaskMonitorFactory taskMonitor() {
        final TaskMonitorFactory provided = getProvidedTaskMonitor();
        return provided != null ? provided : new DefaultTaskMonitorFactory(this);
    }
}
