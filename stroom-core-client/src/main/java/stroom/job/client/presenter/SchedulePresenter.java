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

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.HasClickHandlers;
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.job.shared.GetScheduledTimesRequest;
import stroom.job.shared.JobNode.JobType;
import stroom.job.shared.ScheduledTimeResource;
import stroom.job.shared.ScheduledTimes;
import stroom.util.client.StroomCoreStringUtil;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

public class SchedulePresenter extends MyPresenterWidget<SchedulePresenter.ScheduleView> {
    private static final ScheduledTimeResource SCHEDULED_TIME_RESOURCE = GWT.create(ScheduledTimeResource.class);

    private final RestFactory restFactory;
    private JobType jobType = JobType.UNKNOWN;
    private Long scheduleReferenceTime = 0L;
    private Long lastExecutedTime = 0L;
    private String scheduleString = "";

    @Inject
    public SchedulePresenter(final EventBus eventBus,
                             final ScheduleView view,
                             final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;
    }

    @Override
    protected void onBind() {
        registerHandler(getView().getCalculateButton().addClickHandler(event -> calcTimes()));
    }

    public String getScheduleString() {
        return scheduleString;
    }

    public void setSchedule(final JobType jobType, final Long scheduleReferenceTime, final Long lastExecutedTime,
                            final String scheduleString) {
        this.jobType = jobType;
        this.scheduleReferenceTime = scheduleReferenceTime;
        this.lastExecutedTime = lastExecutedTime;
        this.scheduleString = scheduleString;
        read();
    }

    private void read() {
        getView().getScheduledType().setText(StroomCoreStringUtil.toString(jobType));
        getView().getScheduledString().setText(StroomCoreStringUtil.toString(scheduleString));
        calcTimes();
    }

    private void write() {
        scheduleString = getView().getScheduledString().getText().trim();
    }

    private void calcTimes() {
        final String currentString = getView().getScheduledString().getText();
        final JobType jobType = this.jobType;
        final Long scheduleReferenceTime = this.scheduleReferenceTime;
        final Long lastExecutedTime = this.lastExecutedTime;
        if (currentString != null && currentString.trim().length() > 0 && jobType != null) {
            final GetScheduledTimesRequest request = new GetScheduledTimesRequest(jobType, scheduleReferenceTime, lastExecutedTime, currentString);
            final Rest<ScheduledTimes> rest = restFactory.create();
            rest
                    .onSuccess(result -> {
                        if (result != null) {
                            getView().getLastExecutedTime().setText(result.getLastExecutedTime());
                            getView().getNextScheduledTime().setText(result.getNextScheduledTime());
                        }
                    })
                    .call(SCHEDULED_TIME_RESOURCE)
                    .get(request);
        }
    }

    public void show(final PopupUiHandlers popupUiHandlers) {
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, "Change Schedule", popupUiHandlers);
    }

    public void hide(final boolean autoClose, final boolean ok) {
        // This method is overwritten so that we can validate the schedule
        // before saving. Getting the scheduled times acts as validation.
        if (ok) {
            write();

            final GetScheduledTimesRequest request = new GetScheduledTimesRequest(jobType, scheduleReferenceTime, lastExecutedTime, scheduleString);
            final Rest<ScheduledTimes> rest = restFactory.create();
            rest
                    .onSuccess(result -> HidePopupEvent.fire(SchedulePresenter.this, SchedulePresenter.this, autoClose, ok))
                    .call(SCHEDULED_TIME_RESOURCE)
                    .get(request);
        } else {
            HidePopupEvent.fire(SchedulePresenter.this, SchedulePresenter.this, autoClose, ok);
        }
    }

    public interface ScheduleView extends View {
        HasText getScheduledType();

        HasText getScheduledString();

        HasText getLastExecutedTime();

        HasText getNextScheduledTime();

        HasClickHandlers getCalculateButton();
    }
}
