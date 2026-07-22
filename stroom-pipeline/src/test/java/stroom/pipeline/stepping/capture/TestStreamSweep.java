/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.pipeline.stepping.capture;

import stroom.pipeline.shared.stepping.StepLocation;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class TestStreamSweep {

    private static final long META = 7L;

    private StreamSweep newSweep() {
        return new StreamSweep(META, null);
    }

    private StepLocation loc(final long record) {
        return new StepLocation(META, 0, record);
    }

    @Test
    void testRecordCapturedBumpsVersionAndLocation() {
        final StreamSweep sweep = newSweep();
        final long v0 = sweep.getVersion();
        sweep.recordCaptured(loc(0));
        assertThat(sweep.getVersion()).isGreaterThan(v0);
        assertThat(sweep.getLastCapturedLocation()).isEqualTo(loc(0));
        assertThat(sweep.isFullyCaptured()).isFalse();
    }

    @Test
    void testAwaitReturnsWhenVersionAlreadyAdvanced() {
        // A record captured between reading the version and awaiting must not be missed (no lost wake-up).
        final StreamSweep sweep = newSweep();
        final long known = sweep.getVersion();
        sweep.recordCaptured(loc(0));
        assertThat(sweep.awaitChangeSince(known, 5_000)).isTrue();
    }

    @Test
    void testAwaitWakesOnRecordFromAnotherThread() throws InterruptedException {
        final StreamSweep sweep = newSweep();
        final long known = sweep.getVersion();
        final Thread producer = new Thread(() -> {
            sleep(50);
            sweep.recordCaptured(loc(0));
        });
        producer.start();
        final boolean signalled = sweep.awaitChangeSince(known, 5_000);
        producer.join();
        assertThat(signalled).isTrue();
        assertThat(sweep.getVersion()).isGreaterThan(known);
    }

    @Test
    void testAwaitWakesOnComplete() throws InterruptedException {
        final StreamSweep sweep = newSweep();
        final long known = sweep.getVersion();
        final Thread producer = new Thread(() -> {
            sleep(50);
            sweep.markFullyCaptured();
        });
        producer.start();
        assertThat(sweep.awaitChangeSince(known, 5_000)).isTrue();
        producer.join();
        assertThat(sweep.isFullyCaptured()).isTrue();
    }

    @Test
    void testAwaitTimesOut() {
        final StreamSweep sweep = newSweep();
        // Nothing happens, so the wait must return false once the timeout elapses.
        assertThat(sweep.awaitChangeSince(sweep.getVersion(), 100)).isFalse();
        assertThat(sweep.isFullyCaptured()).isFalse();
    }

    @Test
    void testMarkErrorSetsErrorAndCompletes() {
        final StreamSweep sweep = newSweep();
        final RuntimeException boom = new RuntimeException("boom");
        sweep.markError(boom);
        assertThat(sweep.isFullyCaptured()).isTrue();
        assertThat(sweep.getError()).isSameAs(boom);
        // A subsequent error does not overwrite the first.
        sweep.markError(new RuntimeException("second"));
        assertThat(sweep.getError()).isSameAs(boom);
        // Awaiting on a completed sweep returns immediately.
        assertThat(sweep.awaitChangeSince(sweep.getVersion(), 5_000)).isTrue();
    }

    @Test
    void testAwaitChangeSinceReportsNoProgressWhenInterrupted() throws InterruptedException {
        // An interrupted wait must not claim progress: a caller that believes a record landed goes round
        // its resolve loop again, where the still-set interrupt flag makes the next await throw at once -
        // spinning a full store re-scan until its deadline instead of bailing out.
        final StreamSweep sweep = newSweep();
        final AtomicBoolean signalled = new AtomicBoolean(true);
        final AtomicBoolean interruptFlagPreserved = new AtomicBoolean();

        final Thread waiter = new Thread(() -> {
            signalled.set(sweep.awaitChangeSince(sweep.getVersion(), 30_000));
            interruptFlagPreserved.set(Thread.currentThread().isInterrupted());
        });
        waiter.start();
        // Give the waiter time to park, then interrupt it rather than letting it time out.
        sleep(100);
        waiter.interrupt();
        waiter.join(5_000);

        assertThat(waiter.isAlive()).isFalse();
        assertThat(signalled).isFalse();
        // The interrupt is still restored for the caller.
        assertThat(interruptFlagPreserved).isTrue();
    }

    @Test
    void testAwaitCompleteReportsIncompleteWhenInterrupted() throws InterruptedException {
        final StreamSweep sweep = newSweep();
        final AtomicBoolean completed = new AtomicBoolean(true);

        final Thread waiter = new Thread(() -> completed.set(sweep.awaitFullyCaptured(30_000)));
        waiter.start();
        sleep(100);
        waiter.interrupt();
        waiter.join(5_000);

        assertThat(waiter.isAlive()).isFalse();
        assertThat(completed).isFalse();
    }

    @Test
    void testTerminateRequestIsVisibleToACaptureThatHasNotStarted() {
        // The session sets this flag before reading the task context; a capture task publishes its context
        // before reading the flag. This is what stops a queued sweep starting after its session closed.
        final StreamSweep sweep = newSweep();
        assertThat(sweep.isTerminateRequested()).isFalse();
        assertThat(sweep.getTaskContext()).isNull();

        sweep.requestTerminate();

        assertThat(sweep.isTerminateRequested()).isTrue();
    }

    private static void sleep(final long ms) {
        try {
            Thread.sleep(ms);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
