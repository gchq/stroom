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
import stroom.util.shared.UserRef;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// The document types a user or group may create in a folder, with a button to apply them to the
/// folder's descendant folders.
public class DocumentUserCreatePermissionsEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FormGroup userFormGroup;
    @UiField
    Label document;
    @UiField
    Label user;
    @UiField
    SimplePanel createPermissions;
    @UiField
    Button applyPermissionToDescendants;

    private Consumer<TaskMonitorFactory> onApplyToDescendants = taskMonitorFactory -> {
    };

    /// Makes an empty form, with no document types until [#setDocumentTypes(IsWidget)].
    public DocumentUserCreatePermissionsEditor() {
        initWidget(BINDER.createAndBindUi(this));
        applyPermissionToDescendants.setOnClick(this::onApplyPermissionToDescendants);
    }

    /// @param docRef The folder.
    public void setDocument(final DocRef docRef) {
        document.setText(docRef.getName());
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

    /// @param documentTypes The document types to tick.
    public void setDocumentTypes(final IsWidget documentTypes) {
        createPermissions.setWidget(documentTypes.asWidget());
    }

    /// @param onApplyToDescendants Given Apply To Descendants' button, to show its progress, when
    ///                             it is pressed.
    public void setOnApplyToDescendants(final Consumer<TaskMonitorFactory> onApplyToDescendants) {
        this.onApplyToDescendants = onApplyToDescendants;
    }

    private void onApplyPermissionToDescendants() {
        onApplyToDescendants.accept(applyPermissionToDescendants);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DocumentUserCreatePermissionsEditor> {

    }
}
