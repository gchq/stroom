/*
 * Copyright 2024 Crown Copyright
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

import stroom.util.NullSafe;
import stroom.util.logging.LogUtil;

import com.google.common.base.Preconditions;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A partner to {@link stroom.util.shared.StringUtil} that is for server side
 * use only so can contain java regex goodness.
 */
public class StringUtil {

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
            int rowNum = rowIdx + 1;
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
        } catch (Exception e) {
            throw e;
        }
    }
}
