/*
 * Copyright 2020 Crown Copyright
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

package stroom.dashboard.client.component;

import stroom.cell.tickbox.shared.TickBoxState;
import stroom.dashboard.shared.ComponentSelectionHandler;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// A component's selection handlers, a row each (whether it is enabled and its expression); one can
/// be selected.
public class SelectionHandlerList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<ComponentSelectionHandler> dataGrid;

    private final MultiSelectionModelImpl<ComponentSelectionHandler> selectionModel;

    /// Makes an empty list.
    public SelectionHandlerList() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows keyed by id, so a row whose values change is still the same row
        dataGrid.setKeyProvider(ComponentSelectionHandler::getId);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        addColumns();
    }

    private void addColumns() {
        // Enabled.
        final Column<ComponentSelectionHandler, TickBoxState> enabledColumn =
                DataGridUtil.readOnlyTickBoxColumnBuilder(TickBoxState.createTickBoxFunc(
                                ComponentSelectionHandler::isEnabled))
                        .centerAligned()
                        .build();
        dataGrid.addColumn(
                enabledColumn,
                DataGridUtil.headingBuilder("Enabled")
                        .build(),
                ColumnSizeConstants.ENABLED_COL);

        // Expression.
        final Column<ComponentSelectionHandler, String> expressionColumn =
                new Column<ComponentSelectionHandler, String>(new TextCell()) {
                    @Override
                    public String getValue(final ComponentSelectionHandler row) {
                        return row.getExpression().toString();
                    }
                };
        dataGrid.addResizableColumn(expressionColumn, "Expression", 500);
    }

    /// @param data The selection handlers.
    public void setData(final List<ComponentSelectionHandler> data) {
        dataGrid.setRowData(0, data);
        dataGrid.setRowCount(data.size());
    }

    /// @return The selection, one handler at most.
    public MultiSelectionModel<ComponentSelectionHandler> getSelectionModel() {
        return selectionModel;
    }

    /// @return The toolbar beside the pager, for the screen's own buttons.
    public Toolbar getToolbar() {
        return pager.getToolbar();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, SelectionHandlerList> {

    }
}
