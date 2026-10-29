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
import stroom.hyperlink.client.Hyperlink;
import stroom.hyperlink.client.HyperlinkEvent;
import stroom.hyperlink.client.OpenHyperlinkRequest;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.util.shared.Expander;
import stroom.util.shared.NullSafe;
import stroom.widget.datagrid.client.model.ColumnModel;
import stroom.widget.datagrid.client.model.ColumnSizing;
import stroom.widget.datagrid.client.model.KeyboardRow;
import stroom.widget.datagrid.client.model.PageState;
import stroom.widget.datagrid.client.model.SortCycle;
import stroom.widget.datagrid.client.model.SortEntry;
import stroom.widget.datagrid.client.model.SortRebuild;
import stroom.widget.tab.client.GlobalResizeObserver;
import stroom.widget.util.client.CssProperties;
import stroom.widget.util.client.InputModality;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
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
public class DataGrid<R> extends Widget
        implements DataGridView<R>, HasKeyboardSelectionPolicy, Focusable {

    /// The default page size.
    public static final int DEFAULT_LIST_PAGE_SIZE = 100;

    /// The page size for grids that show a very large list on one page.
    public static final int MASSIVE_LIST_PAGE_SIZE = 100000;

    private static final Binder BINDER = GWT.create(Binder.class);

    // The column model holds sizing; headers and wrapping are kept alongside it by column.
    private final ColumnModel<Column<R, ?>> columnModel = new ColumnModel<>();
    private final Map<Column<R, ?>, Header<?>> headers = new HashMap<>();
    private final Set<Column<R, ?>> wrappingColumns = new HashSet<>();
    private final ColumnSortList sortList;
    // The screen's default sort (R-H4b, D10): shown from the start, and restored when a click
    // leaves the sort list empty
    private final List<SortEntry<Column<?, ?>>> defaultSort = new ArrayList<>();
    // The sort's and default sort's entries of removed columns, taken back by columns added again
    // with the same sort field (a list that makes its columns afresh, e.g. when read only changes)
    private final SortRebuild<Column<?, ?>> sortRebuild = new SortRebuild<>(Column::getDataStoreName);
    private final SortRebuild<Column<?, ?>> defaultSortRebuild = new SortRebuild<>(Column::getDataStoreName);
    private final GridHtml<R> html;

    private final PageState<R> pageState;

    // DOM, from the template
    @UiField
    Element scroller;
    @UiField
    Element head;
    @UiField
    Element headerRow;
    @UiField
    Element content;
    @UiField
    Element body;
    @UiField
    Element empty;
    @UiField
    Element verticalTrack;
    @UiField
    Element verticalThumb;
    @UiField
    Element horizontalTrack;
    @UiField
    Element horizontalThumb;
    @UiField
    Element resizeHandle;
    @UiField
    Element resizeLine;
    @UiField
    Element moveGhost;
    @UiField
    Element moveLine;
    private final OverlayScrollbars scrollbars;
    private final ColumnInteractions columnInteractions;
    private final DataGridContextMenu<R> contextMenu;

    // The rows drawn, and the tree they make if the grid has an expander column
    private final GridRows<R> gridRows;
    private final GridEvents<R> events;
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
    private boolean hideHeader;
    private boolean allowMove = true;
    private boolean allowResize = true;
    private boolean allowHeaderSelection = true;
    private boolean hasContextMenu = true;
    // How many handle the grid's links (addHyperlinkHandler)
    private int hyperlinkHandlerCount;
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

    /// Create a grid with the default page size, e.g. one a template declares. What it asks of the
    /// app (its context menu, a link from it, asking Stroom AI about it) goes up the page as
    /// requests (`MenuRequest`, `OpenHyperlinkRequest`, `AskStroomAiTableRequest`) to the page's
    /// relays.
    public DataGrid() {
        this(DEFAULT_LIST_PAGE_SIZE);
    }

    /// Create a grid.
    ///
    /// @param pageSize The page size.
    public DataGrid(final int pageSize) {
        this.pageState = new PageState<>(pageSize);
        this.sortList = new ColumnSortList(() -> {
            headersDirty = true;
            scheduleRender();
        });
        this.html = new GridHtml<>(columnModel, headers, sortList, this::getKey);
        // Before the first input the grid puts the focus back after (see refocus)
        InputModality.install();

        // The parts (see the template), which key events bubble from to the root, where they are
        // handled
        final Element root = BINDER.createAndBindUi(this);
        scrollbars = new OverlayScrollbars(root,
                scroller,
                verticalTrack,
                verticalThumb,
                horizontalTrack,
                horizontalThumb,
                this::getHeaderHeight);
        columnInteractions = new ColumnInteractions(this,
                root,
                scroller,
                head,
                resizeHandle,
                resizeLine,
                moveGhost,
                moveLine);
        contextMenu = new DataGridContextMenu<>(this);

        keyboard = new DataGridKeyboard<>(this);
        tree = new TreeRows<>(this);
        gridHeader = new GridHeader<>(this, columnModel, html, keyboard, head, headerRow);
        gridRows = new GridRows<>(this, pageState, columnModel, html, tree, keyboard, scroller, content, body);
        events = new GridEvents<>(this, pageState, columnModel, gridRows, keyboard, contextMenu, scrollbars,
                columnInteractions);

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
        // A column made afresh takes back the sort of the one it replaces
        if (sortRebuild.isPending()) {
            setSortList(sortRebuild.add(currentSort(), column));
        }
        if (defaultSortRebuild.isPending()) {
            setDefaultSortEntries(defaultSortRebuild.add(new ArrayList<>(defaultSort), column));
        }
        scheduleRender();
    }

    /// Remove a column.
    ///
    /// @param column The column.
    public void removeColumn(final Column<R, ?> column) {
        if (columnModel.remove(column)) {
            headers.remove(column);
            wrappingColumns.remove(column);
            // The sort never names a column the grid doesn't have; its entry is kept for a column
            // added again with the same sort field
            setSortList(sortRebuild.remove(currentSort(), column));
            setDefaultSortEntries(defaultSortRebuild.remove(new ArrayList<>(defaultSort), column));
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
    /// @param width  The width in pixels.
    public void setColumnWidth(final Column<R, ?> column, final int width) {
        final int index = columnModel.indexOf(column);
        if (index != -1) {
            columnModel.setWidth(index, width);
            scheduleRender();
        }
    }

    /// Set a column's width programmatically.
    ///
    /// @param column The column.
    /// @param width  The width. Only pixel widths are supported; other units are treated as pixels.
    /// @param unit   The unit.
    public void setColumnWidth(final Column<R, ?> column, final int width, final Unit unit) {
        setColumnWidth(column, width);
    }

    /// Set a column's width as its content needs (e.g. a tree's expander column), the fill columns
    /// giving back what it gains where the columns would otherwise run past the grid's edge.
    ///
    /// @param column The column.
    /// @param width  The width in pixels.
    public void setColumnWidthWithinFill(final Column<R, ?> column, final int width) {
        final int index = columnModel.indexOf(column);
        if (index != -1) {
            final int outerWidth = getElement().getClientWidth();
            if (isAttached() && columnModel.isResolved() && outerWidth > 0) {
                columnModel.setWidthWithinFill(index, width, outerWidth);
            } else {
                columnModel.setWidth(index, width);
            }
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
        // Not while columns are being made afresh: the sort they had comes back with them
        if (sortList.size() == 0 && !sortRebuild.isPending()) {
            setSortList(this.defaultSort);
        }
    }

    // Replaces the default sort's entries
    private void setDefaultSortEntries(final List<SortEntry<Column<?, ?>>> entries) {
        defaultSort.clear();
        defaultSort.addAll(entries);
    }

    // The sort list's entries, primary column first
    private List<SortEntry<Column<?, ?>>> currentSort() {
        final List<SortEntry<Column<?, ?>>> current = new ArrayList<>();
        for (int i = 0; i < sortList.size(); i++) {
            final ColumnSortInfo info = sortList.get(i);
            current.add(new SortEntry<>(info.getColumn(), info.isAscending()));
        }
        return current;
    }

    // Once columns made afresh are all added: what no column took back is forgotten, and if that
    // leaves nothing sorted, the default sort applies, so the header shows the order of the data
    private void finishSortRebuild() {
        if (sortRebuild.isPending() || defaultSortRebuild.isPending()) {
            sortRebuild.finish();
            defaultSortRebuild.finish();
            if (sortList.size() == 0 && !defaultSort.isEmpty()) {
                setSortList(defaultSort);
            }
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

    /// Sorts by a header's column, as a click on the header does.
    ///
    /// @param column The column.
    /// @param ctrl   Whether Ctrl was held, which adds the column to the sort.
    void onHeaderSortClick(final Column<R, ?> column, final boolean ctrl) {
        final List<SortEntry<Column<?, ?>>> next = SortCycle.orDefault(
                SortCycle.next(currentSort(), column, column.isDefaultSortAscending(), ctrl),
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
    /// [DataGridSelectionEventManager#setChooser]), not a check box in it.
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
        // Drawn again, so a header shown again is snapped to its height (see GridHeader.snapHeight)
        headersDirty = true;
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

    /// As [#hasContextMenu(boolean)], as a setter a template can call (`hasContextMenu="false"`).
    ///
    /// @param hasContextMenu Whether right-clicking shows the grid's context menu.
    public void setHasContextMenu(final boolean hasContextMenu) {
        hasContextMenu(hasContextMenu);
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
    /// the selection manager doesn't (it is set from a [DataGridSelectionEventManager]'s).
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

    // --------------------------------------------------------------------------------
    // Selection and keyboard
    // --------------------------------------------------------------------------------

    /// Create a [MultiSelectionModelImpl] and the standard selection event manager, and use them.
    ///
    /// @param allowMultiSelect Whether Ctrl and Shift can select several rows.
    /// @return The selection model.
    public MultiSelectionModelImpl<R> addDefaultSelectionModel(final boolean allowMultiSelect) {
        final MultiSelectionModelImpl<R> model = new MultiSelectionModelImpl<>();
        setSelectionModel(model, new DataGridSelectionEventManager<>(this, model, allowMultiSelect));
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
    /// @param handler        The handler, e.g. a [DataGridSelectionEventManager].
    public void setSelectionModel(final SelectionModel<? super R> selectionModel,
                                  final CellPreviewEvent.Handler<R> handler) {
        NullSafe.consume(selectionPreviewRegistration, HandlerRegistration::removeHandler);
        selectionPreviewRegistration = NullSafe.get(handler, this::addCellPreviewHandler);
        keyboard.setSelector(handler instanceof DataGridKeyboard.Selector
                ? asSelector(handler)
                : null);
        multiSelectable = handler instanceof final DataGridSelectionEventManager<?> manager
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

    /// Focus or blur the grid, as GWT's `AbstractHasData.setFocused` does.
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
        final HandlerRegistration registration = addHandler(handler, HyperlinkEvent.getType());
        hyperlinkHandlerCount++;
        final boolean[] removed = new boolean[1];
        return () -> {
            // Counted once, however often it is removed
            if (!removed[0]) {
                removed[0] = true;
                registration.removeHandler();
                hyperlinkHandlerCount--;
            }
        };
    }

    /// Opens a link from a cell, e.g. from the context menu: as a click on it does, with the
    /// grid's [HyperlinkEvent] if something handles it (a dashboard's table, which adds the
    /// dashboard it is in), else with an [OpenHyperlinkRequest] for the app to open it.
    ///
    /// @param hyperlink The link.
    void followHyperlink(final Hyperlink hyperlink) {
        if (hyperlinkHandlerCount > 0) {
            HyperlinkEvent.fire(this, hyperlink, new DefaultTaskMonitorFactory(this));
        } else {
            OpenHyperlinkRequest.fire(getElement(), hyperlink);
        }
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
        // First, while a render is still scheduled, so that a change to the sort it makes is drawn
        // by this render rather than scheduling another
        finishSortRebuild();
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
            gridHeader.render();
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
        // Which shows the empty message (data-grid.css)
        setStyleName("data-grid--empty", isEmpty && emptyMessage != null);
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
            CssProperties.set(getElement(), "--data-grid__columns", columnModel.toGridTemplate());
            CssProperties.set(getElement(), "--data-grid__total-width", columnModel.getTotalWidth() + "px");
        } else {
            // A list is as wide as what it is in, its last column taking the rest of the width
            CssProperties.set(getElement(), "--data-grid__columns", columnModel.toListTemplate());
            CssProperties.set(getElement(),
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
                events::dispatch));
        nativeListeners.add(DataGridDom.addListener(getElement(), "focusin", false, e -> {
            if (Element.is(e.getEventTarget())) {
                keyboard.onFocusIn(Element.as(e.getEventTarget()));
                updateTabStops();
            }
            updateHasFocus();
        }));
        nativeListeners.add(DataGridDom.addListener(getElement(), "focusout", false, e -> {
            // A cell left out of date while it had the focus shows its latest value once it hasn't
            gridRows.onFocusOut(e);
            Scheduler.get().scheduleDeferred(this::updateHasFocus);
        }));
        nativeListeners.add(DataGridDom.addListener(getElement(), BrowserEvents.BLUR, true,
                events::dispatch));
        // A cell's field changing as it is typed in, pasted into or cleared, which a cell that keeps
        // its field's text when its row is drawn again (ValueSpinnerCell) records
        nativeListeners.add(DataGridDom.addListener(getElement(), "input", false,
                events::dispatch));
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
        events.dispatch(event);
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


    // --------------------------------------------------------------------------------


    // The owner is raw: a binder can't name a type variable
    @SuppressWarnings("rawtypes")
    interface Binder extends UiBinder<DivElement, DataGrid> {

    }
}
