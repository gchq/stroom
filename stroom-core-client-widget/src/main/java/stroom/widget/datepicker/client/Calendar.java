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
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Year;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HasValue;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;
import java.util.function.Consumer;

/// Stroom's calendar, for choosing a day: the BEM block `calendar` (`calendar.css`, markup
/// `Calendar.ui.xml`), holding its year and month rows above the month's days
/// ([CalendarGrid], a WAI-ARIA grid). In each row, a selection box ("Year", "Month") and a toolbar of
/// its Previous and Next buttons (also "Year" and "Month", one tab stop each). Left and Right in a
/// box step it back and on.
///
/// Its value is a day at midnight UTC, or null for none.
public class Calendar extends Composite implements HasValue<UTCDate> {

    private static final Binder BINDER = GWT.create(Binder.class);
    // The number of years the year box lists, centred on the year shown
    private static final int VISIBLE_YEAR_COUNT = 21;

    private final DateTimeModel model = new DateTimeModel();
    @UiField
    SelectionBox<IntItem> yearSelect;
    @UiField
    ToolbarButton previousYear;
    @UiField
    ToolbarButton nextYear;
    @UiField
    SelectionBox<IntItem> monthSelect;
    @UiField
    ToolbarButton previousMonth;
    @UiField
    ToolbarButton nextMonth;
    @UiField
    CalendarGrid grid;
    private UTCDate value;
    private Consumer<UTCDate> onChange;

    /// Makes a calendar showing today's month, with no day chosen.
    public Calendar() {
        initWidget(BINDER.createAndBindUi(this));
        grid.setDatePicker(this);

        previousYear.setOnClick(() -> addMonths(-DateTimeModel.MONTHS_IN_YEAR));
        nextYear.setOnClick(() -> addMonths(DateTimeModel.MONTHS_IN_YEAR));
        previousMonth.setOnClick(() -> addMonths(-1));
        nextMonth.setOnClick(() -> addMonths(1));

        for (int i = 0; i < DateTimeModel.MONTHS_IN_YEAR; i++) {
            monthSelect.addItem(new IntItem(model.formatMonth(i), i));
        }
        monthSelect.addValueChangeHandler(e -> {
            final int previous = model.getCurrentMonth().getMonth();
            addMonths(monthSelect.getValue().getValue() - previous);
        });
        stepWithLeftAndRight(monthSelect, 1);

        yearSelect.addValueChangeHandler(e -> {
            final int previous = model.getCurrentMonth().getFullYear();
            addMonths((yearSelect.getValue().getValue() - previous) * DateTimeModel.MONTHS_IN_YEAR);
        });
        stepWithLeftAndRight(yearSelect, DateTimeModel.MONTHS_IN_YEAR);

        refreshAll();
    }

    // Left and Right in a box step it back and on
    private void stepWithLeftAndRight(final SelectionBox<IntItem> box, final int months) {
        box.addDomHandler(e -> {
            if (KeyCodes.KEY_LEFT == e.getNativeKeyCode()) {
                addMonths(-months);
            } else if (KeyCodes.KEY_RIGHT == e.getNativeKeyCode()) {
                addMonths(months);
            }
        }, KeyDownEvent.getType());
    }

    /// @param onChange Told the day chosen when the user chooses one (or `setValue(value, true)`
    ///                 changes it), or null for nothing.
    public void setOnChange(final Consumer<UTCDate> onChange) {
        this.onChange = onChange;
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<UTCDate> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }

    /// @return The first day of the month shown, at midnight UTC.
    public UTCDate getCurrentMonth() {
        return CalendarUtil.copyDate(model.getCurrentMonth());
    }

    /// Shows the month a day is in.
    ///
    /// @param month A day in the month to show.
    public void setCurrentMonth(final UTCDate month) {
        model.setCurrentMonth(month);
        refreshAll();
    }

    @Override
    public final UTCDate getValue() {
        return CalendarUtil.copyDate(value);
    }

    @Override
    public final void setValue(final UTCDate newValue) {
        setValue(newValue, false);
    }

    @Override
    public final void setValue(final UTCDate newValue, final boolean fireEvents) {
        final UTCDate oldValue = value;
        value = CalendarUtil.copyDate(newValue);
        grid.setSelected(value);

        if (fireEvents && !isSameDay(oldValue, value)) {
            ValueChangeEvent.fire(this, getValue());
            if (onChange != null) {
                onChange.accept(getValue());
            }
        }
    }

    /// Shows the month the given number of months on (back, if negative).
    ///
    /// @param months The number of months.
    void addMonths(final int months) {
        model.shiftCurrentMonth(months);
        refreshAll();
    }

    DateTimeModel getModel() {
        return model;
    }

    private void refreshAll() {
        grid.refresh();
        refreshMonthAndYear();
    }

    // Shows the month and year shown in the boxes
    private void refreshMonthAndYear() {
        final UTCDate date = model.getCurrentMonth();
        final int month = date.getMonth();
        monthSelect.setValue(new IntItem(model.formatMonth(month), month));

        yearSelect.clear();
        final int year = date.getFullYear();
        final int startYear = year - (VISIBLE_YEAR_COUNT - 1) / 2;
        final int endYear = year + VISIBLE_YEAR_COUNT / 2;
        for (int i = startYear; i <= endYear; i++) {
            yearSelect.addItem(new IntItem(formatYear(i), i));
        }
        yearSelect.setValue(new IntItem(formatYear(year), year));
    }

    private static String formatYear(final int year) {
        final FormatOptions yearFormat = FormatOptions.builder().year(Year.NUMERIC).timeZone("UTC").build();
        return IntlDateTimeFormat.format(UTCDate.create(year, 0), IntlDateTimeFormat.DEFAULT_LOCALE, yearFormat);
    }

    private static boolean isSameDay(final UTCDate a, final UTCDate b) {
        if (a == null || b == null) {
            return a == b;
        }
        return CalendarUtil.isSameDate(a, b);
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


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, Calendar> {

    }
}
