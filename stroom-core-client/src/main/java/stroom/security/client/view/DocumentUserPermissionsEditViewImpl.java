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

package stroom.security.client.view;

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerConstants;
import stroom.item.client.SelectionBox;
import stroom.security.client.presenter.DocumentUserPermissionsEditPresenter.DocumentUserPermissionsEditView;
import stroom.security.client.presenter.DocumentUserPermissionsEditUiHandler;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.Button;
import stroom.widget.form.client.FormGroup;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public final class DocumentUserPermissionsEditViewImpl
        extends ViewWithUiHandlers<DocumentUserPermissionsEditUiHandler>
        implements DocumentUserPermissionsEditView {

    private static final String NONE_TITLE = "[ none ]";

    private final Widget widget;

    @UiField
    FormGroup userFormGroup;
    @UiField
    Label document;
    @UiField
    Label user;
    @UiField
    SelectionBox<DocumentPermission> permission;
    @UiField
    Button editCreatePermissions;
    @UiField
    Button applyPermissionToDescendants;

    @Inject
    public DocumentUserPermissionsEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        permission.addItems(DocumentPermission.LIST);
        permission.setNonSelectString(NONE_TITLE);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        permission.focus();
    }

    @Override
    public void setDocument(final DocRef docRef) {
        this.document.setText(docRef.getName());
        editCreatePermissions.setVisible(ExplorerConstants.isFolderOrSystem(docRef));
        applyPermissionToDescendants.setVisible(ExplorerConstants.isFolderOrSystem(docRef));
    }

    @Override
    public void setUser(final UserRef userRef) {
        if (userRef.isGroup()) {
            userFormGroup.setLabel("User Group");
        } else {
            userFormGroup.setLabel("User");
        }
        this.user.setText(userRef.getDisplayName());
    }

    @Override
    public void setPermission(final DocumentPermission permission) {
        this.permission.setValue(permission);
    }

    @Override
    public DocumentPermission getPermission() {
        return permission.getValue();
    }

    @UiHandler("editCreatePermissions")
    void onEditCreatePermissions(final ClickEvent e) {
        getUiHandlers().onEditCreatePermissions(editCreatePermissions);
    }

    @UiHandler("applyPermissionToDescendants")
    void onApplyPermissionToDescendants(final ClickEvent e) {
        getUiHandlers().onApplyToDescendants(applyPermissionToDescendants);
    }

    public interface Binder extends UiBinder<Widget, DocumentUserPermissionsEditViewImpl> {

    }
}
