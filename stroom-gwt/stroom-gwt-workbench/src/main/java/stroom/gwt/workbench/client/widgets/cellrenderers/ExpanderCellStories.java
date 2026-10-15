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

package stroom.gwt.workbench.client.widgets.cellrenderers;

import stroom.cell.expander.client.ExpanderCell;
import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.shared.Expander;

import com.google.gwt.user.cellview.client.CellWidget;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [ExpanderCell]. Each cell is a `CellWidget` whose value is the row's
/// [Expander]. The cell reports a click on a branch by updating the value, which the `CellWidget`
/// reports as a value change; as in Stroom's trees, the story then gives the cell the toggled
/// expander.
public final class ExpanderCellStories {

    private static final String ON_TOGGLE = "onToggle";

    private ExpanderCellStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Cell Renderers/ExpanderCell", ExpanderCellStories.class)
                .layout(StoryLayout.CENTERED)
                // No args: every story has its own render
                // Click the expander to toggle the expand/collapse state
                .story("Toggle", ExpanderCellStories::toggle)
                // A tree of nodes at various depths, showing leaf, expanded and collapsed icons
                .story("Tree", context -> StoryPanels.column(2,
                        expander(0, false, true),
                        expander(1, false, true),
                        expander(2, true, false),
                        expander(1, false, false),
                        expander(0, true, false)));
    }

    private static Widget toggle(final StoryContext context) {
        final CellWidget<Expander> cell = expander(0, false, true);
        final InlineLabel state = new InlineLabel("Expanded");
        final Spy onToggle = context.fn(ON_TOGGLE);
        cell.addValueChangeHandler(event -> {
            final Expander expander = event.getValue();
            final boolean expanded = !expander.isExpanded();
            onToggle.call(expanded);
            cell.setValue(new Expander(expander.getDepth(), expanded, expander.isLeaf()));
            state.setText(expanded
                    ? "Expanded"
                    : "Collapsed");
        });
        return StoryPanels.row(8, cell, state);
    }

    private static CellWidget<Expander> expander(final int depth, final boolean leaf, final boolean expanded) {
        return new CellWidget<>(new ExpanderCell(), new Expander(depth, expanded, leaf));
    }
}
