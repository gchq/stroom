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

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;

/// Widgets shared by the `Widgets/Cell Renderers/*` stories.
///
/// Stroom's cells are GWT `Cell`s that are only ever rendered in a table. The stories render each
/// cell with a GWT `CellWidget` inside a bordered box ([#cellBox]), so the cell's own events (e.g.
/// its mouse down on the copy and open buttons) reach it as in a grid.
final class CellRendererWidgets {

    /// The text of the `Last action` echo before anything is done.
    static final String NO_ACTION = "—";

    private CellRendererWidgets() {
        // Static utility
    }

    /// A box for a cell: a `div` of the given width with `padding: 2px 6px` and a
    /// `1px solid var(--panel__border-color,#555)` border.
    ///
    /// @param widget  The cell's widget.
    /// @param widthPx The width of the box in pixels.
    /// @return The box holding the widget.
    static FlowPanel cellBox(final Widget widget, final int widthPx) {
        final FlowPanel box = new FlowPanel();
        final Style style = box.getElement().getStyle();
        style.setProperty("width", widthPx + "px");
        style.setProperty("padding", "2px 6px");
        style.setProperty("border", "1px solid var(--panel__border-color,#555)");
        style.setProperty("boxSizing", "border-box");
        // Stroom's cells don't ellipsise their text, so the box clips a long name as a grid's fixed
        // width cell would
        style.setProperty("overflow", "hidden");
        box.add(widget);
        return box;
    }

    /// Equivalent of `<span style={{fontSize: 12}}>Last action: {msg}</span>`.
    ///
    /// @return The label, showing that nothing has been done yet.
    static InlineLabel lastAction() {
        final InlineLabel label = new InlineLabel("Last action: " + NO_ACTION);
        label.getElement().getStyle().setProperty("fontSize", "12px");
        return label;
    }

    /// Tests whether an event's target is, or is inside, an element with a class name, as Stroom's
    /// cells test it (`ElementUtil.hasClassName(element, className, 5)`).
    ///
    /// @param event     The event.
    /// @param className The class name.
    /// @return True if the target or one of its five nearest ancestors has the class.
    static boolean targetHasClassName(final NativeEvent event, final String className) {
        final EventTarget target = event.getEventTarget();
        if (!Element.is(target)) {
            return false;
        }
        Element element = Element.as(target);
        for (int depth = 0; depth <= 5 && element != null; depth++) {
            if (element.hasClassName(className)) {
                return true;
            }
            element = element.getParentElement();
        }
        return false;
    }
}
