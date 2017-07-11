/*
 * Copyright 2017 Crown Copyright
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

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.query.api.v1.DocRef;
import stroom.util.client.BorderUtil;
import stroom.widget.util.client.MultiSelectionModel;

import java.util.List;

public class ScriptListPresenter extends MyPresenterWidget<DataGridView<DocRef>> {
    @Inject
    public ScriptListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher) {
        super(eventBus, new DataGridViewImpl<DocRef>(true, true));

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
            public String getValue(final DocRef docRef) {
                return docRef.getName();
            }
        };
        getView().addResizableColumn(nameColumn, "Name", 250);
        getView().addEndColumn(new EndColumn<DocRef>());
    }

    public void setData(final List<DocRef> scripts) {
        getView().setRowData(0, scripts);
        getView().setRowCount(scripts.size());
    }

    public MultiSelectionModel<DocRef> getSelectionModel() {
        return getView().getSelectionModel();
    }
}
