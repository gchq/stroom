/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.security.client.presenter;

import stroom.docref.DocRef;
import stroom.item.client.SelectionBox;
import stroom.security.client.presenter.DocumentUserPermissionsPresenter.DocumentUserPermissionsView;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.DocumentUserPermissionsReport;
import stroom.security.shared.PermissionShowLevel;
import stroom.svg.client.Preset;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;

public class DocumentUserPermissionsPresenter
        extends MyPresenterWidget<DocumentUserPermissionsView> {

    private final DocumentUserPermissionsListPresenter documentUserPermissionsListPresenter;
    private final Provider<DocumentUserPermissionsEditPresenter> documentUserPermissionsEditPresenterProvider;
    private final DocPermissionRestClient docPermissionClient;
    private final ButtonView docEdit;
    private final SelectionBox<PermissionShowLevel> showLevel = new SelectionBox<>();
    private DocRef docRef;

    @Inject
    public DocumentUserPermissionsPresenter(
            final EventBus eventBus,
            final DocPermissionRestClient docPermissionClient,
            final DocumentUserPermissionsView view,
            final DocumentUserPermissionsListPresenter documentUserPermissionsListPresenter,
            final Provider<DocumentUserPermissionsEditPresenter> documentUserPermissionsEditPresenterProvider) {

        super(eventBus, view);
        this.documentUserPermissionsListPresenter = documentUserPermissionsListPresenter;
        this.documentUserPermissionsEditPresenterProvider = documentUserPermissionsEditPresenterProvider;
        this.docPermissionClient = docPermissionClient;
        view.setDocUserPermissionListView(documentUserPermissionsListPresenter.getView());

        docEdit = documentUserPermissionsListPresenter.addButton(new Preset(
                SvgImage.EDIT,
                "Edit Permissions For Selected User",
                false));

        showLevel.addItems(PermissionShowLevel.ITEMS);
        showLevel.setValue(PermissionShowLevel.SHOW_EXPLICIT);
        documentUserPermissionsListPresenter.getPagerView().addToolbarWidget(showLevel);
        documentUserPermissionsListPresenter.setShowLevel(showLevel.getValue());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(documentUserPermissionsListPresenter.getSelectionModel().addSelectionHandler(e -> {
            final DocumentUserPermissions selected =
                    documentUserPermissionsListPresenter.getSelectionModel().getSelected();
            updateDetails();
            docEdit.setEnabled(selected != null);
            if (e.getSelectionType().isDoubleSelect()) {
                onEdit();
            }
        }));
        registerHandler(docEdit.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onEdit();
            }
        }));
        registerHandler(showLevel.addValueChangeHandler(e -> {
            documentUserPermissionsListPresenter.setShowLevel(showLevel.getValue());
            documentUserPermissionsListPresenter.refresh();
        }));
    }

    private void onEdit() {
        final DocumentUserPermissions selected =
                documentUserPermissionsListPresenter.getSelectionModel().getSelected();
        if (selected != null) {
            documentUserPermissionsEditPresenterProvider.get().show(
                    docRef, selected, documentUserPermissionsListPresenter::refresh, this);
        }
    }

    public void show(final DocRef docRef) {
        this.docRef = docRef;
        documentUserPermissionsListPresenter.setDocRef(docRef);
        documentUserPermissionsListPresenter.refresh();

        final PopupSize popupSize = PopupSize.builder()
                .width(Size
                        .builder()
                        .initial(1000)
                        .min(1000)
                        .resizable(true)
                        .build())
                .height(Size
                        .builder()
                        .initial(800)
                        .min(800)
                        .resizable(true)
                        .build())
                .build();

        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(popupSize)
                .caption("Permissions For '" + docRef.getDisplayValue() + "'")
                .modal()
                .fire();
    }

    private void updateDetails() {
        final DocumentUserPermissions selection = documentUserPermissionsListPresenter
                .getSelectionModel()
                .getSelected();
        // Fetch detailed permissions report.
        if (selection != null) {
            docPermissionClient.getDocUserPermissionsReport(docRef, selection.getUserRef(), response -> {
                final SafeHtml details = getDetails(response);
                getView().setUserRef(selection.getUserRef());
                getView().setDetails(details);
            }, this);
        } else {
            getView().setUserRef(null);
            getView().setDetails(SafeHtmlUtils.EMPTY_SAFE_HTML);
        }
    }

    private SafeHtml getDetails(final DocumentUserPermissionsReport permissions) {
        DocumentPermission maxPermission = null;

        DescriptionBuilder sb = new DescriptionBuilder();
        if (permissions.getExplicitPermission() != null) {
            sb.addTitle("Explicit Permission: " + permissions.getExplicitPermission().getDisplayValue());
            maxPermission = permissions.getExplicitPermission();
        }

        if (permissions.getInheritedPermissionPaths() != null &&
            permissions.getInheritedPermissionPaths().size() > 0) {
            sb.addNewLine();
            sb.addNewLine();
            sb.addTitle("Inherited Permissions:");
            for (int i = DocumentPermission.LIST.size() - 1; i >= 0; i--) {
                final DocumentPermission permission = DocumentPermission.LIST.get(i);
                final List<String> paths = permissions.getInheritedPermissionPaths().get(permission);
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


    // --------------------------------------------------------------------------------


    public interface DocumentUserPermissionsView extends View {

        void setDocUserPermissionListView(View view);

        void setDetails(SafeHtml details);

        void setUserRef(UserRef userRef);
    }
}
