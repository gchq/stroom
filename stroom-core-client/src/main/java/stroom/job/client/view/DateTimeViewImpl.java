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

package stroom.job.client.view;

import stroom.job.client.presenter.DateTimePopup.DateTimeView;
import stroom.widget.datepicker.client.CustomDatePicker;
import stroom.widget.datepicker.client.IntlDateTimeFormat;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Day;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Hour;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Minute;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Month;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Second;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.TimeZoneName;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Year;
import stroom.widget.datepicker.client.JsDate;
import stroom.widget.datepicker.client.ValueChooser;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class DateTimeViewImpl extends ViewImpl implements DateTimeView {

    private final Widget widget;

    @UiField
    Label date;
    @UiField
    CustomDatePicker datePicker;
    @UiField
    Label time;
    @UiField
    ValueChooser hour;
    @UiField
    ValueChooser minute;
    @UiField
    ValueChooser second;
    @UiField
    ValueChooser millisecond;

    @UiField
    Label today;
    @UiField
    Label yesterday;
    @UiField
    Label weekStart;
    @UiField
    Label now;
    @UiField
    Label midnight;
    @UiField
    Label midday;

    @Inject
    public DateTimeViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        datePicker.setCurrentMonth(JsDate.create());
        datePicker.setYearAndMonthDropdownVisible(true);
        datePicker.setYearArrowsVisible(true);

        hour.setMin(0);
        hour.setMax(23);
        minute.setMin(0);
        minute.setMax(59);
        second.setMin(0);
        second.setMax(59);
        millisecond.setMin(0);
        millisecond.setMax(999);
    }

    private JsDate getCurrentDate() {
        final JsDate date = JsDate.create(datePicker.getValue().getTime());
        date.setHours(hour.getIntValue());
        date.setMinutes(minute.getIntValue());
        date.setSeconds(second.getIntValue());
        date.setMilliseconds(millisecond.getIntValue());

//        for (final TimeZoneName timeZoneName : TimeZoneName.values()) {
//            final String offset = IntlDateTimeFormat.format(date, IntlDateTimeFormat.DEFAULT_LOCALE,
//                    FormatOptions
//                            .builder()
//                            .locale("en-GB")
//                            .timeZone(timeZone.getValue())
//                            .hour(Hour.TWO_DIGIT)
//                            .minute(Minute.TWO_DIGIT)
//                            .timeZoneName(timeZoneName)
//                            .build());
//            GWT.log(timeZoneName + " = " + offset);
//        }

        final String offset2 = IntlDateTimeFormat.format(date, IntlDateTimeFormat.DEFAULT_LOCALE,
                FormatOptions
                        .builder()
                        .locale("en-GB")
                        .hour(Hour.TWO_DIGIT)
                        .minute(Minute.TWO_DIGIT)
                        .timeZoneName(TimeZoneName.SHORT_OFFSET)
                        .build());
        final int index = offset2.lastIndexOf(" ");
        if (index != -1) {
            String offsetString = offset2.substring(index + 1);
            final int plusIndex = offsetString.indexOf("+");
            final int minusIndex = offsetString.indexOf("-");
            Boolean plus = null;
            if (plusIndex != -1) {
                offsetString = offsetString.substring(plusIndex + 1);
                plus = true;
            } else if (minusIndex != -1) {
                offsetString = offsetString.substring(minusIndex + 1);
                plus = false;
            }
            if (plus != null) {
                final int separatorIndex = offsetString.indexOf(":");
                final int milliseconds;
                if (separatorIndex != -1) {
                    final String hourString = offsetString.substring(0, separatorIndex);
                    final String minuteString = offsetString.substring(separatorIndex + 1);
                    final int hours = Integer.parseInt(hourString);
                    final int minutes = Integer.parseInt(minuteString);
                    milliseconds = (hours * 60 * 60 * 1000) + (minutes * 60 * 1000);
                } else {
                    final int hours = Integer.parseInt(offsetString);
                    milliseconds = hours * 60 * 60 * 1000;
                }

                if (plus) {
                    date.setTime(date.getTime() - milliseconds);
                } else {
                    date.setTime(date.getTime() + milliseconds);
                }
            }
        }


//        final String offset2 = IntlDateTimeFormat.format(date, IntlDateTimeFormat.DEFAULT_LOCALE,
//                FormatOptions
//                        .builder()
//                        .locale("en-GB")
//                        .timeZone(timeZone.getValue())
//                        .hour(Hour.TWO_DIGIT)
//                        .minute(Minute.TWO_DIGIT)
//                        .timeZoneName(TimeZoneName.SHORT_OFFSET)
//                        .build());
//        GWT.log("offset2 = " + offset2);

//        final int offset3 = IntlDateTimeFormat.getTimeZoneOffset(date, timeZone.getValue());
//        GWT.log("offset3 = " + offset3);

        return date;
    }

    @Override
    public long getTime() {
        return (long) getCurrentDate().getTime();
    }

    @Override
    public void setTime(final long time) {
        final JsDate current = JsDate.create(time);
        setTimeFields(current);
        setDateFields(current);
        updateDateLabel();
        updateTimeLabel();
    }

    private void setDateFields(final JsDate date) {
        final JsDate current = JsDate.create(date.getTime());
        current.setHours(0, 0, 0, 0);
        datePicker.setCurrentMonth(current);
        datePicker.setValue(current);
    }

    private void setTimeFields(final JsDate date) {
        hour.setValue(date.getHours());
        minute.setValue(date.getMinutes());
        second.setValue(date.getSeconds());
        millisecond.setValue(date.getMilliseconds());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        hour.focus();
    }

    public interface Binder extends UiBinder<Widget, DateTimeViewImpl> {

    }

    @UiHandler("datePicker")
    public void onDatePicker(final ValueChangeEvent<JsDate> event) {
        updateDateLabel();
    }

    @UiHandler("hour")
    public void onHour(final ValueChangeEvent<Long> event) {
        updateTimeLabel();
    }

    @UiHandler("minute")
    public void onMinute(final ValueChangeEvent<Long> event) {
        updateTimeLabel();
    }

    @UiHandler("second")
    public void onSecond(final ValueChangeEvent<Long> event) {
        updateTimeLabel();
    }

    @UiHandler("millisecond")
    public void onMillisecond(final ValueChangeEvent<Long> event) {
        updateTimeLabel();
    }


    @UiHandler("today")
    public void onToday(final ClickEvent event) {
        setDateFields(JsDate.create());
        updateTimeLabel();
    }

    @UiHandler("yesterday")
    public void onYesterday(final ClickEvent event) {
        JsDate newDate = JsDate.create();
        final int date = newDate.getDate();
        newDate.setDate(date - 1);
        setDateFields(newDate);
        updateTimeLabel();
    }

    @UiHandler("weekStart")
    public void onWeekStart(final ClickEvent event) {
        JsDate newDate = JsDate.create();
        final int date = newDate.getDate();
        newDate.setDate(date - newDate.getDay());
        setDateFields(newDate);
        updateTimeLabel();
    }

    @UiHandler("now")
    public void onNow(final ClickEvent event) {
        final JsDate newDate = JsDate.create();
        setTimeFields(newDate);
        updateTimeLabel();
    }

    @UiHandler("midnight")
    public void onMidnight(final ClickEvent event) {
        hour.setValue(0);
        minute.setValue(0);
        second.setValue(0);
        millisecond.setValue(0);
        updateTimeLabel();
    }

    @UiHandler("midday")
    public void onMidday(final ClickEvent event) {
        hour.setValue(12);
        minute.setValue(0);
        second.setValue(0);
        millisecond.setValue(0);
        updateTimeLabel();
    }

    private void updateDateLabel() {
        final FormatOptions options = FormatOptions
                .builder()
                .year(Year.NUMERIC)
                .month(Month.LONG)
                .day(Day.NUMERIC)
                .build();
        date.setText(IntlDateTimeFormat.format(datePicker.getValue(), IntlDateTimeFormat.DEFAULT_LOCALE, options));
    }

    private void updateTimeLabel() {
        final JsDate date = getCurrentDate();
        final FormatOptions options = FormatOptions
                .builder()
                .hour(Hour.NUMERIC)
                .minute(Minute.NUMERIC)
                .second(Second.NUMERIC)
                .fractionalSecondDigits(3)
                .timeZoneName(TimeZoneName.SHORT)
                .build();
        time.setText(IntlDateTimeFormat.format(date, IntlDateTimeFormat.DEFAULT_LOCALE, options));
    }
}
