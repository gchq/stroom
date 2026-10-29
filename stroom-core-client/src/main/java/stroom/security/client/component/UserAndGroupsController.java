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
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.client.UserAndGroupHelper;
import stroom.security.client.UserScreen;
import stroom.security.client.UsersAndGroupsPlugin;
import stroom.security.client.component.UserListController.Mode;
import stroom.security.shared.User;
import stroom.security.shared.UserFields;
import stroom.security.shared.UserResource;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.util.shared.string.CaseType;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.HtmlBuilder;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// The User Groups screen ([UserAndGroups]): every user and group, which groups can be created,
/// edited and deleted in; below, the groups the selected one is a member of and the members of the
/// selected group, which can be added to and removed from. Given a user ([#setUserRef(UserRef)]),
/// it shows only that user's groups and members, as a user's User Groups tab.
public class UserAndGroupsController extends Controller {

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final UserAndGroups view;
    private final UserListController userList;
    private final UserListController parentsList;
    private final UserListController childrenList;

    private final Provider<CreateUserController> createUserProvider;
    private final UserAndGroupActions actions;
    private final Provider<UserRefPopupController> userRefPopupProvider;
    private final RestFactory restFactory;

    private final ToolbarButton createButton;
    private final ToolbarButton editButton;
    private final ToolbarButton deleteButton;

    private final ToolbarButton addMemberOfButton;
    private final ToolbarButton removeMemberOfButton;

    private final ToolbarButton addMembersInButton;
    private final ToolbarButton removeMembersInButton;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                   The application's event bus.
    /// @param view                       The content.
    /// @param userListControllerProvider Makes the three lists.
    /// @param createUserProvider         Makes the dialog that creates a group (or user).
    /// @param actions                    Edits and deletes groups.
    /// @param userRefPopupProvider       Makes the popup that picks a user or group.
    /// @param uiConfigCache              Says whether users come from an external identity provider.
    /// @param restFactory                Changes group membership.
    @Inject
    public UserAndGroupsController(final EventBus eventBus,
                                   final UserAndGroups view,
                                   final Provider<UserListController> userListControllerProvider,
                                   final Provider<CreateUserController> createUserProvider,
                                   final UserAndGroupActions actions,
                                   final Provider<UserRefPopupController> userRefPopupProvider,
                                   final UiConfigCache uiConfigCache,
                                   final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.userList = make(userListControllerProvider).attach(view.getUserList());
        this.userList.setMode(Mode.USERS_AND_GROUPS);
        this.userList.setLabel(UsersAndGroupsPlugin.SCREEN_NAME + ":");
        this.userList.setHelpText(HtmlBuilder.builder()
                .para(paraBuilder -> paraBuilder
                        .append("Lists all users and user groups. User groups can be added and deleted."))
                .para(paraBuilder -> paraBuilder
                        .append("To add/delete/disable users, open the ")
                        .italic("Users")
                        .append(" screen."))
                .toSafeHtml());
        // Top pane doesn't need to link back to itself
        this.userList.setValidUserScreensForActionMenu(UserScreen.allExcept(UserScreen.USER_GROUPS));
        this.userList.setResultPageConsumer(userResultPage ->
                onSelection());
        this.userList.setOnCopyPermissions(this::copyPermissionsTo);

        this.parentsList = make(userListControllerProvider).attach(view.getParents());
        // A parent can only be a group
        this.parentsList.setMode(Mode.GROUPS_ONLY);
        this.parentsList.setValidUserScreensForActionMenu(UserScreen.all());

        this.childrenList = make(userListControllerProvider).attach(view.getChildren());
        this.childrenList.setMode(Mode.USERS_AND_GROUPS);
        this.childrenList.setValidUserScreensForActionMenu(UserScreen.all());

        this.createUserProvider = createUserProvider;
        this.actions = own(actions);
        this.userRefPopupProvider = userRefPopupProvider;
        this.restFactory = restFactory;

        final Toolbar userActions = userList.getToolbar();
        userActions.setName("User and group actions");
        createButton = userActions.button(SvgImage.ADD, "Create Group", this::onCreateUserOrGroup);
        editButton = userActions.button(SvgImage.EDIT, "Edit Selected Group", this::onEditUserOrGroup);
        editButton.setEnabled(false);

        // Only want to let user create a stroom user if they are using an external IDP.
        // If internal then all users are available to be picked from the account tbl.
        uiConfigCache.get(guard(config -> {
            if (config != null) {
                if (config.isExternalIdentityProvider()) {
                    createButton.setTitle("Create User Or Group");
                }
            }
        }), taskMonitor());

        // You can only delete groups on this screen, UsersController for user deletion
        deleteButton = userActions.button(SvgImage.DELETE, "Delete Selected Group", this::onDelete);
        deleteButton.setEnabled(false);

        final Toolbar memberOfActions = parentsList.getToolbar();
        memberOfActions.setName("Member of actions");
        addMemberOfButton = memberOfActions.button(SvgImage.ADD, "Add to Group", this::onAddMemberOf);
        removeMemberOfButton = memberOfActions.button(SvgImage.REMOVE, "Remove from Selected Group",
                this::onRemoveMemberOf);
        removeMemberOfButton.setEnabled(false);

        final Toolbar membersActions = childrenList.getToolbar();
        membersActions.setName("Members actions");
        addMembersInButton = membersActions.button(SvgImage.ADD, "Add Member to Group", this::onAddMembersIn);
        removeMembersInButton = membersActions.button(SvgImage.REMOVE, "Remove Selected Member From Group",
                this::onRemoveMembersIn);
        removeMembersInButton.setEnabled(false);

        // Initialise state.
        onSelection();

        own(userList.getSelectionModel().addSelectionHandler(e -> {
            onSelection();
        }));

        // Deal with Member Of Groups pane.
        own(parentsList.getSelectionModel().addSelectionHandler(e -> {
            final User user = userList.getSelectionModel().getSelected();
            final User group = parentsList.getSelectionModel().getSelected();
            removeMemberOfButton.setEnabled(group != null && user != null);
        }));

        // Deal with Members in Group pane.
        own(childrenList.getSelectionModel().addSelectionHandler(e -> {
            final User group = userList.getSelectionModel().getSelected();
            final User user = childrenList.getSelectionModel().getSelected();
            removeMembersInButton.setEnabled(group != null && user != null);
        }));

    }

    private void onAddMemberOf() {
        final User user = userList.getSelectionModel().getSelected();
        if (user != null) {
            final UserRefPopupController userRefPopup = userRefPopupProvider.get();
            userRefPopup.setAdditionalTerm(ExpressionTerm
                    .builder()
                    .field(UserFields.IS_GROUP.getFldName())
                    .condition(Condition.EQUALS)
                    .value("true")
                    .build());

            userRefPopup.showOnce(this, "Select Group", groupRef -> {
                if (groupRef != null) {
                    addUserToGroup(user.asRef(), groupRef, parentsList, groupRef);
                }
            });
        }
    }

    private void onRemoveMemberOf() {
        final User user = userList.getSelectionModel().getSelected();
        final User group = parentsList.getSelectionModel().getSelected();
        removeUserFromGroup(user, group, parentsList);
    }

    private void onAddMembersIn() {
        final User group = userList.getSelectionModel().getSelected();
        if (group != null) {
            userRefPopupProvider.get().showOnce(this, "Add User Or Group", userRef -> {
                if (userRef != null) {
                    addUserToGroup(userRef, group.asRef(), childrenList, userRef);
                }
            });
        }
    }

    private void onRemoveMembersIn() {
        final User group = userList.getSelectionModel().getSelected();
        final User user = childrenList.getSelectionModel().getSelected();
        removeUserFromGroup(user, group, childrenList);
    }

    private void setParentsListExtraTerm(final UserRef userRef) {
        parentsList.setAdditionalTerm(ExpressionTerm
                .builder()
                .field(UserFields.PARENTS_OF.getFldName())
                .condition(Condition.EQUALS)
                .value(userRef.getUuid())
                .build());
        parentsList.refresh();

        parentsList.setLabel(getIdentityDescription(userRef, CaseType.SENTENCE) + " is a member of:");
        final String lowerUserDesc = getIdentityDescription(userRef, CaseType.LOWER);
        parentsList.setHelpText(HtmlBuilder.builder()
                .para("Lists user groups that " + lowerUserDesc + " is a direct member of.")
                .para(paraBuilder -> paraBuilder
                        .append("Use the '")
                        .bold("+")
                        .append("' icon to add ")
                        .append(lowerUserDesc)
                        .append(" to another group."))
                .para(paraBuilder -> paraBuilder
                        .append("Use the '")
                        .bold("-")
                        .append("' icon to remove ")
                        .append(lowerUserDesc)
                        .append(" from the selected parent group."))
                .toSafeHtml());
    }

    private String getIdentityDescription(final UserRef userRef, final CaseType caseType) {
        Objects.requireNonNull(userRef);
        return userRef.getType(caseType) + " \"" + userRef.getDisplayName() + "\"";
    }

    private void setChildrenListAdditionalTerm(final UserRef userRef) {
        childrenList.setAdditionalTerm(ExpressionTerm
                .builder()
                .field(UserFields.CHILDREN_OF.getFldName())
                .condition(Condition.EQUALS)
                .value(userRef.getUuid())
                .build());
        childrenList.refresh();

        final String identityDesc = getIdentityDescription(userRef, CaseType.LOWER);
        if (userRef.isGroup()) {
            childrenList.setLabel("Members of " + identityDesc + ":");
            childrenList.setHelpText(HtmlBuilder.builder()
                    .para("Lists users and user groups that are direct members of " + identityDesc + ".")
                    .para(paraBuilder -> paraBuilder
                            .append("Use the '")
                            .bold("+")
                            .append("' icon to add a new user or group as a member of ")
                            .append(identityDesc)
                            .append("."))
                    .para(paraBuilder -> paraBuilder
                            .append("Use the '")
                            .bold("-")
                            .append("' icon to remove the selected member from ")
                            .append(identityDesc)
                            .append("."))
                    .toSafeHtml());
        } else {
            childrenList.setLabel("No group selected");
            childrenList.setHelpText(HtmlBuilder.builder()
                    .para("Users cannot have members.")
                    .toSafeHtml());
        }
    }

    private void onSelection() {
        final User selected = userList.getSelectionModel().getSelected();
        onSelection(NullSafe.get(selected, User::asRef));
    }

    private void onSelection(final UserRef selected) {
        if (selected != null) {
            setParentsListExtraTerm(selected);
            setChildrenListAdditionalTerm(selected);

            view.setParentsEnabled(true);
            view.setChildrenEnabled(true);
            // A user has no members, so the members pane only applies to a group
            view.setChildrenVisible(selected.isGroup());

            if (selected.isGroup()) {
                editButton.setTitle("Edit " + getDescription(selected));
                editButton.setEnabled(true);
                deleteButton.setTitle("Delete " + getDescription(selected));
                deleteButton.setEnabled(true);
            } else {
                editButton.setTitle("Edit Selected Group (users cannot be edited here)");
                editButton.setEnabled(false);
                deleteButton.setTitle("Delete Selected Group (users can only be deleted on the Users screen)");
                deleteButton.setEnabled(false);
            }
            addMemberOfButton.setEnabled(true);
            addMembersInButton.setEnabled(selected.isGroup());

        } else {
            parentsList.setLabel("No Selection");
            childrenList.setLabel("No Selection");
            // The panes stay shown (dimmed), so empty them rather than leave the last selection's
            // groups and members under 'No Selection'
            parentsList.clear();
            childrenList.clear();
            view.setParentsEnabled(false);
            view.setChildrenEnabled(false);
            view.setChildrenVisible(true);
            editButton.setTitle("Edit Selected Group");
            editButton.setEnabled(false);
            deleteButton.setTitle("Delete Selected Group");
            deleteButton.setEnabled(false);
            addMemberOfButton.setEnabled(false);
            addMembersInButton.setEnabled(false);
        }
    }

    private void addUserToGroup(final UserRef user,
                                final UserRef group,
                                final UserListController targetList,
                                final UserRef selection) {
        if (group != null && user != null) {
            restFactory
                    .create(USER_RESOURCE)
                    .method(res -> res.addUserToGroup(user.getUuid(), group.getUuid()))
                    .onSuccess(r -> {

                        // Add a consumer to monitor the new result list when there is a refresh.
                        // This allows us to try to select the newly added item.
                        targetList.setResultPageConsumer(resultPage -> {
                            targetList.setResultPageConsumer(null);
                            if (selection == null) {
                                targetList.getSelectionModel().clear();
                            } else {
                                if (resultPage != null && resultPage.getValues() != null) {
                                    final Optional<User> optional = resultPage
                                            .getValues()
                                            .stream()
                                            .filter(u -> u.getUuid().equals(selection.getUuid()))
                                            .findAny();
                                    if (optional.isPresent()) {
                                        targetList.getSelectionModel().setSelected(optional.get());
                                    } else {
                                        targetList.getSelectionModel().clear();
                                    }
                                }
                            }
                        });

                        userList.refresh();
                        parentsList.refresh();
                        childrenList.refresh();
                    })
                    .owner(this)
                    .taskMonitorFactory(targetList.getTaskMonitor())
                    .exec();
        }
    }

    private void removeUserFromGroup(final User user,
                                     final User group,
                                     final UserListController targetList) {
        if (NullSafe.allNonNull(user, group)) {
            final User nextSelection = getNextSelection(targetList);

            restFactory
                    .create(USER_RESOURCE)
                    .method(res -> res.removeUserFromGroup(user.getUuid(), group.getUuid()))
                    .onSuccess(r -> {
                        userList.refresh();
                        parentsList.refresh();
                        childrenList.refresh();

                        if (nextSelection == null) {
                            targetList.getSelectionModel().clear();
                        } else {
                            targetList.getSelectionModel().setSelected(nextSelection);
                        }
                    })
                    .owner(this)
                    .taskMonitorFactory(targetList.getTaskMonitor())
                    .exec();
        }
    }

    // Asks which user to copy a user's groups and permissions from
    private void copyPermissionsTo(final UserRef toUser) {
        final UserRefPopupController userRefPopup = userRefPopupProvider.get();
        userRefPopup.setAdditionalTerm(ExpressionTerm
                .builder()
                .field(UserFields.IS_GROUP.getFldName())
                .condition(Condition.EQUALS)
                .value("false")
                .build());
        userRefPopup.showOnce(this, "Select User", fromUser -> {
            if (fromUser != null) {
                copyPermissions(fromUser, toUser);
            }
        });
    }

    private void copyPermissions(final UserRef fromUser,
                                 final UserRef toUser) {
        if (fromUser != null && toUser != null) {
            restFactory
                    .create(USER_RESOURCE)
                    .method(res -> res.copyGroupsAndPermissions(fromUser.getUuid(), toUser.getUuid()))
                    .onSuccess(user -> {
                        userList.refresh();
                        parentsList.refresh();
                        childrenList.refresh();
                    })
                    .owner(this)
                    .taskMonitorFactory(parentsList.getTaskMonitor())
                    .exec();
        }
    }

    private User getNextSelection(final UserListController targetList) {
        final User currentSelection = targetList.getSelectionModel().getSelected();
        User nextSelection = null;
        final ResultPage<User> resultPage = targetList.getCurrentData();
        if (resultPage != null) {
            final List<User> list = resultPage.getValues();
            if (currentSelection != null && list != null) {
                final int index = list.indexOf(currentSelection);
                if (index == 0) {
                    if (list.size() > 1) {
                        nextSelection = list.get(index + 1);
                    }
                } else if (index > 0) {
                    nextSelection = list.get(index - 1);
                }
            }
        }
        return nextSelection;
    }

    private void onCreateUserOrGroup() {
        createUserProvider.get().show(this,
                UserAndGroupHelper.createAfterChangeConsumer(userList),
                true);
    }

    private void onEditUserOrGroup() {
        actions.edit(userList);
    }

    private void onDelete() {
        actions.delete(userList);
    }

    private static String getDescription(final UserRef userRef) {
        return userRef.getType(CaseType.SENTENCE)
               + " '" + userRef.toDisplayString() + "'";
    }

    /// Fetches the users and groups again.
    public void refresh() {
        userList.refresh();
        onSelection();
    }

    /// Filters the users and groups to one user.
    ///
    /// @param userRef The user.
    public void showUser(final UserRef userRef) {
        userList.showUser(userRef);
    }

    /// Shows only one user's or group's groups and members, without the users and groups.
    ///
    /// @param userRef The user or group.
    public void setUserRef(final UserRef userRef) {
        Objects.requireNonNull(userRef);

        // Specific user so don't show the top pane
        view.setUserListVisible(false);

        onSelection(userRef);

    }

}
