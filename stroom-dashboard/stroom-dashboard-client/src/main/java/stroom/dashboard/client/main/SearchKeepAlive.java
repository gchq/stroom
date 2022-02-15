/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.client.main;

import stroom.dashboard.shared.DashboardQueryKey;
import stroom.dashboard.shared.DashboardResource;
import stroom.dashboard.shared.SearchKeepAliveRequest;
import stroom.dashboard.shared.SearchKeepAliveResponse;
import stroom.dispatch.client.ApplicationInstanceIdProvider;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.event.LogoutEvent;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashSet;
import java.util.Set;

@Singleton
public class SearchKeepAlive {

    private static final DashboardResource DASHBOARD_RESOURCE = GWT.create(DashboardResource.class);

    private static final int KEEP_ALIVE_INTERVAL = 10_000;

    private final RestFactory restFactory;
    private final ApplicationInstanceIdProvider applicationInstanceIdProvider;
    private final Set<DashboardQueryKey> activeKeys = new HashSet<>();
    private final Set<DashboardQueryKey> deadKeys = new HashSet<>();

    @Inject
    public SearchKeepAlive(final EventBus eventBus,
                           final RestFactory restFactory,
                           final ApplicationInstanceIdProvider applicationInstanceIdProvider) {
        this.restFactory = restFactory;
        this.applicationInstanceIdProvider = applicationInstanceIdProvider;

        final Timer keepAliveTimer = new Timer() {
            @Override
            public void run() {
                keepAlive();
            }
        };
        // Tell the server we want to keep searches alive every 10 seconds.
        keepAliveTimer.scheduleRepeating(KEEP_ALIVE_INTERVAL);

        // Listen for logout events.
        eventBus.addHandler(LogoutEvent.getType(), event -> removeAll());
    }

    private void removeAll() {
        deadKeys.addAll(activeKeys);
        activeKeys.clear();

        // Call to ensure any running query is destroyed.
        keepAlive();
    }

    public void add(final DashboardQueryKey key) {
        activeKeys.add(key);
    }

    public void remove(final DashboardQueryKey key) {
        activeKeys.remove(key);
        deadKeys.add(key);

        // Call to ensure the query is destroyed.
        keepAlive();
    }

    private void keepAlive() {
        final Rest<SearchKeepAliveResponse> rest = restFactory.create();
        rest
                .onSuccess(response -> {
                    try {
                        if (response != null) {
                            // We don't need to remember any dead keys that have been removed.
                            deadKeys.removeAll(response.getDeadKeys());
                            GWT.log(response.toString());
                        }
                    } catch (final RuntimeException e) {
                        GWT.log(e.getMessage());
                    }
                })
                .call(DASHBOARD_RESOURCE)
                .keepAlive(new SearchKeepAliveRequest(applicationInstanceIdProvider.get(), activeKeys, deadKeys));
    }
}
