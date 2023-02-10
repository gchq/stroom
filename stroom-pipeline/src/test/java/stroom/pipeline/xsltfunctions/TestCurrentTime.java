package stroom.pipeline.xsltfunctions;

import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import net.sf.saxon.om.Sequence;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestCurrentTime extends AbstractXsltFunctionTest<CurrentTime> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestCurrentTime.class);

    private CurrentTime currentTime = new CurrentTime();

    @Test
    void call() {

        final Instant now = Instant.now();

        final Sequence sequence1 = callFunctionWithSimpleArgs();

        final Optional<String> optTime = getAsStringValue(sequence1);

        assertThat(optTime)
                .isNotEmpty();

        final Instant time = Instant.ofEpochMilli(DateUtil.parseNormalDateTimeString(
                optTime.orElseThrow()));

        // Should be less than 500ms between getting now and calling the func
        Assertions.assertThat(Duration.between(now, time))
                .isLessThan(Duration.ofMillis(500));

        verifyNoLogCalls();
    }

    @Override
    CurrentTime getXsltFunction() {
        return currentTime;
    }

    @Override
    String getFunctionName() {
        return CurrentTime.FUNCTION_NAME;
    }
}
