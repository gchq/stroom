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

package stroom.widget.menu.client.presenter;

import stroom.widget.menu.client.presenter.MenuPresenter.MenuView;
import stroom.widget.presenter.client.ViewWithUiHandlers;

import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.Objects;

/// The view of a [MenuPresenter]: a [Menu], whose items it chooses and whose sub menus it opens
/// and closes (on a hover after a delay, on a click, or with Right and Left).
public class MenuViewImpl extends ViewWithUiHandlers<MenuUiHandlers> implements MenuView, Menu.Handler {

    private static final int SUBMENU_SHOW_DELAY_MILLIS = 400;

    private final Menu menu = new Menu();

    // The item the mouse last moved onto, so that moving within it does nothing more
    private Item mouseOverItem;
    // The item the primary button was pressed on, chosen if it is let go over it too
    private Item pressedItem;
    private Timer subMenuShowTimer;
    // The item that has a delayed sub menu scheduled
    private Item timerItem = null;
    private boolean allowCloseOnMoveLeft = false;

    public MenuViewImpl() {
        menu.setHandler(this);
    }

    public void showSubMenu(final Item item) {
        if (getUiHandlers() != null && item instanceof MenuItem) {
            cancelDelayedSubMenu();
            getUiHandlers().showSubMenu((MenuItem) item, menu.getRowElement(item));
        }
    }

    private void toggleSubMenu(final Item item) {
        if (getUiHandlers() != null && item instanceof MenuItem) {
            cancelDelayedSubMenu();
            getUiHandlers().toggleSubMenu((MenuItem) item, menu.getRowElement(item));
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
                            getUiHandlers().showSubMenu((MenuItem) item, menu.getRowElement(item));
                        }
                    };

                    subMenuShowTimer.schedule(delayMillis);
                    timerItem = item;
                } else if (item instanceof MenuItem) {
                    // Item with no children so hide any existing sub-menu from the previous menu item
                    getUiHandlers().hideExistingSubMenu((MenuItem) item);
                    cancelDelayedSubMenu();
                }
            }
        }
    }

    public void cancelDelayedSubMenu() {
        if (subMenuShowTimer != null) {
            subMenuShowTimer.cancel();
            timerItem = null;
        }
    }

    public void focusSubMenu() {
        if (getUiHandlers() != null) {
            getUiHandlers().focusSubMenu();
        }
    }

    public void focusParent(final boolean hideChildren) {
        if (getUiHandlers() != null) {
            getUiHandlers().focusParent(hideChildren);
        }
    }

    public boolean hasParent() {
        return getUiHandlers() != null && getUiHandlers().hasParent();
    }

    public void escape() {
        cancelDelayedSubMenu();
        if (getUiHandlers() != null) {
            getUiHandlers().escape();
        }
    }

    public void execute(final MenuItem menuItem) {
        if (getUiHandlers() != null) {
            if (menuItem.getCommand() != null) {
                getUiHandlers().execute(menuItem);
            }
        }
    }

    @Override
    public Widget asWidget() {
        return menu;
    }

    @Override
    public void setData(final List<Item> items) {
        menu.setItems(items);
    }

    @Override
    public void setAllowCloseOnMoveLeft(final boolean allowCloseOnMoveLeft) {
        this.allowCloseOnMoveLeft = allowCloseOnMoveLeft;
    }

    @Override
    public void setWidth(final int width) {
        menu.setFixedWidth(width);
    }

    @Override
    public void setName(final String name) {
        menu.setName(name);
    }

    @Override
    public void setLabelledBy(final String id) {
        menu.setLabelledBy(id);
    }

    @Override
    public void setOpenItem(final Item item) {
        menu.setOpenItem(item);
    }

    @Override
    public void selectFirstItem() {
        final int index = getFirstSelectableIndex();
        if (index >= 0) {
            menu.setActiveIndex(index, true);
            showSubMenu(menu.getActiveItem());
        }
    }

    @Override
    public void ensureItemSelected(final Item parentItem) {
        // Called by a sub menu, so we know the mouse is now over the sub menu
        // so cancel any timer that would cause a different sub menu to open.
        // This can happen if cursor is moved diagonally from top menu item to sub menu,
        // crossing another top menu as it goes.
        cancelDelayedSubMenu();

        // Make sure the item is selected as the parent of the current child menu.
        if (parentItem != null) {
            menu.setActiveIndex(menu.getItems().indexOf(parentItem), false);
        }
    }

    @Override
    public void focus() {
        final int index = getFirstSelectableIndex();
        if (index >= 0) {
            menu.setActiveIndex(index, true);
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

    // --------------------------------------------------------------------------------
    // Menu.Handler

    @Override
    public void onMovedTo(final Item item) {
        if (Menu.isActionable(item)) {
            showSubMenu(item);
        } else if (item instanceof final MenuItem menuItem && getUiHandlers() != null) {
            // A disabled item opens no sub menu, so close the one the previous item opened
            cancelDelayedSubMenu();
            getUiHandlers().hideExistingSubMenu(menuItem);
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
        if (hasParent()) {
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
        // ensure the right menu item is selected.
        if (getUiHandlers() != null) {
            getUiHandlers().ensureParentItemSelected();
        }

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
