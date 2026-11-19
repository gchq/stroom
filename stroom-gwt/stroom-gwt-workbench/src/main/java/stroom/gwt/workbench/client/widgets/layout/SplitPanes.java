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

import stroom.widget.text.client.Html;

import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// The panes and frame shared by the `Widgets/Layout/SplitPane` stories.
final class SplitPanes {

    private SplitPanes() {
        // Static utility
    }

    /// The left (first) pane.
    ///
    /// @param hint The second line of text.
    /// @return The pane.
    static Widget leftPane(final String hint) {
        final Widget pane = pane("Left panel", hint);
        pane.getElement().getStyle().setProperty("background", "var(--row__background-color--even)");
        return pane;
    }

    /// The right (second) pane.
    ///
    /// @return The pane.
    static Widget rightPane() {
        return pane("Right panel", "Content goes here");
    }

    /// The top (first) pane of stacked panes.
    ///
    /// @param hint The second line of text.
    /// @return The pane.
    static Widget topPane(final String hint) {
        final Widget pane = pane("Top panel", hint);
        pane.getElement().getStyle().setProperty("background", "var(--row__background-color--even)");
        return pane;
    }

    /// The bottom (second) pane of stacked panes.
    ///
    /// @return The pane.
    static Widget bottomPane() {
        return pane("Bottom panel", "Content goes here");
    }

    /// The stories' frame: 300px high with a rounded border.
    ///
    /// @param split The split pane, which fills the frame.
    /// @return The frame.
    static FlowPanel frame(final Widget split) {
        final FlowPanel frame = new FlowPanel();
        final Style style = frame.getElement().getStyle();
        style.setProperty("height", "300px");
        style.setProperty("border", "1px solid var(--border-color,#444)");
        style.setProperty("borderRadius", "4px");
        style.setProperty("overflow", "hidden");
        // A split pane needs a size to lay its panes out in
        style.setProperty("position", "relative");
        split.setSize("100%", "100%");
        frame.add(split);
        return frame;
    }

    private static Widget pane(final String title, final String hint) {
        final Html pane = new Html("<p style=\"margin: 0 0 8px; font-weight: 600; font-size: 0.85rem\">"
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
