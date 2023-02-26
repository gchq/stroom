package stroom.util.time;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

class TestTimeUtils {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestTimeUtils.class);

    @Test
    void durationToThreshold() {

        final Instant now = ZonedDateTime.of(
                        2020, 1, 1, 17, 30, 0, 0,
                        ZoneOffset.UTC)
                .toInstant();

        final Instant actual = TimeUtils.durationToThreshold(now, Duration.ofMinutes(10));

        LOGGER.info("actual: {}", actual);

        final Instant expected = ZonedDateTime.of(
                        2020, 1, 1, 17, 20, 0, 0,
                        ZoneOffset.UTC)
                .toInstant();

        Assertions.assertThat(actual)
                .isEqualTo(expected);
    }
}
