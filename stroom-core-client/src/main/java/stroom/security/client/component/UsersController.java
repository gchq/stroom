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

import stroom.content.client.component.ContentTabController;
import stroom.security.client.UserAndGroupHelper;
import stroom.security.client.UserScreen;
import stroom.security.client.component.UserListController.Mode;
import stroom.security.shared.User;
import stroom.security.shared.UserResource;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.util.client.HtmlBuilder;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// Shows Stroom's users, and creates and deletes them.
public class UsersController extends ContentTabController {

    public static final String TAB_TYPE = "Users";
    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final UserListController userList;
    private final Provider<CreateUserController> createUserProvider;
    private final UserAndGroupActions actions;

    private final ToolbarButton deleteButton;

    /// Made by GIN, with its screen and its parts.
    ///
    /// @param eventBus           The application's event bus.
    /// @param view               Its component.
    /// @param userList           The user list.
    /// @param createUserProvider Makes the create user.
    /// @param actions            The actions.
    @Inject
    public UsersController(final EventBus eventBus,
                           final Users view,
                           final UserListController userList,
                           final Provider<CreateUserController> createUserProvider,
                           final UserAndGroupActions actions) {
        super(eventBus);
        initWidget(view);
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
        actions.edit(userList);
    }

    private void onDelete() {
        actions.delete(userList);
    }

    /// Fetches the users again.
    public void refresh() {
        userList.refresh();
        onSelection();
    }

    /// Filters the users.
    ///
    /// @param filterInput The filter.
    public void setFilterInput(final String filterInput) {
        userList.setQuickFilterText(filterInput);
    }

    private void onSelection() {
        final User selected = userList.getSelectionModel().getSelected();
        if (selected != null) {
            deleteButton.setTitle("Delete " + getDescription(selected));
            deleteButton.setEnabled(true);
        } else {
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

    /// Filters the users to one.
    ///
    /// @param userRef The user.
    public void showUser(final UserRef userRef) {
        userList.showUser(userRef);
    }
}
