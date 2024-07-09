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

import stroom.cell.info.client.CommandLink;
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
import stroom.job.shared.JobNodeAndInfo;
import stroom.job.shared.JobNodeAndInfoListResponse;
import stroom.job.shared.JobNodeResource;
import stroom.monitoring.client.NodeMonitoringPlugin;
import stroom.node.client.JobNodeListHelper;
import stroom.node.client.event.JobNodeChangeEvent;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.Timer;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Bottom pane of JobPresenter (Jobs tab). Lists jobNodes for a single parent job.
 */
public class JobNodeListPresenter extends MyPresenterWidget<PagerView> {

    private static final JobNodeResource JOB_NODE_RESOURCE = GWT.create(JobNodeResource.class);
    private static final int REDRAW_TIMER_DELAY_MS = 50;

    private final Provider<NodeMonitoringPlugin> nodeMonitoringPluginProvider;
    private final MultiSelectionModelImpl<JobNodeAndInfo> selectionModel;
    private final RestFactory restFactory;
    private final JobNodeListHelper jobNodeListHelper;

    private final RestDataProvider<JobNodeAndInfo, JobNodeAndInfoListResponse> dataProvider;

    private final MyDataGrid<JobNodeAndInfo> dataGrid;
    private final FindJobNodeCriteria findJobNodeCriteria = new FindJobNodeCriteria();
    private final InlineSvgToggleButton showEnabledToggleBtn;

    private final Timer redrawTimer = new Timer() {
        @Override
        public void run() {
            dataGrid.redraw();
        }
    };

    @Inject
    public JobNodeListPresenter(final Provider<NodeMonitoringPlugin> nodeMonitoringPluginProvider,
                                final EventBus eventBus,
                                final PagerView view,
                                final RestFactory restFactory,
                                final JobNodeListHelper jobNodeListHelper) {
        super(eventBus, view);
        this.nodeMonitoringPluginProvider = nodeMonitoringPluginProvider;
        this.restFactory = restFactory;
        this.jobNodeListHelper = jobNodeListHelper;

        dataGrid = new MyDataGrid<>();
        dataGrid.addDefaultSelectionModel(true);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        showEnabledToggleBtn = JobNodeListHelper.buildJobFilterButton(this::refresh);
        view.addButton(showEnabledToggleBtn);

        initTable();

        dataProvider = buildDataProvider(eventBus, view, restFactory);
    }

    @Override
    protected void onBind() {
        super.onBind();

        // NodeJobListPresenter may change a jobNode
        registerHandler(getEventBus().addHandler(
                JobNodeChangeEvent.getType(), event -> {
//                    GWT.log(JobNodeListPresenter.this.getClass().getSimpleName()
//                            + " - jobNodes changed: " + event.getJobNodes().size());
                    if (!Objects.equals(event.getSource().getClass(), JobNodeListPresenter.this.getClass())) {
                        final Set<String> affectedJobNames = event.getJobNodes()
                                .stream()
                                .map(JobNode::getJobName)
                                .collect(Collectors.toSet());
                        final String currentJobName = getJobNameCriteria();
                        if (currentJobName != null && affectedJobNames.contains(currentJobName)) {
                            refresh();
                        }
                    }
                }));
    }

    private RestDataProvider<JobNodeAndInfo, JobNodeAndInfoListResponse> buildDataProvider(
            final EventBus eventBus,
            final PagerView view,
            final RestFactory restFactory) {

        //noinspection Convert2Diamond
        return new RestDataProvider<JobNodeAndInfo, JobNodeAndInfoListResponse>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<JobNodeAndInfoListResponse> dataConsumer,
                                final RestErrorHandler errorHandler) {

                // ON is show enabled only, OFF is show all states
                findJobNodeCriteria.setJobNodeEnabled(showEnabledToggleBtn.isOn()
                        ? true
                        : null);
                restFactory
                        .create(JOB_NODE_RESOURCE)
                        .method(res ->
                                res.find(findJobNodeCriteria))
                        .onSuccess(dataConsumer)
                        .onFailure(errorHandler)
                        .taskListener(view)
                        .exec();
            }

            @Override
            protected void changeData(final JobNodeAndInfoListResponse data) {
                // Ping each node to get its jobNodeInfo, which is held in-memory by the node
                data.getValues().forEach(jobNodeAndInfo -> {
                    // The node that gave us the data should have also given us the info for
                    // its jobNodes, so no need to hit that node again
                    if (jobNodeAndInfo.getJobNodeInfo() == null) {
                        restFactory
                                .create(JOB_NODE_RESOURCE)
                                .method(res ->
                                        res.info(jobNodeAndInfo.getJobName(), jobNodeAndInfo.getNodeName()))
                                .onSuccess(info -> {
                                    jobNodeAndInfo.setJobNodeInfo(info);
                                    super.changeData(data);
                                    scheduleDataGridRedraw();
                                })
                                .onFailure(throwable -> {
                                    jobNodeAndInfo.clearJobNodeInfo();
                                    super.changeData(data);
                                    scheduleDataGridRedraw();
                                })
                                .taskListener(getView())
                                .exec();
                    }
                });
                super.changeData(data);
            }
        };
    }

    private void scheduleDataGridRedraw() {
        // Saves the grid being redrawn for every single row in the list
        if (!redrawTimer.isRunning()) {
            redrawTimer.schedule(REDRAW_TIMER_DELAY_MS);
        }
    }

    private Number getTaskLimit(final JobNodeAndInfo jobNodeAndInfo) {
        return JobType.DISTRIBUTED.equals(jobNodeAndInfo.getJobType())
                ? new EditableInteger(jobNodeAndInfo.getTaskLimit())
                : null;
    }

    void refresh() {
        updateFormGroupHeading();
        dataProvider.refresh();
    }

    /**
     * Add the columns to the table.
     */
    private void initTable() {
        DataGridUtil.addColumnSortHandler(dataGrid, findJobNodeCriteria, this::refresh);

        // Enabled.
        dataGrid.addColumn(
                DataGridUtil.updatableTickBoxColumnBuilder(JobNodeAndInfo::isEnabled)
                        .enabledWhen(JobNodeListHelper::isJobNodeEnabled)
                        .withSorting(FindJobNodeCriteria.FIELD_ID_ENABLED)
                        .withFieldUpdater(jobNodeListHelper.createEnabledStateFieldUpdater(
                                JobNodeListPresenter.this, getView(), this::refresh))
                        .build(),
                DataGridUtil.headingBuilder("Enabled")
                        .withToolTip("Whether this job is enabled on this node or not. " +
                                "The parent job must also be enabled for the job to execute.")
                        .build(),
                60);

        // Node Name
        final Column<JobNodeAndInfo, CommandLink> nodeNameColumn = DataGridUtil.commandLinkColumnBuilder(
                        buildOpenJobNodeCommandLink())
                .enabledWhen(JobNodeListHelper::isJobNodeEnabled)
                .withSorting(FindJobNodeCriteria.FIELD_ID_NODE)
                .build();
        DataGridUtil.addCommandLinkFieldUpdater(nodeNameColumn);
        dataGrid.addResizableColumn(
                nodeNameColumn,
                DataGridUtil.headingBuilder("Node")
                        .withToolTip("The Stroom node the job runs on")
                        .build(),
                300);

        // Type
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(JobNodeListHelper::buildJobTypeStr)
                        .enabledWhen(JobNodeListHelper::isJobNodeEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Type")
                        .withToolTip("The type of the job")
                        .build(),
                80);

        // Schedule.
        final Column<JobNodeAndInfo, CommandLink> scheduleColumn = DataGridUtil.commandLinkColumnBuilder(
                        jobNodeListHelper.buildOpenScheduleCommandLinkFunc(
                                selectionModel,
                                getView(),
                                JobNodeListPresenter.this,
                                this::refresh))
                .enabledWhen(JobNodeListHelper::isJobNodeEnabled)
                .build();
        DataGridUtil.addCommandLinkFieldUpdater(scheduleColumn);
        dataGrid.addResizableColumn(
                scheduleColumn,
                DataGridUtil.headingBuilder("Schedule")
                        .withToolTip("The schedule for this job on this node, if applicable to the job type")
                        .build(),
                250);

        // Job Type Icon, always enabled, so you can edit schedule for disabled jobs
        dataGrid.addColumn(
                DataGridUtil.columnBuilder((JobNodeAndInfo jobNodeAndInfo) ->
                                        GwtNullSafe.requireNonNullElse(jobNodeAndInfo.getJobType(), JobType.UNKNOWN),
                                JobTypeCell::new)
                        .withBrowserEventHandler((context, elem, jobNode, event) -> {
                            if (jobNode != null && MouseUtil.isPrimary(event)) {
                                jobNodeListHelper.showSchedule(
                                        jobNode,
                                        selectionModel,
                                        getView(),
                                        JobNodeListPresenter.this,
                                        this::refresh);
                            }
                        })
                        .build(),
                DataGridUtil.headingBuilder("")
                        .build(),
                ColumnSizeConstants.ICON_COL);

        // Max.
        dataGrid.addColumn(
                DataGridUtil.valueSpinnerColumnBuilder(this::getTaskLimit, 1L, 9999L)
                        .enabledWhen(JobNodeListHelper::isJobNodeEnabled)
                        .withFieldUpdater((rowIndex, jobNodeAndInfo, value) -> {
                            if (jobNodeAndInfo != null) {
                                jobNodeAndInfo.getJobNode().setTaskLimit(value.intValue());
                                restFactory
                                        .create(JOB_NODE_RESOURCE)
                                        .call(res -> res.setTaskLimit(jobNodeAndInfo.getId(), value.intValue()))
                                        .taskListener(getView())
                                        .exec();
                            }
                        })
                        .build(),
                DataGridUtil.headingBuilder("Max Tasks")
                        .withToolTip("The task limit for this job on this node")
                        .build(),
                80);

        // Current Tasks (Cur).
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder(jobNodeListHelper::getCurrentTaskCountAsStr)
                        .enabledWhen(JobNodeListHelper::isJobNodeEnabled)
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("Current Tasks")
                        .withToolTip("The number of the currently executing tasks on this node for this job")
                        .build(),
                100);

        // Last executed.
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder(jobNodeListHelper::getLastExecutedTimeAsStr)
                        .enabledWhen(JobNodeListHelper::isJobNodeEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Last Executed")
                        .withToolTip("The date/time that this job was last executed on this node, " +
                                "if applicable to the job type.")
                        .build(),
                ColumnSizeConstants.DATE_AND_DURATION_COL);

        // Next scheduled
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder(jobNodeListHelper::getNextScheduledTimeAsStr)
                        .enabledWhen(JobNodeListHelper::isJobNodeEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Next Scheduled")
                        .withToolTip("The date/time that this job is next scheduled to execute on this node, " +
                                "if applicable to the job type.")
                        .build(),
                ColumnSizeConstants.DATE_AND_DURATION_COL);

        // Run now icon, always enabled, so you can run disabled jobs
        dataGrid.addColumn(
                DataGridUtil.svgPresetColumnBuilder(true, JobNodeListHelper::buildRunIconPreset)
                        .withBrowserEventHandler(jobNodeListHelper.createExecuteJobNowHandler(
                                JobNodeListPresenter.this,
                                getView()))
                        .build(),
                DataGridUtil.headingBuilder("Run")
                        .withToolTip("Execute the job on a node now.")
                        .build(), 40);

        DataGridUtil.addEndColumn(dataGrid);
    }

    public void read(final Job job) {
//        getView().setHeading(GwtNullSafe.get(job, Job::getName));
        if (dataProvider.getDataDisplays().isEmpty()) {
            dataProvider.addDataDisplay(dataGrid);
        }
        setJobNameCriteria(GwtNullSafe.get(job, Job::getName));
        refresh();
    }

    private String getJobNameCriteria() {
        return findJobNodeCriteria.getJobName().getString();
    }

    private void setJobNameCriteria(final String jobName) {
        findJobNodeCriteria.getJobName().setString(jobName);
    }

    public void setSelected(final JobNode jobNode) {
        if (jobNode != null) {
            selectionModel.setSelected(JobNodeAndInfo.withoutInfo(jobNode));
        } else {
            selectionModel.clear();
        }
    }

    private Function<JobNodeAndInfo, CommandLink> buildOpenJobNodeCommandLink() {
        return (JobNodeAndInfo jobNodeAndInfo) -> {
            if (jobNodeAndInfo != null) {
                final String nodeName = jobNodeAndInfo.getNodeName();
                final String jobName = jobNodeAndInfo.getJobName();
                return new CommandLink(
                        nodeName,
                        "Open node '" + nodeName + "' and job '" + jobName
                                + "' on the Nodes screen.",
                        () -> nodeMonitoringPluginProvider.get()
                                .open(jobNodeAndInfo.getJobNode()));
            } else {
                return null;
            }
        };
    }

    private void updateFormGroupHeading() {
        final String jobName = getJobNameCriteria();
        final boolean isShowEnabled = showEnabledToggleBtn.getState();
        final String stateStr = isShowEnabled
                ? "enabled"
                : "all";
        getView().setHeading(GwtNullSafe.getOrElse(
                jobName,
                name -> "Scheduling of job '" + jobName + "' on " + stateStr + " nodes",
                null));
    }

//    @Override
//    public HandlerRegistration addJobNodeChangeHandler(final Handler handler) {
//        return handler.onChange();
//    }
}
