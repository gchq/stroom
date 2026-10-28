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

package stroom.pipeline.client.component;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.config.global.client.ListDataProvider;
import stroom.data.client.ColumnSizeConstants;
import stroom.data.client.component.DocRefCell;
import stroom.docref.DocRef;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.Selection;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

/// The documents changed, each with a tick box to save it (all ticked at first), and one in the
/// header for them all.
public class DocRefSelection extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DataGrid<DocRef> dataGrid;

    private final Selection<DocRef> selection = new Selection<>(true, new HashSet<>());
    private final ListDataProvider<DocRef> dataProvider = new ListDataProvider<>();
    private List<DocRef> docRefs = new ArrayList<>();

    /// Makes an empty list, with its tick boxes; the documents' column comes with
    /// [#addDocumentColumn(DocRefCell.Builder)].
    public DocRefSelection() {
        initWidget(BINDER.createAndBindUi(this));
        addSelectedColumn();
        dataProvider.addDataDisplay(dataGrid);
    }

    /// Adds the documents' column.
    ///
    /// @param cell The document cell's builder, configured to open its documents.
    public void addDocumentColumn(final DocRefCell.Builder<DocRef> cell) {
        DataGridUtil.addDocRefColumn(cell, dataGrid, "Document Name", Function.identity(), false);
    }

    /// @param docRefs The documents, a row each, all ticked.
    public void setDocRefs(final List<DocRef> docRefs) {
        this.docRefs = new ArrayList<>(docRefs);
        dataProvider.setCompleteList(docRefs);
        selection.setMatchAll(true);
        dataGrid.redrawHeaders();
    }

    /// @return The documents ticked.
    public List<DocRef> getSelectedItems() {
        return selection.isMatchAll()
                ? docRefs
                : new ArrayList<>(selection.getSet());
    }

    private void addSelectedColumn() {
        final Column<DocRef, TickBoxState> column = DataGridUtil.updatableTickBoxColumnBuilder(
                        (DocRef docRef) -> TickBoxState.fromBoolean(selection.isMatch(docRef)))
                .build();

        final Header<TickBoxState> header = new Header<>(TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue() {
                if (selection.isMatchAll() || selection.size() == docRefs.size()) {
                    return TickBoxState.TICK;
                } else if (selection.size() > 0) {
                    return TickBoxState.HALF_TICK;
                }
                return TickBoxState.UNTICK;
            }
        };
        dataGrid.addColumn(column, header, ColumnSizeConstants.CHECKBOX_COL);

        header.setUpdater(value -> {
            if (value.equals(TickBoxState.UNTICK)) {
                setMatchAll(false);
            } else if (value.equals(TickBoxState.TICK)) {
                setMatchAll(true);
            }
        });

        column.setFieldUpdater((index, row, value) -> {
            if (value.toBoolean()) {
                selection.add(row);
            } else {
                // De-selecting one and currently matching all ?
                if (selection.isMatchAll()) {
                    selection.setMatchAll(false);
                    selection.addAll(docRefs);
                }
                selection.remove(row);
            }
            dataGrid.redrawHeaders();
        });
    }

    private void setMatchAll(final boolean select) {
        selection.clear();
        selection.setMatchAll(select);
        dataProvider.updateRowData(0, docRefs);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DocRefSelection> {

    }
}
