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

package stroom.gwt.workbench.client.a11y;

import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Value;

import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.Element;

import java.util.ArrayList;
import java.util.List;

/// Measures the runs of image buttons in part of a story: buttons that sit next to each other on a
/// row, which a keyboard user should cross with one Tab (as a toolbar, moving between its buttons
/// with the arrow keys), e.g.
/// `play.expect(ButtonRuns.of(toolbar)).toEqual(List.of("Add, Edit, Delete: 3 tab stops"))`.
///
/// An image button is an icon button (`.toolbar-button`, e.g. a `ToolbarButton`) or a floating
/// action button (`FabButton`). Two visible image buttons are in the same run if they share a row,
/// no more than [#MAX_GAP_PX] separates them and they share a container no more than
/// [#MAX_CONTAINER_DEPTH] levels above either (e.g. the row of a tab's toolbars), so buttons at
/// either end of a row, or either side of a split between panes, are separate runs.
public final class ButtonRuns {

    /// The most space between two buttons of a run, in pixels.
    public static final int MAX_GAP_PX = 8;

    /// The most levels above each of two buttons of a run that their shared container may be.
    public static final int MAX_CONTAINER_DEPTH = 4;

    private static final String IMAGE_BUTTON_SELECTOR = ".toolbar-button, .fab-button";

    private ButtonRuns() {
        // Static utility
    }

    /// The runs of image buttons in a region, top to bottom and left to right.
    ///
    /// @param region The element whose image buttons are measured.
    /// @return For each run, its buttons' names and the number of tab stops in it (a button's own,
    /// and any inside it), e.g. `Add, Edit, Delete: 3 tab stops`.
    public static Value<List<String>> of(final Query region) {
        final Value<Element> element = region.element();
        return Value.of(region.describe() + " button runs", () -> runsOf(element.get()));
    }

    private static List<String> runsOf(final Element region) {
        final JsArray<Element> buttons = visibleButtons(region, IMAGE_BUTTON_SELECTOR);
        final List<String> runs = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        int tabStops = 0;
        for (int i = 0; i < buttons.length(); i++) {
            final Element button = buttons.get(i);
            if (i > 0 && !adjacent(buttons.get(i - 1), button, MAX_GAP_PX, MAX_CONTAINER_DEPTH)) {
                runs.add(describe(names, tabStops));
                names.clear();
                tabStops = 0;
            }
            names.add(TabStops.nameOf(nameElement(button)));
            tabStops += tabStopsIn(button);
        }
        if (!names.isEmpty()) {
            runs.add(describe(names, tabStops));
        }
        return runs;
    }

    private static String describe(final List<String> names, final int tabStops) {
        return String.join(", ", names) + ": " + tabStops + (tabStops == 1
                ? " tab stop"
                : " tab stops");
    }

    // A FabButton's name is on the button inside it
    private static native Element nameElement(Element button) /*-{
        return button.querySelector('button') || button;
    }-*/;

    private static native int tabStopsIn(Element button) /*-{
        var isTabStop = @stroom.gwt.workbench.client.a11y.TabStops::isTabStop(*);
        var count = isTabStop(button) ? 1 : 0;
        Array.prototype.forEach.call(button.querySelectorAll('*'), function (el) {
            if (isTabStop(el)) {
                count++;
            }
        });
        return count;
    }-*/;

    // The visible buttons (with a size, and not visibility: hidden), in reading order
    private static native JsArray<Element> visibleButtons(Element region, String selector) /*-{
        var win = region.ownerDocument.defaultView;
        var buttons = Array.prototype.slice.call(region.querySelectorAll(selector)).filter(function (el) {
            var rect = el.getBoundingClientRect();
            return rect.width > 0 && rect.height > 0 && win.getComputedStyle(el).visibility !== 'hidden';
        });
        var sameRow = @stroom.gwt.workbench.client.a11y.ButtonRuns::sameRow(*);
        return buttons.sort(function (a, b) {
            if (sameRow(a, b)) {
                return a.getBoundingClientRect().left - b.getBoundingClientRect().left;
            }
            return a.getBoundingClientRect().top - b.getBoundingClientRect().top;
        });
    }-*/;

    // On the same row if their vertical centres are within half the smaller height
    private static native boolean sameRow(Element a, Element b) /*-{
        var ra = a.getBoundingClientRect();
        var rb = b.getBoundingClientRect();
        var centreA = ra.top + ra.height / 2;
        var centreB = rb.top + rb.height / 2;
        return Math.abs(centreA - centreB) <= Math.min(ra.height, rb.height) / 2;
    }-*/;

    private static native boolean adjacent(Element previous, Element next, int maxGap, int maxDepth) /*-{
        if (!@stroom.gwt.workbench.client.a11y.ButtonRuns::sameRow(*)(previous, next)
            || next.getBoundingClientRect().left - previous.getBoundingClientRect().right > maxGap) {
            return false;
        }
        // The shared container must be close to both, not a layout far above them
        var ancestors = [];
        var node = previous.parentElement;
        for (var depth = 1; node && depth <= maxDepth; depth++) {
            ancestors.push(node);
            node = node.parentElement;
        }
        var other = next.parentElement;
        for (var level = 1; other && level <= maxDepth; level++) {
            if (ancestors.indexOf(other) !== -1) {
                return true;
            }
            other = other.parentElement;
        }
        return false;
    }-*/;
}
