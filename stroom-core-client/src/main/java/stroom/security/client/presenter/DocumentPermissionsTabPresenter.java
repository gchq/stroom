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

import stroom.docref.DocRef;
import stroom.security.client.presenter.DocumentPermissionsTabPresenter.DocumentPermissionsTabView;
import stroom.security.shared.Changes;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.User;
import stroom.svg.client.SvgPresets;
import stroom.task.client.TaskListener;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.user.client.ui.Focus;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Provider;

public class DocumentPermissionsTabPresenter
        extends MyPresenterWidget<DocumentPermissionsTabView>
        implements Focus {

    private final DocumentUserListPresenter userListPresenter;
    private final Provider<SelectUserPresenter> selectUserPresenterProvider;
    private final ButtonView addButton;
    private final ButtonView removeButton;

    @Inject
    public DocumentPermissionsTabPresenter(final EventBus eventBus,
                                           final DocumentPermissionsTabView view,
                                           final DocumentUserListPresenter userListPresenter,
                                           final Provider<SelectUserPresenter> selectUserPresenterProvider) {
        super(eventBus, view);
        this.userListPresenter = userListPresenter;
        this.selectUserPresenterProvider = selectUserPresenterProvider;

        addButton = userListPresenter.addButton(SvgPresets.ADD);
        removeButton = userListPresenter.addButton(SvgPresets.REMOVE);

        getView().setUserView(userListPresenter.getView());
    }

    @Override
    public void focus() {
        userListPresenter.getView().focus();
    }

    @Override
    protected void onBind() {
        registerHandler(userListPresenter.getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            final User user = userListPresenter.getSelectionModel().getSelected();
            if (user != null) {
                setCurrentUser(user.asRef());
            } else {
                setCurrentUser(null);
            }
        }));
        registerHandler(addButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                add();
            }
        }));
        registerHandler(removeButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                remove();
            }
        }));

        super.onBind();
    }

    private void enableButtons() {
        removeButton.setEnabled(userListPresenter.getSelectionModel().getSelected() != null);
    }

    protected void setCurrentUser(final UserRef user) {
//        permissionsListPresenter.setCurrentUser(user);
    }

    private void add() {
        final Consumer<User> consumer = user -> {
//            //GWT.log("user: " + user.getName());
//            if (!documentPermissions.containsUser(user.getUuid())) {
//                // This will ensure a perm map exists for the user/group
//                documentPermissions.addUser(user);
            userListPresenter.getSelectionModel().setSelected(user);
            refreshUserList();
//            }
        };
        selectUserPresenterProvider.get().show(consumer);
    }

    private void remove() {
        final User userRef = userListPresenter.getSelectionModel().getSelected();
        userListPresenter.getSelectionModel().clear();

        if (userRef != null) {
//            final String userUuid = userRef.getUuid();
//            final Set<String> currentPermissions = documentPermissions.getPermissionsForUser(userUuid);
//            for (final String permission : currentPermissions) {
//                permissionsListPresenter.removePermission(userUuid, permission);
//            }
//            documentPermissions.removeUser(userRef);
//            refreshUserList();
        }
    }

    public void setDocumentPermissions(final DocRef docRef,
                                       final List<DocumentPermission> allPermissions,
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

        userListPresenter.setDocRef(docRef);

        getView().setUsersLabelText("Groups and Users");

//        permissionsListPresenter.setDocumentPermissions(new DocumentPermissionSet(), allPermissions, changes);
        refreshUserList();
    }

    private void refreshUserList() {
        userListPresenter.refresh();
    }

    @Override
    public void setTaskListener(final TaskListener taskListener) {
//        permissionsListPresenter.setTaskListener(taskListener);
    }


    // --------------------------------------------------------------------------------


    public interface DocumentPermissionsTabView extends View {

        void setUserView(View view);

        void setPermission(DocumentPermission permission);

        DocumentPermission getPermission();

        void setUsersLabelText(String text);
    }
}
