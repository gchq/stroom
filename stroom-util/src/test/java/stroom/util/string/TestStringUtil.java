package stroom.util.string;

import stroom.test.common.TestUtil;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Collections;
import java.util.List;
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
}
