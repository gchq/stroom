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

package stroom.security.identity.client.view;

import stroom.security.identity.client.presenter.ChangePasswordPresenter.ChangePasswordView;
import stroom.security.identity.shared.InternalIdpPasswordPolicyConfig;
import stroom.widget.form.client.FieldValidity;
import stroom.widget.form.client.PasswordBox;
import stroom.widget.popup.client.view.DialogAction;
import stroom.widget.popup.client.view.HideRequest;
import stroom.widget.popup.client.view.HideRequestUiHandlers;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class ChangePasswordViewImpl extends ViewWithUiHandlers<HideRequestUiHandlers> implements ChangePasswordView {

    private final Widget widget;
    @UiField
    Label passwordPolicyMessage;
    @UiField
    PasswordBox password;
    @UiField
    Label passwordFeedback;
    @UiField
    PasswordBox confirmPassword;
    @UiField
    FlowPanel strengthMeter;
    @UiField
    SimplePanel strengthMeterBar;
    @UiField
    Label confirmPasswordFeedback;

    // The new password's length, in its box, coloured by whether it meets the policy
    private final SimplePanel passwordLengthBadge = new SimplePanel();
    private InternalIdpPasswordPolicyConfig passwordPolicyConfig;

    @Inject
    public ChangePasswordViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        password.setPlaceholder("Enter Password");
        confirmPassword.setPlaceholder("Confirm Password");
        passwordLengthBadge.setStyleName("badge badge-pill badge-danger");
        password.setBadge(passwordLengthBadge);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        password.setFocus(true);
    }

    @Override
    public void setPolicy(final InternalIdpPasswordPolicyConfig passwordPolicyConfig) {
        this.passwordPolicyConfig = passwordPolicyConfig;
        passwordPolicyMessage.setText(passwordPolicyConfig.getPasswordPolicyMessage());
    }

    @Override
    public String getPassword() {
        return password.getValue();
    }

    @Override
    public String getConfirmPassword() {
        return confirmPassword.getValue();
    }

    @Override
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
    public void onPasswordKeyDown(final KeyDownEvent e) {
        onKeyDown(e);
    }

    @UiHandler("confirmPassword")
    public void onConfirmPasswordKeyDown(final KeyDownEvent e) {
        onKeyDown(e);
    }

    private void onKeyDown(final KeyDownEvent e) {
        if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            getUiHandlers().hideRequest(new HideRequest(DialogAction.OK, () -> {
            }));
        } else if (e.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
            getUiHandlers().hideRequest(new HideRequest(DialogAction.CANCEL, () -> {
            }));
        }
    }

    @UiHandler("password")
    public void onPassword(final ValueChangeEvent<String> e) {
        updateStrength();
    }

    @UiHandler("password")
    public void onPasswordKeyUp(final KeyUpEvent e) {
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
//        strengthMeter.setVisible(passwordLength > 0);
        strengthMeterBar.setStyleName("strength-meter-bar strength-meter-" + strength);

//        for (int i = 0; i < 5; i++) {
//            final SimplePanel segment = new SimplePanel();
//            if (i < passwordStrength) {
//                segment.setStyleName("segment strength-meter-" + Math.min(passwordStrength, 5));
//            } else {
//                segment.setStyleName("segment");
//            }
//            strengthMeter.add(segment);
//        }

        String text = "" + passwordLength;
        if (passwordLength > 9) {
            text = "9+";
        }
        passwordLengthBadge.getElement().setInnerText(text);
        passwordLengthBadge.setVisible(passwordLength > 0);

        // Dynamically set the password length counter class
        final String badgeColorClass = passwordLength > passwordPolicyConfig.getMinimumPasswordLength()
                ? passwordStrength >= passwordPolicyConfig.getMinimumPasswordStrength()
                ? "badge-success"
                : "badge-warning"
                : "badge-danger";
        final String badgeClass = "badge badge-pill " + badgeColorClass;
        passwordLengthBadge.setStyleName(badgeClass);
    }

    public interface Binder extends UiBinder<Widget, ChangePasswordViewImpl> {

    }
}
