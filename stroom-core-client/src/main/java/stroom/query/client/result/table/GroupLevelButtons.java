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

package stroom.query.client.result.table;

import stroom.query.api.GroupSelection;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;

import java.util.HashSet;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/// A grouped table's Expand and Collapse buttons, which open and close its groups a level at a time.
///
/// The screen keeps the table's [GroupSelection]: pressing a button gives it the selection with one
/// more level open (Expand) or one fewer (Collapse), and whenever the selection or the number of
/// levels changes the screen calls [#update(GroupSelection, int)], which names each button for the
/// level it goes to and enables it only while there is a level to go to.
public class GroupLevelButtons {

    private final ToolbarButton expandButton;
    private final ToolbarButton collapseButton;

    /// Adds the Expand and Collapse buttons to a toolbar, both disabled until [#update] is called.
    ///
    /// @param toolbar   The toolbar to add them to.
    /// @param selection The table's group selection.
    /// @param maxDepth  The number of levels the table's groups have.
    /// @param onChange  Given the selection to use when a button is pressed.
    public GroupLevelButtons(final Toolbar toolbar,
                             final Supplier<GroupSelection> selection,
                             final IntSupplier maxDepth,
                             final Consumer<GroupSelection> onChange) {
        expandButton = toolbar.button(SvgImage.EXPAND_ALL, "Expand Groups", () ->
                onChange.accept(expand(selection.get(), maxDepth.getAsInt())));
        collapseButton = toolbar.button(SvgImage.COLLAPSE_ALL, "Collapse Groups", () ->
                onChange.accept(collapse(selection.get())));
        expandButton.setEnabled(false);
        collapseButton.setEnabled(false);
    }

    /// Names and enables the buttons for the table's groups as they are now.
    ///
    /// @param groupSelection The table's group selection.
    /// @param maxDepth       The number of levels the table's groups have.
    public void update(final GroupSelection groupSelection, final int maxDepth) {
        final int expandedDepth = groupSelection.getExpandedDepth();

        final boolean canExpand = maxDepth > 0
                                  && (expandedDepth < maxDepth || groupSelection.hasClosedGroups());
        expandButton.setEnabled(canExpand);
        expandButton.setTitle(canExpand
                ? "Expand Groups to Level " + Math.min(maxDepth, expandedDepth + 1)
                : "Expand Groups");

        final boolean canCollapse = maxDepth > 0
                                    && (expandedDepth > 0 || groupSelection.hasOpenGroups());
        collapseButton.setEnabled(canCollapse);
        collapseButton.setTitle(canCollapse
                ? "Collapse Groups Level " + Math.min(maxDepth, Math.max(1, expandedDepth))
                : "Collapse Groups");
    }

    // The selection with every group open to one more level
    private static GroupSelection expand(final GroupSelection groupSelection, final int maxDepth) {
        return groupSelection.copy()
                .closedGroups(new HashSet<>())
                .expand(maxDepth)
                .build();
    }

    // The selection with every group closed to one fewer level
    private static GroupSelection collapse(final GroupSelection groupSelection) {
        return groupSelection.copy()
                .collapse()
                .openGroups(new HashSet<>())
                .build();
    }
}
