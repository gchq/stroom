/*
 * Copyright 2025 Crown Copyright
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

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.query.api.ColumnRef;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.OrderByColumn;
import stroom.widget.datagrid.client.PagerPanel;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.client.ui.Widget;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// A table's columns, by name, each with a tick box to choose it; the header's tick box chooses all
/// or none.
public class ColumnSelection extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<ColumnRef> dataGrid;

    private final Set<ColumnRef> allColumns = new HashSet<>();
    private final Set<ColumnRef> selectedColumns = new HashSet<>();
    private Consumer<Set<ColumnRef>> onChange = selected -> {
    };

    /// Makes an empty list.
    public ColumnSelection() {
        initWidget(BINDER.createAndBindUi(this));
        addSelectedColumn();
        addNameColumn();
        dataGrid.addColumnSortHandler(event -> refresh());
    }

    /// @param selectedColumns The columns chosen, shown at the next [#refresh()].
    public void setSelectedColumns(final Set<ColumnRef> selectedColumns) {
        this.selectedColumns.clear();
        this.selectedColumns.addAll(NullSafe.set(selectedColumns));
    }

    /// @param allColumns The columns to choose from, shown at the next [#refresh()].
    public void setAllColumns(final Set<ColumnRef> allColumns) {
        this.allColumns.clear();
        this.allColumns.addAll(NullSafe.set(allColumns));
    }

    /// @return The columns chosen.
    public Set<ColumnRef> getSelectedColumns() {
        return selectedColumns;
    }

    /// @param onChange Given the columns chosen when the user ticks or unticks one, or all.
    public void setOnChange(final Consumer<Set<ColumnRef>> onChange) {
        this.onChange = onChange;
    }

    /// Shows the columns, by name.
    public void refresh() {
        final List<ColumnRef> rows = allColumns
                .stream()
                .sorted(Comparator.comparing(ColumnRef::getName))
                .collect(Collectors.toList());
        dataGrid.setRowData(0, rows);
        dataGrid.setRowCount(rows.size(), true);
    }

    private void addSelectedColumn() {
        // Select Column
        final Column<ColumnRef, TickBoxState> column = new Column<ColumnRef, TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue(final ColumnRef columnRef) {
                return TickBoxState.fromBoolean(selectedColumns.contains(columnRef));
            }

        };
        final Header<TickBoxState> header = new Header<TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue() {
                if (allColumns.size() == selectedColumns.size()) {
                    return TickBoxState.TICK;
                } else if (!selectedColumns.isEmpty()) {
                    return TickBoxState.HALF_TICK;
                }
                return TickBoxState.UNTICK;
            }
        };
        dataGrid.addColumn(column, header, ColumnSizeConstants.CHECKBOX_COL);

        // Add Handlers
        header.setUpdater(value -> {
            if (value.equals(TickBoxState.UNTICK)) {
                selectedColumns.clear();
            } else if (value.equals(TickBoxState.TICK)) {
                selectedColumns.clear();
                selectedColumns.addAll(allColumns);
            }
            onChange.accept(selectedColumns);
            refresh();
        });
        column.setFieldUpdater((index, row, value) -> {
            if (value.toBoolean()) {
                selectedColumns.add(row);
            } else {
                selectedColumns.remove(row);
            }
            onChange.accept(selectedColumns);
            dataGrid.redrawHeaders();
        });
    }

    private void addNameColumn() {
        dataGrid.addResizableColumn(new OrderByColumn<ColumnRef, String>(
                new TextCell(), "Column Name", true) {
            @Override
            public String getValue(final ColumnRef columnRef) {
                return columnRef.getName();
            }
        }, "Column Name", 300);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ColumnSelection> {

    }
}
