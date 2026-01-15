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

import stroom.alert.client.event.AlertEvent;
import stroom.job.shared.GetScheduledTimesRequest;
import stroom.job.shared.ScheduleReferenceTime;
import stroom.job.shared.ScheduleRestriction;
import stroom.job.shared.ScheduledTimes;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.shared.NullSafe;
import stroom.util.shared.StringUtil;
import stroom.util.shared.scheduler.CronExpressions;
import stroom.util.shared.scheduler.FrequencyExpressions;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.event.dom.client.HasClickHandlers;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class SchedulePopup
        extends MyPresenterWidget<SchedulePopup.ScheduleView>
        implements ScheduleUiHandlers {

    private final ScheduledTimeClient scheduledTimeClient;
    private final DateTimeFormatter dateTimeFormatter;
    private Schedule frequencySchedule =
            new Schedule(ScheduleType.FREQUENCY, FrequencyExpressions.EVERY_10_MINUTES.getExpression());
    private Schedule cronSchedule =
            new Schedule(ScheduleType.CRON, CronExpressions.EVERY_10TH_MINUTE.getExpression());
    private Schedule currentSchedule = frequencySchedule;

    private ScheduleRestriction scheduleRestriction = new ScheduleRestriction(false, true, true);

    private ScheduleReferenceTime scheduleReferenceTime;


    @Inject
    public SchedulePopup(final EventBus eventBus,
                         final ScheduleView view,
                         final ScheduledTimeClient scheduledTimeClient,
                         final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);
        this.scheduledTimeClient = scheduledTimeClient;
        this.dateTimeFormatter = dateTimeFormatter;
        view.setUiHandlers(this);
    }

    @Override
    protected void onBind() {
        registerHandler(getView().getCalculateButton().addClickHandler(event -> calcTimes()));
    }

    public void setSchedule(final Schedule schedule,
                            final ScheduleReferenceTime scheduleReferenceTime) {
        this.currentSchedule = schedule;
        this.scheduleReferenceTime = scheduleReferenceTime;
        read();
        onScheduleTypeChange(currentSchedule.getType());
    }

    private void read() {
        getView().setScheduleType(currentSchedule.getType());
        getView().getExpression().setText(StringUtil.toString(currentSchedule.getExpression()));
        calcTimes();
    }

    public void validate(final Schedule schedule,
                         final ScheduleRestriction scheduleRestriction,
                         final Consumer<ScheduledTimes> consumer) {
        scheduledTimeClient.validate(schedule, scheduleRestriction, consumer, this);
    }

    @Override
    public void onScheduleTypeChange(final ScheduleType scheduleType) {
        switch (currentSchedule.getType()) {
            case FREQUENCY: {
                frequencySchedule = new Schedule(currentSchedule.getType(), getView().getExpression().getText().trim());
                break;
            }
            case CRON: {
                cronSchedule = new Schedule(currentSchedule.getType(), getView().getExpression().getText().trim());
                break;
            }
        }
        switch (scheduleType) {
            case FREQUENCY: {
                currentSchedule = frequencySchedule;
                break;
            }
            case CRON: {
                currentSchedule = cronSchedule;
                break;
            }
        }
        read();
    }

    private Schedule createSchedule() {
        return new Schedule(getView().getScheduleType(), getView().getExpression().getText().trim());
    }

    private void calcTimes() {
        final ScheduleType scheduleType = getView().getScheduleType();
        final String currentString = getView().getExpression().getText();
        final Long scheduleReferenceTime = NullSafe
                .get(this.scheduleReferenceTime, ScheduleReferenceTime::getScheduleReferenceTime);
        final Long lastExecutedTime = NullSafe
                .get(this.scheduleReferenceTime, ScheduleReferenceTime::getLastExecutedTime);
        if (!NullSafe.isBlankString(currentString) && scheduleType != null) {
            final Schedule schedule = createSchedule();
            final GetScheduledTimesRequest request = new GetScheduledTimesRequest(
                    schedule,
                    scheduleReferenceTime,
                    null);
            scheduledTimeClient.getScheduledTimes(request, result -> {
                if (result != null) {
                    if (lastExecutedTime == null) {
                        getView().getLastExecutedTime().setText("Never");
                    } else {
                        getView().getLastExecutedTime().setText(dateTimeFormatter
                                .format(lastExecutedTime));
                    }

                    if (result.isError()) {
                        getView().getNextScheduledTime().setText(result.getError());
                    } else if (result.getNextScheduledTimeMs() != null) {
                        getView().getNextScheduledTime().setText(dateTimeFormatter
                                .format(result.getNextScheduledTimeMs()));
                    } else {
                        getView().getNextScheduledTime().setText("Never");
                    }
                }
            }, this);
        }
    }

    public void show(final Consumer<Schedule> consumer) {
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption("Change Schedule")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    // This method is overwritten so that we can validate the schedule
                    // before saving. Getting the scheduled times acts as validation.
                    if (e.isOk()) {
                        validate(createSchedule(), scheduleRestriction, scheduledTimes -> {
                            if (scheduledTimes == null) {
                                e.reset();
                            } else {
                                if (scheduledTimes.isError()) {
                                    AlertEvent.fireWarn(this, scheduledTimes.getError(), e::reset);
                                } else {
                                    consumer.accept(createSchedule());
                                    e.hide();
                                }
                            }
                        });
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    public void setScheduleRestriction(final ScheduleRestriction scheduleRestriction) {
        this.scheduleRestriction = scheduleRestriction;
    }

    public interface ScheduleView extends View, Focus, HasUiHandlers<ScheduleUiHandlers> {

        ScheduleType getScheduleType();

        void setScheduleType(ScheduleType scheduleType);

        HasText getExpression();

        HasText getLastExecutedTime();

        HasText getNextScheduledTime();

        HasClickHandlers getCalculateButton();
    }
}
