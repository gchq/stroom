/*
 * Copyright 2026 Crown Copyright
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

package stroom.widget.datagrid.client;

import stroom.widget.util.client.TypedKey;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;

import java.util.List;

/// The keyboard of a [DataGrid]: the WAI-ARIA grid pattern, with the row focus of its treegrid
/// example, as most of Stroom's grids are lists whose rows are chosen.
///
/// The grid is one tab stop, the row, cell or control with the focus (a roving tab index). On a row,
/// Up and Down (and Page Up, Page Down, Home and End) move between the rows, and Right moves into
/// its first cell. On a cell, Left and Right move between the cells (Left from the first goes back
/// to the row), Up and Down to the same column of the row above or below (Up from the first row to
/// the column's header), and Home and End to the first or last cell.
///
/// Where the focus lands in a cell depends on what it holds:
///
/// - a control that doesn't use the arrow keys (a tick box, a button or link, a selection box,
///   whose list opens on Enter, Space or Alt+Down): the control, whose own keys work;
/// - a text field or a value spinner, which use the arrow keys: the cell. Enter or F2 starts
///   editing, putting the focus in the field; Enter then commits and Escape cancels, both going
///   back to the cell; Tab commits and edits the next cell holding a control (the next row's first
///   at the end of a row, leaving the grid after the last), Shift+Tab the one before. Tab goes on
///   from a tick box or selection box reached that way too, until an arrow key, Escape (back to
///   the cell) or a click ends the editing. A click in the field edits it at once;
/// - nothing to work: the cell.
///
/// The selection follows the row focus: Up and Down choose the row they move to (in a grid that
/// chooses several rows, Shift extends the choice and Ctrl moves without choosing), and Space
/// chooses the focused row (toggling it in a grid that chooses several). The focus on a cell never
/// changes the selection.
///
/// A column's header (`columnheader`) is reached with Up from the first row's cell in its column,
/// and left with Down; Left and Right move along the headers. Enter or Space on it does what a click
/// does: it sorts a sortable column, or opens the column's menu where the grid has one; the context
/// menu key or Shift+F10 opens the menu too.
///
/// The grid hands this its key events as they happen ([DataGrid] handles events
/// synchronously), before the selection manager and the cells: a key this acts on is cancelled and
/// goes no further.
///
/// @param <R> The type of the grid's rows.
class DataGridKeyboard<R> {

    private static final int PAGE_SIZE = 10;
    private static final int KEY_F2 = 113;
    private static final int KEY_F10 = 121;
    private static final int KEY_CONTEXT_MENU = 93;

    private final DataGrid<R> grid;
    private Selector<R> selector;
    // The column with the focus (a cell's, or a header's), or -1 when a row has it
    private int column = -1;
    // Whether a column's header has the focus
    private boolean header;
    // Whether the focus is in a cell's text field or value spinner
    private boolean editing;
    // Whether Tab goes from control to control: set when editing starts, and when Tab while
    // editing reaches a tick box or selection box, until the arrows, Escape or a click end it
    private boolean tabbingControls;
    // The edited field's value when editing started, which Escape puts back
    private String valueBeforeEditing;

    DataGridKeyboard(final DataGrid<R> grid) {
        this.grid = grid;
    }

    /// Sets what moving the focus and Space and Enter do to the grid's selection.
    ///
    /// @param selector The grid's selection manager, or null if its rows can't be chosen.
    void setSelector(final Selector<R> selector) {
        this.selector = selector;
    }

    /// @return The element that should have the focus (and be the grid's tab stop): a column's
    /// header, the keyboard row, or its cell or the cell's control; null if there is no such row.
    Element getFocusTarget() {
        if (header) {
            final Element headerCell = grid.getHeaderCellElement(column);
            if (headerCell != null) {
                return headerCell;
            }
        }
        final Element row = grid.getRowElement(grid.getKeyboardSelectedRow());
        if (row == null) {
            return null;
        }
        final Element cell = column >= 0
                ? cellOf(row, column)
                : null;
        if (cell == null) {
            return row;
        }
        if (editing) {
            final Element field = editableControl(cell);
            if (field != null) {
                return field;
            }
        }
        final Element control = directControl(cell);
        return control != null
                ? control
                : cell;
    }

    /// Makes the focus target the grid's one tab stop. The rows, headers and controls stay focusable
    /// (by a click or a script) but out of the tab order, and the cells aren't focusable at all; with
    /// no rows, the scrolling box is the tab stop, so the grid can still be scrolled.
    ///
    /// @param scroller The grid's scrolling box.
    void updateTabStops(final Element scroller) {
        final Element target = getFocusTarget();
        clearTabStops(grid.getElement(), target);
        if (target != null) {
            target.setTabIndex(0);
            scroller.setTabIndex(-1);
        } else {
            scroller.setTabIndex(0);
        }
    }

    // --------------------------------------------------------------------------------
    // Events from the grid

    /// The focus has come to an element in the grid: the keyboard follows it, e.g. to a control
    /// clicked.
    ///
    /// @param target The element with the focus.
    void onFocusIn(final Element target) {
        final Element headerCell = grid.findAncestorWithAttribute(target, GridHtml.DATA_HCOL);
        if (headerCell != null) {
            header = true;
            editing = false;
            column = DataGrid.parseInt(headerCell.getAttribute(GridHtml.DATA_HCOL));
            return;
        }
        final Element row = grid.findAncestorWithAttribute(target, GridHtml.DATA_ROW);
        if (row == null) {
            return;
        }
        header = false;
        if (target == row) {
            column = -1;
            editing = false;
        } else {
            final Element cell = grid.findAncestorWithAttribute(target, GridHtml.DATA_COL);
            if (cell == null) {
                return;
            }
            column = DataGrid.parseInt(cell.getAttribute(GridHtml.DATA_COL));
            final Element field = editableControl(cell);
            final boolean nowEditing = field != null && field.isOrHasChild(target);
            if (nowEditing && !editing) {
                valueBeforeEditing = inputValue(field);
            }
            editing = nowEditing;
        }
        final int rowIndex = DataGrid.parseInt(row.getAttribute(GridHtml.DATA_ROW));
        if (rowIndex != grid.getKeyboardSelectedRow()) {
            grid.setKeyboardSelectedRow(rowIndex, false);
        }
    }

    /// A press on a row's cells (not a control in one) chooses the row and gives it the focus: the
    /// keyboard goes back to the row.
    ///
    /// @param target The element pressed.
    void onMouseDown(final Element target) {
        tabbingControls = false;
        final Element cell = grid.findAncestorWithAttribute(target, GridHtml.DATA_COL);
        if (cell != null && !isInControl(cell, target)) {
            column = -1;
            header = false;
            editing = false;
        }
    }

    /// A key pressed in the grid.
    ///
    /// @param event The key down event.
    /// @return Whether the keyboard acted on the key, which is then cancelled and goes no further.
    boolean onKeyDown(final NativeEvent event) {
        final Element target = event.getEventTarget().cast();
        if (header && grid.findAncestorWithAttribute(target, GridHtml.DATA_HCOL) != null) {
            return onHeaderKeyDown(event, target);
        }
        final Element row = grid.findAncestorWithAttribute(target, GridHtml.DATA_ROW);
        if (row == null) {
            // The scrolling box is the tab stop while the keyboard row isn't drawn (scrolled away
            // in a grid that draws only the rows in view): keys there act on the keyboard row
            return grid.isScroller(target) && onScrollerKeyDown(event);
        }
        final int rowIndex = DataGrid.parseInt(row.getAttribute(GridHtml.DATA_ROW));
        if (editing) {
            return onEditingKeyDown(event, rowIndex);
        }
        if (event.getAltKey() || DataGridDom.isOtherMeta(event)) {
            return false;
        }
        final int keyCode = event.getKeyCode();
        final boolean shift = event.getShiftKey();
        final boolean ctrl = DataGridDom.isCtrl(event);
        if (tabbingControls && column >= 0) {
            if (keyCode == KeyCodes.KEY_TAB && !ctrl) {
                return onEditingTab(event, rowIndex);
            } else if (keyCode == KeyCodes.KEY_ESCAPE) {
                handled(event);
                event.stopPropagation();
                focusCell(rowIndex, column);
                return true;
            }
        }

        final Element cell = column >= 0
                ? grid.findAncestorWithAttribute(target, GridHtml.DATA_COL)
                : null;
        final boolean tree = grid.getProfile() == DataGridProfile.TREE;
        if (tree && "*".equals(TypedKey.keyOf(event))) {
            // A tree's * expands the row's collapsed siblings, and the row
            handled(event);
            grid.getTreeRows().expandSiblings(rowIndex);
            return true;
        }
        if (!grid.getProfile().hasCells()
            && (keyCode == KEY_CONTEXT_MENU || (keyCode == KEY_F10 && shift))
            && selector != null
            && selector.onContextMenu(item(rowIndex))) {
            // A list's or tree's row's menu, from the keyboard
            handled(event);
            return true;
        }
        switch (keyCode) {
            case KeyCodes.KEY_UP:
                handled(event);
                if (column >= 0 && rowIndex == 0) {
                    focusHeader(column);
                } else if (rowIndex > 0 || !upFromFirst()) {
                    move(rowIndex - 1, shift, ctrl);
                }
                return true;
            case KeyCodes.KEY_DOWN:
                handled(event);
                move(rowIndex + 1, shift, ctrl);
                return true;
            case KeyCodes.KEY_PAGEUP:
                handled(event);
                move(rowIndex - PAGE_SIZE, shift, ctrl);
                return true;
            case KeyCodes.KEY_PAGEDOWN:
                handled(event);
                move(rowIndex + PAGE_SIZE, shift, ctrl);
                return true;
            case KeyCodes.KEY_HOME:
                handled(event);
                if (column >= 0 && !ctrl) {
                    focusCell(rowIndex, firstColumn(row));
                } else {
                    move(0, shift, ctrl && column < 0);
                }
                return true;
            case KeyCodes.KEY_END:
                handled(event);
                if (column >= 0 && !ctrl) {
                    focusCell(rowIndex, lastColumn(row));
                } else {
                    move(grid.getVisibleItemCount() - 1, shift, ctrl && column < 0);
                }
                return true;
            case KeyCodes.KEY_RIGHT:
                handled(event);
                // A treegrid's row: Right expands a collapsed row, and otherwise moves into its cells
                if (column < 0 && grid.getTreeRows().setExpanded(rowIndex, true)) {
                    return true;
                }
                if (tree) {
                    // A tree's expanded row: Right moves to its first child
                    final int child = grid.getTreeRows().getFirstChildRow(rowIndex);
                    if (child >= 0) {
                        move(child, false, false);
                    }
                    return true;
                }
                if (!grid.getProfile().hasCells()) {
                    // A list's row is its only stop; Right may change page (a find dialog's)
                    if (selector != null) {
                        selector.onRight();
                    }
                    return true;
                }
                final int right = nextColumn(row, column);
                if (right >= 0) {
                    focusCell(rowIndex, right);
                }
                return true;
            case KeyCodes.KEY_LEFT:
                handled(event);
                if (column >= 0) {
                    focusCell(rowIndex, previousColumn(row, column));
                } else if (!grid.getTreeRows().setExpanded(rowIndex, false)) {
                    // A treegrid's row: Left collapses an expanded row, and otherwise moves to its
                    // parent row (choosing it, as Up and Down do)
                    final int parent = grid.getTreeRows().getParentRow(rowIndex);
                    if (parent >= 0) {
                        move(parent, false, false);
                    } else if (grid.getProfile() == DataGridProfile.LISTBOX && selector != null) {
                        // A list's Left may change page (a find dialog's)
                        selector.onLeft();
                    }
                }
                return true;
            case KeyCodes.KEY_SPACE:
                // Space never scrolls the page: on a row it chooses the row, on a control it is the
                // control's (a tick box ticks), and on a cell it does nothing
                event.preventDefault();
                if (column < 0) {
                    handled(event);
                    if (selector != null) {
                        selector.onSpace(item(rowIndex), shift, ctrl);
                    }
                    return true;
                }
                return cell != null && target == cell;
            case KeyCodes.KEY_ENTER:
            case KEY_F2:
                if (column < 0) {
                    if (keyCode == KeyCodes.KEY_ENTER && !ctrl && selector != null) {
                        handled(event);
                        selector.onEnter(item(rowIndex));
                        return true;
                    }
                } else if (cell != null && target == cell) {
                    final Element field = editableControl(cell);
                    if (field != null) {
                        handled(event);
                        startEditing(field);
                        return true;
                    }
                }
                return false;
            case KeyCodes.KEY_A:
                if (ctrl && !shift && selector != null) {
                    handled(event);
                    selector.onSelectAll();
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    /// Moves the focus to a row, choosing it as Up and Down do, e.g. the row typing went to.
    ///
    /// @param rowIndex The page-relative row index.
    void moveTo(final int rowIndex) {
        header = false;
        column = -1;
        editing = false;
        move(rowIndex, false, false);
    }

    // Up on the first row: the selection manager may take the focus elsewhere, e.g. a picker's
    // filter box above its list
    private boolean upFromFirst() {
        return selector != null && selector.onUpFromFirst();
    }

    // A key on the scrolling box: a row key moves from the keyboard row, as on the row itself
    private boolean onScrollerKeyDown(final NativeEvent event) {
        if (grid.getVisibleItemCount() == 0 || event.getAltKey() || DataGridDom.isOtherMeta(event)) {
            return false;
        }
        header = false;
        column = -1;
        editing = false;
        final int rowIndex = Math.max(0, grid.getKeyboardSelectedRow());
        final boolean shift = event.getShiftKey();
        final boolean ctrl = DataGridDom.isCtrl(event);
        switch (event.getKeyCode()) {
            case KeyCodes.KEY_UP:
                handled(event);
                if (rowIndex > 0 || !upFromFirst()) {
                    move(rowIndex - 1, shift, ctrl);
                }
                return true;
            case KeyCodes.KEY_DOWN:
                handled(event);
                move(rowIndex + 1, shift, ctrl);
                return true;
            case KeyCodes.KEY_PAGEUP:
                handled(event);
                move(rowIndex - PAGE_SIZE, shift, ctrl);
                return true;
            case KeyCodes.KEY_PAGEDOWN:
                handled(event);
                move(rowIndex + PAGE_SIZE, shift, ctrl);
                return true;
            case KeyCodes.KEY_HOME:
                handled(event);
                move(0, shift, ctrl);
                return true;
            case KeyCodes.KEY_END:
                handled(event);
                move(grid.getVisibleItemCount() - 1, shift, ctrl);
                return true;
            case KeyCodes.KEY_RIGHT:
            case KeyCodes.KEY_LEFT:
            case KeyCodes.KEY_SPACE:
                // Back to the keyboard row, where the key can be pressed again
                handled(event);
                move(rowIndex, false, true);
                return true;
            default:
                return false;
        }
    }

    // A column's header: Left and Right move along the headers, Down goes to the first row's cell
    // in the column; Enter and Space are a click, and the context menu key and Shift+F10 open the
    // column's menu. Keys on a control in the header, e.g. a filter's text box or button, are left
    // to it
    private boolean onHeaderKeyDown(final NativeEvent event, final Element target) {
        if (!target.hasAttribute(GridHtml.DATA_HCOL) || event.getAltKey() || DataGridDom.isOtherMeta(event)) {
            return false;
        }
        final int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyCodes.KEY_DOWN:
                handled(event);
                if (grid.getVisibleItemCount() > 0) {
                    grid.scrollRowIntoView(0);
                    focusCell(0, column);
                }
                return true;
            case KeyCodes.KEY_LEFT:
                handled(event);
                focusHeader(headerColumnBefore(grid.getHeaderRowElement(), column));
                return true;
            case KeyCodes.KEY_RIGHT:
                handled(event);
                focusHeader(headerColumnAfter(grid.getHeaderRowElement(), column));
                return true;
            case KeyCodes.KEY_HOME:
                handled(event);
                focusHeader(headerColumnAfter(grid.getHeaderRowElement(), -1));
                return true;
            case KeyCodes.KEY_END:
                handled(event);
                focusHeader(headerColumnBefore(grid.getHeaderRowElement(), grid.getColumnCount()));
                return true;
            case KeyCodes.KEY_ENTER:
            case KeyCodes.KEY_SPACE:
                handled(event);
                grid.activateHeader(column, event);
                return true;
            case KEY_CONTEXT_MENU:
            case KEY_F10:
                if (keyCode == KEY_F10 && !event.getShiftKey()) {
                    return false;
                }
                handled(event);
                grid.showHeaderMenu(column, event);
                return true;
            default:
                return false;
        }
    }

    // While a cell's field is edited its keys are its own, but for those that end editing
    private boolean onEditingKeyDown(final NativeEvent event, final int rowIndex) {
        final int keyCode = event.getKeyCode();
        if (keyCode == KeyCodes.KEY_ESCAPE) {
            // Escape leaves editing (putting the value back) before it closes a dialog
            handled(event);
            event.stopPropagation();
            setInputValue(event.getEventTarget().cast(), valueBeforeEditing);
            focusCell(rowIndex, column);
            return true;
        } else if (keyCode == KeyCodes.KEY_ENTER
                   && !event.getAltKey() && !event.getCtrlKey() && !event.getMetaKey()) {
            // Enter commits (the field's change, as it loses the focus) and goes back to the cell,
            // rather than pressing a dialog's default button
            handled(event);
            event.stopPropagation();
            focusCell(rowIndex, column);
            return true;
        } else if (keyCode == KeyCodes.KEY_TAB && !event.getAltKey() && !event.getCtrlKey()) {
            return onEditingTab(event, rowIndex);
        }
        return false;
    }

    // Tab edits the next cell holding a control, Shift+Tab the one before; with none, the focus
    // leaves the grid as Tab would leave it from the grid's tab stop
    private boolean onEditingTab(final NativeEvent event, final int rowIndex) {
        final int[] next = nextControlCell(rowIndex, column, event.getShiftKey());
        if (next == null) {
            leaveByTab();
            return false;
        }
        handled(event);
        final Element cell = cellOf(grid.getRowElement(next[0]), next[1]);
        final Element field = editableControl(cell);
        if (field != null) {
            moveKeyboardSelection(next[0], next[1]);
            startEditing(field);
        } else {
            focusCell(next[0], next[1]);
            tabbingControls = true;
        }
        return true;
    }

    // --------------------------------------------------------------------------------
    // Moving the focus

    // Moves to a row, keeping to the row or the column with the focus; on a row, the selection
    // follows (see the class comment). The grid scrolls the row into view and gives it the focus
    // once it is drawn (it may not be yet, in a grid drawing only the rows in view)
    private void move(final int rowIndex, final boolean shift, final boolean ctrl) {
        final int count = grid.getVisibleItemCount();
        if (count == 0) {
            return;
        }
        final int to = Math.max(0, Math.min(count - 1, rowIndex));
        header = false;
        editing = false;
        tabbingControls = false;
        grid.setKeyboardSelectedRow(to, true);
        if (column < 0 && selector != null) {
            selector.onFocusRow(item(to), shift, ctrl);
        }
    }

    // Gives the focus to a row's cell (or the row itself, column -1), which is drawn
    private void focusCell(final int rowIndex, final int columnIndex) {
        moveKeyboardSelection(rowIndex, columnIndex);
        editing = false;
        tabbingControls = false;
        focusTarget();
    }

    // Gives the focus to a column's header, unless the grid hides its header
    private void focusHeader(final int columnIndex) {
        if (columnIndex < 0
            || grid.getHeaderRowElement() == null
            || grid.getHeaderCellElement(columnIndex) == null) {
            return;
        }
        header = true;
        column = columnIndex;
        editing = false;
        tabbingControls = false;
        focusTarget();
    }

    private void moveKeyboardSelection(final int rowIndex, final int columnIndex) {
        header = false;
        column = columnIndex;
        if (rowIndex != grid.getKeyboardSelectedRow()) {
            grid.setKeyboardSelectedRow(rowIndex, false);
        }
    }

    // Focuses the target, scrolling it into view: across, and down or up clear of the header
    private void focusTarget() {
        grid.updateTabStops();
        final Element target = getFocusTarget();
        if (target != null) {
            focusVisible(target);
            grid.scrollIntoViewHorizontally(target);
            if (!header) {
                grid.scrollRowIntoView(grid.getKeyboardSelectedRow());
            }
        }
    }

    private void startEditing(final Element field) {
        editing = true;
        tabbingControls = true;
        valueBeforeEditing = inputValue(field);
        grid.updateTabStops();
        focusVisible(field);
        grid.scrollIntoViewHorizontally(field);
        grid.scrollRowIntoView(grid.getKeyboardSelectedRow());
        selectText(field);
    }

    // The cell holding a control after (or before) the one given, in reading order, as
    // {row, column}, or null
    private int[] nextControlCell(final int rowIndex, final int columnIndex, final boolean back) {
        int r = rowIndex;
        Element row = grid.getRowElement(r);
        int c = columnIndex;
        while (row != null) {
            c = back
                    ? previousColumn(row, c)
                    : nextColumn(row, c);
            if (c < 0) {
                r += back
                        ? -1
                        : 1;
                row = grid.getRowElement(r);
                if (row == null) {
                    return null;
                }
                c = back
                        ? Integer.MAX_VALUE
                        : -1;
                continue;
            }
            final Element cell = cellOf(row, c);
            if (cell != null && (editableControl(cell) != null || formControl(cell) != null)) {
                return new int[]{r, c};
            }
        }
        return null;
    }

    // Lets Tab move the focus on from the edited field and out of the grid: nothing in the grid is a
    // tab stop until the focus has gone, when its tab stop is put back
    private void leaveByTab() {
        clearTabStops(grid.getElement(), null);
        editing = false;
        tabbingControls = false;
        Scheduler.get().scheduleDeferred(grid::updateTabStops);
    }

    private void handled(final NativeEvent event) {
        event.preventDefault();
    }

    private R item(final int rowIndex) {
        final List<R> items = grid.getVisibleItems();
        return rowIndex >= 0 && rowIndex < items.size()
                ? items.get(rowIndex)
                : null;
    }

    private static boolean isInControl(final Element cell, final Element target) {
        final Element field = editableControl(cell);
        final Element control = directControl(cell);
        return (field != null && field.isOrHasChild(target))
               || (control != null && control.isOrHasChild(target));
    }

    // --------------------------------------------------------------------------------
    // Native

    // The row's cell for a column, or null (e.g. a column under another's span)
    private static native Element cellOf(Element row, int column) /*-{
        return row ? row.querySelector(':scope > [data-col="' + column + '"]') : null;
    }-*/;

    // The cells of a row (or header cells of the header row) that the keys stop on: not those too
    // narrow to see, e.g. a dashboard table's group expander column with no groups (1px wide)
    private static native JavaScriptObject keyCells(Element row, String attribute) /*-{
        var all = row.querySelectorAll(':scope > [' + attribute + ']');
        var cells = [];
        for (var i = 0; i < all.length; i++) {
            var style = $wnd.getComputedStyle(all[i]);
            var width = all[i].getBoundingClientRect().width
                - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight);
            if (width > 2) {
                cells.push(all[i]);
            }
        }
        return cells;
    }-*/;

    // The column of the row's first or last cell
    private static native int firstColumn(Element row) /*-{
        var cells = @stroom.widget.datagrid.client.DataGridKeyboard::keyCells(*)(row, 'data-col');
        return cells.length ? parseInt(cells[0].getAttribute('data-col'), 10) : -1;
    }-*/;

    private static native int lastColumn(Element row) /*-{
        var cells = @stroom.widget.datagrid.client.DataGridKeyboard::keyCells(*)(row, 'data-col');
        return cells.length ? parseInt(cells[cells.length - 1].getAttribute('data-col'), 10) : -1;
    }-*/;

    // The column of the row's cell after (or before) the column given (-1 for before the first), or
    // -1 if there is none
    private static native int nextColumn(Element row, int column) /*-{
        var cells = @stroom.widget.datagrid.client.DataGridKeyboard::keyCells(*)(row, 'data-col');
        for (var i = 0; i < cells.length; i++) {
            var c = parseInt(cells[i].getAttribute('data-col'), 10);
            if (c > column) {
                return c;
            }
        }
        return -1;
    }-*/;

    private static native int previousColumn(Element row, int column) /*-{
        var cells = @stroom.widget.datagrid.client.DataGridKeyboard::keyCells(*)(row, 'data-col');
        for (var i = cells.length - 1; i >= 0; i--) {
            var c = parseInt(cells[i].getAttribute('data-col'), 10);
            if (c < column) {
                return c;
            }
        }
        return -1;
    }-*/;

    // The column of the header cell after the column given (-1 for before the first), or -1
    private static native int headerColumnAfter(Element headerRow, int column) /*-{
        if (!headerRow) {
            return -1;
        }
        var next = -1;
        var cells = @stroom.widget.datagrid.client.DataGridKeyboard::keyCells(*)(headerRow, 'data-hcol');
        for (var i = 0; i < cells.length; i++) {
            var c = parseInt(cells[i].getAttribute('data-hcol'), 10);
            if (c > column && (next < 0 || c < next)) {
                next = c;
            }
        }
        return next;
    }-*/;

    // The column of the header cell before the column given (the column count for after the last),
    // or -1
    private static native int headerColumnBefore(Element headerRow, int column) /*-{
        if (!headerRow) {
            return -1;
        }
        var previous = -1;
        var cells = @stroom.widget.datagrid.client.DataGridKeyboard::keyCells(*)(headerRow, 'data-hcol');
        for (var i = 0; i < cells.length; i++) {
            var c = parseInt(cells[i].getAttribute('data-hcol'), 10);
            if (c >= 0 && c < column && c > previous) {
                previous = c;
            }
        }
        return previous;
    }-*/;

    // A text field or value spinner in the cell, which uses the arrow keys, or null
    private static native Element editableControl(Element cell) /*-{
        return cell.querySelector('input:not([type="checkbox"]):not([type="radio"]):not([role="combobox"]), '
            + 'textarea');
    }-*/;

    // A control in the cell that doesn't use the arrow keys, which takes the focus itself, or null. A
    // tick box drawn only as a picture (aria-hidden, a list's option saying its state) isn't one
    private static native Element directControl(Element cell) /*-{
        return cell.querySelector('[role="combobox"], [role="checkbox"]:not([aria-hidden="true"]), button, '
            + 'a[href], [data-row-action]');
    }-*/;

    // A control in the cell that a value is entered with (Tab goes to it while editing), or null
    private static native Element formControl(Element cell) /*-{
        return cell.querySelector('[role="combobox"], '
            + '[role="checkbox"]:not([aria-readonly="true"]):not([aria-hidden="true"])');
    }-*/;

    // Leaves every row, header, cell and control but the one to keep out of the tab order: rows and
    // headers stay focusable (-1), cells aren't focusable, and controls only by a click or a script.
    // The one kept is left focusable, as an element with the focus that stops being focusable loses
    // it
    private static native void clearTabStops(Element grid, Element keep) /*-{
        var elements = grid.querySelectorAll('[data-row], [data-hcol][tabindex], [data-col][tabindex], '
            + '[data-col] input, [data-col] textarea, [data-col] select, [data-col] button, '
            + '[data-col] a[href], [data-col] [role="checkbox"]:not([aria-hidden="true"]), '
            + '[data-col] [role="combobox"], '
            + '[data-col] [tabindex]');
        for (var i = 0; i < elements.length; i++) {
            var element = elements[i];
            if (element === keep) {
                continue;
            }
            if (element.hasAttribute('data-col') || (element.tagName === 'DIV' && !element.getAttribute('role')
                && !element.hasAttribute('data-row') && !element.hasAttribute('data-hcol'))) {
                element.removeAttribute('tabindex');
            } else {
                element.tabIndex = -1;
            }
        }
    }-*/;

    private static native void focusVisible(Element element) /*-{
        element.focus({ focusVisible: true, preventScroll: true });
    }-*/;

    private static native String inputValue(Element field) /*-{
        return field.value === undefined ? null : field.value;
    }-*/;

    private static native void setInputValue(Element field, String value) /*-{
        if (value !== null && field.value !== undefined) {
            field.value = value;
        }
    }-*/;

    private static native void selectText(Element field) /*-{
        if (typeof field.select === 'function') {
            field.select();
        }
    }-*/;

    // --------------------------------------------------------------------------------

    /// What the keyboard does to a grid's selection, done by its selection manager.
    ///
    /// @param <R> The type of the grid's rows.
    interface Selector<R> {

        /// The focus has moved to a row with Up, Down, Page Up, Page Down, Home or End.
        ///
        /// @param item  The row's item.
        /// @param shift Whether Shift was held (extend the choice to the row).
        /// @param ctrl  Whether Ctrl was held (move without choosing).
        void onFocusRow(R item, boolean shift, boolean ctrl);

        /// Space was pressed on a row.
        ///
        /// @param item  The row's item.
        /// @param shift Whether Shift was held (choose the run to the row).
        /// @param ctrl  Whether Ctrl was held.
        void onSpace(R item, boolean shift, boolean ctrl);

        /// Enter was pressed on a row.
        ///
        /// @param item The row's item.
        void onEnter(R item);

        /// Ctrl+A was pressed on a row or cell.
        void onSelectAll();

        /// Up was pressed on the first row, the row itself having the focus.
        ///
        /// @return True if the selection manager moved the focus elsewhere, false to leave it on
        /// the row.
        default boolean onUpFromFirst() {
            return false;
        }

        /// The context menu key, or Shift+F10, was pressed on a list's or tree's row.
        ///
        /// @param item The row's item.
        /// @return Whether the selection manager showed a menu (the key goes no further).
        default boolean onContextMenu(final R item) {
            return false;
        }

        /// Left was pressed on a list's row (a list has no cells to move into).
        default void onLeft() {
        }

        /// Right was pressed on a list's row.
        default void onRight() {
        }
    }
}
