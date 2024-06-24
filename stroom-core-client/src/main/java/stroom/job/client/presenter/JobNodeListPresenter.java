/*
 * Copyright 2016 Crown Copyright
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

package stroom.job.client.presenter;

import stroom.cell.tickbox.shared.TickBoxState;
import stroom.cell.valuespinner.shared.EditableInteger;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.job.client.JobTypeCell;
import stroom.job.shared.FindJobNodeCriteria;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNode.JobType;
import stroom.job.shared.JobNodeInfo;
import stroom.job.shared.JobNodeResource;
import stroom.job.shared.JobNodeUtil;
import stroom.job.shared.ScheduleReferenceTime;
import stroom.preferences.client.DateTimeFormatter;
import stroom.schedule.client.SchedulePopup;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.scheduler.Schedule;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class JobNodeListPresenter extends MyPresenterWidget<PagerView> {

    private static final JobNodeResource JOB_NODE_RESOURCE = GWT.create(JobNodeResource.class);

    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;
    private final SchedulePopup schedulePresenter;
    private final UiConfigCache clientPropertyCache;

    private final RestDataProvider<JobNode, ResultPage<JobNode>> dataProvider;
    private final Map<JobNode, JobNodeInfo> latestNodeInfo = new HashMap<>();

    private final MyDataGrid<JobNode> dataGrid;

    private String jobName;
    private final FindJobNodeCriteria findJobNodeCriteria = new FindJobNodeCriteria();

    @Inject
    public JobNodeListPresenter(final EventBus eventBus,
                                final PagerView view,
                                final RestFactory restFactory,
                                final DateTimeFormatter dateTimeFormatter,
                                final SchedulePopup schedulePresenter,
                                final UiConfigCache clientPropertyCache) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;
        this.schedulePresenter = schedulePresenter;
        this.clientPropertyCache = clientPropertyCache;

        dataGrid = new MyDataGrid<>();
        dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        initTable();

        dataProvider = new RestDataProvider<JobNode, ResultPage<JobNode>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<JobNode>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                findJobNodeCriteria.getJobName().setString(jobName);
                restFactory
                        .create(JOB_NODE_RESOURCE)
                        .method(res -> res.find(findJobNodeCriteria))
                        .onSuccess(dataConsumer)
                        .onFailure(errorHandler)
                        .taskListener(view)
                        .exec();
            }

            @Override
            protected void changeData(final ResultPage<JobNode> data) {
                // Ping each node.
                data.getValues().forEach(jobNodeInfo -> {
                    restFactory
                            .create(JOB_NODE_RESOURCE)
                            .method(res -> res.info(jobNodeInfo.getJob().getName(), jobNodeInfo.getNodeName()))
                            .onSuccess(info -> {
                                latestNodeInfo.put(jobNodeInfo, info);
                                super.changeData(data);
                                dataGrid.redraw();
                            })
                            .onFailure(throwable -> {
                                latestNodeInfo.remove(jobNodeInfo);
                                super.changeData(data);
                            })
                            .taskListener(getView())
                            .exec();
                });
                super.changeData(data);
            }
        };
    }

    private static String getType(JobNode jobNode) {
        //noinspection EnhancedSwitchMigration // not in GWT
        switch (jobNode.getJobType()) {
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

    private Number getTaskLimit(JobNode jobNode) {
        return JobType.DISTRIBUTED.equals(jobNode.getJobType())
                ? new EditableInteger(jobNode.getTaskLimit())
                : null;
    }

    void refresh() {
        dataProvider.refresh();
    }

    /**
     * Add the columns to the table.
     */
    private void initTable() {
        DataGridUtil.addColumnSortHandler(dataGrid, findJobNodeCriteria, this::refresh);

        // Enabled.
        dataGrid.addColumn(
                DataGridUtil.updatableTickBoxColumnBuilder(JobNode::isEnabled)
                        .enabledWhen(this::isJobNodeEnabled)
                        .withSorting(FindJobNodeCriteria.FIELD_ID_ENABLED)
                        .withFieldUpdater(this::updateEnabledState)
                        .build(),
                DataGridUtil.headingBuilder("Enabled")
                        .withToolTip("Whether this job is enabled on this node or not. " +
                                "The parent job must also be enabled for the job to execute.")
                        .build(),
                70);

        // Node Name
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(JobNode::getNodeName)
                        .enabledWhen(this::isJobNodeEnabled)
                        .withSorting(FindJobNodeCriteria.FIELD_ID_NODE)
                        .build(),
                DataGridUtil.headingBuilder("Node")
                        .withToolTip("The Stroom node the job runs on")
                        .build(),
                350);

        // Type
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(JobNodeListPresenter::getType)
                        .enabledWhen(this::isJobNodeEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Type")
                        .withToolTip("The type of the job")
                        .build(),
                80);

        // Schedule.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((JobNode jobNode1) -> GwtNullSafe.requireNonNullElse(
                                jobNode1.getSchedule(),
                                "N/A"))
                        .enabledWhen(this::isJobNodeEnabled)
                        .withBrowserEventHandler((context, elem, jobNode, event) -> {
                            if (jobNode != null && MouseUtil.isPrimary(event)) {
                                showSchedule(jobNode);
                            }
                        })
                        .build(),
                DataGridUtil.headingBuilder("Schedule")
                        .withToolTip("The schedule for this job on this node, if applicable to the job type")
                        .build(),
                250);

        // Job Type Icon
        dataGrid.addColumn(
                DataGridUtil.columnBuilder((JobNode jobNode) ->
                                        GwtNullSafe.requireNonNullElse(jobNode.getJobType(), JobType.UNKNOWN),
                                JobTypeCell::new)
                        .enabledWhen(this::isJobNodeEnabled)
                        .withBrowserEventHandler((context, elem, jobNode, event) -> {
                            if (jobNode != null && MouseUtil.isPrimary(event)) {
                                showSchedule(jobNode);
                            }
                        })
                        .build(),
                DataGridUtil.headingBuilder("")
                        .build(),
                ColumnSizeConstants.ICON_COL);

        // Max.
        dataGrid.addColumn(
                DataGridUtil.valueSpinnerColumnBuilder(this::getTaskLimit, 1L, 9999L)
                        .enabledWhen(this::isJobNodeEnabled)
                        .withFieldUpdater((rowIndex, jobNode, value) -> {
                            jobNode.setTaskLimit(value.intValue());
                            restFactory
                                    .create(JOB_NODE_RESOURCE)
                                    .call(res -> res.setTaskLimit(jobNode.getId(), value.intValue()))
                                    .taskListener(getView())
                                    .exec();
                        })
                        .build(),
                DataGridUtil.headingBuilder("Max Tasks")
                        .withToolTip("The task limit for this job on this node")
                        .build(),
                80);

        // Current Tasks (Cur).
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder(this::getCurrentTaskCountAsStr)
                        .enabledWhen(this::isJobNodeEnabled)
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("Current Tasks")
                        .withToolTip("The number of the currently executing tasks on this node for this job")
                        .build(),
                100);

        // Last executed.
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder(this::getLastExecutedTimeAsStr)
                        .enabledWhen(this::isJobNodeEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Last Executed")
                        .withToolTip("The date/time that this job was last executed on this node, " +
                                "if applicable to the job type.")
                        .build(),
                ColumnSizeConstants.DATE_AND_DURATION_COL);

        // Last executed.
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder(this::getNextScheduledTimeAsStr)
                        .enabledWhen(this::isJobNodeEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Next Scheduled")
                        .withToolTip("The date/time that this job is next scheduled to execute on this node, " +
                                "if applicable to the job type.")
                        .build(),
                ColumnSizeConstants.DATE_AND_DURATION_COL);

        DataGridUtil.addEndColumn(dataGrid);
    }

    private boolean isJobNodeEnabled(final JobNode jobNode) {
        if (jobNode == null) {
            return false;
        } else {
            // A job node is only enabled if enabled both at the job and job node level
            return jobNode.isEnabled()
                    && GwtNullSafe.isTrue(jobNode.getJob(), Job::isEnabled);
        }
    }

    private void showSchedule(final JobNode row) {
        restFactory
                .create(JOB_NODE_RESOURCE)
                .method(res -> res.info(row.getJob().getName(), row.getNodeName()))
                .onSuccess(result -> setSchedule(row, result))
                .onFailure(throwable -> setSchedule(row, null))
                .taskListener(getView())
                .exec();
    }

    private void setSchedule(final JobNode jobNode, JobNodeInfo jobNodeInfo) {
        final Schedule currentSchedule = JobNodeUtil.getSchedule(jobNode);
        if (currentSchedule != null) {
            if (jobNodeInfo == null) {
                jobNodeInfo = JobNodeInfo.empty();
            }

            schedulePresenter.setSchedule(currentSchedule, new ScheduleReferenceTime(
                    jobNodeInfo.getScheduleReferenceTime(),
                    jobNodeInfo.getLastExecutedTime()));
            schedulePresenter.show(schedule -> {
                JobNodeUtil.setSchedule(jobNode, schedule);
                restFactory
                        .create(JOB_NODE_RESOURCE)
                        .call(res -> res.setSchedule(jobNode.getId(), schedule))
                        .onSuccess(result ->
                                dataProvider.refresh())
                        .taskListener(getView())
                        .exec();
            });
        }
    }

    public void read(final Job job) {
        if (jobName == null) {
            jobName = job.getName();
            dataProvider.addDataDisplay(dataGrid);
        } else {
            jobName = job.getName();
            dataProvider.refresh();
        }
    }

    private String getCurrentTaskCountAsStr(JobNode jobNode) {
        return GwtNullSafe.getOrElse(
                latestNodeInfo.get(jobNode),
                info -> ModelStringUtil.formatCsv(info.getCurrentTaskCount()),
                "?");
    }

    private String getLastExecutedTimeAsStr(JobNode jobNode) {
        if (GwtNullSafe.test(jobNode, jobNode2 ->
                jobNode2.getJobType() == JobType.CRON
                        || jobNode2.getJobType() == JobType.FREQUENCY)) {
            return GwtNullSafe.getOrElse(
                    latestNodeInfo.get(jobNode),
                    info -> dateTimeFormatter.formatWithDuration(info.getLastExecutedTime()),
                    "?");
        } else {
            return "N/A";
        }
    }

    private String getNextScheduledTimeAsStr(JobNode jobNode) {
        if (GwtNullSafe.test(jobNode, jobNode2 ->
                jobNode2.getJobType() == JobType.CRON
                        || jobNode2.getJobType() == JobType.FREQUENCY)) {
            return GwtNullSafe.getOrElse(
                    latestNodeInfo.get(jobNode),
                    info -> dateTimeFormatter.formatWithDuration(info.getNextScheduledTime()),
                    "?");
        } else {
            return "N/A";
        }
    }

    private void updateEnabledState(int rowIndex, JobNode jobNode, TickBoxState value) {
        final boolean isEnabled = GwtNullSafe.isTrue(value.toBoolean());
        jobNode.setEnabled(isEnabled);
        restFactory
                .create(JOB_NODE_RESOURCE)
                .call(jobNodeResource -> {
                    jobNodeResource.setEnabled(jobNode.getId(), isEnabled);
                })
                .onSuccess(aVoid -> {
                    // To update the Next Scheduled col
                    dataProvider.refresh();
                })
                .taskListener(getView())
                .exec();
    }
}
