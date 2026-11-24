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

import java.text.ParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestToEpoch {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "2024-01-01T00:00:00.123Z|s|1704067200",
            "2024-01-01T00:00:00.123Z|ms|1704067200123",
            "2024-01-01T00:00:00.123Z|us|1704067200123000",
            "2024-01-01T00:00:00.123Z|ns|1704067200123000000",
            "1969-12-31T23:59:59.500Z|s|-1"})
    void epochs(final String date, final String unit, final long expected) throws ParseException {
        assertThat(eval("toEpoch(" + date + ", '" + unit + "')")).isEqualTo(ValLong.create(expected));
        assertThat(eval("toEpoch(" + date + ", ${field})", ValString.create(unit)))
                .isEqualTo(ValLong.create(expected));
    }

    @Test
    void nulls() throws ParseException {
        assertThat(eval("toEpoch(${field}, 's')")).isEqualTo(ValNull.INSTANCE);
    }

    @Test
    void theTimeZoneIsOnlyNeededForTextWithNoOffset() throws ParseException {
        assertThat(DateFunctionTestUtil.eval(
                DateFunctionTestUtil.UNKNOWN_ZONE, "toEpoch(2024-01-01T00:00:00Z, 's')", ValNull.INSTANCE))
                .isEqualTo(ValLong.create(1704067200L));
        assertThat(DateFunctionTestUtil.eval(
                DateFunctionTestUtil.UNKNOWN_ZONE, "toEpoch(${field}, 's')", ValString.create("2024-01-01T00:00:00Z")))
                .isEqualTo(ValLong.create(1704067200L));
        assertThat(DateFunctionTestUtil.eval(
                DateFunctionTestUtil.UNKNOWN_ZONE, "toEpoch(${field}, 's')", ValString.create("2024-01-01T00:00:00"))
                .toString())
                .contains("Unknown time zone 'Mars/Base'");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "toEpoch(${field}, 's')|'soon' is not a date"})
    void errors(final String expression, final String message) throws ParseException {
        final Val val = eval(expression, ValString.create("soon"));
        assertThat(val.type()).as(val.toString()).isEqualTo(Type.ERR);
        assertThat(val.toString()).contains(message);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "toEpoch(${field}, 'MS')|Unknown unit 'MS'"})
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
