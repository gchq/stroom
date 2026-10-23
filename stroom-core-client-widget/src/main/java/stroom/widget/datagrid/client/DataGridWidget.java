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
import stroom.data.grid.client.ColSpec;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.HeadingListener;
import stroom.hyperlink.client.HyperlinkEvent;
import stroom.util.shared.Expander;
import stroom.util.shared.NullSafe;
import stroom.widget.contextmenu.client.ContextMenuManager;
import stroom.widget.contextmenu.client.TextEditTarget;
import stroom.widget.datagrid.client.model.ColumnModel;
import stroom.widget.datagrid.client.model.ColumnSizing;
import stroom.widget.datagrid.client.model.KeyboardRow;
import stroom.widget.datagrid.client.model.PageState;
import stroom.widget.datagrid.client.model.SortCycle;
import stroom.widget.datagrid.client.model.SortEntry;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.tab.client.view.GlobalResizeObserver;
import stroom.widget.util.client.InputModality;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.cell.client.Cell.Context;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.ColumnSortList.ColumnSortInfo;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.cellview.client.LoadingStateChangeEvent;
import com.google.gwt.user.cellview.client.LoadingStateChangeEvent.LoadingState;
import com.google.gwt.user.cellview.client.RowStyles;
import com.google.gwt.user.cellview.client.SafeHtmlHeader;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Focusable;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.gwt.view.client.HasData;
import com.google.gwt.view.client.ProvidesKey;
import com.google.gwt.view.client.Range;
import com.google.gwt.view.client.RangeChangeEvent;
import com.google.gwt.view.client.RowCountChangeEvent;
import com.google.gwt.view.client.SelectionModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/// A data grid built from plain DOM and CSS grid layout, replacing `MyDataGrid`.
///
/// See `stroom-core-client-widget/DATA_GRID_DESIGN.md` for the requirements and design. In brief:
///
/// * Header and rows live in one native scroller; the header is `position: sticky`, so header and
///   body always scroll horizontally together.
/// * Column widths are a single CSS variable (`--data-grid__columns`) used as `grid-template-columns` by every
///   row, so resizing a column never re-renders rows.
/// * Rows are rendered to HTML strings and cached by key. A row's DOM is only written when its HTML
///   actually changes.
/// * Two rendering modes: *natural* (the default, the whole page rendered with content-sized rows)
///   and *virtual* (fixed row height, only the visible rows rendered). Wrapping (multi-line or a
///   wrapping column) switches virtual mode off.
/// * Events are handled synchronously by delegation from the grid's root element.
/// * The native scrollbars are hidden and overlay thumbs are drawn over the grid instead.
///
/// It implements GWT's `HasData`, takes ordinary GWT `Column`, `Header` and `Cell` objects and fires
/// the same events as `MyDataGrid`, so data providers, pagers and selection models work unchanged.
///
/// @param <R> The row type.
public class DataGridWidget<R> extends Widget
        implements DataGridView<R>, HasKeyboardSelectionPolicy, Focusable {

    /// The default page size.
    public static final int DEFAULT_LIST_PAGE_SIZE = 100;

    /// The page size for grids that show a very large list on one page.
    public static final int MASSIVE_LIST_PAGE_SIZE = 100000;

    private static final String DATA_ROW = GridHtml.DATA_ROW;
    private static final String DATA_COL = GridHtml.DATA_COL;
    private static final String DATA_HCOL = GridHtml.DATA_HCOL;

    private final HasHandlers globalEventBus;

    // The column model holds sizing; headers and wrapping are kept alongside it by column.
    private final ColumnModel<Column<R, ?>> columnModel = new ColumnModel<>();
    private final Map<Column<R, ?>, Header<?>> headers = new HashMap<>();
    private final Set<Column<R, ?>> wrappingColumns = new HashSet<>();
    private final ColumnSortList sortList;
    // The screen's default sort (R-H4b, D10): shown from the start, and restored when a click
    // leaves the sort list empty
    private final List<SortEntry<Column<?, ?>>> defaultSort = new ArrayList<>();
    private final GridHtml<R> html;

    private final PageState<R> pageState;

    // DOM.
    private final Element scroller;
    private final Element head;
    private final Element headerRow;
    private final Element content;
    private final Element body;
    private final Element empty;
    private final OverlayScrollbars scrollbars;
    private final ColumnInteractions columnInteractions;
    private final DataGridContextMenu<R> contextMenu;

    // The rows drawn, and the tree they make if the grid has an expander column
    private final GridRows<R> gridRows;
    private final GridHeader<R> gridHeader;
    private final TreeRows<R> tree;

    private SelectionModel<? super R> selectionModel;
    private HandlerRegistration selectionModelRegistration;
    private HandlerRegistration selectionPreviewRegistration;
    private ProvidesKey<R> keyProvider;
    private KeyboardSelectionPolicy keyboardSelectionPolicy = KeyboardSelectionPolicy.ENABLED;
    // As GWT: the keyboard row starts at 0 and follows its row's key when the rows change (R-S12).
    private int keyboardSelectedRow;
    private Object keyboardRowKey;
    private boolean keyboardRowChanged;
    private boolean keyboardStealFocus;
    // Whether the focus is in the grid's rows; GWT only shows the keyboard row while it is.
    private boolean hasFocus;
    private RowStyles<R> rowStyles;
    private RowSpans<R> rowSpans;
    private HeadingListener headingListener;
    private LoadingState loadingState;
    // Whether data or the row count changed since the loading state was last fired
    private boolean loadingStateUpdated;
    private final DataGridKeyboard<R> keyboard;

    private DataGridProfile profile = DataGridProfile.GRID;
    // Gives a row's tick state, for a list of tick boxes, or null
    private Function<R, TickBoxState> rowChecked;
    // Gives a row's name where its content doesn't, or null
    private Function<R, String> rowName;
    private Function<R, String> rowDescription;
    private String accessibleName;
    // Whether the selection manager lets several rows be chosen (a list says so)
    private boolean multiSelectable;
    private boolean multiLine;
    private boolean virtual;
    private int rowHeight;
    private boolean hideHeader;
    private boolean allowMove = true;
    private boolean allowResize = true;
    private boolean allowHeaderSelection = true;
    private boolean hasContextMenu = true;
    private String tableName;
    private String emptyMessage;

    // Render state.
    private boolean renderScheduled;
    private boolean headersDirty = true;
    private int renderedStructureVersion = -1;
    private int renderedWidthVersion = -1;
    private Boolean renderedVirtual;
    private boolean frameScheduled;
    private int savedScrollTop;
    private int savedScrollLeft;

    // Listeners added outside GWT's event system.
    private final List<JavaScriptObject> nativeListeners = new ArrayList<>();

    /// Create a grid with the default page size.
    ///
    /// @param globalEventBus The application event bus.
    public DataGridWidget(final HasHandlers globalEventBus) {
        this(globalEventBus, DEFAULT_LIST_PAGE_SIZE);
    }

    /// Create a grid.
    ///
    /// @param globalEventBus The application event bus.
    /// @param pageSize       The page size.
    public DataGridWidget(final HasHandlers globalEventBus, final int pageSize) {
        this.globalEventBus = globalEventBus;
        this.pageState = new PageState<>(pageSize);
        this.sortList = new ColumnSortList(() -> {
            headersDirty = true;
            scheduleRender();
        });
        this.html = new GridHtml<>(columnModel, headers, sortList, this::getKey);
        // Before the first input the grid puts the focus back after (see refocus)
        InputModality.install();

        final Document doc = Document.get();
        final Element root = doc.createDivElement();
        root.setClassName("data-grid data-grid--header-selection");

        // The scroller is the grid as far as ARIA is concerned, and takes keyboard focus, so a grid
        // can always be scrolled from the keyboard. Key events bubble from it to the root, where they
        // are handled.
        scroller = div(doc, "data-grid__scroller");
        scroller.setAttribute("role", "grid");
        scroller.setTabIndex(0);
        head = div(doc, "data-grid__head");
        headerRow = div(doc, "data-grid__header-row");
        headerRow.setAttribute("role", "row");
        head.appendChild(headerRow);
        content = div(doc, "data-grid__content");
        body = div(doc, "data-grid__body");
        body.setAttribute("role", "rowgroup");
        content.appendChild(body);
        empty = div(doc, "data-grid__empty");
        content.appendChild(empty);
        scroller.appendChild(head);
        scroller.appendChild(content);
        root.appendChild(scroller);

        scrollbars = new OverlayScrollbars(root, scroller, this::getHeaderHeight);
        columnInteractions = new ColumnInteractions(this, root, scroller, head);
        contextMenu = new DataGridContextMenu<>(this);

        keyboard = new DataGridKeyboard<>(this);
        tree = new TreeRows<>(this);
        gridHeader = new GridHeader<>(this, columnModel, html, keyboard, head, headerRow);
        gridRows = new GridRows<>(this, pageState, columnModel, html, tree, keyboard, scroller, content, body);

        setElement(root);
        sinkEvents(Event.ONCLICK
                   | Event.ONDBLCLICK
                   | Event.ONMOUSEDOWN
                   | Event.ONMOUSEUP
                   | Event.ONMOUSEMOVE
                   | Event.ONMOUSEOVER
                   | Event.ONMOUSEOUT
                   | Event.KEYEVENTS
                   | Event.ONCHANGE
                   | Event.ONCONTEXTMENU);
    }

    private static Element div(final Document doc, final String className) {
        final Element element = doc.createDivElement();
        element.setClassName(className);
        return element;
    }

    // --------------------------------------------------------------------------------
    // Columns
    // --------------------------------------------------------------------------------

    /// Add a fixed-width column that cannot be resized or moved, with the default width.
    ///
    /// @param column The column.
    /// @param name   The header text. Treated as HTML, as `MyDataGrid` does.
    public void addColumn(final Column<R, ?> column, final String name) {
        addColumn(column, name, ColumnSizing.DEFAULT_WIDTH);
    }

    /// Add a fixed-width column that cannot be resized or moved.
    ///
    /// @param column The column.
    /// @param name   The header text. Treated as HTML, as `MyDataGrid` does.
    /// @param width  The width in pixels.
    public void addColumn(final Column<R, ?> column, final String name, final int width) {
        addColumn(column, textHeader(name), ColumnSizing.fixed(width));
    }

    /// Add a fixed-width column that cannot be resized or moved.
    ///
    /// @param column The column.
    /// @param header The header.
    /// @param width  The width in pixels.
    public void addColumn(final Column<R, ?> column, final Header<?> header, final int width) {
        addColumn(column, header, ColumnSizing.fixed(width));
    }

    /// Add a column that the user can resize and move.
    ///
    /// @param column The column.
    /// @param name   The header text. Treated as HTML, as `MyDataGrid` does.
    /// @param width  The starting width in pixels.
    public void addResizableColumn(final Column<R, ?> column, final String name, final int width) {
        addColumn(column, textHeader(name), ColumnSizing.resizable(width));
    }

    /// Add a column that the user can resize and move.
    ///
    /// @param column The column.
    /// @param header The header.
    /// @param width  The starting width in pixels.
    public void addResizableColumn(final Column<R, ?> column, final Header<?> header, final int width) {
        addColumn(column, header, ColumnSizing.resizable(width));
    }

    /// Add a column as a [ColSpec] describes it, sized as `MyDataGrid` sizes it
    /// ([ColumnSizing#fromColSettings]).
    ///
    /// @param colSpec The column's description.
    @Override
    public void addColumn(final ColSpec<R> colSpec) {
        addColumn(colSpec.getColumn(),
                colSpec.getHeader(),
                ColumnSizing.fromColSettings(colSpec.getColSettings(), colSpec.getWidth()));
    }

    /// Add a fill column that takes a weighted share of the spare width on the first layout.
    ///
    /// @param column              The column.
    /// @param name                The header text. Treated as HTML, as `MyDataGrid` does.
    /// @param fillWeight          The column's share of the spare width relative to other fill
    ///                            columns.
    /// @param initialMinimumWidth The declared width in pixels, used as a floor.
    public void addAutoResizableColumn(final Column<R, ?> column,
                                       final String name,
                                       final int fillWeight,
                                       final int initialMinimumWidth) {
        addColumn(column, textHeader(name), ColumnSizing.fill(fillWeight, initialMinimumWidth));
    }

    /// Add a fill column with the default weight.
    ///
    /// @param column              The column.
    /// @param name                The header text. Treated as HTML, as `MyDataGrid` does.
    /// @param initialMinimumWidth The declared width in pixels, used as a floor.
    public void addAutoResizableColumn(final Column<R, ?> column,
                                       final String name,
                                       final int initialMinimumWidth) {
        addColumn(column, textHeader(name), ColumnSizing.fill(initialMinimumWidth));
    }

    /// Add a fill column with the default weight.
    ///
    /// @param column              The column.
    /// @param header              The header.
    /// @param initialMinimumWidth The declared width in pixels, used as a floor.
    public void addAutoResizableColumn(final Column<R, ?> column,
                                       final Header<?> header,
                                       final int initialMinimumWidth) {
        addColumn(column, header, ColumnSizing.fill(initialMinimumWidth));
    }

    /// Add a column whose starting width is a percentage of the grid's initial visible width.
    ///
    /// @param column  The column.
    /// @param name    The header text. Treated as HTML, as `MyDataGrid` does.
    /// @param percent The percentage, e.g. `25`.
    public void addPercentColumn(final Column<R, ?> column, final String name, final double percent) {
        addColumn(column, textHeader(name), ColumnSizing.percent(percent));
    }

    /// Add a column whose starting width is a percentage of the grid's initial visible width.
    ///
    /// @param column  The column.
    /// @param header  The header.
    /// @param percent The percentage, e.g. `25`.
    public void addPercentColumn(final Column<R, ?> column, final Header<?> header, final double percent) {
        addColumn(column, header, ColumnSizing.percent(percent));
    }

    /// Add a column with explicit sizing settings.
    ///
    /// @param column The column.
    /// @param header The header. May be `null` for no header content.
    /// @param sizing The sizing settings.
    public void addColumn(final Column<R, ?> column, final Header<?> header, final ColumnSizing sizing) {
        insertColumn(columnModel.size(), column, header, sizing);
    }

    /// Insert a column with explicit sizing settings.
    ///
    /// @param beforeIndex The index to insert at.
    /// @param column      The column.
    /// @param header      The header. May be `null` for no header content.
    /// @param sizing      The sizing settings.
    public void insertColumn(final int beforeIndex,
                             final Column<R, ?> column,
                             final Header<?> header,
                             final ColumnSizing sizing) {
        columnModel.insert(beforeIndex, column, sizing);
        headers.put(column, header);
        scheduleRender();
    }

    /// Remove a column.
    ///
    /// @param column The column.
    public void removeColumn(final Column<R, ?> column) {
        if (columnModel.remove(column)) {
            headers.remove(column);
            wrappingColumns.remove(column);
            scheduleRender();
        }
    }

    /// Remove the column at an index.
    ///
    /// @param index The column index.
    public void removeColumn(final int index) {
        removeColumn(columnModel.getColumn(index));
    }

    /// Move a column. The heading listener, if any, is told before the move, as `MyDataGrid` does.
    ///
    /// @param fromIndex The current index of the column.
    /// @param toIndex   The position to insert it before, counted in the order before the move.
    public void moveColumn(final int fromIndex, final int toIndex) {
        if (headingListener != null) {
            headingListener.moveColumn(fromIndex, toIndex);
        }
        columnModel.move(fromIndex, toIndex);
        scheduleRender();
    }

    /// Set a column's width as a user resize would, telling the heading listener first.
    ///
    /// @param index The column index.
    /// @param width The width in pixels.
    public void resizeColumn(final int index, final int width) {
        if (headingListener != null) {
            headingListener.resizeColumn(index, width);
        }
        columnModel.setWidth(index, width);
        scheduleRender();
    }

    /// Set a column's width programmatically.
    ///
    /// @param column The column.
    /// @param width  The width. Only pixel widths are supported; other units are treated as pixels.
    /// @param unit   The unit.
    public void setColumnWidth(final Column<R, ?> column, final int width, final Unit unit) {
        final int index = columnModel.indexOf(column);
        if (index != -1) {
            columnModel.setWidth(index, width);
            scheduleRender();
        }
    }

    /// Declare whether a column's content wraps. Any wrapping column switches virtual mode off for
    /// the whole grid, because rows then vary in height.
    ///
    /// @param column The column.
    /// @param wraps  Whether its content wraps.
    public void setColumnWraps(final Column<R, ?> column, final boolean wraps) {
        final boolean changed = wraps
                ? wrappingColumns.add(column)
                : wrappingColumns.remove(column);
        if (changed) {
            scheduleRender();
        }
    }

    /// Grow fill columns to take up any spare width, as `MyDataGrid.resizeTableToFitColumns()`
    /// callers expect after rebuilding columns. Without `redistribute` this only re-applies widths.
    ///
    /// @param redistribute Whether to give spare width to the fill columns.
    public void resizeTableToFitColumns(final boolean redistribute) {
        if (redistribute) {
            columnModel.fill(getElement().getClientWidth());
        }
        scheduleRender();
    }

    /// Re-apply column widths. Equivalent to `resizeTableToFitColumns(false)`.
    public void resizeTableToFitColumns() {
        resizeTableToFitColumns(false);
    }

    /// @return The number of columns.
    public int getColumnCount() {
        return columnModel.size();
    }

    /// @param index The column index.
    /// @return The column.
    public Column<R, ?> getColumn(final int index) {
        return columnModel.getColumn(index);
    }

    /// @param column The column.
    /// @return The column's index, or `-1`.
    public int getColumnIndex(final Column<R, ?> column) {
        return columnModel.indexOf(column);
    }

    /// @param index The column index.
    /// @return The column's header, or `null`.
    public Header<?> getHeader(final int index) {
        return headers.get(columnModel.getColumn(index));
    }

    /// @param index The column index.
    /// @return The column's current width in pixels.
    public int getColumnWidth(final int index) {
        return columnModel.getWidth(index);
    }

    /// @return The column model. Exposed for the column interaction controllers.
    ColumnModel<Column<R, ?>> getColumnModel() {
        return columnModel;
    }

    /// Set a column's width while the user drags its edge. The heading listener is told once the
    /// drag ends, by [#commitColumnResize].
    ///
    /// @param index The column index.
    /// @param width The width in pixels. Never set below the column's minimum width.
    void resizeColumnLive(final int index, final int width) {
        columnModel.resize(index, width);
        scheduleRender();
    }

    /// Tell the heading listener the width a column was dragged to, as `MyDataGrid.resizeColumn`
    /// does when a resize ends.
    ///
    /// @param index The column index.
    /// @param width The column's new width in pixels.
    void commitColumnResize(final int index, final int width) {
        if (headingListener != null) {
            headingListener.resizeColumn(index, width);
        }
        scheduleRender();
    }

    /// Append a hidden copy of a cell, for measuring its content (R-CR4). It has the cell's classes
    /// and content but none of the row state, e.g. selection.
    ///
    /// @param indexOnPage The page-relative row index.
    /// @param index       The column index.
    /// @param sb          The builder to append the copy to.
    void renderCellCopy(final int indexOnPage, final int index, final SafeHtmlBuilder sb) {
        final R value = pageState.getRow(indexOnPage);
        if (value != null) {
            html.cellCopy(value, pageState.getPageStart() + indexOnPage, index, sb);
        }
    }

    /// @return The header row element, which header copies are measured in.
    Element getHeaderRowElement() {
        return hideHeader
                ? null
                : headerRow;
    }

    /// @return The element the rows are in, which cell copies are measured in.
    Element getBodyElement() {
        return body;
    }

    /// @return The page-relative indices of the rows the user can currently see, roughly.
    List<Integer> getRowsInView() {
        final List<Integer> rows = new ArrayList<>();
        if (!isAttached() || renderedVirtual == null) {
            return rows;
        }
        final int first = gridRows.topVisibleRow(renderedVirtual);
        final int count = (int) Math.ceil(scroller.getClientHeight() / gridRows.getOneLineRowHeight()) + 1;
        for (int i = first; i < Math.min(first + count, pageState.getRowDataSize()); i++) {
            rows.add(i);
        }
        return rows;
    }

    /// The text a cell shows, from rendering its row's data rather than reading the DOM, so a row
    /// that is not rendered (in virtual mode) has text too (R-M4). Whitespace is normalised.
    ///
    /// @param indexOnPage The page-relative row index.
    /// @param index       The column index.
    /// @return The cell's text, or an empty string.
    String getCellText(final int indexOnPage, final int index) {
        final R value = pageState.getRow(indexOnPage);
        if (value == null || index < 0 || index >= columnModel.size()) {
            return "";
        }
        return html.cellText(value, pageState.getPageStart() + indexOnPage, index);
    }

    /// @param index The column index.
    /// @return The text the column's header shows, with whitespace normalised.
    String getHeaderText(final int index) {
        return html.headerText(index);
    }

    private static Header<?> textHeader(final String name) {
        return new SafeHtmlHeader(SafeHtmlUtils.fromSafeConstant(NullSafe.string(name)));
    }

    // --------------------------------------------------------------------------------
    // Sorting
    // --------------------------------------------------------------------------------

    /// Add a handler for header sort clicks.
    ///
    /// @param handler The handler.
    /// @return The registration.
    public HandlerRegistration addColumnSortHandler(final ColumnSortEvent.Handler handler) {
        return addHandler(handler, ColumnSortEvent.getType());
    }

    /// @return The sort list, primary sort first.
    public ColumnSortList getColumnSortList() {
        return sortList;
    }

    /// Push a column onto the sort list without firing an event, as `MyDataGrid.sort` does. Pushing
    /// the current primary sort column again reverses it.
    ///
    /// @param column The column.
    public void sort(final Column<R, ?> column) {
        sortList.push(column);
    }

    /// Clear the sort list without firing an event.
    public void clearColumnSortList() {
        sortList.clear();
    }

    /// Declare the screen's default sort (requirement R-H4b, design deviation D10): the order the
    /// data has when nothing else is chosen. If nothing is sorted yet it is shown in the header at
    /// once, without a sort event (the first fetch reads the sort list). Whenever a click leaves
    /// the sort list empty (the third click on the only sorted column), the default comes back,
    /// with a sort event, so the header always shows the order the data is in.
    ///
    /// @param defaultSort The default sort, primary column first. Empty for none.
    public void setDefaultSort(final ColumnSortInfo... defaultSort) {
        this.defaultSort.clear();
        for (final ColumnSortInfo info : defaultSort) {
            this.defaultSort.add(new SortEntry<>(info.getColumn(), info.isAscending()));
        }
        if (sortList.size() == 0) {
            setSortList(this.defaultSort);
        }
    }

    // Replaces the sort list's entries, primary column first
    private void setSortList(final List<SortEntry<Column<?, ?>>> entries) {
        // ColumnSortList.push inserts at the front, so push in reverse.
        sortList.clear();
        for (int i = entries.size() - 1; i >= 0; i--) {
            final SortEntry<Column<?, ?>> entry = entries.get(i);
            sortList.push(new ColumnSortInfo(entry.getColumn(), entry.isAscending()));
        }
    }

    private void onHeaderSortClick(final Column<R, ?> column, final boolean ctrl) {
        final List<SortEntry<Column<?, ?>>> current = new ArrayList<>();
        for (int i = 0; i < sortList.size(); i++) {
            final ColumnSortInfo info = sortList.get(i);
            current.add(new SortEntry<>(info.getColumn(), info.isAscending()));
        }
        final List<SortEntry<Column<?, ?>>> next = SortCycle.orDefault(
                SortCycle.next(current, column, column.isDefaultSortAscending(), ctrl),
                defaultSort);
        setSortList(next);
        ColumnSortEvent.fire(this, sortList);
    }

    // --------------------------------------------------------------------------------
    // Display options
    // --------------------------------------------------------------------------------

    /// Switch wrapping on or off. Wrapping suspends virtual mode while it is on.
    ///
    /// @param multiLine Whether cell and header text wraps.
    public void setMultiLine(final boolean multiLine) {
        if (this.multiLine != multiLine) {
            this.multiLine = multiLine;
            setStyleName("data-grid--multiline", multiLine);
            scheduleRender();
        }
    }

    /// @return Whether cell and header text wraps.
    public boolean isMultiLine() {
        return multiLine;
    }

    /// Switch virtual rendering on or off. Virtual rendering only takes effect while nothing wraps.
    ///
    /// @param virtual Whether to render only the visible rows, at a fixed row height.
    public void setVirtual(final boolean virtual) {
        if (this.virtual != virtual) {
            this.virtual = virtual;
            scheduleRender();
        }
    }

    /// @return Whether virtual rendering has been requested.
    public boolean isVirtual() {
        return virtual;
    }

    /// Set the fixed row height used in virtual mode.
    ///
    /// @param rowHeight The height in pixels, or `0` to measure it from the current density.
    public void setRowHeight(final int rowHeight) {
        this.rowHeight = Math.max(0, rowHeight);
        scheduleRender();
    }

    /// Makes the grid a list rather than a grid (see [DataGridProfile]): its roles, whether it has a
    /// header, and its keys. Set it before the grid is shown.
    ///
    /// @param profile The profile.
    public void setProfile(final DataGridProfile profile) {
        this.profile = Objects.requireNonNull(profile);
        html.setCellRole(profile.getCellRole());
        final boolean cells = profile.hasCells();
        if (cells) {
            body.setAttribute("role", "rowgroup");
        } else {
            body.removeAttribute("role");
            setHideHeader(true);
        }
        setStyleName("data-grid--list", !cells);
        // The column widths are laid out as the profile's (a list's last column fills it)
        renderedWidthVersion = -1;
        redraw();
    }

    /// Makes the grid as high as its rows, up to the height its container allows (the container's
    /// `max-height`, which the grid takes), then scrolling, as a list in a popup grows with its
    /// values. By default a grid fills its container.
    ///
    /// @param fitContent Whether the grid is as high as its rows.
    public void setFitContent(final boolean fitContent) {
        setStyleName("data-grid--fit", fitContent);
    }

    /// Makes the grid as wide as its widest row, up to the width its container allows, as a list
    /// in a popup is. By default a grid fills its container's width.
    ///
    /// @param fitWidth Whether the grid is as wide as its widest row.
    public void setFitWidth(final boolean fitWidth) {
        setStyleName("data-grid--fit-width", fitWidth);
    }

    /// Stripes a list's rows, as a grid's are (a list isn't striped by default), e.g. for results
    /// of two lines each.
    ///
    /// @param striped Whether the rows are striped.
    public void setStriped(final boolean striped) {
        setStyleName("data-grid--striped", striped);
    }

    /// Shows the page that starts at a row, keeping the page size.
    ///
    /// @param index The absolute index of the page's first row.
    public void setPageStart(final int index) {
        setVisibleRange(index, getVisibleRange().getLength());
    }

    /// Leaves out the padding round each cell's content, for cells that draw their own, e.g. the
    /// explorer's (`explorerCell`), so a row is as high as its cell draws it.
    ///
    /// @param cellPadding Whether the cells' content is padded (the default).
    public void setCellPadding(final boolean cellPadding) {
        setStyleName("data-grid--no-cell-padding", !cellPadding);
    }

    /// Makes the rows say whether they are ticked (`aria-checked`, `mixed` for part ticked), as a
    /// list of tick boxes' options do: the row's own state, which Space or a press changes (see
    /// [DataGridWidgetSelectionEventManager#setChooser]), not a check box in it.
    ///
    /// @param rowChecked Gives a row's state, or null for rows that aren't ticked.
    public void setRowChecked(final Function<R, TickBoxState> rowChecked) {
        this.rowChecked = rowChecked;
        redraw();
    }

    /// @return Gives a row's tick state (see [#setRowChecked(Function)]), or null.
    Function<R, TickBoxState> getRowChecked() {
        return rowChecked;
    }

    /// Takes a tree's rows from a function (see [DataGridProfile#TREE]): each row's expander, saying
    /// its depth and whether it is a leaf or expanded, and what expands or collapses one, as clicking
    /// its expander does.
    ///
    /// @param expanderOf   Gives a row's expander.
    /// @param expandAction Expands (true) or collapses (false) a row.
    public void setTreeRows(final Function<R, Expander> expanderOf, final BiConsumer<R, Boolean> expandAction) {
        tree.setSource(expanderOf, expandAction);
        redraw();
    }

    /// @return The grid's profile (see [DataGridProfile]).
    public DataGridProfile getProfile() {
        return profile;
    }

    /// Goes to the next row whose text starts with what has been typed, as typing in a list does
    /// (see [TypeAhead]), choosing it as Up and Down do.
    ///
    /// @param typed The character typed.
    /// @return Whether a row matched (and has the focus).
    public boolean typeAhead(final String typed) {
        final int row = gridRows.typeAhead(typed, keyboardSelectedRow);
        if (row < 0) {
            return false;
        }
        keyboard.moveTo(row);
        return true;
    }

    /// @param hideHeader Whether to hide the header row.
    public void setHideHeader(final boolean hideHeader) {
        this.hideHeader = hideHeader;
        setStyleName("data-grid--no-header", hideHeader);
        scheduleRender();
    }

    /// @param allowMove Whether the user can drag columns to new positions.
    public void setAllowMove(final boolean allowMove) {
        this.allowMove = allowMove;
    }

    /// @return Whether the user can drag columns to new positions.
    public boolean isAllowMove() {
        return allowMove;
    }

    /// @param allowResize Whether the user can drag column edges to resize them.
    public void setAllowResize(final boolean allowResize) {
        this.allowResize = allowResize;
    }

    /// @return Whether the user can drag column edges to resize them.
    public boolean isAllowResize() {
        return allowResize;
    }

    /// @param allowHeaderSelection Whether header text can be selected and the header menu shown.
    public void setAllowHeaderSelection(final boolean allowHeaderSelection) {
        this.allowHeaderSelection = allowHeaderSelection;
        setStyleName("data-grid--header-selection", allowHeaderSelection);
    }

    /// @return Whether header text can be selected and the header menu shown.
    public boolean isAllowHeaderSelection() {
        return allowHeaderSelection;
    }

    /// @param hasContextMenu Whether right-clicking shows the grid's context menu.
    public void hasContextMenu(final boolean hasContextMenu) {
        this.hasContextMenu = hasContextMenu;
    }

    /// @return Whether right-clicking shows the grid's context menu.
    public boolean hasContextMenu() {
        return hasContextMenu;
    }

    /// @param headingListener Notified of header menu clicks and column moves and resizes.
    public void setHeadingListener(final HeadingListener headingListener) {
        this.headingListener = headingListener;
    }

    /// @return The heading listener, or `null`.
    public HeadingListener getHeadingListener() {
        return headingListener;
    }

    /// Names the grid for screen readers (its `grid`), e.g. `Users and groups`. Without one, it is
    /// named by its table name ([#setTableName(String)]).
    ///
    /// @param name The name, or null or blank for the table name.
    public void setAccessibleName(final String name) {
        this.accessibleName = name;
        updateAccessibleName();
    }

    // The grid's name for screen readers: its own, else its table name, else none
    private void updateAccessibleName() {
        final String name = NullSafe.isNonBlankString(accessibleName)
                ? accessibleName
                : tableName;
        if (NullSafe.isBlankString(name)) {
            scroller.removeAttribute("aria-label");
        } else {
            scroller.setAttribute("aria-label", name);
        }
    }

    /// @return The id of the element that is the grid (or list or tree) to assistive technology,
    /// given one if it had none, e.g. for a box that controls a list (`aria-controls`).
    public String getAccessibleElementId() {
        if (scroller.getId().isEmpty()) {
            scroller.setId(Document.get().createUniqueId());
        }
        return scroller.getId();
    }

    /// Says whether several rows of a list or tree can be chosen (`aria-multiselectable`), where
    /// the selection manager doesn't (it is set from a [DataGridWidgetSelectionEventManager]'s).
    ///
    /// @param multiSelectable Whether several rows can be chosen.
    public void setMultiSelectable(final boolean multiSelectable) {
        this.multiSelectable = multiSelectable;
        scheduleRender();
    }

    /// Names each row for assistive technology where its content doesn't, e.g. "None" for a list's
    /// blank option.
    ///
    /// @param rowName Gives a row's name (`aria-label`), or null for the name its content gives.
    public void setRowName(final Function<R, String> rowName) {
        this.rowName = rowName;
        redraw();
    }

    /// @return Gives a row's name (see [#setRowName(Function)]), or null.
    Function<R, String> getRowName() {
        return rowName;
    }

    /// Describes each row for assistive technology (`aria-description`), read after its name, role
    /// and state, e.g. the type of an explorer item ("Countries, tree item, Dictionary").
    ///
    /// @param rowDescription Gives a row's description, or null (or a blank one) for none.
    public void setRowDescription(final Function<R, String> rowDescription) {
        this.rowDescription = rowDescription;
        redraw();
    }

    /// @return Gives a row's description (see [#setRowDescription(Function)]), or null.
    Function<R, String> getRowDescription() {
        return rowDescription;
    }

    /// @param tableName The table's name, used when copying, exporting or asking AI about it, and to
    ///                  name the grid for screen readers when it has no name of its own
    ///                  ([#setAccessibleName(String)]).
    public void setTableName(final String tableName) {
        this.tableName = tableName;
        updateAccessibleName();
    }

    /// @return The table's name, or `null`.
    public String getTableName() {
        return tableName;
    }

    /// @param emptyMessage Text to show when there are no rows, or `null` for none (the default).
    public void setEmptyMessage(final String emptyMessage) {
        this.emptyMessage = emptyMessage;
        empty.setInnerText(NullSafe.string(emptyMessage));
        scheduleRender();
    }

    /// Text to show when there are no rows, as `MyDataGrid` shows it: in normal text with a form's
    /// padding, where [#setEmptyMessage(String)] gives a row's padding and fainter text.
    ///
    /// @param text Text to show when there are no rows, or `null` for none.
    public void setEmptyText(final String text) {
        empty.addClassName("data-grid__empty--text");
        setEmptyMessage(text);
    }

    /// @param rowStyles Extra style names for each row.
    public void setRowStyles(final RowStyles<R> rowStyles) {
        this.rowStyles = rowStyles;
        redraw();
    }

    /// @param rowSpans Column spans for each row, or `null` for none.
    public void setRowSpans(final RowSpans<R> rowSpans) {
        this.rowSpans = rowSpans;
        redraw();
    }

    /// @param keyProvider Provides the key used to recognise a row across redraws.
    public void setKeyProvider(final ProvidesKey<R> keyProvider) {
        this.keyProvider = keyProvider;
    }

    /// @return The key provider, or `null`.
    public ProvidesKey<R> getKeyProvider() {
        return keyProvider;
    }

    /// Does nothing. Kept for compatibility with `MyDataGrid`; hover is pure CSS here.
    ///
    /// @param skipRowHoverCheck Ignored.
    public void setSkipRowHoverCheck(final boolean skipRowHoverCheck) {
        // Hover is handled by CSS.
    }

    // --------------------------------------------------------------------------------
    // Selection and keyboard
    // --------------------------------------------------------------------------------

    /// Create a [MultiSelectionModelImpl] and the standard selection event manager, and use them.
    ///
    /// @param allowMultiSelect Whether Ctrl and Shift can select several rows.
    /// @return The selection model.
    public MultiSelectionModelImpl<R> addDefaultSelectionModel(final boolean allowMultiSelect) {
        final MultiSelectionModelImpl<R> model = new MultiSelectionModelImpl<>();
        setSelectionModel(model, new DataGridWidgetSelectionEventManager<>(this, model, allowMultiSelect));
        return model;
    }

    @Override
    public void setSelectionModel(final SelectionModel<? super R> selectionModel) {
        NullSafe.consume(selectionModelRegistration, HandlerRegistration::removeHandler);
        selectionModelRegistration = null;
        this.selectionModel = selectionModel;
        if (selectionModel != null) {
            selectionModelRegistration = selectionModel.addSelectionChangeHandler(e -> scheduleRender());
        }
        setStyleName("data-grid--selectable", selectionModel != null);
        scheduleRender();
    }

    /// Use a selection model and the handler that turns clicks and keys into selections.
    ///
    /// @param selectionModel The selection model.
    /// @param handler        The handler, e.g. a [DataGridWidgetSelectionEventManager].
    public void setSelectionModel(final SelectionModel<? super R> selectionModel,
                                  final CellPreviewEvent.Handler<R> handler) {
        NullSafe.consume(selectionPreviewRegistration, HandlerRegistration::removeHandler);
        selectionPreviewRegistration = NullSafe.get(handler, this::addCellPreviewHandler);
        keyboard.setSelector(handler instanceof DataGridKeyboard.Selector
                ? asSelector(handler)
                : null);
        multiSelectable = handler instanceof final DataGridWidgetSelectionEventManager<?> manager
                          && manager.isAllowMultiSelect();
        setSelectionModel(selectionModel);
    }

    @SuppressWarnings("unchecked")
    private static <R> DataGridKeyboard.Selector<R> asSelector(final CellPreviewEvent.Handler<R> handler) {
        return (DataGridKeyboard.Selector<R>) handler;
    }

    @Override
    public SelectionModel<? super R> getSelectionModel() {
        return selectionModel;
    }

    @Override
    public HandlerRegistration addCellPreviewHandler(final CellPreviewEvent.Handler<R> handler) {
        return addHandler(handler, CellPreviewEvent.getType());
    }

    @Override
    public KeyboardSelectionPolicy getKeyboardSelectionPolicy() {
        return keyboardSelectionPolicy;
    }

    @Override
    public void setKeyboardSelectionPolicy(final KeyboardSelectionPolicy policy) {
        this.keyboardSelectionPolicy = Objects.requireNonNull(policy);
    }

    /// @return The page-relative index of the keyboard row. `0` until it is moved, and on an empty
    /// page, as GWT's grid reports it.
    public int getKeyboardSelectedRow() {
        return keyboardSelectedRow;
    }

    /// Move the keyboard row without taking focus.
    ///
    /// @param row The page-relative row index.
    public void setKeyboardSelectedRow(final int row) {
        setKeyboardSelectedRow(row, false);
    }

    /// Move the keyboard row.
    ///
    /// @param row        The page-relative row index. Clamped to the rows on the page.
    /// @param stealFocus Whether to focus the row, scrolling it into view.
    public void setKeyboardSelectedRow(final int row, final boolean stealFocus) {
        if (keyboardSelectionPolicy == KeyboardSelectionPolicy.DISABLED) {
            return;
        }
        final int clamped = KeyboardRow.clamp(row, getVisibleItemCount());
        if (clamped != keyboardSelectedRow || stealFocus) {
            keyboardSelectedRow = clamped;
            keyboardRowChanged = true;
            keyboardStealFocus = keyboardStealFocus || stealFocus;
            scheduleRender();
        }
    }

    /// Focus or blur the grid, as the forked GWT `AbstractHasData.setFocused` does.
    ///
    /// @param focused Whether the grid should have focus.
    public void setFocused(final boolean focused) {
        setFocus(focused);
    }

    @Override
    public void setFocus(final boolean focused) {
        if (focused) {
            final Element target = keyboard.getFocusTarget();
            refocus(target != null
                    ? target
                    : scroller);
        } else {
            scroller.blur();
        }
    }

    /// @param element An element.
    /// @return Whether it is the grid's scrolling box, its tab stop while the keyboard row isn't
    /// drawn.
    boolean isScroller(final Element element) {
        return element == scroller;
    }

    @Override
    public int getTabIndex() {
        return scroller.getTabIndex();
    }

    @Override
    public void setTabIndex(final int index) {
        scroller.setTabIndex(index);
    }

    @Override
    public void setAccessKey(final char key) {
        scroller.setPropertyString("accessKey", String.valueOf(key));
    }

    /// Add a handler for clicks on `<u link="...">` hyperlinks in cells.
    ///
    /// @param handler The handler.
    /// @return The registration.
    public HandlerRegistration addHyperlinkHandler(final HyperlinkEvent.Handler handler) {
        return addHandler(handler, HyperlinkEvent.getType());
    }

    /// @return The application event bus passed to the constructor.
    public HasHandlers getGlobalEventBus() {
        return globalEventBus;
    }

    // --------------------------------------------------------------------------------
    // HasData
    // --------------------------------------------------------------------------------

    /// Replace all the data with a list, making the page exactly that list.
    ///
    /// @param values The rows.
    public void setRowData(final List<? extends R> values) {
        setRowCount(values.size(), true);
        setVisibleRange(0, values.size());
        setRowData(0, values);
    }

    @Override
    public void setRowData(final int start, final List<? extends R> values) {
        if (pageState.setRowData(start, values)) {
            RowCountChangeEvent.fire(this, pageState.getRowCount(), pageState.isRowCountExact());
        }
        updateLoadingState();
        scheduleRender();
    }

    @Override
    public void setRowCount(final int count) {
        setRowCount(count, true);
    }

    @Override
    public void setRowCount(final int count, final boolean isExact) {
        if (pageState.setRowCount(count, isExact)) {
            updateLoadingState();
            scheduleRender();
            RowCountChangeEvent.fire(this, count, isExact);
        }
    }

    @Override
    public int getRowCount() {
        return pageState.getRowCount();
    }

    @Override
    public boolean isRowCountExact() {
        return pageState.isRowCountExact();
    }

    @Override
    public Range getVisibleRange() {
        return new Range(pageState.getPageStart(), pageState.getPageSize());
    }

    @Override
    public void setVisibleRange(final int start, final int length) {
        setVisibleRange(new Range(start, length));
    }

    @Override
    public void setVisibleRange(final Range range) {
        if (pageState.setVisibleRange(range.getStart(), range.getLength(), false)) {
            updateLoadingState();
            scheduleRender();
            RangeChangeEvent.fire(this, getVisibleRange());
        }
    }

    @Override
    public void setVisibleRangeAndClearData(final Range range, final boolean forceRangeChangeEvent) {
        final boolean changed = pageState.setVisibleRange(range.getStart(), range.getLength(), true);
        updateLoadingState();
        scheduleRender();
        if (changed || forceRangeChangeEvent) {
            RangeChangeEvent.fire(this, getVisibleRange());
        }
    }

    /// @param pageSize The page size.
    public void setPageSize(final int pageSize) {
        setVisibleRange(pageState.getPageStart(), pageSize);
    }

    /// @return The page size.
    public int getPageSize() {
        return pageState.getPageSize();
    }

    /// @return The absolute index of the first row on the page.
    public int getPageStart() {
        return pageState.getPageStart();
    }

    @Override
    public R getVisibleItem(final int indexOnPage) {
        return pageState.getRow(indexOnPage);
    }

    @Override
    public int getVisibleItemCount() {
        return pageState.getRowDataSize();
    }

    @Override
    public List<R> getVisibleItems() {
        return new ArrayList<>(pageState.getRows());
    }

    @Override
    public HandlerRegistration addRangeChangeHandler(final RangeChangeEvent.Handler handler) {
        return addHandler(handler, RangeChangeEvent.getType());
    }

    @Override
    public HandlerRegistration addRowCountChangeHandler(final RowCountChangeEvent.Handler handler) {
        return addHandler(handler, RowCountChangeEvent.getType());
    }

    /// Add a handler for loading state changes.
    ///
    /// @param handler The handler.
    /// @return The registration.
    public HandlerRegistration addLoadingStateChangeHandler(final LoadingStateChangeEvent.Handler handler) {
        return addHandler(handler, LoadingStateChangeEvent.TYPE);
    }

    private void updateLoadingState() {
        final int cacheSize = pageState.getRowDataSize();
        final int pageSize = pageState.getPageSize();
        final int currentPageSize = pageState.isRowCountExact()
                ? Math.max(0, Math.min(pageSize, pageState.getRowCount() - pageState.getPageStart()))
                : pageSize;
        final LoadingState state;
        if (cacheSize >= currentPageSize) {
            state = LoadingState.LOADED;
        } else if (cacheSize == 0) {
            state = LoadingState.LOADING;
        } else {
            state = LoadingState.PARTIALLY_LOADED;
        }
        loadingState = state;
        loadingStateUpdated = true;
    }

    // As GWT's HasDataPresenter: the loading state is fired once for each change of data or row
    // count, when it is drawn, even if it is unchanged. Screens rely on a LOADED for every load, e.g.
    // to choose the first row or check what was loaded after a refresh of the same page.
    private void fireLoadingState() {
        if (loadingStateUpdated) {
            loadingStateUpdated = false;
            fireEvent(new LoadingStateChangeEvent(loadingState));
        }
    }

    // --------------------------------------------------------------------------------
    // Redraw
    // --------------------------------------------------------------------------------

    /// Render every row and the header again.
    public void redraw() {
        pageState.markAllDirty();
        headersDirty = true;
        scheduleRender();
    }

    /// Render one row again.
    ///
    /// @param absRowIndex The absolute row index.
    public void redrawRow(final int absRowIndex) {
        pageState.markDirty(absRowIndex - pageState.getPageStart());
        scheduleRender();
    }

    /// Render the header again, e.g. after a header cell's state changed.
    public void redrawHeaders() {
        headersDirty = true;
        scheduleRender();
    }

    /// Apply any pending changes now instead of at the end of the current event.
    public void flush() {
        if (renderScheduled) {
            render();
        }
    }

    /// @param element An element, e.g. a key event's target.
    /// @return Whether it is in the grid's rows (not, e.g., in its column headers), as
    /// `MyDataGrid`'s.
    public boolean isInRows(final Element element) {
        return element != null && body.isOrHasChild(element);
    }

    /// @param indexOnPage The page-relative row index.
    /// @return The row's element, or `null` if the row is not currently rendered.
    public Element getRowElement(final int indexOnPage) {
        return gridRows.getElement(indexOnPage);
    }

    /// @return The grid's tree rows (see [TreeRows]).
    TreeRows<R> getTreeRows() {
        return tree;
    }

    /// @param column The column index.
    /// @return The header cell element, or `null` if the header has not been rendered.
    public Element getHeaderCellElement(final int column) {
        return gridHeader.getCellElement(column);
    }

    /// Does what a click on a column's header does, for Enter or Space on it: sorts a sortable
    /// column, or else opens the column's menu where the grid has one.
    ///
    /// @param column The column index.
    /// @param event  The key event.
    void activateHeader(final int column, final NativeEvent event) {
        if (column < 0 || column >= columnModel.size()) {
            return;
        }
        final Column<R, ?> col = columnModel.getColumn(column);
        if (col.isSortable()) {
            onHeaderSortClick(col, DataGridDom.isCtrl(event));
        } else {
            showHeaderMenu(column, event);
        }
    }

    /// Opens a column's menu, as a click on its header does, if the grid has one.
    ///
    /// @param column The column index.
    /// @param event  The key event.
    void showHeaderMenu(final int column, final NativeEvent event) {
        if (isAllowHeaderSelection() && headingListener != null) {
            columnInteractions.showMenu(column, event);
        }
    }

    /// Makes the keyboard's focus target the grid's one tab stop (see [DataGridKeyboard]).
    void updateTabStops() {
        keyboard.updateTabStops(scroller);
    }

    private void scheduleRender() {
        if (!renderScheduled) {
            renderScheduled = true;
            Scheduler.get().scheduleFinally(() -> {
                if (renderScheduled) {
                    render();
                }
            });
        }
    }

    private boolean isEffectiveVirtual() {
        return virtual && !multiLine && wrappingColumns.isEmpty();
    }

    private void render() {
        renderScheduled = false;
        // Columns added after the grid was laid out (once a fetch returns) haven't been resolved
        // by the resize observer, which saw none
        if (!columnModel.isResolved() && isAttached()) {
            columnModel.resolve(getElement().getClientWidth());
        }

        final boolean effectiveVirtual = isEffectiveVirtual();
        int anchorRow = -1;
        if (renderedVirtual != null && renderedVirtual != effectiveVirtual && isAttached()) {
            // Keep the top visible row, not the pixel offset, across a mode switch.
            anchorRow = gridRows.topVisibleRow(renderedVirtual);
        }

        final boolean structureChanged = renderedStructureVersion != columnModel.getStructureVersion();
        if (structureChanged) {
            // New columns may draw taller rows
            gridRows.forgetRowHeight();
            final boolean treeRows = tree.update();
            scroller.setAttribute("role", profile.getRole() != null
                    ? profile.getRole()
                    : treeRows
                            ? "treegrid"
                            : "grid");
        }
        final boolean headerChanged = structureChanged || headersDirty;
        if (headerChanged) {
            gridHeader.render(hideHeader);
        }
        final boolean widthsChanged = structureChanged || renderedWidthVersion != columnModel.getWidthVersion();
        if (widthsChanged) {
            applyWidths();
        }
        if (headerChanged || widthsChanged) {
            // Only once the column widths apply, or the header is measured in the wrong layout. A
            // width change can change the header's height too, e.g. when its text wraps.
            gridHeader.snapHeight(hideHeader);
        }

        resolveKeyboardRow();
        final int[] dirty = pageState.takeDirtyRows();
        final boolean modeChanged = renderedVirtual == null || renderedVirtual != effectiveVirtual;
        setStyleName("data-grid--virtual", effectiveVirtual);
        renderedVirtual = effectiveVirtual;
        gridRows.render(structureChanged || modeChanged, dirty);

        renderedStructureVersion = columnModel.getStructureVersion();
        renderedWidthVersion = columnModel.getWidthVersion();
        headersDirty = false;

        final boolean isEmpty = pageState.getRowDataSize() == 0;
        empty.getStyle().setProperty("display", isEmpty && emptyMessage != null
                ? ""
                : "none");
        if (profile.hasCells()) {
            scroller.setAttribute("aria-rowcount", String.valueOf(pageState.getRowCount() + 1));
            scroller.setAttribute("aria-colcount", String.valueOf(columnModel.size()));
        } else {
            // A list says how many rows there are on each option (aria-setsize)
            scroller.removeAttribute("aria-rowcount");
            scroller.removeAttribute("aria-colcount");
            if (multiSelectable) {
                scroller.setAttribute("aria-multiselectable", "true");
            } else {
                scroller.removeAttribute("aria-multiselectable");
            }
        }

        if (anchorRow >= 0) {
            gridRows.scrollToTop(anchorRow);
        }
        if (keyboardStealFocus) {
            keyboardStealFocus = false;
            focusKeyboardRow();
        }
        updateTabStops();
        updateScrollbars();
        fireLoadingState();
    }

    // As GWT's HasDataPresenter: a keyboard row moved since the last render takes its row's key;
    // otherwise it follows its key to wherever that row now is (R-S12).
    private void resolveKeyboardRow() {
        final List<R> rows = pageState.getRows();
        if (keyboardSelectionPolicy == KeyboardSelectionPolicy.DISABLED) {
            keyboardSelectedRow = 0;
            keyboardRowKey = null;
        } else if (keyboardRowChanged) {
            keyboardSelectedRow = KeyboardRow.clamp(keyboardSelectedRow, rows.size());
            keyboardRowKey = keyOfRow(keyboardSelectedRow);
        } else if (keyboardRowKey != null) {
            final List<Object> keys = new ArrayList<>(rows.size());
            for (int i = 0; i < rows.size(); i++) {
                keys.add(keyOfRow(i));
            }
            final int match = KeyboardRow.bestMatch(keyboardRowKey,
                    KeyboardRow.clamp(keyboardSelectedRow, rows.size()),
                    keys);
            if (match >= 0) {
                keyboardSelectedRow = match;
            } else {
                // The row has gone: back to the first row, with no row remembered.
                keyboardSelectedRow = 0;
                keyboardRowKey = null;
            }
        } else {
            keyboardSelectedRow = KeyboardRow.clamp(keyboardSelectedRow, rows.size());
        }
        keyboardRowChanged = false;
    }

    private Object keyOfRow(final int index) {
        final R row = pageState.getRow(index);
        if (row == null) {
            return null;
        }
        return keyProvider != null
                ? keyProvider.getKey(row)
                : row;
    }

    private void applyWidths() {
        if (profile.hasCells()) {
            DataGridDom.setCssVariable(getElement(), "--data-grid__columns", columnModel.toGridTemplate());
            DataGridDom.setCssVariable(getElement(), "--data-grid__total-width", columnModel.getTotalWidth() + "px");
        } else {
            // A list is as wide as what it is in, its last column taking the rest of the width
            DataGridDom.setCssVariable(getElement(), "--data-grid__columns", columnModel.toListTemplate());
            DataGridDom.setCssVariable(getElement(),
                    "--data-grid__total-width",
                    "max(100%, " + columnModel.getListMinWidth() + "px)");
        }
    }

    // --------------------------------------------------------------------------------
    // Rows (drawn by GridRows)
    // --------------------------------------------------------------------------------

    /// @param row   A row, or null for a row not yet loaded.
    /// @param index The row's page-relative index.
    /// @return The row's key: the key provider's, else the row itself (a placeholder's by index).
    Object getKey(final R row, final int index) {
        if (row == null) {
            return "__placeholder__" + index;
        }
        if (keyProvider != null) {
            final Object key = keyProvider.getKey(row);
            if (key != null) {
                return key;
            }
        }
        return row;
    }

    // The row height is measured again on the next render, e.g. after the columns or density
    // changed
    private void forgetRowHeight() {
        if (gridRows.forgetRowHeight()) {
            scheduleRender();
        }
    }

    /// @return Whether the rows are drawn in virtual mode (only those in view).
    boolean isRenderedVirtual() {
        return Boolean.TRUE.equals(renderedVirtual);
    }

    /// @return Whether the focus is in the grid's rows; the keyboard row is only shown while it is.
    boolean hasRowFocus() {
        return hasFocus;
    }

    /// @return The fixed row height set ([#setRowHeight(int)]), or 0 for rows as high as their
    /// content.
    int getFixedRowHeight() {
        return rowHeight;
    }

    /// @return The row styles set ([#setRowStyles(RowStyles)]), or null.
    RowStyles<R> getRowStyles() {
        return rowStyles;
    }

    /// @return The row spans set ([#setRowSpans(RowSpans)]), or null.
    RowSpans<R> getRowSpans() {
        return rowSpans;
    }

    /// @return The header's height, or 0 if it is hidden.
    int getHeaderHeight() {
        return hideHeader
                ? 0
                : head.getOffsetHeight();
    }

    // --------------------------------------------------------------------------------
    // Scrolling
    // --------------------------------------------------------------------------------

    /// Scroll so that a row is fully visible, moving as little as possible.
    ///
    /// @param indexOnPage The page-relative row index.
    public void scrollRowIntoView(final int indexOnPage) {
        gridRows.scrollIntoView(indexOnPage);
    }

    /// Scroll sideways so that an element in the grid (a cell, a column's header or a control in
    /// one) is in view, moving as little as possible. Only the grid scrolls, never the page around
    /// it, as the browser's `scrollIntoView` would.
    ///
    /// @param element The element.
    void scrollIntoViewHorizontally(final Element element) {
        DataGridDom.scrollHorizontallyWithin(scroller, element);
    }


    // Whether the focus is in the grid, but not in its header (as GWT's allowFocus, gh-4684). A
    // focus that leaves is checked once it has settled, as it passes through the body on the way.
    private void updateHasFocus() {
        final Element active = DataGridDom.getActiveElement();
        final boolean focused = active != null
                                && getElement().isOrHasChild(active)
                                && !head.isOrHasChild(active);
        if (focused != hasFocus) {
            hasFocus = focused;
            scheduleRender();
        }
    }

    private void focusKeyboardRow() {
        if (keyboardSelectedRow < 0) {
            return;
        }
        scrollRowIntoView(keyboardSelectedRow);
        if (Boolean.TRUE.equals(renderedVirtual)) {
            // The row may only now be inside the rendered window.
            gridRows.render(false, new int[]{0, 0});
        }
        final Element element = keyboard.getFocusTarget();
        if (element != null) {
            refocus(element);
            // A mouse press's default action runs after this, and drops the focus if the pressed
            // element was rewritten (e.g. its cells restyled as selected). GWT focuses after it, in
            // a deferred command, so do the same if the focus went nowhere.
            Scheduler.get().scheduleDeferred(() -> {
                final Element active = DataGridDom.getActiveElement();
                final Element current = keyboard.getFocusTarget();
                if (current != null && (active == null || active == Document.get().getBody())) {
                    refocus(current);
                }
            });
        }
    }

    private void onScroll() {
        // Natural mode has nothing to do on a scroll unless the thumbs need placing by script.
        if (!Boolean.TRUE.equals(renderedVirtual) && scrollbars.followsScroll()) {
            return;
        }
        if (!frameScheduled) {
            frameScheduled = true;
            DataGridDom.requestAnimationFrame(() -> {
                frameScheduled = false;
                if (Boolean.TRUE.equals(renderedVirtual)) {
                    gridRows.render(false, new int[]{0, 0});
                    // The keyboard row may have been drawn, or no longer be
                    updateTabStops();
                }
                updateScrollbars();
            });
        }
    }

    private void updateScrollbars() {
        if (isAttached()) {
            scrollbars.update();
        }
    }

    // --------------------------------------------------------------------------------
    // Attach and resize
    // --------------------------------------------------------------------------------

    @Override
    protected void onLoad() {
        super.onLoad();
        nativeListeners.add(DataGridDom.addListener(scroller, "scroll", false, e -> onScroll()));
        // Thumbs that follow the scroll by themselves are also placed once each scroll ends
        nativeListeners.add(DataGridDom.addListener(scroller, "scrollend", false, e -> updateScrollbars()));
        nativeListeners.add(DataGridDom.addListener(getElement(), BrowserEvents.FOCUS, true,
                this::dispatchEvent));
        nativeListeners.add(DataGridDom.addListener(getElement(), "focusin", false, e -> {
            if (Element.is(e.getEventTarget())) {
                keyboard.onFocusIn(Element.as(e.getEventTarget()));
                updateTabStops();
            }
            updateHasFocus();
        }));
        nativeListeners.add(DataGridDom.addListener(getElement(), "focusout", false,
                e -> Scheduler.get().scheduleDeferred(this::updateHasFocus)));
        nativeListeners.add(DataGridDom.addListener(getElement(), BrowserEvents.BLUR, true,
                this::dispatchEvent));
        // A cell's field changing as it is typed in, pasted into or cleared, which a cell that keeps
        // its field's text when its row is drawn again (ValueSpinnerCell) records
        nativeListeners.add(DataGridDom.addListener(getElement(), "input", false,
                this::dispatchEvent));
        scrollbars.attach();
        columnInteractions.attach();
        GlobalResizeObserver.addListener(getElement(), element -> onResize());
        // The header's height is fixed to whole pixels (snapHeaderHeight), so watch its row, whose
        // height follows its content, e.g. when the density or text size preference changes. That
        // changes the rows' height too, which virtual rendering measures.
        GlobalResizeObserver.addListener(headerRow, element -> {
            gridHeader.snapHeight(hideHeader);
            forgetRowHeight();
        });

        scroller.setScrollTop(savedScrollTop);
        scroller.setScrollLeft(savedScrollLeft);
        // The header could not be measured while detached.
        redrawHeaders();
        onResize();
    }

    @Override
    protected void onUnload() {
        savedScrollTop = scroller.getScrollTop();
        savedScrollLeft = scroller.getScrollLeft();
        GlobalResizeObserver.removeListener(getElement());
        GlobalResizeObserver.removeListener(headerRow);
        for (final JavaScriptObject handle : nativeListeners) {
            DataGridDom.removeListener(handle);
        }
        nativeListeners.clear();
        scrollbars.detach();
        columnInteractions.detach();
        super.onUnload();
    }

    /// Lay the grid out again after its size changed. Called automatically by a resize observer.
    public void onResize() {
        if (!isAttached()) {
            return;
        }
        final int width = getElement().getClientWidth();
        if (columnModel.resolve(width)) {
            scheduleRender();
        }
        if (Boolean.TRUE.equals(renderedVirtual)) {
            gridRows.render(false, new int[]{0, 0});
        }
        updateScrollbars();
    }

    // --------------------------------------------------------------------------------
    // Events
    // --------------------------------------------------------------------------------

    @Override
    public void onBrowserEvent(final Event event) {
        super.onBrowserEvent(event);
        dispatchEvent(event);
    }

    private void dispatchEvent(final NativeEvent event) {
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

        final Element headerCell = findAncestorWithAttribute(target, DATA_HCOL);
        if (headerCell != null) {
            onHeaderEvent(event, target, headerCell);
            return;
        }

        final String type = event.getType();
        final Element rowElement = findAncestorWithAttribute(target, DATA_ROW);
        int rowIndex = rowElement != null
                ? parseInt(rowElement.getAttribute(DATA_ROW))
                : -1;
        if (rowIndex == -1 && isKeyEvent(type)) {
            rowIndex = keyboardSelectedRow;
        }
        if (rowIndex < 0 || rowIndex >= pageState.getRowDataSize()) {
            if (isKeyEvent(type) && pageState.getRowDataSize() > 0) {
                // Let the selection manager handle keys when nothing is selected yet.
                firePreview(event, 0, -1, null);
            }
            return;
        }

        final Element cellElement = findAncestorWithAttribute(target, DATA_COL);
        final int columnIndex = cellElement != null
                ? parseInt(cellElement.getAttribute(DATA_COL))
                : 0;
        final Element cellContent = cellElement != null
                ? cellElement.getFirstChildElement()
                : null;
        final R value = pageState.getRow(rowIndex);
        final Context context = new Context(pageState.getPageStart() + rowIndex,
                columnIndex,
                getKey(value, pageState.getPageStart() + rowIndex));

        final boolean canceled = firePreview(event, rowIndex, columnIndex, cellContent);
        if (!canceled && cellContent != null && columnIndex < columnModel.size()) {
            final Column<R, ?> column = columnModel.getColumn(columnIndex);
            if (consumesEvent(column.getCell(), type)) {
                final String before = cellContent.getInnerHTML();
                column.onBrowserEvent(context, cellContent, value, event);
                // A cell may rewrite its own DOM when it handles an event (e.g. TickBoxCell shows
                // the new tick at once), so the stored HTML no longer matches the row. Forget it,
                // so the row is written again the next time it is rendered, as GWT would redraw it.
                // Only if it did: a cell that just took the focus (a selection box) would otherwise
                // be replaced by the redraw the focus brings, taking the focus with it.
                if (!before.equals(cellContent.getInnerHTML())) {
                    gridRows.forgetHtml(rowIndex);
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
        if (!hasContextMenu) {
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
        final Element cellElement = findAncestorWithAttribute(target, DATA_COL);
        final Element rowElement = findAncestorWithAttribute(target, DATA_ROW);
        if (cellElement != null && rowElement != null) {
            rowIndex = parseInt(rowElement.getAttribute(DATA_ROW));
            columnIndex = parseInt(cellElement.getAttribute(DATA_COL));
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
        final Context context = new Context(absIndex, Math.max(0, columnIndex), getKey(value, absIndex));
        boolean isEditing = false;
        if (cellContent != null && columnIndex >= 0 && columnIndex < columnModel.size() && value != null) {
            isEditing = isCellEditing(columnModel.getColumn(columnIndex), context, cellContent, value);
        }
        final CellPreviewEvent<R> preview = CellPreviewEvent.fire(this,
                event,
                this,
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
        final int columnIndex = parseInt(headerCell.getAttribute(DATA_HCOL));
        if (columnIndex < 0 || columnIndex >= columnModel.size()) {
            return;
        }
        final Column<R, ?> column = columnModel.getColumn(columnIndex);
        final Header<?> header = headers.get(column);
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
                onHeaderSortClick(column, DataGridDom.isCtrl(event));
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

    /// @param start     An element.
    /// @param attribute An attribute name.
    /// @return The element, or its nearest ancestor in the grid, with the attribute, or null.
    Element findAncestorWithAttribute(final Element start, final String attribute) {
        Element element = start;
        final Element root = getElement();
        while (element != null && element != root) {
            if (element.hasAttribute(attribute)) {
                return element;
            }
            element = element.getParentElement();
        }
        return null;
    }

    // Gives an element the focus without scrolling, showing it as keyboard focus (:focus-visible)
    // only if the last input on the page was a key (InputModality, as the menu asks): the browser
    // would otherwise show the focus put back after a press redraws the pressed cell (dropping the
    // focus) as keyboard focus
    void refocus(final Element element) {
        DataGridDom.focusWithoutScroll(element, InputModality.isKeyboard());
    }

    static int parseInt(final String value) {
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException e) {
            return -1;
        }
    }


    // --------------------------------------------------------------------------------


    /// Supplies column spans for a row, e.g. so a summary row's text can span several columns.
    ///
    /// @param <R> The row type.
    public interface RowSpans<R> {

        /// @param row      The row.
        /// @param absIndex The absolute row index.
        /// @return A map of column index to the number of columns that cell spans. Empty for none.
        Map<Integer, Integer> getSpans(R row, int absIndex);
    }
}
