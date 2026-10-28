/*
 * Copyright 2018 Crown Copyright
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

package stroom.importexport.client.component;

import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocumentType;
import stroom.document.client.event.DeleteDocumentEvent;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.explorer.client.event.LocateDocEvent;
import stroom.explorer.shared.ExplorerResource;
import stroom.importexport.client.event.ShowDependenciesInfoDialogEvent;
import stroom.importexport.client.event.ShowDocRefDependenciesEvent;
import stroom.importexport.client.event.ShowDocRefDependenciesEvent.DependencyType;
import stroom.importexport.shared.ContentResource;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TokenError;
import stroom.widget.component.client.Controller;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.MenuBuilder;
import stroom.widget.menu.client.ShowMenuEvent;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// The dependencies between documents ([DependencyList]), found by a quick filter, a page at a time
/// in the order chosen; each document's action menu opens, shows, locates or deletes it, or shows
/// its own dependencies.
public class DependencyListController extends Controller {

    private static final ContentResource CONTENT_RESOURCE = GWT.create(ContentResource.class);
    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final RestFactory restFactory;
    private final DependencyCriteria criteria;
    private final RestDataProvider<Dependency, ResultPage<Dependency>> dataProvider;
    private Consumer<String> filterErrorConsumer;
    private final DependencyList view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        The content.
    /// @param restFactory Finds the dependencies and the document types.
    @Inject
    public DependencyListController(final EventBus eventBus,
                                    final DependencyList view,
                                    final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnOpen(this::onOpenDoc);
        view.setOnShowActionMenu((docRef, popupPosition, opener) -> ShowMenuEvent.builder()
                .name(DependencyList.getActionMenuName(docRef))
                .items(buildActionMenu(docRef))
                .popupPosition(popupPosition)
                .opener(opener)
                .fire(this));

        this.restFactory = restFactory;
        criteria = new DependencyCriteria();

        refreshDocTypeIcons();

        dataProvider = new RestDataProvider<Dependency, ResultPage<Dependency>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<Dependency>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CriteriaUtil.setRange(criteria, range);
                CriteriaUtil.setSortList(criteria, view.getSortList());
                restFactory
                        .create(CONTENT_RESOURCE)
                        .method(res -> res.fetchDependencies(criteria))
                        .onSuccess(resultPage -> {
                            // The diagnostic rides back on the successful response rather than as
                            // an error, because the filter re-queries on a debounce and a
                            // half-typed term must not interrupt the user.
                            if (filterErrorConsumer != null) {
                                filterErrorConsumer.accept(
                                        NullSafe.get(resultPage.getFilterError(), TokenError::getText));
                            }
                            dataConsumer.accept(resultPage);
                        })
                        .onFailure(errorHandler)
                        .owner(DependencyListController.this)
                        .taskMonitorFactory(view.getTaskMonitor())
                        .exec();
            }
        };
        view.setDataProvider(dataProvider);
        view.setOnSortChange(dataProvider::refresh);
    }

    private List<Item> buildActionMenu(final DocRef docRef) {

        return MenuBuilder.builder()
                .withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(SvgImage.INFO)
                        .text("Show Info")
                        .command(() -> onDocInfo(docRef)))
                .withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(SvgImage.DELETE)
                        .text("Delete")
                        .command(() -> onDeleteDoc(docRef)))
                .withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(SvgImage.LOCATE)
                        .text("Locate in Explorer")
                        .command(() -> onLocateDoc(docRef)))
                .withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(SvgImage.DEPENDENCIES)
                        .text("Show Dependencies")
                        .command(() -> onShowDependencies(docRef, DependencyType.DEPENDENCY)))
                .withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(SvgImage.DEPENDENCIES)
                        .text("Show Dependants")
                        .command(() -> onShowDependencies(docRef, DependencyType.DEPENDANT)))
                .build();
    }

    /**
     * Open a document
     */
    private void onOpenDoc(final DocRef docRef) {
        OpenDocumentEvent.fire(DependencyListController.this, docRef, true);
    }

    /**
     * Reveal the doc in the Explorer tree
     */
    private void onLocateDoc(final DocRef docRef) {
        LocateDocEvent.fire(this, docRef);
    }

    private void onDocInfo(final DocRef docRef) {
        ShowDependenciesInfoDialogEvent.fire(DependencyListController.this, docRef);
    }

    private void onDeleteDoc(final DocRef docRef) {
        DeleteDocumentEvent.fire(
                DependencyListController.this,
                Collections.singletonList(docRef),
                true,
                result -> refresh(),
                view.getTaskMonitor());
    }

    private void onShowDependencies(final DocRef docRef, final DependencyType dependencyType) {
        ShowDocRefDependenciesEvent.fire(DependencyListController.this, docRef, dependencyType);
    }

    private void refreshDocTypeIcons() {

        // Hold map of doc type icons keyed on type to save constructing for each row
        restFactory
                .create(EXPLORER_RESOURCE)
                .method(ExplorerResource::fetchDocumentTypes)
                .onSuccess(documentTypes -> view.setOpenableTypes(documentTypes
                        .getTypes()
                        .stream()
                        .map(DocumentType::getType)
                        .collect(Collectors.toSet())))
                .owner(this)
                .taskMonitorFactory(view.getTaskMonitor())
                .exec();
    }

    /// @param filterErrorConsumer Given why the filter was rejected after each fetch, or `null` if it
    ///                            wasn't.
    public void setFilterErrorConsumer(final Consumer<String> filterErrorConsumer) {
        this.filterErrorConsumer = filterErrorConsumer;
    }

    /// Filters the dependencies, from the first page.
    ///
    /// @param filterInput The filter's text.
    public void setFilterInput(final String filterInput) {
        this.criteria.setQuickFilter(filterInput);
        // Changing the filter means any existing offset is wrong, so we need to reset to the initial state
        resetRange();
    }

    /// Shows every dependency again, from the first page.
    public void clearFilterInput() {
        this.criteria.setQuickFilter(null);
        // Changing the filter means any existing offset is wrong, so we need to reset to the initial state
        resetRange();
    }

    private void resetRange() {
        view.resetRange();
    }

    /// Fetches the dependencies again.
    public void refresh() {
        this.dataProvider.refresh();
    }
}
