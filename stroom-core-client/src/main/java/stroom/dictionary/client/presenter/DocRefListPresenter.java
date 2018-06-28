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

package stroom.dictionary.client.presenter;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.docref.DocRef;
import stroom.util.client.BorderUtil;
import stroom.widget.util.client.MultiSelectionModel;

import java.util.List;

public class DocRefListPresenter extends MyPresenterWidget<DataGridView<DocRef>> {
//    private final SelectionModel<Volume> selectionModel;

    @Inject
    public DocRefListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher) {
        super(eventBus, new DataGridViewImpl<>(true, true));

//        selectionModel = new MySingleSelectionModel<>();
//        getView().setSelectionModel(selectionModel);

        // Add a border to the list.
        BorderUtil.addBorder(getWidget().getElement());

        initTableColumns();
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Name.
        final Column<DocRef, String> nameColumn = new Column<DocRef, String>(new TextCell()) {
            @Override
            public String getValue(final DocRef row) {
                if (row == null) {
                    return null;
                }
                return row.getName();
            }
        };
        getView().addResizableColumn(nameColumn, "Name", 400);

        getView().addEndColumn(new EndColumn<>());
    }

    public void setData(final List<DocRef> docRefs) {
        getView().setRowData(0, docRefs);
        getView().setRowCount(docRefs.size());
    }

//    public HandlerRegistration addSelectionHandler(DataGridSelectEvent.Handler handler) {
//        return getView().addSelectionHandler(handler);
//    }

    public MultiSelectionModel<DocRef> getSelectionModel() {
        return getView().getSelectionModel();
    }
//
//    public void setSelectionModel(final SelectionModel<Volume> selectionModel) {
//        getView().setSelectionModel(selectionModel);
//    }
}
