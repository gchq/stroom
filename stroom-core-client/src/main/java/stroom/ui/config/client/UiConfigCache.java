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

package stroom.ui.config.client;

import stroom.annotation.client.AnnotationChangeEvent;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.QuietTaskMonitorFactory;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.ClientSecurityContext;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.shared.ExtendedUiConfig;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.Timer;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.google.web.bindery.event.shared.SimpleEventBus;

import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UiConfigCache implements HasHandlers {

    private static final GlobalConfigResource CONFIG_RESOURCE = GWT.create(GlobalConfigResource.class);
    private static final int ONE_MINUTE = 1_000 * 60;

    private final RestFactory restFactory;
    private ExtendedUiConfig clientProperties;
    private EventBus eventBus;
    private long lastAnnotationChangeTime;

    @Inject
    public UiConfigCache(final RestFactory restFactory, final ClientSecurityContext securityContext) {
        this.restFactory = restFactory;

        // Refreshing the client properties keeps them current and also ensures that all actions on the
        // server belonging to the logged-in user are refreshed every minute so that the server doesn't
        // try and terminate them.
        final Timer refreshTimer = createRefreshTimer(securityContext);
        refreshTimer.scheduleRepeating(ONE_MINUTE);
    }

    private Timer createRefreshTimer(final ClientSecurityContext securityContext) {
        return new Timer() {
            private boolean isRefreshInProgress;

            @Override
            public void run() {
                // Don't auto refresh if we are already refreshing.
                if (!isRefreshInProgress) {
                    // Don't auto refresh if we are not logged in as this will keep the user session
                    // alive unnecessarily.
                    if (securityContext.isLoggedIn()) {
                        isRefreshInProgress = true;
                        // Stop refreshing when we get an error
                        refresh(result -> isRefreshInProgress = result == null,
                                new QuietTaskMonitorFactory());
                    }
                }
            }
        };
    }

    public void refresh(final Consumer<ExtendedUiConfig> consumer,
                        final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(CONFIG_RESOURCE)
                .method(GlobalConfigResource::fetchExtendedUiConfig)
                .onSuccess(result -> {
                    clientProperties = result;
                    consumer.accept(result);
                    PropertyChangeEvent.fire(UiConfigCache.this, result);

                    try {
                        if (lastAnnotationChangeTime != result.getLastAnnotationChangeTime()) {
                            lastAnnotationChangeTime = result.getLastAnnotationChangeTime();
                            AnnotationChangeEvent.fire(this, null);
                        }
                    } catch (final RuntimeException e) {
                        GWT.log(e.getMessage());
                    }
                })
                .onFailure(error -> {
//                    GWT.log("Error refreshing uiConfig: "
//                            + error.getException().getClass().getSimpleName()
//                            + " - " + error.getMessage());
                    new DefaultErrorHandler(this, () -> consumer.accept(null));
                })
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void get(final Consumer<ExtendedUiConfig> consumer) {
        get(consumer, new DefaultTaskMonitorFactory(this));
    }

    public void get(final Consumer<ExtendedUiConfig> consumer, final TaskMonitorFactory taskMonitorFactory) {
        final ExtendedUiConfig props = clientProperties;
        if (props == null) {
            refresh(consumer, taskMonitorFactory);
        }
        consumer.accept(props);
    }

    public HandlerRegistration addPropertyChangeHandler(final PropertyChangeEvent.Handler handler) {
        if (eventBus == null) {
            eventBus = new SimpleEventBus();
        }

        final HandlerRegistration handlerRegistration = eventBus.addHandlerToSource(PropertyChangeEvent.getType(),
                this,
                handler);
        get(properties -> PropertyChangeEvent.fire(UiConfigCache.this, properties));
        return handlerRegistration;
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        if (eventBus != null) {
            eventBus.fireEventFromSource(event, this);
        }
    }
}
