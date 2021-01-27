package stroom.query.test.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

public class TimingUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimingUtils.class);

    /**
     * Execute resultSupplier, time how long it takes, then return its result and the duration
     */
    public static <T> TimedResult<T>  timeIt(Supplier<T> resultSupplier) {
        Instant startTime = Instant.now();

        T result = resultSupplier.get();

        return new TimedResult<>(Duration.between(startTime, Instant.now()), result);
    }

    public static boolean isWithinTollerance(final Duration expectedDuration,
                                             final Duration actualDuration,
                                             final Duration tolerance) {

        Duration diff = expectedDuration.minus(actualDuration).abs();

        boolean result = diff.toMillis() <= tolerance.toMillis();

        LOGGER.info("Expected: {}, actual: {}, tolerance: {}, diff {}",
                expectedDuration,
                actualDuration,
                tolerance,
                diff);
        return result;
    }

    public static boolean sleep(final long millis) {
        try {
            if (millis > 0) {
                Thread.sleep(millis);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            // It's not an error that we were Interrupted!! Don't log the
            // exception !
            return false;
        }

        return true;
    }

    public static class TimedResult<T> {
        private final Duration duration;
        private final T result;

        public TimedResult(final Duration duration, final T result) {
            this.duration = duration;
            this.result = result;
        }

        public Duration getDuration() {
            return duration;
        }

        public T getResult() {
            return result;
        }
    }
}
