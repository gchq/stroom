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

import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.datepicker.client.DateTimeBoxController;
import stroom.widget.datepicker.client.component.DateTimePicker;
import stroom.widget.datepicker.client.component.DateTimePickerController;
import stroom.widget.text.client.Label;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Provider;

/// Helpers shared by the `Widgets/Date & Time/*` stories, and by others that show a date-time box.
public final class DateTimeWidgets {

    /// The name of the spy for the widgets' value changes.
    static final String ON_CHANGE = "onChange";

    private DateTimeWidgets() {
        // Static utility
    }

    /// A small paragraph (`margin-top: 8px`, `font-size: 12px`) showing the value.
    ///
    /// @param text The text.
    /// @return A label.
    static Label valueLabel(final String text) {
        final Label label = new Label(text);
        label.getElement().getStyle().setProperty("marginTop", "8px");
        label.getElement().getStyle().setProperty("fontSize", "12px");
        return label;
    }

    /// Equivalent of `<div>{widget}{label}</div>`.
    ///
    /// @param widgets The widgets.
    /// @return A panel holding the widgets.
    static FlowPanel div(final Widget... widgets) {
        final FlowPanel panel = new FlowPanel();
        for (final Widget widget : widgets) {
            panel.add(widget);
        }
        return panel;
    }

    /// Gives a date-time box its dialog, as Stroom's screens do: a [DateTimeBoxController]
    /// attached to it, owned by the story's root.
    ///
    /// @param harness The harness, whose event bus shows the dialog.
    /// @param box     The box.
    /// @return The box.
    public static DateTimeBox withPicker(final ScreenHarness harness, final DateTimeBox box) {
        harness.own(dateTimeBoxControllers(harness).get()).attach(box);
        return box;
    }

    /// Makes date-time box controllers as GIN does for Stroom's screens, e.g. for a view that makes
    /// its own date-time boxes (the query expression editor's).
    ///
    /// @param harness The harness, whose event bus shows the dialog.
    /// @return The provider.
    public static Provider<DateTimeBoxController> dateTimeBoxControllers(final ScreenHarness harness) {
        return () -> new DateTimeBoxController(() ->
                new DateTimePickerController(harness.getEventBus(), new DateTimePicker()));
    }

    /// Creates a harness for a widget whose popup is a Stroom dialog (shown with
    /// `ShowPopupEvent`, e.g. the date-time picker), which needs the harness's event bus and popup
    /// manager. The widgets make no REST requests.
    ///
    /// @param context The story's context.
    /// @return The harness.
    static ScreenHarness popupHarness(final StoryContext context) {
        return ScreenHarness.create(context, RestFixtures.none());
    }
}
