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

package stroom.gwt.workbench.client.widgets.inputs;

import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;

/// Widgets shared by the `Widgets/Inputs/*` stories.
final class InputWidgets {

    /// The name of the spy for React's `onChange` callback props.
    static final String ON_CHANGE = "onChange";

    private InputWidgets() {
        // Static utility
    }

    /// Creates a Stroom text box (the GWT equivalent of React's `TextInput` with
    /// `inputClass="gwt-TextBox"`), reporting each change (as the user types) to the `onChange`
    /// spy.
    ///
    /// @param context     The story's context.
    /// @param value       The initial value.
    /// @param placeholder The placeholder, or null for none.
    /// @return The text box.
    static TextBox textBox(final StoryContext context, final String value, final String placeholder) {
        final TextBox textBox = new TextBox();
        textBox.setValue(value);
        if (placeholder != null) {
            // As Stroom's views set it, e.g. QuickFilter
            textBox.getElement().setAttribute("placeholder", placeholder);
        }
        final Spy onChange = context.fn(ON_CHANGE);
        textBox.addValueChangeHandler(event -> onChange.call(event.getValue()));
        return textBox;
    }

    /// Equivalent of `<div style={{maxWidth: ...}}>`.
    ///
    /// @param widget   The widget to wrap.
    /// @param maxWidth The CSS maximum width, e.g. `400px`.
    /// @return A panel containing the widget.
    static Widget maxWidth(final Widget widget, final String maxWidth) {
        final FlowPanel panel = new FlowPanel();
        panel.getElement().getStyle().setProperty("maxWidth", maxWidth);
        panel.add(widget);
        return panel;
    }
}
