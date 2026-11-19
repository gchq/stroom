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

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.security.identity.shared.AuthenticationResource;
import stroom.security.identity.shared.ChangePasswordRequest;
import stroom.security.identity.shared.LoginRequest;
import stroom.util.client.RedirectUrlUtil;
import stroom.widget.task.client.DefaultTaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Window;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// The sign in page ([Login]): signs the user in, asks for a new password when the server
/// requires one, and goes back to where the user was going.
public class LoginController extends AbstractSignInPageController {

    private static final AuthenticationResource RESOURCE = GWT.create(AuthenticationResource.class);

    private final Login view;
    private final RestFactory restFactory;
    private final Provider<ChangePasswordController> changePasswordProvider;
    private final Provider<EmailResetPasswordController> emailResetPasswordProvider;

    /// Made by GIN, with its page.
    ///
    /// @param eventBus                   The application's event bus.
    /// @param view                       The page.
    /// @param restFactory                Signs in, and changes the password.
    /// @param changePasswordProvider     Makes the dialog that asks for a new password.
    /// @param emailResetPasswordProvider Makes the dialog that emails a password reset link.
    @Inject
    public LoginController(final EventBus eventBus,
                           final Login view,
                           final RestFactory restFactory,
                           final Provider<ChangePasswordController> changePasswordProvider,
                           final Provider<EmailResetPasswordController> emailResetPasswordProvider) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.changePasswordProvider = changePasswordProvider;
        this.emailResetPasswordProvider = emailResetPasswordProvider;
        initWidget(view);
        view.setOnSignIn(this::signIn);
        view.setOnForgotPassword(() -> emailResetPasswordProvider.get().show(this));

        restFactory
                .create(RESOURCE)
                .method(AuthenticationResource::fetchPasswordPolicy)
                .onSuccess(result -> view.setAllowPasswordResets(result.isAllowPasswordResets()))
                .onFailure(new DefaultErrorHandler(this, null))
                .owner(this)
                .taskMonitorFactory(new DefaultTaskMonitorFactory(this))
                .exec();
    }

    private void signIn() {
        if (view.validate()) {
            final LoginRequest request = new LoginRequest(view.getUserName(), view.getPassword());
            restFactory
                    .create(RESOURCE)
                    .method(res -> res.login(request))
                    .onSuccess(result -> {
                        if (result.isRequirePasswordChange()) {
                            changePassword();
                        } else if (result.isLoginSuccessful()) {
                            afterLogin();
                        } else {
                            AlertEvent.fireError(this, result.getMessage(), view::reset);
                        }
                    })
                    .onFailure(new DefaultErrorHandler(this, view::reset))
                    .owner(this)
                    .taskMonitorFactory(new DefaultTaskMonitorFactory(this))
                    .exec();
        } else {
            view.reset();
        }
    }

    private void changePassword() {
        changePasswordProvider.get().show(this, "Change Password", (password, confirmPassword, e) -> {
            final ChangePasswordRequest request = new ChangePasswordRequest(
                    view.getUserName(),
                    view.getPassword(),
                    password,
                    confirmPassword);
            restFactory
                    .create(RESOURCE)
                    .method(res -> res.changePassword(request))
                    .onSuccess(result -> {
                        if (result.isChangeSucceeded()) {
                            afterLogin();
                        } else {
                            AlertEvent.fireError(this, result.getMessage(), e::reset);
                        }
                    })
                    .onFailure(RestErrorHandler.forPopup(this, e))
                    .owner(this)
                    .taskMonitorFactory(new DefaultTaskMonitorFactory(this))
                    .exec();
        }, () -> {
        });
    }

    private void afterLogin() {
        final String uri = Window.Location.getParameter("redirect_uri");
        if (uri == null) {
            Window.Location.replace("");
        } else if (isSameOrigin(uri)) {
            Window.Location.replace(uri);
        } else {
            // Only ever redirect back into stroom itself, so ignore an off-origin target and reload here.
            Window.Location.replace("");
        }
    }

    // Whether a post-login redirect target stays on this application's origin. A safe
    // root-relative path is fine; a protocol-relative //host or an absolute URL to any other
    // origin is not.
    private boolean isSameOrigin(final String uri) {
        if (RedirectUrlUtil.isSafeRootRelativePath(uri)) {
            return true;
        }
        final String origin = Window.Location.getProtocol() + "//" + Window.Location.getHost();
        return uri.equals(origin) || uri.startsWith(origin + "/");
    }

    @Override
    public void onShown() {
        view.focus();
        hideLoading();
    }
}
