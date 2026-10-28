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

package stroom.job.client.component;

import stroom.cell.info.client.CommandLink;
import stroom.cell.valuespinner.shared.EditableInteger;
import stroom.data.client.ColumnSizeConstants;
import stroom.job.shared.FindJobNodeCriteria;
import stroom.job.shared.JobNode.JobType;
import stroom.job.shared.JobNodeAndInfo;
import stroom.node.client.component.JobNodeColumns;
import stroom.node.client.component.JobNodeColumns.ActionMenuHandler;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.ToolbarToggleButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.HeadedPagerPanel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/// A job's nodes, under a heading, a row each (whether it is enabled, its node, the node's state, the
/// job's type and schedule, its task limit, how many tasks it is running, when it last ran and next
/// will, and an action menu), faded unless it runs; several can be selected. Its toolbar filters to
/// the enabled ones and turns auto refresh on or off.
public class JobNodeList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    HeadedPagerPanel pager;
    @UiField
    ToolbarToggleButton showEnabled;
    @UiField
    ToolbarToggleButton autoRefresh;
    @UiField
    DataGrid<JobNodeAndInfo> dataGrid;

    private final MultiSelectionModelImpl<JobNodeAndInfo> selectionModel;
    private JobNodeColumns columns;
    private Runnable onSortChange = () -> {
    };

    /// Makes an empty list, with no columns until [#addColumns(Function, Function, BiConsumer)].
    public JobNodeList() {
        initWidget(BINDER.createAndBindUi(this));
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        dataGrid.addColumnSortHandler(event -> onSortChange.run());
    }

    /// Adds the columns, once.
    ///
    /// @param dateTimeFormat    Formats a time in milliseconds since the epoch, with how long ago it
    ///                          was.
    /// @param nodeLink          Gives a job node's node link, which opens it on the Nodes screen.
    /// @param onTaskLimitChange Given a job node and its new task limit, when the user changes it.
    public void addColumns(final Function<Long, String> dateTimeFormat,
                           final Function<JobNodeAndInfo, CommandLink> nodeLink,
                           final BiConsumer<JobNodeAndInfo, Integer> onTaskLimitChange) {
        columns = new JobNodeColumns(dataGrid, selectionModel, dateTimeFormat);

        // JobNode Enabled tick box
        columns.addEnabledTickBoxColumn(true);

        // Node Name
        final Column<JobNodeAndInfo, CommandLink> nodeNameColumn = DataGridUtil.commandLinkColumnBuilder(nodeLink)
                .enabledWhen(columns::isJobNodeEnabled)
                .withSorting(FindJobNodeCriteria.FIELD_ID_NODE)
                .build();
        DataGridUtil.addCommandLinkFieldUpdater(nodeNameColumn);
        dataGrid.addResizableColumn(
                nodeNameColumn,
                DataGridUtil.headingBuilder("Node")
                        .withToolTip("The Stroom node the job runs on")
                        .build(),
                300);

        // Node State
        columns.addNodeStateColumn();
        // Type
        columns.addTypeColumn();
        // Schedule.
        columns.addScheduleColumn();

        // Max.
        dataGrid.addColumn(
                DataGridUtil.valueSpinnerColumnBuilder("Max Tasks", JobNodeList::getTaskLimit, 1L, 9999L)
                        .enabledWhen(columns::isJobNodeEnabled)
                        .withFieldUpdater((rowIndex, jobNodeAndInfo, value) -> {
                            if (jobNodeAndInfo != null) {
                                onTaskLimitChange.accept(jobNodeAndInfo, value.intValue());
                            }
                        })
                        .build(),
                DataGridUtil.headingBuilder("Max Tasks")
                        .withToolTip("The task limit for this job on this node")
                        .build(),
                ColumnSizeConstants.VALUE_SPINNER_COL);

        // Current Tasks (Cur).
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder(JobNodeColumns::getCurrentTaskCountAsStr)
                        .enabledWhen(columns::isJobNodeEnabled)
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("Current Tasks")
                        .rightAligned()
                        .withToolTip("The number of the currently executing tasks on this node for this job")
                        .build(),
                // Room for the heading and the space before Last Executed
                115);

        // Last executed.
        columns.addLastExecutedColumn();
        // Next scheduled
        columns.addNextExecutedColumn();
        // Action column
        columns.addActionColumn();
    }

    /// @param dataProvider Gives the list its rows.
    public void setDataProvider(final AbstractDataProvider<JobNodeAndInfo> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection.
    public MultiSelectionModelImpl<JobNodeAndInfo> getSelectionModel() {
        return selectionModel;
    }

    /// @return How the rows are sorted, for the request for them.
    public ColumnSortList getSortList() {
        return dataGrid.getColumnSortList();
    }

    /// @param heading The heading above the list, or `null` for none.
    public void setHeading(final String heading) {
        pager.setHeading(heading);
    }

    /// @return Whether only the enabled job nodes are to be shown.
    public boolean isShowEnabledOnly() {
        return showEnabled.isOn();
    }

    /// @param onShowEnabledChange Run when the filter to the enabled job nodes is turned on or off.
    public void setOnShowEnabledChange(final Runnable onShowEnabledChange) {
        showEnabled.setOnToggle(on -> onShowEnabledChange.run());
    }

    /// @param onAutoRefreshChange Given whether auto refresh is now on, when it is turned on or off.
    public void setOnAutoRefreshChange(final Consumer<Boolean> onAutoRefreshChange) {
        autoRefresh.setOnToggle(onAutoRefreshChange);
    }

    /// @param enabledNodeNames The nodes that are enabled; the rows of the others are faded.
    public void setEnabledNodeNames(final Collection<String> enabledNodeNames) {
        columns.setEnabledNodeNames(enabledNodeNames);
        dataGrid.redraw();
    }

    /// @param nodeName A node's name, or `null`.
    /// @return Whether it is enabled.
    public boolean isNodeEnabled(final String nodeName) {
        return columns.isNodeEnabled(nodeName);
    }

    /// Draws the rows again, e.g. once their info has come.
    public void redraw() {
        dataGrid.redraw();
    }

    /// @param onEnabledChange Given a job node and whether it is now enabled, when its tick box is
    ///                        pressed.
    public void setOnEnabledChange(final BiConsumer<JobNodeAndInfo, Boolean> onEnabledChange) {
        columns.setOnEnabledChange(onEnabledChange);
    }

    /// @param onEditSchedule Given a job node whose schedule link is pressed.
    public void setOnEditSchedule(final Consumer<JobNodeAndInfo> onEditSchedule) {
        columns.setOnEditSchedule(onEditSchedule);
    }

    /// @param onShowActionMenu Shows a job node's action menu, when its action button is pressed.
    public void setOnShowActionMenu(final ActionMenuHandler onShowActionMenu) {
        columns.setOnShowActionMenu(onShowActionMenu);
    }

    /// @param onSortChange Run when the user sorts by another column.
    public void setOnSortChange(final Runnable onSortChange) {
        this.onSortChange = onSortChange;
    }

    private static Number getTaskLimit(final JobNodeAndInfo jobNodeAndInfo) {
        return JobType.DISTRIBUTED.equals(jobNodeAndInfo.getJobType())
                ? new EditableInteger(jobNodeAndInfo.getTaskLimit())
                : null;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, JobNodeList> {

    }
}
