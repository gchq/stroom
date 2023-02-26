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

import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.security.shared.AppPermissionResource;
import stroom.security.shared.ChangeSet;
import stroom.security.shared.ChangeUserRequest;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Provider;

public class UserEditAddRemoveUsersPresenter
        extends AbstractDataUserListPresenter
        implements UserListUiHandlers {

    private static final AppPermissionResource APP_PERMISSION_RESOURCE = GWT.create(AppPermissionResource.class);

    private final RestFactory restFactory;
    private final Provider<SelectGroupPresenter> selectGroupPresenterProvider;
    private final Provider<SelectUserPresenter> selectUserPresenterProvider;
    private final ButtonView addButton;
    private final ButtonView removeButton;

    private User relatedUser;

    @Inject
    public UserEditAddRemoveUsersPresenter(final EventBus eventBus,
                                           final UserListView userListView,
                                           final PagerView pagerView,
                                           final RestFactory restFactory,
                                           final Provider<SelectGroupPresenter> selectGroupPresenterProvider,
                                           final Provider<SelectUserPresenter> selectUserPresenterProvider) {
        super(eventBus, userListView, pagerView, restFactory);
        this.restFactory = restFactory;
        this.selectGroupPresenterProvider = selectGroupPresenterProvider;
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

            final Consumer<User> consumer = user -> {
                final Set<User> addSet = new HashSet<>();
                addSet.add(user);
                final ChangeSet<User> changedLinkedUsers = new ChangeSet<>(addSet, null);
                final ChangeUserRequest request = new ChangeUserRequest(relatedUser,
                        changedLinkedUsers,
                        null);

                final Rest<Boolean> rest = restFactory.create();
                rest
                        .onSuccess(result -> refresh())
                        .call(APP_PERMISSION_RESOURCE)
                        .changeUser(request);
            };

            if (!relatedUser.isGroup()) {
                selectGroupPresenterProvider.get().show(consumer);
            } else {
                selectUserPresenterProvider.get().show(consumer);
            }

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

    public void setUser(final User relatedUser) {
        this.relatedUser = relatedUser;

        final FindUserCriteria findUserCriteria = new FindUserCriteria();
        findUserCriteria.setRelatedUser(relatedUser);

        final String type = getRelatedType();
        addButton.setTitle("Add " + type);
        removeButton.setTitle("Remove " + type);

        setup(findUserCriteria);
    }

    private String getRelatedType() {
        String type = "Group";
        if (relatedUser.isGroup()) {
            type = "User";
        }
        return type;
    }
}
