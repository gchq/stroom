package stroom.test.common;

/**
 * Useful class for holding input and expected output values for a test case.
 */
public class TestCase<T1, T2> {

    private final T1 input;
    private final T2 expectedOutput;

    private TestCase(final T1 input, final T2 expectedOutput) {
        this.input = input;
        this.expectedOutput = expectedOutput;
    }

    public static <T1, T2> TestCase<T1, T2> of(final T1 input, final T2 expectedOutput) {
        return new TestCase<>(input, expectedOutput);
    }

    public T1 getInput() {
        return input;
    }

    public T2 getExpectedOutput() {
        return expectedOutput;
    }

    @Override
    public String toString() {
        return "TestCase{" +
                "input=" + input +
                ", expectedOutput=" + expectedOutput +
                '}';
    }
}
