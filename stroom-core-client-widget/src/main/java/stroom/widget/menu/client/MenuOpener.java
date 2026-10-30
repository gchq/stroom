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

package stroom.widget.menu.client;

import stroom.widget.menu.client.component.Menu;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.DOM;

/// Tells screen readers that a button opens a menu (`aria-haspopup="menu"`) and whether that menu
/// is open (`aria-expanded`), as the ARIA menu button pattern asks.
///
/// A button is marked once with [#mark]. [Menu] then keeps its `aria-expanded` up to date for any
/// menu opened with it as the [ShowMenuEvent.Builder#opener].
public final class MenuOpener {

    private static final String ARIA_HAS_POPUP = "aria-haspopup";
    private static final String ARIA_EXPANDED = "aria-expanded";

    private MenuOpener() {
        // Utility class
    }

    /// Marks an element as a button that opens a menu, with its menu closed.
    ///
    /// @param element The button's element.
    public static void mark(final Element element) {
        element.setAttribute(ARIA_HAS_POPUP, "menu");
        element.setAttribute(ARIA_EXPANDED, "false");
    }

    /// Removes the marks [#mark] made, for a button that no longer opens a menu.
    ///
    /// @param element The button's element.
    public static void unmark(final Element element) {
        element.removeAttribute(ARIA_HAS_POPUP);
        element.removeAttribute(ARIA_EXPANDED);
    }

    /// Whether an element was marked with [#mark] as a button that opens a menu.
    ///
    /// @param element The element, or null.
    /// @return True if the element is marked.
    public static boolean isMarked(final Element element) {
        return element != null && element.hasAttribute(ARIA_HAS_POPUP);
    }

    /// Gives an element a unique id if it has none, so that a menu can refer to it (e.g. with
    /// `aria-labelledby`). An existing id is kept.
    ///
    /// @param element The element.
    /// @return The element's id.
    public static String ensureId(final Element element) {
        final String id = element.getId();
        if (id == null || id.isEmpty()) {
            element.setId(DOM.createUniqueId());
        }
        return element.getId();
    }

    /// Says whether a marked button's menu is open. Elements that weren't marked are left alone, so
    /// that an element which merely positions a menu (e.g. a logo or a table cell) isn't announced as
    /// a collapsed menu button.
    ///
    /// @param element  The element the menu was opened from, or null.
    /// @param expanded Whether its menu is open.
    public static void setExpanded(final Element element, final boolean expanded) {
        if (isMarked(element)) {
            element.setAttribute(ARIA_EXPANDED, String.valueOf(expanded));
        }
    }
}
