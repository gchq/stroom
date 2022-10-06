package stroom.test.common;

import stroom.util.NullSafe;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
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

    /**
     * Builder for creating a Junit5 {@link DynamicTest} {@link Stream}.
     */
    public static InitialBuilder buildDynamicTestStream() {
        return new InitialBuilder();
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


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public static class InitialBuilder {

        /**
         * Define the type of the input for the dynamic tests, e.g.
         * <pre>{@code withInputType(String.class)}</pre>
         */
        @SuppressWarnings("unused")
        public <I> InputBuilder<I> withInputType(final Class<I> inputType) {
            final TypeLiteral<I> typeLiteral = new TypeLiteral<I>() {
            };
            return new InputBuilder<>(typeLiteral);
        }

        /**
         * Define the type of both the input and expected for the dynamic tests,
         * <pre>{@code withInputAndOutputType(String.class)}</pre>
         */
        @SuppressWarnings("unused")
        public <T> OutputBuilder<T, T> withInputAndOutputType(final Class<T> type) {
            final InputBuilder<T> inputBuilder = new InputBuilder<>(type);
            final TypeLiteral<T> typeLiteral = new TypeLiteral<T>() {
            };
            return new OutputBuilder<T, T>(inputBuilder, typeLiteral);
        }

        /**
         * Define the type of the input for the dynamic tests where the type uses generics,
         * e.g. a {@link Collection<?>} or a {@link io.vavr.Tuple}. Specify the type using a
         * {@link TypeLiteral}, e.g.
         * <pre>{@code withWrappedInputType(new TypeLiteral<List<String>>(){})}</pre>
         * <pre>{@code withWrappedInputType(new TypeLiteral<Tuple2<Integer, String>>(){})}</pre>
         * If you have multiple wrapped inputs then wrap them in a {@link io.vavr.Tuple} or similar.
         */
        @SuppressWarnings("unused")
        public <I> InputBuilder<I> withWrappedInputType(final TypeLiteral<I> inputType) {
            return new InputBuilder<>(inputType);
        }

        /**
         * Define the types of the two inputs for the dynamic tests, e.g.
         * <pre>{@code withInputTypes(Integer.class, String.class)}</pre>
         */
        @SuppressWarnings("unused")
        public <I extends Tuple2<I1, I2>, I1, I2> InputBuilder<I> withInputTypes(
                @SuppressWarnings("unused") final Class<I1> input1Type,
                @SuppressWarnings("unused") final Class<I2> input2Type) {

            @SuppressWarnings("unchecked") final TypeLiteral<I> typeLiteral =
                    (TypeLiteral<I>) new TypeLiteral<Tuple2<I1, I2>>() {
                    };

            return new InputBuilder<>(typeLiteral);
        }

        /**
         * Define the types of the three inputs for the dynamic tests, e.g.
         * <pre>{@code withInputTypes(Integer.class, String.class, Long.class)}</pre>
         */
        @SuppressWarnings("unused")
        public <I extends Tuple3<I1, I2, I3>, I1, I2, I3> InputBuilder<I> withInputTypes(
                @SuppressWarnings("unused") final Class<I1> input1Type,
                @SuppressWarnings("unused") final Class<I2> input2Type,
                @SuppressWarnings("unused") final Class<I3> input3Type) {

            @SuppressWarnings("unchecked") final TypeLiteral<I> typeLiteral =
                    (TypeLiteral<I>) new TypeLiteral<Tuple3<I1, I2, I3>>() {
                    };

            return new InputBuilder<I>(typeLiteral);
        }
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public static class InputBuilder<I> {

        private InputBuilder(@SuppressWarnings("unused") final Class<I> input1Type) {
        }

        private InputBuilder(@SuppressWarnings("unused") final TypeLiteral<I> input1Type) {
        }

        /**
         * Define the type of the output for the dynamic tests, e.g.
         * <pre>{@code withOutputType(String.class)}</pre>
         */
        @SuppressWarnings("unused")
        public <O> OutputBuilder<I, O> withOutputType(final Class<O> outputType) {
            final TypeLiteral<O> typeLiteral = new TypeLiteral<O>() {
            };
            return new OutputBuilder<>(this, typeLiteral);
        }

        /**
         * Define the type of the output for the dynamic tests where the type uses generics,
         * e.g. a {@link Collection<?>} or a {@link io.vavr.Tuple}. Specify the type using a
         * {@link TypeLiteral}, e.g.
         * <pre>{@code withWrappedOutputType(new TypeLiteral<List<String>>(){})}</pre>
         * <pre>{@code withWrappedOutputType(new TypeLiteral<Tuple2<Integer, String>>(){})}</pre>
         * If you have multiple wrapped outputs then wrap them in a {@link io.vavr.Tuple} or similar.
         */
        @SuppressWarnings("unused")
        public <O> OutputBuilder<I, O> withWrappedOutputType(final TypeLiteral<O> outputType) {
            return new OutputBuilder<>(this, outputType);
        }

        /**
         * Define the types of the two outputs for the dynamic tests, e.g.
         * <pre>{@code withOutputTypes(Integer.class, String.class)}</pre>
         */
        @SuppressWarnings("unused")
        public <O extends Tuple2<O1, O2>, O1, O2> OutputBuilder<I, O> withOutputTypes(
                @SuppressWarnings("unused") final Class<O1> output1Type,
                @SuppressWarnings("unused") final Class<O2> output2Type) {

            @SuppressWarnings("unchecked") final TypeLiteral<O> typeLiteral =
                    (TypeLiteral<O>) new TypeLiteral<Tuple2<O1, O2>>() {
                    };

            return new OutputBuilder<>(this, typeLiteral);
        }

        /**
         * Define the types of the three outputs for the dynamic tests, e.g.
         * <pre>{@code withOutputTypes(Integer.class, String.class, Long.class)}</pre>
         */
        @SuppressWarnings("unused")
        public <O extends Tuple3<O1, O2, O3>, O1, O2, O3> OutputBuilder<I, O> withOutputTypes(
                @SuppressWarnings("unused") final Class<O1> output1Type,
                @SuppressWarnings("unused") final Class<O2> output2Type,
                @SuppressWarnings("unused") final Class<O3> output3Type) {

            @SuppressWarnings("unchecked") final TypeLiteral<O> typeLiteral =
                    (TypeLiteral<O>) new TypeLiteral<Tuple3<O1, O2, O3>>() {
                    };

            return new OutputBuilder<>(this, typeLiteral);
        }
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public static class OutputBuilder<I, O> {

        private OutputBuilder(@SuppressWarnings("unused") final InputBuilder<I> inputBuilder,
                              @SuppressWarnings("unused") final TypeLiteral<O> outputType) {
        }

        /**
         * Define the action for the dynamic test lambda, where the action consumes a {@link TestCase}
         * and uses {@link TestCase#getInput()} to produce some output to evaluate against
         * {@link TestCase#getExpectedOutput()}.
         */
        @SuppressWarnings("unused")
        public CasesBuilder<I, O> withTestAction(final Consumer<TestCase<I, O>> test) {
            Objects.requireNonNull(test);
            return new CasesBuilder<>(test);
        }
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public static class CasesBuilder<I, O> {

        private final Consumer<TestCase<I, O>> test;
        private final List<TestCase<I, O>> testCases = new ArrayList<>();

        private CasesBuilder(final Consumer<TestCase<I, O>> test) {
            this.test = test;
        }

        /**
         * Add a test case to the {@link DynamicTest} {@link Stream}.
         */
        @SuppressWarnings("unused")
        public CasesBuilder<I, O> addCase(final TestCase<I, O> testCase) {
            Objects.requireNonNull(testCase);
            this.testCases.add(testCase);
            return this;
        }

        /**
         * Add a {@link Collection} of test cases to the {@link DynamicTest} {@link Stream}.
         */
        @SuppressWarnings("unused")
        public CasesBuilder<I, O> addCases(final Collection<TestCase<I, O>> testCases) {
            Objects.requireNonNull(testCases);
            this.testCases.addAll(testCases);
            return this;
        }

        /**
         * Add a test case to the {@link DynamicTest} {@link Stream} by specifying the input
         * and expected output of the test.
         *
         * @param input          The input to the test case.
         * @param expectedOutput The expected output of the test case.
         */
        @SuppressWarnings("unused")
        public CasesBuilder<I, O> addCase(final I input,
                                          final O expectedOutput) {
            addCase(TestCase.of(input, expectedOutput));
            return this;
        }

        /**
         * Add a test case to the {@link DynamicTest} {@link Stream} by specifying the input
         * and expected output of the test.
         *
         * @param name           The name of the test case.
         * @param input          The input to the test case.
         * @param expectedOutput The expected output of the test case.
         */
        @SuppressWarnings("unused")
        public CasesBuilder<I, O> addCase(final String name,
                                          final I input,
                                          final O expectedOutput) {
            addCase(TestCase.of(name, input, expectedOutput));
            return this;
        }

        /**
         * Build the {@link Stream} of {@link DynamicTest} with all the added test cases.
         */
        @SuppressWarnings("unused")
        public Stream<DynamicTest> build() {
            return TestUtil.createDynamicTestStream(testCases, test);
        }
    }
}
