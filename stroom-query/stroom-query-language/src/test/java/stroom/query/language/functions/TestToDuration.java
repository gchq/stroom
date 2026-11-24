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

class TestToDuration {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "toDuration('1h30m')|5400000",
            "toDuration('PT1H')|3600000",
            "toDuration('1.5h')|5400000",
            "toDuration(1000)|1000",
            "toDuration(90m)|5400000",
            "toDuration('PT1M')|60000",
            "toDuration('1m')|60000",
            "toDuration('100MS')|100",
            "toDuration(1d)|86400000"})
    void durations(final String expression, final long expected) throws ParseException {
        assertThat(eval(expression)).isEqualTo(ValDuration.create(expected));
    }

    @Test
    void nulls() throws ParseException {
        assertThat(eval("toDuration(${field})")).isEqualTo(ValNull.INSTANCE);
        assertThat(eval("toDuration(${field})", ValString.create(""))).isEqualTo(ValNull.INSTANCE);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "toDuration(${field})|Text cannot be parsed to a Duration",
            "toDuration(2024-01-01T00:00:00Z)|A date is not a duration",
            "toDuration(1M)|can't be used as a duration",
            "toDuration('1M')|can't be used as a duration",
            "toDuration(' -1 M ')|can't be used as a duration",
            "toDuration('1.5M')|can't be used as a duration",
            "toDuration('1y')|Text cannot be parsed to a Duration",
            "toDuration(isNull(1))|A boolean is not a duration"})
    void errors(final String expression, final String message) throws ParseException {
        final Val val = eval(expression, ValString.create("soon"));
        assertThat(val.type()).as(val.toString()).isEqualTo(Type.ERR);
        assertThat(val.toString()).contains(message);
    }

    @Test
    void numbersThatAreNotFinite() throws ParseException {
        for (final double number : new double[]{Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN}) {
            for (final String name : new String[]{"toDuration", "parseDuration"}) {
                assertThat(eval(name + "(${field})", ValDouble.create(number)))
                        .isEqualTo(ValErr.create("A double that is not finite is not a duration"));
            }
        }
    }

    // In a query whose time zone is Europe/London.
    private static Val eval(final String expression, final Val field) throws ParseException {
        return DateFunctionTestUtil.eval(DateFunctionTestUtil.LONDON, expression, field);
    }

    private static Val eval(final String expression) throws ParseException {
        return eval(expression, ValNull.INSTANCE);
    }
}
