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

package stroom.data.grid.client;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.view.client.HasData;
import com.google.gwt.view.client.HasRows;
import com.gwtplatform.mvp.client.View;
import stroom.data.grid.client.DataGridViewImpl.HeadingListener;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.ImageButtonView;
import stroom.svg.client.SvgPreset;
import stroom.widget.util.client.MultiSelectionModel;

import java.util.List;

public interface DataGridView<R> extends View, HasRows {
    void addColumn(Column<R, ?> column, String name, int width);

    void addColumn(Column<R, ?> column, String name);

    void addColumn(Column<R, ?> column, Header<?> header, int width);

    void addResizableColumn(Column<R, ?> column, String name, int width);

    void addResizableColumn(Column<R, ?> column, Header<?> header, int width);

    void addEndColumn(EndColumn<R> column);

    void removeColumn(Column<R, ?> column);

    void setColumnWidth(Column<R, ?> column, int width, Unit unit);

    ImageButtonView addButton(String title, ImageResource enabledImage, ImageResource disabledImage, boolean enabled);

    ButtonView addButton(SvgPreset preset);

    HandlerRegistration addColumnSortHandler(ColumnSortEvent.Handler handler);

    void resizeTableToFitColumns();

    void redrawHeaders();

    void setHeadingListener(HeadingListener headingListener);

    void setRefreshing(boolean refreshing);

    DataGridViewImpl.DefaultResources getResources();

    TableRowElement getRowElement(int row);

    void setRowData(int start, List<? extends R> values);

    HasData<R> getDataDisplay();

    MultiSelectionModel<R> getSelectionModel();
}
