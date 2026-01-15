/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.node.client;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.info.client.ActionCell;
import stroom.cell.info.client.CommandLink;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.ColumnBuilder;
import stroom.data.grid.client.MyDataGrid;
import stroom.dispatch.client.RestFactory;
import stroom.job.client.event.JobNodeChangeEvent;
import stroom.job.shared.BatchScheduleRequest;
import stroom.job.shared.FindJobNodeCriteria;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNode.JobType;
import stroom.job.shared.JobNodeAndInfo;
import stroom.job.shared.JobNodeInfo;
import stroom.job.shared.JobNodeResource;
import stroom.job.shared.JobNodeUtil;
import stroom.job.shared.ScheduleReferenceTime;
import stroom.preferences.client.DateTimeFormatter;
import stroom.schedule.client.SchedulePopup;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitorFactory;
import stroom.task.client.event.OpenTaskManagerEvent;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.scheduler.Schedule;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuBuilder;
import stroom.widget.menu.client.presenter.MenuPresenter;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.cellview.client.Column;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class JobNodeListHelper {

    private static final JobNodeResource JOB_NODE_RESOURCE = GWT.create(JobNodeResource.class);
    private static final String FILTER_BTN_TITLE_ON = "Click to show all jobs";
    private static final String FILTER_BTN_TITLE_OFF = "Click to show only enabled jobs";

    private final DateTimeFormatter dateTimeFormatter;
    private final RestFactory restFactory;
    private final SchedulePopup schedulePresenter;
    private final MenuPresenter menuPresenter;

    private final MultiSelectionModelImpl<JobNodeAndInfo> selectionModel;
    private final TaskMonitorFactory taskMonitorFactory;
    private final HasHandlers hasHandlers;
    private final Runnable refreshFunc;

    private final Set<String> enabledNodeNames = new HashSet<>();

    public JobNodeListHelper(final DateTimeFormatter dateTimeFormatter,
                             final RestFactory restFactory,
                             final SchedulePopup schedulePresenter,
                             final MenuPresenter menuPresenter,
                             final MultiSelectionModelImpl<JobNodeAndInfo> selectionModel,
                             final TaskMonitorFactory taskMonitorFactory,
                             final HasHandlers hasHandlers,
                             final Runnable refreshFunc) {
        this.dateTimeFormatter = dateTimeFormatter;
        this.restFactory = restFactory;
        this.schedulePresenter = schedulePresenter;
        this.menuPresenter = menuPresenter;
        this.selectionModel = selectionModel;
        this.taskMonitorFactory = taskMonitorFactory;
        this.hasHandlers = hasHandlers;
        this.refreshFunc = refreshFunc;
    }

    public void setEnabledNodeNames(final Collection<String> enabledNodeNames) {
        this.enabledNodeNames.clear();
        NullSafe.consume(enabledNodeNames, this.enabledNodeNames::addAll);
    }

    public boolean isNodeEnabled(final String nodeName) {
        if (nodeName == null) {
            return false;
        } else {
            return enabledNodeNames.contains(nodeName);
        }
    }

//    public BrowserEventHandler<JobNodeAndInfo> createExecuteJobNowHandler() {
//        return (Context context, Element elem, JobNodeAndInfo jobNodeAndInfo, NativeEvent event) ->
//                executeJobNow(hasHandlers, taskListener, NullSafe.get(jobNodeAndInfo, JobNodeAndInfo::getJobNode));
//    }

    public void executeJobNow(final JobNode jobNode) {
        ConfirmEvent.fire(hasHandlers,
                "Are you sure you want to execute job '" + jobNode.getJobName()
                + "' on node '" + jobNode.getNodeName() + "' now. " +
                "\n\nThe job will execute shortly, likely within ten seconds. " +
                "You can check it has run by refreshing the table until the 'Last Executed' " +
                "column has been updated.",
                ok -> restFactory.create(JOB_NODE_RESOURCE)
                        .call(resource -> resource.execute(jobNode.getId()))
                        .taskMonitorFactory(taskMonitorFactory)
                        .exec());
    }

    public void showSchedule(final JobNodeAndInfo jobNodeAndInfo) {
        restFactory
                .create(JOB_NODE_RESOURCE)
                .method(resource ->
                        resource.info(jobNodeAndInfo.getJob().getName(), jobNodeAndInfo.getNodeName()))
                .onSuccess(jobNodeInfo ->
                        setSchedule(jobNodeAndInfo, jobNodeInfo))
                .onFailure(throwable ->
                        setSchedule(jobNodeAndInfo, null))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public FieldUpdater<JobNodeAndInfo, TickBoxState> createEnabledStateFieldUpdater(
            final HasHandlers handlers,
            final TaskMonitorFactory taskMonitorFactory,
            final Runnable onSuccessHandler) {

        return (final int rowIndex, final JobNodeAndInfo jobNodeAndInfo, final TickBoxState value) -> {
            if (jobNodeAndInfo != null) {
                final boolean isEnabled = NullSafe.isTrue(value.toBoolean());
                jobNodeAndInfo.getJobNode().setEnabled(isEnabled);
                restFactory
                        .create(JOB_NODE_RESOURCE)
                        .call(jobNodeResource ->
                                jobNodeResource.setEnabled(jobNodeAndInfo.getId(), isEnabled))
                        .onSuccess(aVoid -> {
                            JobNodeChangeEvent.fire(handlers, jobNodeAndInfo.getJobNode());
                            NullSafe.run(onSuccessHandler);
                        })
                        .taskMonitorFactory(taskMonitorFactory)
                        .exec();
            }
        };
    }

    private void setSchedule(final JobNodeAndInfo jobNodeAndInfo,
                             final JobNodeInfo jobNodeInfo) {
        final JobNode jobNode = jobNodeAndInfo.getJobNode();
        final Schedule currentSchedule = JobNodeUtil.getSchedule(jobNode);
        JobNodeInfo info = jobNodeInfo;

        if (currentSchedule != null) {
            if (info == null) {
                info = JobNodeInfo.empty();
            }

            final ScheduleReferenceTime referenceTime = new ScheduleReferenceTime(
                    info.getScheduleReferenceTime(),
                    info.getLastExecutedTime());
            schedulePresenter.setSchedule(currentSchedule, referenceTime);

            final int nodeCount = selectionModel.getSelectedCount();
            if (nodeCount > 1) {
                final List<JobNode> selectedItems = selectionModel.getSelectedItems()
                        .stream()
                        .map(JobNodeAndInfo::getJobNode)
                        .collect(Collectors.toList());
                @SuppressWarnings("SimplifyStreamApiCallChains") final List<JobType> jobTypes = selectedItems.stream()
                        .map(JobNode::getJobType)
                        .distinct()
                        .collect(Collectors.toList());
                if (jobTypes.size() > 1) {
                    AlertEvent.fireError(
                            hasHandlers, "You can only select rows with the same job type.", null);
                } else if (!Objects.equals(jobTypes.get(0), jobNode.getJobType())) {
                    AlertEvent.fireError(
                            hasHandlers, "Job types don't match, '" + jobTypes.get(0) + "' and '"
                                         + jobNode.getJobType() + "'.", null);
                } else {
                    final String msg = "Are you sure you want to change the schedule of job '"
                                       + jobNode.getJobName() + " for " + nodeCount + " nodes?\n\n" +
                                       "All of the following nodes will be set to the same schedule.\n\n"
                                       + selectedItems.stream()
                                               .map(JobNode::getNodeName)
                                               .sorted()
                                               .collect(Collectors.joining("\n"));

                    ConfirmEvent.fire(hasHandlers, msg, isConfirm -> {
                        if (isConfirm) {
                            schedulePresenter.show(schedule -> {
                                JobNodeUtil.setSchedule(jobNode, schedule);
                                final Set<Integer> ids = selectedItems.stream()
                                        .map(JobNode::getId)
                                        .collect(Collectors.toSet());
                                final BatchScheduleRequest batchScheduleRequest = new BatchScheduleRequest(
                                        ids, jobNodeAndInfo.getJobType(), schedule);
                                restFactory
                                        .create(JOB_NODE_RESOURCE)
                                        .call(resource ->
                                                resource.setScheduleBatch(batchScheduleRequest))
                                        .onSuccess(result -> {
                                            JobNodeChangeEvent.fire(hasHandlers, selectedItems);
                                            NullSafe.run(refreshFunc);
                                        })
                                        .taskMonitorFactory(taskMonitorFactory)
                                        .exec();
                            });
                        }
                    });
                }
            } else {
                schedulePresenter.show(schedule -> {
                    JobNodeUtil.setSchedule(jobNode, schedule);
                    restFactory
                            .create(JOB_NODE_RESOURCE)
                            .call(resource ->
                                    resource.setSchedule(jobNodeAndInfo.getId(), schedule))
                            .onSuccess(result -> {
                                JobNodeChangeEvent.fire(hasHandlers, jobNode);
                                NullSafe.run(refreshFunc);
                            })
                            .taskMonitorFactory(taskMonitorFactory)
                            .exec();
                });
            }
        }
    }

    public String getCurrentTaskCountAsStr(final JobNodeAndInfo jobNodeAndInfo) {
        return NullSafe.getOrElse(
                jobNodeAndInfo,
                JobNodeAndInfo::getJobNodeInfo,
                info -> ModelStringUtil.formatCsv(info.getCurrentTaskCount()),
                "?");
    }

    public String getLastExecutedTimeAsStr(final JobNodeAndInfo jobNodeAndInfo) {
        if (NullSafe.test(jobNodeAndInfo, jobNode2 ->
                jobNode2.getJobType() == JobType.CRON
                || jobNode2.getJobType() == JobType.FREQUENCY)) {
            return NullSafe.getOrElse(
                    jobNodeAndInfo,
                    JobNodeAndInfo::getJobNodeInfo,
                    info -> dateTimeFormatter.formatWithDuration(info.getLastExecutedTime()),
                    "?");
        } else {
            return "N/A";
        }
    }

    public String getNextScheduledTimeAsStr(final JobNodeAndInfo jobNodeAndInfo) {
        final JobType jobType = NullSafe.get(jobNodeAndInfo, JobNodeAndInfo::getJobType);
        final boolean isJobNodeEnabled = isJobNodeEnabled(jobNodeAndInfo);

        if (isJobNodeEnabled
            && (jobType == JobType.CRON || jobType == JobType.FREQUENCY)) {
            return NullSafe.getOrElse(
                    jobNodeAndInfo,
                    JobNodeAndInfo::getJobNodeInfo,
                    info -> dateTimeFormatter.formatWithDuration(info.getNextScheduledTime()),
                    "?");
        } else {
            return "N/A";
        }
    }

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

    public static String buildParentJobEnabledStr(final JobNodeAndInfo jobNodeAndInfo) {
        return NullSafe.get(jobNodeAndInfo,
                JobNodeAndInfo::getJob,
                Job::isEnabled,
                isEnabled -> isEnabled
                        ? "Enabled"
                        : "Disabled");
    }

    public static Preset buildRunIconPreset(final JobNodeAndInfo jobNodeAndInfo) {
        if (jobNodeAndInfo != null
            && jobNodeAndInfo.getJobType() != JobType.DISTRIBUTED
            && jobNodeAndInfo.getJobType() != JobType.UNKNOWN) {
            return SvgPresets.RUN
                    .title("Execute job on node " + jobNodeAndInfo.getNodeName() + " now.");
        }
        return null;
    }

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

    public InlineSvgToggleButton buildJobFilterButton() {
        final InlineSvgToggleButton showEnabledToggleBtn = new InlineSvgToggleButton();
        showEnabledToggleBtn.setSvg(SvgImage.FILTER);
        showEnabledToggleBtn.setTitle(FILTER_BTN_TITLE_OFF);
        showEnabledToggleBtn.setOff();

        showEnabledToggleBtn.addClickHandler(event -> {
            if (showEnabledToggleBtn.isOn()) {
                showEnabledToggleBtn.setTitle(FILTER_BTN_TITLE_ON);
            } else {
                showEnabledToggleBtn.setTitle(FILTER_BTN_TITLE_OFF);
            }
            refreshFunc.run();
        });
        return showEnabledToggleBtn;
    }

    public Function<JobNodeAndInfo, CommandLink> buildOpenScheduleCommandLinkFunc() {

        return (final JobNodeAndInfo jobNodeAndInfo) -> {
            final String schedule = NullSafe.get(jobNodeAndInfo, JobNodeAndInfo::getSchedule);

            if (schedule != null) {
                return new CommandLink(
                        schedule,
                        "Edit schedule",
                        () ->
                                showSchedule(jobNodeAndInfo));
            } else {
                return CommandLink.withoutCommand("N/A");
            }
        };
    }

    public List<Item> buildActionMenu(final JobNodeAndInfo jobNodeAndInfo) {

        final JobNode jobNode = NullSafe.get(jobNodeAndInfo, JobNodeAndInfo::getJobNode);
        final String nodeName = NullSafe.get(jobNodeAndInfo, JobNodeAndInfo::getNodeName);
        final boolean isSchedulable = NullSafe.test(
                jobNodeAndInfo,
                JobNodeAndInfo::getJobType,
                type -> type == JobType.CRON || type == JobType.FREQUENCY);

        final MenuBuilder builder = MenuBuilder.builder();

        if (isSchedulable) {
            builder.withIconMenuItem(itemBuilder -> itemBuilder
                    .icon(SvgImage.HISTORY)
                    .text("Edit Schedule")
                    .command(() ->
                            showSchedule(jobNodeAndInfo)));
        }

        if (isSchedulable && isNodeEnabled(nodeName)) {
            builder.withIconMenuItem(itemBuilder -> itemBuilder
                    .icon(SvgImage.PLAY)
                    .text("Run Job on '" + jobNode.getNodeName() + "' Now")
                    .command(() ->
                            executeJobNow(jobNode)));
        }

        return builder
                .withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(SvgImage.JOBS)
                        .text("Show in Server Tasks (" + jobNode.getNodeName() + ")")
                        .command(() -> OpenTaskManagerEvent.fire(
                                hasHandlers,
                                jobNode.getNodeName(),
                                jobNode.getJobName())))
                .withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(SvgImage.JOBS)
                        .text("Show in Server Tasks (All Nodes)")
                        .command(() -> OpenTaskManagerEvent.fire(hasHandlers, jobNode.getJobName())))
                .build();
    }

    public void addEnabledTickBoxColumn(final MyDataGrid<JobNodeAndInfo> dataGrid,
                                        final boolean isSortable) {

        final ColumnBuilder<JobNodeAndInfo, TickBoxState, TickBoxCell> builder =
                DataGridUtil.updatableTickBoxColumnBuilder(TickBoxState.createTickBoxFunc(
                                JobNodeAndInfo::isEnabled))
                        .enabledWhen(this::isJobNodeEnabled)
                        .withFieldUpdater(createEnabledStateFieldUpdater(
                                hasHandlers, taskMonitorFactory, refreshFunc));

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

    public void addTypeColumn(final MyDataGrid<JobNodeAndInfo> dataGrid) {
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(JobNodeListHelper::buildJobTypeStr)
                        .enabledWhen(this::isJobNodeEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Type")
                        .withToolTip("The type of the job. Cron, Frequency or Distributed.")
                        .build(),
                80);
    }

    public void addNodeStateColumn(final MyDataGrid<JobNodeAndInfo> dataGrid) {
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
//                        .withSorting(FindJobNodeCriteria.FIELD_ID_ENABLED)
                        .build(),
                DataGridUtil.headingBuilder("Node State")
                        .withToolTip("Whether this node is enabled or not. Jobs are not executed on disabled nodes.")
                        .build(),
                80);
    }

    public void addScheduleColumn(final MyDataGrid<JobNodeAndInfo> dataGrid) {
        final Column<JobNodeAndInfo, CommandLink> scheduleColumn = DataGridUtil.commandLinkColumnBuilder(
                        buildOpenScheduleCommandLinkFunc())
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

    public void addLastExecutedColumn(final MyDataGrid<JobNodeAndInfo> dataGrid) {
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

    public void addNextExecutedColumn(final MyDataGrid<JobNodeAndInfo> dataGrid) {
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

    public void addActionColumn(final MyDataGrid<JobNodeAndInfo> dataGrid) {

        dataGrid.addColumn(
                DataGridUtil.columnBuilder(
                                Function.identity(),
                                () -> new ActionCell<JobNodeAndInfo>((jobNodeAndInfo, event) -> {
                                    selectionModel.setSelected(jobNodeAndInfo);
                                    final PopupPosition popupPosition = new PopupPosition(
                                            event.getClientX() + 10, event.getClientY());
                                    final List<Item> menuItems = buildActionMenu(jobNodeAndInfo);
                                    menuPresenter.setData(menuItems);
                                    ShowPopupEvent.builder(menuPresenter)
                                            .popupType(PopupType.POPUP)
                                            .popupPosition(popupPosition)
                                            .fire();
                                })
                        )
                        .build(), "", 40);
    }
}
