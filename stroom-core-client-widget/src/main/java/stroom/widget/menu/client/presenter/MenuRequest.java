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

package stroom.widget.menu.client.presenter;

import stroom.widget.popup.client.presenter.PopupPosition;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.HasHandlers;

import java.util.List;

/// Shows a menu from a widget that has no event bus to fire a [ShowMenuEvent] on (e.g. a split
/// pane's splitter). The widget makes a request with [#fire], which bubbles up the page as a DOM
/// event to the one relay installed for the page ([#relay], e.g. by Stroom's main presenter),
/// which fires the [ShowMenuEvent] on the event bus. So no view has to pass its widgets' menus on.
public final class MenuRequest {

    private static final String EVENT = "stroom-menu-request";

    private final List<Item> items;
    private final PopupPosition popupPosition;

    private MenuRequest(final List<Item> items, final PopupPosition popupPosition) {
        this.items = items;
        this.popupPosition = popupPosition;
    }

    /// Asks for a menu to be shown.
    ///
    /// @param source        The element the menu is for, which the request bubbles up from.
    /// @param items         The menu's items.
    /// @param popupPosition Where the menu is shown, e.g. at the pointer.
    public static void fire(final Element source, final List<Item> items, final PopupPosition popupPosition) {
        dispatch(source, EVENT, new MenuRequest(items, popupPosition));
    }

    /// Shows the menus that widgets on the page ask for, by firing a [ShowMenuEvent] for each.
    ///
    /// @param handlers What the [ShowMenuEvent]s are fired on, e.g. a presenter, or an event bus's
    ///                 `fireEvent`.
    /// @return The registration that stops the relay.
    public static HandlerRegistration relay(final HasHandlers handlers) {
        final Object listener = listen(EVENT, handlers);
        return () -> unlisten(EVENT, listener);
    }

    private static void show(final MenuRequest request, final HasHandlers handlers) {
        ShowMenuEvent.builder()
                .items(request.items)
                .popupPosition(request.popupPosition)
                .fire(handlers);
    }

    private static native void dispatch(Element source, String type, MenuRequest request) /*-{
        source.dispatchEvent(new CustomEvent(type, {bubbles: true, detail: request}));
    }-*/;

    private static native Object listen(String type, HasHandlers handlers) /*-{
        var listener = $entry(function (event) {
            @stroom.widget.menu.client.presenter.MenuRequest::show(*)(event.detail, handlers);
        });
        $doc.addEventListener(type, listener);
        return listener;
    }-*/;

    private static native void unlisten(String type, Object listener) /*-{
        $doc.removeEventListener(type, listener);
    }-*/;
}
