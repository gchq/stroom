package stroom.explorer.client.presenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.cellview.client.AbstractCellTable;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.view.client.CellPreviewEvent;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.explorer.client.event.ExplorerTreeSelectEvent;
import stroom.explorer.client.event.ShowExplorerMenuEvent;
import stroom.explorer.client.view.ExplorerTickBoxCell;
import stroom.explorer.shared.ExplorerData;
import stroom.explorer.shared.FetchExplorerDataResult;
import stroom.explorer.shared.TreeStructure;
import stroom.util.shared.EqualsUtil;
import stroom.util.shared.HasNodeState;
import stroom.widget.spinner.client.SpinnerSmall;
import stroom.widget.util.client.DoubleSelectTest;

import java.util.List;
import java.util.Set;

public class ExplorerTickBoxTree extends Composite {
    private final ExplorerTreeModel treeModel;
    private final TickBoxSelectionModel selectionModel;
    private final CellTable<ExplorerData> cellTable;
    private final DoubleSelectTest doubleClickTest = new DoubleSelectTest();

    private String expanderClassName;
    private String tickBoxClassName;
    ExplorerData selectedItem;

    public ExplorerTickBoxTree(final ClientDispatchAsync dispatcher) {
        final SpinnerSmall spinnerSmall = new SpinnerSmall();
        spinnerSmall.getElement().getStyle().setPosition(Style.Position.ABSOLUTE);
        spinnerSmall.getElement().getStyle().setRight(5, Style.Unit.PX);
        spinnerSmall.getElement().getStyle().setTop(5, Style.Unit.PX);

        selectionModel = new TickBoxSelectionModel();

        final ExplorerTickBoxCell explorerCell = new ExplorerTickBoxCell(selectionModel);
        expanderClassName = explorerCell.getExpanderClassName();
        tickBoxClassName = explorerCell.getTickBoxClassName();

        final ExplorerTreeResources resources = GWT.create(ExplorerTreeResources.class);
        cellTable = new CellTable<ExplorerData>(Integer.MAX_VALUE, resources);
        cellTable.setWidth("100%");
        cellTable.setKeyboardSelectionHandler(new MyKeyboardSelectionHandler(cellTable));
        cellTable.addColumn(new Column<ExplorerData, ExplorerData>(explorerCell) {
            @Override
            public ExplorerData getValue(ExplorerData object) {
                return object;
            }
        });

        cellTable.setLoadingIndicator(null);
        cellTable.setSelectionModel(null);


        cellTable.setKeyboardSelectionPolicy(HasKeyboardSelectionPolicy.KeyboardSelectionPolicy.DISABLED);


        cellTable.getRowContainer().getStyle().setCursor(Style.Cursor.POINTER);

        treeModel = new ExplorerTreeModel(cellTable, spinnerSmall, dispatcher) {
            @Override
            protected void onDataChanged(final FetchExplorerDataResult result) {
                super.onDataChanged(result);
                selectionModel.setTreeStructure(result.getTreeStructure());
            }
        };

        final ScrollPanel scrollPanel = new ScrollPanel();
        scrollPanel.setWidth("100%");
        scrollPanel.setHeight("100%");
        scrollPanel.setWidget(cellTable);

        final FlowPanel flowPanel = new FlowPanel();
        flowPanel.getElement().getStyle().setPosition(Style.Position.RELATIVE);
        flowPanel.setWidth("100%");
        flowPanel.setHeight("100%");
        flowPanel.add(scrollPanel);
        flowPanel.add(spinnerSmall);

        initWidget(flowPanel);
    }

    protected void select(final ExplorerData selection) {
        final boolean doubleClick = doubleClickTest.test(selection);
        select(selection, doubleClick);
    }

    protected void select(final ExplorerData selection, final boolean doubleClick) {
        selectedItem = selection;
        if (selection != null) {
            ExplorerTreeSelectEvent.fire(ExplorerTickBoxTree.this, selection, doubleClick, false);
        }
    }

    public void setIncludedTypeSet(final Set<String> types) {
        treeModel.setIncludedTypeSet(types);
        refresh();
    }

    public void changeNameFilter(final String name) {
        treeModel.changeNameFilter(name);
    }

    public void refresh() {
        treeModel.refresh();
    }

    @CssResource.ImportedWithPrefix("gwt-CellTable")
    public interface ExplorerTreeStyle extends CellTable.Style {
        String DEFAULT_CSS = "stroom/explorer/client/view/ExplorerTree.css";
    }

    public interface ExplorerTreeResources extends CellTable.Resources {
        @Override
        @Source(ExplorerTreeStyle.DEFAULT_CSS)
        ExplorerTreeStyle cellTableStyle();
    }

    private class MyKeyboardSelectionHandler extends AbstractCellTable.CellTableKeyboardSelectionHandler<ExplorerData> {
        MyKeyboardSelectionHandler(AbstractCellTable<ExplorerData> table) {
            super(table);
        }

        @Override
        public void onCellPreview(CellPreviewEvent<ExplorerData> event) {
            final NativeEvent nativeEvent = event.getNativeEvent();
            final String type = nativeEvent.getType();
            final ExplorerData selected = getKeyBoardSelected();

            if ("mousedown".equals(type)) {
                final int x = nativeEvent.getClientX();
                final int y = nativeEvent.getClientY();
                final int button = nativeEvent.getButton();

                selectedItem = event.getValue();
                cellTable.setKeyboardSelectedRow(event.getIndex());

                if (selectedItem != null) {
                    if ((button & NativeEvent.BUTTON_RIGHT) != 0) {
                        ExplorerTreeSelectEvent.fire(ExplorerTickBoxTree.this, selectedItem, false, true);
                        ShowExplorerMenuEvent.fire(ExplorerTickBoxTree.this, selectedItem, x, y);
                    }
                }

            } else if ("click".equals(type)) {
                final int button = nativeEvent.getButton();

                selectedItem = event.getValue();

                if (selectedItem != null) {
                    if ((button & NativeEvent.BUTTON_LEFT) != 0) {
                        final Element element = event.getNativeEvent().getEventTarget().cast();

                        if (hasClassName(element, tickBoxClassName, 0, 5)) {
                            selectionModel.setSelected(selectedItem, !selectionModel.isSelected(selectedItem));
                            super.onCellPreview(event);
                            refresh();

                        } else if (HasNodeState.NodeState.LEAF.equals(selectedItem.getNodeState())) {
                            select(selectedItem);
                            super.onCellPreview(event);

                        } else if (hasClassName(element, expanderClassName, 0, 1)) {
                            select(selectedItem, false);
                            super.onCellPreview(event);

                            treeModel.toggleOpenState(selectedItem);
                            refresh();
                        }
                    }
                }

            } else if ("keydown".equals(type)) {
                final int keyCode = nativeEvent.getKeyCode();
                onKeyDown(keyCode);
                super.onCellPreview(event);
            } else {
                super.onCellPreview(event);
            }
        }
    }

    private boolean hasClassName(final Element element, final String className, final int depth, final int maxDepth) {
        if (element == null) {
            return false;
        }

        if (element.hasClassName(className)) {
            return true;
        }

        if (depth < maxDepth) {
            return hasClassName(element.getParentElement(), className, depth + 1, maxDepth);
        }

        return false;
    }

    private void onKeyDown(final int keyCode) {
        switch (keyCode) {
            case KeyCodes.KEY_LEFT:
                setOpenState(false);
                break;
            case KeyCodes.KEY_RIGHT:
                setOpenState(true);
                break;
            case KeyCodes.KEY_UP:
                moveSelection(-1);
                break;
            case KeyCodes.KEY_DOWN:
                moveSelection(+1);
                break;
            case KeyCodes.KEY_ENTER:
                selectCurrent();
                break;
        }
    }

    private void setOpenState(boolean open) {
        final ExplorerData selected = getKeyBoardSelected();
        if (selected != null) {
            if (open) {
                if (!treeModel.getOpenItems().contains(selected)) {
                    treeModel.toggleOpenState(selected);
                    refresh();
                }
            } else {
                if (treeModel.getOpenItems().contains(selected)) {
                    treeModel.toggleOpenState(selected);
                    refresh();
                }
            }
        }
    }

    private void moveSelection(int plus) {
        ExplorerData currentSelection = getKeyBoardSelected();
        if (currentSelection == null) {
            selectFirstItem();
        } else {
            final int index = getItemIndex(currentSelection);
            if (index == -1) {
                selectFirstItem();
            } else {
                final ExplorerData newSelection = cellTable.getVisibleItem(index + plus);
                if (newSelection != null) {
                    selectionModel.setSelected(newSelection, true);
                    selectedItem = newSelection;
                } else {
                    selectFirstItem();
                }
            }
        }
    }

    private void selectFirstItem() {
        final ExplorerData firstItem = cellTable.getVisibleItem(0);
        if (firstItem != null) {
            selectionModel.setSelected(firstItem, true);
        }
        selectedItem = firstItem;
    }

    private int getItemIndex(ExplorerData item) {
        final List<ExplorerData> items = cellTable.getVisibleItems();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                if (EqualsUtil.isEquals(items.get(i), item)) {
                    return i;
                }
            }
        }

        return -1;
    }

    private void selectCurrent() {
        final ExplorerData selected = getKeyBoardSelected();
        if (selected != null) {
            select(selected);
        }
    }

    private ExplorerData getKeyBoardSelected() {
        final int row = cellTable.getKeyboardSelectedRow();
        if (row >= 0) {
            return cellTable.getVisibleItem(row);
        }

        return null;
    }

    public ExplorerData getSelectedItem() {
        return selectedItem;
    }

    public ExplorerTreeModel getTreeModel() {
        return treeModel;
    }

    public TickBoxSelectionModel getSelectionModel() {
        return selectionModel;
    }

    public HandlerRegistration addSelectionHandler(final ExplorerTreeSelectEvent.Handler handler) {
        return addHandler(handler, ExplorerTreeSelectEvent.getType());
    }

    public HandlerRegistration addContextMenuHandler(final ShowExplorerMenuEvent.Handler handler) {
        return addHandler(handler, ShowExplorerMenuEvent.getType());
    }

    public void setFocus(final boolean focused) {
        cellTable.setFocus(focused);
    }
}
