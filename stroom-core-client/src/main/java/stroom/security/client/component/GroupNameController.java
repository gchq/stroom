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

package stroom.security.client.component;

import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.document.client.component.NameEntry;
import stroom.security.shared.User;
import stroom.security.shared.UserResource;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

/// A user group's name ([NameEntry]): the form Add User Group shows to create one, and the Edit
/// User Group dialog that renames one. A deleted group of the same name can be restored.
public class GroupNameController extends Controller implements Focus {

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final RestFactory restFactory;
    private NameEntry view;

    /// Made by GIN; [#attach(NameEntry)] gives it its name field.
    ///
    /// @param eventBus    The application's event bus.
    /// @param restFactory Creates and updates the group.
    @Inject
    public GroupNameController(final EventBus eventBus,
                               final RestFactory restFactory) {
        super(eventBus);
        this.restFactory = restFactory;
    }

    /// Attaches this to a name field, e.g. one a template declares. The field is then this
    /// controller's widget, e.g. to show it as the Edit User Group dialog.
    ///
    /// @param view The name field.
    /// @return This, for its owner to keep.
    public GroupNameController attach(final NameEntry view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);
        // Shown as its own dialog (edit); a dialog it is shown in gives its own
        view.setOnEnter(() -> ControllerDialog.pressAction(this, DialogAction.OK));
        return this;
    }

    /// @param onEnter What Enter in the name does, e.g. press OK on the dialog it is shown in.
    public void setOnEnter(final Runnable onEnter) {
        view.setOnEnter(onEnter);
    }

    @Override
    public void focus() {
        view.focus();
    }

    /// Renames a group in the Edit User Group dialog, owned by `opener` until it is closed.
    ///
    /// @param opener             What shows it.
    /// @param group              The group.
    /// @param onChanged          Given the group once it is renamed.
    /// @param taskMonitorFactory Shows the progress of the update.
    public void edit(final HasControllerNode opener,
                     final User group,
                     final Consumer<User> onChanged,
                     final TaskMonitorFactory taskMonitorFactory) {
        view.setName(group.getSubjectId());
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(600, 200))
                .caption("Edit User Group")
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final String name = NullSafe.trim(view.getName());
                        if (name.isEmpty()) {
                            // As for a new group: said under the field, and the dialog stays open
                            view.setNameInvalid("You must provide a name for the group");
                            e.reset();
                            return;
                        }
                        update(group.copy()
                                .subjectId(name)
                                .displayName(name)
                                .build(), onChanged, e, taskMonitorFactory);
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    /// Creates a group with the name entered, when OK is pressed on the dialog this is shown in.
    /// With no name it says so under the field and keeps the dialog open.
    ///
    /// @param consumer           Given the group once it is created (or restored).
    /// @param event              The dialog's OK.
    /// @param taskMonitorFactory Shows the progress of the request.
    public void create(final Consumer<User> consumer,
                       final HidePopupRequestEvent event,
                       final TaskMonitorFactory taskMonitorFactory) {
        final String name = NullSafe.trim(view.getName());
        if (name.isEmpty()) {
            // Said under the field, which is focused, so a screen reader reads it
            view.setNameInvalid("You must provide a name for the group");
            event.reset();
            return;
        }
        restFactory
                .create(USER_RESOURCE)
                .method(res -> res.createGroup(name))
                .onSuccess(result -> {
                    if (!result.isEnabled()) {
                        enable(result, consumer, event, taskMonitorFactory);
                    } else {
                        consumer.accept(result);
                        event.hide();
                    }
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .owner(this)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    private void enable(final User user,
                        final Consumer<User> consumer,
                        final HidePopupRequestEvent event,
                        final TaskMonitorFactory taskMonitorFactory) {
        ConfirmEvent.fire(this,
                "A deleted group already exists with the same name, " +
                "would you like to restore the existing group?",
                ok -> {
                    if (ok) {
                        update(user.copy().enabled(true).build(), consumer, event, taskMonitorFactory);
                    } else {
                        consumer.accept(user);
                        event.hide();
                    }
                });
    }

    private void update(final User user,
                        final Consumer<User> consumer,
                        final HidePopupRequestEvent event,
                        final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(USER_RESOURCE)
                .method(res -> res.update(user))
                .onSuccess(result -> {
                    consumer.accept(result);
                    event.hide();
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .owner(this)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }
}
