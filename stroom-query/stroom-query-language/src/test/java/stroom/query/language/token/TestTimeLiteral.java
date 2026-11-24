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
import java.time.Period;

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
            "1d,P1D,1d",
            "2w,P14D,14d",
            "18M,P18M,18M",
            "1y,P1Y,1y",
            "0d,P0D,0d",
            "0M,P0D,0d"})
    void testToPeriod(final String text, final String expected, final String expectedLiteral) {
        assertThat(TimeLiteral.isPeriod(TimeLiteral.parse(text))).isTrue();
        final Period period = TimeLiteral.toPeriod(TimeLiteral.parse(text));
        assertThat(period).isEqualTo(Period.parse(expected));
        assertThat(TimeLiteral.formatPeriod(period)).isEqualTo(expectedLiteral);
    }

    @Test
    void testFormatMixedPeriod() {
        assertThat(TimeLiteral.formatPeriod(Period.of(1, 2, 0))).isNull();
        assertThat(TimeLiteral.formatPeriod(Period.of(0, 2, 3))).isNull();
        assertThat(TimeLiteral.formatPeriod(Period.of(1, 0, 3))).isNull();
        assertThat(TimeLiteral.formatPeriod(Period.ofMonths(-3))).isEqualTo("-3M");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1ns", "1ms", "1s", "1m", "1h"})
    void testNotAPeriod(final String text) {
        assertThat(TimeLiteral.isPeriod(TimeLiteral.parse(text))).isFalse();
        assertThatThrownBy(() -> TimeLiteral.toPeriod(TimeLiteral.parse(text)))
                .isInstanceOf(DateTimeException.class)
                .hasMessageContaining("'" + text + "' is not a period");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2147483648d", "2147483647w", "2147483648M", "2147483648y"})
    void testPeriodTooLong(final String text) {
        assertThatThrownBy(() -> TimeLiteral.toPeriod(TimeLiteral.parse(text)))
                .isInstanceOf(DateTimeException.class)
                .hasMessageContaining("too long");
    }

    @ParameterizedTest
    @CsvSource({
            "0,0ms",
            "61000,1m1s",
            "-43200000,-12h",
            "-90061001,-1d1h1m1s1ms",
            "604800000,7d",
            "9223372036854775807,106751991167d7h12m55s807ms"})
    void testCompoundMillis(final long millis, final String expected) {
        assertThat(TimeLiteral.formatCompoundMillis(millis)).isEqualTo(expected);
        assertThat(TimeLiteral.parseCompoundMillis(expected)).isEqualTo(millis);
    }

    @ParameterizedTest
    @CsvSource({
            "1h 30m,5400000",
            "' - 1h  30m ',-5400000",
            "1w1d,691200000",
            "1s1s,2000"})
    void testParseCompoundMillis(final String text, final long expected) {
        assertThat(TimeLiteral.parseCompoundMillis(text)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "1M2d,months and years vary",
            "106751991167d8h,too long",
            "1h-2m,is not a number and a unit",
            "1 5m,is not a number and a unit",
            "1m s,is not a number and a unit"})
    void testBadCompoundMillis(final String text, final String message) {
        assertThatThrownBy(() -> TimeLiteral.parseCompoundMillis(text))
                .isInstanceOf(DateTimeException.class)
                .hasMessageContaining(message);
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
