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

import stroom.widget.datagrid.client.model.AutoFit;
import stroom.widget.datagrid.client.model.ColumnModel;
import stroom.widget.util.client.CssProperties;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

import java.util.ArrayList;
import java.util.List;

/// The header's pointer interactions for [DataGrid] (design sections 5.3 to 5.5): resizing
/// a column by dragging its edge, sizing it to fit by double-clicking the edge, moving a column by
/// dragging its header, and the header click menu.
///
/// * A 10px resize handle follows the pointer over the header, on the edge of the column that
///   [ColumnModel#resizeTarget(double, int)] picks. Dragging it resizes the column live; the
///   heading listener is told the new width once, on release.
/// * Pressing on a movable header and dragging more than 10px starts a move. A translucent ghost
///   follows the pointer and a line marks where the column would go.
/// * A press and release on a header that did not turn into a resize or move shows the heading
///   listener's menu.
///
/// The handle lives in the sticky header, so it scrolls with the columns. The guide lines and the
/// ghost float over the whole grid.
final class ColumnInteractions {

    private static final int HANDLE_WIDTH = 10;
    private static final int HALF_HANDLE_WIDTH = HANDLE_WIDTH / 2;
    private static final int HALF_LINE_WIDTH = 1;
    private static final int MOVE_THRESHOLD = 10;
    private static final String DATA_HCOL = "data-hcol";
    private static final String DRAGGING_CLASS_NAME = "data-grid--dragging";
    private static final String RESIZING_CLASS_NAME = "data-grid--resizing";
    // The resize handle shows on the edge of the column under the pointer
    private static final String HANDLE_SHOWN_CLASS_NAME = "data-grid__resize-handle--shown";
    private static final String MEASURE_CLASS_NAME = "data-grid__measure";

    private final DataGrid<?> grid;
    private final Element root;
    private final Element scroller;
    private final Element head;
    private final Element handle;
    private final Element resizeLine;
    private final Element moveGhost;
    private final Element moveLine;
    private final List<JavaScriptObject> listeners = new ArrayList<>();

    // The column whose right edge the handle is on, or -1 when it is hidden.
    private int handleColumn = -1;

    // Set while a column is being resized.
    private boolean resizing;
    private int resizeColumn;
    private int resizeStartWidth;
    private double resizeStartX;

    // Set from a press on a header cell until the release.
    private int pressedColumn = -1;
    private double pressX;
    private double pressOffset;
    private boolean moving;
    private int insertPosition = -1;

    // Set after a move, so the click that ends it does not also sort the column.
    private boolean suppressClick;

    /// Works the grid's handle, guide lines and ghost (its template's).
    ///
    /// @param grid       The grid.
    /// @param root       The grid's root element.
    /// @param scroller   The grid's scroller.
    /// @param head       The grid's header, which the handle is in.
    /// @param handle     The resize handle.
    /// @param resizeLine The line that follows a column's edge while it is resized.
    /// @param moveGhost  The ghost of a column that follows the pointer while it is moved.
    /// @param moveLine   The line where a moved column will go.
    ColumnInteractions(final DataGrid<?> grid,
                       final Element root,
                       final Element scroller,
                       final Element head,
                       final Element handle,
                       final Element resizeLine,
                       final Element moveGhost,
                       final Element moveLine) {
        this.grid = grid;
        this.root = root;
        this.scroller = scroller;
        this.head = head;
        // Each hidden until needed (data-grid.css): the handle until shown (HANDLE_SHOWN_CLASS_NAME),
        // the resize line while not resizing, and the move ghost and line while not moving
        this.handle = handle;
        this.resizeLine = resizeLine;
        this.moveGhost = moveGhost;
        this.moveLine = moveLine;
    }

    private static Element div(final Document doc, final String className) {
        final Element element = doc.createDivElement();
        element.setClassName(className);
        return element;
    }

    /// Start listening for pointer events. Call when the grid is attached.
    void attach() {
        listeners.add(DataGridDom.addListener(head, "pointerdown", false, this::onPointerDown));
        listeners.add(DataGridDom.addListener(head, "pointermove", false, this::onPointerMove));
        listeners.add(DataGridDom.addListener(head, "pointerup", false, this::onPointerUp));
        listeners.add(DataGridDom.addListener(head, "pointercancel", false, this::onPointerCancel));
        listeners.add(DataGridDom.addListener(head, "pointerleave", false, e -> onPointerLeave()));
        listeners.add(DataGridDom.addListener(handle, BrowserEvents.DBLCLICK, false, this::onHandleDoubleClick));
        // Capture, so a click that ends a move is stopped before it reaches the header and sorts.
        listeners.add(DataGridDom.addListener(root, BrowserEvents.CLICK, true, this::onClickCapture));
    }

    /// Stop listening for pointer events and end any drag. Call when the grid is detached.
    void detach() {
        for (final JavaScriptObject listener : listeners) {
            DataGridDom.removeListener(listener);
        }
        listeners.clear();
        resizing = false;
        moving = false;
        pressedColumn = -1;
        handleColumn = -1;
        root.removeClassName(DRAGGING_CLASS_NAME);
        root.removeClassName(RESIZING_CLASS_NAME);
        handle.removeClassName(HANDLE_SHOWN_CLASS_NAME);
    }

    /// @param element An element.
    /// @return Whether the element is one of the handle, guide lines or ghost.
    boolean contains(final Element element) {
        return handle.isOrHasChild(element)
               || resizeLine.isOrHasChild(element)
               || moveGhost.isOrHasChild(element)
               || moveLine.isOrHasChild(element);
    }

    // --------------------------------------------------------------------------------
    // Pointer events
    // --------------------------------------------------------------------------------

    private void onPointerDown(final NativeEvent event) {
        suppressClick = false;
        if (!MouseUtil.isPrimary(event)) {
            return;
        }
        final Element target = targetOf(event);
        if (target == null) {
            return;
        }
        if (handle.isOrHasChild(target)) {
            if (grid.isAllowResize() && handleColumn >= 0) {
                // Stop the press selecting text or taking the focus.
                event.preventDefault();
                startResize(event);
            }
            return;
        }
        final Element headerCell = findHeaderCell(target);
        if (headerCell == null || isInput(target, headerCell)) {
            pressedColumn = -1;
            return;
        }
        pressedColumn = columnIndexOf(headerCell);
        pressX = event.getClientX();
        pressOffset = event.getClientX() - headerCell.getAbsoluteLeft();
    }

    private void onPointerMove(final NativeEvent event) {
        if (resizing) {
            event.preventDefault();
            updateResize(event);
        } else if (moving) {
            event.preventDefault();
            updateMove(event);
        } else if (pressedColumn >= 0
                   && grid.isAllowMove()
                   && model().getSizing(pressedColumn).isMovable()
                   && Math.abs(event.getClientX() - pressX) > MOVE_THRESHOLD) {
            startMove(event);
        } else {
            updateHandle(event);
        }
    }

    private void onPointerUp(final NativeEvent event) {
        if (resizing) {
            endResize(event);
        } else if (moving) {
            endMove(event);
        } else if (pressedColumn >= 0 && MouseUtil.isPrimary(event)) {
            pressedColumn = -1;
            final HeadingListener headingListener = grid.getHeadingListener();
            final Element target = targetOf(event);
            final Element headerCell = target != null
                    ? findHeaderCell(target)
                    : null;
            if (grid.isAllowHeaderSelection()
                && headingListener != null
                && headerCell != null
                && !isInput(target, headerCell)) {
                final int column = columnIndexOf(headerCell);
                final int x = event.getClientX();
                headingListener.onShowMenu(event, () -> heading(column, x));
            }
        }
    }

    private void onPointerCancel(final NativeEvent event) {
        if (resizing) {
            endResize(event);
        } else if (moving) {
            endMove(event);
        }
        pressedColumn = -1;
    }

    private void onPointerLeave() {
        if (!resizing && !moving) {
            hideHandle();
        }
    }

    private void onClickCapture(final NativeEvent event) {
        if (suppressClick) {
            suppressClick = false;
            event.preventDefault();
            event.stopPropagation();
        }
    }

    // --------------------------------------------------------------------------------
    // Resize
    // --------------------------------------------------------------------------------

    private void updateHandle(final NativeEvent event) {
        if (!grid.isAllowResize()) {
            hideHandle();
            return;
        }
        final ColumnModel<?> model = model();
        final int column = model.resizeTarget(tableX(event.getClientX()), HANDLE_WIDTH);
        if (column < 0) {
            hideHandle();
            return;
        }
        handleColumn = column;
        placeHandle(column);
        handle.addClassName(HANDLE_SHOWN_CLASS_NAME);
    }

    private void placeHandle(final int column) {
        final ColumnModel<?> model = model();
        final int edge = model.getLeft(column) + model.getWidth(column);
        // Keep the handle inside the table, so it never makes the grid scroll sideways.
        final int left = Math.max(0, Math.min(edge - HALF_HANDLE_WIDTH, model.getTotalWidth() - HANDLE_WIDTH));
        CssProperties.set(handle, "--data-grid__handle-left", left + "px");
    }

    private void hideHandle() {
        handleColumn = -1;
        handle.removeClassName(HANDLE_SHOWN_CLASS_NAME);
    }

    private void startResize(final NativeEvent event) {
        resizing = true;
        resizeColumn = handleColumn;
        resizeStartWidth = model().getWidth(resizeColumn);
        resizeStartX = event.getClientX();
        DataGridDom.setPointerCapture(handle, DataGridDom.getPointerId(event));
        root.addClassName(DRAGGING_CLASS_NAME);
        // Which shows the resize line
        root.addClassName(RESIZING_CLASS_NAME);
        placeResizeLine();

        final HeadingListener headingListener = grid.getHeadingListener();
        if (headingListener != null) {
            final int column = resizeColumn;
            final int x = event.getClientX();
            headingListener.onMoveStart(event, () -> heading(column, x));
        }
    }

    private void updateResize(final NativeEvent event) {
        final ColumnModel<?> model = model();
        final int requested = resizeStartWidth + (int) Math.round(event.getClientX() - resizeStartX);
        final int width = model.clampToMinWidth(resizeColumn, requested);
        if (width != model.getWidth(resizeColumn)) {
            // The rows reflow as the column changes (D8); the grid writes the widths once a frame.
            grid.resizeColumnLive(resizeColumn, width);
            placeHandle(resizeColumn);
            placeResizeLine();
        }
    }

    private void placeResizeLine() {
        final ColumnModel<?> model = model();
        final int edge = model.getLeft(resizeColumn) + model.getWidth(resizeColumn);
        CssProperties.set(resizeLine,
                "--data-grid__resize-line-left",
                (edge - scroller.getScrollLeft() - HALF_LINE_WIDTH) + "px");
    }

    private void endResize(final NativeEvent event) {
        resizing = false;
        DataGridDom.releasePointerCapture(handle, DataGridDom.getPointerId(event));
        root.removeClassName(DRAGGING_CLASS_NAME);
        root.removeClassName(RESIZING_CLASS_NAME);

        final int column = resizeColumn;
        final int width = model().getWidth(column);
        if (width != resizeStartWidth) {
            grid.commitColumnResize(column, width);
        }
        final HeadingListener headingListener = grid.getHeadingListener();
        if (headingListener != null) {
            final int x = event.getClientX();
            headingListener.onMoveEnd(event, () -> heading(column, x));
        }
        updateHandle(event);
    }

    private void onHandleDoubleClick(final NativeEvent event) {
        if (!grid.isAllowResize() || handleColumn < 0) {
            return;
        }
        event.preventDefault();
        final int column = handleColumn;
        final int width = AutoFit.width(measureContent(column), model().getSizing(column).getMinWidth());
        if (width != model().getWidth(column)) {
            grid.resizeColumn(column, width);
        }
        placeHandle(column);
    }

    // Measures the widest content of a column (R-CR4). The header and the cells of a sample of the
    // page's rows (see AutoFit) are copied, unwrapped, into hidden elements inside the grid, so they
    // are styled as the real ones, and measured in one layout. Includes the cell padding.
    private double measureContent(final int column) {
        double widest = 0;
        final Element headerRow = grid.getHeaderRowElement();
        final Element headerCell = grid.getHeaderCellElement(column);
        if (headerRow != null && headerCell != null) {
            final Element measure = div(Document.get(), MEASURE_CLASS_NAME);
            final Element copy = div(Document.get(), headerCell.getClassName());
            copy.setInnerHTML(headerCell.getInnerHTML());
            measure.appendChild(copy);
            headerRow.appendChild(measure);
            widest = DataGridDom.getWidth(copy);
            measure.removeFromParent();
        }

        final int[] rows = AutoFit.sampleRows(grid.getVisibleItemCount(), AutoFit.MAX_SAMPLE, grid.getRowsInView());
        if (rows.length > 0) {
            final SafeHtmlBuilder sb = new SafeHtmlBuilder();
            for (final int row : rows) {
                grid.renderCellCopy(row, column, sb);
            }
            final Element measure = div(Document.get(), MEASURE_CLASS_NAME);
            measure.setInnerSafeHtml(sb.toSafeHtml());
            grid.getBodyElement().appendChild(measure);
            for (Element cell = measure.getFirstChildElement(); cell != null; cell = cell.getNextSiblingElement()) {
                widest = Math.max(widest, DataGridDom.getWidth(cell));
            }
            measure.removeFromParent();
        }
        return widest;
    }

    // --------------------------------------------------------------------------------
    // Move
    // --------------------------------------------------------------------------------

    private void startMove(final NativeEvent event) {
        moving = true;
        insertPosition = -1;
        DataGridDom.setPointerCapture(head, DataGridDom.getPointerId(event));
        // The press may have started selecting header text.
        DataGridDom.clearTextSelection();
        // Which shows the move ghost and line, placed by updateMove below
        root.addClassName(DRAGGING_CLASS_NAME);
        hideHandle();

        final HeadingListener headingListener = grid.getHeadingListener();
        if (headingListener != null) {
            final int column = pressedColumn;
            final int x = (int) pressX;
            headingListener.onMoveStart(event, () -> heading(column, x));
        }
        updateMove(event);
    }

    private void updateMove(final NativeEvent event) {
        final ColumnModel<?> model = model();
        final int column = pressedColumn;
        CssProperties.set(moveGhost,
                "--data-grid__move-ghost-left",
                (event.getClientX() - pressOffset - root.getAbsoluteLeft()) + "px");
        CssProperties.set(moveGhost, "--data-grid__move-ghost-width", model.getWidth(column) + "px");

        insertPosition = model.insertPosition(column, tableX(event.getClientX()));
        final int edge = insertPosition == 0
                ? 0
                : model.getLeft(insertPosition - 1) + model.getWidth(insertPosition - 1);
        final int left = insertPosition == 0
                ? 0
                : edge - scroller.getScrollLeft() - HALF_LINE_WIDTH;
        CssProperties.set(moveLine, "--data-grid__move-line-left", left + "px");
    }

    private void endMove(final NativeEvent event) {
        moving = false;
        DataGridDom.releasePointerCapture(head, DataGridDom.getPointerId(event));
        root.removeClassName(DRAGGING_CLASS_NAME);
        // The release ends the move, so its click must not also sort or show the menu.
        suppressClick = true;

        final int column = pressedColumn;
        pressedColumn = -1;
        if (insertPosition >= 0 && insertPosition != column) {
            grid.moveColumn(column, insertPosition);
        }
        final HeadingListener headingListener = grid.getHeadingListener();
        if (headingListener != null) {
            final int x = event.getClientX();
            headingListener.onMoveEnd(event, () -> heading(column, x));
        }
    }

    // --------------------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------------------

    private ColumnModel<?> model() {
        return grid.getColumnModel();
    }

    // Converts a pointer position to table coordinates: from the left edge of the first column,
    // including the horizontal scroll.
    private double tableX(final double clientX) {
        return clientX - scroller.getAbsoluteLeft() + scroller.getScrollLeft();
    }

    /// Opens a column's menu from the keyboard (Enter, Space or the context menu key on its
    /// header), as a click on the header does, the heading taken as pressed at its middle.
    ///
    /// @param column The column index.
    /// @param event  The key event.
    void showMenu(final int column, final NativeEvent event) {
        final HeadingListener headingListener = grid.getHeadingListener();
        final Element headerCell = grid.getHeaderCellElement(column);
        if (headingListener != null && headerCell != null) {
            final int x = headerCell.getAbsoluteLeft() + headerCell.getOffsetWidth() / 2;
            headingListener.onShowMenu(event, () -> heading(column, x));
        }
    }

    private Heading heading(final int column, final int initialX) {
        if (column < 0 || column >= model().size()) {
            return null;
        }
        final Element element = grid.getHeaderCellElement(column);
        return element != null
                ? new Heading(root, element, column, initialX)
                : null;
    }

    private Element findHeaderCell(final Element start) {
        Element element = start;
        while (element != null && element != head) {
            if (element.hasAttribute(DATA_HCOL)) {
                return element;
            }
            element = element.getParentElement();
        }
        return null;
    }

    private int columnIndexOf(final Element headerCell) {
        try {
            final int index = Integer.parseInt(headerCell.getAttribute(DATA_HCOL));
            return index < model().size()
                    ? index
                    : -1;
        } catch (final NumberFormatException e) {
            return -1;
        }
    }

    // As MyDataGrid.getHeading: a press in a header's input, e.g. a filter box, is not a press on
    // the header.
    private static boolean isInput(final Element target, final Element headerCell) {
        Element element = target;
        while (element != null && element != headerCell) {
            if ("input".equalsIgnoreCase(element.getTagName())) {
                return true;
            }
            element = element.getParentElement();
        }
        return false;
    }

    private static Element targetOf(final NativeEvent event) {
        final EventTarget target = event.getEventTarget();
        return Element.is(target)
                ? Element.as(target)
                : null;
    }

}
