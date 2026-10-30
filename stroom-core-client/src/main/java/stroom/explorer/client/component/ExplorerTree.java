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

package stroom.explorer.client.component;

import stroom.cell.tickbox.client.TickBoxState;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.explorer.client.ExplorerCell;
import stroom.explorer.client.TickBoxSelectionModel;
import stroom.explorer.client.event.ExplorerItemKeyEvent;
import stroom.explorer.client.event.ShowExplorerMenuEvent;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.NodeFlag;
import stroom.util.shared.Expander;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.DataGridProfile;
import stroom.widget.datagrid.client.DataGridSelectionEventManager;
import stroom.widget.popup.client.PopupPosition;
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

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.CellPreviewEvent;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
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
///
/// A tick tree's items have tick boxes instead ([#setTickBoxes]): choosing one ticks it.
///
/// It shows what it is given; its [ExplorerTreeController] fetches the nodes and keeps which are
/// open, as the tree asks.
public class ExplorerTree extends Component implements Focus {

    /// The tree's first item where a picker may choose nothing (see [ExplorerTreeController]).
    public static final ExplorerNode NULL_SELECTION = ExplorerNode.builder()
            .docRef(DocRef.builder()
                    .uuid("")
                    .name("None")
                    .type("")
                    .build())
            .build();

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final Action[] ITEM_KEY_ACTIONS = {Action.EXPLORER_RENAME, Action.EXPLORER_INFO};

    @UiField
    DataGrid<ExplorerNode> tree;
    // Tells screen reader users why an item's key did nothing, e.g. "Feeds can't be renamed"
    @UiField
    Element liveRegion;

    private final DoubleSelectTester doubleClickTest = new DoubleSelectTester();
    private final String expanderClassName;
    private final ExplorerCell explorerCell;
    // The choice, or null for a tick tree, whose ticks are its choice
    private MultiSelectionModelImpl<ExplorerNode> selectionModel = new MultiSelectionModelImpl<>();
    private TickBoxSelectionModel tickBoxSelectionModel;
    private ExplorerTreeSelectionEventManager selectionEventManager;
    private boolean allowMultiSelect;
    private boolean includeNullSelection;
    // The keys set by the owner, kept for a selection manager made again
    private Runnable moveUpFromFirst;
    private Consumer<String> typedHandler;
    private boolean selectionFollowsFocus;

    // Required for multiple selection using shift and control key modifiers.
    private ExplorerNode multiSelectStart;
    private List<ExplorerNode> rows;
    private List<ExplorerNode> rootNodes;

    // What the owner is told
    private BiConsumer<ExplorerNode, Boolean> onOpenChange;
    private Consumer<ExplorerNode> onToggleOpen;
    private Runnable onReload;
    private BiConsumer<ExplorerNode, SelectionType> onChoose;
    private Consumer<ExplorerNode> onInitialSelect;

    /// Makes a tree with nothing in it, choosing one item at a time; its controller fills it.
    public ExplorerTree() {
        initWidget(BINDER.createAndBindUi(this));
        explorerCell = new ExplorerCell(null, false);
        expanderClassName = explorerCell.getExpanderClassName();

        tree.setPageSize(DataGrid.MASSIVE_LIST_PAGE_SIZE);
        tree.addColumn(new Column<ExplorerNode, ExplorerNode>(explorerCell) {
            @Override
            public ExplorerNode getValue(final ExplorerNode object) {
                return object;
            }
        }, "", 100);

        // The items' levels and open state, which Right, Left and * change as the expander does
        tree.setTreeRows(ExplorerTree::expanderOf, (item, open) -> {
            if (onOpenChange != null) {
                onOpenChange.accept(item, open);
            }
        });
        // Each item's type, read after its name (#5411): "Countries, tree item, Dictionary"
        tree.setRowDescription(ExplorerTree::typeOf);
        installSelectionManager();
    }

    // The grid's choosing, made again when what can be chosen changes
    private void installSelectionManager() {
        selectionEventManager = new ExplorerTreeSelectionEventManager(tree,
                selectionModel != null
                        ? selectionModel
                        : new MultiSelectionModelImpl<>());
        selectionEventManager.setMoveUpFromFirst(moveUpFromFirst);
        selectionEventManager.setTypedHandler(typedHandler);
        selectionEventManager.selectionFollowsFocus = selectionFollowsFocus;
        tree.setSelectionModel(selectionModel, selectionEventManager);
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

    /// Lets more than one item be chosen (Ctrl and Shift with a click or Space, Shift with the
    /// arrows), as in the explorer.
    ///
    /// @param allowMultiSelect Whether more than one can be chosen.
    public void setMultiSelect(final boolean allowMultiSelect) {
        this.allowMultiSelect = allowMultiSelect;
        installSelectionManager();
    }

    /// Gives the items tick boxes, which choosing an item ticks (or unticks), in place of a choice;
    /// a tick tree has no item menus.
    ///
    /// @param tickBoxes Whether the items have tick boxes.
    public void setTickBoxes(final boolean tickBoxes) {
        if (tickBoxes == (tickBoxSelectionModel != null)) {
            return;
        }
        if (tickBoxes) {
            tickBoxSelectionModel = new TickBoxSelectionModel();
            selectionModel = null;
            explorerCell.setSelectionModel(tickBoxSelectionModel);
            // A tick tree's items say whether they are ticked
            tree.setRowChecked(tickBoxSelectionModel::getState);
        } else {
            tickBoxSelectionModel = null;
            selectionModel = new MultiSelectionModelImpl<>();
            explorerCell.setSelectionModel(null);
            tree.setRowChecked(null);
        }
        installSelectionManager();
    }

    /// Shows (or not) the alert icon of an item with problems, e.g. a broken dependency.
    ///
    /// @param showAlerts Whether to show them.
    public void setShowAlerts(final boolean showAlerts) {
        explorerCell.setShowAlerts(showAlerts);
    }

    /// @return The choice, or null for a tick tree (see [#getTickedSet()]).
    public MultiSelectionModelImpl<ExplorerNode> getSelectionModel() {
        return selectionModel;
    }

    /// Sets the keys of a tree with a filter above it, as in a picker's dialog.
    ///
    /// @param moveUpFromFirst What Up on the first item does, e.g. moves the focus to the filter.
    /// @param typedHandler    Given each character typed in the tree, e.g. to type it in the filter.
    public void setFilterKeys(final Runnable moveUpFromFirst, final Consumer<String> typedHandler) {
        this.moveUpFromFirst = moveUpFromFirst;
        this.typedHandler = typedHandler;
        selectionEventManager.setMoveUpFromFirst(moveUpFromFirst);
        selectionEventManager.setTypedHandler(typedHandler);
    }

    /// Sets whether moving to an item with the keyboard also chooses it, as in a picker that
    /// chooses one item, rather than only moving the focus, which Space chooses.
    ///
    /// @param selectionFollowsFocus Whether the selection follows the focus.
    public void setSelectionFollowsFocus(final boolean selectionFollowsFocus) {
        this.selectionFollowsFocus = selectionFollowsFocus;
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

    /// Sets what is told after an item is chosen, by the user or by the tree, e.g. for a picker to
    /// take it.
    ///
    /// @param onChoose Given the item (or null for none) and how it was chosen.
    public void setOnChoose(final BiConsumer<ExplorerNode, SelectionType> onChoose) {
        this.onChoose = onChoose;
    }

    /// Sets what is told after the tree chooses an item for itself once it is filled, e.g. one it
    /// was asked to show.
    ///
    /// @param onInitialSelect Given the item.
    public void setOnInitialSelect(final Consumer<ExplorerNode> onInitialSelect) {
        this.onInitialSelect = onInitialSelect;
    }

    /// Ticks (or unticks) an item of a tick tree.
    ///
    /// @param explorerNode The item.
    /// @param selected     Whether it is ticked.
    public void setTicked(final ExplorerNode explorerNode, final boolean selected) {
        if (tickBoxSelectionModel != null) {
            tickBoxSelectionModel.setSelected(explorerNode, selected);
            reload();
        }
    }

    /// @return The ticked items of a tick tree, or none for a tree without tick boxes.
    public Set<ExplorerNode> getTickedSet() {
        return tickBoxSelectionModel != null
                ? tickBoxSelectionModel.getSelectedSet()
                : Collections.emptySet();
    }

    /// @param handler Told when an item's menu is asked for (a right click, or the context menu key).
    /// @return Its registration.
    public HandlerRegistration addContextMenuHandler(final ShowExplorerMenuEvent.Handler handler) {
        return addHandler(handler, ShowExplorerMenuEvent.getType());
    }

    /// Adds a handler for the keys for an item's actions (e.g. F2 to rename it).
    ///
    /// @param handler The handler.
    /// @return Its registration.
    public HandlerRegistration addItemKeyHandler(final ExplorerItemKeyEvent.Handler handler) {
        return addHandler(handler, ExplorerItemKeyEvent.getType());
    }

    /// @param focused Whether the tree has the focus.
    public void setFocus(final boolean focused) {
        tree.setFocus(focused);
    }

    // --------------------------------------------------------------------------------
    // For the controller

    /// Sets what is told when an item is opened or closed with the keyboard (Right, Left, `*`).
    void setOnOpenChange(final BiConsumer<ExplorerNode, Boolean> onOpenChange) {
        this.onOpenChange = onOpenChange;
    }

    /// Sets what is told when an item's expander is pressed.
    void setOnToggleOpen(final Consumer<ExplorerNode> onToggleOpen) {
        this.onToggleOpen = onToggleOpen;
    }

    /// Sets what fills the tree again, e.g. once a tick changes the ticks shown.
    void setOnReload(final Runnable onReload) {
        this.onReload = onReload;
    }

    /// Sets whether the first item is [#NULL_SELECTION], which a picker chooses for nothing.
    void setIncludeNullSelection(final boolean includeNullSelection) {
        this.includeNullSelection = includeNullSelection;
    }

    /// Shows the items, the open ones' children after them.
    void setData(final List<ExplorerNode> rows) {
        this.rows = rows;
        tree.setRowData(0, rows);
        tree.setRowCount(rows.size(), true);
    }

    /// The tree's roots, which a tick tree ticks through.
    void setRootNodes(final List<ExplorerNode> rootNodes) {
        this.rootNodes = rootNodes;
        if (tickBoxSelectionModel != null) {
            tickBoxSelectionModel.setRoots(rootNodes);
        }
    }

    /// @return The node immediately above the given node in the currently displayed rows, or null
    /// if the node is not present or is the first row. Used to pick a sensible item to keep in view
    /// when the current selection is removed (e.g. after a delete).
    ExplorerNode getNodeAbove(final ExplorerNode node) {
        if (rows != null && node != null) {
            final int index = rows.indexOf(node);
            if (index > 0) {
                return rows.get(index - 1);
            }
        }
        return null;
    }

    /// Chooses an item for itself once filled, and scrolls it into view.
    void setInitialSelectedItem(final ExplorerNode selection) {
        if (selectionModel != null) {
            selectionModel.clear();
        }
        setSelectedItem(selection);
        scrollSelectedIntoView();
        if (onInitialSelect != null) {
            onInitialSelect.accept(selection);
        }
    }

    /// Points the tree's keyboard at an item and gives it the focus, e.g. after the rows changed.
    ///
    /// @param row The item's index among those shown, or -1 for none (nothing changes).
    void setKeyboardSelectedRow(final int row) {
        if (row >= 0) {
            tree.setKeyboardSelectedRow(row, true);
        }
    }

    // --------------------------------------------------------------------------------

    private ExplorerNode getSelected() {
        return selectionModel == null
                ? null
                : selectionModel.getSelected();
    }

    private void reload() {
        if (onReload != null) {
            onReload.run();
        }
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

    /// Chooses an item as a click on it does, e.g. one to show; a picker's [#NULL_SELECTION] for
    /// none, where it has one.
    ///
    /// @param selection The item, or null for none.
    public void setSelectedItem(ExplorerNode selection) {
        if (includeNullSelection && selection == null) {
            selection = NULL_SELECTION;
        }
        // As a choice (its owner is told, see setOnChoose), then as the tree's own
        doSelect(selection, new SelectionType());
    }

    // Chooses an item, by the user or the tree: in a tick tree, ticks it
    private void doSelect(final ExplorerNode row, final SelectionType selectionType) {
        if (tickBoxSelectionModel != null) {
            toggleTick(row);
            reload();
        } else {
            choose(row, selectionType);
        }
        if (onChoose != null) {
            onChoose.accept(row, selectionType);
        }
    }

    private void choose(final ExplorerNode row, final SelectionType selectionType) {
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

            MultiSelectEvent.fire(this, selectionType);
        }
    }

    private void toggleTick(final ExplorerNode node) {
        if (node != null) {
            tickBoxSelectionModel.setSelected(node, tickBoxSelectionModel.getState(node) != TickBoxState.TICK);
        }
    }

    private void selectAll() {
        if (tickBoxSelectionModel != null && rootNodes != null && rootNodes.size() == 1) {
            toggleTick(rootNodes.get(0));
            reload();
        }
    }

    /// Shows an item's menu: by the pointer for a right click, else by the item.
    ///
    /// @param item        The item.
    /// @param nativeEvent The right click's mouse down, or null for a key.
    private void showMenu(final ExplorerNode item, final NativeEvent nativeEvent) {
        // A tick tree has no item menus
        if (tickBoxSelectionModel != null) {
            return;
        }
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
                        ExplorerTree.this,
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
    private boolean actOnItem(final ExplorerNode item, final Action action) {
        if (selectionModel == null || getHandlerCount(ExplorerItemKeyEvent.getType()) == 0) {
            return false;
        }
        chooseToActOn(item, tree.getVisibleItems().indexOf(item), null);
        Scheduler.get().scheduleDeferred(() ->
                ExplorerItemKeyEvent.fire(ExplorerTree.this, action, this::announce));
        return true;
    }

    // Says something to screen reader users, emptying the region first so a repeat is said again
    private void announce(final String message) {
        liveRegion.setInnerText("");
        Scheduler.get().scheduleDeferred(() -> liveRegion.setInnerText(message));
    }


    // --------------------------------------------------------------------------------


    /// The explorer's choosing, for the tree's keyboard and the mouse.
    private class ExplorerTreeSelectionEventManager extends DataGridSelectionEventManager<ExplorerNode> {

        private boolean selectionFollowsFocus;

        ExplorerTreeSelectionEventManager(final DataGrid<ExplorerNode> tree,
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
                    if (onToggleOpen != null) {
                        onToggleOpen.accept(selectedItem);
                    }
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


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ExplorerTree> {

    }
}
