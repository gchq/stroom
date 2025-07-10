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
import stroom.explorer.client.presenter.DocumentTypeCache;
import stroom.svg.client.Preset;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DocRefListPresenter extends MyPresenterWidget<PagerView> {

    private final MyDataGrid<DocRef> dataGrid;
    private final MultiSelectionModelImpl<DocRef> selectionModel;
    private final DocumentTypeCache documentTypeCache;
    private boolean showTypeIcon = true;
    private boolean isTableInitialised = false;
    private Set<DocRef> selectedDocRefs = new HashSet<>();
    private Set<DocRef> allDocRefs = new HashSet<>();

    @Inject
    public DocRefListPresenter(final EventBus eventBus,
                               final PagerView view,
                               final DocumentTypeCache documentTypeCache) {
        super(eventBus, view);
        this.documentTypeCache = documentTypeCache;

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);
        view.setPagerVisible(false);
        view.getRefreshButton().setEnabled(false);
    }

//    private void initTableColumns() {
//        documentTypeCache.fetch(this::initTableColumns, this);
//    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {

        final Header<TickBoxState> header = new Header<TickBoxState>(
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

        header.setUpdater(value -> {
            if (value.equals(TickBoxState.UNTICK)) {
                selectedDocRefs.clear();
                dataGrid.redraw();
            } else if (value.equals(TickBoxState.TICK)) {
                selectedDocRefs.addAll(allDocRefs);
                dataGrid.redraw();
            }
        });

        dataGrid.addColumn(
                DataGridUtil.updatableTickBoxColumnBuilder((final DocRef docRef) -> {
                            return selectedDocRefs.contains(docRef)
                                    ? TickBoxState.TICK
                                    : TickBoxState.UNTICK;
                        })
                        .centerAligned()
                        .withFieldUpdater((idx, docRef, tickBoxState) -> {
                            switch (tickBoxState) {
                                case UNTICK -> selectedDocRefs.remove(docRef);
                                case TICK -> selectedDocRefs.add(docRef);
                            }
                        })
                        .build(),
                header,
                ColumnSizeConstants.CHECKBOX_COL);

        if (showTypeIcon) {
            // Type icon col
            dataGrid.addColumn(
                    DataGridUtil.svgPresetColumnBuilder(false, (DocRef docRef) ->
                                    getDocTypeIcon(docRef))
                            .centerAligned()
                            .build(),
                    DataGridUtil.headingBuilder("")
                            .withToolTip("Document type")
                            .build(),
                    ColumnSizeConstants.ICON_COL);
        }

        // Name.
        final Column<DocRef, String> nameColumn = new Column<DocRef, String>(new TextCell()) {
            @Override
            public String getValue(final DocRef docRef) {
                return docRef.getName();
            }
        };
        dataGrid.addAutoResizableColumn(nameColumn, "Name", 400);
        dataGrid.addEndColumn(new EndColumn<>());
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

//    public MultiSelectionModel<DocRef> getSelectionModel() {
//        return selectionModel;
//    }

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
