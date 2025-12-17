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

import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Day;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Hour;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Minute;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Month;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Second;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.TimeZoneName;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Year;
import stroom.widget.util.client.ClientStringUtil;

public class DateTimeModel {

    private static final String[] EN_LOCALE = {"en-GB"};

    public static final long MILLIS_IN_SECOND = 1000;
    public static final long MILLIS_IN_MINUTE = 60 * MILLIS_IN_SECOND;
    public static final long MILLIS_IN_HOUR = 60 * MILLIS_IN_MINUTE;














    private static final DateTimeConstants DATE_TIME_CONSTANTS = new DateTimeConstants();

    /**
     * The number of weeks normally displayed in a month.
     */
    public static final int WEEKS_IN_MONTH = 6;

    /**
     * Number of days normally displayed in a week.
     */
    public static final int DAYS_IN_WEEK = 7;

    public static final int MONTHS_IN_YEAR = 12;

    private static final int MAX_DAYS_IN_MONTH = 31;

    private final String[] dayOfWeekNames = DATE_TIME_CONSTANTS.getDayOfWeekNames();
    private final String[] dayOfMonthNames = DATE_TIME_CONSTANTS.getDayOfMonthNames();
    private final String[] monthOfYearNames = DATE_TIME_CONSTANTS.getMonthNames();
    private final UTCDate currentMonth;

    /**
     * Constructor.
     */
    public DateTimeModel() {
        currentMonth = getFirstDayOfMonth(UTCDate.create());
    }

    public static UTCDate getFirstDayOfMonth(final UTCDate date) {
        return UTCDate.create(
                date.getFullYear(),
                date.getMonth(),
                1,
                0,
                0,
                0,
                0);
    }

    /**
     * Formats a date's day of month. For example "1".
     *
     * @param date the date
     * @return the formated day of month
     */
    public String formatDayOfMonth(final UTCDate date) {
        return dayOfMonthNames[date.getDate()];
    }

    /**
     * Format a day in the week. So, for example "Monday".
     *
     * @param dayInWeek the day in week to format
     * @return the formatted day in week
     */
    public String formatDayOfWeek(final int dayInWeek) {
        return dayOfWeekNames[dayInWeek];
    }

    /**
     * Format a month in the year. So, for example "January".
     *
     * @param month A number from 0 (for January) to 11 (for December) identifying the month wanted.
     * @return the formatted month
     */
    public String formatMonth(final int month) {
        return monthOfYearNames[month];
    }

    /**
     * Gets the first day of the first week in the currently specified month.
     *
     * @return the first day
     */
    public UTCDate getCurrentFirstDayOfFirstWeek() {
        final int wkDayOfMonth1st = currentMonth.getDay();
        final int start = CalendarUtil.getStartingDayOfWeek();
        final UTCDate copy = CalendarUtil.copyDate(currentMonth);
        if (wkDayOfMonth1st == start) {
            // always return a copy to allow SimpleCalendarView to adjust first
            // display date
            return copy;
        } else {

            final int offset = wkDayOfMonth1st - start > 0
                    ? wkDayOfMonth1st - start
                    : DAYS_IN_WEEK - (start - wkDayOfMonth1st);
            CalendarUtil.addDaysToDate(copy, -offset);
            return copy;
        }
    }

    /**
     * Gets the date representation of the currently specified month. Used to
     * access both the month and year information.
     *
     * @return the month and year
     */
    public UTCDate getCurrentMonth() {
        return currentMonth;
    }

    /**
     * Is a date in the currently specified month?
     *
     * @param date the date
     * @return date
     */
    public boolean isInCurrentMonth(final UTCDate date) {
        return currentMonth.getMonth() == date.getMonth();
    }

    /**
     * Sets the currently specified date.
     *
     * @param currentDate the currently specified date
     */
    public void setCurrentMonth(final UTCDate currentDate) {
        this.currentMonth.setFullYear(currentDate.getFullYear());
        this.currentMonth.setMonth(currentDate.getMonth());
    }

    /**
     * Shifts the currently specified date by the given number of months. The day
     * of the month will be pinned to the original value as far as possible.
     *
     * @param deltaMonths - number of months to be added to the current date
     */
    public void shiftCurrentMonth(final int deltaMonths) {
        CalendarUtil.addMonthsToDate(currentMonth, deltaMonths);
//        refresh();
    }

    public UTCDate getTodayUTC() {
        final DateRecord today = parseDate(UTCDate.create());
        return UTCDate.create(
                today.getYear(),
                today.getMonth(),
                today.getDay(),
                0,
                0,
                0,
                0);
    }





























    public String formatDateLabel(final UTCDate value) {
        final FormatOptions.Builder builder = FormatOptions
                .builder()
                .year(Year.NUMERIC)
                .month(Month.LONG)
                .day(Day.NUMERIC);
        setTimeZone(builder);

        return IntlDateTimeFormat.format(value, IntlDateTimeFormat.DEFAULT_LOCALE, builder.build());
    }

    public String formatTimeLabel(final UTCDate value) {
        final FormatOptions.Builder builder = FormatOptions
                .builder()
                .hour(Hour.TWO_DIGIT)
                .minute(Minute.TWO_DIGIT)
                .second(Second.TWO_DIGIT)
                .fractionalSecondDigits(3)
                .timeZoneName(TimeZoneName.SHORT);
        setTimeZone(builder);
        return IntlDateTimeFormat.format(value, IntlDateTimeFormat.DEFAULT_LOCALE, builder.build());
    }

    public String formatIso(final UTCDate value) {
        final long offsetMillis = getOffsetMillis(value);
        final UTCDate adjusted = UTCDate.create(value.getTime());
        adjusted.setTime(value.getTime() + offsetMillis);
        final StringBuilder sb = new StringBuilder();
        sb.append(ClientStringUtil.zeroPad(4, adjusted.getFullYear()));
        sb.append("-");
        sb.append(ClientStringUtil.zeroPad(2, adjusted.getMonth() + 1));
        sb.append("-");
        sb.append(ClientStringUtil.zeroPad(2, adjusted.getDate()));
        sb.append("T");
        sb.append(ClientStringUtil.zeroPad(2, adjusted.getHours()));
        sb.append(":");
        sb.append(ClientStringUtil.zeroPad(2, adjusted.getMinutes()));
        sb.append(":");
        sb.append(ClientStringUtil.zeroPad(2, adjusted.getSeconds()));
        sb.append(".");
        sb.append(ClientStringUtil.zeroPad(3, adjusted.getMilliseconds()));
        if (offsetMillis == 0) {
            sb.append("Z");
        } else {
            sb.append(getOffsetString(value));
        }
        return sb.toString();
    }

    public String getOffsetString(final UTCDate value) {
        final String tz = parseTimeZone(value);
        String offset = tz.replaceAll("GMT", "");
        offset = offset.replaceAll(":", "");
        if (offset.length() == 0) {
            offset = "+0000";
        }
        return offset;
    }

    public TimeOffset getOffset(final UTCDate value) {
        final String offsetString = getOffsetString(value);
        int hours = ClientStringUtil.getInt(offsetString.substring(1, 3));
        int minutes = ClientStringUtil.getInt(offsetString.substring(3, 5));
        if (offsetString.charAt(0) == '-') {
            hours = hours * -1;
            minutes = minutes * -1;
        }
        return new TimeOffset(hours, minutes);
    }

    public long getOffsetMillis(final UTCDate value) {
        final String offsetString = getOffsetString(value);
        final int hours = ClientStringUtil.getInt(offsetString.substring(1, 3));
        final int minutes = ClientStringUtil.getInt(offsetString.substring(3, 5));
        long millis = (hours * MILLIS_IN_HOUR) + (minutes * MILLIS_IN_MINUTE);
        if (offsetString.charAt(0) == '-') {
            millis = millis * -1;
        }
        return millis;
    }

    public String parseTimeZone(final UTCDate value) {
        final FormatOptions.Builder builder = FormatOptions
                .builder()
                .hour(Hour.TWO_DIGIT)
                .minute(Minute.TWO_DIGIT)
                .second(Second.TWO_DIGIT)
                .fractionalSecondDigits(3)
                .timeZoneName(TimeZoneName.LONG_OFFSET);
        setTimeZone(builder);

        final String dateTimeString = IntlDateTimeFormat.format(value, EN_LOCALE, builder.build());
        final int index = dateTimeString.indexOf(" ");
        return dateTimeString.substring(index + 1);
    }

    private void setTimeZone(final FormatOptions.Builder builder) {
        final String timeZone = ClientTimeZone.getTimeZone();
        if (timeZone != null) {
            builder.timeZone(timeZone);
        }
    }

    public DateRecord parseDate(final UTCDate value) {
        final FormatOptions.Builder builder = FormatOptions
                .builder()
                .year(Year.NUMERIC)
                .month(Month.TWO_DIGIT)
                .day(Day.TWO_DIGIT);
        setTimeZone(builder);

        final String dateString = IntlDateTimeFormat
                .format(value, EN_LOCALE, builder.build());

        final String[] dateParts = dateString.split("/");
        final int day = ClientStringUtil.getInt(dateParts[0]);
        final int month = ClientStringUtil.getInt(dateParts[1]) - 1;
        final int year = ClientStringUtil.getInt(dateParts[2]);
        return new DateRecord(year, month, day);
    }

    public TimeRecord parseTime(final UTCDate value) {
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
        final int hour = ClientStringUtil.getInt(parts[0]);
        final int minute = ClientStringUtil.getInt(parts[1]);
        final int second = ClientStringUtil.getInt(secondParts[0]);
        final int millisecond = ClientStringUtil.getInt(secondParts[1]);
        return new TimeRecord(hour, minute, second, millisecond);
    }
}
