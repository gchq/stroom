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

        // If UTC then just display the `Z` suffix.
        if (Use.UTC.equals(use)) {
            pattern = pattern.replaceAll("Z", "[Z]");
        }
        // Ensure we haven't doubled up square brackets.
        pattern = pattern.replaceAll("\\[+", "[");
        pattern = pattern.replaceAll("]+", "]");

        // If UTC then just display the `Z` suffix.
        if (Use.UTC.equals(use)) {
            pattern = pattern.replaceAll("Z", "[Z]");
        }
        // Ensure we haven't doubled up square brackets.
        pattern = pattern.replaceAll("\\[+", "[");
        pattern = pattern.replaceAll("]+", "]");

        return MomentJs.nativeToDateString(ms, use.getDisplayValue(), pattern, zoneId, offsetMinutes);
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
}
