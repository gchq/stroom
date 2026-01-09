/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.util.string;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.google.common.base.Preconditions;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A partner to {@link stroom.util.shared.StringUtil} that is for server side
 * use only so can contain java regex goodness.
 */
public class StringUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StringUtil.class);

    public static int MAX_LONG_DIGITS = Long.toString(Long.MAX_VALUE).length();
    public static int MAX_INTEGER_DIGITS = Integer.toString(Integer.MAX_VALUE).length();

    // Split on one or more unix/windows/dos line ends
    private static final Pattern LINE_SPLIT_PATTERN = Pattern.compile("(\r?\n)");

    private static final Pattern WORD_SPLIT_PATTERN = Pattern.compile("((\r?\n)|\\s)");

    public static final char[] ALLOWED_CHARS_CASE_SENSITIVE_ALPHA_NUMERIC =
            "abcdefghijklmnopqrstuvwxyzABCDEFGJKLMNPRSTUVWXYZ0123456789".toCharArray();
    public static final char[] ALLOWED_CHARS_CASE_INSENSITIVE_ALPHA_NUMERIC =
            "ABCDEFGJKLMNPRSTUVWXYZ0123456789".toCharArray();
    public static final char[] ALLOWED_CHARS_HEX =
            "0123456789ABCDEF".toCharArray();
    // See Base58Check. This is NOT base58Check, but uses the same chars, ie. no 'o0il1' for readability
    public static final char[] ALLOWED_CHARS_BASE_58_STYLE = Base58.ALPHABET;

    // Hold values as both upper and lower to make lookup faster
    private static final Set<String> TRUE_VALUES = Stream.of(
                    "y", "yes", "true", "on", "enabled", "1"
            )
            .flatMap(str -> Stream.of(
                    str.toLowerCase(),
                    str.toUpperCase()
            ))
            .collect(Collectors.toSet());

    private static final String[] PAD_ARRAY = new String[MAX_LONG_DIGITS];

    static {
        final StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < PAD_ARRAY.length; i++) {
            PAD_ARRAY[i] = stringBuilder.toString();
            stringBuilder.append("0");
        }
    }


    private StringUtil() {
    }

    /**
     * Splits text into lines, where a line is delimited by \n or \r\n.
     *
     * @param trimLines If true, trims any leading/trailing space and ignores any blank lines
     */
    public static Stream<String> splitToLines(final String text,
                                              final boolean trimLines) {
        if (text == null || text.isEmpty() || (trimLines && text.isBlank())) {
            return Stream.empty();
        } else {
            Stream<String> stream = LINE_SPLIT_PATTERN.splitAsStream(text);

            if (trimLines) {
                stream = stream.map(String::trim)
                        .filter(str -> !str.isBlank());
            }

            return stream;
        }
    }

    /**
     * Trims all lines and removes any blank lines after trimming
     */
    public static String trimLines(final String text) {
        if (NullSafe.isEmptyString(text)) {
            return text;
        } else {
            return splitToLines(text, true)
                    .collect(Collectors.joining("\n"));
        }
    }

    /**
     * Splits text into words where delimiters are taken to be \n, \r\n or the regex \s.
     * Ignores blank words.
     */
    public static Stream<String> splitToWords(final String text) {
        if (text == null || text.isBlank()) {
            return Stream.empty();
        } else {

            return WORD_SPLIT_PATTERN.splitAsStream(text)
                    .filter(str -> !str.isBlank());
        }
    }

    public static String createRandomCode(final int length) {
        return createRandomCode(new SecureRandom(), length, ALLOWED_CHARS_BASE_58_STYLE);
    }

    public static String createRandomCode(final SecureRandom secureRandom,
                                          final int length) {
        return createRandomCode(secureRandom, length, ALLOWED_CHARS_BASE_58_STYLE);
    }

    public static String createRandomCode(final SecureRandom secureRandom,
                                          final int length,
                                          final char[] allowedChars) {
        Preconditions.checkArgument(length >= 1, "length must be >= 1");
        Objects.requireNonNull(allowedChars);
        final int count = allowedChars.length;
        Preconditions.checkArgument(count >= 1, "Need at least one allowedChar");

        final StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < length; i++) {
            stringBuilder.append(allowedChars[secureRandom.nextInt(count)]);
        }
        return stringBuilder.toString();
    }

    public static String ensureFullStop(final String str) {
        if (str == null) {
            return "";
        } else {
            final String str2 = str.stripTrailing();
            if (str2.isBlank()) {
                return str;
            } else if (str2.endsWith(".")) {
                return str2;
            } else {
                return str2 + ".";
            }
        }
    }

    /**
     * Convert a row/col position in a multi-line string into a zero based index
     * in the string. The index includes line breaks. Assumes only '\n' line breaks
     * are used.
     *
     * @param str    The string to evaluate row/col on.
     * @param rowIdx Zero based
     * @param colIdx Zero based
     * @return The zero based index of the row/col position in str.
     */
    public static int convertRowColToIndex(final String str, final int rowIdx, final int colIdx) {
        Objects.requireNonNull(str);
        try {
            int idx = 0;
            final int rowNum = rowIdx + 1;
            // All lines up to the one we want
            final List<String> lines = str.lines()
                    .limit(rowNum)
                    .collect(Collectors.toCollection(ArrayList::new));
            // Re-add a trailing blank line
            if (str.endsWith("\n")) {
                lines.add("");
            }

            if (str.isEmpty()) {
                if (rowIdx > 0) {
                    throw new IllegalArgumentException(LogUtil.message(
                            "rowIdx {} is invalid for an empty string.", rowIdx));
                } else if (colIdx > 0) {
                    throw new IllegalArgumentException(LogUtil.message(
                            "colIdx {} is invalid for an empty string.", colIdx));
                }
                return 0;

            } else {
                if (rowNum > lines.size()) {
                    throw new IllegalArgumentException(LogUtil.message(
                            "rowIdx {} is invalid. str only has {} lines", rowIdx, lines.size()));
                }

                for (int i = 0; i <= rowIdx; i++) {
                    // Allow for \n
                    final int lineLen = lines.get(i).length();
                    if (i < rowIdx) {
                        // Not our rowIdx yet so add whole line len, add one for the \n
                        idx += lineLen + 1;
                    } else {
                        // Desired rowIdx so add the col position
                        if (colIdx > lineLen) {
                            throw new IllegalArgumentException(LogUtil.message(
                                    "colIdx {} is invalid for rowIdx {} which has length {}",
                                    colIdx, rowIdx, lineLen));
                        }
                        idx = idx + colIdx;
                    }
                }
                return idx;
            }
        } catch (final Exception e) {
            throw e;
        }
    }

    /**
     * @return True if str is non-null and equal to one of (y|yes|true|on|enabled|1)
     * ignoring case. All other values return false.
     */
    public static boolean asBoolean(final String str) {
        if (str == null || str.isEmpty()) {
            return false;
        } else {
            // Assume the user has not used mixed case, else incur the cost of lower casing the string
            return TRUE_VALUES.contains(str)
                   || TRUE_VALUES.contains(str.toLowerCase());
        }
    }

    /**
     * @return Null if str is null. True if str is equal to one of (y|yes|true|on|enabled|1)
     * ignoring case, else false.
     */
    public static Boolean asNullableBoolean(final String str) {
        if (str == null) {
            return null;
        } else {
            return asBoolean(str);
        }
    }

    /**
     * Replaces repeated delimiters with a single delimiter and removes
     * any trailing or leading delimiters (repeated or not).
     * Useful for tidying up after a dumb join operation that adds delimiters
     * for null/empty values.
     * <p>
     * E.g. {@code ',,foo,,bar,,'} becomes {@code 'foo,bar'}
     * </p>
     * <p>
     * NOT to be used if you want null/empty items in your delimited list.
     * </p>
     * <p>
     * If str is null, returns null.
     * </p>
     */
    public static String deDupDelimiters(final String str,
                                         final char delimiter) {
        if (NullSafe.isEmptyString(str)) {
            return str;
        } else {
            final char[] outputArray = new char[str.length()];
            final char[] charArray = str.toCharArray();
            boolean lastCharWasDelimiter = false;
            boolean seenNonDelimiterChar = false;
            boolean hasChanged = false;
            int outputIdx = -1;
            final int startIdxInc = 0;
            for (final char chr : charArray) {
                if (chr == delimiter) {
                    if (!lastCharWasDelimiter && seenNonDelimiterChar) {
                        // Only append first delimiter
                        outputArray[++outputIdx] = chr;
                    } else {
                        // Skip this delimiter
                        hasChanged = true;
                    }
                    lastCharWasDelimiter = true;
                } else {
                    outputArray[++outputIdx] = chr;
                    lastCharWasDelimiter = false;
                    seenNonDelimiterChar = true;
                }
            }
            int endIdxInc = outputIdx;

            // Remove any trailing delimiters
            for (int i = endIdxInc; i >= 0; i--) {
                final char chr = outputArray[i];
                if (chr == delimiter) {
                    endIdxInc--;
                    hasChanged = true;
                } else {
                    break;
                }
            }
            if (!hasChanged) {
                // Optimisation to avoid a string creation if we haven't removed anything
                return str;
            } else {
                return new String(outputArray, 0, endIdxInc - startIdxInc + 1);
            }
        }
    }

    public static String getZeroPadding(final int padLen) {
        if (padLen < 0 || padLen > MAX_LONG_DIGITS) {
            throw new IllegalArgumentException("padLen must be >=0 and <= " + MAX_LONG_DIGITS);
        }
        return PAD_ARRAY[padLen];
    }

    /**
     * Zero pads positive longs to 19 digits to support up to {@link Long#MAX_VALUE}.
     *
     * @param val The long to pad
     * @return val padded to 19 digits.
     */
    public static String zeroPad(final long val) {
        return zeroPad(val, MAX_LONG_DIGITS);
    }

    /**
     * Zero pads positive longs to length digits
     *
     * @param val The long to pad
     * @return val padded to 19 digits.
     */
    public static String zeroPad(final long val, final int length) {
        if (val < 0) {
            throw new IllegalArgumentException("Negative values are not supported");
        }
        if (length < 0 || length > MAX_LONG_DIGITS) {
            throw new IllegalArgumentException("length must be >=0 and <= " + MAX_LONG_DIGITS);
        }
        String valStr = String.valueOf(val);
        final int valLen = valStr.length();
        final int padLen = length - valLen;
        if (padLen > 0) {
            valStr = PAD_ARRAY[padLen] + valStr;
        }
        return valStr;
    }

    /**
     * Pads positive integers to 10 digits to support up to {@link Integer#MAX_VALUE}.
     *
     * @param val The long to pad
     * @return val padded to 10 digits.
     */
    public static String zeroPad(final int val) {
        return zeroPad(val, MAX_INTEGER_DIGITS);
    }

    public static String zeroPad(final int val, final int length) {
        if (val < 0) {
            throw new IllegalArgumentException("Negative values are not supported");
        }
        if (length < 0 || length > MAX_INTEGER_DIGITS) {
            throw new IllegalArgumentException("length must be >=0 and <= " + MAX_INTEGER_DIGITS);
        }

        String valStr = String.valueOf(val);
        final int valLen = valStr.length();
        final int padLen = length - valLen;
        if (padLen > 0) {
            valStr = PAD_ARRAY[padLen] + valStr;
        }
        return valStr;
    }

    /**
     * Remove padding from the string, e.g. '000099' => 99
     *
     * @return The de-padded value, 0 if blank/null or -1 if not a number.
     */
    public static long dePadLong(final String paddedVal) {
        if (NullSafe.isBlankString(paddedVal)) {
            return -1L;
        } else {
            final int len = paddedVal.length();
            int startIdx = 0;
            while (startIdx < len) {
                if (paddedVal.charAt(startIdx) == '0') {
                    startIdx++;
                } else {
                    break;
                }
            }
            final String dePaddedId = paddedVal.substring(startIdx);
            if (dePaddedId.isBlank()) {
                return 0L;
            } else {
                try {
                    return Long.parseLong(dePaddedId);
                } catch (final NumberFormatException e) {
                    LOGGER.debug("Unable to convert '{}' to a long", dePaddedId, e);
                    return -1;
                }
            }
        }
    }

    /**
     * Remove padding from the string, e.g. '000099' => 99
     *
     * @return The de-padded value, 0 if blank/null or -1 if not a number.
     */
    public static int dePadInteger(final String paddedVal) {
        if (NullSafe.isBlankString(paddedVal)) {
            return -1;
        } else {
            final int len = paddedVal.length();
            int startIdx = 0;
            while (startIdx < len) {
                if (paddedVal.charAt(startIdx) == '0') {
                    startIdx++;
                } else {
                    break;
                }
            }
            final String dePaddedId = paddedVal.substring(startIdx);
            if (dePaddedId.isBlank()) {
                return 0;
            } else {
                try {
                    return Integer.parseInt(dePaddedId);
                } catch (final NumberFormatException e) {
                    LOGGER.debug("Unable to convert '{}' to an integer", dePaddedId, e);
                    return -1;
                }
            }
        }
    }

    /**
     * Much faster than using string length.
     * Positive values only.
     *
     * @return The number of digits in value, e.g. returns 1 for value 1, 2 for 99, etc.
     */
    public static int getDigitCount(final long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Positive values only, value: " + value);
        }
        if (value < 10_000_000_000L) { // 1 to 10 digits
            if (value < 100_000L) { // 1 to 5 digits
                if (value < 100L) {
                    if (value < 10L) {
                        return 1;
                    } else {
                        return 2;
                    }
                } else {
                    if (value < 1_000L) {
                        return 3;
                    } else if (value < 10_000L) {
                        return 4;
                    } else {
                        return 5;
                    }
                }
            } else { // 6 to 10 digits
                if (value < 10_000_000L) {
                    if (value < 1_000_000L) {
                        return 6;
                    } else {
                        return 7;
                    }
                } else {
                    if (value < 100_000_000L) {
                        return 8;
                    } else if (value < 1_000_000_000L) {
                        return 9;
                    } else {
                        return 10;
                    }
                }
            }
        } else { // 11 to 19 digits
            if (value < 100_000_000_000_000L) { // 11 to 14 digits
                if (value < 1_000_000_000_000L) {
                    if (value < 100_000_000_000L) {
                        return 11;
                    } else {
                        return 12;
                    }
                } else {
                    if (value < 10_000_000_000_000L) {
                        return 13;
                    } else {
                        return 14;
                    }
                }
            } else { // 15 to 19 digits
                if (value < 10_000_000_000_000_000L) {
                    if (value < 1_000_000_000_000_000L) {
                        return 15;
                    } else {
                        return 16;
                    }
                } else {
                    if (value < 100_000_000_000_000_000L) {
                        return 17;
                    } else if (value < 1_000_000_000_000_000_000L) {
                        return 18;
                    } else {
                        return 19;
                    }
                }
            }
        }
    }
}
