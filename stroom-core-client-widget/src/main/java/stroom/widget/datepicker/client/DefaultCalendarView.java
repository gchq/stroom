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

import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;

/**
 * Simple calendar view. Not extensible as we wish to evolve it freely over
 * time.
 */
public final class DefaultCalendarView extends CalendarView {

    private final DateGrid grid = new DateGrid();

    private UTCDate firstDisplayed;

    private final UTCDate lastDisplayed;

    private DateCell ariaSelectedCell;

    /**
     * Constructor.
     */
    public DefaultCalendarView() {
        final UTCDate now = UTCDate.create();
        CalendarUtil.resetTime(now);
        lastDisplayed = now;
    }

    @Override
    public void addStyleToDate(final String styleName, final UTCDate date) {
        assert getDatePicker().isDateVisible(date) : "You tried to add style " + styleName + " to "
                + date + ". The calendar is currently showing " + getFirstDate()
                + " to " + getLastDate();
        final DateCell dateCell = getCell(date);
        if (dateCell != null) {
            dateCell.addStyleName(styleName);
        }
    }

    @Override
    public UTCDate getFirstDate() {
        return firstDisplayed;
    }

    @Override
    public UTCDate getLastDate() {
        return lastDisplayed;
    }

    @Override
    public boolean isDateEnabled(final UTCDate date) {
        final DateCell dateCell = getCell(date);
        if (dateCell != null) {
            return dateCell.isEnabled();
        }
        return false;
    }

    @Override
    public void refresh() {
        firstDisplayed = getModel().getCurrentFirstDayOfFirstWeek();

        if (firstDisplayed.getDate() == 1) {
            // show one empty week if date is Monday is the first in month.
            addDays(firstDisplayed, -7);
        }

        lastDisplayed.setTime(firstDisplayed.getTime());

        for (int i = 0; i < grid.getNumCells(); i++) {
            if (i != 0) {
                addDays(lastDisplayed, 1);
            }
            final DateCell cell = grid.getCell(i);
            if (cell != null) {
                cell.update(lastDisplayed);
            }
        }
        setAriaSelectedCell(null);
    }

    private static void addDays(final UTCDate date, final int days) {
        CalendarUtil.addDaysToDate(date, days);
    }

    @Override
    public void removeStyleFromDate(final String styleName, final UTCDate date) {
        final DateCell dateCell = getCell(date);
        if (dateCell != null) {
            dateCell.removeStyleName(styleName);
        }
    }

    @Override
    public void setAriaSelectedCell(final UTCDate date) {
        if (ariaSelectedCell != null) {
            ariaSelectedCell.setAriaSelected(false);
        }
        final DateCell newSelectedCell = date != null
                ? getCell(date)
                : null;
        if (newSelectedCell != null) {
            newSelectedCell.setAriaSelected(true);
        }
        ariaSelectedCell = newSelectedCell;
    }

    @Override
    public void setKeyboardSelectedCell(final UTCDate date) {
        if (date == null) {
            grid.setKeyboardSelectedCell(-1);
        } else {
            final DateCell cell = getCell(date);
            if (cell != null) {
                grid.setKeyboardSelectedCell(cell);
            }
        }
    }

    @Override
    public void setEnabledOnDate(final boolean enabled, final UTCDate date) {
        final DateCell dateCell = getCell(date);
        if (dateCell != null) {
            dateCell.setEnabled(enabled);
        }
    }

    @Override
    public void setup() {
        // Preparation
        final CellFormatter formatter = grid.getCellFormatter();
        int weekendStartColumn = -1;
        int weekendEndColumn = -1;

        // Set up the day labels.
        for (int i = 0; i < DateTimeModel.DAYS_IN_WEEK; i++) {
            final int shift = CalendarUtil.getStartingDayOfWeek();
            final int dayIdx = i + shift < DateTimeModel.DAYS_IN_WEEK
                    ? i + shift
                    : i + shift - DateTimeModel.DAYS_IN_WEEK;
            final DayLabel cell = new DayLabel(getModel().formatDayOfWeek(dayIdx));
            grid.addElement(cell);
            grid.setWidget(0, i, cell);

            if (CalendarUtil.isWeekend(dayIdx)) {
                formatter.setStyleName(0, i, css().weekendLabel());
                if (weekendStartColumn == -1) {
                    weekendStartColumn = i;
                } else {
                    weekendEndColumn = i;
                }
            } else {
                formatter.setStyleName(0, i, css().weekdayLabel());
            }
        }

        // Set up the calendar grid.
        for (int row = 1; row <= DateTimeModel.WEEKS_IN_MONTH; row++) {
            for (int column = 0; column < DateTimeModel.DAYS_IN_WEEK; column++) {
                final int index = grid.getNumCells();
                final DateCell cell = new DateCell(
                        this,
                        css(),
                        column == weekendStartColumn || column == weekendEndColumn,
                        index);
                grid.addElement(cell);
                grid.addCell(cell);
                grid.setWidget(row, column, cell);
            }
        }
        initWidget(grid);
        grid.setStyleName(css().days());
    }

    private DateCell getCell(final UTCDate date) {
        final int index = CalendarUtil.getDaysBetween(firstDisplayed, date);
        if (index < 0 || grid.getNumCells() <= index) {
            return null;
        }
        return grid.getCell(index);
    }
}
