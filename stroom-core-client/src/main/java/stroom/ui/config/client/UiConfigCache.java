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

import stroom.annotation.client.AnnotationChangeEvent;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.QuietTaskMonitorFactory;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.ClientSecurityContext;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.shared.ExtendedUiConfig;
import stroom.widget.component.client.Listeners;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.Timer;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UiConfigCache implements HasHandlers {

    private static final GlobalConfigResource CONFIG_RESOURCE = GWT.create(GlobalConfigResource.class);
    private static final int ONE_MINUTE = 1_000 * 60;

    private final EventBus eventBus;
    private final RestFactory restFactory;
    private final Timer refreshTimer;
    private ExtendedUiConfig clientProperties;
    // Told the properties whenever they are fetched, e.g. the main screen for the page's title
    private final Listeners<ExtendedUiConfig> propertyListeners = new Listeners<>();
    private long lastAnnotationChangeTime;

    /// Made by GIN, once for the app.
    ///
    /// @param eventBus        The application's event bus, on which a failure to fetch the
    ///                        properties is shown and an annotation change is told.
    /// @param restFactory     Fetches the properties.
    /// @param securityContext Whether the user is logged in, which the regular refresh needs.
    @Inject
    public UiConfigCache(final EventBus eventBus,
                         final RestFactory restFactory,
                         final ClientSecurityContext securityContext) {
        this.eventBus = eventBus;
        this.restFactory = restFactory;

        // Refreshing the client properties keeps them current and also ensures that all actions on the
        // server belonging to the logged-in user are refreshed every minute so that the server doesn't
        // try and terminate them.
        refreshTimer = createRefreshTimer(securityContext);
        refreshTimer.scheduleRepeating(ONE_MINUTE);
    }

    /// Stops the regular refresh of the client properties, e.g. when this cache is no longer
    /// used because the UI that created it is being thrown away. The properties can still be
    /// fetched with [#get(Consumer)] and [#refresh(Consumer, TaskMonitorFactory)].
    public void stopRefreshing() {
        refreshTimer.cancel();
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
                        // A failed refresh never calls back, so leaves this set: refreshing
                        // stops when there is an error
                        refresh(result -> isRefreshInProgress = false,
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
                    propertyListeners.fire(result);

                    try {
                        // The first fetch only learns the time: nothing shown can be older than it
                        final long changeTime = result.getLastAnnotationChangeTime();
                        if (lastAnnotationChangeTime != changeTime) {
                            final boolean known = lastAnnotationChangeTime != 0;
                            lastAnnotationChangeTime = changeTime;
                            if (known) {
                                AnnotationChangeEvent.fire(this, null);
                            }
                        }
                    } catch (final RuntimeException e) {
                        GWT.log(e.getMessage());
                    }
                })
                // Shown to the user; the consumer isn't called, as most can't do without the
                // properties
                .onFailure(new DefaultErrorHandler(this, null))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void get(final Consumer<ExtendedUiConfig> consumer) {
        get(consumer, new DefaultTaskMonitorFactory(this));
    }

    public void get(final Consumer<ExtendedUiConfig> consumer, final TaskMonitorFactory taskMonitorFactory) {
        final ExtendedUiConfig props = clientProperties;
        if (props == null) {
            // The consumer is called once the properties have been fetched (and not at all if
            // fetching them fails, which is shown), so it must not also be called now with null
            refresh(consumer, taskMonitorFactory);
        } else {
            consumer.accept(props);
        }
    }

    /// Listens for the properties, now (once fetched) and whenever they are fetched again, until the
    /// registration is removed.
    ///
    /// @param listener Told the properties.
    /// @return The registration, for the listener's owner to own.
    public HandlerRegistration addPropertyChangeListener(final Consumer<ExtendedUiConfig> listener) {
        final HandlerRegistration registration = propertyListeners.add(listener);
        get(propertyListeners::fire);
        return registration;
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
