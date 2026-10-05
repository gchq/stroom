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

package stroom.gwt.workbench.client.widgets;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.user.cellview.client.CellWidget;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `Widgets/Inputs/TickBox` in the React Storybook.
///
/// The React `TickBox` is a port of [CustomCheckBox] (the `SimpleTickBox` used in Stroom's
/// forms), so that is what these stories show. [CustomCheckBox] has no half-ticked state, so
/// that state is shown using [TickBoxCell], which is how Stroom renders it (e.g. in tables).
public final class TickBoxStories {

    private static final String ON_CHANGE = "onChange";

    private TickBoxStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Inputs/TickBox", TickBoxStories.class)
                .layout(StoryLayout.CENTERED)
                // Basic states - unchecked, checked, indeterminate (all click-to-toggle)
                .story("Basic", context -> StoryPanels.column(10,
                        checkBox(context, false, "Unchecked (click to toggle)"),
                        checkBox(context, true, "Checked (click to toggle)"),
                        halfTick(context, "Indeterminate / half-tick (click to toggle)")))
                // Disabled - non-interactive in each state
                .story("Disabled", context -> StoryPanels.column(10,
                        disabled(checkBox(context, false, "Disabled unchecked")),
                        disabled(checkBox(context, true, "Disabled checked"))))
                // No label - bare checkbox
                .story("NoLabel", context -> checkBox(context, false, null));
    }

    private static CustomCheckBox checkBox(final StoryContext context,
                                           final boolean value,
                                           final String label) {
        final CustomCheckBox checkBox = new CustomCheckBox();
        checkBox.setValue(value);
        if (label != null) {
            checkBox.setLabel(label);
        }
        checkBox.addValueChangeHandler(event ->
                context.action(ON_CHANGE, String.valueOf(event.getValue())));
        return checkBox;
    }

    private static CustomCheckBox disabled(final CustomCheckBox checkBox) {
        checkBox.setEnabled(false);
        return checkBox;
    }

    private static Widget halfTick(final StoryContext context, final String label) {
        final CellWidget<TickBoxState> cell = new CellWidget<>(
                TickBoxCell.create(false, false), TickBoxState.HALF_TICK);
        cell.addValueChangeHandler(event ->
                context.action(ON_CHANGE, String.valueOf(event.getValue())));
        return StoryPanels.row(4, cell, StoryPanels.note(label, "#dce4e5", "inherit"));
    }
}
