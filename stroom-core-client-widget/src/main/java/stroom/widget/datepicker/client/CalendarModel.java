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

/**
 * Model used to get calendar information for {@link CustomDatePicker} and its
 * subclasses.
 */
public class CalendarModel {

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
    private final JsDate currentMonth;

    /**
     * Constructor.
     */
    public CalendarModel() {
        currentMonth = getFirstDayOfMonth(JsDate.create());
    }

    public static JsDate getFirstDayOfMonth(JsDate date) {
        return JsDate.utc(
                date.getUTCFullYear(),
                date.getUTCMonth(),
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
    public String formatDayOfMonth(JsDate date) {
        return dayOfMonthNames[date.getUTCDate()];
    }

    /**
     * Format a day in the week. So, for example "Monday".
     *
     * @param dayInWeek the day in week to format
     * @return the formatted day in week
     */
    public String formatDayOfWeek(int dayInWeek) {
        return dayOfWeekNames[dayInWeek];
    }

    /**
     * Format a month in the year. So, for example "January".
     *
     * @param month A number from 0 (for January) to 11 (for December) identifying the month wanted.
     * @return the formatted month
     */
    public String formatMonth(int month) {
        return monthOfYearNames[month];
    }

    /**
     * Gets the first day of the first week in the currently specified month.
     *
     * @return the first day
     */
    public JsDate getCurrentFirstDayOfFirstWeek() {
        final int wkDayOfMonth1st = currentMonth.getUTCDay();
        final int start = CalendarUtil.getStartingDayOfWeek();
        final JsDate copy = CalendarUtil.copyDate(currentMonth);
        if (wkDayOfMonth1st == start) {
            // always return a copy to allow SimpleCalendarView to adjust first
            // display date
            return copy;
        } else {

            int offset = wkDayOfMonth1st - start > 0
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
    public JsDate getCurrentMonth() {
        return currentMonth;
    }

    /**
     * Is a date in the currently specified month?
     *
     * @param date the date
     * @return date
     */
    public boolean isInCurrentMonth(JsDate date) {
        return currentMonth.getUTCMonth() == date.getUTCMonth();
    }

    /**
     * Sets the currently specified date.
     *
     * @param currentDate the currently specified date
     */
    public void setCurrentMonth(JsDate currentDate) {
        this.currentMonth.setUTCFullYear(currentDate.getUTCFullYear());
        this.currentMonth.setUTCMonth(currentDate.getUTCMonth());
    }

    /**
     * Shifts the currently specified date by the given number of months. The day
     * of the month will be pinned to the original value as far as possible.
     *
     * @param deltaMonths - number of months to be added to the current date
     */
    public void shiftCurrentMonth(int deltaMonths) {
        CalendarUtil.addMonthsToDate(currentMonth, deltaMonths);
        refresh();
    }

    /**
     * Refresh the current model as needed.
     */
    protected void refresh() {
    }
}
