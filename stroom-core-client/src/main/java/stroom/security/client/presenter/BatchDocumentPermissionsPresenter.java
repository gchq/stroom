/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.data.client.presenter.ExpressionPresenter;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.document.client.event.OpenDocumentEvent.CommonDocLinkTab;
import stroom.explorer.client.presenter.DocumentListPresenter;
import stroom.explorer.client.presenter.FindDocResultListHandler;
import stroom.explorer.shared.FindResult;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentPermissionFields;
import stroom.security.shared.QuickFilterExpressionParser;
import stroom.svg.client.Preset;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.dropdowntree.client.view.QuickFilterPageView;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;
import stroom.widget.util.client.MouseUtil;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.Set;

public class BatchDocumentPermissionsPresenter
        extends ContentTabPresenter<QuickFilterPageView>
        implements QuickFilterUiHandlers {

    private final Provider<ExpressionPresenter> docFilterPresenterProvider;
    private final DocumentListPresenter documentListPresenter;
    private final Provider<BatchDocumentPermissionsEditPresenter> batchDocumentPermissionsEditPresenterProvider;
    private final ButtonView docFilter;
    private final ButtonView docEdit;
    private final ButtonView batchEdit;
    private ExpressionOperator filterExpression;
    private ExpressionOperator quickFilterExpression;
    private ExpressionOperator combinedExpression;
    private ResultPage<FindResult> docs;

    @Inject
    public BatchDocumentPermissionsPresenter(final EventBus eventBus,
                                             final QuickFilterPageView view,
                                             final Provider<ExpressionPresenter> docFilterPresenterProvider,
                                             final DocumentListPresenter documentListPresenter,
                                             final Provider<BatchDocumentPermissionsEditPresenter>
                                                     batchDocumentPermissionsEditPresenterProvider) {
        super(eventBus, view);
        this.documentListPresenter = documentListPresenter;
        this.batchDocumentPermissionsEditPresenterProvider = batchDocumentPermissionsEditPresenterProvider;
        this.docFilterPresenterProvider = docFilterPresenterProvider;

        view.setDataView(documentListPresenter.getView());
        getView().setUiHandlers(this);

        filterExpression = ExpressionOperator.builder().op(Op.AND).build();
        quickFilterExpression = ExpressionOperator.builder().op(Op.AND).build();

        // Filter
        docFilter = documentListPresenter.getView().addButton(new Preset(
                SvgImage.FILTER,
                "Filter Documents To Apply Permissions Changes On",
                true));
        docEdit = documentListPresenter.getView().addButton(new Preset(
                SvgImage.EDIT,
                "Edit Permissions For Selected Document",
                false));
        batchEdit = documentListPresenter.getView().addButton(new Preset(
                SvgImage.GENERATE,
                "Batch Edit Permissions For Filtered Documents",
                false));

        documentListPresenter.setFindResultListHandler(new FindDocResultListHandler<FindResult>() {
            @Override
            public void openDocument(final FindResult match) {
                docEdit.setEnabled(documentListPresenter.getSelected() != null);
                onEdit();
            }

            @Override
            public void focus() {

            }
        });

        documentListPresenter.setCurrentResulthandler(resultPage -> {
            docs = resultPage;
            batchEdit.setEnabled(resultPage.getPageResponse().getTotal() > 0);
        });
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(docFilter.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onFilter();
            }
        }));
//        registerHandler(documentListPresenter.getSelectionModel().addSelectionHandler(e -> {
//            docEdit.setEnabled(documentListPresenter.getSelected() != null);
//            if (e.getSelectionType().isDoubleSelect()) {
//                onEdit();
//            }
//        }));
        registerHandler(docEdit.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onEdit();
            }
        }));
        registerHandler(batchEdit.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onBatchEdit();
            }
        }));
    }

    private void onFilter() {
        final ExpressionPresenter presenter = docFilterPresenterProvider.get();
        final HidePopupRequestEvent.Handler handler = e -> {
            if (e.isOk()) {
                filterExpression = presenter.write();
                refresh();
            }
            e.hide();
        };

        presenter.read(filterExpression,
                DocumentPermissionFields.DOCUMENT_STORE_DOC_REF,
                DocumentPermissionFields.getFields());

        presenter.getWidget().getElement().addClassName("default-min-sizes");
        final PopupSize popupSize = PopupSize.resizable(800, 600);
        ShowPopupEvent.builder(presenter)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Filter Documents")
                .onShow(e -> presenter.focus())
                .onHideRequest(handler)
                .fire();
    }

    private void onEdit() {
        final FindResult selected = documentListPresenter.getSelected();
        if (selected != null) {
//            ShowDocumentPermissionsEvent.fire(this, selected.getDocRef());
            OpenDocumentEvent.builder(this, selected.getDocRef())
                    .selectedTab(CommonDocLinkTab.PERMISSIONS)
                    .fire();
        }
    }

    private void onBatchEdit() {
        final BatchDocumentPermissionsEditPresenter presenter =
                batchDocumentPermissionsEditPresenterProvider.get();
        presenter.show(combinedExpression, NullSafe.get(docs, ResultPage::getPageResponse), () ->
                documentListPresenter.refresh());
    }

    private void refresh() {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.AND);
        if (filterExpression != null) {
            builder.addOperator(filterExpression);
        }
        if (quickFilterExpression != null) {
            builder.addOperator(quickFilterExpression);
        }

        combinedExpression = builder.build();
        documentListPresenter.setExpression(combinedExpression);
        documentListPresenter.refresh();
    }

    @Override
    public void onFilterChange(final String text) {
        quickFilterExpression = QuickFilterExpressionParser.parse(text,
                Set.of(DocumentPermissionFields.DOCUMENT_NAME,
                        DocumentPermissionFields.DOCUMENT_UUID),
                DocumentPermissionFields.getAllFieldMap());
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
        documentListPresenter.setRequiredPermissions(Set.of(DocumentPermission.OWNER));
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
        return "DocumentPermissions";
    }

    public interface BatchDocumentPermissionsView extends View {

        void setDocList(View view);
    }
}
