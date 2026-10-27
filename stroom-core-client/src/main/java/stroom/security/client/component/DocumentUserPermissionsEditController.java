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
import stroom.annotation.client.AnnotationChangeEvent;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationTag;
import stroom.docref.DocRef;
import stroom.explorer.shared.FindResult;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.client.presenter.DocPermissionRestClient;
import stroom.security.client.presenter.ExplorerClient;
import stroom.security.client.presenter.PermissionChangeClient;
import stroom.security.shared.AbstractDocumentPermissionsChange;
import stroom.security.shared.BulkDocumentPermissionChangeRequest;
import stroom.security.shared.DocumentPermissionFields;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.DocumentUserPermissionsReport;
import stroom.security.shared.SingleDocumentPermissionChangeRequest;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.stream.Collectors;

/// Sets a user's or group's permission on one document or folder ([DocumentUserPermissionsEdit]),
/// in a dialog made for one use. For a folder, it can also apply the permission to the folder's
/// descendants, and open the document create permissions.
public class DocumentUserPermissionsEditController extends Controller {

    private final DocumentUserPermissionsEdit view;
    private final DocPermissionRestClient docPermissionClient;
    private final ExplorerClient explorerClient;
    private final PermissionChangeClient permissionChangeClient;
    private final Provider<DocumentUserCreatePermissionsEditController> createPermissionsEditProvider;

    private UserRef relatedUser;
    private DocRef relatedDoc;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                      The application's event bus.
    /// @param view                          The content.
    /// @param docPermissionClient           Fetches the user's permission on the document.
    /// @param explorerClient                Finds the descendants and changes their permissions.
    /// @param permissionChangeClient        Changes the permission of a document type that handles
    ///                                      its own.
    /// @param createPermissionsEditProvider Makes the document create permissions dialog.
    @Inject
    public DocumentUserPermissionsEditController(
            final EventBus eventBus,
            final DocumentUserPermissionsEdit view,
            final DocPermissionRestClient docPermissionClient,
            final ExplorerClient explorerClient,
            final PermissionChangeClient permissionChangeClient,
            final Provider<DocumentUserCreatePermissionsEditController> createPermissionsEditProvider) {
        super(eventBus);
        this.view = view;
        this.docPermissionClient = docPermissionClient;
        this.explorerClient = explorerClient;
        this.permissionChangeClient = permissionChangeClient;
        this.createPermissionsEditProvider = createPermissionsEditProvider;
        initWidget(view);
        view.setOnEditCreatePermissions(this::onEditCreatePermissions);
        view.setOnApplyToDescendants(this::onApplyToDescendants);
    }

    /// Fetches a user's permission on a document, then shows it in a dialog, owned by `opener`
    /// until it is closed. OK saves the permission chosen.
    ///
    /// @param opener             What shows it.
    /// @param docRef             The document or folder.
    /// @param permissions        The user's permissions on it, from the list.
    /// @param onClose            Called once the permission is saved.
    /// @param taskMonitorFactory Shows the fetch as busy.
    public void show(final HasControllerNode opener,
                     final DocRef docRef,
                     final DocumentUserPermissions permissions,
                     final Runnable onClose,
                     final TaskMonitorFactory taskMonitorFactory) {
        relatedDoc = docRef;
        relatedUser = permissions.getUserRef();

        // Fetch detailed permissions report.
        docPermissionClient.getDocUserPermissionsReport(relatedDoc, relatedUser, guard(report ->
                onLoad(opener, report, onClose)), taskMonitorFactory);
    }

    private void onLoad(final HasControllerNode opener,
                        final DocumentUserPermissionsReport report,
                        final Runnable onClose) {
        view.setDocument(relatedDoc);
        view.setUser(relatedUser);
        view.setPermission(report.getExplicitPermission());
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .popupSize(PopupSize.builder().build())
                .onShow(e -> view.focus())
                .caption("Set Permissions")
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onChange(e, onClose);
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    private void onChange(final HidePopupRequestEvent event,
                          final Runnable onClose) {
        final AbstractDocumentPermissionsChange change = createChange();

        if (permissionChangeClient.handlesType(relatedDoc.getType())) {
            final SingleDocumentPermissionChangeRequest request =
                    new SingleDocumentPermissionChangeRequest(relatedDoc, change);
            permissionChangeClient.changeDocumentPermissions(request, guard(success -> {
                if (success
                    && (Annotation.TYPE.equals(relatedDoc.getType())
                        || AnnotationTag.TYPE.equals(relatedDoc.getType()))) {
                    AnnotationChangeEvent.fire(this, null);
                }
                onClose.run();
                event.hide();
            }), taskMonitor());

        } else {
            final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.OR);
            builder.addDocRefTerm(DocumentPermissionFields.DOCUMENT, Condition.IS_DOC_REF, relatedDoc);
            final BulkDocumentPermissionChangeRequest request = new BulkDocumentPermissionChangeRequest(
                    builder.build(), change);
            explorerClient.changeDocumentPermissions(request, guard(response -> {
                onClose.run();
                event.hide();
            }), taskMonitor());
        }
    }

    private void onEditCreatePermissions(final TaskMonitorFactory taskMonitorFactory) {
        createPermissionsEditProvider.get().show(this, relatedDoc, relatedUser, () -> {
        }, taskMonitorFactory);
    }

    private void onApplyToDescendants(final TaskMonitorFactory taskMonitorFactory) {
        final AbstractDocumentPermissionsChange change = createChange();

        final ExpressionOperator.Builder builder = ExpressionOperator.builder();
        builder.addTerm(ExpressionTerm.builder()
                .field(DocumentPermissionFields.DESCENDANTS)
                .condition(Condition.OF_DOC_REF)
                .docRef(relatedDoc)
                .build());
        final ExpressionOperator expression = builder.build();

        final BulkDocumentPermissionChangeRequest request = new BulkDocumentPermissionChangeRequest(
                expression, change);
        explorerClient.advancedFind(expression, guard(resultPage -> {
            final long docCount;
            if (resultPage != null
                && resultPage.getPageResponse() != null
                && resultPage.getPageResponse().getTotal() != null) {
                docCount = resultPage.getPageResponse().getTotal();
            } else {
                docCount = 0;
            }

            if (docCount == 0) {
                AlertEvent.fireError(this, "There are no descendant documents in this folder.", null);
            } else {
                final String details = getResultDetails(resultPage);
                String message = "Are you sure you want to change permissions on this document?";
                if (docCount > 1) {
                    message = "Are you sure you want to change permissions for " + docCount + " documents?";
                }
                ConfirmEvent.fire(this,
                        SafeHtmlUtils.fromString(message),
                        SafeHtmlUtils.fromString(details), ok -> {
                            if (ok) {
                                explorerClient.changeDocumentPermissions(request, guard(response -> {
                                    if (response) {
                                        AlertEvent.fireInfo(this, "Successfully changed permissions.", null);
                                    } else {
                                        AlertEvent.fireError(this, "Failed to change permissions.", null);
                                    }
                                }), taskMonitorFactory);
                            }
                        });
            }
        }), taskMonitorFactory);
    }

    private String getResultDetails(final ResultPage<FindResult> resultPage) {
        return resultPage
                .getValues()
                .stream()
                .map(fr -> fr.getPath() + " / " + fr.getDocRef().getName() + " [" + fr.getDocRef().getType() + "]")
                .collect(Collectors.joining("\n"));
    }

    private AbstractDocumentPermissionsChange createChange() {
        return new AbstractDocumentPermissionsChange.SetPermission(relatedUser, view.getPermission());
    }

    private TaskMonitorFactory taskMonitor() {
        final TaskMonitorFactory provided = getProvidedTaskMonitor();
        return provided != null ? provided : new DefaultTaskMonitorFactory(this);
    }
}
