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

package stroom.widget.datepicker.client;

import stroom.util.shared.time.Time;
import stroom.widget.datepicker.client.DateTimePopup.DateTimeView;
import stroom.widget.datepicker.client.TimePopup.TimeView;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class TimeViewImpl extends ViewImpl implements TimeView {

    private final Widget widget;

    @SuppressWarnings("unused")
    @UiField
    ValueSpinner hour;
    @SuppressWarnings("unused")
    @UiField
    ValueSpinner minute;
    @SuppressWarnings("unused")
    @UiField
    ValueSpinner second;

    @Inject
    public TimeViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        hour.setMin(0);
        hour.setMax(23);
        hour.setMinStep(1);
        hour.setMaxStep(1);
        minute.setMin(0);
        minute.setMax(59);
        minute.setMinStep(1);
        minute.setMaxStep(1);
        second.setMin(0);
        second.setMax(59);
        second.setMinStep(1);
        second.setMaxStep(1);
    }

    @Override
    public Time getTime() {
        return new Time(hour.getIntValue(), minute.getIntValue(), second.getIntValue());
    }

    @Override
    public void setTime(final Time time) {
        if (time != null) {
            hour.setValue(time.getHour());
            minute.setValue(time.getMinute());
            second.setValue(time.getSecond());
        } else {
            hour.setValue(0);
            minute.setValue(0);
            second.setValue(0);
        }
    }

    @Override
    public void setHourVisible(final boolean visible) {
        hour.setVisible(visible);
    }

    @Override
    public void setMinuteVisible(final boolean visible) {

    }

    @Override
    public void setSecondVisible(final boolean visible) {

    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        hour.focus();
    }

    @SuppressWarnings("unused")
    @UiHandler("hour")
    public void onHour(final ValueChangeEvent<Long> event) {
    }

    @SuppressWarnings("unused")
    @UiHandler("hourReset")
    public void onHourReset(final ClickEvent event) {
        hour.setValue(0);
        minute.setValue(0);
        second.setValue(0);
    }

    @SuppressWarnings("unused")
    @UiHandler("minute")
    public void onMinute(final ValueChangeEvent<Long> event) {
    }

    @SuppressWarnings("unused")
    @UiHandler("minuteReset")
    public void onMinuteReset(final ClickEvent event) {
        minute.setValue(0);
        second.setValue(0);
    }

    @SuppressWarnings("unused")
    @UiHandler("second")
    public void onSecond(final ValueChangeEvent<Long> event) {
    }

    @SuppressWarnings("unused")
    @UiHandler("secondReset")
    public void onSecondReset(final ClickEvent event) {
        second.setValue(0);
    }

    public interface Binder extends UiBinder<Widget, TimeViewImpl> {

    }
}
