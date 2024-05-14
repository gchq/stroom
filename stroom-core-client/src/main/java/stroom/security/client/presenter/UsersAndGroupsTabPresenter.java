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

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.User;
import stroom.security.shared.UserResource;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import javax.inject.Inject;

/**
 * The presenter for the tab content of the {@link UsersAndGroupsPresenter}. Will show either
 * users or groups depending on what was passed to {@link UsersAndGroupsTabPresenter#setGroup(boolean)}
 */
public class UsersAndGroupsTabPresenter
        extends MyPresenterWidget<UserListView>
        implements HasHandlers {

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    public static final String LIST = "LIST";
    private final SelectGroupPresenter listPresenter;
    private final Provider<UserEditPresenter> userEditPresenterProvider;
    private final Provider<GroupEditPresenter> groupEditPresenterProvider;
    private final Provider<SelectUserPresenter> selectUserPresenterProvider;
    private final ManageNewEntityPresenter newGroupPresenter;
    private final CreateNewUserPresenter newUserPresenter;
    private final CreateMultipleUsersPresenter createMultipleUsersPresenter;
    private final RestFactory restFactory;
    private final ButtonView newButton;
    private final ButtonView addMultipleButton;
    private final ButtonView openButton;
    private final ButtonView deleteButton;
    private final FindUserCriteria criteria = new FindUserCriteria();
    private final UiConfigCache uiConfigCache;
    private final ClientSecurityContext securityContext;

    @Inject
    public UsersAndGroupsTabPresenter(final EventBus eventBus,
                                      final SelectGroupPresenter listPresenter,
                                      final Provider<UserEditPresenter> userEditPresenterProvider,
                                      final Provider<GroupEditPresenter> groupEditPresenterProvider,
                                      final Provider<SelectUserPresenter> selectUserPresenterProvider,
                                      final ManageNewEntityPresenter newGroupPresenter,
                                      final CreateNewUserPresenter newUserPresenter,
                                      final CreateMultipleUsersPresenter createMultipleUsersPresenter,
                                      final RestFactory restFactory,
                                      final ClientSecurityContext securityContext,
                                      final UiConfigCache uiConfigCache) {
        super(eventBus, listPresenter.getView());
        this.listPresenter = listPresenter;
        this.userEditPresenterProvider = userEditPresenterProvider;
        this.groupEditPresenterProvider = groupEditPresenterProvider;
        this.selectUserPresenterProvider = selectUserPresenterProvider;
        this.restFactory = restFactory;
        this.newGroupPresenter = newGroupPresenter;
        this.newUserPresenter = newUserPresenter;
        this.createMultipleUsersPresenter = createMultipleUsersPresenter;
        this.uiConfigCache = uiConfigCache;
        this.securityContext = securityContext;

        setInSlot(LIST, listPresenter);

        newButton = listPresenter.addButton(SvgPresets.NEW_ITEM);
        addMultipleButton = listPresenter.addButton(SvgPresets.ADD_MULTIPLE.title("Add Multiple Users"));
        openButton = listPresenter.addButton(SvgPresets.EDIT);
        deleteButton = listPresenter.addButton(SvgPresets.DELETE);

        refreshButtons(false);
    }

    @Override
    protected void onBind() {
        registerHandler(listPresenter.getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                onOpen();
            }
        }));
        registerHandler(newButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onNew();
            }
        }));
        registerHandler(addMultipleButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onAddMultiple();
            }
        }));
        registerHandler(openButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onOpen();
            }
        }));
        registerHandler(deleteButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onDelete();
            }
        }));

        super.onBind();
    }

    private void refreshButtons(final boolean isGroup) {

        uiConfigCache.get(extendedUiConfig -> {
            if (extendedUiConfig != null) {
                final boolean updatePerm = securityContext.hasAppPermission(
                        PermissionNames.MANAGE_USERS_PERMISSION);

                if (updatePerm) {
                    addMultipleButton.setVisible(!isGroup
                            && extendedUiConfig.isExternalIdentityProvider());
                    newButton.setTitle(isGroup
                            ? "New Group"
                            : "Add User");
                    openButton.setTitle(isGroup
                            ? "Edit Group"
                            : "Edit User");
                    deleteButton.setTitle(isGroup
                            ? "Delete Group"
                            : "Delete User");
                } else {
                    newButton.setVisible(false);
                    addMultipleButton.setVisible(false);
                    deleteButton.setVisible(false);
                    openButton.setTitle(isGroup
                            ? "Open Group"
                            : "Open User");
                }

            }
        }, this);
    }

    /**
     * Sets whether this presenter is showing users or groups
     */
    public void setGroup(final boolean group) {
        criteria.setGroup(group);
        listPresenter.setup(criteria);
        refreshButtons(group);
    }

    private void enableButtons() {
        final boolean enabled = listPresenter.getSelectionModel().getSelected() != null;
        openButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
    }

    private void onOpen() {
        final User e = listPresenter.getSelectionModel().getSelected();
        onEdit(e);
    }

    public void onEdit(final User userRef) {
        if (userRef != null) {
            edit(userRef);
        }
    }

    private void onDelete() {
        final User userRef = listPresenter.getSelectionModel().getSelected();
        if (userRef != null) {
            ConfirmEvent.fire(UsersAndGroupsTabPresenter.this,
                    "Are you sure you want to delete the selected " + getTypeName() + "?",
                    ok -> {
                        if (ok) {
                            restFactory
                                    .create(USER_RESOURCE)
                                    .method(res -> res.delete(userRef.getUuid()))
                                    .onSuccess(result -> {
                                        listPresenter.refresh();
                                        listPresenter.getSelectionModel().clear();
                                    })
                                    .taskListener(this)
                                    .exec();
                        }
                    });
        }
    }

    private void onNew() {
        if (criteria.isGroup()) {
            showNewGroupDialog();
        } else {
            uiConfigCache.get(extendedUiConfig -> {
                if (extendedUiConfig != null) {
                    if (extendedUiConfig.isExternalIdentityProvider()) {
                        showNewUserDialog();
                    } else {
                        selectUserPresenterProvider.get().show(this::edit);
                    }
                }
            }, this);
        }
    }

    private void onAddMultiple() {
        if (criteria.isGroup()) {
            showNewGroupDialog();
        } else {
            uiConfigCache.get(extendedUiConfig -> {
                if (extendedUiConfig != null) {
                    if (extendedUiConfig.isExternalIdentityProvider()) {
                        showAddMultipleUsersDialog();
                    } else {
                        selectUserPresenterProvider.get().show(this::edit);
                    }
                }
            }, this);
        }
    }

    private void showNewGroupDialog() {
        newGroupPresenter.show(e -> {
            if (e.isOk()) {
                restFactory
                        .create(USER_RESOURCE)
                        .method(res -> res.createGroup(newGroupPresenter.getName()))
                        .onSuccess(result -> {
                            e.hide();
                            edit(result);
                        })
                        .onFailure(new DefaultErrorHandler(this, e::reset))
                        .taskListener(this)
                        .exec();
            } else {
                e.hide();
            }
        });
    }

    private void showNewUserDialog() {
        newUserPresenter.show(e -> {
            if (e.isOk()) {
                restFactory
                        .create(USER_RESOURCE)
                        .method(res -> res.createUser(newUserPresenter.getUserName()))
                        .onSuccess(result -> {
                            e.hide();
//                            newUserPresenter.hide();
                            edit(result);
                        })
                        .onFailure(new DefaultErrorHandler(this, e::reset))
                        .taskListener(this)
                        .exec();
            } else {
                e.hide();
            }
        });
    }

    private void showAddMultipleUsersDialog() {
        createMultipleUsersPresenter.show(e -> {
            if (e.isOk()) {
                final String usersCsvData = createMultipleUsersPresenter.getUsersCsvData();
                if (usersCsvData != null && !usersCsvData.isEmpty()) {
                    restFactory
                            .create(USER_RESOURCE)
                            .method(res -> res.createUsersFromCsv(createMultipleUsersPresenter.getUsersCsvData()))
                            .onSuccess(result -> {
                                e.hide();
//                                createMultipleUsersPresenter.hide();
                                listPresenter.refresh();
//                                    edit(result);
                            })
                            .onFailure(caught -> AlertEvent.fireError(
                                    UsersAndGroupsTabPresenter.this,
                                    caught.getMessage(),
                                    e::reset))
                            .taskListener(this)
                            .exec();
                } else {
                    e.hide();
//                    createMultipleUsersPresenter.hide();
                }
            } else {
                e.hide();
            }
        });
    }

    private void edit(final User userRef) {
        if (userRef.isGroup()) {
            if (groupEditPresenterProvider != null) {
                final GroupEditPresenter groupEditPresenter = groupEditPresenterProvider.get();
                groupEditPresenter.show(userRef, listPresenter::refresh);
            }
        } else {
            if (userEditPresenterProvider != null) {
                final UserEditPresenter userEditPresenter = userEditPresenterProvider.get();
                userEditPresenter.show(userRef, listPresenter::refresh);
            }
        }
    }

    private String getTypeName() {
        if (criteria.isGroup()) {
            return "group";
        }

        return "user";
    }
}
