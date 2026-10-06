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


package stroom.gwt.workbench.client.widgets.dateandtime;

import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.datepicker.client.CustomDatePicker;
import stroom.widget.datepicker.client.DateTimeModel;
import stroom.widget.datepicker.client.UTCDate;
import stroom.widget.util.client.ClientStringUtil;

import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's calendar, the [CustomDatePicker] of `stroom.widget.datepicker.client`
/// (its `DefaultMonthSelector`, `DefaultCalendarView` and `DateGrid`), as shown in the date and
/// time dialog (`DateTimeViewImpl`), matching `Widgets/Date & Time/CalendarGrid` in the React
/// Storybook.
///
/// As in React, "today" is the current date (in Stroom's client time zone, UTC by default), so
/// the month shown depends on when the story is opened.
public final class CalendarGridStories {

    private static final String ON_SELECT = "onSelect";

    private CalendarGridStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // React's args (selected, today, onSelect) are only defaults for its Controls; the stories
        // render their own state, and Stroom's picker always takes today from the clock
        registry.component("Widgets/Date & Time/CalendarGrid", CalendarGridStories.class)
                .layout(StoryLayout.CENTERED)
                // Click a day (or use the arrow keys and Enter over the grid) to select it
                .story("Basic", context -> calendar(context, new DateTimeModel().getTodayUTC(), true))
                // No selection: the grid opens on today's month with nothing selected
                .story("NoSelection", context -> calendar(context, null, false))
                // A selection in a different month and year from today
                .story("PastDate", context -> calendar(context, UTCDate.create(2020, 1, 29), false));
    }

    /// The calendar with `<p>Selected: ...</p>` below it.
    ///
    /// @param selected The selected day, or null for none.
    /// @param padded   True to show the month and day with two digits (React's Basic story), false
    ///                 for no padding (its other stories).
    private static Widget calendar(final StoryContext context, final UTCDate selected, final boolean padded) {
        final CustomDatePicker datePicker = new CustomDatePicker();
        // As DateTimeViewImpl sets it up
        datePicker.setYearAndMonthDropdownVisible(true);
        datePicker.setYearArrowsVisible(true);
        if (selected != null) {
            datePicker.setCurrentMonth(selected);
            datePicker.setValue(selected);
        }

        final Label label = DateTimeWidgets.valueLabel(selectedText(selected, padded));
        final Spy onSelect = context.fn(ON_SELECT);
        datePicker.addValueChangeHandler(event -> {
            final String text = selectedText(event.getValue(), padded);
            onSelect.call(text);
            label.setText(text);
        });
        return DateTimeWidgets.div(datePicker, label);
    }

    private static String selectedText(final UTCDate date, final boolean padded) {
        if (date == null) {
            return "Selected: none";
        }
        final int month = date.getMonth() + 1;
        final int day = date.getDate();
        return "Selected: " + date.getFullYear() + "-"
               + (padded
                ? ClientStringUtil.zeroPad(2, month) + "-" + ClientStringUtil.zeroPad(2, day)
                : month + "-" + day);
    }
}
