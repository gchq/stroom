/*
 * Copyright 2024 Crown Copyright
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

package stroom.node.client.presenter;

import stroom.cell.info.client.CommandLink;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.job.client.event.JobChangeEvent;
import stroom.job.client.event.JobNodeChangeEvent;
import stroom.job.client.event.OpenJobNodeEvent;
import stroom.job.shared.FindJobNodeCriteria;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNodeAndInfo;
import stroom.job.shared.JobNodeAndInfoListResponse;
import stroom.job.shared.JobNodeResource;
import stroom.node.client.JobNodeListHelper;
import stroom.node.client.NodeClient;
import stroom.node.client.event.NodeChangeEvent;
import stroom.preferences.client.DateTimeFormatter;
import stroom.schedule.client.component.SchedulePickerController;
import stroom.svg.shared.SvgImage;
import stroom.util.client.DataGridUtil;
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
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Bottom pane of NodePresenter (Nodes tab). Lists jobNodes for a single node.
 */
public class NodeJobListPresenter extends MyPresenterWidget<PagerViewWithHeading> implements Refreshable {

    private static final JobNodeResource JOB_NODE_RESOURCE = GWT.create(JobNodeResource.class);

    private final JobNodeListHelper jobNodeListHelper;
    private final RestDataProvider<JobNodeAndInfo, JobNodeAndInfoListResponse> dataProvider;
    private final DataGrid<JobNodeAndInfo> dataGrid;
    private final FindJobNodeCriteria findJobNodeCriteria = new FindJobNodeCriteria();
    private final ToolbarToggleButton showEnabledToggleBtn;
    private final MultiSelectionModelImpl<JobNodeAndInfo> selectionModel;
    private final NodeClient nodeClient;
    private final ToolbarToggleButton autoRefreshButton;

    private boolean autoRefresh;

    @Inject
    public NodeJobListPresenter(final EventBus eventBus,
                                final PagerViewWithHeading view,
                                final RestFactory restFactory,
                                final Provider<SchedulePickerController> schedulePickerProvider,
                                final DateTimeFormatter dateTimeFormatter,
                                final NodeClient nodeClient) {
        super(eventBus, view);
        this.nodeClient = nodeClient;
        this.dataGrid = new DataGrid<>(this);
        this.dataGrid.setTableName("Node Jobs");
        this.dataGrid.setAccessibleName("Node jobs");
        this.selectionModel = dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);
        this.dataProvider = buildDataProvider(eventBus, view, restFactory);

        this.jobNodeListHelper = new JobNodeListHelper(
                dateTimeFormatter,
                restFactory,
                schedulePickerProvider,
                this,
                selectionModel,
                getView(),
                NodeJobListPresenter.this,
                this::internalRefresh);

        view.getToolbar().setName("Node job actions");
        this.showEnabledToggleBtn = jobNodeListHelper.addJobFilterButton(view.getToolbar());

        autoRefresh = false;
        autoRefreshButton = getView().getToolbar().toggle(SvgImage.AUTO_REFRESH, "Auto Refresh",
                pressed -> onAutoRefreshToggled());
        autoRefreshButton.setState(autoRefresh);

        // Must call this after initialising JobNodeListHelper
        initTable();
        refreshNodeStates();

        // NodeLisPresenter may change a node
        // Currently the node state does not affect how the job nodes are displayed
        listen(
                NodeChangeEvent.getType(), event -> {
                    refreshNodeStates();
                });

        // JobListPresenter may change a job
        listen(
                JobChangeEvent.getType(), event -> {
                    // We are likely showing all jobs so just refresh
                    internalRefresh();
                });

        // JobNodeListPresenter may change one or more jobNodes
        listen(
                JobNodeChangeEvent.getType(), event -> {
//                    GWT.log(NodeJobListPresenter.this.getClass().getSimpleName()
//                            + " - jobNodes changed: " + event.getJobNodes().size());
                    if (!Objects.equals(event.getSource().getClass(), NodeJobListPresenter.this.getClass())) {
                        final Set<String> affectedNodes = event.getJobNodes()
                                .stream()
                                .map(JobNode::getNodeName)
                                .collect(Collectors.toSet());
                        final String currentNodeName = getNodeNameCriteria();
                        if (currentNodeName != null && affectedNodes.contains(currentNodeName)) {
                            internalRefresh();
                        }
                    }
                });
    }

    private void refreshNodeStates() {
        nodeClient.listEnabledNodes(enabledNodeNames -> {
            jobNodeListHelper.setEnabledNodeNames(enabledNodeNames);
            // Redraw the grid in case any node states have changed which impacts enabled state of rows
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
                // We want the
                findJobNodeCriteria.setSort(FindJobNodeCriteria.FIELD_ADVANCED);
                findJobNodeCriteria.addSort(FindJobNodeCriteria.FIELD_JOB_NAME);
                // ON is show enabled only, OFF is show all states
                findJobNodeCriteria.setJobNodeEnabled(showEnabledToggleBtn.isOn()
                        ? true
                        : null);
                restFactory
                        .create(JOB_NODE_RESOURCE)
                        .method(resource ->
                                resource.find(findJobNodeCriteria))
                        .onSuccess(dataConsumer)
                        .onFailure(errorHandler)
//                        .onFailure(error -> {
//                            changeData(new JobNodeAndInfoListResponse(Collections.emptyList()));
//                            errorHandler.onError(error);
//                        })
                        .taskMonitorFactory(view)
                        .exec();
            }

            @Override
            protected void changeData(final JobNodeAndInfoListResponse data) {
                final List<JobNodeAndInfo> rtnList = new ArrayList<>();
                boolean addedGap = false;

                for (final JobNodeAndInfo jobNodeAndInfo : data.getValues()) {
                    rtnList.add(jobNodeAndInfo);
                    if (!addedGap && NullSafe.test(jobNodeAndInfo, JobNodeAndInfo::getJob, Job::isAdvanced)) {
                        // Add a gap between the non-advanced and advanced jobs
                        rtnList.add(null);
                        addedGap = true;
                    }
                }

                final JobNodeAndInfoListResponse modifiedData =
                        JobNodeAndInfoListResponse.createUnboundedResponse(rtnList);
                super.changeData(modifiedData);
            }
        };
    }

    public void read(final String nodeName) {
        if (dataProvider.getDataDisplays().isEmpty()) {
            dataProvider.addDataDisplay(dataGrid);
        }
        setNodeNameCriteria(nodeName);
        // Named for the node whose jobs it shows
        dataGrid.setAccessibleName(nodeName == null
                ? "Node jobs"
                : "Jobs on " + nodeName);
        internalRefresh();
    }

    private String getNodeNameCriteria() {
        return findJobNodeCriteria.getNodeName().getString();
    }

    private void setNodeNameCriteria(final String nodeName) {
        findJobNodeCriteria.getNodeName().setString(nodeName);
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
        jobNodeListHelper.addEnabledTickBoxColumn(dataGrid, false);

        // Job Name
        final Column<JobNodeAndInfo, CommandLink> jobNameColumn = DataGridUtil.commandLinkColumnBuilder(
                        this::openJobNodeAsCommandLink)
                .enabledWhen(jobNodeListHelper::isJobNodeEnabled)
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
                DataGridUtil.textColumnBuilder(JobNodeListHelper::buildParentJobEnabledStr)
                        .enabledWhen(jobNodeListHelper::isJobNodeEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Job State")
                        .withToolTip("Whether this job is enabled across all nodes or not. " +
                                "An enabled job must also be enabled on a node for it to execute on that node.")
                        .build(),
                80);

        // Node State
        jobNodeListHelper.addNodeStateColumn(dataGrid);
        // Type
        jobNodeListHelper.addTypeColumn(dataGrid);
        // Schedule.
        jobNodeListHelper.addScheduleColumn(dataGrid);
        // Last executed.
        jobNodeListHelper.addLastExecutedColumn(dataGrid);
        // Next executed.
        jobNodeListHelper.addNextExecutedColumn(dataGrid);
        // Action column
        jobNodeListHelper.addActionColumn(dataGrid);
    }

    private CommandLink openJobNodeAsCommandLink(final JobNodeAndInfo jobNodeAndInfo) {
        if (jobNodeAndInfo != null) {
            final String jobName = jobNodeAndInfo.getJobName();
            final String nodeName = jobNodeAndInfo.getNodeName();
            return new CommandLink(
                    jobName,
                    "Open job '" + jobName + "' on node '" + nodeName + "' on the Jobs screen.",
                    () -> OpenJobNodeEvent.fire(
                            NodeJobListPresenter.this,
                            jobNodeAndInfo.getJobNode()));
        } else {
            return null;
        }
    }

    public void setSelected(final JobNode jobNode) {
        if (jobNode != null) {
            selectionModel.setSelected(JobNodeAndInfo.withoutInfo(jobNode));
        } else {
            selectionModel.clear();
        }
    }

    private void updateFormGroupHeading() {
        final String nodeName = getNodeNameCriteria();
        final boolean isShowEnabled = showEnabledToggleBtn.getState();
        final String prefix = isShowEnabled
                ? "Enabled"
                : "All";
        final String heading = nodeName != null
                ? prefix + " jobs on node '" + nodeName + "'"
                : null;
        getView().setHeading(heading);
    }
}
