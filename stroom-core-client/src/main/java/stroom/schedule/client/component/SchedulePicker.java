/*
 * Copyright 2016 Crown Copyright
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

package stroom.schedule.client.component;

import stroom.item.client.SelectionBox;
import stroom.util.shared.scheduler.CronExpression;
import stroom.util.shared.scheduler.CronExpressions;
import stroom.util.shared.scheduler.FrequencyExpression;
import stroom.util.shared.scheduler.FrequencyExpressions;
import stroom.util.shared.scheduler.ScheduleType;
import stroom.widget.button.client.Button;
import stroom.widget.button.client.QuickSettings;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextBox;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// The "Change Schedule" dialog's content: the schedule's type, presets for its expression, the
/// expression, and when it last ran and will next run. Its markup is `SchedulePicker.ui.xml`;
/// [SchedulePickerController] shows it and works the times out.
public class SchedulePicker extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SelectionBox<ScheduleType> scheduleType;
    @UiField
    QuickSettings quickSettings;
    @UiField
    FormGroup quickSettingsFormGroup;
    @UiField
    TextBox expression;
    @UiField
    FormGroup expressionFormGroup;
    @UiField
    Label lastExecuted;
    @UiField
    FormGroup lastExecutedFormGroup;
    @UiField
    Label nextScheduledTime;
    @UiField
    FormGroup nextScheduledTimeFormGroup;
    @UiField
    Button calculate;

    private Consumer<ScheduleType> onTypeChange;

    /// Makes a picker; [#setScheduleType(ScheduleType)] chooses the type and its presets.
    public SchedulePicker() {
        initWidget(BINDER.createAndBindUi(this));
        scheduleType.addItem(ScheduleType.INSTANT);
        scheduleType.addItem(ScheduleType.FREQUENCY);
        scheduleType.addItem(ScheduleType.CRON);
        scheduleType.addValueChangeHandler(event -> {
            if (onTypeChange != null) {
                onTypeChange.accept(event.getValue());
            }
        });
    }

    /// @param onTypeChange Given the type the user chooses.
    public void setOnTypeChange(final Consumer<ScheduleType> onTypeChange) {
        this.onTypeChange = onTypeChange;
    }

    /// @param onCalculate Run when the user asks for the times to be worked out again.
    public void setOnCalculate(final Runnable onCalculate) {
        calculate.setOnClick(onCalculate);
    }

    /// Puts the focus on the expression.
    public void focus() {
        expression.setFocus(true);
    }

    /// @return The type chosen.
    public ScheduleType getScheduleType() {
        return scheduleType.getValue();
    }

    /// Shows a type, with its presets; an instant schedule has no expression or times.
    ///
    /// @param scheduleType The type.
    public void setScheduleType(final ScheduleType scheduleType) {
        this.scheduleType.setValue(scheduleType);
        quickSettings.clear();
        switch (scheduleType) {
            case INSTANT: {
                setNonInstantSettingsVisible(false);
                break;
            }
            case FREQUENCY: {
                setNonInstantSettingsVisible(true);
                addFrequencies(quickSettings.addGroup("Minute"), FrequencyExpressions.MINUTE);
                addFrequencies(quickSettings.addGroup("Hour"), FrequencyExpressions.HOUR);
                break;
            }
            case CRON: {
                setNonInstantSettingsVisible(true);
                addCrons(quickSettings.addGroup("Minute"), CronExpressions.MINUTE);
                addCrons(quickSettings.addGroup("Hour"), CronExpressions.HOUR);
                addCrons(quickSettings.addGroup("Day"), CronExpressions.DAY);
                addCrons(quickSettings.addGroup("Month"), CronExpressions.MONTH);
                break;
            }
        }
    }

    /// @return The expression typed or chosen.
    public String getExpression() {
        return expression.getText();
    }

    /// @param expression The expression to show.
    public void setExpression(final String expression) {
        this.expression.setText(expression);
    }

    /// @param text When the schedule last ran, e.g. `Never`.
    public void setLastExecuted(final String text) {
        lastExecuted.setText(text);
    }

    /// @param text When the schedule will next run, or why the expression isn't valid.
    public void setNextScheduledTime(final String text) {
        nextScheduledTime.setText(text);
    }

    // A quick setting sets the expression
    private void addFrequencies(final QuickSettings.Group group, final FrequencyExpression[] expressions) {
        for (final FrequencyExpression frequencyExpression : expressions) {
            group.add(frequencyExpression.getName(),
                    () -> expression.setValue(frequencyExpression.getExpression(), true));
        }
    }

    private void addCrons(final QuickSettings.Group group, final CronExpression[] expressions) {
        for (final CronExpression cronExpression : expressions) {
            group.add(cronExpression.getName(),
                    () -> expression.setValue(cronExpression.getExpression(), true));
        }
    }

    private void setNonInstantSettingsVisible(final boolean visible) {
        quickSettingsFormGroup.setVisible(visible);
        expressionFormGroup.setVisible(visible);
        lastExecutedFormGroup.setVisible(visible);
        nextScheduledTimeFormGroup.setVisible(visible);
        calculate.setVisible(visible);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, SchedulePicker> {

    }
}
