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

import java.util.Collection;

/**
 * String utilities for client side or shared code
 */
public class StringUtil {


    private StringUtil() {
    }

    /**
     * @return obj.toString() or an empty string if obj is null.
     */
    public static String toString(final Object obj) {
        if (obj == null) {
            return "";
        }
        return obj.toString();
    }

    public static String prefix(final String string, final char prefixChar, final int count) {
        final StringBuilder stringBuilder = new StringBuilder();

        //noinspection StringRepeatCanBeUsed
        for (int i = 0; i < count; i++) {
            stringBuilder.append(prefixChar);
        }
        if (string != null) {
            stringBuilder.append(string);
        }
        return stringBuilder.toString();
    }

    public static String suffix(final String string, final char prefixChar, final int count) {
        final StringBuilder stringBuilder = new StringBuilder();
        if (string != null) {
            stringBuilder.append(string);
        }
        //noinspection StringRepeatCanBeUsed
        for (int i = 0; i < count; i++) {
            stringBuilder.append(prefixChar);
        }
        return stringBuilder.toString();
    }

    /**
     * @return str as is, unless it is empty or blank, in which case return null.
     */
    public static String blankAsNull(final String str) {
        if (str != null && (str.isEmpty() || isBlank(str))) {
            return null;
        } else {
            return str;
        }
    }

    /**
     * GWT doesn't support {@link String#isBlank()}
     *
     * @return True if str is null, empty or contains only whitespace.
     */
    public static boolean isBlank(final String str) {
        if (str == null) {
            return true;
        } else if (str.isEmpty()) {
            return true;
        } else {
            return str.chars()
                    .allMatch(Character::isWhitespace);
        }
    }

    /**
     * Converts user input, that has been double-quoted to include whitespace,
     * into the un-quoted value. Any double quotes inside the outermost double
     * quotes will be escaped with a single backslash. Any Unquoted whitespace
     * or whitespace outside the outer double quotes will be trimmed.
     * See the tests for examples.
     */
    public static String removeWhitespaceQuoting(final String userText) {
        final String storedText;
        if (userText == null || userText.isEmpty()) {
            storedText = userText;
        } else {
            final String trimmedStr = userText.trim();
            if (trimmedStr.startsWith("\"")
                && trimmedStr.endsWith("\"")) {

                final int openQuoteIdx = trimmedStr.indexOf("\"");
                final int endQuoteIdx = trimmedStr.lastIndexOf("\"");
                if (openQuoteIdx == endQuoteIdx) {
                    // '"'
                    storedText = trimmedStr;
                } else {
                    // Everything inside the db quotes, un-escaping any dbl quote used
                    storedText = trimmedStr.substring(openQuoteIdx + 1, endQuoteIdx)
                            .replace("\\\"", "\"");
                }
            } else {
                storedText = trimmedStr.replace("\\\"", "\"");
            }
        }
        return storedText;
    }

    /**
     * Converts a system stored value into one for display in the UI.
     * If the value has leading or trailing whitespace it will be quoted and any
     * double quotes escaped.
     * See the tests for examples.
     */
    public static String addWhitespaceQuoting(final String storedText) {
        final String userText;
        if (storedText == null || storedText.isEmpty()) {
            userText = storedText;
        } else {
            final String escapedText = storedText.replace("\"", "\\\"");
            if (storedText.startsWith(" ")
                || storedText.startsWith("\t")
                || storedText.endsWith(" ")
                || storedText.endsWith("\t")) {
                // leading/trailing whitespace so dbl quote the whole thing
                // ' he said "hello" ' => '" he said \"hello\" "'
                userText = "\""
                           + escapedText
                           + "\"";
            } else {
                userText = escapedText;
            }
        }
        return userText;
    }

    /**
     * @return "s" if count is > 1
     */
    public static String pluralSuffix(final int count) {
        return count > 1
                ? "s"
                : "";
    }

    /**
     * @return "s" if {@link Collection#size()} is > 1
     */
    public static String pluralSuffix(final Collection<?> collection) {
        return collection != null && collection.size() > 1
                ? "s"
                : "";
    }

    /**
     * @return "s" if count is > 1
     */
    public static String pluralSuffix(final long count) {
        return count > 1
                ? "s"
                : "";
    }

    /**
     * @return singular + "s" if count is > 1, else singular
     * e.g.
     * <pre>{@code pluralSuffix("document", count)}</pre>
     */
    public static String plural(final String singular, final int count) {
        return count > 1
                ? singular + "s"
                : singular;
    }

    /**
     * @return singular + "s" if {@link Collection#size()} is > 1, else singular
     * e.g.
     * <pre>{@code pluralSuffix("document", list)}</pre>
     */
    public static String plural(final String singular, final Collection<?> collection) {
        return collection != null && collection.size() > 1
                ? singular + "s"
                : singular;
    }

    /**
     * @return singular + "s" if count is > 1, else singular
     * e.g.
     * <pre>{@code pluralSuffix("document", count)}</pre>
     */
    public static String plural(final String singular, final long count) {
        return count > 1
                ? singular + "s"
                : singular;
    }

    /**
     * @return plural if count is > 1, else singular.
     * e.g.
     * <pre>{@code plural("has", "have", list)}</pre>
     */
    public static String plural(final String singular,
                                final String plural,
                                final Collection<?> collection) {
        return collection != null && collection.size() > 1
                ? plural
                : singular;
    }

    /**
     * @return plural if count is > 1, else singular
     * e.g.
     * <pre>{@code plural("has", "have", count)}</pre>
     */
    public static String plural(final String singular,
                                final String plural,
                                final int count) {
        return count > 1
                ? plural
                : singular;
    }

    /**
     * @return plural if count is > 1, else singular
     * e.g.
     * <pre>{@code plural("has", "have", list)}</pre>
     */
    public static String plural(final String singular,
                                final String plural,
                                final long count) {
        return count > 1
                ? plural
                : singular;
    }

    /**
     * Null safe trimming of leading/trailing whitespace.
     */
    public static String trimWhitespace(final String userText) {
        if (userText == null || userText.isEmpty()) {
            return userText;
        } else {
            return userText.trim();
        }
    }

    /**
     * Surrounds str with single quotes. Does not do any escaping. Intended for display purposes.
     * if str is null returns {@code ''}.
     */
    public static String singleQuote(final String str) {
        if (str == null) {
            return "''";
        } else {
            return "'" + str + "'";
        }
    }

    /**
     * @return "Yes" if bool is non-null and true, else "No"
     */
    public static String asYesNo(final Boolean bool) {
        return bool != null && bool
                ? "Yes"
                : "No";
    }

    public static String formatDouble(final double d) {
        final double rounded = Math.round(d * 100D) / 100D;
        final String string = Double.toString(rounded);
        final int index = string.indexOf(".");
        if (index == -1) {
            return string;
        }

        final char[] chars = string.toCharArray();
        int end = chars.length - 1;
        for (; end >= index; end--) {
            if (chars[end] != '0') {
                break;
            }
        }
        if (end == index) {
            return string.substring(0, index);
        } else if (end < chars.length - 1) {
            return string.substring(0, end + 1);
        }
        return string;
    }
}
