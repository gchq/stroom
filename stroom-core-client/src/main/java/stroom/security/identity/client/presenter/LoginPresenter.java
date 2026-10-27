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

package stroom.security.identity.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.security.identity.client.component.ChangePasswordController;
import stroom.security.identity.client.presenter.LoginPresenter.LoginView;
import stroom.security.identity.shared.AuthenticationResource;
import stroom.security.identity.shared.ChangePasswordRequest;
import stroom.security.identity.shared.LoginRequest;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.util.client.RedirectUrlUtil;
import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.presenter.client.View;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Visibility;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

public class LoginPresenter extends SignInPagePresenter<LoginView> implements LoginUiHandlers {

    private static final AuthenticationResource RESOURCE = GWT.create(AuthenticationResource.class);

    private final RestFactory restFactory;
    private final Provider<ChangePasswordController> changePasswordPresenterProvider;
    private final Provider<EmailResetPasswordPresenter> emailResetPasswordPresenterProvider;

    private final Element loadingText;
    private final Element loading;

    @Inject
    public LoginPresenter(final EventBus eventBus,
                          final LoginView view,
                          final RestFactory restFactory,
                          final Provider<ChangePasswordController> changePasswordPresenterProvider,
                          final Provider<EmailResetPasswordPresenter> emailResetPasswordPresenterProvider) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.changePasswordPresenterProvider = changePasswordPresenterProvider;
        this.emailResetPasswordPresenterProvider = emailResetPasswordPresenterProvider;
        getView().setUiHandlers(this);

        loadingText = RootPanel.get("loadingText").getElement();
        loading = RootPanel.get("loading").getElement();

        restFactory
                .create(RESOURCE)
                .method(AuthenticationResource::fetchPasswordPolicy)
                .onSuccess(result -> getView().setAllowPasswordResets(result.isAllowPasswordResets()))
                .onFailure(new DefaultErrorHandler(this, null))
                .taskMonitorFactory(new DefaultTaskMonitorFactory(this))
                .exec();
    }

    @Override
    public void signIn() {
        if (getView().validate()) {
            final LoginRequest request = new LoginRequest(getView().getUserName(), getView().getPassword());
            restFactory
                    .create(RESOURCE)
                    .method(res -> res.login(request))
                    .onSuccess(result -> {
                        if (result.isRequirePasswordChange()) {
                            changePassword();
                        } else if (result.isLoginSuccessful()) {
                            afterLogin();
                        } else {
                            AlertEvent.fireError(this, result.getMessage(), getView()::reset);
                        }
                    })
                    .onFailure(new DefaultErrorHandler(this, getView()::reset))
                    .taskMonitorFactory(new DefaultTaskMonitorFactory(this))
                    .exec();
        } else {
            getView().reset();
        }
    }

    @Override
    public void emailResetPassword() {
        final EmailResetPasswordPresenter emailResetPasswordPresenter =
                makeDialog(emailResetPasswordPresenterProvider);
        emailResetPasswordPresenter.show();
    }

    private void changePassword() {
        changePasswordPresenterProvider.get().show(this, "Change Password", (password, confirmPassword, e) -> {
            final ChangePasswordRequest request = new ChangePasswordRequest(
                    getView().getUserName(),
                    getView().getPassword(),
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

    /**
     * Whether a post-login redirect target stays on this application's origin. A safe root-relative path is
     * fine; a protocol-relative {@code //host} or an absolute URL to any other origin is not.
     */
    private boolean isSameOrigin(final String uri) {
        if (RedirectUrlUtil.isSafeRootRelativePath(uri)) {
            return true;
        }
        final String origin = Window.Location.getProtocol() + "//" + Window.Location.getHost();
        return uri.equals(origin) || uri.startsWith(origin + "/");
    }

    @Override
    public void onShown() {
        getView().focus();
        loading.getStyle().setOpacity(0);
        // Hidden, not just transparent, so screen readers stop reading "Loading..."
        loading.getStyle().setVisibility(Visibility.HIDDEN);
    }


    // --------------------------------------------------------------------------------

    // --------------------------------------------------------------------------------


    public interface LoginView extends View, Focus, HasUiHandlers<LoginUiHandlers> {

        String getUserName();

        String getPassword();

        boolean validate();

        void reset();

        void setAllowPasswordResets(boolean allowPasswordResets);
    }
}
