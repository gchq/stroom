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

import stroom.data.client.component.RefCellsController;
import stroom.dispatch.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.shared.AdvancedDocumentFindWithPermissionsRequest;
import stroom.explorer.shared.AdvancedDocumentFindWithPermissionsRequest.Builder;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.FindResultWithPermissions;
import stroom.query.api.ExpressionOperator;
import stroom.util.client.cell.DocRefCell;
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

/// A user's permissions on documents ([DocumentPermissionsList]), found by the criteria its owner
/// builds with [#getCriteriaBuilder()], a page at a time, the first chosen when the filter changes.
/// Its owner acts on its buttons.
public class DocumentPermissionsListController extends Controller {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final RestFactory restFactory;
    private final RefCellsController refCells;
    private DocumentPermissionsList view;
    private final Builder criteriaBuilder = new Builder();
    private RestDataProvider<FindResultWithPermissions, ResultPage<FindResultWithPermissions>> dataProvider;
    private ExpressionOperator lastFilter;
    private Consumer<String> onFilterError = error -> {
    };
    private Consumer<ResultPage<FindResultWithPermissions>> onResultPage = resultPage -> {
    };

    /// Made by GIN; [#attach(DocumentPermissionsList)] gives it its list.
    ///
    /// @param eventBus    The application's event bus.
    /// @param restFactory Finds the documents.
    /// @param refCells    Lets the document cells open their documents.
    @Inject
    public DocumentPermissionsListController(final EventBus eventBus,
                                             final RestFactory restFactory,
                                             final RefCellsController refCells) {
        super(eventBus);
        this.restFactory = restFactory;
        this.refCells = own(refCells);
    }

    /// Attaches this to a list, e.g. one a template declares. The list is then this controller's
    /// widget.
    ///
    /// @param view The list.
    /// @return This, for its owner to keep.
    public DocumentPermissionsListController attach(final DocumentPermissionsList view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);
        view.addColumns(refCells.configure(new DocRefCell.Builder<FindResultWithPermissions>()));
        return this;
    }

    /// Finds the documents again; the first time, starts the list.
    public void refresh() {
        if (dataProvider == null) {
            final MultiSelectionModel<FindResultWithPermissions> selectionModel = view.getSelectionModel();
            dataProvider = new RestDataProvider<FindResultWithPermissions, ResultPage<FindResultWithPermissions>>(
                    getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<FindResultWithPermissions>> dataConsumer,
                                    final RestErrorHandler errorHandler) {

                    criteriaBuilder.pageRequest(new PageRequest(range.getStart(), range.getLength()));
                    final AdvancedDocumentFindWithPermissionsRequest request = criteriaBuilder.build();
                    final ExpressionOperator filter = request.getExpression();
                    final boolean isFilterChange = !Objects.equals(lastFilter, filter);
                    lastFilter = filter;

                    restFactory
                            .create(EXPLORER_RESOURCE)
                            .method(res -> res.advancedFindWithPermissions(request))
                            .onSuccess(resultPage -> {
                                // A rejected filter comes back as an empty page like any other, so surface
                                // the reason on whichever quick filter drives this list.
                                onFilterError.accept(NullSafe.get(resultPage.getFilterError(), TokenError::getText));
                                onResultPage.accept(resultPage);
                                dataConsumer.accept(resultPage);

                                if (isFilterChange) {
                                    if (!resultPage.isEmpty()) {
                                        selectionModel.setSelected(resultPage.getValues().get(0));
                                    } else {
                                        selectionModel.clear();
                                    }
                                }
                            })
                            .onFailure(errorHandler)
                            .owner(DocumentPermissionsListController.this)
                            .taskMonitorFactory(view.getTaskMonitor())
                            .exec();
                }
            };
            view.setDataProvider(dataProvider);

        } else {
            dataProvider.refresh();
        }
    }

    /// @return The criteria's builder: the owner sets the user, expression, quick filter and so on,
    ///         then calls [#refresh()].
    public Builder getCriteriaBuilder() {
        return criteriaBuilder;
    }

    /// @return The row selected, or `null`.
    public FindResultWithPermissions getSelected() {
        return view.getSelectionModel().getSelected();
    }

    /// Goes back to the first page.
    public void resetRange() {
        view.resetRange();
    }

    /// @param enabled Whether Batch Edit can be pressed.
    public void setBatchEditEnabled(final boolean enabled) {
        view.setBatchEditEnabled(enabled);
    }

    /// @param onEdit Run when Edit is pressed, or a row is double clicked.
    public void setOnEdit(final Runnable onEdit) {
        view.setOnEdit(onEdit);
    }

    /// @param onFilter Run when Filter is pressed.
    public void setOnFilter(final Runnable onFilter) {
        view.setOnFilter(onFilter);
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

    /// @param onResultPage Given each page found, e.g. to say how many documents there are.
    public void setOnResultPage(final Consumer<ResultPage<FindResultWithPermissions>> onResultPage) {
        this.onResultPage = onResultPage;
    }
}
