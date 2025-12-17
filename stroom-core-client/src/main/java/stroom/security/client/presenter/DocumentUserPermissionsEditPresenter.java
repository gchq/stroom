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
import stroom.annotation.client.AnnotationChangeEvent;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationTag;
import stroom.docref.DocRef;
import stroom.explorer.shared.FindResult;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.client.presenter.DocumentUserPermissionsEditPresenter.DocumentUserPermissionsEditView;
import stroom.security.shared.AbstractDocumentPermissionsChange;
import stroom.security.shared.BulkDocumentPermissionChangeRequest;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentPermissionFields;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.DocumentUserPermissionsReport;
import stroom.security.shared.SingleDocumentPermissionChangeRequest;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.Focus;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;

public class DocumentUserPermissionsEditPresenter
        extends MyPresenterWidget<DocumentUserPermissionsEditView>
        implements DocumentUserPermissionsEditUiHandler {

    private final DocPermissionRestClient docPermissionClient;
    private final ExplorerClient explorerClient;
    private final PermissionChangeClient permissionChangeClient;
    private final Provider<DocumentUserCreatePermissionsEditPresenter>
            documentUserCreatePermissionsEditPresenterProvider;

    private UserRef relatedUser;
    private DocRef relatedDoc;

    @Inject
    public DocumentUserPermissionsEditPresenter(final EventBus eventBus,
                                                final DocumentUserPermissionsEditView view,
                                                final DocPermissionRestClient docPermissionClient,
                                                final ExplorerClient explorerClient,
                                                final PermissionChangeClient permissionChangeClient,
                                                final Provider<DocumentUserCreatePermissionsEditPresenter>
                                                        documentUserCreatePermissionsEditPresenterProvider) {
        super(eventBus, view);
        this.docPermissionClient = docPermissionClient;
        this.explorerClient = explorerClient;
        this.permissionChangeClient = permissionChangeClient;
        this.documentUserCreatePermissionsEditPresenterProvider = documentUserCreatePermissionsEditPresenterProvider;
        getView().setUiHandlers(this);
    }

    public void show(final DocRef docRef,
                     final DocumentUserPermissions permissions,
                     final Runnable onClose,
                     final TaskMonitorFactory taskMonitorFactory) {
        relatedDoc = docRef;
        relatedUser = permissions.getUserRef();

        // Fetch detailed permissions report.
        docPermissionClient.getDocUserPermissionsReport(relatedDoc, relatedUser, response ->
                onLoad(docRef, relatedUser, response, onClose), taskMonitorFactory);
    }

    private void onLoad(final DocRef docRef,
                        final UserRef userRef,
                        final DocumentUserPermissionsReport report,
                        final Runnable onClose) {
        getView().setDocument(docRef);
        getView().setUser(userRef);
        getView().setPermission(report.getExplicitPermission());
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.builder().build())
                .onShow(e -> getView().focus())
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
            permissionChangeClient.changeDocumentPermissions(request, success -> {

                if (success &&
                    (Annotation.TYPE.equals(relatedDoc.getType())
                     || AnnotationTag.TYPE.equals(relatedDoc.getType()))) {
                    AnnotationChangeEvent.fire(this, null);
                }
                onClose.run();
                event.hide();
            }, this);

        } else {
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
    }

    @Override
    public void onEditCreatePermissions(final TaskMonitorFactory taskMonitorFactory) {
        documentUserCreatePermissionsEditPresenterProvider.get().show(relatedDoc, relatedUser, () -> {
        }, taskMonitorFactory);
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
                        "There are no descendant documents in this folder.",
                        null);
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
        return new AbstractDocumentPermissionsChange.SetPermission(
                relatedUser,
                getView().getPermission());
    }

    public interface DocumentUserPermissionsEditView
            extends View, Focus, HasUiHandlers<DocumentUserPermissionsEditUiHandler> {

        void setDocument(DocRef docRef);

        void setUser(UserRef userRef);

        DocumentPermission getPermission();

        void setPermission(DocumentPermission permission);
    }
}
