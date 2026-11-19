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

/// Measures the tab stops in part of a story, i.e. the elements the Tab key stops at, in order, so
/// a play can check how many presses it takes a keyboard user to cross it, e.g.
/// `play.expect(TabStops.names(play.getByTestId("toolbar"))).toEqual(List.of("Add", "Edit"))`.
///
/// The rules are a browser's, not the play's simulated Tab (user-event's), which doesn't know about
/// `inert`: an element is a tab stop if it can take focus (a control, a link or an element with a
/// `tabindex`), its tab index (as the browser reads it) isn't negative, it isn't disabled and it
/// isn't hidden (with `display: none` or `visibility: hidden`) or inside an `inert` element.
/// Positive `tabindex` values come first, as a browser orders them.
public final class TabStops {

    private TabStops() {
        // Static utility
    }

    /// The names of the tab stops in a region, in the order Tab visits them.
    ///
    /// @param region The element whose tab stops are measured.
    /// @return For each tab stop, its name: its `aria-label`, else its `title`, else its text, else
    /// its tag, e.g. `button`.
    public static Value<List<String>> names(final Query region) {
        final Value<Element> element = region.element();
        return Value.of(region.describe() + " tab stops", () -> namesOf(element.get()));
    }

    /// @param region The element whose tab stops are measured.
    /// @return The number of tab stops in the region.
    public static Value<Integer> count(final Query region) {
        final Value<Element> element = region.element();
        return Value.of(region.describe() + " tab stop count", () -> inOrder(element.get()).length());
    }

    /// @param element An element.
    /// @return Whether the element is a tab stop.
    public static native boolean isTabStop(Element element) /*-{
        var selector = 'input:not([type=hidden]), button, select, textarea, a[href], '
            + '[contenteditable=""], [contenteditable="true"], [tabindex]';
        if (!element.matches(selector) || element.disabled || element.closest('[inert]')) {
            return false;
        }
        // The browser's own tab index, e.g. -1 for an element whose tabindex attribute is empty
        // or invalid, which can't take the focus
        if (element.tabIndex < 0) {
            return false;
        }
        var win = element.ownerDocument.defaultView;
        for (var node = element; node && node.nodeType === 1; node = node.parentElement) {
            var style = win.getComputedStyle(node);
            if (style.display === 'none' || style.visibility === 'hidden') {
                return false;
            }
        }
        return true;
    }-*/;

    /// @param element An element.
    /// @return The element's name: its `aria-label`, else its `title`, else its text, else its tag.
    public static native String nameOf(Element element) /*-{
        var label = element.getAttribute('aria-label') || element.getAttribute('title')
            || (element.textContent || '').replace(/\s+/g, ' ').trim();
        return label || element.localName;
    }-*/;

    static List<String> namesOf(final Element region) {
        final JsArray<Element> stops = inOrder(region);
        final List<String> names = new ArrayList<>();
        for (int i = 0; i < stops.length(); i++) {
            names.add(nameOf(stops.get(i)));
        }
        return names;
    }

    private static native JsArray<Element> inOrder(Element region) /*-{
        var all = Array.prototype.slice.call(region.querySelectorAll('*'));
        var stops = all.filter(function (el) {
            return @stroom.gwt.workbench.client.a11y.TabStops::isTabStop(*)(el);
        });
        var order = function (el) {
            var tabIndex = Number(el.getAttribute('tabindex') || 0);
            return tabIndex > 0 ? tabIndex : Number.MAX_VALUE;
        };
        // A stable sort, so stops with the same tabindex stay in document order
        return stops
            .map(function (el, i) { return {el: el, i: i}; })
            .sort(function (a, b) { return (order(a.el) - order(b.el)) || (a.i - b.i); })
            .map(function (entry) { return entry.el; });
    }-*/;
}
