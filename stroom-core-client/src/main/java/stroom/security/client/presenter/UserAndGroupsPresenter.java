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

package stroom.security.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.client.presenter.UserAndGroupsPresenter.UserAndGroupsView;
import stroom.security.shared.User;
import stroom.security.shared.UserFields;
import stroom.security.shared.UserResource;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.SelectionType;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Optional;

public class UserAndGroupsPresenter extends ContentTabPresenter<UserAndGroupsView> {

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final UserListPresenter userList;
    private final UserListPresenter memberOfList;
    private final UserListPresenter membersInList;

    private final CreateNewGroupPresenter createNewGroupPresenter;
    private final CreateNewUserPresenter createNewUserPresenter;
    private final CreateMultipleUsersPresenter createMultipleUsersPresenter;
    private final Provider<UserRefPopupPresenter> userRefPopupPresenterProvider;
    private final RestFactory restFactory;

    private ButtonView createNewGroupButton = null;
    private ButtonView createNewUserButton = null;
    private ButtonView createMultipleUsersButton = null;
    private ButtonView deleteUserButton = null;

    private ButtonView addMemberOfButton = null;
    private ButtonView removeMemberOfButton = null;

    private ButtonView addMembersInButton = null;
    private ButtonView removeMembersInButton = null;

    @Inject
    public UserAndGroupsPresenter(final EventBus eventBus,
                                  final UserAndGroupsView view,
                                  final Provider<UserListPresenter> userListPresenterProvider,
                                  final CreateNewGroupPresenter createNewGroupPresenter,
                                  final CreateNewUserPresenter createNewUserPresenter,
                                  final CreateMultipleUsersPresenter createMultipleUsersPresenter,
                                  final Provider<UserRefPopupPresenter> userRefPopupPresenterProvider,
                                  final UiConfigCache uiConfigCache,
                                  final RestFactory restFactory) {
        super(eventBus, view);
        this.userList = userListPresenterProvider.get();
        this.memberOfList = userListPresenterProvider.get();
        this.membersInList = userListPresenterProvider.get();
        this.createNewGroupPresenter = createNewGroupPresenter;
        this.createNewUserPresenter = createNewUserPresenter;
        this.createMultipleUsersPresenter = createMultipleUsersPresenter;
        this.userRefPopupPresenterProvider = userRefPopupPresenterProvider;
        this.restFactory = restFactory;

        userList.getView().setLabel("Users And Groups");
        view.setUserList(userList.getView());
        memberOfList.getView().setLabel("Member Of Groups");
        view.setMemberOfView(memberOfList.getView());
        membersInList.getView().setLabel("Members In Group");
        view.setMembersInView(membersInList.getView());

        createNewGroupButton = userList.addButton(
                SvgPresets.ADD.title("Add User Group"));

        // Only want to let user create a stroom user if they are using an external IDP.
        // If internal then all users are available to be picked from the account tbl.
        uiConfigCache.get(config -> {
            if (config != null) {
                if (config.isExternalIdentityProvider()) {
                    createNewUserButton = userList.addButton(
                            SvgPresets.ADD.title("Add Identity Provider User"));
                    createMultipleUsersButton = userList.addButton(
                            SvgPresets.ADD_MULTIPLE.title("Add Multiple Identity Provider Users"));
                }
            }
        }, this);

        deleteUserButton = userList.addButton(SvgPresets.DELETE.title("Delete User"));

        addMemberOfButton = memberOfList.addButton(SvgPresets.ADD);
        removeMemberOfButton = memberOfList.addButton(SvgPresets.REMOVE);

        addMembersInButton = membersInList.addButton(SvgPresets.ADD);
        removeMembersInButton = membersInList.addButton(SvgPresets.REMOVE);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(createNewGroupButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onAddGroup();
            }
        }));
        GwtNullSafe.consume(createNewUserButton, button ->
                registerHandler(createNewUserButton.addClickHandler(e -> {
                    if (MouseUtil.isPrimary(e)) {
                        onAddUser();
                    }
                })));
        // addMultipleButton not always present
        GwtNullSafe.consume(createMultipleUsersButton, button ->
                registerHandler(createMultipleUsersButton.addClickHandler(e -> {
                    if (MouseUtil.isPrimary(e)) {
                        onAddMultiple();
                    }
                })));
        registerHandler(deleteUserButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onDelete();
            }
        }));
        registerHandler(userList.getSelectionModel().addSelectionHandler(e -> {
            onSelection();
        }));

        // Deal with Member Of Groups pane.
        registerHandler(memberOfList.getSelectionModel().addSelectionHandler(e -> {
            final User user = userList.getSelectionModel().getSelected();
            final User group = memberOfList.getSelectionModel().getSelected();
            removeMemberOfButton.setEnabled(group != null && user != null);
        }));
        registerHandler(addMemberOfButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                final User user = userList.getSelectionModel().getSelected();
                if (user != null) {
                    final UserRefPopupPresenter userRefPopupPresenter = userRefPopupPresenterProvider.get();
                    userRefPopupPresenter.setAdditionalTerm(ExpressionTerm
                            .builder()
                            .field(UserFields.IS_GROUP.getFldName())
                            .condition(Condition.EQUALS)
                            .value("true")
                            .build());
                    userRefPopupPresenter.show("Select Group", groupRef -> {
                        if (groupRef != null) {
                            addUserToGroup(user.asRef(), groupRef, memberOfList, groupRef);
                        }
                    });
                }
            }
        }));
        registerHandler(removeMemberOfButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                final User user = userList.getSelectionModel().getSelected();
                final User group = memberOfList.getSelectionModel().getSelected();
                removeUserFromGroup(user, group, memberOfList);
            }
        }));

        // Deal with Members in Group pane.
        registerHandler(membersInList.getSelectionModel().addSelectionHandler(e -> {
            final User group = userList.getSelectionModel().getSelected();
            final User user = membersInList.getSelectionModel().getSelected();
            removeMembersInButton.setEnabled(group != null && user != null);
        }));
        registerHandler(addMembersInButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                final User group = userList.getSelectionModel().getSelected();
                if (group != null) {
                    final UserRefPopupPresenter userRefPopupPresenter = userRefPopupPresenterProvider.get();
                    userRefPopupPresenter.show("Add User Or Group", userRef -> {
                        if (userRef != null) {
                            addUserToGroup(userRef, group.asRef(), membersInList, userRef);
                        }
                    });
                }
            }
        }));
        registerHandler(removeMembersInButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                final User group = userList.getSelectionModel().getSelected();
                final User user = membersInList.getSelectionModel().getSelected();
                removeUserFromGroup(user, group, membersInList);
            }
        }));
    }

    private void onSelection() {
        final User selected = userList.getSelectionModel().getSelected();
        if (selected != null) {
            memberOfList.setAdditionalTerm(ExpressionTerm
                    .builder()
                    .field(UserFields.PARENT_GROUP.getFldName())
                    .condition(Condition.EQUALS)
                    .value(selected.getUuid())
                    .build());
            memberOfList.refresh();
            membersInList.setAdditionalTerm(ExpressionTerm
                    .builder()
                    .field(UserFields.GROUP_CONTAINS.getFldName())
                    .condition(Condition.EQUALS)
                    .value(selected.getUuid())
                    .build());
            membersInList.refresh();
            getView().setMembersInVisible(selected.isGroup());

            deleteUserButton.setTitle("Delete " + getDescription(selected));
            deleteUserButton.setEnabled(true);
            addMemberOfButton.setEnabled(true);
            addMembersInButton.setEnabled(selected.isGroup());

        } else {
            getView().setMembersInVisible(false);
            deleteUserButton.setTitle("No Selection");
            deleteUserButton.setEnabled(false);
            addMemberOfButton.setEnabled(false);
            addMembersInButton.setEnabled(false);
        }
    }

    private void addUserToGroup(final UserRef user,
                                final UserRef group,
                                final UserListPresenter userListPresenter,
                                final UserRef selection) {
        if (group != null && user != null) {
            restFactory
                    .create(USER_RESOURCE)
                    .method(res -> res.addUserToGroup(user.getUuid(), group.getUuid()))
                    .onSuccess(r -> {

                        // Add a consumer to monitor the new result list when there is a refresh.
                        // This allows us to try to select the newly added item.
                        userListPresenter.setResultPageConsumer(resultPage -> {
                            userListPresenter.setResultPageConsumer(null);
                            if (selection == null) {
                                userListPresenter.getSelectionModel().clear();
                            } else {
                                if (resultPage != null && resultPage.getValues() != null) {
                                    final Optional<User> optional = resultPage
                                            .getValues()
                                            .stream()
                                            .filter(u -> u.getUuid().equals(selection.getUuid()))
                                            .findAny();
                                    if (optional.isPresent()) {
                                        userListPresenter.getSelectionModel().setSelected(optional.get());
                                    } else {
                                        userListPresenter.getSelectionModel().clear();
                                    }
                                }
                            }
                        });

                        userList.refresh();
                        memberOfList.refresh();
                        membersInList.refresh();
                    })
                    .taskListener(userListPresenter.getPagerView())
                    .exec();
        }
    }

    private void removeUserFromGroup(final User user,
                                     final User group,
                                     final UserListPresenter userListPresenter) {
        if (group != null && user != null) {
            final User currentSelection = userListPresenter.getSelectionModel().getSelected();
            final User nextSelection = getNextSelection(userListPresenter);

            restFactory
                    .create(USER_RESOURCE)
                    .method(res -> res.removeUserFromGroup(user.getUuid(), group.getUuid()))
                    .onSuccess(r -> {
                        userList.refresh();
                        memberOfList.refresh();
                        membersInList.refresh();

                        if (nextSelection == null) {
                            userListPresenter.getSelectionModel().clear();
                        } else {
                            userListPresenter.getSelectionModel().setSelected(nextSelection);
                        }
                    })
                    .taskListener(userListPresenter.getPagerView())
                    .exec();
        }
    }

    private User getNextSelection(final UserListPresenter userListPresenter) {
        final User currentSelection = userListPresenter.getSelectionModel().getSelected();
        User nextSelection = null;
        final ResultPage<User> resultPage = userListPresenter.getCurrentData();
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

    private void onAddGroup() {
        createNewGroupPresenter.show(user -> {
            userList.getSelectionModel().setSelected(user, new SelectionType(), true);
            userList.refresh();
        });
    }

    private void onAddUser() {
        createNewUserPresenter.show(user -> {
            userList.getSelectionModel().setSelected(user, new SelectionType(), true);
            userList.refresh();
        });
    }

    private void onAddMultiple() {
        createMultipleUsersPresenter.show(users -> {
            if (users != null && users.size() > 0) {
                userList.getSelectionModel().setSelected(users.get(0), new SelectionType(), true);
            }
            userList.refresh();
        });
    }

    private void onDelete() {
        final User user = userList.getSelectionModel().getSelected();
        if (user != null) {
            ConfirmEvent.fire(this,
                    "Are you sure you want to delete the selected " +
                            getDescription(user) + "?",
                    ok -> {
                        if (ok) {
                            restFactory
                                    .create(USER_RESOURCE)
                                    .method(res -> res.delete(user.getUuid()))
                                    .onSuccess(result -> {
                                        userList.getSelectionModel().clear(true);
                                        userList.refresh();
                                    })
                                    .taskListener(userList.getPagerView())
                                    .exec();
                        }
                    });
        }
    }

    private String getDescription(final User user) {
        if (user.isGroup()) {
            return "group '" + user.asRef().toDisplayString() + "'";
        }

        return "user '" + user.asRef().toDisplayString() + "'";
    }

    public void refresh() {
        userList.refresh();
        onSelection();
    }

//    public void show() {
//        userList.refresh();
//
//        final PopupSize popupSize = PopupSize.builder()
//                .width(Size
//                        .builder()
//                        .initial(800)
//                        .min(400)
//                        .resizable(true)
//                        .build())
//                .height(Size
//                        .builder()
//                        .initial(800)
//                        .min(400)
//                        .resizable(true)
//                        .build())
//                .build();
//        final String caption = "User Groups";
//        ShowPopupEvent.builder(UserAndGroupsPresenter.this)
//                .popupType(PopupType.CLOSE_DIALOG)
//                .popupSize(popupSize)
//                .caption(caption)
//                .onShow(e -> userList.getView().focus())
//                .fire();
//    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.USERS;
    }

    @Override
    public String getLabel() {
        return "Users And Groups";
    }

    @Override
    public String getType() {
        return "UsersAndGroups";
    }

    public interface UserAndGroupsView extends View {

        void setUserList(View view);

        void setMemberOfView(View view);

        void setMembersInView(View view);

        void setMembersInVisible(boolean visible);
    }
}
