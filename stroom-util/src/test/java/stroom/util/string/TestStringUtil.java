package stroom.util.string;

import stroom.test.common.TestUtil;

import com.google.inject.TypeLiteral;
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
}
