package stroom.util.logging;

import java.time.Duration;
import java.time.Instant;

public class DurationTimer {

    private final Instant startTime = Instant.now();

    private DurationTimer() {
        // Use start() factory method.
    }

    public static DurationTimer start() {
        return new DurationTimer();
    }

    public Duration get() {
        return Duration.between(startTime, Instant.now());
    }

    public static Duration measure(final Runnable timedWork) {
        final Instant startTime = Instant.now();
        timedWork.run();
        return Duration.between(startTime, Instant.now());
    }
}
