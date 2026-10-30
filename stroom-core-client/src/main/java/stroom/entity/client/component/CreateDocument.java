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

package stroom.entity.client.component;

import stroom.explorer.client.component.DocumentPicker;
import stroom.explorer.shared.PermissionInheritance;
import stroom.item.client.SelectionBox;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A new document: the folder to make it in, its name and how its permissions are set. Enter in
/// the name submits it.
public class CreateDocument extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DocumentPicker folders;
    @UiField
    FormGroup nameGroup;
    @UiField
    TextBox name;
    @UiField
    SelectionBox<PermissionInheritance> permissionInheritance;

    private Runnable onSubmit = () -> {
    };

    /// Makes an empty form, offering None and Destination, Destination chosen. The name takes the
    /// focus once shown.
    public CreateDocument() {
        initWidget(BINDER.createAndBindUi(this));
        name.addKeyDownHandler(this::onKeyDown);
        name.addKeyUpHandler(this::onKeyUp);
        addAttachHandler(event -> focus());
        permissionInheritance.addItem(PermissionInheritance.NONE);
        permissionInheritance.addItem(PermissionInheritance.DESTINATION);
        permissionInheritance.setValue(PermissionInheritance.DESTINATION);
    }

    /// Puts the focus on the name.
    public void focus() {
        name.setFocus(true);
    }

    /// @return The name typed.
    public String getName() {
        return name.getText();
    }

    /// @param name The name to start with.
    public void setName(final String name) {
        this.name.setText(name);
    }

    /// @return The folder picker, for its controller to attach to.
    public DocumentPicker getFolders() {
        return folders;
    }

    /// @return How the new document's permissions are set.
    public PermissionInheritance getPermissionInheritance() {
        return permissionInheritance.getValue();
    }

    /// Shows why the name is invalid, under the name field, and focuses the field.
    ///
    /// @param message What is wrong with the name.
    public void setNameInvalid(final String message) {
        nameGroup.setFeedback(message);
        name.setFocus(true);
    }

    /// Clears any message about the name being invalid.
    public void setNameValid() {
        nameGroup.clearFeedback();
    }

    /// @param onSubmit Called when Enter is pressed in the name, as OK would be.
    public void setOnSubmit(final Runnable onSubmit) {
        this.onSubmit = onSubmit;
    }

    private void onKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == '\r') {
            onSubmit.run();
        }
    }

    private void onKeyUp(final KeyUpEvent event) {
        // Once a name has been typed the feedback no longer applies
        if (nameGroup.getFeedback() != null && !name.getText().trim().isEmpty()) {
            nameGroup.clearFeedback();
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, CreateDocument> {

    }
}
