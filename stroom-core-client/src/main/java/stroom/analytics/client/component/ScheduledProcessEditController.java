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

package stroom.analytics.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleResource;
import stroom.analytics.shared.ReportDoc;
import stroom.analytics.shared.ScheduleBounds;
import stroom.config.global.client.presenter.ConfigDefaultSetter;
import stroom.config.global.shared.ConfigTarget;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.job.shared.ScheduleReferenceTime;
import stroom.job.shared.ScheduleRestriction;
import stroom.node.client.NodeClient;
import stroom.schedule.client.ScheduleBoxController;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.component.UserRefSelectionController;
import stroom.security.shared.FindUserContext;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.AbstractAnalyticUiDefaultConfig;
import stroom.util.shared.NullSafe;
import stroom.util.shared.scheduler.ScheduleType;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.datepicker.client.DateTimeBoxController;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// Creates or edits an analytic's or report's schedule ([ScheduledProcessEdit]) in a dialog its
/// owner keeps and shows again.
public class ScheduledProcessEditController extends Controller {

    private static final ExecutionScheduleResource EXECUTION_SCHEDULE_RESOURCE =
            GWT.create(ExecutionScheduleResource.class);

    private final ScheduledProcessEdit view;
    private final ScheduleBoxController scheduleBox;
    private final UserRefSelectionController runAsUser;
    private final ClientSecurityContext clientSecurityContext;
    private final UiConfigCache uiConfigCache;
    private final ConfigDefaultSetter configDefaultSetter;
    private ExecutionSchedule executionSchedule;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                      The application's event bus.
    /// @param view                          The content.
    /// @param nodeClient                    Lists the nodes.
    /// @param scheduleBoxControllerProvider Makes the schedule box's controller.
    /// @param dateTimeBoxControllerProvider Makes the start and end boxes' controllers.
    /// @param restFactory                   Fetches when the schedule last ran.
    /// @param runAsUserProvider             Makes the Run As User picker's controller.
    /// @param clientSecurityContext         Gives the user, who runs a new schedule.
    /// @param uiConfigCache                 Gives the default node.
    /// @param configDefaultSetter           Sets the default node.
    @Inject
    public ScheduledProcessEditController(final EventBus eventBus,
                                          final ScheduledProcessEdit view,
                                          final NodeClient nodeClient,
                                          final Provider<ScheduleBoxController> scheduleBoxControllerProvider,
                                          final Provider<DateTimeBoxController> dateTimeBoxControllerProvider,
                                          final RestFactory restFactory,
                                          final Provider<UserRefSelectionController> runAsUserProvider,
                                          final ClientSecurityContext clientSecurityContext,
                                          final UiConfigCache uiConfigCache,
                                          final ConfigDefaultSetter configDefaultSetter) {
        super(eventBus);
        this.view = view;
        this.clientSecurityContext = clientSecurityContext;
        this.uiConfigCache = uiConfigCache;
        this.configDefaultSetter = configDefaultSetter;
        initWidget(view);

        runAsUser = make(runAsUserProvider);
        runAsUser.attach(view.getRunAsUserBox());
        runAsUser.setContext(FindUserContext.RUN_AS);

        // Only an administrator can change a global property, so don't offer it to anyone else.
        view.setSetDefaultVisible(configDefaultSetter.isAllowed());
        view.setOnSetDefaultNode(this::onSetDefaultNode);
        make(dateTimeBoxControllerProvider).attach(view.getStartTimeBox());
        make(dateTimeBoxControllerProvider).attach(view.getEndTimeBox());

        scheduleBox = make(scheduleBoxControllerProvider).attach(view.getScheduleBox());
        scheduleBox.setScheduleRestriction(new ScheduleRestriction(false, false, true));
        scheduleBox.setScheduleReferenceTimeSupplier(scheduleReferenceTimeConsumer -> restFactory
                .create(EXECUTION_SCHEDULE_RESOURCE)
                .method(res -> res.fetchTracker(executionSchedule))
                .onSuccess(tracker -> {
                    Long lastExecuted = null;
                    if (tracker != null) {
                        lastExecuted = tracker.getLastEffectiveExecutionTimeMs();
                    }
                    Long referenceTime = lastExecuted;
                    if (referenceTime == null) {
                        referenceTime = view.getStartTimeBox().getValue();
                    }
                    if (referenceTime == null) {
                        referenceTime = System.currentTimeMillis();
                    }
                    scheduleReferenceTimeConsumer.accept(new ScheduleReferenceTime(referenceTime, lastExecuted));
                })
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec());

        nodeClient.listAllNodes(
                guard(list -> {
                    if (NullSafe.hasItems(list)) {
                        view.setNodes(list);
                    }
                }),
                throwable -> AlertEvent.fireError(this, "Error", throwable.getMessage(), null),
                taskMonitor());
    }

    /// Shows a schedule in the dialog.
    ///
    /// @param executionSchedule The schedule, new (no UUID) or saved.
    /// @param consumer          Given the edited schedule on OK.
    public void show(final ExecutionSchedule executionSchedule,
                     final Consumer<ExecutionSchedule> consumer) {
        read(executionSchedule);

        final Size width = Size.builder().max(1000).resizable(true).build();
        final Size height = Size.builder().build();
        ControllerDialog.show(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.builder().width(width).height(height).build())
                .caption(executionSchedule.getUuid() == null
                        ? "Create Schedule"
                        : "Edit Schedule")
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        write(written -> {
                            consumer.accept(written);
                            e.hide();
                        }, e);
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    private void read(final ExecutionSchedule executionSchedule) {
        this.executionSchedule = executionSchedule;
        view.setName(executionSchedule.getName());
        view.setEnabled(executionSchedule.isEnabled());
        setNode(executionSchedule);
        setScheduleBounds(executionSchedule.getScheduleBounds());
        view.getScheduleBox().setValue(executionSchedule.getSchedule());
        runAsUser.setSelected(executionSchedule.getRunAsUser() == null
                ? clientSecurityContext.getUserRef()
                : executionSchedule.getRunAsUser());
    }

    private void write(final Consumer<ExecutionSchedule> consumer,
                       final HidePopupRequestEvent event) {
        final DateTimeBox startTime = view.getStartTimeBox();
        final DateTimeBox endTime = view.getEndTimeBox();
        scheduleBox.validate(scheduledTimes -> {
            if (scheduledTimes == null) {
                event.reset();
            } else if (scheduledTimes.isError()) {
                AlertEvent.fireWarn(this, scheduledTimes.getError(), event::reset);
            } else if (!startTime.isValid()
                       && !scheduledTimes.getSchedule().getType().equals(ScheduleType.INSTANT)) {
                AlertEvent.fireWarn(this, "Invalid start time", event::reset);
            } else if (!endTime.isValid()
                       && !scheduledTimes.getSchedule().getType().equals(ScheduleType.INSTANT)) {
                AlertEvent.fireWarn(this, "Invalid end time", event::reset);
            } else {
                final ScheduleBounds scheduleBounds = new ScheduleBounds(startTime.getValue(), endTime.getValue());
                consumer.accept(executionSchedule
                        .copy()
                        .name(view.getName())
                        .enabled(view.isEnabled())
                        .nodeName(view.getNode())
                        .schedule(scheduledTimes.getSchedule())
                        .contiguous(true)
                        .scheduleBounds(scheduleBounds)
                        .runAsUser(runAsUser.getSelected())
                        .build());
            }
        });
    }

    private void onSetDefaultNode() {
        configDefaultSetter.setDefault(
                this,
                getConfigTarget(),
                AbstractAnalyticUiDefaultConfig.PROP_NAME_DEFAULT_NODE,
                view.getNode(),
                "processing node",
                taskMonitor());
    }

    // Reports and analytic rules keep their defaults separately
    private ConfigTarget getConfigTarget() {
        final DocRef owningDoc = NullSafe.get(executionSchedule, ExecutionSchedule::getOwningDoc);
        return owningDoc != null && ReportDoc.TYPE.equals(owningDoc.getType())
                ? ConfigTarget.REPORT_UI_DEFAULT
                : ConfigTarget.ANALYTIC_UI_DEFAULT;
    }

    // A new schedule has no node yet. Seed it with the configured default rather than leaving the
    // view to select whichever node happens to be first in the list, which is arbitrary and is why
    // the default node property had no effect on scheduled rules.
    private void setNode(final ExecutionSchedule executionSchedule) {
        if (executionSchedule.getNodeName() != null) {
            view.setNode(executionSchedule.getNodeName());
        } else {
            uiConfigCache.get(guard(extendedUiConfig -> {
                if (extendedUiConfig != null) {
                    // Reports and analytic rules have their own defaults. A null default is ignored
                    // by the view, which then falls back to its own behaviour.
                    final DocRef owningDoc = executionSchedule.getOwningDoc();
                    final AbstractAnalyticUiDefaultConfig config =
                            owningDoc != null && ReportDoc.TYPE.equals(owningDoc.getType())
                                    ? extendedUiConfig.getReportUiDefaultConfig()
                                    : extendedUiConfig.getAnalyticUiDefaultConfig();
                    view.setNode(config.getDefaultNode());
                }
            }), taskMonitor());
        }
    }

    private void setScheduleBounds(final ScheduleBounds scheduleBounds) {
        view.getStartTimeBox().setValue(scheduleBounds == null
                ? null
                : scheduleBounds.getStartTimeMs());
        view.getEndTimeBox().setValue(scheduleBounds == null
                ? null
                : scheduleBounds.getEndTimeMs());
    }

}
