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

import stroom.data.client.component.FilterExpressionController;
import stroom.explorer.client.component.DocumentPermissionsListController;
import stroom.explorer.shared.FindResultWithPermissions;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentPermissionFields;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.widget.component.client.Controller;
import stroom.widget.popup.client.PopupSize;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Set;

/// A user's or group's Document Permissions tab ([UserPermissionReport]): lists the documents and
/// the user's permissions on each, filtered by a quick filter, an expression and which permissions
/// are shown, and edits the permissions on one document or on all those listed.
public class UserPermissionReportController extends Controller {

    private final Provider<FilterExpressionController> filterDialogProvider;
    private final DocumentPermissionsListController documentPermissionsList;
    private final Provider<DocumentUserPermissionsEditorController> documentUserPermissionsEditProvider;
    private final Provider<BatchDocumentPermissionsEditorController> batchDocumentPermissionsEditProvider;
    private ExpressionOperator filterExpression;
    private String quickFilter;
    private ExpressionOperator combinedExpression;
    private ResultPage<FindResultWithPermissions> docs;
    private UserRef userRef;

    /// Made by GIN, with its content and its parts.
    ///
    /// @param eventBus                             The application's event bus.
    /// @param view                                 The content.
    /// @param filterDialogProvider                 Makes the dialog that filters the documents.
    /// @param documentPermissionsList              The documents and the user's permissions on each.
    /// @param documentUserPermissionsEditProvider  Makes the dialog that edits the permissions on one
    ///                                             document.
    /// @param batchDocumentPermissionsEditProvider Makes the dialog that edits the permissions on all
    ///                                             the documents listed.
    /// @param quickFilterContextHandlerFactory     Gives the quick filter its recent filters.
    @Inject
    public UserPermissionReportController(final EventBus eventBus,
                                          final UserPermissionReport view,
                                          final Provider<FilterExpressionController> filterDialogProvider,
                                          final DocumentPermissionsListController documentPermissionsList,
                                          final Provider<DocumentUserPermissionsEditorController>
                                                  documentUserPermissionsEditProvider,
                                          final Provider<BatchDocumentPermissionsEditorController>
                                                  batchDocumentPermissionsEditProvider,
                                          final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus);
        initWidget(view);
        this.documentPermissionsList = own(documentPermissionsList).attach(view.getDocumentsList());
        this.documentUserPermissionsEditProvider = documentUserPermissionsEditProvider;
        this.batchDocumentPermissionsEditProvider = batchDocumentPermissionsEditProvider;
        this.filterDialogProvider = filterDialogProvider;

        view.setOnFilterChange(this::onFilterChange);
        // A rejected filter comes back as an empty page like any other, so surface the reason on
        // the filter box. See ResultPage.filterError.
        documentPermissionsList.setOnFilterError(view::setFilterError);
        view.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                DocumentPermissionFields.QUICK_FILTER_CONTEXT, this, taskMonitor()));
        filterExpression = ExpressionOperator.builder().op(Op.AND).build();

        documentPermissionsList.getCriteriaBuilder().showLevel(view.getPermissionShowLevel());

        documentPermissionsList.setOnEdit(this::onEdit);
        documentPermissionsList.setOnFilter(this::onFilter);
        documentPermissionsList.setOnBatchEdit(this::onBatchEdit);
        documentPermissionsList.setOnResultPage(resultPage -> {
            docs = resultPage;
            documentPermissionsList.setBatchEditEnabled(resultPage.getPageResponse().getTotal() > 0);
        });

        view.setOnPermissionShowLevelChange(showLevel -> {
            documentPermissionsList.getCriteriaBuilder().showLevel(showLevel);
            documentPermissionsList.refresh();
        });
    }

    private void onFilter() {
        filterDialogProvider.get().show(this,
                "Filter Documents",
                PopupSize.resizable(800, 600),
                filterExpression,
                DocumentPermissionFields.DOCUMENT_STORE_DOC_REF,
                DocumentPermissionFields.getFields(),
                (expression, e) -> {
                    filterExpression = expression;
                    documentPermissionsList.resetRange();
                    refresh();
                    e.hide();
                });
    }

    private void onEdit() {
        final FindResultWithPermissions selected = documentPermissionsList.getSelected();
        if (selected != null) {
            documentUserPermissionsEditProvider.get().show(this,
                    selected.getFindResult().getDocRef(),
                    selected.getPermissions(),
                    documentPermissionsList::refresh,
                    taskMonitor());
        }
    }

    private void onBatchEdit() {
        final BatchDocumentPermissionsEditorController batchEdit = batchDocumentPermissionsEditProvider.get();
        batchEdit.setUser(userRef);
        batchEdit.show(this,
                combinedExpression,
                NullSafe.get(docs, ResultPage::getPageResponse),
                documentPermissionsList::refresh);
    }

    private void refresh() {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.AND);
        if (filterExpression != null) {
            builder.addOperator(filterExpression);
        }
        if (NullSafe.isBlankString(quickFilter)) {
            // With no user filter, fall back to this screen's "show everything" term.
            builder.addOperator(getShowAllExpression());
        }

        combinedExpression = builder.build();
        documentPermissionsList.getCriteriaBuilder().expression(combinedExpression);
        documentPermissionsList.refresh();
    }

    private ExpressionOperator getShowAllExpression() {
        return ExpressionOperator
                .builder()
                .addTerm(ExpressionTerm.builder()
                        .field(DocumentPermissionFields.DOCUMENT_NAME)
                        .condition(Condition.EQUALS)
                        .value("*")
                        .build())
                .build();
    }

    private void onFilterChange(final String text) {
        // The filter text goes to the server verbatim - see AdvancedDocumentFindRequest. The
        // show-all term stays an expression: it is this screen's own default, not user input.
        quickFilter = text;
        documentPermissionsList.getCriteriaBuilder().quickFilter(text);
        documentPermissionsList.resetRange();
        refresh();
    }

    /// Lists the documents with a user's or group's permissions on them.
    ///
    /// @param userRef The user or group.
    public void setUserRef(final UserRef userRef) {
        this.userRef = userRef;
        documentPermissionsList.getCriteriaBuilder().userRef(userRef);

        // We only want to see documents tha the current user is effectively the owner of as they can't change
        // permissions on anything else.
        documentPermissionsList.getCriteriaBuilder().requiredPermissions(Set.of(DocumentPermission.VIEW));
        refresh();
    }

}
