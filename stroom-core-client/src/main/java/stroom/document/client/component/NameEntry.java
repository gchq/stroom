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

package stroom.document.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A dialog's name field (e.g. renaming a document, or a new group's name): the name, which must
/// be given, and why it isn't valid under it. The field has the focus when shown.
public class NameEntry extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FormGroup nameGroup;
    @UiField
    TextBox name;

    private Runnable onEnter = () -> {
    };

    /// Makes an empty name field.
    public NameEntry() {
        initWidget(BINDER.createAndBindUi(this));
        addAttachHandler(event -> {
            if (event.isAttached()) {
                focus();
            }
        });
        name.addKeyDownHandler(event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                onEnter.run();
            }
        });
        name.addKeyUpHandler(event -> {
            // Once a name has been typed the feedback no longer applies
            if (nameGroup.getFeedback() != null && !name.getText().trim().isEmpty()) {
                nameGroup.clearFeedback();
            }
        });
    }

    /// Puts the focus in the name.
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

    /// @param onEnter What Enter in the name does, e.g. press the dialog's OK.
    public void setOnEnter(final Runnable onEnter) {
        this.onEnter = onEnter;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, NameEntry> {

    }
}
