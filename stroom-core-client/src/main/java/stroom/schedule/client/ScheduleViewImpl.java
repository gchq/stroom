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

package stroom.schedule.client;

import stroom.item.client.SelectionBox;
import stroom.schedule.client.SchedulePopup.ScheduleView;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.scheduler.CronExpression;
import stroom.util.shared.scheduler.CronExpressions;
import stroom.util.shared.scheduler.FrequencyExpression;
import stroom.util.shared.scheduler.FrequencyExpressions;
import stroom.util.shared.scheduler.ScheduleType;
import stroom.widget.button.client.Button;
import stroom.widget.button.client.QuickSettings;
import stroom.widget.form.client.FormGroup;
import stroom.widget.presenter.client.ViewWithUiHandlers;

import com.google.gwt.event.dom.client.HasClickHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class ScheduleViewImpl extends ViewWithUiHandlers<ScheduleUiHandlers> implements ScheduleView {

    private final Widget widget;

    @UiField
    SelectionBox<ScheduleType> scheduleType;
    @UiField
    SimplePanel quickSettings;
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

    @Inject
    public ScheduleViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        scheduleType.addItem(ScheduleType.INSTANT);
        scheduleType.addItem(ScheduleType.FREQUENCY);
        scheduleType.addItem(ScheduleType.CRON);
        calculate.setIcon(SvgImage.HISTORY);
        // Changes as the expression is calculated, so screen readers are told the new time (or why
        // the expression isn't valid)
        nextScheduledTime.getElement().setAttribute("aria-live", "polite");
        enterCronMode();
    }

    private void enterInstantMode() {
        setNonInstantSettingsVisible(false);
    }

    private void enterFrequencyMode() {
        setNonInstantSettingsVisible(true);

        final QuickSettings settings = new QuickSettings();
        addFrequencies(settings.addGroup("Minute"), FrequencyExpressions.MINUTE);
        addFrequencies(settings.addGroup("Hour"), FrequencyExpressions.HOUR);
        this.quickSettings.setWidget(settings);
    }

    private void enterCronMode() {
        setNonInstantSettingsVisible(true);

        final QuickSettings settings = new QuickSettings();
        addCrons(settings.addGroup("Minute"), CronExpressions.MINUTE);
        addCrons(settings.addGroup("Hour"), CronExpressions.HOUR);
        addCrons(settings.addGroup("Day"), CronExpressions.DAY);
        addCrons(settings.addGroup("Month"), CronExpressions.MONTH);
        this.quickSettings.setWidget(settings);
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
        quickSettings.setVisible(visible);
        quickSettingsFormGroup.setVisible(visible);
        expression.setVisible(visible);
        expressionFormGroup.setVisible(visible);
        lastExecuted.setVisible(visible);
        lastExecutedFormGroup.setVisible(visible);
        nextScheduledTime.setVisible(visible);
        nextScheduledTimeFormGroup.setVisible(visible);
        calculate.setVisible(visible);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        expression.setFocus(true);
    }

    @Override
    public ScheduleType getScheduleType() {
        return scheduleType.getValue();
    }

    @Override
    public void setScheduleType(final ScheduleType scheduleType) {
        this.scheduleType.setValue(scheduleType);
        switch (scheduleType) {
            case INSTANT: {
                enterInstantMode();
                break;
            }
            case FREQUENCY: {
                enterFrequencyMode();
                break;
            }
            case CRON: {
                enterCronMode();
                break;
            }
        }
    }

    @Override
    public HasText getLastExecutedTime() {
        return lastExecuted;
    }

    @Override
    public HasText getNextScheduledTime() {
        return nextScheduledTime;
    }

    @Override
    public HasText getExpression() {
        return expression;
    }

    @Override
    public HasClickHandlers getCalculateButton() {
        return calculate;
    }

    public interface Binder extends UiBinder<Widget, ScheduleViewImpl> {

    }

    @UiHandler("scheduleType")
    public void onScheduleType(final ValueChangeEvent<ScheduleType> event) {
        getUiHandlers().onScheduleTypeChange(event.getValue());
    }
}
