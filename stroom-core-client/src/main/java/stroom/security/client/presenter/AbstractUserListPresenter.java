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
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.security.shared.User;
import stroom.security.shared.UserFields;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.DataGrid;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractUserListPresenter
        extends MyPresenterWidget<UserListView>
        implements UserListUiHandlers {

    private final MyDataGrid<User> dataGrid;
    private final MultiSelectionModelImpl<User> selectionModel;
    final PagerView pagerView;
    private final List<Column<User, ?>> columns = new ArrayList<>();

    public AbstractUserListPresenter(final EventBus eventBus,
                                     final UserListView userListView,
                                     final PagerView pagerView,
                                     final UiConfigCache uiConfigCache) {
        super(eventBus, userListView);
        this.pagerView = pagerView;

        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        pagerView.setDataWidget(dataGrid);

        // Not easy to determine if we are dealing in users or groups at this point so just
        // call it Quick Filter
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                userListView.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                        "Quick Filter",
                        UserFields.FILTER_FIELD_DEFINITIONS,
                        uiConfig.getHelpUrlQuickFilter()));
            }
        }, this);

        userListView.setDatGridView(pagerView);
        userListView.setUiHandlers(this);

        setupColumns();
        refresh();

        uiConfigCache.get(extendedUiConfig -> {
            if (extendedUiConfig != null) {
                refresh();
            }
        }, pagerView);
    }

    public void refresh() {
    }

    private void setupColumns() {
        // Remove any existing cols ready to add cols back in
        for (final Column<User, ?> column : columns) {
            dataGrid.removeColumn(column);
        }
        columns.clear();

        // Icon
        final Column<User, Preset> iconCol = new Column<User, Preset>(new SvgCell()) {
            @Override
            public Preset getValue(final User user) {
                if (!user.isGroup()) {
                    return SvgPresets.USER;
                }

                return SvgPresets.USER_GROUP;
            }
        };
        dataGrid.addColumn(iconCol, "</br>", ColumnSizeConstants.ICON_COL);
        columns.add(iconCol);

        // Preferred User Name
        final Column<User, String> displayNameCol = new Column<User, String>(new TextCell()) {
            @Override
            public String getValue(final User user) {
                return GwtNullSafe.requireNonNullElse(user.getDisplayName(), user.getSubjectId());
            }
        };
        dataGrid.addAutoResizableColumn(displayNameCol, "Display Name", 200);
        columns.add(displayNameCol);

        // Full name
        final Column<User, String> fullNameCol = new Column<User, String>(new TextCell()) {
            @Override
            public String getValue(final User user) {
                return user.getFullName();
            }
        };
        dataGrid.addAutoResizableColumn(fullNameCol, "Full Name", 350);
        columns.add(fullNameCol);

        // Subject ID
        final Column<User, String> uniqueIdentityCol = new Column<User, String>(new TextCell()) {
            @Override
            public String getValue(final User user) {
                return user.getSubjectId();
            }
        };
        final String nameColName = "Name";
        dataGrid.addAutoResizableColumn(uniqueIdentityCol, nameColName, 300);
        columns.add(uniqueIdentityCol);

        final EndColumn<User> endCol = new EndColumn<>();
        dataGrid.addEndColumn(new EndColumn<User>());
        columns.add(endCol);
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
