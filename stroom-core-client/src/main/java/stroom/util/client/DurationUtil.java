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

package stroom.util.client;

import stroom.pathways.shared.otel.trace.NanoDuration;

/**
 * Utility methods for formatting durations and counts in a human-readable form.
 *
 * <p>All methods are pure static helpers with no GWT-specific imports, making
 * the class safe for use in both GWT client code and server-side compilation.
 */
public final class DurationUtil {

    private DurationUtil() {
    }

    /**
     * Formats a {@link NanoDuration} into a human-readable string that scales
     * automatically from nanoseconds up to hours:
     * <ul>
     *   <li>&lt; 1 µs  → {@code Xns}</li>
     *   <li>&lt; 1 ms  → {@code X.Xµs}</li>
     *   <li>&lt; 1 s   → {@code X.Xms}</li>
     *   <li>&lt; 1 min → {@code X.Xs}</li>
     *   <li>&lt; 1 hr  → {@code Xm Xs}</li>
     *   <li>&ge; 1 hr  → {@code Xh Xm Xs}</li>
     * </ul>
     *
     * @param duration the duration to format, or {@code null}
     * @return a human-readable string, or {@code ""} if {@code duration} is {@code null}
     */
    public static String formatDuration(final NanoDuration duration) {
        if (duration == null) {
            return "";
        }
        final long n = duration.getNanos();
        if (n < 0) {
            return "0ns";
        }
        if (n < 1_000L) {
            return n + "ns";
        }
        if (n < 1_000_000L) {
            return formatOneDecimal(n / 1_000.0) + "µs";
        }
        if (n < 1_000_000_000L) {
            return formatOneDecimal(n / 1_000_000.0) + "ms";
        }
        if (n < 60_000_000_000L) {
            // < 1 minute — show seconds to 1 decimal place
            return formatOneDecimal(n / 1_000_000_000.0) + "s";
        }
        // >= 1 minute — break into h / m / s (integer seconds)
        final long totalSecs = n / 1_000_000_000L;
        final long hours = totalSecs / 3_600L;
        final long mins = (totalSecs % 3_600L) / 60L;
        final long secs = totalSecs % 60L;
        final StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        sb.append(mins).append("m ").append(secs).append('s');
        return sb.toString();
    }

    /**
     * Formats a {@code double} to at most one decimal place, trimming a
     * trailing {@code .0} (e.g. {@code 456.0} → {@code "456"}).
     * Avoids {@code DecimalFormat} which is not available in GWT.
     *
     * @param value the value to format
     * @return the formatted string
     */
    private static String formatOneDecimal(final double value) {
        final long rounded = Math.round(value * 10);
        final long whole = rounded / 10;
        final long frac = rounded % 10;
        if (frac == 0) {
            return Long.toString(whole);
        }
        return whole + "." + frac;
    }

}
