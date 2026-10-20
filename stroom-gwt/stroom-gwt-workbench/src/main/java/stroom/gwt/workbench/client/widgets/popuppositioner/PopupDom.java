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


package stroom.gwt.workbench.client.widgets.popuppositioner;

import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.Element;

import java.util.ArrayList;
import java.util.List;

/// Reads the page for the popup contract plays' value suppliers (whether a popup is open, how many
/// are, and their rects and styles). The popups are on the page's body, so every selector is
/// resolved against the whole document.
final class PopupDom {

    private PopupDom() {
        // Static utility
    }

    /// Is there an element matching the selector that isn't `display: none` or
    /// `visibility: hidden`?
    ///
    /// @param selector A CSS selector.
    /// @return True if a matching element is shown.
    static native boolean isOpen(String selector) /*-{
        var el = $doc.querySelector(selector);
        if (!el) {
            return false;
        }
        var s = $wnd.getComputedStyle(el);
        return s.display !== 'none' && s.visibility !== 'hidden';
    }-*/;

    /// The number of shown elements matching a selector.
    ///
    /// @param selector A CSS selector, e.g. a list of the popups' selectors.
    /// @return The number shown.
    static native int countOpen(String selector) /*-{
        var count = 0;
        var all = $doc.querySelectorAll(selector);
        for (var i = 0; i < all.length; i++) {
            var s = $wnd.getComputedStyle(all[i]);
            if (s.display !== 'none' && s.visibility !== 'hidden') {
                count++;
            }
        }
        return count;
    }-*/;

    /// The bounding client rect of the first element matching a selector.
    ///
    /// @param selector A CSS selector.
    /// @return `{left, top, right, bottom, width, height}`, or null if nothing matches.
    static double[] rect(final String selector) {
        final String values = rectString(selector);
        if (values == null) {
            return null;
        }
        final String[] parts = values.split(",");
        final double[] rect = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            rect[i] = Double.parseDouble(parts[i]);
        }
        return rect;
    }

    private static native String rectString(String selector) /*-{
        var el = $doc.querySelector(selector);
        if (!el) {
            return null;
        }
        var r = el.getBoundingClientRect();
        return [r.left, r.top, r.right, r.bottom, r.width, r.height].join(',');
    }-*/;

    /// The bounding client rect of the `n`th shown element matching a selector, or of the `row`th
    /// element matching a second selector inside it.
    ///
    /// @param selector A CSS selector, e.g. the menu popups'.
    /// @param n        The index of the shown element.
    /// @param inner    A selector within that element, or null for the element itself.
    /// @param row      The index of the match of `inner`.
    /// @return `{left, top, right, bottom, width, height}`, or null if there is no such element.
    static double[] rectOfShown(final String selector, final int n, final String inner, final int row) {
        final String values = rectOfShownString(selector, n, inner, row);
        if (values == null) {
            return null;
        }
        final String[] parts = values.split(",");
        final double[] rect = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            rect[i] = Double.parseDouble(parts[i]);
        }
        return rect;
    }

    private static native String rectOfShownString(String selector, int n, String inner, int row) /*-{
        var shown = [];
        var all = $doc.querySelectorAll(selector);
        for (var i = 0; i < all.length; i++) {
            var s = $wnd.getComputedStyle(all[i]);
            if (s.display !== 'none' && s.visibility !== 'hidden') {
                shown.push(all[i]);
            }
        }
        var el = shown[n];
        if (el && inner) {
            el = el.querySelectorAll(inner)[row];
        }
        if (!el) {
            return null;
        }
        var r = el.getBoundingClientRect();
        return [r.left, r.top, r.right, r.bottom, r.width, r.height].join(',');
    }-*/;

    /// @param selector A CSS selector, e.g. the menu popups'.
    /// @param n        The index of the shown element.
    /// @return True if the `n`th shown element matching the selector has the keyboard focus.
    static native boolean shownHasFocus(String selector, int n) /*-{
        var shown = [];
        var all = $doc.querySelectorAll(selector);
        for (var i = 0; i < all.length; i++) {
            var s = $wnd.getComputedStyle(all[i]);
            if (s.display !== 'none' && s.visibility !== 'hidden') {
                shown.push(all[i]);
            }
        }
        var el = shown[n];
        return !!el && el.contains($doc.activeElement);
    }-*/;

    /// The first element matching a selector.
    ///
    /// @param selector A CSS selector.
    /// @return The element, or null.
    static native Element element(String selector) /*-{
        return $doc.querySelector(selector);
    }-*/;

    /// Do two rects overlap by more than a rounding error?
    ///
    /// @param a A rect from [#rect(String)].
    /// @param b Another.
    /// @return True if they overlap by more than 1px.
    static boolean overlaps(final double[] a, final double[] b) {
        final double eps = 1;
        return a[0] < b[2] - eps && a[2] > b[0] + eps && a[1] < b[3] - eps && a[3] > b[1] + eps;
    }

    /// The `NoShieldBehindAnyPopup` search: the shown, hittable, fixed or absolute elements
    /// covering 95% of the viewport that are neither the panel, in it, nor around it.
    ///
    /// @param panelSelector The open panel's selector.
    /// @return The offenders' classes (or tag names).
    static List<String> shields(final String panelSelector) {
        return toList(shieldsArray(panelSelector));
    }

    private static native JsArrayString shieldsArray(String panelSelector) /*-{
        var panel = $doc.querySelector(panelSelector);
        var vw = $wnd.innerWidth;
        var vh = $wnd.innerHeight;
        var result = [];
        var all = $doc.querySelectorAll('body *');
        for (var i = 0; i < all.length; i++) {
            var el = all[i];
            if (panel && (el === panel || panel.contains(el) || el.contains(panel))) {
                continue;
            }
            var s = $wnd.getComputedStyle(el);
            if (s.display === 'none' || s.visibility === 'hidden' || s.pointerEvents === 'none') {
                continue;
            }
            if (s.position !== 'fixed' && s.position !== 'absolute') {
                continue;
            }
            var r = el.getBoundingClientRect();
            if (r.width >= vw * 0.95 && r.height >= vh * 0.95) {
                result.push(el.className || el.tagName);
            }
        }
        return result;
    }-*/;

    /// The `MenuPaintsExactlyOneBorder` walk: the menu's chrome wrappers that paint a top border,
    /// starting at the element matching the selector and descending only into children whose class
    /// names look like chrome (`simplePopup`, `popupContent`, `background`, `content`).
    ///
    /// @param selector The menu popup's selector.
    /// @return `className (width colour)` for each element painting a border.
    static List<String> paintedBorders(final String selector) {
        return toList(paintedBordersArray(selector));
    }

    private static native JsArrayString paintedBordersArray(String selector) /*-{
        var painted = [];
        var walk = function (el) {
            var s = $wnd.getComputedStyle(el);
            var w = parseFloat(s.borderTopWidth) || 0;
            if (w > 0 && s.borderTopStyle !== 'none' && s.borderTopColor !== 'rgba(0, 0, 0, 0)') {
                painted.push((el.className || el.tagName) + ' (' + s.borderTopWidth + ' ' + s.borderTopColor + ')');
            }
            for (var i = 0; i < el.children.length; i++) {
                var c = el.children[i];
                if (/simplePopup|popupContent|background|content/i.test(c.className)) {
                    walk(c);
                }
            }
        };
        var root = $doc.querySelector(selector);
        if (root) {
            walk(root);
        }
        return painted;
    }-*/;

    private static List<String> toList(final JsArrayString array) {
        final List<String> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            list.add(array.get(i));
        }
        return list;
    }
}
