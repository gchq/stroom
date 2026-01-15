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

package stroom.index.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.index.shared.AddField;
import stroom.index.shared.DeleteField;
import stroom.index.shared.IndexFieldImpl;
import stroom.index.shared.IndexResource;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.UpdateField;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.IndexFieldFields;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.dropdowntree.client.view.QuickFilterPageView;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.function.Consumer;

public class IndexFieldListPresenter
        extends DocumentEditPresenter<QuickFilterPageView, LuceneIndexDoc>
        implements QuickFilterUiHandlers {

    private static final IndexResource INDEX_RESOURCE = GWT.create(IndexResource.class);

    private final PagerView pagerView;
    private final RestFactory restFactory;
    private final MyDataGrid<IndexFieldImpl> dataGrid;
    private final MultiSelectionModelImpl<IndexFieldImpl> selectionModel;
    private final IndexFieldEditPresenter indexFieldEditPresenter;
    private final ButtonView newButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;
    private RestDataProvider<IndexFieldImpl, ResultPage<IndexFieldImpl>> dataProvider;

    private String filter;
    private DocRef docRef;
    private boolean readOnly = true;

    @Inject
    public IndexFieldListPresenter(final EventBus eventBus,
                                   final QuickFilterPageView view,
                                   final PagerView pagerView,
                                   final RestFactory restFactory,
                                   final IndexFieldEditPresenter indexFieldEditPresenter) {
        super(eventBus, view);
        this.pagerView = pagerView;
        this.restFactory = restFactory;
        view.setDataView(pagerView);
        view.setUiHandlers(this);

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        pagerView.setDataWidget(dataGrid);

        this.indexFieldEditPresenter = indexFieldEditPresenter;

        newButton = pagerView.addButton(SvgPresets.NEW_ITEM);
        editButton = pagerView.addButton(SvgPresets.EDIT);
        removeButton = pagerView.addButton(SvgPresets.DELETE);

        addColumns();
        enableButtons();
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(newButton.addClickHandler(event -> {
            if (!readOnly) {
                if (MouseUtil.isPrimary(event)) {
                    onAdd();
                }
            }
        }));
        registerHandler(editButton.addClickHandler(event -> {
            if (!readOnly) {
                if (MouseUtil.isPrimary(event)) {
                    onEdit();
                }
            }
        }));
        registerHandler(removeButton.addClickHandler(event -> {
            if (!readOnly) {
                if (MouseUtil.isPrimary(event)) {
                    onRemove();
                }
            }
        }));
        registerHandler(selectionModel.addSelectionHandler(event -> {
            if (!readOnly) {
                enableButtons();
                if (event.getSelectionType().isDoubleSelect()) {
                    onEdit();
                }
            }
        }));
        registerHandler(dataGrid.addColumnSortHandler(event -> refresh()));
    }

    @Override
    public void onFilterChange(final String text) {
        filter = text;
        refresh();
    }

    private void enableButtons() {
        newButton.setEnabled(!readOnly);
        if (!readOnly) {
            final IndexFieldImpl selectedElement = selectionModel.getSelected();
            final boolean enabled = selectedElement != null;
            editButton.setEnabled(enabled);
            removeButton.setEnabled(enabled);
        } else {
            editButton.setEnabled(false);
            removeButton.setEnabled(false);
        }

        if (readOnly) {
            newButton.setTitle("New field disabled as index is read only");
            editButton.setTitle("Edit field disabled as index is read only");
            removeButton.setTitle("Remove field disabled as index is read only");
        } else {
            newButton.setTitle("New Field");
            editButton.setTitle("Edit Field");
            removeButton.setTitle("Remove Field");
        }
    }

    private void addColumns() {
        addNameColumn();
        addTypeColumn();
        addStoreColumn();
        addIndexColumn();
        addTermVectorColumn();
        addAnalyzerColumn();
        addCaseSensitiveColumn();
        dataGrid.addEndColumn(new EndColumn<>());
    }

    private void addNameColumn() {
        final Column<IndexFieldImpl, String> column = DataGridUtil.textColumnBuilder(IndexFieldImpl::getFldName)
                .withSorting(IndexFieldFields.NAME)
                .build();
        dataGrid.addResizableColumn(column,
                IndexFieldFields.NAME,
                150);
        dataGrid.sort(column);
    }

    private void addTypeColumn() {
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((IndexFieldImpl row) -> row.getFldType().getDisplayValue())
                        .withSorting(IndexFieldFields.TYPE)
                        .build(),
                IndexFieldFields.TYPE,
                100);
    }

    private void addStoreColumn() {
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((IndexFieldImpl row) -> getYesNoString(row.isStored()))
                        .withSorting(IndexFieldFields.STORE)
                        .build(),
                IndexFieldFields.STORE,
                100);
    }

    private void addIndexColumn() {
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((IndexFieldImpl row) -> getYesNoString(row.isIndexed()))
                        .withSorting(IndexFieldFields.INDEX)
                        .build(),
                IndexFieldFields.INDEX,
                100);
    }

    private void addTermVectorColumn() {
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((IndexFieldImpl row) -> getYesNoString(row.isTermPositions()))
                        .withSorting(IndexFieldFields.POSITIONS)
                        .build(),
                IndexFieldFields.POSITIONS,
                100);
    }

    private void addAnalyzerColumn() {
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((IndexFieldImpl row) -> row.getAnalyzerType().getDisplayValue())
                        .withSorting(IndexFieldFields.ANALYSER)
                        .build(),
                IndexFieldFields.ANALYSER,
                100);
    }

    private void addCaseSensitiveColumn() {
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((IndexFieldImpl row) -> String.valueOf(row.isCaseSensitive()))
                        .withSorting(IndexFieldFields.CASE_SENSITIVE)
                        .build(),
                "Case Sensitive",
                100);
    }

    private String getYesNoString(final boolean bool) {
        if (bool) {
            return "Yes";
        }
        return "No";
    }

    private void onAdd() {
        indexFieldEditPresenter.read(IndexFieldImpl.builder().build());
        indexFieldEditPresenter.show("New Field", e -> {
            if (e.isOk()) {
                final IndexFieldImpl indexField = indexFieldEditPresenter.write();
                restFactory
                        .create(INDEX_RESOURCE)
                        .method(res -> res.addField(new AddField(docRef, indexField)))
                        .onSuccess(response -> {
                            selectionModel.setSelected(indexField);
                            refresh();
                            e.hide();
                            DirtyEvent.fire(IndexFieldListPresenter.this, true);
                        })
                        .onFailure(new DefaultErrorHandler(this, e::reset))
                        .taskMonitorFactory(pagerView)
                        .exec();
            } else {
                e.hide();
            }
        });
    }

    private void onEdit() {
        final IndexFieldImpl existingField = selectionModel.getSelected();
        if (existingField != null) {
            indexFieldEditPresenter.read(existingField);
            indexFieldEditPresenter.show("Edit Field", e -> {
                if (e.isOk()) {
                    try {
                        final IndexFieldImpl indexField = indexFieldEditPresenter.write();
                        restFactory
                                .create(INDEX_RESOURCE)
                                .method(res -> res.updateField(new UpdateField(
                                        docRef,
                                        existingField.getFldName(),
                                        indexField)))
                                .onSuccess(response -> {
                                    selectionModel.setSelected(indexField);
                                    refresh();
                                    e.hide();
                                    DirtyEvent.fire(IndexFieldListPresenter.this, true);
                                })
                                .onFailure(new DefaultErrorHandler(this, e::reset))
                                .taskMonitorFactory(pagerView)
                                .exec();
                    } catch (final RuntimeException ex) {
                        AlertEvent.fireError(IndexFieldListPresenter.this, ex.getMessage(), e::reset);
                    }
                } else {
                    e.hide();
                }
            });
        }
    }

    private void onRemove() {
        final List<IndexFieldImpl> list = selectionModel.getSelectedItems();
        if (list != null && list.size() > 0) {
            String message = "Are you sure you want to delete the selected field?";
            if (list.size() > 1) {
                message = "Are you sure you want to delete the selected fields?";
            }

            ConfirmEvent.fire(this, message, result -> {
                if (result) {
                    for (final IndexFieldImpl indexField : list) {
                        restFactory
                                .create(INDEX_RESOURCE)
                                .method(res -> res.deleteField(new DeleteField(docRef, indexField.getFldName())))
                                .onSuccess(response -> {
                                    selectionModel.clear();
                                    refresh();
                                    DirtyEvent.fire(IndexFieldListPresenter.this, true);
                                })
                                .taskMonitorFactory(pagerView)
                                .exec();
                    }
                }
            });
        }
    }

    @Override
    protected void onRead(final DocRef docRef, final LuceneIndexDoc document, final boolean readOnly) {
        this.docRef = docRef;
        this.readOnly = readOnly;
        enableButtons();
        refresh();
    }

    @Override
    protected LuceneIndexDoc onWrite(final LuceneIndexDoc document) {
        return document;
    }

    private void refresh() {
        if (dataProvider == null) {
            dataProvider = new RestDataProvider<IndexFieldImpl, ResultPage<IndexFieldImpl>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<IndexFieldImpl>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    final FindFieldCriteria criteria = new FindFieldCriteria(
                            CriteriaUtil.createPageRequest(range),
                            CriteriaUtil.createSortList(dataGrid.getColumnSortList()),
                            docRef,
                            filter,
                            null);

                    restFactory
                            .create(INDEX_RESOURCE)
                            .method(res -> res.findFields(criteria))
                            .onSuccess(dataConsumer)
                            .onFailure(errorHandler)
                            .taskMonitorFactory(pagerView)
                            .exec();
                }
            };
            dataProvider.addDataDisplay(dataGrid);

        } else {
            dataProvider.refresh();
        }
    }
}
