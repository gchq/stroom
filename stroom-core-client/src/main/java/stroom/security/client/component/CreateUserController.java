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

import stroom.security.client.component.CreateUser.CreateType;
import stroom.security.shared.User;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DialogAction;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.function.Consumer;

/// Adds a user group, or one or several identity provider users ([CreateUser]), in a dialog made
/// for one use.
public class CreateUserController extends Controller {

    private final CreateUser view;
    private final CreateNewGroupController group;
    private final CreateExternalUserController externalUser;
    private final CreateMultipleUsersController multipleUsers;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus              The application's event bus.
    /// @param view                  The content.
    /// @param group                 Adds a group, with the form for its name.
    /// @param externalUserProvider  Makes what adds one identity provider user.
    /// @param multipleUsersProvider Makes what adds several.
    @Inject
    public CreateUserController(final EventBus eventBus,
                                final CreateUser view,
                                final CreateNewGroupController group,
                                final Provider<CreateExternalUserController> externalUserProvider,
                                final Provider<CreateMultipleUsersController> multipleUsersProvider) {
        super(eventBus);
        this.view = view;
        this.group = own(group);
        initWidget(view);
        view.setGroupForm(group);
        externalUser = make(externalUserProvider).attach(view.getExternalUser());
        multipleUsers = make(multipleUsersProvider).attach(view.getMultipleUsers());

        final Runnable pressOk = () -> ControllerDialog.pressAction(this, DialogAction.OK);
        group.setOnEnter(pressOk);
        view.getExternalUser().setOnEnter(pressOk);
        view.setOnTypeChange(this::focus);
    }

    /// Shows the dialog, owned by `opener` until it is closed: to add a user group, or one or
    /// several identity provider users.
    ///
    /// @param opener        What shows it.
    /// @param consumer      Given the group or (first) user added.
    /// @param includeGroups Whether it adds a group, rather than users.
    public void show(final HasControllerNode opener,
                     final Consumer<User> consumer,
                     final boolean includeGroups) {
        final String caption;
        final PopupSize popupSize;
        if (includeGroups) {
            view.setCreateTypesVisible(false);
            view.setCreateTypes(List.of(CreateType.USER_GROUP));
            view.setCreateType(CreateType.USER_GROUP);
            caption = "Add User Group";
            popupSize = PopupSize.resizableX();
        } else {
            view.setCreateTypesVisible(true);
            view.setCreateTypes(List.of(CreateType.IDP_USER, CreateType.MULTI_IDP_USERS));
            view.setCreateType(CreateType.IDP_USER);
            caption = "Add External User(s)";
            popupSize = PopupSize.resizable(600, 600);
        }

        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e -> focus(view.getCreateType()))
                .onHideRequest(e -> onHideRequest(e, consumer))
                .fire();
    }

    private void onHideRequest(final HidePopupRequestEvent e, final Consumer<User> consumer) {
        if (!e.isOk()) {
            e.hide();
            return;
        }
        switch (view.getCreateType()) {
            case USER_GROUP -> group.create(consumer, e, null);
            case IDP_USER -> externalUser.create(consumer, e);
            case MULTI_IDP_USERS -> multipleUsers.create(consumer, e);
        }
    }

    // Focuses the form shown for what is to be added
    private void focus(final CreateType createType) {
        switch (createType) {
            case USER_GROUP -> group.focus();
            case IDP_USER -> view.getExternalUser().focus();
            case MULTI_IDP_USERS -> view.getMultipleUsers().focus();
        }
    }
}
