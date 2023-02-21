package stroom.pipeline.xsltfunctions;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;

import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestHexToDec extends AbstractXsltFunctionTest<HexToDec> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestHexToDec.class);

    private HexToDec hexToDec;

    @BeforeEach
    void setUp() {
        hexToDec = new HexToDec();
    }

    @Test
    void call() {

        final Sequence sequence = callFunctionWithSimpleArgs("2A");

        // Not sure why it is outputting a StringValue instead of an IntegerValue
        final Long decimalVal = getAsLongValue(sequence)
                .orElseThrow();

        assertThat(decimalVal)
                .isEqualTo(42L);
    }

    @Test
    void call_invalid() {

        logLogCallsToDebug();

        final Sequence sequence = callFunctionWithSimpleArgs("foobar");

        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);

        final LogArgs logArgs = verifySingleLogCall();
        Assertions.assertThat(logArgs.getSeverity())
                .isEqualTo(Severity.WARNING);
        Assertions.assertThat(logArgs.getMessage())
                .containsIgnoringCase("error converting")
                .containsIgnoringCase("foobar")
                .containsIgnoringCase("decimal");
    }

    @Override
    HexToDec getXsltFunction() {
        return hexToDec;
    }

    @Override
    String getFunctionName() {
        return HexToDec.FUNCTION_NAME;
    }
}
