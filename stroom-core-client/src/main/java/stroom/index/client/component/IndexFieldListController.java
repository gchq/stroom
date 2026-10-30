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

package stroom.index.client.component;

import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.index.shared.AddField;
import stroom.index.shared.DeleteField;
import stroom.index.shared.IndexFieldImpl;
import stroom.index.shared.IndexResource;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.UpdateField;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TokenError;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.datagrid.client.CriteriaUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.function.Consumer;

/// The Fields tab of a Lucene index ([IndexFieldList]): its fields, a page at a time, found by the
/// quick filter. New, Edit and Delete change them on the server at once, unless the index is read only
/// ([Contexts#READ_ONLY]).
public class IndexFieldListController extends Controller {

    private static final IndexResource INDEX_RESOURCE = GWT.create(IndexResource.class);

    private final IndexFieldList view;
    private final RestFactory restFactory;
    private final Provider<IndexFieldEditorController> indexFieldEditorProvider;
    private final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory;
    private RestDataProvider<IndexFieldImpl, ResultPage<IndexFieldImpl>> dataProvider;

    private String filter;
    private DocRef docRef;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                         The application's event bus.
    /// @param view                             The content.
    /// @param restFactory                      Finds and changes the fields.
    /// @param indexFieldEditorProvider         Makes a field's editor.
    /// @param quickFilterContextHandlerFactory Makes the quick filter's recent filters.
    @Inject
    public IndexFieldListController(final EventBus eventBus,
                                    final IndexFieldList view,
                                    final RestFactory restFactory,
                                    final Provider<IndexFieldEditorController> indexFieldEditorProvider,
                                    final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.indexFieldEditorProvider = indexFieldEditorProvider;
        this.quickFilterContextHandlerFactory = quickFilterContextHandlerFactory;
        initWidget(view);
        view.setOnFilterChange(this::onFilterChange);
        view.setOnNew(this::onAdd);
        view.setOnEdit(this::onEdit);
        view.setOnRemove(this::onRemove);
        view.setOnSortChange(this::refresh);
        watch(Contexts.READ_ONLY, view::setReadOnly);
    }

    private void onFilterChange(final String text) {
        filter = text;
        refresh();
    }

    private void onAdd() {
        indexFieldEditorProvider.get().edit(this, "New Field", IndexFieldImpl.builder().build(),
                (indexField, e) -> restFactory
                        .create(INDEX_RESOURCE)
                        .method(res -> res.addField(new AddField(docRef, indexField)))
                        .onSuccess(response -> {
                            view.getSelectionModel().setSelected(indexField);
                            refresh();
                            e.hide();
                        })
                        .onFailure(new DefaultErrorHandler(this, e::reset))
                        .owner(this)
                        .taskMonitorFactory(view.getTaskMonitor())
                        .exec());
    }

    private void onEdit() {
        final IndexFieldImpl existingField = view.getSelectionModel().getSelected();
        if (existingField != null) {
            indexFieldEditorProvider.get().edit(this, "Edit Field", existingField, (indexField, e) -> restFactory
                    .create(INDEX_RESOURCE)
                    .method(res -> res.updateField(new UpdateField(
                            docRef,
                            existingField.getFldName(),
                            indexField)))
                    .onSuccess(response -> {
                        view.getSelectionModel().setSelected(indexField);
                        refresh();
                        e.hide();
                    })
                    .onFailure(new DefaultErrorHandler(this, e::reset))
                    .owner(this)
                    .taskMonitorFactory(view.getTaskMonitor())
                    .exec());
        }
    }

    private void onRemove() {
        final List<IndexFieldImpl> list = view.getSelectionModel().getSelectedItems();
        if (list != null && !list.isEmpty()) {
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
                                    view.getSelectionModel().clear();
                                    refresh();
                                })
                                .owner(this)
                                .taskMonitorFactory(view.getTaskMonitor())
                                .exec();
                    }
                }
            });
        }
    }

    /// Shows an index's fields.
    ///
    /// @param docRef   The index.
    /// @param document Its document.
    public void read(final DocRef docRef, final LuceneIndexDoc document) {
        view.setIndexName(docRef.getName());
        this.docRef = docRef;
        // Field names are particular to one index, so its recent filters are too.
        view.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                IndexResource.QUICK_FILTER_CONTEXT.withDataSource(docRef), this, view.getTaskMonitor()));
        refresh();
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
                            CriteriaUtil.createSortList(view.getSortList()),
                            docRef,
                            filter,
                            null);

                    restFactory
                            .create(INDEX_RESOURCE)
                            .method(res -> res.findFields(criteria))
                            .onSuccess(resultPage -> {
                                // A rejected filter comes back as an empty page like any other, so
                                // surface the reason on the filter box. See ResultPage.filterError.
                                view.setFilterError(
                                        NullSafe.get(resultPage.getFilterError(), TokenError::getText));
                                dataConsumer.accept(resultPage);
                            })
                            .onFailure(errorHandler)
                            .owner(IndexFieldListController.this)
                            .taskMonitorFactory(view.getTaskMonitor())
                            .exec();
                }
            };
            view.setDataProvider(dataProvider);

        } else {
            dataProvider.refresh();
        }
    }
}
