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

public class SelectUserPresenter extends MyPresenterWidget<UserListView> implements UserListUiHandlers {

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final MyDataGrid<String> dataGrid;
    private final MultiSelectionModelImpl<String> selectionModel;
    private final RestFactory restFactory;
    private final ButtonView newButton;
    private final ManageNewEntityPresenter newPresenter;
    private UserNameDataProvider dataProvider;
    private FindUserNameCriteria findUserCriteria;
    private String selected;

    @Inject
    public SelectUserPresenter(final EventBus eventBus,
                               final UserListView userListView,
                               final PagerView pagerView,
                               final RestFactory restFactory,
                               final ManageNewEntityPresenter newPresenter) {
        super(eventBus, userListView);
        this.restFactory = restFactory;
        this.newPresenter = newPresenter;

        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        pagerView.setDataWidget(dataGrid);

        userListView.setDatGridView(pagerView);
        userListView.setUiHandlers(this);

        // Icon
        dataGrid.addColumn(new Column<String, Preset>(new SvgCell()) {
            @Override
            public Preset getValue(final String userRef) {
                return SvgPresets.USER;
            }
        }, "</br>", 20);

        // Name.
        dataGrid.addResizableColumn(new Column<String, String>(new TextCell()) {
            @Override
            public String getValue(final String userRef) {
                return userRef;
            }
        }, "Name", 350);

        dataGrid.addEndColumn(new EndColumn<>());
        newButton = pagerView.addButton(SvgPresets.NEW_ITEM);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(newButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onNew();
            }
        }));
        registerHandler(selectionModel.addSelectionHandler(event -> {
            if (event.getSelectionType().isDoubleSelect()) {
                selected = selectionModel.getSelected();
                if (selected != null && selected.length() > 0) {
                    HidePopupEvent.builder(SelectUserPresenter.this).fire();
                }
            }
        }));
    }

    private void onNew() {
        newPresenter.show(e -> {
            if (e.isOk()) {
                selected = newPresenter.getName();
                if (selected != null && selected.length() > 0) {
                    HidePopupEvent.builder(this).fire();
                }
            }
            e.hide();
        });
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

        final PopupSize popupSize = PopupSize.resizable(400, 400);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Choose User To Add")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final String selected = getSelected();
                        if (selected != null) {
                            final Rest<User> rest = restFactory.create();
                            rest
                                    .onSuccess(userConsumer)
                                    .call(USER_RESOURCE)
                                    .create(selected, false);
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

    public String getSelected() {
        return selected;
    }

    public DataGrid<String> getDataGrid() {
        return dataGrid;
    }
}
