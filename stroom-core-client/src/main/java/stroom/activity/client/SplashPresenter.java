/*
 * Copyright 2018 Crown Copyright
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

import com.google.gwt.user.client.ui.MaxScrollPanel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.activity.shared.AcknowledgeSplashAction;
import stroom.alert.client.event.AlertEvent;
import stroom.core.client.UrlParameters;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.security.client.api.event.LogoutEvent;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.SplashConfig;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.function.Consumer;

public class SplashPresenter extends MyPresenterWidget<SplashPresenter.SplashView> {
    private final UiConfigCache uiConfigCache;
    private final ClientDispatchAsync dispatcher;
    private final UrlParameters urlParameters;
//    private boolean enabled;

    @Inject
    public SplashPresenter(final EventBus eventBus,
                           final SplashView view,
                           final UiConfigCache uiConfigCache,
                           final ClientDispatchAsync dispatcher,
                           final UrlParameters urlParameters) {
        super(eventBus, view);
        this.uiConfigCache = uiConfigCache;
        this.dispatcher = dispatcher;
        this.urlParameters = urlParameters;
    }

//    @Override
//    protected void onBind() {
//        super.onBind();
//        registerHandler(getView().getScrollPanel().addScrollHandler(event -> testScroll()));
//    }
//
//    private boolean testScroll() {
//        if (!enabled) {
//            if (getView().getScrollPanel().getVerticalScrollPosition() >= getView().getScrollPanel().getMaximumVerticalScrollPosition()) {
//                EnablePopupEvent.fire(SplashPresenter.this, SplashPresenter.this);
//                enabled = true;
//            }
//        }
//        return enabled;
//    }

    public void show(final Consumer<Boolean> consumer) {
        uiConfigCache.get().onSuccess(uiConfig -> {
            final SplashConfig splashConfig = uiConfig.getSplashConfig();
            final boolean enableSplashScreen = splashConfig.isEnabled();
            if (enableSplashScreen && !urlParameters.isEmbedded()) {
                final String title = splashConfig.getTitle();
                final String body = splashConfig.getBody();
                final String version = splashConfig.getVersion();
                setHtml(body);
                final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                    @Override
                    public void onHideRequest(final boolean autoClose, final boolean ok) {
                        if (ok) {
                            dispatcher.exec(new AcknowledgeSplashAction(body, version)).onSuccess(result -> hide(autoClose, ok));
                        } else {
                            AlertEvent.fireWarn(SplashPresenter.this, "You must accept the terms to use this system", null, () -> {
                                hide(autoClose, ok);
                            });
                        }
                    }

                    @Override
                    public void onHide(final boolean autoClose, final boolean ok) {
                        if (!ok) {
                            LogoutEvent.fire(SplashPresenter.this);
                        }
                        consumer.accept(ok);
                    }
                };

//                Scheduler.get().scheduleFixedDelay(this::testScroll, 2000);

                final PopupSize popupSize = new PopupSize(800, 600, true);
                ShowPopupEvent.fire(SplashPresenter.this, SplashPresenter.this, PopupType.ACCEPT_REJECT_DIALOG, null, popupSize, title, popupUiHandlers, true);

            } else {
                consumer.accept(true);
            }
        });
    }

    private void hide(final boolean autoClose, final boolean ok) {
        HidePopupEvent.fire(SplashPresenter.this, SplashPresenter.this, autoClose, ok);
    }

    public void setHtml(final String html) {
        getView().setHtml(html);
    }

    public interface SplashView extends View {
        void setHtml(String html);

        MaxScrollPanel getScrollPanel();
    }
}
