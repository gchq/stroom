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

package stroom.query.language.functions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.ParseException;
import java.time.DateTimeException;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The arithmetic and comparison of dates, durations and periods, as it is written in expressions.
class TestDateArithmetic {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Periods are added on the local line. The clocks go forward on 31 March and back on 27 October 2024.
            "2024-03-30T12:00:00Z + 1d|2024-03-31T11:00:00.000Z",
            "2024-03-30T12:00:00Z + 24h|2024-03-31T12:00:00.000Z",
            "2024-10-26T12:00:00+01:00 + 1w|2024-11-02T12:00:00.000Z",
            "2024-01-31T09:00:00Z + 1M|2024-02-29T09:00:00.000Z",
            "2024-02-29T09:00:00Z + 1y|2025-02-28T09:00:00.000Z",
            "1M + 2024-01-31T09:00:00Z|2024-02-29T09:00:00.000Z",
            "2024-03-31T09:00:00+01:00 - 1M|2024-02-29T09:00:00.000Z",
            "2024-03-31T12:00:00+01:00 - 1d|2024-03-30T12:00:00.000Z",
            // 01:30 doesn't happen on 31 March, so it moves forward by the hour that is skipped.
            "2024-03-30T01:30:00Z + 1d|2024-03-31T01:30:00.000Z",
            // Left to right.
            "2024-01-30T09:00:00Z + 1M + 1d|2024-03-01T09:00:00.000Z",
            "2024-01-30T09:00:00Z + 1d + 1M|2024-02-29T09:00:00.000Z",
            "2024-01-31T09:00:00Z + 2 * 1M|2024-03-31T08:00:00.000Z",
            "2024-01-31T09:00:00Z + 1M + 2h|2024-02-29T11:00:00.000Z",
            // Durations.
            "2024-01-01T00:00:00Z + 90m|2024-01-01T01:30:00.000Z",
            "90m + 2024-01-01T00:00:00Z|2024-01-01T01:30:00.000Z",
            "2024-01-01T00:00:00Z - 90m|2023-12-31T22:30:00.000Z",
            "sum(2024-03-30T12:00:00Z, 1d)|2024-03-31T11:00:00.000Z"})
    void datesWithAmounts(final String expression, final String expected) throws ParseException {
        assertThat(eval(expression))
                .isEqualTo(ValDate.create(DateUtil.parseNormalDateTimeString(expected)));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "2024-01-02T00:00:00Z - 2024-01-01T12:00:00Z|43200000",
            "2024-01-01T12:00:00Z - 2024-01-02T00:00:00Z|-43200000",
            "1h + 30m|5400000",
            "1h - 30m|1800000",
            "1h * 1.5|5400000",
            "3 * 20m|3600000",
            "1h / 4|900000",
            "10ms / 3|3",
            "-(1h)|-3600000",
            // A period of days stands in for a duration where the other value is one.
            "1d + 2h|93600000",
            "2h + 1d|93600000",
            "1w - 1h|601200000",
            "1h - 1d|-82800000",
            "parseDuration(1d)|86400000",
            "parseDuration(61s)|61000",
            "parseISODuration(90m)|5400000",
            "parseDuration('1m1s')|61000",
            "parseDuration('-12h')|-43200000",
            "parseDuration('1d 2h 30m')|95400000",
            "1000 + 1h|3601000",
            "1h + 1000|3601000",
            "1h - 1000|3599000",
            "1000 - 1h|-3599000",
            "1h + 1.6|3600002",
            "1d + 5|86400005",
            "max(1h, 25m)|3600000",
            "min(1h, 25m)|1500000",
            "min(90m, 1d)|5400000",
            "max(90m, 1d)|86400000",
            "max(1d, 1w)|604800000"})
    void durations(final String expression, final long expected) throws ParseException {
        assertThat(eval(expression)).isEqualTo(ValDuration.create(expected));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1y + 2M + 3d|1|2|3",
            "1y - 2M|1|-2|0",
            "1w + 1d|0|0|8",
            "3 * 1M|0|3|0",
            "1M * 3|0|3|0",
            "1d * 2|0|0|2",
            "-1M|0|-1|0",
            "-(1y + 1d)|-1|0|-1"})
    void periods(final String expression, final int years, final int months, final int days) throws ParseException {
        assertThat(eval(expression)).isEqualTo(ValPeriod.create(years, months, days));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "90m / 1h|1.5",
            "1d / 1h|24",
            "36h / 1d|1.5",
            "1w / 1d|7",
            // Numbers are still used as milliseconds.
            "2024-01-01T00:00:00Z + 1000|1704067201000",
            "2024-01-01T00:00:00Z / 1000|1704067200",
            "1000 / 1s|1"})
    void numbers(final String expression, final double expected) throws ParseException {
        assertThat(eval(expression)).isEqualTo(ValDouble.create(expected));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "2024-01-01T00:00:00Z + 2024-01-01T00:00:00Z|Can't add a date to a date",
            "1h - 2024-01-01T00:00:00Z|Can't subtract a date from a duration",
            "1M - 2024-01-01T00:00:00Z|Can't subtract a date from a period",
            "2024-01-01T00:00:00Z * 2|Can't multiply a date",
            "2024-01-01T00:00:00Z / 1h|Can't divide a date by a duration",
            "2024-01-01T00:00:00Z / 1d|Can't divide a date by a period",
            "2024-01-01T00:00:00Z / 0|is not a number",
            "2 / 2024-01-01T00:00:00Z|Can't divide by a date",
            "2024-01-01T00:00:00Z / 2024-01-01T00:00:00Z|Can't divide by a date",
            "1M + 1h|Can't mix a duration and a period",
            "1h - 1y|Can't mix a duration and a period",
            "1M + 5|can't be used as a number",
            "1h * 1h|Can't multiply a duration by a duration",
            "1h * 1d|Can't multiply a duration by a period",
            "1M * 1.5|only be multiplied by a whole number",
            "1M * 3000000000|too long",
            "1M / 2|Can't divide a period by a number",
            "1d / 2|Can't divide a period by a number",
            "1y / 1M|can't be used as a number",
            "1h / 0|is not a number",
            "1h * 10000000000000|overflow",
            "5 % 1M|can't be used as a number",
            "1h = 1M|Can't compare a duration and a period",
            "1M > 1h|A period of months or years can't be compared",
            "1M < 31d|A period of months or years can't be compared",
            "1y <= 12M|A period of months or years can't be compared",
            "1y >= 1y|A period of months or years can't be compared",
            "1d < 1M|A period of months or years can't be compared",
            "1M < 'foo'|A period of months or years can't be compared",
            "1M = 5|Can't compare a double and a period",
            "2024-01-01T00:00:00Z = 1d|Can't compare a date and a period",
            "1M != 5|Can't compare a double and a period",
            "2024-01-01T00:00:00Z < 1d|Can't compare a date and a period",
            "round(1d)|Can't round a period",
            "parseDuration(parsePeriod('P1M'))|can't be used as a duration",
            "average(parsePeriod('P1M'), 1y)|Can't average a period",
            "max(1M, 1000h)|A period of months or years can't be compared",
            "min(1000h, 1M)|A period of months or years can't be compared",
            "min(1M, 1d, 1h)|A period of months or years can't be compared",
            "min(1h, 1d, 1M)|A period of months or years can't be compared",
            "max(1M)|A period of months or years can't be compared",
            "max(1h, 1d, 1y)|A period of months or years can't be compared",
            "max(1y, 1y)|A period of months or years can't be compared",
            "inRange(1d, 1h, 1M)|A period of months or years can't be compared",
            "inRange(12M, 1y, 2y)|A period of months or years can't be compared",
            "inRange(2024-01-01T00:00:00Z, 1d, 7d)|Can't compare a date and a period",
            "inRange(1M, 1h, 2h)|A period of months or years can't be compared",
            "inRange(1h, 1m, 1M)|A period of months or years can't be compared",
            "formatDuration(1M)|can't be used as a duration",
            "formatDuration(parsePeriod('P1M'))|can't be used as a duration",
            "formatISODuration(1y)|can't be used as a duration",
            "formatISODuration(parsePeriod('P1Y'))|can't be used as a duration",
            "parseISODuration(1M)|can't be used as a duration",
            "parseDuration('106751991167d8h')|is too long"})
    void errors(final String expression, final String message) throws ParseException {
        final Val val = eval(expression);
        assertThat(val.type()).as(val.toString()).isEqualTo(Type.ERR);
        assertThat(val.toString()).contains(message);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "25h > 1d|true",
            "24h = 1d|true",
            "1d >= 25h|false",
            "86400000 = 1d|true",
            "1w = 7d|true",
            "1y = 12M|false",
            "1y = 1y|true",
            "1y != 12M|true",
            "1d < 1w|true",
            "1w <= 7d|true",
            "inRange(2h, 1h, 1d)|true",
            "inRange(2d, 1h, 1d)|false",
            "inRange(2d, 1d, 1w)|true",
            "1M < ${field}|false",
            "1M = ${field}|false"})
    void comparisons(final String expression, final boolean expected) throws ParseException {
        assertThat(eval(expression)).isEqualTo(ValBoolean.create(expected));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "round(2024-01-01T00:00:00.123Z)|date",
            "floor(2024-01-01T00:00:00.123Z)|date",
            "ceiling(1h)|duration",
            "round(1.5)|double",
            "1d|period",
            "negate(1h)|duration",
            "toLong(1d)|long"})
    void types(final String expression, final String expected) throws ParseException {
        assertThat(eval("typeOf(" + expression + ")")).isEqualTo(ValString.create(expected));
    }

    @Test
    void fieldsAreWorkedOutAsConstantsAre() throws ParseException {
        final Val date = ValDate.create(DateUtil.parseNormalDateTimeString("2024-03-30T12:00:00Z"));

        assertThat(DateFunctionTestUtil.eval(DateFunctionTestUtil.LONDON, "${field} + 1d", date))
                .isEqualTo(ValDate.create(DateUtil.parseNormalDateTimeString("2024-03-31T11:00:00Z")));
        assertThat(DateFunctionTestUtil.eval(DateFunctionTestUtil.NO_SETTINGS, "${field} + 1d", date))
                .isEqualTo(ValDate.create(DateUtil.parseNormalDateTimeString("2024-03-31T12:00:00Z")));
        assertThat(DateFunctionTestUtil.eval(
                DateFunctionTestUtil.LONDON, "${field} + 1M", ValDuration.create(1)).type()).isEqualTo(Type.ERR);
        assertThat(DateFunctionTestUtil.eval(DateFunctionTestUtil.LONDON, "${field} - 1M", ValNull.INSTANCE))
                .isEqualTo(ValNull.INSTANCE);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "formatISODuration(61s)|PT1M1S",
            "formatDuration(90m)|1.5h",
            "formatISODuration(1d)|PT24H",
            "concat(2024-01-01T00:01:01Z - 2024-01-01T00:00:00Z, '')|1m1s",
            "concat(2024-01-01T00:00:00Z - 2024-01-01T12:00:00Z, '')|-12h"})
    void durationText(final String expression, final String expected) throws ParseException {
        assertThat(eval(expression)).isEqualTo(ValString.create(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "${field} + 1M", "${field} + 1h", "1M + ${field}", "1h + ${field}", "${field} - 1M", "${field} - 1h"})
    void nothingIsMadeFromABlank(final String expression) throws ParseException {
        assertThat(eval(expression)).isEqualTo(ValNull.INSTANCE);
    }

    @Test
    void numbersAreStillAddedToABlank() throws ParseException {
        assertThat(eval("${field} + 5")).isEqualTo(ValDouble.create(5));
    }

    @Test
    void totalsSkipBlanks() {
        final Calculator calculator = Add.Calc.createForSum(null);
        final ValPeriod month = ValPeriod.create(0, 1, 0);
        final ValDuration hour = ValDuration.create(3_600_000);

        assertThat(calculator.calc(calculator.calc(month, ValNull.INSTANCE), month))
                .isEqualTo(ValPeriod.create(0, 2, 0));
        assertThat(calculator.calc(ValNull.INSTANCE, month)).isEqualTo(month);
        assertThat(calculator.calc(calculator.calc(hour, ValNull.INSTANCE), hour))
                .isEqualTo(ValDuration.create(7_200_000));
    }

    @Test
    void totalsSkipText() {
        final Calculator calculator = Add.Calc.createForSum(null);
        final ValDuration hour = ValDuration.create(3_600_000);
        final ValPeriod month = ValPeriod.create(0, 1, 0);

        for (final Val text : List.of(ValString.create(""), ValString.create("foo"))) {
            for (final Val val : List.of(hour, month)) {
                assertThat(calculator.calc(text, val)).isEqualTo(val);
                assertThat(calculator.calc(val, text)).isEqualTo(val);
            }
        }
    }

    @Test
    void inRangeOfAField() throws ParseException {
        assertThat(DateFunctionTestUtil.eval(
                DateFunctionTestUtil.LONDON, "inRange(${field}, 1h, 1d)", ValDuration.create(7_200_000)))
                .isEqualTo(ValBoolean.TRUE);
        assertThat(DateFunctionTestUtil.eval(
                DateFunctionTestUtil.LONDON, "inRange(${field}, 1, 10)", ValLong.create(11)))
                .isEqualTo(ValBoolean.FALSE);
    }

    @Test
    void datesCanStillBeTotalledForAnAverage() {
        final Calculator calculator = Add.Calc.createForSum(null);
        final ValDate date = ValDate.create(1000);

        assertThat(calculator.calc(calculator.calc(ValNull.INSTANCE, date), date)).isEqualTo(ValDouble.create(2000));
    }

    @Test
    void timeOutOfRange() {
        assertThatThrownBy(() -> DateArithmetic.add(
                ValDate.create(Long.MAX_VALUE), ValPeriod.create(1, 0, 0), () -> ZoneOffset.UTC))
                .isInstanceOfAny(DateTimeException.class, ArithmeticException.class);
    }

    private static Val eval(final String expression) throws ParseException {
        return DateFunctionTestUtil.eval(DateFunctionTestUtil.LONDON, expression, ValNull.INSTANCE);
    }
}
