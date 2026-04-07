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

package stroom.analytics.client.view;

import stroom.analytics.client.presenter.BatchExecutionScheduleEditPresenter.BatchExecutionScheduleEditView;
import stroom.analytics.client.presenter.ProcessingStatusUiHandlers;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ScheduleBounds;
import stroom.item.client.SelectionBox;
import stroom.schedule.client.ScheduleBox;
import stroom.security.client.presenter.UserRefSelectionBoxPresenter;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.UserRef;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;
import stroom.widget.button.client.Button;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.form.client.FormGroup;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.List;

public final class BatchExecutionScheduleEditViewImpl
        extends ViewWithUiHandlers<ProcessingStatusUiHandlers>
        implements BatchExecutionScheduleEditView {

    private final Widget widget;

    @UiField
    TextBox nameBox;
    @UiField
    FormGroup nameForm;
    @UiField
    CustomCheckBox nameEnable;

    @UiField
    CustomCheckBox enabledBox;
    @UiField
    FormGroup enabledForm;
    @UiField
    CustomCheckBox enabledEnable;

    @UiField
    SelectionBox<String> nodeBox;
    @UiField
    FormGroup nodeForm;
    @UiField
    CustomCheckBox nodeEnable;

    @UiField
    ScheduleBox scheduleBox;
    @UiField
    FormGroup scheduleForm;
    @UiField
    CustomCheckBox scheduleEnable;

    @UiField
    DateTimeBox startTimeBox;
    @UiField
    FormGroup startTimeForm;
    @UiField
    CustomCheckBox startTimeEnable;

    @UiField
    DateTimeBox endTimeBox;
    @UiField
    FormGroup endTimeForm;
    @UiField
    CustomCheckBox endTimeEnable;

    @UiField
    SimplePanel runAsUserBox;
    @UiField
    FormGroup runAsUserForm;
    @UiField
    CustomCheckBox runAsUserEnable;

    @UiField
    Button applySelectionButton;
    @UiField
    Button applyFilteredButton;

    private String selectedNode;
    private final UserRefSelectionBoxPresenter userRefSelectionBoxPresenter;

    @Inject
    public BatchExecutionScheduleEditViewImpl(final Binder binder,
                                              final UserRefSelectionBoxPresenter userRefSelectionBoxPresenter) {
        widget = binder.createAndBindUi(this);
        this.userRefSelectionBoxPresenter = userRefSelectionBoxPresenter;
        setRunAsUserView();
        endTimeBox.setOptional(true);

        applySelectionButton.setIcon(SvgImage.OK);
        applyFilteredButton.setIcon(SvgImage.GENERATE);

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

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        if (!nameEnable.getValue()) {
            nameBox.setValue("");
        }
        if (!enabledEnable.getValue()) {
            enabledBox.setValue(false);
        }
        if (!nodeEnable.getValue()) {
            nodeBox.setValue(this.nodeBox.getItems().get(0));
        }
        if (!scheduleEnable.getValue()) {
            scheduleBox.setValue(new Schedule(ScheduleType.CRON, ""));
        }
        if (!startTimeEnable.getValue()) {
            startTimeBox.setValue(null);
        }
        if (!endTimeEnable.getValue()) {
            endTimeBox.setValue(null);
        }
        if (!runAsUserEnable.getValue()) {
            userRefSelectionBoxPresenter.setSelected(null);
        }
    }

    @Override
    public ExecutionSchedule getUpdatedExecutionSchedule(final ExecutionSchedule executionSchedule) {
        final String name        = nameEnable.getValue()     ? nameBox.getValue()     : executionSchedule.getName();
        final boolean enabled    = enabledEnable.getValue()  ? enabledBox.getValue()  : executionSchedule.isEnabled();
        final String nodeName    = nodeEnable.getValue()     ? nodeBox.getValue()     : executionSchedule.getNodeName();
        final Schedule schedule  = scheduleEnable.getValue() ? scheduleBox.getValue() : executionSchedule.getSchedule();
        final boolean contiguous = executionSchedule.isContiguous();

        final Long startTime = startTimeEnable.getValue()
                ? startTimeBox.getValue()
                : executionSchedule.getScheduleBounds().getStartTimeMs();
        final Long endTime = endTimeEnable.getValue()
                ? endTimeBox.getValue()
                : executionSchedule.getScheduleBounds().getEndTimeMs();
        final ScheduleBounds scheduleBounds = new ScheduleBounds(startTime, endTime);
        final UserRef runAsUser = runAsUserEnable.getValue()
                ? userRefSelectionBoxPresenter.getSelected()
                : executionSchedule.getRunAsUser();

        return executionSchedule
                .copy()
                .name(name)
                .enabled(enabled)
                .nodeName(nodeName)
                .schedule(schedule)
                .contiguous(contiguous)
                .scheduleBounds(scheduleBounds)
                .runAsUser(runAsUser)
                .build();
    }

    private void update() {
        nameBox.setEnabled(nameEnable.getValue());
        nameForm.setDisabled(!nameEnable.getValue());

        enabledBox.setEnabled(enabledEnable.getValue());
        enabledForm.setDisabled(!enabledEnable.getValue());

        nodeBox.setEnabled(nodeEnable.getValue());
        nodeForm.setDisabled(!nodeEnable.getValue());

        final boolean instantSchedule = scheduleBox.getValue().getType().equals(ScheduleType.INSTANT);
        scheduleBox.setEnabled(scheduleEnable.getValue() && !instantSchedule);
        scheduleForm.setDisabled(!(scheduleEnable.getValue() && !instantSchedule));
        scheduleForm.setLabel("Schedule (" + scheduleBox.getValue().getType().getDisplayValue() + ")");

        startTimeBox.setEnabled(startTimeEnable.getValue() && !instantSchedule);
        startTimeForm.setDisabled(!(startTimeEnable.getValue() && !instantSchedule));

        endTimeBox.setEnabled(endTimeEnable.getValue() && !instantSchedule);
        endTimeForm.setDisabled(!(endTimeEnable.getValue() && !instantSchedule));

        userRefSelectionBoxPresenter.setEnabled(runAsUserEnable.getValue());
        runAsUserForm.setDisabled(!runAsUserEnable.getValue());
    }

    @Override
    public String getName() {
        return nameBox.getValue();
    }

    @Override
    public void setName(final String name) {
        this.nameBox.setValue(name);
    }

    @Override
    public boolean isEnabled() {
        return this.enabledBox.getValue();
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabledBox.setValue(enabled);
    }

    @Override
    public void setNodes(final List<String> nodes) {
        this.nodeBox.clear();
        this.nodeBox.addItems(nodes);
        if (selectedNode == null) {
            if (nodes.size() > 0) {
                this.nodeBox.setValue(nodes.get(0));
            }
        } else {
            this.nodeBox.setValue(selectedNode);
            selectedNode = null;
        }
    }

    @Override
    public String getNode() {
        return this.nodeBox.getValue();
    }

    @Override
    public void setNode(final String node) {
        if (node != null) {
            selectedNode = node;
            this.nodeBox.setValue(node);
        }
    }

    @Override
    public ScheduleBox getScheduleBox() {
        return scheduleBox;
    }

    @Override
    public DateTimeBox getStartTime() {
        return startTimeBox;
    }

    @Override
    public DateTimeBox getEndTime() {
        return endTimeBox;
    }

    @Override
    public void setRunAsUserView() {
        final View view = this.userRefSelectionBoxPresenter.getView();
        this.runAsUserBox.setWidget(view.asWidget());
        view.asWidget().addStyleName("w-100");

    }

    @Override
    public Button getApplySelectionButton() {
        return applySelectionButton;
    }

    @Override
    public Button getApplyFilteredButton() {
        return applyFilteredButton;
    }

    public boolean isAnyBoxEnabled() {
        return nameEnable.getValue()
               || enabledEnable.getValue()
               || nodeEnable.getValue()
               || scheduleEnable.getValue()
               || startTimeEnable.getValue()
               || endTimeEnable.getValue()
               || runAsUserEnable.getValue();
    }

    // Formats the contents of all enabled boxes into a multiline String for the
    // edit summary confirmation window
    public String getEditSummary() {
        final StringBuilder sb = new StringBuilder();
        if (nameEnable.getValue()) {
            sb.append("Name to '");
            sb.append(nameBox.getValue());
            sb.append("'\n");
        }
        if (enabledEnable.getValue()) {
            sb.append("Enabled to ");
            sb.append(enabledBox.getValue() ? "'true'" : "'false'");
            sb.append("\n");
        }
        if (nodeEnable.getValue()) {
            sb.append("Processing Node to '");
            sb.append(nodeBox.getValue());
            sb.append("'\n");
        }
        if (scheduleEnable.getValue()) {
            sb.append("Schedule to '");
            sb.append(scheduleBox.getValue());
            sb.append("'\n");
        }
        if (startTimeEnable.getValue()) {
            sb.append("Start Time to '");
            sb.append(ClientDateUtil.toISOString(startTimeBox.getValue()));
            sb.append("'\n");
        }
        if (endTimeEnable.getValue()) {
            sb.append("End Time to '");
            sb.append(ClientDateUtil.toISOString(endTimeBox.getValue()));
            sb.append("'\n");
        }
        if (runAsUserEnable.getValue()) {
            sb.append("Run As User to '");
            sb.append(userRefSelectionBoxPresenter.getSelected());
            sb.append("'\n");
        }
        return sb.toString();
    }

    public interface Binder extends UiBinder<Widget, BatchExecutionScheduleEditViewImpl> {

    }
}
