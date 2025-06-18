package stroom.pipeline.xsltfunctions;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import net.sf.saxon.om.Sequence;
import net.sf.saxon.value.DateTimeValue;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestToUnixTime extends AbstractXsltFunctionTest<ToUnixTime> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestToUnixTime.class);

    private final ToUnixTime toUnixTime = new ToUnixTime();

    @Test
    void call() throws Exception {

        final Instant now = Instant.now();

        final DateTimeValue dateTime = DateTimeValue.fromJavaTime(now.toEpochMilli());

        final Sequence sequence1 = callFunctionWithSimpleArgs(dateTime);

        final Optional<Long> optTimeMs = getAsLongValue(sequence1);

        assertThat(optTimeMs).isNotEmpty();

        assertThat(optTimeMs.get()).isEqualTo(now.toEpochMilli());
    }

    @Override
    ToUnixTime getXsltFunction() {
        return toUnixTime;
    }

    @Override
    String getFunctionName() {
        return ToUnixTime.FUNCTION_NAME;
    }
}
