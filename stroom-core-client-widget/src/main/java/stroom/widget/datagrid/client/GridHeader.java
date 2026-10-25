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

import stroom.widget.datagrid.client.model.ColumnModel;
import stroom.widget.util.client.CssProperties;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.cellview.client.Column;

/// Draws a grid's header row, keeping the focus on a column's header, or a control in one (with
/// its caret), when the header is drawn again.
///
/// @param <R> The type of the grid's rows.
final class GridHeader<R> {

    private final DataGrid<R> grid;
    private final ColumnModel<Column<R, ?>> columnModel;
    private final GridHtml<R> html;
    private final DataGridKeyboard<R> keyboard;
    private final Element head;
    private final Element headerRow;

    GridHeader(final DataGrid<R> grid,
               final ColumnModel<Column<R, ?>> columnModel,
               final GridHtml<R> html,
               final DataGridKeyboard<R> keyboard,
               final Element head,
               final Element headerRow) {
        this.grid = grid;
        this.columnModel = columnModel;
        this.html = html;
        this.keyboard = keyboard;
        this.head = head;
        this.headerRow = headerRow;
    }

    /// Draws the header row; a hidden header is hidden by the grid's `data-grid--no-header`
    /// (data-grid.css).
    void render() {
        final Element active = DataGridDom.getActiveElement();
        final boolean headerFocused = active != null && active.hasAttribute(GridHtml.DATA_HCOL)
                                      && headerRow.isOrHasChild(active);
        // A control in a column's header with the focus, e.g. the dashboard's filter box
        final Element controlHeader = !headerFocused && active != null && headerRow.isOrHasChild(active)
                ? grid.findAncestorWithAttribute(active, GridHtml.DATA_HCOL)
                : null;
        final int controlIndex = controlHeader != null
                ? DataGridDom.controlIndex(controlHeader, active)
                : -1;
        final int selectionStart = controlIndex >= 0
                ? DataGridDom.getSelectionStart(active)
                : -1;
        final int selectionEnd = controlIndex >= 0
                ? DataGridDom.getSelectionEnd(active)
                : -1;
        headerRow.setInnerSafeHtml(html.header());
        // With no columns (e.g. a query's table before its first results) the header row holds no
        // column headers, and a row must hold cells, so it is no row until it does
        if (columnModel.size() > 0) {
            headerRow.setAttribute("role", "row");
        } else {
            headerRow.removeAttribute("role");
        }
        // A column's header with the keyboard focus (e.g. sorted with Enter) keeps it when the
        // header is drawn again
        if (headerFocused) {
            final Element target = keyboard.getFocusTarget();
            if (target != null) {
                grid.refocus(target);
            }
        } else if (controlIndex >= 0) {
            // As does a control in it, with its caret, e.g. a filter box after Enter set the filter
            final Element cell = getCellElement(DataGrid.parseInt(
                    controlHeader.getAttribute(GridHtml.DATA_HCOL)));
            final Element control = cell != null
                    ? DataGridDom.controlAt(cell, controlIndex)
                    : null;
            if (control != null) {
                grid.refocus(control);
                DataGridDom.setSelectionRange(control, selectionStart, selectionEnd);
            }
        }
    }

    /// GWT's DataGrid lays its body out below the header's offsetHeight, a whole number of pixels,
    /// so the rows start on a whole pixel. This does the same, so rows line up with `MyDataGrid`'s
    /// exactly.
    ///
    /// @param hidden Whether the header is hidden (when there is nothing to snap).
    void snapHeight(final boolean hidden) {
        if (!grid.isAttached() || hidden) {
            return;
        }
        CssProperties.remove(head, "--data-grid__head-height");
        final int height = head.getOffsetHeight();
        // A grid that is not displayed has no height to snap to.
        if (height > 0) {
            CssProperties.set(head, "--data-grid__head-height", height + "px");
        }
    }

    /// @param column The column index.
    /// @return The header cell element, or `null` if the header has not been rendered.
    Element getCellElement(final int column) {
        for (Element child = headerRow.getFirstChildElement();
                child != null;
                child = child.getNextSiblingElement()) {
            if (String.valueOf(column).equals(child.getAttribute(GridHtml.DATA_HCOL))) {
                return child;
            }
        }
        return null;
    }
}
