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

package stroom.util.shared;

import stroom.docref.HasDisplayValue;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;

public final class ModelStringUtil {

    public static final int DEFAULT_SIGNIFICANT_FIGURES = 3;

    private static final int METRIC_DIV = 1_000;
    private static final int IEC_BYTE_DIV = 1_024;

    private static final Divider[] SIZE_DIVIDER = new Divider[]{
            new Divider(1, ""),
            new Divider(METRIC_DIV, "K"),
            new Divider(METRIC_DIV, "M"),
            new Divider(METRIC_DIV, "G"),
            new Divider(METRIC_DIV, "T"),
            new Divider(METRIC_DIV, "P")
    };

    private static final Divider[] METRIC_BYTE_SIZE_DIVIDER = new Divider[]{
            new Divider(1, "B", "b", "bytes", ""),
            new Divider(METRIC_DIV, "K", "KB"),
            new Divider(METRIC_DIV, "M", "MB"),
            new Divider(METRIC_DIV, "G", "GB"),
            new Divider(METRIC_DIV, "T", "TB"),
            new Divider(METRIC_DIV, "P", "PB")
    };

    private static final Divider[] IEC_BYTE_SIZE_DIVIDER = new Divider[]{
            new Divider(1, "B", "b", "bytes", ""),
            new Divider(IEC_BYTE_DIV, "K", "KB", "KiB"),
            new Divider(IEC_BYTE_DIV, "M", "MB", "MiB"),
            new Divider(IEC_BYTE_DIV, "G", "GB", "GiB"),
            new Divider(IEC_BYTE_DIV, "T", "TB", "TiB"),
            new Divider(IEC_BYTE_DIV, "P", "PB", "PiB")
    };

    /**
     * Format always append ms but parse consider ms and '' as the same thing
     */
    private static final Divider[] TIME_SIZE_DIVIDER = new Divider[]{
            new Divider(1, "ms", ""),
            new Divider(1000, "s"),
            new Divider(60, "m"),
            new Divider(60, "h"),
            new Divider(24, "d")
    };

//    private static final NumberFormat ONE_DECIMAL_POINT_WITH_TRAILING_ZEROS_FORMAT = new DecimalFormat("0.0");
//    private static final NumberFormat ONE_DECIMAL_POINT_WITHOUT_TRAILING_ZEROS_FORMAT = new DecimalFormat("0.#");
//    private static final NumberFormat NO_DECIMAL_POINTS = new DecimalFormat("0");

//    private static Divider[] TIME_SIZE_DIVIDER_PARSE = new Divider(1, "", new Divider(1, " ms",
//            new Divider(1000, " s", new Divider(60, " m", new Divider(60, " h", new Divider(24, " d", null))))));

    private ModelStringUtil() {
        // Utility class.
    }

    /**
     * Pad a string out (yes I know apache commons can do this but it's used by
     * GWT).
     *
     * @param amount pad size
     * @param in     string
     * @return padded value.
     */
    public static String zeroPad(final int amount, final String in) {
        final int left = amount - in.length();
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < left; i++) {
            out.append("0");
        }
        out.append(in);
        return out.toString();
    }

    /**
     * Return nice string like "25 B", "4 kB", "45 MB", etc.
     */
    public static String formatMetricByteSizeString(final Long streamSize) {
        return formatMetricByteSizeString(streamSize, false);
    }

    /**
     * Return nice string like "25 B", "4 kB", "45 MB", etc.
     */
    public static String formatMetricByteSizeString(final Long streamSize,
                                                    final boolean stripTrailingZeros) {
        if (streamSize == null) {
            return "";
        }
        return formatNumberString(streamSize, METRIC_BYTE_SIZE_DIVIDER, stripTrailingZeros);
    }

    /**
     * Return nice string like "25 B", "4 K", "45 M", rounded to the desired significantFigures.
     *
     * @param significantFigures The number of significant digits required, however if the number of
     *                           integer digits is greater that will be used. This is to ensure we always
     *                           show full precision for the integer part, e.g. output '1023B' when
     *                           significantFigures is 3.
     */
    public static String formatMetricByteSizeString(final Long streamSize,
                                                    final boolean stripTrailingZeros,
                                                    final int significantFigures) {
        if (streamSize == null) {
            return "";
        }
        return formatNumberString(streamSize, METRIC_BYTE_SIZE_DIVIDER, stripTrailingZeros, significantFigures);
    }

    /**
     * Return nice string like "25 B", "4 K", "45 M", etc.
     */
    public static String formatIECByteSizeString(final Long streamSize) {
        return formatIECByteSizeString(streamSize, false);
    }

    /**
     * Return nice string like "25 B", "4 K", "45 M", etc.
     */
    public static String formatIECByteSizeString(final Long streamSize,
                                                 final boolean stripTrailingZeros) {
        if (streamSize == null) {
            return "";
        }
        return formatNumberString(streamSize, IEC_BYTE_SIZE_DIVIDER, stripTrailingZeros);
    }

    /**
     * Return nice string like "25 B", "4 K", "45 M", rounded to the desired significantFigures.
     *
     * @param significantFigures The number of significant digits required, however if the number of
     *                           integer digits is greater that will be used. This is to ensure we always
     *                           show full precision for the integer part, e.g. output '1023B' when
     *                           significantFigures is 3.
     */
    public static String formatIECByteSizeString(final Long streamSize,
                                                 final boolean stripTrailingZeros,
                                                 final int significantFigures) {
        if (streamSize == null) {
            return "";
        }
        return formatNumberString(streamSize, IEC_BYTE_SIZE_DIVIDER, stripTrailingZeros, significantFigures);
    }

    /**
     * Experimental idea to show a size indicator next to the IEC size.
     * Leaving it here in case it gets used.
     */
    public static String formatIECByteSizeStringWithSizeIndicator(final Long streamSize) {
        final String val = formatIECByteSizeString(streamSize, false);
        if (val.isEmpty()) {
            return "";
        } else if (val.endsWith("B")) {
            return val + " ▎";
        } else if (val.endsWith("K")) {
            return val + " ▌";
        } else if (val.endsWith("M")) {
            return val + " ▊";
        } else {
            return val + " █";
        }
    }

    /**
     * Formats a duration in millis to human-readable form, e.g. 999ms, 1.0s, 10m, 20h
     */
    public static String formatDurationString(final Long ms) {
        return formatDurationString(ms, false);
    }

    /**
     * Formats a duration in millis to human-readable form, e.g. 999ms, 1.0s, 10m, 20h
     *
     * @param stripTrailingZeros If true, any trailing zeros in the decimal part are ommitted.
     */
    public static String formatDurationString(final Long ms, final boolean stripTrailingZeros) {
        if (ms == null) {
            return "";
        }
        return formatNumberString(ms, TIME_SIZE_DIVIDER, stripTrailingZeros);

    }

    private static String formatNumberString(final double number,
                                             final Divider[] dividers,
                                             final boolean stripTrailingZeros) {
        return formatNumberString(number, dividers, stripTrailingZeros, null);
    }

    private static String formatNumberString(final double number,
                                             final Divider[] dividers,
                                             final boolean stripTrailingZeros,
                                             final Integer significantFigures) {
        double nextNumber = number;
        Divider lastDivider = dividers[0];

        for (final Divider divider : dividers) {
            if (nextNumber < divider.div) {
                break;
            }
            nextNumber = nextNumber / divider.div;
            lastDivider = divider;
        }

        // GWT doesn't support Java's NumberFormat so forced to use BigDecimal
        BigDecimal bigDecimal = BigDecimal.valueOf(nextNumber);
        final String suffix;
        if (lastDivider != null) {
            // Show the first dec place if the number is smaller than 10
            if (significantFigures == null) {
                final int scale = nextNumber < 10
                        ? 1
                        : 0;
                bigDecimal = bigDecimal.setScale(scale, RoundingMode.HALF_UP);
            } else {
                if (significantFigures <= 0) {
                    throw new IllegalArgumentException("significantFigures should be > 0.");
                }
                // Because we are always dealing in numbers between 0 and 1024 we need to add
                // the additional sig fig if we go over 1000, so we don't lost the last digit,
                // but for fractional stuff, 3 sig fig is fine.
                final long valAsLong = bigDecimal.longValue();
                final int precision;
                if (significantFigures >= 4) {
                    precision = significantFigures;
                } else {
                    final int integerDigitCount = Long.toString(valAsLong).length();
                    precision = Math.max(significantFigures, integerDigitCount);
                }
                bigDecimal = bigDecimal.round(new MathContext(precision, RoundingMode.HALF_UP));
            }

            suffix = lastDivider.unit[0];
        } else {
            // No dividers so leave do nothing to the bigDecimal
            suffix = "";
        }

        if (stripTrailingZeros) {
            bigDecimal = bigDecimal.stripTrailingZeros();
        }
        return bigDecimal.toPlainString() + suffix;
    }

    /**
     * Parses human-readable numbers that use SI unit prefixes into a {@link Long}.
     * e.g. '1.1k' => 1100, '1M' => 1_000_000. The case of the unit prefixes is ignored.
     * Valid unit prefixes are 'k', 'M', 'G', 'T', 'P'.
     * If there is no unit prefix then the number is converted from the string as is.
     *
     * @return The number or null for a null/empty input.
     * @throws NumberFormatException If it can't be parsed.
     */
    public static Long parseNumberString(final String str) throws NumberFormatException {
        return parseNumberString(str, SIZE_DIVIDER);
    }

    /**
     * Parses human-readable byte counts that use metric unit prefixes into a {@link Long}.
     * e.g. '1.1kB' => 1100, '1MB' => 1_000_000. The case of the unit prefixes is ignored.
     * Valid unit prefixes (case-insensitive) are:
     * <pre>{@code "B" "bytes"}</pre>
     * <pre>{@code "k" "kB"}</pre>
     * <pre>{@code "M" "MB"}</pre>
     * <pre>{@code "G" "GB"}</pre>
     * <pre>{@code "T" "TB"}</pre>
     * <pre>{@code "P" "PB"}</pre>
     * If there is no unit prefix then the number is assumed to be in bytes and is
     * converted from the string as is.
     *
     * @return The number or null for a null/empty input.
     * @throws NumberFormatException If it can't be parsed.
     */
    public static Long parseMetricByteSizeString(final String str) throws NumberFormatException {
        return parseNumberString(str, METRIC_BYTE_SIZE_DIVIDER);
    }

    /**
     * Parses human-readable byte counts that use IEC binary unit prefixes into a {@link Long}.
     * e.g. '1kiB' => 1024, '1MB' => 1_048_576. The case of the unit prefixes is ignored.
     * Valid unit prefixes (case-insensitive) are:
     * <pre>{@code "B" "bytes"}</pre>
     * <pre>{@code "k" "kB"}</pre>
     * <pre>{@code "M" "MB"}</pre>
     * <pre>{@code "G" "GB"}</pre>
     * <pre>{@code "T" "TB"}</pre>
     * <pre>{@code "P" "PB"}</pre>
     * If there is no unit prefix then the number is assumed to be in bytes and is
     * converted from the string as is.
     *
     * @return The number or null for a null/empty input.
     * @throws NumberFormatException If it can't be parsed.
     */
    public static Long parseIECByteSizeString(final String str) throws NumberFormatException {
        return parseNumberString(str, IEC_BYTE_SIZE_DIVIDER);
    }

    /**
     * Parses human-readable time durations that use time units into milliseconds.
     * Only supports integer/long values.
     * e.g. '10s' => 10_000, '10' => 10. The case of the unit prefixes is ignored.
     * Valid unit prefixes (case-insensitive) are:
     * <pre>{@code "ms"}</pre>
     * <pre>{@code "s"}</pre>
     * <pre>{@code "m"}</pre>
     * <pre>{@code "h"}</pre>
     * <pre>{@code "d"}</pre>
     * If there is no unit prefix then the number is assumed to be in millis and is
     * converted from the string as is.
     *
     * @return The number or null for a null/empty input.
     * @throws NumberFormatException If it can't be parsed.
     */
    public static Long parseDurationString(final String str) throws NumberFormatException {
        return parseNumberString(str, TIME_SIZE_DIVIDER);
    }

    /**
     * Parses human-readable numbers that use SI unit prefixes into a {@link Integer}.
     * e.g. '1.1k' => 1100, '1M' => 1_000_000. The case of the unit prefixes is ignored.
     * Valid unit prefixes are 'k', 'M', 'G', 'T', 'P'.
     * If there is no unit prefix then the number is converted from the string as is.
     *
     * @return The number or null for a null/empty input.
     * @throws NumberFormatException If it can't be parsed or the number is too large
     *                               for an {@link Integer}
     */
    public static Integer parseNumberStringAsInt(final String str) throws NumberFormatException {
        final Long num = parseNumberString(str, SIZE_DIVIDER);
        if (num == null) {
            return null;
        }
        if (num > Integer.MAX_VALUE) {
            throw new NumberFormatException(str + " is too big for an int.  (Max value " + formatCsv(Integer.MAX_VALUE)
                                            + " and you number was " + formatCsv(num) + ")");
        }
        if (num < Integer.MIN_VALUE) {
            throw new NumberFormatException(str + " is too small for an int.  (Min value " +
                                            formatCsv(Integer.MIN_VALUE) +
                                            " and you number was " +
                                            formatCsv(num) +
                                            ")");
        }
        return num.intValue();
    }

    /**
     * @return The display value of {@code hasDisplayValue}
     */
    public static String format(final HasDisplayValue hasDisplayValue) {
        if (hasDisplayValue == null) {
            return "";
        } else {
            return hasDisplayValue.getDisplayValue();
        }
    }

    private static Long parseNumberString(String str, final Divider[] dividers) throws NumberFormatException {
        if (str == null) {
            return null;
        }
        // Cat fix this findbug as code used in UI
        str = str.trim().toUpperCase();
        // Kill Quotes
        if (str.startsWith("'") || str.startsWith("\"")) {
            str = str.substring(1);
        }
        if (str.endsWith("'") || str.endsWith("\"")) {
            str = str.substring(0, str.length() - 1);
        }

        final StringBuilder numPart = new StringBuilder();
        final StringBuilder suffixPart = new StringBuilder();
        boolean inNum = true;

        for (int i = 0; i < str.length(); i++) {
            final char c = str.charAt(i);
            if (inNum) {
                if (Character.isDigit(c) || c == '.') {
                    numPart.append(c);
                } else {
                    inNum = false;
                }
            }
            if (!inNum) {
                suffixPart.append(c);
            }
        }

        if (numPart.length() == 0) {
            return null;
        }
        final double d = Double.parseDouble(numPart.toString());

        final String suffix = suffixPart.toString().trim();

        long multiplier = 1;

        for (final Divider divider : dividers) {
            multiplier *= divider.div;
            for (final String unit : divider.unit) {
                if (unit.equalsIgnoreCase(suffix)) {
                    return (long) (multiplier * d);
                }
            }
        }

        throw new NumberFormatException("Unable to parse " + str + " as suffix " + suffix + " not recognised");

    }

    /**
     * Formats a number as a long with thousands delimiters, i.e. #,###,###
     *
     * @return The formatted number or an empty string if null.
     */
    public static String formatCsv(final Number number) {
        if (number == null) {
            return "";
        }
        return formatCsv(number.longValue());
    }

    /**
     * Formats a long with thousands delimiters, i.e. #,###,###
     *
     * @return The formatted number or an empty string if null.
     */
    public static String formatCsv(final Long number) {
        if (number == null) {
            return "";
        }
        final String s = String.valueOf(number);
        return addThousandsSeparators(s);
    }

    /**
     * Formats a double with thousands delimiters and a fixed
     * number of decimal places, i.e. {@code #,###,###.##}.
     *
     * @return The formatted number or an empty string if null.
     */
    public static String formatCsv(final Double number, final int decimalPlaces) {
        return formatCsv(number, decimalPlaces, false);
    }

    /**
     * Formats a double with thousands delimiters and a maximum
     * number of decimal places, i.e. {@code #,###,###.##}.
     * If {@code stripTrailingZeros} is true all trailing zeros in the decimal
     * part will be omitted.
     *
     * @return The formatted number or an empty string if null.
     */
    public static String formatCsv(final Double number,
                                   final int decimalPlaces,
                                   final boolean stripTrailingZeros) {
        if (decimalPlaces < 0) {
            throw new IllegalArgumentException("decimalPlaces must be > 0");
        }
        if (number == null) {
            return "";
        }
        // GWT so no DecimalFormat :-(
        BigDecimal bigDecimal = new BigDecimal(number)
                .setScale(decimalPlaces, RoundingMode.HALF_UP);

        if (stripTrailingZeros) {
            bigDecimal = bigDecimal.stripTrailingZeros();
        }

        final String s = bigDecimal.toPlainString();
        return addThousandsSeparators(s);
    }

    /**
     * Formats the string as lowerCamelCase.
     */
    public static String toCamelCase(final String string) {
        final char[] chars = string.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            final char c = chars[i];
            if (Character.isUpperCase(c)) {
                // If we have moved beyond the first character, aren't yet at
                // the end and the next character is lower case then this must
                // be the first capital of the next word so don't lower case and
                // stop any further modification.
                if (i > 0 && i < chars.length - 1 && Character.isLowerCase(chars[i + 1])) {
                    break;
                } else {
                    chars[i] = Character.toLowerCase(c);
                }
            } else {
                break;
            }
        }
        return new String(chars);
    }

    /**
     * Separates the parts in lowerCamelCase or UpperCamelCase with spaces,
     * e.g. 'lower Camel Case' and 'Upper Camel Case'.
     */
    public static String toDisplayValue(final String string) {
        if (string == null) {
            return "null";
        }
        final char[] chars = string.toCharArray();
        final char[] output = new char[chars.length * 2];

        int i = 0;
        int j = 0;
        for (; i < chars.length; i++, j++) {
            final char c = chars[i];
            if (i > 0 && i < chars.length - 1 && Character.isUpperCase(c) && Character.isLowerCase(chars[i + 1])) {
                // If we have moved beyond the first character, aren't yet at
                // the end and the next character is lower case then this must
                // be the first capital of the next word so insert a space.
                output[j++] = ' ';
            }

            output[j] = c;
        }
        return new String(output, 0, j);
    }

    public static Comparator<String> pathComparator() {
        return (o1, o2) -> {
            final int min = Math.min(o1.length(), o2.length());
            for (int i = 0; i < min; i++) {
                final int r = ((Character) o1.charAt(i)).compareTo(o2.charAt(i));
                if (r != 0) {
                    return r;
                }
            }
            return ((Integer) o1.length()).compareTo(o2.length());
        };
    }

    private static String addThousandsSeparators(final String number) {
        final String integerPart;
        final String decimalPart;

        if (number.contains(".")) {
            final String[] parts = number.split("\\.");
            integerPart = parts[0];
            decimalPart = "." + parts[1];
        } else {
            integerPart = number;
            decimalPart = "";
        }
        final StringBuilder sb = new StringBuilder();

        // GWT so no NumberFormat :-(
        for (int i = 0; i < integerPart.length(); i++) {
            if ((integerPart.length() - i) % 3 == 0) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
            }
            sb.append(integerPart.charAt(i));
        }
        sb.append(decimalPart);
        return sb.toString();
    }

    private static class Divider {

        final int div;
        final String[] unit;

        Divider(final int div, final String... unit) {
            this.div = div;
            this.unit = unit;
        }

        @Override
        public String toString() {
            return "Divider{" +
                   "div=" + div +
                   ", unit=" + Arrays.toString(unit) +
                   '}';
        }
    }
}
