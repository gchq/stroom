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

package stroom.about.client.presenter;

import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.proxy.Proxy;
import stroom.alert.client.event.AlertEvent;
import stroom.properties.global.client.ClientPropertyCache;
import stroom.properties.shared.ClientProperties;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

public class AboutPresenter extends MyPresenter<AboutPresenter.AboutView, AboutPresenter.AboutProxy> {
    @Inject
    public AboutPresenter(final EventBus eventBus, final AboutView view, final AboutProxy proxy,
                          final ClientPropertyCache clientPropertyCache) {
        super(eventBus, view, proxy);
        clientPropertyCache.get()
                .onSuccess(result -> {
                    view.setHTML(result.get(ClientProperties.ABOUT_HTML));
                    view.getBuildVersion().setText("Build Version: " + result.get(ClientProperties.BUILD_VERSION));
                    view.getBuildDate().setText("Build Date: " + result.get(ClientProperties.BUILD_DATE));
                    view.getUpDate().setText("Up Date: " + result.get(ClientProperties.UP_DATE));
                    view.getNodeName().setText("Node Name: " + result.get(ClientProperties.NODE_NAME));
                })
                .onFailure(caught -> AlertEvent.fireError(AboutPresenter.this, caught.getMessage(), null));
    }

    @Override
    protected void revealInParent() {
        ShowPopupEvent.fire(this, this, PopupType.CLOSE_DIALOG, "About");
    }

    @ProxyCodeSplit
    public interface AboutProxy extends Proxy<AboutPresenter> {
    }

    public interface AboutView extends View {
        void setHTML(String html);

        HasText getBuildVersion();

        HasText getBuildDate();

        HasText getUpDate();

        HasText getNodeName();
    }
}
