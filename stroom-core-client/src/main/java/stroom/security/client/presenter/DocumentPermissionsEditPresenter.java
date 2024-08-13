/*
 * Copyright 2016 Crown Copyright
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

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.config.global.client.presenter.ErrorEvent;
import stroom.data.client.presenter.ExpressionPresenter;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.explorer.client.presenter.DocumentListPresenter;
import stroom.explorer.client.presenter.DocumentTypeCache;
import stroom.explorer.client.presenter.FindDocResultListHandler;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.FindResult;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.client.presenter.DocumentPermissionsEditPresenter.DocumentPermissionsEditView;
import stroom.security.shared.AbstractDocumentPermissionsChange;
import stroom.security.shared.BulkDocumentPermissionChangeRequest;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentPermissionChange;
import stroom.security.shared.DocumentPermissionFields;
import stroom.svg.client.Preset;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskListener;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Focus;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;

public class DocumentPermissionsEditPresenter
        extends MyPresenterWidget<DocumentPermissionsEditView>
        implements DocumentPermissionsEditUiHandlers {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final Provider<ExpressionPresenter> docFilterPresenterProvider;
    private final DocSelectionBoxPresenter docSelectionBoxPresenter;
    private final UserRefSelectionBoxPresenter userRefSelectionBoxPresenter;
    private final DocumentListPresenter documentListPresenter;
    private final Provider<DocumentCreatePermissionsListPresenter> documentCreatePermissionsListPresenterProvider;
    private final Provider<DocumentPermissionsPresenter> documentPermissionsPresenterProvider;
    private final RestFactory restFactory;
    private final ButtonView docFilter;
    private final ButtonView docView;
    private ExpressionOperator expression;

    @Inject
    public DocumentPermissionsEditPresenter(final EventBus eventBus,
                                            final DocumentPermissionsEditView view,
                                            final Provider<ExpressionPresenter> docFilterPresenterProvider,
                                            final DocumentListPresenter documentListPresenter,
                                            final Provider<DocumentCreatePermissionsListPresenter>
                                                    documentCreatePermissionsListPresenterProvider,
                                            final Provider<DocumentPermissionsPresenter>
                                                    documentPermissionsPresenterProvider,
                                            final DocSelectionBoxPresenter docSelectionBoxPresenter,
                                            final UserRefSelectionBoxPresenter userRefSelectionBoxPresenter,
                                            final RestFactory restFactory,
                                            final DocumentTypeCache documentTypeCache) {
        super(eventBus, view);
        this.documentListPresenter = documentListPresenter;
        this.restFactory = restFactory;
        this.docSelectionBoxPresenter = docSelectionBoxPresenter;
        this.userRefSelectionBoxPresenter = userRefSelectionBoxPresenter;
        this.documentCreatePermissionsListPresenterProvider = documentCreatePermissionsListPresenterProvider;
        this.documentPermissionsPresenterProvider = documentPermissionsPresenterProvider;
        this.docFilterPresenterProvider = docFilterPresenterProvider;

        view.setDocList(documentListPresenter.getView());
        view.setUiHandlers(this);

        // Filter
        docFilter = documentListPresenter.getView().addButton(new Preset(
                SvgImage.FILTER,
                "Filter Documents To Apply Permissions Changes On",
                true));
        docView = documentListPresenter.getView().addButton(new Preset(
                SvgImage.EYE,
                "View Permissions For Selected Document",
                false));
        expression = ExpressionOperator.builder().op(Op.AND).build();

        documentTypeCache.fetch(types -> {
            getView().setDocTypes(types.getTypes());
        }, this);
        getView().setDocRefSelection(docSelectionBoxPresenter.getView());
        getView().setUserRefSelection(userRefSelectionBoxPresenter.getView());

        documentListPresenter.setFindResultListHandler(new FindDocResultListHandler() {
            @Override
            public void openDocument(final FindResult match) {
                final FindResult selected = documentListPresenter.getSelected();
                if (match != null) {
                    documentPermissionsPresenterProvider.get().show(match.getDocRef());
                }
                docView.setEnabled(match != null);
            }

            @Override
            public void focus() {

            }
        });
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(docFilter.addClickHandler(event -> {
            final ExpressionPresenter presenter = docFilterPresenterProvider.get();
            final HidePopupRequestEvent.Handler handler = e -> {
                if (e.isOk()) {
                    expression = presenter.write();

//                    expressionValidator.validateExpression(
//                            MetaPresenter.this,
//                            MetaFields.getAllFields(),
//                            expression, expression2 -> {
//                                if (!expression2.equals(getCriteria().getExpression())) {
//                                    setExpression(expression2);
//                                    e.hide();
//                                } else {
//                                    // Nothing changed!
//                                    e.hide();
//                                }
//                            }, this);

                    documentListPresenter.setExpression(expression);
                    documentListPresenter.refresh();
                }
                e.hide();
            };

            presenter.read(expression,
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
        }));

        registerHandler(documentListPresenter.getSelectionModel().addSelectionHandler(e -> {
            final FindResult selected = documentListPresenter.getSelected();
//            if (selected != null) {
//                if (e.getSelectionType().isDoubleSelect()) {
//                    documentPermissionsPresenterProvider.get().show(selected.getDocRef());
//                }
//            }
            docView.setEnabled(selected != null);
        }));
        registerHandler(docView.addClickHandler(e -> {
            final FindResult selected = documentListPresenter.getSelected();
            if (selected != null) {
                documentPermissionsPresenterProvider.get().show(selected.getDocRef());
            }
        }));
    }

    public void show(final DocRef docRef, final Runnable onClose) {
        if (docRef != null) {
            docSelectionBoxPresenter.setSelectedEntityReference(docRef);
            final ExpressionTerm term = new ExpressionTerm(
                    true,
                    DocumentPermissionFields.DOCUMENT.getFldName(),
                    Condition.IS_DOC_REF,
                    null,
                    docRef);
            show(ExpressionOperator.builder().op(Op.AND).addTerm(term).build(), onClose);
        }
    }

    public void show(final ExpressionOperator expression, final Runnable onClose) {
        this.expression = expression;

        // We only want to see documents tha the current user is effectively the owner of as they can't change
        // permissions on anything else.
        documentListPresenter.setRequiredPermissions(Set.of(DocumentPermission.OWNER));

//        final ExplorerTreeFilterBuilder explorerTreeFilterBuilder =
//                findDocResultListPresenter.getExplorerTreeFilterBuilder();
//        // Don't want favourites in the recent items as they are effectively duplicates
//        explorerTreeFilterBuilder.setIncludedRootTypes(ExplorerConstants.SYSTEM);
//        explorerTreeFilterBuilder.setNameFilter("test", true);
        documentListPresenter.setExpression(expression);
        documentListPresenter.refresh();


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
                .onShow(e -> getView().focus())
                .caption("Change Permissions")
                .onHideRequest(e -> {
                    onClose.run();
                    e.hide();
                })
                .fire();
    }

    @Override
    public void validate() {
        final ResultPage<FindResult> docs = documentListPresenter.getCurrentResults();
        int docCount = 0;
        if (docs != null) {
            docCount = docs.getPageResponse().getLength();
        }

        if (docCount > 0) {
            try {
                final BulkDocumentPermissionChangeRequest request = createRequest();
                // No error so valid.
                getView().setApplyEnabled(true);
            } catch (final Exception e) {
                getView().setApplyEnabled(false);
            }
        } else {
            getView().setApplyEnabled(false);
        }
    }

    private BulkDocumentPermissionChangeRequest createRequest() {
        final DocumentPermissionChange change = getView().getChange();
        Objects.requireNonNull(change, "Change is null");

        return new BulkDocumentPermissionChangeRequest(expression, createChange());
    }

    private AbstractDocumentPermissionsChange createChange() {
        final DocumentPermissionChange change = getView().getChange();
        Objects.requireNonNull(change, "Change is null");

        switch (change) {
            case SET_PERMSSION: {
                return new AbstractDocumentPermissionsChange.SetPermission(
                        userRefSelectionBoxPresenter.getSelected(),
                        getView().getPermission());
            }
            case REMOVE_PERMISSION: {
                return new AbstractDocumentPermissionsChange.RemovePermission(
                        userRefSelectionBoxPresenter.getSelected(),
                        getView().getPermission());
            }


            case ADD_DOCUMENT_CREATE_PERMSSION: {
                return new AbstractDocumentPermissionsChange.AddDocumentCreatePermission(
                        userRefSelectionBoxPresenter.getSelected(),
                        getView().getDocType());
            }
            case REMOVE_DOCUMENT_CREATE_PERMSSION: {
                return new AbstractDocumentPermissionsChange.RemoveDocumentCreatePermission(
                        userRefSelectionBoxPresenter.getSelected(),
                        getView().getDocType());
            }


            case ADD_ALL_DOCUMENT_CREATE_PERMSSIONS: {
                return new AbstractDocumentPermissionsChange.AddAllDocumentCreatePermissions(
                        userRefSelectionBoxPresenter.getSelected());
            }
            case REMOVE_ALL_DOCUMENT_CREATE_PERMSSIONS: {
                return new AbstractDocumentPermissionsChange.RemoveAllDocumentCreatePermissions(
                        userRefSelectionBoxPresenter.getSelected());
            }


            case ADD_ALL_PERMSSIONS_FROM: {
                return new AbstractDocumentPermissionsChange.AddAllPermissionsFrom(
                        docSelectionBoxPresenter.getSelectedEntityReference());
            }
            case SET_ALL_PERMSSIONS_FROM: {
                return new AbstractDocumentPermissionsChange.SetAllPermissionsFrom(
                        docSelectionBoxPresenter.getSelectedEntityReference());
            }

            case REMOVE_ALL_PERMISSIONS: {
                return new AbstractDocumentPermissionsChange.RemoveAllPermissions();
            }
        }
        throw new RuntimeException("Unexpected permission change type");
    }

    @Override
    public void apply(final TaskListener taskListener) {
        final ResultPage<FindResult> docs = documentListPresenter.getCurrentResults();
        int docCount = 0;
        if (docs != null) {
            docCount = docs.getPageResponse().getLength();
        }

        if (docCount == 0) {
            ErrorEvent.fire(
                    this,
                    "No documents are included in the current filter for this permission change.");
        } else {
            String message = "Are you sure you want to change permissions on this document?";
            if (docCount > 1) {
                message = "Are you sure you want to change permissions for " + docCount + " documents?";
            }
            ConfirmEvent.fire(
                    this,
                    message,
                    ok -> {
                        if (ok) {
                            doApply(taskListener);
                        }
                    }
            );
        }
    }

    private void doApply(final TaskListener taskListener) {
        final BulkDocumentPermissionChangeRequest request = createRequest();
        restFactory
                .create(EXPLORER_RESOURCE)
                .method(res -> res.changeDocumentPermssions(request))
                .onSuccess(result -> {
                    if (result) {
                        AlertEvent.fireInfo(
                                this,
                                "Successfully changed permissions.",
                                null);
                    } else {
                        AlertEvent.fireError(
                                this,
                                "Failed to change permissions.",
                                null);
                    }
                })
                .taskListener(taskListener)
                .exec();
    }

    public interface DocumentPermissionsEditView extends View, Focus, HasUiHandlers<DocumentPermissionsEditUiHandlers> {

        void setDocList(View view);

        DocumentPermissionChange getChange();

        void setUserRefSelection(View view);

        void setDocRefSelection(View view);

        void setDocTypes(List<DocumentType> docTypes);

        DocumentType getDocType();

        DocumentPermission getPermission();

        void setApplyEnabled(boolean enabled);
    }
}
