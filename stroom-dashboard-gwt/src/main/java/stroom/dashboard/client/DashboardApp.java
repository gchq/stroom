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

package stroom.dashboard.client;

import stroom.dashboard.client.gin.DashboardAppGinjectorUser;
import stroom.dispatch.client.QuietTaskMonitorFactory;
import stroom.preferences.client.UserPreferencesManager;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.gwtplatform.mvp.client.DelayedBindRegistry;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class DashboardApp implements EntryPoint {

    public final DashboardAppGinjectorUser ginjector = GWT.create(DashboardAppGinjectorUser.class);

    /**
     * This is the entry point method.
     */
    public void onModuleLoad() {
        // This is required for Gwt-Platform proxy's generator.
        DelayedBindRegistry.bind(ginjector);

        final UserPreferencesManager userPreferencesManager = ginjector.getPreferencesManager();
        userPreferencesManager.fetch(preferences -> {
            userPreferencesManager.setCurrentPreferences(preferences);

            // Show the application panel.
            ginjector.getCorePresenter().get().forceReveal();

            // Start the login manager. This will attempt to auto login with PKI and
            // will therefore start the rest of the application.
            ginjector.getLoginManager().fetchUserAndPermissions();

            // Remember how places were used in case we want to use URLs and history
            // at some point.
            // ginjector.getPlaceManager().revealCurrentPlace();
        }, new QuietTaskMonitorFactory());
    }
}
