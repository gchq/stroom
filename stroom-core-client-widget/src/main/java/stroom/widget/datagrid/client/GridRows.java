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

import stroom.cell.tickbox.shared.TickBoxState;
import stroom.util.shared.NullSafe;
import stroom.widget.datagrid.client.model.ColumnModel;
import stroom.widget.datagrid.client.model.PageState;
import stroom.widget.datagrid.client.model.RowWritePlan;
import stroom.widget.datagrid.client.model.VirtualWindow;
import stroom.widget.util.client.CssProperties;
import stroom.widget.util.client.TypeAhead;

import com.google.gwt.cell.client.Cell.Context;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.RowStyles;
import com.google.gwt.view.client.SelectionModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/// Draws a grid's rows: each row's cells are rendered to HTML and its element written only when
/// that HTML changed, so selecting a row or moving the keyboard rewrites no cells. Rows are kept by
/// key, and put in order moving as few elements as possible. In virtual mode only the rows in view
/// (and a few more) are drawn. A cell that had the focus when its row was rewritten takes it back.
///
/// @param <R> The type of the grid's rows.
final class GridRows<R> {

    private static final String KEYBOARD_CELL_CLASS_NAME = "data-grid__cell--keyboard";
    private static final double FALLBACK_ROW_HEIGHT = 24;
    // Natural pages with more rows than this let the browser skip laying out the rows out of view
    // (data-grid--contain, see data-grid.css)
    private static final int CONTAIN_ROW_THRESHOLD = 200;

    private final DataGrid<R> grid;
    private final PageState<R> pageState;
    private final ColumnModel<Column<R, ?>> columnModel;
    private final GridHtml<R> html;
    private final TreeRows<R> tree;
    private final DataGridKeyboard<R> keyboard;
    private final Element scroller;
    private final Element content;
    private final Element body;

    // Rendered rows by key, and the same rows by page-relative index.
    private final Map<RowKey, RenderedRow> renderedRows = new HashMap<>();
    private final Map<Integer, RenderedRow> renderedRowsByIndex = new HashMap<>();
    // Set while the rows are written, so the events that writing causes are ignored (see render)
    private boolean writing;
    private double measuredRowHeight;
    private final TypeAhead typeAhead = new TypeAhead();

    GridRows(final DataGrid<R> grid,
             final PageState<R> pageState,
             final ColumnModel<Column<R, ?>> columnModel,
             final GridHtml<R> html,
             final TreeRows<R> tree,
             final DataGridKeyboard<R> keyboard,
             final Element scroller,
             final Element content,
             final Element body) {
        this.grid = grid;
        this.pageState = pageState;
        this.columnModel = columnModel;
        this.html = html;
        this.tree = tree;
        this.keyboard = keyboard;
        this.scroller = scroller;
        this.content = content;
        this.body = body;
    }

    /// @return Whether the rows are being written, when the events that writing causes (e.g. a
    /// blur from a focused element being replaced) are to be ignored.
    boolean isWriting() {
        return writing;
    }

    /// @param indexOnPage The page-relative row index.
    /// @return The row's element, or `null` if the row is not currently drawn.
    Element getElement(final int indexOnPage) {
        final RenderedRow rendered = renderedRowsByIndex.get(indexOnPage);
        return rendered != null
                ? rendered.element
                : null;
    }

    /// Forgets the HTML a cell was drawn with, so it is written again the next time its row is
    /// drawn (once the focus has left it, if it has the focus): for a cell that rewrote its own DOM
    /// (e.g. a tick box showing its new tick at once).
    ///
    /// @param indexOnPage The page-relative row index.
    /// @param column      The cell's column.
    void forgetCell(final int indexOnPage, final int column) {
        final RenderedRow rendered = renderedRowsByIndex.get(indexOnPage);
        if (rendered != null && rendered.cells != null) {
            final int position = rendered.cells.positionOf(column);
            rendered.cells = position >= 0
                    ? rendered.cells.withHtml(position, null)
                    : null;
        }
    }

    /// Draws a row again once the focus leaves the cell of it that was left out of date while the
    /// focus was inside it (see [RowWritePlan]), so the cell shows its latest value. A move within
    /// the cell (e.g. from a spinner's field to its arrows) leaves it as it is.
    ///
    /// @param event The `focusout` event.
    void onFocusOut(final NativeEvent event) {
        if (!Element.is(event.getEventTarget())) {
            return;
        }
        final Element target = Element.as(event.getEventTarget());
        final Element rowElement = grid.findAncestorWithAttribute(target, GridHtml.DATA_ROW);
        if (rowElement == null) {
            return;
        }
        final int index = DataGrid.parseInt(rowElement.getAttribute(GridHtml.DATA_ROW));
        final RenderedRow rendered = renderedRowsByIndex.get(index);
        if (rendered == null || rendered.element != rowElement || rendered.staleCell < 0) {
            return;
        }
        final Element cellElement = cellElementAt(rowElement, rendered.staleCell);
        if (cellElement == null || !cellElement.isOrHasChild(target)) {
            return;
        }
        final EventTarget related = event.getRelatedEventTarget();
        if (related != null && Element.is(related) && cellElement.isOrHasChild(Element.as(related))) {
            return;
        }
        grid.redrawRow(pageState.getPageStart() + index);
    }

    /// Draws the rows.
    ///
    /// @param forceAll Whether to render every row's cells again, e.g. after the columns changed.
    /// @param dirty    The page-relative range of rows whose data changed, `[start, end)`, or null
    ///                 for all of them.
    void render(final boolean forceAll, final int[] dirty) {
        // Replacing, moving or removing a focused element makes the browser fire blur at it while
        // it is still in the row. Ignore the events writing the rows causes, as GWT's
        // AbstractHasData ignores events while it refreshes: a cell being edited (e.g.
        // ValueSpinnerCell) would otherwise think editing had ended and not take the focus back.
        if (writing) {
            return;
        }
        final boolean hadRowFocus = grid.hasRowFocus() && body.isOrHasChild(DataGridDom.getActiveElement());
        writing = true;
        try {
            write(forceAll, dirty);
        } finally {
            writing = false;
        }
        // As GWT's AbstractHasData: if the rows had the focus and the focused row was removed (its
        // row went, or changed key), the keyboard row takes the focus, so the keyboard still works.
        if (hadRowFocus && !grid.getElement().isOrHasChild(DataGridDom.getActiveElement())) {
            final Element target = keyboard.getFocusTarget();
            grid.refocus(target != null
                    ? target
                    : scroller);
        }
    }

    private void write(final boolean forceAll, final int[] dirty) {
        final List<R> rows = pageState.getRows();
        final int start;
        final int end;
        if (grid.isRenderedVirtual()) {
            final double rowH = getOneLineRowHeight();
            CssProperties.set(grid.getElement(), "--data-grid__row-height", rowH + "px");
            final VirtualWindow window = VirtualWindow.compute(
                    scroller.getScrollTop(),
                    Math.max(0, scroller.getClientHeight() - grid.getHeaderHeight()),
                    rowH,
                    rows.size(),
                    VirtualWindow.DEFAULT_BUFFER_ROWS);
            start = window.getStart();
            end = window.getEnd();
            CssProperties.set(content, "--data-grid__content-height", window.getContentHeight() + "px");
            CssProperties.set(body, "--data-grid__body-offset", window.getOffset() + "px");
        } else {
            start = 0;
            end = rows.size();
            CssProperties.remove(content, "--data-grid__content-height");
            CssProperties.remove(body, "--data-grid__body-offset");
            final boolean contain = rows.size() > CONTAIN_ROW_THRESHOLD;
            grid.setStyleName("data-grid--contain", contain);
            if (contain) {
                // The height assumed for rows not yet laid out
                CssProperties.set(grid.getElement(),
                        "--data-grid__row-height",
                        getOneLineRowHeight() + "px");
            }
        }

        if (grid.getProfile() == DataGridProfile.TREE) {
            tree.placeRows(rows);
        }
        final Map<RowKey, RenderedRow> previous = new HashMap<>(renderedRows);
        renderedRows.clear();
        renderedRowsByIndex.clear();
        final Map<Object, Integer> occurrences = new HashMap<>();
        final List<RenderedRow> ordered = new ArrayList<>(end - start);

        // 1. Update (or create) the rows to show.
        for (int i = start; i < end; i++) {
            final R row = rows.get(i);
            final Object key = grid.getKey(row, i);
            final int occurrence = occurrences.merge(key, 1, Integer::sum) - 1;
            final RowKey rowKey = new RowKey(key, occurrence);

            RenderedRow rendered = previous.remove(rowKey);
            final boolean isDirty = forceAll
                                    || dirty == null
                                    || (i >= dirty[0] && i < dirty[1]);
            if (rendered == null) {
                rendered = new RenderedRow(Document.get().createDivElement());
                rendered.element.setAttribute("role", grid.getProfile().getRowRole());
                rendered.element.setTabIndex(-1);
            }
            update(rendered, row, i, isDirty || rendered.value != row);
            renderedRows.put(rowKey, rendered);
            renderedRowsByIndex.put(i, rendered);
            ordered.add(rendered);
        }

        // 2. Remove the rows no longer shown, before placing the others. Left in place, they would
        // make every row after them look out of place and be moved.
        for (final RenderedRow stale : previous.values()) {
            stale.element.removeFromParent();
        }

        // 3. Put the rows in order, moving as few as possible.
        Element expected = body.getFirstChildElement();
        for (final RenderedRow rendered : ordered) {
            if (rendered.element != expected) {
                body.insertBefore(rendered.element, expected);
            } else {
                expected = expected.getNextSiblingElement();
            }
        }
    }

    private void update(final RenderedRow rendered, final R row, final int index, final boolean dataChanged) {
        final int absIndex = pageState.getPageStart() + index;
        final SelectionModel<? super R> selectionModel = grid.getSelectionModel();
        final boolean selected = row != null && selectionModel != null && selectionModel.isSelected(row);
        // As GWT, the keyboard row is only shown while the grid has the focus
        final boolean keyboardRow = index == grid.getKeyboardSelectedRow() && grid.hasRowFocus();
        final boolean even = absIndex % 2 == 0;

        if (dataChanged
            || rendered.selected != selected
            || rendered.even != even
            || rendered.index != index) {
            // The keyboard cell's class is set on the element below, not written in the HTML, so the
            // focus coming and going never rewrites a row (which would lose a click's target, or a
            // control's open list, e.g. a selection box's, whose focus has left the grid).
            final DataGrid.RowSpans<R> rowSpans = grid.getRowSpans();
            final Map<Integer, Integer> spans = rowSpans != null && row != null
                    ? rowSpans.getSpans(row, absIndex)
                    : Collections.emptyMap();
            writeCells(rendered, html.rowCells(row, absIndex, spans), row, absIndex);
        }
        final Element firstCell = rendered.element.getFirstChildElement();
        if (firstCell != null) {
            if (keyboardRow) {
                firstCell.addClassName(KEYBOARD_CELL_CLASS_NAME);
            } else {
                firstCell.removeClassName(KEYBOARD_CELL_CLASS_NAME);
            }
        }

        final StringBuilder classes = new StringBuilder("data-grid__row ");
        classes.append(even
                ? "data-grid__row--even"
                : "data-grid__row--odd");
        if (selected) {
            classes.append(" data-grid__row--selected");
        }
        if (keyboardRow) {
            classes.append(" data-grid__row--keyboard");
        }
        final RowStyles<R> rowStyles = grid.getRowStyles();
        if (rowStyles != null && row != null) {
            final String extra = rowStyles.getStyleNames(row, absIndex);
            if (extra != null && !extra.isEmpty()) {
                classes.append(' ').append(extra);
            }
        }
        final String className = classes.toString();
        if (!className.equals(rendered.className)) {
            rendered.element.setClassName(className);
            rendered.className = className;
        }
        if (rendered.index != index) {
            rendered.element.setAttribute(GridHtml.DATA_ROW, String.valueOf(index));
        }
        if (grid.getProfile().hasCells()) {
            rendered.element.setAttribute("aria-rowindex", String.valueOf(absIndex + 2));
        } else if (grid.getProfile() == DataGridProfile.LISTBOX) {
            // A list's option says where it is among them all, as some may not be drawn (another
            // page, or out of view)
            rendered.element.setAttribute("aria-posinset", String.valueOf(absIndex + 1));
            rendered.element.setAttribute("aria-setsize", pageState.isRowCountExact()
                    ? String.valueOf(pageState.getRowCount())
                    : "-1");
        }
        if (selectionModel != null) {
            rendered.element.setAttribute("aria-selected", String.valueOf(selected));
        } else {
            rendered.element.removeAttribute("aria-selected");
        }
        tree.setAttributes(rendered.element, row, index);
        final Function<R, String> rowName = grid.getRowName();
        final String name = rowName != null && row != null
                ? rowName.apply(row)
                : null;
        if (name == null) {
            rendered.element.removeAttribute("aria-label");
        } else {
            rendered.element.setAttribute("aria-label", name);
        }
        final Function<R, String> rowDescription = grid.getRowDescription();
        final String description = rowDescription != null && row != null
                ? rowDescription.apply(row)
                : null;
        if (NullSafe.isBlankString(description)) {
            rendered.element.removeAttribute("aria-description");
        } else {
            rendered.element.setAttribute("aria-description", description);
        }
        final Function<R, TickBoxState> rowChecked = grid.getRowChecked();
        final TickBoxState checked = rowChecked != null && row != null
                ? rowChecked.apply(row)
                : null;
        if (checked == null) {
            rendered.element.removeAttribute("aria-checked");
        } else {
            rendered.element.setAttribute("aria-checked", switch (checked) {
                case TICK -> "true";
                case HALF_TICK -> "mixed";
                case UNTICK -> "false";
            });
        }

        rendered.value = row;
        rendered.index = index;
        rendered.selected = selected;
        rendered.keyboard = keyboardRow;
        rendered.even = even;
    }

    // Writes a row's cells that changed, as RowWritePlan says: the whole row when the focus isn't in
    // its cells (or its columns changed), otherwise cell by cell, leaving a control that has the
    // focus in place, out of date until the focus leaves it (onFocusOut).
    private void writeCells(final RenderedRow rendered,
                            final GridHtml.RowCells cells,
                            final R row,
                            final int absIndex) {
        final Element rowElement = rendered.element;
        final GridHtml.RowCells before = rendered.cells;

        // The cell in use: the one holding the focus, else one whose control has its list or menu
        // open (aria-expanded), as a selection box does with the focus in its popup
        int focusedColumn = -1;
        int focusedCell = -1;
        boolean focusOnCell = false;
        final Element active = DataGridDom.getActiveElement();
        Element inUse = null;
        if (active != null && active != rowElement && rowElement.isOrHasChild(active)) {
            inUse = grid.findAncestorWithAttribute(active, GridHtml.DATA_COL);
            if (inUse != null) {
                focusedColumn = DataGrid.parseInt(inUse.getAttribute(GridHtml.DATA_COL));
                focusOnCell = active == inUse;
            }
        } else {
            final Element expanded = findExpanded(rowElement);
            if (expanded != null) {
                inUse = grid.findAncestorWithAttribute(expanded, GridHtml.DATA_COL);
            }
        }
        if (inUse != null && before != null) {
            focusedCell = before.positionOf(DataGrid.parseInt(inUse.getAttribute(GridHtml.DATA_COL)));
        }

        final RowWritePlan plan = RowWritePlan.plan(
                before != null
                        ? before.getHtml()
                        : null,
                before != null
                        ? before.getColumns()
                        : null,
                cells.getHtml(),
                cells.getColumns(),
                focusedCell,
                focusOnCell);

        if (plan.isWholeRow()) {
            rowElement.setInnerHTML(cells.joined());
            rendered.cells = cells;
            rendered.staleCell = -1;
            // The element that had the focus is gone, so let its cell take the focus back
            if (focusedColumn >= 0) {
                resetFocus(rowElement, row, absIndex, focusedColumn);
            }
            return;
        }

        for (final int position : plan.getCells()) {
            final Element cellElement = cellElementAt(rowElement, position);
            if (cellElement != null) {
                rowElement.replaceChild(toElement(cells.getHtml().get(position)), cellElement);
            }
        }
        final int contentOnly = plan.getContentOnly();
        if (contentOnly >= 0) {
            // The cell's own element has the focus (moving between cells), so it stays
            final Element cellElement = cellElementAt(rowElement, contentOnly);
            if (cellElement != null) {
                cellElement.setClassName(cells.getClasses(contentOnly));
                cellElement.setInnerHTML(cells.getContent(contentOnly));
            }
        }
        final int stale = plan.getLeftStale();
        rendered.cells = stale >= 0
                ? cells.withHtml(stale, before.getHtml().get(stale))
                : cells;
        rendered.staleCell = stale;
    }

    // A control in a row's cells with its list or menu open, or null for none
    private static native Element findExpanded(Element rowElement) /*-{
        return rowElement.querySelector('[data-col] [aria-expanded="true"]');
    }-*/;

    // The cell element at a position in a row, or null if there is none
    private static Element cellElementAt(final Element rowElement, final int position) {
        Element cell = rowElement.getFirstChildElement();
        for (int i = 0; i < position && cell != null; i++) {
            cell = cell.getNextSiblingElement();
        }
        return cell;
    }

    // An element made from a cell's HTML
    private static Element toElement(final String cellHtml) {
        final Element holder = Document.get().createDivElement();
        holder.setInnerHTML(cellHtml);
        return holder.getFirstChildElement();
    }

    // After a row is rewritten, let the cell that had the focus take it back, as GWT's
    // AbstractHasData does with Cell.resetFocus. If the cell doesn't, keep the focus on the row so
    // the keyboard still works.
    private void resetFocus(final Element rowElement, final R row, final int absIndex, final int columnIndex) {
        boolean focused = false;
        if (row != null && columnIndex < columnModel.size()) {
            final Element cellContent = findCellContent(rowElement, columnIndex);
            if (cellContent != null) {
                final Context context = new Context(absIndex, columnIndex, grid.getKey(row, absIndex));
                focused = resetCellFocus(columnModel.getColumn(columnIndex), context, cellContent, row);
            }
        }
        // Otherwise the keyboard's target takes it, which may be in another row by now (the key
        // that moved the focus there is why this row was drawn again). A target in a row not yet
        // drawn is given the focus once all the rows are (render)
        if (!focused) {
            final Element target = keyboard.getFocusTarget();
            if (target != null) {
                grid.refocus(target);
            }
        }
    }

    private <C> boolean resetCellFocus(final Column<R, C> column,
                                       final Context context,
                                       final Element parent,
                                       final R row) {
        return column.getCell().resetFocus(context, parent, column.getValue(row));
    }

    private static Element findCellContent(final Element rowElement, final int columnIndex) {
        final String col = String.valueOf(columnIndex);
        for (Element cell = rowElement.getFirstChildElement();
                cell != null;
                cell = cell.getNextSiblingElement()) {
            if (col.equals(cell.getAttribute(GridHtml.DATA_COL))) {
                return cell.getFirstChildElement();
            }
        }
        return null;
    }

    /// @return The height of a row of one line, measured with the first row's cells at the current
    /// density.
    double getOneLineRowHeight() {
        if (measuredRowHeight > 0) {
            return measuredRowHeight;
        }
        if (!grid.isAttached()) {
            return FALLBACK_ROW_HEIGHT;
        }
        // Measured with the first row's own cells, whose content may be taller than plain text
        // (e.g. a dashboard table's cells carry their own padding). Only that is kept: one taken
        // before there are rows is taken again once they arrive.
        final boolean hasRows = pageState.getRowDataSize() > 0;
        final double height = measureRowHeight(hasRows
                ? html.cells(pageState.getRow(0), grid.getPageStart(), Collections.emptyMap())
                .asString()
                : "<div class=\"data-grid__cell\"><div>X</div></div>");
        if (hasRows) {
            measuredRowHeight = height;
        }
        return height > 0
                ? height
                : FALLBACK_ROW_HEIGHT;
    }

    // Measure a one-line row of the cells given, at the current density. Kept fractional, as
    // natural rows are, so virtual rows line up with them exactly.
    private double measureRowHeight(final String cellsHtml) {
        final Element probe = Document.get().createDivElement();
        probe.setClassName("data-grid__row data-grid__row--probe data-grid__row--even");
        probe.setInnerHTML(cellsHtml);
        body.appendChild(probe);
        final double height = DataGridDom.getHeight(probe);
        probe.removeFromParent();
        return height;
    }

    /// The row that typing goes to: the next whose text (its cells' joined) starts with what has
    /// been typed (see [TypeAhead]).
    ///
    /// @param typed   The character typed.
    /// @param current The page-relative index of the row with the focus.
    /// @return The page-relative index of the row, or -1 if none matches.
    int typeAhead(final String typed, final int current) {
        return typeAhead.next(typed, current, pageState.getRowDataSize(), this::getRowText);
    }

    // A row's text, its cells' joined
    private String getRowText(final int indexOnPage) {
        final R row = pageState.getRow(indexOnPage);
        final StringBuilder text = new StringBuilder();
        for (int i = 0; row != null && i < columnModel.size(); i++) {
            final String cellText = html.cellText(row, pageState.getPageStart() + indexOnPage, i);
            if (!cellText.isEmpty()) {
                if (text.length() > 0) {
                    text.append(' ');
                }
                text.append(cellText);
            }
        }
        return text.toString();
    }

    /// Scrolls so that a row is fully visible, moving as little as possible.
    ///
    /// @param indexOnPage The page-relative row index.
    void scrollIntoView(final int indexOnPage) {
        final double[] bounds = getBounds(indexOnPage, grid.isRenderedVirtual());
        if (bounds == null) {
            return;
        }
        final int headerHeight = grid.getHeaderHeight();
        final double viewTop = scroller.getScrollTop() + headerHeight;
        final double viewBottom = scroller.getScrollTop() + scroller.getClientHeight();
        if (bounds[0] < viewTop) {
            scroller.setScrollTop((int) Math.floor(bounds[0] - headerHeight));
        } else if (bounds[1] > viewBottom) {
            scroller.setScrollTop((int) Math.ceil(bounds[1] - scroller.getClientHeight()));
        }
    }

    /// Scrolls so that a row is at the top (once it is drawn, with its new position).
    ///
    /// @param indexOnPage The page-relative row index.
    void scrollToTop(final int indexOnPage) {
        final double[] bounds = getBounds(indexOnPage, grid.isRenderedVirtual());
        if (bounds != null) {
            scroller.setScrollTop((int) Math.floor(bounds[0] - grid.getHeaderHeight()));
        }
    }

    // The row's top and bottom in the scroller's content, or null if unknown
    private double[] getBounds(final int indexOnPage, final boolean virtualMode) {
        if (indexOnPage < 0 || indexOnPage >= pageState.getRowDataSize()) {
            return null;
        }
        final int headerHeight = grid.getHeaderHeight();
        if (virtualMode) {
            final double rowH = getOneLineRowHeight();
            final double top = headerHeight + indexOnPage * rowH;
            return new double[]{top, top + rowH};
        }
        final Element element = getElement(indexOnPage);
        if (element == null) {
            return null;
        }
        final double top = headerHeight + DataGridDom.getOffsetTop(element);
        return new double[]{top, top + DataGridDom.getHeight(element)};
    }

    /// @param virtualMode Whether the rows are drawn in virtual mode.
    /// @return The page-relative index of the first row in view.
    int topVisibleRow(final boolean virtualMode) {
        // Scroll positions are whole pixels but rows can be a fractional height, so a row with less
        // than a pixel still showing counts as scrolled off.
        final double scrollTop = scroller.getScrollTop() + 1;
        if (virtualMode) {
            final double rowH = getOneLineRowHeight();
            return Math.max(0, (int) Math.floor(scrollTop / rowH));
        }
        // A linear scan, but only on the render that switches modes.
        for (int i = 0; i < pageState.getRowDataSize(); i++) {
            final double[] bounds = getBounds(i, false);
            if (bounds != null && bounds[1] - grid.getHeaderHeight() > scrollTop) {
                return i;
            }
        }
        return 0;
    }

    /// Forgets the measured row height, so it is measured again, e.g. after the columns or the
    /// density changed.
    ///
    /// @return Whether there was one to forget (and the rows need drawing again).
    boolean forgetRowHeight() {
        if (measuredRowHeight > 0) {
            measuredRowHeight = 0;
            return true;
        }
        return false;
    }
}
