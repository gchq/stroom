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

import stroom.test.common.TestCase;
import stroom.test.common.TestOutcome;
import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestValDurationUtil {

    public static final String ERR_TEXT = ValDurationUtil.PARSE_ERROR_MESSAGE;

    @TestFactory
    Stream<DynamicTest> formatDuration_val() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(Val.class)
                .withSingleArgTestFunction(ValDurationUtil::formatDuration)
                .withAssertions(TestValDurationUtil::getTestAssertions)
                .withNameFunction(TestValDurationUtil::getTestName)
                .addCase(Val.create(null), ValNull.INSTANCE)
                .addCase(Val.create(""), ValNull.INSTANCE)
                .addCase(Val.create(1000), Val.create("1s"))
                .addCase(Val.create(1000L), Val.create("1s"))
                .addCase(Val.create(1000F), Val.create("1s"))
                .addCase(Val.create(1000D), Val.create("1s"))
                .addCase(Val.create(1000.123D), Val.create("1s"))
                .addCase(Val.create("1000"), Val.create("1s"))
                .addCase(Val.create("1000.123"), Val.create("1s"))
                .addCase(Val.create(60_000L), Val.create("1m"))
                .addCase(Val.create(Duration.ofDays(1)), Val.create("1d"))
                .addCase(Val.create("10d"), Val.create("10d"))
                .addCase(Val.create("P1D"), Val.create("1d"))
                .addCase(Val.create("foo"), ValErr.create(ERR_TEXT))
                .addCase(Val.create("P1D foo"), ValErr.create(ERR_TEXT))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> formatISODuration_val() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(Val.class)
                .withSingleArgTestFunction(ValDurationUtil::formatISODuration)
                .withAssertions(TestValDurationUtil::getTestAssertions)
                .withNameFunction(TestValDurationUtil::getTestName)
                .addCase(Val.create(null), ValNull.INSTANCE)
                .addCase(Val.create(""), ValNull.INSTANCE)
                .addCase(Val.create(1000), Val.create("PT1S"))
                .addCase(Val.create(1000L), Val.create("PT1S"))
                .addCase(Val.create(1000F), Val.create("PT1S"))
                .addCase(Val.create(1000D), Val.create("PT1S"))
                .addCase(Val.create("1000"), Val.create("PT1S"))
                .addCase(Val.create(60_000L), Val.create("PT1M"))
                .addCase(Val.create(Duration.ofDays(2)), Val.create("PT48H"))
                .addCase(Val.create(Duration.ofDays(10)), Val.create("PT240H")) // Durarion.toString doesn't use 'D'
                .addCase(Val.create("10d"), Val.create("PT240H"))
                .addCase(Val.create("P1D"), Val.create("PT24H"))
                .addCase(Val.create("foo"), ValErr.create(ERR_TEXT))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> parseDuration_val() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(Val.class)
                .withSingleArgTestFunction(ValDurationUtil::toDuration)
                .withAssertions(TestValDurationUtil::getTestAssertions)
                .withNameFunction(TestValDurationUtil::getTestName)
                .addCase(Val.create(null), ValNull.INSTANCE)
                .addCase(Val.create(""), ValNull.INSTANCE)
                .addCase(Val.create(1000), Val.create(Duration.ofSeconds(1)))
                .addCase(Val.create(1000L), Val.create(Duration.ofSeconds(1)))
                .addCase(Val.create(1000F), Val.create(Duration.ofSeconds(1)))
                .addCase(Val.create(1000D), Val.create(Duration.ofSeconds(1)))
                .addCase(Val.create("1000"), Val.create(Duration.ofSeconds(1)))
                .addCase(Val.create(60_000L), Val.create(Duration.ofMinutes(1)))
                .addCase(Val.create(Duration.ofDays(1)), Val.create(Duration.ofDays(1)))
                .addCase(Val.create("10d"), Val.create(Duration.ofDays(10)))
                .addCase(Val.create("P1D"), Val.create(Duration.ofDays(1)))
                .addCase(Val.create("foo"), ValErr.create(ERR_TEXT))
                .addCase(Val.create("P1D foo"), ValErr.create(ERR_TEXT))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> parseISODuration_val() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(Val.class)
                .withSingleArgTestFunction(ValDurationUtil::parseISODuration)
                .withAssertions(TestValDurationUtil::getTestAssertions)
                .withNameFunction(TestValDurationUtil::getTestName)
                .addCase(Val.create(null), ValNull.INSTANCE)
                .addCase(Val.create(""), ValNull.INSTANCE)
                .addCase(Val.create(1000), Val.create(Duration.ofSeconds(1)))
                .addCase(Val.create(1000L), Val.create(Duration.ofSeconds(1)))
                .addCase(Val.create(1000F), Val.create(Duration.ofSeconds(1)))
                .addCase(Val.create(1000D), Val.create(Duration.ofSeconds(1)))
                .addCase(Val.create(60_000L), Val.create(Duration.ofMinutes(1)))
                .addCase(Val.create(Duration.ofDays(1)), Val.create(Duration.ofDays(1)))
                .addCase(Val.create(" PT1H "), Val.create(Duration.ofHours(1)))
                .addCase(Val.create("10d"), ValErr.create(ERR_TEXT))
                .addCase(Val.create("P1D"), Val.create(Duration.ofDays(1)))
                .addCase(Val.create("foo"), ValErr.create(ERR_TEXT))
                .addCase(Val.create("P1D foo"), ValErr.create(ERR_TEXT))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> parseToMilliseconds() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(Long.class)
                .withSingleArgTestFunction(ValDurationUtil::parseToMilliseconds)
                .withSimpleEqualityAssertion()
                .withNameFunction(TestValDurationUtil::getTestName)
                .addCase("0", Duration.ZERO.toMillis())
                .addCase("2000", Duration.ofSeconds(2).toMillis())
                .addCase("P1D", Duration.ofDays(1).toMillis())
                .addCase("10d", Duration.ofDays(10).toMillis())
                .addThrowsCase(null, DateTimeParseException.class)
                .addThrowsCase("", DateTimeParseException.class)
                .addThrowsCase("foo", DateTimeParseException.class)
                .addThrowsCase("P1D foo", DateTimeParseException.class)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> parseMilliseconds() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(Long.class)
                .withSingleArgTestFunction(ValDurationUtil::parseMilliseconds)
                .withSimpleEqualityAssertion()
                .withNameFunction(TestValDurationUtil::getTestName)
                .addCase("2000", 2_000L)
                .addCase("P1D", Duration.ofDays(1).toMillis())
                .addCase("1h30m", 5_400_000L)
                // These would be a millisecond out as doubles.
                .addCase("0.7d", Duration.ofHours(16).plusMinutes(48).toMillis())
                .addCase("0.29h", 1_044_000L)
                .addCase("1.4 d", 120_960_000L)
                .addCase(" 4.35s ", 4_350L)
                .addCase("9007199254740993", 9007199254740993L)
                .addCase("-1.5h", -5_400_000L)
                .addCase("- 0.7 d", -60_480_000L)
                .addCase("-2h", -7_200_000L)
                .addCase("1.9", 1L)
                .addCase("1.s", 1_000L)
                .addCase(".5s", 500L)
                // Signs and spaces.
                .addCase("+2h", 7_200_000L)
                .addCase("+5", 5L)
                .addCase("- 1d 2h", -93_600_000L)
                .addCase(" 1 h  30 m ", 5_400_000L)
                // Every unit.
                .addCase("1w2d", 777_600_000L)
                .addCase("1.5w", 907_200_000L)
                .addCase("1.5h30m", 7_200_000L)
                .addCase("1d2h3m4s5ms", 93_784_005L)
                .addCase("1500us", 1L)
                .addCase("999us 999999ns", 1L)
                .addCase("2000000ns", 2L)
                .addCase("1ns", 0L)
                // Numbers of milliseconds, as dates are read from.
                .addCase("1E3", 1_000L)
                .addCase("-1.9", -1L)
                .addCase("9223372036854775807", Long.MAX_VALUE)
                .addCase("-9223372036854775808", Long.MIN_VALUE)
                // ISO 8601.
                .addCase(" PT1H ", 3_600_000L)
                .addCase("-PT1H", -3_600_000L)
                .addCase("+pt1h", 3_600_000L)
                .addCase("P2DT3H", 183_600_000L)
                // Quoted text.
                .addCase("'0.7d'", 60_480_000L)
                .addCase("\"PT1H\"", 3_600_000L)
                .addCase("' 5 '", 5L)
                .addCase(" '1h' ", 3_600_000L)
                .addThrowsCase("''", DateTimeParseException.class)
                .addThrowsCase("''1h''", DateTimeParseException.class)
                // Units must be as they are in an expression.
                .addThrowsCase("1H", DateTimeParseException.class)
                .addThrowsCase("1MS", DateTimeParseException.class)
                .addThrowsCase("1M", DateTimeParseException.class)
                .addThrowsCase("1h 1M", DateTimeParseException.class)
                .addThrowsCase("P1M", DateTimeParseException.class)
                .addThrowsCase("P1W", DateTimeParseException.class)
                .addThrowsCase("1 5m", DateTimeParseException.class)
                .addThrowsCase("1h30", DateTimeParseException.class)
                .addThrowsCase("1h-30m", DateTimeParseException.class)
                .addThrowsCase("1.h.", DateTimeParseException.class)
                .addThrowsCase("١h", DateTimeParseException.class)
                .addThrowsCase("PT9999999999999H", DateTimeParseException.class)
                .addThrowsCase("99999999999999h", DateTimeParseException.class)
                .addThrowsCase("-5M", DateTimeParseException.class)
                .addThrowsCase("-1.5H", DateTimeParseException.class)
                .addCase("-5", -5L)
                .addThrowsCase("--1.5h", DateTimeParseException.class)
                .addThrowsCase("99999999999999999999.5d", DateTimeParseException.class)
                .addThrowsCase("-99999999999999999999.5d", DateTimeParseException.class)
                .addThrowsCase("99999999999999999999", DateTimeParseException.class)
                .addThrowsCase("1.2.3s", DateTimeParseException.class)
                .addThrowsCase("soon", DateTimeParseException.class)
                .build();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1M|'1M' is a period of months or years, so is not a duration. Use parsePeriod to read it. Text: '1M'.",
            "P1Y|'P1Y' is a period of months or years, so is not a duration. Use parsePeriod to read it. Text: 'P1Y'.",
            "0M|'0M' is a period of months or years, so is not a duration. Use parsePeriod to read it. Text: '0M'.",
            "1y 2M|'1y 2M' is a period of months or years, so is not a duration. Use parsePeriod to read it. "
            + "Text: '1y 2M'.",
            // parsePeriod can't read these either.
            "1M 2h|'1M 2h' has months or years, so is not a duration. Text: '1M 2h'.",
            "1.5M|'1.5M' has months or years, so is not a duration. Text: '1.5M'.",
            "-1M|'-1M' has months or years, so is not a duration. Text: '-1M'.",
            "1H|Unknown unit 'H' in a duration. Use ns, us, ms, s, m, h, d or w. Text: '1H'.",
            "1e300|The duration is too long. Text: '1e300'.",
            "PT99999999999999999H|The duration is too long. Text: 'PT99999999999999999H'.",
            "PT99999999999999999999999H|The duration is too long. Text: 'PT99999999999999999999999H'.",
            "PT9999999999999H|The duration is too long. Text: 'PT9999999999999H'.",
            "P0M|'P0M' is a period of months or years, so is not a duration. Use parsePeriod to read it. Text: 'P0M'.",
            "-p1y2m3w4d|'-p1y2m3w4d' is a period of months or years, so is not a duration. Use parsePeriod to read it. "
            + "Text: '-p1y2m3w4d'.",
            "P1M2DT3H|'P1M2DT3H' has months or years, so is not a duration. Text: 'P1M2DT3H'.",
            "P1YT1.5S|'P1YT1.5S' has months or years, so is not a duration. Text: 'P1YT1.5S'.",
            "P2D1M|'P2D1M' has months or years, so is not a duration. Text: 'P2D1M'.",
            // These only look like it.
            "P1MX|Text cannot be parsed to a Duration. Text: 'P1MX'.",
            "P1MT|Text cannot be parsed to a Duration. Text: 'P1MT'.",
            "P5 Main|Text cannot be parsed to a Duration. Text: 'P5 Main'.",
            "P1W2D|Text cannot be parsed to a Duration. Text: 'P1W2D'.",
            "P1HT5M|Text cannot be parsed to a Duration. Text: 'P1HT5M'.",
            "PTX|Text cannot be parsed to a Duration. Text: 'PTX'.",
            "Pending|Text cannot be parsed to a Duration. Text: 'Pending'.",
            "P1W|Text cannot be parsed to a Duration. Text: 'P1W'.",
            "soon|Text cannot be parsed to a Duration. Text: 'soon'."})
    void textThatIsNotADuration(final String text, final String message) {
        assertThat(ValDurationUtil.toDuration(ValString.create(text))).isEqualTo(ValErr.create(message));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "PT9999999999999H", "PT99999999999999999H", "PT99999999999999999999999H", "-PT9999999999999H"})
    void parseISODurationThatIsTooLong(final String text) {
        assertThat(ValDurationUtil.parseISODuration(ValString.create(text)))
                .isEqualTo(ValErr.create("The duration is too long. Text: '" + text + "'."));
    }

    @Test
    void parseISODurationOfMonths() {
        assertThat(ValDurationUtil.parseISODuration(ValString.create("P1M")))
                .isEqualTo(ValErr.create("Text cannot be parsed to a Duration. Text: 'P1M'."));
    }

    @Test
    void parseMillisecondsOfMonths() {
        // It is for whatever asked to say what to do about it.
        assertThatThrownBy(() -> ValDurationUtil.parseMilliseconds("1M"))
                .isInstanceOf(DateTimeParseException.class)
                .hasMessage("'1M' is a period of months or years, so is not a duration");
    }

    @Test
    void toMillis() {
        assertThat(ValDurationUtil.toMillis(ValDuration.create(5))).isEqualTo(5);
        assertThat(ValDurationUtil.toMillis(ValDouble.create(5.9))).isEqualTo(5);
        assertThat(ValDurationUtil.toMillis(ValPeriod.create(0, 0, 1))).isEqualTo(86_400_000);
        assertThat(ValDurationUtil.toMillis(ValString.create(" 1h 30m "))).isEqualTo(5_400_000);
        assertThat(ValDurationUtil.toMillis(ValString.create("-PT1H"))).isEqualTo(-3_600_000);
        for (final Val val : List.of(ValString.create("N/A"), ValString.create("Pending"), ValString.create("1M"),
                ValString.create("1H"), ValString.create("P1M"), ValString.create("1e300"), ValString.create(" "),
                ValDate.create(5), ValBoolean.TRUE, ValNull.INSTANCE, ValErr.create("bad"))) {
            assertThat(ValDurationUtil.toMillis(val)).as(val.toString()).isNull();
        }
    }

    @Test
    void valuesThatAreNotDurations() {
        for (final Val val : List.of(
                ValBoolean.TRUE,
                ValDate.create(0),
                ValPeriod.create(0, 1, 0),
                ValDouble.create(Double.NaN),
                ValDouble.create(1e300),
                ValFloat.create(Float.NEGATIVE_INFINITY))) {
            assertThat(ValDurationUtil.toDuration(val)).as(val.toString()).isInstanceOf(ValErr.class);
            assertThat(ValDurationUtil.parseISODuration(val)).as(val.toString()).isInstanceOf(ValErr.class);
            assertThat(ValDurationUtil.formatDuration(val)).as(val.toString()).isInstanceOf(ValErr.class);
            assertThat(ValDurationUtil.formatISODuration(val)).as(val.toString()).isInstanceOf(ValErr.class);
        }
        for (final Val val : List.of(ValNull.INSTANCE, ValErr.create("bad"))) {
            assertThat(ValDurationUtil.toDuration(val)).isEqualTo(val);
            assertThat(ValDurationUtil.parseISODuration(val)).isEqualTo(val);
            assertThat(ValDurationUtil.formatDuration(val)).isEqualTo(val);
            assertThat(ValDurationUtil.formatISODuration(val)).isEqualTo(val);
        }
    }

    @Test
    void textOfADurationIsReadTheSameByEveryFunction() {
        for (final String text : List.of("0.7d", "-1.5h", "1h 30m", " PT1H ", "-PT1H", "-5", "1E3", "1500us", "'1h'",
                "99999999999999999999", "1M", "1H", "soon")) {
            final Val val = ValString.create(text);
            final Val duration = ValDurationUtil.toDuration(val);
            assertThat(ValDurationUtil.formatDuration(val)).as(text)
                    .isEqualTo(ValDurationUtil.formatDuration(duration));
            assertThat(ValDurationUtil.formatISODuration(val)).as(text)
                    .isEqualTo(ValDurationUtil.formatISODuration(duration));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "0,0ms",
            "-1,-1ms",
            "-1500,-1.5s",
            "-7200000,-2h",
            "-90061000,-1d",
            "9223372036854775807,106751991167d",
            "-9223372036854775808,-106751991167d"})
    void formatDurationOfNegatives(final long millis, final String expected) {
        assertThat(ValDurationUtil.formatDuration(ValDuration.create(millis))).isEqualTo(ValString.create(expected));
        assertThat(ValDurationUtil.formatDuration(ValLong.create(millis))).isEqualTo(ValString.create(expected));
    }

    private static void getTestAssertions(final TestOutcome<?, ?> testOutcome) {
        if (testOutcome.getExpectedOutput() instanceof final ValErr valErrExpected) {
            assertThat(testOutcome.getActualOutput())
                    .isInstanceOf(ValErr.class);
            assertThat(((ValErr) testOutcome.getActualOutput()).getMessage())
                    .containsIgnoringCase(valErrExpected.getMessage());
        } else {
            assertThat(testOutcome.getActualOutput())
                    .isEqualTo(testOutcome.getExpectedOutput());
        }
    }

    private static String getTestName(final TestCase<?, ?> testCase) {
        final Object input = testCase.getInput();
        if (input == null) {
            return "null";
        } else {
            return input.getClass().getSimpleName() + "(" + input + ")";
        }
    }
}
