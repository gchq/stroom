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

import stroom.data.grid.client.MyDataGrid;
import stroom.data.table.client.MyCellTable;
import stroom.widget.menu.client.presenter.MenuPresenter.MenuView;
import stroom.widget.util.client.AbstractSelectionEventManager;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.TableCellElement;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.user.cellview.client.AbstractHasData;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.CellPreviewEvent;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class MenuViewImpl extends ViewWithUiHandlers<MenuUiHandlers> implements MenuView {

    private static final int SUBMENU_SHOW_DELAY_MILLIS = 400;
    private static final String ROLE = "role";
    private static final String ROLE_NONE = "none";
    private static final String ARIA_DISABLED = "aria-disabled";
    private static final String ARIA_HAS_POPUP = "aria-haspopup";
    private static final Set<String> TABLE_SECTIONS = Set.of("THEAD", "TBODY", "TFOOT");

    private final CellTable<Item> cellTable;
    private final Widget widget;

    private final MySingleSelectionModel<Item> selectionModel = new MySingleSelectionModel<>();
    private int mouseOverRow = -1;
    private Timer subMenuShowTimer;
    // The item that has a delayed sub menu scheduled
    private Item timerItem = null;
    private boolean allowCloseOnMoveLeft = false;

    public MenuViewImpl() {
        cellTable = new MyCellTable<>(MyDataGrid.DEFAULT_LIST_PAGE_SIZE);
        cellTable.getElement().setClassName("menuCellTable");
        // The table is a menu, and each time it is drawn its rows are given the roles of menu items
        cellTable.getElement().setAttribute(ROLE, "menu");
        cellTable.addRedrawHandler(this::updateAria);

        // Sink events.
        final int mouseMove = Event.getTypeInt(BrowserEvents.MOUSEMOVE);
        cellTable.sinkEvents(mouseMove);

        cellTable.getElement().getStyle().setProperty("minWidth", 50 + "px");
        cellTable.getElement().getStyle().setProperty("maxWidth", 600 + "px");

        final ScrollPanel scrollPanel = new ScrollPanel(cellTable);
        scrollPanel.getElement().getStyle().setProperty("minWidth", 50 + "px");
        scrollPanel.getElement().getStyle().setProperty("maxWidth", 400 + "px");
        scrollPanel.getElement().getStyle().setProperty("maxHeight", 500 + "px");

        final Column<Item, Item> iconColumn = new Column<Item, Item>(new MenuItemCell()) {
            @Override
            public Item getValue(final Item item) {
                return item;
            }
        };
        cellTable.addColumn(iconColumn);
        cellTable.setSkipRowHoverCheck(true);

        cellTable.setSelectionModel(selectionModel, new MenuSelectionEventManager(cellTable));

        widget = scrollPanel;
    }

    // An enabled item, which can be chosen and can open its sub menu
    private boolean isActionable(final Item item) {
        return item instanceof MenuItem && ((MenuItem) item).isEnabled();
    }

    // Any item, enabled or not, that the arrow keys move to, so that a disabled item is still read
    // out, as disabled
    private boolean isReachable(final Item item) {
        return item instanceof MenuItem;
    }

    // Gives the table the structure of a menu: the table's own parts have no role, and the
    // focusable element of each row (the div GWT's table builder puts in the cell, which takes
    // the keyboard focus) is a menu item or separator
    private void updateAria() {
        final NodeList<Element> sections = cellTable.getElement().getChildNodes().cast();
        for (int i = 0; i < sections.getLength(); i++) {
            final Element section = sections.getItem(i);
            // The head, body and foot (a column group may not have a role)
            if (Element.is(section) && TABLE_SECTIONS.contains(section.getTagName().toUpperCase(Locale.ROOT))) {
                section.setAttribute(ROLE, ROLE_NONE);
            }
        }
        final List<Item> items = cellTable.getVisibleItems();
        for (int row = 0; row < items.size(); row++) {
            final TableRowElement tr = cellTable.getRowElement(row);
            tr.setAttribute(ROLE, ROLE_NONE);
            final NodeList<TableCellElement> cells = tr.getCells();
            for (int i = 0; i < cells.getLength(); i++) {
                final TableCellElement td = cells.getItem(i);
                td.setAttribute(ROLE, ROLE_NONE);
                final Element focusable = td.getFirstChildElement();
                if (focusable != null) {
                    setItemAria(focusable, items.get(row));
                }
            }
        }
    }

    private static void setItemAria(final Element element, final Item item) {
        if (item instanceof final MenuItem menuItem) {
            element.setAttribute(ROLE, "menuitem");
            if (menuItem.isEnabled()) {
                element.removeAttribute(ARIA_DISABLED);
            } else {
                element.setAttribute(ARIA_DISABLED, "true");
            }
            if (item instanceof HasChildren) {
                element.setAttribute(ARIA_HAS_POPUP, "menu");
            } else {
                element.removeAttribute(ARIA_HAS_POPUP);
            }
        } else if (item instanceof Separator) {
            element.setAttribute(ROLE, "separator");
        } else {
            // e.g. a group heading, which is read as it comes
            element.setAttribute(ROLE, ROLE_NONE);
        }
    }

    public void showSubMenu(final Item item) {
        if (getUiHandlers() != null && item instanceof MenuItem) {
            cancelDelayedSubMenu();
            getUiHandlers().showSubMenu((MenuItem) item, getRowElement(item));
        }
    }

    private void toggleSubMenu(final Item item) {
        if (getUiHandlers() != null && item instanceof MenuItem) {
            cancelDelayedSubMenu();
            getUiHandlers().toggleSubMenu((MenuItem) item, getRowElement(item));
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
                            getUiHandlers().showSubMenu((MenuItem) item, getRowElement(item));
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

    private Element getRowElement(final Item item) {
        if (getUiHandlers() != null && item instanceof MenuItem) {
            final List<Item> items = cellTable.getVisibleItems();
            final int row = items.indexOf(item);
            return cellTable.getRowElement(row);
        } else {
            return null;
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
        return widget;
    }

    @Override
    public void setData(final List<Item> items) {
        cellTable.setRowData(0, items);
        cellTable.setRowCount(items.size());
    }

    @Override
    public void setAllowCloseOnMoveLeft(final boolean allowCloseOnMoveLeft) {
        this.allowCloseOnMoveLeft = allowCloseOnMoveLeft;
    }

    @Override
    public void selectFirstItem() {
        final int row = getFirstSelectableRow();
        if (row >= 0) {
            final List<Item> items = cellTable.getVisibleItems();
            final Item item = items.get(row);
            selectRow(row, true);
            showSubMenu(item);
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
            final int row = cellTable.getVisibleItems().indexOf(parentItem);
            if (row >= 0) {
                selectRow(row, false);
            }
        }
    }

    @Override
    public void focus() {
        final int row = getFirstSelectableRow();
        if (row >= 0) {
            selectRow(row, true);
        }
    }

    private void selectRow(final int row, final boolean stealFocus) {
        final List<Item> items = cellTable.getVisibleItems();
        if (row >= 0 && row < items.size()) {
            final Item item = items.get(row);
            selectionModel.setSelected(item, true);
            cellTable.setKeyboardSelectedRow(row, stealFocus);
        }
    }

    private int getFirstSelectableRow() {
        final List<Item> items = cellTable.getVisibleItems();

        int row = cellTable.getKeyboardSelectedRow();
        if (row > 0 && row < items.size()) {
            return row;
        }

        row = -1;
        for (int i = 0; i < items.size() && row == -1; i++) {
            final Item item = items.get(i);
            if (isActionable(item)) {
                row = i;
            }
        }

        return row;
    }


    // --------------------------------------------------------------------------------


    private class MenuSelectionEventManager
            extends AbstractSelectionEventManager<Item> {

        public MenuSelectionEventManager(final AbstractHasData<Item> cellTable) {
            super(cellTable);
        }

        @Override
        protected void onMoveRight(final CellPreviewEvent<Item> e) {
            final Item selected = selectionModel.getSelectedObject();
            // A disabled item's sub menu doesn't open
            if (isActionable(selected)) {
                showSubMenu(selected);
                focusSubMenu();
            }
        }

        @Override
        protected void onMoveLeft(final CellPreviewEvent<Item> e) {
            if (hasParent()) {
                focusParent(true);
            } else if (allowCloseOnMoveLeft) {
                escape();
            }
        }

        @Override
        protected void onClose(final CellPreviewEvent<Item> e) {
            escape();
        }

        @Override
        protected void onExecute(final CellPreviewEvent<Item> e) {
            onSelect(e);
        }

        @Override
        protected void onSelect(final CellPreviewEvent<Item> e) {
            final Item selected = selectionModel.getSelectedObject();
            // A disabled item does nothing
            if (isActionable(selected)) {
                execute((MenuItem) selected);
            }
        }

        @Override
        protected void onMouseDown(final CellPreviewEvent<Item> e) {
            final Item item = e.getValue();
            if (isActionable(item)) {
                final int row = cellTable.getVisibleItems().indexOf(item);
                selectRow(row, true);

                if (item instanceof MenuItem && ((MenuItem) item).getCommand() != null) {
                    execute((MenuItem) item);
                } else {
                    // A click on the item of an open sub menu closes it
                    toggleSubMenu(item);
                }
            }
        }

        @Override
        protected void onMouseMove(final CellPreviewEvent<Item> e) {
            // We have moved the mouse over this menu so tell the parent to cancel the sub menu timer and ensure the
            // right menu item is selected.
            if (getUiHandlers() != null) {
                getUiHandlers().ensureParentItemSelected();
            }

            final Item item = e.getValue();
            if (isActionable(item)) {
                final int row = cellTable.getVisibleItems().indexOf(item);
                if (row != mouseOverRow) {
                    selectRow(row, true);
                    mouseOverRow = row;
                    showSubMenuAfterDelay(item, SUBMENU_SHOW_DELAY_MILLIS);
                }
            }
        }

        @Override
        protected void onBlur(final CellPreviewEvent<Item> e) {
            final Item item = e.getValue();
            if (isActionable(item)) {
                mouseOverRow = -1;
            }
        }

        @Override
        protected void onKeyboardSelectRow(final int row, final boolean stealFocus) {
            selectRow(row, true);
            final List<Item> items = cellTable.getVisibleItems();
            final Item item = items.get(row);
            if (isActionable(item)) {
                showSubMenu(item);
            } else if (item instanceof final MenuItem menuItem && getUiHandlers() != null) {
                // A disabled item opens no sub menu, so close the one the previous item opened
                cancelDelayedSubMenu();
                getUiHandlers().hideExistingSubMenu(menuItem);
            }
        }

        @Override
        protected boolean isSelectable(final Item item) {
            return isReachable(item);
        }
    }
}
