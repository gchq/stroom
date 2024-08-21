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
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.explorer.client.presenter.DocumentTypeCache;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.FindResult;
import stroom.query.api.v2.ExpressionOperator;
import stroom.security.client.presenter.BatchDocumentPermissionsEditPresenter.BatchDocumentPermissionsEditView;
import stroom.security.shared.AbstractDocumentPermissionsChange;
import stroom.security.shared.BulkDocumentPermissionChangeRequest;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentPermissionChange;
import stroom.task.client.TaskListener;
import stroom.util.shared.ResultPage;
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
import javax.inject.Inject;
import javax.inject.Provider;

public class BatchDocumentPermissionsEditPresenter
        extends MyPresenterWidget<BatchDocumentPermissionsEditView>
        implements BatchDocumentPermissionsEditUiHandlers {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final DocSelectionBoxPresenter docSelectionBoxPresenter;
    private final UserRefSelectionBoxPresenter userRefSelectionBoxPresenter;
    private final RestFactory restFactory;
    private ExpressionOperator expression;
    private ResultPage<FindResult> docs;

    @Inject
    public BatchDocumentPermissionsEditPresenter(final EventBus eventBus,
                                                 final BatchDocumentPermissionsEditView view,
                                                 final Provider<ExpressionPresenter> docFilterPresenterProvider,
                                                 final DocSelectionBoxPresenter docSelectionBoxPresenter,
                                                 final UserRefSelectionBoxPresenter userRefSelectionBoxPresenter,
                                                 final RestFactory restFactory,
                                                 final DocumentTypeCache documentTypeCache) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.docSelectionBoxPresenter = docSelectionBoxPresenter;
        this.userRefSelectionBoxPresenter = userRefSelectionBoxPresenter;

        view.setUiHandlers(this);

        documentTypeCache.fetch(types -> {
            getView().setDocTypes(types.getTypes());
        }, this);
        getView().setDocRefSelection(docSelectionBoxPresenter.getView());
        getView().setUserRefSelection(userRefSelectionBoxPresenter.getView());
    }

    public void show(final ExpressionOperator expression,
                     final ResultPage<FindResult> docs,
                     final Runnable onClose) {
        this.expression = expression;
        this.docs = docs;

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
                .caption("Batch Change Permissions For All Filtered Documents")
                .onHideRequest(e -> {
                    onClose.run();
                    e.hide();
                })
                .fire();
    }

    @Override
    public void validate() {
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
//            case REMOVE_PERMISSION: {
//                return new AbstractDocumentPermissionsChange.RemovePermission(
//                        userRefSelectionBoxPresenter.getSelected(),
//                        getView().getPermission());
//            }


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

    public interface BatchDocumentPermissionsEditView
            extends View, Focus, HasUiHandlers<BatchDocumentPermissionsEditUiHandlers> {

        DocumentPermissionChange getChange();

        void setUserRefSelection(View view);

        void setDocRefSelection(View view);

        void setDocTypes(List<DocumentType> docTypes);

        DocumentType getDocType();

        DocumentPermission getPermission();

        void setApplyEnabled(boolean enabled);
    }
}
