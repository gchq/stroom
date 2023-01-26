package stroom.util.logging;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

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

    public static <T> TimedResult<T> measure(final Supplier<T> resultSupplier) {
        Instant startTime = Instant.now();
        T result = resultSupplier.get();
        return new TimedResult<>(Duration.between(startTime, Instant.now()), result);
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
