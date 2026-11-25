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

import stroom.query.language.functions.ref.KryoDataReader;
import stroom.query.language.functions.ref.KryoDataWriter;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.ParseException;
import java.time.DateTimeException;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

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
            "max(1d, 1w)|604800000",
            "average(1h, 2h)|5400000",
            "average(1d, 3d)|172800000",
            "average(1d, 2h)|46800000",
            "average(1h, 1000)|1800500",
            "average(1ms, 2ms)|2",
            "stDev(1h, 3h)|3600000",
            "stDev(1d, 3d)|86400000",
            "stDev(3h, 1h)|3600000",
            "stDev(1h, 5400000, 2h)|1469694",
            "stDev(2h, 0)|3600000",
            // Too many milliseconds for a double to be exact.
            "parseDuration('9007199254740993') % 10ms|3",
            "100m % 1h|2400000",
            "-100m % 1h|-2400000",
            "100m % 1h % 25m|900000",
            "25h % 1d|3600000",
            "3d % 2d|86400000",
            "1d % 7h|10800000",
            "100m % 60000|0",
            "90500ms % 60000|30500",
            "10ms % 2.5|0",
            "10ms % 0.4|0",
            "floor(average(90m, 100m), 1h)|3600000"})
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
            "1000 / 1s|1",
            "average(1, 2)|1.5",
            // The average of dates is worked out from the total of their milliseconds.
            "average(1970-01-01T00:00:01Z, 1970-01-01T00:00:02Z)|1500",
            "stDev(1, 3)|1",
            "stDev(1970-01-01T00:00:01Z, 1970-01-01T00:00:03Z)|1000",
            "variance(1s, 3s)|1000000",
            "5000 % 3s|2000",
            "1h % 2024-01-01T00:00:00Z|3600000",
            "1970-01-01T01:40:00Z % 1h|2400000",
            "7 % 4|3"})
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
            "1M % 5|can't be used as a number",
            "1M % 1h|Can't mix a duration and a period",
            "1h % 1M|Can't mix a duration and a period",
            "1h % 0|Result of 1h % 0 is not a number",
            "1h % 0h|Result of 1h % 0ms is not a number",
            "1h / 0|Result of 1h / 0 is not a number",
            "5 % 0|Result of 5.0 % 0.0 is not a number",
            "1d > '1M'|A period of months or years can't be compared",
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
            "inRange('2024-07-03T10:00:00Z', '2024-07-01', '2024-07-31')|must be valid ISO date strings",
            "inRange(2024-07-03T10:00:00Z, '2024-07-01', '2024-07-31')|must be all numeric or all ISO date strings",
            "inRange('soon', '1h', '2h')|must be valid ISO date strings",
            "inRange(1h, 'now', '2h')|must be all numeric or all ISO date strings",
            "inRange(true(), '0', '2')|must be all numeric or all ISO date strings",
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
            "1M = '1M'|true",
            "equals('1M', 1M)|true",
            "1M = ' 1 M '|true",
            "1M != '1M'|false",
            "1M = 'P1M'|true",
            // Two texts are only text.
            "equals('1M', 'P1M')|false",
            "1M = '2M'|false",
            "1y = '12M'|false",
            "parsePeriod('P1Y2M') = '1y2M'|true",
            "1M = 'soon'|false",
            "1M = ''|false",
            "1d = '1d'|true",
            "1d = '24h'|true",
            "1w = '7d'|true",
            "1d < '2d'|true",
            "1d < '25h'|true",
            "greaterThan('2d', 1d)|true",
            "inRange(2024-07-03T10:00:00Z, '2024-07-03T09:00:00Z', '2024-07-03T12:00:00+01:00')|true",
            "inRange(2024-07-03T10:00:00Z, '2024-07-03T09:00:00Z', '2024-07-03T10:30:00+01:00')|false",
            "inRange('2024-07-03T10:00:00Z', '2024-07-03T09:00:00Z', '2024-07-03T10:00:00Z')|true",
            "inRange('2024-07-03T10:00:00Z', 2024-07-03T10:00:00Z, 2024-07-03T11:00:00Z)|true",
            "inRange('2024-07-03T10:00:00Z', 2024-07-03T10:00:01Z, 2024-07-03T11:00:00Z)|false",
            "inRange(90m, '1h', '2h')|true",
            "inRange('90m', '1h', '2h')|true",
            "inRange('150m', '1h', '2h')|false",
            "inRange('30m', '1h', '2h')|false",
            "inRange('90m', 1h, 1d)|true",
            "inRange('10', '9', '11')|true",
            "inRange(10, '9', '11')|true",
            "inRange('2024-07-03', '2024-07-01', '2024-07-31')|true",
            "inRange('2024-08-03', '2024-07-01', '2024-07-31')|false",
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
            "toLong(1d)|long",
            "average(1h)|duration",
            "stDev(1h)|duration",
            "100m % 1h|duration"})
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
    void averagesOfGroups() throws ParseException {
        final ValDuration one = ValDuration.create(3_600_000);
        final ValDuration three = ValDuration.create(10_800_000);
        final ValPeriod day = ValPeriod.create(0, 0, 1);
        final ValDuration two = ValDuration.create(7_200_000);

        assertThat(evalGroup("average(${field})", one, three)).isEqualTo(two);
        assertThat(evalGroup("stDev(${field})", one, three)).isEqualTo(one);
        assertThat(evalGroup("stDev(${field})", day, ValPeriod.create(0, 0, 3)))
                .isEqualTo(ValDuration.create(86_400_000));
        assertThat(evalGroup("average(${field})", day, ValPeriod.create(0, 0, 3)))
                .isEqualTo(ValDuration.create(172_800_000));
        // Whichever is first, and whether or not the other is skipped.
        assertThat(evalGroup("stDev(${field})", ValLong.create(0), two)).isEqualTo(one);
        assertThat(evalGroup("stDev(${field})", two, ValLong.create(0))).isEqualTo(one);
        assertThat(evalGroup("stDev(${field})", one, ValString.create("soon"))).isEqualTo(ValDuration.create(0));
        assertThat(evalGroup("average(${field})", ValLong.create(1), ValLong.create(2)))
                .isEqualTo(ValDouble.create(1.5));
        assertThat(evalGroup("stDev(${field})", ValLong.create(1), ValLong.create(3)))
                .isEqualTo(ValDouble.create(1));
        assertThat(evalGroup("stDev(${field})", ValDate.create(1), ValDate.create(3)))
                .isEqualTo(ValDouble.create(1));
        assertThat(evalGroup("variance(${field})", one, three).type()).isEqualTo(Type.DOUBLE);
        assertThat(evalGroup("average(${field})", ValPeriod.create(0, 1, 0), day).toString())
                .contains("Can't average a period");
        assertThat(evalGroup("average(${field})", ValString.create("a"), ValString.create("b")).toString())
                .contains("Can't average a string");
        assertThat(evalGroup("stDev(${field})", ValDuration.create(Long.MIN_VALUE), ValDuration.create(Long.MAX_VALUE))
                .toString()).contains("too long");
    }

    // The values in turn, and as results that are merged and then stored.
    private static Val evalGroup(final String input, final Val first, final Val second) throws ParseException {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create("field");
        final Expression expression = new ExpressionParser(new ParamFactory(Map.of()))
                .parse(DateFunctionTestUtil.LONDON, fieldIndex, input);
        expression.setStaticMappedValues(Map.of());
        final ValueReferenceIndex valueReferenceIndex = new ValueReferenceIndex();
        expression.addValueReferences(valueReferenceIndex);
        final Generator generator = expression.createGenerator();

        final StoredValues inTurn = valueReferenceIndex.createStoredValues();
        generator.set(new Val[]{first}, inTurn);
        generator.set(new Val[]{second}, inTurn);

        final StoredValues merged = valueReferenceIndex.createStoredValues();
        generator.set(new Val[]{first}, merged);
        final StoredValues other = valueReferenceIndex.createStoredValues();
        generator.set(new Val[]{second}, other);
        generator.merge(merged, other);

        final Val result = generator.eval(inTurn, null);
        assertThat(generator.eval(merged, null)).isEqualTo(result);
        final ByteBufferOutput output = new ByteBufferOutput(1000);
        valueReferenceIndex.write(merged, new KryoDataWriter(output));
        final StoredValues stored = valueReferenceIndex.read(
                new KryoDataReader(new ByteBufferInput(output.toBytes())));
        assertThat(generator.eval(stored, null)).isEqualTo(result);
        return result;
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
