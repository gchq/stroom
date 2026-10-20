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

import stroom.docref.HasDisplayValue;
import stroom.item.client.SelectionBox;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Toolbar;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Year;
import stroom.widget.form.client.AccessibleName;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;

import java.util.Objects;

/// The year and month rows above a [CustomDatePicker]'s days: in each, a selection box ("Year",
/// "Month") and a toolbar of its Previous and Next buttons (also "Year" and "Month", one tab stop
/// each). Left and Right in a box step it back and on.
public final class DefaultMonthSelector extends Composite {

    private static final String BLOCK = "calendar";
    private static final String HEADER_CLASS_NAME = BLOCK + "__header";
    private static final String ROW_CLASS_NAME = BLOCK + "__row";
    // The number of years the year box lists, centred on the year shown
    private static final int VISIBLE_YEAR_COUNT = 21;

    private final CustomDatePicker datePicker;
    private final SelectionBox<IntItem> monthSelect;
    private final SelectionBox<IntItem> yearSelect;

    /// @param datePicker The calendar the rows belong to.
    DefaultMonthSelector(final CustomDatePicker datePicker) {
        this.datePicker = datePicker;

        final Toolbar yearToolbar = new Toolbar("Year");
        yearToolbar.button(SvgImage.ARROW_LEFT, "Previous Year",
                () -> datePicker.addMonths(-DateTimeModel.MONTHS_IN_YEAR));
        yearToolbar.button(SvgImage.ARROW_RIGHT, "Next Year",
                () -> datePicker.addMonths(DateTimeModel.MONTHS_IN_YEAR));
        final Toolbar monthToolbar = new Toolbar("Month");
        monthToolbar.button(SvgImage.ARROW_LEFT, "Previous Month", () -> datePicker.addMonths(-1));
        monthToolbar.button(SvgImage.ARROW_RIGHT, "Next Month", () -> datePicker.addMonths(1));

        monthSelect = createMonthSelect();
        yearSelect = createYearSelect();

        final FlowPanel year = new FlowPanel();
        year.setStyleName(ROW_CLASS_NAME);
        year.add(yearSelect);
        year.add(yearToolbar);

        final FlowPanel month = new FlowPanel();
        month.setStyleName(ROW_CLASS_NAME);
        month.add(monthSelect);
        month.add(monthToolbar);

        final FlowPanel layout = new FlowPanel();
        layout.setStyleName(HEADER_CLASS_NAME);
        layout.add(year);
        layout.add(month);
        initWidget(layout);
    }

    /// Shows the picker's month and year in the boxes.
    void refresh() {
        final UTCDate date = datePicker.getModel().getCurrentMonth();
        final int month = date.getMonth();
        monthSelect.setValue(new IntItem(datePicker.getModel().formatMonth(month), month));

        yearSelect.clear();
        final int year = date.getFullYear();
        final int startYear = year - (VISIBLE_YEAR_COUNT - 1) / 2;
        final int endYear = year + VISIBLE_YEAR_COUNT / 2;
        for (int i = startYear; i <= endYear; i++) {
            yearSelect.addItem(new IntItem(formatYear(i), i));
        }
        yearSelect.setValue(new IntItem(formatYear(year), year));
    }

    private SelectionBox<IntItem> createMonthSelect() {
        final SelectionBox<IntItem> monthListBox = new SelectionBox<>();
        AccessibleName.set(monthListBox, "Month");

        for (int i = 0; i < DateTimeModel.MONTHS_IN_YEAR; i++) {
            monthListBox.addItem(new IntItem(datePicker.getModel().formatMonth(i), i));
        }

        monthListBox.addValueChangeHandler(e -> {
            final int previousMonth = datePicker.getModel().getCurrentMonth().getMonth();
            datePicker.addMonths(monthListBox.getValue().getValue() - previousMonth);
        });
        monthListBox.addDomHandler(e -> {
            if (KeyCodes.KEY_LEFT == e.getNativeKeyCode()) {
                datePicker.addMonths(-1);
            } else if (KeyCodes.KEY_RIGHT == e.getNativeKeyCode()) {
                datePicker.addMonths(1);
            }
        }, KeyDownEvent.getType());

        return monthListBox;
    }

    private SelectionBox<IntItem> createYearSelect() {
        final SelectionBox<IntItem> yearListBox = new SelectionBox<>();
        AccessibleName.set(yearListBox, "Year");
        yearListBox.addValueChangeHandler(e -> {
            final int previousYear = datePicker.getModel().getCurrentMonth().getFullYear();
            final int delta = yearListBox.getValue().getValue() - previousYear;
            datePicker.addMonths(delta * DateTimeModel.MONTHS_IN_YEAR);
        });
        yearListBox.addDomHandler(e -> {
            if (KeyCodes.KEY_LEFT == e.getNativeKeyCode()) {
                datePicker.addMonths(-DateTimeModel.MONTHS_IN_YEAR);
            } else if (KeyCodes.KEY_RIGHT == e.getNativeKeyCode()) {
                datePicker.addMonths(DateTimeModel.MONTHS_IN_YEAR);
            }
        }, KeyDownEvent.getType());

        return yearListBox;
    }

    private static String formatYear(final int year) {
        final FormatOptions yearFormat = FormatOptions.builder().year(Year.NUMERIC).timeZone("UTC").build();
        return IntlDateTimeFormat.format(UTCDate.create(year, 0), IntlDateTimeFormat.DEFAULT_LOCALE, yearFormat);
    }


    // --------------------------------------------------------------------------------


    /// A month or year in a box, by its number.
    public static class IntItem implements HasDisplayValue {

        private final String label;
        private final int value;

        /// @param label The text shown.
        /// @param value The month (0 for January) or year.
        public IntItem(final String label, final int value) {
            this.label = label;
            this.value = value;
        }

        @Override
        public String getDisplayValue() {
            return label;
        }

        /// @return The month (0 for January) or year.
        public int getValue() {
            return value;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final IntItem that = (IntItem) o;
            return value == that.value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }
}
