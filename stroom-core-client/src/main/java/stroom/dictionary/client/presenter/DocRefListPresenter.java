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

package stroom.dictionary.client.presenter;

import stroom.cell.info.client.CommandLink;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.List;
import java.util.function.Function;

public class DocRefListPresenter extends MyPresenterWidget<PagerView> {

    private final MyDataGrid<DocRef> dataGrid;
    private final MultiSelectionModelImpl<DocRef> selectionModel;

    @Inject
    public DocRefListPresenter(final EventBus eventBus,
                               final PagerView view) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);
    }

    /**
     * Add the columns to the table.
     */
    public void initTableColumns(final String columnName, final boolean hasOpenLink) {
        if (hasOpenLink) {
            final Column<DocRef, CommandLink> nodeNameColumn = DataGridUtil.commandLinkColumnBuilder(
                            buildOpenDocCommandLink())
                    .build();
            DataGridUtil.addCommandLinkFieldUpdater(nodeNameColumn);
            dataGrid.addAutoResizableColumn(
                    nodeNameColumn,
                    DataGridUtil.headingBuilder(columnName)
                            .build(),
                    500);
        } else {
            dataGrid.addAutoResizableColumn(DataGridUtil.textColumnBuilder(DocRef::getName).build(),
                    DataGridUtil.headingBuilder(columnName).build(),
                    500);
        }
        dataGrid.addEndColumn(new EndColumn<>());
    }

    private Function<DocRef, CommandLink> buildOpenDocCommandLink() {
        return (final DocRef docRef) -> {
            if (docRef != null) {
                final String name = docRef.getName();
                return new CommandLink(
                        name,
                        "Open " + docRef.getType() + " '" + name + "'.",
                        () -> OpenDocumentEvent.fire(DocRefListPresenter.this, docRef, true));
            } else {
                return null;
            }
        };
    }

    public void setData(final List<DocRef> docRefs) {
        dataGrid.setRowData(0, docRefs);
        dataGrid.setRowCount(docRefs.size());
        if (NullSafe.hasItems(docRefs)) {
            selectionModel.setSelected(docRefs.get(0));
        } else {
            selectionModel.clear();
        }
    }

//    public HandlerRegistration addSelectionHandler(DataGridSelectEvent.Handler handler) {
//        return dataGrid.addSelectionHandler(handler);
//    }

    public MultiSelectionModel<DocRef> getSelectionModel() {
        return selectionModel;
    }
//
//    public void setSelectionModel(final SelectionModel<Volume> selectionModel) {
//        dataGrid.setSelectionModel(selectionModel);
//    }
}
