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

package stroom.security.identity.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.security.identity.client.presenter.LoginPresenter.LoginProxy;
import stroom.security.identity.client.presenter.LoginPresenter.LoginView;
import stroom.security.identity.shared.AuthenticationResource;
import stroom.security.identity.shared.ChangePasswordRequest;
import stroom.security.identity.shared.LoginRequest;
import stroom.task.client.DefaultTaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyStandard;
import com.gwtplatform.mvp.client.proxy.Proxy;
import com.gwtplatform.mvp.client.proxy.RevealRootContentEvent;

public class LoginPresenter extends MyPresenter<LoginView, LoginProxy> implements LoginUiHandlers {

    private static final AuthenticationResource RESOURCE = GWT.create(AuthenticationResource.class);

    private final RestFactory restFactory;
    private final Provider<ChangePasswordPresenter> changePasswordPresenterProvider;
    private final Provider<EmailResetPasswordPresenter> emailResetPasswordPresenterProvider;

    private final Element loadingText;
    private final Element loading;

    @Inject
    public LoginPresenter(final EventBus eventBus,
                          final LoginView view,
                          final LoginProxy loginProxy,
                          final RestFactory restFactory,
                          final Provider<ChangePasswordPresenter> changePasswordPresenterProvider,
                          final Provider<EmailResetPasswordPresenter> emailResetPasswordPresenterProvider) {
        super(eventBus, view, loginProxy);
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
        final EmailResetPasswordPresenter emailResetPasswordPresenter = emailResetPasswordPresenterProvider.get();
        emailResetPasswordPresenter.show();
    }

    private void changePassword() {
        final ChangePasswordPresenter changePasswordPresenter =
                changePasswordPresenterProvider.get();
        changePasswordPresenter.show("Change Password", e -> {
            if (e.isOk()) {
                if (getView().validate()) {
                    final ChangePasswordRequest request = new ChangePasswordRequest(
                            getView().getUserName(),
                            getView().getPassword(),
                            changePasswordPresenter.getPassword(),
                            changePasswordPresenter.getConfirmPassword());
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

                } else {
                    e.reset();
                }
            } else {
                e.hide();
            }
        });
    }

    private void afterLogin() {
        final String uri = Window.Location.getParameter("redirect_uri");
        if (uri == null) {
            Window.Location.replace("");
        } else {
            Window.Location.replace(uri);
        }
    }

    @Override
    protected void revealInParent() {
        RevealRootContentEvent.fire(this, this);
        getView().focus();
        loading.getStyle().setOpacity(0);
    }


    // --------------------------------------------------------------------------------


    @ProxyStandard
    public interface LoginProxy extends Proxy<LoginPresenter> {

    }


    // --------------------------------------------------------------------------------


    public interface LoginView extends View, Focus, HasUiHandlers<LoginUiHandlers> {

        String getUserName();

        String getPassword();

        boolean validate();

        void reset();

        void setAllowPasswordResets(boolean allowPasswordResets);
    }
}
