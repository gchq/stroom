/*
 * Copyright 2026 Crown Copyright
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

package stroom.analytics.client.component;

import stroom.analytics.shared.DuplicateCheckRow;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.ArrayList;
import java.util.List;

/// A rule's duplicate check rows, a column for each of the values the rule checks; several can be
/// selected and deleted.
public class DuplicateManagementList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    ToolbarButton deleteButton;
    @UiField
    DataGrid<DuplicateCheckRow> dataGrid;

    private final MultiSelectionModelImpl<DuplicateCheckRow> selectionModel;
    private final List<Column<DuplicateCheckRow, ?>> columns = new ArrayList<>();
    private Runnable onDelete = () -> {
    };
    private Runnable onSelectionChange = () -> {
    };

    /// Makes an empty list.
    public DuplicateManagementList() {
        initWidget(BINDER.createAndBindUi(this));
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        deleteButton.setOnClick(() -> onDelete.run());
        selectionModel.addSelectionHandler(event -> onSelectionChange.run());
    }

    /// Makes a column for each of the values, if their number has changed.
    ///
    /// @param columnNames The values' names.
    public void setColumns(final List<String> columnNames) {
        if (columnNames.size() != columns.size()) {
            columns.forEach(dataGrid::removeColumn);
            columns.clear();

            for (int i = 0; i < columnNames.size(); i++) {
                final int pos = i;
                final String columnName = columnNames.get(pos);
                //noinspection Convert2Diamond // cos GWT
                final Column<DuplicateCheckRow, String> column = new Column<DuplicateCheckRow, String>(new TextCell()) {
                    @Override
                    public String getValue(final DuplicateCheckRow duplicateCheckRow) {
                        if (duplicateCheckRow != null &&
                            duplicateCheckRow.getValues() != null &&
                            duplicateCheckRow.getValues().size() > pos) {
                            return duplicateCheckRow.getValues().get(pos);
                        }
                        return null;
                    }
                };
                dataGrid.addResizableColumn(column, columnName, 200);
                columns.add(column);
            }
        }
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<DuplicateCheckRow> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The rows selected.
    public List<DuplicateCheckRow> getSelectedItems() {
        return selectionModel.getSelectedItems();
    }

    /// @param enabled Whether Delete can be pressed.
    public void setDeleteEnabled(final boolean enabled) {
        deleteButton.setEnabled(enabled);
    }

    /// @param onDelete Run when Delete is pressed.
    public void setOnDelete(final Runnable onDelete) {
        this.onDelete = onDelete;
    }

    /// @param onSelectionChange Run when the selection changes.
    public void setOnSelectionChange(final Runnable onSelectionChange) {
        this.onSelectionChange = onSelectionChange;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DuplicateManagementList> {

    }
}
