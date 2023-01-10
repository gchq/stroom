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
import stroom.security.shared.User;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;

import com.google.gwt.dom.client.NativeEvent;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Provider;

public class DocumentPermissionsTabPresenter
        extends MyPresenterWidget<DocumentPermissionsTabView> {

    private final DocumentUserListPresenter userListPresenter;
    private final PermissionsListPresenter permissionsListPresenter;
    private final Provider<SelectGroupPresenter> selectGroupPresenterProvider;
    private final Provider<SelectUserPresenter> selectUserPresenterProvider;
    private final ButtonView addButton;
    private final ButtonView removeButton;

    private DocumentPermissions documentPermissions;
    private boolean isGroup;

    @Inject
    public DocumentPermissionsTabPresenter(final EventBus eventBus,
                                           final DocumentPermissionsTabView view,
                                           final DocumentUserListPresenter userListPresenter,
                                           final PermissionsListPresenter permissionsListPresenter,
                                           final Provider<SelectGroupPresenter> selectGroupPresenterProvider,
                                           final Provider<SelectUserPresenter> selectUserPresenterProvider) {
        super(eventBus, view);
        this.userListPresenter = userListPresenter;
        this.permissionsListPresenter = permissionsListPresenter;
        this.selectGroupPresenterProvider = selectGroupPresenterProvider;
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
        final Consumer<User> consumer = user -> {
            //GWT.log("user: " + user.getName());
            if (!documentPermissions.containsUserOrGroup(user.getUuid(), isGroup)) {
                // This will ensure a perm map exists for the user/group
                documentPermissions.addUser(user, isGroup);
                userListPresenter.getSelectionModel().setSelected(user);
                refreshUserList();
            }
        };

        if (isGroup) {
            selectGroupPresenterProvider.get().show(consumer);
        } else {
            selectUserPresenterProvider.get().show(consumer);
        }
    }

    private void remove() {
        final User userRef = userListPresenter.getSelectionModel().getSelected();
        userListPresenter.getSelectionModel().clear();

        if (userRef != null) {
            final String userUuid = userRef.getUuid();
            final Set<String> currentPermissions = documentPermissions.getPermissionsForUser(userUuid);
            for (final String permission : currentPermissions) {
                permissionsListPresenter.removePermission(userUuid, permission);
            }
            documentPermissions.removeUser(userRef);
            refreshUserList();
        }
    }

    public void setDocumentPermissions(final List<String> allPermissions,
                                       final DocumentPermissions documentPermissions,
                                       final boolean group,
                                       final Changes changes) {
//        GWT.log("setDocumentPermissions(isGroup: " + group
//                + " documentPermissions (groups): "
//                + (documentPermissions != null
//                ? documentPermissions.getGroups().stream().map(User::getName).collect(Collectors.joining(", "))
//                : "null")
//                + " documentPermissions (users): "
//                + (documentPermissions != null
//                ? documentPermissions.getUsers().stream().map(User::getName).collect(Collectors.joining(", "))
//                : "null"));

        this.documentPermissions = documentPermissions;
        this.isGroup = group;

        if (group) {
            getView().setUsersLabelText("Groups:");
            userListPresenter.setDocumentPermissions(documentPermissions.getGroups(), true);
        } else {
            getView().setUsersLabelText("Users:");
            userListPresenter.setDocumentPermissions(documentPermissions.getUsers(), false);
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
