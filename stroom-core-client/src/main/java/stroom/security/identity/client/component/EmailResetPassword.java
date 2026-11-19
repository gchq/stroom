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
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;

/// The email address to send a password reset link to.
public class EmailResetPassword extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FormGroup emailGroup;
    @UiField
    TextBox email;

    private Runnable onEnter = () -> {
    };

    /// Makes an empty form.
    public EmailResetPassword() {
        initWidget(BINDER.createAndBindUi(this));
        email.addKeyDownHandler(this::onEmailKeyDown);
    }

    /// Focuses the email address.
    public void focus() {
        email.setFocus(true);
    }

    /// @param onEnter What Enter in the email address does, e.g. press the dialog's OK.
    public void setOnEnter(final Runnable onEnter) {
        this.onEnter = Objects.requireNonNull(onEnter, "onEnter");
    }

    /// @return The email address entered.
    public String getEmail() {
        return email.getValue();
    }

    /// Checks an email address has been given and looks like one, saying under it if not and
    /// focusing it.
    ///
    /// @return Whether it is valid.
    public boolean validate() {
        final boolean valid;
        if (email.getValue().length() == 0) {
            emailGroup.setFeedback("Email is required");
            valid = false;
        } else if (!EmailValidator.validate(email.getValue())) {
            emailGroup.setFeedback("Invalid email address");
            valid = false;
        } else {
            emailGroup.clearFeedback();
            valid = true;
        }

        if (!valid) {
            FieldValidity.focusFirstInvalid(email);
        }
        return valid;
    }

    private void onEmailKeyDown(final KeyDownEvent e) {
        if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            onEnter.run();
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, EmailResetPassword> {

    }
}
