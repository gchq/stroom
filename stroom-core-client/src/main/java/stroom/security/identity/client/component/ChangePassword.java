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

import stroom.security.identity.shared.InternalIdpPasswordPolicyConfig;
import stroom.widget.component.client.AriaPanel;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FieldValidity;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.PasswordBox;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;

/// A new password and its confirmation, with a strength meter and a length badge that update as
/// the password is typed, checked against the password policy.
public class ChangePassword extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    // The strength meter's bar and the length badge (change-password.css)
    private static final String STRENGTH_BAR = "change-password__strength-bar";
    private static final String LENGTH_BADGE = "change-password__length";
    // The strength meter's values, 0 (nothing typed) to 5, as a screen reader says them
    private static final String[] STRENGTH_NAMES = {"None", "Very weak", "Weak", "Fair", "Good", "Strong"};

    @UiField
    Label passwordPolicyMessage;
    @UiField
    FormGroup passwordGroup;
    @UiField
    PasswordBox password;
    @UiField
    FormGroup confirmPasswordGroup;
    @UiField
    PasswordBox confirmPassword;
    @UiField
    DivElement strengthMeter;
    @UiField
    DivElement strengthMeterBar;
    // The new password's length, in its box, coloured by whether it meets the policy
    @UiField
    AriaPanel passwordLengthBadge;

    private InternalIdpPasswordPolicyConfig passwordPolicyConfig;
    private Runnable onEnter = () -> {
    };

    /// Makes an empty form.
    public ChangePassword() {
        initWidget(BINDER.createAndBindUi(this));
        password.addKeyDownHandler(this::onPasswordKeyDown);
        confirmPassword.addKeyDownHandler(this::onConfirmPasswordKeyDown);
        password.addValueChangeHandler(this::onPassword);
        password.addKeyUpHandler(this::onPasswordKeyUp);

        // The policy describes the password field, so that it is read with it
        final Element policy = passwordPolicyMessage.getElement();
        policy.setId(DOM.createUniqueId());
        FieldValidity.addDescribedBy(password.getInput().getElement(), policy.getId(), false);
    }

    /// Focuses the new password.
    public void focus() {
        password.focus();
    }

    /// Sets the policy the password is checked against, and shows its message.
    ///
    /// @param passwordPolicyConfig The policy.
    public void setPolicy(final InternalIdpPasswordPolicyConfig passwordPolicyConfig) {
        this.passwordPolicyConfig = passwordPolicyConfig;
        passwordPolicyMessage.setText(passwordPolicyConfig.getPasswordPolicyMessage());
    }

    /// Sets what to do when Enter is pressed in either password.
    ///
    /// @param onEnter What to do, e.g. press the dialog's OK.
    public void setOnEnter(final Runnable onEnter) {
        this.onEnter = Objects.requireNonNull(onEnter, "onEnter");
    }

    /// @return The new password.
    public String getPassword() {
        return password.getValue();
    }

    /// @return The new password typed again.
    public String getConfirmPassword() {
        return confirmPassword.getValue();
    }

    /// Checks the password against the policy and its confirmation, saying under each what is
    /// wrong and focusing the first that is.
    ///
    /// @return Whether both are valid.
    public boolean validate() {
        boolean valid = true;

        if (password.getValue().length() == 0) {
            passwordGroup.setFeedback("Password is required");
            valid = false;
        } else if (password.getValue().length() < passwordPolicyConfig.getMinimumPasswordLength()) {
            passwordGroup.setFeedback("Password is short");
            valid = false;
        } else if (getPasswordStrength(password.getValue()) < passwordPolicyConfig.getMinimumPasswordStrength()) {
            passwordGroup.setFeedback("Password is weak");
            valid = false;
        } else {
            passwordGroup.clearFeedback();
        }

        if (confirmPassword.getValue().length() == 0) {
            confirmPasswordGroup.setFeedback("Password confirmation is required");
            valid = false;
        } else if (!confirmPassword.getValue().equals(password.getValue())) {
            confirmPasswordGroup.setFeedback("Passwords must match");
            valid = false;
        } else {
            confirmPasswordGroup.clearFeedback();
        }

        if (!valid) {
            FieldValidity.focusFirstInvalid(password.getInput(), confirmPassword.getInput());
        }

        return valid;
    }

    private native int getPasswordStrength(final String text) /*-{
        var result = $wnd.zxcvbn(text);
        return result.score;
    }-*/;

    private void onPasswordKeyDown(final KeyDownEvent e) {
        onKeyDown(e);
    }

    private void onConfirmPasswordKeyDown(final KeyDownEvent e) {
        onKeyDown(e);
    }

    private void onKeyDown(final KeyDownEvent e) {
        if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            onEnter.run();
        }
    }

    private void onPassword(final ValueChangeEvent<String> e) {
        updateStrength();
    }

    private void onPasswordKeyUp(final KeyUpEvent e) {
        // Shows the password's strength and length as it is typed
        updateStrength();
    }

    private void updateStrength() {
        final int passwordLength = password.getValue().length();
        final int passwordStrength = getPasswordStrength(password.getText());

        int strength = 0;
        if (passwordLength > 0) {
            strength = Math.min(passwordStrength + 1, 5);
        }
        strengthMeterBar.setClassName(STRENGTH_BAR + " " + STRENGTH_BAR + "--" + strength);
        strengthMeter.setAttribute("aria-valuenow", String.valueOf(strength));
        strengthMeter.setAttribute("aria-valuetext", STRENGTH_NAMES[strength]);

        String text = "" + passwordLength;
        if (passwordLength > 9) {
            text = "9+";
        }
        passwordLengthBadge.getElement().setInnerText(text);
        passwordLengthBadge.setVisible(passwordLength > 0);

        // The length badge's colour: too short, long enough but weak, or both met
        final String lengthModifier = passwordLength > passwordPolicyConfig.getMinimumPasswordLength()
                ? passwordStrength >= passwordPolicyConfig.getMinimumPasswordStrength()
                ? "--ok"
                : "--weak"
                : "--short";
        passwordLengthBadge.setStyleName(LENGTH_BADGE + " " + LENGTH_BADGE + lengthModifier);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ChangePassword> {

    }
}
