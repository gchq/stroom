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
import stroom.docref.DocRef;
import stroom.explorer.client.DocumentTypeCache;
import stroom.explorer.shared.DocumentTypes;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.security.shared.DocPermissionResource;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.FetchDocumentUserPermissionsRequest;
import stroom.security.shared.PermissionShowLevel;
import stroom.security.shared.UserFields;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.client.cell.UserRefCell;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TokenError;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Controller;
import stroom.widget.datagrid.client.CriteriaUtil;
import stroom.widget.quickfilter.client.QuickFilterTooltipUtil;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

/// The users and groups with permissions on a document ([DocumentUserPermissionsList]), found by a
/// quick filter, a page at a time in the order chosen.
public class DocumentUserPermissionsListController extends Controller {

    private static final DocPermissionResource DOC_PERMISSION_RESOURCE = GWT.create(DocPermissionResource.class);

    private final RestFactory restFactory;
    private final FetchDocumentUserPermissionsRequest.Builder criteriaBuilder =
            new FetchDocumentUserPermissionsRequest.Builder();
    private final UiConfigCache uiConfigCache;
    private final DocumentTypeCache documentTypeCache;
    private final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory;
    private DocumentUserPermissionsList view;
    private RestDataProvider<DocumentUserPermissions, ResultPage<DocumentUserPermissions>> dataProvider;
    private final RefCellsController refCells;
    private DocRef docRef;
    private DocumentTypes documentTypes;
    private boolean columnsAdded;

    /// Made by GIN; [#attach(DocumentUserPermissionsList)] gives it its list.
    ///
    /// @param eventBus                         The application's event bus.
    /// @param restFactory                      Fetches the permissions.
    /// @param uiConfigCache                    Gives the quick filter's help link.
    /// @param documentTypeCache                Gives the document types.
    /// @param quickFilterContextHandlerFactory Gives the quick filter its recent filters.
    /// @param refCells                         Lets the user cells open their users.
    @Inject
    public DocumentUserPermissionsListController(final EventBus eventBus,
                                                 final RestFactory restFactory,
                                                 final UiConfigCache uiConfigCache,
                                                 final DocumentTypeCache documentTypeCache,
                                                 final QuickFilterContextHandlerFactory
                                                         quickFilterContextHandlerFactory,
                                                 final RefCellsController refCells) {
        super(eventBus);
        this.refCells = own(refCells);
        this.restFactory = restFactory;
        this.uiConfigCache = uiConfigCache;
        this.documentTypeCache = documentTypeCache;
        this.quickFilterContextHandlerFactory = quickFilterContextHandlerFactory;
    }

    /// Attaches this to a list, e.g. one a template declares. The list is then this controller's
    /// widget.
    ///
    /// @param view The list.
    /// @return This, for its owner to keep.
    public DocumentUserPermissionsListController attach(final DocumentUserPermissionsList view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);
        documentTypeCache.fetch(documentTypes -> {
            this.documentTypes = documentTypes;
            addColumnsWhenReady();
        }, view.getTaskMonitor());

        // Not easy to determine if we are dealing in users or groups at this point so just
        // call it Quick Filter
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                view.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                        "Quick Filter",
                        UserFields.FILTER_FIELD_DEFINITIONS,
                        uiConfig.getHelpUrlQuickFilter()));
            }
        }, view.getTaskMonitor());

        view.setOnFilterChange(this::onFilterChange);
        view.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                UserFields.QUICK_FILTER_CONTEXT, this, view.getTaskMonitor()));

        view.setOnSortChange(this::refresh);
        return this;
    }

    private void onFilterChange(String text) {
        text = NullSafe.trim(text);
        if (text.isEmpty()) {
            text = null;
        }
        // Clear to ensure we go back to page one
        clear();
        // The filter text goes to the server verbatim - see FindUserCriteria.
        criteriaBuilder.quickFilter(text);
        refresh();
    }

    /// Fetches the permissions again; the first time, starts the list.
    public void refresh() {
        if (dataProvider == null) {
            dataProvider =
                    new RestDataProvider<DocumentUserPermissions, ResultPage<DocumentUserPermissions>>(
                            getEventBus()) {
                        @Override
                        protected void exec(final Range range,
                                            final Consumer<ResultPage<DocumentUserPermissions>> dataConsumer,
                                            final RestErrorHandler errorHandler) {
                            criteriaBuilder.pageRequest(CriteriaUtil.createPageRequest(range));
                            criteriaBuilder.sortList(CriteriaUtil.createSortList(view.getSortList()));
                            final FetchDocumentUserPermissionsRequest request = criteriaBuilder.build();
                            restFactory
                                    .create(DOC_PERMISSION_RESOURCE)
                                    .method(res -> res.fetchDocumentUserPermissions(request))
                                    .onSuccess(response -> {
                                        view.setFilterError(NullSafe.get(
                                                response.getFilterError(), TokenError::getText));
                                        dataConsumer.accept(response);
                                    })
                                    .onFailure(errorHandler)
                                    .owner(DocumentUserPermissionsListController.this)
                                    .taskMonitorFactory(view.getTaskMonitor())
                                    .exec();
                        }
                    };
            view.setDataProvider(dataProvider);
        } else {
            dataProvider.refresh();
        }
    }

    /// Empties the list and clears the selection.
    public void clear() {
        view.getSelectionModel().clear();
        if (dataProvider != null) {
            view.clearRows();
        }
    }

    /// @param docRef The document whose permissions are shown.
    public void setDocRef(final DocRef docRef) {
        this.docRef = docRef;
        criteriaBuilder.docRef(docRef);
        addColumnsWhenReady();
    }

    // The columns depend on whether the document is a folder (its create types are shown) and on
    // how many document types there are, so they are added once both are known, whichever is first
    private void addColumnsWhenReady() {
        if (!columnsAdded && documentTypes != null && docRef != null) {
            columnsAdded = true;
            view.addColumns(
                    refCells.configure(new UserRefCell.Builder<DocumentUserPermissions>()),
                    DocumentTypes.isFolder(docRef),
                    documentTypes.getTypes().size());
        }
    }

    /// The toolbar beside the list's pager, for the screen's own buttons.
    ///
    /// @return The toolbar.
    public Toolbar getToolbar() {
        return view.getToolbar();
    }

    /// @return The selection, one row at most.
    public MultiSelectionModel<DocumentUserPermissions> getSelectionModel() {
        return view.getSelectionModel();
    }

    /// @param showLevel Which permissions to show, e.g. only the explicit ones.
    public void setShowLevel(final PermissionShowLevel showLevel) {
        if (!Objects.equals(showLevel, criteriaBuilder.getShowLevel())) {
            criteriaBuilder.showLevel(showLevel);
            // We may be on page two so clear everything, so we go back to page one
            clear();
        }
    }
}
