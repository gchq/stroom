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

import com.google.gwt.aria.client.Roles;
import com.google.gwt.aria.client.SelectedValue;

public final class DateCell extends AbstractCell {

    private boolean enabled = true;
    private boolean keyboardSelected;
    private final int index;

    private final DefaultCalendarView defaultCalendarView;
    private final CustomDatePicker.StandardCss css;
    private String cellStyle;
    private String dateStyle;
    private UTCDate value = UTCDate.create();

    public DateCell(final DefaultCalendarView defaultCalendarView,
                    final CustomDatePicker.StandardCss css,
                    final boolean isWeekend,
                    final int index) {
        this.defaultCalendarView = defaultCalendarView;
        this.css = css;
        this.index = index;
        cellStyle = css.day();

        if (isWeekend) {
            cellStyle += " " + css.dayIsWeekend();
        }
        getElement().setTabIndex(isFiller()
                ? -1
                : 0);
        setAriaSelected(false);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isSelected() {
        final UTCDate selected = defaultCalendarView.getDatePicker().getValue();
        return selected != null && value != null && value.getTime() == selected.getTime();
    }

    public boolean isHighlighted() {
        final UTCDate highlighted = defaultCalendarView.getDatePicker().getHighlightedDate();
        return highlighted != null && value != null && value.getTime() == highlighted.getTime();
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        updateStyle();
    }

    private void updateStyle() {
        String accum = dateStyle;
        if (isHighlighted()) {
            accum += " " + css.dayIsHighlighted();
            if (isSelected()) {
                accum += " " + css.dayIsValueAndHighlighted();
            }
        }
        if (!isEnabled()) {
            accum += " " + css.dayIsDisabled();
        }
        if (keyboardSelected) {
            accum += " " + "keyboard-selected";
        }
        setStyleName("cellOuter " + accum);
    }

    @Override
    public void addStyleName(final String styleName) {
        if (dateStyle.indexOf(" " + styleName + " ") == -1) {
            dateStyle += styleName + " ";
        }
        updateStyle();
    }

    public boolean isFiller() {
        return !defaultCalendarView.getModel().isInCurrentMonth(value);
    }

    public void onSelected(final boolean selected) {
        if (selected) {
            final UTCDate selectedValue = CalendarUtil.copyDate(value);
            defaultCalendarView.getDatePicker().setValue(selectedValue, true);
            if (isFiller()) {
                defaultCalendarView.getDatePicker().setCurrentMonth(selectedValue);
            }
        }
        updateStyle();
    }

    public void setKeyboardSelected(final boolean selected) {
        this.keyboardSelected = selected;
        updateStyle();
    }

    @Override
    public void removeStyleName(final String styleName) {
        dateStyle = dateStyle.replace(" " + styleName + " ", " ");
        updateStyle();
    }

    public void setAriaSelected(final boolean value) {
        Roles.getGridcellRole().setAriaSelectedState(getElement(), SelectedValue.of(value));
    }

    public UTCDate getValue() {
        return value;
    }

    void update(final UTCDate current) {
        setEnabled(true);
        value = CalendarUtil.copyDate(current);
        final String text = defaultCalendarView.getModel().formatDayOfMonth(value);
        setText(text);
        dateStyle = cellStyle;
        if (isFiller()) {
            getElement().setTabIndex(-1);
            dateStyle += " " + css.dayIsFiller();
        } else {
            getElement().setTabIndex(-1);
            final String extraStyle = defaultCalendarView.getDatePicker().getStyleOfDate(value);
            if (extraStyle != null) {
                dateStyle += " " + extraStyle;
            }
        }
        // We want to certify that all date styles have " " before and after
        // them for ease of adding to and replacing them.
        dateStyle += " ";
        updateStyle();
    }

    public int getIndex() {
        return index;
    }
}
