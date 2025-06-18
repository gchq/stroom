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

package stroom.util.string;

import stroom.test.common.TestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
}
