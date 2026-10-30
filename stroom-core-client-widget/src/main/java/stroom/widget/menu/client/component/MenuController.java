/*
 * Copyright 2016 Crown Copyright
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

import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.menu.client.HasChildren;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.MenuItem;
import stroom.widget.menu.client.MenuManager;
import stroom.widget.menu.client.ShowMenuEvent;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.PopupPosition.PopupLocation;
import stroom.widget.popup.client.PopupType;
import stroom.widget.task.client.SimpleTask;
import stroom.widget.task.client.Task;
import stroom.widget.task.client.TaskMonitor;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.Rect;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;

/// A menu's controller: it shows a [Menu], chooses its items and opens and closes its sub menus
/// (on a hover after a delay, on a click, or with Right and Left), each a [MenuController] of its
/// own, shown as a popup beside the item that opened it. [MenuManager] shows the root menu.
public class MenuController extends Controller implements Menu.Handler {

    private static final int HORIZONTAL_PADDING = 2;
    // How far a menu's items sit in from its top edge (its border and its viewport's padding, menu.css),
    // so that a sub menu's first item lines up with the item that opened it
    private static final int VERTICAL_PADDING = 6;
    private static final String ITEM_TEXT_CLASS = "menu__text";
    private static final int SUBMENU_SHOW_DELAY_MILLIS = 400;

    private final Menu menu;
    private final Provider<MenuController> menuControllerProvider;
    private MenuController currentMenu;
    private MenuItem currentItem;
    // Whether the current sub menu was opened (or since clicked) by a click on its item, rather
    // than by hovering over it or the keyboard
    private boolean subMenuOpenedByClick;

    private MenuController parent;
    private MenuItem parentItem;

    // The item the mouse last moved onto, so that moving within it does nothing more
    private Item mouseOverItem;
    // The item the primary button was pressed on, chosen if it is let go over it too
    private Item pressedItem;
    private Timer subMenuShowTimer;
    // The item that has a delayed sub menu scheduled
    private Item timerItem = null;
    private boolean allowCloseOnMoveLeft = false;
    // Set once the menu is closed, which is before it is disposed (once the popup manager is done)
    private boolean hidden;

    /// Made by GIN for one use: a menu shown once, as a popup.
    ///
    /// @param eventBus               The application's event bus, which the popup is shown on.
    /// @param menu                   The menu shown.
    /// @param menuControllerProvider Makes the sub menus.
    @Inject
    public MenuController(final EventBus eventBus,
                          final Menu menu,
                          final Provider<MenuController> menuControllerProvider) {
        super(eventBus);
        this.menu = menu;
        this.menuControllerProvider = menuControllerProvider;
        initWidget(menu);
        menu.setHandler(this);
        onDispose(this::cancelDelayedSubMenu);
    }

    /// Shows the menu's items.
    ///
    /// @param items The items, in order.
    public void setData(final List<Item> items) {
        menu.setItems(items);
    }

    /// Sets whether Left closes the menu when it isn't a sub menu, e.g. for a menu opened with
    /// Right from an explorer tree's item.
    ///
    /// @param allowCloseOnMoveLeft Whether it does.
    public void setAllowCloseOnMoveLeft(final boolean allowCloseOnMoveLeft) {
        this.allowCloseOnMoveLeft = allowCloseOnMoveLeft;
    }

    /// Gives the menu an accessible name, which screen readers announce on entering it.
    ///
    /// @param name The name, as plain text without the word "menu", or null for none.
    public void setName(final String name) {
        menu.setName(name);
    }

    /// Names the menu by another element, e.g. the button that opened it.
    ///
    /// @param id The id of the element that names the menu, or null for none.
    public void setLabelledBy(final String id) {
        menu.setLabelledBy(id);
    }

    /// Gives the menu a fixed width rather than fitting it to its items. See
    /// [ShowMenuEvent.Builder#width(int)].
    ///
    /// @param width The menu's width in pixels.
    public void setWidth(final int width) {
        menu.setFixedWidth(width);
    }

    /// Focuses the active item, else the first enabled one.
    public void focus() {
        final int index = getFirstSelectableIndex();
        if (index >= 0) {
            menu.setActiveIndex(index, true);
        }
    }

    /// Focuses an item of the menu, or its first if the item is null or not in the menu.
    ///
    /// @param item The item to focus. Can be null.
    public void focusItem(final Item item) {
        if (item != null) {
            ensureItemSelected(item);
        }
        focus();
    }

    /// Closes the menu, any sub menus it has open and any menus it is a sub menu of.
    ///
    /// @param autoClose Whether it is closed by a click outside it.
    /// @param ok        Whether it is closed by a choice.
    public void hideAll(final boolean autoClose, final boolean ok) {
        cancelDelayedSubMenu();
        hideChildren(autoClose, ok);
        hideSelf(autoClose, ok);
        hideParent(autoClose, ok);
    }

    private void setParent(final MenuController parent, final MenuItem parentItem) {
        this.parent = parent;
        this.parentItem = parentItem;
    }

    private void focus(final boolean hideChildren) {
        hideChildren(hideChildren, false);
        focus();
    }

    private void selectFirstItem() {
        final int index = getFirstSelectableIndex();
        if (index >= 0) {
            menu.setActiveIndex(index, true);
            showSubMenu(menu.getActiveItem());
        }
    }

    // Called by a sub menu, so we know the mouse is now over the sub menu so cancel any timer that
    // would cause a different sub menu to open. This can happen if the cursor is moved diagonally
    // from a menu item to its sub menu, crossing another item as it goes
    private void ensureItemSelected(final Item item) {
        cancelDelayedSubMenu();
        // Make sure the item is selected as the parent of the current child menu
        if (item != null) {
            menu.setActiveIndex(menu.getItems().indexOf(item), false);
        }
    }

    // The active item, else the first enabled one, or -1 for none
    private int getFirstSelectableIndex() {
        if (menu.getActiveIndex() >= 0) {
            return menu.getActiveIndex();
        }
        final List<Item> items = menu.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (Menu.isActionable(items.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private void showSubMenu(final Item item) {
        if (item instanceof MenuItem) {
            cancelDelayedSubMenu();
            showSubMenu((MenuItem) item, menu.getRowElement(item), false);
        }
    }

    private void toggleSubMenu(final Item item) {
        if (item instanceof final MenuItem menuItem) {
            cancelDelayedSubMenu();
            if (currentItem != null && Objects.equals(currentItem, menuItem)) {
                if (subMenuOpenedByClick) {
                    hideChildren(false, false);
                } else {
                    // The sub menu was opened by hovering over its item, perhaps only just, so a
                    // click on the item (likely meant to open it) leaves it open. A further click
                    // closes it
                    subMenuOpenedByClick = true;
                }
            } else {
                showSubMenu(menuItem, menu.getRowElement(item), true);
            }
        }
    }

    private void showSubMenu(final MenuItem menuItem, final Element element, final boolean byClick) {
        if (!Objects.equals(currentItem, menuItem)) {
            subMenuOpenedByClick = byClick;
            if (currentItem != null) {
                // We are changing the highlighted item so close the current popup if it is open
                hideChildren(false, false);
            }

            if (menuItem instanceof final HasChildren hasChildren) {
                hasChildren.getChildren().onSuccess(children -> {
                    // Not once the menu is closed: a sub menu shown then would be left open
                    if (children != null && !children.isEmpty() && !hidden && !isDisposed()) {
                        // We are changing the highlighted item so close the current popup if it is
                        // open
                        hideChildren(false, false);

                        final MenuController subMenu = menuControllerProvider.get();
                        subMenu.setParent(this, menuItem);
                        // Named by the text of its parent item. Not by reference to the item's
                        // element, which loses any id it is given when the menu redraws its rows
                        subMenu.setName(getItemText(element));
                        subMenu.setData(children);

                        // Tell this menu that the sub menu is showing
                        currentMenu = subMenu;
                        setCurrentItem(menuItem);

                        final Rect dialog = new Rect(menu.getElement());
                        final Rect selection = new Rect(element);
                        Rect relativeRect = Rect.min(dialog, selection);
                        relativeRect = relativeRect.growX(HORIZONTAL_PADDING);
                        relativeRect = relativeRect.growY(VERTICAL_PADDING);

                        final PopupPosition popupPosition = new PopupPosition(relativeRect, PopupLocation.RIGHT);

                        ControllerDialog.showOnce(this, subMenu)
                                .popupType(PopupType.POPUP)
                                .popupPosition(popupPosition)
                                .addAutoHidePartner(element)
                                .onHideRequest(e -> {
                                    if (!e.isAutoClose() && !e.isOk()) {
                                        // Escape, which the sub menu's popup takes as its close
                                        // action: close the sub menu, as Escape in it does
                                        subMenu.escape();
                                    } else {
                                        subMenu.hideChildren(e.isAutoClose(), e.isOk());
                                        subMenu.hideSelf(e.isAutoClose(), e.isOk());
                                        currentMenu = null;
                                        setCurrentItem(null);
                                    }
                                })
                                .fire();
                    }
                });
            }
        }
    }

    private void showSubMenuAfterDelay(final Item item, final int delayMillis) {
        if (item != null) {
            if (timerItem == null || !Objects.equals(item, timerItem)) {
                if (subMenuShowTimer != null) {
                    subMenuShowTimer.cancel();
                }

                if (item instanceof HasChildren) {
                    subMenuShowTimer = new Timer() {
                        @Override
                        public void run() {
                            // Timer has fired so clear the item associated with it
                            timerItem = null;
                            showSubMenu((MenuItem) item, menu.getRowElement(item), false);
                        }
                    };

                    subMenuShowTimer.schedule(delayMillis);
                    timerItem = item;
                } else if (item instanceof MenuItem) {
                    // Item with no children so hide any existing sub-menu from the previous menu item
                    hideExistingSubMenu((MenuItem) item);
                    cancelDelayedSubMenu();
                }
            }
        }
    }

    private void cancelDelayedSubMenu() {
        if (subMenuShowTimer != null) {
            subMenuShowTimer.cancel();
            timerItem = null;
        }
    }

    // The text of a menu item's row, without its shortcut or arrow
    private static String getItemText(final Element rowElement) {
        if (rowElement == null) {
            return null;
        }
        final Element text = ElementUtil.findChild(rowElement, MenuController::isItemText);
        final String name = text != null
                ? text.getInnerText()
                : rowElement.getInnerText();
        return name == null
                ? null
                : name.trim();
    }

    // Whether an element holds a menu item's text. Its class is read as an attribute, as an SVG
    // element's className isn't a string
    private static boolean isItemText(final Element element) {
        final String className = ElementUtil.getClassName(element);
        if (className == null) {
            return false;
        }
        for (final String name : className.split(" ")) {
            if (ITEM_TEXT_CLASS.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private void hideExistingSubMenu(final MenuItem newMenuItem) {
        if (currentItem != null && !Objects.equals(currentItem, newMenuItem)) {
            hideChildren(false, false);
        }
    }

    private void ensureParentItemSelected() {
        if (parent != null && parentItem != null) {
            parent.ensureItemSelected(parentItem);
        }
    }

    private void focusSubMenu() {
        if (currentMenu != null) {
            currentMenu.selectFirstItem();
        }
    }

    private void focusParent(final boolean hideChildren) {
        if (parent != null) {
            hideChildren(hideChildren, false);
            parent.focus(hideChildren);
        }
    }

    private void execute(final MenuItem menuItem) {
        if (menuItem != null && menuItem.getCommand() != null) {
            final TaskMonitor taskMonitor = taskMonitor().createTaskMonitor();
            final Task task = new SimpleTask("Executing menu item " + menuItem.getText());
            taskMonitor.onStart(task);
            Scheduler.get().scheduleDeferred(() -> {
                try {
                    hideAll(false, false);
                    menuItem.getCommand().execute();
                } finally {
                    taskMonitor.onEnd(task);
                }
            });
        }
    }

    private void hideSelf(final boolean autoClose, final boolean ok) {
        hidden = true;
        cancelDelayedSubMenu();
        ControllerDialog.hide(this, autoClose, ok);
    }

    private void hideChildren(final boolean autoClose, final boolean ok) {
        // First make sure all children are hidden
        if (currentMenu != null) {
            currentMenu.cancelDelayedSubMenu();
            currentMenu.hideChildren(autoClose, ok);
            currentMenu.hideSelf(autoClose, ok);
            currentMenu = null;
        }
        setCurrentItem(null);
    }

    // The item whose sub menu is open, or null for none
    private void setCurrentItem(final MenuItem item) {
        currentItem = item;
        menu.setOpenItem(item);
    }

    private void hideParent(final boolean autoClose, final boolean ok) {
        if (parent != null) {
            parent.cancelDelayedSubMenu();
            parent.hideSelf(autoClose, ok);
            parent.hideParent(autoClose, ok);
        }
    }

    // Closes this menu: a sub menu closes and puts the focus back on the item that opened it, as
    // Left does (the WAI-ARIA menu pattern); the root menu closes with any sub menus
    private void escape() {
        cancelDelayedSubMenu();
        if (parent != null) {
            focusParent(true);
        } else {
            hideAll(true, false);
        }
    }

    // --------------------------------------------------------------------------------
    // Menu.Handler

    @Override
    public void onMovedTo(final Item item) {
        if (Menu.isActionable(item)) {
            showSubMenu(item);
        } else if (item instanceof final MenuItem menuItem) {
            // A disabled item opens no sub menu, so close the one the previous item opened
            cancelDelayedSubMenu();
            hideExistingSubMenu(menuItem);
        }
    }

    @Override
    public void onOpen(final Item item) {
        // A disabled item's sub menu doesn't open
        if (Menu.isActionable(item)) {
            showSubMenu(item);
            focusSubMenu();
        }
    }

    @Override
    public void onBack() {
        if (parent != null) {
            focusParent(true);
        } else if (allowCloseOnMoveLeft) {
            escape();
        }
    }

    @Override
    public boolean onClose() {
        escape();
        return true;
    }

    @Override
    public void onChoose(final MenuItem item) {
        execute(item);
    }

    @Override
    public void onPress(final Item item, final boolean primary) {
        pressedItem = null;
        if (Menu.isActionable(item)) {
            if (((MenuItem) item).getCommand() != null) {
                // Chosen when the button is let go over it, so that a press can be taken back by
                // moving off it first (WCAG 2.5.2)
                if (primary) {
                    pressedItem = item;
                }
            } else {
                // A click on the item of an open sub menu closes it
                toggleSubMenu(item);
            }
        }
    }

    @Override
    public void onRelease(final Item item, final boolean primary) {
        final boolean chosen = item != null
                               && Objects.equals(item, pressedItem)
                               && primary
                               && Menu.isActionable(item);
        pressedItem = null;
        if (chosen) {
            execute((MenuItem) item);
        }
    }

    @Override
    public void onMouseOver(final Item item) {
        // We have moved the mouse over this menu so tell the parent to cancel the sub menu timer and
        // ensure the right menu item is selected
        ensureParentItemSelected();

        if (Menu.isActionable(item) && !Objects.equals(item, mouseOverItem)) {
            menu.setActiveIndex(menu.getItems().indexOf(item), true);
            mouseOverItem = item;
            showSubMenuAfterDelay(item, SUBMENU_SHOW_DELAY_MILLIS);
        }
    }

    @Override
    public void onLeft(final Item item) {
        if (Menu.isActionable(item)) {
            mouseOverItem = null;
        }
    }
}
