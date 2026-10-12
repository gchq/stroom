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

package stroom.widget.util.client;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.DockLayoutPanel.Direction;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/// Puts a dock layout's panes in reading order, so that Tab moves through them as they are seen:
/// top, left, centre, right, bottom (WCAG 2.4.3, Focus Order).
///
/// A [DockLayoutPanel] (e.g. a split between panes) must have its centre pane added last, so a pane
/// to the right of or below the centre comes before it in the page, and Tab reaches it first. The
/// panes are placed absolutely, so their order in the page can change without moving them: this
/// reorders their containers, leaving the splitters (which take the mouse between the panes)
/// after them, as they were.
public final class ReadingOrder {

    private static final String SPLITTER_CLASS = "Dragger";

    private ReadingOrder() {
        // Static utility
    }

    /// Puts a dock layout's panes in reading order. Call once its panes are added, e.g. after
    /// binding a template.
    ///
    /// @param panel The dock layout.
    public static void apply(final DockLayoutPanel panel) {
        final List<Widget> panes = new ArrayList<>();
        for (final Widget widget : panel) {
            panes.add(widget);
        }
        // A stable sort, so panes on the same side keep their order
        panes.sort(Comparator.comparingInt(widget -> rank(panel, widget)));
        final Element parent = panel.getElement();
        for (final Widget pane : panes) {
            parent.appendChild(panel.getWidgetContainerElement(pane));
        }
    }

    private static int rank(final DockLayoutPanel panel, final Widget widget) {
        if (widget.getStyleName().contains(SPLITTER_CLASS)) {
            return 5;
        }
        final Direction direction = panel.getWidgetDirection(widget);
        return switch (direction) {
            case NORTH -> 0;
            case WEST, LINE_START -> 1;
            case CENTER -> 2;
            case EAST, LINE_END -> 3;
            case SOUTH -> 4;
        };
    }
}
