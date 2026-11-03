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

package stroom.search.elastic.client.presenter;

import java.util.ArrayList;
import java.util.List;

/// Sample size options and number formatting for the field statistics shown on the Info tab.
///
/// Formatting is done here rather than with `String.format`, which is not available in GWT client code.
public final class ElasticFieldStatsSupport {

    /// The sample sizes per shard the user can choose from.
    public static final List<Integer> SAMPLE_SIZES = List.of(1_000, 5_000, 10_000, 50_000);
    /// The sample size per shard used until the user chooses another.
    public static final int DEFAULT_SAMPLE_SIZE = 5_000;
    /// The number of days before now that the statistics time range starts at by default.
    public static final int DEFAULT_RANGE_DAYS = 30;

    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;

    private ElasticFieldStatsSupport() {
        // Static utility
    }

    /// @param maxSampleSize the largest sample size per shard the server allows, or `null` if not known
    /// @return the sample sizes that do not exceed `maxSampleSize`, always including the smallest
    public static List<Integer> getAllowedSampleSizes(final Integer maxSampleSize) {
        final List<Integer> allowed = new ArrayList<>();
        for (final Integer size : SAMPLE_SIZES) {
            if (maxSampleSize == null || size <= maxSampleSize || allowed.isEmpty()) {
                allowed.add(size);
            }
        }
        return allowed;
    }

    /// @param sampleSize    the chosen sample size per shard
    /// @param maxSampleSize the largest sample size per shard the server allows, or `null` if not known
    /// @return `sampleSize` if it is allowed, otherwise the largest allowed sample size below it, or the smallest
    /// allowed sample size if there is none below it
    public static int clampSampleSize(final int sampleSize, final Integer maxSampleSize) {
        final List<Integer> allowed = getAllowedSampleSizes(maxSampleSize);
        if (allowed.contains(sampleSize)) {
            return sampleSize;
        }
        int clamped = allowed.get(0);
        for (final Integer size : allowed) {
            if (size <= sampleSize) {
                clamped = size;
            }
        }
        return clamped;
    }

    /// @param nowMs the current time in epoch milliseconds
    /// @return the default start of the statistics time range, [#DEFAULT_RANGE_DAYS] days before `nowMs`
    public static long getDefaultFromMs(final long nowMs) {
        return nowMs - (DEFAULT_RANGE_DAYS * MILLIS_PER_DAY);
    }

    /// @param part  the count of matching items
    /// @param whole the total count
    /// @return `part` as a percentage of `whole`, or 0 if `whole` is not positive
    public static double getPercent(final long part, final long whole) {
        if (whole <= 0) {
            return 0;
        }
        return (part * 100.0) / whole;
    }

    /// @param part  the count of matching items
    /// @param whole the total count
    /// @return `part` as a percentage of `whole` with one decimal place, e.g. `12.5%`
    public static String formatPercent(final long part, final long whole) {
        return formatDecimal(getPercent(part, whole), 1) + "%";
    }

    /// @param count a count
    /// @return the count with comma thousands separators, e.g. `12,345`
    public static String formatCount(final long count) {
        final String digits = Long.toString(Math.abs(count));
        final StringBuilder sb = new StringBuilder();
        final int firstGroupLength = digits.length() % 3 == 0
                ? 3
                : digits.length() % 3;
        sb.append(digits.substring(0, firstGroupLength));
        for (int i = firstGroupLength; i < digits.length(); i += 3) {
            sb.append(',').append(digits.substring(i, i + 3));
        }
        return count < 0
                ? "-" + sb
                : sb.toString();
    }

    /// @param count a count, or `null`
    /// @return the count with comma thousands separators, or an empty string for `null`
    public static String formatCount(final Long count) {
        return count == null
                ? ""
                : formatCount(count.longValue());
    }

    /// @param value         a number
    /// @param decimalPlaces the number of decimal places to round to
    /// @return the number rounded to `decimalPlaces`, without trailing zeros after the decimal point
    public static String formatDecimal(final double value, final int decimalPlaces) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return Double.toString(value);
        }
        double scale = 1;
        for (int i = 0; i < decimalPlaces; i++) {
            scale *= 10;
        }
        final long scaled = Math.round(Math.abs(value) * scale);
        final long whole = (long) (scaled / scale);
        final long fraction = scaled - (long) (whole * scale);

        final StringBuilder sb = new StringBuilder();
        if (value < 0 && scaled != 0) {
            sb.append('-');
        }
        sb.append(formatCount(whole));
        if (decimalPlaces > 0 && fraction != 0) {
            String fractionDigits = Long.toString(fraction);
            while (fractionDigits.length() < decimalPlaces) {
                fractionDigits = "0" + fractionDigits;
            }
            while (fractionDigits.endsWith("0")) {
                fractionDigits = fractionDigits.substring(0, fractionDigits.length() - 1);
            }
            sb.append('.').append(fractionDigits);
        }
        return sb.toString();
    }

    /// @param value a number, or `null`
    /// @return the number rounded to three decimal places, or an empty string for `null`
    public static String formatDecimal(final Double value) {
        return value == null
                ? ""
                : formatDecimal(value, 3);
    }
}
