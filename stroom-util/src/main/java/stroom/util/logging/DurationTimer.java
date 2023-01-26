package stroom.util.logging;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

public class DurationTimer {

    public static Duration measure(final Runnable timedWork) {
        final Instant startTime = Instant.now();
        timedWork.run();
        return Duration.between(startTime, Instant.now());
    }

    public static <R> DurationResult<R> measure(final Supplier<R> timedWork) {
        final Instant startTime = Instant.now();
        final R result = timedWork.get();
        final Duration duration = Duration.between(startTime, Instant.now());
        return new DurationResult<>(duration, result);
    }

    public static String getDurationMessage(final String work, final Duration duration) {
        return LogUtil.message("Completed [{}] in {}",
                work,
                duration);
    }

    public static class DurationResult<R> {

        private final Duration duration;
        private final R result;

        public DurationResult(final Duration duration, final R result) {
            this.duration = duration;
            this.result = result;
        }

        public Duration getDuration() {
            return duration;
        }

        public R getResult() {
            return result;
        }
    }
}
