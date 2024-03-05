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

import stroom.expression.api.UserTimeZone;
import stroom.job.client.presenter.DateTimePopup.DateTimeView;
import stroom.preferences.client.UserPreferencesManager;
import stroom.ui.config.shared.UserPreferences;
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
import stroom.widget.valuespinner.client.DecreaseEvent;
import stroom.widget.valuespinner.client.IncreaseEvent;

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

    private static final String[] EN_LOCALE = {"en-GB"};

    private final long MILLIS_IN_SECOND = 1000;
    private final long MILLIS_IN_MINUTE = 60 * MILLIS_IN_SECOND;
    private final long MILLIS_IN_HOUR = 60 * MILLIS_IN_MINUTE;

    private final UserPreferencesManager userPreferencesManager;

    private final Widget widget;

    @SuppressWarnings("unused")
    @UiField
    Label date;
    @SuppressWarnings("unused")
    @UiField
    CustomDatePicker datePicker;
    @SuppressWarnings("unused")
    @UiField
    Label time;
    @SuppressWarnings("unused")
    @UiField
    ValueChooser hour;
    @SuppressWarnings("unused")
    @UiField
    ValueChooser minute;
    @SuppressWarnings("unused")
    @UiField
    ValueChooser second;
    @SuppressWarnings("unused")
    @UiField
    ValueChooser millisecond;

    @SuppressWarnings("unused")
    @UiField
    Label today;
    @SuppressWarnings("unused")
    @UiField
    Label yesterday;
    @SuppressWarnings("unused")
    @UiField
    Label weekStart;
    @SuppressWarnings("unused")
    @UiField
    Label now;
    @SuppressWarnings("unused")
    @UiField
    Label midnight;
    @SuppressWarnings("unused")
    @UiField
    Label midday;

    private JsDate value;
    private TimeRecord currentTime;

    @Inject
    public DateTimeViewImpl(final Binder binder,
                            final UserPreferencesManager userPreferencesManager) {
        this.userPreferencesManager = userPreferencesManager;

        widget = binder.createAndBindUi(this);
        setDatePickerTime(JsDate.create());
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

        setTime((long) JsDate.create().getTime());
    }

    private void setDatePickerTime(final JsDate date) {
        final JsDate utc = zoneToUTCDate(date);
        datePicker.setCurrentMonth(utc);
        datePicker.setValue(utc);
    }

    @Override
    public long getTime() {
        return (long) value.getTime();
    }

    @Override
    public void setTime(final long time) {
        setTime(JsDate.create(time));
    }

    private void setTime(final JsDate time) {
        value = JsDate.create(time.getTime());
        update(value);
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
    @UiHandler("today")
    public void onToday(final ClickEvent event) {
        final JsDate newDate = JsDate.create();
        setDatePickerTime(newDate);
        setDate();
    }

    @SuppressWarnings("unused")
    @UiHandler("yesterday")
    public void onYesterday(final ClickEvent event) {
        final JsDate newDate = JsDate.create();
        final int date = newDate.getDate();
        newDate.setDate(date - 1);
        setDatePickerTime(newDate);
        setDate();
    }

    @SuppressWarnings("unused")
    @UiHandler("weekStart")
    public void onWeekStart(final ClickEvent event) {
        final JsDate newDate = JsDate.create();
        final int date = newDate.getDate();
        newDate.setDate(date - newDate.getDay());
        setDatePickerTime(newDate);
        setDate();
    }

    @SuppressWarnings("unused")
    @UiHandler("datePicker")
    public void onDatePicker(final ValueChangeEvent<JsDate> event) {
        setDate();
    }

    @SuppressWarnings("unused")
    @UiHandler("now")
    public void onNow(final ClickEvent event) {
        this.value = fixDate(JsDate.create());
        updateTime(value);
    }

    @SuppressWarnings("unused")
    @UiHandler("midnight")
    public void onMidnight(final ClickEvent event) {
        value.setTime(fixDate(value).getTime() -
                (currentTime.getHour() * MILLIS_IN_HOUR) -
                (currentTime.getMinute() * MILLIS_IN_MINUTE) -
                (currentTime.getSecond() * MILLIS_IN_SECOND) -
                currentTime.getMillisecond());
        updateTime(value);
    }

    @SuppressWarnings("unused")
    @UiHandler("midday")
    public void onMidday(final ClickEvent event) {
        value.setTime(fixDate(value).getTime() -
                (currentTime.getHour() * MILLIS_IN_HOUR) -
                (currentTime.getMinute() * MILLIS_IN_MINUTE) -
                (currentTime.getSecond() * MILLIS_IN_SECOND) -
                currentTime.getMillisecond() +
                (12 * MILLIS_IN_HOUR));
        updateTime(value);
    }

    @SuppressWarnings("unused")
    @UiHandler("hour")
    public void onHour(final ValueChangeEvent<Long> event) {
        int diff = hour.getIntValue() - currentTime.getHour();
        value.setTime(fixDate(value).getTime() + (diff * MILLIS_IN_HOUR));
        updateTime(value);
    }

    @SuppressWarnings("unused")
    @UiHandler("hour")
    public void onIncreaseHour(final IncreaseEvent event) {
        value.setTime(fixDate(value).getTime() + MILLIS_IN_HOUR);
        updateTime(value);
    }

    @SuppressWarnings("unused")
    @UiHandler("hour")
    public void onDecreaseHour(final DecreaseEvent event) {
        value.setTime(fixDate(value).getTime() - MILLIS_IN_HOUR);
        updateTime(value);
    }

    @SuppressWarnings("unused")
    @UiHandler("minute")
    public void onMinute(final ValueChangeEvent<Long> event) {
        int diff = minute.getIntValue() - currentTime.getMinute();
        value.setTime(fixDate(value).getTime() + (diff * MILLIS_IN_MINUTE));
        updateTime(value);
    }

    @SuppressWarnings("unused")
    @UiHandler("minute")
    public void onIncreaseMinute(final IncreaseEvent event) {
        value.setTime(fixDate(value).getTime() + MILLIS_IN_MINUTE);
        updateTime(value);
    }

    @SuppressWarnings("unused")
    @UiHandler("minute")
    public void onDecreaseMinute(final DecreaseEvent event) {
        value.setTime(fixDate(value).getTime() - MILLIS_IN_MINUTE);
        updateTime(value);
    }

    @SuppressWarnings("unused")
    @UiHandler("second")
    public void onSecond(final ValueChangeEvent<Long> event) {
        int diff = second.getIntValue() - currentTime.getSecond();
        value.setTime(fixDate(value).getTime() + (diff * MILLIS_IN_SECOND));
        updateTime(value);
    }

    @SuppressWarnings("unused")
    @UiHandler("second")
    public void onIncreaseSecond(final IncreaseEvent event) {
        value.setTime(fixDate(value).getTime() + MILLIS_IN_SECOND);
        updateTime(value);
    }

    @SuppressWarnings("unused")
    @UiHandler("second")
    public void onDecreaseSecond(final DecreaseEvent event) {
        value.setTime(fixDate(value).getTime() - MILLIS_IN_SECOND);
        updateTime(value);
    }

    @SuppressWarnings("unused")
    @UiHandler("millisecond")
    public void onMillisecond(final ValueChangeEvent<Long> event) {
        int diff = millisecond.getIntValue() - currentTime.getMillisecond();
        value.setTime(fixDate(value).getTime() + diff);
        updateTime(value);
    }

    @SuppressWarnings("unused")
    @UiHandler("millisecond")
    public void onIncreaseMillisecond(final IncreaseEvent event) {
        value.setTime(fixDate(value).getTime() + 1);
        updateTime(value);
    }

    @SuppressWarnings("unused")
    @UiHandler("millisecond")
    public void onDecreaseMillisecond(final DecreaseEvent event) {
        value.setTime(fixDate(value).getTime() - 1);
        updateTime(value);
    }

    private void setDate() {
        setTime(fixDate(value));
    }

    private JsDate fixDate(final JsDate value) {
        final TimeRecord timeRecord = parseTime(value);
        final String timeZoneName = parseTimeZone(value);

        final String year = zeroPad(4, datePicker.getValue().getUTCFullYear());
        final String month = zeroPad(2, datePicker.getValue().getUTCMonth());
        final String day = zeroPad(2, datePicker.getValue().getUTCDate());

        final String dateTimeString =
                year + "-" +
                        month + "-" +
                        day + "T" +
                        zeroPad(2, timeRecord.getHour()) + ":" +
                        zeroPad(2, timeRecord.getMinute()) + ":" +
                        zeroPad(2, timeRecord.getSecond()) + "." +
                        zeroPad(3, timeRecord.getMillisecond()) +
                        timeZoneName;
        return JsDate.create(JsDate.parse(dateTimeString));

//
//
//
//
//        IntlDateTimeFormat.format(value, EN_LOCALE, builder.build());
//
//        final String timeZone = getTimeZone();
//
//        final FormatOptions.Builder builder = FormatOptions
//                .builder()
//                .year(Year.NUMERIC)
//                .month(Month.NUMERIC)
//                .day(Day.NUMERIC)
//                .hour(Hour.NUMERIC)
//                .minute(Minute.NUMERIC)
//                .second(Second.NUMERIC)
//                .fractionalSecondDigits(3)
//                .timeZoneName(TimeZoneName.SHORT);
//        if (timeZone != null) {
//            builder.timeZone(timeZone);
//        }
//        final String dateTimeString = IntlDateTimeFormat.format(value, EN_LOCALE, builder.build());
//
//        final String[] dateTimeParts = dateTimeString.split(DATE_TIME_DELIMITER);
//        final String dateString = dateTimeParts[0];
//        final String timeString = dateTimeParts[1];
//
//        final String year = "" + datePicker.getValue().getUTCFullYear();
//        final String month = "" + datePicker.getValue().getUTCMonth();
//        final String day = "" + datePicker.getValue().getUTCDate();
//        final String date = day + "/" + month + "/" + year;
//
//        final String newDateTimeString = date + DATE_TIME_DELIMITER + timeString;
//        return JsDate.create(JsDate.parse(newDateTimeString));
    }

    private static String zeroPad(final int amount, int value) {
        return zeroPad(amount, "" + value);
    }

    private static String zeroPad(final int amount, final String in) {
        final int left = amount - in.length();
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < left; i++) {
            out.append("0");
        }
        out.append(in);
        return out.toString();
    }

    private void update(final JsDate value) {
        updateDate(value);
        updateTime(value);
    }

    private void updateDate(final JsDate value) {
        setDatePickerTime(value);
        updateDateLabel(value);
    }

    private void updateDateLabel(final JsDate value) {
        final FormatOptions.Builder builder = FormatOptions
                .builder()
                .year(Year.NUMERIC)
                .month(Month.LONG)
                .day(Day.NUMERIC);
        setTimeZone(builder);

        final String dateString = IntlDateTimeFormat.format(value, IntlDateTimeFormat.DEFAULT_LOCALE, builder.build());
        this.date.setText(dateString);
    }

    private void updateTime(final JsDate value) {
        currentTime = parseTime(value);
        this.hour.setValue(currentTime.getHour());
        this.minute.setValue(currentTime.getMinute());
        this.second.setValue(currentTime.getSecond());
        this.millisecond.setValue(currentTime.getMillisecond());

        updateTimeLabel(value);
    }

    private void updateTimeLabel(final JsDate value) {
        final FormatOptions.Builder builder = FormatOptions
                .builder()
                .hour(Hour.NUMERIC)
                .minute(Minute.NUMERIC)
                .second(Second.NUMERIC)
                .fractionalSecondDigits(3)
                .timeZoneName(TimeZoneName.SHORT);
        setTimeZone(builder);

        final String timeString = IntlDateTimeFormat.format(value, IntlDateTimeFormat.DEFAULT_LOCALE, builder.build());
        time.setText(timeString);
    }

    private JsDate zoneToUTCDate(final JsDate value) {
        final DateRecord dateRecord = parseDate(value);
        final TimeRecord timeRecord = parseTime(value);
        return JsDate.create(JsDate.utc(
                dateRecord.getYear(),
                dateRecord.getMonth(),
                dateRecord.getDay(),
                timeRecord.getHour(),
                timeRecord.getMinute(),
                timeRecord.getSecond(),
                timeRecord.getMillisecond()));
    }

    private DateRecord parseDate(final JsDate value) {
        final FormatOptions.Builder builder = FormatOptions
                .builder()
                .year(Year.NUMERIC)
                .month(Month.NUMERIC)
                .day(Day.NUMERIC);
        setTimeZone(builder);

        final String dateString = IntlDateTimeFormat
                .format(value, EN_LOCALE, builder.build());

        final String[] dateParts = dateString.split("/");
        final int day = getInt(dateParts[0]);
        final int month = getInt(dateParts[1]) - 1;
        final int year = getInt(dateParts[2]);
        return new DateRecord(year, month, day);
    }

    private TimeRecord parseTime(final JsDate value) {
        final FormatOptions.Builder builder = FormatOptions
                .builder()
                .hour(Hour.NUMERIC)
                .minute(Minute.NUMERIC)
                .second(Second.NUMERIC)
                .fractionalSecondDigits(3)
                .timeZoneName(TimeZoneName.SHORT);
        setTimeZone(builder);

        String timeString = IntlDateTimeFormat
                .format(value, EN_LOCALE, builder.build());
        final int timeZoneIndex = timeString.indexOf(" ");
        if (timeZoneIndex != -1) {
            timeString = timeString.substring(0, timeZoneIndex);
        }
        final String[] parts = timeString.split(":");
        final String[] secondParts = parts[2].split("\\.");
        final int hour = getInt(parts[0]);
        final int minute = getInt(parts[1]);
        final int second = getInt(secondParts[0]);
        final int millisecond = getInt(secondParts[1]);
        return new TimeRecord(hour, minute, second, millisecond);
    }

    private String parseTimeZone(final JsDate value) {
        final String tz = parseTimeZone(value, TimeZoneName.LONG_OFFSET);
        String offset = tz.replaceAll("GMT", "");
        offset = offset.replaceAll(":", "");

        if (offset.length() > 0) {
            return offset;
        }
        return "+0000";
    }

    private String parseTimeZone(final JsDate value, final TimeZoneName timeZoneName) {
        final FormatOptions.Builder builder = FormatOptions
                .builder()
                .hour(Hour.NUMERIC)
                .minute(Minute.NUMERIC)
                .second(Second.NUMERIC)
                .fractionalSecondDigits(3)
                .timeZoneName(timeZoneName);
        setTimeZone(builder);

        final String dateTimeString = IntlDateTimeFormat.format(value, EN_LOCALE, builder.build());
        final int index = dateTimeString.indexOf(" ");
        final String tz = dateTimeString.substring(index + 1);

        return tz;
    }

    private int getInt(final String string) {
        int index = -1;
        final char[] chars = string.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '0') {
                index = i;
            } else {
                break;
            }
        }
        String trimmed = string;
        if (index != -1) {
            trimmed = trimmed.substring(index + 1);
        }
        if (trimmed.length() == 0) {
            return 0;
        }
        return Integer.parseInt(trimmed);
    }

    private void setTimeZone(final FormatOptions.Builder builder) {
        final String timeZone = getTimeZone();
        if (timeZone != null) {
            builder.timeZone(timeZone);
        }
    }

    private String getTimeZone() {
        final UserPreferences userPreferences = userPreferencesManager.getCurrentUserPreferences();
        final UserTimeZone userTimeZone = userPreferences.getTimeZone();
        String timeZone = null;
        switch (userTimeZone.getUse()) {
            case UTC: {
                timeZone = "GMT";
                break;
            }
            case ID: {
                timeZone = userTimeZone.getId();
                break;
            }
            case OFFSET: {
                final String hours = zeroPad(2, userTimeZone.getOffsetHours());
                final String minutes = zeroPad(2, userTimeZone.getOffsetMinutes());
                String offset = hours + minutes;
                if (userTimeZone.getOffsetHours() >= 0 && userTimeZone.getOffsetMinutes() >= 0) {
                    offset = "+" + offset;
                } else {
                    offset = "-" + offset;
                }

                timeZone = "GMT" + offset;
                break;
            }
        }
        return timeZone;
    }

    public interface Binder extends UiBinder<Widget, DateTimeViewImpl> {

    }
}
