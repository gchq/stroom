/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.dispatch.client.RestFactory;
import stroom.security.client.presenter.UserListPresenter.Mode;
import stroom.security.client.presenter.UsersPresenter.UsersView;
import stroom.security.shared.User;
import stroom.security.shared.UserResource;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

/**
 * For managing all the users (not groups)
 */
public class UsersPresenter extends ContentTabPresenter<UsersView> {

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final UserListPresenter userList;
    private final RestFactory restFactory;
    private final CreateUserPresenter createUserPresenter;
    private final Provider<CreateNewGroupPresenter> createNewGroupPresenterProvider;

    private ButtonView createButton = null;
    //    private ButtonView editButton = null;
    private ButtonView deleteButton = null;

    @Inject
    public UsersPresenter(final EventBus eventBus,
                          final UsersView view,
                          final UserListPresenter userList,
                          final RestFactory restFactory,
                          final CreateUserPresenter createUserPresenter,
                          final Provider<CreateNewGroupPresenter> createNewGroupPresenterProvider) {
        super(eventBus, view);
        this.userList = userList;
        this.restFactory = restFactory;
        this.createUserPresenter = createUserPresenter;
        this.createNewGroupPresenterProvider = createNewGroupPresenterProvider;

        view.setUserList(this.userList.getView());

        this.userList.getView().setLabel("Users:");
        this.userList.getView().setHelpText(HtmlBuilder.builder()
                .para(htmlBuilder -> htmlBuilder.append(
                        "Lists all Stroom users. Users can be added, deleted, disabled and enabled."))
                .toSafeHtml());
        this.userList.setMode(Mode.USERS_ONLY);
        this.userList.setShowUniqueUserIdCol(true);
        this.userList.setShowEnabledCol(true);
        this.userList.setValidUserScreensForActionMenu(UserScreen.allExcept(UserScreen.USERS));

        createButton = userList.addButton(SvgPresets.ADD.title("Create User"));
//        editButton = userList.addButton(SvgPresets.EDIT.title("Edit User"));
        deleteButton = userList.addButton(SvgPresets.DELETE.title("Delete User"));

        /// Initialise state.
        onSelection();
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(createButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onCreateUserOrGroup();
            }
        }));
//        registerHandler(editButton.addClickHandler(e -> {
//            if (MouseUtil.isPrimary(e)) {
//                onEditUserOrGroup();
//            }
//        }));
        registerHandler(deleteButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onDelete();
            }
        }));
        registerHandler(userList.getSelectionModel().addSelectionHandler(e -> {
            onSelection();
        }));
    }

    private void onCreateUserOrGroup() {
        createUserPresenter.show(UserAndGroupHelper.createAfterChangeConsumer(userList), false);
    }

    private void onEditUserOrGroup() {
        UserAndGroupHelper.onEditUserOrGroup(userList, createNewGroupPresenterProvider, this);
    }

    private void onDelete() {
        UserAndGroupHelper.onDelete(userList, restFactory, this);
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
//            editButton.setTitle("No Selection");
//            editButton.setEnabled(false);
            deleteButton.setTitle("No Selection");
            deleteButton.setEnabled(false);
        }
    }

    private String getDescription(final User user) {
        return "user '" + user.asRef().toDisplayString() + "'";
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
        return "Users";
    }

    public void showUser(final UserRef userRef) {
        userList.showUser(userRef);
    }


    // --------------------------------------------------------------------------------


    public interface UsersView extends View {

        void setUserList(View view);
    }
}
