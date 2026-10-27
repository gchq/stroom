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

import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Button;
import stroom.widget.button.client.LinkButton;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FieldValidity;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.PasswordBox;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;

/// The sign in page: a user name and password, Sign In, and a link to reset a forgotten password
/// when resets are allowed.
public class Login extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String PASSWORD_HELP = "Enter your Stroom account password.";

    @UiField
    SimplePanel userImage;
    @UiField
    TextBox userName;
    @UiField
    Label userNameFeedback;
    @UiField
    FormGroup passwordGroup;
    @UiField
    PasswordBox password;
    @UiField
    Label passwordFeedback;
    @UiField
    Button signInButton;
    @UiField
    LinkButton forgotPasswordLink;

    private Runnable onSignIn = () -> {
    };
    private Runnable onForgotPassword = () -> {
    };

    /// Makes an empty page.
    public Login() {
        initWidget(BINDER.createAndBindUi(this));

        userImage.getElement().setInnerHTML(SvgImage.INFINITY_LOGO.getSvg());
        userImage.addStyleName(SvgImage.INFINITY_LOGO.getClassName());

        // The fields say what they hold, so that browsers and password managers can fill them in
        userName.getElement().setAttribute("autocomplete", "username");
        password.getInput().getElement().setAttribute("autocomplete", "current-password");

        setAllowPasswordResets(false);
    }

    /// Focuses the user name, all of it selected.
    public void focus() {
        userName.setFocus(true);
        userName.selectAll();
    }

    /// @param onSignIn What Sign In (or Enter in either field) does, once the button shows it is
    ///                 busy.
    public void setOnSignIn(final Runnable onSignIn) {
        this.onSignIn = Objects.requireNonNull(onSignIn, "onSignIn");
    }

    /// @param onForgotPassword What the 'Forgot password?' link does.
    public void setOnForgotPassword(final Runnable onForgotPassword) {
        this.onForgotPassword = Objects.requireNonNull(onForgotPassword, "onForgotPassword");
    }

    /// @return The user name entered.
    public String getUserName() {
        return userName.getValue();
    }

    /// @return The password entered.
    public String getPassword() {
        return password.getValue();
    }

    /// Checks both fields are filled in, saying under each what is missing and focusing the first.
    ///
    /// @return Whether both are.
    public boolean validate() {
        boolean valid = true;

        if (userName.getValue().length() == 0) {
            FieldValidity.setInvalid(userName, userNameFeedback, "User name is required");
            valid = false;
        } else {
            FieldValidity.setValid(userName, userNameFeedback);
        }

        if (password.getValue().length() == 0) {
            FieldValidity.setInvalid(password.getInput(), passwordFeedback, "Password is required");
            valid = false;
        } else {
            FieldValidity.setValid(password.getInput(), passwordFeedback);
        }

        if (!valid) {
            FieldValidity.focusFirstInvalid(userName, password.getInput());
        }

        return valid;
    }

    /// Makes Sign In ready to press again, once a sign in has failed.
    public void reset() {
        signInButton.setEnabled(true);
        signInButton.setLoading(false);
    }

    /// @param allowPasswordResets Whether a forgotten password can be reset by email, which shows
    ///                            the 'Forgot password?' link.
    public void setAllowPasswordResets(final boolean allowPasswordResets) {
        forgotPasswordLink.setVisible(allowPasswordResets);
        // Point to the 'Forgot password?' link only when it is shown
        passwordGroup.setHelpText(allowPasswordResets
                ? PASSWORD_HELP + " If you have forgotten it, use the 'Forgot password?' link."
                : PASSWORD_HELP + " If you have forgotten it, ask your administrator to reset it.");
    }

    @UiHandler("userName")
    void onUserNameKeyDown(final KeyDownEvent e) {
        onKeyDown(e);
    }

    @UiHandler("password")
    void onPasswordKeyDown(final KeyDownEvent e) {
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
        onSignIn.run();
    }

    @UiHandler("signInButton")
    void onSignInButton(final ClickEvent e) {
        signIn();
    }

    @UiHandler("forgotPasswordLink")
    void onForgotPasswordLink(final ClickEvent e) {
        onForgotPassword.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, Login> {

    }
}
