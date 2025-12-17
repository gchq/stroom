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

package stroom.util.string;

import stroom.test.common.TestUtil;
import stroom.test.common.TestUtil.TimedCase;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestStringUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestStringUtil.class);

    @TestFactory
    Stream<DynamicTest> splitToLines_noTrim() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withWrappedOutputType(new TypeLiteral<List<String>>() {
                })
                .withTestFunction(testCase ->
                        StringUtil.splitToLines(testCase.getInput(), false)
                                .collect(Collectors.toList()))
                .withSimpleEqualityAssertion()
                .addCase(null, Collections.emptyList())
                .addCase("", Collections.emptyList())
                .addCase("   ", List.of("   "))
                .addCase("""
                                one
                                two
                                three""",
                        List.of(
                                "one",
                                "two",
                                "three"))
                .addCase("""
                                 one

                                two\s
                                three""",
                        List.of(
                                " one",
                                "",
                                "two ",
                                "three"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> splitToLines_trim() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withWrappedOutputType(new TypeLiteral<List<String>>() {
                })
                .withTestFunction(testCase ->
                        StringUtil.splitToLines(testCase.getInput(), true)
                                .collect(Collectors.toList()))
                .withSimpleEqualityAssertion()
                .addCase(null, Collections.emptyList())
                .addCase("", Collections.emptyList())
                .addCase("   ", Collections.emptyList())
                .addCase("   \n   ", Collections.emptyList())
                .addCase("   \r\n   ", Collections.emptyList())
                .addCase("""
                                one
                                two
                                three""",
                        List.of(
                                "one",
                                "two",
                                "three"))
                .addCase("""
                                 one

                                two\s
                                three""",
                        List.of(
                                "one",
                                "two",
                                "three"))
                .build();
    }


    @TestFactory
    Stream<DynamicTest> testTrimLines() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withSingleArgTestFunction(StringUtil::trimLines)
                .withSimpleEqualityAssertion()
                .addCase(null, null)
                .addCase("", "")
                .addCase(" ", "")
                .addCase(" foo ", "foo")
                .addCase("""
                        \sfoo\s
                        \s bar \s""", """
                        foo
                        bar""")
                .addCase("""
                        \sfoo\s
                        """, "foo")
                .addCase("""

                        \s \s""", "")
                .build();
    }


    @TestFactory
    Stream<DynamicTest> splitToWords() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withWrappedOutputType(new TypeLiteral<List<String>>() {
                })
                .withTestFunction(testCase ->
                        StringUtil.splitToWords(testCase.getInput())
                                .collect(Collectors.toList()))
                .withSimpleEqualityAssertion()
                .addCase(null, Collections.emptyList())
                .addCase("", Collections.emptyList())
                .addCase("   ", Collections.emptyList())
                .addCase("   \n   ", Collections.emptyList())
                .addCase("   \r\n   ", Collections.emptyList())
                .addCase("one two three",
                        List.of(
                                "one",
                                "two",
                                "three"))
                .addCase("one\ttwo\tthree",
                        List.of(
                                "one",
                                "two",
                                "three"))
                .addCase("  one  two  three  ",
                        List.of(
                                "one",
                                "two",
                                "three"))
                .addCase("one\ntwo\nthree",
                        List.of(
                                "one",
                                "two",
                                "three"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testCreateRandomCode() {
        final Pattern allowedCharsPattern = Pattern.compile(
                "[" + new String(StringUtil.ALLOWED_CHARS_BASE_58_STYLE) + "]+");

        // Random content, so we can't do equality asserts on it
        final String expectedOutput = "expected output ignored";
        return TestUtil.buildDynamicTestStream()
                .withInputType(int.class)
                .withOutputType(String.class)
                .withSingleArgTestFunction(StringUtil::createRandomCode)
                .withAssertions(outcome -> {
                    final String actual = outcome.getActualOutput();
                    Assertions.assertThat(actual.length())
                            .isEqualTo(outcome.getInput());
                    Assertions.assertThat(actual)
                            .matches(allowedCharsPattern);
                })
                .addThrowsCase(-1, IllegalArgumentException.class)
                .addThrowsCase(0, IllegalArgumentException.class)
                .addCase(1, expectedOutput)
                .addCase(2, expectedOutput)
                .addCase(40, expectedOutput)
                .addCase(500, expectedOutput)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testCreateRandomCode_customChars() {
        final SecureRandom secureRandom = new SecureRandom();
        final char[] allowedChars = "0123456789ABCDEF".toCharArray();
        final Pattern allowedCharsPattern = Pattern.compile(
                "[" + new String(allowedChars) + "]+");
        // Random content, so we can't do equality asserts on it
        final String expectedOutput = "expected output ignored";
        return TestUtil.buildDynamicTestStream()
                .withInputType(int.class)
                .withOutputType(String.class)
                .withSingleArgTestFunction(length ->
                        StringUtil.createRandomCode(secureRandom, length, allowedChars))
                .withAssertions(outcome -> {
                    final String actual = outcome.getActualOutput();
                    Assertions.assertThat(actual.length())
                            .isEqualTo(outcome.getInput());
                    Assertions.assertThat(actual)
                            .matches(allowedCharsPattern);
                })
                .addThrowsCase(-1, IllegalArgumentException.class)
                .addThrowsCase(0, IllegalArgumentException.class)
                .addCase(1, expectedOutput)
                .addCase(2, expectedOutput)
                .addCase(40, expectedOutput)
                .addCase(500, expectedOutput)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testCreateRandomCode_base64() {
        final SecureRandom secureRandom = new SecureRandom();
        final char[] allowedChars = "0123456789ABCDEF".toCharArray();
        final Pattern allowedCharsPattern = Pattern.compile(
                "[" + new String(allowedChars) + "]+");
        // Random content, so we can't do equality asserts on it
        final String expectedOutput = "expected output ignored";
        return TestUtil.buildDynamicTestStream()
                .withInputType(int.class)
                .withOutputType(String.class)
                .withSingleArgTestFunction(length ->
                        StringUtil.createRandomCode(secureRandom, length, allowedChars))
                .withAssertions(outcome -> {
                    final String actual = outcome.getActualOutput();
                    Assertions.assertThat(actual.length())
                            .isEqualTo(outcome.getInput());
                    Assertions.assertThat(actual)
                            .matches(allowedCharsPattern);
                })
                .addThrowsCase(-1, IllegalArgumentException.class)
                .addThrowsCase(0, IllegalArgumentException.class)
                .addCase(1, expectedOutput)
                .addCase(2, expectedOutput)
                .addCase(40, expectedOutput)
                .addCase(500, expectedOutput)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testEnsureFullStop() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withSingleArgTestFunction(StringUtil::ensureFullStop)
                .withSimpleEqualityAssertion()
                .addCase(null, "")
                .addCase("", "")
                .addCase(" ", " ")
                .addCase("foo", "foo.")
                .addCase("foo ", "foo.")
                .addCase("foo.", "foo.")
                .addCase("foo. ", "foo.")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testConvertRowColToIndex() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, int.class, int.class)
                .withOutputType(int.class)
                .withTestFunction(testCase -> {
                    final String str = testCase.getInput()._1;
                    final int row = testCase.getInput()._2;
                    final int col = testCase.getInput()._3;
                    final int idx = StringUtil.convertRowColToIndex(str, row, col);
                    char charAtRowCol = '?';
                    try {
                        charAtRowCol = str.charAt(idx);
                    } catch (final Exception e) {
                        // swallow
                    }
                    LOGGER.debug("Char: '{}', row: {}, col: {}, idx: {}",
                            charAtRowCol, row, col, idx);

                    try {
                        final Integer expectedIdx = testCase.getExpectedOutput();
                        final char chrAtIdx = str.charAt(expectedIdx);
                        LOGGER.debug("chrAtIdx: '{}' (code: {})",
                                HexDumpUtil.asPrintableChar(chrAtIdx), (int) chrAtIdx);
                    } catch (final StringIndexOutOfBoundsException e) {
                        LOGGER.debug("At end of string");
                    }

                    return idx;
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of("", 0, 0), 0)
                .addCase(Tuple.of(" ", 0, 0), 0)
                .addCase(Tuple.of("..", 0, 1), 1)
                .addCase(Tuple.of("""
                        Line 1
                        Line 2""", 0, 0), 0)
                .addCase(Tuple.of("""
                        Line 1
                        Line 2""", 0, 5), 5)
                .addCase(Tuple.of("""
                        Line 1
                        Line 2""", 1, 0), 7)
                .addCase(Tuple.of("""
                        Line 1
                        Line 2""", 1, 5), 12)
                .addCase(Tuple.of("""
                        Line 1
                        """, 1, 0), 7)
                .addThrowsCase(Tuple.of("", 1, 0), IllegalArgumentException.class)
                .addThrowsCase(Tuple.of("", 0, 1), IllegalArgumentException.class)
                .addThrowsCase(Tuple.of("""
                        Line 1
                        Line 2""", 0, 99), IllegalArgumentException.class)
                .addThrowsCase(Tuple.of("""
                        Line 1
                        Line 2""", 1, 99), IllegalArgumentException.class)
                .addThrowsCase(Tuple.of("""
                        Line 1
                        Line 2""", 2, 99), IllegalArgumentException.class)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testAsBoolean() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(StringUtil::asBoolean)
                .withSimpleEqualityAssertion()
                .addCase("y", true)
                .addCase("Y", true)
                .addCase("yes", true)
                .addCase("YES", true)
                .addCase("Yes", true)
                .addCase("true", true)
                .addCase("TRUE", true)
                .addCase("True", true)
                .addCase("on", true)
                .addCase("ON", true)
                .addCase("On", true)
                .addCase("enabled", true)
                .addCase("ENABLED", true)
                .addCase("Enabled", true)
                .addCase("1", true)
                .addCase("0", false)
                .addCase("false", false)
                .addCase("FALSE", false)
                .addCase("False", false)
                .addCase("foo", false)
                .addCase("", false)
                .addCase(null, false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> deDupDelimiters() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withTestFunction(testCase ->
                        StringUtil.deDupDelimiters(testCase.getInput(), ','))
                .withSimpleEqualityAssertion()
                .addCase(null, null)
                .addCase("", "")
                .addCase("foo", "foo")
                .addCase(",foo", "foo")
                .addCase("foo,", "foo")
                .addCase(",foo,", "foo")
                .addCase("foo,bar", "foo,bar")
                .addCase("foo,,bar", "foo,bar")
                .addCase(",foo,bar,", "foo,bar")
                .addCase(",,foo,,bar,,", "foo,bar")
                .addCase(",,,foo,,,bar,,,", "foo,bar")
                .addCase(",,a,,b,,c,,", "a,b,c")
                .build();
    }

    @Test
    void testGetDigitCount1() {
        final List<Long> values = List.of(
                0L, 1L,
                10L, 99L,
                100L, 999L,
                1000L, 9999L,
                10000L, 99999L,
                100000L, 999999L,
                1000000L, 9999999L,
                Long.MAX_VALUE);
        values.forEach(i -> {
            final int len1 = StringUtil.getDigitCount(i);
            final int len2 = String.valueOf(i).length();
            assertThat(len2)
                    .isEqualTo(len1);
        });
    }

    @Test
    void testGetDigitCount2() {
        // Test 1/5th of positive integers
        final AtomicInteger atomicInteger = new AtomicInteger(1);
        IntStream.generate(
                        () -> atomicInteger.getAndAccumulate(
                                99,
                                (currVal, ignored) -> {
                                    try {
                                        return currVal * 5;
                                    } catch (final Exception e) {
                                        return -1;
                                    }
                                }))
                .parallel()
                .takeWhile(anInt -> anInt > -1)
                .forEach(i -> {
                    final int len1 = StringUtil.getDigitCount(i);
                    final int len2 = String.valueOf(i).length();
                    assertThat(len2)
                            .isEqualTo(len1);
                });
    }

    @Disabled // Too slow for CI, but proves every case
    @Test
    void testGetDigitCount3() {
        IntStream.rangeClosed(0, Integer.MAX_VALUE)
                .parallel()
                .forEach(i -> {
                    final int len1 = StringUtil.getDigitCount(i);
                    final int len2 = String.valueOf(i).length();
                    assertThat(len2)
                            .isEqualTo(len1);
                });
    }

    // getDigitCount is ~x10 faster, 0.62ns/op vs 8.64ns/op
    @Disabled
    @Test
    void testGetDigitCountPerf() {
        final int totalIterations = 100_000_000;
        //noinspection MismatchedReadAndWriteOfArray
        final int[] arr = new int[totalIterations];
        TestUtil.comparePerformance(
                5,
                totalIterations,
                LOGGER::info,
                TimedCase.of(
                        "String.valueOf",
                        (round, iterations) -> {
                            for (int i = 0; i < iterations; i++) {
                                arr[i] = String.valueOf(i).length();
                            }
                        }),
                TimedCase.of(
                        "getDigitCount",
                        (round, iterations) -> {
                            for (int i = 0; i < iterations; i++) {
                                arr[i] = StringUtil.getDigitCount(i);
                            }
                        })
        );
    }
}
