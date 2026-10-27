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

import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ScheduleBounds;
import stroom.item.client.SelectionBox;
import stroom.schedule.client.ScheduleBox;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextBox;
import stroom.widget.tickbox.client.view.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.Consumer;

/// A change to make to many execution schedules: each field has a tick box saying whether to
/// change it, and the field is only enabled when ticked. The change is applied to the selected or
/// the filtered schedules, by one of two buttons.
public class BatchExecutionScheduleEdit extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox nameBox;
    @UiField
    FormGroup nameForm;
    @UiField
    TickBox nameEnable;
    @UiField
    TickBox enabledBox;
    @UiField
    FormGroup enabledForm;
    @UiField
    TickBox enabledEnable;
    @UiField
    SelectionBox<String> nodeBox;
    @UiField
    FormGroup nodeForm;
    @UiField
    TickBox nodeEnable;
    @UiField
    ScheduleBox scheduleBox;
    @UiField
    FormGroup scheduleForm;
    @UiField
    TickBox scheduleEnable;
    @UiField
    DateTimeBox startTimeBox;
    @UiField
    FormGroup startTimeForm;
    @UiField
    TickBox startTimeEnable;
    @UiField
    DateTimeBox endTimeBox;
    @UiField
    FormGroup endTimeForm;
    @UiField
    TickBox endTimeEnable;
    @UiField
    DropDownBox runAsUserBox;
    @UiField
    FormGroup runAsUserForm;
    @UiField
    TickBox runAsUserEnable;
    @UiField
    Button applySelectionButton;
    @UiField
    Button applyFilteredButton;

    private Consumer<Boolean> onRunAsUserEnabled = enabled -> {
    };
    private Runnable onApplySelection = () -> {
    };
    private Runnable onApplyFiltered = () -> {
    };

    /// Makes the form, nothing ticked.
    public BatchExecutionScheduleEdit() {
        initWidget(BINDER.createAndBindUi(this));
        nameEnable.addValueChangeHandler(event -> update());
        enabledEnable.addValueChangeHandler(event -> update());
        nodeEnable.addValueChangeHandler(event -> update());
        scheduleEnable.addValueChangeHandler(event -> update());
        startTimeEnable.addValueChangeHandler(event -> update());
        endTimeEnable.addValueChangeHandler(event -> update());
        runAsUserEnable.addValueChangeHandler(event -> update());
        scheduleBox.addValueChangeHandler(event -> update());
        update();
    }

    /// @return The schedule box, for its controller to attach to.
    public ScheduleBox getScheduleBox() {
        return scheduleBox;
    }

    /// @return The start time box, for its controller to attach to.
    public DateTimeBox getStartTimeBox() {
        return startTimeBox;
    }

    /// @return The end time box, for its controller to attach to.
    public DateTimeBox getEndTimeBox() {
        return endTimeBox;
    }

    /// @return The Run As User picker, for its controller to attach to.
    public DropDownBox getRunAsUserBox() {
        return runAsUserBox;
    }

    /// @param nodes The nodes a schedule can run on.
    public void setNodes(final List<String> nodes) {
        nodeBox.clear();
        nodeBox.addItems(nodes);
        if (!nodes.isEmpty()) {
            nodeBox.setValue(nodes.get(0));
        }
    }

    /// @return Whether any field is ticked, so there is something to change.
    public boolean isAnyBoxEnabled() {
        return nameEnable.getValue()
               || enabledEnable.getValue()
               || nodeEnable.getValue()
               || scheduleEnable.getValue()
               || startTimeEnable.getValue()
               || endTimeEnable.getValue()
               || runAsUserEnable.getValue();
    }

    /// Changes a schedule's ticked fields.
    ///
    /// @param executionSchedule The schedule.
    /// @param runAsUser         The run as user chosen.
    /// @return A copy of the schedule with the ticked fields changed.
    public ExecutionSchedule getUpdatedExecutionSchedule(final ExecutionSchedule executionSchedule,
                                                         final UserRef runAsUser) {
        final Long startTime = startTimeEnable.getValue()
                ? startTimeBox.getValue()
                : NullSafe.get(executionSchedule.getScheduleBounds(), ScheduleBounds::getStartTimeMs);
        final Long endTime = endTimeEnable.getValue()
                ? endTimeBox.getValue()
                : NullSafe.get(executionSchedule.getScheduleBounds(), ScheduleBounds::getEndTimeMs);
        return executionSchedule
                .copy()
                .name(nameEnable.getValue()
                        ? nameBox.getValue()
                        : executionSchedule.getName())
                .enabled(enabledEnable.getValue()
                        ? enabledBox.getValue()
                        : executionSchedule.isEnabled())
                .nodeName(nodeEnable.getValue()
                        ? nodeBox.getValue()
                        : executionSchedule.getNodeName())
                .schedule(scheduleEnable.getValue()
                        ? scheduleBox.getValue()
                        : executionSchedule.getSchedule())
                .contiguous(executionSchedule.isContiguous())
                .scheduleBounds(new ScheduleBounds(startTime, endTime))
                .runAsUser(runAsUserEnable.getValue()
                        ? runAsUser
                        : executionSchedule.getRunAsUser())
                .build();
    }

    /// Says what the ticked fields are changed to, one per line, for the confirmation.
    ///
    /// @param runAsUser The run as user chosen.
    /// @return The summary.
    public String getEditSummary(final UserRef runAsUser) {
        final StringBuilder sb = new StringBuilder();
        if (nameEnable.getValue()) {
            sb.append("Name to '").append(nameBox.getValue()).append("'\n");
        }
        if (enabledEnable.getValue()) {
            sb.append("Enabled to ").append(enabledBox.getValue()
                    ? "'true'"
                    : "'false'").append("\n");
        }
        if (nodeEnable.getValue()) {
            sb.append("Processing Node to '").append(nodeBox.getValue()).append("'\n");
        }
        if (scheduleEnable.getValue()) {
            sb.append("Schedule to '").append(scheduleBox.getValue()).append("'\n");
        }
        if (startTimeEnable.getValue()) {
            sb.append("Start Time to '").append(ClientDateUtil.toISOString(startTimeBox.getValue())).append("'\n");
        }
        if (endTimeEnable.getValue()) {
            sb.append("End Time to '").append(ClientDateUtil.toISOString(endTimeBox.getValue())).append("'\n");
        }
        if (runAsUserEnable.getValue()) {
            sb.append("Run As User to '").append(runAsUser).append("'\n");
        }
        return sb.toString();
    }

    /// @param onRunAsUserEnabled Given whether the run as user is ticked, when it changes.
    public void setOnRunAsUserEnabled(final Consumer<Boolean> onRunAsUserEnabled) {
        this.onRunAsUserEnabled = onRunAsUserEnabled;
        onRunAsUserEnabled.accept(runAsUserEnable.getValue());
    }

    /// @param onApplySelection Called when Apply to Selection is pressed.
    public void setOnApplySelection(final Runnable onApplySelection) {
        this.onApplySelection = onApplySelection;
    }

    /// @param onApplyFiltered Called when Apply to Filtered is pressed.
    public void setOnApplyFiltered(final Runnable onApplyFiltered) {
        this.onApplyFiltered = onApplyFiltered;
    }

    @UiHandler("applySelectionButton")
    void onApplySelection(final ClickEvent event) {
        onApplySelection.run();
    }

    @UiHandler("applyFilteredButton")
    void onApplyFiltered(final ClickEvent event) {
        onApplyFiltered.run();
    }

    // Enables each field only when its tick box is ticked; the start and end not for an Instant
    // schedule
    private void update() {
        nameBox.setEnabled(nameEnable.getValue());
        nameForm.setDisabled(!nameEnable.getValue());

        enabledBox.setEnabled(enabledEnable.getValue());
        enabledForm.setDisabled(!enabledEnable.getValue());

        nodeBox.setEnabled(nodeEnable.getValue());
        nodeForm.setDisabled(!nodeEnable.getValue());

        final boolean instantSchedule = scheduleBox.getValue().getType().equals(ScheduleType.INSTANT);
        scheduleBox.setEnabled(scheduleEnable.getValue());
        scheduleForm.setDisabled(!scheduleEnable.getValue());
        scheduleForm.setLabel("Schedule (" + scheduleBox.getValue().getType().getDisplayValue() + ")");

        startTimeBox.setEnabled(startTimeEnable.getValue() && !instantSchedule);
        startTimeForm.setDisabled(!(startTimeEnable.getValue() && !instantSchedule));

        endTimeBox.setEnabled(endTimeEnable.getValue() && !instantSchedule);
        endTimeForm.setDisabled(!(endTimeEnable.getValue() && !instantSchedule));

        onRunAsUserEnabled.accept(runAsUserEnable.getValue());
        runAsUserForm.setDisabled(!runAsUserEnable.getValue());
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, BatchExecutionScheduleEdit> {

    }
}
