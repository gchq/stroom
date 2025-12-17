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

import stroom.security.identity.client.presenter.LoginPresenter.LoginView;
import stroom.security.identity.client.presenter.LoginUiHandlers;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Button;
import stroom.widget.button.client.InlineSvgButton;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Hyperlink;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class LoginViewImpl extends ViewWithUiHandlers<LoginUiHandlers> implements LoginView {

    private final Widget widget;

    @UiField
    SimplePanel userImage;
    @UiField
    TextBox userName;
    @UiField
    Label userNameFeedback;
    @UiField
    PasswordTextBox password;
    @UiField
    InlineSvgButton showPassword;
    @UiField
    Label passwordFeedback;
    @UiField
    Button signInButton;
    @UiField
    Hyperlink forgotPasswordLink;

    @Inject
    public LoginViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        userImage.getElement().setInnerHTML(SvgImage.INFINITY_LOGO.getSvg());
        userImage.addStyleName(SvgImage.INFINITY_LOGO.getClassName());

        userName.getElement().setAttribute("placeholder", "Enter User Name");

        showPassword.setSvg(SvgImage.EYE);
        showPassword.setTitle("Show Password");
        showPassword.setEnabled(true);

        password.getElement().setAttribute("placeholder", "Enter Password");

        forgotPasswordLink.setVisible(false);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        userName.setFocus(true);
        userName.selectAll();
    }

    @Override
    public String getUserName() {
        return userName.getValue();
    }

    @Override
    public String getPassword() {
        return password.getValue();
    }

    @Override
    public boolean validate() {
        boolean valid = true;

        userName.removeStyleName("invalid");
        if (userName.getValue().length() == 0) {
            userNameFeedback.setText("User name is required");
            userName.addStyleName("invalid");
            valid = false;
        } else {
            userNameFeedback.setText("");
        }

        password.removeStyleName("invalid");
        if (password.getValue().length() == 0) {
            passwordFeedback.setText("Password is required");
            password.addStyleName("invalid");
            valid = false;
        } else {
            passwordFeedback.setText("");
        }

        return valid;
    }

    @Override
    public void reset() {
        signInButton.setEnabled(true);
        signInButton.setLoading(false);
    }

    @Override
    public void setAllowPasswordResets(final boolean allowPasswordResets) {
        forgotPasswordLink.setVisible(allowPasswordResets);
    }

    @UiHandler("userName")
    public void onUserNameKeyDown(final KeyDownEvent e) {
        onKeyDown(e);
    }

    @UiHandler("password")
    public void onPasswordKeyDown(final KeyDownEvent e) {
        onKeyDown(e);
    }

    private void onKeyDown(final KeyDownEvent e) {
        if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            signIn();
        }
    }

    private void signIn() {
        signInButton.setLoading(true);
        signInButton.setEnabled(false);
        getUiHandlers().signIn();
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

    @UiHandler("signInButton")
    public void onSignInButton(final ClickEvent e) {
        signIn();
    }

    @UiHandler("forgotPasswordLink")
    public void onForgotPasswordLink(final ClickEvent e) {
        getUiHandlers().emailResetPassword();
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, LoginViewImpl> {

    }
}
