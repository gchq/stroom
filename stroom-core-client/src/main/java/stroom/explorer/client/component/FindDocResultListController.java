/*
 * Copyright 2023 Crown Copyright
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

package stroom.explorer.client.component;

import stroom.data.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.shared.DocumentFindRequest;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.FindResult;
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
import java.util.function.Consumer;

/// The documents found ([FindDocResultList]) by a filter its owner builds with
/// [#getExplorerTreeFilterBuilder()]: a page at a time, the first chosen when the filter changes.
/// An empty filter finds nothing, without asking Stroom.
public class FindDocResultListController extends Controller {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final FindDocResultList view;
    private final RestDataProvider<FindResult, ResultPage<FindResult>> dataProvider;
    private final ExplorerTreeFilter.Builder explorerTreeFilterBuilder = ExplorerTreeFilter.builder();
    private DocumentFindRequest currentQuery = new DocumentFindRequest(
            PageRequest.createDefault(),
            null,
            null);
    private ExplorerTreeFilter lastFilter;
    private boolean initialised;
    private boolean focusText;
    private Consumer<String> onFilterError = error -> {
    };
    private Runnable onFocusFilter = () -> {
    };

    /// Made by GIN, with its content.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        The content.
    /// @param restFactory Finds the documents.
    @Inject
    public FindDocResultListController(final EventBus eventBus,
                                       final FindDocResultList view,
                                       final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnUpFromFirst(() -> onFocusFilter.run());

        explorerTreeFilterBuilder.requiredPermissions(DocumentPermission.VIEW);

        final MultiSelectionModel<FindResult> selectionModel = view.getSelectionModel();
        dataProvider = new RestDataProvider<FindResult, ResultPage<FindResult>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<FindResult>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
                final ExplorerTreeFilter filter = explorerTreeFilterBuilder.build();
                final boolean filterChange = !Objects.equals(lastFilter, filter);
                lastFilter = filter;

                currentQuery = new DocumentFindRequest(
                        pageRequest,
                        currentQuery.getSortList(),
                        filter);

                if ((filter.getRecentItems() == null && NullSafe.isBlankString(filter.getNameFilter())) ||
                    (filter.getRecentItems() != null && filter.getRecentItems().size() == 0)) {
                    final ResultPage<FindResult> resultPage = ResultPage.empty();
                    if (resultPage.getPageStart() != view.getPageStart()) {
                        view.setPageStart(resultPage.getPageStart());
                    }
                    dataConsumer.accept(resultPage);
                    selectionModel.clear();
                    resetFocus();

                } else {
                    restFactory
                            .create(EXPLORER_RESOURCE)
                            .method(res -> res.find(currentQuery))
                            .onSuccess(resultPage -> {
                                // A rejected filter comes back as an empty page like any other,
                                // so surface the reason on the filter box. See
                                // ResultPage.filterError.
                                onFilterError.accept(NullSafe.get(resultPage.getFilterError(), TokenError::getText));
                                if (resultPage.getPageStart() != view.getPageStart()) {
                                    view.setPageStart(resultPage.getPageStart());
                                }
                                dataConsumer.accept(resultPage);

                                if (filterChange) {
                                    if (resultPage.size() > 0) {
                                        selectionModel.setSelected(resultPage.getValues().get(0));
                                    } else {
                                        selectionModel.clear();
                                    }
                                }

                                resetFocus();
                            })
                            .onFailure(errorHandler)
                            .owner(FindDocResultListController.this)
                            .taskMonitorFactory(view.getTaskMonitor())
                            .exec();
                }
            }
        };
    }

    private void resetFocus() {
        if (focusText) {
            focusText = false;
            onFocusFilter.run();
        }
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

    /// @param onFilterError Given why the filter was rejected after each find, or `null` if it
    ///                      wasn't.
    public void setOnFilterError(final Consumer<String> onFilterError) {
        this.onFilterError = onFilterError;
    }

    /// @param onOpen Given a document found to open (Enter, or a double click, on it).
    public void setOnOpen(final Consumer<FindResult> onOpen) {
        view.setOnOpen(onOpen);
    }

    /// @param onFocusFilter Run to put the focus back on the filter: on Up from the first result,
    ///                      and after a find asked for with [#setFocusText(boolean)].
    public void setOnFocusFilter(final Runnable onFocusFilter) {
        this.onFocusFilter = onFocusFilter;
    }

    /// @return The filter's builder: the owner sets the name filter or recent items on it, then
    ///         calls [#refresh()].
    public ExplorerTreeFilter.Builder getExplorerTreeFilterBuilder() {
        return explorerTreeFilterBuilder;
    }

    /// @return The document found with the focus, or `null`.
    public FindResult getSelected() {
        return view.getSelectionModel().getSelected();
    }

    /// Moves the focus to a row.
    ///
    /// @param row        The row on the page.
    /// @param stealFocus Whether to take the focus from where it is.
    public void setKeyboardSelectedRow(final int row, final boolean stealFocus) {
        view.setKeyboardSelectedRow(row, stealFocus);
    }

    /// @param focusText Whether the next find puts the focus back on the filter.
    public void setFocusText(final boolean focusText) {
        this.focusText = focusText;
    }
}
