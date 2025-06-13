package stroom.pipeline.xsltfunctions;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import net.sf.saxon.om.Sequence;
import net.sf.saxon.value.Int64Value;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestFromUnixTime extends AbstractXsltFunctionTest<FromUnixTime> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestFromUnixTime.class);

    private final FromUnixTime fromUnixTime = new FromUnixTime();

    @Test
    void call() throws Exception {

        final Int64Value int64 = Int64Value.makeIntegerValue(1749727780400L);

        final Sequence sequence1 = callFunctionWithSimpleArgs(int64);

        final Optional<String> optDateTime = getAsDateTimeValue(sequence1);

        assertThat(optDateTime).isNotEmpty();

        assertThat(optDateTime.get()).isEqualTo("2025-06-12T11:29:40.4Z");
    }

    @Override
    FromUnixTime getXsltFunction() {
        return fromUnixTime;
    }

    @Override
    String getFunctionName() {
        return FromUnixTime.FUNCTION_NAME;
    }
}
