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

package stroom.security.client.presenter;

import stroom.data.client.component.ExpressionDialogController;
import stroom.explorer.client.component.DocumentPermissionsListController;
import stroom.explorer.shared.FindResultWithPermissions;
import stroom.item.client.SelectionBox;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.security.client.component.BatchDocumentPermissionsEditController;
import stroom.security.client.component.DocumentUserPermissionsEditController;
import stroom.security.client.presenter.UserPermissionReportPresenter.UserPermissionReportView;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentPermissionFields;
import stroom.security.shared.PermissionShowLevel;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.widget.dropdowntree.client.view.QuickFilterPageView;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Set;
import javax.inject.Inject;

public class UserPermissionReportPresenter
        extends MyPresenterWidget<UserPermissionReportView>
        implements QuickFilterUiHandlers {

    private final Provider<ExpressionDialogController> filterDialogProvider;
    private final DocumentPermissionsListController documentPermissionsList;
    private final Provider<DocumentUserPermissionsEditController> documentUserPermissionsEditProvider;
    private final Provider<BatchDocumentPermissionsEditController> batchDocumentPermissionsEditProvider;
    private final SelectionBox<PermissionShowLevel> permissionVisibility;
    private ExpressionOperator filterExpression;
    private String quickFilter;
    private ExpressionOperator combinedExpression;
    private ResultPage<FindResultWithPermissions> docs;
    private UserRef userRef;

    @Inject
    public UserPermissionReportPresenter(final EventBus eventBus,
                                         final UserPermissionReportView view,
                                         final QuickFilterPageView quickFilterPageView,
                                         final Provider<ExpressionDialogController> filterDialogProvider,
                                         final DocumentPermissionsListController documentPermissionsList,
                                         final Provider<DocumentUserPermissionsEditController>
                                                 documentUserPermissionsEditProvider,
                                         final Provider<BatchDocumentPermissionsEditController>
                                                 batchDocumentPermissionsEditProvider,
                                         final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus, view);
        own(documentPermissionsList);
        this.documentPermissionsList = documentPermissionsList;
        this.documentUserPermissionsEditProvider = documentUserPermissionsEditProvider;
        this.batchDocumentPermissionsEditProvider = batchDocumentPermissionsEditProvider;
        this.filterDialogProvider = filterDialogProvider;

        view.setPermissionListView(quickFilterPageView);
        quickFilterPageView.setDataView(documentPermissionsList);
        quickFilterPageView.setUiHandlers(this);
        // A rejected filter comes back as an empty page like any other, so surface the reason on
        // the filter box. See ResultPage.filterError.
        documentPermissionsList.setOnFilterError(quickFilterPageView::setFilterError);
        quickFilterPageView.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                DocumentPermissionFields.QUICK_FILTER_CONTEXT, this, this));
        quickFilterPageView.setLabel("Documents");
        quickFilterPageView.setHelpText(new SafeHtmlBuilder()
                .appendHtmlConstant("<p>")
                .appendEscaped("Lists documents, optionally filtered based on the value of the")
                .appendHtmlConstant("<em>")
                .appendEscaped("Permission Visibility")
                .appendHtmlConstant("</em>")
                .appendEscaped("dropdown.")
                .appendHtmlConstant("</p>")
                .toSafeHtml());
        filterExpression = ExpressionOperator.builder().op(Op.AND).build();

        permissionVisibility = view.getPermissionVisibility();
        permissionVisibility.addItems(PermissionShowLevel.ITEMS);
        permissionVisibility.setValue(PermissionShowLevel.SHOW_EXPLICIT);
        documentPermissionsList.getCriteriaBuilder().showLevel(permissionVisibility.getValue());

        documentPermissionsList.setOnEdit(this::onEdit);
        documentPermissionsList.setOnFilter(this::onFilter);
        documentPermissionsList.setOnBatchEdit(this::onBatchEdit);
        documentPermissionsList.setOnResultPage(resultPage -> {
            docs = resultPage;
            documentPermissionsList.setBatchEditEnabled(resultPage.getPageResponse().getTotal() > 0);
        });

        own(permissionVisibility.addValueChangeHandler(e -> {
            documentPermissionsList.getCriteriaBuilder().showLevel(permissionVisibility.getValue());
            documentPermissionsList.refresh();
        }));
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
                    this);
        }
    }

    private void onBatchEdit() {
        final BatchDocumentPermissionsEditController batchEdit = batchDocumentPermissionsEditProvider.get();
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

    @Override
    public void onFilterChange(final String text) {
        // The filter text goes to the server verbatim - see AdvancedDocumentFindRequest. The
        // show-all term stays an expression: it is this screen's own default, not user input.
        quickFilter = text;
        documentPermissionsList.getCriteriaBuilder().quickFilter(text);
        documentPermissionsList.resetRange();
        refresh();
    }
//
//    public void show(final DocRef docRef, final Runnable onClose) {
//        if (docRef != null) {
//            final ExpressionTerm term = new ExpressionTerm(
//                    true,
//                    DocumentPermissionFields.DOCUMENT.getFldName(),
//                    Condition.IS_DOC_REF,
//                    null,
//                    docRef);
//            show(ExpressionOperator.builder().op(Op.AND).addTerm(term).build(), onClose);
//        }
//    }
//
//    public void show(final ExpressionOperator expression, final Runnable onClose) {
//        setExpression(expression);
//
//        final PopupSize popupSize = PopupSize.builder()
//                .width(Size
//                        .builder()
//                        .initial(800)
//                        .min(400)
//                        .resizable(true)
//                        .build())
//                .height(Size
//                        .builder()
//                        .initial(800)
//                        .min(400)
//                        .resizable(true)
//                        .build())
//                .build();
//        ShowPopupEvent.builder(this)
//                .popupType(PopupType.CLOSE_DIALOG)
//                .popupSize(popupSize)
//                .caption("Change Permissions")
//                .onHideRequest(e -> {
//                    onClose.run();
//                    e.hide();
//                })
//                .fire();
//    }
//
//    public void setExpression(final ExpressionOperator expression) {
//        this.filterExpression = expression;
//
//        // We only want to see documents tha the current user is effectively the owner of as they can't change
//        // permissions on anything else.
//        documentListPresenter.setRequiredPermissions(Set.of(DocumentPermission.OWNER));
//        refresh();
//    }

    public void setUserRef(final UserRef userRef) {
        this.userRef = userRef;
        documentPermissionsList.getCriteriaBuilder().userRef(userRef);

        // We only want to see documents tha the current user is effectively the owner of as they can't change
        // permissions on anything else.
        documentPermissionsList.getCriteriaBuilder().requiredPermissions(Set.of(DocumentPermission.VIEW));
        refresh();
    }

//    @Override
//    public SvgImage getIcon() {
//        return SvgImage.FILE_RAW;
//    }
//
//    @Override
//    public String getLabel() {
//        return "Document Permission Report For '" + userRef.toDisplayString() + "'";
//    }
//
//    @Override
//    public String getType() {
//        return "DocumentPermissionReport";
//    }

    // --------------------------------------------------------------------------------

    public interface UserPermissionReportView extends View {

        SelectionBox<PermissionShowLevel> getPermissionVisibility();

        void setPermissionListView(View view);
    }
}
