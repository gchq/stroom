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

import stroom.widget.contextmenu.client.ContextMenuManager;
import stroom.widget.contextmenu.client.TextEditTarget;
import stroom.widget.datagrid.client.model.ColumnModel;
import stroom.widget.datagrid.client.model.PageState;
import stroom.widget.popup.client.PopupPosition;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.cell.client.Cell.Context;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.view.client.CellPreviewEvent;

import java.util.Set;

/// Where an event on a [DataGrid] goes: to its keyboard, its context menu, a header (and its sort), the
/// selection manager (as a [CellPreviewEvent]) and the cell it happened in. Split out of the grid, as
/// its keyboard ([DataGridKeyboard]), column interactions ([ColumnInteractions]) and context menu
/// ([DataGridContextMenu]) are.
///
/// @param <R> The row type.
final class GridEvents<R> {

    private static final String DATA_ROW = GridHtml.DATA_ROW;
    private static final String DATA_COL = GridHtml.DATA_COL;
    private static final String DATA_HCOL = GridHtml.DATA_HCOL;

    private final DataGrid<R> grid;
    private final PageState<R> pageState;
    private final ColumnModel<Column<R, ?>> columnModel;
    private final GridRows<R> gridRows;
    private final DataGridKeyboard<R> keyboard;
    private final DataGridContextMenu<R> contextMenu;
    private final OverlayScrollbars scrollbars;
    private final ColumnInteractions columnInteractions;

    /// @param grid               The grid.
    /// @param pageState          Its rows.
    /// @param columnModel        Its columns.
    /// @param gridRows           Its drawn rows.
    /// @param keyboard           Its keyboard.
    /// @param contextMenu        Its context menu.
    /// @param scrollbars         Its scrollbars, whose events are their own.
    /// @param columnInteractions Its column resizing and moving, whose events are their own.
    GridEvents(final DataGrid<R> grid,
               final PageState<R> pageState,
               final ColumnModel<Column<R, ?>> columnModel,
               final GridRows<R> gridRows,
               final DataGridKeyboard<R> keyboard,
               final DataGridContextMenu<R> contextMenu,
               final OverlayScrollbars scrollbars,
               final ColumnInteractions columnInteractions) {
        this.grid = grid;
        this.pageState = pageState;
        this.columnModel = columnModel;
        this.gridRows = gridRows;
        this.keyboard = keyboard;
        this.contextMenu = contextMenu;
        this.scrollbars = scrollbars;
        this.columnInteractions = columnInteractions;
    }

    /// Hands an event on the grid to whatever it is for: the keyboard, the context menu, a
    /// header, the selection manager (as a cell preview) and the cell it happened in.
    ///
    /// @param event The browser event.
    void dispatch(final NativeEvent event) {
        if (gridRows.isWriting()) {
            return;
        }
        final EventTarget eventTarget = event.getEventTarget();
        if (!Element.is(eventTarget)) {
            return;
        }
        final Element target = Element.as(eventTarget);
        if (scrollbars.contains(target) || columnInteractions.contains(target)) {
            return;
        }
        if (BrowserEvents.KEYDOWN.equals(event.getType())) {
            if (keyboard.onKeyDown(event)) {
                return;
            }
        }
        if (BrowserEvents.MOUSEDOWN.equals(event.getType())) {
            keyboard.onMouseDown(target);
        }
        if (BrowserEvents.CONTEXTMENU.equals(event.getType())) {
            onContextMenu(event, target);
            return;
        }

        final Element headerCell = grid.findAncestorWithAttribute(target, DATA_HCOL);
        if (headerCell != null) {
            onHeaderEvent(event, target, headerCell);
            return;
        }

        final String type = event.getType();
        final Element rowElement = grid.findAncestorWithAttribute(target, DATA_ROW);
        int rowIndex = rowElement != null
                ? DataGrid.parseInt(rowElement.getAttribute(DATA_ROW))
                : -1;
        if (rowIndex == -1 && isKeyEvent(type)) {
            rowIndex = grid.getKeyboardSelectedRow();
        }
        if (rowIndex < 0 || rowIndex >= pageState.getRowDataSize()) {
            if (isKeyEvent(type) && pageState.getRowDataSize() > 0) {
                // Let the selection manager handle keys when nothing is selected yet.
                firePreview(event, 0, -1, null);
            }
            return;
        }

        final Element cellElement = grid.findAncestorWithAttribute(target, DATA_COL);
        final int columnIndex = cellElement != null
                ? DataGrid.parseInt(cellElement.getAttribute(DATA_COL))
                : 0;
        final Element cellContent = cellElement != null
                ? cellElement.getFirstChildElement()
                : null;
        final R value = pageState.getRow(rowIndex);
        final Context context = new Context(pageState.getPageStart() + rowIndex,
                columnIndex,
                grid.getKey(value, pageState.getPageStart() + rowIndex));

        final boolean canceled = firePreview(event, rowIndex, columnIndex, cellContent);
        if (!canceled && cellContent != null && columnIndex < columnModel.size()) {
            final Column<R, ?> column = columnModel.getColumn(columnIndex);
            if (consumesEvent(column.getCell(), type)) {
                final String before = cellContent.getInnerHTML();
                column.onBrowserEvent(context, cellContent, value, event);
                // A cell may rewrite its own DOM when it handles an event (e.g. TickBoxCell shows
                // the new tick at once), so the stored HTML no longer matches it. Forget that cell's,
                // so it is written again the next time its row is drawn (once the focus has left it),
                // as GWT would redraw it. Only if it did: a cell that just took the focus (a
                // selection box) has nothing to write again.
                if (!before.equals(cellContent.getInnerHTML())) {
                    gridRows.forgetCell(rowIndex, columnIndex);
                }
            }
        }
    }

    private void onContextMenu(final NativeEvent event, final Element target) {
        if (ContextMenuManager.isLeftToBrowser(event)) {
            // e.g. a Shift+right-click shows the browser's own menu, as everywhere in Stroom
            return;
        }
        // A right-click on a cell's text field (or the header's filter) starts with the edit items
        final TextEditTarget textField = ContextMenuManager.findEditTarget(event);
        if (!grid.hasContextMenu()) {
            if (textField == null) {
                event.preventDefault();
                event.stopPropagation();
            }
            // else the document's ContextMenuManager shows the edit menu alone
            return;
        }
        // Otherwise the grid's menu, never the browser's
        event.preventDefault();
        event.stopPropagation();
        int rowIndex = -1;
        int columnIndex = -1;
        final Element cellElement = grid.findAncestorWithAttribute(target, DATA_COL);
        final Element rowElement = grid.findAncestorWithAttribute(target, DATA_ROW);
        if (cellElement != null && rowElement != null) {
            rowIndex = DataGrid.parseInt(rowElement.getAttribute(DATA_ROW));
            columnIndex = DataGrid.parseInt(cellElement.getAttribute(DATA_COL));
            if (rowIndex >= pageState.getRowDataSize() || columnIndex >= columnModel.size()) {
                rowIndex = -1;
                columnIndex = -1;
            }
        }
        final PopupPosition position = textField != null
                ? ContextMenuManager.getPosition(event, textField)
                : new PopupPosition(event.getClientX(), event.getClientY());
        contextMenu.show(position, rowIndex, columnIndex, target, textField);
    }

    private boolean firePreview(final NativeEvent event,
                                final int rowIndex,
                                final int columnIndex,
                                final Element cellContent) {
        final R value = pageState.getRow(rowIndex);
        final int absIndex = pageState.getPageStart() + rowIndex;
        final Context context = new Context(absIndex, Math.max(0, columnIndex), grid.getKey(value, absIndex));
        boolean isEditing = false;
        if (cellContent != null && columnIndex >= 0 && columnIndex < columnModel.size() && value != null) {
            isEditing = isCellEditing(columnModel.getColumn(columnIndex), context, cellContent, value);
        }
        final CellPreviewEvent<R> preview = CellPreviewEvent.fire(grid,
                event,
                grid,
                context,
                value,
                isEditing,
                false);
        return preview.isCanceled();
    }

    @SuppressWarnings("unchecked")
    private <C> boolean isCellEditing(final Column<R, C> column,
                                      final Context context,
                                      final Element parent,
                                      final R row) {
        return column.getCell().isEditing(context, parent, column.getValue(row));
    }

    private void onHeaderEvent(final NativeEvent event, final Element target, final Element headerCell) {
        final int columnIndex = DataGrid.parseInt(headerCell.getAttribute(DATA_HCOL));
        if (columnIndex < 0 || columnIndex >= columnModel.size()) {
            return;
        }
        final Column<R, ?> column = columnModel.getColumn(columnIndex);
        final Header<?> header = grid.getHeader(columnIndex);
        final String type = event.getType();
        // The element the header was rendered into, as GWT's grid passes it: the name holder of a
        // sortable header, otherwise the header cell. Cells such as FilterCell find their input in it.
        final Element outer = headerCell.getFirstChildElement();
        final Element headerContent = column.isSortable() && outer != null && outer.getFirstChildElement() != null
                ? outer.getFirstChildElement()
                : headerCell;
        final Context context = new Context(0, columnIndex, header != null
                ? header.getKey()
                : null);

        if (header != null && consumesEvent(header.getCell(), type)) {
            header.onBrowserEvent(context, headerContent, event);
        }

        final boolean isSortClick = BrowserEvents.CLICK.equals(type);
        if (isSortClick && column.isSortable() && !isEditableTarget(target)) {
            boolean shouldSort = true;
            if (header != null) {
                shouldSort = header.onPreviewColumnSortEvent(context, headerContent, event);
            }
            if (shouldSort) {
                grid.onHeaderSortClick(column, DataGridDom.isCtrl(event));
            }
        }
    }

    private static boolean consumesEvent(final Cell<?> cell, final String type) {
        if (cell == null) {
            return false;
        }
        final Set<String> consumed = cell.getConsumedEvents();
        return consumed != null && consumed.contains(type);
    }

    private static boolean isKeyEvent(final String type) {
        return BrowserEvents.KEYDOWN.equals(type)
               || BrowserEvents.KEYUP.equals(type)
               || BrowserEvents.KEYPRESS.equals(type);
    }

    private static boolean isEditableTarget(final Element target) {
        final String tag = target.getTagName();
        return "input".equalsIgnoreCase(tag)
               || "textarea".equalsIgnoreCase(tag)
               || "select".equalsIgnoreCase(tag)
               || "button".equalsIgnoreCase(tag);
    }
}
