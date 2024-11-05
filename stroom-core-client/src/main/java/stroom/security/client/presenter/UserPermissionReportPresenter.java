/*
 * Copyright 2017 Crown Copyright
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
import stroom.explorer.client.presenter.DocumentPermissionsListPresenter;
import stroom.explorer.shared.FindResultWithPermissions;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentPermissionFields;
import stroom.security.shared.QuickFilterExpressionParser;
import stroom.svg.client.Preset;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.dropdowntree.client.view.QuickFilterPageView;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MouseUtil;

import com.google.web.bindery.event.shared.EventBus;

import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;

public class UserPermissionReportPresenter
        extends ContentTabPresenter<QuickFilterPageView>
        implements QuickFilterUiHandlers {

    private final Provider<ExpressionPresenter> docFilterPresenterProvider;
    private final DocumentPermissionsListPresenter documentListPresenter;
    private final Provider<DocumentUserPermissionsEditPresenter> documentUserPermissionsEditPresenterProvider;
    private final Provider<BatchDocumentPermissionsEditPresenter> batchDocumentPermissionsEditPresenterProvider;
    private final ButtonView docEdit;
    private final InlineSvgToggleButton explicitOnly;
    private final ButtonView docFilter;
    private final ButtonView batchEdit;
    private ExpressionOperator filterExpression;
    private ExpressionOperator quickFilterExpression;
    private ExpressionOperator combinedExpression;
    private ResultPage<FindResultWithPermissions> docs;
    private UserRef userRef;

    @Inject
    public UserPermissionReportPresenter(final EventBus eventBus,
                                         final QuickFilterPageView view,
                                         final Provider<ExpressionPresenter> docFilterPresenterProvider,
                                         final DocumentPermissionsListPresenter documentListPresenter,
                                         final Provider<DocumentUserPermissionsEditPresenter>
                                                 documentUserPermissionsEditPresenterProvider,
                                         final Provider<BatchDocumentPermissionsEditPresenter>
                                                 batchDocumentPermissionsEditPresenterProvider) {
        super(eventBus, view);
        this.documentListPresenter = documentListPresenter;
        this.documentUserPermissionsEditPresenterProvider = documentUserPermissionsEditPresenterProvider;
        this.batchDocumentPermissionsEditPresenterProvider = batchDocumentPermissionsEditPresenterProvider;
        this.docFilterPresenterProvider = docFilterPresenterProvider;

        view.setDataView(documentListPresenter.getView());
        getView().setUiHandlers(this);

        filterExpression = ExpressionOperator.builder().op(Op.AND).build();
        quickFilterExpression = getShowAllExpression();

        docEdit = documentListPresenter.getView().addButton(new Preset(
                SvgImage.EDIT,
                "Edit Permissions For Selected Document",
                false));
        explicitOnly = new InlineSvgToggleButton();
        explicitOnly.setSvg(SvgImage.EYE_OFF);
        explicitOnly.setTitle("Only Show Documents With Explicit Permissions");
        explicitOnly.setState(false);
        documentListPresenter.getView().addButton(explicitOnly);
        documentListPresenter.getCriteriaBuilder().explicitPermission(explicitOnly.getState());
        docFilter = documentListPresenter.getView().addButton(new Preset(
                SvgImage.FILTER,
                "Filter Documents To Apply Permissions Changes On",
                true));
        batchEdit = documentListPresenter.getView().addButton(new Preset(
                SvgImage.GENERATE,
                "Batch Edit Permissions For Filtered Documents",
                false));


        documentListPresenter.setCurrentResulthandler(resultPage -> {
            docs = resultPage;
            batchEdit.setEnabled(resultPage.getPageResponse().getTotal() > 0);
        });
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(docEdit.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onEdit();
            }
        }));
        registerHandler(explicitOnly.addClickHandler(e -> {
            if (explicitOnly.getState()) {
                explicitOnly.setTitle("Show All Documents");
                explicitOnly.setSvg(SvgImage.EYE);
            } else {
                explicitOnly.setTitle("Only Show Documents With Explicit Permissions");
                explicitOnly.setSvg(SvgImage.EYE_OFF);
            }
            documentListPresenter.getCriteriaBuilder().explicitPermission(explicitOnly.getState());
            documentListPresenter.refresh();
        }));
        registerHandler(docFilter.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onFilter();
            }
        }));
        registerHandler(batchEdit.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onBatchEdit();
            }
        }));
        registerHandler(documentListPresenter.getSelectionModel().addSelectionHandler(e -> {
            final FindResultWithPermissions selected = documentListPresenter.getSelectionModel().getSelected();
            docEdit.setEnabled(selected != null);
            if (selected != null && e.getSelectionType().isDoubleSelect()) {
                onEdit();
            }
        }));
//        registerHandler(documentListPresenter.addFocusHandler(e -> {
//            getView().focus();
//        }));
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
        final FindResultWithPermissions selected = documentListPresenter.getSelectionModel().getSelected();
        if (selected != null) {
            final DocumentUserPermissionsEditPresenter presenter = documentUserPermissionsEditPresenterProvider.get();
            presenter.show(selected.getFindResult().getDocRef(), selected.getPermissions(), () -> {
                documentListPresenter.refresh();
            });
        }
    }

    private void onBatchEdit() {
        final BatchDocumentPermissionsEditPresenter presenter =
                batchDocumentPermissionsEditPresenterProvider.get();
        presenter.getUserRefSelectionBoxPresenter().setSelected(userRef);
        presenter.show(combinedExpression, GwtNullSafe.get(docs, ResultPage::getPageResponse), () ->
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
        documentListPresenter.getCriteriaBuilder().expression(combinedExpression);
        documentListPresenter.refresh();
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
        if (GwtNullSafe.isNonBlankString(text)) {
            quickFilterExpression = QuickFilterExpressionParser.parse(text,
                    Set.of(DocumentPermissionFields.DOCUMENT_NAME),
                    DocumentPermissionFields.getAllFieldMap());
        } else {
            quickFilterExpression = getShowAllExpression();
        }
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
        documentListPresenter.getCriteriaBuilder().userRef(userRef);

        // We only want to see documents tha the current user is effectively the owner of as they can't change
        // permissions on anything else.
        documentListPresenter.getCriteriaBuilder().requiredPermissions(Set.of(DocumentPermission.VIEW));
        refresh();
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.FILE_RAW;
    }

    @Override
    public String getLabel() {
        return "Document Permission Report For '" + userRef.toDisplayString() + "'";
    }

    @Override
    public String getType() {
        return "DocumentPermissionReport";
    }
}
