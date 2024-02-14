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

package stroom.job.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.job.shared.GetScheduledTimesRequest;
import stroom.job.shared.ScheduledTimeResource;
import stroom.job.shared.ScheduledTimes;
import stroom.query.api.v2.CronExpressions;
import stroom.query.api.v2.FrequencyExpressions;
import stroom.util.shared.StringUtil;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.HasClickHandlers;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class SchedulePresenter
        extends MyPresenterWidget<SchedulePresenter.ScheduleView>
        implements ScheduleUiHandlers {



    private final ScheduledTimeClient scheduledTimeClient;
    private Schedule frequencySchedule =
            new Schedule(ScheduleType.FREQUENCY, FrequencyExpressions.EVERY_10_MINUTES.getExpression());
    private Schedule cronSchedule =
            new Schedule(ScheduleType.CRON, CronExpressions.EVERY_10TH_MINUTE.getExpression());
    private Schedule currentSchedule = frequencySchedule;

    private Long scheduleReferenceTime;
    private Long lastExecutedTime;


    @Inject
    public SchedulePresenter(final EventBus eventBus,
                             final ScheduleView view,
                             final ScheduledTimeClient scheduledTimeClient) {
        super(eventBus, view);
        this.scheduledTimeClient = scheduledTimeClient;
        view.setUiHandlers(this);
    }

    @Override
    protected void onBind() {
        registerHandler(getView().getCalculateButton().addClickHandler(event -> calcTimes()));
    }

    public void setSchedule(final Schedule schedule,
                            final Long scheduleReferenceTime,
                            final Long lastExecutedTime) {
        this.currentSchedule = schedule;
        this.scheduleReferenceTime = scheduleReferenceTime;
        this.lastExecutedTime = lastExecutedTime;
        read();
        onScheduleTypeChange(currentSchedule.getType());
    }

    private void read() {
        getView().setScheduleType(currentSchedule.getType());
        getView().getExpression().setText(StringUtil.toString(currentSchedule.getExpression()));
        calcTimes();
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
        final Long scheduleReferenceTime = this.scheduleReferenceTime;
        final Long lastExecutedTime = this.lastExecutedTime;
        if (currentString != null && currentString.trim().length() > 0 && scheduleType != null) {
            final Schedule schedule = createSchedule();
            final GetScheduledTimesRequest request = new GetScheduledTimesRequest(
                    schedule,
                    scheduleReferenceTime,
                    lastExecutedTime);
            scheduledTimeClient.getScheduledTimes(request, result -> {
                if (result != null) {
                    getView().getLastExecutedTime().setText(result.getLastExecutedTime());
                    getView().getNextScheduledTime().setText(result.getNextScheduledTime());
                }
            });
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
                        final GetScheduledTimesRequest request = new GetScheduledTimesRequest(
                                createSchedule(),
                                scheduleReferenceTime,
                                lastExecutedTime);
                        scheduledTimeClient.getScheduledTimes(request, result -> e.hide());
                    } else {
                        e.hide();
                    }
                })
                .onHide(e -> {
                    if (e.isOk()) {
                        consumer.accept(createSchedule());
                    }
                })
                .fire();
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
