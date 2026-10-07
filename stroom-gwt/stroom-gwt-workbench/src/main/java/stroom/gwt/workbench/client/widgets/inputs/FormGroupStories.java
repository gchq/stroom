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

import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.form.client.DescriptionHTML;
import stroom.widget.form.client.FieldValidity;
import stroom.widget.form.client.FormGroup;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;

/// Stories for [FormGroup], matching `Widgets/Inputs/FormGroup` in the React Storybook.
///
/// React's `TextInput` children are Stroom's `TextBox` (see [TextInputStories]).
public final class FormGroupStories {

    private static final String MAX_WIDTH = "400px";

    private FormGroupStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Inputs/FormGroup", FormGroupStories.class)
                .layout(StoryLayout.CENTERED)
                // Basic labelled field wrapping a TextInput
                .story("Basic", context -> {
                    final FormGroup formGroup = formGroup("fg-name", "Display Name");
                    formGroup.add(InputWidgets.textBox(context, "", "Enter display name…"));
                    return InputWidgets.maxWidth(formGroup, MAX_WIDTH);
                })
                // With a ? help popup (help text) and an always-visible description
                .story("WithHelp", context -> {
                    final FormGroup formGroup = formGroup("fg-email", "Email address");
                    formGroup.setHelpText("The email address used for notifications. Must be a valid address "
                                          + "in the format user@domain.com.");
                    formGroup.add(description("Used to send pipeline failure notifications."));
                    formGroup.add(InputWidgets.textBox(context, "", "user@example.com"));
                    return InputWidgets.maxWidth(formGroup, MAX_WIDTH);
                })
                // Validation feedback - the message updates when the value changes
                .story("Feedback", context -> {
                    final FormGroup formGroup = formGroup("fg-validated", "Username");
                    final TextBox textBox = InputWidgets.textBox(context, "", "Choose a username…");
                    // Differs from React: GWT's FormGroup has no feedback setter (its invalid-feedback
                    // label is private and always empty). Stroom's views (e.g. ChangePasswordViewImpl)
                    // add their own "feedback" label under the control and mark the control with
                    // FieldValidity, so this story does the same.
                    // Differs from React: the message updates when the text box reports its value,
                    // on leaving it (React's updates as you type)
                    final Label feedback = new Label();
                    feedback.setStyleName("feedback");
                    textBox.addValueChangeHandler(event -> validate(textBox, feedback));
                    validate(textBox, feedback);
                    final FlowPanel control = new FlowPanel();
                    control.add(textBox);
                    control.add(feedback);
                    formGroup.add(control);
                    return InputWidgets.maxWidth(formGroup, MAX_WIDTH);
                })
                .withPlay(play -> {
                    // The label names the text box in the panel, which is marked invalid and
                    // described by its feedback
                    final Query username = play.getByLabelText("Username");
                    play.expect(username).toHaveAttribute("aria-invalid", "true");
                    play.expect(username).toHaveAttribute("aria-describedby");
                    play.expect(play.getByText("This field is required.")).toBeInTheDocument();
                    // A text box reports its new value on leaving it
                    play.type(username, "alice");
                    play.tab();
                    play.waitFor(() -> play.expect(username).not().toHaveAttribute("aria-invalid"));
                    play.expect(username).not().toHaveAttribute("aria-describedby");
                    play.expect(username).not().toHaveClass("invalid");
                    // Ends invalid, so the screenshot shows how an invalid field looks
                    play.clear(username);
                    play.type(username, "ab");
                    play.tab();
                    play.waitFor(() -> play.expect(play.getByText("Must be at least 3 characters."))
                            .toBeInTheDocument());
                    play.expect(username).toHaveAttribute("aria-invalid", "true");
                    play.expect(username).toHaveClass("invalid");
                })
                // Disabled state - greys out the label and description
                .story("Disabled", context -> {
                    final FormGroup formGroup = formGroup("fg-disabled", "Read-only setting");
                    formGroup.add(description("This field is currently disabled."));
                    final TextBox textBox = InputWidgets.textBox(context, "Locked value", null);
                    textBox.setEnabled(false);
                    formGroup.add(textBox);
                    formGroup.setDisabled(true);
                    return InputWidgets.maxWidth(formGroup, MAX_WIDTH);
                });
    }

    private static FormGroup formGroup(final String identity, final String label) {
        final FormGroup formGroup = new FormGroup();
        formGroup.setIdentity(identity);
        formGroup.setLabel(label);
        return formGroup;
    }

    private static DescriptionHTML description(final String text) {
        final DescriptionHTML description = new DescriptionHTML();
        description.setText(text);
        return description;
    }

    /// React's rule: required, then at least 3 characters.
    private static void validate(final TextBox textBox, final Label feedback) {
        final String value = textBox.getValue();
        final String message = value.isEmpty()
                ? "This field is required."
                : value.length() < 3
                        ? "Must be at least 3 characters."
                        : "";
        if (message.isEmpty()) {
            FieldValidity.setValid(textBox, feedback);
        } else {
            FieldValidity.setInvalid(textBox, feedback, message);
        }
    }
}
