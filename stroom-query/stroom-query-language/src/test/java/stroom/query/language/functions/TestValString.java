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

import stroom.test.common.TestUtil;
import stroom.util.date.DateUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.math.BigDecimal;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestValString {

//    @Test
//    void testSerDeser() {
//        TestUtil.testSerialisation(ValString.create("foo"), ValString.class);
//    }

    @TestFactory
    Stream<DynamicTest> testHasNumericValue() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValString.class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(ValString::hasNumericValue)
                .withSimpleEqualityAssertion()
                .addCase(ValString.create("foo"), false)
                .addCase(ValString.create("10 foo"), false)
                .addCase(ValString.create(""), false)

                .addCase(ValString.create("0"), true)
                .addCase(ValString.create("1"), true)
                .addCase(ValString.create("-1"), true)
                .addCase(ValString.create("1.1"), true)
                .addCase(ValString.create(" 1.2"), true)
                .addCase(ValString.create("1.3 "), true)
                .addCase(ValString.create(" 1.4 "), true)
                .addCase(ValString.create("1234"), true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testHasFractionalPart() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValString.class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(ValString::hasFractionalPart)
                .withSimpleEqualityAssertion()
                .addCase(ValString.create("foo"), false)
                .addCase(ValString.create("10 foo"), false)
                .addCase(ValString.create(""), false)
                .addCase(ValString.create("1"), false)
                .addCase(ValString.create("1234"), false)

                .addCase(ValString.create("1.1"), true)
                .addCase(ValString.create(" 1.2"), true)
                .addCase(ValString.create("1.3 "), true)
                .addCase(ValString.create(" 1.4 "), true)
                .addCase(ValString.create("1.0000000000001"), true)
                .addCase(ValString.create(new BigDecimal(Long.toString(Long.MAX_VALUE)).toString()),
                        false)
                .addCase(ValString.create(Double.toString(Double.parseDouble(Long.toString(Long.MAX_VALUE)))),
                        false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testToString() {
        final Instant now = Instant.now();
        final Duration duration = Duration.ofDays(10);
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValString.class)
                .withOutputType(String.class)
                .withSingleArgTestFunction(ValString::toString)
                .withSimpleEqualityAssertion()
                .addCase(ValString.create("foo"), "foo")
                .addCase(ValString.create("10 foo"), "10 foo")
                .addCase(ValString.create(""), "")
                .addCase(ValString.create("1"), "1")
                .addCase(ValString.create("1234"), "1234")

                .addCase(ValString.create("1.1"), "1.1")
                .addCase(ValString.create(" 1.2"), " 1.2")
                .addCase(ValString.create("1.3 "), "1.3 ")
                .addCase(ValString.create(" 1.4 "), " 1.4 ")
                .addCase(
                        ValString.create(DateUtil.createNormalDateTimeString(now)),
                        DateUtil.createNormalDateTimeString(now))
                .addCase(
                        ValString.create(duration.toString()),
                        duration.toString())
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testToDouble() {
        final Instant now = Instant.now();
        final Duration duration = Duration.ofDays(10);
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValString.class)
                .withOutputType(Double.class)
                .withSingleArgTestFunction(ValString::toDouble)
                .withSimpleEqualityAssertion()
                .addCase(ValString.create("foo"), null)
                .addCase(ValString.create("10 foo"), null)
                .addCase(ValString.create(""), null)
                .addCase(ValString.create("1"), 1D)
                .addCase(ValString.create("1234"), 1234D)
                .addCase(ValString.create("1.1"), 1.1D)
                .addCase(ValString.create(" 1.2"), 1.2D)
                .addCase(ValString.create("1.3 "), 1.3D)
                .addCase(ValString.create(" 1.4 "), 1.4D)
                // Date parsed then returned as millis
                .addCase(
                        ValString.create(DateUtil.createNormalDateTimeString(now)),
                        (double) now.toEpochMilli())
                // Duration parsed then returned as millis
                .addCase(
                        ValString.create(duration.toString()),
                        (double) duration.toMillis())
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testToLong() {
        final Instant now = Instant.now();
        final Duration duration = Duration.ofDays(10);
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValString.class)
                .withOutputType(Long.class)
                .withSingleArgTestFunction(ValString::toLong)
                .withSimpleEqualityAssertion()
                .addCase(ValString.create("foo"), null)
                .addCase(ValString.create("10 foo"), null)
                .addCase(ValString.create(""), null)
                .addCase(ValString.create("1"), 1L)
                .addCase(ValString.create("1234"), 1234L)
                .addCase(ValString.create("1.1"), 1L)
                .addCase(ValString.create(" 1.2"), 1L)
                .addCase(ValString.create("1.3 "), 1L)
                .addCase(ValString.create(" 1.4 "), 1L)
                // Date parsed then returned as millis
                .addCase(
                        ValString.create(DateUtil.createNormalDateTimeString(now)),
                        now.toEpochMilli())
                // Duration parsed then returned as millis
                .addCase(
                        ValString.create(duration.toString()),
                        duration.toMillis())
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testToInteger() {
        final Instant now = Instant.now();
        final Duration duration = Duration.ofDays(10);
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValString.class)
                .withOutputType(Integer.class)
                .withSingleArgTestFunction(ValString::toInteger)
                .withSimpleEqualityAssertion()
                .addCase(ValString.create("foo"), null)
                .addCase(ValString.create("10 foo"), null)
                .addCase(ValString.create(""), null)
                .addCase(ValString.create("1"), 1)
                .addCase(ValString.create("1234"), 1234)
                .addCase(ValString.create("1.1"), 1)
                .addCase(ValString.create(" 1.2"), 1)
                .addCase(ValString.create("1.3 "), 1)
                .addCase(ValString.create(" 1.4 "), 1)
                // Date parsed then returned as millis
                .addCase(
                        ValString.create(DateUtil.createNormalDateTimeString(now)),
                        (int) now.toEpochMilli())
                // Duration parsed then returned as millis
                .addCase(
                        ValString.create(duration.toString()),
                        (int) duration.toMillis())
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testToBoolean() {
        final Instant now = Instant.now();
        final Duration duration = Duration.ofDays(10);
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValString.class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(ValString::toBoolean)
                .withSimpleEqualityAssertion()
                .addCase(ValString.create("TRUE"), true)
                .addCase(ValString.create("true"), true)
                .addCase(ValString.create("1"), true)
                .addCase(ValString.create("1234"), true)

                .addCase(ValString.create("1.1"), false)
                .addCase(ValString.create("false"), false)
                .addCase(ValString.create("foo"), false)
                .addCase(ValString.create("10 foo"), false)
                .addCase(ValString.create(""), false)
                .addCase(ValString.create("0"), false)
                // Date parsed then returned as millis
                .addCase(
                        ValString.create(DateUtil.createNormalDateTimeString(now)),
                        false)
                // Duration parsed then returned as millis
                .addCase(
                        ValString.create(duration.toString()),
                        false)
                .build();
    }

    // Any text can be compared with a number or used in a sum, so what is read as a number can't be changed without
    // changing queries that have nothing to do with it. These were recorded before durations could be read exactly.
    @ParameterizedTest
    @CsvFileSource(resources = "TestValString_numbers.csv", nullValues = "null", emptyValue = "",
            ignoreLeadingAndTrailingWhitespace = false)
    void testTextThatIsANumber(final String text,
                               final Long expectedLong,
                               final Double expectedDouble,
                               final boolean expectedNumeric) {
        final ValString val = ValString.create(text);
        assertThat(val.toLong()).isEqualTo(expectedLong);
        assertThat(val.toDouble()).isEqualTo(expectedDouble);
        assertThat(val.hasNumericValue()).isEqualTo(expectedNumeric);
    }

    // Such as an ID.
    @Test
    void testWholeNumberThatIsTooLargeForALong() throws ParseException {
        final ValString text = ValString.create("12345678901234567890");
        assertThat(text.hasNumericValue()).isTrue();
        assertThat(text.hasFractionalPart()).isFalse();
        assertThat(text.toLong()).isEqualTo(Long.MAX_VALUE);
        assertThat(text.toDouble()).isEqualTo(12345678901234567890d);
        assertThat(text.toNumber()).isEqualTo(Long.MAX_VALUE);
        assertThat(DateFunctionTestUtil.eval(
                DateFunctionTestUtil.LONDON, "${field} = 12345678901234567890", text))
                .isEqualTo(ValBoolean.TRUE);
    }
}
