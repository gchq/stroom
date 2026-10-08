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

import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.form.client.DescriptionHTML;
import stroom.widget.form.client.FieldValidity;
import stroom.widget.form.client.FormGroup;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextBox;

/// Stories for [FormGroup], matching `Widgets/Inputs/FormGroup` in the React Storybook.
///
/// React's `TextInput` children are Stroom's `TextBox` (see [TextInputStories]).
public final class FormGroupStories {

    private static final String MAX_WIDTH = "400px";
    private static final String STORE_SIZE_HELP = "The largest the store may grow to on each node, e.g. 10GiB "
                                                  + "or 500MiB. When it is reached, writes to the store fail "
                                                  + "until data is removed. Invalid values are replaced with "
                                                  + "10GiB.";
    private static final String STORE_SIZE_SR = "Size with units, e.g. 10GiB";
    private static final String RETAIN_FOR_SR = "How long to keep the data";
    private static final String TICK_HELP = "Whether a redirect response is followed.";

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
                    // As Stroom shows a help button's popup
                    StoryPopups.create(context).withHelp();
                    final FormGroup formGroup = formGroup("fg-email", "Email address");
                    formGroup.setHelpText("The email address used for notifications. Must be a valid address "
                                          + "in the format user@domain.com.");
                    formGroup.add(description("Used to send pipeline failure notifications."));
                    formGroup.add(InputWidgets.textBox(context, "", "user@example.com"));
                    return InputWidgets.maxWidth(formGroup, MAX_WIDTH);
                })
                .withPlay(play -> {
                    // The help is the text box's description, and F1 shows it, as the help button
                    // isn't in the tab order
                    final Query email = play.getByLabelText("Email address");
                    play.expect(email).toHaveAccessibleDescription("The email address used for notifications. "
                                                                   + "Must be a valid address in the format "
                                                                   + "user@domain.com.");
                    play.expect(email).toHaveAttribute("aria-keyshortcuts", "F1");
                    final Query help = play.getByTitle("Email address - Click for help");
                    play.expect(help).toHaveAttribute("tabindex", "-2");
                    // Clicking the help button shows the help, leaving the focus in the text box
                    play.click(email);
                    play.click(help);
                    play.expect(play.screen().findByText(TextMatch.containing("used for notifications"),
                                    ".help-button-tooltip *"))
                            .toBeInTheDocument();
                    play.expect(email).toHaveFocus();
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
                    play.expect(username).toHaveAccessibleDescription("This field is required.");
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
                })
                // GWT-only: a short screen reader description, read on each focus, beside fuller help
                .story("ScreenReaderText", context -> {
                    StoryPopups.create(context).withHelp();
                    final FormGroup formGroup = formGroup("fg-store-size", "Max Store Size");
                    formGroup.setHelpText(STORE_SIZE_HELP);
                    formGroup.setScreenReaderText(STORE_SIZE_SR);
                    formGroup.setRequired(true);
                    formGroup.add(InputWidgets.textBox(context, "10GiB", null));
                    return InputWidgets.maxWidth(formGroup, MAX_WIDTH);
                })
                .withPlay(play -> {
                    // The short text is read, not the fuller help, which F1 or the help button shows
                    final Query size = play.getByLabelText("Max Store Size");
                    play.expect(size).toHaveAccessibleDescription(STORE_SIZE_SR);
                    play.expect(size).toHaveAttribute("aria-keyshortcuts", "F1");
                    // A required field is announced as required (not in its description)
                    play.expect(size).toHaveAttribute("aria-required", "true");
                    play.click(play.getByTitle("Max Store Size - Click for help"));
                    play.expect(play.screen().findByText(STORE_SIZE_HELP, ".help-button-tooltip *"))
                            .toBeInTheDocument();
                })
                // GWT-only: a tick box carries its own label, beside the box, so the group has none and
                // its help goes on the same line, right after the tick box's label
                .story("TickBoxWithHelp", context -> {
                    StoryPopups.create(context).withHelp();
                    final FormGroup formGroup = new FormGroup();
                    formGroup.setIdentity("fg-tick");
                    formGroup.setHelpText(TICK_HELP);
                    final CustomCheckBox tickBox = new CustomCheckBox();
                    tickBox.setLabel("Follow Redirects");
                    formGroup.add(tickBox);
                    formGroup.getElement().setAttribute("data-testid", "fg-tick-group");
                    return InputWidgets.maxWidth(formGroup, MAX_WIDTH);
                })
                .withPlay(play -> {
                    final Query group = play.getByTestId("fg-tick-group");
                    play.expect(group).toHaveClass("form-group--inline-tick");
                    play.expect(play.within(group).getByRole("checkbox", "Follow Redirects"))
                            .toBeInTheDocument();
                    // The help is for the tick box's label, as it would be for a group label
                    play.click(play.within(group).getByTitle("Follow Redirects - Click for help"));
                    play.expect(play.screen().findByText(TICK_HELP, ".help-button-tooltip *"))
                            .toBeInTheDocument();
                })
                // GWT-only: a group of several controls (an amount and its unit), which one label can't
                // name: the label names the group and each control has its own name
                .story("SeveralControls", context -> {
                    final FormGroup formGroup = formGroup("fg-retain-for", "Retain For");
                    formGroup.setScreenReaderText(RETAIN_FOR_SR);
                    formGroup.setControlNames("Amount, Unit");
                    final TextBox amount = InputWidgets.textBox(context, "30", null);
                    final ListBox unit = new ListBox();
                    unit.addItem("Days");
                    unit.addItem("Weeks");
                    final FlowPanel control = new FlowPanel();
                    control.add(amount);
                    control.add(unit);
                    formGroup.add(control);
                    return InputWidgets.maxWidth(formGroup, MAX_WIDTH);
                })
                .withPlay(play -> {
                    final Query group = play.getByRole("group", "Retain For");
                    play.expect(group).toHaveAccessibleDescription(RETAIN_FOR_SR);
                    play.expect(play.within(group).getByRole("textbox", "Amount")).toHaveValue("30");
                    play.expect(play.within(group).getByRole("combobox", "Unit")).toHaveValue("Days");
                    // The label names the group, not either control
                    play.expect(play.queryByRole("textbox", "Retain For")).toBeNull();
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
