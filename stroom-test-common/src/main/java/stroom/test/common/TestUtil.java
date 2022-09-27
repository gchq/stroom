package stroom.test.common;

import stroom.util.NullSafe;

import org.junit.jupiter.api.DynamicTest;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Useful utility methods for junit tests
 */
public class TestUtil {

    private TestUtil() {
    }

    /**
     * Creates dynamic tests bases on the passed testCases.
     * Uses the toString of {@link TestCase#getInput()} for the test name.
     */
    public static <T1, T2> Stream<DynamicTest> createDynamicTestStream(
            final List<TestCase<T1, T2>> testCases,
            final Consumer<TestCase<T1, T2>> work) {
        return createDynamicTestStream(
                testCases,
                testCase -> {
                    String name = NullSafe.toStringOrElse(
                            testCase,
                            TestCase::getInput,
                            "<NULL>");
                    if (name.isEmpty()) {
                        name = "<EMPTY STRING>";
                    }
                    return name;
                },
                work);
    }

    /**
     * Creates dynamic tests bases on the passed testCases.
     * Uses the toString of {@link TestCase#getInput()} for the test name.
     *
     * @param nameFunction Function to provide a name for the test using the testCase
     */
    public static <T1, T2> Stream<DynamicTest> createDynamicTestStream(
            final List<TestCase<T1, T2>> testCases,
            final Function<TestCase<T1, T2>, String> nameFunction,
            final Consumer<TestCase<T1, T2>> work) {
        Objects.requireNonNull(work);
        Objects.requireNonNull(testCases);

        return testCases.stream()
                .map(testCase -> {
                    final String testName = nameFunction.apply(testCase);
                    return DynamicTest.dynamicTest(testName, () ->
                            work.accept(testCase));
                });
    }
}
