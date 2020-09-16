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

import stroom.security.client.presenter.DocumentPermissionsTabPresenter.DocumentPermissionsTabView;
import stroom.security.shared.Changes;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView;

import com.google.gwt.dom.client.NativeEvent;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DocumentPermissionsTabPresenter
        extends MyPresenterWidget<DocumentPermissionsTabView> {
    private final DocumentUserListPresenter userListPresenter;
    private final PermissionsListPresenter permissionsListPresenter;
    private final Provider<AdvancedUserListPresenter> selectUserPresenterProvider;
    private final ButtonView addButton;
    private final ButtonView removeButton;

    private DocumentPermissions documentPermissions;
    private boolean isGroup;

    @Inject
    public DocumentPermissionsTabPresenter(final EventBus eventBus,
                                           final DocumentPermissionsTabView view,
                                           final DocumentUserListPresenter userListPresenter,
                                           final PermissionsListPresenter permissionsListPresenter,
                                           final Provider<AdvancedUserListPresenter> selectUserPresenterProvider) {
        super(eventBus, view);
        this.userListPresenter = userListPresenter;
        this.permissionsListPresenter = permissionsListPresenter;
        this.selectUserPresenterProvider = selectUserPresenterProvider;

        addButton = userListPresenter.addButton(SvgPresets.ADD);
        removeButton = userListPresenter.addButton(SvgPresets.REMOVE);

        getView().setUserView(userListPresenter.getView());
        getView().setPermissionsView(permissionsListPresenter.getView());
    }

    @Override
    protected void onBind() {
        registerHandler(userListPresenter.getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            setCurrentUser(userListPresenter.getSelectionModel().getSelected());
        }));
        registerHandler(addButton.addClickHandler(event -> {
            if (event.getNativeButton() == NativeEvent.BUTTON_LEFT) {
                add();
            }
        }));
        registerHandler(removeButton.addClickHandler(event -> {
            if (event.getNativeButton() == NativeEvent.BUTTON_LEFT) {
                remove();
            }
        }));

        super.onBind();
    }

    private void enableButtons() {
        removeButton.setEnabled(userListPresenter.getSelectionModel().getSelected() != null);
    }

    protected void setCurrentUser(final User user) {
        permissionsListPresenter.setCurrentUser(user);
    }

    private void add() {
        final FindUserCriteria findUserCriteria = new FindUserCriteria();
        findUserCriteria.setGroup(isGroup);

//                // If we are a group then get users and vice versa.
//                findUserCriteria.setGroup(!relatedUser.isGroup());

//        final String type = "User";
        final AdvancedUserListPresenter selectUserPresenter = selectUserPresenterProvider.get();
        selectUserPresenter.setup(findUserCriteria);

        final PopupSize popupSize = new PopupSize(400, 400, 400, 400, true);
        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(boolean autoClose, boolean ok) {
                HidePopupEvent.fire(
                        DocumentPermissionsTabPresenter.this,
                        selectUserPresenter,
                        autoClose,
                        ok);
            }

            @Override
            public void onHide(boolean autoClose, boolean ok) {
                if (ok) {
                    final User selected = selectUserPresenter.getSelectionModel().getSelected();
                    if (selected != null) {
                        final String uuid = selected.getUuid();
                        if (!documentPermissions.containsUserOrGroup(uuid, isGroup)) {
                            // This will ensure a perm map exists for the user/group
                            documentPermissions.addUser(selected, isGroup);
                            userListPresenter.getSelectionModel().setSelected(selected);
                            refreshUserList();
                        }
                    }
                }
            }
        };

        String type = "User";
        if (isGroup) {
            type = "Group";
        }
        ShowPopupEvent.fire(
                DocumentPermissionsTabPresenter.this,
                selectUserPresenter,
                PopupView.PopupType.OK_CANCEL_DIALOG,
                popupSize,
                "Choose " + type + " To Add",
                popupUiHandlers);
    }

    private void remove() {
        final User userRef = userListPresenter.getSelectionModel().getSelected();
        userListPresenter.getSelectionModel().clear();

        if (userRef != null) {
            final String userUuid = userRef.getUuid();
            final Set<String> currentPermissions = documentPermissions.getPermissions().get(userUuid);
            if (currentPermissions != null) {
                final Set<String> permissionsToRemove = new HashSet<>(currentPermissions);
                for (final String permission : permissionsToRemove) {
                    permissionsListPresenter.removePermission(userUuid, permission);
                }
                documentPermissions.getPermissions().remove(userUuid);
            }

            refreshUserList();
        }
    }

    public void setDocumentPermissions(final List<String> allPermissions,
                                       final DocumentPermissions documentPermissions,
                                       final boolean group,
                                       final Changes changes) {
        this.documentPermissions = documentPermissions;
        this.isGroup = group;

        if (group) {
            getView().setUsersLabelText("Groups:");
            userListPresenter.setDocumentPermissions(documentPermissions.getGroups());
        } else {
            getView().setUsersLabelText("Users:");
            userListPresenter.setDocumentPermissions(documentPermissions.getUsers());
        }

        final List<String> permissions = new ArrayList<>();
        for (final String permission : allPermissions) {
            if (!permission.startsWith("Create")) {
                permissions.add(permission);
            }
        }

        permissionsListPresenter.setDocumentPermissions(documentPermissions, permissions, changes);
        refreshUserList();
    }

    public DocumentPermissions getDocumentPermissions() {
        return this.documentPermissions;
    }

    private void refreshUserList() {
        userListPresenter.refresh();
    }

    public interface DocumentPermissionsTabView extends View {
        void setUserView(View view);

        void setPermissionsView(View view);

        void setUsersLabelText(String text);
    }
}
