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

package stroom.about.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.config.global.shared.SessionInfoResource;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.BuildInfo;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.proxy.Proxy;

public class AboutPresenter
        extends MyPresenter<AboutPresenter.AboutView, AboutPresenter.AboutProxy> {

    private static final SessionInfoResource SESSION_INFO_RESOURCE = GWT.create(SessionInfoResource.class);
    private final RestFactory restFactory;
    private final UiConfigCache clientPropertyCache;
    private final DateTimeFormatter dateTimeFormatter;

    @Inject
    public AboutPresenter(final EventBus eventBus,
                          final AboutView view,
                          final AboutProxy proxy,
                          final RestFactory restFactory,
                          final UiConfigCache clientPropertyCache,
                          final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view, proxy);
        this.restFactory = restFactory;
        this.clientPropertyCache = clientPropertyCache;
        this.dateTimeFormatter = dateTimeFormatter;

        buildContent();
    }

    private void buildContent() {
        restFactory
                .create(SESSION_INFO_RESOURCE)
                .method(SessionInfoResource::get)
                .onSuccess(sessionInfo -> {
                    final BuildInfo buildInfo = sessionInfo.getBuildInfo();
                    getView().getBuildVersion().setText("Build Version: " + buildInfo.getBuildVersion());
                    getView().getBuildDate().setText("Build Date: " +
                            dateTimeFormatter.format(buildInfo.getBuildTime()));
                    getView().getUpDate().setText("Up Date: " +
                            dateTimeFormatter.format(buildInfo.getUpTime()));
                    getView().getNodeName().setText("Node Name: " + sessionInfo.getNodeName());
                })
                .onFailure(caught -> AlertEvent.fireError(AboutPresenter.this, caught.getMessage(), null))
                .taskMonitorFactory(this)
                .exec();

        clientPropertyCache.get(result -> {
            if (result != null) {
                getView().setHTML(result.getAboutHtml());
            }
        }, this);
    }

    @Override
    protected void revealInParent() {
        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .caption("About")
                .fire();
    }

    public void show() {
        revealInParent();
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
