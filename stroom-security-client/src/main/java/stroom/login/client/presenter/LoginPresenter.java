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
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.NameToken;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Place;
import com.gwtplatform.mvp.client.proxy.ProxyPlace;
import com.gwtplatform.mvp.client.proxy.RevealRootContentEvent;
import stroom.alert.client.event.AlertEvent;
import stroom.core.client.KeyboardInterceptor;
import stroom.core.client.NameTokens;
import stroom.node.client.ClientPropertyCache;
import stroom.node.shared.ClientProperties;
import stroom.security.client.event.LoginFailedEvent;
import stroom.security.client.event.LogoutEvent;

public class LoginPresenter extends MyPresenter<LoginPresenter.LoginView, LoginPresenter.LoginProxy>
        implements LoginFailedEvent.LoginFailedHandler, LogoutEvent.LogoutHandler {

    @Inject
    public LoginPresenter(final EventBus eventBus, final LoginView view, final LoginProxy proxy,
                          final ClientPropertyCache clientPropertyCache, final KeyboardInterceptor keyboardInterceptor) {
        super(eventBus, view, proxy);

        // Handle key presses.
        keyboardInterceptor.register(view.asWidget());

        clientPropertyCache.get()
                .onSuccess(result -> {
                    view.setHTML(result.get(ClientProperties.LOGIN_HTML));
                    view.setBuildVersion("Build Version: " + result.get(ClientProperties.BUILD_VERSION));
                    view.setBuildDate("Build Date: " + result.get(ClientProperties.BUILD_DATE));
                    view.setUpDate("Up Date: " + result.get(ClientProperties.UP_DATE));
                    view.setNodeName("Node Name: " + result.get(ClientProperties.NODE_NAME));
                })
                .onFailure(caught -> AlertEvent.fireError(LoginPresenter.this, caught.getMessage(), null));

        registerHandler(clientPropertyCache.addPropertyChangeHandler(event -> getView().setBanner(event.getProperties().get(ClientProperties.MAINTENANCE_MESSAGE))));
    }

    @ProxyEvent
    @Override
    public void onLoginFailed(final LoginFailedEvent event) {
        forceReveal();
    }

    @ProxyEvent
    @Override
    public void onLogout(final LogoutEvent event) {
        forceReveal();
    }

    @Override
    protected void revealInParent() {
        RevealRootContentEvent.fire(this, this);
        RootPanel.get("logo").setVisible(true);
    }

    @ProxyCodeSplit
    @NameToken(NameTokens.LOGIN)
    public interface LoginProxy extends ProxyPlace<LoginPresenter>, Place {
    }

    public interface LoginView extends View {
        void setBanner(String text);

        void setHTML(String html);

        void setBuildVersion(String buildVersion);

        void setBuildDate(String buildDate);

        void setUpDate(String upDate);

        void setNodeName(String nodeName);
    }
}
