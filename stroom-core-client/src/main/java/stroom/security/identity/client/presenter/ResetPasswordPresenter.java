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

package stroom.security.identity.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.security.identity.client.presenter.ResetPasswordPresenter.ResetPasswordProxy;
import stroom.security.identity.client.presenter.ResetPasswordPresenter.ResetPasswordView;
import stroom.security.identity.shared.AuthenticationResource;
import stroom.security.identity.shared.ResetPasswordRequest;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.util.shared.NullSafe;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyStandard;
import com.gwtplatform.mvp.client.proxy.Proxy;
import com.gwtplatform.mvp.client.proxy.RevealRootContentEvent;

/**
 * The page a user lands on after following the password reset link that was emailed to them. It collects
 * a new password using the same form as a normal password change and submits it along with the token from
 * the link. The user is not signed in as a result, they must sign in with the new password afterwards.
 */
public class ResetPasswordPresenter extends MyPresenter<ResetPasswordView, ResetPasswordProxy> {

    private static final AuthenticationResource RESOURCE = GWT.create(AuthenticationResource.class);

    /**
     * Where every exit from this page goes: success, cancel and the invalid-link alert.
     * <p>
     * The {@code error=login_required} parameter is required, not decoration. {@code App.onModuleLoad}
     * reveals the sign in form for this path only when it sees that value, and reveals the
     * Authentication Error page for anything else — <b>including no parameter at all</b>. The server
     * adds it on the redirects it drives ({@code AuthenticationServiceImpl.createSignInUri}), so a
     * navigation from the client has to add it itself.
     */
    private static final String SIGN_IN_URL = "/signIn?error=login_required";
    private static final String INVALID_LINK_MESSAGE =
            "This password reset link is invalid or has expired. Please request a new one.";

    private final RestFactory restFactory;
    private final Provider<ChangePasswordPresenter> changePasswordPresenterProvider;
    private final Element loading;

    @Inject
    public ResetPasswordPresenter(final EventBus eventBus,
                                  final ResetPasswordView view,
                                  final ResetPasswordProxy proxy,
                                  final RestFactory restFactory,
                                  final Provider<ChangePasswordPresenter> changePasswordPresenterProvider) {
        super(eventBus, view, proxy);
        this.restFactory = restFactory;
        this.changePasswordPresenterProvider = changePasswordPresenterProvider;

        loading = RootPanel.get("loading").getElement();
    }

    @Override
    protected void revealInParent() {
        RevealRootContentEvent.fire(this, this);
        loading.getStyle().setOpacity(0);
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
        final ChangePasswordPresenter changePasswordPresenter = changePasswordPresenterProvider.get();
        changePasswordPresenter.show("Reset Password", event -> {
            if (event.isOk()) {
                if (changePasswordPresenter.validate()) {
                    resetPassword(changePasswordPresenter, token, event);
                } else {
                    event.reset();
                }
            } else {
                event.hide();
                gotoSignIn();
            }
        });
    }

    private void resetPassword(final ChangePasswordPresenter changePasswordPresenter,
                               final String token,
                               final stroom.widget.popup.client.event.HidePopupRequestEvent event) {
        final ResetPasswordRequest request = new ResetPasswordRequest(
                token,
                changePasswordPresenter.getPassword(),
                changePasswordPresenter.getConfirmPassword());

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
                .taskMonitorFactory(new DefaultTaskMonitorFactory(this))
                .exec();
    }

    private void gotoSignIn() {
        Window.Location.replace(SIGN_IN_URL);
    }


    // --------------------------------------------------------------------------------


    @ProxyStandard
    public interface ResetPasswordProxy extends Proxy<ResetPasswordPresenter> {

    }


    // --------------------------------------------------------------------------------


    public interface ResetPasswordView extends View {

    }
}
