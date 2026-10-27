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

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.client.component.ExpressionDialogController;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.document.client.event.OpenDocumentEvent.CommonDocLinkTab;
import stroom.explorer.client.component.DocumentListController;
import stroom.explorer.shared.FindResult;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.security.client.component.BatchDocumentPermissionsEditController;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentPermissionFields;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.widget.dropdowntree.client.view.QuickFilterPageView;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;
import stroom.widget.presenter.client.View;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Set;

public class BatchDocumentPermissionsPresenter
        extends ContentTabPresenter<QuickFilterPageView>
        implements QuickFilterUiHandlers {

    public static final String TAB_TYPE = "DocumentPermissions";

    private final Provider<ExpressionDialogController> filterDialogProvider;
    private final DocumentListController documentList;
    private final Provider<BatchDocumentPermissionsEditController> batchDocumentPermissionsEditProvider;
    private ExpressionOperator filterExpression;
    private ExpressionOperator combinedExpression;
    private ResultPage<FindResult> docs;

    @Inject
    public BatchDocumentPermissionsPresenter(final EventBus eventBus,
                                             final QuickFilterPageView view,
                                             final Provider<ExpressionDialogController> filterDialogProvider,
                                             final DocumentListController documentList,
                                             final Provider<BatchDocumentPermissionsEditController>
                                                     batchDocumentPermissionsEditProvider,
                                             final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus, view);
        own(documentList);
        getView().setAccessibleName("Filter documents");
        this.documentList = documentList;
        // A rejected filter comes back as an empty page like any other, so surface the reason on
        // the filter box. See ResultPage.filterError.
        documentList.setOnFilterError(view::setFilterError);
        view.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                DocumentPermissionFields.QUICK_FILTER_CONTEXT, this, this));
        this.batchDocumentPermissionsEditProvider = batchDocumentPermissionsEditProvider;
        this.filterDialogProvider = filterDialogProvider;

        view.setDataView(documentList);
        getView().setUiHandlers(this);

        filterExpression = ExpressionOperator.builder().op(Op.AND).build();

        documentList.setOnFilter(this::onFilter);
        documentList.setOnEdit(this::onEdit);
        documentList.setOnBatchEdit(this::onBatchEdit);
        // Edit is enabled only once a document is opened (see ISSUES.md)
        documentList.setOnOpen(match -> {
            documentList.setEditEnabled(documentList.getSelected() != null);
            onEdit();
        });

        documentList.setOnResultPage(resultPage -> {
            docs = resultPage;
            documentList.setBatchEditEnabled(resultPage.getPageResponse().getTotal() > 0);
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
                    refresh();
                    e.hide();
                });
    }

    private void onEdit() {
        final FindResult selected = documentList.getSelected();
        if (selected != null) {
            OpenDocumentEvent.builder(this, selected.getDocRef())
                    .selectedTab(CommonDocLinkTab.PERMISSIONS)
                    .fire();
        }
    }

    private void onBatchEdit() {
        batchDocumentPermissionsEditProvider.get().show(this,
                combinedExpression,
                NullSafe.get(docs, ResultPage::getPageResponse),
                documentList::refresh);
    }

    private void refresh() {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.AND);
        if (filterExpression != null) {
            builder.addOperator(filterExpression);
        }

        combinedExpression = builder.build();
        documentList.setExpression(combinedExpression);
        documentList.refresh();
    }

    @Override
    public void onFilterChange(final String text) {
        // The filter text goes to the server verbatim - see AdvancedDocumentFindRequest.
        documentList.setQuickFilter(text);
        refresh();
    }

    public void show(final DocRef docRef, final Runnable onClose) {
        if (docRef != null) {
            final ExpressionTerm term = ExpressionTerm
                    .builder()
                    .field(DocumentPermissionFields.DOCUMENT.getFldName())
                    .condition(Condition.IS_DOC_REF)
                    .docRef(docRef)
                    .build();
            show(ExpressionOperator.builder().op(Op.AND).addTerm(term).build(), onClose);
        }
    }

    public void show(final ExpressionOperator expression, final Runnable onClose) {
        setExpression(expression);

        final PopupSize popupSize = PopupSize.builder()
                .width(Size
                        .builder()
                        .initial(800)
                        .min(400)
                        .resizable(true)
                        .build())
                .height(Size
                        .builder()
                        .initial(800)
                        .min(400)
                        .resizable(true)
                        .build())
                .build();
        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .modal()
                .popupSize(popupSize)
                .caption("Change Permissions")
                .onHideRequest(e -> {
                    onClose.run();
                    e.hide();
                })
                .fire();
    }

    public void setExpression(final ExpressionOperator expression) {
        this.filterExpression = expression;

        // We only want to see documents tha the current user is effectively the owner of as they can't change
        // permissions on anything else.
        documentList.setRequiredPermissions(Set.of(DocumentPermission.OWNER));
        refresh();
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.LOCKED;
    }

    @Override
    public String getLabel() {
        return "Document Permissions";
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }

    public interface BatchDocumentPermissionsView extends View {

        void setDocList(View view);
    }
}
