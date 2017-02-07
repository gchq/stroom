/*
 *
 *  * Copyright 2017 Crown Copyright
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package stroom.data.table.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Cursor;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.CssResource.ImportedWithPrefix;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.CellTable.Resources;
import com.google.gwt.user.cellview.client.CellTable.Style;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy.KeyboardSelectionPolicy;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.Range;
import com.google.gwt.view.client.RangeChangeEvent.Handler;
import com.google.gwt.view.client.SelectionModel;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.widget.util.client.MySingleSelectionModel;

import java.util.List;

public class CellTableViewImpl<R> extends ViewImpl implements CellTableView<R> {
    @ImportedWithPrefix("gwt-CellTable")
    public interface BasicStyle extends Style {
        String DEFAULT_CSS = "stroom/data/table/client/BasicCellTable.css";
    }

    public interface BasicResources extends Resources {
        @Override
        @Source(BasicStyle.DEFAULT_CSS)
        BasicStyle cellTableStyle();
    }

    @ImportedWithPrefix("gwt-CellTable")
    public interface DefaultStyle extends Style {
        String DEFAULT_CSS = "stroom/data/table/client/DefaultCellTable.css";
    }

    public interface DefaultResources extends Resources {
        @Override
        @Source(DefaultStyle.DEFAULT_CSS)
        DefaultStyle cellTableStyle();
    }

    @ImportedWithPrefix("gwt-CellTable")
    public interface DisabledStyle extends Style {
        String DEFAULT_CSS = "stroom/data/table/client/DisabledCellTable.css";
    }

    public interface DisabledResources extends Resources {
        @Override
        @Source(DisabledStyle.DEFAULT_CSS)
        DisabledStyle cellTableStyle();
    }

    @ImportedWithPrefix("gwt-CellTable")
    public interface HoverStyle extends Style {
        String DEFAULT_CSS = "stroom/data/table/client/HoverCellTable.css";
    }

    public interface HoverResources extends Resources {
        @Override
        @Source(HoverStyle.DEFAULT_CSS)
        HoverStyle cellTableStyle();
    }

    public interface MenuResources extends Resources {
        @Override
        @Source("MenuCellTable.css")
        Style cellTableStyle();
    }

    /**
     * The main DataGrid.
     */
    @UiField(provided = true)
    CellTable<R> cellTable;

    private Widget widget;

    public CellTableViewImpl() {
        this(false);
    }

    public CellTableViewImpl(final boolean supportsSelection) {
        this(supportsSelection, (Resources) GWT.create(BasicResources.class));
    }

    public CellTableViewImpl(final boolean supportsSelection, final Resources resources) {
        cellTable = new CellTable<R>(DataGridViewImpl.DEFAULT_LIST_PAGE_SIZE, resources);
        cellTable.setWidth("100%");

        cellTable.setLoadingIndicator(null);

        setSupportsSelection(supportsSelection);
        setWidget(cellTable);
    }

    CellTable<R> getCellTable() {
        return cellTable;
    }

    void setWidget(final Widget widget) {
        this.widget = widget;
    }

    @Override
    public void setSupportsSelection(final boolean supportsSelection) {
        if (supportsSelection) {
            cellTable.setSelectionModel(new MySingleSelectionModel<R>());
            cellTable.setKeyboardSelectionPolicy(KeyboardSelectionPolicy.ENABLED);
            cellTable.getRowContainer().getStyle().setCursor(Cursor.POINTER);
        } else {
            cellTable.setSelectionModel(null);
            cellTable.setKeyboardSelectionPolicy(KeyboardSelectionPolicy.DISABLED);
            cellTable.getRowContainer().getStyle().setCursor(Cursor.DEFAULT);
        }
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void addColumn(final Column<R, ?> column) {
        cellTable.addColumn(column);
    }

    @Override
    public void addColumn(final Column<R, ?> column, final int width) {
        addColumn(column);
        cellTable.setColumnWidth(column, width, Unit.PX);
    }

    @Override
    public HandlerRegistration addRangeChangeHandler(final Handler handler) {
        return cellTable.addRangeChangeHandler(handler);
    }

    @Override
    public HandlerRegistration addRowCountChangeHandler(
            final com.google.gwt.view.client.RowCountChangeEvent.Handler handler) {
        return cellTable.addRowCountChangeHandler(handler);
    }

    @Override
    public int getRowCount() {
        return cellTable.getRowCount();
    }

    @Override
    public Range getVisibleRange() {
        return cellTable.getVisibleRange();
    }

    @Override
    public boolean isRowCountExact() {
        return cellTable.isRowCountExact();
    }

    @Override
    public void setRowCount(final int count) {
        cellTable.setRowCount(count);
    }

    @Override
    public void setRowCount(final int count, final boolean isExact) {
        cellTable.setRowCount(count, isExact);
    }

    @Override
    public void setVisibleRange(final int start, final int length) {
        cellTable.setVisibleRange(start, length);
    }

    @Override
    public void setVisibleRange(final Range range) {
        cellTable.setVisibleRange(range);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        cellTable.fireEvent(event);
    }

    @Override
    public HandlerRegistration addCellPreviewHandler(
            final com.google.gwt.view.client.CellPreviewEvent.Handler<R> handler) {
        return cellTable.addCellPreviewHandler(handler);
    }

    @SuppressWarnings("unchecked")
    @Override
    public SelectionModel<R> getSelectionModel() {
        return (SelectionModel<R>) cellTable.getSelectionModel();
    }

    @Override
    public R getVisibleItem(final int indexOnPage) {
        return cellTable.getVisibleItem(indexOnPage);
    }

    @Override
    public int getVisibleItemCount() {
        return cellTable.getVisibleItemCount();
    }

    @Override
    public Iterable<R> getVisibleItems() {
        return cellTable.getVisibleItems();
    }

    @Override
    public void setRowData(final int start, final List<? extends R> values) {
        cellTable.setRowData(start, values);
    }

    @Override
    public void setSelectionModel(final SelectionModel<? super R> selectionModel) {
        cellTable.setSelectionModel(selectionModel);
    }

    @Override
    public void setVisibleRangeAndClearData(final Range range, final boolean forceRangeChangeEvent) {
        cellTable.setVisibleRangeAndClearData(range, forceRangeChangeEvent);
    }

    @Override
    public void setSkipRowHoverCheck(final boolean skipRowHoverCheck) {
        cellTable.setSkipRowHoverCheck(skipRowHoverCheck);
    }
}
