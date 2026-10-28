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

import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.datepicker.client.Calendar;
import stroom.widget.datepicker.client.DateTimeModel;
import stroom.widget.datepicker.client.UTCDate;
import stroom.widget.text.client.Label;
import stroom.widget.util.client.ClientStringUtil;

import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's calendar, the [Calendar] of `stroom.widget.datepicker.client`
/// (its year and month rows and its `CalendarGrid`), as shown in the date and time dialog
/// (`DateTimePicker`).
///
/// The days are a WAI-ARIA grid named for its month, each day a `gridcell` named by its full date
/// (in the browser's language, en-GB in the test runner). The grid is one tab stop, the focus on a
/// day: arrows, Home, End, Page Up and Page Down (with Shift for a year) move it, and Enter, Space
/// or a click chooses a day.
///
/// "Today" is the current date (in Stroom's client time zone, UTC by default), so the Basic and
/// NoSelection stories show the month they are opened in.
public final class CalendarGridStories {

    private static final String ON_SELECT = "onSelect";
    private static final String GRID = "grid";
    private static final String DAY = "gridcell";

    private CalendarGridStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // No args: the stories render their own state, and Stroom's picker always takes today from
        // the clock
        registry.component("Widgets/Date & Time/CalendarGrid", CalendarGridStories.class)
                .layout(StoryLayout.CENTERED)
                // Click a day (or use the keys over the grid) to choose it
                .story("Basic", context -> calendar(context, new DateTimeModel().getTodayUTC(), true))
                // No selection: the grid opens on today's month with nothing chosen, today having
                // the tab stop
                .story("NoSelection", context -> calendar(context, null, false))
                // A selection in a different month and year from today
                .story("PastDate", context -> calendar(context, UTCDate.create(2020, 1, 29), false))
                // The grid is named for its month, each day by its full date; the chosen day is
                // selected and has the one tab stop, the others none. Weekday headers have their full
                // name for screen readers
                .story("GridRolesAndNames", context -> calendar(context, UTCDate.create(2024, 0, 15), true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.getByRole(GRID, "January 2024");
                    final Query chosen = screen.getByRole(DAY, "Monday, 15 January 2024");
                    play.expect(chosen).toHaveAttribute("aria-selected", "true");
                    play.expect(chosen).toHaveAttribute("tabindex", "0");
                    final Query other = screen.getByRole(DAY, "Tuesday, 16 January 2024");
                    play.expect(other).toHaveAttribute("aria-selected", "false");
                    play.expect(other).toHaveAttribute("tabindex", "-1");
                    // A day of the month before, shown to fill the first week
                    play.expect(screen.getByRole(DAY, "Sunday, 31 December 2023"))
                            .toHaveClass("calendar__day--outside");
                    play.expect(screen.getByRole("columnheader", "Mon")).toHaveAttribute("abbr", "Monday");
                })
                // Tab reaches the grid once, on the chosen day, after the year and month boxes and
                // their toolbars, and goes on past it
                .story("OneTabStop", context -> calendar(context, UTCDate.create(2024, 0, 15), true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.tab();
                    play.expect(screen.getByRole("combobox", "Year")).toHaveFocus();
                    play.tab();
                    play.tab();
                    play.expect(screen.getByRole("combobox", "Month")).toHaveFocus();
                    play.tab();
                    play.tab();
                    play.expect(screen.getByRole(DAY, "Monday, 15 January 2024")).toHaveFocus();
                })
                // Left and Right move a day, Up and Down a week, Home and End to the ends of the week:
                // moving doesn't choose, Enter does
                .story("ArrowKeysMoveTheFocus", context -> calendar(context, UTCDate.create(2024, 0, 17), true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    focusDay(play, "Wednesday, 17 January 2024");
                    play.keyboard("{ArrowRight}");
                    play.expect(screen.getByRole(DAY, "Thursday, 18 January 2024")).toHaveFocus();
                    play.keyboard("{ArrowDown}");
                    play.expect(screen.getByRole(DAY, "Thursday, 25 January 2024")).toHaveFocus();
                    play.keyboard("{ArrowUp}{ArrowUp}");
                    play.expect(screen.getByRole(DAY, "Thursday, 11 January 2024")).toHaveFocus();
                    play.keyboard("{ArrowLeft}");
                    play.expect(screen.getByRole(DAY, "Wednesday, 10 January 2024")).toHaveFocus();
                    play.keyboard("{Home}");
                    play.expect(screen.getByRole(DAY, "Monday, 8 January 2024")).toHaveFocus();
                    play.keyboard("{End}");
                    play.expect(screen.getByRole(DAY, "Sunday, 14 January 2024")).toHaveFocus();
                    // Only the day with the focus is a tab stop
                    play.expect(screen.getByRole(DAY, "Sunday, 14 January 2024")).toHaveAttribute("tabindex", "0");
                    play.expect(screen.getByRole(DAY, "Wednesday, 17 January 2024"))
                            .toHaveAttribute("tabindex", "-1");
                    play.expect(play.spy(ON_SELECT)).not().toHaveBeenCalled();
                    play.keyboard("{Enter}");
                    play.expect(play.spy(ON_SELECT)).toHaveBeenLastCalledWith("Selected: 2024-01-14");
                    play.expect(screen.getByRole(DAY, "Sunday, 14 January 2024"))
                            .toHaveAttribute("aria-selected", "true");
                    play.expect(screen.getByRole(DAY, "Sunday, 14 January 2024")).toHaveFocus();
                })
                // Moving past the month's end shows the next month, the focus on the day moved to
                .story("ArrowsCrossIntoTheNextMonth", context -> calendar(context, UTCDate.create(2024, 0, 31), true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    focusDay(play, "Wednesday, 31 January 2024");
                    play.keyboard("{ArrowRight}");
                    screen.getByRole(GRID, "February 2024");
                    play.expect(screen.getByRole(DAY, "Thursday, 1 February 2024")).toHaveFocus();
                    play.expect(screen.getByRole("combobox", "Month")).toHaveValue("February");
                    play.keyboard("{ArrowUp}");
                    screen.getByRole(GRID, "January 2024");
                    play.expect(screen.getByRole(DAY, "Thursday, 25 January 2024")).toHaveFocus();
                })
                // Page Up and Page Down move a month, Shift with them a year, keeping the day of the
                // month (or the month's last day, if it is shorter)
                .story("PageKeysMoveAMonthOrAYear", context -> calendar(context, UTCDate.create(2024, 0, 31), true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    focusDay(play, "Wednesday, 31 January 2024");
                    play.keyboard("{PageDown}");
                    screen.getByRole(GRID, "February 2024");
                    play.expect(screen.getByRole(DAY, "Thursday, 29 February 2024")).toHaveFocus();
                    play.keyboard("{Shift>}{PageUp}{/Shift}");
                    screen.getByRole(GRID, "February 2023");
                    play.expect(screen.getByRole(DAY, "Tuesday, 28 February 2023")).toHaveFocus();
                    play.expect(screen.getByRole("combobox", "Year")).toHaveValue("2023");
                    play.keyboard("{Shift>}{PageDown}{/Shift}{PageUp}");
                    screen.getByRole(GRID, "January 2024");
                    play.expect(screen.getByRole(DAY, "Sunday, 28 January 2024")).toHaveFocus();
                    // Moving doesn't choose
                    play.expect(play.spy(ON_SELECT)).not().toHaveBeenCalled();
                })
                // Space chooses the day with the focus, as Enter does
                .story("SpaceChooses", context -> calendar(context, UTCDate.create(2024, 0, 15), true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    focusDay(play, "Monday, 15 January 2024");
                    play.keyboard("{ArrowLeft} ");
                    play.expect(play.spy(ON_SELECT)).toHaveBeenLastCalledWith("Selected: 2024-01-14");
                    play.expect(screen.getByRole(DAY, "Sunday, 14 January 2024"))
                            .toHaveAttribute("aria-selected", "true");
                    play.expect(screen.getByRole(DAY, "Monday, 15 January 2024"))
                            .toHaveAttribute("aria-selected", "false");
                })
                // Clicking a day of the next month, shown to fill the last week, chooses it and shows
                // its month
                .story("ClickingAnOutsideDayShowsItsMonth",
                        context -> calendar(context, UTCDate.create(2024, 0, 15), true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(screen.getByRole(DAY, "Friday, 2 February 2024"));
                    play.expect(play.spy(ON_SELECT)).toHaveBeenLastCalledWith("Selected: 2024-02-02");
                    screen.getByRole(GRID, "February 2024");
                    play.expect(screen.getByRole(DAY, "Friday, 2 February 2024"))
                            .toHaveAttribute("aria-selected", "true");
                    play.expect(screen.getByRole(DAY, "Friday, 2 February 2024")).toHaveFocus();
                })
                // The year and month toolbars show another month, keeping the tab stop on the same day
                // of the month, without choosing it
                .story("ToolbarsChangeTheMonth", context -> calendar(context, UTCDate.create(2024, 0, 15), true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(screen.getByRole("button", "Next Month"));
                    screen.getByRole(GRID, "February 2024");
                    play.expect(screen.getByRole(DAY, "Thursday, 15 February 2024"))
                            .toHaveAttribute("tabindex", "0");
                    play.click(screen.getByRole("button", "Previous Year"));
                    screen.getByRole(GRID, "February 2023");
                    play.expect(play.spy(ON_SELECT)).not().toHaveBeenCalled();
                });
    }

    // Puts the focus on a day without choosing it: Tab to the grid's tab stop (the chosen day)
    private static void focusDay(final Play play, final String name) {
        for (int i = 0; i < 5; i++) {
            play.tab();
        }
        play.expect(play.screen().getByRole(DAY, name)).toHaveFocus();
    }

    /// The calendar with `<p>Selected: ...</p>` below it.
    ///
    /// @param selected The selected day, or null for none.
    /// @param padded   True to show the month and day with two digits, false for no padding.
    private static Widget calendar(final StoryContext context, final UTCDate selected, final boolean padded) {
        final Calendar datePicker = new Calendar();
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
