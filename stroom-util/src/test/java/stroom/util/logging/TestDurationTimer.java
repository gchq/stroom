package stroom.util.logging;

import stroom.util.logging.DurationTimer.IterationTimer;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TestDurationTimer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestDurationTimer.class);

    @Test
    void testIterationTimer_empty() {
        final IterationTimer iterationTimer = DurationTimer.newIterationTimer();

        assertThat(iterationTimer.getCumulativeDuration())
                .isEqualTo(Duration.ZERO);
        assertThat(iterationTimer.getMinDuration())
                .isEmpty();
        assertThat(iterationTimer.getMaxDuration())
                .isEmpty();
        assertThat(iterationTimer.getAverageDuration())
                .isEqualTo(Duration.ZERO);
        assertThat(iterationTimer.getIterationCount())
                .isEqualTo(0);
    }

    @Test
    void testIterationTimer_one() {
        final IterationTimer iterationTimer = DurationTimer.newIterationTimer();

        iterationTimer.logIteration(Duration.ofSeconds(1));

        assertThat(iterationTimer.getCumulativeDuration())
                .isEqualTo(Duration.ofSeconds(1));
        assertThat(iterationTimer.getMinDuration())
                .hasValue(Duration.ofSeconds(1));
        assertThat(iterationTimer.getMaxDuration())
                .hasValue(Duration.ofSeconds(1));
        assertThat(iterationTimer.getAverageDuration())
                .isEqualTo(Duration.ofSeconds(1));
        assertThat(iterationTimer.getIterationCount())
                .isEqualTo(1);

        LOGGER.info(iterationTimer.toString());
    }

    @Test
    void testIterationTimer_multiple() {
        final IterationTimer iterationTimer = DurationTimer.newIterationTimer();

        iterationTimer.logIteration(Duration.ofSeconds(10));

        assertThat(iterationTimer.getCumulativeDuration())
                .isEqualTo(Duration.ofSeconds(10));
        assertThat(iterationTimer.getMinDuration())
                .hasValue(Duration.ofSeconds(10));
        assertThat(iterationTimer.getMaxDuration())
                .hasValue(Duration.ofSeconds(10));
        assertThat(iterationTimer.getIterationCount())
                .isEqualTo(1);

        iterationTimer.logIteration(Duration.ofSeconds(20));
        iterationTimer.logIteration(Duration.ofSeconds(5));
        iterationTimer.logIteration(Duration.ofSeconds(5));

        assertThat(iterationTimer.getCumulativeDuration())
                .isEqualTo(Duration.ofSeconds(40));
        assertThat(iterationTimer.getMinDuration())
                .hasValue(Duration.ofSeconds(5));
        assertThat(iterationTimer.getMaxDuration())
                .hasValue(Duration.ofSeconds(20));
        assertThat(iterationTimer.getAverageDuration())
                .isEqualTo(Duration.ofSeconds(10));
        assertThat(iterationTimer.getIterationCount())
                .isEqualTo(4);

        LOGGER.info(iterationTimer.toString());
    }
}
