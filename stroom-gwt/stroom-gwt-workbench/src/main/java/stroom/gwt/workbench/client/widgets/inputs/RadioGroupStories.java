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
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.RadioGroup;
import stroom.widget.form.client.TextBox;

/// Stories for [RadioGroup], a choice of one of a few options, all shown (`radio-group`): a
/// WAI-ARIA radio group of native radio buttons, one tab stop, with the arrow keys between them.
public final class RadioGroupStories {

    private static final String ON_CHANGE = InputWidgets.ON_CHANGE;
    private static final String GROUP = "Graphic";

    private RadioGroupStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Inputs/RadioGroup", RadioGroupStories.class)
                .layout(StoryLayout.CENTERED)
                // In a form group, which names it: Shape chosen
                .story("Basic", context -> StoryPanels.column(10,
                        namedTextBox("Before"),
                        inFormGroup(radioGroup(context)),
                        namedTextBox("After")))
                .withPlay(play -> {
                    // A radio group named by its form group's label, each option by its text
                    final Query group = play.getByRole("radiogroup", GROUP);
                    play.expect(group).toHaveClass("radio-group");
                    play.expect(play.getByRole("radio", "Shape")).toBeChecked();
                    play.expect(play.getByRole("radio", "Icon")).not().toBeChecked();
                    // Clicking an option's text chooses it, reporting the change once
                    play.click(play.getByText("Icon"));
                    play.expect(play.getByRole("radio", "Icon")).toBeChecked();
                    play.expect(play.getByRole("radio", "Shape")).not().toBeChecked();
                    play.expect(play.spy(ON_CHANGE)).toHaveBeenCalledTimes(1);
                    play.expect(play.spy(ON_CHANGE)).toHaveBeenLastCalledWith("ICON");
                    // The arrow keys move between the options, choosing as they go
                    play.keyboard("{ArrowRight}");
                    play.expect(play.getByRole("radio", "Image")).toBeChecked();
                    play.expect(play.getByRole("radio", "Image")).toHaveFocus();
                    play.expect(play.spy(ON_CHANGE)).toHaveBeenLastCalledWith("IMAGE");
                    // One tab stop: Tab leaves the group, and Shift+Tab comes back to the chosen option
                    play.tab();
                    play.expect(play.getByRole("textbox", "After")).toHaveFocus();
                    play.tab(true);
                    play.expect(play.getByRole("radio", "Image")).toHaveFocus();
                    // BEM (radio-button.css): an option is a label holding its radio button and text
                    play.expect(play.querySelectorAll("label.radio-group__option > input.radio-group__input"))
                            .toHaveLength(3);
                    play.expect(play.querySelectorAll("label.radio-group__option > .radio-group__label"))
                            .toHaveLength(3);
                    play.expect(play.querySelectorAll(".gwt-RadioButton")).toHaveLength(0);
                })
                // Disabled: none of its options can be chosen
                .story("Disabled", context -> {
                    final RadioGroup<String> radioGroup = radioGroup(context);
                    radioGroup.setEnabled(false);
                    return inFormGroup(radioGroup);
                })
                .withPlay(play -> {
                    play.expect(play.getByRole("radio", "Shape")).toBeDisabled();
                    play.expect(play.getByRole("radio", "Shape")).toBeChecked();
                    play.click(play.getByText("Icon"));
                    play.expect(play.getByRole("radio", "Icon")).not().toBeChecked();
                    play.expect(play.spy(ON_CHANGE)).not().toHaveBeenCalled();
                })
                // Named by setAccessibleName, where no form group names it (e.g. in a table's row)
                .story("AccessibleName", context -> {
                    final RadioGroup<String> radioGroup = radioGroup(context);
                    radioGroup.setAccessibleName("Mode");
                    return radioGroup;
                })
                .withPlay(play -> {
                    play.expect(play.getByRole("radiogroup", "Mode")).toBeInTheDocument();
                    // Each option's tooltip says what it does
                    play.expect(play.getByText("Shape").closest("label"))
                            .toHaveAttribute("title", "Draw a coloured shape");
                });
    }

    private static RadioGroup<String> radioGroup(final StoryContext context) {
        final RadioGroup<String> radioGroup = new RadioGroup<>();
        radioGroup.addItem("SHAPE", "Shape", "Draw a coloured shape");
        radioGroup.addItem("ICON", "Icon", "Draw one of the built-in icons");
        radioGroup.addItem("IMAGE", "Image", "Draw an image");
        radioGroup.setValue("SHAPE");
        final Spy onChange = context.fn(ON_CHANGE);
        radioGroup.addValueChangeHandler(event -> onChange.call(event.getValue()));
        return radioGroup;
    }

    // A text box before or after the group, to tab from and to
    private static TextBox namedTextBox(final String name) {
        final TextBox textBox = new TextBox();
        textBox.getElement().setAttribute("aria-label", name);
        return textBox;
    }

    private static FormGroup inFormGroup(final RadioGroup<String> radioGroup) {
        final FormGroup formGroup = new FormGroup();
        formGroup.setIdentity("radioGroupGraphic");
        formGroup.setLabel(GROUP);
        formGroup.add(radioGroup);
        return formGroup;
    }
}
