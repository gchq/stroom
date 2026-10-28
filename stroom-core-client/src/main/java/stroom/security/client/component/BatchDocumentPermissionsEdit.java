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

import stroom.docstore.shared.DocumentType;
import stroom.item.client.SelectionBox;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentPermissionChange;
import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.FormGroup;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// The content of the batch permissions dialog: a permission change for every document a filter
/// matches, showing only the fields the change chosen needs.
public class BatchDocumentPermissionsEdit extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SelectionBox<DocumentPermissionChange> documentPermissionChange;
    @UiField
    FormGroup userRefLabel;
    @UiField
    FormGroup docRefLabel;
    @UiField
    FormGroup permissionLabel;
    @UiField
    FormGroup docTypeLabel;
    @UiField
    DropDownBox userRef;
    @UiField
    DropDownBox docRef;
    @UiField
    SelectionBox<DocumentPermission> permission;
    @UiField
    SelectionBox<DocumentType> docType;

    /// Makes the content, with Set Permission chosen.
    public BatchDocumentPermissionsEdit() {
        initWidget(BINDER.createAndBindUi(this));
        documentPermissionChange.addValueChangeHandler(this::onDocumentPermissionChange);
        permission.addItems(DocumentPermission.LIST);
        // Added where the list stands, so set after the permissions to come last
        permission.setNonSelectString("[ none ]");
        documentPermissionChange.addItems(DocumentPermissionChange.LIST);
        documentPermissionChange.setValue(DocumentPermissionChange.SET_PERMISSION);
        update();
    }

    /// Puts the focus on the change.
    public void focus() {
        documentPermissionChange.focus();
    }

    /// @return The change chosen.
    public DocumentPermissionChange getChange() {
        return documentPermissionChange.getValue();
    }

    /// @return The user or group picker, for its controller to attach to.
    public DropDownBox getUserRefBox() {
        return userRef;
    }

    /// @return The From Document picker, for its controller to attach to.
    public DropDownBox getDocRefBox() {
        return docRef;
    }

    /// @param docTypes The document types whose create permission can be changed.
    public void setDocTypes(final List<DocumentType> docTypes) {
        docType.addItems(docTypes);
    }

    /// @return The document type chosen.
    public DocumentType getDocType() {
        return docType.getValue();
    }

    /// @return The permission chosen, or `null` for none.
    public DocumentPermission getPermission() {
        return permission.getValue();
    }

    private void onDocumentPermissionChange(final ValueChangeEvent<DocumentPermissionChange> e) {
        update();
    }

    // Shows only the fields the change chosen needs
    private void update() {
        final DocumentPermissionChange change = documentPermissionChange.getValue();
        userRefLabel.setVisible(needsUser(change));
        docRefLabel.setVisible(change == DocumentPermissionChange.ADD_ALL_PERMISSIONS_FROM
                               || change == DocumentPermissionChange.SET_ALL_PERMISSIONS_FROM);
        permissionLabel.setVisible(change == DocumentPermissionChange.SET_PERMISSION);
        docTypeLabel.setVisible(change == DocumentPermissionChange.ADD_DOCUMENT_CREATE_PERMISSION
                                || change == DocumentPermissionChange.REMOVE_DOCUMENT_CREATE_PERMISSION);
    }

    private static boolean needsUser(final DocumentPermissionChange change) {
        return switch (change) {
            case SET_PERMISSION,
                 ADD_DOCUMENT_CREATE_PERMISSION,
                 REMOVE_DOCUMENT_CREATE_PERMISSION,
                 ADD_ALL_DOCUMENT_CREATE_PERMISSIONS,
                 REMOVE_ALL_DOCUMENT_CREATE_PERMISSIONS -> true;
            default -> false;
        };
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, BatchDocumentPermissionsEdit> {

    }
}
