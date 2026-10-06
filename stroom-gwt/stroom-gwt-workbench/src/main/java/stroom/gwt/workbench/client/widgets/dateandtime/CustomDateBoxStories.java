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
import stroom.widget.customdatebox.client.MyDateBox;

import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [MyDateBox] (a text box for an ISO date-time, with a calendar popup to
/// pick the day, used by e.g. the processor and API key dialogs), matching
/// `Widgets/Date & Time/CustomDateBox` in the React Storybook.
///
/// The popup opens when the box gets the focus, is clicked or on the down arrow, and closes on
/// Enter, Tab, Escape or the up arrow. Picking a day keeps the box's time, or adds
/// `T00:00:00.000Z` (`T00:00:00.000` if not UTC) if it has none.
public final class CustomDateBoxStories {

    private static final String ON_CHANGE = DateTimeWidgets.ON_CHANGE;
    private static final String VALUE = "2024-01-15T10:30:00.000Z";

    private CustomDateBoxStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // React's value arg is only a default for its Controls; the stories render their own state
        registry.component("Widgets/Date & Time/CustomDateBox", CustomDateBoxStories.class)
                .layout(StoryLayout.CENTERED)
                // Type an ISO date-time, or click the box to pick a day from the calendar popup
                .story("Default", context -> withValueLabel(context, VALUE, true))
                // An empty box: the popup shows today
                .story("Empty", context -> withValueLabel(context, "", true))
                // Not UTC: a newly picked day gets the `T00:00:00.000` (no `Z`) suffix
                .story("LocalTime", context -> withValueLabel(context, "2024-01-15T00:00:00.000", false))
                // A value that can't be parsed.
                // Differs from React: MyDateBox doesn't mark a value it can't parse (React shows the
                // `dateBoxFormatError` red background of GWT's DateBox-based CustomDateBox, which
                // Stroom doesn't use); its popup shows today
                .story("FormatError", context -> withValueLabel(context, "not-a-date", true))
                // Disabled: greyed out, and the popup doesn't open
                .story("Disabled", context -> {
                    final MyDateBox dateBox = dateBox(context, VALUE, true);
                    dateBox.setEnabled(false);
                    return dateBox;
                });
    }

    /// The date box with `<p>Value: ...</p>` below it.
    private static Widget withValueLabel(final StoryContext context, final String value, final boolean utc) {
        final MyDateBox dateBox = dateBox(context, value, utc);
        final Label label = DateTimeWidgets.valueLabel(valueText(value));
        dateBox.addValueChangeHandler(event -> label.setText(valueText(event.getValue())));
        return DateTimeWidgets.div(dateBox, label);
    }

    private static MyDateBox dateBox(final StoryContext context, final String value, final boolean utc) {
        final MyDateBox dateBox = new MyDateBox();
        dateBox.setUtc(utc);
        dateBox.setValue(value);
        final Spy onChange = context.fn(ON_CHANGE);
        dateBox.addValueChangeHandler(event -> onChange.call(event.getValue()));
        return dateBox;
    }

    private static String valueText(final String value) {
        // React's onChange gives null for an empty box; MyDateBox gives an empty string
        return "Value: " + (value == null || value.isEmpty()
                ? "(empty)"
                : value);
    }
}
