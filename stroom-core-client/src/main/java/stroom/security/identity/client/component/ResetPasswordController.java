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

package stroom.security.identity.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.security.identity.shared.AuthenticationResource;
import stroom.security.identity.shared.ResetPasswordRequest;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Window;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// The password reset page ([ResetPassword]), served for the link in a password reset email: asks
/// for a new password with the token the link carries, then goes to the sign in page.
public class ResetPasswordController extends SignInPageController {

    private static final AuthenticationResource RESOURCE = GWT.create(AuthenticationResource.class);

    // Where every exit from this page goes: success, cancel and the invalid-link alert.
    //
    // The error=login_required parameter is required, not decoration. App.onModuleLoad reveals the
    // sign in form for this path only when it sees that value, and reveals the Authentication Error
    // page for anything else, including no parameter at all. The server adds it on the redirects it
    // drives (AuthenticationServiceImpl.createSignInUri), so a navigation from the client has to add
    // it itself.
    private static final String SIGN_IN_URL = "/signIn?error=login_required";
    private static final String INVALID_LINK_MESSAGE =
            "This password reset link is invalid or has expired. Please request a new one.";

    private final RestFactory restFactory;
    private final Provider<ChangePasswordController> changePasswordProvider;

    /// Made by GIN, with its page.
    ///
    /// @param eventBus               The application's event bus.
    /// @param view                   The page.
    /// @param restFactory            Resets the password.
    /// @param changePasswordProvider Makes the dialog that asks for the new password.
    @Inject
    public ResetPasswordController(final EventBus eventBus,
                                   final ResetPassword view,
                                   final RestFactory restFactory,
                                   final Provider<ChangePasswordController> changePasswordProvider) {
        super(eventBus);
        this.restFactory = restFactory;
        this.changePasswordProvider = changePasswordProvider;
        initWidget(view);
    }

    @Override
    public void onShown() {
        hideLoading();
        promptForNewPassword();
    }

    private void promptForNewPassword() {
        final String token = Window.Location.getParameter("token");
        if (NullSafe.isBlankString(token)) {
            AlertEvent.fireError(this, INVALID_LINK_MESSAGE, this::gotoSignIn);
            return;
        }

        // Reuse the normal change password form so that the password policy, strength meter and
        // validation are identical to changing a password from within the application.
        changePasswordProvider.get().show(this, "Reset Password",
                (password, confirmPassword, event) -> resetPassword(token, password, confirmPassword, event),
                this::gotoSignIn);
    }

    private void resetPassword(final String token,
                               final String password,
                               final String confirmPassword,
                               final HidePopupRequestEvent event) {
        final ResetPasswordRequest request = new ResetPasswordRequest(token, password, confirmPassword);

        restFactory
                .create(RESOURCE)
                .method(res -> res.resetPassword(request))
                .onSuccess(result -> {
                    if (result.isChangeSucceeded()) {
                        event.hide();
                        AlertEvent.fireInfo(this,
                                "Your password has been reset. Please sign in with your new password.",
                                this::gotoSignIn);
                    } else {
                        AlertEvent.fireError(this, result.getMessage(), event::reset);
                    }
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .owner(this)
                .taskMonitorFactory(new DefaultTaskMonitorFactory(this))
                .exec();
    }

    private void gotoSignIn() {
        Window.Location.replace(SIGN_IN_URL);
    }
}
