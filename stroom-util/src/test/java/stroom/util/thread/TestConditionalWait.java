package stroom.util.thread;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class TestConditionalWait {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestConditionalWait.class);

    @Test
    void wait_zeroTimeout() {

        assertTimeTaken(Duration.ZERO, 300L, () -> {
            ConditionalWait.Outcome outcome = ConditionalWait.wait(() -> false, Duration.ZERO);
            assertThat(outcome).isEqualTo(ConditionalWait.Outcome.TIMED_OUT);
        });
    }

    @Test
    void wait_2secTimeout() {

        final Duration duration = Duration.of(2, ChronoUnit.SECONDS);
        assertTimeTaken(duration, 300L, () -> {
            ConditionalWait.Outcome outcome = ConditionalWait.wait(() -> false, duration);
            assertThat(outcome).isEqualTo(ConditionalWait.Outcome.TIMED_OUT);
        });
    }

    @Test
    void wait_conditionCompletesBeforeTimeout() {

        final Duration waitDuration = Duration.of(2, ChronoUnit.SECONDS);
        final Duration sleepDuration = Duration.of(100, ChronoUnit.MILLIS);
        final long startTimeMs = System.currentTimeMillis();

        assertTimeTaken(sleepDuration, 300L, () -> {

            final AtomicBoolean atomicBoolean = new AtomicBoolean(false);
            //spawn a thread that will wait a short time then change the value used for the wait condition
            CompletableFuture.runAsync(() -> {
                try {
                    //removing the time taken to spin up this thread
                    long sleepMs = sleepDuration.toMillis() - (System.currentTimeMillis() - startTimeMs);
                    Thread.sleep(sleepMs);
                } catch (final InterruptedException e) {
                    // Continue to interrupt this thread.
                    Thread.currentThread().interrupt();

                    throw new RuntimeException("Thread interrupted", e);
                }
                atomicBoolean.set(true);
            });

            //be sure it is false before waiting
            assertThat(atomicBoolean).isFalse();

            ConditionalWait.Outcome outcome = ConditionalWait.wait(atomicBoolean::get, waitDuration);
            assertThat(outcome).isEqualTo(ConditionalWait.Outcome.CONDITION_MET);
        });
    }


    private void assertTimeTaken(Duration expectedDuration, long toleranceMs, Runnable test) {
        Instant startTime = Instant.now();

        test.run();

        Duration actualDuration = Duration.between(startTime, Instant.now());

        LOGGER.info("Actual: {}, expected: {}", actualDuration, expectedDuration);

        long durationDiffMs = Math.abs(actualDuration.toMillis() - expectedDuration.toMillis());
//        long toleranceMs = (long) Math.max(expectedDuration.toMillis() * 0.2, 10);

        //check it is within 5%
        assertThat(durationDiffMs).isLessThan(toleranceMs);
    }
}