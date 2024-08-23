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

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerConstants;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.client.presenter.DocumentUserPermissionsEditPresenter.DocumentUserPermissionsEditView;
import stroom.security.shared.AbstractDocumentPermissionsChange;
import stroom.security.shared.BulkDocumentPermissionChangeRequest;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentPermissionFields;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.DocumentUserPermissionsReport;
import stroom.util.shared.UserRef;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.Focus;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;

public class DocumentUserPermissionsEditPresenter
        extends MyPresenterWidget<DocumentUserPermissionsEditView>
        implements ChangeUiHandlers {

    private final Provider<DocumentCreatePermissionsListPresenter>
            documentCreatePermissionsListPresenterProvider;
    private final DocPermissionRestClient docPermissionClient;
    private final ExplorerClient explorerClient;

    private DocumentUserPermissionsReport currentPermissions;
    private UserRef relatedUser;
    private DocRef relatedDoc;
    private DocumentCreatePermissionsListPresenter documentCreatePermissionsListPresenter;

    @Inject
    public DocumentUserPermissionsEditPresenter(final EventBus eventBus,
                                                final DocumentUserPermissionsEditView view,
                                                final Provider<DocumentCreatePermissionsListPresenter>
                                                        documentCreatePermissionsListPresenterProvider,
                                                final DocPermissionRestClient docPermissionClient,
                                                final ExplorerClient explorerClient) {
        super(eventBus, view);
        this.documentCreatePermissionsListPresenterProvider = documentCreatePermissionsListPresenterProvider;
        this.docPermissionClient = docPermissionClient;
        this.explorerClient = explorerClient;
        view.setUiHandlers(this);
        docPermissionClient.setTaskListener(this);
        explorerClient.setTaskListener(this);
    }

    public void show(final DocRef docRef,
                     final DocumentUserPermissions permissions,
                     final Runnable onClose) {
        relatedDoc = docRef;
        relatedUser = permissions.getUserRef();

        // Fetch detailed permissions report.
        docPermissionClient.getDocUserPermissionsReport(relatedDoc, relatedUser, response -> onLoad(response, onClose));
    }

    private void onLoad(final DocumentUserPermissionsReport permissions,
                        final Runnable onClose) {
        this.currentPermissions = permissions;

        final PopupSize popupSize;
        if (ExplorerConstants.isFolderOrSystem(relatedDoc)) {
            documentCreatePermissionsListPresenter = documentCreatePermissionsListPresenterProvider.get();
            documentCreatePermissionsListPresenter.setDocPermissionClient(docPermissionClient);
            documentCreatePermissionsListPresenter.setExplorerClient(explorerClient);
            documentCreatePermissionsListPresenter.setup(relatedUser, relatedDoc, permissions);
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

        getView().setPermission(permissions.getExplicitPermission());
        updateDetails();

        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(popupSize)
                .onShow(e -> getView().focus())
                .caption("Change Permissions On '" +
                        relatedDoc.getDisplayValue() +
                        "' For '" +
                        relatedUser.toDisplayString() +
                        "'")
                .onHideRequest(e -> {
                    onClose.run();
                    e.hide();
                })
                .fire();
    }

    private void refresh() {
        // Fetch detailed permissions report.
        docPermissionClient.getDocUserPermissionsReport(relatedDoc, relatedUser, response -> onRefresh(response));
    }

    private void onRefresh(final DocumentUserPermissionsReport permissions) {
        this.currentPermissions = permissions;
        getView().setPermission(permissions.getExplicitPermission());
        updateDetails();

        if (documentCreatePermissionsListPresenter != null) {
            documentCreatePermissionsListPresenter.setup(relatedUser, relatedDoc, permissions);
        }
    }

    private void updateDetails() {
        final SafeHtml details = getDetails();
        getView().setDetails(details);
    }

    private SafeHtml getDetails() {
        DocumentPermission maxPermission = null;

        DescriptionBuilder sb = new DescriptionBuilder();
        if (currentPermissions.getExplicitPermission() != null) {
            sb.addTitle("Explicit Permission: " + currentPermissions.getExplicitPermission().getDisplayValue());
            maxPermission = currentPermissions.getExplicitPermission();
        }

        if (currentPermissions.getInheritedPermissionPaths() != null &&
                currentPermissions.getInheritedPermissionPaths().size() > 0) {
            sb.addNewLine();
            sb.addNewLine();
            sb.addTitle("Inherited Permissions:");
            for (int i = DocumentPermission.LIST.size() - 1; i >= 0; i--) {
                final DocumentPermission permission = DocumentPermission.LIST.get(i);
                final List<String> paths = currentPermissions.getInheritedPermissionPaths().get(permission);
                if (paths != null) {
                    if (maxPermission == null || permission.isHigher(maxPermission)) {
                        maxPermission = permission;
                    }

                    for (final String path : paths) {
                        sb.addNewLine();
                        sb.addLine(permission.getDisplayValue());
                        sb.addLine(": ");
                        sb.addLine(path);
                    }
                }
            }

            final DescriptionBuilder sb2 = new DescriptionBuilder();
            sb2.addTitle("Effective Permission: " + maxPermission.getDisplayValue());
            sb2.addNewLine();
            sb2.addNewLine();
            sb2.append(sb.toSafeHtml());
            sb = sb2;
        }

        if (maxPermission == null) {
            sb.addTitle("No Permission");
        }
        return sb.toSafeHtml();
    }

    @Override
    public void onChange() {
        if (relatedUser != null) {
            final DocumentPermission permission = getView().getPermission();

            final AbstractDocumentPermissionsChange change = new AbstractDocumentPermissionsChange.SetPermission(
                    relatedUser,
                    getView().getPermission());

            final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.OR);
            builder.addDocRefTerm(DocumentPermissionFields.DOCUMENT, Condition.IS_DOC_REF, relatedDoc);
            if (documentCreatePermissionsListPresenter != null &&
                    documentCreatePermissionsListPresenter.isIncludeDescendants()) {
                builder.addTerm(ExpressionTerm.builder()
                        .field(DocumentPermissionFields.DESCENDANTS)
                        .condition(Condition.OF_DOC_REF)
                        .docRef(relatedDoc)
                        .build());
            }
            final ExpressionOperator expression = builder.build();

            final BulkDocumentPermissionChangeRequest request = new BulkDocumentPermissionChangeRequest(expression,
                    change);
            explorerClient.changeDocumentPermssions(request, response -> refresh());
        }
    }

    public interface DocumentUserPermissionsEditView extends View, Focus, HasUiHandlers<ChangeUiHandlers> {

        DocumentPermission getPermission();

        void setPermission(DocumentPermission permission);

        void setDocumentTypeView(View view);

        void setDetails(SafeHtml details);
    }
}
