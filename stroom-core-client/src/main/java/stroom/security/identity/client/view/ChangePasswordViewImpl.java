/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.popup.client.view.DialogAction;
import stroom.widget.popup.client.view.HideRequest;
import stroom.widget.popup.client.view.HideRequestUiHandlers;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class ChangePasswordViewImpl extends ViewWithUiHandlers<HideRequestUiHandlers> implements ChangePasswordView {

    private final Widget widget;
    @UiField
    Label passwordPolicyMessage;
    @UiField
    PasswordTextBox password;
    @UiField
    InlineSvgButton showPassword;
    @UiField
    Label passwordFeedback;
    @UiField
    PasswordTextBox confirmPassword;
    @UiField
    InlineSvgButton showConfirmPassword;
    @UiField
    FlowPanel strengthMeter;
    @UiField
    SimplePanel strengthMeterBar;
    @UiField
    SimplePanel passwordLengthBadge;
    @UiField
    Label confirmPasswordFeedback;

    private InternalIdpPasswordPolicyConfig passwordPolicyConfig;

    @Inject
    public ChangePasswordViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        showPassword.setSvg(SvgImage.EYE);
        showPassword.setTitle("Show Password");
        showPassword.setEnabled(true);

        showConfirmPassword.setSvg(SvgImage.EYE);
        showConfirmPassword.setTitle("Show Password");
        showConfirmPassword.setEnabled(true);

        password.getElement().setAttribute("placeholder", "Enter Password");
        confirmPassword.getElement().setAttribute("placeholder", "Confirm Password");
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

        password.removeStyleName("invalid");
        if (password.getValue().length() == 0) {
            passwordFeedback.setText("Password is required");
            password.addStyleName("invalid");
            valid = false;
        } else if (password.getValue().length() < passwordPolicyConfig.getMinimumPasswordLength()) {
            passwordFeedback.setText("Password is short");
            password.addStyleName("invalid");
            valid = false;
        } else if (!RegExp.compile(passwordPolicyConfig.getPasswordComplexityRegex()).test(password.getValue())) {
            passwordFeedback.setText("Password is invalid");
            password.addStyleName("invalid");
            valid = false;
        } else if (getPasswordStrength(password.getValue()) < passwordPolicyConfig.getMinimumPasswordStrength()) {
            passwordFeedback.setText("Password is weak");
            password.addStyleName("invalid");
            valid = false;
        } else {
            passwordFeedback.setText("");
        }

        confirmPassword.removeStyleName("invalid");
        if (confirmPassword.getValue().length() == 0) {
            confirmPasswordFeedback.setText("Password confirmation is required");
            confirmPassword.addStyleName("invalid");
            valid = false;
        } else if (!confirmPassword.getValue().equals(password.getValue())) {
            confirmPasswordFeedback.setText("Passwords must match");
            confirmPassword.addStyleName("invalid");
            valid = false;
        } else {
            confirmPasswordFeedback.setText("");
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

    @UiHandler("showPassword")
    public void onShowPassword(final ClickEvent e) {
        final String type = password.getElement().getAttribute("type");
        if (type == null || "password".equals(type)) {
            password.getElement().setAttribute("type", "text");
            showPassword.setSvg(SvgImage.EYE_OFF);
            showPassword.setTitle("Hide Password");
        } else {
            password.getElement().setAttribute("type", "password");
            showPassword.setSvg(SvgImage.EYE);
            showPassword.setTitle("Show Password");
        }
    }

    @UiHandler("showConfirmPassword")
    public void onShowConfirmPassword(final ClickEvent e) {
        final String type = confirmPassword.getElement().getAttribute("type");
        if (type == null || "password".equals(type)) {
            confirmPassword.getElement().setAttribute("type", "text");
            showConfirmPassword.setSvg(SvgImage.EYE_OFF);
            showConfirmPassword.setTitle("Hide Password");
        } else {
            confirmPassword.getElement().setAttribute("type", "password");
            showConfirmPassword.setSvg(SvgImage.EYE);
            showConfirmPassword.setTitle("Show Password");
        }
    }

    @UiHandler("password")
    public void onPassword(final ValueChangeEvent<String> e) {
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
