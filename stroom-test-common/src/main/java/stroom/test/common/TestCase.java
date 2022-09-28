package stroom.test.common;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;

/**
 * Useful class for holding input and expected output values for a test case.
 *
 * @param <I> The type of the test case input. If there are multiple inputs then
 *            {@code I} may be a {@link Tuple} of input values.
 * @param <O> The type of the test case expected output. If there are multiple outputs then
 *            {@code O} may be a {@link Tuple} of input values.
 */
public class TestCase<I, O> {

    private final I input;
    private final O expectedOutput;
    private final String name;

    private TestCase(final I input,
                     final O expectedOutput,
                     final String name) {
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.name = name;
    }

    public static <I, O> TestCase<I, O> of(final I input,
                                           final O expectedOutput) {
        return new TestCase<>(input, expectedOutput, null);
    }

    public static <I, O> TestCase<I, O> of(final String name,
                                           final I input,
                                           final O expectedOutput) {
        return new TestCase<>(input, expectedOutput, name);
    }

    /**
     * Build a {@link TestCase} with a single input value.
     */
    public static <I> OutputBuilder<I> singleInputBuilder(final I input) {
        return new OutputBuilder<>(input);
    }

    /**
     * Build a {@link TestCase} with a two input values.
     */
    public static <I1, I2> OutputBuilder<Tuple2<I1, I2>> biInputBuilder(
            final I1 input1,
            final I2 input2) {
        return new OutputBuilder<>(Tuple.of(input1, input2));
    }

    /**
     * Build a {@link TestCase} with a three input values.
     */
    public static <I1, I2, I3> OutputBuilder<Tuple3<I1, I2, I3>> triInputBuilder(
            final I1 input1,
            final I2 input2,
            final I3 input3) {
        return new OutputBuilder<>(Tuple.of(input1, input2, input3));
    }

    public I getInput() {
        return input;
    }

    public O getExpectedOutput() {
        return expectedOutput;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "TestCase{" +
                "input=" + input +
                ", expectedOutput=" + expectedOutput +
                ", name='" + name + '\'' +
                '}';
    }

    public static class OutputBuilder<I> {

        private final I input;

        private OutputBuilder(final I input) {
            this.input = input;
        }

        /**
         * Set a single expected output value.
         */
        public <O> FinalBuilder<I, O> withSingleOutput(final O expectedOutput) {
            return new FinalBuilder<>(input, expectedOutput);
        }

        /**
         * Set two expected output values.
         */
        public <O1, O2> FinalBuilder<I, Tuple2<O1, O2>> withBiOutput(
                final O1 expectedOutput1,
                final O2 expectedOutput2) {
            return new FinalBuilder<>(input, Tuple.of(expectedOutput1, expectedOutput2));
        }

        /**
         * Set three expected output values.
         */
        public <O1, O2, O3> FinalBuilder<I, Tuple3<O1, O2, O3>> withTriOutput(
                final O1 expectedOutput1,
                final O2 expectedOutput2,
                final O3 expectedOutput3) {
            return new FinalBuilder<>(input, Tuple.of(
                    expectedOutput1,
                    expectedOutput2,
                    expectedOutput3));
        }
    }

    public static class FinalBuilder<I, O> {

        private final I input;
        private final O output;
        private String name;

        private FinalBuilder(final I input, final O output) {
            this.input = input;
            this.output = output;
        }

        /**
         * Set the name of the {@link TestCase}
         */
        public FinalBuilder<I, O> withName(final String name) {
            this.name = name;
            return this;
        }

        /**
         * Build the {@link TestCase}
         */
        public TestCase<I, O> build() {
            return new TestCase<>(input, output, name);
        }
    }
}
