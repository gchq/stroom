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

package stroom.gwt.workbench.client;

import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;

/// Helpers for laying out the widgets in a story, e.g. in rows or columns, with notes beside them.
public final class StoryPanels {

    private StoryPanels() {
        // Static utility
    }

    /// Equivalent of `<div style={{display: 'flex', gap, alignItems: 'center', flexWrap: 'wrap'}}>`.
    ///
    /// @param gapPx   The gap between the widgets in pixels.
    /// @param widgets The widgets.
    /// @return A panel laying the widgets out in a row.
    public static FlowPanel row(final int gapPx, final Widget... widgets) {
        final FlowPanel panel = flex(gapPx, widgets);
        panel.getElement().getStyle().setProperty("alignItems", "center");
        panel.getElement().getStyle().setProperty("flexWrap", "wrap");
        return panel;
    }

    /// Equivalent of `<div style={{display: 'flex', flexDirection: 'column', gap}}>`.
    ///
    /// @param gapPx   The gap between the widgets in pixels.
    /// @param widgets The widgets.
    /// @return A panel laying the widgets out in a column.
    public static FlowPanel column(final int gapPx, final Widget... widgets) {
        final FlowPanel panel = flex(gapPx, widgets);
        panel.getElement().getStyle().setProperty("flexDirection", "column");
        return panel;
    }

    /// Equivalent of `<span style={{color: 'var(--text-color, <fallback>)', fontSize}}>`.
    ///
    /// @param text          The text.
    /// @param fallbackColor The colour to use if the theme doesn't define `--text-color`.
    /// @param fontSize      The CSS font size, e.g. `0.85rem`.
    /// @return A label.
    public static InlineLabel note(final String text, final String fallbackColor, final String fontSize) {
        final InlineLabel label = new InlineLabel(text);
        final Style style = label.getElement().getStyle();
        style.setProperty("color", "var(--text-color," + fallbackColor + ")");
        style.setProperty("fontSize", fontSize);
        return label;
    }

    private static FlowPanel flex(final int gapPx, final Widget... widgets) {
        final FlowPanel panel = new FlowPanel();
        final Style style = panel.getElement().getStyle();
        style.setProperty("display", "flex");
        style.setProperty("gap", gapPx + "px");
        for (final Widget widget : widgets) {
            panel.add(widget);
        }
        return panel;
    }
}
