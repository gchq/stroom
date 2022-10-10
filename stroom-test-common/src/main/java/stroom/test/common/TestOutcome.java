package stroom.test.common;

import java.util.Optional;

public class TestOutcome<I, O> {

    private final TestCase<I, O> testCase;
    private final O actualOutput;
    private final Throwable actualThrowable;

    TestOutcome(final TestCase<I, O> testCase,
                final O actualOutput,
                final Throwable actualThrowable) {
        this.testCase = testCase;
        this.actualOutput = actualOutput;
        this.actualThrowable = actualThrowable;
    }

    public I getInput() {
        return testCase.getInput();
    }

    public O getExpectedOutput() {
        return testCase.getExpectedOutput();
    }

    public O getActualOutput() {
        return actualOutput;
    }

    public Class<? extends Throwable> getExpectedThrowableType() {
        return testCase.getExpectedThrowableType();
    }

    public String getName() {
        return testCase.getName();
    }

    public Optional<Throwable> getActualThrowable() {
        return Optional.ofNullable(actualThrowable);
    }

    public boolean isExpectedToThrow() {
        return testCase.getExpectedThrowableType() != null;
    }

    @Override
    public String toString() {
        return "TestOutcome{" +
                "testCase=" + testCase +
                ", actualOutput=" + actualOutput +
                ", throwable=" + actualThrowable +
                '}';
    }
}
