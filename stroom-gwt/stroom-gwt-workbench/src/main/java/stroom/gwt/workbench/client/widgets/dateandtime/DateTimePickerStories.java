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

import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.datepicker.client.component.DateTimeBox;
import stroom.widget.datepicker.client.component.DateTimePickerController;
import stroom.widget.form.client.AccessibleName;
import stroom.widget.form.client.IconBox;
import stroom.widget.text.client.Label;

import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [DateTimeBox] (a text box for an ISO date-time, whose calendar icon opens
/// the "Set Date And Time" dialog, [DateTimePickerController], used by e.g. the schedule dialogs).
///
/// The dialog is shown with Stroom's `ShowPopupEvent`, so the stories use a [ScreenHarness] for
/// its event bus and popup manager (no REST requests are made). Times are shown in Stroom's
/// client time zone, UTC by default.
public final class DateTimePickerStories {

    private static final String ON_CHANGE = DateTimeWidgets.ON_CHANGE;
    // The calendar icon, and the element its read-only class is on
    private static final String ICON_OUTER = ".icon-box__icon";
    private static final String ICON_INNER = ".icon-box";
    /// 2024-01-15T09:50:00.000Z.
    private static final long VALUE_MS = 1705312200000L;

    private DateTimePickerStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // No args: the stories render their own state
        registry.component("Widgets/Date & Time/DateTimePicker", DateTimePickerStories.class)
                .layout(StoryLayout.CENTERED)
                // Type an ISO date-time, or use the calendar icon to open the dialog
                .story("Basic", context -> withMsLabel(context, VALUE_MS))
                // Empty: the calendar icon opens the dialog on the current time
                .story("Empty", context -> withMsLabel(context, null))
                // In the dialog, the calendar's Year and Month boxes are named, and its previous and
                // next buttons are toolbars named "Year" and "Month", one tab stop each. They once
                // weren't: the dialog is laid out while hidden, so the toolbars saw no buttons, and
                // never looked again once it was shown
                .story("CalendarToolbarsInTheDialog", context -> withMsLabel(context, VALUE_MS))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    // Opened from the keyboard, so no pointer rests on a button (its tooltip would
                    // take its title)
                    play.tab();
                    play.keyboard("{Enter}");
                    screen.findByText("Set Date And Time");
                    for (final String name : new String[]{"Year", "Month"}) {
                        screen.findByRole("toolbar", name);
                        screen.getByRole("combobox", name);
                    }
                    play.expect(screen.getByRole("button", "Previous Year")).toHaveAttribute("tabindex", "0");
                    play.expect(screen.getByRole("button", "Next Year")).toHaveAttribute("tabindex", "-1");
                    play.expect(screen.getByRole("button", "Previous Month")).toHaveAttribute("tabindex", "0");
                    play.expect(screen.getByRole("button", "Next Month")).toHaveAttribute("tabindex", "-1");
                })
                // Disabled: the text box and calendar icon are greyed out, and the icon doesn't open
                // the dialog
                .story("Disabled", context -> {
                    final ScreenHarness harness = DateTimeWidgets.popupHarness(context);
                    final DateTimeBox box = dateTimeBox(context, harness, VALUE_MS);
                    box.setEnabled(false);
                    harness.add(box);
                    return harness.asWidget();
                })
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.expect(play.querySelector("input")).toBeDisabled();
                    play.expect(play.querySelector(ICON_INNER)).toHaveClass(IconBox.ICON_BOX_READONLY_CLASS_NAME);
                    // The icon once still opened the dialog (DateTimeBox.setEnabled only disabled
                    // its text box)
                    play.click(play.querySelector(ICON_OUTER));
                    play.sleep(100);
                    play.expect(screen.queryByText("Set Date And Time")).toBeNull();
                })
                // Read only: the value can be read and stays in the tab order, but neither the icon
                // nor Enter opens the dialog
                .story("ReadOnly", context -> {
                    final ScreenHarness harness = DateTimeWidgets.popupHarness(context);
                    final DateTimeBox box = dateTimeBox(context, harness, VALUE_MS);
                    box.setReadOnly(true);
                    harness.add(box);
                    return harness.asWidget();
                })
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.expect(play.querySelector("input")).toHaveAttribute("readonly");
                    play.expect(play.querySelector(ICON_INNER)).toHaveClass(IconBox.ICON_BOX_READONLY_CLASS_NAME);
                    play.click(play.querySelector(ICON_OUTER));
                    play.tab();
                    play.keyboard("{Enter}");
                    play.sleep(100);
                    play.expect(screen.queryByText("Set Date And Time")).toBeNull();
                })
                // The field says it opens a dialog; Alt+Down opens it as Enter does, and the focus
                // starts in the Hour spinner. Escape closes it unchanged
                .story("AltDownOpensTheDialog", context -> withMsLabel(context, VALUE_MS))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.expect(play.querySelector("input")).toHaveAttribute("aria-haspopup", "dialog");
                    play.tab();
                    play.keyboard("{Alt>}{ArrowDown}{/Alt}");
                    screen.findByText("Set Date And Time");
                    play.expect(screen.getByRole("spinbutton", "Hour")).toHaveFocus();
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(screen.queryByText("Set Date And Time")).toBeNull());
                    play.expect(play.spy(ON_CHANGE)).not().toHaveBeenCalled();
                })
                // In the dialog, a day is chosen with the calendar's keys, and OK puts it in the
                // field, keeping the time
                .story("ChooseADayWithTheKeys", context -> withMsLabel(context, VALUE_MS))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.tab();
                    play.keyboard("{Enter}");
                    screen.findByText("Set Date And Time");
                    play.click(screen.getByRole("gridcell", "Monday, 15 January 2024"));
                    play.keyboard("{ArrowRight}{ArrowDown}{Enter}");
                    play.expect(screen.getByText("23 January 2024")).toBeVisible();
                    play.click(screen.getByRole("button", "OK"));
                    play.waitFor(() -> play.expect(screen.queryByText("Set Date And Time")).toBeNull());
                    play.expect(play.querySelector("input")).toHaveValue("2024-01-23T09:50:00.000Z");
                    play.expect(play.spy(ON_CHANGE)).toHaveBeenCalledTimes(1);
                })
                // The dialog's shortcuts and Reset are buttons, named for what they do (they were
                // text that only a mouse could use)
                .story("ShortcutsAreButtons", context -> withMsLabel(context, VALUE_MS))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.tab();
                    play.keyboard("{Enter}");
                    screen.findByText("Set Date And Time");
                    for (final String name : new String[]{"Today", "Yesterday", "Week Start", "Now",
                            "Midnight", "Midday", "Reset hour", "Reset minute", "Reset second",
                            "Reset millisecond"}) {
                        screen.getByRole("button", name);
                    }
                    play.click(screen.getByRole("button", "Midday"));
                    play.expect(screen.getByRole("spinbutton", "Hour")).toHaveValue("12");
                    play.click(screen.getByRole("button", "Reset hour"));
                    play.expect(screen.getByRole("spinbutton", "Hour")).toHaveValue("0");
                })
                // Text that isn't a time is marked invalid when the field is left
                .story("InvalidTextIsMarked", context -> withMsLabel(context, VALUE_MS))
                .withPlay(play -> {
                    play.tab();
                    play.keyboard("{Control>}a{/Control}now()-1d{Tab}");
                    play.expect(play.querySelector("input")).toHaveAttribute("aria-invalid", "true");
                })
                // A box that allows expressions (a query's time range and terms) takes any text and
                // doesn't mark it. Enter is the screen's (e.g. to run the query), so doesn't open the
                // dialog; Alt+Down does, and OK replaces the expression with the time chosen
                .story("ExpressionsAllowed", context -> {
                    final ScreenHarness harness = DateTimeWidgets.popupHarness(context);
                    final DateTimeBox box = dateTimeBox(context, harness, null);
                    box.setExpressionsAllowed(true);
                    box.setText("now()-1d");
                    harness.add(box);
                    return harness.asWidget();
                })
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.tab();
                    play.keyboard("{Enter}");
                    play.sleep(100);
                    play.expect(screen.queryByText("Set Date And Time")).toBeNull();
                    play.keyboard("{Tab}");
                    play.expect(play.querySelector("input")).not().toHaveAttribute("aria-invalid", "true");
                    play.keyboard("{Shift>}{Tab}{/Shift}{Alt>}{ArrowDown}{/Alt}");
                    screen.findByText("Set Date And Time");
                    play.click(screen.getByRole("button", "OK"));
                    play.waitFor(() -> play.expect(screen.queryByText("Set Date And Time")).toBeNull());
                    play.expect(play.querySelector("input")).not().toHaveValue("now()-1d");
                    play.expect(play.spy(ON_CHANGE)).toHaveBeenCalledTimes(1);
                });
    }

    /// The box with `<p>ms: ...</p>` below it.
    private static Widget withMsLabel(final StoryContext context, final Long valueMs) {
        final ScreenHarness harness = DateTimeWidgets.popupHarness(context);
        final DateTimeBox box = dateTimeBox(context, harness, valueMs);
        final Label label = DateTimeWidgets.valueLabel(msText(valueMs));
        // The event's value is null when the dialog sets the value, so read the box's
        box.addValueChangeHandler(event -> label.setText(msText(box.getValue())));
        harness.add(DateTimeWidgets.div(box, label));
        return harness.asWidget();
    }

    private static DateTimeBox dateTimeBox(final StoryContext context,
                                           final ScreenHarness harness,
                                           final Long valueMs) {
        // As Stroom's controllers give it the dialog, e.g. ScheduledProcessEditorController
        final DateTimeBox box = DateTimeWidgets.withPicker(harness, new DateTimeBox());
        // Named, as a form group's label names it on Stroom's screens
        AccessibleName.set(box, "Start Time");
        box.setValue(valueMs);
        final Spy onChange = context.fn(ON_CHANGE);
        box.addValueChangeHandler(event -> onChange.call(box.getValue()));
        return box;
    }

    private static String msText(final Long ms) {
        return "ms: " + (ms != null
                ? String.valueOf(ms)
                : "none");
    }
}
