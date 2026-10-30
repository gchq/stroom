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

package stroom.dashboard.shared;

import stroom.util.shared.NullSafe;

import java.util.Objects;

/// Checks the value of a dashboard text input against its [TextInputFormat].
///
/// This class is shared with the GWT UI so only uses [String#matches(String)] for
/// regular expressions. GWT emulates that method and, as in Java, it must match the
/// whole string.
public final class TextInputValidator {

    private static final String NUMBER_REGEX = "[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?";

    // An absolute ISO 8601 date-time, with optional seconds, fraction and offset, e.g.
    // 2026-01-31T13:45:00.000Z or 2026-01-31T13:45:00.000+10:00. The server can't parse an
    // offset without a colon, e.g. +1000
    private static final String ISO_DATE_TIME_REGEX =
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d{1,9})?)?(Z|[+-]\\d{2}:\\d{2})?";

    // The date point functions understood by the server side date expression parser.
    private static final String DATE_POINT_REGEX = "(now|second|minute|hour|day|week|month|year)\\(\\)";

    // One or more signed durations, e.g. -1d, + 2h30m
    private static final String DURATIONS_REGEX = "(\\s*[+-]\\s*(\\d+[smhdwMy])+)*";

    private static final String DATE_TIME_REGEX = "\\s*("
                                                  + ISO_DATE_TIME_REGEX
                                                  + "|"
                                                  + DATE_POINT_REGEX
                                                  + ")"
                                                  + DURATIONS_REGEX
                                                  + "\\s*";

    private TextInputValidator() {
        // Static utility class.
    }

    /// Tests whether a text input value conforms to a format.
    ///
    /// A blank value is always valid, as an empty text input produces no parameter.
    ///
    /// @param format  The format to test against. `null` is treated as [TextInputFormat#TEXT].
    /// @param pattern The regular expression to match when `format` is [TextInputFormat#PATTERN].
    ///                It must match the whole value. Ignored for other formats.
    /// @param value   The value entered by the user.
    /// @return `true` if `value` conforms to `format`. Always `false` for
    /// [TextInputFormat#PATTERN] when `pattern` is blank or not a valid regular expression.
    public static boolean isValid(final TextInputFormat format,
                                  final String pattern,
                                  final String value) {
        if (NullSafe.isBlankString(value)) {
            return true;
        }
        final String trimmed = value.trim();
        final TextInputFormat nonNullFormat = Objects.requireNonNullElse(format, TextInputFormat.TEXT);
        return switch (nonNullFormat) {
            case TEXT -> true;
            case NUMBER -> trimmed.matches(NUMBER_REGEX);
            case DATE_TIME -> isValidDateTime(trimmed);
            case PATTERN -> matchesPattern(pattern, trimmed);
        };
    }

    /// Tests whether a string is a usable regular expression for [TextInputFormat#PATTERN].
    ///
    /// @param pattern The regular expression to test.
    /// @return `true` if `pattern` is non-blank and compiles.
    public static boolean isValidPattern(final String pattern) {
        if (NullSafe.isBlankString(pattern)) {
            return false;
        }
        try {
            //noinspection ResultOfMethodCallIgnored // Called only to compile the pattern
            "".matches(pattern);
            return true;
        } catch (final RuntimeException e) {
            return false;
        }
    }

    /// Tests whether a value is an absolute ISO 8601 date-time or a relative date expression,
    /// e.g. `now()-1d` or `day()+2h30m`.
    ///
    /// @param value The value to test.
    /// @return `true` if `value` is a date-time the dashboard query can parse.
    static boolean isValidDateTime(final String value) {
        return value != null && value.matches(DATE_TIME_REGEX);
    }

    private static boolean matchesPattern(final String pattern, final String value) {
        if (!isValidPattern(pattern)) {
            return false;
        }
        return value.matches(pattern);
    }
}
