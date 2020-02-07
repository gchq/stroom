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

package stroom.ui.config.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.Timer;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.google.web.bindery.event.shared.SimpleEventBus;
import stroom.config.global.shared.ConfigResource;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.ClientSecurityContext;
import stroom.ui.config.shared.UiConfig;
import stroom.widget.util.client.Future;
import stroom.widget.util.client.FutureImpl;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UiConfigCache implements HasHandlers {
    private static final ConfigResource CONFIG_RESOURCE = GWT.create(ConfigResource.class);
    private static final int ONE_MINUTE = 1000 * 60;

    private final RestFactory restFactory;
    private UiConfig clientProperties;
    private boolean refreshing;
    private EventBus eventBus;

    @Inject
    public UiConfigCache(final RestFactory restFactory, final ClientSecurityContext securityContext) {
        this.restFactory = restFactory;

        final Timer refreshTimer = new Timer() {
            @Override
            public void run() {
                // Don't auto refresh if we are already refreshing.
                if (!refreshing) {
                    // Don't auto refresh if we are not logged in as this will keep the user session alive unnecessarily.
                    if (securityContext.isLoggedIn()) {
                        refreshing = true;
                        refresh()
                                .onSuccess(result -> refreshing = false)
                                .onFailure(throwable -> refreshing = false);
                    }
                }
            }
        };

        // Refreshing the client properties keeps them current and also ensures that all actions on the server belonging
        // to the logged in user are refreshed every minute so that the server doesn't try and terminate them.
        refreshTimer.scheduleRepeating(ONE_MINUTE);
    }

    public Future<UiConfig> refresh() {
        final FutureImpl<UiConfig> future = new FutureImpl<>();
        final Rest<UiConfig> rest = restFactory.create();
        rest
                .onSuccess(result -> {
                    clientProperties = result;
                    future.setResult(result);
                    PropertyChangeEvent.fire(UiConfigCache.this, result);
                }).onFailure(future::setThrowable)
                .call(CONFIG_RESOURCE)
                .fetchUiConfig();
        return future;
    }

    public Future<UiConfig> get() {
        final UiConfig props = clientProperties;
        if (props == null) {
            return refresh();
        }

        final FutureImpl<UiConfig> future = new FutureImpl<>();
        future.setResult(props);
        return future;
    }

    public HandlerRegistration addPropertyChangeHandler(final PropertyChangeEvent.Handler handler) {
        if (eventBus == null) {
            eventBus = new SimpleEventBus();
        }

        final HandlerRegistration handlerRegistration = eventBus.addHandlerToSource(PropertyChangeEvent.getType(), this, handler);
        get().onSuccess(properties -> PropertyChangeEvent.fire(UiConfigCache.this, properties));
        return handlerRegistration;
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        if (eventBus != null) {
            eventBus.fireEventFromSource(event, this);
        }
    }
}
