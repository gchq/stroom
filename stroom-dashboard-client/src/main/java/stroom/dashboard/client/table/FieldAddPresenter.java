/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.client.table;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.cellview.client.CellTable.Resources;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.SelectionChangeEvent.Handler;
import com.google.gwt.view.client.SelectionChangeEvent.HasSelectionChangedHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.data.table.client.CellTableView;
import stroom.data.table.client.CellTableViewImpl;
import stroom.data.table.client.CellTableViewImpl.HoverResources;
import stroom.data.table.client.ScrollableCellTableViewImpl;
import stroom.query.shared.Field;
import stroom.widget.util.client.MySingleSelectionModel;

import java.util.List;

public class FieldAddPresenter extends MyPresenterWidget<CellTableView<Field>> implements HasSelectionChangedHandlers {
    private final MySingleSelectionModel<Field> selectionModel = new MySingleSelectionModel<>();

    @Inject
    public FieldAddPresenter(final EventBus eventBus) {
        super(eventBus, new ScrollableCellTableViewImpl<Field>(true, GWT.create(HoverResources.class)));
        final Column<Field, String> textColumn = new Column<Field, String>(new TextCell()) {
            @Override
            public String getValue(final Field field) {
                return field.getName();
            }
        };
        getView().addColumn(textColumn, 200);
        getView().setSelectionModel(selectionModel);
    }

    public void setFields(final List<Field> fields) {
        getView().setRowData(0, fields);
        getView().setRowCount(fields.size());
    }

    public Field getSelectedObject() {
        return selectionModel.getSelectedObject();
    }

    public void clearSelection() {
        selectionModel.clear();
    }

    @Override
    public HandlerRegistration addSelectionChangeHandler(final Handler handler) {
        return selectionModel.addSelectionChangeHandler(handler);
    }
}
