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

import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.Element;

/// The tab stops the browser would move through with Tab, for code that moves the focus as Tab
/// would (e.g. a popup that closes on Tab, or a modal dialog that keeps the focus in it).
///
/// A tab stop is a button, link or form field, or an element with a `tabindex` of 0 or more, that is
/// shown (has a box and isn't `visibility: hidden`) and not disabled, in document order.
public final class TabOrder {

    private TabOrder() {
        // Static utility
    }

    /// @param scope The element to look in, e.g. a dialog, or the document's body.
    /// @return The tab stops in it, in document order.
    public static native JsArray<Element> tabStops(Element scope) /*-{
        var selector = 'a[href], button, input, select, textarea, [tabindex]';
        return Array.prototype.filter.call(scope.querySelectorAll(selector), function (element) {
            if (element.tabIndex < 0 || element.disabled) {
                return false;
            }
            if (element.getClientRects().length === 0) {
                // Not shown (e.g. display: none, or in a hidden tab)
                return false;
            }
            var style = element.ownerDocument.defaultView.getComputedStyle(element);
            return style.visibility !== 'hidden';
        });
    }-*/;

    /// Moves the focus to the tab stop after (or before) an element, as Tab (or Shift+Tab) would:
    /// within the modal dialog it is in, going round from the last to the first, or else within
    /// the page.
    ///
    /// @param from      The element to move on from, e.g. the one with the focus.
    /// @param backwards Whether to move back, as Shift+Tab does.
    public static native void focusNext(Element from, boolean backwards) /*-{
        var doc = from.ownerDocument;
        var dialog = from.closest('[aria-modal="true"]');
        var scope = dialog || doc.body;
        var stops = @stroom.widget.util.client.TabOrder::tabStops(*)(scope);
        if (stops.length === 0) {
            return;
        }
        var index = stops.indexOf(from);
        var next;
        if (index < 0) {
            // Not a tab stop itself: the first one after it (or before it) in the document
            next = null;
            for (var i = 0; i < stops.length; i++) {
                var after = from.compareDocumentPosition(stops[i]) & Node.DOCUMENT_POSITION_FOLLOWING;
                if (backwards ? !after : after) {
                    next = stops[i];
                    if (!backwards) {
                        break;
                    }
                }
            }
        } else {
            next = stops[index + (backwards ? -1 : 1)];
        }
        if (!next && dialog) {
            next = backwards ? stops[stops.length - 1] : stops[0];
        }
        if (next) {
            next.focus();
        }
    }-*/;
}
