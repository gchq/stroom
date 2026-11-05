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
import com.google.gwt.dom.client.DataTransfer;
import com.google.gwt.dom.client.DataTransfer.DropEffect;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.event.dom.client.DragEndEvent;
import com.google.gwt.event.dom.client.DragLeaveEvent;
import com.google.gwt.event.dom.client.DragOverEvent;
import com.google.gwt.event.dom.client.DragStartEvent;
import com.google.gwt.event.dom.client.DropEvent;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.cellview.client.AbstractHasData;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.MaxScrollPanel;
import com.google.gwt.view.client.CellPreviewEvent;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class AbstractExplorerTree extends Composite implements Focus {

    private static final String DROP_TARGET_CLASS_NAME = "explorerTree-dropTarget";
    // How long a drag must hover over a closed folder before it is opened.
    private static final int AUTO_EXPAND_DELAY_MS = 750;

    private final ExplorerTreeModel treeModel;
    private final RestFactory restFactory;
    private final TaskMonitorFactory taskMonitorFactory;
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

    // Drag and drop state, only used once a drop handler has been set.
    private DropHandler dropHandler;
    private List<ExplorerNode> draggedNodes;
    private ExplorerDragPermissions dragPermissions;
    private Element dropTargetElement;
    private ExplorerNode autoExpandNode;
    private Timer autoExpandTimer;
    // A mouse down on an item that is already part of a multiple selection is not applied until the
    // mouse up, so that the whole selection can be dragged. A drag start discards it.
    private ExplorerNode pendingSelection;

    AbstractExplorerTree(final RestFactory restFactory,
                         final TaskMonitorFactory taskMonitorFactory,
                         final boolean allowMultiSelect,
                         final boolean showAlerts) {
        this.restFactory = restFactory;
        this.taskMonitorFactory = taskMonitorFactory;
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

    /**
     * @return The node immediately above the given node in the currently displayed rows, or null if the
     * node is not present or is the first row. Used to pick a sensible item to keep in view when the
     * current selection is removed (e.g. after a delete).
     */
    ExplorerNode getNodeAbove(final ExplorerNode node) {
        if (rows != null && node != null) {
            final int index = rows.indexOf(node);
            if (index > 0) {
                return rows.get(index - 1);
            }
        }
        return null;
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

    /**
     * Point the cell table's keyboard-selected row at the given row and take focus. This is applied
     * with the (deferred) row render, so it overrides the table's own tendency to re-focus a reset
     * keyboard row (which would scroll the view to the top) after the data changes.
     */
    void setKeyboardSelectedRow(final int row) {
        if (row >= 0) {
            cellTable.setKeyboardSelectedRow(row, true);
        }
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

    /// Allows nodes to be dragged and dropped onto folders within this tree. Holding Ctrl (or Alt/Option)
    /// when dropping requests a copy, otherwise a move is requested. The tree validates the drop against
    /// the rows it is displaying and the user's permissions (fetched during the drag);
    /// `dropHandler` is responsible for carrying it out.
    ///
    /// @param dropHandler Called with the dragged nodes and the destination folder when a valid drop occurs.
    public void setDropHandler(final DropHandler dropHandler) {
        Objects.requireNonNull(dropHandler);
        if (this.dropHandler == null) {
            explorerCell.setDraggable(true);
            dragPermissions = new ExplorerDragPermissions(restFactory, taskMonitorFactory);
            autoExpandTimer = new Timer() {
                @Override
                public void run() {
                    treeModel.setItemOpen(autoExpandNode, true);
                }
            };
            cellTable.addDomHandler(this::onDragStart, DragStartEvent.getType());
            cellTable.addDomHandler(this::onDragOver, DragOverEvent.getType());
            cellTable.addDomHandler(this::onDragLeave, DragLeaveEvent.getType());
            cellTable.addDomHandler(this::onDrop, DropEvent.getType());
            cellTable.addDomHandler(event -> endDrag(), DragEndEvent.getType());
            cellTable.addDomHandler(this::onMouseUp, MouseUpEvent.getType());
            cellTable.redraw();
        }
        this.dropHandler = dropHandler;
    }

    private void onDragStart(final DragStartEvent event) {
        pendingSelection = null;
        final ExplorerNode node = getNode(event.getNativeEvent());
        final List<ExplorerNode> nodes = ExplorerDragDropRules.getDraggedNodes(
                node,
                selectionModel.getSelectedItems());
        if (nodes.isEmpty()) {
            event.preventDefault();
        } else {
            draggedNodes = nodes;
            dragPermissions.start(nodes);
            final DataTransfer dataTransfer = event.getDataTransfer();
            // Some browsers (e.g. Firefox) won't start a drag unless some data is set.
            dataTransfer.setData("text/plain", nodes.stream()
                    .map(ExplorerNode::getName)
                    .collect(Collectors.joining("\n")));
            setEffectAllowed(dataTransfer, "copyMove");
            setDragImage(dataTransfer, nodes);
        }
    }

    private void setDragImage(final DataTransfer dataTransfer, final List<ExplorerNode> nodes) {
        // The browser snapshots the drag image during dragstart, so it only needs to be in the document
        // until then. Without this the whole row, including the expander and indent, is used.
        final Element container = Document.get().createDivElement();
        container.setInnerSafeHtml(explorerCell.renderDragImage(nodes));
        final Element dragImage = container.getFirstChildElement();
        Document.get().getBody().appendChild(dragImage);
        dataTransfer.setDragImage(dragImage, 0, 0);
        Scheduler.get().scheduleDeferred(dragImage::removeFromParent);
    }

    private boolean isAllowedDrop(final List<ExplorerNode> nodes,
                                  final ExplorerNode target,
                                  final boolean copy) {
        return ExplorerDragDropRules.isValidDrop(rows, nodes, target, copy)
               && dragPermissions.isPermitted(nodes, target, copy);
    }

    private void onDragOver(final DragOverEvent event) {
        // Ignore drags that didn't start in this tree, e.g. files from the desktop.
        if (draggedNodes != null) {
            final NativeEvent nativeEvent = event.getNativeEvent();
            final int row = getRowIndex(nativeEvent);
            final ExplorerNode target = getNode(row);
            final boolean copy = isCopy(nativeEvent);
            if (isAllowedDrop(draggedNodes, target, copy)) {
                // Cancelling dragover is what tells the browser a drop is allowed here.
                event.preventDefault();
                event.getDataTransfer().setDropEffect(copy
                        ? DropEffect.COPY
                        : DropEffect.MOVE);
                setDropTargetElement(cellTable.getRowElement(row));
            } else {
                event.getDataTransfer().setDropEffect(DropEffect.NONE);
                setDropTargetElement(null);
            }
            scheduleAutoExpand(target);
        }
    }

    private void onDragLeave(final DragLeaveEvent event) {
        // Drag leave fires every time the pointer crosses into a child element, so only reset when it
        // leaves the table altogether.
        final Element related = getElement(event.getNativeEvent().getRelatedEventTarget());
        if (related == null || !cellTable.getElement().isOrHasChild(related)) {
            setDropTargetElement(null);
            scheduleAutoExpand(null);
        }
    }

    private void onDrop(final DropEvent event) {
        event.preventDefault();
        final NativeEvent nativeEvent = event.getNativeEvent();
        final ExplorerNode target = getNode(nativeEvent);
        final boolean copy = isCopy(nativeEvent);
        final List<ExplorerNode> nodes = draggedNodes;
        final boolean allowed = nodes != null && isAllowedDrop(nodes, target, copy);
        endDrag();
        if (allowed) {
            dropHandler.onDrop(nodes, target, copy);
        }
    }

    private void onMouseUp(final MouseUpEvent event) {
        if (pendingSelection != null) {
            final ExplorerNode node = pendingSelection;
            pendingSelection = null;
            doSelect(node, new SelectionType(
                    doubleClickTest.test(node),
                    false,
                    allowMultiSelect,
                    false,
                    false));
        }
    }

    private void endDrag() {
        draggedNodes = null;
        dragPermissions.clear();
        setDropTargetElement(null);
        scheduleAutoExpand(null);
    }

    private void setDropTargetElement(final Element element) {
        if (!Objects.equals(dropTargetElement, element)) {
            if (dropTargetElement != null) {
                dropTargetElement.removeClassName(DROP_TARGET_CLASS_NAME);
            }
            dropTargetElement = element;
            if (dropTargetElement != null) {
                dropTargetElement.addClassName(DROP_TARGET_CLASS_NAME);
            }
        }
    }

    private void scheduleAutoExpand(final ExplorerNode node) {
        if (!Objects.equals(autoExpandNode, node)) {
            autoExpandTimer.cancel();
            autoExpandNode = null;
            final boolean isDragged = node != null
                                      && draggedNodes != null
                                      && draggedNodes.stream()
                                              .anyMatch(dragged -> Objects.equals(dragged.getDocRef(),
                                                      node.getDocRef()));
            if (node != null && node.hasNodeFlag(NodeFlag.CLOSED) && !isDragged) {
                autoExpandNode = node;
                autoExpandTimer.schedule(AUTO_EXPAND_DELAY_MS);
            }
        }
    }

    private static boolean isCopy(final NativeEvent nativeEvent) {
        // Ctrl is the copy modifier on Windows/Linux, Alt (Option) on macOS.
        return nativeEvent.getCtrlKey() || nativeEvent.getAltKey();
    }

    private ExplorerNode getNode(final NativeEvent nativeEvent) {
        return getNode(getRowIndex(nativeEvent));
    }

    private ExplorerNode getNode(final int row) {
        final List<ExplorerNode> items = cellTable.getVisibleItems();
        if (row >= 0 && row < items.size()) {
            return items.get(row);
        }
        return null;
    }

    private int getRowIndex(final NativeEvent nativeEvent) {
        Element element = getElement(nativeEvent.getEventTarget());
        while (element != null && element != cellTable.getElement()) {
            if (TableRowElement.is(element)) {
                final int index = TableRowElement.as(element).getSectionRowIndex();
                // Make sure it is one of the table's own rows rather than a row nested in a cell.
                if (index >= 0
                    && index < cellTable.getVisibleItemCount()
                    && cellTable.getRowElement(index) == element) {
                    return index;
                }
            }
            element = element.getParentElement();
        }
        return -1;
    }

    private static Element getElement(final EventTarget eventTarget) {
        if (eventTarget != null && Node.is(eventTarget)) {
            final Node node = Node.as(eventTarget);
            // Drag events can target text nodes.
            if (Element.is(node)) {
                return Element.as(node);
            }
            return node.getParentElement();
        }
        return null;
    }

    private static native void setEffectAllowed(final DataTransfer dataTransfer, final String effectAllowed) /*-{
        dataTransfer.effectAllowed = effectAllowed;
    }-*/;

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
            pendingSelection = null;
            final int row = cellTable.getVisibleItems().indexOf(e.getValue());
            if (row >= 0) {
                cellTable.setKeyboardSelectedRow(row, true);
            } else {
                cellTable.setKeyboardSelectedRow(cellTable.getKeyboardSelectedRow(), true);
            }

            if (MouseUtil.isSecondary(nativeEvent)) {
                showMenu(e);

            } else if (MouseUtil.isPrimary(nativeEvent)) {
                if (isDeferredSelect(e)) {
                    pendingSelection = e.getValue();
                } else {
                    select(e);
                }
            }
        }

        private boolean isDeferredSelect(final CellPreviewEvent<ExplorerNode> e) {
            final NativeEvent nativeEvent = e.getNativeEvent();
            final Element element = nativeEvent.getEventTarget().cast();
            return dropHandler != null
                   && allowMultiSelect
                   && !nativeEvent.getCtrlKey()
                   && !nativeEvent.getShiftKey()
                   && selectionModel.getSelectedCount() > 1
                   && selectionModel.isSelected(e.getValue())
                   && !ElementUtil.hasClassName(element, expanderClassName, 5);
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


    // --------------------------------------------------------------------------------


    /// Receives drops of explorer nodes onto a folder in the tree.
    public interface DropHandler {

        /// Called when nodes are dropped onto a valid destination.
        ///
        /// @param nodes       The dropped nodes, never empty.
        /// @param destination The folder (or `System` root) they were dropped onto.
        /// @param copy        True if a copy was requested, false for a move.
        void onDrop(List<ExplorerNode> nodes, ExplorerNode destination, boolean copy);
    }
}
