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

import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's `TextBox` (its copy of GWT's, which reports a change on every input
/// event), matching `Widgets/Inputs/TextInput` in the React Storybook.
public final class TextInputStories {

    private TextInputStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Inputs/TextInput", TextInputStories.class)
                .layout(StoryLayout.CENTERED)
                // Basic controlled text input
                .story("Basic", context -> {
                    final TextBox textBox = InputWidgets.textBox(context, "", "Type something…");
                    final InlineLabel value = StoryPanels.note("Value: (empty)", "#aaa", "0.8rem");
                    textBox.addValueChangeHandler(event -> value.setText("Value: "
                            + (event.getValue().isEmpty()
                            ? "(empty)"
                            : event.getValue())));
                    return minWidth(StoryPanels.column(8, textBox, value));
                })
                // Invalid state - sets aria-invalid and the invalid class
                .story("Invalid", context -> {
                    final TextBox textBox = InputWidgets.textBox(context, "not-an-email", "Email");
                    // Differs from React: Stroom's views mark an invalid field with the invalid class
                    // only (e.g. LoginViewImpl); React also sets aria-invalid="true".
                    textBox.addStyleName("invalid");
                    return textBox;
                })
                // Disabled and read-only variants
                .story("DisabledAndReadonly", context -> {
                    final TextBox disabled = InputWidgets.textBox(context, "Disabled value", null);
                    disabled.setEnabled(false);
                    final TextBox readOnly = InputWidgets.textBox(context, "Read-only value", null);
                    readOnly.setReadOnly(true);
                    return minWidth(StoryPanels.column(10, disabled, readOnly));
                });
    }

    /// Adds `minWidth: 260` to a panel.
    private static Widget minWidth(final FlowPanel panel) {
        panel.getElement().getStyle().setProperty("minWidth", "260px");
        return panel;
    }
}
