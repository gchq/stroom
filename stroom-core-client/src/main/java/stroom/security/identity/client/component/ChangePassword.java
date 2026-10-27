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
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FieldValidity;
import stroom.widget.form.client.PasswordBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;

/// A new password and its confirmation, with a strength meter and a length badge that update as
/// the password is typed, checked against the password policy.
public class ChangePassword extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    // The strength meter's bar and the length badge (BEM blocks, ChangePassword.css)
    private static final String STRENGTH_BAR = "password-strength__bar";
    private static final String LENGTH_BLOCK = "password-length";
    // The strength meter's values, 0 (nothing typed) to 5, as a screen reader says them
    private static final String[] STRENGTH_NAMES = {"None", "Very weak", "Weak", "Fair", "Good", "Strong"};

    @UiField
    Label passwordPolicyMessage;
    @UiField
    PasswordBox password;
    @UiField
    Label passwordFeedback;
    @UiField
    PasswordBox confirmPassword;
    @UiField
    DivElement strengthMeter;
    @UiField
    DivElement strengthMeterBar;
    @UiField
    Label confirmPasswordFeedback;

    // The new password's length, in its box, coloured by whether it meets the policy
    private final SimplePanel passwordLengthBadge = new SimplePanel();
    private InternalIdpPasswordPolicyConfig passwordPolicyConfig;
    private Runnable onEnter = () -> {
    };

    /// Makes an empty form.
    public ChangePassword() {
        initWidget(BINDER.createAndBindUi(this));

        // The length is shown as a picture only: the strength meter says how good the password is
        passwordLengthBadge.setStyleName(LENGTH_BLOCK + " " + LENGTH_BLOCK + "--short");
        passwordLengthBadge.getElement().setAttribute("aria-hidden", "true");
        password.setBadge(passwordLengthBadge);

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
            FieldValidity.setInvalid(password.getInput(), passwordFeedback, "Password is required");
            valid = false;
        } else if (password.getValue().length() < passwordPolicyConfig.getMinimumPasswordLength()) {
            FieldValidity.setInvalid(password.getInput(), passwordFeedback, "Password is short");
            valid = false;
        } else if (getPasswordStrength(password.getValue()) < passwordPolicyConfig.getMinimumPasswordStrength()) {
            FieldValidity.setInvalid(password.getInput(), passwordFeedback, "Password is weak");
            valid = false;
        } else {
            FieldValidity.setValid(password.getInput(), passwordFeedback);
        }

        if (confirmPassword.getValue().length() == 0) {
            FieldValidity.setInvalid(confirmPassword.getInput(), confirmPasswordFeedback,
                    "Password confirmation is required");
            valid = false;
        } else if (!confirmPassword.getValue().equals(password.getValue())) {
            FieldValidity.setInvalid(confirmPassword.getInput(), confirmPasswordFeedback, "Passwords must match");
            valid = false;
        } else {
            FieldValidity.setValid(confirmPassword.getInput(), confirmPasswordFeedback);
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

    @UiHandler("password")
    void onPasswordKeyDown(final KeyDownEvent e) {
        onKeyDown(e);
    }

    @UiHandler("confirmPassword")
    void onConfirmPasswordKeyDown(final KeyDownEvent e) {
        onKeyDown(e);
    }

    private void onKeyDown(final KeyDownEvent e) {
        if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            onEnter.run();
        }
    }

    @UiHandler("password")
    void onPassword(final ValueChangeEvent<String> e) {
        updateStrength();
    }

    @UiHandler("password")
    void onPasswordKeyUp(final KeyUpEvent e) {
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
        passwordLengthBadge.setStyleName(LENGTH_BLOCK + " " + LENGTH_BLOCK + lengthModifier);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ChangePassword> {

    }
}
