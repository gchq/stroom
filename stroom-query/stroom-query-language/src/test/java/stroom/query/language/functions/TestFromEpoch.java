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
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestFromEpoch {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1704067200|s|2024-01-01T00:00:00Z",
            "1704067200.25|s|2024-01-01T00:00:00.250Z",
            "1704067200123|ms|2024-01-01T00:00:00.123Z",
            "1704067200123456|us|2024-01-01T00:00:00.123Z",
            "1704067200123456789|ns|2024-01-01T00:00:00.123Z",
            "-1|s|1969-12-31T23:59:59Z",
            "-1|us|1969-12-31T23:59:59.999Z"})
    void dates(final String amount, final String unit, final Instant expected) throws ParseException {
        final ValDate date = ValDate.create(expected.toEpochMilli());
        assertThat(eval("fromEpoch(${field}, '" + unit + "')", ValString.create(amount))).isEqualTo(date);
        if (!amount.contains(".")) {
            assertThat(eval("fromEpoch(${field}, '" + unit + "')", ValLong.create(Long.parseLong(amount))))
                    .isEqualTo(date);
        }
        assertThat(eval("fromEpoch(" + amount + ", '" + unit + "')")).isEqualTo(date);
    }

    @Test
    void nulls() throws ParseException {
        assertThat(eval("fromEpoch(${field}, 's')")).isEqualTo(ValNull.INSTANCE);
    }

    @Test
    void numbersThatAreNotFinite() throws ParseException {
        for (final double number : new double[]{Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN}) {
            assertThat(eval("fromEpoch(${field}, 's')", ValDouble.create(number)).toString())
                    .contains("is not a number");
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "fromEpoch(${field}, 's')|'soon' is not a number",
            "fromEpoch(1, ${field})|Unknown unit 'soon'. Use one of s, ms, us, ns",
            "fromEpoch('1e300', 's')|Overflow"})
    void errors(final String expression, final String message) throws ParseException {
        final Val val = eval(expression, ValString.create("soon"));
        assertThat(val.type()).as(val.toString()).isEqualTo(Type.ERR);
        assertThat(val.toString()).contains(message);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "fromEpoch(${field}, 'sec')|Unknown unit 'sec'",
            "fromEpoch(1h, 's')|is not a number"})
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
