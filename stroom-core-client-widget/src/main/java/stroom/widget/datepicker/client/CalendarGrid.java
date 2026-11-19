/*
 * Copyright 2026 Crown Copyright
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
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Month;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Weekday;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Year;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.TableCellElement;
import com.google.gwt.dom.client.TableElement;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.dom.client.TableSectionElement;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;

/// The days of a [Calendar]'s month: a WAI-ARIA grid (a `table`, `role="grid"`, named for
/// the month it shows) of six weeks, each day a `gridcell` named by its full date.
///
/// The grid is one tab stop: the focus is on a day (a roving `tabindex`), and the keys move it
/// (Left and Right a day, Up and Down a week, Home and End to the start and end of its week, Page
/// Up and Page Down a month, Shift+Page Up and Shift+Page Down a year), showing the month the day
/// is in. Enter or Space chooses the day with the focus, as a click chooses the day clicked.
///
/// States come from ARIA attributes: the chosen day is `aria-selected`, today is
/// `aria-current="date"`. A day of the month before or after, shown to fill the weeks, is the
/// `calendar__day--outside` modifier, which has no ARIA meaning.
public final class CalendarGrid extends Widget {

    private static final String BLOCK = "calendar";
    private static final String GRID_CLASS_NAME = BLOCK + "__grid";
    private static final String WEEKDAY_CLASS_NAME = BLOCK + "__weekday";
    private static final String DAY_CLASS_NAME = BLOCK + "__day";
    private static final String OUTSIDE_CLASS_NAME = DAY_CLASS_NAME + "--outside";
    private static final int CELL_COUNT = DateTimeModel.WEEKS_IN_MONTH * DateTimeModel.DAYS_IN_WEEK;

    private final TableSectionElement head;
    private final List<TableCellElement> cells = new ArrayList<>(CELL_COUNT);
    // The day shown in each cell, at midnight UTC
    private final List<UTCDate> dates = new ArrayList<>(CELL_COUNT);
    // The day with the roving tab stop (and the focus, while the grid has it), at midnight UTC
    private UTCDate focusDate;
    private Calendar datePicker;

    /// Makes the grid, for a template; its calendar gives it itself ([#setDatePicker]).
    public CalendarGrid() {
        final Document document = Document.get();
        final TableElement table = document.createTableElement();
        table.setClassName(GRID_CLASS_NAME);
        table.setAttribute("role", "grid");

        // The weekdays' names, filled in once it has its calendar (setDatePicker)
        head = document.createTHeadElement();
        table.appendChild(head);

        final TableSectionElement body = document.createTBodyElement();
        for (int week = 0; week < DateTimeModel.WEEKS_IN_MONTH; week++) {
            final TableRowElement row = document.createTRElement();
            for (int column = 0; column < DateTimeModel.DAYS_IN_WEEK; column++) {
                final TableCellElement cell = document.createTDElement();
                cell.setClassName(DAY_CLASS_NAME);
                cell.setAttribute("role", "gridcell");
                cell.setTabIndex(-1);
                row.appendChild(cell);
                cells.add(cell);
                dates.add(UTCDate.create());
            }
            body.appendChild(row);
        }
        table.appendChild(body);

        setElement(table);
        sinkEvents(Event.ONCLICK | Event.ONKEYDOWN);
    }

    /// @param datePicker The calendar the grid belongs to, which holds the month shown and the
    ///                   chosen day.
    void setDatePicker(final Calendar datePicker) {
        if (this.datePicker != null) {
            throw new IllegalStateException("The grid already has its calendar");
        }
        this.datePicker = datePicker;
        final Document document = Document.get();
        final TableRowElement headRow = document.createTRElement();
        for (int column = 0; column < DateTimeModel.DAYS_IN_WEEK; column++) {
            final int dayOfWeek = (column + CalendarUtil.getStartingDayOfWeek()) % DateTimeModel.DAYS_IN_WEEK;
            final TableCellElement weekday = document.createTHElement();
            weekday.setClassName(WEEKDAY_CLASS_NAME);
            weekday.setAttribute("scope", "col");
            // The full name, for screen readers, as the column's header
            weekday.setAttribute("abbr", getModel().formatDayOfWeekLong(dayOfWeek));
            weekday.setInnerText(getModel().formatDayOfWeek(dayOfWeek));
            headRow.appendChild(weekday);
        }
        head.appendChild(headRow);
    }

    /// Shows the picker's month, keeping the day with the tab stop in it: the day it was on, if
    /// that is in the month; else the chosen day, if that is; else today, if that is; else the
    /// same day of the month (or the month's last day, if it is shorter).
    void refresh() {
        final DateTimeModel model = getModel();
        final UTCDate first = model.getCurrentFirstDayOfFirstWeek();
        if (first.getDate() == 1) {
            // Show the last week of the month before, as a month that starts on the first day of
            // the week would otherwise show none of it
            CalendarUtil.addDaysToDate(first, -DateTimeModel.DAYS_IN_WEEK);
        }

        focusDate = chooseFocusDate();

        final UTCDate value = datePicker.getValue();
        final UTCDate today = model.getTodayUTC();
        for (int i = 0; i < CELL_COUNT; i++) {
            final UTCDate date = UTCDate.create(first.getTime());
            CalendarUtil.addDaysToDate(date, i);
            dates.set(i, date);

            final TableCellElement cell = cells.get(i);
            cell.setInnerText(model.formatDayOfMonth(date));
            cell.setAttribute("aria-label", formatFullDate(date));
            cell.setAttribute("aria-selected", String.valueOf(isSameDay(date, value)));
            if (isSameDay(date, today)) {
                cell.setAttribute("aria-current", "date");
            } else {
                cell.removeAttribute("aria-current");
            }
            if (model.isInCurrentMonth(date)) {
                cell.removeClassName(OUTSIDE_CLASS_NAME);
            } else {
                cell.addClassName(OUTSIDE_CLASS_NAME);
            }
            cell.setTabIndex(isSameDay(date, focusDate)
                    ? 0
                    : -1);
        }

        getElement().setAttribute("aria-label", model.formatMonth(model.getCurrentMonth().getMonth())
                                                + " " + model.getCurrentMonth().getFullYear());
    }

    /// Marks the chosen day, without changing the month shown, and gives it the tab stop if it is
    /// in that month.
    ///
    /// @param value The chosen day, or null for none.
    void setSelected(final UTCDate value) {
        if (isInShownMonth(value)) {
            focusDate = UTCDate.create(value.getTime());
        }
        for (int i = 0; i < CELL_COUNT; i++) {
            final UTCDate date = dates.get(i);
            cells.get(i).setAttribute("aria-selected", String.valueOf(isSameDay(date, value)));
            cells.get(i).setTabIndex(isSameDay(date, focusDate)
                    ? 0
                    : -1);
        }
    }

    /// Puts the focus on the day with the tab stop.
    public void focus() {
        final int index = indexOf(focusDate);
        if (index != -1) {
            cells.get(index).focus();
        }
    }

    @Override
    public void onBrowserEvent(final Event event) {
        super.onBrowserEvent(event);
        if (event.getTypeInt() == Event.ONCLICK) {
            final int index = cellIndexOf(event);
            if (index != -1) {
                choose(dates.get(index));
            }
        } else if (event.getTypeInt() == Event.ONKEYDOWN) {
            onKeyDown(event);
        }
    }

    private void onKeyDown(final NativeEvent event) {
        if (event.getAltKey() || event.getCtrlKey() || event.getMetaKey()) {
            return;
        }
        final UTCDate target = UTCDate.create(focusDate.getTime());
        final int dayOfWeek = (focusDate.getDay() - CalendarUtil.getStartingDayOfWeek()
                               + DateTimeModel.DAYS_IN_WEEK) % DateTimeModel.DAYS_IN_WEEK;
        switch (event.getKeyCode()) {
            case KeyCodes.KEY_LEFT:
                CalendarUtil.addDaysToDate(target, -1);
                break;
            case KeyCodes.KEY_RIGHT:
                CalendarUtil.addDaysToDate(target, 1);
                break;
            case KeyCodes.KEY_UP:
                CalendarUtil.addDaysToDate(target, -DateTimeModel.DAYS_IN_WEEK);
                break;
            case KeyCodes.KEY_DOWN:
                CalendarUtil.addDaysToDate(target, DateTimeModel.DAYS_IN_WEEK);
                break;
            case KeyCodes.KEY_HOME:
                CalendarUtil.addDaysToDate(target, -dayOfWeek);
                break;
            case KeyCodes.KEY_END:
                CalendarUtil.addDaysToDate(target, DateTimeModel.DAYS_IN_WEEK - 1 - dayOfWeek);
                break;
            case KeyCodes.KEY_PAGEUP:
                moveFocus(addMonths(focusDate, event.getShiftKey()
                        ? -DateTimeModel.MONTHS_IN_YEAR
                        : -1), event);
                return;
            case KeyCodes.KEY_PAGEDOWN:
                moveFocus(addMonths(focusDate, event.getShiftKey()
                        ? DateTimeModel.MONTHS_IN_YEAR
                        : 1), event);
                return;
            case KeyCodes.KEY_ENTER:
            case KeyCodes.KEY_SPACE:
                event.preventDefault();
                choose(focusDate);
                return;
            default:
                return;
        }
        moveFocus(target, event);
    }

    private void moveFocus(final UTCDate date, final NativeEvent event) {
        event.preventDefault();
        focusDate = date;
        if (!isInShownMonth(date)) {
            // Show the month the day is in (which refreshes the grid, keeping the day)
            datePicker.setCurrentMonth(date);
        } else {
            final int index = indexOf(date);
            for (int i = 0; i < CELL_COUNT; i++) {
                cells.get(i).setTabIndex(i == index
                        ? 0
                        : -1);
            }
        }
        focus();
    }

    private void choose(final UTCDate date) {
        focusDate = UTCDate.create(date.getTime());
        datePicker.setValue(date, true);
        if (!isInShownMonth(date)) {
            datePicker.setCurrentMonth(date);
        }
        focus();
    }

    private UTCDate chooseFocusDate() {
        final DateTimeModel model = getModel();
        if (isInShownMonth(focusDate)) {
            return focusDate;
        }
        final UTCDate value = datePicker.getValue();
        if (isInShownMonth(value)) {
            return UTCDate.create(value.getTime());
        }
        final UTCDate today = model.getTodayUTC();
        if (isInShownMonth(today)) {
            return today;
        }
        final UTCDate month = model.getCurrentMonth();
        final int day = focusDate != null
                ? focusDate.getDate()
                : 1;
        return UTCDate.create(month.getFullYear(), month.getMonth(),
                Math.min(day, daysInMonth(month.getFullYear(), month.getMonth())));
    }

    private boolean isInShownMonth(final UTCDate date) {
        final UTCDate month = getModel().getCurrentMonth();
        return date != null
               && date.getFullYear() == month.getFullYear()
               && date.getMonth() == month.getMonth();
    }

    private int cellIndexOf(final Event event) {
        final Element target = event.getEventTarget().cast();
        for (int i = 0; i < CELL_COUNT; i++) {
            if (cells.get(i).isOrHasChild(target)) {
                return i;
            }
        }
        return -1;
    }

    private int indexOf(final UTCDate date) {
        for (int i = 0; i < CELL_COUNT; i++) {
            if (isSameDay(dates.get(i), date)) {
                return i;
            }
        }
        return -1;
    }

    private DateTimeModel getModel() {
        return datePicker.getModel();
    }

    /// @return The date's full name, e.g. "Monday 15 January 2024", the date being midnight UTC.
    private static String formatFullDate(final UTCDate date) {
        final FormatOptions options = FormatOptions.builder()
                .weekday(Weekday.LONG)
                .day(Day.NUMERIC)
                .month(Month.LONG)
                .year(Year.NUMERIC)
                .timeZone("UTC")
                .build();
        return IntlDateTimeFormat.format(date, IntlDateTimeFormat.DEFAULT_LOCALE, options);
    }

    /// @return The same day of the month the given number of months on, or the month's last day if
    /// it is shorter (31 January plus a month is 28 or 29 February).
    static UTCDate addMonths(final UTCDate date, final int months) {
        final UTCDate month = UTCDate.create(date.getFullYear(), date.getMonth() + months, 1);
        final int day = Math.min(date.getDate(), daysInMonth(month.getFullYear(), month.getMonth()));
        return UTCDate.create(month.getFullYear(), month.getMonth(), day);
    }

    private static int daysInMonth(final int year, final int month) {
        // Day 0 of the next month is the last day of this one
        return UTCDate.create(year, month + 1, 0).getDate();
    }

    private static boolean isSameDay(final UTCDate a, final UTCDate b) {
        return a != null && b != null && CalendarUtil.isSameDate(a, b);
    }
}
