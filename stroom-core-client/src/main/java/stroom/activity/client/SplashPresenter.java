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

package stroom.activity.client;

import stroom.activity.shared.AcknowledgeSplashRequest;
import stroom.activity.shared.ActivityResource;
import stroom.alert.client.event.AlertEvent;
import stroom.core.client.UrlParameters;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.SplashConfig;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class SplashPresenter
        extends MyPresenterWidget<SplashPresenter.SplashView> {

    private static final ActivityResource ACTIVITY_RESOURCE = GWT.create(ActivityResource.class);

    private final UiConfigCache uiConfigCache;
    private final RestFactory restFactory;
    private final UrlParameters urlParameters;

    @Inject
    public SplashPresenter(final EventBus eventBus,
                           final SplashView view,
                           final UiConfigCache uiConfigCache,
                           final RestFactory restFactory,
                           final UrlParameters urlParameters) {
        super(eventBus, view);
        this.uiConfigCache = uiConfigCache;
        this.restFactory = restFactory;
        this.urlParameters = urlParameters;
    }

    public void show(final Consumer<Boolean> consumer) {
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                final SplashConfig splashConfig = uiConfig.getSplash();
                final boolean enableSplashScreen = splashConfig.isEnabled();
                if (enableSplashScreen && !urlParameters.isEmbedded()) {
                    final String title = splashConfig.getTitle();
                    final String body = splashConfig.getBody();
                    final String version = splashConfig.getVersion();
                    setHtml(body);

                    final PopupSize popupSize = PopupSize.resizable(800, 600);
                    ShowPopupEvent.builder(this)
                            .popupType(PopupType.ACCEPT_REJECT_DIALOG)
                            .popupSize(popupSize)
                            .caption(title)
                            .onHideRequest(e -> {
                                if (e.isOk()) {
                                    restFactory
                                            .create(ACTIVITY_RESOURCE)
                                            .method(res -> res.acknowledgeSplash(new AcknowledgeSplashRequest(body,
                                                    version)))
                                            .onSuccess(result -> {
                                                e.hide();
                                                consumer.accept(e.isOk());
                                            })
                                            .onFailure(RestErrorHandler.forPopup(this, e))
                                            .taskMonitorFactory(this)
                                            .exec();
                                } else {
                                    AlertEvent.fireWarn(SplashPresenter.this,
                                            "You must accept the terms to use this system",
                                            null,
                                            () -> {
                                                e.hide();
                                                consumer.accept(e.isOk());
                                            });
                                }
                            })
                            .modal(true)
                            .fire();

                } else {
                    consumer.accept(true);
                }
            }
        }, new DefaultTaskMonitorFactory(this));
    }

    public void setHtml(final String html) {
        getView().setHtml(html);
    }

    public interface SplashView extends View {

        void setHtml(String html);
    }
}
