package stroom.pipeline.xsltfunctions;

import stroom.test.common.TestUtil;
import stroom.util.shared.Severity;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestAbstractLookup {

    @TestFactory
    Stream<DynamicTest> testShouldLog() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(Inputs.class)
                .withOutputType(Boolean.class)
                .withTestFunction(testCase -> AbstractLookup.shouldLog(
                        testCase.getInput().severity,
                        testCase.getInput().isTrace,
                        testCase.getInput().isIgnoreWarnings))
                .withSimpleEqualityAssertion()
                .addCase(new Inputs(Severity.INFO, false, true), false)
                .addCase(new Inputs(Severity.INFO, true, true), true)
                .addCase(new Inputs(Severity.INFO, false, false), false)
                .addCase(new Inputs(Severity.WARNING, false, true), false)
                .addCase(new Inputs(Severity.WARNING, false, true), false)
                .addCase(new Inputs(Severity.WARNING, true, true), true)
                .addCase(new Inputs(Severity.ERROR, false, true), true)
                .addCase(new Inputs(Severity.ERROR, true, true), true)
                .addCase(new Inputs(Severity.ERROR, true, false), true)
                .addCase(new Inputs(Severity.ERROR, false, false), true)
                .build();
    }

    // TODO: 20/01/2023 Convert to local record in method when on J17+
    private static class Inputs {
        private final Severity severity;
        private final boolean isTrace;
        private final boolean isIgnoreWarnings;

        private Inputs(final Severity severity, final boolean isTrace, final boolean isIgnoreWarnings) {
            this.severity = severity;
            this.isTrace = isTrace;
            this.isIgnoreWarnings = isIgnoreWarnings;
        }

        @Override
        public String toString() {
            return String.join(", ",
                    severity.toString(),
                    "trace:" + isTrace,
                    "ignoreWarnings:" + isIgnoreWarnings);
        }
    }
}
