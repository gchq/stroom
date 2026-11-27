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
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestMakeDate {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "makeDate(2024, 7, 1)|2024-06-30T23:00:00Z",
            "makeDate(2024, 7, 1, 9)|2024-07-01T08:00:00Z",
            "makeDate(2024, 7, 1, 9, 30, 15, 250)|2024-07-01T08:30:15.250Z",
            "makeDate('2024', '7', '1')|2024-06-30T23:00:00Z",
            // Zones.
            "makeDate(2024, 7, 1, 'America/New_York')|2024-07-01T04:00:00Z",
            "makeDate(2024, 7, 1, 9, 30, 'America/New_York')|2024-07-01T13:30:00Z",
            "makeDate(2024, 7, 1, '+02')|2024-06-30T22:00:00Z",
            "makeDate(2024, 7, 1, '-05:00')|2024-07-01T05:00:00Z",
            "makeDate(2024, 7, 1, 9, '+0400')|2024-07-01T05:00:00Z",
            "makeDate(2024, 7, 1, '9')|2024-07-01T08:00:00Z",
            "makeDate(2024, 7, 1, 9, 30, 15, 250, 'America/New_York')|2024-07-01T13:30:15.250Z",
            // 01:30 doesn't happen on 31 March, so it moves forward to 02:30.
            "makeDate(2024, 3, 31, 1, 30)|2024-03-31T01:30:00Z",
            // 01:30 happens twice on 27 October, and the first is used.
            "makeDate(2024, 10, 27, 1, 30)|2024-10-27T00:30:00Z"})
    void dates(final String expression, final Instant expected) throws ParseException {
        assertThat(eval(expression)).isEqualTo(ValDate.create(expected.toEpochMilli()));
    }

    @Test
    void fields() throws ParseException {
        assertThat(eval("makeDate(2024, ${field}, 1)", ValLong.create(7)))
                .isEqualTo(ValDate.create(Instant.parse("2024-06-30T23:00:00Z").toEpochMilli()));
        assertThat(eval("makeDate(2024, 7, 1, 0, 0, 0, 0, ${field})", ValString.create("UTC")))
                .isEqualTo(ValDate.create(Instant.parse("2024-07-01T00:00:00Z").toEpochMilli()));
        assertThat(eval("makeDate(2024, ${field}, 1)", ValNull.INSTANCE)).isEqualTo(ValNull.INSTANCE);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "makeDate(2024, 7, 1, param('p'))|America/New_York|2024-07-01T04:00:00Z",
            "makeDate(2024, 7, 1, param('p'))|+02|2024-06-30T22:00:00Z",
            "makeDate(2024, 7, 1, param('p'))|9|2024-07-01T08:00:00Z",
            "makeDate(2024, 7, param('p'))|2|2024-07-01T23:00:00Z",
            "makeDate(2024, 7, 1, 0, 0, 0, 0, param('p'))|UTC|2024-07-01T00:00:00Z"})
    void queryParameters(final String expression, final String param, final Instant expected) throws ParseException {
        assertThat(DateFunctionTestUtil.eval(
                DateFunctionTestUtil.LONDON, expression, Map.of("p", param), ValNull.INSTANCE))
                .isEqualTo(ValDate.create(expected.toEpochMilli()));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "makeDate(2024, 7, 1, param('p'))|BST|Unknown time zone 'BST'",
            "makeDate(2024, 7, param('p'))|UTC|needs a year, month and day before the time zone"})
    void queryParametersThatCantBeUsed(final String expression,
                                       final String param,
                                       final String message) throws ParseException {
        assertThat(DateFunctionTestUtil.eval(
                DateFunctionTestUtil.LONDON, expression, Map.of("p", param), ValNull.INSTANCE).toString())
                .contains(message);
    }

    // As they are from a field.
    @ParameterizedTest
    @ValueSource(strings = {
            "makeDate(2024, '', 1)", "makePeriod(1, ' ', 0)", "ago('')", "dateDiff('', 2024-01-01T00:00:00Z, day())",
            "datePart(2024-01-01T00:00:00Z, '')", "fromEpoch(1, '')", "toEpoch(2024-01-01T00:00:00Z, '')"})
    void blanksAreNull(final String expression) throws ParseException {
        assertThat(eval(expression)).isEqualTo(ValNull.INSTANCE);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "makeDate(2023, 2, 29)|February 29",
            "makeDate(2024, 20 - 7, 1)|MonthOfYear",
            "makeDate(999999999, 1, 1)|'+999999999-01-01T00:00' is too large to be a date",
            "makeDate(2024, ${field}, 1)|The month '1.5' is not a whole number"})
    void errors(final String expression, final String message) throws ParseException {
        final Val val = eval(expression, ValDouble.create(1.5));
        assertThat(val.type()).as(val.toString()).isEqualTo(Type.ERR);
        assertThat(val.toString()).contains(message);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "makeDate(2024, 13, 1)|MonthOfYear",
            "makeDate(2024, 1, 1, 24)|HourOfDay",
            "makeDate(2024, 1, 1, 0, 0, 0, 1000)|MilliOfSecond",
            "makeDate(2024, 1.5, 1)|The month '1.5' is not a whole number",
            "makeDate(2024, 1, 1, 9h)|The hour '9h' is not a whole number",
            "makeDate(2024, 1, 1, 'Mars/Base')|Unknown time zone 'Mars/Base'",
            "makeDate(2024, 7, 'Europe/London')|needs a year, month and day before the time zone",
            "makeDate(2024, 7, '-1')|needs a year, month and day before the time zone",
            "makeDate(2024, 1, 1d)|The day 'P1D' is not a whole number",
            "makeDate(2024, '2h', 1)|The month '2h' is not a whole number",
            "makeDate(2024, 1)|Invalid number of parameters"})
    void mistakesThatAreReportedWhenTheExpressionIsParsed(final String expression, final String message) {
        assertThatThrownBy(() -> eval(expression)).hasMessageContaining(message);
    }

    // In a query whose time zone is Europe/London.
    private static Val eval(final String expression, final Val field) throws ParseException {
        return DateFunctionTestUtil.eval(DateFunctionTestUtil.LONDON, expression, field);
    }

    private static Val eval(final String expression) throws ParseException {
        return eval(expression, ValNull.INSTANCE);
    }
}
