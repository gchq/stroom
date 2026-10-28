/*
 * Copyright 2026 Crown Copyright
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
import stroom.job.shared.ScheduleRestriction;
import stroom.node.client.NodeClient;
import stroom.schedule.client.ScheduleBoxController;
import stroom.security.client.component.UserRefSelectionController;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.datepicker.client.DateTimeBoxController;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// A change to make to many execution schedules ([BatchExecutionScheduleEdit]), in a dialog its
/// owner keeps and shows again. Its owner applies the change, to the selected or the filtered
/// schedules, when one of the dialog's buttons is pressed.
public class BatchExecutionScheduleEditController extends Controller {

    private final BatchExecutionScheduleEdit view;
    private final ScheduleBoxController scheduleBox;
    private final UserRefSelectionController runAsUser;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                      The application's event bus.
    /// @param view                          The content.
    /// @param nodeClient                    Lists the nodes.
    /// @param scheduleBoxControllerProvider Makes the schedule box's controller.
    /// @param dateTimeBoxControllerProvider Makes the start and end boxes' controllers.
    /// @param runAsUserProvider             Makes the Run As User picker's controller.
    @Inject
    public BatchExecutionScheduleEditController(final EventBus eventBus,
                                                final BatchExecutionScheduleEdit view,
                                                final NodeClient nodeClient,
                                                final Provider<ScheduleBoxController> scheduleBoxControllerProvider,
                                                final Provider<DateTimeBoxController> dateTimeBoxControllerProvider,
                                                final Provider<UserRefSelectionController> runAsUserProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);

        runAsUser = make(runAsUserProvider);
        runAsUser.attach(view.getRunAsUserBox());
        view.setOnRunAsUserEnabled(runAsUser::setEnabled);

        make(dateTimeBoxControllerProvider).attach(view.getStartTimeBox());
        make(dateTimeBoxControllerProvider).attach(view.getEndTimeBox());
        scheduleBox = make(scheduleBoxControllerProvider).attach(view.getScheduleBox());
        scheduleBox.setScheduleRestriction(new ScheduleRestriction(false, false, true));

        nodeClient.listAllNodes(
                guard(list -> {
                    if (NullSafe.hasItems(list)) {
                        view.setNodes(list);
                    }
                }),
                throwable -> AlertEvent.fireError(this, "Error", throwable.getMessage(), null),
                taskMonitor());
    }

    /// Shows the dialog.
    public void show() {
        final PopupSize popupSize = PopupSize.builder()
                .width(Size
                        .builder()
                        .resizable(true)
                        .build())
                .height(Size
                        .builder()
                        .resizable(true)
                        .build())
                .build();
        ControllerDialog.show(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .modal()
                .popupSize(popupSize)
                .caption("Batch Change Selected Schedules")
                .fire();
    }

    /// Checks the ticked schedule and times are valid, saying what is wrong if they aren't.
    ///
    /// @param consumer Told once whether they are valid.
    public void validate(final Consumer<Boolean> consumer) {
        final DateTimeBox startTime = view.getStartTimeBox();
        final DateTimeBox endTime = view.getEndTimeBox();
        scheduleBox.validate(scheduledTimes -> {
            if (scheduledTimes == null) {
                consumer.accept(false);
            } else if (view.getScheduleBox().isEnabled() && scheduledTimes.isError()) {
                AlertEvent.fireWarn(this, scheduledTimes.getError(), null);
                consumer.accept(false);
            } else if (startTime.isEnabled() && !startTime.isValid()) {
                AlertEvent.fireWarn(this, "Invalid start time", null);
                consumer.accept(false);
            } else if (endTime.isEnabled() && !endTime.isValid()) {
                AlertEvent.fireWarn(this, "Invalid end time", null);
                consumer.accept(false);
            } else {
                consumer.accept(true);
            }
        });
    }

    /// @return Whether any field is ticked, so there is something to change.
    public boolean isAnyBoxEnabled() {
        return view.isAnyBoxEnabled();
    }

    /// Changes a schedule's ticked fields.
    ///
    /// @param executionSchedule The schedule.
    /// @return A copy of the schedule with the ticked fields changed.
    public ExecutionSchedule getUpdatedExecutionSchedule(final ExecutionSchedule executionSchedule) {
        return view.getUpdatedExecutionSchedule(executionSchedule, runAsUser.getSelected());
    }

    /// @return What the ticked fields are changed to, one per line, for the confirmation.
    public String getEditSummary() {
        return view.getEditSummary(runAsUser.getSelected());
    }

    /// @param onApplySelection Called when Apply to Selection is pressed.
    public void setOnApplySelection(final Runnable onApplySelection) {
        view.setOnApplySelection(onApplySelection);
    }

    /// @param onApplyFiltered Called when Apply to Filtered is pressed.
    public void setOnApplyFiltered(final Runnable onApplyFiltered) {
        view.setOnApplyFiltered(onApplyFiltered);
    }

}
