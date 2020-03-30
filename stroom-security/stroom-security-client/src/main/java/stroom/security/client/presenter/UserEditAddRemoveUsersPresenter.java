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

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.security.shared.AppPermissionResource;
import stroom.security.shared.ChangeSet;
import stroom.security.shared.ChangeUserRequest;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView;

import javax.inject.Provider;
import java.util.HashSet;
import java.util.Set;

public class UserEditAddRemoveUsersPresenter extends AdvancedUserListPresenter implements UserListUiHandlers {
    private static final AppPermissionResource APP_PERMISSION_RESOURCE = GWT.create(AppPermissionResource.class);

    private final RestFactory restFactory;
    private final Provider<AdvancedUserListPresenter> selectUserPresenterProvider;
    private final ButtonView addButton;
    private final ButtonView removeButton;

    private User relatedUser;

    @Inject
    public UserEditAddRemoveUsersPresenter(final EventBus eventBus, final UserListView userListView,
                                           final RestFactory restFactory, final Provider<AdvancedUserListPresenter> selectUserPresenterProvider) {
        super(eventBus, userListView, restFactory);
        this.restFactory = restFactory;
        this.selectUserPresenterProvider = selectUserPresenterProvider;

        addButton = addButton(SvgPresets.ADD);
        addButton.setTitle("Add Group");
        removeButton = addButton(SvgPresets.REMOVE);
        removeButton.setTitle("Remove Group");
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(addButton.addClickHandler(event -> {
            final FindUserCriteria findUserCriteria = new FindUserCriteria();

            // If we are a group then get users and vice versa.
            findUserCriteria.setGroup(!relatedUser.isGroup());

            final String type = getRelatedType();
            final AdvancedUserListPresenter selectUserPresenter = selectUserPresenterProvider.get();
            selectUserPresenter.setup(findUserCriteria);

            final PopupSize popupSize = new PopupSize(400, 400, 400, 400, true);
            final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                @Override
                public void onHideRequest(boolean autoClose, boolean ok) {
                    HidePopupEvent.fire(UserEditAddRemoveUsersPresenter.this, selectUserPresenter, autoClose, ok);
                }

                @Override
                public void onHide(boolean autoClose, boolean ok) {
                    if (ok) {
                        final User selected = selectUserPresenter.getSelectionModel().getSelected();
                        if (selected != null) {
                            final Set<User> addSet = new HashSet<>();
                            addSet.add(selected);
                            final ChangeSet<User> changedLinkedUsers = new ChangeSet<>(addSet, null);
                            final ChangeUserRequest request = new ChangeUserRequest(relatedUser, changedLinkedUsers, null);

                            final Rest<Boolean> rest = restFactory.create();
                            rest
                                    .onSuccess(result -> refresh())
                                    .call(APP_PERMISSION_RESOURCE)
                                    .changeUser(request);
                        }
                    }
                }
            };
            ShowPopupEvent.fire(UserEditAddRemoveUsersPresenter.this, selectUserPresenter, PopupView.PopupType.OK_CANCEL_DIALOG, popupSize, "Choose " + type + " To Add", popupUiHandlers);
        }));
        registerHandler(removeButton.addClickHandler(event -> {
            final User selected = getSelectionModel().getSelected();
            if (selected != null) {
                final Set<User> removeSet = new HashSet<>();
                removeSet.add(selected);
                final ChangeSet<User> changedLinkedUsers = new ChangeSet<>(null, removeSet);
                final ChangeUserRequest request = new ChangeUserRequest(relatedUser, changedLinkedUsers, null);

                final Rest<Boolean> rest = restFactory.create();
                rest
                        .onSuccess(result -> refresh())
                        .call(APP_PERMISSION_RESOURCE)
                        .changeUser(request);
            }
        }));
        registerHandler(getSelectionModel().addSelectionHandler(event -> enableButtons()));
    }

    private void enableButtons() {
        removeButton.setEnabled(getSelectionModel().getSelected() != null);
    }

    public void setUser(final User relateduser) {
        this.relatedUser = relateduser;

        final FindUserCriteria findUserCriteria = new FindUserCriteria();
        findUserCriteria.setRelatedUser(relateduser);

        final String type = getRelatedType();
        addButton.setTitle("Add " + type);
        removeButton.setTitle("Remove " + type);

        setup(findUserCriteria);
    }

    private String getRelatedType() {
        String type = "User";
        if (relatedUser.isGroup()) {
            type = "User";
        } else {
            type = "Group";
        }
        return type;
    }
}
