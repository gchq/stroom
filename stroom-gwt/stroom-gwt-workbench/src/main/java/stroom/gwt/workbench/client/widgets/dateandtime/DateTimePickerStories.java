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
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.datepicker.client.DateTimeModel;
import stroom.widget.datepicker.client.DateTimePopup;
import stroom.widget.datepicker.client.DateTimeViewImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [DateTimeBox] (a text box for an ISO date-time, whose calendar icon opens
/// the "Set Date And Time" dialog, [DateTimePopup], used by e.g. the schedule dialogs), matching
/// `Widgets/Date & Time/DateTimePicker` in the React Storybook.
///
/// The dialog is shown with Stroom's `ShowPopupEvent`, so the stories use a [ScreenHarness] for
/// its event bus and popup manager (no REST requests are made). Times are shown in Stroom's
/// client time zone, UTC by default.
public final class DateTimePickerStories {

    private static final String ON_CHANGE = DateTimeWidgets.ON_CHANGE;
    /// 2024-01-15T09:50:00.000Z.
    private static final long VALUE_MS = 1705312200000L;

    private DateTimePickerStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // React's valueMs arg is only a default for its Controls; the stories render their own state
        registry.component("Widgets/Date & Time/DateTimePicker", DateTimePickerStories.class)
                .layout(StoryLayout.CENTERED)
                // Type an ISO date-time, or use the calendar icon to open the dialog
                .story("Basic", context -> withMsLabel(context, VALUE_MS))
                // Empty: the calendar icon opens the dialog on the current time
                .story("Empty", context -> withMsLabel(context, null))
                // Disabled: the text box is greyed out.
                // Differs from React: the calendar icon still opens the dialog (DateTimeBox.setEnabled
                // only disables its text box)
                .story("Disabled", context -> {
                    final ScreenHarness harness = DateTimeWidgets.popupHarness(context);
                    final DateTimeBox box = dateTimeBox(context, harness, VALUE_MS);
                    box.setEnabled(false);
                    harness.add(box);
                    return harness.asWidget();
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
        final DateTimeBox box = new DateTimeBox();
        // As Stroom's presenters give it the dialog, e.g. ScheduledProcessEditPresenter
        box.setPopupProvider(() -> new DateTimePopup(harness.getEventBus(),
                new DateTimeViewImpl(GWT.create(DateTimeViewImpl.Binder.class)),
                new DateTimeModel()));
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
