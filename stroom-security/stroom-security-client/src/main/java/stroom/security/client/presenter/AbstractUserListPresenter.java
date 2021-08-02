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
import stroom.security.shared.User;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.DataGrid;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

public abstract class AbstractUserListPresenter extends MyPresenterWidget<UserListView> implements UserListUiHandlers {

    private final MyDataGrid<User> dataGrid;
    private final MultiSelectionModelImpl<User> selectionModel;
    private final PagerView pagerView;

    public AbstractUserListPresenter(final EventBus eventBus,
                                     final UserListView userListView,
                                     final PagerView pagerView) {
        super(eventBus, userListView);
        this.pagerView = pagerView;

        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        pagerView.setDataWidget(dataGrid);

        userListView.setDatGridView(pagerView);
        userListView.setUiHandlers(this);

        // Icon
        dataGrid.addColumn(new Column<User, Preset>(new SvgCell()) {
            @Override
            public Preset getValue(final User userRef) {
                if (userRef.isEnabled()) {
                    if (!userRef.isGroup()) {
                        return SvgPresets.USER;
                    }

                    return SvgPresets.USER_GROUP;
                }

                if (!userRef.isGroup()) {
                    return SvgPresets.USER_DISABLED;
                }

                return SvgPresets.USER_GROUP_DISABLED;
            }
        }, "</br>", 20);

        // Name.
        dataGrid.addResizableColumn(new Column<User, String>(new TextCell()) {
            @Override
            public String getValue(final User userRef) {
                return userRef.getName();
            }
        }, "Name", 350);

        dataGrid.addEndColumn(new EndColumn<>());
    }

    public ButtonView addButton(final Preset preset) {
        return pagerView.addButton(preset);
    }

    @Override
    public void changeNameFilter(String name) {

    }

    public MultiSelectionModel<User> getSelectionModel() {
        return selectionModel;
    }

    public DataGrid<User> getDataGrid() {
        return dataGrid;
    }
}
