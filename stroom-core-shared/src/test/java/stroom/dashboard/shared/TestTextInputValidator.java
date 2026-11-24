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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestTextInputValidator {

    /// A blank value never produces a param, so it is valid whatever the format.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void testIsValid_blankValueAlwaysValid(final String value) {
        for (final TextInputFormat format : TextInputFormat.values()) {
            assertThat(TextInputValidator.isValid(format, "[a-z]+", value))
                    .as("format %s", format)
                    .isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"anything at all", "123", "now()", "!@#$%^"})
    void testIsValid_text(final String value) {
        assertThat(TextInputValidator.isValid(TextInputFormat.TEXT, null, value)).isTrue();
    }

    /// A null format is treated as text, e.g. for settings saved before formats existed.
    @Test
    void testIsValid_nullFormatIsText() {
        assertThat(TextInputValidator.isValid(null, null, "abc")).isTrue();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0|true",
            "123|true",
            "-123|true",
            "+123|true",
            "1.5|true",
            "1.|true",
            ".5|true",
            "-0.25|true",
            "1e10|true",
            "1.5E-3|true",
            " 42 |true",
            "abc|false",
            "1.2.3|false",
            "1,000|false",
            "--1|false",
            "1e|false",
            ".|false",
            "12abc|false",
            "0x1F|false",
    })
    void testIsValid_number(final String value, final boolean expected) {
        assertThat(TextInputValidator.isValid(TextInputFormat.NUMBER, null, value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Absolute ISO 8601
            "2026-01-31T13:45:00.000Z|true",
            "2026-01-31T13:45:00Z|true",
            "2026-01-31T13:45Z|true",
            "2026-01-31T13:45:00.123456789Z|true",
            "2026-01-31T13:45:00.000+10:00|true",
            "2026-01-31T13:45:00.000-05:30|true",
            // No offset is read in the user's time zone
            "2026-01-31T13:45:00|true",
            // Relative expressions
            "now()|true",
            "second()|true",
            "minute()|true",
            "hour()|true",
            "day()|true",
            "week()|true",
            "month()|true",
            "year()|true",
            "now()-1d|true",
            "now() - 1d|true",
            "day()+2h30m|true",
            "now()-1w+3d-12h|true",
            "month()-1M|true",
            "year()-1y|true",
            "2026-01-31T13:45:00.000Z-1d|true",
            " now()-1d |true",
            // Invalid
            // The server can't parse an offset without a colon
            "2026-01-31T13:45:00.000+1000|false",
            "yesterday|false",
            "2026-01-31|false",
            "31/01/2026 13:45|false",
            "2026-01-31 13:45:00|false",
            "now|false",
            "now()-1|false",
            "now()-1x|false",
            "now()1d|false",
            "-1d|false",
            "now()now()|false",
            "1700000000000|false",
            "today()|false",
    })
    void testIsValid_dateTime(final String value, final boolean expected) {
        assertThat(TextInputValidator.isValid(TextInputFormat.DATE_TIME, null, value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "[A-Z]{3}-\\d+|ABC-123|true",
            // Must match the whole value
            "[A-Z]{3}-\\d+|xABC-123|false",
            "[A-Z]{3}-\\d+|ABC-123x|false",
            "abc|abc|true",
            "abc|ABC|false",
            "(?i)abc|ABC|true",
    })
    void testIsValid_pattern(final String pattern, final String value, final boolean expected) {
        assertThat(TextInputValidator.isValid(TextInputFormat.PATTERN, pattern, value)).isEqualTo(expected);
    }

    /// Value is trimmed before matching, as the param value is trimmed.
    @Test
    void testIsValid_patternTrimsValue() {
        assertThat(TextInputValidator.isValid(TextInputFormat.PATTERN, "\\d+", " 123 ")).isTrue();
    }

    /// With no usable pattern nothing can conform, so no param is produced.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "[unclosed", "(", "*"})
    void testIsValid_patternMissingOrInvalid(final String pattern) {
        assertThat(TextInputValidator.isValid(TextInputFormat.PATTERN, pattern, "abc")).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            "[a-z]+|true",
            "\\d{3}|true",
            ".*|true",
            "NULL|false",
            "''|false",
            "[unclosed|false",
            "(|false",
            "*|false",
    })
    void testIsValidPattern(final String pattern, final boolean expected) {
        assertThat(TextInputValidator.isValidPattern(pattern)).isEqualTo(expected);
    }
}
