/*
 * Copyright 2017 Crown Copyright
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

package stroom.explorer.client.presenter;

import stroom.data.table.client.CellTableViewImpl.DefaultResources;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.client.event.ShowExplorerMenuEvent;
import stroom.explorer.client.view.ExplorerCell;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNode.NodeState;
import stroom.util.shared.EqualsUtil;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.spinner.client.SpinnerSmall;
import stroom.widget.util.client.DoubleSelectTester;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectEvent;
import stroom.widget.util.client.MultiSelectEvent.Handler;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.Selection;
import stroom.widget.util.client.SelectionType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.CellTable.Resources;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy.KeyboardSelectionPolicy;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.MaxScrollPanel;

import java.util.List;
import java.util.Set;

public class ExplorerTree extends AbstractExplorerTree {

    public ExplorerTree(final RestFactory restFactory, final boolean allowMultiSelect) {
        super(restFactory, allowMultiSelect);
    }

    //
//    private final ExplorerTreeModel treeModel;
//    private final MultiSelectionModel<ExplorerNode> selectionModel;
//    private final MaxScrollPanel scrollPanel;
//    private final CellTable<ExplorerNode> cellTable;
//    private final DoubleSelectTester doubleClickTest = new DoubleSelectTester();
//    //    private final boolean allowMultiSelect;
//    private final String expanderClassName;
//
//    // Required for multiple selection using shift and control key modifiers.
//    private ExplorerNode multiSelectStart;
//    private List<ExplorerNode> rows;
//
//    ExplorerTree(final RestFactory restFactory, final boolean allowMultiSelect) {
////        this.allowMultiSelect = allowMultiSelect;
//
//        final SpinnerSmall spinnerSmall = new SpinnerSmall();
//        spinnerSmall.getElement().getStyle().setPosition(Style.Position.ABSOLUTE);
//        spinnerSmall.getElement().getStyle().setRight(5, Style.Unit.PX);
//        spinnerSmall.getElement().getStyle().setTop(5, Style.Unit.PX);
//
//        final ExplorerCell explorerCell = new ExplorerCell(null);
//        expanderClassName = explorerCell.getExpanderClassName();
//
//        final Resources resources = GWT.create(DefaultResources.class);
//        cellTable = new CellTable<>(Integer.MAX_VALUE, resources);
//        cellTable.getElement().setClassName("explorerTree");
//        cellTable.addColumn(new Column<ExplorerNode, ExplorerNode>(explorerCell) {
//            @Override
//            public ExplorerNode getValue(ExplorerNode object) {
//                return object;
//            }
//        });
//
//        cellTable.setLoadingIndicator(null);
//
//        final MultiSelectionModelImpl<ExplorerNode> multiSelectionModel = new MultiSelectionModelImpl<ExplorerNode>() {
//            @Override
//            public HandlerRegistration addSelectionHandler(final Handler handler) {
//                return addHandler(handler, MultiSelectEvent.getType());
//            }
//
//            @Override
//            protected void fireChange(final SelectionType selectionType) {
//                MultiSelectEvent.fire(ExplorerTree.this, selectionType);
//            }
//        };
//        cellTable.setSelectionModel(multiSelectionModel);
//        selectionModel = multiSelectionModel;
//        cellTable.setKeyboardSelectionPolicy(KeyboardSelectionPolicy.ENABLED);
//        cellTable.getRowContainer().getStyle().setCursor(Style.Cursor.POINTER);
//
//        treeModel = new ExplorerTreeModel(this, spinnerSmall, restFactory);
//
//        scrollPanel = new MaxScrollPanel();
//        scrollPanel.setWidget(cellTable);
//
//        final FlowPanel flowPanel = new FlowPanel();
//        flowPanel.getElement().getStyle().setPosition(Style.Position.RELATIVE);
//        flowPanel.setWidth("100%");
//        flowPanel.setHeight("100%");
//        flowPanel.add(scrollPanel);
//        flowPanel.add(spinnerSmall);
//
//        // We need to set this to prevent default keyboard behaviour.
//        cellTable.setKeyboardSelectionHandler(e -> {
////            GWT.log("KSH: " + e.getNativeEvent().getType() + " " + e.getValue());
//        });
//
//        cellTable.addCellPreviewHandler(e -> {
//            final NativeEvent nativeEvent = e.getNativeEvent();
//            final String type = nativeEvent.getType();
////            GWT.log("CELL PREVIEW: " + type + " " + e.getValue());
//
//            if ("keydown".equals(type) || "focus".equals(type)) {
//                final List<ExplorerNode> items = cellTable.getVisibleItems();
//                final ExplorerNode item = e.getValue();
//
//                if (items.size() > 0) {
//                    final int originalRow = cellTable.getKeyboardSelectedRow();
//                    final int keyCode = e.getNativeEvent().getKeyCode();
//
//                    if (keyCode == KeyCodes.KEY_UP) {
//                        int row = originalRow - 1;
//                        row = Math.max(0, row);
//                        if (row != originalRow) {
//                            cellTable.setKeyboardSelectedRow(row, true);
//                        }
//
//                    } else if (keyCode == KeyCodes.KEY_DOWN) {
//                        int row = originalRow + 1;
//                        row = Math.min(cellTable.getVisibleItemCount() - 1, row);
//                        if (row != originalRow) {
//                            cellTable.setKeyboardSelectedRow(row, true);
//                        }
//
//                    } else if (keyCode == KeyCodes.KEY_RIGHT) {
//                        treeModel.setItemOpen(item, true);
//
//                    } else if (keyCode == KeyCodes.KEY_LEFT) {
//                        treeModel.setItemOpen(item, false);
//
//                        // Default behaviour uses space for selection anyway.
////                    } else if (keyCode == KeyCodes.KEY_SPACE) {
////                        selectionModel.setSelected(item, !selectionModel.isSelected(item));
//
//                    } else if (keyCode == KeyCodes.KEY_ENTER) {
//                        selectionModel.setSelected(item, true);
//                        doSelect(item,
//                                new SelectionType(true,
//                                        false,
//                                        allowMultiSelect,
//                                        nativeEvent.getCtrlKey(),
//                                        nativeEvent.getShiftKey()));
//
//                    } else if (keyCode == KeyCodes.KEY_ALT) {
//                        final int row = cellTable.getKeyboardSelectedRow();
//                        final Element element = cellTable.getRowElement(row);
//                        if (element != null) {
//                            final int x = element.getAbsoluteRight();
//                            final int y = element.getAbsoluteTop();
//
//                            // If the item clicked is already selected then don't change the selection.
//                            if (!selectionModel.isSelected(item)) {
//                                // Change the selection.
//                                doSelect(item,
//                                        new SelectionType(false,
//                                                true,
//                                                false,
//                                                nativeEvent.getCtrlKey(),
//                                                nativeEvent.getShiftKey()));
//                            }
//
//                            final Runnable closeHandler = () ->
//                                    cellTable.setKeyboardSelectedRow(row, true);
//                            ShowExplorerMenuEvent.fire(
//                                    ExplorerTree.this,
//                                    selectionModel,
//                                    closeHandler,
//                                    new PopupPosition(x, y));
//                        }
//                    }
//                }
//
//
//            } else if ("mousedown".equals(type)) {
//                // We set focus here so that we can use the keyboard to navigate once we have focus.
//                cellTable.setFocus(true);
//                final int row = cellTable.getVisibleItems().indexOf(e.getValue());
//                if (row >= 0) {
//                    cellTable.setKeyboardSelectedRow(row);
//                }
//
//                final int x = nativeEvent.getClientX();
//                final int y = nativeEvent.getClientY();
//
//                if (MouseUtil.isSecondary(nativeEvent)) {
//                    final ExplorerNode selectedItem = e.getValue();
//                    // If the item clicked is already selected then don't change the selection.
//                    if (!selectionModel.isSelected(selectedItem)) {
//                        // Change the selection.
//                        doSelect(selectedItem,
//                                new SelectionType(false,
//                                        true,
//                                        false,
//                                        nativeEvent.getCtrlKey(),
//                                        nativeEvent.getShiftKey()));
//                    }
//
//                    final Runnable closeHandler = () ->
//                            cellTable.setKeyboardSelectedRow(row, true);
//                    ShowExplorerMenuEvent.fire(
//                            ExplorerTree.this,
//                            selectionModel,
//                            closeHandler,
//                            new PopupPosition(x, y));
//
//                } else if (MouseUtil.isPrimary(nativeEvent)) {
//                    final ExplorerNode selectedItem = e.getValue();
//                    if (selectedItem != null && MouseUtil.isPrimary(nativeEvent)) {
//                        if (NodeState.LEAF.equals(selectedItem.getNodeState())) {
//                            final boolean doubleClick = doubleClickTest.test(selectedItem);
//                            doSelect(selectedItem,
//                                    new SelectionType(doubleClick,
//                                            false,
//                                            allowMultiSelect,
//                                            nativeEvent.getCtrlKey(),
//                                            nativeEvent.getShiftKey()));
//                        } else {
//                            final Element element = nativeEvent.getEventTarget().cast();
//                            final String className = element.getClassName();
//
//                            // Expander
//                            if ((ElementUtil.hasClassName(element, expanderClassName, 0, 5))) {
//                                treeModel.toggleOpenState(selectedItem);
//                            } else {
//                                final boolean doubleClick = doubleClickTest.test(selectedItem);
//                                doSelect(selectedItem,
//                                        new SelectionType(doubleClick,
//                                                false,
//                                                allowMultiSelect,
//                                                nativeEvent.getCtrlKey(),
//                                                nativeEvent.getShiftKey()));
//                            }
//                        }
//                    }
//                }
//
//
////            } else if ("click".equals(type)) {
////                final ExplorerNode item = e.getValue();
////                final int row = cellTable.getVisibleItems().indexOf(item);
////                cellTable.setKeyboardSelectedRow(row);
////                selectionModel.setSelected(item, true);
//////                mouseOverRow = -1;
////
//////                if (item instanceof CommandMenuItem) {
//////                    execute((CommandMenuItem) item);
//////                } else {
//////                    showSubMenu((MenuItem) item, true);
//////                }
////
//            }
//        });
//
//
//        initWidget(flowPanel);
//    }
//
//    @Override
//    void setData(final List<ExplorerNode> rows) {
//        this.rows = rows;
//        cellTable.setRowData(0, rows);
//        cellTable.setRowCount(rows.size(), true);
//    }
//
//    public void setIncludedTypeSet(final Set<String> types) {
//        treeModel.setIncludedTypeSet(types);
//        refresh();
//    }
//
//    public void changeNameFilter(final String name) {
//        treeModel.changeNameFilter(name);
//    }
//
//    public void refresh() {
//        treeModel.refresh();
//    }
//
////    private void onKeyDown(final int keyCode) {
////        switch (keyCode) {
//////            case KeyCodes.KEY_LEFT:
//////                setOpenState(false);
//////                break;
//////            case KeyCodes.KEY_RIGHT:
//////                setOpenState(true);
//////                break;
//////            case KeyCodes.KEY_UP:
//////                moveSelection(-1);
//////                break;
//////            case KeyCodes.KEY_DOWN:
//////                moveSelection(+1);
//////                break;
////            case KeyCodes.KEY_ENTER:
////                final ExplorerNode selected = selectionModel.getSelected();
////                if (selected != null) {
////                    final boolean doubleClick = doubleClickTest.test(selected);
////                    doSelect(selected, new SelectionType(doubleClick, false));
////                }
////                break;
////        }
////    }
//
////    private void setOpenState(boolean open) {
////        treeModel.setItemOpen(selectionModel.getSelected(), open);
////    }
////
////    private void moveSelection(int plus) {
////        ExplorerNode currentSelection = selectionModel.getSelected();
////        if (currentSelection == null) {
////            selectFirstItem();
////        } else {
////            final int index = getItemIndex(currentSelection);
////            if (index == -1) {
////                selectFirstItem();
////            } else {
////                final ExplorerNode newSelection = cellTable.getVisibleItem(index + plus);
////                if (newSelection != null) {
////                    setSelectedItem(newSelection);
////                } else {
////                    selectFirstItem();
////                }
////            }
////        }
////    }
////
////    private void selectFirstItem() {
////        final ExplorerNode firstItem = cellTable.getVisibleItem(0);
////        setSelectedItem(firstItem);
////    }
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
//
//    @Override
//    protected void setInitialSelectedItem(final ExplorerNode selection) {
//        selectionModel.clear();
//        setSelectedItem(selection);
//        scrollSelectedIntoView();
//    }
//
//    private void scrollSelectedIntoView() {
//        final ExplorerNode selected = selectionModel.getSelected();
//        if (selected != null) {
//            final int index = getItemIndex(selected);
//            if (index > 0) {
//                final TableRowElement tableRowElement = cellTable.getRowElement(index);
//                tableRowElement.scrollIntoView();
//                scrollPanel.scrollToLeft();
//            }
//        }
//    }
//
//    protected void setSelectedItem(ExplorerNode selection) {
//        if (treeModel.isIncludeNullSelection() && selection == null) {
//            selection = ExplorerTreeModel.NULL_SELECTION;
//        }
//
//        doSelect(selection, new SelectionType(false, false));
//    }
//
//    protected void doSelect(final ExplorerNode row, final SelectionType selectionType) {
//        final Selection<ExplorerNode> selection = selectionModel.getSelection();
//
//        if (row == null) {
//            multiSelectStart = null;
//            selection.clear();
//        } else if (selectionType.isAllowMultiSelect() && selectionType.isShiftPressed() && multiSelectStart != null) {
//            // If control isn't pressed as well as shift then we are selecting a new range so clear.
//            if (!selectionType.isControlPressed()) {
//                selection.clear();
//            }
//
//            final int index1 = rows.indexOf(multiSelectStart);
//            final int index2 = rows.indexOf(row);
//            if (index1 != -1 && index2 != -1) {
//                final int start = Math.min(index1, index2);
//                final int end = Math.max(index1, index2);
//                for (int i = start; i <= end; i++) {
//                    selection.setSelected(rows.get(i), true);
//                }
//            } else if (selectionType.isControlPressed()) {
//                multiSelectStart = row;
//                selection.setSelected(row, !selection.isSelected(row));
//            } else {
//                multiSelectStart = row;
//                selection.setSelected(row);
//            }
//        } else if (selectionType.isAllowMultiSelect() && selectionType.isControlPressed()) {
//            multiSelectStart = row;
//            selection.setSelected(row, !selection.isSelected(row));
//        } else {
//            multiSelectStart = row;
//            selection.setSelected(row);
//        }
//
//        selectionModel.setSelection(selection, selectionType);
//        MultiSelectEvent.fire(ExplorerTree.this, selectionType);
//    }
//
//    public ExplorerTreeModel getTreeModel() {
//        return treeModel;
//    }
//
//    public MultiSelectionModel<ExplorerNode> getSelectionModel() {
//        return selectionModel;
//    }
//
//    public HandlerRegistration addContextMenuHandler(final ShowExplorerMenuEvent.Handler handler) {
//        return addHandler(handler, ShowExplorerMenuEvent.getType());
//    }
//
//    public void setFocus(final boolean focused) {
//        cellTable.setFocus(focused);
//    }
//
////    private class MySelectionEventManager extends AbstractCellTable.CellTableKeyboardSelectionHandler<ExplorerNode> {
////
////        MySelectionEventManager(AbstractCellTable<ExplorerNode> table) {
////            super(table);
////        }
////
////        @Override
////        public void onCellPreview(CellPreviewEvent<ExplorerNode> event) {
////            final NativeEvent nativeEvent = event.getNativeEvent();
////            final String type = nativeEvent.getType();
////
////            if ("mousedown".equals(type)) {
////                // We set focus here so that we can use the keyboard to navigate once we have focus.
////                cellTable.setFocus(true);
////
////                final int x = nativeEvent.getClientX();
////                final int y = nativeEvent.getClientY();
////                final int button = nativeEvent.getButton();
////
////                if (MouseUtil.isSecondary(event)) {
////                    final ExplorerNode selectedItem = event.getValue();
////                    // If the item clicked is already selected then don't change the selection.
////                    if (!selectionModel.isSelected(selectedItem)) {
////                        // Change the selection.
////                        doSelect(selectedItem,
////                                new SelectionType(false,
////                                        true,
////                                        false,
////                                        event.getNativeEvent().getCtrlKey(),
////                                        event.getNativeEvent().getShiftKey()));
////                    }
////
////                    ShowExplorerMenuEvent.fire(ExplorerTree.this, selectionModel, x, y);
////
////                } else if (MouseUtil.isPrimaryClick(nativeEvent)) {
////                    final ExplorerNode selectedItem = event.getValue();
////                    if (selectedItem != null && MouseUtil.isPrimaryClick(nativeEvent)) {
////                        if (NodeState.LEAF.equals(selectedItem.getNodeState())) {
////                            final boolean doubleClick = doubleClickTest.test(selectedItem);
////                            doSelect(selectedItem,
////                                    new SelectionType(doubleClick,
////                                            false,
////                                            allowMultiSelect,
////                                            event.getNativeEvent().getCtrlKey(),
////                                            event.getNativeEvent().getShiftKey()));
////                            super.onCellPreview(event);
////                        } else {
////                            final Element element = event.getNativeEvent().getEventTarget().cast();
////                            final String className = element.getClassName();
////
////                            // Expander
////                            if ((className != null && className.contains(expanderClassName))
////                                    || (element.getParentElement().getClassName() != null
////                                    && element.getParentElement().getClassName().contains(expanderClassName))) {
////                                super.onCellPreview(event);
////
////                                treeModel.toggleOpenState(selectedItem);
////                            } else {
////                                final boolean doubleClick = doubleClickTest.test(selectedItem);
////                                doSelect(selectedItem,
////                                        new SelectionType(doubleClick,
////                                                false,
////                                                allowMultiSelect,
////                                                event.getNativeEvent().getCtrlKey(),
////                                                event.getNativeEvent().getShiftKey()));
////                                super.onCellPreview(event);
////                            }
////                        }
////                    }
////                }
////            } else if ("keydown".equals(type)) {
////                final int keyCode = nativeEvent.getKeyCode();
////                onKeyDown(keyCode);
//////                super.onCellPreview(event);
////            } else {
////                super.onCellPreview(event);
////            }
////        }
////    }
}
