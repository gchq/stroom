/*
 * Copyright 2024 Crown Copyright
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
import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestFormatDuration extends AbstractFunctionTest<FormatDuration> {

    @Override
    Class<FormatDuration> getFunctionType() {
        return FormatDuration.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "null",
                        ValNull.INSTANCE,
                        Val.create(null)),
                TestCase.of(
                        "",
                        ValNull.INSTANCE,
                        Val.create("")),
                TestCase.of(
                        "foo",
                        ValErr.create(ValDurationUtil.PARSE_ERROR_MESSAGE),
                        Val.create("foo")),
                TestCase.of(
                        "P1D foo",
                        ValErr.create(ValDurationUtil.PARSE_ERROR_MESSAGE),
                        Val.create("P1D foo")),
                TestCase.of(
                        "1E3",
                        ValString.create("1s"),
                        Val.create("1E3")),
                TestCase.of(
                        "1h",
                        ValString.create("1h"),
                        ValDuration.create(Duration.ofHours(1).toMillis())),
                TestCase.of(
                        "22h",
                        ValString.create("22h"),
                        ValDuration.create(Duration.ofHours(22).toMillis())),
                TestCase.of(
                        "5m",
                        ValString.create("5m"),
                        ValDuration.create(Duration.ofMinutes(5).toMillis()))
        );
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "formatDuration(${field}, 'd.hh:mm:ss')|2.01:30:05",
            "formatDuration(${field}, 'hh:mm')|49:30",
            "formatDuration(${field})|2.1d",
            "formatDuration(49h, 'h:mm:ss')|49:00:00",
            "formatDuration('1.5h', 'h:mm')|1:30",
            "formatDuration('-1.5h', 'h:mm')|-1:30",
            "formatDuration(5000, 's.fff')|5.000",
            "formatDuration(2d, 'hh:mm')|48:00"})
    void patterns(final String expression, final String expected) throws ParseException {
        final Val field = ValDuration.create(Duration.ofDays(2).plusMinutes(90).plusSeconds(5));
        assertThat(DateFunctionTestUtil.eval(DateFunctionTestUtil.LONDON, expression, field))
                .isEqualTo(ValString.create(expected));
    }

    @Test
    void valuesThatAreNotDurations() throws ParseException {
        final String expression = "formatDuration(${field}, 'hh:mm')";
        assertThat(DateFunctionTestUtil.eval(DateFunctionTestUtil.LONDON, expression, ValNull.INSTANCE))
                .isEqualTo(ValNull.INSTANCE);
        assertThat(DateFunctionTestUtil.eval(DateFunctionTestUtil.LONDON, expression, ValErr.create("bad")))
                .isEqualTo(ValErr.create("bad"));
        assertThat(DateFunctionTestUtil.eval(DateFunctionTestUtil.LONDON, expression, ValBoolean.TRUE))
                .isEqualTo(ValErr.create("A boolean is not a duration"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "formatDuration(1h, 'HH:mm')|has a letter 'H' that is not a unit",
            "formatDuration(1h, '')|A duration pattern is needed",
            "formatDuration(1h, null())|String expected as second argument",
            "formatDuration(1h, ${field})|String expected as second argument"})
    void badPatternsAreReportedWhenTheExpressionIsParsed(final String expression, final String message) {
        assertThatThrownBy(() -> DateFunctionTestUtil.eval(
                DateFunctionTestUtil.LONDON, expression, ValNull.INSTANCE))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining(message);
    }
}
