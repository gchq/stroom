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

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.client.component.DocSelectionController;
import stroom.explorer.client.presenter.DocumentTypeCache;
import stroom.explorer.shared.ExplorerResource;
import stroom.query.api.ExpressionOperator;
import stroom.security.shared.AbstractDocumentPermissionsChange;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddAllDocumentUserCreatePermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddDocumentUserCreatePermission;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveAllDocumentUserCreatePermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveDocumentUserCreatePermission;
import stroom.security.shared.BulkDocumentPermissionChangeRequest;
import stroom.security.shared.DocumentPermissionChange;
import stroom.util.shared.PageResponse;
import stroom.util.shared.UserRef;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

/// Changes the permissions of every document a filter matches ([BatchDocumentPermissionsEdit]), in
/// a dialog made for one use. OK asks first, saying how many documents will change.
public class BatchDocumentPermissionsEditController extends Controller {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final BatchDocumentPermissionsEdit view;
    private final DocSelectionController docSelection;
    private final UserRefSelectionController userRefSelection;
    private final RestFactory restFactory;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                 The application's event bus.
    /// @param view                     The content.
    /// @param docSelectionProvider     Makes the From Document picker.
    /// @param userRefSelectionProvider Makes the user or group picker.
    /// @param restFactory              Makes the change.
    /// @param documentTypeCache        Gives the document types.
    @Inject
    public BatchDocumentPermissionsEditController(
            final EventBus eventBus,
            final BatchDocumentPermissionsEdit view,
            final Provider<DocSelectionController> docSelectionProvider,
            final Provider<UserRefSelectionController> userRefSelectionProvider,
            final RestFactory restFactory,
            final DocumentTypeCache documentTypeCache) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        initWidget(view);
        docSelection = make(docSelectionProvider);
        docSelection.attach(view.getDocRefBox());
        userRefSelection = make(userRefSelectionProvider);
        userRefSelection.attach(view.getUserRefBox());

        documentTypeCache.fetch(guard(types -> view.setDocTypes(types.getTypes())), taskMonitor());
    }

    /// @param userRef The user or group the change is for, chosen to start with.
    public void setUser(final UserRef userRef) {
        userRefSelection.setSelected(userRef);
    }

    /// Shows the dialog, owned by `opener` until it is closed.
    ///
    /// @param opener                    What shows it.
    /// @param expression                The filter whose documents change.
    /// @param currentResultPageResponse The filter's results, saying how many documents it
    ///                                  matches.
    /// @param onClose                   Called once the change is made, or on Cancel.
    public void show(final HasControllerNode opener,
                     final ExpressionOperator expression,
                     final PageResponse currentResultPageResponse,
                     final Runnable onClose) {
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
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .popupSize(popupSize)
                .onShow(e -> view.focus())
                .caption("Batch Change Permissions For All Filtered Documents")
                .onHideRequest(e -> onHideRequest(e, expression, currentResultPageResponse, onClose))
                .fire();
    }

    // Package-private for the tests
    void onHideRequest(final HidePopupRequestEvent event,
                       final ExpressionOperator expression,
                       final PageResponse currentResultPageResponse,
                       final Runnable onClose) {
        if (event.isOk()) {
            apply(event, expression, currentResultPageResponse, onClose);
        } else {
            onClose.run();
            event.hide();
        }
    }

    private AbstractDocumentPermissionsChange createChange() {
        final DocumentPermissionChange change = view.getChange();
        Objects.requireNonNull(change, "Change is null");

        switch (change) {
            case SET_PERMISSION: {
                return new AbstractDocumentPermissionsChange.SetPermission(
                        userRefSelection.getSelected(),
                        view.getPermission());
            }
            case ADD_DOCUMENT_CREATE_PERMISSION: {
                return new AddDocumentUserCreatePermission(
                        userRefSelection.getSelected(),
                        view.getDocType().getType());
            }
            case REMOVE_DOCUMENT_CREATE_PERMISSION: {
                return new RemoveDocumentUserCreatePermission(
                        userRefSelection.getSelected(),
                        view.getDocType().getType());
            }
            case ADD_ALL_DOCUMENT_CREATE_PERMISSIONS: {
                return new AddAllDocumentUserCreatePermissions(
                        userRefSelection.getSelected());
            }
            case REMOVE_ALL_DOCUMENT_CREATE_PERMISSIONS: {
                return new RemoveAllDocumentUserCreatePermissions(
                        userRefSelection.getSelected());
            }
            case ADD_ALL_PERMISSIONS_FROM: {
                return new AbstractDocumentPermissionsChange.AddAllPermissionsFrom(
                        docSelection.getSelectedEntityReference());
            }
            case SET_ALL_PERMISSIONS_FROM: {
                return new AbstractDocumentPermissionsChange.SetAllPermissionsFrom(
                        docSelection.getSelectedEntityReference());
            }
            case REMOVE_ALL_PERMISSIONS: {
                return new AbstractDocumentPermissionsChange.RemoveAllPermissions();
            }
        }
        throw new RuntimeException("Unexpected permission change type");
    }

    private void apply(final HidePopupRequestEvent event,
                       final ExpressionOperator expression,
                       final PageResponse currentResultPageResponse,
                       final Runnable onClose) {
        final long docCount;
        if (currentResultPageResponse != null && currentResultPageResponse.getTotal() != null) {
            docCount = currentResultPageResponse.getTotal();
        } else {
            docCount = 0;
        }

        if (docCount == 0) {
            AlertEvent.fireWarn(
                    this,
                    "No documents are included in the current filter for this permission change.",
                    event::reset);
        } else {
            String message = "Are you sure you want to change permissions on this document?";
            if (docCount > 1) {
                message = "Are you sure you want to change permissions for " + docCount + " documents?";
            }
            ConfirmEvent.fire(this, message, ok -> {
                if (ok) {
                    doApply(event, expression, onClose);
                } else {
                    event.reset();
                }
            });
        }
    }

    private void doApply(final HidePopupRequestEvent event,
                         final ExpressionOperator expression,
                         final Runnable onClose) {
        final BulkDocumentPermissionChangeRequest request =
                new BulkDocumentPermissionChangeRequest(expression, createChange());
        restFactory
                .create(EXPLORER_RESOURCE)
                .method(res -> res.changeDocumentPermissions(request))
                .onSuccess(result -> {
                    if (result) {
                        AlertEvent.fireInfo(this, "Successfully changed permissions.", () -> {
                            event.hide();
                            onClose.run();
                        });
                    } else {
                        AlertEvent.fireError(this, "Failed to change permissions.", event::reset);
                    }
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

}
