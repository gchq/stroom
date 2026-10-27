/*
 * Copyright 2018 Crown Copyright
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

package stroom.query.client.component;

import stroom.query.client.presenter.TableRow;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A query's results as a table: the rows (only those in view are drawn), grouped and expandable,
/// below the table's actions, which its controller puts on the pager's toolbar with the columns.
public class QueryResultTable extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<TableRow> dataGrid;

    /// Makes the table, with no columns.
    public QueryResultTable() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The pager, with the table's toolbar, refresh button and messages.
    public PagerPanel getPager() {
        return pager;
    }

    /// @return The grid of rows, for the controller to give columns and rows.
    public DataGrid<TableRow> getDataGrid() {
        return dataGrid;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, QueryResultTable> {

    }
}
