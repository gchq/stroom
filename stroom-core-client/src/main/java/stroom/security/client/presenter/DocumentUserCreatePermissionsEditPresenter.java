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
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.FindResult;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.client.presenter.DocumentUserCreatePermissionsEditPresenter.DocumentUserCreatePermissionsEditView;
import stroom.security.shared.AbstractDocumentPermissionsChange;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddAllDocumentUserCreatePermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveAllDocumentUserCreatePermissions;
import stroom.security.shared.BulkDocumentPermissionChangeRequest;
import stroom.security.shared.DocumentPermissionFields;
import stroom.security.shared.DocumentUserPermissionsReport;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;

import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.Focus;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;

public class DocumentUserCreatePermissionsEditPresenter
        extends MyPresenterWidget<DocumentUserCreatePermissionsEditView>
        implements DocumentUserCreatePermissionsEditUiHandler {

    private final Provider<DocumentCreatePermissionsListPresenter>
            documentCreatePermissionsListPresenterProvider;
    private final DocPermissionRestClient docPermissionClient;
    private final ExplorerClient explorerClient;

    private UserRef relatedUser;
    private DocRef relatedDoc;
    private DocumentCreatePermissionsListPresenter documentCreatePermissionsListPresenter;

    @Inject
    public DocumentUserCreatePermissionsEditPresenter(final EventBus eventBus,
                                                      final DocumentUserCreatePermissionsEditView view,
                                                      final Provider<DocumentCreatePermissionsListPresenter>
                                                              documentCreatePermissionsListPresenterProvider,
                                                      final DocPermissionRestClient docPermissionClient,
                                                      final ExplorerClient explorerClient) {
        super(eventBus, view);
        this.documentCreatePermissionsListPresenterProvider = documentCreatePermissionsListPresenterProvider;
        this.docPermissionClient = docPermissionClient;
        this.explorerClient = explorerClient;
        getView().setUiHandlers(this);
    }

    public void show(final DocRef docRef,
                     final UserRef userRef,
                     final Runnable onClose,
                     final TaskMonitorFactory taskMonitorFactory) {
        relatedDoc = docRef;
        relatedUser = userRef;

        // Fetch detailed permissions report.
        docPermissionClient.getDocUserPermissionsReport(relatedDoc, relatedUser, response ->
                onLoad(docRef, relatedUser, response, onClose), taskMonitorFactory);
    }

    private void onLoad(final DocRef docRef,
                        final UserRef userRef,
                        final DocumentUserPermissionsReport report,
                        final Runnable onClose) {
        final PopupSize popupSize;
        if (ExplorerConstants.isFolderOrSystem(relatedDoc)) {
            documentCreatePermissionsListPresenter = documentCreatePermissionsListPresenterProvider.get();
            documentCreatePermissionsListPresenter.setup(report);
            getView().setDocumentTypeView(documentCreatePermissionsListPresenter.getView());
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

        getView().setDocument(docRef);
        getView().setUser(userRef);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .onShow(e -> getView().focus())
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
        final AbstractDocumentPermissionsChange change = createChange();

        final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.OR);
        builder.addDocRefTerm(DocumentPermissionFields.DOCUMENT, Condition.IS_DOC_REF, relatedDoc);
        final ExpressionOperator expression = builder.build();

        final BulkDocumentPermissionChangeRequest request = new BulkDocumentPermissionChangeRequest(
                expression, change);
        explorerClient.changeDocumentPermissions(request, response -> {
            onClose.run();
            event.hide();
        }, this);
    }

    @Override
    public void onApplyToDescendants(final TaskMonitorFactory taskMonitorFactory) {
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
        explorerClient.advancedFind(builder.build(), resultPage -> {
            final long docCount;
            if (resultPage != null &&
                resultPage.getPageResponse() != null &&
                resultPage.getPageResponse().getTotal() != null) {
                docCount = resultPage.getPageResponse().getTotal();
            } else {
                docCount = 0;
            }

            if (docCount == 0) {
                AlertEvent.fireError(
                        this,
                        "There are no descendant folders of this folder.",
                        null);
            } else {
                final String details = "Setting document create permissions for:\n" +
                                       relatedUser.getDisplayName() +
                                       "\n\nTo:\n" +
                                       getPermissionChange() +
                                       "\n\nOn:\n" +
                                       getResultDetails(resultPage);
                String message = "Are you sure you want to change permissions on this document?";
                if (docCount > 1) {
                    message = "Are you sure you want to change permissions for " + docCount + " documents?";
                }
                ConfirmEvent.fire(this,
                        SafeHtmlUtils.fromString(message),
                        SafeHtmlUtils.fromString(details), ok -> {
                            if (ok) {
                                explorerClient.changeDocumentPermissions(request, response -> {
                                    if (response) {
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
                                }, taskMonitorFactory);
                            }
                        });
            }
        }, taskMonitorFactory);

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
                documentCreatePermissionsListPresenter.getExplicitCreatePermissions();
        if (explicitCreatePermissions.contains(ExplorerConstants.ALL_CREATE_PERMISSIONS)) {
            return new AddAllDocumentUserCreatePermissions(relatedUser);
        } else if (explicitCreatePermissions.size() == 0) {
            return new RemoveAllDocumentUserCreatePermissions(relatedUser);
        } else {
            return new AbstractDocumentPermissionsChange.SetDocumentUserCreatePermissions(
                    relatedUser,
                    explicitCreatePermissions);
        }
    }

    private String getPermissionChange() {
        final Set<String> explicitCreatePermissions =
                documentCreatePermissionsListPresenter.getExplicitCreatePermissions();
        if (explicitCreatePermissions.contains(ExplorerConstants.ALL_CREATE_PERMISSIONS)) {
            return "[ all ]";
        } else if (explicitCreatePermissions.size() == 0) {
            return "[ none ]";
        } else {
            return explicitCreatePermissions.stream().sorted().collect(Collectors.joining("\n"));
        }
    }

    public interface DocumentUserCreatePermissionsEditView extends View, Focus,
            HasUiHandlers<DocumentUserCreatePermissionsEditUiHandler> {

        void setDocument(DocRef docRef);

        void setUser(UserRef userRef);

        void setDocumentTypeView(View view);
    }
}
