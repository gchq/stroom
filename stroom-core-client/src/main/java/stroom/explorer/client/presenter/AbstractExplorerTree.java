/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.data.table.client.MyCellTable;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.client.event.ShowExplorerMenuEvent;
import stroom.explorer.client.view.ExplorerCell;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.explorer.shared.NodeFlag;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.util.client.AbstractSelectionEventManager;
import stroom.widget.util.client.DoubleSelectTester;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectEvent;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.Selection;
import stroom.widget.util.client.SelectionType;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.cellview.client.AbstractHasData;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.MaxScrollPanel;
import com.google.gwt.view.client.CellPreviewEvent;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public abstract class AbstractExplorerTree extends Composite implements Focus {

    private final ExplorerTreeModel treeModel;
    private final MultiSelectionModelImpl<ExplorerNode> selectionModel;
    private final MaxScrollPanel scrollPanel;
    private final CellTable<ExplorerNode> cellTable;
    private final DoubleSelectTester doubleClickTest = new DoubleSelectTester();
    private final boolean allowMultiSelect;
    private final String expanderClassName;
    private final ExplorerCell explorerCell;

    // Required for multiple selection using shift and control key modifiers.
    private ExplorerNode multiSelectStart;
    private List<ExplorerNode> rows;
    private boolean showAlerts;
    private Consumer<FetchExplorerNodeResult> changeHandler = null;

    AbstractExplorerTree(final RestFactory restFactory,
                         final TaskMonitorFactory taskMonitorFactory,
                         final boolean allowMultiSelect,
                         final boolean showAlerts) {
        this.allowMultiSelect = allowMultiSelect;
        this.showAlerts = showAlerts;

        explorerCell = new ExplorerCell(getTickBoxSelectionModel(), showAlerts);
        expanderClassName = explorerCell.getExpanderClassName();

        cellTable = new MyCellTable<>(Integer.MAX_VALUE);
        cellTable.getElement().setClassName("explorerTree");
        cellTable.addColumn(new Column<ExplorerNode, ExplorerNode>(explorerCell) {
            @Override
            public ExplorerNode getValue(final ExplorerNode object) {
                return object;
            }
        });
        selectionModel = getSelectionModel();
        final ExplorerTreeSelectionEventManager selectionEventManager =
                new ExplorerTreeSelectionEventManager(cellTable);
        cellTable.setSelectionModel(selectionModel, selectionEventManager);

        treeModel = new ExplorerTreeModel(this, restFactory, taskMonitorFactory) {
            @Override
            protected void onDataChanged(final FetchExplorerNodeResult result) {
                onData(result);
                super.onDataChanged(result);

                if (changeHandler != null) {
                    changeHandler.accept(result);
                }
            }
        };
        treeModel.setShowAlerts(showAlerts);

        scrollPanel = new MaxScrollPanel();
        scrollPanel.setWidget(cellTable);

        final FlowPanel flowPanel = new FlowPanel();
        flowPanel.getElement().getStyle().setPosition(Style.Position.RELATIVE);
        flowPanel.setWidth("100%");
        flowPanel.setHeight("100%");
        flowPanel.add(scrollPanel);

        initWidget(flowPanel);
    }

    @Override
    public void focus() {
//        GWT.log("focus");
//
//        int row = 0;
//        if (selectionModel.getSelected() != null) {
//            int index = cellTable.getVisibleItems().indexOf(selectionModel.getSelected());
//            if (index >= 0) {
//                row = index;
//            }
//        }
//        cellTable.setKeyboardSelectedRow(row, true);
        cellTable.setFocus(true);
    }

    abstract MultiSelectionModelImpl<ExplorerNode> getSelectionModel();

    void onData(final FetchExplorerNodeResult result) {
    }

    void addChangeHandler(final Consumer<FetchExplorerNodeResult> changeHandler) {
        this.changeHandler = changeHandler;
    }

    void setData(final List<ExplorerNode> rows) {
        this.rows = rows;
        cellTable.setRowData(0, rows);
        cellTable.setRowCount(rows.size(), true);
    }

    public void setIncludedTypeSet(final Set<String> types) {
        treeModel.setIncludedTypeSet(types);
        refresh();
    }

    public void changeNameFilter(final String name) {
        treeModel.changeNameFilter(name);
    }

    public void setShowAlerts(final boolean showAlerts) {
        this.showAlerts = showAlerts;
        treeModel.setShowAlerts(showAlerts);
        explorerCell.setShowAlerts(showAlerts);
        treeModel.refresh();
    }

    public void refresh() {
        treeModel.refresh();
    }

    private int getItemIndex(final ExplorerNode item) {
        final List<ExplorerNode> items = cellTable.getVisibleItems();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                if (Objects.equals(items.get(i), item)) {
                    return i;
                }
            }
        }

        return -1;
    }

    protected void setInitialSelectedItem(final ExplorerNode selection) {
        if (selectionModel != null) {
            selectionModel.clear();
        }
        setSelectedItem(selection);
        scrollSelectedIntoView();
    }

    private void scrollSelectedIntoView() {
        if (selectionModel != null) {
            final ExplorerNode selected = selectionModel.getSelected();
            if (selected != null) {
                final int index = getItemIndex(selected);
                if (index > 0) {
                    final TableRowElement tableRowElement = cellTable.getRowElement(index);
                    ElementUtil.scrollIntoViewNearest(tableRowElement);
//                    tableRowElement.scrollIntoView();
//                    scrollPanel.scrollToLeft();
                }
            }
        }
    }

    void setSelectedItem(ExplorerNode selection) {
        if (treeModel.isIncludeNullSelection() && selection == null) {
            selection = ExplorerTreeModel.NULL_SELECTION;
        }

        doSelect(selection, new SelectionType());
    }

    void doSelect(final ExplorerNode row, final SelectionType selectionType) {
        if (selectionModel != null) {
            final Selection<ExplorerNode> selection = selectionModel.getSelection();

            if (allowMultiSelect) {
                if (row == null) {
                    multiSelectStart = null;
                    selection.clear();
                } else if (selectionType.isAllowMultiSelect() &&
                        selectionType.isShiftPressed() &&
                        multiSelectStart != null) {
                    // If control isn't pressed as well as shift then we are selecting a new range so clear.
                    if (!selectionType.isControlPressed()) {
                        selection.clear();
                    }

                    final int index1 = rows.indexOf(multiSelectStart);
                    final int index2 = rows.indexOf(row);
                    if (index1 != -1 && index2 != -1) {
                        final int start = Math.min(index1, index2);
                        final int end = Math.max(index1, index2);
                        for (int i = start; i <= end; i++) {
                            selection.setSelected(rows.get(i), true);
                        }
                    } else if (selectionType.isControlPressed()) {
                        multiSelectStart = row;
                        selection.setSelected(row, !selection.isSelected(row));
                    } else {
                        multiSelectStart = row;
                        selection.setSelected(row);
                    }
                } else if (selectionType.isAllowMultiSelect() && selectionType.isControlPressed()) {
                    multiSelectStart = row;
                    selection.setSelected(row, !selection.isSelected(row));
                } else {
                    multiSelectStart = row;
                    selection.setSelected(row);
                }

                selectionModel.setSelection(selection, selectionType);

            } else if (!selectionModel.isSelected(row)) {
                selectionModel.clear();
                selectionModel.setSelected(row);
            }

            MultiSelectEvent.fire(AbstractExplorerTree.this, selectionType);
        }
    }

    public ExplorerTreeModel getTreeModel() {
        return treeModel;
    }

    public HandlerRegistration addContextMenuHandler(final ShowExplorerMenuEvent.Handler handler) {
        return addHandler(handler, ShowExplorerMenuEvent.getType());
    }

    public void setFocus(final boolean focused) {
        cellTable.setFocus(focused);
    }

    TickBoxSelectionModel getTickBoxSelectionModel() {
        return null;
    }

    void showMenu(final CellPreviewEvent<ExplorerNode> e) {
        final NativeEvent nativeEvent = e.getNativeEvent();
        final ExplorerNode item = e.getValue();

        final int row = cellTable.getVisibleItems().indexOf(e.getValue());
        if (row >= 0) {
            cellTable.setKeyboardSelectedRow(row);
        }

        final PopupPosition popupPosition;
        if (BrowserEvents.MOUSEDOWN.equals(e.getNativeEvent().getType())) {
            popupPosition = new PopupPosition(nativeEvent.getClientX(), nativeEvent.getClientY());
        } else {
            final Element element = cellTable.getRowElement(row);
            if (element != null) {
                popupPosition = new PopupPosition(element.getAbsoluteRight(), element.getAbsoluteTop());
            } else {
                popupPosition = null;
            }
        }

        if (selectionModel != null && popupPosition != null) {
            // If the item clicked is already selected then don't change the selection.
            if (!selectionModel.isSelected(item)) {
                // Change the selection.
                doSelect(item,
                        new SelectionType(false,
                                true,
                                false,
                                nativeEvent.getCtrlKey(),
                                nativeEvent.getShiftKey()));
            }

            cellTable.setKeyboardSelectedRow(row, true);
            Scheduler.get().scheduleDeferred(() -> {
                ShowExplorerMenuEvent.fire(
                        AbstractExplorerTree.this,
                        selectionModel,
                        popupPosition);
            });
        }
    }

    void selectAll() {
    }


    // --------------------------------------------------------------------------------


    private class ExplorerTreeSelectionEventManager extends AbstractSelectionEventManager<ExplorerNode> {

        public ExplorerTreeSelectionEventManager(final AbstractHasData<ExplorerNode> cellTable) {
            super(cellTable);
        }

        @Override
        protected void onMoveRight(final CellPreviewEvent<ExplorerNode> e) {
            if (e.getValue().hasNodeFlags(NodeFlag.LEAF)) {
                showMenu(e);
            } else {
                treeModel.setItemOpen(e.getValue(), true);
            }
        }

        @Override
        protected void onMoveLeft(final CellPreviewEvent<ExplorerNode> e) {
            treeModel.setItemOpen(e.getValue(), false);
        }

        @Override
        protected void onExecute(final CellPreviewEvent<ExplorerNode> e) {
            final NativeEvent nativeEvent = e.getNativeEvent();
            final ExplorerNode item = e.getValue();
            doSelect(item,
                    new SelectionType(true,
                            false,
                            allowMultiSelect,
                            nativeEvent.getCtrlKey(),
                            nativeEvent.getShiftKey()));
        }

        @Override
        protected void onSelect(final CellPreviewEvent<ExplorerNode> e) {
            final NativeEvent nativeEvent = e.getNativeEvent();
            // Change the selection.
            doSelect(e.getValue(),
                    new SelectionType(false,
                            false,
                            true,
                            nativeEvent.getCtrlKey(),
                            nativeEvent.getShiftKey()));
        }

        @Override
        protected void onMenu(final CellPreviewEvent<ExplorerNode> e) {
            showMenu(e);
        }

        @Override
        protected void onSelectAll(final CellPreviewEvent<ExplorerNode> e) {
            selectAll();
        }

        @Override
        protected void onMouseDown(final CellPreviewEvent<ExplorerNode> e) {
            final NativeEvent nativeEvent = e.getNativeEvent();
            final int row = cellTable.getVisibleItems().indexOf(e.getValue());
            if (row >= 0) {
                cellTable.setKeyboardSelectedRow(row, true);
            } else {
                cellTable.setKeyboardSelectedRow(cellTable.getKeyboardSelectedRow(), true);
            }

            if (MouseUtil.isSecondary(nativeEvent)) {
                showMenu(e);

            } else if (MouseUtil.isPrimary(nativeEvent)) {
                select(e);
            }
        }

        private void select(final CellPreviewEvent<ExplorerNode> e) {
            final NativeEvent nativeEvent = e.getNativeEvent();
            final ExplorerNode selectedItem = e.getValue();
            if (selectedItem != null && MouseUtil.isPrimary(nativeEvent)) {
                if (selectedItem.hasNodeFlag(NodeFlag.LEAF)) {
                    final boolean doubleClick = doubleClickTest.test(selectedItem);
                    doSelect(selectedItem,
                            new SelectionType(doubleClick,
                                    false,
                                    allowMultiSelect,
                                    nativeEvent.getCtrlKey(),
                                    nativeEvent.getShiftKey()));
                } else {
                    final Element element = nativeEvent.getEventTarget().cast();

                    // Expander
                    if ((ElementUtil.hasClassName(element, expanderClassName, 5))) {
                        treeModel.toggleOpenState(selectedItem);
                    } else {
                        final boolean doubleClick = doubleClickTest.test(selectedItem);
                        doSelect(selectedItem,
                                new SelectionType(doubleClick,
                                        false,
                                        allowMultiSelect,
                                        nativeEvent.getCtrlKey(),
                                        nativeEvent.getShiftKey()));
                    }
                }
            }
        }
    }
}
