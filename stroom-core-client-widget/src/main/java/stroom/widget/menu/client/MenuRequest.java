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

import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.util.client.DomRequests;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.HasHandlers;

import java.util.List;

/// Shows a menu from a widget that has no event bus to fire a [ShowMenuEvent] on (e.g. a split
/// pane's splitter). The widget makes a request with [#fire], which bubbles up the page as a DOM
/// event to the one relay installed for the page ([#relay], by Stroom's core controller, in Stroom
/// and the standalone dashboard), which fires the [ShowMenuEvent] on the event bus. So no view has
/// to pass its widgets' menus on.
public final class MenuRequest {

    private static final String EVENT = "stroom-menu-request";

    private final String name;
    private final List<Item> items;
    private final PopupPosition popupPosition;
    private final Element opener;
    private final boolean allowCloseOnMoveLeft;
    private final HidePopupEvent.Handler onHide;

    private MenuRequest(final String name,
                        final List<Item> items,
                        final PopupPosition popupPosition,
                        final Element opener,
                        final boolean allowCloseOnMoveLeft,
                        final HidePopupEvent.Handler onHide) {
        this.name = name;
        this.items = items;
        this.popupPosition = popupPosition;
        this.opener = opener;
        this.allowCloseOnMoveLeft = allowCloseOnMoveLeft;
        this.onHide = onHide;
    }

    /// Asks for a menu to be shown.
    ///
    /// @param source        The element the menu is for, which the request bubbles up from.
    /// @param name          The menu's accessible name (see [ShowMenuEvent.Builder#name]).
    /// @param items         The menu's items.
    /// @param popupPosition Where the menu is shown, e.g. at the pointer.
    public static void fire(final Element source,
                            final String name,
                            final List<Item> items,
                            final PopupPosition popupPosition) {
        fire(source, name, items, popupPosition, null);
    }

    /// Asks for a menu to be shown, as [#fire(Element, String, List, PopupPosition)] does, and to be
    /// told when it closes, e.g. to put back a field's selection that opening it changed.
    ///
    /// @param source        The element the menu is for, which the request bubbles up from.
    /// @param name          The menu's accessible name, or null for none.
    /// @param items         The menu's items.
    /// @param popupPosition Where the menu is shown, e.g. at the pointer.
    /// @param onHide        What is told when the menu closes, or null for nothing.
    public static void fire(final Element source,
                            final String name,
                            final List<Item> items,
                            final PopupPosition popupPosition,
                            final HidePopupEvent.Handler onHide) {
        DomRequests.send(source, EVENT, new MenuRequest(name, items, popupPosition, null, false, onHide));
    }

    /// Asks for a menu to be shown, opened by a button (see [ShowMenuEvent.Builder#opener]), which the
    /// request bubbles up from.
    ///
    /// @param opener        The button that opens the menu, marked as doing so (see [MenuOpener#mark]).
    /// @param name          The menu's accessible name (see [ShowMenuEvent.Builder#name]).
    /// @param items         The menu's items.
    /// @param popupPosition Where the menu is shown, e.g. below the button.
    public static void fireFromOpener(final Element opener,
                                      final String name,
                                      final List<Item> items,
                                      final PopupPosition popupPosition) {
        fireFromOpener(opener, name, items, popupPosition, false);
    }

    /// Asks for a menu to be shown, opened by a button, as
    /// [#fireFromOpener(Element, String, List, PopupPosition)] does, saying whether Left on one of
    /// its top items closes it (see [ShowMenuEvent.Builder#allowCloseOnMoveLeft(boolean)]), e.g. for
    /// a row's actions.
    ///
    /// @param opener               The button that opens the menu.
    /// @param name                 The menu's accessible name.
    /// @param items                The menu's items.
    /// @param popupPosition        Where the menu is shown.
    /// @param allowCloseOnMoveLeft Whether Left on one of the menu's top items closes it.
    public static void fireFromOpener(final Element opener,
                                      final String name,
                                      final List<Item> items,
                                      final PopupPosition popupPosition,
                                      final boolean allowCloseOnMoveLeft) {
        DomRequests.send(opener,
                EVENT,
                new MenuRequest(name, items, popupPosition, opener, allowCloseOnMoveLeft, null));
    }

    /// Shows the menus that widgets on the page ask for, by firing a [ShowMenuEvent] for each.
    ///
    /// @param handlers What the [ShowMenuEvent]s are fired on, e.g. a controller, or an event bus's
    ///                 `fireEvent`.
    /// @return The registration that stops the relay.
    public static HandlerRegistration relay(final HasHandlers handlers) {
        return DomRequests.<MenuRequest>receive(EVENT, request -> show(request, handlers));
    }

    private static void show(final MenuRequest request, final HasHandlers handlers) {
        final ShowMenuEvent.Builder builder = ShowMenuEvent.builder()
                .name(request.name)
                .items(request.items)
                .popupPosition(request.popupPosition)
                .allowCloseOnMoveLeft(request.allowCloseOnMoveLeft);
        if (request.opener != null) {
            builder.opener(request.opener);
        }
        if (request.onHide != null) {
            builder.onHide(request.onHide);
        }
        builder.fire(handlers);
    }
}
