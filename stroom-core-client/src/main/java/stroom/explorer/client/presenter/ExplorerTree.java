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
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.explorer.client.event.ExplorerTreeSelectEvent;
import stroom.explorer.client.event.ShowExplorerMenuEvent;
import stroom.explorer.client.view.ExplorerCell;
import stroom.explorer.shared.ExplorerData;
import stroom.util.shared.EqualsUtil;
import stroom.util.shared.HasNodeState;
import stroom.widget.spinner.client.SpinnerSmall;
import stroom.widget.util.client.DoubleSelectTest;
import stroom.widget.util.client.MySingleSelectionModel;

import java.util.List;
import java.util.Set;

public class ExplorerTree extends Composite {
    private final ExplorerTreeModel treeModel;
    private final MySingleSelectionModel<ExplorerData> selectionModel;
    private final CellTable<ExplorerData> cellTable;
    private final DoubleSelectTest doubleClickTest = new DoubleSelectTest();

    private String expanderClassName;
    ExplorerData selectedItem;

    public ExplorerTree(final ClientDispatchAsync dispatcher) {
        final SpinnerSmall spinnerSmall = new SpinnerSmall();
        spinnerSmall.getElement().getStyle().setPosition(Style.Position.ABSOLUTE);
        spinnerSmall.getElement().getStyle().setRight(5, Style.Unit.PX);
        spinnerSmall.getElement().getStyle().setTop(5, Style.Unit.PX);

        selectionModel = new MySingleSelectionModel<ExplorerData>();

        final ExplorerCell explorerCell = new ExplorerCell();
        expanderClassName = explorerCell.getExpanderClassName();

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
        cellTable.setSelectionModel(selectionModel);


        cellTable.setKeyboardSelectionPolicy(HasKeyboardSelectionPolicy.KeyboardSelectionPolicy.ENABLED);

//        cellTable.addDomHandler(new KeyDownHandler() {
//            @Override
//            public void onKeyDown(final KeyDownEvent event) {
//                ExplorerTree.this.onKeyDown(event.getNativeKeyCode());
//            }
//        }, KeyDownEvent.getType());

//        cellTable.sinkEvents(Event.ONKEYDOWN);


        cellTable.getRowContainer().getStyle().setCursor(Style.Cursor.POINTER);

        treeModel = new ExplorerTreeModel(cellTable, spinnerSmall, dispatcher);

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
            ExplorerTreeSelectEvent.fire(ExplorerTree.this, selection, doubleClick, false);
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
            final ExplorerData selected = selectionModel.getSelectedObject();

            if ("mousedown".equals(type)) {
                final int x = nativeEvent.getClientX();
                final int y = nativeEvent.getClientY();
                final int button = nativeEvent.getButton();

                selectedItem = event.getValue();

                if (selectedItem != null) {
                    if ((button & NativeEvent.BUTTON_RIGHT) != 0) {
//                        selectionModel.setSelected(selectedItem, true);
                        cellTable.setKeyboardSelectedRow(event.getIndex());
                        ExplorerTreeSelectEvent.fire(ExplorerTree.this, selectedItem, false, true);
                        ShowExplorerMenuEvent.fire(ExplorerTree.this, selectedItem, x, y);
//                        super.onCellPreview(event);
                    }

                }

            } else if ("click".equals(type)) {
                final int button = nativeEvent.getButton();

                selectedItem = event.getValue();

                if (selectedItem != null) {
                    if ((button & NativeEvent.BUTTON_LEFT) != 0) {
                        if (HasNodeState.NodeState.LEAF.equals(selectedItem.getNodeState())) {
                            select(selectedItem);
                            super.onCellPreview(event);
                        } else {
                            final Element element = event.getNativeEvent().getEventTarget().cast();
                            final String className = element.getClassName();
                            if ((className != null && className.equals(expanderClassName)) || (element.getParentElement().getClassName() != null && element.getParentElement().getClassName().equals(expanderClassName))) {
//                                // If we are collapsing a node then select the node being collapsed to ensure an appropriate item is always selected.
//                                if (HasNodeState.NodeState.OPEN.equals(selectedItem.getNodeState())) {
//                                    select(selectedItem);
//                                }
                                select(selectedItem, false);
                                super.onCellPreview(event);

                                treeModel.toggleOpenState(selectedItem);
                                refresh();
                            } else {
                                select(selectedItem);
                                super.onCellPreview(event);
                            }
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
        final ExplorerData selected = selectionModel.getSelectedObject();
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
        ExplorerData currentSelection = selectionModel.getSelectedObject();
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
        final ExplorerData selected = selectionModel.getSelectedObject();
        if (selected != null) {
            select(selected);
        }
    }

    public ExplorerData getSelectedItem() {
        return selectedItem;
    }

    public ExplorerTreeModel getTreeModel() {
        return treeModel;
    }

    public MySingleSelectionModel<ExplorerData> getSelectionModel() {
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
