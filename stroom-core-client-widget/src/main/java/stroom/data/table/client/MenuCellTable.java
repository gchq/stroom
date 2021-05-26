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

package stroom.data.table.client;

import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.table.client.CellTableViewImpl.DefaultResources;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Cursor;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.CellTable.Resources;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy.KeyboardSelectionPolicy;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.view.client.Range;
import com.google.gwt.view.client.RangeChangeEvent.Handler;
import com.google.gwt.view.client.SelectionModel;

import java.util.List;

public class MenuCellTable<R> extends Composite {

    /**
     * The main DataGrid.
     */
    @UiField(provided = true)
    CellTable<R> cellTable;

    public MenuCellTable() {
        this(false);
    }

    public MenuCellTable(final boolean supportsSelection) {
        this(supportsSelection, "basicCellTable");
    }

    public MenuCellTable(final boolean supportsSelection, final String className) {
        final Resources resources = GWT.create(DefaultResources.class);
        cellTable = new CellTable<>(DataGridViewImpl.DEFAULT_LIST_PAGE_SIZE, resources);
        cellTable.setWidth("100%");
        cellTable.getElement().setClassName(className);
        cellTable.setLoadingIndicator(null);

        setSupportsSelection(supportsSelection);

        // Create the UiBinder.
        initWidget(cellTable);
    }

    public void setSupportsSelection(final boolean supportsSelection) {
        if (supportsSelection) {
            cellTable.setSelectionModel(new MySingleSelectionModel<>());
            cellTable.setKeyboardSelectionPolicy(KeyboardSelectionPolicy.ENABLED);
            cellTable.getRowContainer().getStyle().setCursor(Cursor.POINTER);
        } else {
            cellTable.setSelectionModel(null);
            cellTable.setKeyboardSelectionPolicy(KeyboardSelectionPolicy.DISABLED);
            cellTable.getRowContainer().getStyle().setCursor(Cursor.DEFAULT);
        }
    }

    public void addColumn(final Column<R, ?> column) {
        cellTable.addColumn(column);
    }

    public void addColumn(final Column<R, ?> column, final int width) {
        addColumn(column);
        cellTable.setColumnWidth(column, width, Unit.PX);
    }

    public HandlerRegistration addRangeChangeHandler(final Handler handler) {
        return cellTable.addRangeChangeHandler(handler);
    }

    public HandlerRegistration addRowCountChangeHandler(
            final com.google.gwt.view.client.RowCountChangeEvent.Handler handler) {
        return cellTable.addRowCountChangeHandler(handler);
    }

    public int getRowCount() {
        return cellTable.getRowCount();
    }

    public void setRowCount(final int count) {
        cellTable.setRowCount(count);
    }

    public Range getVisibleRange() {
        return cellTable.getVisibleRange();
    }

    public void setVisibleRange(final Range range) {
        cellTable.setVisibleRange(range);
    }

    public boolean isRowCountExact() {
        return cellTable.isRowCountExact();
    }

    public void setRowCount(final int count, final boolean isExact) {
        cellTable.setRowCount(count, isExact);
    }

    public void setVisibleRange(final int start, final int length) {
        cellTable.setVisibleRange(start, length);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        cellTable.fireEvent(event);
    }

    public HandlerRegistration addCellPreviewHandler(
            final com.google.gwt.view.client.CellPreviewEvent.Handler<R> handler) {
        return cellTable.addCellPreviewHandler(handler);
    }

    @SuppressWarnings("unchecked")
    public SelectionModel<R> getSelectionModel() {
        return (SelectionModel<R>) cellTable.getSelectionModel();
    }

    public void setSelectionModel(final SelectionModel<? super R> selectionModel) {
        cellTable.setSelectionModel(selectionModel);
    }

    public R getVisibleItem(final int indexOnPage) {
        return cellTable.getVisibleItem(indexOnPage);
    }

    public int getVisibleItemCount() {
        return cellTable.getVisibleItemCount();
    }

    public Iterable<R> getVisibleItems() {
        return cellTable.getVisibleItems();
    }

    public void setRowData(final int start, final List<? extends R> values) {
        cellTable.setRowData(start, values);
    }

    public void setVisibleRangeAndClearData(final Range range, final boolean forceRangeChangeEvent) {
        cellTable.setVisibleRangeAndClearData(range, forceRangeChangeEvent);
    }

    public void setSkipRowHoverCheck(final boolean skipRowHoverCheck) {
        cellTable.setSkipRowHoverCheck(skipRowHoverCheck);
    }
}
