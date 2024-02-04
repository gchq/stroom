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

class TestHexToString extends AbstractXsltFunctionTest<HexToString> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestHexToString.class);

    private HexToString hexToString;

    @BeforeEach
    void setUp() {
        hexToString = new HexToString();
    }

    @Test
    void call() {

        Sequence sequence = callFunctionWithSimpleArgs("74 65 73 74 69 6e 67 20 31 32 33", "ASCII");
        assertThat(getAsStringValue(sequence).orElseThrow())
                .isEqualTo("testing 123")
                .describedAs("ASCII encoded text should be decoded correctly");

        sequence = callFunctionWithSimpleArgs("74 65 73 74 69 6e 67 20 31 32 33", "UTF-8");
        assertThat(getAsStringValue(sequence).orElseThrow())
                .isEqualTo("testing 123")
                .describedAs("UTF-8 encoded text should be decoded correctly");

        sequence = callFunctionWithSimpleArgs("74 65 73 74 69 6E 67 20 31 32 33", "UTF-8");
        assertThat(getAsStringValue(sequence).orElseThrow())
                .isEqualTo("testing 123")
                .describedAs("UTF-8 encoded text should be decoded correctly, regardless of input case");

        sequence = callFunctionWithSimpleArgs("00 74 00 65 00 73 00 74 00 69 00 6e 00 67 00 20 00 31 00 32 00 33", "UTF-16BE");
        assertThat(getAsStringValue(sequence).orElseThrow())
                .isEqualTo("testing 123")
                .describedAs("UTF-16BE encoded text should be decoded correctly");

        sequence = callFunctionWithSimpleArgs("", "UTF-8");
        assertThat(getAsStringValue(sequence).orElseThrow())
                .isEqualTo("")
                .describedAs("Empty hex value should return an empty string");
    }

    @Test
    void call_invalid() {

        logLogCallsToDebug();

        final Sequence sequence = callFunctionWithSimpleArgs("7 ", "UTF-8");
        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);

        final LogArgs logArgs = verifySingleLogCall();
        Assertions.assertThat(logArgs.getSeverity())
                .isEqualTo(Severity.WARNING);
        Assertions.assertThat(logArgs.getMessage())
                .containsIgnoringCase("invalid string length");
    }

    @Override
    HexToString getXsltFunction() {
        return hexToString;
    }

    @Override
    String getFunctionName() {
        return HexToString.FUNCTION_NAME;
    }
}
