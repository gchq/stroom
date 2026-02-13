/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.pipeline.client.presenter;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.config.global.client.presenter.ListDataProvider;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.docref.DocRef;
import stroom.pipeline.client.presenter.DocRefSelectionPresenter.DocRefSelectionView;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.Selection;

import com.google.gwt.user.cellview.client.AbstractHasData;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

public class DocRefSelectionPresenter extends MyPresenterWidget<DocRefSelectionView> {

    private final MyDataGrid<DocRef> dataGrid;
    private final Selection<DocRef> selection = new Selection<>(true, new HashSet<>());
    private final ListDataProvider<DocRef> dataProvider = new ListDataProvider<>();

    private List<DocRef> docRefs = new ArrayList<>();

    @Inject
    public DocRefSelectionPresenter(final EventBus eventBus, final DocRefSelectionView view) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this);
        addSelectedColumn();
        DataGridUtil.addDocRefColumn(getEventBus(), dataGrid, "Document Name", Function.identity(), false);
        dataGrid.addEndColumn(new EndColumn<>());
        dataGrid.hasContextMenu(false);

        dataProvider.addDataDisplay(dataGrid);

        view.setDataWidget(dataGrid);
    }


    void addSelectedColumn() {
        // Select Column
        final Column<DocRef, TickBoxState> column = DataGridUtil.updatableTickBoxColumnBuilder(
                        (DocRef docRef) -> TickBoxState.fromBoolean(selection.isMatch(docRef)))
                .build();

        final Header<TickBoxState> header = new Header<>(
                TickBoxCell.create(false, false)) {
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

        // Add Handlers
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

    public void setMessageText(final String message) {
        getView().setMessage(message);
    }

    public void setDocRefs(final List<DocRef> docRefs) {
        this.docRefs = new ArrayList<>(docRefs);
        dataProvider.setCompleteList(docRefs);
        selection.setMatchAll(true);
        dataGrid.redrawHeaders();
    }

    public List<DocRef> getSelectedItems() {
        return selection.isMatchAll()
                ? docRefs
                : new ArrayList<>(selection.getSet());
    }

    public interface DocRefSelectionView extends View {

        void setDataWidget(final AbstractHasData<?> widget);

        void setMessage(final String message);
    }
}
