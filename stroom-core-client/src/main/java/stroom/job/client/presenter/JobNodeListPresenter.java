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

package stroom.job.client.presenter;

import stroom.cell.info.client.CommandLink;
import stroom.cell.valuespinner.shared.EditableInteger;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerViewWithHeading;
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
import stroom.node.client.NodeManager;
import stroom.node.client.event.NodeChangeEvent;
import stroom.node.client.event.OpenNodeEvent;
import stroom.preferences.client.DateTimeFormatter;
import stroom.schedule.client.SchedulePopup;
import stroom.svg.shared.SvgImage;
import stroom.util.client.DataGridUtil;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.menu.client.presenter.MenuPresenter;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
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
public class JobNodeListPresenter extends MyPresenterWidget<PagerViewWithHeading> implements Refreshable {

    private static final JobNodeResource JOB_NODE_RESOURCE = GWT.create(JobNodeResource.class);
    private static final int REDRAW_TIMER_DELAY_MS = 50;
    private static final String AUTO_REFRESH_ON_TITLE = "Turn Auto Refresh Off";
    private static final String AUTO_REFRESH_OFF_TITLE = "Turn Auto Refresh On";

    private final MultiSelectionModelImpl<JobNodeAndInfo> selectionModel;
    private final RestFactory restFactory;
    private final JobNodeListHelper jobNodeListHelper;

    private final RestDataProvider<JobNodeAndInfo, JobNodeAndInfoListResponse> dataProvider;

    private final MyDataGrid<JobNodeAndInfo> dataGrid;
    private final FindJobNodeCriteria findJobNodeCriteria = new FindJobNodeCriteria();
    private final InlineSvgToggleButton showEnabledToggleBtn;

    private final DelayedUpdate redrawDelayedUpdate;
    private final NodeManager nodeManager;
    private final InlineSvgToggleButton autoRefreshButton;

    private boolean autoRefresh;

    @Inject
    public JobNodeListPresenter(final EventBus eventBus,
                                final PagerViewWithHeading view,
                                final RestFactory restFactory,
                                final SchedulePopup schedulePresenter,
                                final MenuPresenter menuPresenter,
                                final DateTimeFormatter dateTimeFormatter,
                                final NodeManager nodeManager) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.nodeManager = nodeManager;

        this.dataGrid = new MyDataGrid<>(this);
        this.dataGrid.addDefaultSelectionModel(true);
        this.redrawDelayedUpdate = new DelayedUpdate(REDRAW_TIMER_DELAY_MS, dataGrid::redraw);
        this.selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        this.dataProvider = buildDataProvider(eventBus, view, restFactory);
        this.jobNodeListHelper = new JobNodeListHelper(
                dateTimeFormatter,
                restFactory,
                schedulePresenter,
                menuPresenter,
                selectionModel,
                getView(),
                JobNodeListPresenter.this,
                this::internalRefresh);

        this.showEnabledToggleBtn = jobNodeListHelper.buildJobFilterButton();
        view.addButton(showEnabledToggleBtn);

        autoRefresh = false;
        autoRefreshButton = new InlineSvgToggleButton();
        autoRefreshButton.setSvg(SvgImage.AUTO_REFRESH);
        autoRefreshButton.setTitle(AUTO_REFRESH_OFF_TITLE);
        autoRefreshButton.setState(autoRefresh);
        getView().addButton(autoRefreshButton);


        // Must call this after initialising JobNodeListHelper
        initTable();
        refreshNodeStates();
    }

    private void refreshNodeStates() {
        nodeManager.listEnabledNodes(enabledNodeNames -> {
            jobNodeListHelper.setEnabledNodeNames(enabledNodeNames);
            // Redraw the grid in case any node states have changed which impacts enabled state of rows.
            // Don't need to refresh as the grid doesn't use the node table
            dataGrid.redraw();
        }, throwable -> {
        }, getView());
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(autoRefreshButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                autoRefresh = !autoRefresh;
                if (autoRefresh) {
                    autoRefreshButton.setTitle(AUTO_REFRESH_ON_TITLE);
                    internalRefresh();
                } else {
                    autoRefreshButton.setTitle(AUTO_REFRESH_OFF_TITLE);
                }
            }
        }));

        // NodeLisPresenter may change a node
        registerHandler(getEventBus().addHandler(
                NodeChangeEvent.getType(), event -> {
                    // We are likely showing all jobs so just refresh
                    refreshNodeStates();
                }));

        // JobListPresenter may change a job
        registerHandler(getEventBus().addHandler(
                JobChangeEvent.getType(), event -> {
//                    GWT.log("Handling JobChangeEvent " + event);
                    final String currentJobName = getJobNameCriteria();
                    final String affectedJobName = NullSafe.get(event, JobChangeEvent::getJob, Job::getName);
                    if (currentJobName != null && Objects.equals(currentJobName, affectedJobName)) {
                        internalRefresh();
                    }
                }));

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
                            internalRefresh();
                        }
                    }
                }));

        registerHandler(dataGrid.addColumnSortHandler(event -> internalRefresh()));
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
                DataGridUtil.valueSpinnerColumnBuilder(this::getTaskLimit, 1L, 9999L)
                        .enabledWhen(jobNodeListHelper::isJobNodeEnabled)
                        .withFieldUpdater((rowIndex, jobNodeAndInfo, value) -> {
                            if (jobNodeAndInfo != null) {
                                jobNodeAndInfo.getJobNode().setTaskLimit(value.intValue());
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
                80);

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
                100);

        // Last executed.
        jobNodeListHelper.addLastExecutedColumn(dataGrid);

        // Next scheduled
        jobNodeListHelper.addNextExecutedColumn(dataGrid);

        // Action column
        jobNodeListHelper.addActionColumn(dataGrid);

        DataGridUtil.addEndColumn(dataGrid);
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
