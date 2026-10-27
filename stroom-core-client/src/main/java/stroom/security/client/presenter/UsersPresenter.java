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

package stroom.security.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.security.client.component.CreateUserController;
import stroom.security.client.component.UserAndGroupActions;
import stroom.security.client.component.UserListController;
import stroom.security.client.component.UserListController.Mode;
import stroom.security.client.presenter.UsersPresenter.UsersView;
import stroom.security.shared.User;
import stroom.security.shared.UserResource;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.presenter.client.View;
import stroom.widget.util.client.HtmlBuilder;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/**
 * For managing all the users (not groups)
 */
public class UsersPresenter extends ContentTabPresenter<UsersView> {

    public static final String TAB_TYPE = "Users";

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final UserListController userList;
    private final Provider<CreateUserController> createUserProvider;
    private final UserAndGroupActions actions;

    //    private final ToolbarButton editButton;
    private final ToolbarButton deleteButton;

    @Inject
    public UsersPresenter(final EventBus eventBus,
                          final UsersView view,
                          final UserListController userList,
                          final Provider<CreateUserController> createUserProvider,
                          final UserAndGroupActions actions) {
        super(eventBus, view);
        own(userList);
        this.userList = userList;
        this.createUserProvider = createUserProvider;
        this.actions = own(actions);

        view.setUserList(this.userList);

        this.userList.setLabel("Users:");
        this.userList.setHelpText(HtmlBuilder.builder()
                .para(htmlBuilder -> htmlBuilder.append(
                        "Lists all Stroom users. Users can be added, deleted, disabled and enabled."))
                .toSafeHtml());
        this.userList.setMode(Mode.USERS_ONLY);
        this.userList.setShowUniqueUserIdCol(true);
        this.userList.setShowEnabledCol(true);
        this.userList.setValidUserScreensForActionMenu(UserScreen.allExcept(UserScreen.USERS));

        final Toolbar toolbar = userList.getToolbar();
        toolbar.setName("User actions");
        toolbar.button(SvgImage.ADD, "Create User", this::onCreateUserOrGroup);
//        editButton = toolbar.button(SvgImage.EDIT, "Edit User", this::onEditUserOrGroup);
        deleteButton = toolbar.button(SvgImage.DELETE, "Delete Selected User", this::onDelete);
        deleteButton.setEnabled(false);

        /// Initialise state.
        onSelection();

        own(userList.getSelectionModel().addSelectionHandler(e -> {
            onSelection();
        }));
    }

    private void onCreateUserOrGroup() {
        createUserProvider.get().show(this, UserAndGroupHelper.createAfterChangeConsumer(userList), false);
    }

    private void onEditUserOrGroup() {
        // The edit dialog is this one's, made for one use
        actions.edit(userList);
    }

    private void onDelete() {
        actions.delete(userList);
    }

    public void refresh() {
        userList.refresh();
        onSelection();
    }

    public void setFilterInput(final String filterInput) {
        userList.setQuickFilterText(filterInput);
    }

    private void onSelection() {
        final User selected = userList.getSelectionModel().getSelected();
        if (selected != null) {
//            editButton.setTitle("Edit " + getDescription(selected));
//            editButton.setEnabled(true);
            deleteButton.setTitle("Delete " + getDescription(selected));
            deleteButton.setEnabled(true);
        } else {
//            editButton.setEnabled(false);
            deleteButton.setTitle("Delete Selected User");
            deleteButton.setEnabled(false);
        }
    }

    private String getDescription(final User user) {
        return "User '" + user.asRef().toDisplayString() + "'";
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.USER;
    }

    @Override
    public String getLabel() {
        return "Users";
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }

    public void showUser(final UserRef userRef) {
        userList.showUser(userRef);
    }


    // --------------------------------------------------------------------------------


    public interface UsersView extends View {

        void setUserList(IsWidget view);
    }
}
