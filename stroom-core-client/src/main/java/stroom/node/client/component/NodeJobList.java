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

import stroom.cell.client.CommandLink;
import stroom.job.shared.JobNodeAndInfo;
import stroom.node.client.component.JobNodeColumns.ActionMenuHandler;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.ToolbarToggleButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.form.client.FormGroup;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/// A node's jobs, under a heading, a row each (whether it is enabled on the node, the job's name and
/// state, the node's state, the job's type and schedule, when it last ran and next will, and an
/// action menu), faded unless it runs, the advanced jobs after a gap; one can be selected. Its toolbar
/// filters to the enabled ones and turns auto refresh on or off.
public class NodeJobList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FormGroup heading;
    @UiField
    PagerPanel pager;
    @UiField
    ToolbarToggleButton showEnabled;
    @UiField
    ToolbarToggleButton autoRefresh;
    @UiField
    DataGrid<JobNodeAndInfo> dataGrid;

    private final MultiSelectionModelImpl<JobNodeAndInfo> selectionModel;
    private JobNodeColumns columns;

    /// Makes an empty list, with no columns until [#addColumns(Function, Function)].
    public NodeJobList() {
        initWidget(BINDER.createAndBindUi(this));
        selectionModel = dataGrid.addDefaultSelectionModel(false);
    }

    /// Adds the columns, once.
    ///
    /// @param dateTimeFormat Formats a time in milliseconds since the epoch, with how long ago it was.
    /// @param jobLink        Gives a job node's job link, which opens it on the Jobs screen.
    public void addColumns(final Function<Long, String> dateTimeFormat,
                           final Function<JobNodeAndInfo, CommandLink> jobLink) {
        columns = new JobNodeColumns(dataGrid, selectionModel, dateTimeFormat);

        // JobNode Enabled tick box
        columns.addEnabledTickBoxColumn(false);

        // Job Name
        final Column<JobNodeAndInfo, CommandLink> jobNameColumn = DataGridUtil.commandLinkColumnBuilder(jobLink)
                .enabledWhen(columns::isJobNodeEnabled)
                .build();
        DataGridUtil.addCommandLinkFieldUpdater(jobNameColumn);
        dataGrid.addResizableColumn(
                jobNameColumn,
                DataGridUtil.headingBuilder("Job Name")
                        .withToolTip("The name of the job.")
                        .build(),
                350);

        // Job State.
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder(JobNodeColumns::buildParentJobEnabledStr)
                        .enabledWhen(columns::isJobNodeEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Job State")
                        .withToolTip("Whether this job is enabled across all nodes or not. " +
                                     "An enabled job must also be enabled on a node for it to execute on that node.")
                        .build(),
                80);

        // Node State
        columns.addNodeStateColumn();
        // Type
        columns.addTypeColumn();
        // Schedule.
        columns.addScheduleColumn();
        // Last executed.
        columns.addLastExecutedColumn();
        // Next executed.
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

    /// @param nodeName The node whose jobs are shown, which names the list, or `null`.
    public void setNodeName(final String nodeName) {
        dataGrid.setAccessibleName(nodeName == null
                ? "Node jobs"
                : "Jobs on " + nodeName);
    }

    /// @param heading The heading above the list, or `null` for none.
    public void setHeading(final String heading) {
        this.heading.setLabel(heading);
    }

    /// @return Whether only the enabled job nodes are to be shown.
    public boolean isShowEnabledOnly() {
        return showEnabled.isPressed();
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


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, NodeJobList> {

    }
}
