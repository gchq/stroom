/*
 * Copyright 2019 Crown Copyright
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
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.job.client.event.JobChangeEvent;
import stroom.job.client.event.JobNodeChangeEvent;
import stroom.job.shared.FindJobNodeCriteria;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNode.JobType;
import stroom.job.shared.JobNodeAndInfo;
import stroom.job.shared.JobNodeAndInfoListResponse;
import stroom.job.shared.JobNodeResource;
import stroom.node.client.JobNodeListHelper;
import stroom.node.client.NodeClient;
import stroom.node.client.event.NodeChangeEvent;
import stroom.node.client.event.OpenNodeEvent;
import stroom.preferences.client.DateTimeFormatter;
import stroom.schedule.client.SchedulePopup;
import stroom.svg.shared.SvgImage;
import stroom.util.client.DataGridUtil;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ToolbarToggleButton;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerViewWithHeading;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Bottom pane of JobPresenter (Jobs tab). Lists jobNodes for a single parent job.
 */
public class JobNodeListPresenter extends MyPresenterWidget<PagerViewWithHeading> implements Refreshable {

    private static final JobNodeResource JOB_NODE_RESOURCE = GWT.create(JobNodeResource.class);
    private static final int REDRAW_TIMER_DELAY_MS = 50;

    private final MultiSelectionModelImpl<JobNodeAndInfo> selectionModel;
    private final RestFactory restFactory;
    private final JobNodeListHelper jobNodeListHelper;

    private final RestDataProvider<JobNodeAndInfo, JobNodeAndInfoListResponse> dataProvider;

    private final DataGrid<JobNodeAndInfo> dataGrid;
    private final FindJobNodeCriteria findJobNodeCriteria = new FindJobNodeCriteria();
    private final ToolbarToggleButton showEnabledToggleBtn;

    private final DelayedUpdate redrawDelayedUpdate;
    private final NodeClient nodeClient;
    private final ToolbarToggleButton autoRefreshButton;

    private boolean autoRefresh;

    @Inject
    public JobNodeListPresenter(final EventBus eventBus,
                                final PagerViewWithHeading view,
                                final RestFactory restFactory,
                                final SchedulePopup schedulePresenter,
                                final DateTimeFormatter dateTimeFormatter,
                                final NodeClient nodeClient) {
        super(eventBus, view);
        own(schedulePresenter);
        this.restFactory = restFactory;
        this.nodeClient = nodeClient;

        this.dataGrid = new DataGrid<>(this);
        this.dataGrid.setTableName("Job Nodes");
        this.redrawDelayedUpdate = new DelayedUpdate(REDRAW_TIMER_DELAY_MS, dataGrid::redraw);
        this.selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        this.dataProvider = buildDataProvider(eventBus, view, restFactory);
        this.jobNodeListHelper = new JobNodeListHelper(
                dateTimeFormatter,
                restFactory,
                schedulePresenter,
                selectionModel,
                getView(),
                JobNodeListPresenter.this,
                this::internalRefresh);

        view.getToolbar().setName("Job node actions");
        this.showEnabledToggleBtn = jobNodeListHelper.addJobFilterButton(view.getToolbar());

        autoRefresh = false;
        autoRefreshButton = getView().getToolbar().toggle(SvgImage.AUTO_REFRESH, "Auto Refresh",
                pressed -> onAutoRefreshToggled());
        autoRefreshButton.setState(autoRefresh);


        // Must call this after initialising JobNodeListHelper
        initTable();
        refreshNodeStates();

        // NodeListPresenter may change a node
        listen(
                NodeChangeEvent.getType(), event -> {
                    // We are likely showing all jobs so just refresh
                    refreshNodeStates();
                });

        // JobListPresenter may change a job
        listen(
                JobChangeEvent.getType(), event -> {
//                    GWT.log("Handling JobChangeEvent " + event);
                    final String currentJobName = getJobNameCriteria();
                    final String affectedJobName = NullSafe.get(event, JobChangeEvent::getJob, Job::getName);
                    if (currentJobName != null && Objects.equals(currentJobName, affectedJobName)) {
                        internalRefresh();
                    }
                });

        // NodeJobListPresenter may change a jobNode
        listen(
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
                            internalRefresh();
                        }
                    }
                });

        own(dataGrid.addColumnSortHandler(event -> internalRefresh()));
    }

    private void refreshNodeStates() {
        nodeClient.listEnabledNodes(enabledNodeNames -> {
            jobNodeListHelper.setEnabledNodeNames(enabledNodeNames);
            // Redraw the grid in case any node states have changed which impacts enabled state of rows.
            // Don't need to refresh as the grid doesn't use the node table
            dataGrid.redraw();
        }, throwable -> {
        }, getView());
    }

    private void onAutoRefreshToggled() {
        autoRefresh = !autoRefresh;
        if (autoRefresh) {
            internalRefresh();
        }
    }

    private RestDataProvider<JobNodeAndInfo, JobNodeAndInfoListResponse> buildDataProvider(
            final EventBus eventBus,
            final PagerViewWithHeading view,
            final RestFactory restFactory) {

        //noinspection Convert2Diamond
        return new RestDataProvider<JobNodeAndInfo, JobNodeAndInfoListResponse>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<JobNodeAndInfoListResponse> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CriteriaUtil.setSortList(findJobNodeCriteria, dataGrid.getColumnSortList());

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
                        .taskMonitorFactory(view)
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
                                    scheduleDataGridRedraw();
                                })
                                .onFailure(throwable -> {
                                    jobNodeAndInfo.clearJobNodeInfo();
                                    scheduleDataGridRedraw();
                                })
                                .taskMonitorFactory(getView())
                                .exec();
                    }
                });
                super.changeData(data);
            }
        };
    }

    private void scheduleDataGridRedraw() {
        // Saves the grid being redrawn for every single row in the list
        redrawDelayedUpdate.update();
    }

    private Number getTaskLimit(final JobNodeAndInfo jobNodeAndInfo) {
        return JobType.DISTRIBUTED.equals(jobNodeAndInfo.getJobType())
                ? new EditableInteger(jobNodeAndInfo.getTaskLimit())
                : null;
    }

    @Override
    public void refresh() {
        if (autoRefresh) {
            internalRefresh();
        }
    }

    private void internalRefresh() {
        updateFormGroupHeading();
        dataProvider.refresh();
    }

    /**
     * Add the columns to the table.
     */
    private void initTable() {
        // JobNode Enabled tick box
        jobNodeListHelper.addEnabledTickBoxColumn(dataGrid, true);

        // Node Name
        final Column<JobNodeAndInfo, CommandLink> nodeNameColumn = DataGridUtil.commandLinkColumnBuilder(
                        buildOpenJobNodeCommandLink())
                .enabledWhen(jobNodeListHelper::isJobNodeEnabled)
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
        jobNodeListHelper.addNodeStateColumn(dataGrid);
        // Type
        jobNodeListHelper.addTypeColumn(dataGrid);
        // Schedule.
        jobNodeListHelper.addScheduleColumn(dataGrid);

        // Max.
        dataGrid.addColumn(
                DataGridUtil.valueSpinnerColumnBuilder("Max Tasks", this::getTaskLimit, 1L, 9999L)
                        .enabledWhen(jobNodeListHelper::isJobNodeEnabled)
                        .withFieldUpdater((rowIndex, jobNodeAndInfo, value) -> {
                            if (jobNodeAndInfo != null) {
                                jobNodeAndInfo.setJobNode(jobNodeAndInfo.getJobNode()
                                        .copy()
                                        .taskLimit(value.intValue())
                                        .build());
                                restFactory
                                        .create(JOB_NODE_RESOURCE)
                                        .call(res -> res.setTaskLimit(jobNodeAndInfo.getId(), value.intValue()))
                                        .taskMonitorFactory(getView())
                                        .exec();
                            }
                        })
                        .build(),
                DataGridUtil.headingBuilder("Max Tasks")
                        .withToolTip("The task limit for this job on this node")
                        .build(),
                ColumnSizeConstants.VALUE_SPINNER_COL);

        // Current Tasks (Cur).
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder(jobNodeListHelper::getCurrentTaskCountAsStr)
                        .enabledWhen(jobNodeListHelper::isJobNodeEnabled)
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("Current Tasks")
                        .rightAligned()
                        .withToolTip("The number of the currently executing tasks on this node for this job")
                        .build(),
                // Room for the heading and the space before Last Executed
                115);

        // Last executed.
        jobNodeListHelper.addLastExecutedColumn(dataGrid);

        // Next scheduled
        jobNodeListHelper.addNextExecutedColumn(dataGrid);

        // Action column
        jobNodeListHelper.addActionColumn(dataGrid);
    }

    public void read(final Job job) {
//        getView().setHeading(NullSafe.get(job, Job::getName));
        if (dataProvider.getDataDisplays().isEmpty()) {
            dataProvider.addDataDisplay(dataGrid);
        }
        setJobNameCriteria(NullSafe.get(job, Job::getName));
        internalRefresh();
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
        return (final JobNodeAndInfo jobNodeAndInfo) -> {
            if (jobNodeAndInfo != null) {
                final String nodeName = jobNodeAndInfo.getNodeName();
                final String jobName = jobNodeAndInfo.getJobName();
                return new CommandLink(
                        nodeName,
                        "Open node '" + nodeName + "' and job '" + jobName
                        + "' on the Nodes screen.",
                        () -> OpenNodeEvent.fire(
                                JobNodeListPresenter.this, jobNodeAndInfo.getJobNode()));
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
        getView().setHeading(NullSafe.getOrElse(
                jobName,
                name -> "Scheduling of job '" + jobName + "' on " + stateStr + " nodes",
                null));
    }
}
