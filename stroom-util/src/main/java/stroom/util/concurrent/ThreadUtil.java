package stroom.util.concurrent;

import stroom.util.time.StroomDuration;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

public class ThreadUtil {

    public static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    public static void sleep(final StroomDuration stroomDuration) {
        if (stroomDuration != null) {
            try {
                Thread.sleep(stroomDuration.toMillis());
            } catch (final InterruptedException e) {
                throw UncheckedInterruptedException.create(e);
            }
        }
    }

    public static void sleep(final Duration duration) {
        if (duration != null) {
            try {
                Thread.sleep(duration.toMillis());
            } catch (final InterruptedException e) {
                throw UncheckedInterruptedException.create(e);
            }
        }
    }

    public static void sleep(final long millis, final int nanos) {
        try {
            Thread.sleep(millis, nanos);
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    public static void sleepIgnoringInterrupts(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void sleepIgnoringInterrupts(final long millis,
                                               final int nanos) {
        try {
            Thread.sleep(millis, nanos);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void await(final CountDownLatch latch) {
        try {
            Objects.requireNonNull(latch).await();
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    public static void checkInterrupt() {
        if (Thread.currentThread().isInterrupted()) {
            try {
                throw new InterruptedException("Interrupted");
            } catch (final InterruptedException e) {
                throw UncheckedInterruptedException.create(e);
            }
        }
    }
}
