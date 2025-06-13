package stroom.pipeline.xsltfunctions;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import net.sf.saxon.om.Sequence;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestCurrentUnixTime extends AbstractXsltFunctionTest<CurrentUnixTime> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestCurrentUnixTime.class);

    private final CurrentUnixTime currentUnixTime = new CurrentUnixTime();

    @Test
    void call() {

        final Instant now = Instant.now();

        final Sequence sequence1 = callFunctionWithSimpleArgs();

        final Optional<Long> optTime = getAsLongValue(sequence1);

        assertThat(optTime).isNotEmpty();

        final Instant time = Instant.ofEpochMilli(optTime.orElseThrow());

        // Should be less than 500ms between getting now and calling the func
        Assertions.assertThat(Duration.between(now, time))
                .isLessThan(Duration.ofMillis(500));
    }

    @Override
    CurrentUnixTime getXsltFunction() {
        return currentUnixTime;
    }

    @Override
    String getFunctionName() {
        return CurrentUnixTime.FUNCTION_NAME;
    }
}
