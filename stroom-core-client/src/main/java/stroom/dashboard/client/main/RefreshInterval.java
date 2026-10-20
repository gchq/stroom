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

import stroom.dashboard.shared.Automate;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;

/// The rules for a query component's auto refresh interval, e.g. `10s` or `5m`: what may be
/// entered in its settings, and how long a refresh then waits.
public final class RefreshInterval {

    /// The shortest interval, 10 seconds.
    public static final long MIN_MILLIS = 10_000L;
    /// The longest interval, 24 days, as a browser timer can't wait much longer (about 24.8 days).
    public static final long MAX_MILLIS = 24L * 24 * 60 * 60 * 1000;

    static final String NOT_SET_MESSAGE = "A query refresh interval must be provided";
    static final String UNREADABLE_MESSAGE = "Query refresh interval must be a duration, e.g. 10s, 5m or 1h";
    static final String TOO_SHORT_MESSAGE = "Query refresh interval must be greater than or equal to 10 seconds";
    static final String TOO_LONG_MESSAGE = "Query refresh interval must be 24 days or less";

    private RefreshInterval() {
        // Static utility
    }

    /// Checks an interval entered in a query component's settings.
    ///
    /// @param interval The interval entered, e.g. `10s`, which may be null or blank.
    /// @return What is wrong with it, or null if it is valid.
    public static String validate(final String interval) {
        if (NullSafe.isBlankString(interval)) {
            return NOT_SET_MESSAGE;
        }
        final Long millis = parse(interval);
        if (millis == null) {
            return UNREADABLE_MESSAGE;
        } else if (millis < MIN_MILLIS) {
            return TOO_SHORT_MESSAGE;
        } else if (millis > MAX_MILLIS) {
            return TOO_LONG_MESSAGE;
        }
        return null;
    }

    /// Works out how long a refresh waits. An interval that isn't set, or can't be read (e.g. for a
    /// component saved before the interval had a default), is the default interval, so that the
    /// component still refreshes. One under 10 seconds is 10 seconds, and one over 24 days is 24
    /// days.
    ///
    /// @param interval The refresh interval, e.g. `10s`, which may be null or blank.
    /// @return The milliseconds to wait.
    public static int getMillis(final String interval) {
        Long millis = parse(interval);
        if (millis == null) {
            millis = parse(Automate.DEFAULT_REFRESH_INTERVAL);
        }
        return (int) Math.min(Math.max(millis, MIN_MILLIS), MAX_MILLIS);
    }

    // The interval in milliseconds, or null if it is blank or can't be read.
    private static Long parse(final String interval) {
        if (NullSafe.isBlankString(interval)) {
            return null;
        }
        try {
            return ModelStringUtil.parseDurationString(interval);
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
