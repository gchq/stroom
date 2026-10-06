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

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

/// Helpers shared by the `Widgets/Date & Time/*` stories.
final class DateTimeWidgets {

    /// The name of the spy for React's `onChange` callback props.
    static final String ON_CHANGE = "onChange";

    private DateTimeWidgets() {
        // Static utility
    }

    /// Equivalent of React's `<p style={{marginTop: 8, fontSize: 12}}>`, showing the value.
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

    /// Creates a harness for a widget whose popup is a Stroom dialog (shown with
    /// `ShowPopupEvent`, e.g. `DateTimePopup`), which needs the harness's event bus and popup
    /// manager. The widgets make no REST requests.
    ///
    /// @param context The story's context.
    /// @return The harness.
    static ScreenHarness popupHarness(final StoryContext context) {
        return ScreenHarness.create(context, RestFixtures.none());
    }
}
