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

import stroom.query.api.token.TokenException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.text.ParseException;
import java.time.Period;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestMakePeriod {

    @ParameterizedTest
    @CsvSource({"1,2,3,P1Y2M3D", "0,0,0,P0D", "-1,0,14,P-1Y14D"})
    void periods(final int years, final int months, final int days, final String expected) throws ParseException {
        assertThat(eval("makePeriod(" + years + ", " + months + ", " + days + ")"))
                .isEqualTo(ValPeriod.create(Period.parse(expected)));
        assertThat(eval("makePeriod(" + years + ", " + months + ", ${field})", ValInteger.create(days)))
                .isEqualTo(ValPeriod.create(Period.parse(expected)));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "makePeriod(0, 0, 2d)|The days 'P2D' is not a whole number",
            "makePeriod('2h', 0, 0)|The years '2h' is not a whole number",
            "makePeriod(1.5, 0, 0)|The years '1.5' is not a whole number",
            "makePeriod(0, '2024-01-01T00:00:00Z', 0)|is not a whole number"})
    void mistakesThatAreReportedWhenTheExpressionIsParsed(final String expression, final String message) {
        assertThatThrownBy(() -> eval(expression))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining(message);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "makePeriod(${field}, 0, 0)|The years '1.5' is not a whole number",
            "makePeriod(isNull(1), 0, 0)|is not a whole number"})
    void errors(final String expression, final String message) throws ParseException {
        final Val val = eval(expression, ValDouble.create(1.5));
        assertThat(val.type()).as(val.toString()).isEqualTo(Type.ERR);
        assertThat(val.toString()).contains(message);
    }

    @Test
    void numbersThatAreNotFinite() throws ParseException {
        assertThat(eval("makePeriod(${field}, 0, 0)", ValDouble.create(Double.NaN)).type()).isEqualTo(Type.ERR);
    }

    // In a query whose time zone is Europe/London.
    private static Val eval(final String expression, final Val field) throws ParseException {
        return DateFunctionTestUtil.eval(DateFunctionTestUtil.LONDON, expression, field);
    }

    private static Val eval(final String expression) throws ParseException {
        return eval(expression, ValNull.INSTANCE);
    }
}
