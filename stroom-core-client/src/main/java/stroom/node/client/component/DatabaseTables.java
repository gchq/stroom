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

package stroom.node.client.component;

import stroom.node.shared.DBTableStatus;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.OrderByColumn;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.task.client.TaskMonitorFactory;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.function.Function;

/// The Database Tables screen: Stroom's database tables, a row each (database, table, row count,
/// data size and index size), sortable by any column, with a pager.
public class DatabaseTables extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<DBTableStatus> dataGrid;

    private Runnable onSortChange = () -> {
    };

    /// Makes an empty list.
    public DatabaseTables() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows keyed by database and table
        dataGrid.setKeyProvider(row -> row.getDb() + "." + row.getTable());
        addColumn(DBTableStatus.FIELD_DATABASE, true, 200, DBTableStatus::getDb);
        addColumn(DBTableStatus.FIELD_TABLE, true, 200, DBTableStatus::getTable);
        addColumn(DBTableStatus.FIELD_ROW_COUNT, false, 100,
                row -> ModelStringUtil.formatCsv(row.getCount()));
        addColumn(DBTableStatus.FIELD_DATA_SIZE, false, 100,
                row -> ModelStringUtil.formatIECByteSizeString(row.getDataSize()));
        addColumn(DBTableStatus.FIELD_INDEX_SIZE, false, 100,
                row -> ModelStringUtil.formatIECByteSizeString(row.getIndexSize()));
        dataGrid.addColumnSortHandler(event -> onSortChange.run());
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<DBTableStatus> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The columns the rows are sorted by, first to last.
    public ColumnSortList getColumnSortList() {
        return dataGrid.getColumnSortList();
    }

    /// @param onSortChange Run when the user sorts by a column.
    public void setOnSortChange(final Runnable onSortChange) {
        this.onSortChange = onSortChange;
    }

    private void addColumn(final String name,
                           final boolean ignoreCase,
                           final int width,
                           final Function<DBTableStatus, String> function) {
        dataGrid.addResizableColumn(new OrderByColumn<DBTableStatus, String>(
                new TextCell(), name, ignoreCase) {
            @Override
            public String getValue(final DBTableStatus row) {
                return function.apply(row);
            }
        }, name, width);
    }

    // --------------------------------------------------------------------------------

    interface Binder extends UiBinder<Widget, DatabaseTables> {

    }
}
