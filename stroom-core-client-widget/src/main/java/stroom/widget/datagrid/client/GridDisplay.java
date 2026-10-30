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

import stroom.hyperlink.client.HyperlinkEvent;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.cellview.client.LoadingStateChangeEvent;
import com.google.gwt.user.cellview.client.RowStyles;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.gwt.view.client.HasData;
import com.google.gwt.view.client.SelectionModel;

import java.util.List;

/// The data grid as the code shared by several screens uses it (e.g. `DataGridUtil`'s column
/// helpers, `DataGridComparatorFactory` and `TreeRowHandler`), and as tests mock it.
/// [DataGrid] is its one implementation (`stroom-core-client-widget/DATA_GRID_DESIGN.md`, §5.12
/// and Phase 3).
///
/// @param <R> The row type.
public interface GridDisplay<R> extends HasData<R>, IsWidget {

    // --------------------------------------------------------------------------------
    // Columns

    /// Add a fixed-width column that can't be resized, with no set width.
    ///
    /// @param column The column.
    /// @param name   The header text. Treated as HTML.
    void addColumn(Column<R, ?> column, String name);

    /// Add a fixed-width column that can't be resized.
    ///
    /// @param column The column.
    /// @param name   The header text. Treated as HTML.
    /// @param width  The width in pixels.
    void addColumn(Column<R, ?> column, String name, int width);

    /// Add a fixed-width column that can't be resized.
    ///
    /// @param column The column.
    /// @param header The header.
    /// @param width  The width in pixels.
    void addColumn(Column<R, ?> column, Header<?> header, int width);

    /// Add a column as a [ColSpec] describes it.
    ///
    /// @param colSpec The column's description.
    void addColumn(ColSpec<R> colSpec);

    /// Add a column the user can resize.
    ///
    /// @param column The column.
    /// @param name   The header text. Treated as HTML.
    /// @param width  The width in pixels.
    void addResizableColumn(Column<R, ?> column, String name, int width);

    /// Add a column the user can resize.
    ///
    /// @param column The column.
    /// @param header The header.
    /// @param width  The width in pixels.
    void addResizableColumn(Column<R, ?> column, Header<?> header, int width);

    /// Add a resizable column that grows to fill the grid's spare width, sharing it with the other
    /// such columns by weight, until the user resizes it.
    ///
    /// @param column              The column.
    /// @param name                The header text. Treated as HTML.
    /// @param fillWeight          The column's share of the spare width relative to other fill
    ///                            columns.
    /// @param initialMinimumWidth The width in pixels it never fills below.
    void addAutoResizableColumn(Column<R, ?> column, String name, int fillWeight, int initialMinimumWidth);

    /// Add a resizable column that grows to fill the grid's spare width, with the default weight,
    /// until the user resizes it.
    ///
    /// @param column              The column.
    /// @param name                The header text. Treated as HTML.
    /// @param initialMinimumWidth The width in pixels it never fills below.
    void addAutoResizableColumn(Column<R, ?> column, String name, int initialMinimumWidth);

    /// Add a resizable column that grows to fill the grid's spare width, with the default weight,
    /// until the user resizes it.
    ///
    /// @param column              The column.
    /// @param header              The header.
    /// @param initialMinimumWidth The width in pixels it never fills below.
    void addAutoResizableColumn(Column<R, ?> column, Header<?> header, int initialMinimumWidth);

    /// Remove a column.
    ///
    /// @param column The column.
    void removeColumn(Column<R, ?> column);

    /// Move a column, as the user can by dragging its header.
    ///
    /// @param fromIndex The column's index.
    /// @param toIndex   The index to move it to.
    void moveColumn(int fromIndex, int toIndex);

    /// Set a column's width, as the user can by dragging its header's edge.
    ///
    /// @param index The column's index.
    /// @param width The width in pixels.
    void resizeColumn(int index, int width);

    /// Set a column's width.
    ///
    /// @param column The column.
    /// @param width  The width.
    /// @param unit   The width's unit.
    void setColumnWidth(Column<R, ?> column, int width, Unit unit);

    /// Set a column's width as its content needs (e.g. a tree's expander column), the fill columns
    /// giving back what it gains where the columns would otherwise run past the grid's edge.
    ///
    /// @param column The column.
    /// @param width  The width in pixels.
    void setColumnWidthWithinFill(Column<R, ?> column, int width);

    /// @return The number of columns.
    int getColumnCount();

    /// @param index The column's index.
    /// @return The column.
    Column<R, ?> getColumn(int index);

    /// @param column The column.
    /// @return The column's index, or -1 if it isn't in the grid.
    int getColumnIndex(Column<R, ?> column);

    // --------------------------------------------------------------------------------
    // Layout

    /// Fit the columns to the grid's width: the fill columns take the spare width.
    void resizeTableToFitColumns();

    /// Fit the columns to the grid's width.
    ///
    /// @param redistribute Whether to share the width out again among the fill columns, even those
    ///                     already sized.
    void resizeTableToFitColumns(boolean redistribute);

    /// @param multiLine Whether the cells' text wraps, making rows as tall as it needs.
    void setMultiLine(boolean multiLine);

    // --------------------------------------------------------------------------------
    // Header

    /// Draw the headers again, e.g. after their content changed.
    void redrawHeaders();

    /// @param headingListener The listener told of presses on the headers, e.g. to show a menu.
    void setHeadingListener(HeadingListener headingListener);

    /// @param allowMove Whether the user can move columns.
    void setAllowMove(boolean allowMove);

    /// @param allowResize Whether the user can resize columns.
    void setAllowResize(boolean allowResize);

    /// @param allowHeaderSelection Whether the header can be selected.
    void setAllowHeaderSelection(boolean allowHeaderSelection);

    /// @param handler The handler told when the user sorts a column.
    /// @return The registration that removes the handler.
    HandlerRegistration addColumnSortHandler(ColumnSortEvent.Handler handler);

    /// @return The columns sorted, most significant first.
    ColumnSortList getColumnSortList();

    /// Sort a column, as a press on its header does.
    ///
    /// @param column The column.
    void sort(Column<R, ?> column);

    /// Remove every column's sort.
    void clearColumnSortList();

    /// @param handler The handler told of presses on hyperlinks in the cells.
    /// @return The registration that removes the handler.
    HandlerRegistration addHyperlinkHandler(HyperlinkEvent.Handler handler);

    /// @param column The column's index.
    /// @return The column's header cell, or `null` if there is none, e.g. before the header is
    /// drawn.
    Element getHeaderCellElement(int column);

    // --------------------------------------------------------------------------------
    // Selection and focus

    /// Use a [MultiSelectionModelImpl] with the grid's own handler of clicks and keys.
    ///
    /// @param allowMultiSelect Whether Ctrl and Shift can select several rows.
    /// @return The selection model.
    MultiSelectionModelImpl<R> addDefaultSelectionModel(boolean allowMultiSelect);

    /// Use a selection model and the handler that turns clicks and keys into selections.
    ///
    /// @param selectionModel The selection model.
    /// @param handler        The handler.
    void setSelectionModel(SelectionModel<? super R> selectionModel, CellPreviewEvent.Handler<R> handler);

    /// @return The index on the page of the keyboard's row, or -1.
    int getKeyboardSelectedRow();

    /// Move the keyboard's row.
    ///
    /// @param row        The index on the page of the row.
    /// @param stealFocus Whether to give the row the focus.
    void setKeyboardSelectedRow(int row, boolean stealFocus);

    // --------------------------------------------------------------------------------
    // Rows

    /// @param rowStyles The styles to give each row.
    void setRowStyles(RowStyles<R> rowStyles);

    /// Draw every row again.
    void redraw();

    /// Draw a row again.
    ///
    /// @param absRowIndex The row's index, counted from the first row of the first page.
    void redrawRow(int absRowIndex);

    /// Draw now any change waiting to be drawn.
    void flush();

    /// @param indexOnPage The row's index on the page.
    /// @return The row's element.
    Element getRowElement(int indexOnPage);

    /// @return The rows on the page.
    List<R> getVisibleItems();

    /// @param handler The handler told when the grid starts or finishes loading.
    /// @return The registration that removes the handler.
    HandlerRegistration addLoadingStateChangeHandler(LoadingStateChangeEvent.Handler handler);

    // --------------------------------------------------------------------------------
    // Other

    /// @param hasContextMenu Whether the grid offers its own context menu (copy, export and so on).
    void hasContextMenu(boolean hasContextMenu);

    /// @param tableName The name given to the data the grid exports.
    void setTableName(String tableName);

    /// @return The name given to the data the grid exports.
    String getTableName();

    /// @param name The name the grid has for assistive technology.
    void setAccessibleName(String name);

    /// @param text The text shown when there are no rows.
    void setEmptyText(String text);
}
