package stroom.test.common;

import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;

import com.google.common.base.Strings;
import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class DynamicTestBuilder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestUtil.class);

    private DynamicTestBuilder() {
        // Just a class to bundle up all the builder classes below
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public static class InitialBuilder {

        InitialBuilder() {
        }

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
         * e.g. a {@link Collection <?>} or a {@link io.vavr.Tuple}. Specify the type using a
         * {@link TypeLiteral}, e.g.
         * <pre>{@code withWrappedInputType(new TypeLiteral<List<String>>(){})}</pre>
         * <pre>{@code withWrappedInputType(new TypeLiteral<Tuple2<Integer, String>>(){})}</pre>
         * If you have multiple inputs (at least one of which is a generic type) then wrap them
         * in a {@link io.vavr.Tuple} or similar.
         */
        @SuppressWarnings("unused")
        public <I> InputBuilder<I> withWrappedInputType(final TypeLiteral<I> inputType) {
            return new InputBuilder<>(inputType);
        }

        /**
         * Define the type of both the input and output for the dynamic tests, where the type uses generics,
         * e.g. a {@link Collection <?>} or a {@link io.vavr.Tuple}. Specify the type using a
         * {@link TypeLiteral}, e.g.
         * <pre>{@code withWrappedInputAndOutputType(new TypeLiteral<List<String>>(){})}</pre>
         * <pre>{@code withWrappedInputAndOutputType(new TypeLiteral<Tuple2<Integer, String>>(){})}</pre>
         * If you have multiple inputs/outputs (at least one of which is a generic type) then wrap them
         * in a {@link io.vavr.Tuple} or similar.
         */
        @SuppressWarnings("unused")
        public <T> OutputBuilder<T, T> withWrappedInputAndOutputType(final TypeLiteral<T> inputType) {
            final InputBuilder<T> inputBuilder = new InputBuilder<>(inputType);
            final TypeLiteral<T> typeLiteral = new TypeLiteral<T>() {
            };
            return new OutputBuilder<T, T>(inputBuilder, typeLiteral);
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
         * Define the action for the dynamic test lambda, where the action
         * uses {@link TestCase#getInput()} to produce some output.
         * Test assertions are added later with {@link AssertionsBuilder#withAssertions(Consumer)}.
         */
        @SuppressWarnings("unused")
        public AssertionsBuilder<I, O> withTestFunction(final Function<TestCase<I, O>, O> testFunction) {
            Objects.requireNonNull(testFunction);
            return new AssertionsBuilder<>(testFunction);
        }

        /**
         * Define the action for the dynamic test lambda, where the test input
         * uses {@link TestCase#getInput()} to produce some output.
         * Test assertions are added later with {@link AssertionsBuilder#withAssertions(Consumer)}.
         */
        @SuppressWarnings("unused")
        public AssertionsBuilder<I, O> withSingleArgTestFunction(final Function<I, O> testFunction) {
            Objects.requireNonNull(testFunction);
            return new AssertionsBuilder<>(testCase ->
                    testFunction.apply(testCase.getInput()));
        }
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public static class AssertionsBuilder<I, O> {

        private final Function<TestCase<I, O>, O> testAction;

        private AssertionsBuilder(final Function<TestCase<I, O>, O> testAction) {
            this.testAction = testAction;
        }

        /**
         * Define the assertions to run for this test.
         * If the {@link TestCase} includes an expected {@link Throwable} type then any exceptions will
         * be intercepted and asserted automatically. The consumer will not be called if an exception is
         * expected.
         *
         * @param testOutcomeConsumer A consumer of a {@link TestOutcome} that should run assertions
         *                            using the expected and actual outputs available in the {@link TestOutcome}.
         */
        public CasesBuilder<I, O> withAssertions(final Consumer<TestOutcome<I, O>> testOutcomeConsumer) {
            Objects.requireNonNull(testOutcomeConsumer);
            final Consumer<TestOutcome<I, O>> wrappedConsumer = wrapTestOutcomeConsumer(testOutcomeConsumer);
            return new CasesBuilder<>(testAction, wrappedConsumer);
        }

        /**
         * A pre-canned simple test action that just asserts that the actual output is equal to
         * the expected output.
         * If the {@link TestCase} includes an expected {@link Throwable} type then any exceptions will
         * be intercepted and asserted automatically. The consumer will not be called if an exception is
         * expected.
         */
        public CasesBuilder<I, O> withSimpleEqualityAssertion() {
            final Consumer<TestOutcome<I, O>> wrappedConsumer = wrapTestOutcomeConsumer(testOutcome -> {
                if (testOutcome.getExpectedOutput() instanceof Collection
                    && testOutcome.getActualOutput() instanceof Collection) {
                    // Using contains will give a better error message
                    Assertions.assertThat((Collection<O>) testOutcome.getActualOutput())
                            .containsExactlyElementsOf((Collection<O>) testOutcome.getExpectedOutput());
                } else {
                    Assertions.assertThat(testOutcome.getActualOutput())
                            .withFailMessage(testOutcome::buildFailMessage)
                            .isEqualTo(testOutcome.getExpectedOutput());
                }
            });
            return new CasesBuilder<>(testAction, wrappedConsumer);
        }

        private Consumer<TestOutcome<I, O>> wrapTestOutcomeConsumer(
                final Consumer<TestOutcome<I, O>> testOutcomeConsumer) {

            // Intercept the testOutcome before testOutcomeConsumer sees it, so we can
            // assert for a thrown exception if the test case expected it.
            return testOutcome -> {
                if (testOutcome.isExpectedToThrow()) {
                    final Class<? extends Throwable> expectedThrowableType = testOutcome.getExpectedThrowableType();

                    if (testOutcome.getActualThrowable().isPresent()) {
                        final Throwable actualThrowable = testOutcome.getActualThrowable().get();
                        LOGGER.debug("Test threw exception: '{}' (expecting: {}), message: '{}'",
                                actualThrowable.getClass().getSimpleName(),
                                expectedThrowableType.getSimpleName(),
                                actualThrowable.getMessage());
                        Assertions.assertThat(actualThrowable)
                                .isInstanceOf(expectedThrowableType);
                    } else {
                        LOGGER.debug("Test did not throw an exception but we were expecting it to throw: {}",
                                expectedThrowableType.getSimpleName());
                        Assertions.fail("Expecting test to throw "
                                + expectedThrowableType.getSimpleName());
                    }
                } else {
                    LOGGER.debug("Actual output: '{}'", testOutcome.getActualOutput());
                    testOutcomeConsumer.accept(testOutcome);
                }
            };
        }
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public static class CasesBuilder<I, O> {

        private final Function<TestCase<I, O>, O> testAction;
        private final Consumer<TestOutcome<I, O>> testOutcomeConsumer;
        private final List<TestCase<I, O>> testCases = new ArrayList<>();
        // Use a default name function but let the user override it
        // TestCase.name takes precedence though.
        private Function<TestCase<I, O>, String> nameFunction = null;
        private Runnable beforeCaseAction = null;
        private Runnable afterCaseAction = null;

        private CasesBuilder(final Function<TestCase<I, O>, O> testAction,
                             final Consumer<TestOutcome<I, O>> testOutcomeConsumer) {
            this.testAction = Objects.requireNonNull(testAction);
            this.testOutcomeConsumer = Objects.requireNonNull(testOutcomeConsumer);
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
        public CasesBuilder<I, O> addNamedCase(final String name,
                                               final I input,
                                               final O expectedOutput) {
            addCase(TestCase.of(name, input, expectedOutput));
            return this;
        }

        /**
         * Add a test case to the {@link DynamicTest} {@link Stream} that is expected to throw
         * an exception with type expectedThrowableType. The exception assertion will be done for
         * you and any with*Assertion* steps will not be called.
         *
         * @param input                 The input to the test case.
         * @param expectedThrowableType The expected class of the exception that will be thrown.
         */
        @SuppressWarnings("unused")
        public CasesBuilder<I, O> addThrowsCase(final I input,
                                                final Class<? extends Throwable> expectedThrowableType) {

            final TestCase<I, O> testCase = (TestCase<I, O>) TestCase.throwing(input, expectedThrowableType);
            addCase(testCase);
            return this;
        }

        /**
         * Add a test case to the {@link DynamicTest} {@link Stream} that is expected to throw
         * an exception with type expectedThrowableType. The exception assertion will be done for
         * you and any with*Assertion* steps will not be called.
         *
         * @param name                  The name of the test case.
         * @param input                 The input to the test case.
         * @param expectedThrowableType The expected class of the exception that will be thrown.
         */
        @SuppressWarnings("unused")
        public CasesBuilder<I, O> addNamedThrowsCase(final String name,
                                                     final I input,
                                                     final Class<? extends Throwable> expectedThrowableType) {

            final TestCase<I, O> testCase = (TestCase<I, O>) TestCase.throwing(name, input, expectedThrowableType);
            addCase(testCase);
            return this;
        }

        /**
         * Replace the default test case naming function with the supplied {@code nameFunction}
         * that generates a name from a {@link TestCase}. The default name function basically
         * does a toString() on {@link TestCase#getInput()}.
         * A name explicitly set on {@link TestCase} will override any name function.
         */
        @SuppressWarnings("unused")
        public CasesBuilder<I, O> withNameFunction(final Function<TestCase<I, O>, String> nameFunction) {
            this.nameFunction = Objects.requireNonNull(nameFunction);
            return this;
        }

        /**
         * Set an action to run before each test case. Note {@link org.junit.jupiter.api.BeforeEach}
         * is NOT called before
         * each case in a dynamic test, so this is an alternative. Note also that the same instance of the test
         * class is used for each test case.
         */
        public CasesBuilder<I, O> withBeforeTestCaseAction(final Runnable action) {
            this.beforeCaseAction = action;
            return this;
        }

        /**
         * Set an action to run after each test case. Note {@link org.junit.jupiter.api.AfterEach}
         * is NOT called after
         * each case in a dynamic test, so this is an alternative. Note also that the same instance of the test
         * class is used for each test case.
         */
        public CasesBuilder<I, O> withAfterTestCaseAction(final Runnable action) {
            this.afterCaseAction = action;
            return this;
        }

        /**
         * Build the {@link Stream} of {@link DynamicTest} with all the added test cases.
         */
        @SuppressWarnings("unused")
        public Stream<DynamicTest> build() {
            if (NullSafe.isEmptyCollection(testCases)) {
                Assertions.fail("No test cases provided");
            }
            return createDynamicTestStream();
        }

        private String buildTestNameFromInput(final TestCase<I, O> testCase) {
            return NullSafe.getOrElseGet(
                    testCase,
                    TestCase::getName,
                    () -> "Input: " + valueToStr(testCase.getInput()));
        }

        private String valueToStr(final Object value) {
            final StringBuilder stringBuilder = new StringBuilder();

            if (value == null) {
                stringBuilder.append("null");
            } else if (value instanceof Double) {
                stringBuilder.append(ModelStringUtil.formatCsv(
                                (Double) value, 5, true))
                        .append("D");
            } else if (value instanceof Integer) {
                stringBuilder.append(ModelStringUtil.formatCsv(((Integer) value).longValue()));
            } else if (value instanceof Long) {
                stringBuilder.append(ModelStringUtil.formatCsv((Long) value))
                        .append("L");
            } else if (value instanceof Tuple) {
                final Tuple tuple = (Tuple) value;
                final String tupleStr = tuple.toSeq()
                        .toStream()
                        .map(this::valueToStr)
                        .collect(Collectors.joining(", "));

                stringBuilder.append("(")
                        .append(tupleStr)
                        .append(")");
            } else {
                String valStr = value.toString();
                // No point outputting the lambda reference as it doesn't help
                if (valStr.contains("$$Lambda")) {
                    valStr = "lambda";
                }

                stringBuilder.append("'")
                        .append(valStr)
                        .append("'");
            }
            return stringBuilder.toString();
        }

        private void runAction(final Runnable action, final String name) {
            LOGGER.debug("Running action: {}", name);
            try {
                action.run();
            } catch (Exception e) {
                throw new RuntimeException(
                        LogUtil.message("Error running action: " + name + ". " + e.getMessage()), e);
            }
        }

        private Stream<DynamicTest> createDynamicTestStream() {

            AtomicInteger caseCounter = new AtomicInteger();

            return testCases.stream()
                    .sequential()
                    .map(testCase -> {
                        // Name defined in the testCase overrides the name function
                        final String testName = buildTestName(
                                testCase,
                                caseCounter.incrementAndGet(),
                                testCases.size());

                        return DynamicTest.dynamicTest(testName, () -> {
                            NullSafe.consume(beforeCaseAction, action ->
                                    runAction(action, "Before Test Case"));

                            if (LOGGER.isDebugEnabled()) {
                                logCaseToDebug(testCase);
                            }
                            O actualOutput = null;
                            Throwable actualThrowable = null;
                            try {
                                actualOutput = testAction.apply(testCase);
                            } catch (Throwable t) {
                                actualThrowable = t;
                                if (!testCase.isExpectedToThrow()) {
                                    Assertions.fail(LogUtil.message(
                                            "Not expecting test to throw an exception but it threw {} '{}'",
                                            t, t.getMessage()), t);
                                }
                            }

                            final TestOutcome<I, O> testOutcome = new TestOutcome<>(
                                    testCase, actualOutput, actualThrowable);

                            testOutcomeConsumer.accept(testOutcome);

                            NullSafe.consume(afterCaseAction, action ->
                                    runAction(action, "After Test Case"));
                        });
                    });
        }

        private String buildTestName(final TestCase<I, O> testCase,
                                     final int caseNo,
                                     final int casesCount) {

            final Function<TestCase<I, O>, String> nameFunc;
            if (!NullSafe.isBlankString(testCase.getName())) {
                nameFunc = TestCase::getName;
            } else if (nameFunction != null) {
                nameFunc = nameFunction;
            } else {
                // No name or namefunc provided so build a name from the case number and the input
                // Case number may help in linking a failed test to the source
                final int padLen = Integer.toString(casesCount).length();
                final String paddedCaseNo = Strings.padStart(Integer.toString(caseNo), padLen, '0');
                nameFunc = testCase2 -> paddedCaseNo + " " + buildTestNameFromInput(testCase2);
            }
            return nameFunc.apply(testCase);
        }

        private void logCaseToDebug(final TestCase<I, O> testCase) {
            if (testCase.isExpectedToThrow()) {
                LOGGER.debug(() -> LogUtil.message(
                        "Running test case - {}, expected to throw: '{}'",
                        TestCase.valueToString("input", testCase.getInput()),
                        testCase.getExpectedThrowableType().getSimpleName()));
            } else {
                LOGGER.debug(() -> LogUtil.message("Running test case - {}, expected {}",
                        TestCase.valueToString("input", testCase.getInput()),
                        TestCase.valueToString("output", testCase.getExpectedOutput())));
            }
        }
    }
}
