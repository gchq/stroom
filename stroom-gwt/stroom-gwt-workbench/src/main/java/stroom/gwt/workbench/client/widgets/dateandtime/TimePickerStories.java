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

import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.shared.time.Time;
import stroom.widget.datepicker.client.TimeBox;
import stroom.widget.datepicker.client.TimeBoxController;
import stroom.widget.datepicker.client.component.TimePicker;
import stroom.widget.datepicker.client.component.TimePickerController;
import stroom.widget.form.client.AccessibleName;
import stroom.widget.text.client.Label;

import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [TimeBox] (a text box for a time, whose clock icon opens the "Set Time"
/// dialog, [TimePickerController], used by e.g. the processing profile period dialog).
///
/// The dialog is shown with Stroom's `ShowPopupEvent`, so the stories use a [ScreenHarness] for
/// its event bus and popup manager (no REST requests are made).
public final class TimePickerStories {

    private static final String ON_CHANGE = DateTimeWidgets.ON_CHANGE;

    private TimePickerStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // No args: the stories render their own state
        registry.component("Widgets/Date & Time/TimePicker", TimePickerStories.class)
                .layout(StoryLayout.CENTERED)
                // A full HH:MM:SS time
                .story("Basic", context -> timePicker(context, new Time(14, 30, 0), true, false))
                // Hours and minutes only (no seconds spinner in the dialog)
                .story("HourMinute", context -> timePicker(context, new Time(9, 0, 0), false, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.querySelector(".icon-box__icon"));
                    final Play dialog = screen.within(screen.findByText("Set Time").closest(StroomDom.DIALOG));
                    play.expect(dialog.getByText("Minute")).toBeVisible();
                    // The seconds spinner was once still shown (TimeViewImpl.setSecondVisible did
                    // nothing)
                    play.expect(dialog.getByText("Second")).not().toBeVisible();
                    play.click(dialog.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Set Time")).toBeNull());
                    play.expect(play.spy(ON_CHANGE)).not().toHaveBeenCalled();
                })
                // OK puts the time chosen in the box: Reset on the hour zeroes the whole time
                .story("ChooseTime", context -> timePicker(context, new Time(14, 30, 15), true, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.querySelector(".icon-box__icon"));
                    final Play dialog = screen.within(screen.findByText("Set Time").closest(StroomDom.DIALOG));
                    play.click(dialog.getByRole("button", "Reset hour"));
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ON_CHANGE)).toHaveBeenCalledWith("Time: 00:00:00"));
                    play.expect(play.getByDisplayValue("00:00:00")).toBeInTheDocument();
                })
                // Read only: the clock is dimmed and Enter doesn't open the dialog
                .story("ReadOnly", context -> timePicker(context, new Time(14, 30, 0), true, true))
                .withPlay(play -> {
                    play.expect(play.querySelector(".icon-box")).toHaveClass("icon-box--read-only");
                    play.click(play.getByDisplayValue("14:30:00"));
                    play.keyboard("{Enter}");
                    play.sleep(200);
                    play.expect(play.screen().queryByText("Set Time")).toBeNull();
                });
    }

    /// The box with `<p>Time: HH:MM:SS</p>` below it, in a column with a gap of 8.
    private static Widget timePicker(final StoryContext context,
                                     final Time time,
                                     final boolean showSecond,
                                     final boolean readOnly) {
        final ScreenHarness harness = DateTimeWidgets.popupHarness(context);
        final TimeBox box = new TimeBox();
        // Named, as a form group's label names it on Stroom's screens
        AccessibleName.set(box, "Time");
        // As Stroom's controllers attach one, e.g. ProfilePeriodEditorController
        final TimeBoxController controller = harness.own(new TimeBoxController(() ->
                new TimePickerController(harness.getEventBus(), new TimePicker())));
        controller.attach(box);
        controller.setSecondVisible(showSecond);
        box.setValue(time);
        box.setReadOnly(readOnly);

        final Label label = DateTimeWidgets.valueLabel(timeText(box.getValue()));
        final Spy onChange = context.fn(ON_CHANGE);
        // The event's value is null when the dialog sets the value, so read the box's
        box.addValueChangeHandler(event -> {
            onChange.call(timeText(box.getValue()));
            label.setText(timeText(box.getValue()));
        });
        harness.add(StoryPanels.column(8, box, label));
        return harness.asWidget();
    }

    private static String timeText(final Time time) {
        return "Time: " + time;
    }
}
