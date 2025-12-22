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

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.config.global.client.presenter.ErrorEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docstore.shared.DocumentType;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.explorer.client.presenter.DocumentTypeCache;
import stroom.explorer.shared.ExplorerResource;
import stroom.query.api.ExpressionOperator;
import stroom.security.client.presenter.BatchDocumentPermissionsEditPresenter.BatchDocumentPermissionsEditView;
import stroom.security.shared.AbstractDocumentPermissionsChange;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddAllDocumentUserCreatePermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddDocumentUserCreatePermission;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveAllDocumentUserCreatePermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveDocumentUserCreatePermission;
import stroom.security.shared.BulkDocumentPermissionChangeRequest;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentPermissionChange;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.PageResponse;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Focus;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Objects;
import javax.inject.Inject;

public class BatchDocumentPermissionsEditPresenter
        extends MyPresenterWidget<BatchDocumentPermissionsEditView> {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final DocSelectionBoxPresenter docSelectionBoxPresenter;
    private final UserRefSelectionBoxPresenter userRefSelectionBoxPresenter;
    private final RestFactory restFactory;
    private ExpressionOperator expression;
    private PageResponse currentResultPageResponse;

    @Inject
    public BatchDocumentPermissionsEditPresenter(final EventBus eventBus,
                                                 final BatchDocumentPermissionsEditView view,
                                                 final DocSelectionBoxPresenter docSelectionBoxPresenter,
                                                 final UserRefSelectionBoxPresenter userRefSelectionBoxPresenter,
                                                 final RestFactory restFactory,
                                                 final DocumentTypeCache documentTypeCache) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.docSelectionBoxPresenter = docSelectionBoxPresenter;
        this.userRefSelectionBoxPresenter = userRefSelectionBoxPresenter;

        documentTypeCache.fetch(types -> getView().setDocTypes(types.getTypes()), this);
        getView().setDocRefSelection(docSelectionBoxPresenter.getView());
        getView().setUserRefSelection(userRefSelectionBoxPresenter.getView());
    }

    public void show(final ExpressionOperator expression,
                     final PageResponse currentResultPageResponse,
                     final Runnable onClose) {
        this.expression = expression;
        this.currentResultPageResponse = currentResultPageResponse;

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
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .onShow(e -> getView().focus())
                .caption("Batch Change Permissions For All Filtered Documents")
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        apply(e, onClose);
                    } else {
                        onClose.run();
                        e.hide();
                    }
                })
                .fire();
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
            case SET_PERMISSION: {
                return new AbstractDocumentPermissionsChange.SetPermission(
                        userRefSelectionBoxPresenter.getSelected(),
                        getView().getPermission());
            }
//            case REMOVE_PERMISSION: {
//                return new AbstractDocumentPermissionsChange.RemovePermission(
//                        userRefSelectionBoxPresenter.getSelected());
//            }
            case ADD_DOCUMENT_CREATE_PERMISSION: {
                return new AddDocumentUserCreatePermission(
                        userRefSelectionBoxPresenter.getSelected(),
                        getView().getDocType().getType());
            }
            case REMOVE_DOCUMENT_CREATE_PERMISSION: {
                return new RemoveDocumentUserCreatePermission(
                        userRefSelectionBoxPresenter.getSelected(),
                        getView().getDocType().getType());
            }


            case ADD_ALL_DOCUMENT_CREATE_PERMISSIONS: {
                return new AddAllDocumentUserCreatePermissions(
                        userRefSelectionBoxPresenter.getSelected());
            }
            case REMOVE_ALL_DOCUMENT_CREATE_PERMISSIONS: {
                return new RemoveAllDocumentUserCreatePermissions(
                        userRefSelectionBoxPresenter.getSelected());
            }


            case ADD_ALL_PERMISSIONS_FROM: {
                return new AbstractDocumentPermissionsChange.AddAllPermissionsFrom(
                        docSelectionBoxPresenter.getSelectedEntityReference());
            }
            case SET_ALL_PERMISSIONS_FROM: {
                return new AbstractDocumentPermissionsChange.SetAllPermissionsFrom(
                        docSelectionBoxPresenter.getSelectedEntityReference());
            }

            case REMOVE_ALL_PERMISSIONS: {
                return new AbstractDocumentPermissionsChange.RemoveAllPermissions();
            }
        }
        throw new RuntimeException("Unexpected permission change type");
    }

    private void apply(final HidePopupRequestEvent event,
                       final Runnable onClose) {
        final long docCount;
        if (currentResultPageResponse != null &&
            currentResultPageResponse.getTotal() != null) {
            docCount = currentResultPageResponse.getTotal();
        } else {
            docCount = 0;
        }

        if (docCount == 0) {
            ErrorEvent.fire(
                    this,
                    "No documents are included in the current filter for this permission change.");
            event.reset();
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
                            doApply(this, event, onClose);
                        } else {
                            event.reset();
                        }
                    }
            );
        }
    }

    private void doApply(final TaskMonitorFactory taskMonitorFactory,
                         final HidePopupRequestEvent event,
                         final Runnable onClose) {
        final BulkDocumentPermissionChangeRequest request = createRequest();
        restFactory
                .create(EXPLORER_RESOURCE)
                .method(res -> res.changeDocumentPermissions(request))
                .onSuccess(result -> {
                    if (result) {
                        AlertEvent.fireInfo(
                                this,
                                "Successfully changed permissions.",
                                () -> {
                                    event.hide();
                                    onClose.run();
                                });
                    } else {
                        AlertEvent.fireError(
                                this,
                                "Failed to change permissions.",
                                event::reset);
                    }
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public UserRefSelectionBoxPresenter getUserRefSelectionBoxPresenter() {
        return userRefSelectionBoxPresenter;
    }

    public interface BatchDocumentPermissionsEditView
            extends View, Focus {

        DocumentPermissionChange getChange();

        void setUserRefSelection(View view);

        void setDocRefSelection(View view);

        void setDocTypes(List<DocumentType> docTypes);

        DocumentType getDocType();

        DocumentPermission getPermission();
    }
}
