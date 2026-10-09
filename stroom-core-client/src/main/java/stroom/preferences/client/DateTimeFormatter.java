/*
 * Copyright 2021 Crown Copyright
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

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DateTimeFormatter {

    private static final long ONE_SECOND = 1000;
    private static final long ONE_MINUTE = ONE_SECOND * 60;
    private static final long ONE_HOUR = ONE_MINUTE * 60;

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

    // STROOMWORKS-LOCAL: formatCompact and toCompactPattern are a local addition -
    // KEEP LOCAL ON MERGE FROM master. Upstream has only format(Long). FloorMap's timeline
    // axis and scrub tooltip cannot fit the user's full pattern, but must still show the
    // same instant, zone and field order as every other time in the UI - they previously
    // hand-rolled UTC and ignored the user's preferences entirely. Dropping these breaks that.
    /**
     * Formats {@code ms} like {@link #format(Long)} but without sub-minute precision or a
     * time-zone suffix, for places with a hard width limit.
     *
     * <p>The pattern is <strong>derived from the user's own</strong> rather than replaced,
     * so their field order, separators and 12- or 24-hour choice are all preserved:
     * {@code dd/MM/yyyy HH:mm:ss} yields {@code 20/08/2026 09:26}, and
     * {@code MM/dd/yyyy hh:mm:ss a} yields {@code 08/20/2026 09:26 am}. That matters because
     * the user's {@code dateTimePattern} preference is the only date-localisation mechanism
     * Stroom has — there is no locale detection — so imposing a fixed pattern here would
     * override the one control they have.</p>
     *
     * <p><strong>"Compact" means less precision, not a guaranteed width.</strong> A verbose
     * preference such as {@code EEEE, dd MMMM yyyy HH:mm:ss} still yields a long string;
     * seconds and zone are dropped, day and month names are the user's business.</p>
     *
     * @param ms epoch milliseconds, or {@code null}
     * @return the formatted time, or {@code null} if {@code ms} is {@code null}
     */
    public String formatCompact(final Long ms) {
        if (ms == null) {
            return null;
        }
        final TimeZoneSettings tz = getTimeZoneSettings();
        // Shortening removes the zone token, so the UTC `Z` -> `[Z]` handling that
        // format(Long) needs does not apply here.
        return MomentJs.nativeToDateString(
                ms, tz.use.getDisplayValue(), toCompactPattern(tz.pattern),
                tz.zoneId, tz.offsetMinutes);
    }

    /**
     * Strips sub-minute precision and any time-zone token from a Moment.js pattern,
     * leaving everything else — including literals and separators — untouched.
     *
     * <p>Package-private so it can be unit-tested directly, as
     * {@link #convertJavaDateTimePattern(String)} is: the rendering around it needs
     * Moment.js and so cannot run on the JVM.</p>
     *
     * <p>Bracketed literals are masked before matching, so a literal {@code [T]} or a
     * literal containing {@code ss} is never mistaken for a token. If stripping would leave
     * nothing usable — a pattern that was <em>only</em> seconds, say — the original is
     * returned rather than an empty pattern.</p>
     *
     * @param momentPattern a Moment.js pattern, or {@code null}
     * @return the shortened pattern, or {@code null} if the input was {@code null}
     */
    String toCompactPattern(final String momentPattern) {
        if (momentPattern == null) {
            return null;
        }

        // Mask [literals] so their contents cannot match a token below. The sentinels are
        // private-use characters, not control characters: String.trim() strips anything
        // <= U+0020, so a low sentinel at the end of a pattern was eaten before it could be
        // unmasked - silently corrupting any pattern that ended in a literal.
        final List<String> literals = new ArrayList<>();
        final StringBuilder masked = new StringBuilder();
        int i = 0;
        while (i < momentPattern.length()) {
            final char c = momentPattern.charAt(i);
            if (c == '[') {
                final int close = momentPattern.indexOf(']', i);
                if (close > i) {
                    masked.append('\uE000').append(literals.size()).append('\uE001'); // mask sentinels
                    literals.add(momentPattern.substring(i, close + 1));
                    i = close + 1;
                    continue;
                }
            }
            masked.append(c);
            i++;
        }

        String p = masked.toString();

        // Order matters. Zone first: a trailing zone token otherwise sits immediately
        // after the seconds and blocks the "not followed by a letter" guard below.
        p = p.replaceAll("\\s*(ZZ|Z|zz|z)", "");

        // Then precision, each in two passes: with its separator where there is one, then
        // bare. A single pass cannot do both - a leading "not preceded by a letter" guard
        // is evaluated at the start of the match, so with ":ss" it rejects the match that
        // would have eaten the colon and then accepts the one that leaves it behind.
        p = p.replaceAll("[^A-Za-z\uE000\uE001]S{1,9}", ""); // sentinels are not separators
        p = p.replaceAll("(?<![A-Za-z])S{1,9}(?![A-Za-z])", "");
        p = p.replaceAll("[^A-Za-z\uE000\uE001]ss(?![A-Za-z])", ""); // sentinels are not separators
        p = p.replaceAll("(?<![A-Za-z])ss(?![A-Za-z])", "");

        // Tidy up whatever the removals left dangling.
        p = p.replaceAll("\\s{2,}", " ");
        p = p.replaceAll("[\\s.,:;#/\\-]+$", "");
        p = p.trim();

        // Restore the literals.
        for (int n = 0; n < literals.size(); n++) {
            p = p.replace("\uE000" + n + "\uE001", literals.get(n)); // unmask
        }

        // If nothing time-bearing survived, the pattern was too specialised to shorten.
        return p.matches(".*[YMDHhmAa].*") ? p : momentPattern;
    }

    public String format(final Long ms) {
        return format(ms, null);
    }

    public String format(final Long ms, final String momentPatternOverride) {
        if (ms == null) {
            return null;
        }

        final TimeZoneSettings tz = getTimeZoneSettings();

        String pattern = tz.pattern;

        if (momentPatternOverride != null) {
            pattern = momentPatternOverride;
        } else {
            // If UTC then just display the `Z` suffix.
            if (Use.UTC.equals(tz.use)) {
                pattern = pattern.replaceAll("Z", "[Z]");
            }
            // Ensure we haven't doubled up square brackets.
            pattern = pattern.replaceAll("\\[+", "[");
            pattern = pattern.replaceAll("]+", "]");
        }

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
                    use = timeZone.getUse();

                    offsetMinutes += timeZone.getOffsetHours() * 60;
                    offsetMinutes += timeZone.getOffsetMinutes();

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
