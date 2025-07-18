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

package stroom.importexport.client.presenter;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.svg.client.Preset;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;

import com.google.gwt.user.cellview.client.Header;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DocRefListPresenter extends MyPresenterWidget<PagerView> {

    private final MyDataGrid<DocRef> dataGrid;
    private final Set<DocRef> selectedDocRefs = new HashSet<>();
    private final Set<DocRef> allDocRefs = new HashSet<>();

    private boolean showTypeIcon = true;
    private boolean isTableInitialised = false;

    @Inject
    public DocRefListPresenter(final EventBus eventBus,
                               final PagerView view) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this);
        dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);
        view.setPagerVisible(false);
        view.getRefreshButton().setEnabled(false);
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {

        // Tick box col
        final Header<TickBoxState> tickBoxHeader = buildTickBoxHeader();
        dataGrid.addColumn(
                DataGridUtil.updatableTickBoxColumnBuilder((final DocRef docRef) ->
                                selectedDocRefs.contains(docRef)
                                        ? TickBoxState.TICK
                                        : TickBoxState.UNTICK)
                        .centerAligned()
                        .withFieldUpdater((idx, docRef, tickBoxState) -> {
                            switch (tickBoxState) {
                                case UNTICK -> selectedDocRefs.remove(docRef);
                                case TICK -> selectedDocRefs.add(docRef);
                            }
                        })
                        .build(),
                tickBoxHeader,
                ColumnSizeConstants.CHECKBOX_COL);

        // Type icon col
        if (showTypeIcon) {
            dataGrid.addColumn(
                    DataGridUtil.svgPresetColumnBuilder(false, this::getDocTypeIcon)
                            .centerAligned()
                            .build(),
                    DataGridUtil.headingBuilder("")
                            .withToolTip("Document type")
                            .build(),
                    ColumnSizeConstants.ICON_COL);
        }

        // Name col
        dataGrid.addAutoResizableColumn(
                DataGridUtil.textColumnBuilder(DocRef::getName)
                        .build(),
                "Name",
                400);
        dataGrid.addEndColumn(new EndColumn<>());
    }

    private Header<TickBoxState> buildTickBoxHeader() {
        //noinspection Convert2Diamond // Cos GWT
        final Header<TickBoxState> tickBoxHeader = new Header<TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue() {
                final boolean all = selectedDocRefs.containsAll(allDocRefs);
                if (all) {
                    return TickBoxState.TICK;
                } else if (!selectedDocRefs.isEmpty()) {
                    return TickBoxState.HALF_TICK;
                } else {
                    return TickBoxState.UNTICK;
                }
            }
        };

        tickBoxHeader.setUpdater(value -> {
            if (value.equals(TickBoxState.UNTICK)) {
                selectedDocRefs.clear();
                dataGrid.redraw();
            } else if (value.equals(TickBoxState.TICK)) {
                selectedDocRefs.addAll(allDocRefs);
                dataGrid.redraw();
            }
        });
        return tickBoxHeader;
    }

    public void setData(final List<DocRef> allDocRefs) {
        selectedDocRefs.clear();
        this.allDocRefs.clear();
        this.allDocRefs.addAll(allDocRefs);

        if (!isTableInitialised) {
            initTableColumns();
            isTableInitialised = true;
        }
        dataGrid.setRowData(0, allDocRefs);
        dataGrid.setRowCount(allDocRefs.size());
        dataGrid.redraw();
    }

    public Set<DocRef> getSelectedDocRefs() {
        return selectedDocRefs;
    }

    /**
     * Must be called before {@link DocRefListPresenter#setData(List)}
     */
    public void setShowTypeIcon(final boolean showTypeIcon) {
        this.showTypeIcon = showTypeIcon;
        dataGrid.redraw();
    }

    private Preset getDocTypeIcon(final DocRef docRef) {
        if (docRef != null) {
            final DocumentType documentType = DocumentTypeRegistry.get(docRef.getType());
            return NullSafe.get(
                    documentType,
                    dt -> new Preset(dt.getIcon(), dt.getDisplayType(), true));
        } else {
            return null;
        }
    }
}
