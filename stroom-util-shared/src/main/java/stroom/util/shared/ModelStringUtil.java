/*
 * Copyright 2016 Crown Copyright
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

import java.util.Comparator;

public final class ModelStringUtil {
    private static final int METRIC_DIV = 1000;
    private static final int IEC_BYTE_DIV = 1024;

    private static Divider[] SIZE_DIVIDER = new Divider[] {
            new Divider(1, ""),
            new Divider(METRIC_DIV, "K"),
            new Divider(METRIC_DIV, "M"),
            new Divider(METRIC_DIV, "G"),
            new Divider(METRIC_DIV, "T")
    };

    private static Divider[] METRIC_BYTE_SIZE_DIVIDER = new Divider[] {
            new Divider(1, "B", "b", "bytes", ""),
            new Divider(METRIC_DIV, "K", "KB"),
            new Divider(METRIC_DIV, "M", "MB"),
            new Divider(METRIC_DIV, "G", "GB"),
            new Divider(METRIC_DIV, "T", "TB")
    };

    private static Divider[] IEC_BYTE_SIZE_DIVIDER = new Divider[]{
            new Divider(1, "B", "b", "bytes", ""),
            new Divider(IEC_BYTE_DIV, "K", "KB", "KiB"),
            new Divider(IEC_BYTE_DIV, "M", "MB", "MiB"),
            new Divider(IEC_BYTE_DIV, "G", "GB", "GiB"),
            new Divider(IEC_BYTE_DIV, "T", "TB", "TiB")
    };

    /**
     * Format always append ms but parse consider ms and '' as the same thing
     */
    private static Divider[] TIME_SIZE_DIVIDER = new Divider[]{
            new Divider(1, "ms", ""),
            new Divider(1000, "s"),
            new Divider(60, "m"),
            new Divider(60, "h"),
            new Divider(24, "d")
    };

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
        if (streamSize == null) {
            return "";
        }
        return formatNumberString(streamSize, METRIC_BYTE_SIZE_DIVIDER);
    }

    /**
     * Return nice string like "25 B", "4KiB", "45 MiB", etc.
     */
    public static String formatIECByteSizeString(final Long streamSize) {
        if (streamSize == null) {
            return "";
        }
        return formatNumberString(streamSize, IEC_BYTE_SIZE_DIVIDER);
    }

    public static String formatDurationString(final Long ms) {
        if (ms == null) {
            return "";
        }
        return formatNumberString(ms, TIME_SIZE_DIVIDER);

    }

    private static final String formatNumberString(final double number, final Divider[] dividers) {
        double nextNumber = number;
        Divider lastDivider = null;

        for (final Divider divider : dividers) {
            if (nextNumber < divider.div) {
                break;
            }
            nextNumber = nextNumber / divider.div;
            lastDivider = divider;
        }

        // Show the first dec place if the number is smaller than 10
        if (lastDivider != null) {
            if (nextNumber < 10) {
                String str = String.valueOf(nextNumber);
                final int decPt = str.indexOf(".");
                if (decPt > 0 && decPt + 2 < str.length()) {
                    str = str.substring(0, decPt + 2);
                }
                return str + " " + lastDivider.unit[0];
            } else {
                return (long) nextNumber + " " + lastDivider.unit[0];
            }
        }

        return String.valueOf(nextNumber);
    }

    public static final Long parseNumberString(final String str) throws NumberFormatException {
        return parseNumberString(str, SIZE_DIVIDER);
    }

    public static final Long parseMetricByteSizeString(final String str) throws NumberFormatException {
        return parseNumberString(str, METRIC_BYTE_SIZE_DIVIDER);
    }

    public static final Long parseIECByteSizeString(final String str) throws NumberFormatException {
        return parseNumberString(str, IEC_BYTE_SIZE_DIVIDER);
    }

    public static final Long parseDurationString(final String str) throws NumberFormatException {
        return parseNumberString(str, TIME_SIZE_DIVIDER);
    }

    public static final Integer parseNumberStringAsInt(final String str) throws NumberFormatException {
        final Long num = parseNumberString(str, SIZE_DIVIDER);
        if (num == null) {
            return null;
        }
        if (num.longValue() > Integer.MAX_VALUE) {
            throw new NumberFormatException(str + " is too big for an int.  (Max value " + formatCsv(Integer.MAX_VALUE)
                    + " and you number was " + formatCsv(num) + ")");
        }
        if (num.longValue() < Integer.MIN_VALUE) {
            throw new NumberFormatException(str + " is too small for an int.  (Min value "
                    + formatCsv(Integer.MIN_VALUE) + " and you number was " + formatCsv(num) + ")");
        }
        return num.intValue();
    }

    public static final String format(final HasDisplayValue hasDisplayValue) {
        if (hasDisplayValue == null) {
            return "";
        } else {
            return hasDisplayValue.getDisplayValue();
        }
    }

    private static final Long parseNumberString(String str, final Divider[] dividers) throws NumberFormatException {
        if (str == null) {
            return null;
        }
        // Cat fix this findbug as code used in UI
        str = str.trim().toUpperCase();
        // Kill Quotes
        if (str.startsWith("\'") || str.startsWith("\"")) {
            str = str.substring(1);
        }
        if (str.endsWith("\'") || str.endsWith("\"")) {
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

        throw new NumberFormatException("Unable to parse " + str + " as suffix not recognised");

    }

    public static String formatCsv(final Number number) {
        if (number == null) {
            return "";
        }
        return formatCsv(number.longValue());

    }

    public static String formatCsv(final Long number) {
        if (number == null) {
            return "";
        }
        final String s = String.valueOf(number);
        final StringBuilder sb = new StringBuilder();

        for (int i = 0; i < s.length(); i++) {
            if ((s.length() - i) % 3 == 0) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
            }
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

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

    private static class Divider {
        final int div;
        final String[] unit;

        public Divider(final int div, final String... unit) {
            this.div = div;
            this.unit = unit;
        }
    }
}
