/*
 * Copyright 2021 Crown Copyright
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

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.PopupPanel;

import java.util.ArrayList;
import java.util.List;

/// Returns the keyboard focus to where it was when a popup was shown, once the popup closes.
///
/// Each open popup keeps its own record, so popups that close out of order (e.g. a menu that
/// finishes closing after the dialog it opened has been shown) restore their own focus, and a
/// popup only restores the focus if it had it (the focus is in the popup, or was lost to the page
/// when the popup closed), so it never takes the focus from somewhere it has since moved, e.g. a
/// newer dialog's editor.
class CurrentFocus {

    // The popups that are showing, oldest first
    private static final List<Entry> OPEN = new ArrayList<>();

    private CurrentFocus() {
        // Static methods only
    }

    /// Remembers where the focus is as the popup is shown, and returns it there when it closes.
    ///
    /// @param popup The popup being shown.
    static void remember(final PopupPanel popup) {
        final Element active = getActiveElement(Document.get());
        final List<Element> targets = new ArrayList<>();
        if (active != null && active != Document.get().getBody()) {
            targets.add(active);
            // If the focus is in another popup (e.g. the menu item that opened this dialog), that
            // popup may close first, so its own target is the fallback
            for (final Entry entry : OPEN) {
                if (entry.popup.getElement().isOrHasChild(active)) {
                    targets.addAll(entry.targets);
                }
            }
        }
        final Entry entry = new Entry(popup, targets);
        OPEN.add(entry);
        entry.registration = popup.addCloseHandler(event -> restore(entry));
    }

    private static void restore(final Entry entry) {
        entry.registration.removeHandler();
        OPEN.remove(entry);
        if (hadFocus(entry.popup)) {
            for (final Element target : entry.targets) {
                // An element that has gone (e.g. the item of a menu that has closed) can't take it
                if (Document.get().getBody().isOrHasChild(target)) {
                    target.focus();
                    return;
                }
            }
        }
    }

    // Whether the popup had the focus as it closed: the focus is in it, or nowhere (it was in the
    // popup, and went to the page when the popup was removed)
    private static boolean hadFocus(final PopupPanel popup) {
        final Element active = getActiveElement(Document.get());
        return active == null
               || active == Document.get().getBody()
               || popup.getElement().isOrHasChild(active);
    }

    public static native Element getActiveElement(Document doc) /*-{
        return doc.activeElement;
    }-*/;

    // --------------------------------------------------------------------------------


    private static final class Entry {

        private final PopupPanel popup;
        private final List<Element> targets;
        private HandlerRegistration registration;

        private Entry(final PopupPanel popup, final List<Element> targets) {
            this.popup = popup;
            this.targets = targets;
        }
    }
}
