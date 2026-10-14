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

package stroom.ai.client;

import com.google.gwt.dom.client.Document;

/// Lets the user know about their AI chats when they are looking at another browser tab: with a browser
/// notification, and a count in the tab's title, e.g. "(1) Stroom".
final class BrowserNotifications {

    private static final String COUNT_PREFIX_PATTERN = "^\\(\\d+\\) ";

    private BrowserNotifications() {
        // Static only.
    }

    /// Asks the user for permission to show notifications, if they have not been asked before. Browsers only
    /// ask in response to something the user did, e.g. sending a message.
    static native void requestPermission() /*-{
        if ($wnd.Notification && $wnd.Notification.permission === 'default') {
            $wnd.Notification.requestPermission();
        }
    }-*/;

    /// Shows a notification, if the user has allowed them. Clicking it brings the Stroom tab to the front.
    ///
    /// @param title The notification's title.
    /// @param body  Its text, shown as plain text.
    /// @param tag   Replaces any earlier notification with the same tag, e.g. one per chat.
    static native void show(String title, String body, String tag) /*-{
        if (!$wnd.Notification || $wnd.Notification.permission !== 'granted') {
            return;
        }
        var notification = new $wnd.Notification(title, {body: body, tag: tag});
        notification.onclick = function () {
            $wnd.focus();
            notification.close();
        };
    }-*/;

    /// Puts a count at the front of the browser tab's title, e.g. "(2) Stroom", or takes it away.
    ///
    /// @param count How many chats need the user or have answers they have not read, or 0.
    static void setTitleCount(final int count) {
        final String title = Document.get().getTitle();
        final String base = title == null
                ? ""
                : title.replaceFirst(COUNT_PREFIX_PATTERN, "");
        final String newTitle = count > 0
                ? "(" + count + ") " + base
                : base;
        if (!newTitle.equals(title)) {
            Document.get().setTitle(newTitle);
        }
    }
}
