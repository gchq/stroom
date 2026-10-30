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
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.Widget;

/// Gives the focus to a screen that has just been shown, e.g. a tab opened from the main menu or
/// the explorer, so that a keyboard user is taken to it rather than left where they chose it (WCAG
/// 2.4.3, Focus Order).
///
/// A screen that says where its focus should go (it is a [Focus], e.g. one that focuses its quick
/// filter) is asked to focus itself. Otherwise the focus goes to the screen's first tab stop, the
/// first thing a keyboard user would reach in it; a screen with none takes the focus itself
/// (`tabindex="-1"`), so that Tab then moves on from it.
public final class ContentFocus {

    private ContentFocus() {
        // Static utility
    }

    /// Focuses a screen that has just been shown.
    ///
    /// @param content The screen, e.g. a tab's controller, or its widget.
    public static void focus(final Object content) {
        if (content instanceof final Focus focus) {
            focus.focus();
            return;
        }
        final Widget widget = widgetOf(content);
        if (widget != null) {
            final Element element = widget.getElement();
            final Element first = firstTabStop(element);
            if (first != null) {
                focusWithoutScrolling(first);
            } else {
                element.setTabIndex(-1);
                focusWithoutScrolling(element);
            }
        }
    }

    // The screen's widget, or null if it has none
    private static Widget widgetOf(final Object content) {
        return content instanceof final IsWidget isWidget
                ? isWidget.asWidget()
                : null;
    }

    // A screen that has just opened shows its start, so focusing must not scroll it, or the
    // panes around it (focus scrolls even a container whose overflow is hidden)
    private static native void focusWithoutScrolling(Element element) /*-{
        element.focus({preventScroll: true});
    }-*/;

    /// @param container An element.
    /// @return The first element inside it that Tab stops at, or null if there is none: an element
    /// that can take the focus, without a negative `tabindex`, that isn't disabled, hidden or
    /// `inert`.
    public static native Element firstTabStop(Element container) /*-{
        var selector = 'input:not([type=hidden]), button, select, textarea, a[href], '
            + '[contenteditable=""], [contenteditable="true"], [tabindex]';
        var win = container.ownerDocument.defaultView;
        var shown = function (element) {
            for (var node = element; node && node !== container; node = node.parentElement) {
                var style = win.getComputedStyle(node);
                if (style.display === 'none' || style.visibility === 'hidden') {
                    return false;
                }
            }
            return true;
        };
        var candidates = container.querySelectorAll(selector);
        for (var i = 0; i < candidates.length; i++) {
            var element = candidates[i];
            // The browser's own tab index, e.g. -1 for an element whose tabindex attribute is
            // empty or invalid, which can't take the focus
            if (element.disabled || element.closest('[inert]') || element.tabIndex < 0 || !shown(element)) {
                continue;
            }
            return element;
        }
        return null;
    }-*/;
}
