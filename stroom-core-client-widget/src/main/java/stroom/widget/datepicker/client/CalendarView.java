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

/**
 * The CalendarView is a calendar grid that represents the current view of a
 * {@link CustomDatePicker}. Note, the calendar view only deals with the currently
 * visible dates and all state is flushed when the calendar view is refreshed.
 */
public abstract class CalendarView extends DatePickerComponent {

    /**
     * Constructor.
     */
    public CalendarView() {
    }

    /**
     * Adds a style name to the cell of the supplied date. This style is only set
     * until the next time the {@link CalendarView} is refreshed.
     *
     * @param styleName style name to add
     * @param date      date that will have the supplied style added
     */
    public abstract void addStyleToDate(String styleName, UTCDate date);

    /**
     * Returns the first date that is currently shown by the calendar.
     *
     * @return the first date.
     */
    public abstract UTCDate getFirstDate();

    /**
     * Returns the last date that is currently shown by the calendar.
     *
     * @return the last date.
     */
    public abstract UTCDate getLastDate();

    /**
     * Is the cell representing the given date enabled?
     *
     * @param date the date
     * @return is the date enabled
     */
    public abstract boolean isDateEnabled(UTCDate date);

    /**
     * Removes a visible style name from the cell of the supplied date.
     *
     * @param styleName style name to remove
     * @param date      date that will have the supplied style added
     */
    public abstract void removeStyleFromDate(String styleName, UTCDate date);

    /**
     * Sets aria-selected in the given date's cell and clears the other cells.
     *
     * @param date the date of the cell where aria-selected should be set,
     *             or null to clear aria-selected.
     */
    public void setAriaSelectedCell(final UTCDate date) {
    }

    public void setKeyboardSelectedCell(final UTCDate date) {
    }

    /**
     * Enables or Disables a particular date. by default all valid dates are
     * enabled after a rendering event. Disabled dates cannot be selected.
     *
     * @param enabled true for enabled, false for disabled
     * @param date    date to enable or disable
     */
    public abstract void setEnabledOnDate(boolean enabled, UTCDate date);
}
