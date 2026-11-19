/*
 * Copyright 2026 Crown Copyright
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

package stroom.widget.popup.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The content of a dialog asking for a name (e.g. a tab session's): the name, which is required,
/// and, under it, why it isn't valid. Its markup is `NameDialog.ui.xml`.
public class NameDialog extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FormGroup nameGroup;
    @UiField
    TextBox textBox;

    /// Makes the content with no name, valid.
    public NameDialog() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The name typed.
    public String getName() {
        return textBox.getText();
    }

    /// @param name The name to start with.
    public void setName(final String name) {
        textBox.setText(name);
    }

    /// Puts the focus in the name.
    public void focus() {
        textBox.setFocus(true);
    }

    /// Marks the name invalid, showing why under it and moving the focus to it.
    ///
    /// @param message What is wrong with the name.
    public void setInvalid(final String message) {
        nameGroup.setFeedback(message);
        textBox.setFocus(true);
    }

    /// Marks the name valid, clearing any message.
    public void setValid() {
        nameGroup.clearFeedback();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, NameDialog> {

    }
}
