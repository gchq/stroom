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

package stroom.widget.popup.client;

import com.google.gwt.dom.client.Element;

/// Keeps the focus in a modal dialog: Tab from its last tab stop goes to its first, and Shift+Tab
/// from its first to its last, rather than to the page behind it, which can't be used while the
/// dialog is open (WAI-ARIA's modal dialog pattern).
///
/// Its tab stops are [stroom.widget.util.client.TabOrder]'s. A key something inside the dialog has
/// already handled (e.g. Tab in a code editor, which indents) is left alone.
public final class DialogFocusTrap {

    private static final String INSTALLED_ATTRIBUTE = "data-focus-trap";

    private DialogFocusTrap() {
        // Static utility
    }

    /// Keeps the focus in a dialog. Installing it again on the same dialog does nothing.
    ///
    /// @param dialog The dialog's element.
    public static void install(final Element dialog) {
        if (!dialog.hasAttribute(INSTALLED_ATTRIBUTE)) {
            dialog.setAttribute(INSTALLED_ATTRIBUTE, "");
            listen(dialog);
        }
    }

    private static native void listen(Element dialog) /*-{
        var tabStops = function () {
            return @stroom.widget.util.client.TabOrder::tabStops(*)(dialog);
        };
        dialog.addEventListener('keydown', function (event) {
            if (event.key !== 'Tab' || event.defaultPrevented
                || event.altKey || event.ctrlKey || event.metaKey
                || dialog.getAttribute('aria-modal') !== 'true') {
                return;
            }
            var stops = tabStops();
            if (stops.length === 0) {
                return;
            }
            var first = stops[0];
            var last = stops[stops.length - 1];
            var active = dialog.ownerDocument.activeElement;
            if (event.shiftKey && (active === first || !dialog.contains(active))) {
                event.preventDefault();
                last.focus();
            } else if (!event.shiftKey && active === last) {
                event.preventDefault();
                first.focus();
            }
        });
    }-*/;
}
