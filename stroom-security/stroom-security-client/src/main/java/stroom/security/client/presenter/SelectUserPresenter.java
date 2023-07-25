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
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.security.shared.FindUserNameCriteria;
import stroom.security.shared.User;
import stroom.security.shared.UserResource;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.UserName;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.DataGrid;
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

    private final MyDataGrid<UserName> dataGrid;
    private final MultiSelectionModelImpl<UserName> selectionModel;
    private final RestFactory restFactory;
    private ButtonView newButton = null;
    private ButtonView addMultipleButton = null;
    private final CreateNewUserPresenter newPresenter;
    private final CreateMultipleUsersPresenter createMultipleUsersPresenter;
    private final UiConfigCache uiConfigCache;
    private UserNameDataProvider dataProvider;
    private FindUserNameCriteria findUserCriteria;
    private UserName selected;

    @Inject
    public SelectUserPresenter(final EventBus eventBus,
                               final UserListView userListView,
                               final PagerView pagerView,
                               final RestFactory restFactory,
                               final CreateNewUserPresenter newPresenter,
                               final CreateMultipleUsersPresenter createMultipleUsersPresenter,
                               final UiConfigCache uiConfigCache) {
        super(eventBus, userListView);
        this.restFactory = restFactory;
        this.newPresenter = newPresenter;
        this.createMultipleUsersPresenter = createMultipleUsersPresenter;
        this.uiConfigCache = uiConfigCache;

        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        pagerView.setDataWidget(dataGrid);

        userListView.setDatGridView(pagerView);
        userListView.setUiHandlers(this);

        // Icon
        dataGrid.addColumn(new Column<UserName, Preset>(new SvgCell()) {
            @Override
            public Preset getValue(final UserName userRef) {
                return SvgPresets.USER;
            }
        }, "</br>", 20);

        // Display Name
        final Column<UserName, String> displayNameCol = new Column<UserName, String>(new TextCell()) {
            @Override
            public String getValue(final UserName userRef) {
                return GwtNullSafe.requireNonNullElse(userRef.getDisplayName(), userRef.getSubjectId());
            }
        };
        dataGrid.addResizableColumn(displayNameCol, "Display Name", 200);

        // Full name
        final Column<UserName, String> fullNameCol = new Column<UserName, String>(new TextCell()) {
            @Override
            public String getValue(final UserName userRef) {
                return userRef.getFullName();
            }
        };
        dataGrid.addResizableColumn(fullNameCol, "Full Name", 350);

        // Subject ID (aka the unique ID for the user)
        dataGrid.addResizableColumn(new Column<UserName, String>(new TextCell()) {
            @Override
            public String getValue(final UserName userRef) {
                return userRef.getSubjectId();
            }
        }, "Unique User Identity", 300);


        dataGrid.addEndColumn(new EndColumn<>());
        // Only want to let user create a stroom user if they are using an external IDP.
        // If internal then all users are available to be picked from the account tbl.
        uiConfigCache.get()
                .onSuccess(config -> {
                    if (config.isExternalIdentityProvider()) {
                        newButton = pagerView.addButton(
                                SvgPresets.ADD.title("Add Identity Provider User"));
                        addMultipleButton = pagerView.addButton(
                                SvgPresets.ADD_MULTIPLE.title("Add Multiple Identity Provider Users"));
                    }
                });
    }

    @Override
    protected void onBind() {
        super.onBind();
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
        registerHandler(selectionModel.addSelectionHandler(event -> {
            selected = selectionModel.getSelected();
            if (event.getSelectionType().isDoubleSelect()) {
                if (selected != null) {
                    HidePopupEvent.builder(SelectUserPresenter.this).fire();
                }
            }
        }));
    }

    private void onNew() {
        newPresenter.show(e -> {
            if (e.isOk()) {
                selected = newPresenter.getUserName();
                if (selected != null) {
                    HidePopupEvent.builder(this).fire();
                }
            }
            e.hide();
        });
    }

    private void onAddMultiple() {
        createMultipleUsersPresenter.show(e -> {
            if (e.isOk()) {
                HidePopupEvent.builder(this).fire();
            }
            e.hide();
        });
//        final PopupUiHandlers hidePopupUiHandlers = new DefaultPopupUiHandlers() {
//            @Override
//            public void onHideRequest(final boolean autoClose, final boolean ok) {
//                if (ok) {
//                    // Added multiple so don't select anything
//                    hide(false, true, null);
//                }
//                createMultipleUsersPresenter.hide();
//            }
//        };
//
//        createMultipleUsersPresenter.show(hidePopupUiHandlers);
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
        final FindUserNameCriteria findUserCriteria = new FindUserNameCriteria();
        setup(findUserCriteria);

        final PopupSize popupSize = PopupSize.resizable(1_000, 400);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Choose User To Add")
                .onShow(e -> getView().focus())
                .onHide(e -> {
                    if (e.isOk()) {
                        final UserName selected = getSelected();
                        if (selected != null) {
                            final Rest<User> rest = restFactory.create();
                            rest
                                    .onSuccess(userConsumer)
                                    .call(USER_RESOURCE)
                                    .createUser(selected);
                        }
                    }
                })
                .fire();
    }

    public void setup(final FindUserNameCriteria findUserCriteria) {
        this.findUserCriteria = findUserCriteria;
        dataProvider = new UserNameDataProvider(getEventBus(), restFactory, getDataGrid());
        dataProvider.setCriteria(findUserCriteria);
        refresh();
    }

    public void refresh() {
        dataProvider.refresh();
    }

    public UserName getSelected() {
        return selected;
    }

    public DataGrid<UserName> getDataGrid() {
        return dataGrid;
    }
}
