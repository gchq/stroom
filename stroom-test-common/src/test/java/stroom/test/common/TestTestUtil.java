package stroom.test.common;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

class TestTestUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestTestUtil.class);

    @BeforeEach
    void setUp() {
        LOGGER.info("beforeEach called");
    }

    @TestFactory
    Stream<DynamicTest> testBuildDynamicTestStream_sameInputAsOutput() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withTestFunction(testCase ->
                        testCase.getInput().toUpperCase())
                .withSimpleEqualityAssertion()
                .addCase("a", "A")
                .addNamedCase("Zed", "z", "Z")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testBuildDynamicTestStream_simpleTypes() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(Integer.class)
                .withOutputType(String.class)
                .withTestFunction(testCase ->
                        Month.of(testCase.getInput()).getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .withSimpleEqualityAssertion()
                .addCase(1, "January")
                .addNamedCase("December case", 12, "December")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testBuildDynamicTestStream_nameFunction() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(Integer.class)
                .withOutputType(String.class)
                .withTestFunction(testCase ->
                        Month.of(testCase.getInput()).getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .withSimpleEqualityAssertion()
                .addCase(1, "January")
                .addNamedCase("Explicit case name", 12, "December")
                .withNameFunction(testCase ->
                        LogUtil.message("Name func name: '{}' => '{}'",
                                testCase.getInput(), testCase.getExpectedOutput()))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testBuildDynamicTestStream_throwsExpectedException() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withTestFunction(testCase -> {
                    if (testCase.getInput().equals("a")) {
                        throw new IllegalArgumentException("a is not allowed");
                    } else {
                        return testCase.getInput().toUpperCase();
                    }
                })
                .withSimpleEqualityAssertion()
                .addThrowsCase("a", IllegalArgumentException.class)
                .addCase("z", "Z")
                .build();
    }

    @Disabled // This is to make sure the handling of an unexpected exception in a test
    // is handled properly by junit. As the test case fails it can only be a manual test.
    @TestFactory
    Stream<DynamicTest> testBuildDynamicTestStream_throwsUnexpectedException() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withTestFunction(testCase -> {
                    if (testCase.getInput().equals("a")) {
                        throw new IllegalArgumentException("a is not allowed");
                    } else {
                        return testCase.getInput().toUpperCase();
                    }
                })
                .withSimpleEqualityAssertion()
                .addCase("a", "A")
                .addCase("z", "Z")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testBuildDynamicTestStream_collection() {

        final List<String> months = IntStream.rangeClosed(1, 12)
                .boxed()
                .map(i ->
                        Month.of(i).getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .collect(Collectors.toList());

        return TestUtil.buildDynamicTestStream()
                .withInputTypes(Integer.class, Integer.class)
                .withWrappedOutputType(new TypeLiteral<List<String>>() {
                })
                .withTestFunction(testCase ->
                        months.subList(testCase.getInput()._1, testCase.getInput()._2))
                .withAssertions(testOutcome ->
                        Assertions.assertThat(testOutcome.getActualOutput())
                                .containsExactlyElementsOf(testOutcome.getExpectedOutput()))
                .addCase(Tuple.of(0, 2), List.of("January", "February"))
                .build();
    }
}
