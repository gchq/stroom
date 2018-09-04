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

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.activity.shared.AcknowledgeSplashAction;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.node.client.ClientPropertyCache;
import stroom.node.shared.ClientProperties;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.function.Consumer;

public class SplashPresenter extends MyPresenterWidget<SplashPresenter.SplashView> {
    private final ClientPropertyCache clientPropertyCache;
    private final ClientDispatchAsync dispatcher;

    @Inject
    public SplashPresenter(final EventBus eventBus,
                           final SplashView view,
                           final ClientPropertyCache clientPropertyCache,
                           final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.clientPropertyCache = clientPropertyCache;
        this.dispatcher = dispatcher;
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
                        dispatcher.exec(new AcknowledgeSplashAction(body, version)).onSuccess(result -> HidePopupEvent.fire(SplashPresenter.this, SplashPresenter.this));
                    }

                    @Override
                    public void onHide(final boolean autoClose, final boolean ok) {
                        consumer.accept(ok);
                    }
                };
                final PopupSize popupSize = new PopupSize(1000, 600, true);
                ShowPopupEvent.fire(SplashPresenter.this, SplashPresenter.this, PopupType.CLOSE_DIALOG, null, popupSize, title, popupUiHandlers, true);

            } else {
                consumer.accept(true);
            }
        });
    }

    public void setHtml(final String html) {
        getView().setHtml(html);
    }

    public interface SplashView extends View {
        void setHtml(String html);
    }
}
