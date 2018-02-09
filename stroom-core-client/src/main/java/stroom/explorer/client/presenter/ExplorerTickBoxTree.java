/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.view.client.CellPreviewEvent;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.explorer.client.event.ShowExplorerMenuEvent;
import stroom.explorer.client.view.ExplorerTickBoxCell;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.util.shared.HasNodeState;
import stroom.widget.spinner.client.SpinnerSmall;
import stroom.widget.util.client.MultiSelectEvent;

import java.util.List;

public class ExplorerTickBoxTree extends AbstractExplorerTree {
    private final ExplorerTreeModel treeModel;
    private final TickBoxSelectionModel selectionModel;
    private final CellTable<ExplorerNode> cellTable;

    private String expanderClassName;
    private String tickBoxClassName;

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
        cellTable = new CellTable<>(Integer.MAX_VALUE, resources);
        cellTable.setWidth("100%");
        cellTable.addColumn(new Column<ExplorerNode, ExplorerNode>(explorerCell) {
            @Override
            public ExplorerNode getValue(ExplorerNode object) {
                return object;
            }
        });

        cellTable.setLoadingIndicator(null);
        cellTable.setSelectionModel(null, new MySelectionEventManager(cellTable));
        cellTable.setKeyboardSelectionPolicy(HasKeyboardSelectionPolicy.KeyboardSelectionPolicy.DISABLED);

        cellTable.getRowContainer().getStyle().setCursor(Style.Cursor.POINTER);

        treeModel = new ExplorerTreeModel(this, spinnerSmall, dispatcher) {
            @Override
            protected void onDataChanged(final FetchExplorerNodeResult result) {
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

    @Override
    void setData(final List<ExplorerNode> rows) {
        cellTable.setRowData(0, rows);
        cellTable.setRowCount(rows.size(), true);
    }

//    protected void select(final ExplorerNode selection) {
//        final boolean doubleClick = doubleClickTest.test(selection);
//        select(selection, doubleClick);
//    }
//
//    protected void select(final ExplorerNode selection, final boolean doubleClick) {
//        selectedItem = selection;
//        if (selection != null) {
//            ExplorerTreeSelectEvent.fire(ExplorerTickBoxTree.this, selection, doubleClick, false);
//        }
//    }
//
//    public void setIncludedTypeSet(final Set<String> types) {
//        treeModel.setIncludedTypeSet(types);
//        refresh();
//    }

    public void changeNameFilter(final String name) {
        treeModel.changeNameFilter(name);
    }

    public void refresh() {
        treeModel.refresh();
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
//            case KeyCodes.KEY_UP:
//                moveSelection(-1);
//                break;
//            case KeyCodes.KEY_DOWN:
//                moveSelection(+1);
//                break;
            case KeyCodes.KEY_ENTER:
                selectCurrent();
                break;
        }
    }

    private void setOpenState(boolean open) {
        final ExplorerNode selected = getKeyBoardSelected();
        treeModel.setItemOpen(selected, open);
    }

    private void selectCurrent() {
        final ExplorerNode selected = getKeyBoardSelected();
        if (selected != null) {
            toggleSelection(selected);
        }
    }

    private ExplorerNode getKeyBoardSelected() {
        final int row = cellTable.getKeyboardSelectedRow();
        if (row >= 0) {
            return cellTable.getVisibleItem(row);
        }

        return null;
    }

    @Override
    void setInitialSelectedItem(final ExplorerNode selection) {
//        doSelect(selection);
    }

//    private void moveSelection(int plus) {
//        ExplorerNode currentSelection = getKeyBoardSelected();
//        if (currentSelection == null) {
//            selectFirstItem();
//        } else {
//            final int index = getItemIndex(currentSelection);
//            if (index == -1) {
//                selectFirstItem();
//            } else {
//                final ExplorerNode newSelection = cellTable.getVisibleItem(index + plus);
//                if (newSelection != null) {
//                    setSelectedItem(newSelection);
//                } else {
//                    selectFirstItem();
//                }
//            }
//        }
//    }
//
//    private void selectFirstItem() {
//        final ExplorerNode firstItem = cellTable.getVisibleItem(0);
//        setSelectedItem(firstItem);
//    }
//
//    private int getItemIndex(ExplorerNode item) {
//        final List<ExplorerNode> items = cellTable.getVisibleItems();
//        if (items != null) {
//            for (int i = 0; i < items.size(); i++) {
//                if (EqualsUtil.isEquals(items.get(i), item)) {
//                    return i;
//                }
//            }
//        }
//
//        return -1;
//    }

    private void toggleSelection(final ExplorerNode selection) {
        if (selection != null) {
            selectionModel.setSelected(selection, !selectionModel.isSelected(selection));
//        } else {
//            selectionModel.clear();
        }
//        ExplorerTreeSelectEvent.fire(ExplorerTickBoxTree.this, selectionModel, false, false);
    }

    public ExplorerTreeModel getTreeModel() {
        return treeModel;
    }

    public TickBoxSelectionModel getSelectionModel() {
        return selectionModel;
    }

//    protected void setSelectedItem(final ExplorerNode selection) {
//        doSelect(selection, false, false);
//    }
//
//    private void setSelectedItemWithDoubleClickTest(final ExplorerNode selection) {
//        final boolean doubleClick = doubleClickTest.test(selection);
//        doSelect(selection, doubleClick, false);
//    }
//
//    protected void doSelect(final ExplorerNode selection, final boolean doubleClick, final boolean rightClick) {
//        if (selection != null) {
//            selectionModel.setSelected(selection, true);
////        } else {
////            selectionModel.clear();
//        }
//        ExplorerTreeSelectEvent.fire(ExplorerTickBoxTree.this, selection, doubleClick, rightClick);
//    }

    public HandlerRegistration addSelectionHandler(final MultiSelectEvent.Handler handler) {
        return addHandler(handler, MultiSelectEvent.getType());
    }
//
//    public ExplorerNode getSelectedItem() {
//        return selectedItem;
//    }

    public HandlerRegistration addContextMenuHandler(final ShowExplorerMenuEvent.Handler handler) {
        return addHandler(handler, ShowExplorerMenuEvent.getType());
    }

    public void setFocus(final boolean focused) {
        cellTable.setFocus(focused);
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

    private class MySelectionEventManager extends AbstractCellTable.CellTableKeyboardSelectionHandler<ExplorerNode> {
        MySelectionEventManager(AbstractCellTable<ExplorerNode> table) {
            super(table);
        }

        @Override
        public void onCellPreview(CellPreviewEvent<ExplorerNode> event) {
            final NativeEvent nativeEvent = event.getNativeEvent();
            final String type = nativeEvent.getType();

            if ("click".equals(type)) {
                final int button = nativeEvent.getButton();

                final ExplorerNode selectedItem = event.getValue();

                if (selectedItem != null) {
                    if ((button & NativeEvent.BUTTON_LEFT) != 0) {
                        final Element element = event.getNativeEvent().getEventTarget().cast();

                        if (hasClassName(element, tickBoxClassName, 0, 5)) {
                            toggleSelection(selectedItem);
                            super.onCellPreview(event);
                            refresh();

                        } else if (HasNodeState.NodeState.LEAF.equals(selectedItem.getNodeState())) {
                            toggleSelection(selectedItem);
                            super.onCellPreview(event);

                        } else if (hasClassName(element, expanderClassName, 0, 1)) {
                            super.onCellPreview(event);

                            treeModel.toggleOpenState(selectedItem);
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
}
