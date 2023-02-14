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

import stroom.cell.info.client.SvgCell;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.security.shared.FindUserNameCriteria;
import stroom.security.shared.User;
import stroom.util.shared.UserName;
import stroom.security.shared.UserResource;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

/**
 * For selecting a username to add a user to a permissions set or selecting a user to add to a group.
 * For internal IDP this will list users from account and stroom_user combined.
 * For external IDP this will list users from stroom_user only and users on the IDP but not in
 * stroom_user will have to be manually added to the list.
 */
public class SelectUserPresenter
        extends MyPresenterWidget<UserListView>
        implements UserListUiHandlers {

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final DataGridView<UserName> dataGridView;
    private final RestFactory restFactory;
    private ButtonView newButton = null;
    private final CreateNewUserPresenter newPresenter;
    private final UiConfigCache uiConfigCache;
    private UserNameDataProvider dataProvider;
    private FindUserNameCriteria findUserCriteria;
    private Consumer<User> userConsumer;

    @Inject
    public SelectUserPresenter(final EventBus eventBus,
                               final UserListView userListView,
                               final RestFactory restFactory,
                               final CreateNewUserPresenter newPresenter,
                               final UiConfigCache uiConfigCache) {
        super(eventBus, userListView);
        this.restFactory = restFactory;
        this.newPresenter = newPresenter;
        this.uiConfigCache = uiConfigCache;

        dataGridView = new DataGridViewImpl<>(true);
        userListView.setDatGridView(dataGridView);
        userListView.setUiHandlers(this);

        // Icon
        dataGridView.addColumn(new Column<UserName, Preset>(new SvgCell()) {
            @Override
            public Preset getValue(final UserName userRef) {
                return SvgPresets.USER;
            }
        }, "</br>", 20);

        // Name.
        dataGridView.addResizableColumn(new Column<UserName, String>(new TextCell()) {
            @Override
            public String getValue(final UserName userRef) {
                return userRef.getName();
            }
        }, "User ID", 350);

        // Preferred User Name
        final Column<UserName, String> preferredUsernameCol = new Column<UserName, String>(new TextCell()) {
            @Override
            public String getValue(final UserName userRef) {
                return userRef.getPreferredUsername();
            }
        };
        dataGridView.addResizableColumn(preferredUsernameCol, "Display Name", 250);

        // Full name
        final Column<UserName, String> fullNameCol = new Column<UserName, String>(new TextCell()) {
            @Override
            public String getValue(final UserName userRef) {
                return userRef.getFullName();
            }
        };
        dataGridView.addResizableColumn(fullNameCol, "Full Name", 350);

        dataGridView.addEndColumn(new EndColumn<>());
        // Only want to let user create a stroom user if they are using an external IDP.
        // If internal then all users are available to be picked from the account tbl.
        uiConfigCache.get()
                .onSuccess(config -> {
                    if (config.isExternalIdentityProvider()) {
                        newButton = dataGridView.addButton(SvgPresets.NEW_ITEM);
                    }
                });
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(newButton.addClickHandler(event -> {
            if (event.getNativeButton() == NativeEvent.BUTTON_LEFT) {
                onNew();
            }
        }));
        registerHandler(dataGridView.getSelectionModel().addSelectionHandler(event -> {
            if (event.getSelectionType().isDoubleSelect()) {
                final UserName selected = dataGridView.getSelectionModel().getSelected();
                hide(false, true, selected);
            }
        }));
    }

    private void onNew() {
        final PopupUiHandlers hidePopupUiHandlers = new DefaultPopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    final UserName selected = newPresenter.getUserName();
                    hide(false, true, selected);
                }
                newPresenter.hide();
            }
        };

        newPresenter.show(hidePopupUiHandlers);
    }

    @Override
    public void changeNameFilter(String name) {
        if (findUserCriteria != null) {
            String filter = name;

            if (filter != null) {
                filter = filter.trim();
                if (filter.length() == 0) {
                    filter = null;
                }
            }

            if ((filter == null && findUserCriteria.getQuickFilterInput() == null) ||
                    (filter != null && filter.equals(findUserCriteria.getQuickFilterInput()))) {
                return;
            }

            findUserCriteria.setQuickFilterInput(filter);
            dataProvider.refresh();
        }
    }

    public void show(final Consumer<User> userConsumer) {
        this.userConsumer = userConsumer;
        final FindUserNameCriteria findUserCriteria = new FindUserNameCriteria();
        setup(findUserCriteria);

        final PopupSize popupSize = PopupSize.resizable(1000, 600);
        final PopupUiHandlers popupUiHandlers = new DefaultPopupUiHandlers() {
            @Override
            public void onHideRequest(boolean autoClose, boolean ok) {
                final UserName selected = dataGridView.getSelectionModel().getSelected();
                hide(autoClose, ok, selected);
            }
        };
        ShowPopupEvent.fire(
                SelectUserPresenter.this,
                SelectUserPresenter.this,
                PopupView.PopupType.OK_CANCEL_DIALOG,
                popupSize,
                "Choose User To Add",
                popupUiHandlers);
    }

    private void hide(final boolean autoClose, final boolean ok, final UserName selected) {
        if (ok && userConsumer != null && selected != null) {
            final Rest<User> rest = restFactory.create();
            rest
                    .onSuccess(user -> {
                        userConsumer.accept(user);
                        HidePopupEvent.fire(
                                this,
                                this,
                                autoClose,
                                ok);
                    })
                    .call(USER_RESOURCE)
                    .createUser(selected);
        } else {
            HidePopupEvent.fire(
                    this,
                    this,
                    autoClose,
                    ok);
        }
    }

    public void setup(final FindUserNameCriteria findUserCriteria) {
        this.findUserCriteria = findUserCriteria;
        dataProvider = new UserNameDataProvider(getEventBus(), restFactory, getDataGridView());
        dataProvider.setCriteria(findUserCriteria);
        refresh();
    }

    public void refresh() {
        dataProvider.refresh();
    }

    public DataGridView<UserName> getDataGridView() {
        return dataGridView;
    }
}
