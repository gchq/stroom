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

package stroom.dashboard.client.flexlayout;

import stroom.widget.util.client.CssProperties;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// A [Splitter]'s line and what can be grabbed of it, again, on the page's root above the frames of
/// visualisations and other pages (`VisFrame`, `IFrameContent`). Those frames are on the root, over
/// the dashboard, so that they keep their pages as the dashboard changes, and would otherwise hide
/// the splitters beside them: a splitter's stacking is within its pane's (`split-pane__pane` isolates
/// it), so no z-index of its own puts it above them.
///
/// It is for the mouse and the eye only (`aria-hidden`, never focused): the splitter itself keeps the
/// focus, the keyboard and its name, and the overlay shows its focus ring when it has the focus.
final class SplitterOverlay extends Widget {

    // Room round the line for its focus ring (--focus-shadow's 4px), which is clipped to the overlay
    private static final double RING = 4;

    private final Element grab;
    private final Element line;
    private Consumer<Event> onMouseDown = event -> {
    };

    /// @param orientation The splitter's `aria-orientation`: `vertical` for a line between panels
    ///                    side by side.
    SplitterOverlay(final String orientation) {
        final Element element = DOM.createDiv();
        element.setClassName("flex-layout__overlay");
        element.setAttribute("aria-hidden", "true");
        element.setAttribute("aria-orientation", orientation);

        // The grab area first, as hovering it lights the line after it (flex-layout.css)
        grab = DOM.createDiv();
        grab.setClassName("flex-layout__overlay-grab");
        line = DOM.createDiv();
        line.setClassName("flex-layout__overlay-line");
        element.appendChild(grab);
        element.appendChild(line);

        setElement(element);
        sinkEvents(Event.ONMOUSEDOWN);
    }

    @Override
    public void onBrowserEvent(final Event event) {
        if (Event.ONMOUSEDOWN == event.getTypeInt()) {
            onMouseDown.accept(event);
        }
        super.onBrowserEvent(event);
    }

    /// @param onMouseDown Given a mouse press on the grab area, to start dragging the splitter.
    void setOnMouseDown(final Consumer<Event> onMouseDown) {
        this.onMouseDown = onMouseDown;
    }

    /// Lays the overlay over the splitter's line and grab area, clipped to the space the dashboard
    /// shows (as the splitter is, by the dashboard's scrolled area).
    ///
    /// @param splitterLine The splitter's element, its line.
    /// @param splitterGrab The splitter's grab area (`flex-layout__dragger`).
    /// @param clip         The dashboard's scrolled area.
    void layOver(final Element splitterLine, final Element splitterGrab, final Element clip) {
        final Rect lineRect = ElementUtil.getBoundingClientRectPlusWindowScroll(splitterLine);
        final Rect grabRect = ElementUtil.getBoundingClientRectPlusWindowScroll(splitterGrab);
        final Rect area = Rect.min(ElementUtil.getClientRect(clip), Rect.max(lineRect.grow(RING), grabRect));

        // The overlay on the page, its parts within it
        place(getElement(), "--flex-layout__overlay-", area, 0, 0);
        place(grab, "--flex-layout__overlay-grab-", grabRect, area.getLeft(), area.getTop());
        place(line, "--flex-layout__overlay-line-", lineRect, area.getLeft(), area.getTop());
    }

    /// @param focused Whether the splitter has the keyboard focus (`:focus-visible`), so the overlay
    ///                draws its line as the splitter's focused line.
    void setFocused(final boolean focused) {
        if (focused) {
            getElement().addClassName("flex-layout__overlay--focused");
        } else {
            getElement().removeClassName("flex-layout__overlay--focused");
        }
    }

    // Places an element by its custom properties, from a rectangle on the page, relative to a point
    private static void place(final Element element,
                              final String prefix,
                              final Rect rect,
                              final double originLeft,
                              final double originTop) {
        CssProperties.set(element, prefix + "left", (rect.getLeft() - originLeft) + "px");
        CssProperties.set(element, prefix + "top", (rect.getTop() - originTop) + "px");
        CssProperties.set(element, prefix + "width", rect.getWidth() + "px");
        CssProperties.set(element, prefix + "height", rect.getHeight() + "px");
    }
}
