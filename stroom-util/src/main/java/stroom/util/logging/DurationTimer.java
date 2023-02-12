package stroom.util.logging;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
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

    public static Duration measureIf(final boolean isTimed, final Runnable timedWork) {
        if (isTimed) {
            final Instant startTime = Instant.now();
            timedWork.run();
            return Duration.between(startTime, Instant.now());
        } else {
            timedWork.run();
            return Duration.ZERO;
        }
    }

    public static <T> TimedResult<T> measure(final Supplier<T> resultSupplier) {
        final Instant startTime = Instant.now();
        final T result = resultSupplier.get();
        return new TimedResult<>(Duration.between(startTime, Instant.now()), result);
    }

    /**
     * Measures the time taken to perform resultSupplier if isTimed is true.
     * If isTimed is false returns a {@link TimedResult} containing a zero duration.
     */
    public static <T> TimedResult<T> measureIf(final boolean isTimed, final Supplier<T> resultSupplier) {
        if (isTimed) {
            final Instant startTime = Instant.now();
            final T result = resultSupplier.get();
            return new TimedResult<>(Duration.between(startTime, Instant.now()), result);
        } else {
            return TimedResult.zero(resultSupplier.get());
        }
    }


    // --------------------------------------------------------------------------------


    public static class TimedResult<T> {

        private final Duration duration;
        private final T result;

        public static <R> TimedResult<R> zero(final R result) {
            return new TimedResult<>(Duration.ZERO, result);
        }

        public TimedResult(final Duration duration, final T result) {
            this.duration = Objects.requireNonNull(duration);
            // Debatable whether null results should be allowed, probably need to be
            this.result = result;
        }

        public Duration getDuration() {
            return duration;
        }

        public T getResult() {
            return result;
        }

        @Override
        public String toString() {
            return "TimedResult{" +
                    "duration=" + duration +
                    ", result=" + result +
                    '}';
        }
    }
}
