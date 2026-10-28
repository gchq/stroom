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
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.FindResult;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.client.DocPermissionRestClient;
import stroom.security.client.ExplorerClient;
import stroom.security.shared.AbstractDocumentPermissionsChange;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddAllDocumentUserCreatePermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveAllDocumentUserCreatePermissions;
import stroom.security.shared.BulkDocumentPermissionChangeRequest;
import stroom.security.shared.DocumentPermissionFields;
import stroom.security.shared.DocumentUserPermissionsReport;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.Size;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Set;
import java.util.stream.Collectors;

/// Sets the document types a user or group may create in a folder
/// ([DocumentUserCreatePermissionsEdit]), in a dialog made for one use, and can apply them to the
/// folder's descendant folders.
public class DocumentUserCreatePermissionsEditController extends Controller {

    private final DocumentUserCreatePermissionsEdit view;
    private final Provider<DocumentCreatePermissionsListController> documentCreatePermissionsListProvider;
    private final DocPermissionRestClient docPermissionClient;
    private final ExplorerClient explorerClient;

    private UserRef relatedUser;
    private DocRef relatedDoc;
    private DocumentCreatePermissionsListController documentCreatePermissionsList;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                              The application's event bus.
    /// @param view                                  The content.
    /// @param documentCreatePermissionsListProvider Makes the document types' list, for a folder.
    /// @param docPermissionClient                   Fetches the user's permissions on the folder.
    /// @param explorerClient                        Finds the descendants and changes permissions.
    @Inject
    public DocumentUserCreatePermissionsEditController(
            final EventBus eventBus,
            final DocumentUserCreatePermissionsEdit view,
            final Provider<DocumentCreatePermissionsListController> documentCreatePermissionsListProvider,
            final DocPermissionRestClient docPermissionClient,
            final ExplorerClient explorerClient) {
        super(eventBus);
        this.view = view;
        this.documentCreatePermissionsListProvider = documentCreatePermissionsListProvider;
        this.docPermissionClient = docPermissionClient;
        this.explorerClient = explorerClient;
        initWidget(view);
        view.setOnApplyToDescendants(this::onApplyToDescendants);
    }

    /// Fetches a user's permissions on a folder, then shows them in a dialog, owned by `opener`
    /// until it is closed. OK saves the document types chosen.
    ///
    /// @param opener             What shows it.
    /// @param docRef             The folder.
    /// @param userRef            The user or group.
    /// @param onClose            Called once the dialog closes, by OK or Cancel.
    /// @param taskMonitorFactory Shows the fetch as busy.
    public void show(final HasControllerNode opener,
                     final DocRef docRef,
                     final UserRef userRef,
                     final Runnable onClose,
                     final TaskMonitorFactory taskMonitorFactory) {
        relatedDoc = docRef;
        relatedUser = userRef;

        // Fetch detailed permissions report; not shown if it can't be, so disposed then
        docPermissionClient.getDocUserPermissionsReport(relatedDoc, relatedUser, guard(report ->
                        onLoad(opener, report, onClose)),
                new DefaultErrorHandler(this, this::dispose),
                taskMonitorFactory);
    }

    private void onLoad(final HasControllerNode opener,
                        final DocumentUserPermissionsReport report,
                        final Runnable onClose) {
        final PopupSize popupSize;
        if (ExplorerConstants.isFolderOrSystem(relatedDoc)) {
            documentCreatePermissionsList = make(documentCreatePermissionsListProvider);
            documentCreatePermissionsList.setup(report);
            view.setDocumentTypes(documentCreatePermissionsList);
            popupSize = PopupSize.builder()
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
        } else {
            popupSize = PopupSize.builder().build();
        }

        view.setDocument(relatedDoc);
        view.setUser(relatedUser);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .popupSize(popupSize)
                .caption("Set Document Create Permissions")
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onChange(e, onClose);
                    } else {
                        onClose.run();
                        e.hide();
                    }
                })
                .fire();
    }

    private void onChange(final HidePopupRequestEvent event,
                          final Runnable onClose) {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.OR);
        builder.addDocRefTerm(DocumentPermissionFields.DOCUMENT, Condition.IS_DOC_REF, relatedDoc);
        final BulkDocumentPermissionChangeRequest request = new BulkDocumentPermissionChangeRequest(
                builder.build(), createChange());
        explorerClient.changeDocumentPermissions(request, guard(response -> {
            onClose.run();
            event.hide();
        }), taskMonitor());
    }

    private void onApplyToDescendants(final TaskMonitorFactory taskMonitorFactory) {
        final AbstractDocumentPermissionsChange change = createChange();

        final ExpressionOperator.Builder builder = ExpressionOperator.builder();
        builder.addTerm(ExpressionTerm.builder()
                .field(DocumentPermissionFields.DESCENDANTS)
                .condition(Condition.OF_DOC_REF)
                .docRef(relatedDoc)
                .build());
        builder.addTerm(ExpressionTerm.builder()
                .field(DocumentPermissionFields.DOCUMENT_TYPE)
                .condition(Condition.EQUALS)
                .value("Folder")
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
                AlertEvent.fireError(this, "There are no descendant folders of this folder.", null);
            } else {
                final String details = "Setting document create permissions for:\n"
                                       + relatedUser.getDisplayName()
                                       + "\n\nTo:\n"
                                       + getPermissionChange()
                                       + "\n\nOn:\n"
                                       + getResultDetails(resultPage);
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
        final Set<String> explicitCreatePermissions =
                documentCreatePermissionsList.getExplicitCreatePermissions();
        if (explicitCreatePermissions.contains(ExplorerConstants.ALL_CREATE_PERMISSIONS)) {
            return new AddAllDocumentUserCreatePermissions(relatedUser);
        } else if (explicitCreatePermissions.isEmpty()) {
            return new RemoveAllDocumentUserCreatePermissions(relatedUser);
        } else {
            return new AbstractDocumentPermissionsChange.SetDocumentUserCreatePermissions(
                    relatedUser,
                    explicitCreatePermissions);
        }
    }

    private String getPermissionChange() {
        final Set<String> explicitCreatePermissions =
                documentCreatePermissionsList.getExplicitCreatePermissions();
        if (explicitCreatePermissions.contains(ExplorerConstants.ALL_CREATE_PERMISSIONS)) {
            return "[ all ]";
        } else if (explicitCreatePermissions.isEmpty()) {
            return "[ none ]";
        } else {
            return explicitCreatePermissions.stream().sorted().collect(Collectors.joining("\n"));
        }
    }

}
