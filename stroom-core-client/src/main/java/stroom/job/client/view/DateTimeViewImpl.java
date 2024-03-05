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

    private final long MILLIS_IN_SECOND = 1000;
    private final long MILLIS_IN_MINUTE = 60 * MILLIS_IN_SECOND;
    private final long MILLIS_IN_HOUR = 60 * MILLIS_IN_MINUTE;

    private final UserPreferencesManager userPreferencesManager;

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

    private JsDate value;
    private DateTimeRecord currentDateTime;

    @Inject
    public DateTimeViewImpl(final Binder binder,
                            final UserPreferencesManager userPreferencesManager) {
        this.userPreferencesManager = userPreferencesManager;

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

        setTime((long) JsDate.create().getTime());
    }

//    private JsDate getCurrentDate() {
//        final JsDate date = JsDate.create(datePicker.getValue().getTime());
//        date.setHours(hour.getIntValue());
//        date.setMinutes(minute.getIntValue());
//        date.setSeconds(second.getIntValue());
//        date.setMilliseconds(millisecond.getIntValue());
//
////        for (final TimeZoneName timeZoneName : TimeZoneName.values()) {
////            final String offset = IntlDateTimeFormat.format(date, IntlDateTimeFormat.DEFAULT_LOCALE,
////                    FormatOptions
////                            .builder()
////                            .locale("en-GB")
////                            .timeZone(timeZone.getValue())
////                            .hour(Hour.TWO_DIGIT)
////                            .minute(Minute.TWO_DIGIT)
////                            .timeZoneName(timeZoneName)
////                            .build());
////            GWT.log(timeZoneName + " = " + offset);
////        }
//
//        final String offset2 = IntlDateTimeFormat.format(date, IntlDateTimeFormat.DEFAULT_LOCALE,
//                FormatOptions
//                        .builder()
//                        .locale("en-GB")
//                        .hour(Hour.TWO_DIGIT)
//                        .minute(Minute.TWO_DIGIT)
//                        .timeZoneName(TimeZoneName.SHORT_OFFSET)
//                        .build());
//        final int index = offset2.lastIndexOf(" ");
//        if (index != -1) {
//            String offsetString = offset2.substring(index + 1);
//            final int plusIndex = offsetString.indexOf("+");
//            final int minusIndex = offsetString.indexOf("-");
//            Boolean plus = null;
//            if (plusIndex != -1) {
//                offsetString = offsetString.substring(plusIndex + 1);
//                plus = true;
//            } else if (minusIndex != -1) {
//                offsetString = offsetString.substring(minusIndex + 1);
//                plus = false;
//            }
//            if (plus != null) {
//                final int separatorIndex = offsetString.indexOf(":");
//                final int milliseconds;
//                if (separatorIndex != -1) {
//                    final String hourString = offsetString.substring(0, separatorIndex);
//                    final String minuteString = offsetString.substring(separatorIndex + 1);
//                    final int hours = Integer.parseInt(hourString);
//                    final int minutes = Integer.parseInt(minuteString);
//                    milliseconds = (hours * 60 * 60 * 1000) + (minutes * 60 * 1000);
//                } else {
//                    final int hours = Integer.parseInt(offsetString);
//                    milliseconds = hours * 60 * 60 * 1000;
//                }
//
//                if (plus) {
//                    date.setTime(date.getTime() - milliseconds);
//                } else {
//                    date.setTime(date.getTime() + milliseconds);
//                }
//            }
//        }
//
//
////        final String offset2 = IntlDateTimeFormat.format(date, IntlDateTimeFormat.DEFAULT_LOCALE,
////                FormatOptions
////                        .builder()
////                        .locale("en-GB")
////                        .timeZone(timeZone.getValue())
////                        .hour(Hour.TWO_DIGIT)
////                        .minute(Minute.TWO_DIGIT)
////                        .timeZoneName(TimeZoneName.SHORT_OFFSET)
////                        .build());
////        GWT.log("offset2 = " + offset2);
//
////        final int offset3 = IntlDateTimeFormat.getTimeZoneOffset(date, timeZone.getValue());
////        GWT.log("offset3 = " + offset3);
//
//        return date;
//    }

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
//
//    private void setDateFields(final JsDate date) {
//
//    }

//    private void setTimeFields(final JsDate date) {
//        final Offset offset = getOffset(date);
//        lastHour =
//
//        hour.setValue(date.getUTCHours() + offset.getHours());
//        minute.setValue(date.getUTCMinutes() + offset.getMinutes());
//        second.setValue(date.getUTCSeconds());
//        millisecond.setValue(date.getUTCMilliseconds());
//    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        hour.focus();
    }

    @UiHandler("today")
    public void onToday(final ClickEvent event) {
        final JsDate newDate = JsDate.create();
        datePicker.setCurrentMonth(newDate);
        datePicker.setValue(newDate);
        setDate();
    }

    @UiHandler("yesterday")
    public void onYesterday(final ClickEvent event) {
        final JsDate newDate = JsDate.create();
        final int date = newDate.getDate();
        newDate.setDate(date - 1);
        datePicker.setCurrentMonth(newDate);
        datePicker.setValue(newDate);
        setDate();
    }

    @UiHandler("weekStart")
    public void onWeekStart(final ClickEvent event) {
        final JsDate newDate = JsDate.create();
        final int date = newDate.getDate();
        newDate.setDate(date - newDate.getDay());
        datePicker.setCurrentMonth(newDate);
        datePicker.setValue(newDate);
        setDate();
    }

    @UiHandler("datePicker")
    public void onDatePicker(final ValueChangeEvent<JsDate> event) {
        setDate();
    }

    @UiHandler("now")
    public void onNow(final ClickEvent event) {
        this.value = fixDate(JsDate.create());
        updateTime(value);
    }

    @UiHandler("midnight")
    public void onMidnight(final ClickEvent event) {
        value.setTime(fixDate(value).getTime() -
                (currentDateTime.getHour() * MILLIS_IN_HOUR) -
                (currentDateTime.getMinute() * MILLIS_IN_MINUTE) -
                (currentDateTime.getSecond() * MILLIS_IN_SECOND) -
                currentDateTime.getMillisecond());
        updateTime(value);
    }

    @UiHandler("midday")
    public void onMidday(final ClickEvent event) {
        value.setTime(fixDate(value).getTime() -
                (currentDateTime.getHour() * MILLIS_IN_HOUR) -
                (currentDateTime.getMinute() * MILLIS_IN_MINUTE) -
                (currentDateTime.getSecond() * MILLIS_IN_SECOND) -
                currentDateTime.getMillisecond() +
                (12 * MILLIS_IN_HOUR));
        updateTime(value);
    }

    @UiHandler("hour")
    public void onHour(final ValueChangeEvent<Long> event) {
        int diff = hour.getIntValue() - currentDateTime.getHour();
        value.setTime(fixDate(value).getTime() + (diff * MILLIS_IN_HOUR));
        updateTime(value);
    }

    @UiHandler("hour")
    public void onIncreaseHour(final IncreaseEvent event) {
        value.setTime(fixDate(value).getTime() + MILLIS_IN_HOUR);
        updateTime(value);
    }

    @UiHandler("hour")
    public void onDecreaseHour(final DecreaseEvent event) {
        value.setTime(fixDate(value).getTime() - MILLIS_IN_HOUR);
        updateTime(value);
    }

    @UiHandler("minute")
    public void onMinute(final ValueChangeEvent<Long> event) {
        int diff = minute.getIntValue() - currentDateTime.getMinute();
        value.setTime(fixDate(value).getTime() + (diff * MILLIS_IN_MINUTE));
        updateTime(value);
    }

    @UiHandler("minute")
    public void onIncreaseMinute(final IncreaseEvent event) {
        value.setTime(fixDate(value).getTime() + MILLIS_IN_MINUTE);
        updateTime(value);
    }

    @UiHandler("minute")
    public void onDecreaseMinute(final DecreaseEvent event) {
        value.setTime(fixDate(value).getTime() - MILLIS_IN_MINUTE);
        updateTime(value);
    }

    @UiHandler("second")
    public void onSecond(final ValueChangeEvent<Long> event) {
        int diff = second.getIntValue() - currentDateTime.getSecond();
        value.setTime(fixDate(value).getTime() + (diff * MILLIS_IN_SECOND));
        updateTime(value);
    }

    @UiHandler("second")
    public void onIncreaseSecond(final IncreaseEvent event) {
        value.setTime(fixDate(value).getTime() + MILLIS_IN_SECOND);
        updateTime(value);
    }

    @UiHandler("second")
    public void onDecreaseSecond(final DecreaseEvent event) {
        value.setTime(fixDate(value).getTime() - MILLIS_IN_SECOND);
        updateTime(value);
    }

    @UiHandler("millisecond")
    public void onMillisecond(final ValueChangeEvent<Long> event) {
        int diff = millisecond.getIntValue() - currentDateTime.getMillisecond();
        value.setTime(fixDate(value).getTime() + diff);
        updateTime(value);
    }

    @UiHandler("millisecond")
    public void onIncreaseMillisecond(final IncreaseEvent event) {
        value.setTime(fixDate(value).getTime() + 1);
        updateTime(value);
    }

    @UiHandler("millisecond")
    public void onDecreaseMillisecond(final DecreaseEvent event) {
        value.setTime(fixDate(value).getTime() - 1);
        updateTime(value);
    }

    private void setDate() {
        setTime(fixDate(value));
    }

    private JsDate fixDate(final JsDate value) {
        value.setFullYear(datePicker.getValue().getFullYear());
        value.setMonth(datePicker.getValue().getMonth());
        value.setDate(datePicker.getValue().getDate());
        return value;
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

    private void update(final JsDate date) {
        updateDate(date);
        updateTime(date);
    }

    private void updateDate(final JsDate date) {
        datePicker.setCurrentMonth(date);
        datePicker.setValue(date);

        final FormatOptions options = FormatOptions
                .builder()
                .year(Year.NUMERIC)
                .month(Month.LONG)
                .day(Day.NUMERIC)
                .build();
        this.date.setText(IntlDateTimeFormat.format(datePicker.getValue(), IntlDateTimeFormat.DEFAULT_LOCALE, options));
    }

    private void updateTime(final JsDate date) {
        final FormatOptions.Builder builder = FormatOptions
                .builder()
                .hour(Hour.NUMERIC)
                .minute(Minute.NUMERIC)
                .second(Second.NUMERIC)
                .fractionalSecondDigits(3)
                .timeZoneName(TimeZoneName.SHORT);

        final String timeZone = getTimeZone();
        if (timeZone != null) {
            builder.timeZone(timeZone);
        }

        final String dateTime = IntlDateTimeFormat
                .format(date, IntlDateTimeFormat.DEFAULT_LOCALE, builder.build());

        final int year = datePicker.getValue().getFullYear();
        final int month = datePicker.getValue().getMonth();
        final int day = datePicker.getValue().getDate();

        String trimmed = dateTime;
        final int timeZoneIndex = dateTime.indexOf(" ");
        if (timeZoneIndex != -1) {
            trimmed = trimmed.substring(0, timeZoneIndex);
        }
        final String[] parts = trimmed.split(":");
        final String[] secondParts = parts[2].split("\\.");
        final int hour = getInt(parts[0]);
        final int minute = getInt(parts[1]);
        final int second = getInt(secondParts[0]);
        final int millisecond = getInt(secondParts[1]);

        currentDateTime = new DateTimeRecord(year, month, day, hour, minute, second, millisecond);

        this.hour.setValue(hour);
        this.minute.setValue(minute);
        this.second.setValue(second);
        this.millisecond.setValue(millisecond);

        time.setText(dateTime);
    }

//    private Offset getOffset(final JsDate date) {
//        final FormatOptions.Builder builder = FormatOptions
//                .builder()
//                .locale("en-GB")
//                .hour(Hour.TWO_DIGIT)
//                .minute(Minute.TWO_DIGIT)
//                .timeZoneName(TimeZoneName.SHORT_OFFSET);
//        final String timeZone = getTimeZone();
//        if (timeZone != null) {
//            builder.timeZone(timeZone);
//        }
//
//        final String dateString = IntlDateTimeFormat.format(date, IntlDateTimeFormat.DEFAULT_LOCALE, builder.build());
//        final int index = dateString.lastIndexOf(" ");
//        if (index != -1) {
//            String offsetString = dateString.substring(index + 1);
//            final int plusIndex = offsetString.indexOf("+");
//            final int minusIndex = offsetString.indexOf("-");
//            Boolean plus = null;
//            if (plusIndex != -1) {
//                offsetString = offsetString.substring(plusIndex + 1);
//                plus = true;
//            } else if (minusIndex != -1) {
//                offsetString = offsetString.substring(minusIndex + 1);
//                plus = false;
//            }
//            if (plus != null) {
//                final int separatorIndex = offsetString.indexOf(":");
//                final int milliseconds;
//                if (separatorIndex != -1) {
//                    final String hourString = offsetString.substring(0, separatorIndex);
//                    final String minuteString = offsetString.substring(separatorIndex + 1);
//                    final int hours = Integer.parseInt(hourString);
//                    final int minutes = Integer.parseInt(minuteString);
//
//                    if (plus) {
//                        return new Offset(hours, minutes);
//                    }
//                    return new Offset(-hours, -minutes);
//                } else {
//                    final int hours = Integer.parseInt(offsetString);
//                    if (plus) {
//                        return new Offset(hours, 0);
//                    }
//                    return new Offset(-hours, 0);
//                }
//            }
//        }
//        return new Offset(0, 0);
//    }
//
//    private int getHours(final JsDate date) {
//        final FormatOptions.Builder builder = FormatOptions
//                .builder()
//                .locale(EN)
//                .hour(Hour.TWO_DIGIT);
//        return getPart(builder, date);
//    }
//
//    private int getMinutes(final JsDate date) {
//        final FormatOptions.Builder builder = FormatOptions
//                .builder()
//                .locale(EN)
//                .minute(Minute.TWO_DIGIT);
//        return getPart(builder, date);
//    }
//
//    private int getPart(final JsDate date) {
//        final FormatOptions.Builder builder = FormatOptions
//                .builder()
//                .locale(EN)
//                .hour(Hour.TWO_DIGIT)
//                .minute(Minute.TWO_DIGIT)
//                .second(Second.TWO_DIGIT)
//                .fractionalSecondDigits(3);
//
//        final String timeZone = getTimeZone();
//        if (timeZone != null) {
//            builder.timeZone(timeZone);
//        }
//
//        final String dateString = IntlDateTimeFormat.format(date, IntlDateTimeFormat.DEFAULT_LOCALE, builder.build());
//        final String trimmed = removeLeadingZeros(dateString);
//        return Integer.parseInt(trimmed);
//    }

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
                String hours = "" + userTimeZone.getOffsetHours();
                hours = zeroPad(2, hours);

                String minutes = "" + userTimeZone.getOffsetMinutes();
                minutes = zeroPad(2, minutes);

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
//
//    private static class Offset {
//
//        private final int hours;
//        private final int minutes;
//
//        public Offset(final int hours, final int minutes) {
//            this.hours = hours;
//            this.minutes = minutes;
//        }
//
//        public int getHours() {
//            return hours;
//        }
//
//        public int getMinutes() {
//            return minutes;
//        }
//    }

    public interface Binder extends UiBinder<Widget, DateTimeViewImpl> {

    }
}
