/*
 * Copyright 2016 Crown Copyright
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

package stroom.login.client.presenter;

import com.google.gwt.user.client.ui.RootPanel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.NameToken;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Place;
import com.gwtplatform.mvp.client.proxy.ProxyPlace;
import com.gwtplatform.mvp.client.proxy.RevealRootContentEvent;
import stroom.alert.client.event.AlertEvent;
import stroom.app.client.NameTokens;
import stroom.core.client.KeyboardInterceptor;
import stroom.dispatch.client.AsyncCallbackAdaptor;
import stroom.node.client.ClientPropertyCache;
import stroom.node.shared.ClientProperties;
import stroom.security.client.event.EmailResetPasswordEvent;
import stroom.security.client.event.LoginEvent;
import stroom.security.client.event.LoginFailedEvent;
import stroom.security.client.event.LogoutEvent;

public class LoginPresenter extends MyPresenter<LoginPresenter.LoginView, LoginPresenter.LoginProxy>
        implements LoginUiHandlers, LoginFailedEvent.LoginFailedHandler, LogoutEvent.LogoutHandler {
    private boolean busy;

    @Inject
    public LoginPresenter(final EventBus eventBus, final LoginView view, final LoginProxy proxy,
            final ClientPropertyCache clientPropertyCache, final KeyboardInterceptor keyboardInterceptor) {
        super(eventBus, view, proxy);
        view.setUiHandlers(this);

        // Handle key presses.
        keyboardInterceptor.register(view.asWidget());

        clientPropertyCache.get(new AsyncCallbackAdaptor<ClientProperties>() {
            @Override
            public void onSuccess(final ClientProperties result) {
                view.setHTML(result.get(ClientProperties.LOGIN_HTML));
                view.setBuildVersion("Build Version: " + result.get(ClientProperties.BUILD_VERSION));
                view.setBuildDate("Build Date: " + result.get(ClientProperties.BUILD_DATE));
                view.setUpDate("Up Date: " + result.get(ClientProperties.UP_DATE));
                view.setNodeName("Node Name: " + result.get(ClientProperties.NODE_NAME));
            }

            @Override
            public void onFailure(final Throwable caught) {
                AlertEvent.fireError(LoginPresenter.this, caught.getMessage(), null);
            }
        });
    }

    @ProxyEvent
    @Override
    public void onLoginFailed(final LoginFailedEvent event) {
        getView().setError(event.getError());
        getView().setPassword("");
        forceReveal();
    }

    @ProxyEvent
    @Override
    public void onLogout(final LogoutEvent event) {
        getView().setError("");
        getView().setPassword("");
        forceReveal();
    }

    @Override
    protected void revealInParent() {
        RevealRootContentEvent.fire(this, this);
        RootPanel.get("logo").setVisible(true);
    }

    @Override
    public void login() {
        if (!busy) {
            busy = true;
            // Clear any existing error.
            getView().setError("");
            // Fire login event.
            LoginEvent.fire(this, getView().getUserName(), getView().getPassword());
            // Clear the password.
            getView().setPassword("");
            busy = false;
        }
    }

    @Override
    public void emailResetPassword() {
        if (!busy) {
            busy = true;
            final String userName = getView().getUserName();
            if (userName.length() == 0) {
                AlertEvent.fireWarn(this, "No user name entered!", null);
            } else {
                EmailResetPasswordEvent.fire(this, getView().getUserName());
            }
            busy = false;
        }
    }

    @ProxyCodeSplit
    @NameToken(NameTokens.LOGIN)
    public interface LoginProxy extends ProxyPlace<LoginPresenter>, Place {
    }

    public interface LoginView extends View, HasUiHandlers<LoginUiHandlers> {
        void setHTML(String html);

        void setBuildVersion(String buildVersion);

        void setBuildDate(String buildDate);

        void setUpDate(String upDate);

        void setNodeName(String nodeName);

        String getUserName();

        String getPassword();

        void setPassword(String password);

        void setError(String error);
    }
}
