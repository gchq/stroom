package stroom.pipeline.xsltfunctions;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;

import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestLog extends AbstractXsltFunctionTest<Log> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestLog.class);

    private Log log;

    @BeforeEach
    void setUp() {
        log = new Log();
    }

    @Test
    void call() {

        logLogCallsToDebug();
        final Sequence sequence = callFunctionWithSimpleArgs(Severity.INFO.toString().toLowerCase(), "My msg");

        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);

        final List<LogArgs> logArgsList = verifyLogCalls(1);

        assertThat(logArgsList)
                .first()
                .extracting(LogArgs::getSeverity)
                .isEqualTo(Severity.INFO);

        assertThat(logArgsList)
                .first()
                .extracting(LogArgs::getMessage)
                .isEqualTo("My msg");
    }

    @Test
    void call_badSeverity() {

        logLogCallsToDebug();

        final Sequence sequence = callFunctionWithSimpleArgs("foo", "My msg");

        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);

        final LogArgs logArgs = verifySingleLogCall();

        assertThat(logArgs.getSeverity())
                .isEqualTo(Severity.ERROR);

        assertThat(logArgs.getMessage())
                .containsIgnoringCase("unknown severity")
                .containsIgnoringCase("foo");
    }

    @Test
    void call_nullSeverity() {

        logLogCallsToDebug();

        final Sequence sequence = callFunctionWithSimpleArgs(null, "My msg");

        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);

        final List<LogArgs> logArgsList = verifyLogCalls(2);

        assertThat(logArgsList.get(0).getSeverity())
                .isEqualTo(Severity.WARNING);
        assertThat(logArgsList.get(0).getMessage())
                .containsIgnoringCase("non string argument");

        assertThat(logArgsList.get(1).getSeverity())
                .isEqualTo(Severity.ERROR);
        assertThat(logArgsList.get(1).getMessage())
                .containsIgnoringCase("unknown severity");
    }

    @Override
    Log getXsltFunction() {
        return log;
    }

    @Override
    String getFunctionName() {
        return Log.FUNCTION_NAME;
    }
}
