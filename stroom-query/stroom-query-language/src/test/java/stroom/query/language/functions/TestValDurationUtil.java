/*
 * Copyright 2016-2025 Crown Copyright
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
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

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
    Stream<DynamicTest> formatDuration_string() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(Val.class)
                .withSingleArgTestFunction(ValDurationUtil::formatDuration)
                .withAssertions(TestValDurationUtil::getTestAssertions)
                .withNameFunction(TestValDurationUtil::getTestName)
                .addCase(null, ValNull.INSTANCE)
                .addCase("", ValNull.INSTANCE)
                .addCase("1000", Val.create("1s"))
                .addCase("60000", Val.create("1m"))
                .addCase("10d", Val.create("10d"))
                .addCase("P1D", Val.create("1d"))
                .addCase("foo", ValErr.create(ERR_TEXT))
                .addCase("P1D foo", ValErr.create(ERR_TEXT))
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
    Stream<DynamicTest> formatISODuration_string() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(Val.class)
                .withSingleArgTestFunction(ValDurationUtil::formatISODuration)
                .withAssertions(TestValDurationUtil::getTestAssertions)
                .withNameFunction(TestValDurationUtil::getTestName)
                .addCase(null, ValNull.INSTANCE)
                .addCase("", ValNull.INSTANCE)
                .addCase("1000", Val.create("PT1S"))
                .addCase("60000", Val.create("PT1M"))
                .addCase("10d", Val.create("PT240H"))
                .addCase("P1D", Val.create("PT24H"))
                .addCase("foo", ValErr.create(ERR_TEXT))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> parseDuration_val() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(Val.class)
                .withSingleArgTestFunction(ValDurationUtil::parseDuration)
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
    Stream<DynamicTest> parseDuration_string() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(Val.class)
                .withSingleArgTestFunction(ValDurationUtil::parseDuration)
                .withAssertions(TestValDurationUtil::getTestAssertions)
                .withNameFunction(TestValDurationUtil::getTestName)
                .addCase(null, ValNull.INSTANCE)
                .addCase("", ValNull.INSTANCE)
                .addCase("1000", Val.create(Duration.ofSeconds(1)))
                .addCase("60000", Val.create(Duration.ofMinutes(1)))
                .addCase("10d", Val.create(Duration.ofDays(10)))
                .addCase("P1D", Val.create(Duration.ofDays(1)))
                .addCase("foo", ValErr.create(ERR_TEXT))
                .addCase("P1D foo", ValErr.create(ERR_TEXT))
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
                .addCase(Val.create("10d"), ValErr.create(ERR_TEXT))
                .addCase(Val.create("P1D"), Val.create(Duration.ofDays(1)))
                .addCase(Val.create("foo"), ValErr.create(ERR_TEXT))
                .addCase(Val.create("P1D foo"), ValErr.create(ERR_TEXT))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> parseISODuration_string() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(Val.class)
                .withSingleArgTestFunction(ValDurationUtil::parseISODuration)
                .withAssertions(TestValDurationUtil::getTestAssertions)
                .withNameFunction(TestValDurationUtil::getTestName)
                .addCase(null, ValNull.INSTANCE)
                .addCase("", ValNull.INSTANCE)
                .addCase("P1D", Val.create(Duration.ofDays(1)))
                .addCase("10d", ValErr.create(ERR_TEXT))
                .addCase("foo", ValErr.create(ERR_TEXT))
                .addCase("P1D foo", ValErr.create(ERR_TEXT))
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
