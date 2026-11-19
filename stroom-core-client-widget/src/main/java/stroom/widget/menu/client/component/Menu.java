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

package stroom.widget.menu.client.component;

import stroom.widget.menu.client.CheckMenuItem;
import stroom.widget.menu.client.HasChildren;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.MenuItem;
import stroom.widget.menu.client.MenuItemCell;
import stroom.widget.menu.client.Separator;
import stroom.widget.util.client.CssProperties;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.InputModality;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.TypeAhead;
import stroom.widget.util.client.TypedKey;

import com.google.gwt.cell.client.Cell.Context;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// A menu: a list of [Item]s drawn by [MenuItemCell], with the roles, keys and focus of the
/// WAI-ARIA menu pattern. The element with the focus is the active item's row (a roving tab stop),
/// which the arrow keys, Page Up, Page Down, Home, End and type-ahead move, reaching every
/// [MenuItem], enabled or not, so that a disabled item is still read out.
///
/// The menu only draws and moves: what an item does (choosing it, opening and closing sub menus) is
/// up to its [Handler], given each key and mouse action. [MenuController] is the handler for
/// Stroom's menus.
///
/// Its elements (`menu.css`, the first two in `Menu.ui.xml`): `menu__viewport`, the scrolling root,
/// holds `menu` (role `menu`),
/// which holds a `menu__row` per item with the item's role (`menuitem`, `menuitemcheckbox`,
/// `separator` or `none`) and `menu__row--active` on the active one. Each row holds what
/// [MenuItemCell] draws (`menu__item` or `menu__separator`).
public class Menu extends Widget {

    private static final String ROW_CLASS = "menu__row";
    private static final String ACTIVE_ROW_CLASS = "menu__row--active";
    private static final String KEYBOARD_CLASS = "menu--keyboard";
    private static final String FIXED_WIDTH_CLASS = "menu--fixed-width";
    private static final String VIEWPORT_FIXED_WIDTH_CLASS = "menu__viewport--fixed-width";
    private static final String WIDTH_PROPERTY = "--menu__width";
    private static final String TEXT_CLASS = "menu__text";
    private static final String INDEX_ATTRIBUTE = "data-index";
    private static final String TAB_INDEX = "tabindex";
    private static final String ROLE = "role";
    private static final String ROLE_NONE = "none";
    private static final String ARIA_CHECKED = "aria-checked";
    private static final String ARIA_DISABLED = "aria-disabled";
    private static final String ARIA_EXPANDED = "aria-expanded";
    private static final String ARIA_HAS_POPUP = "aria-haspopup";
    private static final String ARIA_KEY_SHORTCUTS = "aria-keyshortcuts";
    private static final String ARIA_LABEL = "aria-label";
    private static final String ARIA_LABELLED_BY = "aria-labelledby";
    private static final int PAGE_SIZE = 10;

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DivElement menuElement;
    private final MenuItemCell cell = new MenuItemCell();
    // Typing a character goes to the next item starting with it (the WAI-ARIA menu's type-ahead)
    private final TypeAhead typeAhead = new TypeAhead();

    private Handler handler;
    private List<Item> items = Collections.emptyList();
    private final List<Element> rows = new ArrayList<>();
    private int activeIndex = -1;
    // The item whose sub menu is open, or null for none
    private Item openItem;
    private JavaScriptObject focusOutListener;

    /// Makes an empty menu.
    public Menu() {
        setElement(BINDER.createAndBindUi(this));

        // The focus ring shows only while the keyboard is in use (menu--keyboard): the menu moves the
        // focus itself, as the mouse moves too, so the browser's :focus-visible can't tell
        addAttachHandler(event -> {
            if (event.isAttached()) {
                setKeyboard(InputModality.isKeyboard());
                focusOutListener = addFocusOutListener(menuElement, this::onFocusOut);
            } else if (focusOutListener != null) {
                removeFocusOutListener(menuElement, focusOutListener);
                focusOutListener = null;
            }
        });
        addDomHandler(this::onKeyDown, KeyDownEvent.getType());
        addDomHandler(this::onMouseDown, MouseDownEvent.getType());
        addDomHandler(this::onMouseUp, MouseUpEvent.getType());
        addDomHandler(this::onMouseMove, MouseMoveEvent.getType());
    }

    /// Sets what the menu's items do when chosen, opened or closed with the keyboard or the mouse.
    ///
    /// @param handler The handler, or null for none.
    public void setHandler(final Handler handler) {
        this.handler = handler;
    }

    /// Draws the items, replacing any drawn before, with none active.
    ///
    /// @param items The items, in order.
    public void setItems(final List<Item> items) {
        this.items = items == null
                ? Collections.emptyList()
                : new ArrayList<>(items);
        activeIndex = -1;
        final SafeHtmlBuilder sb = new SafeHtmlBuilder();
        for (int i = 0; i < this.items.size(); i++) {
            final Item item = this.items.get(i);
            sb.appendHtmlConstant("<div class=\"" + ROW_CLASS + "\" " + INDEX_ATTRIBUTE + "=\"" + i + "\">");
            cell.render(new Context(i, 0, item), item, sb);
            sb.appendHtmlConstant("</div>");
        }
        menuElement.setInnerSafeHtml(sb.toSafeHtml());

        rows.clear();
        Element row = menuElement.getFirstChildElement();
        while (row != null) {
            rows.add(row);
            row = row.getNextSiblingElement();
        }
        for (int i = 0; i < rows.size(); i++) {
            setRowAria(rows.get(i), this.items.get(i));
        }
    }

    /// @return The items, in order.
    public List<Item> getItems() {
        return Collections.unmodifiableList(items);
    }

    /// @return The index of the active item (the one the keyboard or mouse is on), or -1 for none.
    public int getActiveIndex() {
        return activeIndex;
    }

    /// @return The active item (the one the keyboard or mouse is on), or null for none.
    public Item getActiveItem() {
        return activeIndex >= 0
                ? items.get(activeIndex)
                : null;
    }

    /// Makes an item the active one: it is highlighted and is the menu's tab stop.
    ///
    /// @param index The item's index; one out of range does nothing.
    /// @param focus Whether to give the item the focus too.
    public void setActiveIndex(final int index, final boolean focus) {
        if (index < 0 || index >= items.size()) {
            return;
        }
        if (activeIndex != index) {
            if (activeIndex >= 0) {
                final Element previous = rows.get(activeIndex);
                previous.removeClassName(ACTIVE_ROW_CLASS);
                setTabIndex(previous, items.get(activeIndex), false);
            }
            activeIndex = index;
            final Element row = rows.get(index);
            row.addClassName(ACTIVE_ROW_CLASS);
            setTabIndex(row, items.get(index), true);
        }
        if (focus) {
            rows.get(index).focus();
        }
    }

    /// @param item An item of the menu.
    /// @return The item's row, which has its role and holds its text, or null if the item isn't
    /// in the menu.
    public Element getRowElement(final Item item) {
        final int index = items.indexOf(item);
        return index >= 0
                ? rows.get(index)
                : null;
    }

    /// @param index An item's index.
    /// @return The text the item shows (its label, without its shortcut), or null for none.
    public String getItemText(final int index) {
        final Element text = ElementUtil.findChild(rows.get(index), TEXT_CLASS);
        return text != null
                ? text.getInnerText()
                : null;
    }

    /// Says which item's sub menu is open (`aria-expanded` on the items that have one).
    ///
    /// @param item The item whose sub menu is open, or null for none.
    public void setOpenItem(final Item item) {
        if (!Objects.equals(openItem, item)) {
            openItem = item;
            for (int i = 0; i < rows.size(); i++) {
                setRowAria(rows.get(i), items.get(i));
            }
        }
    }

    /// Gives the menu an accessible name (`aria-label`), replacing any element it was labelled by.
    ///
    /// @param name The name, as plain text, or null or empty for none.
    public void setName(final String name) {
        // The name goes on the element with the menu role, which screen readers announce as focus
        // moves into it
        menuElement.removeAttribute(ARIA_LABELLED_BY);
        if (name == null || name.isEmpty()) {
            menuElement.removeAttribute(ARIA_LABEL);
        } else {
            menuElement.setAttribute(ARIA_LABEL, name);
        }
    }

    /// Names the menu by another element (`aria-labelledby`), replacing any name it was given.
    ///
    /// @param id The id of the element that names the menu, or null or empty for none.
    public void setLabelledBy(final String id) {
        menuElement.removeAttribute(ARIA_LABEL);
        if (id == null || id.isEmpty()) {
            menuElement.removeAttribute(ARIA_LABELLED_BY);
        } else {
            menuElement.setAttribute(ARIA_LABELLED_BY, id);
        }
    }

    /// Gives the menu a fixed width, cutting items too long for it short with an ellipsis, rather
    /// than fitting it to its items.
    ///
    /// @param width The menu's width in pixels.
    public void setFixedWidth(final int width) {
        CssProperties.set(getElement(), WIDTH_PROPERTY, width + "px");
        getElement().addClassName(VIEWPORT_FIXED_WIDTH_CLASS);
        menuElement.addClassName(FIXED_WIDTH_CLASS);
    }

    /// @param item An item.
    /// @return Whether the arrow keys and type-ahead reach the item: any [MenuItem], enabled or
    /// not, so that a disabled item is still read out, as disabled.
    public static boolean isReachable(final Item item) {
        return item instanceof MenuItem;
    }

    /// @param item An item.
    /// @return Whether the item can be chosen and can open its sub menu: an enabled [MenuItem].
    public static boolean isActionable(final Item item) {
        return item instanceof MenuItem && ((MenuItem) item).isEnabled();
    }

    // Moves to a reachable item with the keyboard: from start, on in the direction of delta, page
    // reachable items at a time. Stops at the first and last items
    private void move(final int start, final int delta, final int page) {
        int index = activeIndex;
        int pos = Math.max(0, Math.min(start, items.size() - 1));
        for (int i = 0; i < page; i++) {
            for (; pos >= 0 && pos < items.size(); pos += delta) {
                if (isReachable(items.get(pos))) {
                    index = pos;
                    break;
                }
            }
        }
        if (index != activeIndex) {
            moveTo(index);
        }
    }

    // Makes the item at index active with the keyboard, focusing it
    private void moveTo(final int index) {
        setActiveIndex(index, true);
        if (handler != null) {
            handler.onMovedTo(items.get(index));
        }
    }

    private void onKeyDown(final KeyDownEvent event) {
        setKeyboard(true);
        final NativeEvent nativeEvent = event.getNativeEvent();
        if (items.isEmpty()) {
            return;
        }

        // Typed characters go to an item rather than being movement keys (KeyBinding's W, A, S, D,
        // H, J, K and L); Space still chooses the item
        final String key = TypedKey.keyOf(nativeEvent);
        if (TypedKey.isTypedCharacter(nativeEvent, key) && !" ".equals(key)) {
            handled(nativeEvent);
            typeAhead(key);
            return;
        }

        final Action action = KeyBinding.test(nativeEvent);
        if (action == null) {
            return;
        }
        switch (action) {
            case MOVE_UP:
                move(activeIndex - 1, -1, 1);
                break;
            case MOVE_DOWN:
                move(activeIndex + 1, 1, 1);
                break;
            case MOVE_PAGE_UP:
                move(activeIndex - PAGE_SIZE, -1, PAGE_SIZE);
                break;
            case MOVE_PAGE_DOWN:
                move(activeIndex + PAGE_SIZE, 1, PAGE_SIZE);
                break;
            case MOVE_START:
                move(0, 1, 1);
                break;
            case MOVE_END:
                move(items.size() - 1, -1, 1);
                break;
            case MOVE_RIGHT:
                if (handler != null) {
                    handler.onOpen(getActiveItem());
                }
                break;
            case MOVE_LEFT:
                if (handler != null) {
                    handler.onBack();
                }
                break;
            case CLOSE:
                // Escape that the menu doesn't use goes on to the popup holding it, which closes
                if (handler == null || !handler.onClose()) {
                    return;
                }
                break;
            case EXECUTE:
            case SELECT:
                if (handler != null && isActionable(getActiveItem())) {
                    handler.onChoose((MenuItem) getActiveItem());
                }
                break;
            default:
                // Other actions (e.g. select all) do nothing in a menu, and don't reach the page
                break;
        }
        handled(nativeEvent);
    }

    // Goes to the next item, enabled or not, whose text starts with what has been typed, as the
    // arrow keys would
    private void typeAhead(final String text) {
        final int index = typeAhead.next(text,
                activeIndex,
                items.size(),
                i -> isReachable(items.get(i))
                        ? getItemText(i)
                        : null);
        if (index >= 0) {
            moveTo(index);
        }
    }

    private void onMouseDown(final MouseDownEvent event) {
        setKeyboard(false);
        final int index = indexOf(event.getNativeEvent());
        final Item item = index >= 0
                ? items.get(index)
                : null;
        if (isActionable(item)) {
            setActiveIndex(index, true);
        } else {
            // Neither a separator, a heading nor a disabled item takes the focus from the active item
            event.preventDefault();
        }
        if (handler != null) {
            handler.onPress(item, MouseUtil.isPrimary(event.getNativeEvent()));
        }
    }

    private void onMouseUp(final MouseUpEvent event) {
        if (handler != null) {
            final int index = indexOf(event.getNativeEvent());
            handler.onRelease(index >= 0
                            ? items.get(index)
                            : null,
                    MouseUtil.isPrimary(event.getNativeEvent()));
        }
    }

    private void onMouseMove(final MouseMoveEvent event) {
        setKeyboard(false);
        final int index = indexOf(event.getNativeEvent());
        if (handler != null) {
            handler.onMouseOver(index >= 0
                    ? items.get(index)
                    : null);
        }
    }

    // The focus leaving an item, for another item or out of the menu
    private void onFocusOut(final NativeEvent event) {
        final int index = indexOf(event);
        if (index >= 0 && handler != null) {
            handler.onLeft(items.get(index));
        }
    }

    // The index of the item whose row the event happened in, or -1 for none
    private int indexOf(final NativeEvent event) {
        final EventTarget target = event.getEventTarget();
        if (!Element.is(target)) {
            return -1;
        }
        Element element = Element.as(target);
        while (element != null && element != menuElement) {
            if (element.hasAttribute(INDEX_ATTRIBUTE) && element.getParentElement() == menuElement) {
                return Integer.parseInt(element.getAttribute(INDEX_ATTRIBUTE));
            }
            element = element.getParentElement();
        }
        return -1;
    }

    // Whether the keyboard is in use in the menu, which shows its focus ring
    private void setKeyboard(final boolean keyboard) {
        if (keyboard) {
            menuElement.addClassName(KEYBOARD_CLASS);
        } else {
            menuElement.removeClassName(KEYBOARD_CLASS);
        }
    }

    private static void handled(final NativeEvent event) {
        event.stopPropagation();
        event.preventDefault();
    }

    // A reachable item's row is a stop for the keyboard: the active one is the menu's tab stop, the
    // others are focused by the menu itself
    private static void setTabIndex(final Element row, final Item item, final boolean active) {
        if (active) {
            row.setAttribute(TAB_INDEX, "0");
        } else if (isReachable(item)) {
            row.setAttribute(TAB_INDEX, "-1");
        } else {
            row.removeAttribute(TAB_INDEX);
        }
    }

    private void setRowAria(final Element row, final Item item) {
        setTabIndex(row, item, rows.indexOf(row) == activeIndex);
        if (item instanceof final MenuItem menuItem) {
            if (item instanceof final CheckMenuItem checkMenuItem) {
                // A setting that is on or off, read as a ticked or unticked check box
                row.setAttribute(ROLE, "menuitemcheckbox");
                row.setAttribute(ARIA_CHECKED, String.valueOf(checkMenuItem.isChecked()));
            } else {
                row.setAttribute(ROLE, "menuitem");
                row.removeAttribute(ARIA_CHECKED);
            }
            if (menuItem.isEnabled()) {
                row.removeAttribute(ARIA_DISABLED);
            } else {
                row.setAttribute(ARIA_DISABLED, "true");
            }
            final String keyShortcuts = menuItem.getAriaKeyShortcuts();
            if (keyShortcuts != null) {
                row.setAttribute(ARIA_KEY_SHORTCUTS, keyShortcuts);
            } else {
                row.removeAttribute(ARIA_KEY_SHORTCUTS);
            }
            if (item instanceof HasChildren) {
                row.setAttribute(ARIA_HAS_POPUP, "menu");
                row.setAttribute(ARIA_EXPANDED, String.valueOf(Objects.equals(item, openItem)));
            } else {
                row.removeAttribute(ARIA_HAS_POPUP);
                row.removeAttribute(ARIA_EXPANDED);
            }
        } else if (item instanceof Separator) {
            row.setAttribute(ROLE, "separator");
        } else {
            // e.g. a group heading, which is read as it comes
            row.setAttribute(ROLE, ROLE_NONE);
        }
    }

    private static native JavaScriptObject addFocusOutListener(Element element,
                                                               FocusOutCallback callback) /*-{
        var listener = $entry(function (event) {
            callback.@stroom.widget.menu.client.component.Menu.FocusOutCallback::onFocusOut(*)(event);
        });
        element.addEventListener('focusout', listener);
        return listener;
    }-*/;

    private static native void removeFocusOutListener(Element element, JavaScriptObject listener) /*-{
        element.removeEventListener('focusout', listener);
    }-*/;

    // --------------------------------------------------------------------------------

    /// What a [Menu]'s items do. The menu calls it as the keyboard and mouse act on it.
    public interface Handler {

        /// The keyboard (an arrow key, Page Up, Page Down, Home, End or type-ahead) has moved to
        /// an item, which is now active and focused.
        ///
        /// @param item The item, a [MenuItem], enabled or not.
        void onMovedTo(Item item);

        /// Right was pressed, to open the active item's sub menu.
        ///
        /// @param item The active item, or null for none.
        void onOpen(Item item);

        /// Left was pressed, to go back to the menu this one is a sub menu of.
        void onBack();

        /// Escape was pressed.
        ///
        /// @return Whether the menu used it; if not, it goes on to whatever holds the menu.
        boolean onClose();

        /// Enter or Space was pressed on the active item, which is enabled.
        ///
        /// @param item The item.
        void onChoose(MenuItem item);

        /// A mouse button was pressed on the menu. An enabled item pressed is active and focused.
        ///
        /// @param item The item pressed, or null for none (e.g. the gap round the items).
        /// @param primary Whether it was the primary button.
        void onPress(Item item, boolean primary);

        /// A mouse button was let go over the menu.
        ///
        /// @param item The item let go over, or null for none.
        /// @param primary Whether it was the primary button.
        void onRelease(Item item, boolean primary);

        /// The mouse moved over the menu.
        ///
        /// @param item The item it is over, or null for none.
        void onMouseOver(Item item);

        /// The focus left an item, for another item or out of the menu.
        ///
        /// @param item The item.
        void onLeft(Item item);
    }

    // --------------------------------------------------------------------------------

    @FunctionalInterface
    private interface FocusOutCallback {

        void onFocusOut(NativeEvent event);
    }

    // --------------------------------------------------------------------------------

    interface Binder extends UiBinder<DivElement, Menu> {

    }
}
