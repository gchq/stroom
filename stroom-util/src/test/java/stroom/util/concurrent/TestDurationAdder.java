package stroom.util.concurrent;

import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TestDurationAdder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestDurationAdder.class);

    @Test
    void add() {
        final DurationAdder durationAdder = new DurationAdder();

        LOGGER.info("duration.ZERO: {}", Duration.ZERO);
        LOGGER.info("atomicDuration: {}, {}", durationAdder, durationAdder.get());

        assertThat(durationAdder.get().toMillis())
                .isEqualTo(0);

        assertThat(durationAdder.get())
                .isEqualTo(Duration.ZERO);

        Duration duration = durationAdder.add(Duration.ofMinutes(10));

        assertThat(duration)
                .isEqualTo(durationAdder.get())
                .isEqualTo(Duration.ofMinutes(10));

        duration = durationAdder.add(Duration.ofMinutes(10));

        assertThat(duration)
                .isEqualTo(durationAdder.get())
                .isEqualTo(Duration.ofMinutes(20));
    }

    @Test
    void add2() {
        final DurationAdder durationAdder = new DurationAdder(Duration.ofMinutes(10));

        Duration duration = durationAdder.add(Duration.ofMinutes(10));

        assertThat(duration)
                .isEqualTo(durationAdder.get())
                .isEqualTo(Duration.ofMinutes(20));

        duration = durationAdder.add(Duration.ofMinutes(10));

        assertThat(duration)
                .isEqualTo(durationAdder.get())
                .isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void add_durationTimer() {
        final DurationAdder durationAdder = new DurationAdder();

        final DurationTimer durationTimer = DurationTimer.start();

        durationAdder.add(durationTimer);

        assertThat(durationAdder.get().compareTo(Duration.ZERO))
                .isGreaterThan(0);
    }

    @Test
    void add_durationAdder() {
        final DurationAdder durationAdder1 = new DurationAdder(Duration.ofMinutes(10));
        final DurationAdder durationAdder2 = new DurationAdder(Duration.ofMinutes(5));

        final Duration duration = durationAdder1.add(durationAdder2);

        assertThat(duration)
                .isEqualTo(durationAdder1.get())
                .isEqualTo(Duration.ofMinutes(15));

        // Unchanged
        assertThat(durationAdder2.get())
                .isEqualTo(Duration.ofMinutes(5));
    }
}
