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

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestFactory;
import stroom.job.client.event.JobNodeChangeEvent;
import stroom.job.shared.BatchScheduleRequest;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNode.JobType;
import stroom.job.shared.JobNodeAndInfo;
import stroom.job.shared.JobNodeInfo;
import stroom.job.shared.JobNodeResource;
import stroom.job.shared.JobNodeUtil;
import stroom.job.shared.ScheduleReferenceTime;
import stroom.schedule.client.component.SchedulePickerController;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitorFactory;
import stroom.task.client.event.OpenServerTasksEvent;
import stroom.util.shared.NullSafe;
import stroom.util.shared.scheduler.Schedule;
import stroom.widget.component.client.Controller;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.MenuBuilder;
import stroom.widget.menu.client.ShowMenuEvent;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/// Does what the lists of job nodes (the Jobs screen's and the Nodes screen's) ask: enables or
/// disables a job node, runs one now, edits the schedule of one or of all those selected, and shows a
/// job node's action menu. Each list makes its own, with `make`, and tells it about the list. The
/// changes are announced with [JobNodeChangeEvent], with this as their source.
public class JobNodeActionsController extends Controller {

    private static final JobNodeResource JOB_NODE_RESOURCE = GWT.create(JobNodeResource.class);

    private final RestFactory restFactory;
    private final Provider<SchedulePickerController> schedulePickerProvider;

    private MultiSelectionModel<JobNodeAndInfo> selectionModel;
    private TaskMonitorFactory taskMonitorFactory;
    private Predicate<String> isNodeEnabled = nodeName -> false;
    private Runnable onChange = () -> {
    };

    /// Made by GIN.
    ///
    /// @param eventBus               The application's event bus.
    /// @param restFactory            Changes the job nodes.
    /// @param schedulePickerProvider Makes the dialog that edits a schedule.
    @Inject
    public JobNodeActionsController(final EventBus eventBus,
                                    final RestFactory restFactory,
                                    final Provider<SchedulePickerController> schedulePickerProvider) {
        super(eventBus);
        this.restFactory = restFactory;
        this.schedulePickerProvider = schedulePickerProvider;
    }

    /// Tells this about the list it acts for.
    ///
    /// @param selectionModel     The list's selection: a schedule edited with several job nodes
    ///                           selected is set on all of them.
    /// @param taskMonitorFactory Spins the list's refresh button while a request runs.
    /// @param isNodeEnabled      Whether a node, by name, is enabled.
    /// @param onChange           Run when a job node's enabled state or schedule has changed.
    public void setList(final MultiSelectionModel<JobNodeAndInfo> selectionModel,
                        final TaskMonitorFactory taskMonitorFactory,
                        final Predicate<String> isNodeEnabled,
                        final Runnable onChange) {
        this.selectionModel = selectionModel;
        this.taskMonitorFactory = taskMonitorFactory;
        this.isNodeEnabled = isNodeEnabled;
        this.onChange = onChange;
    }

    /// Asks whether to run a job node now, then asks its node to.
    ///
    /// @param jobNode The job node.
    public void executeJobNow(final JobNode jobNode) {
        ConfirmEvent.fire(this,
                "Are you sure you want to execute job '" + jobNode.getJobName()
                + "' on node '" + jobNode.getNodeName() + "' now. " +
                "\n\nThe job will execute shortly, likely within ten seconds. " +
                "You can check it has run by refreshing the table until the 'Last Executed' " +
                "column has been updated.",
                ok -> {
                    if (ok) {
                        restFactory.create(JOB_NODE_RESOURCE)
                                .call(resource -> resource.execute(jobNode.getId()))
                                .owner(this)
                                .taskMonitorFactory(taskMonitorFactory)
                                .exec();
                    }
                });
    }

    /// Edits a job node's schedule, or, with several selected, theirs.
    ///
    /// @param jobNodeAndInfo The job node.
    public void showSchedule(final JobNodeAndInfo jobNodeAndInfo) {
        restFactory
                .create(JOB_NODE_RESOURCE)
                .method(resource ->
                        resource.info(jobNodeAndInfo.getJob().getName(), jobNodeAndInfo.getNodeName()))
                .onSuccess(jobNodeInfo ->
                        setSchedule(jobNodeAndInfo, jobNodeInfo))
                .onFailure(throwable ->
                        setSchedule(jobNodeAndInfo, null))
                .owner(this)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /// Enables or disables a job node.
    ///
    /// @param jobNodeAndInfo The job node.
    /// @param isEnabled      Whether it is to be enabled.
    public void setEnabled(final JobNodeAndInfo jobNodeAndInfo, final boolean isEnabled) {
        restFactory
                .create(JOB_NODE_RESOURCE)
                .call(jobNodeResource ->
                        jobNodeResource.setEnabled(jobNodeAndInfo.getId(), isEnabled))
                .onSuccess(aVoid -> {
                    JobNodeChangeEvent.fire(this, jobNodeAndInfo.getJobNode());
                    onChange.run();
                })
                .owner(this)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /// Shows a job node's action menu, as Stroom's other menus are, so that it is named and takes
    /// the focus.
    ///
    /// @param jobNodeAndInfo The job node.
    /// @param popupPosition  Where to show it.
    /// @param opener         The button that opened it, or `null`.
    public void showActionMenu(final JobNodeAndInfo jobNodeAndInfo,
                               final PopupPosition popupPosition,
                               final Element opener) {
        ShowMenuEvent.builder()
                .name(JobNodeColumns.getActionMenuName(jobNodeAndInfo))
                .items(buildActionMenu(jobNodeAndInfo))
                .popupPosition(popupPosition)
                .opener(opener)
                .fire(this);
    }

    private List<Item> buildActionMenu(final JobNodeAndInfo jobNodeAndInfo) {
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

        if (isSchedulable && nodeName != null && isNodeEnabled.test(nodeName)) {
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
                        .command(() -> OpenServerTasksEvent.fire(
                                this,
                                jobNode.getNodeName(),
                                jobNode.getJobName())))
                .withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(SvgImage.JOBS)
                        .text("Show in Server Tasks (All Nodes)")
                        .command(() -> OpenServerTasksEvent.fire(this, jobNode.getJobName())))
                .build();
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
                            this, "You can only select rows with the same job type.", null);
                } else if (!Objects.equals(jobTypes.get(0), jobNode.getJobType())) {
                    AlertEvent.fireError(
                            this, "Job types don't match, '" + jobTypes.get(0) + "' and '"
                                  + jobNode.getJobType() + "'.", null);
                } else {
                    final String msg = "Are you sure you want to change the schedule of job '"
                                       + jobNode.getJobName() + "' for " + nodeCount + " nodes?\n\n" +
                                       "All of the following nodes will be set to the same schedule.\n\n"
                                       + selectedItems.stream()
                                               .map(JobNode::getNodeName)
                                               .sorted()
                                               .collect(Collectors.joining("\n"));

                    ConfirmEvent.fire(this, msg, isConfirm -> {
                        if (isConfirm) {
                            editSchedule(currentSchedule, referenceTime, schedule -> {
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
                                            JobNodeChangeEvent.fire(this, selectedItems);
                                            onChange.run();
                                        })
                                        .owner(this)
                                        .taskMonitorFactory(taskMonitorFactory)
                                        .exec();
                            });
                        }
                    });
                }
            } else {
                editSchedule(currentSchedule, referenceTime, schedule -> {
                    restFactory
                            .create(JOB_NODE_RESOURCE)
                            .call(resource ->
                                    resource.setSchedule(jobNodeAndInfo.getId(), schedule))
                            .onSuccess(result -> {
                                JobNodeChangeEvent.fire(this, jobNode);
                                onChange.run();
                            })
                            .owner(this)
                            .taskMonitorFactory(taskMonitorFactory)
                            .exec();
                });
            }
        }
    }

    private void editSchedule(final Schedule schedule,
                              final ScheduleReferenceTime referenceTime,
                              final Consumer<Schedule> onOk) {
        schedulePickerProvider.get().edit(this, schedule, referenceTime, onOk);
    }
}
