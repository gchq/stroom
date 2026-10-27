/*
 * Copyright 2026 Crown Copyright
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

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.presenter.UserAndGroupHelper;
import stroom.security.shared.User;
import stroom.security.shared.UserResource;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.string.CaseType;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// What the users and user groups screens do to the user or group chosen in their list: delete it,
/// once confirmed, and rename a group in the Edit User Group dialog. Made by the screen that owns
/// the list (`make(provider)`); its dialogs are its own.
public class UserAndGroupActions extends Controller {

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final RestFactory restFactory;
    private final Provider<CreateNewGroupController> createNewGroupControllerProvider;

    /// Made by GIN.
    ///
    /// @param eventBus                         The application's event bus.
    /// @param restFactory                      Deletes the user or group.
    /// @param createNewGroupControllerProvider Makes the Edit User Group dialog.
    @Inject
    public UserAndGroupActions(final EventBus eventBus,
                               final RestFactory restFactory,
                               final Provider<CreateNewGroupController> createNewGroupControllerProvider) {
        super(eventBus);
        this.restFactory = restFactory;
        this.createNewGroupControllerProvider = createNewGroupControllerProvider;
    }

    /// Deletes the user or group chosen in a list, once the user has confirmed it, and fetches the
    /// list again.
    ///
    /// @param userList The list.
    public void delete(final UserListController userList) {
        final User user = userList.getSelectionModel().getSelected();
        if (user != null) {
            final String userDescription = user.getType(CaseType.LOWER)
                                           + " '" + user.asRef().toDisplayString() + "'";
            final String msg = "Are you sure you want to permanently delete " + userDescription + "?"
                               + "\n\nThis will also remove them from any groups that they are currently " +
                               "a member of and delete any API keys they held. " +
                               "Any documents that are solely owned by them will then only be " +
                               "accessible by an administrator."
                               + "\nYou will not be permitted to delete them if any content has a " +
                               "dependency on them.";
            ConfirmEvent.fire(this, msg, ok -> {
                if (ok) {
                    restFactory
                            .create(USER_RESOURCE)
                            .method(resource -> resource.delete(user.getUuid()))
                            .onSuccess(didDelete -> {
                                if (didDelete) {
                                    userList.getSelectionModel().clear(true);
                                    userList.refresh();
                                }
                            })
                            .onFailure(error ->
                                    AlertEvent.fireError(
                                            this,
                                            "Error deleting " + userDescription,
                                            error.getMessage(),
                                            null))
                            .owner(this)
                            .taskMonitorFactory(userList.getTaskMonitor())
                            .exec();
                }
            });
        }
    }

    /// Renames the group chosen in a list, if a group is, in the Edit User Group dialog, and fetches
    /// the list again once it is renamed.
    ///
    /// @param userList The list.
    public void edit(final UserListController userList) {
        final User user = userList.getSelectionModel().getSelected();
        if (user != null && user.isGroup()) {
            createNewGroupControllerProvider.get().edit(this,
                    user,
                    UserAndGroupHelper.createAfterChangeConsumer(userList),
                    taskMonitor());
        }
    }

    // The screen's task monitor, as the update's progress shows on the screen
    private TaskMonitorFactory taskMonitor() {
        final TaskMonitorFactory provided = getProvidedTaskMonitor();
        return provided != null ? provided : new DefaultTaskMonitorFactory(this);
    }
}
