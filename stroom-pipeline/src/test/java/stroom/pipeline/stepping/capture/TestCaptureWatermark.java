package stroom.pipeline.stepping.capture;

import stroom.pipeline.shared.stepping.StepLocation;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The invariant a consumer's liveness rests on: <b>every way a producer can stop must wake its waiters</b>.
 * A producer that stops silently does not give a wrong answer, it hangs the reader waiting on it, so each
 * terminal state is checked here directly rather than only through {@link StreamSweep}.
 * <p>
 * {@code TestStreamSweep} covers the same semantics through the sweep's delegating API; these test the
 * watermark on its own, because a per-element stage will hold one without a sweep around it.
 */
class TestCaptureWatermark {

    // The waiter's patience is deliberately far longer than the time we allow for it to be woken. Asserting
    // only that the wait RETURNED TRUE would prove nothing: with no signal at all the waiter blocks until its
    // own timeout, then re-checks the loop condition, sees the terminal flag already set and returns true -
    // late, but true. The wake has to be shown to be prompt, so the gap between these two is the test.
    private static final long AWAIT_TIMEOUT_MS = 30_000;
    private static final long WAKE_TIMEOUT_MS = 2_000;

    private static StepLocation loc(final long part, final long record) {
        return new StepLocation(1L, part, record);
    }

    /**
     * Runs {@code waiter} on another thread and returns what it observed, having first waited for that thread
     * to actually be inside the wait - otherwise the test could pass by simply never blocking.
     */
    private boolean observeFromAnotherThread(final Runnable trigger,
                                             final java.util.function.BooleanSupplier waiter)
            throws InterruptedException {
        final CountDownLatch started = new CountDownLatch(1);
        final AtomicBoolean result = new AtomicBoolean();
        final Thread thread = new Thread(() -> {
            started.countDown();
            result.set(waiter.getAsBoolean());
        });
        thread.start();
        assertThat(started.await(WAKE_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        // Give the waiter a moment to reach the condition before the terminal state is published, so this
        // exercises the signal rather than the already-terminal fast path.
        Thread.sleep(50);
        trigger.run();
        thread.join(WAKE_TIMEOUT_MS);
        assertThat(thread.isAlive())
                .as("waiter was woken promptly rather than left to time out")
                .isFalse();
        return result.get();
    }

    @Test
    void testCapturingARecordWakesAWaiter() throws InterruptedException {
        final CaptureWatermark watermark = new CaptureWatermark();
        final long version = watermark.getVersion();

        assertThat(observeFromAnotherThread(
                () -> watermark.recordCaptured(loc(0, 0)),
                () -> watermark.awaitChangeSince(version, AWAIT_TIMEOUT_MS))).isTrue();
    }

    @Test
    void testFinishingWakesAWaiter() throws InterruptedException {
        final CaptureWatermark watermark = new CaptureWatermark();
        final long version = watermark.getVersion();

        assertThat(observeFromAnotherThread(
                watermark::markFullyCaptured,
                () -> watermark.awaitChangeSince(version, AWAIT_TIMEOUT_MS))).isTrue();
    }

    @Test
    void testFailingWakesAWaiter() throws InterruptedException {
        // The case most easily got wrong: a producer that dies must not leave its consumer waiting out the
        // whole timeout for records that are never coming.
        final CaptureWatermark watermark = new CaptureWatermark();
        final long version = watermark.getVersion();

        assertThat(observeFromAnotherThread(
                () -> watermark.markError(new RuntimeException("producer died")),
                () -> watermark.awaitChangeSince(version, AWAIT_TIMEOUT_MS))).isTrue();
        assertThat(watermark.getError()).hasMessage("producer died");
    }

    @Test
    void testFailingAlsoWakesAWaiterOnCompletion() throws InterruptedException {
        final CaptureWatermark watermark = new CaptureWatermark();

        assertThat(observeFromAnotherThread(
                () -> watermark.markError(new RuntimeException("producer died")),
                () -> watermark.awaitFullyCaptured(AWAIT_TIMEOUT_MS))).isTrue();
    }

    @Test
    void testStoppedIsNotTheSameAsSucceeded() {
        // markError deliberately sets fullyCaptured so readers stop waiting, which is exactly why a caller
        // deciding whether the captured chunks are reusable has to ask the other question.
        final CaptureWatermark watermark = new CaptureWatermark();
        watermark.markError(new RuntimeException("boom"));

        assertThat(watermark.isFullyCaptured()).as("stopped producing").isTrue();
        assertThat(watermark.isSuccessfullyCaptured()).as("but not usable").isFalse();
    }

    @Test
    void testSucceededOnlyAfterACleanFinish() {
        final CaptureWatermark watermark = new CaptureWatermark();
        assertThat(watermark.isSuccessfullyCaptured()).isFalse();

        watermark.markFullyCaptured();

        assertThat(watermark.isSuccessfullyCaptured()).isTrue();
        assertThat(watermark.getError()).isNull();
    }

    @Test
    void testAlreadyAdvancedVersionDoesNotWait() {
        final CaptureWatermark watermark = new CaptureWatermark();
        final long version = watermark.getVersion();
        watermark.recordCaptured(loc(0, 0));

        // The lost-wake-up guard: a reader that took its version before reading the store must not block when
        // a record landed in between, however short the timeout.
        assertThat(watermark.awaitChangeSince(version, 0)).isTrue();
    }

    @Test
    void testWaitTimesOutWhenNothingHappens() {
        final CaptureWatermark watermark = new CaptureWatermark();

        assertThat(watermark.awaitChangeSince(watermark.getVersion(), 10)).isFalse();
        assertThat(watermark.awaitFullyCaptured(10)).isFalse();
    }

    @Test
    void testCapturedRangeIsTrackedPerPart() {
        final CaptureWatermark watermark = new CaptureWatermark();

        assertThat(watermark.getCapturedFirstRecordIndex(0)).as("nothing captured yet").isEqualTo(-1);
        assertThat(watermark.getCapturedLastRecordIndex(0)).isEqualTo(-1);

        watermark.recordCaptured(loc(0, 3));
        watermark.recordCaptured(loc(0, 4));
        watermark.recordCaptured(loc(1, 0));

        // Non-zero base: reader and text elements are 1-based where SAX is 0-based, so the range is whatever
        // was actually captured, not an assumed 0..n.
        assertThat(watermark.getCapturedFirstRecordIndex(0)).isEqualTo(3);
        assertThat(watermark.getCapturedLastRecordIndex(0)).isEqualTo(4);
        assertThat(watermark.getCapturedFirstRecordIndex(1)).isEqualTo(0);
        assertThat(watermark.getCapturedLastRecordIndex(1)).isEqualTo(0);
        assertThat(watermark.getCapturedFirstRecordIndex(2)).as("a part with no records").isEqualTo(-1);
    }

    @Test
    void testLastCapturedLocationFollowsTheProducer() {
        final CaptureWatermark watermark = new CaptureWatermark();
        assertThat(watermark.getLastCapturedLocation()).isNull();

        watermark.recordCaptured(loc(0, 0));
        watermark.recordCaptured(loc(0, 1));

        assertThat(watermark.getLastCapturedLocation()).isEqualTo(loc(0, 1));
        assertThat(watermark.getVersion()).isEqualTo(2);
    }
}
