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

package stroom.processor.client.view;

import stroom.processor.client.presenter.ProfilePeriodEditPresenter.ProfilePeriodEditView;
import stroom.util.shared.time.Days;
import stroom.widget.datepicker.client.TimeBox;
import stroom.widget.tickbox.client.view.CustomCheckBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class ProfilePeriodEditViewImpl
        extends ViewImpl
        implements ProfilePeriodEditView {

    private final Widget widget;

    @UiField
    DaysWidget days;
    @UiField
    TimeBox startTime;
    @UiField
    TimeBox endTime;
    @UiField
    CustomCheckBox limitNodeThreads;
    @UiField
    ValueSpinner maxNodeThreads;
    @UiField
    CustomCheckBox limitClusterThreads;
    @UiField
    ValueSpinner maxClusterThreads;

    @Inject
    public ProfilePeriodEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        maxNodeThreads.setMin(0);
        maxNodeThreads.setMax(1000000);
        maxClusterThreads.setMin(0);
        maxClusterThreads.setMax(1000000);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        startTime.focus();
    }

    @Override
    public Days getDays() {
        return days.getValue();
    }

    @Override
    public void setDays(final Days days) {
        this.days.setValue(days);
    }

    @Override
    public TimeBox getStartTime() {
        return startTime;
    }

    @Override
    public TimeBox getEndTime() {
        return endTime;
    }

    @Override
    public boolean isLimitNodeThreads() {
        return limitNodeThreads.getValue();
    }

    @Override
    public void setLimitNodeThreads(final boolean limitNodeThreads) {
        this.limitNodeThreads.setValue(limitNodeThreads);
        maxNodeThreads.setEnabled(limitNodeThreads);
    }

    @Override
    public int getMaxNodeThreads() {
        return maxNodeThreads.getIntValue();
    }

    @Override
    public void setMaxNodeThreads(final int maxNodeThreads) {
        this.maxNodeThreads.setValue(maxNodeThreads);
    }

    @Override
    public boolean isLimitClusterThreads() {
        return limitClusterThreads.getValue();
    }

    @Override
    public void setLimitClusterThreads(final boolean limitTotalClusterThreads) {
        this.limitClusterThreads.setValue(limitTotalClusterThreads);
        maxClusterThreads.setEnabled(limitTotalClusterThreads);
    }

    @Override
    public int getMaxClusterThreads() {
        return maxClusterThreads.getIntValue();
    }

    @Override
    public void setMaxClusterThreads(final int totalClusterThreads) {
        this.maxClusterThreads.setValue(totalClusterThreads);
    }

    @UiHandler("limitNodeThreads")
    public void onLimitNodeThreads(final ValueChangeEvent<Boolean> event) {
        maxNodeThreads.setEnabled(limitNodeThreads.getValue());
    }

    @UiHandler("limitClusterThreads")
    public void onLimitClusterThreads(final ValueChangeEvent<Boolean> event) {
        maxClusterThreads.setEnabled(limitClusterThreads.getValue());
    }

    public interface Binder extends UiBinder<Widget, ProfilePeriodEditViewImpl> {

    }
}
