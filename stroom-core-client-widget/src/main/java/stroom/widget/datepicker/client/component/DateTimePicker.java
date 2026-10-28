/*
 * Copyright 2024 Crown Copyright
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

package stroom.widget.datepicker.client.component;

import stroom.widget.button.client.LinkButton;
import stroom.widget.component.client.Component;
import stroom.widget.datepicker.client.Calendar;
import stroom.widget.datepicker.client.DateRecord;
import stroom.widget.datepicker.client.DateTimeModel;
import stroom.widget.datepicker.client.TimeOffset;
import stroom.widget.datepicker.client.TimeRecord;
import stroom.widget.datepicker.client.UTCDate;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

/// The "Set Date And Time" dialog's content: a calendar for the date and spinners for the time,
/// shown in the user's time zone, with shortcuts (Today, Now, Midnight and the like). Its markup
/// is `DateTimePicker.ui.xml`, styled by `date-time-view.css`; [DateTimePickerController] shows
/// it.
public class DateTimePicker extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Label date;
    @UiField
    Calendar datePicker;
    @UiField
    Label time;
    @UiField
    ValueSpinner hour;
    @UiField
    ValueSpinner minute;
    @UiField
    ValueSpinner second;
    @UiField
    ValueSpinner millisecond;

    @UiField
    LinkButton today;
    @UiField
    LinkButton yesterday;
    @UiField
    LinkButton weekStart;
    @UiField
    LinkButton now;
    @UiField
    LinkButton midnight;
    @UiField
    LinkButton midday;
    @UiField
    LinkButton hourReset;
    @UiField
    LinkButton minuteReset;
    @UiField
    LinkButton secondReset;
    @UiField
    LinkButton millisecondReset;

    private UTCDate value;
    private String currentDateString;
    private final DateTimeModel dateTimeModel = new DateTimeModel();
    private int previousHour = 0;

    /// Makes a picker; [#setTime(long)] gives it the time to show.
    public DateTimePicker() {
        initWidget(BINDER.createAndBindUi(this));
        today.setOnClick(this::onToday);
        yesterday.setOnClick(this::onYesterday);
        weekStart.setOnClick(this::onWeekStart);
        datePicker.addValueChangeHandler(this::onDatePicker);
        now.setOnClick(this::onNow);
        midnight.setOnClick(this::onMidnight);
        midday.setOnClick(this::onMidday);
        hour.setOnChange(value -> onHour());
        hourReset.setOnClick(this::onHourReset);
        minute.setOnChange(value -> onMinute());
        minuteReset.setOnClick(this::onMinuteReset);
        second.setOnChange(value -> onSecond());
        secondReset.setOnClick(this::onSecondReset);
        millisecond.setOnChange(value -> onMillisecond());
        millisecondReset.setOnClick(this::onMillisecondReset);
    }

    private void setDatePickerTime(final UTCDate value) {
        final DateRecord dateRecord = dateTimeModel.parseDate(value);
        final UTCDate utc = UTCDate.create(
                dateRecord.getYear(),
                dateRecord.getMonth(),
                dateRecord.getDay(),
                0,
                0,
                0,
                0);

        datePicker.setCurrentMonth(utc);
        datePicker.setValue(utc);
    }

    /// @return The time chosen, in milliseconds since the epoch.
    public long getTime() {
        return (long) value.getTime();
    }

    /// @param time The time to show, in milliseconds since the epoch.
    public void setTime(final long time) {
        this.value = UTCDate.create(time);
        update();
    }

    /// Puts the focus on the hour.
    public void focus() {
        hour.focus();
    }

    /// Chooses today, keeping the time.
    private void onToday() {
        final UTCDate utc = dateTimeModel.getTodayUTC();
        setDatePickerTime(utc);
        value = resolveDateTime(value);
        update();
    }

    /// Chooses yesterday, keeping the time.
    private void onYesterday() {
        final UTCDate utc = dateTimeModel.getTodayUTC();
        utc.setDate(utc.getDate() - 1);
        setDatePickerTime(utc);
        value = resolveDateTime(value);
        update();
    }

    /// Chooses the start of this week, keeping the time.
    private void onWeekStart() {
        final UTCDate utc = dateTimeModel.getTodayUTC();
        utc.setDate(utc.getDate() - utc.getDay());
        setDatePickerTime(utc);
        value = resolveDateTime(value);
        update();
    }

    /// Takes the day chosen in the calendar.
    ///
    /// @param event The event.
    private void onDatePicker(final ValueChangeEvent<UTCDate> event) {
        value = resolveDateTime(value);
        updateDateLabel();
        updateTime();
    }

    /// Chooses the time now, keeping the date.
    private void onNow() {
        final TimeRecord timeRecord = dateTimeModel.parseTime(UTCDate.create());
        hour.setValue(timeRecord.getHour());
        minute.setValue(timeRecord.getMinute());
        second.setValue(timeRecord.getSecond());
        millisecond.setValue(timeRecord.getMillisecond());
        value = resolveDateTime(value);
        update();
    }

    /// Chooses midnight, keeping the date.
    private void onMidnight() {
        hour.setValue(0);
        minute.setValue(0);
        second.setValue(0);
        millisecond.setValue(0);
        value = resolveDateTime(value);
        update();
    }

    /// Chooses midday, keeping the date.
    private void onMidday() {
        hour.setValue(12);
        minute.setValue(0);
        second.setValue(0);
        millisecond.setValue(0);
        value = resolveDateTime(value);
        update();
    }

    /// Takes the hour typed or spun, allowing for daylight saving changes.
    private void onHour() {
        final int previousHour = this.previousHour;
        final DateRecord dateBefore = dateTimeModel.parseDate(value);
        final int hour = this.hour.getIntValue();
        value = resolveDateTime(value, hour == 23);
        updateTime();

        // Deal with daylight savings offset changes that could switch the day or hour.
        if (hour == 0) {
            final DateRecord dateAfter = dateTimeModel.parseDate(value);
            if (!dateBefore.equals(dateAfter)) {
                GWT.log("Fix hour for DST: " + dateBefore + " -> " + dateAfter);
                value.setTime(value.getTime() + DateTimeModel.MILLIS_IN_HOUR);
                updateTime();
            } else if (previousHour == 23 && this.hour.getIntValue() == 1) {
                GWT.log("Fix hour for DST wrap: " + dateBefore + " -> " + dateAfter);
                value.setTime(value.getTime() - DateTimeModel.MILLIS_IN_HOUR);
                updateTime();
            }
        }
    }

    /// Zeroes the time.
    private void onHourReset() {
        hour.setValue(0);
        minute.setValue(0);
        second.setValue(0);
        millisecond.setValue(0);
        value = resolveDateTime(value);
        update();
    }

    /// Takes the minute typed or spun.
    private void onMinute() {
        final int minute = this.minute.getIntValue();
        value = resolveDateTime(value, minute == 59);
        updateTime();
    }

    /// Zeroes the minute and what follows it.
    private void onMinuteReset() {
        minute.setValue(0);
        second.setValue(0);
        millisecond.setValue(0);
        value = resolveDateTime(value);
        update();
    }

    /// Takes the second typed or spun.
    private void onSecond() {
        value.setSeconds(second.getIntValue());
        updateTimeLabel();
    }

    /// Zeroes the second and what follows it.
    private void onSecondReset() {
        second.setValue(0);
        millisecond.setValue(0);
        value = resolveDateTime(value);
        update();
    }

    /// Takes the millisecond typed or spun.
    private void onMillisecond() {
        value.setMilliseconds(millisecond.getIntValue());
        updateTimeLabel();
    }

    /// Zeroes the millisecond.
    private void onMillisecondReset() {
        millisecond.setValue(0);
        value = resolveDateTime(value);
        update();
    }

    private UTCDate resolveDateTime(final UTCDate previousTime) {
        return resolveDateTime(previousTime, true);
    }

    private UTCDate resolveDateTime(final UTCDate previousTime, final boolean allowOffsetChange) {
        final UTCDate datePickerTime = datePicker.getValue();

        final long currentOffsetMs = dateTimeModel.getOffsetMillis(previousTime);
        final UTCDate date = UTCDate.create(
                datePickerTime.getFullYear(),
                datePickerTime.getMonth(),
                datePickerTime.getDate(),
                hour.getIntValue(),
                minute.getIntValue(),
                second.getIntValue(),
                millisecond.getIntValue());
        date.setTime(date.getTime() - currentOffsetMs);

        final long newOffsetMs = dateTimeModel.getOffsetMillis(date);
        if (allowOffsetChange && newOffsetMs != currentOffsetMs) {
            GWT.log("Offset changed: " + currentOffsetMs + " -> " + newOffsetMs);
            final UTCDate newDate = UTCDate.create(
                    datePickerTime.getFullYear(),
                    datePickerTime.getMonth(),
                    datePickerTime.getDate(),
                    hour.getIntValue(),
                    minute.getIntValue(),
                    second.getIntValue(),
                    millisecond.getIntValue());
            newDate.setTime(newDate.getTime() - newOffsetMs);
            return newDate;

        } else {
            return date;
        }
    }

    private void update() {
        updateDate();
        updateTime();
    }

    private void updateDate() {
        setDatePickerTime(value);
    }

    private void updateDateLabel() {
        final String dateString = dateTimeModel.formatDateLabel(value);
        this.date.setText(dateString);

        if (currentDateString == null || !currentDateString.equals(dateString)) {
            currentDateString = dateString;
            setDatePickerTime(value);
        }
    }

    private void updateTime() {
        final TimeOffset offset = dateTimeModel.getOffset(value);

        int hour = value.getHours() + offset.getHours();
        if (hour > 23) {
            hour = hour - 24;
        } else if (hour < 0) {
            hour = hour + 24;
        }

        int minute = value.getMinutes() + offset.getMinutes();
        if (minute > 59) {
            minute = minute - 60;
        } else if (minute < 0) {
            minute = minute + 60;
        }

        previousHour = hour;
        this.hour.setValue(hour);
        this.minute.setValue(minute);
        this.second.setValue(value.getSeconds());
        this.millisecond.setValue(value.getMilliseconds());

        updateDateLabel();
        updateTimeLabel();
    }

    private void updateTimeLabel() {
        final String timeString = dateTimeModel.formatTimeLabel(value);
        time.setText(timeString);
    }



    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DateTimePicker> {

    }
}
