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

public class TestParseDuration extends AbstractFunctionTest<ParseDuration> {

    @Override
    Class<ParseDuration> getFunctionType() {
        return ParseDuration.class;
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
                        "1.1", // It is debatable whether this should parse or error
                        Val.create(Duration.ofMillis(1)),
                        Val.create(1.1D)),
                TestCase.of(
                        "1000 (long)",
                        Val.create(Duration.ofMillis(1_000)),
                        Val.create(1000L)),
                TestCase.of(
                        "1000 (int)",
                        Val.create(Duration.ofMillis(1_000)),
                        Val.create(1000L)),
                TestCase.of(
                        "1000 (float)",
                        Val.create(Duration.ofMillis(1_000)),
                        Val.create(1000F)),
                TestCase.of(
                        "1000 (str)",
                        Val.create(Duration.ofMillis(1_000)),
                        Val.create("1000")),
                TestCase.of(
                        "P2DT3H",
                        Val.create(Duration.ofDays(2).plusHours(3)),
                        Val.create("P2DT3H")),
                TestCase.of(
                        "P2D",
                        Val.create(Duration.ofDays(2)),
                        Val.create("P2D")),
                TestCase.of(
                        "1h",
                        Val.create(Duration.ofHours(1)),
                        Val.create("1h")),
                TestCase.of(
                        "22h",
                        Val.create(Duration.ofHours(22)),
                        Val.create("22h")),
                TestCase.of(
                        "5m",
                        Val.create(Duration.ofMinutes(5)),
                        Val.create("5m"))
        );
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "'26:03:04'|hh:mm:ss|PT26H3M4S",
            "'1.02:03:04'|d.hh:mm:ss|PT26H3M4S",
            "'-01:30'|hh:mm|PT-1H-30M",
            "'0:00:01.123456'|h:mm:ss.fff|PT1.123S",
            // The pattern says what a number is of.
            "90|m|PT90M",
            "1.5|m|PT90S",
            "2.0|s|PT2S",
            "0.7|d|PT16H48M",
            "0.0019|s|PT0.002S",
            "'1.5'|m|PT90S",
            "'-1.5'|m|PT-90S",
            "'.5'|hh|PT30M",
            "'2.0'|s|PT2S",
            // Any part of a millisecond is dropped, as it is in other text.
            "'0.0019'|s|PT0.001S",
            "'1.9996'|s|PT1.999S",
            "'1.9996'|s.fff|PT1.999S",
            "'-1.9996'|s|PT-1.999S",
            "'9007199254740993.5'|fff|PT9007199254740.993S",
            "'+2'|d|PT48H",
            "'02'|d|PT48H",
            "'1E3'|s|PT1000S",
            "'9007199254740993'|fff|PT9007199254740.993S",
            "'9007199254740993.0'|fff|PT9007199254740.993S",
            "15|f|PT1.5S",
            "0|hh|PT0S",
            "'90'|m|PT90M",
            // These are durations already.
            "1h|m|PT1H",
            "2d|m|PT48H"})
    void patterns(final String value, final String pattern, final Duration expected) throws ParseException {
        final String function = "parseDuration(" + value + ", '" + pattern + "')";
        assertThat(eval(function, ValNull.INSTANCE)).isEqualTo(ValDuration.create(expected));

        final Val field = eval(value, ValNull.INSTANCE);
        assertThat(eval("parseDuration(${field}, '" + pattern + "')", field)).isEqualTo(ValDuration.create(expected));
        assertThat(eval("parseDuration(max(${field}), '" + pattern + "')", field))
                .isEqualTo(ValDuration.create(expected));
    }

    @Test
    void formattedDurationIsParsed() throws ParseException {
        assertThat(eval("parseDuration(formatDuration(${field}, 'd.hh:mm:ss.fff'), 'd.hh:mm:ss.fff')",
                ValDuration.create(-93_784_005L))).isEqualTo(ValDuration.create(-93_784_005L));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "'soon'|Text is not a duration in the pattern 'hh:mm'. Text: 'soon'.",
            "'1:60'|Text is not a duration in the pattern 'hh:mm'. Text: '1:60'.",
            "'1h'|Text is not a duration in the pattern 'hh:mm'. Text: '1h'.",
            "'1M'|Text is not a duration in the pattern 'hh:mm'. Text: '1M'.",
            "90|A number can only be read with a pattern of one unit, such as m",
            "1.5|A number can only be read with a pattern of one unit, such as m",
            "'90'|Text is not a duration in the pattern 'hh:mm'. Text: '90'.",
            "'9999999999999999:00'|The duration is too long. Text: '9999999999999999:00'.",
            "true()|A boolean is not a duration",
            "1M|A period of months or years can't be used as a duration",
            "2024-07-01T00:00:00Z|A date is not a duration"})
    void valuesThatAreNotInThePattern(final String value, final String message) throws ParseException {
        assertThat(eval("parseDuration(" + value + ", 'hh:mm')", ValNull.INSTANCE)).isEqualTo(ValErr.create(message));
        assertThat(eval("parseDuration(${field}, 'hh:mm')", eval(value, ValNull.INSTANCE)))
                .isEqualTo(ValErr.create(message));
    }

    @Test
    void numbers() throws ParseException {
        for (final Val val : new Val[]{ValInteger.create(-2), ValLong.create(-2), ValDouble.create(-2),
                ValFloat.create(-2)}) {
            assertThat(eval("parseDuration(${field}, 'h')", val)).isEqualTo(ValDuration.create(-7_200_000));
        }
        assertThat(eval("parseDuration(${field}, 'h')", ValDouble.create(1e15)))
                .isEqualTo(ValErr.create("The duration is too long. Text: '1000000000000000'."));
        assertThat(eval("parseDuration(${field}, 'fff')", ValLong.create(Long.MIN_VALUE)))
                .isEqualTo(ValDuration.create(Long.MIN_VALUE));
        for (final Val val : new Val[]{ValFloat.create(0.7f), ValDouble.create(0.7), ValString.create("0.7"),
                ValDouble.create(-0.0005), ValDouble.create(0.00205)}) {
            assertThat(eval("parseDuration(${field}, 'm')", val)).as(val.toString())
                    .isEqualTo(eval("${field} * 1m", val.type().isNumber()
                            ? val
                            : ValDouble.create(0.7)));
        }
        assertThat(eval("parseDuration(${field}, 'm')", ValFloat.create(0.7f))).isEqualTo(ValDuration.create(42_000));
        assertThat(eval("parseDuration(${field}, 'h')", ValString.create("1e15")).toString())
                .contains("The duration is too long");
        assertThat(eval("parseDuration(${field}, 'h')", ValString.create("99999999999999999999")).toString())
                .contains("The duration is too long");
        assertThat(eval("parseDuration(${field}, 'h')", ValString.create(" 2")).type()).isEqualTo(Type.ERR);
        assertThat(eval("parseDuration(${field}, 'h')", ValString.create("2 h")).type()).isEqualTo(Type.ERR);
        // Whatever the number, so that a column isn't read in some rows only.
        for (final String pattern : new String[]{"s.fff", "hhmmss", "m'm'"}) {
            for (final Val val : new Val[]{ValDouble.create(1.5), ValDouble.create(2), ValLong.create(10203)}) {
                assertThat(eval("parseDuration(${field}, \"" + pattern + "\")", val)).as(pattern + val)
                        .isEqualTo(ValErr.create("A number can only be read with a pattern of one unit, such as m"));
            }
        }
    }

    @Test
    void textThatIsNotANumberOfTheUnit() throws ParseException {
        for (final String text : new String[]{"soon", "1h", "2 ", "NaN"}) {
            assertThat(eval("parseDuration(${field}, 'm')", ValString.create(text))).isEqualTo(ValErr.create(
                    "Text is not a duration in the pattern 'm'. Text: '" + text + "'."));
        }
    }

    @Test
    void numbersThatAreNotFinite() throws ParseException {
        assertThat(eval("parseDuration(${field}, 'm')", ValDouble.create(Double.NaN)).type()).isEqualTo(Type.ERR);
        assertThat(eval("parseDuration(${field}, 'm')", ValDouble.create(1e300)).type()).isEqualTo(Type.ERR);
    }

    @Test
    void valuesToLeaveWithAPattern() throws ParseException {
        for (final Val val : new Val[]{ValNull.INSTANCE, ValString.create(""), ValString.create(" ")}) {
            assertThat(eval("parseDuration(${field}, 'hh:mm')", val)).isEqualTo(ValNull.INSTANCE);
        }
        assertThat(eval("parseDuration('', 'hh:mm')", ValNull.INSTANCE)).isEqualTo(ValNull.INSTANCE);
        assertThat(eval("parseDuration(${field}, 'hh:mm')", ValErr.create("bad"))).isEqualTo(ValErr.create("bad"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "'HH:mm'|has a letter 'H' that is not a unit",
            "'mmhh'|has units together that can't be told apart",
            "''|A duration pattern is needed",
            "null()|String expected as second argument",
            "${field}|String expected as second argument"})
    void badPatternsAreReportedWhenTheExpressionIsParsed(final String pattern, final String message) {
        assertThatThrownBy(() -> eval("parseDuration('1:30', " + pattern + ")", ValNull.INSTANCE))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining(message);
    }

    private static Val eval(final String expression, final Val field) throws ParseException {
        return DateFunctionTestUtil.eval(DateFunctionTestUtil.LONDON, expression, field);
    }
}
