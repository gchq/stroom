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

package stroom.ai.client.component;

import stroom.task.client.TaskMonitorFactory;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.List;

/// An Ask Stroom AI attachment's table, a page of its rows at a time, a column for each of its
/// headings.
public class AiAttachmentData extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<List<String>> dataGrid;

    /// Makes an empty table.
    public AiAttachmentData() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Adds a column for each heading, showing the cell of each row in its place.
    ///
    /// @param headers The table's headings.
    public void addColumns(final List<String> headers) {
        for (int i = 0; i < headers.size(); i++) {
            final int colIndex = i;
            final Column<List<String>, String> column =
                    new Column<List<String>, String>(new TextCell()) {
                        @Override
                        public String getValue(final List<String> row) {
                            if (row != null && colIndex < row.size()) {
                                return row.get(colIndex);
                            }
                            return "";
                        }
                    };
            dataGrid.addColumn(column, headers.get(colIndex));
        }
    }

    /// @param dataProvider Gives the table its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<List<String>> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// Shows the page that starts at a row, if it isn't the one shown.
    ///
    /// @param pageStart The absolute index of the first row of the page.
    public void showPageStartingAt(final int pageStart) {
        if (pageStart != dataGrid.getPageStart()) {
            dataGrid.setVisibleRange(pageStart, dataGrid.getPageSize());
        }
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AiAttachmentData> {

    }
}
