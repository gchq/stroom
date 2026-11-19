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

package stroom.security.identity.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.form.client.FieldValidity;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.PasswordBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;

/// The user's current password, asked for before they change it.
public class CurrentPassword extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FormGroup passwordGroup;
    @UiField
    PasswordBox password;

    private Runnable onEnter = () -> {
    };

    /// Makes an empty form.
    public CurrentPassword() {
        initWidget(BINDER.createAndBindUi(this));
        password.addKeyDownHandler(this::onPasswordKeyDown);
    }

    /// Focuses the password.
    public void focus() {
        password.focus();
    }

    /// Sets what to do when Enter is pressed in the password.
    ///
    /// @param onEnter What to do, e.g. press the dialog's OK.
    public void setOnEnter(final Runnable onEnter) {
        this.onEnter = Objects.requireNonNull(onEnter, "onEnter");
    }

    /// @return The password.
    public String getPassword() {
        return password.getValue();
    }

    /// Checks a password has been given, saying under it if not and focusing it.
    ///
    /// @return Whether one has.
    public boolean validate() {
        if (password.getValue().length() == 0) {
            passwordGroup.setFeedback("Password is required");
            FieldValidity.focusFirstInvalid(password.getInput());
            return false;
        }
        passwordGroup.clearFeedback();
        return true;
    }

    private void onPasswordKeyDown(final KeyDownEvent e) {
        if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            onEnter.run();
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, CurrentPassword> {

    }
}
