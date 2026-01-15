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

package stroom.dashboard.client.main;

import stroom.dashboard.shared.Automate;
import stroom.util.shared.ModelStringUtil;

import com.google.gwt.user.client.Timer;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public abstract class AbstractRefreshableComponentPresenter<V extends View>
        extends AbstractComponentPresenter<V> implements Refreshable, Queryable {

    private static final int TEN_SECONDS = 10000;

    private boolean allowRefresh = true;
    private Timer refreshTimer;

    public AbstractRefreshableComponentPresenter(final EventBus eventBus, final V view,
                                                 final Provider<?> settingsPresenterProvider) {
        super(eventBus, view, settingsPresenterProvider);
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
        if (isInitialised() && automate.isRefresh()) {
            try {
                final String interval = automate.getRefreshInterval();
                int millis = ModelStringUtil.parseDurationString(interval).intValue();

                // Ensure that the refresh interval is not less than 10 seconds.
                millis = Math.max(millis, TEN_SECONDS);

                refreshTimer = new Timer() {
                    @Override
                    public void run() {
                        if (!isInitialised()) {
                            stop();
                        } else {
                            // Make sure search is currently inactive before we attempt to execute a new query.
                            if (allowRefresh && !isSearching()) {
                                AbstractRefreshableComponentPresenter.this.run(false, false);
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
