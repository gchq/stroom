package stroom.widget.menu.client.presenter;

import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.table.client.CellTableViewImpl.DefaultResources;
import stroom.widget.menu.client.presenter.MenuPresenter.MenuView;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style.Cursor;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.CellTable.Resources;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy.KeyboardSelectionPolicy;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.List;
import java.util.Set;

public class MenuViewImpl extends ViewWithUiHandlers<MenuUiHandlers> implements MenuView, MenuItemCellUiHandler {

    private final CellTable<Item> cellTable;
    private final Widget widget;

    private final MySingleSelectionModel<Item> selectionModel = new MySingleSelectionModel<>();
//    private final Map<Item, Element> hoverItems = new HashMap<>();
//
//    private Set<Item> highlightItems;

    private int mouseOverRow = -1;

    public MenuViewImpl() {
        final Resources resources = GWT.create(DefaultResources.class);
        cellTable = new CellTable<>(DataGridViewImpl.DEFAULT_LIST_PAGE_SIZE, resources);
        cellTable.setWidth("100%");
        cellTable.getElement().setClassName("menuCellTable");
        cellTable.setLoadingIndicator(null);

        // Sink events.
        final int mouseMove = Event.getTypeInt(BrowserEvents.MOUSEMOVE);
        cellTable.sinkEvents(mouseMove);

        cellTable.getElement().getStyle().setProperty("minWidth", 50 + "px");
        cellTable.getElement().getStyle().setProperty("maxWidth", 600 + "px");

        final ScrollPanel scrollPanel = new ScrollPanel(cellTable);
        scrollPanel.getElement().getStyle().setProperty("minWidth", 50 + "px");
        scrollPanel.getElement().getStyle().setProperty("maxWidth", 600 + "px");
        scrollPanel.getElement().getStyle().setProperty("maxHeight", 600 + "px");

        final Column<Item, Item> iconColumn = new Column<Item, Item>(new MenuItemCell(this)) {
            @Override
            public Item getValue(final Item item) {
                return item;
            }
        };
        cellTable.addColumn(iconColumn);
        cellTable.setSkipRowHoverCheck(true);

        cellTable.setSelectionModel(selectionModel);
        cellTable.setKeyboardSelectionPolicy(KeyboardSelectionPolicy.ENABLED);
        cellTable.getRowContainer().getStyle().setCursor(Cursor.POINTER);

        widget = scrollPanel;
    }

    @Override
    public HandlerRegistration bind() {
        // We need to set this to prevent default keyboard behaviour.
        cellTable.setKeyboardSelectionHandler(e -> {
//            GWT.log("KSH: " + e.getNativeEvent().getType() + " " + e.getValue());
        });

        return cellTable.addCellPreviewHandler(e -> {
            final NativeEvent nativeEvent = e.getNativeEvent();
            final String type = nativeEvent.getType();
//            GWT.log("CELL PREVIEW: " + type + " " + e.getValue());

            if ("keydown".equals(type)) {
                final List<Item> items = cellTable.getVisibleItems();

                if (items.size() > 0) {
                    final Item selected = selectionModel.getSelectedObject();
                    int originalRow = -1;
                    if (selected != null) {
                        originalRow = items.indexOf(selected);
                    }

                    int row = originalRow;
                    int keyCode = nativeEvent.getKeyCode();
                    if (keyCode == KeyCodes.KEY_UP) {
                        for (int i = row - 1; i >= 0; i--) {
                            final Item item = items.get(i);
                            if (isSelectable(item)) {
                                row = i;
                                break;
                            }
                        }

                    } else if (keyCode == KeyCodes.KEY_DOWN) {
                        for (int i = row + 1; i < items.size(); i++) {
                            final Item item = items.get(i);
                            if (isSelectable(item)) {
                                row = i;
                                break;
                            }
                        }
                    } else if (keyCode == KeyCodes.KEY_RIGHT) {
                        if (selected instanceof MenuItem) {
                            showSubMenu(selected);
                            focusSubMenu();
                            row = -1;
                        }
                    } else if (keyCode == KeyCodes.KEY_LEFT) {
                        focusParent();
                        row = -1;

                    } else if (keyCode == KeyCodes.KEY_ESCAPE) {
                        escape();
                        row = -1;

                    } else if (keyCode == KeyCodes.KEY_ENTER) {
                        if (selected instanceof CommandMenuItem) {
                            execute((CommandMenuItem) selected);
                        }
                        row = -1;
                    }

                    if (row >= 0) {
                        if (row != originalRow) {
                            selectRow(row);
                        }

                        final Item item = items.get(row);
                        if (item instanceof MenuItem) {
//                            selectionModel.setSelected(item, true);
                            showSubMenu(item);
                        }
                    }
                }

            } else if ("click".equals(type)) {
                final Item item = e.getValue();
                if (isSelectable(item)) {
                    final int row = cellTable.getVisibleItems().indexOf(item);
                    selectRow(row);

                    if (item instanceof CommandMenuItem && ((CommandMenuItem) item).getCommand() != null) {
                        execute((CommandMenuItem) item);
                    } else {
                        showSubMenu(item);
//                        focusSubMenu();
                    }
                }

            } else if ("mousemove".equals(type)) {
                GWT.log("MOUSEMOVE: " + mouseOverRow);

                final Item item = e.getValue();
                if (isSelectable(item)) {
                    final int row = cellTable.getVisibleItems().indexOf(item);
                    if (row != mouseOverRow) {
                        selectRow(row);
                        showSubMenu(item);
                        mouseOverRow = row;
                    }
                }
            } else if ("blur".equals(type)) {
                final Item item = e.getValue();
                if (isSelectable(item)) {
                    mouseOverRow = -1;
                }

                GWT.log("BLUR: " + mouseOverRow);
            }
        });
    }

    private boolean isSelectable(final Item item) {
        return item instanceof MenuItem &&
                (!(item instanceof CommandMenuItem) || ((CommandMenuItem) item).isEnabled());
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

    public void execute(final CommandMenuItem menuItem) {
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
    public void onClick(final MenuItem menuItem, final Element element) {
    }

    @Override
    public void onMouseOver(final MenuItem menuItem, final Element element) {
//        hoverItems.put(menuItem, element);
    }

    @Override
    public void onMouseOut(final MenuItem menuItem, final Element element) {
//        hoverItems.remove(menuItem);
    }

    @Override
    public boolean isHover(final MenuItem menuItem) {
//        return hoverItems.containsKey(menuItem);
//
        return false;
    }

    protected void removeHover(final MenuItem menuItem) {
//        final Element tr = hoverItems.remove(menuItem);
//        if (tr != null) {
//            tr.removeClassName("cellTableHoveredRow");
//        }
    }

    protected void removeAllHovers() {
//        final Iterator<Entry<Item, Element>> iter = hoverItems.entrySet().iterator();
//        while (iter.hasNext()) {
//            final Entry<Item, Element> entry = iter.next();
//
//            final Element tr = entry.getValue();
//            if (tr != null) {
//                tr.removeClassName("cellTableHoveredRow");
//            }
//
//            iter.remove();
//        }
    }

    @Override
    public boolean isHighlighted(final MenuItem menuItem) {
//        if (highlightItems == null) {
//            return false;
//        }
//        return highlightItems.contains(menuItem);

        return false;
    }

//    @Override
//    public Set<Item> getHighlightItems() {
//        return highlightItems;
//    }

    @Override
    public void setHighlightItems(final Set<Item> highlightItems) {
//        this.highlightItems = highlightItems;
    }

    @Override
    public void setData(final List<Item> items) {
        removeAllHovers();
        cellTable.setRowData(0, items);
        cellTable.setRowCount(items.size());

//        selectionModel.setSelected(items.get(0), true);
//        cellTable.setKeyboardSelectedRow(0, true);
//        Scheduler.get().scheduleDeferred(() -> cellTable.getRowElement(0).focus());
    }

    @Override
    public void selectFirstItem() {
        int row = getFirstSelectableRow();
        if (row >= 0) {
            final List<Item> items = cellTable.getVisibleItems();
            final Item item = items.get(row);
            selectRow(row);
            showSubMenu(item);
        }
    }

    @Override
    public void focus() {
        int row = getFirstSelectableRow();
        if (row >= 0) {
            selectRow(row);
        }
    }

    private void selectRow(final int row) {
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
}
