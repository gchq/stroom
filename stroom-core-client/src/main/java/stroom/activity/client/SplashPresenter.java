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

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.ui.MaxScrollPanel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.activity.shared.AcknowledgeSplashAction;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.node.client.ClientPropertyCache;
import stroom.node.shared.ClientProperties;
import stroom.security.client.event.LogoutEvent;
import stroom.widget.popup.client.event.EnablePopupEvent;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.function.Consumer;

public class SplashPresenter extends MyPresenterWidget<SplashPresenter.SplashView> {
    private final ClientPropertyCache clientPropertyCache;
    private final ClientDispatchAsync dispatcher;
    private boolean enabled;

    @Inject
    public SplashPresenter(final EventBus eventBus,
                           final SplashView view,
                           final ClientPropertyCache clientPropertyCache,
                           final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.clientPropertyCache = clientPropertyCache;
        this.dispatcher = dispatcher;
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(getView().getScrollPanel().addScrollHandler(event -> testScroll()));
    }

    private boolean testScroll() {
        if (!enabled) {
            if (getView().getScrollPanel().getVerticalScrollPosition() >= getView().getScrollPanel().getMaximumVerticalScrollPosition()) {
                EnablePopupEvent.fire(SplashPresenter.this, SplashPresenter.this);
                enabled = true;
            }
        }
        return enabled;
    }

    public void show(final Consumer<Boolean> consumer) {
        clientPropertyCache.get().onSuccess(clientProperties -> {
            final boolean enableSplashScreen = clientProperties.getBoolean(ClientProperties.SPLASH_ENABLED, false);
            if (enableSplashScreen) {
                final String title = clientProperties.get(ClientProperties.SPLASH_TITLE);
                final String body = clientProperties.get(ClientProperties.SPLASH_BODY);
                final String version = clientProperties.get(ClientProperties.SPLASH_VERSION);
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

                Scheduler.get().scheduleFixedDelay(this::testScroll, 2000);

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
