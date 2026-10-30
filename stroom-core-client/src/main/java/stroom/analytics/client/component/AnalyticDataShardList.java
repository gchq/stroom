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

import stroom.analytics.shared.AnalyticDataShard;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.ColumnSizeConstants;
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

import java.util.Optional;
import java.util.function.Function;

/// An analytic's data shards on every node, a row each (its node, when it was made, its size and
/// its path); several can be selected.
public class AnalyticDataShardList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<AnalyticDataShard> dataGrid;

    private final MultiSelectionModelImpl<AnalyticDataShard> selectionModel;

    /// Makes an empty list, with no columns until [#addColumns(Function)].
    public AnalyticDataShardList() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows come from every node, so are keyed by node and path
        dataGrid.setKeyProvider(shard -> shard.getNode() + ":" + shard.getPath());
        selectionModel = dataGrid.addDefaultSelectionModel(true);
    }

    /// Adds the columns, once.
    ///
    /// @param dateTimeFormat Formats a time in milliseconds since the epoch, with how long ago it was.
    public void addColumns(final Function<Long, String> dateTimeFormat) {
        // Node.
        dataGrid.addResizableColumn(new Column<AnalyticDataShard, String>(new TextCell()) {
            @Override
            public String getValue(final AnalyticDataShard row) {
                return Optional.ofNullable(row)
                        .map(AnalyticDataShard::getNode)
                        .orElse(null);
            }
        }, "Node", ColumnSizeConstants.MEDIUM_COL);

        // Create Time.
        dataGrid.addResizableColumn(new Column<AnalyticDataShard, String>(new TextCell()) {
            @Override
            public String getValue(final AnalyticDataShard row) {
                return dateTimeFormat.apply(row.getCreateTimeMs());
            }
        }, "Create Time", ColumnSizeConstants.DATE_AND_DURATION_COL);

        // Shard size
        dataGrid.addResizableColumn(new Column<AnalyticDataShard, String>(new TextCell()) {
            @Override
            public String getValue(final AnalyticDataShard row) {
                return ModelStringUtil.formatIECByteSizeString(row.getSize());
            }
        }, "Size", ColumnSizeConstants.SMALL_COL);

        // Path.
        dataGrid.addResizableColumn(new Column<AnalyticDataShard, String>(new TextCell()) {
            @Override
            public String getValue(final AnalyticDataShard row) {
                return row.getPath();
            }
        }, "Path", 600);
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<AnalyticDataShard> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection.
    public MultiSelectionModelImpl<AnalyticDataShard> getSelectionModel() {
        return selectionModel;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AnalyticDataShardList> {

    }
}
