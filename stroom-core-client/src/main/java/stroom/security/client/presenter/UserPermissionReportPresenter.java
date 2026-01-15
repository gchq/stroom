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

import stroom.data.client.presenter.ExpressionPresenter;
import stroom.explorer.client.presenter.DocumentPermissionsListPresenter;
import stroom.explorer.shared.FindResultWithPermissions;
import stroom.item.client.SelectionBox;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.client.presenter.UserPermissionReportPresenter.UserPermissionReportView;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentPermissionFields;
import stroom.security.shared.PermissionShowLevel;
import stroom.security.shared.QuickFilterExpressionParser;
import stroom.svg.client.Preset;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.ButtonView;
import stroom.widget.dropdowntree.client.view.QuickFilterPageView;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;

public class UserPermissionReportPresenter
        extends MyPresenterWidget<UserPermissionReportView>
        implements QuickFilterUiHandlers {

    private final Provider<ExpressionPresenter> docFilterPresenterProvider;
    private final DocumentPermissionsListPresenter documentPermissionsListPresenter;
    private final Provider<DocumentUserPermissionsEditPresenter> documentUserPermissionsEditPresenterProvider;
    private final Provider<BatchDocumentPermissionsEditPresenter> batchDocumentPermissionsEditPresenterProvider;
    private final SelectionBox<PermissionShowLevel> permissionVisibility;
    private final ButtonView docEdit;
    private final ButtonView docFilter;
    private final ButtonView batchEdit;
    private ExpressionOperator filterExpression;
    private ExpressionOperator quickFilterExpression;
    private ExpressionOperator combinedExpression;
    private ResultPage<FindResultWithPermissions> docs;
    private UserRef userRef;

    @Inject
    public UserPermissionReportPresenter(final EventBus eventBus,
                                         final UserPermissionReportView view,
                                         final QuickFilterPageView quickFilterPageView,
                                         final Provider<ExpressionPresenter> docFilterPresenterProvider,
                                         final DocumentPermissionsListPresenter documentPermissionsListPresenter,
                                         final Provider<DocumentUserPermissionsEditPresenter>
                                                 documentUserPermissionsEditPresenterProvider,
                                         final Provider<BatchDocumentPermissionsEditPresenter>
                                                 batchDocumentPermissionsEditPresenterProvider) {
        super(eventBus, view);
        this.documentPermissionsListPresenter = documentPermissionsListPresenter;
        this.documentUserPermissionsEditPresenterProvider = documentUserPermissionsEditPresenterProvider;
        this.batchDocumentPermissionsEditPresenterProvider = batchDocumentPermissionsEditPresenterProvider;
        this.docFilterPresenterProvider = docFilterPresenterProvider;

        view.setPermissionListView(quickFilterPageView);
        quickFilterPageView.setDataView(documentPermissionsListPresenter.getView());
        quickFilterPageView.setUiHandlers(this);
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
        quickFilterExpression = getShowAllExpression();

        permissionVisibility = view.getPermissionVisibility();
        permissionVisibility.addItems(PermissionShowLevel.ITEMS);
        permissionVisibility.setValue(PermissionShowLevel.SHOW_EXPLICIT);
        documentPermissionsListPresenter.getCriteriaBuilder().showLevel(permissionVisibility.getValue());

        docEdit = documentPermissionsListPresenter.getView().addButton(new Preset(
                SvgImage.EDIT,
                "Edit Permissions For Selected Document",
                false));
        docFilter = documentPermissionsListPresenter.getView().addButton(new Preset(
                SvgImage.FILTER,
                "Filter Documents To Apply Permissions Changes On",
                true));
        batchEdit = documentPermissionsListPresenter.getView().addButton(new Preset(
                SvgImage.GENERATE,
                "Batch Edit Permissions For Filtered Documents",
                false));


        documentPermissionsListPresenter.setCurrentResultHandler(resultPage -> {
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
        registerHandler(permissionVisibility.addValueChangeHandler(e -> {
            documentPermissionsListPresenter.getCriteriaBuilder().showLevel(permissionVisibility.getValue());
            documentPermissionsListPresenter.refresh();
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
        registerHandler(documentPermissionsListPresenter.getSelectionModel().addSelectionHandler(e -> {
            final FindResultWithPermissions selected = documentPermissionsListPresenter.getSelectionModel()
                    .getSelected();
            docEdit.setEnabled(selected != null);
            if (selected != null && e.getSelectionType().isDoubleSelect()) {
                onEdit();
            }
        }));
    }

    private void onFilter() {
        final ExpressionPresenter presenter = docFilterPresenterProvider.get();
        final HidePopupRequestEvent.Handler handler = e -> {
            if (e.isOk()) {
                filterExpression = presenter.write();
                documentPermissionsListPresenter.resetRange();
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
        final FindResultWithPermissions selected = documentPermissionsListPresenter.getSelectionModel().getSelected();
        if (selected != null) {
            final DocumentUserPermissionsEditPresenter presenter = documentUserPermissionsEditPresenterProvider.get();
            presenter.show(selected.getFindResult().getDocRef(),
                    selected.getPermissions(),
                    documentPermissionsListPresenter::refresh,
                    this);
        }
    }

    private void onBatchEdit() {
        final BatchDocumentPermissionsEditPresenter presenter =
                batchDocumentPermissionsEditPresenterProvider.get();
        presenter.getUserRefSelectionBoxPresenter().setSelected(userRef);
        presenter.show(combinedExpression,
                NullSafe.get(docs, ResultPage::getPageResponse),
                documentPermissionsListPresenter::refresh);
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
        documentPermissionsListPresenter.getCriteriaBuilder().expression(combinedExpression);
        documentPermissionsListPresenter.refresh();
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
        if (NullSafe.isNonBlankString(text)) {
            quickFilterExpression = QuickFilterExpressionParser.parse(text,
                    Set.of(DocumentPermissionFields.DOCUMENT_NAME),
                    DocumentPermissionFields.getAllFieldMap());
        } else {
            quickFilterExpression = getShowAllExpression();
        }
        documentPermissionsListPresenter.resetRange();
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
        documentPermissionsListPresenter.getCriteriaBuilder().userRef(userRef);

        // We only want to see documents tha the current user is effectively the owner of as they can't change
        // permissions on anything else.
        documentPermissionsListPresenter.getCriteriaBuilder().requiredPermissions(Set.of(DocumentPermission.VIEW));
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
