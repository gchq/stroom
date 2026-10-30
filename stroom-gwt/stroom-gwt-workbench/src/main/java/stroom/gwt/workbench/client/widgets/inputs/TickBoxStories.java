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
import stroom.widget.tickbox.client.TickBox;

/// Stories for [TickBox], the tick box (`tick-box`) used in Stroom's forms. A form's tick box is
/// ticked or not (e.g. whether something is enabled), so it has no half-ticked state: that belongs
/// to the tick box of Stroom's grids, which is smaller and has its own stories
/// (`Widgets/Cell Renderers/TickBoxCell`).
public final class TickBoxStories {

    private static final String ON_CHANGE = InputWidgets.ON_CHANGE;
    private static final String UNCHECKED = "Unchecked (click to toggle)";
    private static final String CHECKED = "Checked (click to toggle)";

    private TickBoxStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Inputs/TickBox", TickBoxStories.class)
                .layout(StoryLayout.CENTERED)
                // Basic states - unchecked and checked (click to toggle)
                // No indeterminate tick box, as a form's tick box has no half-ticked state
                .story("Basic", context -> StoryPanels.column(10,
                        checkBox(context, false, UNCHECKED),
                        checkBox(context, true, CHECKED)))
                .withPlay(play -> {
                    // Each tick box is named by its label
                    play.expect(play.getByRole("checkbox", CHECKED)).toBeChecked();
                    play.expect(play.getByRole("checkbox", UNCHECKED)).not().toBeChecked();
                    // Clicking the label toggles the tick box, reporting the change once
                    play.click(play.getByText(UNCHECKED));
                    play.expect(play.getByRole("checkbox", UNCHECKED)).toBeChecked();
                    play.expect(play.spy(ON_CHANGE)).toHaveBeenCalledTimes(1);
                    play.expect(play.spy(ON_CHANGE)).toHaveBeenLastCalledWith(true);
                    play.click(play.getByRole("checkbox", CHECKED));
                    play.expect(play.getByRole("checkbox", CHECKED)).not().toBeChecked();
                    play.expect(play.spy(ON_CHANGE)).toHaveBeenCalledTimes(2);
                    play.expect(play.spy(ON_CHANGE)).toHaveBeenLastCalledWith(false);
                    // BEM (tick-box.css): a label wrapping the check box, its drawn box and its text,
                    // with no other wrapper
                    final Query input = play.getByRole("checkbox", CHECKED);
                    play.expect(input).toHaveClass("tick-box__input");
                    play.expect(input.closest("label")).toHaveClass("tick-box");
                    play.expect(play.querySelectorAll("label.tick-box > .tick-box__mark")).toHaveLength(2);
                    play.expect(play.querySelectorAll("label.tick-box > .tick-box__label")).toHaveLength(2);
                    play.expect(play.querySelectorAll(".SimpleTickBox, .checkbox-container")).toHaveLength(0);
                })
                // Read only - can be focused and read, but clicking it or its label changes nothing
                .story("ReadOnly", context -> {
                    final TickBox checkBox = checkBox(context, true, "Read only");
                    checkBox.setReadOnly(true);
                    return checkBox;
                })
                .withPlay(play -> {
                    final Query input = play.getByRole("checkbox", "Read only");
                    play.expect(input).toHaveAttribute("aria-readonly", "true");
                    play.click(play.getByText("Read only"));
                    play.expect(input).toBeChecked();
                    play.expect(play.spy(ON_CHANGE)).not().toHaveBeenCalled();
                    // The state is the attribute alone, not a class as well
                    play.expect(input.closest("label")).not().toHaveClass("tick-box-readonly");
                })
                // Disabled - non-interactive in each state
                .story("Disabled", context -> StoryPanels.column(10,
                        disabled(checkBox(context, false, "Disabled unchecked")),
                        disabled(checkBox(context, true, "Disabled checked"))))
                .withPlay(play -> {
                    play.expect(play.getByRole("checkbox", "Disabled checked")).toBeDisabled();
                    play.expect(play.getByRole("checkbox", "Disabled checked")).toBeChecked();
                    // Clicking the label of a disabled tick box changes nothing
                    play.click(play.getByText("Disabled unchecked"));
                    play.expect(play.getByRole("checkbox", "Disabled unchecked")).not().toBeChecked();
                    play.expect(play.spy(ON_CHANGE)).not().toHaveBeenCalled();
                })
                // Its text set with GWT's setText: the label's text, not the box's (which it once replaced,
                // losing the tick)
                .story("SetText", context -> {
                    final TickBox checkBox = checkBox(context, true, null);
                    checkBox.setText("Set by setText");
                    return checkBox;
                })
                .withPlay(play -> {
                    final Query input = play.getByRole("checkbox", "Set by setText");
                    play.expect(input).toBeChecked();
                    play.expect(play.querySelector(".tick-box__label")).toHaveTextContent("Set by setText");
                    play.expect(play.querySelector(".tick-box__mark svg")).toBeInTheDocument();
                })
                // No label - a bare tick box, named for screen readers as a view would name it
                .story("NoLabel", context -> {
                    final TickBox checkBox = checkBox(context, false, null);
                    checkBox.setAriaLabel("Agree");
                    return checkBox;
                })
                .withPlay(play -> play.expect(play.getByRole("checkbox", "Agree")).not().toBeChecked());
    }

    private static TickBox checkBox(final StoryContext context,
                                           final boolean value,
                                           final String label) {
        final TickBox checkBox = new TickBox();
        checkBox.setValue(value);
        if (label != null) {
            checkBox.setLabel(label);
        }
        final Spy onChange = context.fn(ON_CHANGE);
        checkBox.addValueChangeHandler(event -> onChange.call(event.getValue()));
        return checkBox;
    }

    private static TickBox disabled(final TickBox checkBox) {
        checkBox.setEnabled(false);
        return checkBox;
    }
}
