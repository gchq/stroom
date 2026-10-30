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

import stroom.cell.client.ActionCell;
import stroom.cell.client.CommandLink;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.client.TickBoxState;
import stroom.job.shared.FindJobNodeCriteria;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode.JobType;
import stroom.job.shared.JobNodeAndInfo;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.datagrid.client.ColumnBuilder;
import stroom.widget.datagrid.client.ColumnSizeConstants;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.cellview.client.Column;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/// The columns the lists of job nodes share (the Jobs screen's and the Nodes screen's), added to their
/// grid: whether it is enabled, its type, its node's state, its schedule, when it last ran and next
/// will, and an action menu. A row is faded unless its node, its job and it are all enabled. What the
/// user does goes out through callbacks; it does no work of its own.
public class JobNodeColumns {

    private final DataGrid<JobNodeAndInfo> dataGrid;
    private final MultiSelectionModel<JobNodeAndInfo> selectionModel;
    private final Function<Long, String> dateTimeFormat;
    private final Set<String> enabledNodeNames = new HashSet<>();
    private BiConsumer<JobNodeAndInfo, Boolean> onEnabledChange = (row, enabled) -> {
    };
    private Consumer<JobNodeAndInfo> onEditSchedule = row -> {
    };
    private ActionMenuHandler onShowActionMenu = (row, popupPosition, opener) -> {
    };

    /// Makes the columns' builder for a grid; nothing is added until asked.
    ///
    /// @param dataGrid       The grid to add to.
    /// @param selectionModel The grid's selection, a row of which is selected when its action menu is
    ///                       shown.
    /// @param dateTimeFormat Formats a time in milliseconds since the epoch, with how long ago it was.
    public JobNodeColumns(final DataGrid<JobNodeAndInfo> dataGrid,
                          final MultiSelectionModel<JobNodeAndInfo> selectionModel,
                          final Function<Long, String> dateTimeFormat) {
        this.dataGrid = dataGrid;
        this.selectionModel = selectionModel;
        this.dateTimeFormat = dateTimeFormat;
    }

    /// @param enabledNodeNames The nodes that are enabled; the rows of the others are faded.
    public void setEnabledNodeNames(final Collection<String> enabledNodeNames) {
        this.enabledNodeNames.clear();
        NullSafe.consume(enabledNodeNames, this.enabledNodeNames::addAll);
    }

    /// @param nodeName A node's name, or `null`.
    /// @return Whether it is enabled.
    public boolean isNodeEnabled(final String nodeName) {
        return nodeName != null && enabledNodeNames.contains(nodeName);
    }

    /// @param jobNodeAndInfo A job node, or `null`.
    /// @return Whether it runs: it, its node and its parent job are all enabled.
    public boolean isJobNodeEnabled(final JobNodeAndInfo jobNodeAndInfo) {
        if (jobNodeAndInfo == null) {
            return false;
        } else {
            // A job node is only enabled if the node and the parent job is also enabled
            return isNodeEnabled(NullSafe.get(jobNodeAndInfo, JobNodeAndInfo::getNodeName))
                   && jobNodeAndInfo.isEnabled()
                   && NullSafe.isTrue(jobNodeAndInfo.getJob(), Job::isEnabled);
        }
    }

    /// @param onEnabledChange Given a job node and whether it is now enabled, when its tick box is
    ///                        pressed.
    public void setOnEnabledChange(final BiConsumer<JobNodeAndInfo, Boolean> onEnabledChange) {
        this.onEnabledChange = onEnabledChange;
    }

    /// @param onEditSchedule Given a job node whose schedule link is pressed.
    public void setOnEditSchedule(final Consumer<JobNodeAndInfo> onEditSchedule) {
        this.onEditSchedule = onEditSchedule;
    }

    /// @param onShowActionMenu Shows a job node's action menu, when its action button is pressed.
    public void setOnShowActionMenu(final ActionMenuHandler onShowActionMenu) {
        this.onShowActionMenu = onShowActionMenu;
    }

    /// Adds the column of tick boxes that enable a job node.
    ///
    /// @param isSortable Whether the list can be sorted by it.
    public void addEnabledTickBoxColumn(final boolean isSortable) {
        final ColumnBuilder<JobNodeAndInfo, TickBoxState, TickBoxCell> builder =
                DataGridUtil.updatableTickBoxColumnBuilder(TickBoxState.createTickBoxFunc(
                                JobNodeAndInfo::isEnabled))
                        .enabledWhen(this::isJobNodeEnabled)
                        .withFieldUpdater((rowIndex, jobNodeAndInfo, value) -> {
                            if (jobNodeAndInfo != null) {
                                onEnabledChange.accept(jobNodeAndInfo, NullSafe.isTrue(value.toBoolean()));
                            }
                        });

        if (isSortable) {
            builder.withSorting(FindJobNodeCriteria.FIELD_ID_ENABLED);
        }

        final Column<JobNodeAndInfo, TickBoxState> column = builder.build();

        dataGrid.addColumn(
                column,
                DataGridUtil.headingBuilder("Enabled")
                        .withToolTip("Whether this job is enabled on this node or not. " +
                                     "Both the node and parent job must also be enabled for the job to execute.")
                        .build(),
                ColumnSizeConstants.ENABLED_COL);
    }

    /// Adds the column of the jobs' types.
    public void addTypeColumn() {
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(JobNodeColumns::buildJobTypeStr)
                        .enabledWhen(this::isJobNodeEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Type")
                        .withToolTip("The type of the job. Cron, Frequency or Distributed.")
                        .build(),
                80);
    }

    /// Adds the column of whether each job node's node is enabled.
    public void addNodeStateColumn() {
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder(
                                (final JobNodeAndInfo jobNodeAndInfo) -> {
                                    final String nodeName = NullSafe.get(
                                            jobNodeAndInfo, JobNodeAndInfo::getNodeName);
                                    return isNodeEnabled(nodeName)
                                            ? "Enabled"
                                            : "Disabled";
                                })
                        .enabledWhen(this::isJobNodeEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Node State")
                        .withToolTip("Whether this node is enabled or not. Jobs are not executed on disabled nodes.")
                        .build(),
                80);
    }

    /// Adds the column of the job nodes' schedules, each a link that edits it.
    public void addScheduleColumn() {
        final Column<JobNodeAndInfo, CommandLink> scheduleColumn = DataGridUtil.commandLinkColumnBuilder(
                        this::buildOpenScheduleCommandLink)
                .enabledWhen(this::isJobNodeEnabled)
                .build();
        DataGridUtil.addCommandLinkFieldUpdater(scheduleColumn);
        dataGrid.addResizableColumn(
                scheduleColumn,
                DataGridUtil.headingBuilder("Schedule")
                        .withToolTip("The schedule for this job on this node, if applicable to the job type")
                        .build(),
                200);
    }

    /// Adds the column of when each job node last ran.
    public void addLastExecutedColumn() {
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder(this::getLastExecutedTimeAsStr)
                        .enabledWhen(this::isJobNodeEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Last Executed")
                        .withToolTip("The date/time that this job was last executed on this node, " +
                                     "if applicable to the job type.")
                        .build(),
                ColumnSizeConstants.DATE_AND_DURATION_COL);
    }

    /// Adds the column of when each job node will next run.
    public void addNextExecutedColumn() {
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder(this::getNextScheduledTimeAsStr)
                        .enabledWhen(this::isJobNodeEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Next Scheduled")
                        .withToolTip("The date/time that this job is next scheduled to execute on this node, " +
                                     "if applicable to the job type.")
                        .build(),
                ColumnSizeConstants.DATE_AND_DURATION_COL);
    }

    /// Adds the column of action buttons, each showing its job node's action menu.
    public void addActionColumn() {
        dataGrid.addColumn(
                DataGridUtil.columnBuilder(
                                Function.identity(),
                                () -> new ActionCell<JobNodeAndInfo>(this::showActionMenu,
                                        JobNodeColumns::getActionMenuName)
                        )
                        .build(), "", 40);
    }

    /// @param jobNodeAndInfo A job node.
    /// @return The name of its actions (its button's and its menu's).
    public static String getActionMenuName(final JobNodeAndInfo jobNodeAndInfo) {
        return "Actions for job '" + jobNodeAndInfo.getJobName()
               + "' on node '" + jobNodeAndInfo.getNodeName() + "'";
    }

    /// @param jobNodeAndInfo A job node, or `null`.
    /// @return How many of its tasks are running, or "?" if not known.
    public static String getCurrentTaskCountAsStr(final JobNodeAndInfo jobNodeAndInfo) {
        return NullSafe.getOrElse(
                jobNodeAndInfo,
                JobNodeAndInfo::getJobNodeInfo,
                info -> ModelStringUtil.formatCsv(info.getCurrentTaskCount()),
                "?");
    }

    /// @param jobNodeAndInfo A job node, or `null`.
    /// @return Whether its parent job is enabled, as "Enabled" or "Disabled".
    public static String buildParentJobEnabledStr(final JobNodeAndInfo jobNodeAndInfo) {
        return NullSafe.get(jobNodeAndInfo,
                JobNodeAndInfo::getJob,
                Job::isEnabled,
                isEnabled -> isEnabled
                        ? "Enabled"
                        : "Disabled");
    }

    /// @param jobNodeAndInfo A job node.
    /// @return Its job's type, e.g. "Cron", or `null` if unknown.
    public static String buildJobTypeStr(final JobNodeAndInfo jobNodeAndInfo) {
        //noinspection EnhancedSwitchMigration // not in GWT
        switch (jobNodeAndInfo.getJobType()) {
            case CRON:
                return "Cron";
            case FREQUENCY:
                return "Frequency";
            case DISTRIBUTED:
                return "Distributed";
            default:
                return null;
        }
    }

    private String getLastExecutedTimeAsStr(final JobNodeAndInfo jobNodeAndInfo) {
        if (NullSafe.test(jobNodeAndInfo, jobNode2 ->
                jobNode2.getJobType() == JobType.CRON
                || jobNode2.getJobType() == JobType.FREQUENCY)) {
            return NullSafe.getOrElse(
                    jobNodeAndInfo,
                    JobNodeAndInfo::getJobNodeInfo,
                    info -> dateTimeFormat.apply(info.getLastExecutedTime()),
                    "?");
        } else {
            return "N/A";
        }
    }

    private String getNextScheduledTimeAsStr(final JobNodeAndInfo jobNodeAndInfo) {
        final JobType jobType = NullSafe.get(jobNodeAndInfo, JobNodeAndInfo::getJobType);
        final boolean isJobNodeEnabled = isJobNodeEnabled(jobNodeAndInfo);

        if (isJobNodeEnabled
            && (jobType == JobType.CRON || jobType == JobType.FREQUENCY)) {
            return NullSafe.getOrElse(
                    jobNodeAndInfo,
                    JobNodeAndInfo::getJobNodeInfo,
                    info -> dateTimeFormat.apply(info.getNextScheduledTime()),
                    "?");
        } else {
            return "N/A";
        }
    }

    private CommandLink buildOpenScheduleCommandLink(final JobNodeAndInfo jobNodeAndInfo) {
        final String schedule = NullSafe.get(jobNodeAndInfo, JobNodeAndInfo::getSchedule);

        if (schedule != null) {
            // Says which job and node, as the button's name is heard on its own
            return new CommandLink(
                    schedule,
                    "Edit schedule for job '" + jobNodeAndInfo.getJobName()
                    + "' on node '" + jobNodeAndInfo.getNodeName() + "'",
                    () -> onEditSchedule.accept(jobNodeAndInfo));
        } else {
            return CommandLink.withoutCommand("N/A");
        }
    }

    // Shown as Stroom's other menus are, so that it is named and takes the focus
    private void showActionMenu(final JobNodeAndInfo jobNodeAndInfo, final NativeEvent event) {
        selectionModel.setSelected(jobNodeAndInfo);
        final PopupPosition popupPosition = new PopupPosition(
                event.getClientX() + 10, event.getClientY());
        final Element opener = Element.is(event.getEventTarget())
                ? Element.as(event.getEventTarget())
                : null;
        onShowActionMenu.show(jobNodeAndInfo, popupPosition, opener);
    }


    // --------------------------------------------------------------------------------


    /// Shows a job node's action menu.
    public interface ActionMenuHandler {

        /// @param row           The job node.
        /// @param popupPosition Where to show it.
        /// @param opener        The action button pressed, given the focus back when the menu
        ///                      closes, or `null`.
        void show(JobNodeAndInfo row, PopupPosition popupPosition, Element opener);
    }
}
