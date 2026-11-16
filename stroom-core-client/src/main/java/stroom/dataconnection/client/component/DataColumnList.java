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

package stroom.dataconnection.client.component;

import stroom.dataconnection.client.model.DataColumns;
import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataColumnType;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.selection.client.SelectionBoxCell;
import stroom.widget.selection.client.SimpleSelectionItemWrapper;
import stroom.widget.selection.client.SimpleSelectionListModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.ListDataProvider;

import java.util.ArrayList;
import java.util.List;

/// The columns of a Data Connection's data (CSV or Excel columns, or LDAP attributes): a grid of each
/// column's name and a box to choose its type. CSV and Excel take their names from the data, so
/// they can't be changed here; LDAP attributes can also be added by name and removed
/// ([#setNamesEditable(boolean)]), as an entry has many attributes and some, such as
/// `modifyTimestamp`, are only returned when asked for by name.
public class DataColumnList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Toolbar toolbar;
    @UiField
    ToolbarButton add;
    @UiField
    ToolbarButton remove;
    @UiField
    DataGrid<Row> grid;

    private final ListDataProvider<Row> dataProvider = new ListDataProvider<>();
    private final MultiSelectionModelImpl<Row> selectionModel;
    private final SelectionBoxCell<DataColumnType, SimpleSelectionItemWrapper<DataColumnType>> typeCell;
    private boolean namesEditable;
    private Runnable onChange = () -> {
    };

    /// Makes the list, with no columns, whose names can't be changed.
    public DataColumnList() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows are kept as they are edited, so they are their own keys
        selectionModel = grid.addDefaultSelectionModel(false);
        selectionModel.addSelectionHandler(event -> updateButtons());
        dataProvider.addDataDisplay(grid);

        grid.addResizableColumn(new Column<Row, String>(new TextCell()) {
            @Override
            public String getValue(final Row row) {
                return row.name;
            }
        }, "Name", 250);

        final SimpleSelectionListModel<DataColumnType> typeModel = new SimpleSelectionListModel<>();
        typeModel.addItems(DataColumnType.values());
        typeCell = new SelectionBoxCell<>(typeModel, this::typeLabel);
        final Column<Row, DataColumnType> typeColumn = new Column<>(typeCell) {
            @Override
            public DataColumnType getValue(final Row row) {
                return row.type;
            }
        };
        typeColumn.setFieldUpdater((index, row, type) -> {
            if (!isReadOnly() && type != null && type != row.type) {
                row.type = type;
                onChange.run();
            }
        });
        grid.addResizableColumn(typeColumn, "Type", 150);

        remove.setOnClick(this::onRemove);
        toolbar.setVisible(false);
    }

    /// Lets the user add columns by name and remove them, as LDAP attributes need.
    /// Bound to the `namesEditable` attribute in ui.xml.
    ///
    /// @param namesEditable True to show the Add and Remove toolbar.
    public void setNamesEditable(final boolean namesEditable) {
        this.namesEditable = namesEditable;
        toolbar.setVisible(namesEditable);
        updateButtons();
    }

    /// Bound to the `tableName` attribute in ui.xml.
    ///
    /// @param tableName The grid's name for screen readers, e.g. "Attributes".
    public void setTableName(final String tableName) {
        grid.setTableName(tableName);
    }

    /// Bound to the `emptyText` attribute in ui.xml.
    ///
    /// @param emptyText What to say when there are no columns.
    public void setEmptyText(final String emptyText) {
        grid.setEmptyText(emptyText);
    }

    /// @param onAdd Run when Add is pressed, for the owner to ask for a name ([#addColumn(String)]).
    public void setOnAdd(final Runnable onAdd) {
        add.setOnClick(onAdd);
    }

    /// @param onChange Run when the user changes a column's type, or removes or adds one.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// @return The columns, with the types chosen.
    public List<DataColumn> getColumns() {
        final List<DataColumn> columns = new ArrayList<>(dataProvider.getList().size());
        for (final Row row : dataProvider.getList()) {
            columns.add(new DataColumn(row.name, row.type));
        }
        return columns;
    }

    /// @param columns The columns to show, or `null` for none. A column with no type is shown as Text.
    public void setColumns(final List<DataColumn> columns) {
        final List<Row> rows = new ArrayList<>();
        if (columns != null) {
            for (final DataColumn column : columns) {
                rows.add(new Row(column.getName(), DataColumns.typeOf(column)));
            }
        }
        selectionModel.clear();
        dataProvider.setList(rows);
        updateButtons();
    }

    /// Adds a Text column at the end and selects it (UI-GUIDE.md, "What a list selects").
    ///
    /// @param name The name, already checked ([DataColumns#validateNewName]).
    public void addColumn(final String name) {
        final Row row = new Row(name.trim(), DataColumnType.TEXT);
        dataProvider.getList().add(row);
        selectionModel.setSelected(row);
        onChange.run();
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        typeCell.setReadOnly(readOnly);
        // The cells only pick it up when they draw again
        grid.redraw();
        updateButtons();
    }

    private void onRemove() {
        final Row selected = selectionModel.getSelected();
        if (selected != null && !isReadOnly()) {
            // The row now in its place (UI-GUIDE.md, "What a list selects")
            selectionModel.selectNextAfterRemove(List.of(selected));
            dataProvider.getList().remove(selected);
            onChange.run();
        }
    }

    private void updateButtons() {
        add.setEnabled(namesEditable && !isReadOnly());
        remove.setEnabled(namesEditable && !isReadOnly() && selectionModel.getSelected() != null);
    }

    // Names each row's type box for its column, e.g. "Type of mail"
    private String typeLabel(final int index) {
        final List<Row> rows = dataProvider.getList();
        return index >= 0 && index < rows.size()
                ? "Type of " + rows.get(index).name
                : "Type, row " + (index + 1);
    }


    // --------------------------------------------------------------------------------


    // A column as it is edited: its type changes in place, so the row keeps its place and focus.
    // Rows are compared by identity.
    private static final class Row {

        private final String name;
        private DataColumnType type;

        private Row(final String name, final DataColumnType type) {
            this.name = name;
            this.type = type;
        }
    }

    interface Binder extends UiBinder<Widget, DataColumnList> {

    }
}
