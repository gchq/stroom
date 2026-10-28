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

package stroom.node.client.component;

import stroom.cell.info.client.CommandLink;
import stroom.data.client.RestDataProvider;
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
import stroom.node.client.NodeClient;
import stroom.node.client.event.NodeChangeEvent;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// The bottom pane of the Nodes screen: a node's jobs ([NodeJobList]), the advanced ones last,
/// refreshed when they, any job or the nodes change elsewhere, or now and then if auto refresh is on.
public class NodeJobListController extends Controller {

    private static final JobNodeResource JOB_NODE_RESOURCE = GWT.create(JobNodeResource.class);

    private final NodeJobList view;
    private final NodeClient nodeClient;
    private final JobNodeActionsController actions;
    private final RestDataProvider<JobNodeAndInfo, JobNodeAndInfoListResponse> dataProvider;
    private final FindJobNodeCriteria findJobNodeCriteria = new FindJobNodeCriteria();

    private boolean autoRefresh;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus          The application's event bus.
    /// @param view              The content.
    /// @param restFactory       Finds the job nodes.
    /// @param actionsProvider   Makes what does the job nodes' actions.
    /// @param dateTimeFormatter Formats the times shown.
    /// @param nodeClient        Lists the enabled nodes.
    @Inject
    public NodeJobListController(final EventBus eventBus,
                                 final NodeJobList view,
                                 final RestFactory restFactory,
                                 final Provider<JobNodeActionsController> actionsProvider,
                                 final DateTimeFormatter dateTimeFormatter,
                                 final NodeClient nodeClient) {
        super(eventBus);
        this.view = view;
        this.nodeClient = nodeClient;
        initWidget(view);
        dataProvider = buildDataProvider(eventBus, restFactory);

        view.addColumns(dateTimeFormatter::formatWithDuration, this::openJobNodeAsCommandLink);
        actions = make(actionsProvider);
        actions.setList(view.getSelectionModel(), view.getTaskMonitor(), view::isNodeEnabled,
                this::internalRefresh);
        view.setOnEnabledChange(actions::setEnabled);
        view.setOnEditSchedule(actions::showSchedule);
        view.setOnShowActionMenu(actions::showActionMenu);
        view.setOnShowEnabledChange(this::internalRefresh);
        view.setOnAutoRefreshChange(this::onAutoRefreshToggled);

        refreshNodeStates();

        // NodeStatusListController may change a node
        // Currently the node state does not affect how the job nodes are displayed
        listen(
                NodeChangeEvent.getType(), event -> {
                    refreshNodeStates();
                });

        // JobListController may change a job
        listen(
                JobChangeEvent.getType(), event -> {
                    // We are likely showing all jobs so just refresh
                    internalRefresh();
                });

        // JobNodeListController may change one or more jobNodes; this list's own changes are
        // refreshed already
        listen(
                JobNodeChangeEvent.getType(), event -> {
                    if (event.getSource() != actions) {
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
            final EventBus eventBus,
            final RestFactory restFactory) {

        //noinspection Convert2Diamond
        return new RestDataProvider<JobNodeAndInfo, JobNodeAndInfoListResponse>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<JobNodeAndInfoListResponse> dataConsumer,
                                final RestErrorHandler errorHandler) {
                findJobNodeCriteria.setSort(FindJobNodeCriteria.FIELD_ADVANCED);
                findJobNodeCriteria.addSort(FindJobNodeCriteria.FIELD_JOB_NAME);
                // ON is show enabled only, OFF is show all states
                findJobNodeCriteria.setJobNodeEnabled(view.isShowEnabledOnly()
                        ? true
                        : null);
                restFactory
                        .create(JOB_NODE_RESOURCE)
                        .method(resource ->
                                resource.find(findJobNodeCriteria))
                        .onSuccess(dataConsumer)
                        .onFailure(errorHandler)
                        .owner(NodeJobListController.this)
                        .taskMonitorFactory(view.getTaskMonitor())
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

    /// Shows a node's jobs; the first time, starts the list.
    ///
    /// @param nodeName The node, or `null` for none.
    public void read(final String nodeName) {
        if (dataProvider.getDataDisplays().isEmpty()) {
            view.setDataProvider(dataProvider);
        }
        setNodeNameCriteria(nodeName);
        // Named for the node whose jobs it shows
        view.setNodeName(nodeName);
        internalRefresh();
    }

    private String getNodeNameCriteria() {
        return findJobNodeCriteria.getNodeName().getString();
    }

    private void setNodeNameCriteria(final String nodeName) {
        findJobNodeCriteria.getNodeName().setString(nodeName);
    }

    /// Finds the node's jobs again, if auto refresh is on.
    public void refresh() {
        if (autoRefresh) {
            internalRefresh();
        }
    }

    private void internalRefresh() {
        updateFormGroupHeading();
        dataProvider.refresh();
    }

    private CommandLink openJobNodeAsCommandLink(final JobNodeAndInfo jobNodeAndInfo) {
        if (jobNodeAndInfo != null) {
            final String jobName = jobNodeAndInfo.getJobName();
            final String nodeName = jobNodeAndInfo.getNodeName();
            return new CommandLink(
                    jobName,
                    "Open job '" + jobName + "' on node '" + nodeName + "' on the Jobs screen.",
                    () -> OpenJobNodeEvent.fire(this, jobNodeAndInfo.getJobNode()));
        } else {
            return null;
        }
    }

    /// @param jobNode The job node to select, or `null` to select none.
    public void setSelected(final JobNode jobNode) {
        if (jobNode != null) {
            view.getSelectionModel().setSelected(JobNodeAndInfo.withoutInfo(jobNode));
        } else {
            view.getSelectionModel().clear();
        }
    }

    private void updateFormGroupHeading() {
        final String nodeName = getNodeNameCriteria();
        final String prefix = view.isShowEnabledOnly()
                ? "Enabled"
                : "All";
        final String heading = nodeName != null
                ? prefix + " jobs on node '" + nodeName + "'"
                : null;
        view.setHeading(heading);
    }
}
