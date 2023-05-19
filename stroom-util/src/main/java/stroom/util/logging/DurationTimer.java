package stroom.util.logging;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public class DurationTimer {

    public static final DurationTimer ZERO = new DurationTimer(Instant.EPOCH, Instant.EPOCH);
    private final Instant startTime;
    private volatile Instant endTime = null;

    private DurationTimer() {
        startTime = Instant.now();
        // Use start() factory method.
    }

    private DurationTimer(final Instant startTime, final Instant endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public static DurationTimer start() {
        return new DurationTimer();
    }

    /**
     * Marks the stop time. Can be called multiple times, each time overwriting the stop time.
     * Allows you to freeze the timer if you want to log it at a later time.
     */
    public void stop() {
        endTime = Instant.now();
    }

    public static IterationTimer newIterationTimer() {
        return new IterationTimer();
    }

    /**
     * @return The duration between the start and stop time (if {@link DurationTimer#stop()} was called)
     * else the duration between the start time and now.
     */
    public Duration get() {
        return Duration.between(
                startTime,
                Objects.requireNonNullElseGet(endTime, Instant::now));
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

    @Override
    public String toString() {
        return get().toString();
    }

    // --------------------------------------------------------------------------------


    public static class TimedResult<T> {

        private final Duration duration;
        private final Duration cumulativeDuration;
        private final T result;

        public static <R> TimedResult<R> zero(final R result) {
            return new TimedResult<>(Duration.ZERO, result);
        }

        private TimedResult(final Duration duration,
                            final T result) {
            this.duration = Objects.requireNonNull(duration);
            this.cumulativeDuration = null;
            this.result = result;
        }

        private TimedResult(final Duration duration,
                            final Duration cumulativeDuration,
                            final T result) {
            this.duration = Objects.requireNonNull(duration);
            this.cumulativeDuration = cumulativeDuration;
            this.result = result;
        }

        public Duration getDuration() {
            return duration;
        }

        public Duration getCulmulativeDuration() {
            return Objects.requireNonNullElse(cumulativeDuration, duration);
        }

        public T getResult() {
            return result;
        }

        @Override
        public String toString() {
            return "TimedResult{" +
                    "duration=" + duration +
                    ", cumulativeDuration=" + cumulativeDuration +
                    ", result=" + result +
                    '}';
        }
    }


    // --------------------------------------------------------------------------------

    /**
     * Not thread safe
     */
    public static class IterationTimer {

        private Duration cumulativeDuration = Duration.ZERO;
        private int iterationCount = 0;
        private Duration minDuration = null;
        private Duration maxDuration = null;

        private IterationTimer() {
        }

        public void logIteration(final Duration duration) {
            if (duration != null) {
                cumulativeDuration = cumulativeDuration.plus(duration);
                if (minDuration == null || duration.compareTo(minDuration) < 0) {
                    minDuration = duration;
                }
                if (maxDuration == null || duration.compareTo(maxDuration) > 0) {
                    maxDuration = duration;
                }
            }
            iterationCount++;
        }

        public <T> void logIteration(final TimedResult<T> timedResult) {
            final Duration duration = timedResult.getDuration();
            logIteration(duration);
        }

        public Duration measure(final Runnable timedWork) {
            final Duration duration = DurationTimer.measure(timedWork);
            logIteration(duration);
            return duration;
        }

        public Duration measureIf(final boolean isTimed, final Runnable timedWork) {
            if (isTimed) {
                final Duration duration = DurationTimer.measure(timedWork);
                logIteration(duration);
                return duration;
            } else {
                timedWork.run();
                return Duration.ZERO;
            }
        }

        public <T> TimedResult<T> measure(final Supplier<T> resultSupplier) {
            final TimedResult<T> timedResult = DurationTimer.measure(resultSupplier);
            logIteration(timedResult);
            return timedResult;
        }

        /**
         * Measures the time taken to perform resultSupplier if isTimed is true.
         * If isTimed is false returns a {@link TimedResult} containing a zero duration.
         */
        public <T> TimedResult<T> measureIf(final boolean isTimed, final Supplier<T> resultSupplier) {
            if (isTimed) {
                final TimedResult<T> timedResult = DurationTimer.measure(resultSupplier);
                logIteration(timedResult);
                return timedResult;
            } else {
                return TimedResult.zero(resultSupplier.get());
            }
        }

        public Duration getCumulativeDuration() {
            return cumulativeDuration;
        }

        public Optional<Duration> getMaxDuration() {
            return Optional.ofNullable(maxDuration);
        }

        public Optional<Duration> getMinDuration() {
            return Optional.ofNullable(minDuration);
        }

        public int getIterationCount() {
            return iterationCount;
        }

        public Duration getAverageDuration() {
            if (cumulativeDuration.isZero()) {
                return Duration.ZERO;
            } else {
                return cumulativeDuration.dividedBy(iterationCount);
            }
        }

        @Override
        public String toString() {
            return "Total: " + cumulativeDuration +
                    ", iterations: " + iterationCount +
                    ", average: " + getAverageDuration() +
                    ", min: " + minDuration +
                    ", max: " + maxDuration;
        }
    }
}
