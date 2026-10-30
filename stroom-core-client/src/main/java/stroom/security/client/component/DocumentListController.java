/*
 * Copyright 2024 Crown Copyright
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

package stroom.security.client.component;

import stroom.dispatch.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.shared.AdvancedDocumentFindRequest;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.FindResult;
import stroom.query.api.ExpressionOperator;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TokenError;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/// The documents found ([DocumentList]) by an expression and a quick filter, a page at a time, the
/// first chosen when the filter changes. Its owner acts on its buttons and sets whether they can be
/// pressed.
public class DocumentListController extends Controller {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final DocumentList view;
    private final RestDataProvider<FindResult, ResultPage<FindResult>> dataProvider;
    private final AdvancedDocumentFindRequest.Builder criteriaBuilder = new AdvancedDocumentFindRequest.Builder();
    private ExpressionOperator lastFilter;
    private boolean initialised;
    private Consumer<String> onFilterError = error -> {
    };
    private Consumer<ResultPage<FindResult>> onResultPage = resultPage -> {
    };
    private Consumer<FindResult> onSelectionChange = selected -> {
    };

    /// Made by GIN, with its content.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        The content.
    /// @param restFactory Finds the documents.
    @Inject
    public DocumentListController(final EventBus eventBus,
                                  final DocumentList view,
                                  final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        initWidget(view);

        final MultiSelectionModel<FindResult> selectionModel = view.getSelectionModel();
        own(selectionModel.addSelectionHandler(event -> onSelectionChange.accept(getSelected())));
        dataProvider = new RestDataProvider<FindResult, ResultPage<FindResult>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<FindResult>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
                criteriaBuilder.pageRequest(pageRequest);

                final AdvancedDocumentFindRequest request = criteriaBuilder.build();
                final ExpressionOperator filter = request.getExpression();
                final boolean filterChange = !Objects.equals(lastFilter, filter);
                lastFilter = filter;

                restFactory
                        .create(EXPLORER_RESOURCE)
                        .method(res -> res.advancedFind(request))
                        .onSuccess(resultPage -> {
                            // A rejected filter comes back as an empty page like any other, so surface
                            // the reason on whichever quick filter drives this list.
                            onFilterError.accept(NullSafe.get(resultPage.getFilterError(), TokenError::getText));
                            onResultPage.accept(resultPage);
                            if (resultPage.getPageStart() != view.getPageStart()) {
                                view.setPageStart(resultPage.getPageStart());
                            }
                            dataConsumer.accept(resultPage);

                            if (filterChange) {
                                if (!resultPage.isEmpty()) {
                                    selectionModel.setSelected(resultPage.getValues().get(0));
                                } else {
                                    selectionModel.clear();
                                }
                            }
                        })
                        .onFailure(errorHandler)
                        .owner(DocumentListController.this)
                        .taskMonitorFactory(view.getTaskMonitor())
                        .exec();
            }
        };
    }

    /// Finds the documents again; the first time, starts the list.
    public void refresh() {
        if (!initialised) {
            initialised = true;
            view.setDataProvider(dataProvider);
        } else {
            dataProvider.refresh();
        }
    }

    /// @param quickFilter The quick filter's text, sent to Stroom as it is.
    public void setQuickFilter(final String quickFilter) {
        criteriaBuilder.quickFilter(quickFilter);
    }

    /// @param expression What the documents must match.
    public void setExpression(final ExpressionOperator expression) {
        criteriaBuilder.expression(expression);
    }

    /// @param requiredPermissions The permissions the user must have on a document to see it.
    public void setRequiredPermissions(final Set<DocumentPermission> requiredPermissions) {
        criteriaBuilder.requiredPermissions(requiredPermissions);
    }

    /// @return The document with the focus, or `null`.
    public FindResult getSelected() {
        return view.getSelectionModel().getSelected();
    }

    /// @param enabled Whether Edit can be pressed.
    public void setEditEnabled(final boolean enabled) {
        view.setEditEnabled(enabled);
    }

    /// @param enabled Whether Batch Edit can be pressed.
    public void setBatchEditEnabled(final boolean enabled) {
        view.setBatchEditEnabled(enabled);
    }

    /// @param onOpen Given a document to open (Enter, or a double click, on it).
    public void setOnOpen(final Consumer<FindResult> onOpen) {
        view.setOnOpen(onOpen);
    }

    /// @param onFilter Run when Filter is pressed.
    public void setOnFilter(final Runnable onFilter) {
        view.setOnFilter(onFilter);
    }

    /// @param onEdit Run when Edit is pressed.
    public void setOnEdit(final Runnable onEdit) {
        view.setOnEdit(onEdit);
    }

    /// @param onBatchEdit Run when Batch Edit is pressed.
    public void setOnBatchEdit(final Runnable onBatchEdit) {
        view.setOnBatchEdit(onBatchEdit);
    }

    /// @param onFilterError Given why the filter was rejected after each find, or `null` if it
    ///                      wasn't.
    public void setOnFilterError(final Consumer<String> onFilterError) {
        this.onFilterError = onFilterError;
    }

    /// @param onSelectionChange Given the chosen document, or null for none, whenever the choice
    ///                          changes, e.g. to enable what acts on it.
    public void setOnSelectionChange(final Consumer<FindResult> onSelectionChange) {
        this.onSelectionChange = onSelectionChange;
    }

    /// @param onResultPage Given each page found, e.g. to say how many documents there are.
    public void setOnResultPage(final Consumer<ResultPage<FindResult>> onResultPage) {
        this.onResultPage = onResultPage;
    }
}
