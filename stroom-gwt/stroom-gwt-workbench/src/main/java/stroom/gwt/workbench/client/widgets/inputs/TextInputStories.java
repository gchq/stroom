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
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.form.client.AccessibleName;
import stroom.widget.form.client.FieldValidity;
import stroom.widget.form.client.TextArea;
import stroom.widget.form.client.TextBox;
import stroom.widget.text.client.InlineLabel;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's `TextBox` and `TextArea` (its copies of GWT's: a text box reports a change
/// on every input event, and both are the BEM block `text-field`, a text area with the
/// `text-field--multiline` modifier, styled by `text-field.css`).
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
                .withPlay(play -> {
                    // The text field block, not GWT's gwt-TextBox
                    play.expect(play.getByRole("textbox")).toHaveClass("text-field");
                    play.expect(play.getByRole("textbox")).not().toHaveClass("gwt-TextBox");
                })
                // A text area: the same field over several lines
                .story("MultiLine", context -> {
                    final TextArea textArea = new TextArea();
                    AccessibleName.set(textArea, "Description");
                    textArea.setText("A first line\nand a second");
                    return minWidth(StoryPanels.column(8, textArea));
                })
                .withPlay(play -> {
                    final Query area = play.getByRole("textbox", "Description");
                    play.expect(area).toHaveClass("text-field");
                    play.expect(area).toHaveClass("text-field--multiline");
                    play.expect(area).not().toHaveClass("gwt-TextArea");
                })
                // Invalid state: aria-invalid, which also draws its red border (not a class as well)
                .story("Invalid", context -> {
                    final TextBox textBox = InputWidgets.textBox(context, "not-an-email", "Email");
                    // As Stroom's views (e.g. Login) mark an invalid field
                    FieldValidity.setInvalid(textBox.getElement(), true);
                    return textBox;
                })
                .withPlay(play -> {
                    play.expect(play.getByRole("textbox")).toHaveAttribute("aria-invalid", "true");
                    play.expect(play.getByRole("textbox")).not().toHaveClass("invalid");
                })
                // Disabled and read-only variants
                .story("DisabledAndReadonly", context -> {
                    final TextBox disabled = InputWidgets.textBox(context, "Disabled value", null);
                    AccessibleName.set(disabled, "Disabled");
                    disabled.setEnabled(false);
                    final TextBox readOnly = InputWidgets.textBox(context, "Read-only value", null);
                    AccessibleName.set(readOnly, "Read only");
                    readOnly.setReadOnly(true);
                    return minWidth(StoryPanels.column(10, disabled, readOnly));
                })
                .withPlay(play -> {
                    final Query readOnly = play.getByRole("textbox", "Read only");
                    play.expect(readOnly).toHaveAttribute("readonly");
                    // The readonly attribute alone, not GWT's <primary>-readonly class as well
                    play.expect(readOnly).not().toHaveClass("text-field-readonly");
                    play.expect(play.getByRole("textbox", "Disabled")).toBeDisabled();
                });
    }

    /// Adds `minWidth: 260` to a panel.
    private static Widget minWidth(final FlowPanel panel) {
        panel.getElement().getStyle().setProperty("minWidth", "260px");
        return panel;
    }
}
