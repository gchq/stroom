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

package stroom.job.client.component;

import stroom.cell.info.client.CommandLink;
import stroom.data.client.CriteriaUtil;
import stroom.data.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.job.client.event.JobChangeEvent;
import stroom.job.client.event.JobNodeChangeEvent;
import stroom.job.shared.FindJobNodeCriteria;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNodeAndInfo;
import stroom.job.shared.JobNodeAndInfoListResponse;
import stroom.job.shared.JobNodeResource;
import stroom.node.client.NodeClient;
import stroom.node.client.component.JobNodeActionsController;
import stroom.node.client.event.NodeChangeEvent;
import stroom.node.client.event.OpenNodeEvent;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// The bottom pane of the Jobs screen: a job's nodes ([JobNodeList]), each with its info asked of its
/// node, refreshed when they, the job or the nodes change elsewhere, or now and then if auto refresh
/// is on.
public class JobNodeListController extends Controller {

    private static final JobNodeResource JOB_NODE_RESOURCE = GWT.create(JobNodeResource.class);
    private static final int REDRAW_TIMER_DELAY_MS = 50;

    private final RestFactory restFactory;
    private final NodeClient nodeClient;
    private final DateTimeFormatter dateTimeFormatter;
    private final JobNodeActionsController actions;
    private final RestDataProvider<JobNodeAndInfo, JobNodeAndInfoListResponse> dataProvider;
    private final FindJobNodeCriteria findJobNodeCriteria = new FindJobNodeCriteria();
    private final DelayedUpdate redrawDelayedUpdate;

    private JobNodeList view;
    private boolean autoRefresh;

    /// Made by GIN; [#attach(JobNodeList)] gives it its list.
    ///
    /// @param eventBus          The application's event bus.
    /// @param restFactory       Finds the job nodes and their info, and sets their task limits.
    /// @param actionsProvider   Makes what does the job nodes' actions.
    /// @param dateTimeFormatter Formats the times shown.
    /// @param nodeClient        Lists the enabled nodes.
    @Inject
    public JobNodeListController(final EventBus eventBus,
                                 final RestFactory restFactory,
                                 final Provider<JobNodeActionsController> actionsProvider,
                                 final DateTimeFormatter dateTimeFormatter,
                                 final NodeClient nodeClient) {
        super(eventBus);
        this.restFactory = restFactory;
        this.nodeClient = nodeClient;
        this.dateTimeFormatter = dateTimeFormatter;

        redrawDelayedUpdate = new DelayedUpdate(REDRAW_TIMER_DELAY_MS, () -> view.redraw());
        onDispose(redrawDelayedUpdate::reset);
        dataProvider = buildDataProvider(eventBus);
        actions = make(actionsProvider);

        // NodeStatusListController may change a node
        listen(
                NodeChangeEvent.getType(), event -> {
                    // We are likely showing all jobs so just refresh
                    refreshNodeStates();
                });

        // JobListController may change a job
        listen(
                JobChangeEvent.getType(), event -> {
                    final String currentJobName = getJobNameCriteria();
                    final String affectedJobName = NullSafe.get(event, JobChangeEvent::getJob, Job::getName);
                    if (currentJobName != null && Objects.equals(currentJobName, affectedJobName)) {
                        internalRefresh();
                    }
                });

        // NodeJobListController may change a jobNode; this list's own changes are refreshed already
        listen(
                JobNodeChangeEvent.getType(), event -> {
                    if (event.getSource() != actions) {
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
    }

    /// Attaches this to a list, e.g. one a template declares, and asks which nodes are enabled.
    /// The list is then this controller's widget.
    ///
    /// @param view The list.
    /// @return This, for its owner to keep.
    public JobNodeListController attach(final JobNodeList view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);

        view.addColumns(dateTimeFormatter::formatWithDuration, this::buildOpenJobNodeCommandLink,
                this::setTaskLimit);
        actions.setList(view.getSelectionModel(), view.getTaskMonitor(), view::isNodeEnabled,
                this::internalRefresh);
        view.setOnEnabledChange(actions::setEnabled);
        view.setOnEditSchedule(actions::showSchedule);
        view.setOnShowActionMenu(actions::showActionMenu);
        view.setOnShowEnabledChange(this::internalRefresh);
        view.setOnAutoRefreshChange(this::onAutoRefreshToggled);
        view.setOnSortChange(this::internalRefresh);

        refreshNodeStates();
        return this;
    }

    private void refreshNodeStates() {
        nodeClient.listEnabledNodes(view::setEnabledNodeNames, throwable -> {
        }, view.getTaskMonitor());
    }

    private void onAutoRefreshToggled(final boolean on) {
        autoRefresh = on;
        if (autoRefresh) {
            internalRefresh();
        }
    }

    private RestDataProvider<JobNodeAndInfo, JobNodeAndInfoListResponse> buildDataProvider(
            final EventBus eventBus) {

        //noinspection Convert2Diamond
        return new RestDataProvider<JobNodeAndInfo, JobNodeAndInfoListResponse>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<JobNodeAndInfoListResponse> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CriteriaUtil.setSortList(findJobNodeCriteria, view.getSortList());

                // ON is show enabled only, OFF is show all states
                findJobNodeCriteria.setJobNodeEnabled(view.isShowEnabledOnly()
                        ? true
                        : null);
                restFactory
                        .create(JOB_NODE_RESOURCE)
                        .method(res ->
                                res.find(findJobNodeCriteria))
                        .onSuccess(dataConsumer)
                        .onFailure(errorHandler)
                        .owner(JobNodeListController.this)
                        .taskMonitorFactory(view.getTaskMonitor())
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
                                .owner(JobNodeListController.this)
                                .taskMonitorFactory(view.getTaskMonitor())
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

    private void setTaskLimit(final JobNodeAndInfo jobNodeAndInfo, final int taskLimit) {
        jobNodeAndInfo.setJobNode(jobNodeAndInfo.getJobNode()
                .copy()
                .taskLimit(taskLimit)
                .build());
        restFactory
                .create(JOB_NODE_RESOURCE)
                .call(res -> res.setTaskLimit(jobNodeAndInfo.getId(), taskLimit))
                .owner(this)
                .taskMonitorFactory(view.getTaskMonitor())
                .exec();
    }

    /// Finds the job nodes again, if auto refresh is on.
    public void refresh() {
        if (autoRefresh) {
            internalRefresh();
        }
    }

    private void internalRefresh() {
        updateFormGroupHeading();
        dataProvider.refresh();
    }

    /// Shows a job's nodes; the first time, starts the list.
    ///
    /// @param job The job, or `null` for none.
    public void read(final Job job) {
        if (dataProvider.getDataDisplays().isEmpty()) {
            view.setDataProvider(dataProvider);
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

    /// @param jobNode The job node to select, or `null` to select none.
    public void setSelected(final JobNode jobNode) {
        if (jobNode != null) {
            view.getSelectionModel().setSelected(JobNodeAndInfo.withoutInfo(jobNode));
        } else {
            view.getSelectionModel().clear();
        }
    }

    private CommandLink buildOpenJobNodeCommandLink(final JobNodeAndInfo jobNodeAndInfo) {
        if (jobNodeAndInfo != null) {
            final String nodeName = jobNodeAndInfo.getNodeName();
            final String jobName = jobNodeAndInfo.getJobName();
            return new CommandLink(
                    nodeName,
                    "Open node '" + nodeName + "' and job '" + jobName
                    + "' on the Nodes screen.",
                    () -> OpenNodeEvent.fire(this, jobNodeAndInfo.getJobNode()));
        } else {
            return null;
        }
    }

    private void updateFormGroupHeading() {
        final String jobName = getJobNameCriteria();
        final String stateStr = view.isShowEnabledOnly()
                ? "enabled"
                : "all";
        view.setHeading(NullSafe.getOrElse(
                jobName,
                name -> "Scheduling of job '" + jobName + "' on " + stateStr + " nodes",
                null));
    }
}
