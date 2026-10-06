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

package stroom.gwt.workbench.client.widgets.dialogs;

import stroom.gwt.workbench.client.StoryPanels;
import stroom.widget.button.client.Button;

import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.web.bindery.event.shared.EventBus;

/// Widgets shared by the `Widgets/Dialogs/*` stories.
final class DialogWidgets {

    /// The React stories' `Button variant="contained-primary"`.
    static final String PRIMARY = "Button--contained-primary";
    /// The React stories' `Button variant="contained-secondary"`.
    static final String SECONDARY = "Button--contained-secondary";

    private DialogWidgets() {
        // Static utility
    }

    /// A Stroom button that opens a dialog, as the React stories' trigger buttons.
    ///
    /// @param text         The button's text.
    /// @param variantClass The variant's style name, e.g. [#PRIMARY].
    /// @param onClick      What clicking it does.
    /// @return The button.
    static Button button(final String text, final String variantClass, final ClickHandler onClick) {
        final Button button = new Button();
        button.setText(text);
        button.addStyleName(variantClass);
        button.addClickHandler(onClick);
        return button;
    }

    /// The React stories' result echo, `<span style={{fontSize: '0.85rem'}}>`, hidden until set.
    ///
    /// @return The label.
    static InlineLabel result() {
        final InlineLabel label = StoryPanels.note("", "#ccc", "0.85rem");
        label.setVisible(false);
        return label;
    }

    /// Shows text in a [#result()] label.
    ///
    /// @param label The label.
    /// @param text  The text to show.
    static void setResult(final InlineLabel label, final String text) {
        label.setText(text);
        label.setVisible(true);
    }

    /// Adapts an event bus to the `HasHandlers` that Stroom's `fire` methods take.
    ///
    /// @param eventBus The event bus.
    /// @return Something that fires events on the bus.
    static HasHandlers handlers(final EventBus eventBus) {
        return eventBus::fireEvent;
    }
}
