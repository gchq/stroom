/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.script.client.presenter;

import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.docref.DocRef;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.List;

public class ScriptListPresenter extends MyPresenterWidget<PagerView> {

    private final MyDataGrid<DocRef> dataGrid;
    private final MultiSelectionModelImpl<DocRef> selectionModel;

    @Inject
    public ScriptListPresenter(final EventBus eventBus,
                               final PagerView view) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        initTableColumns();
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Name.
        final Column<DocRef, String> nameColumn = new Column<DocRef, String>(new TextCell()) {
            @Override
            public String getValue(final DocRef docRef) {
                return docRef.getName();
            }
        };
        dataGrid.addResizableColumn(nameColumn, "Name", 250);
        dataGrid.addEndColumn(new EndColumn<>());
    }

    public void setData(final List<DocRef> scripts) {
        dataGrid.setRowData(0, scripts);
        dataGrid.setRowCount(scripts.size());
    }

    public MultiSelectionModel<DocRef> getSelectionModel() {
        return selectionModel;
    }
}
