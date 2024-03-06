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

public class DateTimeViewImpl extends ViewImpl implements DateTimeView {

    private static final String[] EN_LOCALE = {"en-GB"};

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
    private Offset currentOffset;
    private String currentDateString;

    @Inject
    public DateTimeViewImpl(final Binder binder,
                            final UserPreferencesManager userPreferencesManager) {
        this.userPreferencesManager = userPreferencesManager;

        widget = binder.createAndBindUi(this);
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

    private void setDatePickerTime(final JsDate value) {
        final DateRecord dateRecord = parseDate(value);
        final JsDate utc = JsDate.utc(
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

    @Override
    public long getTime() {
        return (long) value.getTime();
    }

    @Override
    public void setTime(final long time) {
        final JsDate date = JsDate.create(time);
        currentOffset = getOffset(date);
        this.value = date;
        update();
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
        final DateRecord today = parseDate(JsDate.create());
        final JsDate utc = JsDate.utc(
                today.getYear(),
                today.getMonth(),
                today.getDay(),
                0,
                0,
                0,
                0);
        setDatePickerTime(utc);
        value = resolveDateTime(value);
        update();
    }

    @SuppressWarnings("unused")
    @UiHandler("yesterday")
    public void onYesterday(final ClickEvent event) {
        final DateRecord today = parseDate(JsDate.create());
        final JsDate utc = JsDate.utc(
                today.getYear(),
                today.getMonth(),
                today.getDay(),
                0,
                0,
                0,
                0);
        utc.setUTCDate(utc.getUTCDate() - 1);
        setDatePickerTime(utc);
        value = resolveDateTime(value);
        update();
    }

    @SuppressWarnings("unused")
    @UiHandler("weekStart")
    public void onWeekStart(final ClickEvent event) {
        final DateRecord today = parseDate(JsDate.create());
        final JsDate utc = JsDate.utc(
                today.getYear(),
                today.getMonth(),
                today.getDay(),
                0,
                0,
                0,
                0);
        utc.setUTCDate(utc.getUTCDate() - utc.getUTCDay());
        setDatePickerTime(utc);
        value = resolveDateTime(value);
        update();
    }

    @SuppressWarnings("unused")
    @UiHandler("datePicker")
    public void onDatePicker(final ValueChangeEvent<JsDate> event) {
        value = resolveDateTime(value);
        updateDateLabel();
        updateTime();
    }

    @SuppressWarnings("unused")
    @UiHandler("now")
    public void onNow(final ClickEvent event) {
        final TimeRecord timeRecord = parseTime(JsDate.create());
        hour.setValue(timeRecord.getHour());
        minute.setValue(timeRecord.getMinute());
        second.setValue(timeRecord.getSecond());
        millisecond.setValue(timeRecord.getMillisecond());
        value = resolveDateTime(value);
        update();
    }

    @SuppressWarnings("unused")
    @UiHandler("midnight")
    public void onMidnight(final ClickEvent event) {
        hour.setValue(0);
        minute.setValue(0);
        second.setValue(0);
        millisecond.setValue(0);
        value = resolveDateTime(value);
        update();
    }

    @SuppressWarnings("unused")
    @UiHandler("midday")
    public void onMidday(final ClickEvent event) {
        hour.setValue(12);
        minute.setValue(0);
        second.setValue(0);
        millisecond.setValue(0);
        value = resolveDateTime(value);
        update();
    }

    @SuppressWarnings("unused")
    @UiHandler("hour")
    public void onHour(final ValueChangeEvent<Long> event) {
        final DateRecord dateBefore = parseDate(value);
        final int hour = this.hour.getIntValue();
        value = resolveDateTime(value, false);
        updateTime();

        // Deal with daylight savings offset changes that could switch the day.
        if (hour == 0) {
            final DateRecord dateAfter = parseDate(value);
            if (!dateBefore.equals(dateAfter)) {
                GWT.log("Fix hour for DST: " + dateBefore + " -> " + dateAfter);
                value.setTime(value.getTime() + 60 * 60 * 1000);
                updateTime();
            }
        }
    }

    @SuppressWarnings("unused")
    @UiHandler("minute")
    public void onMinute(final ValueChangeEvent<Long> event) {
        value = resolveDateTime(value, false);
        updateTime();
    }

    @SuppressWarnings("unused")
    @UiHandler("second")
    public void onSecond(final ValueChangeEvent<Long> event) {
        value.setUTCSeconds(second.getIntValue());
        updateTimeLabel();
    }

    @SuppressWarnings("unused")
    @UiHandler("millisecond")
    public void onMillisecond(final ValueChangeEvent<Long> event) {
        value.setUTCMilliseconds(millisecond.getIntValue());
        updateTimeLabel();
    }

    private JsDate resolveDateTime(final JsDate previousTime) {
        return resolveDateTime(previousTime, true);
    }

    private JsDate resolveDateTime(final JsDate previousTime, final boolean allowOffsetChange) {
        final JsDate datePickerTime = datePicker.getValue();
        String currentOffset = getOffsetString(previousTime);
        final String isoDateString =
                zeroPad(4, datePickerTime.getUTCFullYear()) +
                        "-" +
                        zeroPad(2, (datePickerTime.getUTCMonth() + 1)) +
                        "-" +
                        zeroPad(2, datePickerTime.getDate()) +
                        "T" +
                        zeroPad(2, hour.getIntValue()) +
                        ":" +
                        zeroPad(2, minute.getIntValue()) +
                        ":" +
                        zeroPad(2, second.getIntValue()) +
                        "." +
                        zeroPad(3, millisecond.getIntValue()) +
                        currentOffset;
        final JsDate isoDate = JsDate.create(isoDateString);
        final String newOffset = getOffsetString(isoDate);
        if (allowOffsetChange && !newOffset.equals(currentOffset)) {
            GWT.log("Offset changed: " + currentOffset + " -> " + newOffset);
            final String newIsoDateString =
                    zeroPad(4, datePickerTime.getUTCFullYear()) +
                            "-" +
                            zeroPad(2, (datePickerTime.getUTCMonth() + 1)) +
                            "-" +
                            zeroPad(2, datePickerTime.getDate()) +
                            "T" +
                            zeroPad(2, hour.getIntValue()) +
                            ":" +
                            zeroPad(2, minute.getIntValue()) +
                            ":" +
                            zeroPad(2, second.getIntValue()) +
                            "." +
                            zeroPad(3, millisecond.getIntValue()) +
                            newOffset;
            final JsDate newIsoDate = JsDate.create(newIsoDateString);
            return newIsoDate;

        } else {
            return isoDate;
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
        final FormatOptions.Builder builder = FormatOptions
                .builder()
                .year(Year.NUMERIC)
                .month(Month.LONG)
                .day(Day.NUMERIC);
        setTimeZone(builder);

        final String dateString =
                IntlDateTimeFormat.format(value, IntlDateTimeFormat.DEFAULT_LOCALE, builder.build());
        this.date.setText(dateString);

        if (currentDateString == null || !currentDateString.equals(dateString)) {
            currentDateString = dateString;
            setDatePickerTime(value);
        }
    }

    private void updateTime() {
        currentOffset = getOffset(value);

        int hour = value.getUTCHours() + currentOffset.getHours();
        if (hour > 23) {
            hour = hour - 24;
        } else if (hour < 0) {
            hour = hour + 24;
        }

        int minute = value.getUTCMinutes() + currentOffset.getMinutes();
        if (minute > 59) {
            minute = minute - 60;
        } else if (minute < 0) {
            minute = minute + 60;
        }

        this.hour.setValue(hour);
        this.minute.setValue(minute);
        this.second.setValue(value.getUTCSeconds());
        this.millisecond.setValue(value.getUTCMilliseconds());

        updateDateLabel();
        updateTimeLabel();
    }

    private void updateTimeLabel() {
        final String timeZone = getTimeZone();
        final FormatOptions.Builder builder = FormatOptions
                .builder()
                .hour(Hour.TWO_DIGIT)
                .minute(Minute.TWO_DIGIT)
                .second(Second.TWO_DIGIT)
                .fractionalSecondDigits(3)
                .timeZoneName(TimeZoneName.SHORT);
        if (timeZone != null) {
            builder.timeZone(timeZone);
        }
        final String timeString =
                IntlDateTimeFormat.format(value, IntlDateTimeFormat.DEFAULT_LOCALE, builder.build());

        time.setText(timeString);
    }

    private String getOffsetString(final JsDate value) {
        final String tz = parseTimeZone(value, TimeZoneName.LONG_OFFSET);
        String offset = tz.replaceAll("GMT", "");
        offset = offset.replaceAll(":", "");
        if (offset.length() == 0) {
            offset = "+0000";
        }
        return offset;
    }

    private Offset getOffset(final JsDate value) {
        final String offsetString = getOffsetString(value);
        int hours = getInt(offsetString.substring(1, 3));
        int minutes = getInt(offsetString.substring(3, 5));
        if (offsetString.charAt(0) == '-') {
            hours = hours * -1;
            minutes = minutes * -1;
        }
        return new Offset(hours, minutes);
    }

    private String parseTimeZone(final JsDate value, final TimeZoneName timeZoneName) {
        final FormatOptions.Builder builder = FormatOptions
                .builder()
                .hour(Hour.TWO_DIGIT)
                .minute(Minute.TWO_DIGIT)
                .second(Second.TWO_DIGIT)
                .fractionalSecondDigits(3)
                .timeZoneName(timeZoneName);
        setTimeZone(builder);

        final String dateTimeString = IntlDateTimeFormat.format(value, EN_LOCALE, builder.build());
        final int index = dateTimeString.indexOf(" ");
        final String tz = dateTimeString.substring(index + 1);

        return tz;
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

    private DateRecord parseDate(final JsDate value) {
        final FormatOptions.Builder builder = FormatOptions
                .builder()
                .year(Year.NUMERIC)
                .month(Month.TWO_DIGIT)
                .day(Day.TWO_DIGIT);
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
                .hour(Hour.TWO_DIGIT)
                .minute(Minute.TWO_DIGIT)
                .second(Second.TWO_DIGIT)
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

    public interface Binder extends UiBinder<Widget, DateTimeViewImpl> {

    }

    private static class Offset {

        private final int hours;
        private final int minutes;

        public Offset(final int hours, final int minutes) {
            this.hours = hours;
            this.minutes = minutes;
        }

        public int getHours() {
            return hours;
        }

        public int getMinutes() {
            return minutes;
        }
    }
}
