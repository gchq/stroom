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

package stroom.gwt.workbench.client.widgets.layout;

import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Widget;

/// The panes and frame shared by the `Widgets/Layout/SplitLayoutPanel` and
/// `Widgets/Layout/ThinSplitLayoutPanel` stories, and the ways the stories configure the split,
/// applied to Stroom's split panels (both [DockLayoutPanel]s) as Stroom's `.ui.xml` files do.
final class SplitPanes {

    /// The docked pane's default size.
    static final double DEFAULT_SIZE = 300;

    private SplitPanes() {
        // Static utility
    }

    /// The left (first) pane.
    ///
    /// @param hint The second line of text, which differs between the two components' stories.
    /// @return The pane.
    static Widget leftPane(final String hint) {
        final Widget pane = pane("Left panel", hint);
        pane.getElement().getStyle().setProperty("background", "var(--sidebar__background-color,#1e2229)");
        return pane;
    }

    /// The right (second) pane.
    ///
    /// @return The pane.
    static Widget rightPane() {
        return pane("Right panel", "Content goes here");
    }

    /// The stories' frame: 300px high with a rounded border.
    ///
    /// @param split The split panel, which fills the frame.
    /// @return The frame.
    static Widget frame(final DockLayoutPanel split) {
        final FlowPanel frame = new FlowPanel();
        final Style style = frame.getElement().getStyle();
        style.setProperty("height", "300px");
        style.setProperty("border", "1px solid var(--border-color,#444)");
        style.setProperty("borderRadius", "4px");
        style.setProperty("overflow", "hidden");
        // A layout panel needs a size to lay its children out in
        style.setProperty("position", "relative");
        split.setSize("100%", "100%");
        frame.add(split);
        return frame;
    }

    /// Lays the two panes out as a story asks, as a `.ui.xml` would with
    /// `<g:west size="N">`/`<g:north>`/`<g:east>` and `<g:center>`.
    ///
    /// @param split       The split panel.
    /// @param vertical    Whether the panes are one above the other.
    /// @param anchorEnd   Whether the second pane is the docked one.
    /// @param size        The docked pane's size in pixels.
    /// @param leftHint    The second line of the left pane's text.
    /// @return The docked pane, e.g. for setting its minimum size.
    static Widget layout(final DockLayoutPanel split,
                         final boolean vertical,
                         final boolean anchorEnd,
                         final double size,
                         final String leftHint) {
        final Widget first = leftPane(leftHint);
        final Widget second = rightPane();
        if (anchorEnd) {
            if (vertical) {
                split.addSouth(second, size);
            } else {
                split.addEast(second, size);
            }
            split.add(first);
            return second;
        }
        if (vertical) {
            split.addNorth(first, size);
        } else {
            split.addWest(first, size);
        }
        split.add(second);
        return first;
    }

    private static Widget pane(final String title, final String hint) {
        final HTML pane = new HTML("<p style=\"margin: 0 0 8px; font-weight: 600; font-size: 0.85rem\">"
                                   + title
                                   + "</p><p style=\"margin: 0; font-size: 0.8rem; color: var(--text-color,#aaa)\">"
                                   + hint
                                   + "</p>");
        final Style style = pane.getElement().getStyle();
        style.setProperty("padding", "16px");
        style.setProperty("height", "100%");
        style.setProperty("overflow", "auto");
        return pane;
    }
}
