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

package stroom.preferences.client;

import stroom.query.api.UserTimeZone;
import stroom.query.api.UserTimeZone.Use;
import stroom.ui.config.shared.UserPreferences;
import stroom.util.shared.NullSafe;
import stroom.widget.customdatebox.client.MomentJs;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DateTimeFormatter {

    private final UserPreferencesManager userPreferencesManager;

    @Inject
    public DateTimeFormatter(final UserPreferencesManager userPreferencesManager) {
        this.userPreferencesManager = userPreferencesManager;
    }

    public String formatWithDuration(final Long ms) {
        if (ms == null) {
            return null;
        }

        final long now = System.currentTimeMillis();
        return format(ms) +
                " (" +
                MomentJs.humanise(ms - now, true) +
                ")";
    }

    public String format(final Long ms) {
        if (ms == null) {
            return null;
        }

        final TimeZoneSettings tz = getTimeZoneSettings();

        String pattern = tz.pattern;

        // If UTC then just display the `Z` suffix.
        if (Use.UTC.equals(tz.use)) {
            pattern = pattern.replaceAll("Z", "[Z]");
        }
        // Ensure we haven't doubled up square brackets.
        pattern = pattern.replaceAll("\\[+", "[");
        pattern = pattern.replaceAll("]+", "]");

        // If UTC then just display the `Z` suffix.
        if (Use.UTC.equals(tz.use)) {
            pattern = pattern.replaceAll("Z", "[Z]");
        }
        // Ensure we haven't doubled up square brackets.
        pattern = pattern.replaceAll("\\[+", "[");
        pattern = pattern.replaceAll("]+", "]");

        return MomentJs.nativeToDateString(ms, tz.use.getDisplayValue(), pattern, tz.zoneId, tz.offsetMinutes);
    }

    /**
     * Format an epoch-millis timestamp as a human-readable relative time string
     * (e.g. "just now", "5 minutes ago", "yesterday") relative to the given
     * {@code nowMs} value.  Calendar day boundaries (for "yesterday" / "N days ago")
     * are computed in the user's preferred timezone.
     */
    public String formatRelative(final long timeMs, final long nowMs) {
        final long diff = nowMs - timeMs;

        if (diff < ONE_SECOND) {
            return "just now";
        }

        final long seconds = diff / ONE_SECOND;
        if (seconds < 60) {
            return seconds == 1
                    ? "a second ago"
                    : seconds + " seconds ago";
        }

        final long minutes = diff / ONE_MINUTE;
        if (minutes < 60) {
            return minutes == 1
                    ? "a minute ago"
                    : minutes + " minutes ago";
        }

        final long hours = diff / ONE_HOUR;
        if (hours < 24) {
            return hours == 1
                    ? "an hour ago"
                    : hours + " hours ago";
        }

        // Use timezone-aware calendar day computation.
        final TimeZoneSettings tz = getTimeZoneSettings();
        final int days = MomentJs.daysBetween(
                timeMs, nowMs, tz.use.getDisplayValue(), tz.zoneId, tz.offsetMinutes);

        if (days == 1) {
            return "yesterday";
        } else if (days >= 365) {
            final int years = days / 365;
            return years == 1
                    ? "a year ago"
                    : years + " years ago";
        } else {
            return days + " days ago";
        }
    }

    private TimeZoneSettings getTimeZoneSettings() {
        Use use = Use.UTC;
        String pattern = "YYYY-MM-DD[T]HH:mm:ss.SSSZ";
        int offsetMinutes = 0;
        String zoneId = "UTC";

        final UserPreferences userPreferences = userPreferencesManager.getCurrentUserPreferences();
        if (userPreferences != null) {
            if (NullSafe.isNonBlankString(userPreferences.getDateTimePattern())) {

                final UserTimeZone timeZone = userPreferences.getTimeZone();
                if (timeZone != null) {
                    if (timeZone.getUse() != null) {
                        use = timeZone.getUse();
                    }

                    if (timeZone.getOffsetHours() != null) {
                        offsetMinutes += timeZone.getOffsetHours() * 60;
                    }
                    if (timeZone.getOffsetMinutes() != null) {
                        offsetMinutes += timeZone.getOffsetMinutes();
                    }

                    zoneId = timeZone.getId();
                }

                pattern = userPreferences.getDateTimePattern();
                pattern = convertJavaDateTimePattern(pattern);
            }
        }

        return new TimeZoneSettings(use, pattern, offsetMinutes, zoneId);
    }

    String convertJavaDateTimePattern(final String pattern) {
        String converted = pattern;
        converted = converted.replace('y', 'Y');
        converted = converted.replace('d', 'D');
        converted = converted.replaceAll("'", "");
        converted = converted.replaceAll("SSSXX", "SSSZ");
        converted = converted.replaceAll("T", "[T]");
        converted = converted.replaceAll("xxx", "Z");
        converted = converted.replaceAll("xx", "z");
        converted = converted.replaceAll("VV", "ZZ");

        // Deal with day name formatting.
        converted = converted.replaceAll("E{2,}", "dddd");
        converted = converted.replaceAll("E", "ddd");
        converted = converted.replaceAll("e{4,}", "dddd");
        converted = converted.replaceAll("e{3,}", "ddd");
        converted = converted.replaceAll("e+", "d");
        converted = converted.replaceAll("c{4,}", "dddd");
        converted = converted.replaceAll("c{3,}", "ddd");
        converted = converted.replaceAll("c+", "d");


        return converted;
    }

    // ---------------------------------------------------------------

    private static final long ONE_SECOND = 1000;
    private static final long ONE_MINUTE = ONE_SECOND * 60;
    private static final long ONE_HOUR = ONE_MINUTE * 60;

    private static class TimeZoneSettings {

        private final Use use;
        private final String pattern;
        private final int offsetMinutes;
        private final String zoneId;

        private TimeZoneSettings(final Use use,
                                 final String pattern,
                                 final int offsetMinutes,
                                 final String zoneId) {
            this.use = use;
            this.pattern = pattern;
            this.offsetMinutes = offsetMinutes;
            this.zoneId = zoneId;
        }
    }
}
