/*
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package stroom.widget.datepicker.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.i18n.client.DateTimeFormatInfo;
import com.google.gwt.i18n.client.LocaleInfo;

/**
 * Useful utilities for creating views of a calendar.
 */
public class CalendarUtil {

    private static int firstDayOfWeekend;
    private static int lastDayOfWeekend;
    private static int startingDay;

    static {
        if (GWT.isClient()) {
            DateTimeFormatInfo dateTimeFormatInfo = LocaleInfo.getCurrentLocale().getDateTimeFormatInfo();
            // Finding the start and end of weekend
            firstDayOfWeekend = dateTimeFormatInfo.weekendStart();
            lastDayOfWeekend = dateTimeFormatInfo.weekendEnd();
            startingDay = dateTimeFormatInfo.firstDayOfTheWeek();
        }
    }

    /**
     * Adds the given number of days to a date.
     *
     * @param date the date
     * @param days number of days
     */
    public static void addDaysToDate(JsDate date, int days) {
        date.setUTCDate(date.getUTCDate() + days);
    }

    /**
     * Adds the given number of months to a date.
     *
     * @param date the date
     * @param months number of months
     */
    public static void addMonthsToDate(JsDate date, int months) {
        if (months != 0) {
            date.setUTCMonth(date.getUTCMonth() + months);
        }
    }

    /**
     * Copies a date.
     *
     * @param date the date
     * @return the copy
     */
    public static JsDate copyDate(JsDate date) {
        if (date == null) {
            return null;
        }
        return JsDate.create(date.getTime());
    }

    /**
     * Returns the number of days between the two dates. Time is ignored.
     *
     * @param start starting date
     * @param finish ending date
     * @return the different
     */
    public static int getDaysBetween(JsDate start, JsDate finish) {
        // Convert the dates to the same time
        start = copyDate(start);
        resetTime(start);
        finish = copyDate(finish);
        resetTime(finish);

        double aTime = start.getTime();
        double bTime = finish.getTime();

        long adjust = 60 * 60 * 1000;
        adjust = (bTime > aTime) ? adjust : -adjust;

        return (int) ((bTime - aTime + adjust) / (24 * 60 * 60 * 1000));
    }

    /**
     * Returns the day of the week on which week starts in the current locale. The
     * range between 0 for Sunday and 6 for Saturday.
     *
     * @return the day of the week
     */
    public static int getStartingDayOfWeek() {
        return startingDay;
    }

    /**
     * Check if two dates represent the same date of the same year, even if they
     * have different times.
     *
     * @param date0 a date
     * @param date1 a second date
     * @return true if the dates are the same
     */
    public static boolean isSameDate(JsDate date0, JsDate date1) {
        assert date0 != null : "date0 cannot be null";
        assert date1 != null : "date1 cannot be null";
        return date0.getUTCFullYear() == date1.getUTCFullYear()
                && date0.getUTCMonth() == date1.getUTCMonth()
                && date0.getUTCDate() == date1.getUTCDate();
    }

    /**
     * Sets a date object to be at the beginning of the month and no time
     * specified.
     *
     * @param date the date
     */
    public static void setToFirstDayOfMonth(JsDate date) {
        resetTime(date);
        date.setUTCDate(1);
    }

    /**
     * Is a day in the week a weekend?
     *
     * @param dayOfWeek day of week
     * @return is the day of week a weekend?
     */
    static boolean isWeekend(int dayOfWeek) {
        return dayOfWeek == firstDayOfWeekend || dayOfWeek == lastDayOfWeekend;
    }

    /**
     * Resets the date to have no time modifiers. Note that the hour might not be zero if the time
     * hits a DST transition date.
     *
     * @param date the date
     */
    public static void resetTime(JsDate date) {
        date.setUTCHours(0);
        date.setUTCMinutes(0);
        date.setUTCSeconds(0);
        date.setUTCMilliseconds(0);
    }
}
