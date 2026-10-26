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

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasValue;

/// Stroom's calendar, for choosing a day: the BEM block `calendar` (`calendar.css`), holding its
/// year and month rows ([DefaultMonthSelector]) above the month's days ([CalendarGrid], a WAI-ARIA
/// grid).
///
/// Its value is a day at midnight UTC, or null for none.
public class CustomDatePicker extends Composite implements HasValue<UTCDate> {

    private static final String BLOCK = "calendar";

    private final DateTimeModel model = new DateTimeModel();
    private final DefaultMonthSelector monthSelector;
    private final CalendarGrid grid;
    private UTCDate value;

    /// Makes a calendar showing today's month, with no day chosen.
    public CustomDatePicker() {
        monthSelector = new DefaultMonthSelector(this);
        grid = new CalendarGrid(this);

        final FlowPanel panel = new FlowPanel();
        panel.setStyleName(BLOCK);
        panel.add(monthSelector);
        panel.add(grid);
        initWidget(panel);

        refreshAll();
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
        }
    }

    /// Puts the focus on the grid's day with the tab stop: the chosen day, if it is in the month
    /// shown.
    public void focusGrid() {
        grid.focus();
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
        monthSelector.refresh();
    }

    private static boolean isSameDay(final UTCDate a, final UTCDate b) {
        if (a == null || b == null) {
            return a == b;
        }
        return CalendarUtil.isSameDate(a, b);
    }
}
