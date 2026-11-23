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

package stroom.query.language.token;

import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DateTimeException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestTimeLiteral {

    @ParameterizedTest
    @CsvSource({
            "1ns,NANOSECONDS",
            "1ms,MILLISECONDS",
            "1s,SECONDS",
            "1m,MINUTES",
            "1h,HOURS",
            "1d,DAYS",
            "1w,WEEKS",
            "1M,MONTHS",
            "1y,YEARS"})
    void testParse(final String text, final TimeUnit timeUnit) {
        assertThat(TimeLiteral.parse(text)).isEqualTo(new SimpleDuration(1L, timeUnit));
        assertThat(text).matches(TimeLiteral.REGEX);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1H", "1D", "1W", "1Y", "1MS", "1mo", "1x"})
    void testUnknownUnit(final String text) {
        assertThatThrownBy(() -> TimeLiteral.parse(text))
                .isInstanceOf(DateTimeException.class)
                .hasMessageContaining("Unknown unit '" + text.substring(1) + "' in '" + text + "'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "1", "d", "-1d", "1.5h", "1 d", "1d 2h", "1d2", "P1D"})
    void testNotALiteral(final String text) {
        assertThatThrownBy(() -> TimeLiteral.parse(text))
                .isInstanceOf(DateTimeException.class)
                .hasMessageContaining("is not a number and a unit");
    }

    @Test
    void testTooLarge() {
        assertThatThrownBy(() -> TimeLiteral.parse("99999999999999999999s"))
                .isInstanceOf(DateTimeException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void testCompound() {
        assertThat(TimeLiteral.parseCompound("1d2h3ms")).containsExactly(
                new SimpleDuration(1L, TimeUnit.DAYS),
                new SimpleDuration(2L, TimeUnit.HOURS),
                new SimpleDuration(3L, TimeUnit.MILLISECONDS));
        assertThatThrownBy(() -> TimeLiteral.parse("1d2h"))
                .isInstanceOf(DateTimeException.class)
                .hasMessageContaining("is not one number and one unit");
    }

    @ParameterizedTest
    @CsvSource({
            "2000000ns,2",
            "5ms,5",
            "1s,1000",
            "1m,60000",
            "1h,3600000",
            "1d,86400000",
            "1w,604800000"})
    void testToExactMillis(final String text, final long expected) {
        assertThat(TimeLiteral.toExactMillis(TimeLiteral.parse(text))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "0,0ms",
            "5,5ms",
            "1500,1500ms",
            "61000,61s",
            "5400000,90m",
            "3600000,1h",
            "129600000,36h",
            "604800000,168h",
            "9223372036854775807,9223372036854775807ms"})
    void testFormatExactMillis(final long millis, final String expected) {
        assertThat(TimeLiteral.formatExactMillis(millis)).isEqualTo(expected);
        assertThat(TimeLiteral.toExactMillis(TimeLiteral.parse(expected))).isEqualTo(millis);
    }

    @ParameterizedTest
    @CsvSource({
            "1M,months and years vary",
            "1y,months and years vary",
            "1ns,held to the millisecond",
            "9223372036854775807s,too long",
            "9223372036854775807w,too long"})
    void testNoExactMillis(final String text, final String message) {
        assertThatThrownBy(() -> TimeLiteral.toExactMillis(TimeLiteral.parse(text)))
                .isInstanceOf(DateTimeException.class)
                .hasMessageContaining(message);
    }
}
