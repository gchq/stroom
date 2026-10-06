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
import stroom.widget.datepicker.client.TimePopup;
import stroom.widget.datepicker.client.TimeViewImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [TimeBox] (a text box for a time, whose clock icon opens the "Set Time"
/// dialog, [TimePopup], used by e.g. the processing profile period dialog), matching
/// `Widgets/Date & Time/TimePicker` in the React Storybook.
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
        // React's value arg is only a default for its Controls; the stories render their own state
        registry.component("Widgets/Date & Time/TimePicker", TimePickerStories.class)
                .layout(StoryLayout.CENTERED)
                // A full HH:MM:SS time
                .story("Basic", context -> timePicker(context, new Time(14, 30, 0), true))
                // Hours and minutes only (no seconds spinner in the dialog)
                .story("HourMinute", context -> timePicker(context, new Time(9, 0, 0), false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.querySelector(".svgIconBox-icon-outer"));
                    final Play dialog = screen.within(screen.findByText("Set Time").closest(StroomDom.DIALOG));
                    play.expect(dialog.getByText("Minute")).toBeVisible();
                    // The seconds spinner was once still shown (TimeViewImpl.setSecondVisible did
                    // nothing)
                    play.expect(dialog.getByText("Second")).not().toBeVisible();
                    play.click(dialog.getByRole("button", StroomDom.button("Cancel")));
                });
    }

    /// The box with `<p>Time: HH:MM:SS</p>` below it, in a column with a gap of 8.
    private static Widget timePicker(final StoryContext context, final Time time, final boolean showSecond) {
        final ScreenHarness harness = DateTimeWidgets.popupHarness(context);
        final TimeBox box = new TimeBox();
        // As Stroom's presenters give it the dialog, e.g. ProfilePeriodEditPresenter
        box.setPopupProvider(() -> new TimePopup(harness.getEventBus(),
                new TimeViewImpl(GWT.create(TimeViewImpl.Binder.class))));
        box.setSecondVisible(showSecond);
        box.setValue(time);

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
