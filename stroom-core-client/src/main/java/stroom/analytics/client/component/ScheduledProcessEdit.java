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

import stroom.item.client.SelectionBox;
import stroom.schedule.client.ScheduleBox;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextBox;
import stroom.widget.tickbox.client.view.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// An analytic's or report's schedule: its name, whether it is enabled, its node, schedule,
/// start and end, and the user it runs as. The start and end only apply to a schedule that isn't
/// Instant.
public class ScheduledProcessEdit extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox name;
    @UiField
    TickBox enabled;
    @UiField
    SelectionBox<String> node;
    @UiField
    FormGroup scheduleForm;
    @UiField
    ScheduleBox scheduleBox;
    @UiField
    DateTimeBox startTime;
    @UiField
    DateTimeBox endTime;
    @UiField
    DropDownBox runAsUser;
    @UiField
    Button setDefaultNode;

    // The node to choose once the nodes are known
    private String selectedNode;
    private Runnable onSetDefaultNode = () -> {
    };

    /// Makes an empty form.
    public ScheduledProcessEdit() {
        initWidget(BINDER.createAndBindUi(this));
        scheduleBox.addValueChangeHandler(this::onSchedule);
    }

    /// Puts the focus on the name, its text selected, and shows the schedule's type.
    public void focus() {
        name.setFocus(true);
        name.selectAll();
        updateNonInstantUI(scheduleBox.getValue().getType());
    }

    /// @return The schedule box, for its controller to attach to.
    public ScheduleBox getScheduleBox() {
        return scheduleBox;
    }

    /// @return The start time box, for its controller to attach to.
    public DateTimeBox getStartTimeBox() {
        return startTime;
    }

    /// @return The end time box, for its controller to attach to.
    public DateTimeBox getEndTimeBox() {
        return endTime;
    }

    /// @return The Run As User picker, for its controller to attach to.
    public DropDownBox getRunAsUserBox() {
        return runAsUser;
    }

    /// @return The schedule's name.
    public String getName() {
        return name.getValue();
    }

    /// @param name The schedule's name.
    public void setName(final String name) {
        this.name.setValue(name);
    }

    /// @return Whether the schedule runs.
    public boolean isEnabled() {
        return enabled.getValue();
    }

    /// @param enabled Whether the schedule runs.
    public void setEnabled(final boolean enabled) {
        this.enabled.setValue(enabled);
    }

    /// Lists the nodes, choosing the node given to [#setNode(String)], else the first.
    ///
    /// @param nodes The nodes.
    public void setNodes(final List<String> nodes) {
        node.clear();
        node.addItems(nodes);
        if (selectedNode == null) {
            if (!nodes.isEmpty()) {
                node.setValue(nodes.get(0));
            }
        } else {
            node.setValue(selectedNode);
            selectedNode = null;
        }
    }

    /// @return The node the schedule runs on.
    public String getNode() {
        return node.getValue();
    }

    /// @param node The node the schedule runs on, or `null` to leave it as it is.
    public void setNode(final String node) {
        if (node != null) {
            selectedNode = node;
            this.node.setValue(node);
        }
    }

    /// @param visible Whether Set Default is offered, as it is to an administrator.
    public void setSetDefaultVisible(final boolean visible) {
        setDefaultNode.setVisible(visible);
    }

    /// @param onSetDefaultNode Called when Set Default is pressed.
    public void setOnSetDefaultNode(final Runnable onSetDefaultNode) {
        this.onSetDefaultNode = onSetDefaultNode;
    }

    @UiHandler("setDefaultNode")
    void onSetDefaultNode(final ClickEvent event) {
        onSetDefaultNode.run();
    }

    private void onSchedule(final ValueChangeEvent<Schedule> event) {
        if (event != null && event.getValue() != null) {
            updateNonInstantUI(event.getValue().getType());
        }
    }

    // The start and end only apply to a schedule that isn't Instant
    private void updateNonInstantUI(final ScheduleType scheduleType) {
        startTime.setEnabled(!scheduleType.equals(ScheduleType.INSTANT));
        endTime.setEnabled(!scheduleType.equals(ScheduleType.INSTANT));
        if (scheduleType.equals(ScheduleType.INSTANT)) {
            scheduleBox.setValue(scheduleBox
                    .getValue()
                    .copy()
                    .expression(ScheduleType.INSTANT.getDisplayValue())
                    .build());
        }
        scheduleForm.setLabel("Schedule (" + scheduleType.getDisplayValue() + ")");
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ScheduledProcessEdit> {

    }
}
