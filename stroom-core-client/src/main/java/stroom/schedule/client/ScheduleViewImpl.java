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

import com.google.gwt.event.dom.client.HasClickHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class ScheduleViewImpl extends ViewWithUiHandlers<ScheduleUiHandlers> implements ScheduleView {

    private final Widget widget;

    @UiField
    SelectionBox<ScheduleType> scheduleType;
    @UiField
    SimplePanel quickSettings;
    @UiField
    TextBox expression;
    @UiField
    Label lastExecuted;
    @UiField
    Label nextScheduledTime;
    @UiField
    Button calculate;

    @Inject
    public ScheduleViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        scheduleType.addItem(ScheduleType.FREQUENCY);
        scheduleType.addItem(ScheduleType.CRON);
        calculate.setIcon(SvgImage.HISTORY);
        enterCronMode();
    }

    private void enterFrequencyMode() {
        final FlowPanel minute = createPanel("Minute");
        for (final FrequencyExpression cronExpression : FrequencyExpressions.MINUTE) {
            minute.add(createLabel(cronExpression));
        }

        final FlowPanel hour = createPanel("Hour");
        for (final FrequencyExpression cronExpression : FrequencyExpressions.HOUR) {
            hour.add(createLabel(cronExpression));
        }

        final FlowPanel quickSettingsPanel = new FlowPanel();
        quickSettingsPanel.setStyleName("timeRange-quickSettings");
        quickSettingsPanel.add(minute);
        quickSettingsPanel.add(hour);

        this.quickSettings.setWidget(quickSettingsPanel);
    }

    private void enterCronMode() {
        final FlowPanel minute = createPanel("Minute");
        for (final CronExpression cronExpression : CronExpressions.MINUTE) {
            minute.add(createLabel(cronExpression));
        }

        final FlowPanel hour = createPanel("Hour");
        for (final CronExpression cronExpression : CronExpressions.HOUR) {
            hour.add(createLabel(cronExpression));
        }

        final FlowPanel day = createPanel("Day");
        for (final CronExpression cronExpression : CronExpressions.DAY) {
            day.add(createLabel(cronExpression));
        }

        final FlowPanel month = createPanel("Month");
        for (final CronExpression cronExpression : CronExpressions.MONTH) {
            month.add(createLabel(cronExpression));
        }

        final FlowPanel quickSettingsPanel = new FlowPanel();
        quickSettingsPanel.setStyleName("timeRange-quickSettings");
        quickSettingsPanel.add(minute);
        quickSettingsPanel.add(hour);
        quickSettingsPanel.add(day);
        quickSettingsPanel.add(month);

        this.quickSettings.setWidget(quickSettingsPanel);
    }

    private FlowPanel createPanel(final String name) {
        final FlowPanel panel = new FlowPanel();
        panel.addStyleName("timeRange-quickSettingPanel");
        final Label label = new Label(name, false);
        label.addStyleName("timeRange-quickSettingPanel-title");
        panel.add(label);
        return panel;
    }

    private Label createLabel(final CronExpression cronExpression) {
        final Label label = new Label(cronExpression.getName(), false);
        label.addStyleName("timeRange-quickSetting");
        label.addClickHandler(event -> {
            expression.setValue(cronExpression.getExpression(), true);
        });
        return label;
    }

    private Label createLabel(final FrequencyExpression cronExpression) {
        final Label label = new Label(cronExpression.getName(), false);
        label.addStyleName("timeRange-quickSetting");
        label.addClickHandler(event -> {
            expression.setValue(cronExpression.getExpression(), true);
        });
        return label;
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
            case CRON: {
                enterCronMode();
                break;
            }
            case FREQUENCY: {
                enterFrequencyMode();
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
