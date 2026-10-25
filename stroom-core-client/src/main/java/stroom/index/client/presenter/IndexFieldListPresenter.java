/*
 * Copyright 2020 Crown Copyright
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

import stroom.alert.client.event.ConfirmEvent;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.index.shared.AddField;
import stroom.index.shared.DeleteField;
import stroom.index.shared.IndexFieldImpl;
import stroom.index.shared.IndexResource;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.UpdateField;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.IndexFieldFields;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.svg.shared.SvgImage;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TokenError;
import stroom.widget.button.client.ButtonView;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.dropdowntree.client.view.QuickFilterPageView;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.function.Consumer;

public class IndexFieldListPresenter
        extends DocPresenter<QuickFilterPageView, LuceneIndexDoc>
        implements QuickFilterUiHandlers {

    private static final IndexResource INDEX_RESOURCE = GWT.create(IndexResource.class);
    private static final String NEW_TITLE = "New Index Field";
    private static final String EDIT_TITLE = "Edit Selected Index Field";
    private static final String DELETE_TITLE = "Delete Selected Index Fields";

    private final PagerPanel pagerView;
    private final RestFactory restFactory;
    private final DataGrid<IndexFieldImpl> dataGrid;
    private final MultiSelectionModelImpl<IndexFieldImpl> selectionModel;
    private final IndexFieldEditPresenter indexFieldEditPresenter;
    private final ButtonView newButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;
    private RestDataProvider<IndexFieldImpl, ResultPage<IndexFieldImpl>> dataProvider;

    private String filter;
    private DocRef docRef;
    private final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory;
    private boolean readOnly = true;

    @Inject
    public IndexFieldListPresenter(final EventBus eventBus,
                                   final QuickFilterPageView view,
                                   final PagerPanel pagerView,
                                   final RestFactory restFactory,
                                   final IndexFieldEditPresenter indexFieldEditPresenter,
                                   final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus, view);
        own(indexFieldEditPresenter);
        getView().setAccessibleName("Filter fields");
        this.pagerView = pagerView;
        this.restFactory = restFactory;
        view.setDataView(pagerView);
        view.setUiHandlers(this);
        this.quickFilterContextHandlerFactory = quickFilterContextHandlerFactory;

        dataGrid = new DataGrid<>();
        // Rows keyed by name, unique in an index
        dataGrid.setKeyProvider(IndexFieldImpl::getFldName);
        dataGrid.setTableName("Index Fields");
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        pagerView.setDataWidget(dataGrid);

        this.indexFieldEditPresenter = indexFieldEditPresenter;

        pagerView.getToolbar().setName("Field actions");
        newButton = pagerView.getToolbar().button(SvgImage.ADD, NEW_TITLE, this::onNew);
        editButton = pagerView.getToolbar().button(SvgImage.EDIT, EDIT_TITLE, this::onEditClicked);
        editButton.setEnabled(false);
        removeButton = pagerView.getToolbar().button(SvgImage.DELETE, DELETE_TITLE, this::onRemoveClicked);
        removeButton.setEnabled(false);

        addColumns();
        enableButtons();

        own(selectionModel.addSelectionHandler(event -> {
            if (!readOnly) {
                enableButtons();
                if (event.getSelectionType().isDoubleSelect()) {
                    onEdit();
                }
            }
        }));
        own(dataGrid.addColumnSortHandler(event -> refresh()));
    }

    private void onNew() {
        if (!readOnly) {
            onAdd();
        }
    }

    private void onEditClicked() {
        if (!readOnly) {
            onEdit();
        }
    }

    private void onRemoveClicked() {
        if (!readOnly) {
            onRemove();
        }
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

        // The names stay, with why the buttons are disabled for a read-only index
        final String suffix = readOnly
                ? " (index is read only)"
                : "";
        newButton.setTitle(NEW_TITLE + suffix);
        editButton.setTitle(EDIT_TITLE + suffix);
        removeButton.setTitle(DELETE_TITLE + suffix);
    }

    private void addColumns() {
        addNameColumn();
        addTypeColumn();
        addStoreColumn();
        addIndexColumn();
        addTermVectorColumn();
        addAnalyzerColumn();
        addCaseSensitiveColumn();
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
        indexFieldEditPresenter.show("New Field", (indexField, e) -> restFactory
                .create(INDEX_RESOURCE)
                .method(res -> res.addField(new AddField(docRef, indexField)))
                .onSuccess(response -> {
                    selectionModel.setSelected(indexField);
                    refresh();
                    e.hide();
                })
                .onFailure(new DefaultErrorHandler(this, e::reset))
                .taskMonitorFactory(pagerView)
                .exec());
    }

    private void onEdit() {
        final IndexFieldImpl existingField = selectionModel.getSelected();
        if (existingField != null) {
            indexFieldEditPresenter.read(existingField);
            indexFieldEditPresenter.show("Edit Field", (indexField, e) -> restFactory
                    .create(INDEX_RESOURCE)
                    .method(res -> res.updateField(new UpdateField(
                            docRef,
                            existingField.getFldName(),
                            indexField)))
                    .onSuccess(response -> {
                        selectionModel.setSelected(indexField);
                        refresh();
                        e.hide();
                    })
                    .onFailure(new DefaultErrorHandler(this, e::reset))
                    .taskMonitorFactory(pagerView)
                    .exec());
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
                                })
                                .taskMonitorFactory(pagerView)
                                .exec();
                    }
                }
            });
        }
    }

    @Override
    protected void onRead(final DocRef docRef, final LuceneIndexDoc document) {
        dataGrid.setTableName("Index '" + docRef.getName() + "' Fields");
        this.docRef = docRef;
        // Field names are particular to one index, so its recent filters are too.
        getView().setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                IndexResource.QUICK_FILTER_CONTEXT.withDataSource(docRef), this, this));
        this.readOnly = isReadOnly();
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
                            .onSuccess(resultPage -> {
                                // A rejected filter comes back as an empty page like any other, so
                                // surface the reason on the filter box. See ResultPage.filterError.
                                getView().setFilterError(
                                        NullSafe.get(resultPage.getFilterError(), TokenError::getText));
                                dataConsumer.accept(resultPage);
                            })
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
