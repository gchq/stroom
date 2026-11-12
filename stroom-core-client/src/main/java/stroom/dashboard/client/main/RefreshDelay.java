/*
 * Copyright 2026 Crown Copyright
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

import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.time.SimpleDuration;

/// Works out how long a dashboard component waits before rerunning its query automatically.
public final class RefreshDelay {

    /// The shortest wait of any dashboard component.
    public static final long DASHBOARD_MIN_MILLIS = 10_000L;

    private RefreshDelay() {
        // Static methods only.
    }

    /// The wait before an automatic rerun: the longest of the interval the user chose, the
    /// dashboard's own minimum, and the data source's minimum.
    ///
    /// @param refreshInterval      The interval the user chose, e.g. `30s`.
    /// @param dataSourceMinimum    The data source's minimum, or `null` for none.
    /// @return The wait in milliseconds.
    /// @throws RuntimeException If the chosen interval is empty or cannot be read.
    public static long millis(final String refreshInterval, final SimpleDuration dataSourceMinimum) {
        final Long chosen = ModelStringUtil.parseDurationString(refreshInterval);
        if (chosen == null) {
            throw new IllegalArgumentException("No refresh interval has been set");
        }
        final long dataSourceMillis = dataSourceMinimum == null
                ? 0L
                : dataSourceMinimum.getApproxMillis();
        return Math.max(chosen, Math.max(DASHBOARD_MIN_MILLIS, dataSourceMillis));
    }
}
