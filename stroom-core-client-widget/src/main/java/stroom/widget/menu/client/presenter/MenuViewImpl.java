package stroom.widget.menu.client.presenter;

import stroom.data.grid.client.MyDataGrid;
import stroom.data.table.client.MyCellTable;
import stroom.widget.menu.client.presenter.MenuPresenter.MenuView;
import stroom.widget.util.client.AbstractSelectionEventManager;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.cellview.client.AbstractHasData;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.CellPreviewEvent;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.List;

public class MenuViewImpl extends ViewWithUiHandlers<MenuUiHandlers> implements MenuView {

    private final CellTable<Item> cellTable;
    private final Widget widget;

    private final MySingleSelectionModel<Item> selectionModel = new MySingleSelectionModel<>();
    private int mouseOverRow = -1;

    public MenuViewImpl() {
        cellTable = new MyCellTable<>(MyDataGrid.DEFAULT_LIST_PAGE_SIZE);
        cellTable.getElement().setClassName("menuCellTable");

        // Sink events.
        final int mouseMove = Event.getTypeInt(BrowserEvents.MOUSEMOVE);
        cellTable.sinkEvents(mouseMove);

        cellTable.getElement().getStyle().setProperty("minWidth", 50 + "px");
        cellTable.getElement().getStyle().setProperty("maxWidth", 600 + "px");

        final ScrollPanel scrollPanel = new ScrollPanel(cellTable);
        scrollPanel.getElement().getStyle().setProperty("minWidth", 50 + "px");
        scrollPanel.getElement().getStyle().setProperty("maxWidth", 600 + "px");
        scrollPanel.getElement().getStyle().setProperty("maxHeight", 600 + "px");

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

    private boolean isSelectable(final Item item) {
        return item instanceof MenuItem && ((MenuItem) item).isEnabled();
    }

    public void showSubMenu(final Item item) {
        if (getUiHandlers() != null && item instanceof MenuItem) {
            final List<Item> items = cellTable.getVisibleItems();
            final int row = items.indexOf(item);
            final Element rowElement = cellTable.getRowElement(row);
            getUiHandlers().showSubMenu((MenuItem) item, rowElement);
        }
    }

    public void focusSubMenu() {
        if (getUiHandlers() != null) {
            getUiHandlers().focusSubMenu();
        }
    }

    public void focusParent() {
        if (getUiHandlers() != null) {
            getUiHandlers().focusParent();
        }
    }

    public void escape() {
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
    public void selectFirstItem(final boolean stealFocus) {
        int row = getFirstSelectableRow();
        if (row >= 0) {
            final List<Item> items = cellTable.getVisibleItems();
            final Item item = items.get(row);
            selectRow(row, stealFocus);
            showSubMenu(item);
        }
    }

    @Override
    public void focus() {
        int row = getFirstSelectableRow();
        if (row >= 0) {
            selectRow(row, true);
        }
    }

    private void selectRow(final int row, final boolean stealFocus) {
        final List<Item> items = cellTable.getVisibleItems();
        if (row >= 0 && row < items.size()) {
            final Item item = items.get(row);
            selectionModel.setSelected(item, true);
            cellTable.setKeyboardSelectedRow(row, true);
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
            if (isSelectable(item)) {
                row = i;
            }
        }

        return row;
    }

    private class MenuSelectionEventManager
            extends AbstractSelectionEventManager<Item> {

        public MenuSelectionEventManager(final AbstractHasData<Item> cellTable) {
            super(cellTable);
        }

        @Override
        protected void onMoveRight(final CellPreviewEvent<Item> e) {
            final Item selected = selectionModel.getSelectedObject();
            if (selected instanceof MenuItem) {
                showSubMenu(selected);
                focusSubMenu();
            }
        }

        @Override
        protected void onMoveLeft(final CellPreviewEvent<Item> e) {
            focusParent();
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
            if (selected instanceof MenuItem) {
                execute((MenuItem) selected);
            }
        }

        @Override
        protected void onMouseDown(final CellPreviewEvent<Item> e) {
            final Item item = e.getValue();
            if (isSelectable(item)) {
                final int row = cellTable.getVisibleItems().indexOf(item);
                selectRow(row, false);

                if (item instanceof MenuItem && ((MenuItem) item).getCommand() != null) {
                    execute((MenuItem) item);
                } else {
                    showSubMenu(item);
//                        focusSubMenu();
                }
            }
        }

        @Override
        protected void onMouseMove(final CellPreviewEvent<Item> e) {
            final Item item = e.getValue();
            if (isSelectable(item)) {
                final int row = cellTable.getVisibleItems().indexOf(item);
                if (row != mouseOverRow) {
                    selectRow(row, false);
                    showSubMenu(item);
                    mouseOverRow = row;
                }
            }
        }

        @Override
        protected void onBlur(final CellPreviewEvent<Item> e) {
            final Item item = e.getValue();
            if (isSelectable(item)) {
                mouseOverRow = -1;
            }
        }

        @Override
        protected void onKeyboardSelectRow(final int row, final boolean stealFocus) {
            selectRow(row, true);
            final List<Item> items = cellTable.getVisibleItems();
            final Item item = items.get(row);
            if (item instanceof MenuItem) {
                showSubMenu(item);
            }
        }

        @Override
        protected boolean isSelectable(final Item item) {
            return item instanceof MenuItem && ((MenuItem) item).isEnabled();
        }
    }
}
