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

package stroom.widget.contextmenu.client;

import stroom.svg.shared.SvgImage;
import stroom.widget.contextmenu.client.TextEditMenu.PasteSupport;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.Separator;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.PopupPosition.PopupLocation;
import stroom.widget.util.client.Clipboard;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.Window;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/// Decides what a right-click (or Shift+F10, or the context menu key) shows, in place of the
/// `oncontextmenu="return false;"` the page's body once had: Stroom's edit menu ([TextEditMenu]) on a
/// text field or code editor, unless a widget shows its own menu first; elsewhere, a link's items
/// (Open Link in New Tab, Copy Link Address) and Copy for selected text, or no menu at all.
///
/// It listens on the document, so widgets that show their own menu on a right-click (the data
/// grid, for one) and stop the event going further keep doing so. A Shift+right-click is left to the
/// browser, which shows its own menu (Firefox does so without telling the page).
///
/// Installing it more than once (e.g. once for each workbench story) keeps one listener, which
/// shows menus through the event bus it was last given.
@Singleton
public class ContextMenuManager {

    // The mouse button a right-click reports; one from the keyboard reports another (0 or -1)
    private static final int SECONDARY_BUTTON = 2;
    private static final String STATUS_CLASS = "sr-only";

    private static HasHandlers eventBus;
    private static boolean installed;
    private static boolean pasteRefused;
    private static boolean browserMenuForText;
    private static Element status;

    /// Installs the manager.
    ///
    /// @param eventBus The application event bus, which shows the menus.
    @Inject
    public ContextMenuManager(final EventBus eventBus) {
        install(eventBus::fireEvent);
    }

    /// Installs the manager's listener on the document, once, showing menus through an event bus.
    ///
    /// @param eventBus The event bus that shows menus (see [ShowMenuEvent]), or null to show none,
    ///                 only stopping the browser's own.
    /// @return Stops showing menus through that event bus (e.g. when a workbench story that gave it
    /// ends), unless another has been given since. The browser's menu stays stopped.
    public static Runnable install(final HasHandlers eventBus) {
        ContextMenuManager.eventBus = eventBus;
        if (!installed) {
            installed = true;
            addListener();
        }
        return () -> {
            if (ContextMenuManager.eventBus == eventBus) {
                ContextMenuManager.eventBus = null;
            }
        };
    }

    /// Sets what a right-click in a text field or code editor shows (the user's Text Context Menu
    /// preference).
    ///
    /// @param browserMenuForText True for the browser's own menu, false for Stroom's edit menu.
    public static void setBrowserMenuForText(final boolean browserMenuForText) {
        ContextMenuManager.browserMenuForText = browserMenuForText;
    }

    /// @param event A `contextmenu` event.
    /// @return True if the browser should show its own menu for the event, so no widget should show
    /// one or stop the browser's: a Shift+right-click, or a right-click in a text field or code
    /// editor when the user prefers the browser's menu there.
    public static boolean isLeftToBrowser(final NativeEvent event) {
        if (event.getShiftKey()) {
            return true;
        }
        return browserMenuForText && findAnyEditTarget(getTarget(event)) != null;
    }

    // Called by the document's contextmenu listener
    private static void onContextMenu(final NativeEvent event) {
        if (isLeftToBrowser(event) || isDefaultPrevented(event)) {
            // The browser's own menu, or a widget has dealt with it
            return;
        }
        event.preventDefault();

        final Element target = getTarget(event);
        final TextEditTarget field = findAnyEditTarget(target);
        if (eventBus == null) {
            return;
        }
        if (field != null) {
            showEditMenu(field, getPosition(event, field));
        } else {
            showFallbackMenu(event, target);
        }
    }

    // Not in a text field or code editor: a link's items, and Copy for selected text under the
    // pointer (or any, from the keyboard); no menu if there are none
    private static void showFallbackMenu(final NativeEvent event, final Element target) {
        final List<Item> items = new ArrayList<>();
        final String href = findLinkHref(target);
        if (href != null) {
            items.add(new IconMenuItem.Builder()
                    .priority(items.size())
                    .icon(SvgImage.OPEN)
                    .text("Open Link in New Tab")
                    .opensNewTab()
                    .command(() -> Window.open(href, "_blank", "noopener"))
                    .build());
            items.add(new IconMenuItem.Builder()
                    .priority(items.size())
                    .icon(SvgImage.COPY)
                    .text("Copy Link Address")
                    .command(() -> Clipboard.writeText(href))
                    .build());
        }
        final String selectedText = getSelectedText(event, event.getButton() == SECONDARY_BUTTON);
        if (selectedText != null && !selectedText.isEmpty()) {
            if (!items.isEmpty()) {
                items.add(new Separator(items.size()));
            }
            items.add(new IconMenuItem.Builder()
                    .priority(items.size())
                    .icon(TextEditCommand.COPY.getIcon())
                    .text(TextEditCommand.COPY.getText())
                    .shortcut(TextEditMenu.getShortcut(TextEditCommand.COPY, isMac()))
                    .command(() -> Clipboard.writeText(selectedText))
                    .build());
        }
        if (!items.isEmpty()) {
            ShowMenuEvent.builder()
                    .name(href != null
                            ? "Link"
                            : "Selected text")
                    .items(items)
                    .popupPosition(new PopupPosition(event.getClientX(), event.getClientY()))
                    .fire(eventBus);
        }
    }

    /// Finds the text field or code editor a right-click was on, for a widget that shows its own
    /// menu on a right-click and puts the edit items at its top (see [#withEditItems]).
    ///
    /// @param event The `contextmenu` event.
    /// @return The field or editor, or null if the right-click wasn't on one the edit menu is for,
    /// or is left to the browser (see [#isLeftToBrowser]).
    public static TextEditTarget findEditTarget(final NativeEvent event) {
        if (isLeftToBrowser(event)) {
            return null;
        }
        return findAnyEditTarget(getTarget(event));
    }

    private static TextEditTarget findAnyEditTarget(final Element target) {
        if (target == null) {
            return null;
        }
        final TextField field = TextField.find(target);
        return field != null
                ? field
                : CodeEditorText.find(target);
    }

    /// Creates the edit menu's items for a field or editor, for a widget's own menu to start with.
    /// The widget should put the focus back on it when its menu closes ([TextEditTarget#restore]).
    ///
    /// @param field The field or editor, from [#findEditTarget].
    /// @return The items: Undo, Redo, Cut, Copy, Paste, Delete and Select All, with separators.
    public static List<Item> createEditItems(final TextEditTarget field) {
        return TextEditMenu.createItems(
                field.getState(getPasteSupport(), isMac()),
                command -> field.execute(command, ContextMenuManager::onPasteRefused));
    }

    /// Puts a field's edit items before a widget's own menu items, with a separator between.
    ///
    /// @param field The field or editor the menu was opened on, or null for none.
    /// @param items The widget's own items.
    /// @return The edit items then the widget's, or the widget's alone if there is no field.
    public static List<Item> withEditItems(final TextEditTarget field, final List<Item> items) {
        if (field == null) {
            return items;
        }
        final List<Item> all = new ArrayList<>(createEditItems(field));
        if (items != null && !items.isEmpty()) {
            all.add(new Separator(all.size()));
            all.addAll(items);
        }
        return all;
    }

    /// @param event  A `contextmenu` event.
    /// @param target The field or editor the menu is for.
    /// @return Where to open a menu: at the pointer for a right-click, or below the field (or a code
    /// editor's cursor) for a menu opened from the keyboard, which has no pointer to open at.
    public static PopupPosition getPosition(final NativeEvent event, final TextEditTarget target) {
        if (event.getButton() == SECONDARY_BUTTON) {
            return new PopupPosition(event.getClientX(), event.getClientY());
        }
        return new PopupPosition(target.getMenuAnchor(), PopupLocation.BELOW);
    }

    private static void showEditMenu(final TextEditTarget field, final PopupPosition position) {
        ShowMenuEvent.builder()
                .name(TextEditMenu.NAME)
                .items(createEditItems(field))
                .popupPosition(position)
                .onHide(e -> field.restore())
                .fire(eventBus);
    }

    private static PasteSupport getPasteSupport() {
        if (!Clipboard.canRead()) {
            return PasteSupport.INSECURE;
        }
        return pasteRefused
                ? PasteSupport.REFUSED
                : PasteSupport.AVAILABLE;
    }

    // Paste is greyed from now on, saying why, and screen readers are told now
    private static void onPasteRefused() {
        pasteRefused = true;
        announce(TextEditMenu.PASTE_REFUSED_TOOLTIP);
    }

    private static void announce(final String message) {
        if (status == null) {
            status = Document.get().createDivElement();
            status.setClassName(STATUS_CLASS);
            status.setAttribute("role", "status");
            status.setAttribute("aria-live", "polite");
            Document.get().getBody().appendChild(status);
        }
        status.setInnerText(message);
    }

    // The address of the link the element is in (an `a` with an `href`), or null for none or a
    // `javascript:` one
    private static native String findLinkHref(final Element element) /*-{
        var link = element && element.closest ? element.closest('a[href]') : null;
        if (!link) {
            return null;
        }
        var href = link.href;
        return href && href.toLowerCase().indexOf('javascript:') !== 0
                ? href
                : null;
    }-*/;

    // The page's selected text, or null for none; for a right-click, only if it was on the
    // selection, as the browser's own Copy is
    private static native String getSelectedText(final NativeEvent event, final boolean atPointer) /*-{
        var selection = $wnd.getSelection();
        if (!selection || selection.isCollapsed || selection.rangeCount === 0) {
            return null;
        }
        var text = selection.toString();
        if (!text || !atPointer) {
            return text || null;
        }
        var x = event.clientX;
        var y = event.clientY;
        for (var i = 0; i < selection.rangeCount; i++) {
            var rects = selection.getRangeAt(i).getClientRects();
            for (var j = 0; j < rects.length; j++) {
                var rect = rects[j];
                if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
                    return text;
                }
            }
        }
        return null;
    }-*/;

    private static native boolean isDefaultPrevented(final NativeEvent event) /*-{
        return event.defaultPrevented;
    }-*/;

    private static native Element getTarget(final NativeEvent event) /*-{
        var target = event.target;
        return target && target.nodeType === 1
                ? target
                : null;
    }-*/;

    private static native boolean isMac() /*-{
        var nav = $wnd.navigator;
        var platform = (nav.userAgentData && nav.userAgentData.platform) || nav.platform || "";
        return platform.toLowerCase().indexOf("mac") === 0;
    }-*/;

    private static native void addListener() /*-{
        $doc.addEventListener('contextmenu', $entry(function (event) {
            @stroom.widget.contextmenu.client.ContextMenuManager::onContextMenu(*)(event);
        }));
    }-*/;
}
