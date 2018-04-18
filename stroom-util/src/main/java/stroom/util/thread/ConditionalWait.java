package stroom.util.thread;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

public class ConditionalWait {

    private static final long DEFAULT_SLEEP_TIME_MS = 1_000;

    private ConditionalWait() {
    }

    /**
     * Method to wait for the conditionSupplier to return true or for the timeout to be reached.
     * This is intended to be used was a thread needs to wait for another thread to change some
     * condition but the other thread cannot signal the change.
     * @param conditionSupplier A supplier of a boolean that is used as the condition to be tested
     * @param timeout The duration to wait for the condition to be true before giving up
     * @return CONDITION_MET if the conditionSupplier returns true before the timeout is reached,
     * TIMED_OUT if the conditionSupplier still returned false when the timeout was reached,
     * INTERRUPTED if the thread was interrupted.
     */
    public static Outcome wait(final BooleanSupplier conditionSupplier, final Duration timeout) {

        final Instant timeoutTime = Instant.now().plus(timeout);

        //sleep at most the DEFAULT_SLEEP_TIME_MS or 10% of the timeout period if that is shorter
        //this is to prevent long sleeps when a short timeout is requested
        final long sleepTimeMs = Math.min(DEFAULT_SLEEP_TIME_MS, timeout.toMillis() / 10);

        for (;;) {
            if (conditionSupplier.getAsBoolean()) {
                return Outcome.CONDITION_MET;
            } else if (Instant.now().isAfter(timeoutTime)) {
                return Outcome.TIMED_OUT;
            }

            try {
                //TODO need to adjust the sleep time on each iteration to ensure we don't go over the
                //requested timeout
                Thread.sleep(sleepTimeMs);
            } catch (final InterruptedException e) {
                // Continue to interrupt this thread.
                Thread.currentThread().interrupt();

                return Outcome.INTERRUPTED;
            }
        }
    }

    public enum Outcome {
        CONDITION_MET,
        TIMED_OUT,
        INTERRUPTED
    }
}
