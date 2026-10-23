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

import stroom.dispatch.client.RestFactory;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.explorer.client.event.ExplorerItemKeyEvent;
import stroom.explorer.client.event.ShowExplorerMenuEvent;
import stroom.explorer.client.view.ExplorerCell;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.explorer.shared.NodeFlag;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.Expander;
import stroom.util.shared.NullSafe;
import stroom.util.shared.TokenError;
import stroom.widget.datagrid.client.DataGridProfile;
import stroom.widget.datagrid.client.DataGridWidget;
import stroom.widget.datagrid.client.DataGridWidgetSelectionEventManager;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.util.client.DoubleSelectTester;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectEvent;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.Selection;
import stroom.widget.util.client.SelectionType;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.view.client.CellPreviewEvent;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/// The explorer's tree of documents, on the data grid with its tree profile ([DataGridProfile#TREE]):
/// a `tree` of `treeitem`s, each saying its level, whether it is expanded and its place among its
/// siblings, with WAI-ARIA's tree keys: Up and Down through the items shown, Right and Left to
/// expand and collapse, or to the first child and the parent, Home and End, `*` to expand the
/// siblings, and typing to go to the next item starting with what is typed (or into a picker's
/// filter). Moving doesn't choose an item (choosing one opens its document), unless the tree is a
/// picker's ([#setSelectionFollowsFocus]): Space chooses (Ctrl+Space adds to the choice, Shift and
/// the arrows extend it, where several can be chosen), Enter opens, as a double click does, and the
/// context menu key or Shift+F10 shows the item's menu, as a right click does. Keys for some of the
/// menu's actions (F2 to rename, Alt+Enter for info, which the user can change) act on the item, and
/// a screen reader is told when they can't.
public abstract class AbstractExplorerTree extends Composite implements Focus {

    private static final Action[] ITEM_KEY_ACTIONS = {Action.EXPLORER_RENAME, Action.EXPLORER_INFO};

    private final ExplorerTreeModel treeModel;
    private final MultiSelectionModelImpl<ExplorerNode> selectionModel;
    private final DataGridWidget<ExplorerNode> tree;
    private final DoubleSelectTester doubleClickTest = new DoubleSelectTester();
    private final boolean allowMultiSelect;
    private final String expanderClassName;
    private final ExplorerCell explorerCell;
    private final ExplorerTreeSelectionEventManager selectionEventManager;
    // Tells screen reader users why an item's key did nothing, e.g. "Feeds can't be renamed"
    private final Element liveRegion;

    // Required for multiple selection using shift and control key modifiers.
    private ExplorerNode multiSelectStart;
    private List<ExplorerNode> rows;
    private boolean showAlerts;
    private Consumer<FetchExplorerNodeResult> changeHandler = null;
    private Consumer<String> filterErrorConsumer = null;

    AbstractExplorerTree(final RestFactory restFactory,
                         final TaskMonitorFactory taskMonitorFactory,
                         final boolean allowMultiSelect,
                         final boolean showAlerts) {
        this.allowMultiSelect = allowMultiSelect;
        this.showAlerts = showAlerts;

        explorerCell = new ExplorerCell(getTickBoxSelectionModel(), showAlerts);
        expanderClassName = explorerCell.getExpanderClassName();

        tree = new DataGridWidget<>(this, DataGridWidget.MASSIVE_LIST_PAGE_SIZE);
        tree.setProfile(DataGridProfile.TREE);
        // The explorer's cells draw their own padding; a long name scrolls the tree sideways
        tree.setCellPadding(false);
        tree.setFitWidth(true);
        tree.hasContextMenu(false);
        tree.addStyleName("explorerTree");
        tree.addColumn(new Column<ExplorerNode, ExplorerNode>(explorerCell) {
            @Override
            public ExplorerNode getValue(final ExplorerNode object) {
                return object;
            }
        }, "", 100);
        selectionModel = getSelectionModel();

        treeModel = new ExplorerTreeModel(this, restFactory, taskMonitorFactory) {
            @Override
            protected void onDataChanged(final FetchExplorerNodeResult result) {
                onData(result);
                super.onDataChanged(result);

                // A rejected filter comes back as an empty tree like any other, so surface the
                // reason on whichever quick filter is driving this tree. Every explorer surface -
                // navigation, entity tree, the explorer popup, export config - shares this one
                // callback. See FetchExplorerNodeResult.filterError.
                if (filterErrorConsumer != null) {
                    filterErrorConsumer.accept(
                            NullSafe.get(result.getFilterError(), TokenError::getText));
                }

                if (changeHandler != null) {
                    changeHandler.accept(result);
                }
            }
        };
        treeModel.setShowAlerts(showAlerts);

        // The items' levels and open state, which Right, Left and * change as the expander does
        tree.setTreeRows(AbstractExplorerTree::expanderOf, treeModel::setItemOpen);
        // Each item's type, read after its name (#5411): "Countries, tree item, Dictionary"
        tree.setRowDescription(AbstractExplorerTree::typeOf);
        final TickBoxSelectionModel tickBoxSelectionModel = getTickBoxSelectionModel();
        if (tickBoxSelectionModel != null) {
            // A tick tree's items say whether they are ticked
            tree.setRowChecked(tickBoxSelectionModel::getState);
        }
        selectionEventManager = new ExplorerTreeSelectionEventManager(tree,
                selectionModel != null
                        ? selectionModel
                        : new MultiSelectionModelImpl<>());
        tree.setSelectionModel(selectionModel, selectionEventManager);

        final FlowPanel flowPanel = new FlowPanel();
        flowPanel.getElement().getStyle().setPosition(Style.Position.RELATIVE);
        flowPanel.setWidth("100%");
        flowPanel.setHeight("100%");
        flowPanel.add(tree);

        liveRegion = Document.get().createDivElement();
        liveRegion.setClassName("sr-only");
        liveRegion.setAttribute("role", "status");
        liveRegion.setAttribute("aria-live", "polite");
        flowPanel.getElement().appendChild(liveRegion);

        initWidget(flowPanel);
    }

    // An item's level, and whether it is a leaf or open
    private static Expander expanderOf(final ExplorerNode node) {
        return new Expander(node.getDepth(),
                node.hasNodeFlag(NodeFlag.OPEN),
                !node.hasNodeFlag(NodeFlag.OPEN) && !node.hasNodeFlag(NodeFlag.CLOSED));
    }

    // An item's type as people know it, e.g. Dictionary or Folder (else its type's own name)
    private static String typeOf(final ExplorerNode node) {
        final String type = node.getType();
        return NullSafe.getOrElse(DocumentTypeRegistry.get(type), DocumentType::getDisplayType, type);
    }

    @Override
    public void focus() {
        tree.setFocus(true);
    }

    abstract MultiSelectionModelImpl<ExplorerNode> getSelectionModel();

    /// Sets the keys of a tree with a filter above it, as in a picker's dialog.
    ///
    /// @param moveUpFromFirst What Up on the first item does, e.g. moves the focus to the filter.
    /// @param typedHandler    Given each character typed in the tree, e.g. to type it in the filter.
    public void setFilterKeys(final Runnable moveUpFromFirst, final Consumer<String> typedHandler) {
        selectionEventManager.setMoveUpFromFirst(moveUpFromFirst);
        selectionEventManager.setTypedHandler(typedHandler);
    }

    /// Sets whether moving to an item with the keyboard also chooses it, as in a picker that
    /// chooses one item, rather than only moving the focus, which Space chooses.
    ///
    /// @param selectionFollowsFocus Whether the selection follows the focus.
    public void setSelectionFollowsFocus(final boolean selectionFollowsFocus) {
        selectionEventManager.selectionFollowsFocus = selectionFollowsFocus;
    }

    /// Names the tree for screen readers, e.g. `Documents`.
    ///
    /// @param name The name.
    public void setAccessibleName(final String name) {
        tree.setAccessibleName(name);
    }

    /// Moves the focus to the chosen item (or the first, if none is), e.g. from a filter above.
    public void focusSelected() {
        tree.setKeyboardSelectedRow(Math.max(0, getItemIndex(getSelected())), true);
    }

    /// Makes the chosen item the tree's tab stop, so tabbing into the tree lands on it.
    public void showSelectedAsTabStop() {
        final int index = getItemIndex(getSelected());
        if (index >= 0) {
            tree.setKeyboardSelectedRow(index, false);
        }
    }

    private ExplorerNode getSelected() {
        return selectionModel == null
                ? null
                : selectionModel.getSelected();
    }

    void onData(final FetchExplorerNodeResult result) {
    }

    void addChangeHandler(final Consumer<FetchExplorerNodeResult> changeHandler) {
        this.changeHandler = changeHandler;
    }

    void setData(final List<ExplorerNode> rows) {
        this.rows = rows;
        tree.setRowData(0, rows);
        tree.setRowCount(rows.size(), true);
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
        final List<ExplorerNode> items = tree.getVisibleItems();
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

    /// Points the tree's keyboard at an item and gives it the focus, e.g. after the rows changed.
    ///
    /// @param row The item's index among those shown, or -1 for none (nothing changes).
    void setKeyboardSelectedRow(final int row) {
        if (row >= 0) {
            tree.setKeyboardSelectedRow(row, true);
        }
    }

    private void scrollSelectedIntoView() {
        if (selectionModel != null) {
            final ExplorerNode selected = selectionModel.getSelected();
            if (selected != null) {
                final int index = getItemIndex(selected);
                if (index > 0) {
                    tree.scrollRowIntoView(index);
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
        tree.setFocus(focused);
    }

    TickBoxSelectionModel getTickBoxSelectionModel() {
        return null;
    }

    /// Shows an item's menu: by the pointer for a right click, else by the item.
    ///
    /// @param item        The item.
    /// @param nativeEvent The right click's mouse down, or null for a key.
    void showMenu(final ExplorerNode item, final NativeEvent nativeEvent) {
        final int row = tree.getVisibleItems().indexOf(item);
        final PopupPosition popupPosition;
        if (nativeEvent != null) {
            popupPosition = new PopupPosition(nativeEvent.getClientX(), nativeEvent.getClientY());
        } else {
            final Element element = tree.getRowElement(row);
            popupPosition = element != null
                    ? new PopupPosition(element.getAbsoluteRight(), element.getAbsoluteTop())
                    : null;
        }

        if (selectionModel != null && popupPosition != null) {
            chooseToActOn(item, row, nativeEvent);
            Scheduler.get().scheduleDeferred(() -> {
                ShowExplorerMenuEvent.fire(
                        AbstractExplorerTree.this,
                        selectionModel,
                        popupPosition);
            });
        }
    }

    // Chooses the item an action is to act on, as a right click does, unless it is already chosen
    // (keeping the rest of the choice)
    private void chooseToActOn(final ExplorerNode item, final int row, final NativeEvent nativeEvent) {
        if (!selectionModel.isSelected(item)) {
            doSelect(item,
                    new SelectionType(false,
                            true,
                            false,
                            nativeEvent != null && nativeEvent.getCtrlKey(),
                            nativeEvent != null && nativeEvent.getShiftKey()));
        }

        if (row >= 0) {
            tree.setKeyboardSelectedRow(row, true);
        }
    }

    /// Acts on an item with a key for one of its menu's actions, e.g. F2 to rename it.
    ///
    /// @param item   The item with the focus.
    /// @param action The action whose key was pressed.
    /// @return Whether there is anything to act on the item (the key goes no further).
    boolean actOnItem(final ExplorerNode item, final Action action) {
        if (selectionModel == null || getHandlerCount(ExplorerItemKeyEvent.getType()) == 0) {
            return false;
        }
        chooseToActOn(item, tree.getVisibleItems().indexOf(item), null);
        Scheduler.get().scheduleDeferred(() ->
                ExplorerItemKeyEvent.fire(AbstractExplorerTree.this, action, this::announce));
        return true;
    }

    // Says something to screen reader users, emptying the region first so a repeat is said again
    private void announce(final String message) {
        liveRegion.setInnerText("");
        Scheduler.get().scheduleDeferred(() -> liveRegion.setInnerText(message));
    }

    /// Adds a handler for the keys for an item's actions (e.g. F2 to rename it).
    ///
    /// @param handler The handler.
    /// @return Its registration.
    public HandlerRegistration addItemKeyHandler(final ExplorerItemKeyEvent.Handler handler) {
        return addHandler(handler, ExplorerItemKeyEvent.getType());
    }

    void selectAll() {
    }


    // --------------------------------------------------------------------------------


    /// The explorer's choosing, for the tree's keyboard and the mouse.
    private class ExplorerTreeSelectionEventManager extends DataGridWidgetSelectionEventManager<ExplorerNode> {

        private boolean selectionFollowsFocus;

        ExplorerTreeSelectionEventManager(final DataGridWidget<ExplorerNode> tree,
                                          final MultiSelectionModel<ExplorerNode> model) {
            super(tree, model, allowMultiSelect);
        }

        // Moving chooses nothing (choosing opens the document), but Shift extends the choice, and a
        // picker's moving chooses
        @Override
        public void onFocusRow(final ExplorerNode item, final boolean shift, final boolean ctrl) {
            if (item == null) {
                return;
            }
            if (shift && allowMultiSelect) {
                doSelect(item, new SelectionType(false, false, true, false, true));
            } else if (selectionFollowsFocus && !ctrl) {
                doSelect(item, new SelectionType());
            }
        }

        @Override
        public void onSpace(final ExplorerNode item, final boolean shift, final boolean ctrl) {
            if (item != null) {
                doSelect(item, new SelectionType(false, false, true, ctrl, shift));
            }
        }

        // Enter opens the item, as a double click does
        @Override
        public void onEnter(final ExplorerNode item) {
            if (item != null) {
                doSelect(item, new SelectionType(true, false, allowMultiSelect, false, false));
            }
        }

        @Override
        public void onSelectAll() {
            selectAll();
        }

        @Override
        public boolean onContextMenu(final ExplorerNode item) {
            showMenu(item, null);
            return true;
        }

        // The keys for an item's actions, e.g. F2 to rename it
        @Override
        public boolean onRowKey(final ExplorerNode item, final NativeEvent event) {
            if (item != null) {
                for (final Action action : ITEM_KEY_ACTIONS) {
                    if (KeyBinding.isShortcut(event, action)) {
                        return actOnItem(item, action);
                    }
                }
            }
            return false;
        }

        @Override
        public void onCellPreview(final CellPreviewEvent<ExplorerNode> event) {
            if (BrowserEvents.MOUSEDOWN.equals(event.getNativeEvent().getType())) {
                onMouseDown(event);
            } else {
                super.onCellPreview(event);
            }
        }

        private void onMouseDown(final CellPreviewEvent<ExplorerNode> e) {
            final NativeEvent nativeEvent = e.getNativeEvent();
            final ExplorerNode selectedItem = e.getValue();
            final int row = tree.getVisibleItems().indexOf(selectedItem);
            if (row >= 0) {
                tree.setKeyboardSelectedRow(row, true);
            }

            if (selectedItem == null) {
                return;
            }
            if (MouseUtil.isSecondary(nativeEvent)) {
                showMenu(selectedItem, nativeEvent);

            } else if (MouseUtil.isPrimary(nativeEvent)) {
                final Element element = nativeEvent.getEventTarget().cast();
                if (!selectedItem.hasNodeFlag(NodeFlag.LEAF)
                    && ElementUtil.hasClassName(element, expanderClassName, 5)) {
                    // The expander
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

    /**
     * Receives the reason the server rejected the current filter, or null when it was applied.
     */
    public void setFilterErrorConsumer(final Consumer<String> filterErrorConsumer) {
        this.filterErrorConsumer = filterErrorConsumer;
    }

}
