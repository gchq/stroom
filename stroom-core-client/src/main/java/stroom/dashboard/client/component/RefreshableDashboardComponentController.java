/*
 * Copyright 2025 Crown Copyright
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

package stroom.dashboard.client.component;

import stroom.dashboard.client.main.Queryable;
import stroom.dashboard.client.main.RefreshInterval;
import stroom.dashboard.client.main.Refreshable;
import stroom.dashboard.shared.Automate;

import com.google.gwt.user.client.Timer;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// A dashboard pane that runs a query ([DashboardComponentController]) and runs it again on a timer
/// after each search, if its settings say to.
public abstract class RefreshableDashboardComponentController
        extends DashboardComponentController
        implements Refreshable, Queryable {

    private boolean allowRefresh = true;
    private Timer refreshTimer;

    /// @param eventBus                  The application's event bus.
    /// @param settingsPresenterProvider Makes the pane's settings dialog.
    protected RefreshableDashboardComponentController(
            final EventBus eventBus,
            final Provider<? extends ComponentSettingsDialogController> settingsPresenterProvider) {
        super(eventBus, settingsPresenterProvider);
        onDispose(this::cancelRefresh);
    }

    @Override
    public void setAllowRefresh(final boolean allowRefresh) {
        this.allowRefresh = allowRefresh;
    }

    @Override
    public boolean isRefreshScheduled() {
        return refreshTimer != null;
    }

    @Override
    public void cancelRefresh() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
        }
        refreshTimer = null;
    }

    @Override
    public void scheduleRefresh() {
        // Schedule auto refresh after a query has finished.
        cancelRefresh();

        final Automate automate = getAutomate();
        if (isInitialised() && automate != null && automate.isRefresh()) {
            try {
                final int millis = RefreshInterval.getMillis(automate.getRefreshInterval());

                refreshTimer = new Timer() {
                    @Override
                    public void run() {
                        if (!isInitialised()) {
                            stop();
                        } else {
                            // Make sure search is currently inactive before we attempt to execute a new query.
                            if (allowRefresh && !isSearching()) {
                                RefreshableDashboardComponentController.this.run(false, false);
                            } else {
                                scheduleRefresh();
                            }
                        }
                    }
                };
                refreshTimer.schedule(millis);
            } catch (final RuntimeException e) {
                // Ignore as we cannot display this error now.
            }
        }
    }
}
