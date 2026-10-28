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

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerConstants;
import stroom.item.client.SelectionBox;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// A user's or group's permission on one document or folder, with, for a folder, buttons to apply
/// it to the folder's descendants and to set the document create permissions.
public class DocumentUserPermissionsEdit extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

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

    private Consumer<TaskMonitorFactory> onEditCreatePermissions = taskMonitorFactory -> {
    };
    private Consumer<TaskMonitorFactory> onApplyToDescendants = taskMonitorFactory -> {
    };

    /// Makes an empty form, offering every permission, then none.
    public DocumentUserPermissionsEdit() {
        initWidget(BINDER.createAndBindUi(this));
        editCreatePermissions.setOnClick(this::onEditCreatePermissions);
        applyPermissionToDescendants.setOnClick(this::onApplyPermissionToDescendants);
        permission.addItems(DocumentPermission.LIST);
        // Added where the list stands, so set after the permissions to come last
        permission.setNonSelectString("[ none ]");
    }

    /// Puts the focus on the permission.
    public void focus() {
        permission.focus();
    }

    /// Shows the document's name, and the folder buttons only for a folder.
    ///
    /// @param docRef The document or folder.
    public void setDocument(final DocRef docRef) {
        document.setText(docRef.getName());
        final boolean folder = ExplorerConstants.isFolderOrSystem(docRef);
        editCreatePermissions.setVisible(folder);
        applyPermissionToDescendants.setVisible(folder);
    }

    /// Shows the user's or group's name, labelled as which it is.
    ///
    /// @param userRef The user or group.
    public void setUser(final UserRef userRef) {
        userFormGroup.setLabel(userRef.isGroup()
                ? "User Group"
                : "User");
        user.setText(userRef.getDisplayName());
    }

    /// @param permission The permission held, or `null` for none.
    public void setPermission(final DocumentPermission permission) {
        this.permission.setValue(permission);
    }

    /// @return The permission chosen, or `null` for none.
    public DocumentPermission getPermission() {
        return permission.getValue();
    }

    /// @param onEditCreatePermissions Given Set Document Create Permissions' button, to show its
    ///                                progress, when it is pressed.
    public void setOnEditCreatePermissions(final Consumer<TaskMonitorFactory> onEditCreatePermissions) {
        this.onEditCreatePermissions = onEditCreatePermissions;
    }

    /// @param onApplyToDescendants Given Apply To Descendants' button, to show its progress, when
    ///                             it is pressed.
    public void setOnApplyToDescendants(final Consumer<TaskMonitorFactory> onApplyToDescendants) {
        this.onApplyToDescendants = onApplyToDescendants;
    }

    private void onEditCreatePermissions() {
        onEditCreatePermissions.accept(editCreatePermissions);
    }

    private void onApplyPermissionToDescendants() {
        onApplyToDescendants.accept(applyPermissionToDescendants);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DocumentUserPermissionsEdit> {

    }
}
