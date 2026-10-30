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

import stroom.analytics.shared.ExecutionHistory;
import stroom.analytics.shared.ExecutionHistoryFields;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.ColumnSizeConstants;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.ColumnSortList.ColumnSortInfo;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/// An execution schedule's past executions, latest first, a row each (when it ran, the time its data
/// was based on, whether it completed and its message); one can be selected and replayed unless read
/// only.
public class ScheduledProcessHistoryList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    ToolbarButton replayButton;
    @UiField
    DataGrid<ExecutionHistory> dataGrid;

    private final MultiSelectionModelImpl<ExecutionHistory> selectionModel;
    private Consumer<ExecutionHistory> onReplay = executionHistory -> {
    };
    private Runnable onSortChange = () -> {
    };

    /// Makes an empty list, with no columns until [#addColumns(Function)].
    public ScheduledProcessHistoryList() {
        initWidget(BINDER.createAndBindUi(this));
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        replayButton.setOnClick(() -> {
            if (!isReadOnly()) {
                onReplay.accept(selectionModel.getSelected());
            }
        });
        selectionModel.addSelectionHandler(ignored -> enableButtons());
        dataGrid.addColumnSortHandler(ignored -> onSortChange.run());
    }

    /// Adds the columns, once.
    ///
    /// @param dateTimeFormat Formats a time in milliseconds since the epoch.
    public void addColumns(final Function<Long, String> dateTimeFormat) {
        final Column<ExecutionHistory, String> executionTimeColumn = DataGridUtil.textColumnBuilder(
                        DataGridUtil.toStringFunc(ExecutionHistory::getExecutionTimeMs, dateTimeFormat::apply))
                .withSorting(ExecutionHistoryFields.EXECUTION_TIME, false)
                .build();
        dataGrid.addResizableColumn(
                executionTimeColumn,
                DataGridUtil.headingBuilder(ExecutionHistoryFields.EXECUTION_TIME)
                        .withToolTip("The time it was actually executed.")
                        .build(),
                ColumnSizeConstants.DATE_COL);
        // The latest first, unless another sort is chosen
        dataGrid.setDefaultSort(new ColumnSortInfo(executionTimeColumn, false));

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataGridUtil.toStringFunc(
                                ExecutionHistory::getEffectiveExecutionTimeMs,
                                dateTimeFormat::apply))
                        .withSorting(ExecutionHistoryFields.EFFECTIVE_EXECUTION_TIME, false)
                        .build(),
                DataGridUtil.headingBuilder(ExecutionHistoryFields.EFFECTIVE_EXECUTION_TIME)
                        .withToolTip("The time the data was based on rather than the time it was executed.")
                        .build(),
                ColumnSizeConstants.DATE_COL);

        dataGrid.addResizableColumn(
                DataGridUtil.redGreenTextColumnBuilder(
                                ExecutionHistory::isComplete,
                                ExecutionHistory.STATUS_COMPLETE,
                                ExecutionHistory.STATUS_ERROR)
                        .withSorting(ExecutionHistoryFields.STATUS, false)
                        .build(),
                DataGridUtil.headingBuilder(ExecutionHistoryFields.STATUS)
                        .withToolTip("The status of the execution (Complete|Error).")
                        .build(),
                ColumnSizeConstants.MEDIUM_COL);

        dataGrid.addAutoResizableColumn(
                DataGridUtil.textColumnBuilder(ExecutionHistory::getMessage)
                        .withSorting(ExecutionHistoryFields.MESSAGE, false)
                        .build(),
                DataGridUtil.headingBuilder(ExecutionHistoryFields.MESSAGE)
                        .withToolTip("Any output or error message output by the execution.")
                        .build(),
                ColumnSizeConstants.BIG_COL);
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<ExecutionHistory> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection, one execution at most.
    public MultiSelectionModel<ExecutionHistory> getSelectionModel() {
        return selectionModel;
    }

    /// @return How the rows are sorted, for the request for them.
    public ColumnSortList getSortList() {
        return dataGrid.getColumnSortList();
    }

    /// Empties the list.
    public void clearRows() {
        dataGrid.setRowData(0, new ArrayList<>(0));
        dataGrid.setRowCount(0, true);
    }

    /// @param onReplay Given the selected execution when Replay is pressed (not when read only).
    public void setOnReplay(final Consumer<ExecutionHistory> onReplay) {
        this.onReplay = onReplay;
    }

    /// @param onSortChange Run when the user sorts by another column.
    public void setOnSortChange(final Runnable onSortChange) {
        this.onSortChange = onSortChange;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        enableButtons();
    }

    // Replaying adds a new schedule, which isn't allowed if the owning document is read only
    private void enableButtons() {
        replayButton.setEnabled(!isReadOnly() && selectionModel.hasSelectedItems());
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ScheduledProcessHistoryList> {

    }
}
