package stroom.test.common;

import stroom.util.NullSafe;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Useful utility methods for junit tests
 */
public class TestUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestUtil.class);

    private TestUtil() {
    }

    /**
     * Creates dynamic tests bases on the passed testCases.
     * Uses the toString of {@link TestCase#getInput()} for the test name.
     */
    public static <I, O> Stream<DynamicTest> createDynamicTestStream(
            final List<TestCase<I, O>> testCases,
            final Consumer<TestCase<I, O>> work) {
        return createDynamicTestStream(
                testCases,
                TestUtil::buildTestName,
                work);
    }

    private static String buildTestName(final TestCase<?, ?> testCase) {
        return NullSafe.getOrElseGet(
                testCase,
                TestCase::getName,
                () -> {
                    String inputStr = NullSafe.toStringOrElse(
                            testCase,
                            TestCase::getInput,
                            "<NULL>");

                    if (inputStr.isEmpty()) {
                        inputStr = "<EMPTY STRING>";
                    }

                    return "Input: '" + inputStr + "'";
                });
    }

    /**
     * Creates dynamic tests bases on the passed testCases.
     * Uses the toString of {@link TestCase#getInput()} for the test name.
     *
     * @param nameFunction Function to provide a name for the test using the testCase
     */
    public static <I, O> Stream<DynamicTest> createDynamicTestStream(
            final List<TestCase<I, O>> testCases,
            final Function<TestCase<I, O>, String> nameFunction,
            final Consumer<TestCase<I, O>> work) {
        Objects.requireNonNull(work);
        Objects.requireNonNull(testCases);

        return testCases.stream()
                .map(testCase -> {
                    final String testName = nameFunction.apply(testCase);
                    return DynamicTest.dynamicTest(testName, () -> {
                        LOGGER.debug(() -> LogUtil.message("Input: '{}', expectedOutput: '{}'",
                                testCase.getInput(), testCase.getExpectedOutput()));
                        work.accept(testCase);
                    });
                });
    }

    public static <I, O> DynamicDynamicTestBuilder<I, O> buildDynamicTestStream(
            final Class<I> inputType,
            final Class<O> outputType) {
        return new DynamicDynamicTestBuilder<>(inputType, outputType);
    }

    public static <T> DynamicDynamicTestBuilder<T, T> buildDynamicTestStream(
            final Class<T> inputOutputType) {
        return new DynamicDynamicTestBuilder<>(inputOutputType, inputOutputType);
    }

    /**
     * Logs to info message followed by ':\n', followed by an ASCII table
     * of the map entries
     */
    public static <K, V> void dumpMapToInfo(final String message,
                                            final Map<K, V> map) {
        LOGGER.info("{}:\n{}", message, AsciiTable.from(map));
    }

    /**
     * Logs to debug message followed by ':\n', followed by an ASCII table
     * of the map entries
     */
    public static <K, V> void dumpMapToDebug(final String message,
                                             final Map<K, V> map) {
        LOGGER.debug("{}:\n{}", message, AsciiTable.from(map));
    }

    /**
     * Repeatedly call test with pollFrequency until it returns true, or timeout is reached.
     * If timeout is reached before test returns true a {@link RuntimeException} is thrown.
     *
     * @param valueSupplier   Supplier of the value to test. This will be called repeatedly until
     *                        its return value match requiredValue, or timeout is reached.
     * @param requiredValue   The value that valueSupplier is required to ultimately return.
     * @param messageSupplier Supplier of the name of the thing being waited for.
     * @param timeout         The timeout duration after which waitForIt will give up and throw
     *                        a {@link RuntimeException}.
     * @param pollFrequency   The time between calls to valueSupplier.
     */
    public static <T> void waitForIt(final Supplier<T> valueSupplier,
                                     final T requiredValue,
                                     final Supplier<String> messageSupplier,
                                     final Duration timeout,
                                     final Duration pollFrequency) {

        final Instant startTime = Instant.now();
        final Instant endTime = startTime.plus(timeout);
        T currValue = null;
        while (Instant.now().isBefore(endTime)) {
            currValue = valueSupplier.get();
            if (Objects.equals(currValue, requiredValue)) {
                LOGGER.debug("Waited {}", Duration.between(startTime, Instant.now()));
                return;
            } else {
                ThreadUtil.sleepIgnoringInterrupts(pollFrequency.toMillis());
            }
        }

        // Timed out so throw
        throw new RuntimeException(LogUtil.message("Timed out (timeout: {}) waiting for '{}' to be '{}'. " +
                        "Last value '{}'",
                timeout,
                messageSupplier.get(),
                requiredValue,
                currValue));
    }

    /**
     * Repeatedly call test with pollFrequency until it returns true, or timeout is reached.
     * If timeout is reached before test returns true a {@link RuntimeException} is thrown.
     * A default timeout of 5s is used with a default pollFrequency of 1ms.
     *
     * @param valueSupplier   Supplier of the value to test. This will be called repeatedly until
     *                        its return value match requiredValue, or timeout is reached.
     * @param requiredValue   The value that valueSupplier is required to ultimately return.
     * @param messageSupplier Supplier of the name of the thing being waited for.
     */
    public static <T> void waitForIt(final Supplier<T> valueSupplier,
                                     final T requiredValue,
                                     final Supplier<String> messageSupplier) {
        waitForIt(
                valueSupplier,
                requiredValue,
                messageSupplier,
                Duration.ofSeconds(5),
                Duration.ofMillis(1));
    }

    /**
     * Repeatedly call test with pollFrequency until it returns true, or timeout is reached.
     * If timeout is reached before test returns true a {@link RuntimeException} is thrown.
     * A default timeout of 5s is used with a default pollFrequency of 1ms.
     *
     * @param valueSupplier Supplier of the value to test. This will be called repeatedly until
     *                      its return value match requiredValue, or timeout is reached.
     * @param requiredValue The value that valueSupplier is required to ultimately return.
     * @param message       The name of the thing being waited for.
     */
    public static <T> void waitForIt(final Supplier<T> valueSupplier,
                                     final T requiredValue,
                                     final String message) {
        waitForIt(
                valueSupplier,
                requiredValue,
                () -> message,
                Duration.ofSeconds(5),
                Duration.ofMillis(1));
    }

    public static class DynamicDynamicTestBuilder<I, O> {

        // Pass in the classes to set the generic types
        private DynamicDynamicTestBuilder(final Class<I> inputType,
                                          final Class<O> outputType) {
        }

        /**
         * Runs <pre>test</pre> for each {@link TestCase}. Caller is responsible for performing
         * all test assertions they need in <pre>test</pre>.
         */
        public CasesDynamicTestBuilder<I, O> withTest(final Consumer<TestCase<I, O>> test) {
            Objects.requireNonNull(test);
            return new CasesDynamicTestBuilder<>(this, test);
        }

        /**
         * Asserts that the value provided by outputValueFunction is equal to
         * {@link TestCase#getExpectedOutput()}
         */
        public CasesDynamicTestBuilder<I, O> withSimpleEqualityTest(
                final Function<TestCase<I, O>, O> outputValueFunction) {

            Objects.requireNonNull(outputValueFunction);
            return new CasesDynamicTestBuilder<>(this, testCase -> {
                final O actualOutput = outputValueFunction.apply(testCase);
                LOGGER.debug("Actual output: '{}'", actualOutput);
                Assertions.assertThat(actualOutput)
                        .isEqualTo(testCase.getExpectedOutput());
            });
        }
    }

    public static class CasesDynamicTestBuilder<I, O> {

        private final DynamicDynamicTestBuilder<I, O> builder;
        private final Consumer<TestCase<I, O>> test;
        private final List<TestCase<I, O>> testCases = new ArrayList<>();

        private CasesDynamicTestBuilder(final DynamicDynamicTestBuilder<I, O> builder,
                                        final Consumer<TestCase<I, O>> test) {
            this.builder = builder;
            this.test = test;
        }

        public CasesDynamicTestBuilder<I, O> addCase(final TestCase<I, O> testCase) {
            Objects.requireNonNull(testCase);
            this.testCases.add(testCase);
            return this;
        }

        public CasesDynamicTestBuilder<I, O> addCases(final Collection<TestCase<I, O>> testCases) {
            Objects.requireNonNull(testCases);
            this.testCases.addAll(testCases);
            return this;
        }

        public CasesDynamicTestBuilder<I, O> addCase(final I input,
                                                     final O expectedOutput) {
            addCase(TestCase.of(input, expectedOutput));
            return this;
        }

        public Stream<DynamicTest> build() {
            return TestUtil.createDynamicTestStream(testCases, test);
        }
    }
}
